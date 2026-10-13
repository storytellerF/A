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
