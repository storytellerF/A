/* Test-only observer: record counts, never credentials, URLs or message bodies. */
(() => {
  const NativeSocket = window.WebSocket;
  const state = {attempts: 0, opened: 0, sent: 0, received: 0, sockets: []};
  window.appiumSocketProbe = state;
  window.WebSocket = class extends NativeSocket {
    constructor(...args) {
      super(...args);
      state.attempts++;
      state.sockets.push(this);
      this.addEventListener('open', () => state.opened++);
      this.addEventListener('message', () => state.received++);
    }
    send(data) {
      super.send(data);
      state.sent++;
    }
  };
})();

(() => {
  const nativeFetch = window.fetch;
  const http = {started: 0, completed: 0, failed: 0, failures: {}};
  window.appiumHttpProbe = http;
  window.fetch = function (...args) {
    http.started++;
    const result = nativeFetch.apply(this, args);
    result.then(() => http.completed++, error => {
      http.failed++;
      const kind = error?.name || 'unknown';
      http.failures[kind] = (http.failures[kind] || 0) + 1;
    });
    return result;
  };
  const NativeWorker = window.Worker;
  const pending = new Map();
  let nextWorker = 0;
  const workers = {sent: 0, received: 0, errors: 0};
  Object.defineProperty(workers, 'pending', {enumerable: true, get() {
    return [...pending.values()].reduce((counts, command) => {
      counts[command] = (counts[command] || 0) + 1;
      return counts;
    }, {});
  }});
  window.appiumWorkerProbe = workers;
  window.Worker = class extends NativeWorker {
    constructor(...args) {
      super(...args);
      this.probeId = nextWorker++;
      this.addEventListener('message', event => {
        workers.received++;
        if (event.data?.error) workers.errors++;
        pending.delete(`${this.probeId}:${event.data?.id}`);
      });
    }
    postMessage(...args) {
      workers.sent++;
      const message = args[0];
      if (['open', 'prepare', 'step'].includes(message?.data?.cmd)) {
        pending.set(`${this.probeId}:${message.id}`, message.data.cmd);
      }
      super.postMessage(...args);
    }
  };
})();
