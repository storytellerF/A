const {deflate} = require('node:zlib');
const {promisify} = require('node:util');
const compress = promisify(deflate);
const crcTable = Array.from({length: 256}, (_, value) => {
  for (let bit = 0; bit < 8; bit++) value = (value >>> 1) ^ ((value & 1) ? 0xedb88320 : 0);
  return value >>> 0;
});

async function driverScreenshot(next, driver, createNative, processId) {
  const caps = driver.caps || driver.opts || {};
  const automation = caps.automationName || caps['appium:automationName'];
  if (String(caps.platformName).toLowerCase() !== 'windows' ||
      String(automation).toLowerCase() !== 'desktopdriver' || !Number.isSafeInteger(processId)) return next();
  // Retain DesktopDriver's focus behavior before reading the physical window pixels.
  await next();
  return captureWindow(createNative(), await driver.getWindowHandle(), processId);
}

function chunk(type, bytes) {
  const content = Buffer.concat([Buffer.from(type), bytes]);
  let crc = 0xffffffff;
  for (const value of content) crc = (crc >>> 8) ^ crcTable[(crc ^ value) & 255];
  const size = Buffer.alloc(4);
  size.writeUInt32BE(bytes.length);
  const checksum = Buffer.alloc(4);
  checksum.writeUInt32BE((crc ^ 0xffffffff) >>> 0);
  return Buffer.concat([size, content, checksum]);
}

async function captureWindow(native, handle, processId) {
  const window = native.describe(handle);
  if (window.processId !== processId) throw Error('Window no longer belongs to the session process');
  if (!window.visible || window.processId <= 0) throw Error('Session window is not visible');
  if (native.describe(native.foreground()).processId !== window.processId) {
    throw Error('Session application is not in the foreground');
  }
  const {width, height, pixels} = native.capture(handle);
  if (![width, height].every(Number.isSafeInteger) || width <= 0 || height <= 0 ||
      width * height > 8388608 || pixels.length !== width * height * 4) throw Error('Invalid window capture');
  const rows = Buffer.alloc(height * (1 + width * 4));
  for (let y = 0; y < height; y++) {
    const row = y * (1 + width * 4);
    for (let x = 0; x < width; x++) {
      const source = (y * width + x) * 4;
      const target = row + 1 + x * 4;
      rows[target] = pixels[source + 2];
      rows[target + 1] = pixels[source + 1];
      rows[target + 2] = pixels[source];
      rows[target + 3] = 255;
    }
  }
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(height, 4);
  header[8] = 8;
  header[9] = 6;
  return Buffer.concat([Buffer.from('89504e470d0a1a0a', 'hex'), chunk('IHDR', header),
    chunk('IDAT', await compress(rows)), chunk('IEND', Buffer.alloc(0))]).toString('base64');
}

module.exports = {captureWindow, driverScreenshot};
