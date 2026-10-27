/*
 * This is a private project. All rights reserved.
 */
const {test} = require('node:test');
const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const {resolve} = require('node:path');
const vm = require('node:vm');
const source = readFileSync(resolve(__dirname, '../../main/resources/wasm-coverage.js'), 'utf8');

function runtime(reply = () => Promise.resolve({ok: true})) {
  const requests = [], beacons = [], listeners = new Map();
  const build = 'a'.repeat(64);
  class Instance {
    constructor() {
      this.exports = {
        ['__e2e_cov_build_' + build]: 0,
        __e2e_cov_line_0: new WebAssembly.Global({value: 'i32', mutable: true}, 1),
      };
    }
  }
  const context = vm.createContext({
    document: {currentScript: {dataset: {build, lines: '1'}}},
    crypto: {randomUUID: () => 'document'},
    WebAssembly: {Global: WebAssembly.Global, Instance,
      instantiate: async () => ({instance: new Instance()}),
      instantiateStreaming: async () => ({instance: new Instance()})},
    fetch: (url, options) => {
      requests.push({url, body: JSON.parse(options.body)});
      return url.endsWith('/snapshot') ? reply() : Promise.resolve({ok: true});
    },
    navigator: {sendBeacon: (url, body) => {beacons.push({url, body: JSON.parse(body)}); return true;}},
    addEventListener: (event, callback) => listeners.set(event, callback),
  });
  vm.runInContext(source, context);
  return {requests, beacons, coverage: context.__e2eCoverage,
    instantiate: () => new context.WebAssembly.Instance(), hide: () => listeners.get('pagehide')()};
}

function deferred() {
  let resolve;
  const promise = new Promise(complete => {resolve = complete;});
  return {promise, resolve};
}

test('acknowledged final snapshot suppresses redundant unload publication', async () => {
  const run = runtime();
  run.instantiate();
  await run.coverage.collect();
  run.hide();
  assert.equal(run.requests.filter(request => request.url.endsWith('/snapshot')).length, 1);
  assert.deepEqual(run.requests.at(-1).body.instances[0].covered, [0]);
  assert.equal(run.beacons.length, 0);
});

test('pending snapshot keeps the unload fallback until acknowledgement', async () => {
  const gate = deferred();
  const run = runtime(() => gate.promise);
  run.instantiate();
  const collected = run.coverage.collect();
  run.hide();
  assert.equal(run.beacons.length, 1);
  gate.resolve({ok: true});
  await collected;
});

test('new instance after acknowledgement requires a new snapshot', async () => {
  const run = runtime();
  run.instantiate();
  await run.coverage.collect();
  run.instantiate();
  run.hide();
  assert.equal(run.beacons.length, 1);
  assert.equal(run.beacons[0].body.instances.length, 2);
  await run.coverage.collect();
  run.hide();
  assert.equal(run.beacons.length, 1);
});

test('acknowledgement cannot cover an instance registered during publication', async () => {
  const gate = deferred();
  const run = runtime(() => gate.promise);
  run.instantiate();
  const collected = run.coverage.collect();
  run.instantiate();
  gate.resolve({ok: true});
  await collected;
  run.hide();
  assert.equal(run.beacons.length, 1);
  assert.equal(run.beacons[0].body.instances.length, 2);
});

test('rejected snapshot does not disarm the unload fallback', async () => {
  const run = runtime(() => Promise.resolve({ok: false}));
  run.instantiate();
  await assert.rejects(run.coverage.collect(), /rejected snapshot/);
  run.hide();
  assert.equal(run.beacons.length, 1);
});
