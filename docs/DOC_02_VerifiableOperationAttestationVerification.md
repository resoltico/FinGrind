---
afad: "5.0.1"
version: "0.61.0"
domain: BOOK_OPERATION_ATTESTATION_VERIFICATION
updated: "2026-07-22"
scope:
  paths: ["contract", "core", "executor", "sqlite", "cli", "docs"]
  symbols: ["AttestationInspectionService", "AttestationReviewResult", "AttestationStaleHeadException", "AttestationVerification", "AttestationVerificationException", "AttestationVerificationFailure", "AttestationVerifier", "VerifyBookAttestationResult"]
route:
  keywords: [attestation-verification, verifier-precedence, compromise-review, structural-invalid, stale-head, receipt-artifact, verification-rejection, clean-attestation]
  questions: ["how does FinGrind verify a protected-book attestation", "which attestation verification failure is reported first", "what does an attestation review finding mean", "how does FinGrind publish verification failures"]
stage: "Current public protocol 32 and protected-book format 51 contract"
---

# Verifiable Operation Attestation Verification

This document is the canonical verifier contract for FinGrind protocol 32 and protected-book
format 51. It defines verification result surfaces, compromise review, and deterministic failure
precedence. The [core protocol](./DOC_02_VerifiableOperationAttestation.md) owns the immutable
operation envelope, preimage grammar, historical authorization facts, and operation profiles that
this verifier consumes; the [artifact protocol](./DOC_02_VerifiableOperationAttestationArtifacts.md)
owns manifest, receipt, and container shapes.

## `AttestationVerification`

`AttestationVerification` returns the authenticated book identity, unsigned-64 head order,
operation head, and non-persisted review finding identifiers for a structurally valid chain.

## `AttestationVerificationException`

`AttestationVerificationException` identifies the first stable structural-failure token.

## `AttestationStaleHeadException`

`AttestationStaleHeadException` is the durable-write refusal when an authorization was created
from an attestation head that another committed operation has superseded. It carries defensive
copies of the observed head and current head plus the current unsigned-64 order. The CLI maps this
condition to the `stale-head` precondition envelope; it never reports it as an internal error. A
mutating `execute-plan` rolls back rather than converting this refusal into a plan journal. An
acknowledged `backup-created` operation re-observes the chain and obtains a new authorization
before retrying, while every other operation requires a fresh caller action.

## `AttestationVerificationFailure`

`AttestationVerificationFailure` is the closed public structural-rejection vocabulary for
`verify-book` and `verify-receipt`. Its exact `wireCode` is published in the rejected JSON
envelope, has the `structural-invalid` category, and produces process exit `2`; unknown,
normalized, or aliased codes are rejected at the contract boundary rather than becoming generic
internal errors.

```java
public enum AttestationVerificationFailure
```

| Member | `wireCode` |
|:--|:--|
| `UNSUPPORTED_VERSION` | `attestation-unsupported-version` |
| `PREIMAGE_INVALID` | `attestation-preimage-invalid` |
| `PREVIOUS_HEAD_INVALID` | `attestation-previous-head-invalid` |
| `REQUEST_PROFILE_INVALID` | `attestation-request-profile-invalid` |
| `UNKNOWN_OPERATION_KIND` | `attestation-unknown-operation-kind` |
| `ENVELOPE_ORDER_INVALID` | `attestation-envelope-order-invalid` |
| `QUORUM_BELOW` | `attestation-quorum-below` |
| `QUORUM_EXCESS` | `attestation-quorum-excess` |
| `DUPLICATE_PRINCIPAL` | `attestation-duplicate-principal` |
| `DUPLICATE_KEY` | `attestation-duplicate-key` |
| `KEY_NOT_ENROLLED` | `attestation-key-not-enrolled` |
| `KEY_REVOKED` | `attestation-key-revoked` |
| `KEY_PRINCIPAL_MISMATCH` | `attestation-key-principal-mismatch` |
| `KEY_ALGORITHM_INVALID` | `attestation-key-algorithm-invalid` |
| `SIGNATURE_INVALID` | `attestation-signature-invalid` |
| `CAPABILITY_INVALID` | `attestation-capability-invalid` |
| `CREDENTIAL_PURPOSE_INVALID` | `attestation-credential-purpose-invalid` |
| `SYSTEM_DERIVATION_INVALID` | `attestation-system-derivation-invalid` |
| `GENESIS_INVALID` | `attestation-genesis-invalid` |
| `MANIFEST_INVALID` | `attestation-manifest-invalid` |
| `RECEIPT_INVALID` | `attestation-receipt-invalid` |
| `RECEIPT_ARTIFACT_INVALID` | `receipt-artifact-invalid` |

The enum owns the public code vocabulary and response descriptors. Core verification may report
the first of these codes, while the executor maps it across the application boundary without
exposing an unclassified internal token.

---

## `AttestationVerifier`

`AttestationVerifier` is the pure complete-chain boundary; it owns no private-key type, custodian
handle, filesystem path, or mutable book state.

## `Attestation Inspection And Verification Results`

`AttestationInspectionService` projects verifier facts for the selected protected book.
`VerifyBookAttestationResult` and `AttestationReviewResult` are its public success/rejection
surfaces: verification proves the chain through its head, while review reports non-persisted
compromise findings without changing the book. A review result is not an authorization decision
and cannot repair, delete, or rewrite evidence. An invalid verification result contains exactly one
`AttestationVerificationFailure.wireCode`; it is never converted into an `internal-error` envelope.

## Verification, Compromise Review, And Failure Taxonomy

verify-book folds registry and policy, validates genesis, walks the chain, recomputes preimage
digests and operation heads, applies every historical envelope rule, checks chain linkage, and
reports the first structural break. A verifier checks a manifest independently from any
backup-created acknowledgement. It never reports a structural attestation defect as a generic
storage-runtime failure.

### Deterministic Failure Precedence

Every verifier applies the following numbered checks in order and returns the first failing check.
It does not substitute a later cryptographic or storage failure for an earlier structural
classification. For an operation, check 1 validates the domain tag and version
(attestation-unsupported-version for a syntactically present unsupported version); check 2 validates
the fixed payload grammar and bounds; check 3 decodes both preimages and validates their grammar,
ordering, and digests; check 4 resolves the closed operation kind; check 5 validates its exact
semantic profile; check 6 validates chain position and previous head; check 7 resolves historical
policy; check 8 compares sigCount with M; check 9 rejects duplicate principal IDs; check 10 rejects
duplicate key IDs; check 11 rejects a non-ascending adjacent key ID; check 12 rejects a non-Ed25519
credential or algorithmId; check 13a rejects a key with no binding effective at the resolving
position, check 13b rejects an effective revoked binding, and check 13c rejects a key-to-principal
binding mismatch; check 14 verifies signatures; check 15 validates capability eligibility; check
16a validates the sourceChannel's all-system or all-operator credential-purpose rule; and check 16b
validates an autonomous system derivation. Equal key IDs are check-10 duplicates, never an
envelope-order failure. A malformed or unknown operation domain tag, payload field, preimage field,
or record tag in checks 1 through 3 is attestation-preimage-invalid; check 4 is
attestation-unknown-operation-kind; check 5 is attestation-request-profile-invalid; checks 13a,
13b, and 13c are respectively attestation-key-not-enrolled, attestation-key-revoked, and
attestation-key-principal-mismatch.

For a manifest, before check 7 it performs these preamble checks in order: container trailer and
length framing; manifest domain tag and version; payload grammar; snapshot digest; declared bookId
against the snapshot; source order and source head against the internal chain. For a receipt,
before check 7 it performs: receipt domain tag and version; payload grammar; referenced operation
order; bookId; and operation head. A preamble failure uses attestation-manifest-invalid or
attestation-receipt-invalid, except an unsupported syntactically present structure version uses
attestation-unsupported-version. The shared envelope checks then start at check 7 because the
structure-specific resolving position is now known.

Genesis performs checks 1 through 3, then validates founder declaration, operator-purpose
requirement, and unanimity as attestation-genesis-invalid before applying any ordinary chain,
historical-policy, or capability check. A malformed backup container that is not a valid manifest
artifact is attestation-manifest-invalid; a receipt whose tuple does not bind the resolved book head
is attestation-receipt-invalid. An unreadable or malformed selected receipt artifact is
receipt-artifact-invalid.

Compromise review is verifier input, never mutable book state. A review declaration is the tuple
credential keyId, firstAffectedOrder, and optional lastAffectedOrder; an omitted end means the
verified head. Its interval is inclusive. A valid operation signed by that credential in the
interval receives a reviewRequired finding containing that tuple and the operation order.
verify-book remains valid; require-clean-attestation changes any reviewRequired finding to exit 2.
attestation-review is the same non-persisted, full finding report.

The following are valid-result findings rather than structural failures: reviewRequired contains
the compromise-review tuple and affected operation order; receipt-not-independent reports a receipt
retained within the book's trust boundary. Both have exit 0 unless require-clean-attestation turns
reviewRequired into exit 2.

| Exact result | Meaning | Exit |
|:--|:--|:--:|
| attestation-unsupported-version | unknown profile version | 2 |
| attestation-preimage-invalid | malformed preimage, unknown record, field-count, presence, ordering, or digest failure | 2 |
| attestation-request-profile-invalid | request records do not match the operation profile | 2 |
| attestation-unknown-operation-kind | operation kind is outside the closed catalog | 2 |
| attestation-previous-head-invalid | previous head does not link | 2 |
| attestation-envelope-order-invalid | envelope entries are not strictly keyId ascending | 2 |
| attestation-quorum-below | sigCount is smaller than M | 2 |
| attestation-quorum-excess | sigCount is larger than M | 2 |
| attestation-duplicate-principal | a principal occurs more than once | 2 |
| attestation-duplicate-key | a key occurs more than once | 2 |
| attestation-key-not-enrolled | key was not active at the resolving position | 2 |
| attestation-key-revoked | key was revoked at the resolving position | 2 |
| attestation-key-principal-mismatch | key does not belong to the stated principal | 2 |
| attestation-key-algorithm-invalid | non-Ed25519 key or algorithmId | 2 |
| attestation-signature-invalid | signature does not verify | 2 |
| attestation-capability-invalid | signer is not eligible or policy quorum is impossible | 2 |
| attestation-credential-purpose-invalid | sourceChannel conflicts with enrolled credential purposes | 2 |
| attestation-system-derivation-invalid | a system-channel close does not reproduce its one workflow derivation | 2 |
| attestation-genesis-invalid | genesis order, founders, policy, declared key, or unanimity rule fails | 2 |
| attestation-manifest-invalid | container, digest, source head, book identity, or BACKUP rule fails | 2 |
| attestation-receipt-invalid | receipt does not match the book, head, or ANCHOR rule | 2 |
| receipt-artifact-invalid | selected receipt artifact cannot be read or structurally parsed | 2 |
| stale-head | live head changed before CAS admission | 2 |
| backup-acknowledgement-conflict | backupId was reused with different facts | 2 |
| artifact-already-exists | no-clobber target already exists | 7 |
| custodian-not-supported | caller selected an unshipped key custodian | 2 |
