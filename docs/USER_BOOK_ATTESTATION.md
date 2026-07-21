---
afad: "5.0.1"
version: "0.61.0"
domain: USER_BOOK_ATTESTATION
updated: "2026-07-21"
route:
  keywords: [fingrind, book-attestation, ed25519, founder, verify-book, attestation-review, receipt, backup, restore, rekey]
  questions: ["how does fingrind attest a book mutation", "how do I create founder credentials", "how do I verify a fingrind book", "how do I retain and verify an attestation receipt"]
---

# Protected-Book Attestation

**Purpose**: Operate the immutable authorization evidence retained with every FinGrind protected-book mutation.
**Prerequisites**: A FinGrind protocol-32 / format-51 binary, one book passphrase source, and an authorized founder or operator credential where a command requires signing.

## What The Attestation Proves

Every accepted mutation is one ordered immutable operation with canonical request and committed-effect
preimages, an Ed25519 authorization envelope, and a SHA-256 operation head. The protected-book
transaction commits the accounting effect and the evidence together. FinGrind verifies the chain
from genesis rather than trusting mutable audit rows, reports, or rendered output.

This proves that the book-recognized quorum authorized the recorded operation at its historical
position. It does not prove a person's real-world identity, an external event's truth, or events
that were never entered. An independently retained receipt can reveal rollback, truncation, or a
changed chain before its recorded head; it cannot reveal a fork that no independent observer sees.

Format 51 is a hard break. Earlier protected-book formats are rejected. There is no reader mode,
migration, alias, or compatibility layer.

## Founder And Operator Credentials

`open-book` requires one through five aligned founder triples. Repeat each option in matching order:

```bash
fingrind open-book \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --entity-name "Acme Studio" \
  --book-template-id OWNER_MANAGED_SERVICE \
  --accounting-basis CASH \
  --functional-currency EUR \
  --fiscal-year-start 01-01 \
  --attestation-founder-principal-id 123e4567-e89b-12d3-a456-426614174000 \
  --attestation-founder-key-file ./secrets/founder.fgatk \
  --attestation-founder-passphrase-file ./secrets/founder.passphrase
```

At genesis, a missing founder key path is created once and never overwritten; an existing path is
opened as the founder credential. Later signing commands require an existing enrolled credential:
`--attestation-principal-id`, `--attestation-key-file`, and
`--attestation-passphrase-file`. Do not reuse a book key file as an attestation key file, copy an
attestation passphrase into a command line, or store either secret alongside an exported receipt.

The file-backed credential format is public: it stores an Ed25519 PKCS#8 private key encrypted
with PBKDF2-HMAC-SHA-256 (600,000 iterations, a fresh 16-byte salt) and AES-256-GCM (a fresh
12-byte IV and a 128-bit tag). A passphrase file must be valid UTF-8, nonempty after one optional
trailing line ending, and at most 4,096 bytes. Private-key material, passphrases, and local key
paths are not attestation payloads and must not be put in request JSON, logs, manifests, receipts,
or support tickets.

## Verify And Review

Use `verify-book` before relying on a book copied from another system or a recovered artifact:

```bash
fingrind verify-book \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --require-clean-attestation
```

Structural verification returns the first deterministic chain break. `--require-clean-attestation`
also refuses a structurally valid chain that has compromise-review findings, with exit code 2.
`attestation-review` returns those non-persisted findings without changing the book:

```bash
fingrind attestation-review \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key
```

## Backups, Restores, And Receipts

`backup-book` publishes an encrypted backup pair only to absent destinations, then appends the
matching `backup-book` acknowledgement to the live chain. Supply a stable UUID with `--backup-id`.
If publication succeeds but acknowledgement is interrupted, rerun the exact same command with the
same book, backup paths, credentials, and backup ID; FinGrind resumes only that exact tuple.

`restore-book` verifies the backup's internal chain and manifest before restoring it to an absent
destination and appending a signed `restore-book` continuation. Restore uses the backup key to
verify the artifact and creates a new live-book key; it does not need an acknowledgement in the
source book.

Retain receipts outside the book and its backup storage boundary. Receipt export is no-clobber and
does not mutate the book:

```bash
fingrind export-attestation-receipt \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --receipt-file ./receipts/acme.fgar \
  --attestation-principal-id 123e4567-e89b-12d3-a456-426614174000 \
  --attestation-key-file ./secrets/founder.fgatk \
  --attestation-passphrase-file ./secrets/founder.passphrase

fingrind verify-receipt \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --receipt-file ./receipts/acme.fgar
```

For the canonical binary encoding, verification rules, authorization policy, and artifact
invariants, see [DOC_02_VerifiableOperationAttestation.md](./DOC_02_VerifiableOperationAttestation.md)
and [DOC_02_VerifiableOperationAttestationArtifacts.md](./DOC_02_VerifiableOperationAttestationArtifacts.md).
