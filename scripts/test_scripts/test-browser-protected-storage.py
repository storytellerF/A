# This is a private project. All rights reserved.
"""Exercise the production Wasm storage bridge in an isolated headless browser."""
import argparse
import http.server
import json
import pathlib
import subprocess
import tempfile
import threading

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCE = ROOT / "client/composeCore/src/wasmJsMain/kotlin/com/storyteller_f/a/client/compose_core/utils/SigninCookies.wasmJs.kt"
TEST = r"""
(async () => {
    try {
        const assert = (ok, message) => { if (!ok) throw new Error(message); };
        const one = FACTORY;
        assert(await one.read('test:one') === null, 'Fresh store must be empty');
        await one.write('test:one', 'synthetic-private-key');
        await one.write('test:two', 'another-key');
        const restarted = FACTORY;
        assert(await restarted.read('test:one') === 'synthetic-private-key', 'Persisted value missing');
        assert(await restarted.read('test:two') === 'another-key', 'Account namespaces collided');
        assert(!Object.values(localStorage).some(value => value.includes('synthetic-private-key')), 'Plaintext in localStorage');
        const db = await new Promise((resolve, reject) => {
            const request = indexedDB.open('a-protected-accounts', 1);
            request.onsuccess = () => resolve(request.result);
            request.onerror = reject;
        });
        const load = key => new Promise(resolve => {
            const tx = db.transaction('entries', 'readonly');
            const request = tx.objectStore('entries').get(key);
            tx.oncomplete = () => resolve(request.result);
        });
        const secret = await load('master');
        assert(secret.extractable === false, 'Encryption key must not be exportable');
        let exported = false;
        try { await crypto.subtle.exportKey('raw', secret); exported = true; } catch (_) {}
        assert(!exported, 'Encryption key was exported');
        const record = await load('value:test:one');
        const data = new Uint8Array(record.data);
        assert(!new TextDecoder().decode(data).includes('synthetic-private-key'), 'Record is plaintext');
        data[0] ^= 1;
        await new Promise((resolve, reject) => {
            const tx = db.transaction('entries', 'readwrite');
            tx.objectStore('entries').put({...record, data}, 'value:test:one');
            tx.oncomplete = resolve;
            tx.onabort = reject;
        });
        let rejected = false;
        try { await restarted.read('test:one'); } catch (_) { rejected = true; }
        assert(rejected, 'Modified ciphertext was accepted');
        await fetch('/result', {method:'POST', body:JSON.stringify({passed:true})});
    } catch (error) {
        await fetch('/result', {method:'POST', body:JSON.stringify({passed:false, error:String(error)})});
    }
})();
"""


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--browser', required=True, help='Chromium or Edge executable')
    args = parser.parse_args()
    blocks = SOURCE.read_text(encoding='utf-8').split('"""')
    factory = '(' + blocks[3] + ').create(' + blocks[1] + ')'
    listener = "window.onerror = message => fetch('/result', {method:'POST', body:JSON.stringify({passed:false, error:String(message)})});"
    page = '<!doctype html><meta charset="utf-8"><script>' + listener + '</script><script>' + TEST.replace('FACTORY', factory) + '</script>'
    completed = threading.Event()
    result = {}

    class Handler(http.server.BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def do_GET(self):
            result['loaded'] = True
            self.send_response(200)
            self.send_header('Content-Type', 'text/html; charset=utf-8')
            self.end_headers()
            self.wfile.write(page.encode())

        def do_POST(self):
            result.update(json.loads(self.rfile.read(int(self.headers['Content-Length']))))
            self.send_response(200)
            self.end_headers()
            completed.set()

    output = ROOT / 'build' / 'browser-protected-storage'
    output.mkdir(parents=True, exist_ok=True)
    profile = tempfile.mkdtemp(prefix='profile-', dir=output)
    server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    runner = threading.Thread(target=server.serve_forever, daemon=True)
    runner.start()
    browser = subprocess.Popen([
        args.browser, '--headless=new', '--disable-gpu', '--no-first-run',
        '--no-default-browser-check', '--user-data-dir=' + profile,
        'http://127.0.0.1:' + str(server.server_port),
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        if not completed.wait(30):
            raise RuntimeError('Browser storage test did not finish; page loaded=' + str(result.get('loaded', False)))
        if not result.get('passed'):
            raise RuntimeError(result.get('error', 'Browser test failed'))
        print('PASS: browser persistence, namespace isolation, non-extractable key and tamper rejection')
    finally:
        browser.terminate()
        browser.wait(timeout=10)
        server.shutdown()
        server.server_close()


if __name__ == '__main__':
    main()
