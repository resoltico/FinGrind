---
afad: "5.0.1"
version: "0.61.0"
domain: BOOK_OPERATION_ATTESTATION
updated: "2026-07-18"
scope:
  paths: ["contract", "core", "executor", "sqlite", "cli", "docs"]
  symbols: ["AttestedOperation", "AttestationEnvelope", "BackupManifest", "AttestationReceipt"]
route:
  keywords: [verifiable-operation-attestation, operation-head, attestation-envelope, backup-manifest, receipt-anchor, principal-quorum, ed25519, stale-head]
  questions: ["what does FinGrind book-operation attestation prove", "how is an attested operation encoded", "how does an attested backup restore without its source book", "how does FinGrind verify an attestation receipt"]
stage: "Slice 0 feature-branch specification; not released behavior"
---

# Verifiable Operation Attestation Protocol

This reference is the normative feature-branch contract for FinGrind's next accepted book format.
It is not a claim about any released command or persisted book until the Slice-5 hard format break
ships it together with its implementation, CLI contract, schema, examples, and user guides.

## Scope And Terminology

Book-operation attestation is distinct from the GitHub artifact attestation that establishes
publisher provenance for a FinGrind release archive. This protocol verifies mutations to one
protected book.

| Term | Meaning |
|:--|:--|
| attested operation | One committed domain mutation whose request and effect are signed and chained. |
| principal | A book-recognized credential holder; not a verified real-world identity. |
| credential | One enrolled Ed25519 public key identified by `SHA-256(SPKI)`. |
| operation head | `SHA-256` of one canonical operation envelope. |
| effect preimage | Immutable canonical facts describing one committed domain effect. |
| backup manifest | An off-chain, signed blessing embedded in a backup artifact. |
| receipt | An off-chain, signed anchor for an already committed operation head. |

## Guarantees And Limits

For every mutation, an attested book proves that a credential belonging to an authorized
book-recognized principal signed the exact semantic request and committed domain effect. Operations
form an ordered hash chain whose head commits to the signatures and authorization identities.

The protocol does not prove human intent, real-world identity, wall-clock truth, or completeness
relative to events outside the book. A retained independent receipt detects rollback, truncation,
or alteration through the head it pins. A witness can detect equivocation only when it observes both
branches; no anchor detects a fork that an attacker never reveals.

## Format And Policy Invariants

- Attestation is mandatory in the next accepted format. Earlier formats are rejected; there is no
  mode toggle or compatibility path.
- Version 1 accepts Ed25519 only. `algorithmId` occurs once in every signed payload and every
  signing entry must use Ed25519.
- Private-key bytes never enter a book, report, log, telemetry payload, or generic domain object.
  Version 1 supports only encrypted file-backed PKCS#8 custody, where a key is transiently
  decrypted inside the JCA signing boundary.
- Every policy rule names a capability and a concrete quorum `M`. A rule changes only through an
  attested alter-policy operation; its value is resolved at the signed structure's position.
- Every authorization envelope has exactly `M` distinct principals and exactly `M` distinct keys.
  Extra signatures are invalid; increase `M` explicitly when unanimous authorization is required.

## Principals, Credentials, And Capabilities

`principalId` is a 16-byte UUID. It identifies a principal recognized by the book, not a person,
company, or agent verified outside the book. `keyId` is the 32-byte SHA-256 digest of an X.509
SubjectPublicKeyInfo encoding and belongs to one principal at a time.

| Capability | Applies to | Genesis default `M` |
|:--|:--|:--:|
| `POST` | postings, reversals, opening facts | 1 |
| `APPROVE` | approval attachment | 1 |
| `CLOSE_PERIOD` | interim-result sweep and fiscal-year close | 1 |
| `BACKUP` | backup manifest and backup acknowledgement | 1 |
| `ANCHOR` | receipt export | 1 |
| `RESTORE` | restore-book | `min(2, founderCount)` |
| `REKEY` | rekey-book | `min(2, founderCount)` |
| `ENROLL_KEY` / `REVOKE_KEY` | credential enrollment, rollover, and revocation | `min(2, founderCount)` |
| `ALTER_POLICY` | policy change | `min(2, founderCount)` |

Genesis has one through five founders. Its effect preimage declares founder principals, their SPKIs,
and every initial policy rule. The genesis envelope contains exactly one signature from each founder.
Genesis is self-authorizing and is trusted out of band. All later operations resolve policy and key
validity as of `operationOrder - 1`.

## Canonical Primitive Encodings

All integers are big-endian. Fixed field order comes from the record-type catalog below; a field
does not carry a repeated field name or type tag.

| Value | Canonical bytes | Constraint |
|:--|:--|:--|
| `u8`, `u16`, `u32`, `u64` | unsigned fixed-width integer | big-endian |
| `i64`, `i128` | signed two's-complement integer | big-endian |
| `uuid` | 16 raw RFC-4122 bytes | textual UUIDs are never signed |
| `hash` | 32 raw SHA-256 bytes | no hex text in payloads |
| `spki` | `u16` byte length then DER SubjectPublicKeyInfo bytes | length 1–4096 |
| `bytes` | `u32` byte length then bytes | field-specific limit, never secrets |
| `token` | `u8` byte length then ASCII kebab token | length 1–64 |
| `text` | `u32` byte length then NFC UTF-8 | field-specific limit, never secrets |
| `date` | 10 ASCII bytes `YYYY-MM-DD` | Gregorian calendar date |
| `instant` | 24 ASCII bytes `YYYY-MM-DDThh:mm:ss.sssZ` | UTC millisecond precision |
| `money` | currency: 3 ASCII bytes; sign: `u8`; minor units: `u128` | no decimal point |
| `scaled` | scale: `u8`; sign: `u8`; units: `u128` | non-money quantity or rate |
| `bool` | one byte `00` or `01` | no other value |

Every optional field begins with `presence`: `00` means absent and has no following value;
`01` means present and is followed by its catalog-defined value. All text is NFC-normalized before
encoding. Passphrases, private keys, custodian handles, environment values, and local paths are
never request-preimage fields.

## Common Authorization Envelope

Every operation, manifest, and receipt uses this envelope.

```text
envelope = canonicalPayload
         || sigCount(u16 be)
         || sigCount * [principalId(16) || keyId(32) || signature(64)]
```

Entries are strictly ascending by raw `keyId`. Each signature is raw 64-byte Ed25519 output over
`canonicalPayload`. At the resolving position an envelope is valid only when:

1. `sigCount == M` for the applicable capability;
2. each principal and key appears exactly once;
3. each key belongs to its stated principal and is valid at that position;
4. each principal holds the capability at that position; and
5. every signature verifies under an Ed25519 SPKI whose digest equals its `keyId`.

## Operation Payload And Chain

```text
operationPayload =
  "FGATTOP1"                         // 8 bytes
  || payloadVersion(u8)               // 1
  || bookId(uuid)                     // 16
  || operationOrder(u64 be)           // 8
  || operationKind(token)             // 1 + length
  || algorithmId(token)               // 1 + length; v1 "ed25519"
  || previousHead(hash)               // 32; genesis is 32 zero bytes
  || recordedAt(instant)              // 24
  || requestDigest(hash)              // 32
  || effectDigest(hash)               // 32

operationHead = SHA-256(envelope(operationPayload))
```

An initiator signs the head it observed. Commit performs a compare-and-swap against that head; a
changed head refuses with stale-head, exit 2, and `{observedHead, currentHead, currentOrder}`.
No operation order is reserved while a custodian prompt or a co-signature exchange is pending.

| Offset | Size | Field | Example |
|--:|--:|:--|:--|
| 0 | 8 | domain tag | `FGATTOP1` |
| 8 | 1 | payload version | `01` |
| 9 | 16 | book ID | UUID bytes |
| 25 | 8 | operation order | `42` |
| 33 | 20 | operation kind | `13` + `record-sale-settled` |
| 53 | 8 | algorithm ID | `07` + `ed25519` |
| 61 | 32 | previous head | SHA-256 hash |
| 93 | 24 | recorded at | `2026-07-17T03:34:00.485Z` |
| 117 | 32 | request digest | SHA-256 hash |
| 149 | 32 | effect digest | SHA-256 hash |
| 181 | 2 | signature count | `00 01` |
| 183 | 16 | principal ID | UUID bytes |
| 199 | 32 | key ID | SHA-256(SPKI) |
| 231 | 64 | signature | Ed25519 bytes |
| 295 | — | envelope end | operation head input ends |

## Request And Effect Preimages

Preimages are append-only facts persisted with the operation. They are not recomputed from mutable
current-state rows. The mutation wrapper derives the effect preimage from the exact planned domain
writes and verifies that those writes and the preimage enter the same SQLite transaction.

```text
preimage = recordCount(u32 be)
         || records sorted by (recordTypeTag, perTypeSortKey)

record = recordTypeTag(u16 be)
      || fieldCount(u16 be)
      || fields in the catalog-defined order
```

Request preimages use the same framing and contain normalized semantic command facts. Effect
preimages contain every semantic state mutation, generated identity, lifecycle change, and
relationship update. They exclude the attested-operation row, envelope, audit-event row, anchor,
and physical output paths.

### Record-Type Catalog

Every effect record starts with `mutation` (`CREATE`, `AMEND`, `RETIRE`, `REACTIVATE`,
`REVERSE`, `DERIVE`, or `ACKNOWLEDGE`). The listed identity fields form `perTypeSortKey` in their
listed order. `?` marks an optional field encoded with `presence`.

| Tag | Record | Identity / sort key | Remaining field order |
|:--|:--|:--|:--|
| `0001` | `book.identity` | `bookId` | entity name, kernel profile, basis, framework position, entity form, template, costing doctrine?, functional currency, fiscal-year month, fiscal-year day, book start date |
| `0002` | `principal.key-binding` | `principalId`, `keyId` | binding action, SPKI, enrolled at, rollover predecessor? |
| `0003` | `policy.capability-rule` | capability | quorum `M`, require-distinct-principals |
| `0004` | `credential.revocation` | `keyId` | principal ID, reason text?, effective operation order |
| `0005` | `backup.acknowledgement` | `backupId` | artifact digest, source order, source head |
| `0010` | `account.state` | account code | name, type, node kind, active, all financial-position, cash-flow, profit-and-loss, unit, and quantity classifications |
| `0011` | `account.relationship` | account code, relationship kind | target account code? |
| `0012` | `tax.registration` | registration ID | jurisdiction, registration code, payable account, receivable account, active |
| `0013` | `tax.registration-code` | registration ID, tax code | rate/scaled value, effective date interval |
| `0020` | `posting.fact` | posting ID | kind, origin kind, effective date, recorded at, prior posting ID?, command ID, idempotency key, causation ID, source channel |
| `0021` | `posting.source-document` | posting ID, source document ID | document type, document date |
| `0022` | `posting.approval` | posting ID, approval ID | approving principal reference, approval facts |
| `0023` | `posting.applied-tax` | posting ID, registration ID, tax code | resolved taxable amount, tax amount, tax direction |
| `0024` | `posting.foreign-exchange` | posting ID | foreign currency, foreign amount, functional amount, exchange rate |
| `0025` | `journal.line` | posting ID, line order | account code, debit-or-credit side, money amount, quantity? |
| `0030` | `inventory.movement` | movement order | posting ID, account code, movement kind, quantity, unit cost, inventory cost |
| `0031` | `inventory.on-hand` | account code | quantity, cost pool, valuation horizon |
| `0040` | `interim-result-sweep` | sweep order | effective date range, result-holding account, generated posting identities |
| `0041` | interim-result-sweep-total | sweep order, currency | total money amount |
| `0042` | interim-result-sweep-posting | sweep order, posting ID | linkage facts |
| `0043` | `fiscal-year-close` | close order | effective date range, capital, result-holding, retained-result accounts |
| `0044` | fiscal-year-close-posting | close order, posting ID | linkage facts |
| `0050` | accrual-cutoff | cutoff ID | kind, origin posting ID, start/end dates, deferred or accrued amount |
| `0051` | accrual-cutoff-application | cutoff ID, application order | posting ID, application kind, recognized amount, reversal link? |
| `0060` | fixed-asset | asset ID | origin posting ID, asset class, capitalization amount, service date |
| `0061` | fixed-asset-application | asset ID, application order | posting ID, application kind, amount, period |
| `0062` | fixed-asset-reversal | reversal posting ID | asset ID, reversed application or origin ID |
| `0070` | financing-arrangement | arrangement ID | origin posting ID, principal, commencement facts |
| `0071` | financing-application | arrangement ID, application order | posting ID, principal amount, interest amount, date |
| `0072` | financing-reversal | reversal posting ID | arrangement ID, reversed application or origin ID |
| `0080` | foreign-currency-obligation | obligation ID | origin posting ID, currency, foreign amount, functional amount |
| `0081` | foreign-currency-settlement | obligation ID, settlement ID | posting ID, settlement amount, realized gain/loss |
| `0082` | foreign-currency-reversal | reversal posting ID | obligation or settlement ID |
| `0090` | latvian-payroll-run | payroll run ID | employee, month, withholding profile, gross/net/tax contribution amounts |
| `0091` | latvian-payroll-run-reversal | reversal posting ID | payroll run ID |
| `0092` | latvian-payroll-settlement | payroll run ID, settlement kind | posting ID, settled amount |
| `0093` | latvian-payroll-settlement-reversal | reversal posting ID | payroll run ID, settlement kind |
| `00A0` | `restore.provenance` | backup ID | artifact digest, restored-from order, historical-snapshot-authorization |

The catalog is intentionally semantic, not a mirror of SQLite rows. A change to a database index,
trigger, cache, audit row, generated report, or physical file path is not a domain record. Adding a
record tag or changing a field order requires a new attestation payload version and hard format
break.

## Backup Manifest And Recovery

A backup is a self-contained encrypted snapshot plus a manifest. The manifest, rather than the
later source-book index entry, is the artifact's standalone blessing.

```text
manifestPayload =
  "FGATTBM1" || manifestVersion(u8) || bookId(uuid) || backupId(uuid)
  || sourceOrder(u64 be) || sourceOperationHead(hash) || snapshotDigest(hash)
  || algorithmId(token)

manifestEnvelope = envelope(manifestPayload)   // BACKUP quorum as of sourceOrder

trailer = "FGATBMF1" || containerVersion(u8) || snapshotLength(u64 be)
        || manifestEnvelopeLength(u32 be)

publishedArtifact = snapshot || manifestEnvelope || trailer
```

The final 21-byte trailer lets a verifier split the artifact without scanning untrusted content.
It must specify exactly one snapshot and one manifest whose declared lengths consume the complete
file; trailing or unconsumed bytes are invalid. `snapshotDigest` hashes exactly the snapshot bytes.
`backupArtifactDigest` hashes the complete container.

Backup first creates one consistent SQLite snapshot at source order `H`, signs its manifest under
the `BACKUP` quorum as of `H`, and publishes it through an atomic no-clobber primitive. It then
best-effort appends an on-chain backup-created acknowledgement. A crash after publication but
before acknowledgement leaves a manifest-attested artifact and an understated source-book index.
A manifest-attested artifact is never classified as unattested or as an orphan.
Resume is a no-op success only for the identical `{bookId, backupId, backupArtifactDigest,
sourceHead}` tuple; any other reuse of `backupId` refuses with backup-acknowledgement-conflict.

Restore verifies the snapshot chain, manifest digest, source head, book ID, manifest signatures,
and `BACKUP` policy from the snapshot itself. It then resolves `RESTORE` as of `sourceOrder`,
appends `restore-book` at `sourceOrder + 1`, and atomically no-clobber publishes the destination.
Restore preserves `bookId`, is necessarily a potential fork, and records
`historicalSnapshotAuthorization=true`. A later-revoked key can therefore create a valid
restoration-derived branch; version 1 deliberately has no external current recovery authority.

Published backup artifacts are never deleted automatically. Explicit discard is an off-chain local
deletion and never claims in-book proof that a file is gone.

## Receipts And Anchors

```text
receiptPayload =
  "FGATTRC1" || receiptVersion(u8) || bookId(uuid) || operationOrder(u64 be)
  || operationHead(hash) || receiptTimestamp(instant) || algorithmId(token)

receiptEnvelope = envelope(receiptPayload)    // ANCHOR quorum as of operationOrder
```

Receipt timestamps are signer-asserted until a future RFC 3161 counter-signature. Export is
non-mutating and writes with atomic no-clobber semantics. A receipt anchors only after it leaves
the book's trust boundary and is retained independently; an output beside the book is explicitly
reported as non-independent.

## Verification And Failure Taxonomy

The future verify-book operation folds registry and policy facts, applies the genesis exception, recomputes both
preimage digests and every operation head, and reports the first structural break. Valid chains may
also carry `reviewRequired` findings for a declared key-compromise window. Those findings never
rewrite history. `--require-clean-attestation` maps any review finding to exit 2;
The future attestation-review operation is a non-persisted report.

| Result | Meaning | Recovery |
|:--|:--|:--|
| invalid-signature | An envelope signature does not verify. | Treat the book or artifact as invalid. |
| invalid-previous-head | The chain linkage is broken. | Compare against an independent receipt or witness. |
| invalid-quorum | Count, uniqueness, key binding, or capability rule fails. | Repair only from a valid source; never edit history. |
| invalid-preimage-digest | Immutable request/effect bytes differ from their digest. | Treat the operation as invalid. |
| invalid-backup-manifest | Container, digest, source-head, or manifest policy check fails. | Refuse restore. |
| stale-head | Live head changed before CAS commit. | Re-read, rebuild, and re-sign. |
| backup-acknowledgement-conflict | A backup ID was reused with different facts. | Use a fresh backup ID. |
| `reviewRequired` | A valid operation falls within a suspect key window. | Investigate; it is not automatic voiding. |

## Golden Vectors

The test seeds below are public fixtures only. They are never usable production credentials. Vector
implementations must reproduce every listed byte sequence and failure result exactly.

### `V-OP-01` — Single-Signer Operation Envelope

```text
privateSeed = 000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f
spki        = 302a300506032b657003210003a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8
keyId       = a050837d85070582ccf7394b0988847cc312cb88259b894899f6f239cf1791a5
principalId = 102132435465768798a9babcbddceeff
payload     = 46474154544f50310100112233445566778899aabbccddeeff000000000000002a137265636f72642d73616c652d736574746c65640765643235353139000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f323032362d30372d31375430333a33343a30302e3438355a202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f
signature   = 8f09b867c26f97cf7887d76fe87035b1ecf96ba078f816463e439d2d035e882288a6b4ec50951ba6e2bc7f28b954c1579e1fc37328a405b869644ff15f877d0e
head        = d7e8fb5126e2d1a7ff28398faec6bfa0e061ca1c74ffd4d1947ea5f70a339213
```

`payload` is 181 bytes. The envelope is `payload || 0001 || principalId || keyId || signature`
and is 295 bytes. Its SHA-256 digest must equal `head`.

### `V-MANIFEST-02` — Two-Principal Backup Quorum

```text
principalA  = 102132435465768798a9babcbddceeff
seedA       = 000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f
keyA        = a050837d85070582ccf7394b0988847cc312cb88259b894899f6f239cf1791a5
principalB  = 112233445566778899aabbccddeeff00
seedB       = 202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f
keyB        = 824c89aa8efb95ef93629b4519599129cace4adac9a6180daba31ceed41ecee6
manifestHead = c3a03b2006e080726454b60ace100df0f9e4e78cdf2154b0454503794c830c69
```

Use book ID `00112233445566778899aabbccddeeff`, backup ID
`ffeeddccbbaa99887766554433221100`, source order `42`, source head `V-OP-01.head`, snapshot
digest `606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f`, and `ed25519`.
The envelope uses `sigCount=0002` and orders key B before key A. Its complete SHA-256 digest is
`manifestHead`.

### `V-RECEIPT-02` — Two-Principal Anchor Quorum

Use the two principals and keys from `V-MANIFEST-02`, book ID
`00112233445566778899aabbccddeeff`, operation order `42`, operation head `V-OP-01.head`,
timestamp `2026-07-17T04:00:00.000Z`, and `ed25519`. The exact envelope SHA-256 digest is
`42549e39bdb60205d16082d6e557c4c9d12e000a87b40f0974b2d82f62f3d0dc`.

### Required Negative Corpus

| Vector family | Mutation or setup | Exact result |
|:--|:--|:--|
| operation, manifest, receipt | Flip one signature byte. | invalid-signature |
| operation, manifest, receipt | Set `sigCount < M`. | invalid-quorum: below-quorum |
| operation, manifest, receipt | Set `sigCount > M`. | invalid-quorum: excess-signature |
| operation, manifest, receipt | Repeat a principal or key entry. | invalid-quorum: duplicate-principal or duplicate-key |
| operation, manifest, receipt | Put entries out of key-ID order. | invalid-envelope-order |
| operation, manifest, receipt | Use a valid key that is not bound, active, or capable at the resolving position. | invalid-authorization |
| operation | Replace `previousHead`. | invalid-previous-head |
| genesis | Omit a founder or mismatch `keyId` and SPKI. | invalid-genesis |
| manifest | Mismatch snapshot digest, source head, source book ID, or trailer length. | invalid-backup-manifest |
| receipt | Use a signer without `ANCHOR` at the anchored order. | invalid-authorization |
| admission | Submit against a changed live head. | stale-head, exit 2 |
| admission | Resume a backup ID with different acknowledged facts. | backup-acknowledgement-conflict, exit 2 |

## Implementation Boundary

The code introduced after this document must make the protocol facts above canonical rather than
copying tags, limits, or result names into adapters. Slice 1 implements the canonical preimage and
envelope encoder. Slice 2 owns JCA custody. Slice 3 owns principal/policy resolution. Slice 4 owns
verification. Slice 5 makes the one public format break and updates user, CLI, response, schema,
security, index, example, and changelog documentation in the same release change.

---
