---
afad: "5.0.1"
version: "0.61.0"
domain: ATTESTATION
updated: "2026-07-23"
scope:
  paths: ["core/src/main/java/dev/erst/fingrind/core/attestation"]
route:
  keywords: [operation-envelope, conformance-vector, ed25519, operation-head, quorum, byte-grammar]
  questions: ["what are the operation envelope conformance vectors", "how do I reproduce the attestation operation payload bytes"]
---

# Verifiable Operation Attestation Vectors

## Operation Envelope Golden Vectors

All vector private seeds are public fixtures only, never production credentials. An encoder must
reproduce every declared payload, envelope, length, and digest byte-for-byte. A verifier must
return the listed exact result without falling through to a generic failure.

### V-OP-01: Single-Signer Operation Envelope

~~~text
privateSeed = 000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f
spki        = 302a300506032b657003210003a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8
keyId       = a050837d85070582ccf7394b0988847cc312cb88259b894899f6f239cf1791a5
principalId = 102132435465768798a9babcbddceeff
payload     = 46474154544f50310100112233445566778899aabbccddeeff000000000000002a137265636f72642d73616c652d736574746c65640765643235353139000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f323032362d30372d31375430333a33343a30302e3438355a202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f
signature   = 8f09b867c26f97cf7887d76fe87035b1ecf96ba078f816463e439d2d035e882288a6b4ec50951ba6e2bc7f28b954c1579e1fc37328a405b869644ff15f877d0e
head        = d7e8fb5126e2d1a7ff28398faec6bfa0e061ca1c74ffd4d1947ea5f70a339213
~~~

payload is 181 bytes. The envelope is payload, 0001, principalId, keyId, and signature; it is 295
bytes and SHA-256 equals head.

### V-OP-02: Complete Two-Principal Posting Envelope

This uses the V-OP-01 payload, principal A, and principal B. Under the standalone M=2 POST
envelope resolver in the corpus, both principals are active and granted POST. keyB sorts before
keyA, so B's entry precedes A's. It is not a complete protected-book chain fixture.

~~~text
signatureB = aa1ed4763cf3b2712c1826c25d43ff3d4cef5fb11ebd840ae97e57036f7003b2408a59ec16c9d3754ab1467d27488a96b455bb178182a4d56fd2d96d2b4be601
envelope   = 46474154544f50310100112233445566778899aabbccddeeff000000000000002a137265636f72642d73616c652d736574746c65640765643235353139000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f323032362d30372d31375430333a33343a30302e3438355a202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f0002112233445566778899aabbccddeeff00824c89aa8efb95ef93629b4519599129cace4adac9a6180daba31ceed41ecee6aa1ed4763cf3b2712c1826c25d43ff3d4cef5fb11ebd840ae97e57036f7003b2408a59ec16c9d3754ab1467d27488a96b455bb178182a4d56fd2d96d2b4be601102132435465768798a9babcbddceeffa050837d85070582ccf7394b0988847cc312cb88259b894899f6f239cf1791a58f09b867c26f97cf7887d76fe87035b1ecf96ba078f816463e439d2d035e882288a6b4ec50951ba6e2bc7f28b954c1579e1fc37328a405b869644ff15f877d0e
head       = 1340639b39f477bde0427c9e347b9096e18ef19551ff288f88aa597f1347d45a
~~~

The envelope is 407 bytes and SHA-256 equals head. V-OP-01's payload and A signature are unchanged.
The backup-manifest, receipt, and parser vectors are owned by
[DOC_02_VerifiableOperationAttestationArtifacts.md](./DOC_02_VerifiableOperationAttestationArtifacts.md).
