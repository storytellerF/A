'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const transform = require('./wasm-node-import-meta.cjs');

test('the lowered Node getter retains its module URL', async () => {
  const original = 'const importMeta = import.meta; return globalThis.module.default.createRequire(importMeta.url);';
  const lowered = transform(original);
  assert.doesNotMatch(lowered, /const importMeta/);
  const source = `
    const globalThis = {module: {default: {createRequire: url => url}}};
    export function original() {${original}}
    export function lowered() {${lowered}}
  `;
  const getters = await import('data:text/javascript,' + encodeURIComponent(source));
  assert.equal(getters.original(), getters.lowered());
});

test('unrelated import.meta aliases are preserved', () => {
  const source = 'const importMeta = import.meta; return otherGetter(importMeta.url);';
  assert.equal(transform(source), source);
});
