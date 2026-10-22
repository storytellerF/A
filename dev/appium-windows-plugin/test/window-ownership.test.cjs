const {test} = require('node:test');
const assert = require('node:assert/strict');
const {inspectWindows, resizeWindow} = require('../window-ownership.cjs');

const native = {
  describe: handle => ({processId: handle === 'foreign' ? 99 : 10, threadId: 7, visible: true}),
  foreground: () => 'foreign',
  dpi: () => 144,
};

test('foreign foreground windows cannot become the test target', () => {
  assert.deepEqual(inspectWindows(native, 10, 'main', ['main', 'dialog', 'foreign']),
    {ownedHandles: ['main', 'dialog'], foregroundHandle: null});
});

test('reused main handle fails before any mutation', () => {
  let changed = false;
  assert.throws(() => resizeWindow({...native, resize: () => { changed = true; }}, 10, 'foreign', 800, 600), /does not belong/);
  assert.equal(changed, false);
});

test('resize converts logical dimensions using guest window DPI', () => {
  const mutations = [];
  resizeWindow({...native, resize: (...args) => mutations.push(args)}, 10, 'main', 800, 600);
  assert.deepEqual(mutations, [['main', 1200, 900]]);
});
