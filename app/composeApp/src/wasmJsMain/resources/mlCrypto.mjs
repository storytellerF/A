import { p256 } from '@noble/curves/p256';
import { md5 } from '@noble/hashes/legacy';
import { ripemd160 } from '@noble/hashes/ripemd160';
import { ml_dsa65 } from '@noble/post-quantum/ml-dsa.js';
import { ml_kem768 } from '@noble/post-quantum/ml-kem.js';

const hex = (bytes) => Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
const bytes = (value) => {
  if (value.length % 2 !== 0 || !/^[0-9a-f]*$/i.test(value)) throw new Error('invalid hex');
  return Uint8Array.from(value.match(/.{2}/g) ?? [], (part) => Number.parseInt(part, 16));
};
const encodeKeys = ({ secretKey, publicKey }) => JSON.stringify({ privateKey: hex(secretKey), publicKey: hex(publicKey) });

const readDerItem = (der, offset, expectedTag) => {
  if (der[offset] !== expectedTag) throw new Error(`unexpected DER tag at ${offset}`);
  const firstLength = der[offset + 1];
  let length;
  let valueOffset;
  if ((firstLength & 0x80) === 0) {
    length = firstLength;
    valueOffset = offset + 2;
  } else {
    const lengthBytes = firstLength & 0x7f;
    if (lengthBytes === 0 || lengthBytes > 4) throw new Error('invalid DER length');
    length = 0;
    for (let index = 0; index < lengthBytes; index += 1) {
      length = (length << 8) | der[offset + 2 + index];
    }
    valueOffset = offset + 2 + lengthBytes;
  }
  const endOffset = valueOffset + length;
  if (endOffset > der.length) throw new Error('truncated DER value');
  return { valueOffset, endOffset };
};

const decodeP256PrivateScalar = (pem) => {
  const base64 = pem
    .replace('-----BEGIN PRIVATE KEY-----', '')
    .replace('-----END PRIVATE KEY-----', '')
    .replace(/\s/g, '');
  const der = Uint8Array.from(atob(base64), (character) => character.charCodeAt(0));
  const pkcs8 = readDerItem(der, 0, 0x30);
  let offset = pkcs8.valueOffset;
  offset = readDerItem(der, offset, 0x02).endOffset;
  offset = readDerItem(der, offset, 0x30).endOffset;
  const privateKey = readDerItem(der, offset, 0x04);
  const ecPrivateKey = readDerItem(der, privateKey.valueOffset, 0x30);
  offset = readDerItem(der, ecPrivateKey.valueOffset, 0x02).endOffset;
  const scalar = readDerItem(der, offset, 0x04);
  const value = der.slice(scalar.valueOffset, scalar.endOffset);
  if (value.length > 32) throw new Error('invalid P-256 private scalar');
  const padded = new Uint8Array(32);
  padded.set(value, padded.length - value.length);
  return padded;
};

const encodeP256Keys = (pem) => {
  const scalar = decodeP256PrivateScalar(pem);
  const publicKey = p256.getPublicKey(scalar, false);
  const privateDer = bytes(
    `308187020100301306072a8648ce3d020106082a8648ce3d030107046d306b0201010420${hex(scalar)}` +
      `a144034200${hex(publicKey)}`,
  );
  const publicDer = bytes(`3059301306072a8648ce3d020106082a8648ce3d030107034200${hex(publicKey)}`);
  return { privateDer, publicDer };
};

export const p256PrivateKeyDer = (privateKeyPem) => hex(encodeP256Keys(privateKeyPem).privateDer);
export const p256PublicKeyDer = (privateKeyPem) => hex(encodeP256Keys(privateKeyPem).publicDer);
export const md5Hex = (data) => hex(md5(bytes(data)));
export const ripemd160Hex = (data) => hex(ripemd160(bytes(data)));

export const mlDsa65KeyPair = () => encodeKeys(ml_dsa65.keygen());
export const mlDsa65PublicKey = (privateKey) => hex(ml_dsa65.getPublicKey(bytes(privateKey)));
export const mlDsa65Sign = (message, privateKey) => hex(ml_dsa65.sign(new TextEncoder().encode(message), bytes(privateKey)));
export const mlDsa65Verify = (signature, message, publicKey) =>
  ml_dsa65.verify(bytes(signature), new TextEncoder().encode(message), bytes(publicKey));

export const mlKem768KeyPair = () => encodeKeys(ml_kem768.keygen());
export const mlKem768PublicKey = (privateKey) => hex(ml_kem768.getPublicKey(bytes(privateKey)));
export const mlKem768Encapsulate = (publicKey) => {
  const { cipherText, sharedSecret } = ml_kem768.encapsulate(bytes(publicKey));
  return JSON.stringify({ cipherText: hex(cipherText), sharedSecret: hex(sharedSecret) });
};
export const mlKem768Decapsulate = (cipherText, privateKey) => hex(ml_kem768.decapsulate(bytes(cipherText), bytes(privateKey)));
