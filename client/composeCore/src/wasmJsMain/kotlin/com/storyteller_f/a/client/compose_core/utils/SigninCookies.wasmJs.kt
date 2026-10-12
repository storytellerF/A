/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.client.compose_core.utils

import kotlinx.coroutines.await
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.toJsString

@OptIn(ExperimentalWasmJsInterop::class)
private external interface BrowserProtectedStorage : JsAny {
    fun read(key: JsString): Promise<JsString?>
    fun write(key: JsString, value: JsString): Promise<JsAny?>
}

@OptIn(ExperimentalWasmJsInterop::class)
private external interface BrowserProtectedStorageFactory : JsAny {
    fun create(master: JsAny): BrowserProtectedStorage
}

@OptIn(ExperimentalWasmJsInterop::class)
private val protectedStorage = createProtectedStorageFactory().create(createMasterKeyFactory())

/** A read/write transaction ensures simultaneous tabs keep the same non-extractable master key. */
@OptIn(ExperimentalWasmJsInterop::class)
private fun createMasterKeyFactory(): JsAny =
    js(
    """(async (db) => {
            const candidate = await crypto.subtle.generateKey({name: 'AES-GCM', length: 256}, false, ['encrypt', 'decrypt']);
            return new Promise((resolve, reject) => {
                const tx = db.transaction('entries', 'readwrite');
                const store = tx.objectStore('entries');
                const request = store.get('master');
                let selected;
                request.onsuccess = () => {
                    selected = request.result ?? candidate;
                    if (!request.result) store.put(selected, 'master');
                };
                tx.oncomplete = () => resolve(selected);
                tx.onabort = () => reject(new Error('Protected key unavailable'));
            });
})""",
)

/** Non-extractable keys and ciphertext live in IndexedDB; no plaintext or key bytes enter localStorage. */
@OptIn(ExperimentalWasmJsInterop::class)
private fun createProtectedStorageFactory(): BrowserProtectedStorageFactory =
    js(
    """({create(master) {
        let opening;
        const open = () => {
            if (!globalThis.isSecureContext || !globalThis.crypto?.subtle) throw new Error('Secure context required');
            return opening ??= new Promise((resolve, reject) => {
                const request = indexedDB.open('a-protected-accounts', 1);
                request.onupgradeneeded = () => request.result.createObjectStore('entries');
                request.onsuccess = () => resolve(request.result);
                request.onerror = () => reject(new Error('Protected storage unavailable'));
                request.onblocked = () => reject(new Error('Protected storage blocked'));
            });
        };
        const load = (db, key) => new Promise((resolve, reject) => {
            const tx = db.transaction('entries', 'readonly');
            const request = tx.objectStore('entries').get(key);
            tx.oncomplete = () => resolve(request.result);
            tx.onabort = () => reject(new Error('Protected read failed'));
        });
        return {
            async read(key) {
                const db = await open();
                const record = await load(db, 'value:' + key);
                if (!record) return null;
                const secret = await load(db, 'master');
                if (!secret) throw new Error('Protected key missing');
                const plain = await crypto.subtle.decrypt({name:'AES-GCM', iv:record.iv,
                    additionalData:new TextEncoder().encode(key)}, secret, record.data);
                return new TextDecoder().decode(plain);
            },
            async write(key, value) {
                const db = await open();
                const secret = await master(db);
                const iv = crypto.getRandomValues(new Uint8Array(12));
                const data = await crypto.subtle.encrypt({name:'AES-GCM', iv,
                    additionalData:new TextEncoder().encode(key)}, secret, new TextEncoder().encode(value));
                await new Promise((resolve, reject) => {
                    const tx = db.transaction('entries', 'readwrite');
                    tx.objectStore('entries').put({iv, data}, 'value:' + key);
                    tx.oncomplete = () => resolve();
                    tx.onabort = () => reject(new Error('Protected write failed'));
                });
            }
        };
    }})""",
)

@OptIn(ExperimentalWasmJsInterop::class)
internal actual suspend fun readProtectedValue(key: String): String? =
    protectedStorage.read(key.toJsString()).await()?.toString()

@OptIn(ExperimentalWasmJsInterop::class)
internal actual suspend fun writeProtectedValue(key: String, value: String) {
    protectedStorage.write(key.toJsString(), value.toJsString()).await()
}

internal actual suspend fun readSigninCookies(key: String): String? = null
internal actual suspend fun writeSigninCookies(key: String, value: String) = Unit
actual val supportsChildAccountSwitching = false
