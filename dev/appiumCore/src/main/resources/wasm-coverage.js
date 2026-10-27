/* Test-only coverage runtime. Probe stores stay inside each Wasm instance. */
(() => {
  const script = document.currentScript;
  const expectedBuild = script.dataset.build;
  const expectedLines = Number(script.dataset.lines);
  const documentKey = crypto.randomUUID();
  const instances = [];
  const registered = new WeakSet();
  let acknowledgedInstances = 0;
  const endpoint = '/__e2e-coverage/';
  const snapshot = () => ({
    buildId: expectedBuild,
    documentKey,
    instances: instances.map((counters, index) => ({
      index,
      covered: counters.flatMap((counter, id) => counter.value === 1 ? [id] : []),
    })),
  });
  const publish = (kind, data) => fetch(endpoint + kind, {
    method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(data),
  }).then(response => {
    if (!response.ok) throw new Error('Coverage collector rejected snapshot');
    return true;
  });
  const register = instance => {
    if (registered.has(instance)) return;
    registered.add(instance);
    const build = Object.keys(instance.exports).find(name => name.startsWith('__e2e_cov_build_'));
    if (!build) return;
    if (build.slice('__e2e_cov_build_'.length) !== expectedBuild) throw new Error('Coverage build identity mismatch');
    const counters = Array.from({length: expectedLines}, (_, id) => instance.exports['__e2e_cov_line_' + id]);
    if (counters.some(counter => !(counter instanceof WebAssembly.Global))) throw new Error('Coverage counters are missing');
    instances.push(counters);
  };
  const instantiate = WebAssembly.instantiate;
  WebAssembly.instantiate = async function(...args) {
    const result = await Reflect.apply(instantiate, this, args);
    register(result.instance || result);
    return result;
  };
  const streaming = WebAssembly.instantiateStreaming;
  WebAssembly.instantiateStreaming = async function(...args) {
    const result = await Reflect.apply(streaming, this, args);
    register(result.instance);
    return result;
  };
  const Instance = WebAssembly.Instance;
  WebAssembly.Instance = new Proxy(Instance, {construct(target, args, newTarget) {
    const result = Reflect.construct(target, args, newTarget);
    register(result);
    return result;
  }});
  globalThis.__e2eCoverage = {
    snapshot,
    async collect() {
      if (this.error) throw new Error(this.error);
      if (!instances.length) throw new Error('Instrumented Kotlin/Wasm instance was not registered');
      const state = snapshot();
      await publish('snapshot', state);
      acknowledgedInstances = state.instances.length;
      return true;
    },
  };
  // Track navigation before instantiation, so startup failures cannot disappear behind an older page's snapshot.
  publish('begin', {buildId: expectedBuild, documentKey}).catch(error => { globalThis.__e2eCoverage.error = String(error); });
  addEventListener('pagehide', () => {
    // An explicit boundary snapshot owns the handoff. Avoid a redundant unload request
    // that Firefox can abort while closing the browser; unacknowledged instances still need it.
    if (instances.length > acknowledgedInstances) navigator.sendBeacon(endpoint + 'snapshot', JSON.stringify(snapshot()));
  });
})();
