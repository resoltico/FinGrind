---
afad: "5.0.1"
version: "0.61.0"
domain: BOOK_OPERATION_ATTESTATION_VERIFICATION
updated: "2026-07-22"
scope:
  paths: ["contract", "core", "executor", "sqlite", "cli", "docs"]
  symbols: ["AttestationAdmissionRejectedException", "AttestationAuthorizationException", "AttestationAuthorizationFailure", "AttestationBookInspection", "AttestationCompromiseReview", "AttestationInspectionService", "AttestationReceiptArtifactException", "AttestationRegistryInspection", "AttestationReviewFinding", "AttestationReviewResult", "AttestationStaleHeadException", "AttestationVerification", "AttestationVerificationException", "AttestationVerificationFailure", "AttestationVerifier", "VerifyBookAttestationResult"]
route:
  keywords: [attestation-verification, verifier-precedence, compromise-review, structural-invalid, stale-head, receipt-artifact, verification-rejection, clean-attestation]
  questions: ["how does FinGrind verify a protected-book attestation", "which attestation verification failure is reported first", "what does an attestation review finding mean", "how does FinGrind publish verification failures"]
stage: "Current public protocol 33 and protected-book format 51 contract"
---

# Verifiable Operation Attestation Verification

This document is the canonical verifier contract for FinGrind protocol 33 and protected-book
format 51. It defines verification result surfaces, compromise review, and deterministic failure
precedence. The [core protocol](./DOC_02_VerifiableOperationAttestation.md) owns the immutable
operation envelope, preimage grammar, historical authorization facts, and operation profiles that
this verifier consumes; the [artifact protocol](./DOC_02_VerifiableOperationAttestationArtifacts.md)
owns manifest, receipt, and container shapes.

## `AttestationVerification`

`AttestationVerification` returns the authenticated book identity, unsigned-64 head order,
operation head, and typed non-persisted review findings for a structurally valid chain.

## `AttestationBookInspection` And `AttestationRegistryInspection`

`AttestationVerifier.verifyAndInspectBook` returns an `AttestationBookInspection`: the verified
head plus an immutable `AttestationRegistryInspection` reconstructed from the same chain. The
registry snapshot contains all credential bindings (including active or revoked state and binding
lineage), effective capability quorum with its eligible principal counts, principal capability
decisions, and system-workflow policies. `VerifyBookAttestationResult.Valid` publishes that
snapshot only when its book ID and head order exactly match the verification result. It is a
read-only proof of the authority state at that historical head; it has no write or repair API.

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

## `AttestationAuthorizationException` And `AttestationAuthorizationFailure`

`AttestationAuthorizationException` carries the first deterministic historical authorization
refusal. Its closed `AttestationAuthorizationFailure` value owns the same stable `attestation-*`
code that the public `AttestationVerificationFailure` catalog publishes. Live credential and
policy mutations translate this exception into a `structural-invalid` rejected envelope with exit
code 2, preserving such conditions as insufficient quorum, an unenrolled or revoked key, an
ineligible capability, and an invalid credential purpose instead of misclassifying them as
storage failures.

## `AttestationAdmissionRejectedException`

`AttestationAdmissionRejectedException` is the application-boundary marker for a historical
authorization refusal discovered while admitting a newly signed, live-book mutation. It preserves
both the exact `AttestationAuthorizationFailure` and the candidate-verification cause that exposed
it. Mutation and lifecycle commands publish that failure's `attestation-*` code in a rejected
envelope with exit code `2`; they do not relabel it as an internal or operational failure.
Historical evidence verification remains a verification result and does not use this live-admission
marker.

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
| `POLICY_CAPACITY_INVALID` | `attestation-policy-capacity-invalid` |
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

```java
public static AttestationVerification verifyBook(List<AttestationEvidence> operations)
public static AttestationVerification verifyBook(
    List<AttestationEvidence> operations,
    List<AttestationCompromiseReview> compromiseReviews)
```

`AttestationCompromiseReview` is an immutable external declaration with a lowercase 64-hex
`credentialKeyId`, an unsigned-64 `firstAffectedOrder`, and an optional unsigned-64 inclusive
`lastAffectedOrder`. `AttestationReviewFinding` binds that declaration to one unsigned-64
`operationOrder`. The verifier canonicalizes declarations and rejects duplicate or overlapping
intervals for a credential; callers never receive opaque review strings.

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
artifact is attestation-manifest-invalid. A selected receipt whose raw bytes cannot decode as a
receipt artifact is receipt-artifact-invalid. Once the receipt decodes, its version, tuple,
historical authorization, envelope, and chain checks retain their exact published code, such as
attestation-unsupported-version, attestation-receipt-invalid, attestation-signature-invalid, or
attestation-quorum-below; the executor does not flatten those semantic refusals into an artifact
error.

Compromise review is verifier input, never mutable book state. A review declaration is the tuple
credential keyId, firstAffectedOrder, and optional lastAffectedOrder; an omitted end means the
verified head. Its interval is inclusive. A valid operation signed by that credential in the
interval receives a typed review finding containing that tuple and the operation order.
`verify-book` remains structurally valid; `--require-clean-attestation` changes any finding to
exit 2. `attestation-review` is the same non-persisted, full finding report.

The CLI accepts declarations only through `--attestation-review-file <path>`. The file must be a
regular bounded JSON file with no duplicate object keys or unknown fields:

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

Orders are canonical unsigned-decimal strings. `lastAffectedOrder` may be omitted or JSON `null`;
it then extends through the verified head. The declarations are sorted and checked for duplicate or
overlapping inclusive intervals per credential before verification begins. The result payload
returns `credentialKeyId`, `firstAffectedOrder`, nullable `lastAffectedOrder`, and
`operationOrder`, all order values as decimal strings. A declaration file is not persisted in the
book, backup, manifest, or receipt. Malformed declarations are an `invalid-request` refusal on
`--attestation-review-file`, not a verification finding.

Every command that creates or opens private attestation key material requires an explicit
`--attestation-custodian file-pkcs8` selection. `file-pkcs8` is the only shipped custodian. Any
other selected value, including `pkcs11`, is refused as `custodian-not-supported` with exit 2;
FinGrind never falls back to file custody.

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
| attestation-duplicate-principal | a signature set or enroll-key request repeats a principal | 2 |
| attestation-duplicate-key | a signature set or credential binding repeats a key | 2 |
| attestation-key-not-enrolled | key was not active at the resolving position | 2 |
| attestation-key-revoked | key was revoked at the resolving position | 2 |
| attestation-key-principal-mismatch | key does not belong to the stated principal | 2 |
| attestation-key-algorithm-invalid | non-Ed25519 key or algorithmId | 2 |
| attestation-signature-invalid | signature does not verify | 2 |
| attestation-capability-invalid | signer is not eligible for the capability | 2 |
| attestation-policy-capacity-invalid | a policy change would leave its configured quorum unreachable | 2 |
| attestation-credential-purpose-invalid | sourceChannel conflicts with enrolled credential purposes | 2 |
| attestation-system-derivation-invalid | a system-channel close does not reproduce its one workflow derivation | 2 |
| attestation-genesis-invalid | genesis order, founders, policy, declared key, or unanimity rule fails | 2 |
| attestation-manifest-invalid | container, digest, source head, book identity, or BACKUP rule fails | 2 |
| attestation-receipt-invalid | receipt does not match the book, head, or ANCHOR rule | 2 |
| receipt-artifact-invalid | selected artifact is absent, non-regular, or its raw bytes cannot decode as a receipt | 2 |
| stale-head | live head changed before CAS admission | 2 |
| backup-acknowledgement-conflict | backupId was reused with different facts | 2 |
| artifact-already-exists | no-clobber target already exists | 7 |
| custodian-not-supported | caller selected an unshipped key custodian | 2 |
