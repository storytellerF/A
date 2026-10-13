'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { LinuxDriver } = require('../lib/driver');
const { errors } = require('@appium/base-driver');

test('default implicit wait returns an empty lookup without polling', async () => {
  const driver = new LinuxDriver();
  let calls = 0;
  driver._atspi = async () => {
    calls++;
    return { ok: true, elements: [] };
  };
  driver._sleep = async () => { throw new Error('Unexpected fixed wait'); };
  assert.deepEqual(await driver.findElOrEls('name', 'missing', true), []);
  assert.equal(calls, 1);
});

test('configured implicit wait polls until the element appears', async () => {
  const driver = new LinuxDriver();
  driver.implicitWaitMs = 1000;
  let calls = 0;
  driver._atspi = async () => ({
    ok: true,
    elements: ++calls === 1 ? [] : [{ name: 'ready', cx: 1, cy: 1 }],
  });
  driver._sleep = async () => {};
  const elements = await driver.findElOrEls('name', 'ready', true);
  assert.equal(elements.length, 1);
  assert.equal(calls, 2);
});

test('click retains the AT-SPI object identity after result order changes', async () => {
  const driver = new LinuxDriver();
  driver._atspi = async () => ({ ok: true, elements: [{ name: 'title', path: '/stable/title' }] });
  const [element] = await driver.findElOrEls('name', 'title', true);
  driver._atspi = async (command) => {
    assert.equal(command.path, '/stable/title');
    return { ok: true, action: 'click' };
  };
  await driver.click(element['element-6066-11e4-a52e-4f735466cecf']);
});

test('a vanished element never falls back to old screen coordinates', async () => {
  const driver = new LinuxDriver();
  driver._atspi = async () => ({ ok: true, elements: [{ name: 'title', path: '/old/title' }] });
  const [element] = await driver.findElOrEls('name', 'title', true);
  driver._atspi = async () => ({ ok: false, stale: true, error: 'element no longer exists' });
  driver._xdotool = () => { throw new Error('Unexpected coordinate fallback'); };
  await assert.rejects(driver.click(element['element-6066-11e4-a52e-4f735466cecf']), /element no longer exists/);
});

test('an unavailable application returns an empty multiple lookup', async () => {
  const driver = new LinuxDriver();
  driver._atspi = async () => ({ ok: false, error: 'app not found' });
  assert.deepEqual(await driver.findElOrEls('name', 'control', true), []);
});

test('an unavailable application returns the missing-element protocol error', async () => {
  const driver = new LinuxDriver();
  driver._atspi = async () => ({ ok: false, error: 'app not found' });
  await assert.rejects(driver.findElOrEls('name', 'control', false), errors.NoSuchElementError);
});

for (const action of ['click', 'setValue']) {
  test(`${action} never uses old coordinates after the application disappears`, async () => {
    const driver = new LinuxDriver();
    driver._atspi = async () => ({
      ok: true,
      elements: [{ name: 'control', path: '/old/control', cx: 10, cy: 20 }],
    });
    const [element] = await driver.findElOrEls('name', 'control', true);
    driver._atspi = async () => ({ ok: false, error: 'app not found' });
    driver._xdotool = () => { throw new Error('Unexpected coordinate fallback'); };
    const id = element['element-6066-11e4-a52e-4f735466cecf'];
    await assert.rejects(
      action === 'click' ? driver.click(id) : driver.setValue('text', id),
      errors.StaleElementReferenceError,
    );
  });
}

test('a disconnected Xwayland display is rejected before launching the application', () => {
  const driver = new LinuxDriver();
  driver._displayInfo = () => ({ status: 0, stdout: '  dimensions:    0x0 pixels (0x0 millimeters)\n' });
  assert.throws(() => driver._assertDisplay(), /display with nonzero dimensions/);
});

test('an unavailable display is rejected before launching the application', () => {
  const driver = new LinuxDriver();
  driver._displayInfo = () => ({ status: 1, stdout: '' });
  assert.throws(() => driver._assertDisplay(), /display with nonzero dimensions/);
});

test('a valid virtual display permits application launch', () => {
  const driver = new LinuxDriver();
  driver._displayInfo = () => ({ status: 0, stdout: '  dimensions:    1600x1000 pixels (406x254 millimeters)\n' });
  driver._assertDisplay();
});

test('setValue sends the saved identity and concatenates input', async () => {
  const driver = new LinuxDriver();
  driver._atspi = async () => ({ ok: true, elements: [{ name: 'field', path: '/saved/field' }] });
  const [element] = await driver.findElOrEls('name', 'field', true);
  driver._atspi = async (command) => {
    assert.equal(command.path, '/saved/field');
    assert.equal(command.text, 'new text');
    return { ok: true };
  };
  await driver.setValue(['new ', 'text'], element.ELEMENT);
});

for (const result of [
  { ok: false, stale: true, error: 'element no longer exists' },
  { ok: false, error: 'editable text did not reach the requested value' },
]) {
  test(`setValue reports ${result.error} without keyboard or coordinate fallback`, async () => {
    const driver = new LinuxDriver();
    driver._atspi = async () => ({ ok: true, elements: [{ path: '/saved/field', cx: 10, cy: 20 }] });
    const [element] = await driver.findElOrEls('name', 'field', true);
    driver._atspi = async () => result;
    driver._xdotool = () => { throw new Error('Unexpected input fallback'); };
    await assert.rejects(driver.setValue('text', element.ELEMENT),
      result.stale ? errors.StaleElementReferenceError : /editable text did not reach/);
  });
}

test('Python helper identity regressions', () => {
  const { spawnSync } = require('node:child_process');
  const path = require('node:path');
  const result = spawnSync(process.platform === 'win32' ? 'python' : 'python3',
    [path.join(__dirname, 'atspi_helper_test.py')], { encoding: 'utf8' });
  assert.equal(result.status, 0, result.stdout + result.stderr);
});

test('dialog dismissal sends Escape without changing page-back behavior', async () => {
  const driver = new LinuxDriver();
  const commands = [];
  driver._xdotool = (...args) => commands.push(args);
  await driver.execute('linux: dismissDialog');
  await driver.back();
  assert.deepEqual(commands, [['key', 'Escape'], ['key', 'alt+Left']]);
  await assert.rejects(driver.execute('linux: unsupported'), errors.UnknownCommandError);
});
