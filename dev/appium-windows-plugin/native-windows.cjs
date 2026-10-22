const koffi = require('koffi');

function createNativeWindows() {
  if (process.platform !== 'win32') throw Error('Windows guest required');
  const user = koffi.load('user32.dll');
  const owner = user.func('uint32_t __stdcall GetWindowThreadProcessId(uintptr_t hwnd, _Out_ uint32_t *pid)');
  const visible = user.func('bool __stdcall IsWindowVisible(uintptr_t hwnd)');
  const foreground = user.func('uintptr_t __stdcall GetForegroundWindow()');
  const dpi = user.func('uint32_t __stdcall GetDpiForWindow(uintptr_t hwnd)');
  const show = user.func('bool __stdcall ShowWindow(uintptr_t hwnd, int command)');
  const position = user.func('bool __stdcall SetWindowPos(uintptr_t hwnd, uintptr_t after, int x, int y, int width, int height, uint32_t flags)');
  const rectType = koffi.struct({left: 'int32_t', top: 'int32_t', right: 'int32_t', bottom: 'int32_t'});
  const headerType = koffi.struct({size: 'uint32_t', width: 'int32_t', height: 'int32_t', planes: 'uint16_t',
    bitCount: 'uint16_t', compression: 'uint32_t', imageSize: 'uint32_t', xPixels: 'int32_t', yPixels: 'int32_t',
    colorsUsed: 'uint32_t', colorsImportant: 'uint32_t'});
  const awareness = user.func('intptr_t __stdcall SetThreadDpiAwarenessContext(intptr_t context)');
  const monitorType = koffi.struct({size: 'uint32_t', monitor: rectType, work: rectType, flags: 'uint32_t'});
  const monitorFrom = user.func('uintptr_t __stdcall MonitorFromWindow(uintptr_t hwnd, uint32_t flags)');
  const monitorInfo = user.func('GetMonitorInfoW', 'bool', ['uintptr_t', koffi.inout(koffi.pointer(monitorType))]);
  const getRect = user.func('GetWindowRect', 'bool', ['uintptr_t', koffi.out(koffi.pointer(rectType))]);
  const dwm = koffi.load('dwmapi.dll');
  const visibleRect = dwm.func('DwmGetWindowAttribute', 'long', ['uintptr_t', 'uint32_t', koffi.out(koffi.pointer(rectType)), 'uint32_t']);
  const getDc = user.func('uintptr_t __stdcall GetDC(uintptr_t hwnd)');
  const releaseDc = user.func('int __stdcall ReleaseDC(uintptr_t hwnd, uintptr_t dc)');
  const gdi = koffi.load('gdi32.dll');
  const createDc = gdi.func('uintptr_t __stdcall CreateCompatibleDC(uintptr_t dc)');
  const createBitmap = gdi.func('uintptr_t __stdcall CreateCompatibleBitmap(uintptr_t dc, int width, int height)');
  const select = gdi.func('uintptr_t __stdcall SelectObject(uintptr_t dc, uintptr_t object)');
  const copy = gdi.func('bool __stdcall BitBlt(uintptr_t dc, int x, int y, int width, int height, uintptr_t source, int sx, int sy, uint32_t mode)');
  const getBits = gdi.func('GetDIBits', 'int', ['uintptr_t', 'uintptr_t', 'uint32_t', 'uint32_t', 'void *', koffi.inout(koffi.pointer(headerType)), 'uint32_t']);
  const deleteDc = gdi.func('bool __stdcall DeleteDC(uintptr_t dc)');
  const deleteBitmap = gdi.func('bool __stdcall DeleteObject(uintptr_t object)');
  const parse = handle => {
    if (typeof handle !== 'string' || !/^0x[0-9a-f]+$/i.test(handle)) throw Error('Invalid native handle');
    const value = Number.parseInt(handle.slice(2), 16);
    if (!Number.isSafeInteger(value) || value <= 0) throw Error('Invalid native handle');
    return value;
  };
  return {
    describe(handle) {
      const pid = [0];
      const window = parse(handle);
      const threadId = owner(window, pid);
      return {processId: pid[0], threadId, visible: visible(window)};
    },
    foreground: () => `0x${Number(foreground()).toString(16).padStart(8, '0')}`,
    dpi: handle => dpi(parse(handle)),
    capture(handle) {
      // Keep all GDI calls in one synchronous scope so Appium's thread DPI context is always restored.
      const previousDpi = awareness(-4);
      if (!previousDpi) throw Error('Cannot enter physical-pixel capture context');
      let screen = 0, memory = 0, bitmap = 0, previousBitmap = 0;
      try {
        const rect = {};
        // DWM excludes invisible resize borders, keeping other windows out of the image.
        if (visibleRect(parse(handle), 9, rect, 16) !== 0) throw Error('Session window has no visible bounds');
        const width = rect.right - rect.left, height = rect.bottom - rect.top;
        if (width <= 0 || height <= 0 || width * height > 8388608) throw Error('Window capture exceeds pixel limit');
        screen = getDc(0);
        memory = createDc(screen);
        bitmap = createBitmap(screen, width, height);
        if (!screen || !memory || !bitmap) throw Error('Cannot allocate window capture');
        previousBitmap = select(memory, bitmap);
        if (!previousBitmap || !copy(memory, 0, 0, width, height, screen, rect.left, rect.top, 0x00cc0020 | 0x40000000)) {
          throw Error('Native window capture failed');
        }
        select(memory, previousBitmap);
        previousBitmap = 0;
        const pixels = Buffer.alloc(width * height * 4);
        const info = {size: 40, width, height: -height, planes: 1, bitCount: 32, compression: 0, imageSize: 0,
          xPixels: 0, yPixels: 0, colorsUsed: 0, colorsImportant: 0};
        if (getBits(screen, bitmap, 0, height, pixels, info, 0) !== height) throw Error('Incomplete window pixels');
        return {width, height, pixels};
      } finally {
        if (previousBitmap) select(memory, previousBitmap);
        if (bitmap) deleteBitmap(bitmap);
        if (memory) deleteDc(memory);
        if (screen) releaseDc(0, screen);
        awareness(previousDpi);
      }
    },
    resize(handle, width, height) {
      const window = parse(handle);
      const previousDpi = awareness(-4);
      if (!previousDpi) throw Error('Cannot enter physical-pixel resize context');
      try {
        show(window, 9);
        const info = {size: 40}, rect = {};
        if (!monitorInfo(monitorFrom(window, 2), info) || !getRect(window, rect)) throw Error('No session monitor bounds');
        const area = info.work;
        if (width > area.right - area.left || height > area.bottom - area.top) {
          throw Error('Requested test window does not fit the desktop at its current DPI');
        }
        const x = Math.max(area.left, Math.min(rect.left, area.right - width));
        const y = Math.max(area.top, Math.min(rect.top, area.bottom - height));
        if (!position(window, 0, x, y, width, height, 0x0004)) throw Error('Guest window resize failed');
      } finally { awareness(previousDpi); }
    },
  };
}

module.exports = {createNativeWindows};
