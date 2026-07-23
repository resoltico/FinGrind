---
afad: "5.0.1"
version: "0.61.0"
domain: USER_BOOK_ATTESTATION
updated: "2026-07-23"
route:
  keywords: [fingrind, book-attestation, ed25519, founder, enroll-key, rollover-key, revoke-key, alter-policy, verify-book, attestation-review, receipt, backup, restore, rekey]
  questions: ["how does fingrind attest a book mutation", "how do I manage attestation credentials and policy", "how do I verify a fingrind book", "how do I retain and verify an attestation receipt"]
---

# Protected-Book Attestation

**Purpose**: Operate the immutable authorization evidence retained with every FinGrind protected-book mutation.
**Prerequisites**: A FinGrind protocol-34 / format-52 binary, one book passphrase source, and an authorized founder or operator credential where a command requires signing.

## What The Attestation Proves

Every accepted mutation is one ordered immutable operation with canonical request and committed-effect
preimages, an Ed25519 authorization envelope, and a SHA-256 operation head. The protected-book
transaction commits the accounting effect and the evidence together. FinGrind verifies the chain
from genesis rather than trusting mutable audit rows, reports, or rendered output.

Operation and receipt timestamps are recorded in UTC at millisecond precision. FinGrind
canonicalizes the runtime clock value before it becomes a durable accounting fact, attestation
preimage, or receipt payload, so every signed artifact carries an exact canonical time.

This proves that the book-recognized quorum authorized the recorded operation at its historical
position. It does not prove a person's real-world identity, an external event's truth, or events
that were never entered. An independently retained receipt can reveal rollback, truncation, or a
changed chain before its recorded head; it cannot reveal a fork that no independent observer sees.

Format 52 is a hard break. Earlier protected-book formats are rejected. There is no reader mode,
migration, alias, or compatibility layer.

## Founder And Operator Credentials

`open-book` requires one explicit custody selection and one through five aligned founder credential
triplets. Repeat each founder option in matching order:

```bash
fingrind open-book \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --entity-name "Acme Studio" \
  --book-template-id OWNER_MANAGED_SERVICE \
  --accounting-basis CASH \
  --functional-currency EUR \
  --fiscal-year-start 01-01 \
  --attestation-custodian file-pkcs8 \
  --attestation-founder-principal-id 123e4567-e89b-12d3-a456-426614174000 \
  --attestation-founder-key-file ./secrets/founder.fgatk \
  --attestation-founder-passphrase-file ./secrets/founder.passphrase
```

At genesis, a missing founder key path is created once and never overwritten; an existing path is
opened as the founder credential. Every command that creates or opens private attestation key
material requires an explicit `--attestation-custodian file-pkcs8` selection. Later signing
commands also require an existing enrolled credential: `--attestation-principal-id`,
`--attestation-key-file`, and `--attestation-passphrase-file`. The only shipped custodian is
`file-pkcs8`; an explicit unsupported selection, such as `pkcs11`, is refused as
`custodian-not-supported` with exit code 2. FinGrind never falls back to file custody. Do not
reuse a book key file as an attestation key file, copy an attestation passphrase into a command
line, or store either secret alongside an exported receipt. Every later signing command accepts
one through 64 aligned credential triplets under the selected custody, matching the full exact-quorum range that a reachable
policy may require. A policy therefore never leaves its own public signing, backup, restore,
rekey, receipt, or policy-repair path unreachable.

Before generating a standalone credential, create its parent directory yourself. The target must
sit beneath an existing real directory, never a symbolic link; FinGrind does not create credential
parent directories or silently redirect secret output. The final credential file is no-clobber and
owner-only, while the passphrase file remains a separately protected local secret.

The file-backed credential format is public: it stores an Ed25519 PKCS#8 private key encrypted
with PBKDF2-HMAC-SHA-256 (600,000 iterations, a fresh 16-byte salt) and AES-256-GCM (a fresh
12-byte IV and a 128-bit tag). A passphrase file must be valid UTF-8, nonempty after one optional
trailing line ending, and at most 4,096 bytes. Private-key material, passphrases, and local key
paths are not attestation payloads and must not be put in request JSON, logs, manifests, receipts,
or support tickets.

## Credential And Policy Lifecycle

Credential provisioning is an off-book custody step. First prepare a distinct owner-only,
nonempty UTF-8 passphrase file through your local secret-management procedure, then let FinGrind
create the no-clobber encrypted credential and emit the exact public identity that an enrollment
or rollover request needs:

```bash
fingrind generate-attestation-key-file \
  --attestation-custodian file-pkcs8 \
  --new-attestation-key-file ./secrets/operator.fgatk \
  --attestation-passphrase-file ./secrets/operator.passphrase
```

The successful payload includes canonical unpadded base64url `credentialSpki` and its derived
lowercase-hex `keyId`; it never includes the passphrase or private key. To recover the public
identity of an existing founder or operator credential for rollover or revocation, use:

```bash
fingrind inspect-attestation-key-file \
  --attestation-custodian file-pkcs8 \
  --attestation-key-file ./secrets/founder.fgatk
```

Lifecycle request documents carry only that public DER SubjectPublicKeyInfo (SPKI) as canonical
unpadded base64url in `credentialSpki`; they never carry a local credential path, a passphrase, a
private key, a caller-chosen key ID, or an encrypted-key-file payload. FinGrind derives the key ID
as SHA-256 of the SPKI and signs the resulting public binding facts.

All four lifecycle request documents are emitted directly by the binary. Use
`print-request-template enroll-key`, `print-request-template rollover-key`,
`print-request-template revoke-key`, or `print-request-template alter-policy`; the full JSON help
for each command carries that same `attestationTemplate` under `requestFile`. Template
UUIDs are canonical RFC 4122 spellings and must be replaced with the operation's real identities.
`credentialPurpose` is closed to the lowercase values `operator` and `system`; enrollment and
rollover templates publish `operator` before any request is submitted.

`print-request-template attestation-review` emits the distinct non-persisted
`--attestation-review-file` scaffold. Its full-detail JSON help also publishes the scaffold as
`requestFile.attestationTemplate`.

`enroll-key` binds a new credential to a principal. It does not grant a capability: use
`alter-policy` to grant the principal the intended capability after enrollment. For example:

```json
{
  "principalId": "01234567-89ab-4cde-8fab-0123456789ab",
  "credentialSpki": "MCowBQYDK2VwAyEAJYpWgBK4pHaKkIRKs9p8_6B01sG0SuOXLjI69Q5mGlI",
  "credentialPurpose": "operator"
}
```

```bash
fingrind enroll-key \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --request-file ./enroll-key.json \
  --attestation-custodian file-pkcs8 \
  --attestation-principal-id 123e4567-e89b-12d3-a456-426614174000 \
  --attestation-key-file ./secrets/founder.fgatk \
  --attestation-passphrase-file ./secrets/founder.passphrase
```

`rollover-key` binds a different new credential to the same principal and names the predecessor's
public SPKI in `predecessorCredentialSpki`. Its one signed operation records both the replacement
binding and a terminal `superseded` retirement of that predecessor, so the predecessor cannot sign
the next operation. Use `revoke-key`, with the credential's `credentialSpki` and an optional
non-blank reason, for a separately authorized security withdrawal. Supersession and revocation
are both final, auditable, and distinct states.

`alter-policy` changes only future policy. Its optional `policyRules`, `capabilityGrants`, and
`systemWorkflowPolicies` arrays must contain at least one item in total. Each rule names a
closed lowercase capability token and a quorum from 1 through 64. Each grant uses `grant` or
`revoke`. A system workflow is active only when its exact account configuration is supplied;
an interim-result sweep omits capital and retained-result accounts, while a fiscal-year close
supplies both. A single policy request may not repeat a capability, principal-capability pair, or
workflow ID. All four commands are ordinary attested mutations: their signer quorum, credential
purpose, and capability are resolved at the preceding book head, and a successful change first
governs the next operation.

An unauthorized protected-book mutation is a rejected envelope with the exact historical
attestation code, such as `attestation-key-not-enrolled`, `attestation-quorum-below`, or
`attestation-capability-invalid`, and exit code 2. It is never reported as a storage failure.

## Verify And Review

Use `verify-book` before relying on a book copied from another system or a recovered artifact:

```bash
fingrind verify-book \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --attestation-review-file ./reviews/acme-compromise.json \
  --require-clean-attestation
```

Structural verification returns the first deterministic chain break. `--require-clean-attestation`
also returns a rejected `attestation-review-required` envelope for a structurally valid chain that
has compromise-review findings, with exit code 2 and no success payload. On success,
`verify-book` publishes the full verified registry snapshot as `registry`: every credential binding
and its active, superseded, or revoked state, each effective capability quorum with eligible
principal counts, principal capability grants, and system-workflow policies. This is read-only
verification evidence reconstructed from the immutable chain, not a mutable administration view.
`attestation-review` returns the same non-persisted findings without changing the book:

```bash
fingrind attestation-review \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --attestation-review-file ./reviews/acme-compromise.json
```

The review file is strict JSON and does not alter the book:

```json
{
  "compromiseReviews": [
    {
      "credentialKeyId": "8f0e9c3c96c8188db78dc9de35290a86f8d3a5c0b9e9d1d2a0e3fd48c6b7a901",
      "firstAffectedOrder": "41",
      "lastAffectedOrder": "57"
    }
  ]
}
```

`credentialKeyId` is 64 lowercase hexadecimal characters. The orders are unsigned-decimal
strings, the interval is inclusive, and an omitted or `null` `lastAffectedOrder` runs through the
verified head. FinGrind rejects unknown fields, duplicate JSON object keys, and duplicate or
overlapping intervals for one credential as `invalid-request` on
`--attestation-review-file`. A finding returns the declaration plus its affected
`operationOrder`; JSON keeps an open-ended `lastAffectedOrder` as `null` rather than using a
sentinel string.

An invalid `verify-book` or `verify-receipt` JSON response is a rejected envelope with exit code
`2`, category `structural-invalid`, and one exact attestation failure code. The response never
falls back to `internal-error` for an expected verification refusal; use the published code to
distinguish a bad signature, invalid chain link, unauthorized historical credential, malformed
receipt, or unsupported format.

## Backups, Restores, And Receipts

`backup-book` publishes an encrypted backup pair only to absent destinations, then appends the
matching `backup-created` acknowledgement to the live chain. Supply a stable UUID with `--backup-id`.
If publication succeeds but acknowledgement is interrupted, rerun the exact same command with the
same book, backup paths, credentials, and backup ID; FinGrind resumes only that exact tuple.
If publication succeeds but the live-head authorization check refuses the acknowledgement, the
command returns the exact `attestation-*` rejected code with exit code `2`. The published pair is
preserved and is not reported as acknowledgement-pending; retain it, correct the signing
credentials or policy, and rerun the same exact tuple. Exit code `4` acknowledgement-pending is
reserved for an operational interruption whose authorization result was not determined.
If a forced stop leaves only a FinGrind-owned generated backup-key fragment, the next exact
`backup-book` invocation recovers that owned incomplete publication before destination admission
and starts a fresh pair. It never deletes an unowned or complete destination artifact.

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
  --attestation-custodian file-pkcs8 \
  --attestation-principal-id 123e4567-e89b-12d3-a456-426614174000 \
  --attestation-key-file ./secrets/founder.fgatk \
  --attestation-passphrase-file ./secrets/founder.passphrase

fingrind verify-receipt \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --receipt-file ./receipts/acme.fgar
```

Receipt export uses the historical `anchor` policy at the current head. A valid but insufficient,
unenrolled, revoked, or otherwise unauthorized signer set returns the exact attestation rejection
code, such as `attestation-quorum-below`, with exit code `2`; it never becomes a storage or
internal error and no receipt artifact is created.

`verify-receipt` reports `receipt-artifact-invalid` only when the selected bytes cannot be decoded
as a receipt artifact. Once decoded, receipt version, tuple, signature, quorum, and chain failures
retain their exact codes, such as `attestation-unsupported-version`,
`attestation-receipt-invalid`, `attestation-signature-invalid`, or
`attestation-quorum-below`.

For the canonical binary encoding and authorization policy, see
[DOC_02_VerifiableOperationAttestation.md](./DOC_02_VerifiableOperationAttestation.md). For
verification rules and structural failures, see
[DOC_02_VerifiableOperationAttestationVerification.md](./DOC_02_VerifiableOperationAttestationVerification.md).
Artifact invariants are in
[DOC_02_VerifiableOperationAttestationArtifacts.md](./DOC_02_VerifiableOperationAttestationArtifacts.md).
