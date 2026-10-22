const {test} = require('node:test');
const assert = require('node:assert/strict');
const {inflateSync} = require('node:zlib');
const {captureWindow, driverScreenshot} = require('../window-screenshot.cjs');

test('other Appium drivers keep their standard screenshot implementation', async () => {
  const output = await driverScreenshot(async () => 'android-image',
    {caps: {platformName: 'Android', automationName: 'UiAutomator2'}},
    () => { throw Error('Windows APIs must not be loaded'); });
  assert.equal(output, 'android-image');
});

test('physical capture dimensions and opaque BGRA pixels survive PNG encoding', async () => {
  const native = {
    describe: () => ({processId: 10, visible: true}), foreground: () => 'main',
    capture: () => ({width: 2, height: 1, pixels: Buffer.from([0, 0, 255, 0, 255, 0, 0, 0])}),
  };
  const png = Buffer.from(await captureWindow(native, 'main', 10), 'base64');
  assert.equal(png.subarray(0, 8).toString('hex'), '89504e470d0a1a0a');
  assert.equal(png.subarray(-8).toString('hex'), '49454e44ae426082');
  assert.equal(png.readUInt32BE(16), 2);
  assert.equal(png.readUInt32BE(20), 1);
  const size = png.readUInt32BE(33);
  assert.deepEqual(inflateSync(png.subarray(41, 41 + size)), Buffer.from([0, 255, 0, 0, 255, 0, 0, 255, 255]));
});

test('a foreign foreground process fails before any pixel capture', async () => {
  let captured = false;
  const native = {
    describe: handle => ({processId: handle === 'main' ? 10 : 99, visible: true}),
    foreground: () => 'foreign', capture: () => { captured = true; },
  };
  await assert.rejects(captureWindow(native, 'main', 10), /not in the foreground/);
  assert.equal(captured, false);
});


test('a reused native handle is rejected even when the foreign process is foreground', async () => {
  let captured = false;
  const native = {
    describe: () => ({processId: 99, visible: true}), foreground: () => 'main',
    capture: () => { captured = true; },
  };
  await assert.rejects(captureWindow(native, 'main', 10), /no longer belongs/);
  assert.equal(captured, false);
});
