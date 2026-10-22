const test = require('node:test');
const assert = require('node:assert/strict');
const {elementText} = require('../element-text.cjs');
const caps = {platformName: 'Windows', automationName: 'DesktopDriver'};

test('non-editor TextPattern content is preserved', async () => {
  const driver = {caps, getProperty: async () => 'Text'};
  assert.equal(await elementText(async () => 'visible', driver, 'id'), 'visible');
});

test('native editor value replaces its accessible caption', async () => {
  const properties = {ControlType: 'Edit', IsPassword: false, IsValuePatternAvailable: 'True'};
  const driver = {caps, getProperty: async name => properties[name], execute: async (script, args) => {
    assert.equal(script, 'windows: getValue');
    assert.deepEqual(args, [{elementId: 'id'}]);
    return 'draft text';
  }};
  assert.equal(await elementText(async () => 'Search', driver, 'id'), 'draft text');
});

test('passwords, unsupported patterns and other drivers do not expose values', async () => {
  for (const properties of [
    {ControlType: 'Edit', IsPassword: true, IsValuePatternAvailable: true},
    {ControlType: 'Edit', IsPassword: 'True', IsValuePatternAvailable: true},
    {ControlType: 'Edit', IsPassword: false, IsValuePatternAvailable: false},
    {ControlType: 'Text'},
  ]) {
    const driver = {caps, getProperty: async name => properties[name], execute: () => assert.fail('Must not read value')};
    assert.equal(await elementText(async () => '', driver, 'id'), '');
  }
  for (const other of [{caps: {platformName: 'Linux'}}, {caps, currentContext: 'WEBVIEW_1'}, {caps, isIEContext: () => true}]) {
    assert.equal(await elementText(async () => '', other, 'id'), '');
  }
});

test('ValuePattern read errors retain their original cause', async () => {
  const original = Error('Read failed');
  const driver = {caps, getProperty: async name => name === 'ControlType' ? 'Edit' : name === 'IsValuePatternAvailable',
    execute: async () => { throw original; }};
  await assert.rejects(elementText(async () => '', driver, 'id'), error => error === original);
});
