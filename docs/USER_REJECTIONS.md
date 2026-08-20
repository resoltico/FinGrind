---
afad: "5.0.1"
version: "0.63.0"
domain: OPERATOR_REJECTIONS
updated: "2026-08-20"
route:
  keywords: [fingrind, deterministic rejection, deterministic error, account-state-violations, entry-semantics-violations, invalid-request, pair-targets-conflict, target-owner-only-required, source-artifact-identity-duplicated, source-artifact-identity-changed, maintenance-recovery-pending, publication-transaction-incomplete, protected-book-pair-publication-evidence-blocked, rejection repair]
  questions: ["what deterministic rejections can FinGrind return", "how do I repair a deterministic FinGrind rejection", "what does account-state-violations mean in FinGrind", "why did FinGrind reject my protected-book pair targets", "what does source-artifact-identity-duplicated mean", "what does source-artifact-identity-changed mean", "how do I resume protected-book recovery evidence", "what does publication-transaction-incomplete mean", "what does protected-book-pair-publication-evidence-blocked mean"]
---

# Deterministic Rejection Guide

**Purpose**: Interpret deterministic rejection and associated error diagnostics, then repair the
request or command invocation from their typed facts.
**Prerequisites**: Familiarity with [USER_CLI.md](./USER_CLI.md) and the request shapes in
[USER_REQUESTS.md](./USER_REQUESTS.md).

This guide is the canonical catalog and repair surface for deterministic rejections and associated
error diagnostics.
[USER_RESPONSES.md](./USER_RESPONSES.md) owns shared success and error envelopes, discovery, read
payloads, report payload routing, and plan-result envelopes.

## Deterministic Rejections

| Code | Meaning | Extra `details` |
|:-----|:--------|:----------------|
| `book-already-initialized` | `open-book` targeted a book that is already initialized | none |
| `book-contains-schema` | `open-book` targeted a pre-existing SQLite file that already has schema objects | none |
| `administration-book-not-initialized` | an administration command targeted a book that does not exist or has not been opened yet | none |
| `query-book-not-initialized` | a query command targeted a book that does not exist or has not been opened yet | none |
| `posting-book-not-initialized` | a posting command targeted a book that does not exist or has not been opened yet | none |
| `pair-targets-conflict` | a protected-book and generated-secret final pair established or conservatively can establish one filesystem identity and therefore cannot form two independent final members | `bookTarget`, `generatedSecretTarget` |
| `artifact-path-invalid` with `pathFailure: "source-artifact-identity-duplicated"` | a later protected-book maintenance source role resolves to the same physical file as an earlier selected source | `artifactRole`, `artifactPath`, `pathFailure` |
| `artifact-path-invalid` with `pathFailure: "source-artifact-identity-changed"` | post-lock revalidation found that a protected-book maintenance source no longer has its locked physical identity | `artifactRole`, `artifactPath`, `pathFailure` |
| `artifact-path-invalid` with `pathFailure: "target-owner-only-required"` | an existing protected-book maintenance artifact is not owner-only | `artifactRole`, `artifactPath`, `pathFailure` |
| `account-type-conflict` | `declare-account` attempted to amend an existing account's immutable classification | `accountCode`, `existingAccountType`, `requestedAccountType` |
| `account-not-found` | `amend-account` or `retire-account` named an account that is not declared in the selected book | `accountCode` |
| `account-has-dependents` | an account amendment or retirement is blocked by durable postings, tax registrations, or child accounts | `accountCode`, `dependencies[]` |
| `account-balance-not-zero` | `retire-account` targeted an account whose current balance is not zero | `accountCode` |
| `unknown-account` | a query named an undeclared account | `accountCode` |
| `posting-not-found` | `get-posting` targeted a posting id that does not exist in the selected book | `postingId` |
| `account-state-violations` | `preflight-entry`, one of the typed `record-*` commit commands, or raw `post-entry` found one or more undeclared, inactive, or non-postable accounts, one inventory movement that would backdate before an account horizon, one inventory quantity decrease that would drive on-hand quantity below zero, or one carrying-cost decrease that would drive an inventory pool below zero | `violations[]`, where each item includes `code`, `field`, `message`, `category`, `repair`, `accountCode`, and optional `accountNodeKind` |
| `inactive-account` | one item inside `account-state-violations.violations[]` named an inactive account | `code`, `field`, `message`, `category`, `repair`, `accountCode` |
| `non-postable-account` | one item inside `account-state-violations.violations[]` named a declared header account that cannot accept direct postings | `code`, `field`, `message`, `category`, `repair`, `accountCode`, `accountNodeKind` |
| `inventory-movement-precedes-account-horizon` | one item inside `account-state-violations.violations[]` named one inventory movement whose effective date would backdate before the selected inventory account's accepted horizon | `code`, `field`, `message`, `category`, `repair`, `accountCode` |
| `inventory-quantity-below-zero` | one item inside `account-state-violations.violations[]` named one inventory decrease that would drive exact quantity on hand below zero | `code`, `field`, `message`, `category`, `repair`, `accountCode` |
| `inventory-write-down-exceeds-carrying-cost` | one item inside `account-state-violations.violations[]` named one carrying-cost decrease that would drive an inventory pool below zero | `code`, `field`, `message`, `category`, `repair`, `accountCode` |
| `entry-semantics-violations` | `preflight-entry`, one of the typed `record-*` commit commands, or raw `post-entry` found one or more ordered entry-semantics conflicts in the selected posting request | `violations[]`, where each item includes `code`, `field`, `message`, `category`, and `repair` |
| `economic-null-journal` | one item inside `entry-semantics-violations.violations[]` found that the supplied `DIRECT_JOURNAL` lines net every referenced account to zero | `code`, `field`, `message`, `category`, `repair` |
| `raw-journal-requires-cash-line` | one item inside `entry-semantics-violations.violations[]` found that the supplied `DIRECT_JOURNAL` adjustment omits every declared cash account line on a cash-basis book | `code`, `field`, `message`, `category`, `repair` |
| `raw-journal-touches-inventory` | one item inside `entry-semantics-violations.violations[]` found that the supplied `DIRECT_JOURNAL` contains one line whose declared account resolves to the inventory role, even though raw journals do not own exact inventory quantity truth | `code`, `field`, `message`, `category`, `repair` |
| `distinct-role-accounts-required` | one item inside `entry-semantics-violations.violations[]` found that two semantic role fields point to the same account even though the entry kind requires distinct accounts | `code`, `field`, `message`, `category`, `repair` |
| `account-type-mismatch` | one item inside `entry-semantics-violations.violations[]` found that one referenced account uses the wrong declared `accountType` for the selected entry kind | `code`, `field`, `message`, `category`, `repair` |
| `cash-flow-asset-classification-mismatch` | one item inside `entry-semantics-violations.violations[]` found that one referenced account uses the wrong declared `cashFlowAssetClassification` for the selected entry kind | `code`, `field`, `message`, `category`, `repair` |
| `financial-position-classification-mismatch` | one item inside `entry-semantics-violations.violations[]` found that one referenced account uses the wrong declared `financialPositionLineClassification` for the selected entry kind | `code`, `field`, `message`, `category`, `repair` |
| `account-role-mismatch` | one item inside `entry-semantics-violations.violations[]` found that one referenced account resolves to the wrong semantic `accountRole` for the selected entry kind | `code`, `field`, `message`, `category`, `repair` |
| `source-document-type-not-accepted` | one item inside `entry-semantics-violations.violations[]` found that the selected evidence uses one unsupported `sourceDocumentType` | `code`, `field`, `message`, `category`, `repair` |
| `unknown-tax-registration` | one item inside `entry-semantics-violations.violations[]` found that one tax selector references an undeclared `taxRegistrationId` | `code`, `field`, `message`, `category`, `repair` |
| `unknown-tax-code` | one item inside `entry-semantics-violations.violations[]` found that one tax selector references a `taxCode` not declared on the selected tax registration | `code`, `field`, `message`, `category`, `repair` |
| `tax-application-kind-mismatch` | one item inside `entry-semantics-violations.violations[]` found that one selected tax code resolves to an unsupported tax `applicationKind` for the entry kind | `code`, `field`, `message`, `category`, `repair` |
| `verb-requires-receivable-role` | one item inside `entry-semantics-violations.violations[]` found that the selected typed entry requires trade-receivable semantics that the current cash-basis book does not admit | `code`, `field`, `message`, `category`, `repair` |
| `verb-requires-payable-role` | one item inside `entry-semantics-violations.violations[]` found that the selected typed entry requires trade-payable semantics that the current cash-basis book does not admit | `code`, `field`, `message`, `category`, `repair` |
| `verb-requires-trading-template` | one item inside `entry-semantics-violations.violations[]` found that the selected inventory-purchase verb is admitted only on trading-template books | `code`, `field`, `message`, `category`, `repair` |
| `trading-sale-requires-inventory-relief` | one item inside `entry-semantics-violations.violations[]` found that a trading-template sale omitted the required `inventoryRelief` object | `code`, `field`, `message`, `category`, `repair` |
| `inventory-relief-requires-trading-book` | one item inside `entry-semantics-violations.violations[]` found that `inventoryRelief` appeared on a non-trading sale request | `code`, `field`, `message`, `category`, `repair` |
| `inventory-quantity-incompatible-with-unit-of-measure` | one item inside `entry-semantics-violations.violations[]` found that one inventory quantity field contradicts the selected inventory account's declared `unitOfMeasure` scale | `code`, `field`, `message`, `category`, `repair` |
| `inventory-acquisition-cost-not-exact` | one item inside `entry-semantics-violations.violations[]` found that one inventory acquisition's `quantity` and `unitCost` cannot compose one exact carrying-cost amount at the currency minor-unit boundary | `code`, `field`, `message`, `category`, `repair` |
| `inventory-acquisition-breaches-minor-unit-floor` | one item inside `entry-semantics-violations.violations[]` found that one inventory acquisition would leave a positive carrying-cost pool below the minimum minor-unit floor required to preserve zero-to-zero disposal truth | `code`, `field`, `message`, `category`, `repair` |
| `inventory-acquisition-foreign-exchange-functional-amount-mismatch` | one item inside `entry-semantics-violations.violations[]` found that a foreign-exchange inventory acquisition's retained functional amount differs from the exact pre-tax acquisition cost resolved from `quantity × unitCost` | `code`, `field`, `message`, `category`, `repair` |
| `inventory-capitalization-requires-quantity-on-hand` | one item inside `entry-semantics-violations.violations[]` found that a cost-only capitalization attempted to create a zero-quantity inventory pool | `code`, `field`, `message`, `category`, `repair` |
| `evidence-class-conflict` | one item inside `entry-semantics-violations.violations[]` found that the retained evidence class contradicts the event class resolved from the request | `code`, `field`, `message`, `category`, `repair` |
| `raw-journal-shadows-typed-event` | one item inside `entry-semantics-violations.violations[]` found that the supplied `DIRECT_JOURNAL` resolves to one published typed business event and therefore must not be admitted through the raw path | `code`, `field`, `message`, `category`, `repair` |
| `raw-journal-bundles-operational-events` | one item inside `entry-semantics-violations.violations[]` found that the supplied `DIRECT_JOURNAL` bundles multiple operational business events into one posting | `code`, `field`, `message`, `category`, `repair` |
| `opening-window-account-not-permitted` | one item inside `entry-semantics-violations.violations[]` found that an `OPENING_POSITION` request referenced one account that the adoption opening window does not permit | `code`, `field`, `message`, `category`, `repair` |
| `opening-inventory-requires-quantity` | one item inside `entry-semantics-violations.violations[]` found that an `OPENING_POSITION` request omitted `openingBalances[].quantity` for an inventory account and therefore cannot establish exact inventory on hand | `code`, `field`, `message`, `category`, `repair` |
| `opening-quantity-requires-inventory` | one item inside `entry-semantics-violations.violations[]` found that a non-inventory opening balance carried `openingBalances[].quantity` | `code`, `field`, `message`, `category`, `repair` |
| `inventory-opening-must-be-first-movement` | one item inside `entry-semantics-violations.violations[]` found that an inventory opening followed an existing durable movement for that account | `code`, `field`, `message`, `category`, `repair` |
| `inventory-opening-carrying-cost-invalid` | one item inside `entry-semantics-violations.violations[]` found that an inventory opening did not supply a positive carrying cost consistent with its quantity | `code`, `field`, `message`, `category`, `repair` |
| `idempotency-key-conflict` | the selected book already contains the same `idempotencyKey`, but it is bound to a different committed posting request | none |
| `posting-effective-date-in-future` | the selected posting request uses an `effectiveDate` later than the current UTC date | `attemptedEffectiveDate`, `currentUtcDate` |
| `book-functional-currency-mismatch` | the selected posting request uses one journal-line or typed-entry currency that does not match the selected book functional currency | `functionalCurrency`, `attemptedCurrency` |
| `closed-period-violation` | the selected posting request uses an effective date that falls inside one transferred reporting period | `transferredThroughEffectiveDate`, `attemptedEffectiveDate` |
| `opening-position-window-closed` | `OPENING_POSITION` was submitted after the book already contains its first committed posting | `firstBlockingPostingKind`, `firstBlockingEffectiveDate` |
| `opening-position-touches-nominal-account` | `OPENING_POSITION` touched a revenue or expense account | `accountCode`, `accountType` |
| `reserved-result-classification` | the selected posting request touched one account whose `financialPositionLineClassification` is reserved for reporting-period closes | `accountCode`, `financialPositionLineClassification` |
| `reversal-target-not-found` | `reversal.priorPostingId` does not exist in the selected book | `priorPostingId` |
| `reversal-target-is-reversal` | `reversal.priorPostingId` already identifies one reversal posting | `priorPostingId` |
| `reversal-already-exists` | the target posting already has a full reversal | `priorPostingId` |
| `reversal-does-not-negate-target` | a reversal request does not negate the target posting exactly | `priorPostingId` |

`unknown-account` and `posting-not-found` are query-side rejections. `account-state-violations` is the posting-side rejection for account-registry failures, and its ordered `details.violations[]` payload owns the machine-readable per-issue repair guidance while the top-level machine `message` stays a stable count-summary and no top-level repair `hint` is emitted.
`entry-semantics-violations` follows the same rule for semantic contradictions inside one accepted request shape. The paired `--output text` surface renders one `Summary` header plus one `Issue N | <code>` section per violation for both nested repairable posting families.
Checked-in machine and operator examples live at [examples/account-state-violations-response.json](./examples/account-state-violations-response.json), [examples/account-state-violations-text.txt](./examples/account-state-violations-text.txt), [examples/entry-semantics-violations-response.json](./examples/entry-semantics-violations-response.json), and [examples/entry-semantics-violations-text.txt](./examples/entry-semantics-violations-text.txt).

Malformed JSON, wrong field types, missing required fields, invalid date/time text, and domain-validation failures return `status: "error"` with code `invalid-request`.
Argument and parsing failures may also carry a `hint` and `argument` field so a caller can correct the invocation mechanically.
For `execute-plan`, a complete credential tuple paired with a decoded query-only or assertion-only
plan instead returns `status: "error"`, code `attestation-credentials-not-allowed`, and exit `1`.
The request is decoded to establish that pairing, but FinGrind opens no credential and executes no
plan step. A partial tuple remains the distinct parser-level `invalid-request` case.
Journal-entry validation now reports every detected journal grammar violation in one deterministic `invalid-request` response and publishes the full ordered set under `details.violations[]`, so callers can repair the whole request before retrying without scraping prose.

Deterministic CLI-side non-success examples are also checked in:
- [examples/invalid-page-cursor-error.json](./examples/invalid-page-cursor-error.json)
- [examples/protected-book-verification-failed-error.json](./examples/protected-book-verification-failed-error.json)
- [examples/unsupported-book-format-version-error.json](./examples/unsupported-book-format-version-error.json)
- [examples/pair-targets-conflict-rejection.json](./examples/pair-targets-conflict-rejection.json)
- [examples/source-artifact-identity-duplicated-rejection.json](./examples/source-artifact-identity-duplicated-rejection.json)
- [examples/source-artifact-identity-changed-rejection.json](./examples/source-artifact-identity-changed-rejection.json)
- [examples/maintenance-recovery-pending-error.json](./examples/maintenance-recovery-pending-error.json)
- [examples/publication-transaction-incomplete-error.json](./examples/publication-transaction-incomplete-error.json)
- [examples/protected-book-pair-publication-evidence-blocked-error.json](./examples/protected-book-pair-publication-evidence-blocked-error.json)
- [examples/open-book-preparation-artifacts-retained-error.json](./examples/open-book-preparation-artifacts-retained-error.json)
- [examples/open-book-publication-progress-error.json](./examples/open-book-publication-progress-error.json)
- [examples/interactive-prompt-unavailable-error.txt](./examples/interactive-prompt-unavailable-error.txt)

When you want those malformed-input or deterministic non-success examples from the live CLI, rerun the
same command: the diagnostics envelope is JSON even when the selected success mode is text.

## Protected-Book Pair Target Admission

The two final members of a protected-book maintenance pair establish distinct filesystem identities
after lifecycle source validation and final-parent admission. A previously admitted eligible missing
parent may remain. Initial admission creates no final target, retained lease-control file, stage,
capability witness, reservation, claim, or pair-recovery-evidence artifact.

### `pair-targets-conflict`

`pair-targets-conflict` is a `rejected`, `precondition` response with exit code `2`. It occurs
when both existing final targets establish one filesystem identity through `Files.isSameFile`, or
when two absent leaves in one physical parent have exactly the same raw leaf name or collide after
canonical Unicode decomposition plus root-locale case mapping. Its
`details.bookTarget` and `details.generatedSecretTarget` retain normalized absolute submitted
spellings; they do not claim to be canonical physical paths. If those strings differ, top-level
`path` is the book spelling and `relatedPaths` retains the generated-secret spelling, even when the
filesystem proved they identify one object. Choose a generated-secret target with a distinct
filesystem identity, then rerun the maintenance command.

The checked-in [pair-targets conflict example](./examples/pair-targets-conflict-rejection.json)
shows the complete JSON envelope.

## Duplicate Maintenance Source Artifact

`artifact-path-invalid` with
`details.pathFailure: "source-artifact-identity-duplicated"` is a `rejected`, `precondition`
response with exit code `6`. Before target admission, staging, or publication, FinGrind requires
the complete file-backed source set to contain distinct physical files: the live book or backup
source and every selected key-file source. A later source role that resolves to an earlier source's
object, including through a hard link, receives this failure. The response identifies the later
role and canonical path in `details.artifactRole` and `details.artifactPath`.

Choose an independent source artifact and rerun. Do not treat a hard-link alias as separate key or
book custody; it cannot establish independent source authority. The checked-in
[duplicate-source rejection example](./examples/source-artifact-identity-duplicated-rejection.json)
shows the complete JSON envelope.

## Changed Maintenance Source Artifact

`artifact-path-invalid` with
`details.pathFailure: "source-artifact-identity-changed"` is a `rejected`, `precondition`
response with exit code `6`. FinGrind acquires the complete source set before it admits a target,
then revalidates every selected source against the exact physical identity it locked. The response
names the source role and its canonical path in `details.artifactRole` and
`details.artifactPath`; it establishes neither a target reservation nor a new publication.

Keep every selected source stable. Restore the trustworthy intended source if it changed, then
rerun the complete maintenance command; do not treat a substituted or replacement file as the
original source authority. The checked-in
[changed-source rejection example](./examples/source-artifact-identity-changed-rejection.json)
shows the complete JSON envelope.

## Maintenance Recovery Pending

`maintenance-recovery-pending` is a `rejected`, `precondition` maintenance-state conflict with
exit code `7`. It occurs before `backup-book`, `restore-book`, or `rekey-book` starts a stage,
probe, reservation, or final mutation when verified retained pair evidence establishes an
incomplete maintenance workflow that belongs to a different request. The retained owner record
binds the original operation, both final targets, and its operation-specific recovery facts; it is
not an interchangeable target-pair reservation. Backup and restore retain immutable source facts.
Rekey deliberately does not retain a pre-rekey source identity or head because a completed rekey
replaces both; its recovery instead proves the final signed rekey state using the final generated
key pair.
JSON always carries non-null
`details.recoveryOperation`, `details.bookTarget`, and `details.generatedSecretTarget`; text
labels them `Recovery operation`, `Book target`, and `Generated secret target`. The operation is
the canonical wire name and targets are canonical absolute paths. Top-level `argument` is `null`;
`path` is the book target and `relatedPaths` contains the generated-secret target.

Resume that named operation with its admitted operation-specific inputs: backup and restore use
their original verified sources, while rekey uses its exact final pair and proves the final signed
rekey state. The response details are a recovery locator, not enough information to reconstruct a
source, backup acknowledgement, or credential. Preserve the owner
record and every retained artifact; do not rename, overwrite, delete, recreate, or repurpose them.
Malformed, legacy, or internally inconsistent evidence cannot establish a verified original
workflow. It fails closed as the exit-`4`
`protected-book-pair-publication-evidence-blocked` error, never
`maintenance-recovery-pending`, and must be independently investigated rather than adopted.

## Publication Transaction Incomplete

`publication-transaction-incomplete` is an exit-`4`, `precondition` error for a publication
transaction that cannot establish its complete outcome or cleanup state. Its details contain
exactly `candidateArtifact` and ID-only `publicationTransaction.{id,state,commitOutcome,cleanupOutcome}`.
The candidate is the only final path the error proves; no private stage, digest, or cleanup
capability is exposed.

Preserve the reported candidate and rerun only the exact same operation with its complete original
inputs. Never rename, overwrite, delete, recreate, or reuse any final member; do not start a fresh
pair. The checked-in
[publication-transaction-incomplete example](./examples/publication-transaction-incomplete-error.json)
shows the full envelope.

## Protected-Book Pair Evidence Blocked

`protected-book-pair-publication-evidence-blocked` is the distinct exit-`4`, `precondition` error
when legacy, malformed, or internally inconsistent sidecar evidence cannot establish a safe
final-member publication state. It is not recoverable and it does not name an operation to rerun.
Its `details.pairPublication` has both final members with `state: "unestablished"` and reports no
private stage. Top-level `path` and `relatedPaths` retain the canonical book and generated-secret
paths only.

Preserve the evidence and both final members exactly as found. Do not rerun, rename, overwrite,
delete, recreate, or reuse either final member or the evidence. Investigate the retained evidence
independently before any further action. The checked-in [evidence-blocked example](./examples/protected-book-pair-publication-evidence-blocked-error.json)
shows the full envelope.

## Existing Artifact Is Not Owner-Only

`artifact-path-invalid` with `details.pathFailure: "target-owner-only-required"` is a
`rejected`, `precondition` response with exit code `6`. It means an existing protected-book
source or FinGrind-owned recovery artifact that the operation must inspect is not confined to its
owner under the host's supported secure-permission model. The response identifies its role and
canonical path in `details.artifactRole` and `details.artifactPath`. A caller-owned ordinary leaf
selected as a no-clobber output is not inspected as a FinGrind artifact; it receives the operation's
exact occupied-target rejection instead. Completed pair-publication records and their retained
stage-owner records remain immutable historical evidence; they do not turn an unrelated later
occupied output into an evidence-blocked recovery response.

Correct that existing artifact's ownership and permissions outside FinGrind, then rerun the
maintenance command. Do not replace it with a symlink or a different object merely to change its
permissions; a changed artifact must pass the normal path and verification checks again.
