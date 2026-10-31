function inspectWindows(native, processId, mainHandle, handles) {
  if (!Number.isSafeInteger(processId) || processId <= 0) throw Error('Invalid process ID');
  if (!Array.isArray(handles) || handles.length > 256) throw Error('Invalid window list');
  const main = native.describe(mainHandle);
  if (main.processId !== processId) throw Error('Main window does not belong to the test process');
  const owned = handles.filter(handle => {
    const window = native.describe(handle);
    return window.processId === processId && window.visible;
  });
  const foreground = native.foreground();
  return {ownedHandles: owned, foregroundHandle: owned.includes(foreground) ? foreground : null};
}

function resizeWindow(native, processId, handle, width, height) {
  if (![width, height].every(value => Number.isSafeInteger(value) && value > 0 && value <= 16384)) {
    throw Error('Window dimensions must be positive logical pixels');
  }
  inspectWindows(native, processId, handle, [handle]);
  const dpi = native.dpi(handle);
  if (dpi <= 0) throw Error('Owned window has no DPI');
  native.resize(handle, Math.round(width * dpi / 96), Math.round(height * dpi / 96));
}

module.exports = {inspectWindows, resizeWindow};
