'use strict';

// kotlinx-io's Node getter aliases the whole import.meta object. Webpack's
// browser bundle folds the URL read but leaves the bare alias in a classic
// script. Keep the getter equivalent while allowing property-level lowering.
module.exports = function wasmNodeImportMeta(source) {
  return source.replace(
    /const importMeta = import\.meta;\s+return globalThis\.module\.default\.createRequire\(importMeta\.url\);/g,
    'return globalThis.module.default.createRequire(import.meta.url);',
  );
};
