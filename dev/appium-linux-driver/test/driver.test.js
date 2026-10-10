'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { LinuxDriver } = require('../lib/driver');

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
