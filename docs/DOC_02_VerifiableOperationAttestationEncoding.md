---
afad: "5.0.1"
version: "0.62.2"
domain: BOOK_OPERATION_ATTESTATION_ENCODING
updated: "2026-08-09"
scope:
  paths: ["contract", "core", "executor", "sqlite", "cli", "docs"]
  symbols: ["AttestationCredentialException", "AttestationCredentialSource", "AttestationCredentialUseException", "AttestationCustodian", "AttestationCustodianNotSupportedException", "AttestationKeyFileCreation", "AttestationKeyFileMetadata", "AttestationKeyFiles", "AttestationMutationAuthorization", "AttestationOperationSigner", "AttestationPublicCredential", "AttestationSigningCredential", "AttestationSigningCredentialOpening", "AttestationSigningSession", "AttestationSigningSessionFactory"]
route:
  keywords: [verifiable-operation-attestation, credential, file-pkcs8, encrypted-key-file, ed25519, canonical-encoding, domain-tag, payload-version, big-endian, immutable-preimage]
  questions: ["how are FinGrind attestation credentials represented", "what is the file-pkcs8 credential container format", "which canonical primitive encoding does the attestation protocol use", "which attestation payload versions does FinGrind accept"]
stage: "Current public protocol 58 and protected-book format 57 contract"
---

# Verifiable Operation Attestation Credentials And Encoding

This document is the canonical credential-custody, signing, credential-value, and
canonical-byte-primitive owner for protected-book format 57.
[DOC_02_VerifiableOperationAttestation.md](./DOC_02_VerifiableOperationAttestation.md) owns the
operation, registry, and envelope protocol that uses these values.

## `AttestationKeyFiles`

`AttestationKeyFiles.create` is the sole current public creation path for a new encrypted
file-backed Ed25519 credential. It publishes a no-clobber key file and returns
`AttestationKeyFileCreation`: the public credential, canonical physical key-file path, and a
successful `PublicationTransactionArtifact`. A non-success publication raises
`PublicationTransactionExecutionException`, which carries only the transaction result and its
transaction identifier for recovery; it exposes no private stage. A verified pre-existing
no-replace target instead causes the journal-owned stage to be durably aborted and returns the
ordinary occupied-target failure; that result needs no recovery transaction.
`AttestationKeyFiles.loadPublicCredential` reads the public credential published with an existing
encrypted key without decrypting its private material. A credential contains only canonical public
DER-SPKI bytes and its SHA-256 key identifier.

Credential publication follows the transaction contract in
[the artifact reference](./DOC_02_VerifiableOperationAttestationArtifacts.md#publication-transactions).
Success requires both durable final publication and complete private-stage cleanup. Callers preserve
the reported candidate final path, inspect or recover a failure only by transaction identifier, and
choose a fresh target rather than treating a no-clobber failure as proof that the target is absent.

The public CLI makes those two safe custody operations available as
`generate-attestation-key-file` and `inspect-attestation-key-file`. Both require the caller to
select `--attestation-custodian file-pkcs8`; an unshipped selected custodian is refused rather than
silently falling back. The generator emits the canonical base64url SPKI required by `enroll-key`
or `rollover-key`, while inspection makes an existing credential's SPKI recoverable for revocation
or predecessor selection without exposing private material.

## `AttestationPublicCredential`

`AttestationPublicCredential` owns one canonical Ed25519 DER SubjectPublicKeyInfo value and its
derived SHA-256 key identifier. Construction rejects non-Ed25519 SPKI material, while `spki()` and
`keyId()` return defensive copies so a caller cannot mutate credential identity after admission.
It is public metadata only: it never contains a private key, encrypted key-file passphrase, or a
signing capability.

## `AttestationCustodian` And `AttestationCustodianNotSupportedException`

`AttestationCustodian` is the closed explicit source of private attestation-key custody. Its only
shipped wire value is `file-pkcs8`. The selection is exact: aliases, case normalization, and
implicit file custody are absent. `AttestationCustodianNotSupportedException` retains an explicit
unshipped value so the CLI can publish `custodian-not-supported` rather than misclassifying a
selection failure as an invalid credential or storage error.

## `AttestationKeyFileMetadata`

`AttestationKeyFileMetadata` is the public non-secret response value for standalone credential
custody. It carries the selected credential-file path, canonical base64url `credentialSpki`, and
the derived lowercase-hex SHA-256 `keyId`; the CLI publishes the key file itself only as the
generator's artifact and never publishes private-key or passphrase material.

## `AttestationOperationSigner`

`AttestationOperationSigner` builds an ordered operation envelope only from canonical request and
effect preimages; persistence reconstructs current authority from immutable evidence, then performs
authorization and compare-and-swap admission.

## `Attestation Signing Sessions And Authorization`

`AttestationSigningSession` owns one short-lived private signing capability. Its executor factory,
`AttestationSigningSessionFactory`, resolves a selected credential source without exposing private
key bytes to the caller. `AttestationCredentialSource` names the accepted credential source,
`AttestationCredentialException` reports an executor-facing credential-resolution failure, while
`AttestationCredentialUseException` reports that one explicitly selected encrypted credential
could not be read or verified and retains only its path plus a non-secret cause.
`AttestationMutationAuthorization` couples an authorized mutation to the operation evidence it
must append. These types are adapter seams: application code supplies a credential source and
canonical mutation facts, while the session owns signing and zeroization.

`AttestationSigningCredentialOpening` distinguishes an existing credential from one this
invocation created: it always carries the opened signing credential and carries a key-file
publication fact only for a newly created key. Genesis preparation uses that fact to retain and
disclose newly created founder-key evidence without treating an already-existing credential as a
new artifact.

## `AttestationSigningCredential`

`AttestationSigningCredential` binds one recognized principal to its public credential, selected
encrypted PKCS#8 path, and an owned passphrase copy. It is an `AutoCloseable` internal signing
input: closing it clears that owned passphrase, and its package-local signing operation verifies
that the decrypted private key matches the declared public credential before returning a signature.
The public CLI exposes credential metadata and custody operations, never this private signing
input or its passphrase.

## Profile Constants And Canonical Primitives

The current operation and artifact protocol values are exactly 01. A syntactically present other
value is `attestation-unsupported-version`; an implementation must not guess a newer grammar. The
independent `file-pkcs8` credential container has its own closed version 02 grammar below.

| Structure | Domain tag | Version field | Current value |
|:--|:--|:--|:--:|
| operation payload | FGATTOP1 | payloadVersion | 01 |
| backup manifest | FGATTBM1 | manifestVersion | 01 |
| receipt | FGATTRC1 | receiptVersion | 01 |
| backup artifact trailer | FGATBMF1 | containerVersion | 01 |

All integer values are big-endian. All timestamps are exactly 24 ASCII bytes in the form
YYYY-MM-DDThh:mm:ss.sssZ and must denote a valid UTC instant. All dates are exactly 10 ASCII bytes
in the form YYYY-MM-DD and must denote a valid Gregorian calendar date.

| Type | Canonical bytes | Validation |
|:--|:--|:--|
| u8, u16, u32, u64 | unsigned fixed-width integer | big-endian |
| i64, i128 | signed two's-complement fixed-width integer | big-endian |
| uuid | 16 RFC-4122 network-order bytes | textual UUIDs are never signed |
| hash | 32 raw SHA-256 bytes | hexadecimal text is never signed |
| spki | u16 byte length then DER SubjectPublicKeyInfo | length 1 through 4096; Ed25519 only |
| bytes | u32 byte length then raw bytes | length at most 1,048,576; never secret material |
| token | u8 byte length then lowercase ASCII kebab token | length 1 through 64 |
| algorithm-id | u8 byte length then lowercase ASCII kebab token | length 1 through 32; version 1 value is ed25519 |
| text | u32 byte length then NFC UTF-8 | length at most 1,048,576; no NUL |
| currency | three uppercase ASCII letters | ISO-4217 code when a currency is used |
| date | ten ASCII bytes | valid Gregorian date |
| instant | 24 ASCII bytes | valid millisecond UTC instant |
| money | currency, sign u8, minorUnits u128 | sign 00 is plus and 01 is minus; zero must use 00 |
| scaled | scale u8, sign u8, units u128 | scale 0 through 18; sign rules equal money |
| bool | one byte 00 or 01 | no other value |

## `file-pkcs8` Credential Container

The local file-backed credential is a versioned, bounded custody artifact, not a signed payload or
a protected-book record. Its exact byte grammar is:

~~~text
filePkcs8Credential =
  "FGATK"
  || credentialFormatVersion(u8 = 02)
  || kdfIterationCount(u32 = 600000)
  || salt(raw 16 bytes)
  || initializationVector(raw 12 bytes)
  || credentialSpkiLength(u16)
  || credentialSpki(credentialSpkiLength bytes; canonical Ed25519 DER SPKI)
  || encryptedPkcs8AndTag(remaining bytes)
~~~

Integers use the big-endian primitives above. `credentialSpki` is intentionally plaintext metadata
so `inspect-attestation-key-file` can recover the public enrollment identity without a passphrase;
it is validated as canonical Ed25519 DER SubjectPublicKeyInfo whenever FinGrind reads it. The
remaining bytes are the AES-256-GCM encryption of the PKCS#8 private key followed by the 16-byte
GCM authentication tag. Version 02 derives the 256-bit AES key with PBKDF2-HMAC-SHA-256 from the
passphrase and fresh salt, uses the fresh IV, and supplies no additional authenticated data.

A credential file is at most 1 KiB. FinGrind rejects a different magic, credential version, KDF
work factor, malformed SPKI, or oversized file; a signing attempt also rejects a key that cannot
decrypt and sign. It never guesses a legacy or future credential grammar. The private key,
passphrase, local path, and encrypted bytes remain outside all signed preimages, manifests,
receipts, reports, and response payloads.

No signed payload contains passphrases, private keys, custodian handles, environment values, local
paths, encrypted-key bytes, or presentation-only path hints. A preimage has at most 1,000,000
records and 16 MiB of encoded bytes. An authorization quorum is in the inclusive range 1 through
64. The post-operation fold of every policy, binding, credential retirement, grant, or system-workflow-policy
mutation must leave each configured quorum no greater than its eligible-principal count. It must
also leave each capability's quorum no greater than its operator-purpose eligible-principal count
whenever that capability admits a cli operation. If an active system-workflow policy exists, the
CLOSE_PERIOD quorum must additionally be no greater than its system-purpose eligible-principal
count. A system workflow is therefore never activated into an impossible all-system quorum.
