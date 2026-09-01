---
afad: "5.0.1"
version: "0.64.0"
domain: BOOK_OPERATION_ATTESTATION_VERIFICATION
updated: "2026-09-01"
scope:
  paths: ["contract", "core", "executor", "sqlite", "cli", "docs"]
  symbols: ["AttestationAdmissionRejectedException", "AttestationAuthorizationException", "AttestationAuthorizationFailure", "AttestationBookInspection", "AttestationCompromiseReview", "AttestationDiagnosticDescriptors", "AttestationInspectionService", "AttestationLifecycleRecoveryEvidenceVerifier", "AttestationReceiptArtifactException", "AttestationRegistryInspection", "AttestationReviewFinding", "AttestationReviewResult", "AttestationReviewWindowException", "AttestationStaleHeadException", "AttestationVerification", "AttestationVerificationException", "AttestationVerificationFailure", "AttestationVerifier", "VerifyBookAttestationResult"]
route:
  keywords: [attestation-verification, verifier-precedence, compromise-review, structural-invalid, stale-head, receipt-artifact, verification-rejection, clean-attestation]
  questions: ["how does FinGrind verify a protected-book attestation", "which attestation verification failure is reported first", "what does an attestation review finding mean", "how does FinGrind publish verification failures"]
stage: "Current public protocol 58 and protected-book format 57 contract"
---

# Verifiable Operation Attestation Verification

This document is the canonical verifier contract for FinGrind protocol 58 and protected-book
format 57. It defines verification result surfaces, compromise review, and deterministic failure
precedence. The [core protocol](./DOC_02_VerifiableOperationAttestation.md) owns the immutable
operation envelope, preimage grammar, historical authorization facts, and operation profiles that
this verifier consumes; the [artifact protocol](./DOC_02_VerifiableOperationAttestationArtifacts.md)
owns manifest, receipt, and container shapes.

## `AttestationVerification`

`AttestationVerification` returns the authenticated book identity, unsigned-64 head order,
operation head, its signed predecessor (`previousHead`), and typed non-persisted review findings
for a structurally valid chain. `previousHead` is the authenticated `previousHead` field of the
current-head envelope: it is all-zero at genesis and otherwise equals the preceding operation's
head.

## `AttestationLifecycleRecoveryEvidenceVerifier`

`AttestationLifecycleRecoveryEvidenceVerifier` proves that a recovered lifecycle head is the
exact signed restore acknowledgement or rekey continuation named by durable pair-publication
evidence. The filesystem recovery record never becomes cryptographic authority: the helper first
verifies the complete chain, then requires both the claimed order and head to match before it
compares the operation-specific immutable preimage facts. Invalid evidence, a wrong kind, or a
mismatched head returns `false`; no recovery path infers a matching operation from a partial
record.

## `AttestationBookInspection` And `AttestationRegistryInspection`

`AttestationVerifier.verifyAndInspectBook` returns an `AttestationBookInspection`: the verified
head plus an immutable `AttestationRegistryInspection` reconstructed from the same chain. The
registry snapshot contains all credential bindings (including active, superseded, or revoked state and binding
lineage), effective capability quorum with its eligible principal counts, principal capability
decisions, and system-workflow policies. `VerifyBookAttestationResult.Valid` publishes that
snapshot only when its book ID, head order, and operation head exactly match the verification
result. It is a read-only proof of the authority state at that historical head; it has no write or
repair API.

`AttestationVerifier.verifyAndInspectPostingCommitments` first verifies that same complete chain,
including every execute-plan wrapper and its reconstructed direct child profile. Only then does it
unwrap verified plan.child-effect-fact records and associate each posting.fact with the containing
aggregate operation order and head. The result is reconstructed evidence, not a mutable posting
backlink: malformed wrappers, a cross-step child substitution, or an invalid chain produces no
commitment mapping.

## `AttestationVerificationException`

`AttestationVerificationException` identifies the first stable structural-failure token.

## `AttestationReviewWindowException`

`AttestationReviewWindowException` refuses a non-persisted compromise-review declaration whose
first order, or finite final order, lies after the fully authenticated book head. The verifier
raises it only after it has established that head, rather than fabricating an impossible finding or
letting a transport projection fail. The CLI maps it to the domain-semantic
`attestation-review-window-exceeds-head` error with the credential key ID, declared interval, and
`verifiedHeadOrder`; its details always retain `lastAffectedOrder`, using JSON `null` for an
open-ended declaration. No book state changes.

## `AttestationStaleHeadException`

`AttestationStaleHeadException` is the durable-write refusal when an authorization was created
from an attestation head that another committed operation has superseded. It carries defensive
copies of the observed head and current head plus the current unsigned-64 order. The CLI maps this
condition to the `stale-head` precondition envelope; it never reports it as an internal error. A
mutating `execute-plan` rolls back rather than converting this refusal into a plan journal. An
acknowledged `backup-created` operation re-observes the chain and obtains a new authorization
before retrying, while every other operation requires a fresh caller action.

## `AttestationAuthorizationException` And `AttestationAuthorizationFailure`

`AttestationAuthorizationException` carries the first deterministic authorization refusal at a
resolving attestation position. Its closed `AttestationAuthorizationFailure` value owns the same
stable `attestation-*` code that the public `AttestationVerificationFailure` catalog publishes.
Live credential and policy mutations evaluate authority reconstructed through the current head and
translate this exception into a `structural-invalid` rejected envelope with exit code 2, preserving
such conditions as insufficient quorum, an unenrolled or revoked key, an ineligible capability, a
superseded or revoked credential, and an invalid credential purpose instead of misclassifying them
as storage failures.

## `AttestationAdmissionRejectedException`

`AttestationAdmissionRejectedException` is the application-boundary marker for an authorization
refusal discovered while admitting a signing credential, a genesis founder set, or a newly signed
mutation. It preserves the exact `AttestationAuthorizationFailure` and retains the lower-level
evidence when one exists; directly detected duplicate principal or public-key identities carry a
synthetic classification cause instead. Mutation and lifecycle commands publish that failure's
`attestation-*` code in a rejected envelope with exit code `2`; they do not relabel it as an
internal or operational failure. Credential and founder admission compare public key identities,
so path aliases and hard links to the same encrypted key reject as `attestation-duplicate-key`.
Historical evidence verification remains a verification result and does not use this live-admission
marker.

## `AttestationVerificationFailure`

`AttestationVerificationFailure` is the closed public rejection vocabulary for complete-chain
verification and for the same authorization conditions discovered against authority reconstructed
through the current head while admitting a live mutation, registry lifecycle change, receipt export,
or backup acknowledgement. Its exact `wireCode` is published in the rejected JSON envelope, has
the `structural-invalid` category, and produces process exit `2`; unknown, normalized, or aliased
codes are rejected at the contract boundary rather than becoming generic internal errors.

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
| `KEY_SUPERSEDED` | `attestation-key-superseded` |
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

The enum owns the public code vocabulary, context-neutral immutable-evidence description, and
per-code live-admission and evidence-preserving recovery facts. One internal catalog derives every
public contextual projection from those facts; CLI transport never reauthors message or hint text.
Core verification may report the first of these codes, while the executor maps it across the
application boundary without exposing an unclassified internal token.

### Operator Diagnostics

For ordinary attestation admission, the rejected envelope's `message` is that member's exact
live-admission description and its `hint` is that member's exact operator remediation. Diagnostics
therefore identify the cause itself — for example, too few signatures, too many signatures, an
invalid capability grant, or a key-to-principal mismatch — rather than collapsing distinct causes
into one generic signer error.

`capabilities --output json --detail full` publishes the same exact triplets at
`payload.fullContract.responseModel.attestationAdmissionDiagnostics[]`. Each row is
`{ context, diagnostics: [{ code, message, hint }] }`; `context` is one of
`ordinary-live-admission`, `registry-mutation`, or `backup-acknowledgement`. The ordinary context
also carries artifact-creation and receipt-export authorization outcomes. Registry mutation and
backup acknowledgement rows contain only operation-evidence failures: they deliberately omit
manifest and receipt failures, which cannot arise at those two append boundaries.

Registry lifecycle commands may substitute a truthful target-specific message for a failure that
describes the target credential or principal, while retaining the canonical remediation. A backup
acknowledgement prepends the fact that the backup artifact was already published, then retains the
exact causal description and remediation. Neither context may obscure the underlying public code
or replace it with a generic authorization diagnostic.

Historical verification is a different diagnostic context. `verify-book`, `attestation-review`,
and source-book verification before receipt export prepend a surface-specific statement to the
member's exact context-neutral description, then use its evidence-preserving verification recovery.
Those responses never describe historical authority as though it were evaluated at the live book
head. `verify-receipt` likewise names the exact cause, but its recovery first preserves the selected
receipt and directs the operator to compare it with a verified protected book before exporting a
replacement receipt or restoring a verified independently retained backup.

The corresponding discovery path is
`payload.fullContract.responseModel.attestationVerificationDiagnostics[]`, with rows
`{ surface, diagnostics: [{ code, message, hint }] }` for `verify-book`, `attestation-review`,
`export-attestation-receipt`, and `verify-receipt`. Only `verify-receipt` can publish the raw
`receipt-artifact-invalid` refusal; the other three surfaces publish verified-chain failures.

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
`operationOrder`. The verifier canonicalizes declarations, rejects duplicate or overlapping
intervals for a credential, and refuses an interval outside the authenticated head; callers never
receive opaque review strings.

## `Attestation Inspection And Verification Results`

`AttestationInspectionService` projects verifier facts for the selected protected book.
`VerifyBookAttestationResult` and `AttestationReviewResult` are its public success/rejection
surfaces: verification proves the chain through its head, while review reports non-persisted
compromise findings without changing the book. A valid verify result maps the verifier's
`bookId`, `headOrder`, `operationHead`, and signed `previousHead` directly; it does not persist a
second provenance backlink. `AttestationReviewResult.Valid` carries the verified `bookId`,
`headOrder`, and exact current `operationHeadHex` with its findings, so a review is bound to one
cryptographic chain state rather than only an ordinal position. `AttestationReviewResult.Invalid` carries the first exact
`AttestationVerificationFailure.wireCode` that prevented review. A review result is not an
authorization decision and cannot repair, delete, or rewrite evidence. Neither invalid result is
converted into an `internal-error` envelope.

CLI JSON projects a verified current identity as `verifiedAttestationHead` with the canonical
`operationOrder` and `operationHead` pair. `verify-book` retains the separately meaningful signed
`previousHead`; `attestation-review` publishes the same verified-head object beside its flat
findings. When `verify-book --require-clean-attestation` rejects a structurally valid chain with
review findings, its rejected envelope keeps no success payload but its typed `details` object
contains `bookId`, `verifiedAttestationHead`, `previousHead`, and the same flat
`reviewFindings`. Thus an automation client receives the exact state and cause from the one
verification read, without a racy follow-up command.

Receipt verification is a separate non-mutating proof at a selected historical position.
`VerifyAttestationReceiptResult.Valid` publishes the full verified receipt anchor — `bookId`,
`operationOrder`, and 64-lowercase-hex `operationHead` — plus the resolved canonical physical
receipt path rather than the caller's input spelling. This lets an operator or machine correlate
the exact receipt FinGrind read with the immutable chain without reparsing the artifact.

## Verification, Compromise Review, And Failure Taxonomy

verify-book folds registry and policy, validates genesis, walks the chain, recomputes preimage
digests and operation heads, applies every historical envelope rule, checks chain linkage, and
reports the first structural break. A verifier checks a manifest independently from any
backup-created acknowledgement. It never reports a structural attestation defect as a generic
storage-runtime failure.

The verified predecessor makes snapshot restoration independently checkable. If a verified source
snapshot has `(bookId, headOrder, operationHead) = (B, O, H)`, a verified restored destination
must report `(bookId, headOrder, previousHead) = (B, O + 1, H)`. A source-side
`backup-created` acknowledgement may be later than the snapshot and is never the restored
operation's predecessor.

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
`verify-book` remains structurally valid without the strict flag. With
`--require-clean-attestation`, any finding becomes the rejected
`attestation-review-required` envelope with exit 2 and no success payload.
`attestation-review` is the same non-persisted, full finding report.

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
overlapping inclusive intervals per credential before verification begins. A syntactically valid
declaration whose first order or finite final order is beyond the authenticated head is refused as
`attestation-review-window-exceeds-head`, with the declared bounds and `verifiedHeadOrder`; it is
not a verification finding. Both the error details and result payload return `credentialKeyId`,
`firstAffectedOrder`, an always-present nullable `lastAffectedOrder`, and their respective head
or `operationOrder`, all order values as decimal strings. A
declaration file is not persisted in the book, backup, manifest, or receipt. Malformed declarations
are an `invalid-request` refusal on `--attestation-review-file`.

Every command that creates or opens private attestation key material requires an explicit
`--attestation-custodian file-pkcs8` selection. `file-pkcs8` is the only shipped custodian. Any
other selected value, including `pkcs11`, is refused as `custodian-not-supported` with exit 2;
FinGrind never falls back to file custody.

The following are valid-result findings rather than structural failures: reviewRequired contains
the compromise-review tuple and affected operation order; receipt-not-independent reports a receipt
retained within the book's trust boundary. Both have exit 0 unless
`--require-clean-attestation` turns reviewRequired into the rejected
`attestation-review-required` result with exit 2.

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
| attestation-duplicate-key | a signature set, credential binding, or selected credential repeats a public key | 2 |
| attestation-key-not-enrolled | key was not active at the resolving position | 2 |
| attestation-key-revoked | key was revoked at the resolving position | 2 |
| attestation-key-superseded | key was replaced by rollover at the resolving position | 2 |
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
