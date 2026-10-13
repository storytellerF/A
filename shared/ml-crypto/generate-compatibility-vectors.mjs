/*
 * This is a private project. All rights reserved.
 */

import { mkdirSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

// Optionally resolve packages from Gradle's installed Wasm dependency directory.
const require = createRequire(process.argv[2] ? resolve(process.argv[2], 'package.json') : import.meta.url);
const { ml_dsa65 } = await import(pathToFileURL(require.resolve('@noble/post-quantum/ml-dsa.js')));
const { ml_kem768 } = await import(pathToFileURL(require.resolve('@noble/post-quantum/ml-kem.js')));
const hex = (bytes) => Buffer.from(bytes).toString('hex');
const message = 'Noble 0.6.1 interoperability';
const signing = ml_dsa65.keygen(Uint8Array.from({ length: 32 }, (_, i) => i));
const encryption = ml_kem768.keygen(Uint8Array.from({ length: 64 }, (_, i) => i));
const encapsulated = ml_kem768.encapsulate(encryption.publicKey, Uint8Array.from({ length: 32 }, (_, i) => 31 - i));
const aesKey = Uint8Array.from({ length: 32 }, (_, i) => i);
const wrapped = aesKey.map((byte, i) => byte ^ encapsulated.sharedSecret[i]);
const values = {
  message,
  signingPrivate: hex(signing.secretKey),
  signingPublic: hex(signing.publicKey),
  signature: hex(ml_dsa65.sign(new TextEncoder().encode(message), signing.secretKey, { extraEntropy: false })),
  encryptionPrivate: hex(encryption.secretKey),
  encryptionPublic: hex(encryption.publicKey),
  encryptedAesKey: hex(Buffer.concat([encapsulated.cipherText, wrapped])),
  aesKey: hex(aesKey),
};
const destination = fileURLToPath(new URL('../src/headlessTest/resources/post-quantum-noble.properties', import.meta.url));
mkdirSync(dirname(destination), { recursive: true });
writeFileSync(destination, '# Synthetic fixed-seed test keys, generated with @noble/post-quantum 0.6.1.\n'
  + Object.entries(values).map(([key, value]) => `${key}=${value}\n`).join(''));
