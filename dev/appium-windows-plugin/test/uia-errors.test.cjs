const test = require('node:test');
const assert = require('node:assert/strict');
const {withUiaErrors} = require('../uia-errors.cjs');
const driver = {caps: {platformName: 'Windows', automationName: 'DesktopDriver'}};
class StaleElementError extends Error {}

test('retired UIA geometry becomes stale without retrying the action', async () => {
  let calls = 0;
  const original = Error('An event was unable to invoke any subscribers (0x80040201)');
  await assert.rejects(withUiaErrors(async () => { calls++; throw original; }, driver,
    'getElementRect', [], StaleElementError), error => error instanceof StaleElementError && error.cause === original);
  assert.equal(calls, 1);
});

test('UIA extension commands recognize the symbolic unavailable code', async () => {
  await assert.rejects(withUiaErrors(async () => { throw Error('UIA_E_ELEMENTNOTAVAILABLE'); },
    driver, 'execute', ['windows: hover'], StaleElementError), StaleElementError);
});

test('other drivers and non-UIA commands preserve the original failure', async () => {
  const original = Error('0x80040201');
  for (const [target, command, args] of [
    [{caps: {platformName: 'Windows', automationName: 'Windows'}}, 'getElementRect', []],
    [{caps: {platformName: 'Linux', automationName: 'linux'}}, 'getElementRect', []],
    [{caps: driver.caps, currentContext: 'WEBVIEW_1'}, 'getElementRect', []],
    [{caps: driver.caps, isIEContext: () => true}, 'getElementRect', []],
    [driver, 'createSession', []], [driver, 'execute', ['powerShell']], [undefined, 'getStatus', []],
  ]) {
    await assert.rejects(withUiaErrors(async () => { throw original; }, target, command, args, StaleElementError),
      error => error === original);
  }
});

test('disabled controls, timeouts and unrelated COM codes are not swallowed', async () => {
  for (const code of ['0x80040200', '0x80131505', '0x80040202', '0x800402010']) {
    const original = Error(code);
    await assert.rejects(withUiaErrors(async () => { throw original; }, driver,
      'getElementRect', [], StaleElementError), error => error === original);
  }
  assert.equal(await withUiaErrors(async () => 42, driver, 'getElementRect', [], StaleElementError), 42);
});
