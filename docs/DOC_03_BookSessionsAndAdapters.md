---
afad: "5.0.1"
version: "0.62.0"
domain: ADAPTERS
updated: "2026-07-30"
route:
  keywords: [fingrind, adapters, seams, sqlite, sqlite3mc, session, posting-fact, ffm, key-file, runtime, classifier, ledger-plan, plan-transaction, plan-child, source-artifact-identity-duplicated, source-artifact-identity-changed, pair-targets-conflict, target-owner-only-required, protected-book-pair-publication-evidence-blocked]
  questions: ["how are committed facts stored in fingrind", "what are the storage seams in fingrind", "where is the ledger-plan execution store documented", "what does the sqlite adapter do in fingrind", "how does fingrind describe its sqlite runtime", "how does the sqlite adapter establish protected-book pair target identity"]
---

# Book Session And Adapter API Reference

This file documents the public seam and adapter layer around the contract/executor core: explicit
book-access tuples, local bookkeeping records that cross session boundaries, executor-owned
sessions, and the durable SQLite runtime and store.

## `BookAccess` And `BookAccess.PassphraseSource`

`BookAccess` is the explicit protected-book access tuple passed into the SQLite adapter.

```java
public record BookAccess(Path bookFilePath, PassphraseSource passphraseSource)
```

- Purpose: keep the normalized book path and exactly one passphrase-source selection coupled as one
  value
- `BookAccess.PassphraseSource` variants: key file, standard input, and interactive prompt
- Contract: there is no plaintext CLI argument or environment-variable passphrase transport

## `PostingFact`

`PostingFact` is the canonical committed fact carried across FinGrind's adapter seam.

```java
public record PostingFact(
    PostingId postingId,
    JournalEntry journalEntry,
    PostingLineage postingLineage,
    PostingKind postingKind,
    PostingOriginKind postingOriginKind,
    AccountingEvidence evidence,
    CommittedProvenance provenance)
```

- Purpose: represent one committed posting independently of any concrete storage adapter
- Durable meaning: `postingKind` keeps the storage-family boundary, while `postingOriginKind`
  preserves the original published-entry origin after the write crosses into canonical postings
- Evidence: committed facts preserve the retained evidence bundle that justified the posting at
  acceptance time
- Surface: `reversalReference()` and `reversalReason()` delegate to the typed `PostingLineage`
- Validation: rejects `null` posting id, journal entry, posting lineage, posting kind,
  posting-origin kind, evidence, and provenance

## `CommittedPosting`

`CommittedPosting` is the local bookkeeping committed-posting record used inside executor and
storage seams before the public `PostingFact` projection is rendered.

```java
public record CommittedPosting(
    PostingId postingId,
    JournalEntry journalEntry,
    PostingLineageModel postingLineage,
    PostingKind postingKind,
    PostingOriginKind postingOriginKind,
    AccountingEvidence evidence,
    CommittedProvenance provenance)
```

- Purpose: preserve bookkeeping-local lineage typing and provenance while one write is being
  stored, queried, or journaled
- Added fact: `postingKind` keeps standard, opening-balance, interim-result-sweep, and
  fiscal-year-close postings distinct inside local bookkeeping seams, while
  `postingOriginKind` preserves which published entry family produced one committed posting and
  `evidence` keeps the retained justification bundle attached
- Boundary: projected to `PostingFact` only at the public published-language edge

## `AccountDeclaration`, `AccountDeclarationOutcome`, `BookOpeningOutcome`, And `RegisteredAccount`

These local bookkeeping administration types carry one translated account declaration, its outcome,
book-opening results, and one registry snapshot across session and store seams.

```java
public record AccountDeclaration(
    AccountCode accountCode,
    AccountName accountName,
    AccountType accountType,
    AccountTaxonomy accountTaxonomy,
    @Nullable UnitOfMeasure unitOfMeasure)
public sealed interface AccountDeclarationOutcome
public sealed interface BookOpeningOutcome
public record RegisteredAccount(...)
```

- `AccountDeclaration`: bookkeeping-local declaration request after the public command crosses the
  translator boundary, including the inventory account's owned unit token and exact quantity
  scale when the declared taxonomy is `INVENTORY`
- `AccountDeclarationOutcome`: closed family of accepted-versus-rejected declaration outcomes
- `BookOpeningOutcome`: closed family of accepted-versus-rejected initialization outcomes
- `RegisteredAccount`: local registry snapshot that owns redeclare/reactivate semantics and
  preserves declared-at time while keeping `accountType`, `accountTaxonomy`, and the inventory
  `unitOfMeasure` immutable after the first declaration while deriving `normalBalance()` from
  `AccountTaxonomyDoctrine`

## `AccountDeclarationDecision`, `AccountAmendmentDecision`, And `AccountRetirementDecision`

These sealed local decision families distinguish an admissible account-registry state transition
from later direct or plan-bound persistence and attestation outcomes.

```java
public sealed interface AccountDeclarationDecision
public sealed interface AccountAmendmentDecision
public sealed interface AccountRetirementDecision
```

- `AccountDeclarationDecision`: `Declared`, `Reactivated`, and `Renamed` carry the candidate
  account snapshot; `Unchanged` carries the already matching snapshot; `Rejected` carries the
  local administration rejection.
- `AccountAmendmentDecision`: `Amended` carries the candidate replacement; `Unchanged` and
  `Rejected` preserve the corresponding pre-persistence alternatives.
- `AccountRetirementDecision`: `Retired` carries the candidate inactive snapshot; `Unchanged`
  and `Rejected` preserve the corresponding pre-persistence alternatives.
- Boundary: these decisions carry no append result. A direct mutation or aggregate plan child
  maps a successful decision to its own durable outcome only after persistence succeeds.

## `PlanAccountDeclarationOutcome` And `PlanTaxRegistrationMutationOutcome`

These local result families are available only while one aggregate ledger plan is executing. They
keep a plan child's deferred-attestation disposition distinct from ordinary direct-command results.

```java
public sealed interface PlanAccountDeclarationOutcome
public sealed interface PlanTaxRegistrationMutationOutcome
```

- `PlanAccountDeclarationOutcome`: `Declared`, `Reactivated`, and `Renamed` retain the durable
  account result for a completed plan child; `Unchanged` records a no-op; `Rejected` carries the
  local administration rejection before any child mutation persists.
- `PlanTaxRegistrationMutationOutcome`: `Declared` and `Updated` retain the durable registration
  result for a completed plan child; `Unchanged` records a no-op; `Rejected` carries the local tax
  declaration rejection before any child mutation persists.
- Boundary: these results never publish a child operation or independent `AttestationCommit`.
  Only the enclosing `execute-plan` may publish its single aggregate commitment after all accepted
  child mutations have persisted.

## `TaxRegistrationMutationOutcome`

`TaxRegistrationMutationOutcome` is the local direct-command result family for a durable tax
registration declaration or replacement.

```java
public sealed interface TaxRegistrationMutationOutcome
```

- `Declared` and `Updated` carry the durable registration plus the exact newly appended
  `AttestationAppendOutcome.Appended` verification.
- `Unchanged` carries an already matching registration and appends no operation.
- `Rejected` carries the deterministic tax-declaration rejection before persistence.
- Boundary: this direct-command outcome is distinct from `PlanTaxRegistrationMutationOutcome`,
  whose accepted children defer attestation to their enclosing aggregate plan.

## `BookAuditEvent` And `BookAuditEventKind`

These local bookkeeping types own the durable append-only audit stream written beside account and
posting facts.

```java
public record BookAuditEvent(...)
public enum BookAuditEventKind implements WireValue
```

- `BookAuditEvent`: one validated durable audit fact carrying event time plus the local account or
  posting identity when the event kind requires it
- `BookAuditEventKind`: the closed durable audit vocabulary: `BOOK_OPENED`,
  `ACCOUNT_DECLARED`, `ACCOUNT_REACTIVATED`, `ACCOUNT_RENAMED`, `ACCOUNT_AMENDED`,
  `ACCOUNT_RETIRED`, `POSTING_COMMITTED`, `POSTING_REVERSED`, `INTERIM_RESULT_SWEPT`, and
  `FISCAL_YEAR_CLOSED`
- Storage boundary: SQLite persists these rows in `audit_event` and rejects direct update/delete
  mutation through append-only triggers

## `BookLifecycleReader`, `BookAdministrationStore`, `AccountLookupStore`, `AccountCatalogStore`, `PostingLookupStore`, `PostingHistoryStore`, `PostingRangeStore`, `BookkeepingReportStore`, `BookkeepingReadStore`, `PostingCommitStore`, `ReportingPeriodCloseStore`, And `LedgerPlanExecutionStore`

These exported `executor.spi` interfaces are the explicit store-port set for one selected book
boundary.

```java
public interface BookLifecycleReader
public interface BookAdministrationStore
public interface AccountLookupStore
public interface AccountCatalogStore
public interface PostingLookupStore
public interface PostingHistoryStore
public interface PostingRangeStore
public interface BookkeepingReportStore
public interface BookkeepingReadStore
public interface PostingCommitStore
public interface ReportingPeriodCloseStore
public interface LedgerPlanExecutionStore
```

- `BookLifecycleReader`: local lifecycle inspection without mutation
- `BookAdministrationStore`: book opening plus account-registry declaration/reactivation writes
- `AccountLookupStore`: one-account and batched account lookup by semantic account code
- `AccountCatalogStore`: full ordered registry reads plus paginated account-catalog views
- `PostingLookupStore`: idempotency, posting-id, and reversal lookup for committed facts
- `PostingHistoryStore`: paginated posting-history reads
- `PostingRangeStore`: effective-date posting streams plus earliest-posting and close-horizon facts
- `BookkeepingReportStore`: grouped totals plus account-balance, trial-balance, account-ledger,
  period-summary, and statement local report views
- `BookkeepingReadStore`: the composite read-side seam that combines lifecycle, lookup, history,
  and report ports for application read services
- `PostingCommitStore`: durable posting commit boundary
- `ReportingPeriodCloseStore`: durable interim-result-sweep and fiscal-year-close commit boundary
- Purpose: keep lifecycle, administration, lookup, history, reporting, durable commit, and
  transaction ownership on auditable narrow seams.
- Lifecycle: the outer workflow or adapter owns `close()`, not these executor seams

## `LedgerPlanReadStore`, `LedgerPlanReadOnlyTransaction`, And `LedgerPlanReadOnlyExecutionStore`

Credential-free and signed plan execution share reads but deliberately do not share transaction
authority.

```java
public interface LedgerPlanReadStore
public interface LedgerPlanReadOnlyTransaction
public interface LedgerPlanReadOnlyExecutionStore
```

- `LedgerPlanReadStore`: shared read, verified-provenance, and preflight capability without any
  plan transaction begin operation.
- `LedgerPlanReadOnlyTransaction`: a stable snapshot transaction with source-plan step admission,
  commit, and rollback only.
- `LedgerPlanReadOnlyExecutionStore`: the credential-free composition that cannot obtain a
  child-write port, final-only authorizer, or aggregate append operation.

## `LedgerPlanTransaction`, `LedgerPlanMutationStore`, And `LedgerPlanExecutionStore`

Ledger-plan execution is a deliberately composed store boundary, rather than a caller-assembled
set of write ports.

```java
public interface LedgerPlanTransaction
public interface PlanAccountDeclarationStore
public interface PlanTaxRegistrationStore
public interface PlanPostingCommitStore
public interface LedgerPlanMutationStore
    extends PlanAccountDeclarationStore, PlanTaxRegistrationStore, PlanPostingCommitStore
public sealed interface PlanPostingCommitResult
public interface LedgerPlanExecutionStore
    extends LedgerPlanReadStore, LedgerPlanTransaction, LedgerPlanMutationStore
```

- `LedgerPlanTransaction`: the composed transaction SPI. `beginLedgerPlanTransaction` binds the
  immutable plan identity and final-only `AttestationPlanOperationAuthorizer`; it controls step
  admission, completed-child observation, the one final aggregate append, commit, and rollback.
  Its aggregate append requires the exact authority bound at begin and rejects both a read-only
  plan and a duplicate append. It is not a separately injected execution entry point.
- `PlanAccountDeclarationStore`, `PlanTaxRegistrationStore`, and `PlanPostingCommitStore`: the
  three capability-confined child-write ports. Each accepts the final-only plan authorizer, persists
  only its matching child family, and defers attestation to the enclosing plan.
- `LedgerPlanMutationStore`: the composite of those three child-write ports. It is not the
  ordinary direct-mutation service surface and is not an independently supplied plan dependency.
- `PlanPostingCommitResult`: `Deferred` means a newly persisted posting whose child evidence is
  now eligible for the aggregate; `Replayed` means an idempotent posting replay that adds neither
  a posting nor a child; `Rejected` means the posting was refused before persistence.
- `LedgerPlanExecutionStore`: the one bound protected-book capability that composes those lower
  SPIs with plan reads, posting validation, and verified posting-commitment lookup. A plan's reads,
  plan-specific children, post-persistence child tracking, final aggregate attestation, and
  commit-or-rollback lifecycle therefore share one protected-book session and transaction.
- Coordinator boundary: `BookWorkflowExecutionService` receives only
  `LedgerPlanExecutionStore`; it orchestrates child steps, observes whether the bound store has
  durably completed children, and requests at most one aggregate operation through the bound
  final-only authority. The bound child-write capability records its durable child evidence. No
  generic dependency bundle or split transaction seam exists for callers to combine unrelated
  stores into an aggregate operation.
- Read-only boundary: `BookWorkflowReadOnlyExecutionService` receives only
  `LedgerPlanReadOnlyExecutionStore`. Its transaction is physically incapable of child writes,
  aggregate append, or attestation authorization; a plan that declares a mutation is rejected
  before it executes any step. The two store capabilities are intentionally disjoint.

## `AttestationPostingCommitmentStore`

`AttestationPostingCommitmentStore` is the separate read-side attestation port used to project an
authenticated operation reference for requested postings.

```java
public interface AttestationPostingCommitmentStore
```

- Surface: `attestationCommitsFor(Set<PostingId>)` returns each requested posting's verified
  `AttestationCommit`, when its attested operation contains that posting effect
- Integrity: an adapter verifies the complete immutable attestation chain before returning any
  commitment; invalid, incomplete, or ambiguous evidence is a protected-book verification failure
- Boundary: this is deliberately not part of `BookkeepingReadStore`; ordinary bookkeeping lookup
  does not authenticate evidence, and combining the two would make every bookkeeping reader claim
  an attestation capability it does not have

## `AttestationPostingCommitmentProjection`

`AttestationPostingCommitmentProjection` bounds authenticated posting-commitment results to the
exact posting selection requested by one caller.

```java
public final class AttestationPostingCommitmentProjection
```

- Surface: `resolve(AttestationPostingCommitmentStore, Set<PostingId>)` returns the immutable
  requested subset of verified `AttestationCommit` values
- Integrity: it rejects a store result that includes any posting outside the requested selection;
  callers therefore cannot accidentally project authenticated evidence onto an unrelated posting
- Boundary: direct posting queries and `execute-plan` posting-query steps share this projection,
  so both paths publish the same cryptographically verified linkage

## `AttestationCommitProjection`

`AttestationCommitProjection` converts one verified durable append into the public attestation
identity returned by the command that created it.

```java
public final class AttestationCommitProjection
```

- Surface: `fromVerifiedAppend(AttestationVerification)` returns the verification's exact operation
  order and lowercase-hex operation head as `AttestationCommit`.
- Integrity: callers must use the verification returned by the completed append, never reread the
  current chain head; a later append could otherwise overstate which operation this command made.
- Boundary: bookkeeping, maintenance, tax, and registry write paths share this projection before
  their public result translators and CLI renderers publish the commitment.

## `AccountCurrencyTotals`

`AccountCurrencyTotals` is the executor-owned aggregate row used by statement reads and close
generation.

```java
public record AccountCurrencyTotals(
    RegisteredAccount account,
    CurrencyUnit currencyUnit,
    long debitTotalMinor,
    long creditTotalMinor)
```

- Purpose: move per-account, per-currency exact totals across the
  `BookkeepingReportStore.accountTotals(...)` seam without materializing full posting streams for
  statement computation
- Surface: `balance()` derives the canonical `CurrencyBalance` projection when local reporting
  callers need one net balance view
- Boundary: stores compute these totals; statement and close services consume them as local
  aggregate truth

## `PostingValidationStore`

`PostingValidationStore` is the minimal lookup and lifecycle seam shared by preflight and
transactional commit validation.

```java
public interface PostingValidationStore
```

- Composition: `BookLifecycleReader`, `AccountLookupStore`, `PostingLookupStore`, and
  `PostingRangeStore`
- Purpose: let application preflight and commit-time validation reuse one authoritative
  initialized-book lookup contract

## `InventoryMovementRecord`, `InventoryValuationMovementRecord`, `InventoryAccountState`, `InventoryMovementLookupStore`, `InventoryValuationStore`, `InventoryStateLookupStore`, `SqliteResolvedInventoryCostingReader`, `InventoryMovementPrecedesAccountHorizonViolation`, `InventoryQuantityBelowZeroViolation`, And `InventoryWriteDownExceedsCarryingCostViolation`

These local inventory-state models and lookup seams support executor-owned weighted-average
admission before durable commit.

```java
public record InventoryMovementRecord(...)
public record InventoryValuationMovementRecord(...)
public record InventoryAccountState(...)
public interface InventoryMovementLookupStore
public interface InventoryValuationStore
public interface InventoryStateLookupStore
final class SqliteResolvedInventoryCostingReader
public record InventoryMovementPrecedesAccountHorizonViolation(...)
public record InventoryQuantityBelowZeroViolation(...)
public record InventoryWriteDownExceedsCarryingCostViolation(...)
```

- `InventoryMovementRecord`: one exact per-account inventory movement carrying effective date,
  movement kind, quantity delta, and carrying-cost delta
- `InventoryAccountState`: one exact per-account on-hand pool plus the last accepted movement date
  that defines the current effective-date horizon
- `InventoryMovementLookupStore`: loads the committed inventory movements linked to one posting so
  reversal and verification can replay durable inventory history
- `InventoryValuationMovementRecord`: carries the store-owned account sequence and source posting
  identity required to replay one inventory account in canonical `(effective_date, account_sequence)` order
- `InventoryValuationStore`: loads those ordered durable ledger facts through an optional inclusive
  inventory-valuation cutoff
- `InventoryStateLookupStore`: loads the current exact inventory pool for one inventory account
- `SqliteResolvedInventoryCostingReader`: reconstructs a costed sale's exact cost of sales,
  relieved quantity, and informational rounded moving-average unit-cost projection from the sale's
  disposal movement plus the preceding canonical inventory replay order; the rounded projection is
  derived at read time and is never persisted as costing truth
- `InventoryMovementPrecedesAccountHorizonViolation`,
  `InventoryQuantityBelowZeroViolation`, and
  `InventoryWriteDownExceedsCarryingCostViolation`: local account-state violations that the
  executor later translates into the published inventory account-state detail family

## `BookLifecycleInspection` And `BookInspectionPublishedLanguageTranslator`

`BookLifecycleInspection` is the executor-owned local lifecycle snapshot, and
`BookInspectionPublishedLanguageTranslator` is the exported boundary translator that projects it
into the published runtime/discovery contract.

```java
public sealed interface BookLifecycleInspection
public final class BookInspectionPublishedLanguageTranslator
```

- `BookLifecycleInspection`: local lifecycle/compatibility family with `Missing`, `Existing`, and
  `Initialized` variants plus the local `Status` vocabulary that drives read gating and workflow
  inspect steps; the type itself owns `allowsInitializedWorkflow()` so initialized-book
  admissibility comes from one canonical local state family
- `BookInspectionPublishedLanguageTranslator`: projects this local inspection family into the
  public `BookInspection` contract

## `BookkeepingReadService`, `BookkeepingInventoryReadService`, And `BookkeepingLookupOutcome`

`BookkeepingReadService` owns local bookkeeping inspection, lookup, and general reporting
semantics; `BookkeepingInventoryReadService` owns lifecycle-gated inventory-ledger valuation; and
`BookkeepingLookupOutcome` preserves lifecycle rejection, ordinary absence, and presence distinctly
for internal callers.

```java
public final class BookkeepingReadService
public final class BookkeepingInventoryReadService
public sealed interface BookkeepingLookupOutcome<T>
```

- `BookkeepingReadService`: keeps local account, posting, and financial-statement behavior inside
  the bookkeeping context before any public DTO or public query-rejection family is projected
- `BookkeepingInventoryReadService`: owns point-in-time inventory valuation and delegates exact
  canonical movement replay to the reporting service without widening the general read-service seam
- `BookkeepingLookupOutcome`: `Found`, `Missing`, and `Rejected` variants keep “book not
  initialized” distinct from ordinary account/posting absence in internal workflow and assertion
  helpers

## `BookkeepingQueryRejection`

`BookkeepingQueryRejection` is the local bookkeeping refusal family for query and report commands.

```java
public sealed interface BookkeepingQueryRejection
```

- Variants: `BookNotInitialized`, `UnknownAccount`, `PostingNotFound`
- Purpose: keep local read/report refusals inside the bookkeeping context until
  `BookkeepingReadPagePublishedLanguageTranslator`,
  `BookkeepingReadReportPublishedLanguageTranslator`, or
  `BookkeepingReadStatementPublishedLanguageTranslator` projects them into public
  `BookQueryRejection`

## `ProtectedBookAccess`

`ProtectedBookAccess` is the local protected-book maintenance access tuple.

```java
public record ProtectedBookAccess(...)
```

- Purpose: keep one normalized book path plus one passphrase-source requirement inside the local
  maintenance workflow instead of shaping the store seam around public runtime DTOs
- Boundary: `fromPublished(...)` and `toPublished()` are the only translators between this local
  type and the public `BookAccess` contract

## `MaintenanceDecision` And `MaintenanceFailure`

These local maintenance support types keep accepted-versus-failed workflow outcomes separate from
the public `ContractDecision` surface.

```java
public sealed interface MaintenanceDecision<T>
public record MaintenanceFailure(...)
```

- `MaintenanceDecision`: local accepted-or-failed decision type used by the maintenance workflow
  and store seam before public projection
- `MaintenanceFailure`: local runtime failure value that isolates protected-book maintenance from
  the public contract failure envelope until the published-language adapter translates it outward

## `ProtectedBookBackupOutcome`, `ProtectedBookRekeyOutcome`, And `ProtectedBookRestoreOutcome`

These local maintenance result families keep backup, restore, and rekey outcomes inside the
maintenance context until the published-language translator projects them into public contract types.

```java
public sealed interface ProtectedBookBackupOutcome
public sealed interface ProtectedBookRekeyOutcome
public sealed interface ProtectedBookRestoreOutcome
```

- `ProtectedBookBackupOutcome`: accepted or rejected result for verified encrypted backup export
- `ProtectedBookRekeyOutcome`: accepted or rejected result for staged rekey publication under one
  newly generated key file
- `ProtectedBookRestoreOutcome`: accepted or rejected result for verified backup restore
- Boundary: each local outcome carries local `Path` values and local maintenance rejections; the
  published JSON contract preserves their canonical absolute paths

## `ProtectedBookMaintenanceArtifactRole`, `ProtectedBookMaintenancePathFailure`, `ProtectedBookMaintenanceRejection`, And `ProtectedBookMaintenanceRejectionException`

These local maintenance types own protected-book maintenance semantics, deterministic refusals, and
artifact-role vocabulary behind the public maintenance adapter.

```java
public enum ProtectedBookMaintenanceArtifactRole
public enum ProtectedBookMaintenancePathFailure
public sealed interface ProtectedBookMaintenanceRejection
public final class ProtectedBookMaintenanceRejectionException
```

- `ProtectedBookMaintenanceArtifactRole`: local role vocabulary for live-book,
  live-book-key-source, backup-source, backup-key-source, backup-target, backup-key-target,
  restored-target, and new-book-key-target verification and busy-lease outcomes
- `ProtectedBookMaintenancePathFailure`: local typed path-failure vocabulary:
  `MISSING_PARENT_DIRECTORY`, `PARENT_PATH_COLLISION`, `PARENT_OWNER_ACCESS_REQUIRED`,
  `PARENT_OWNER_ONLY_REQUIRED`, `ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE`,
  `TARGET_OWNER_ONLY_REQUIRED`, `TARGET_IDENTITY_UNESTABLISHED`,
  `SOURCE_ARTIFACT_IDENTITY_DUPLICATED`,
  `UNSUPPORTED_SECURE_FILESYSTEM`,
  `ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED`,
  `ATOMIC_SECRET_PUBLICATION_UNSUPPORTED`, `ATOMIC_BOOK_PUBLICATION_UNSUPPORTED`, and
  `ATOMIC_BOOK_REPLACEMENT_UNSUPPORTED`. Its public wire mapping is owned by
  [Book Maintenance Contract Reference](./DOC_02_BookMaintenanceContracts.md#bookmaintenanceartifactrole-bookmaintenancepathfailure-bookmaintenanceverificationfailure-and-bookmaintenancerejection).
- `ProtectedBookMaintenanceRejection`: local deterministic refusal family for blocking artifacts,
  same-path restore, final-target identity conflict, busy artifacts, caller-controlled artifact-path
  failures, and verification failures
- `ProtectedBookMaintenanceRejectionException`: local short-circuit carrier that preserves one
  typed maintenance rejection across workflow orchestration without collapsing it into generic
  runtime failure handling

## `ProtectedBookAccess` And `ProtectedBookPassphraseSource`

`ProtectedBookAccess` plus `ProtectedBookPassphraseSource` are the local maintenance access
language. They keep path selection and passphrase-source semantics inside the maintenance context
and translate back to `BookAccess` only at the published edge.

```java
public record ProtectedBookAccess(...)
public sealed interface ProtectedBookPassphraseSource
```

- `ProtectedBookAccess`: local protected-book path plus one local passphrase-source value
- `ProtectedBookPassphraseSource`: local key-file, standard-input, and interactive-prompt family
- Boundary: the maintenance workflow and store SPI use these local types; the published service
  adapter performs the only contract translation

## `ProtectedBookMaintenancePublishedLanguageTranslator` And `ProtectedBookVerificationFailure`

These maintenance-boundary types keep protected-book verification local inside executor while
projecting only redacted, typed maintenance outcomes into the public contract.

```java
public final class ProtectedBookMaintenancePublishedLanguageTranslator
public enum ProtectedBookVerificationFailure
```

- `ProtectedBookVerificationFailure`: local verification vocabulary for missing, blank-SQLite,
  foreign-SQLite, incomplete-FinGrind, and generic protected-book verification failures
  discovered before backup, restore, or rekey maintenance is allowed to proceed
- `ProtectedBookMaintenancePublishedLanguageTranslator`: the only exported translator that may
  project local maintenance outcomes into `BackupBookResult`, `RestoreBookResult`,
  `RekeyBookResult`, and `BookMaintenanceRejection`
- Translation rule: the translator normalizes filesystem `Path` values into the stable public
  contract and converts local artifact-role and verification-failure vocabularies without leaking
  SQLite implementation detail. JSON preserves canonical absolute paths; text rendering redacts
  them at the final presentation boundary.

## `ProtectedBookLiveAccessPathFailures`

`ProtectedBookLiveAccessPathFailures` is the one local factory for caller-controlled live-book
and live-key path refusals. It maps each `ProtectedBookMaintenancePathFailure` to the precise
published `invalid-book-file-path` or `invalid-book-key-file` message and hint, so CLI and workflow
callers cannot independently paraphrase a security-sensitive path admission decision.

## `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView`

These exported `executor.bookkeeping` types are the local bookkeeping read model used by
`BookkeepingReadStore`, `BookkeepingReadService`, SQLite read helpers, and workflow/query
execution before any public report DTOs are projected.

```java
public record AccountRegistryCursor(...)
public record AccountRegistryQuery(...)
public record AccountRegistryPage(...)
public record PostingHistoryCursor(...)
public record PostingHistoryQuery(...)
public record PostingHistoryPage(...)
public record AccountBalanceCriteria(...)
public record AccountBalanceView(...)
public record TrialBalanceCriteria(...)
public record TrialBalanceRowView(...)
public record TrialBalanceView(...)
public record AccountLedgerCriteria(...)
public record AccountLedgerEntryView(...)
public record AccountLedgerView(...)
public record PeriodSummaryCriteria(...)
public record PeriodCurrencySummaryView(...)
public record PeriodAccountActivityView(...)
public record PeriodSummaryView(...)
```

- Purpose: keep pagination, balance criteria, report rows, and report views inside the local
  bookkeeping context

## `AccountLedgerCursor`

This local cursor is the executor-owned continuation boundary for an ascending account-ledger
page. It is translated to the public opaque cursor only at the published-language boundary.

```java
public record AccountLedgerCursor(
    LocalDate effectiveDate,
    Instant recordedAt,
    PostingId postingId)
```

- Purpose: preserve the deterministic `(effectiveDate, recordedAt, postingId)` keyset boundary
  between ledger pages without exposing a storage-specific representation
- Shared kernel: these local types reuse `core.EffectiveDateRange` and
  `core.CurrencyBalance` where the concept is genuinely common to public and local bookkeeping
  language, while public interaction limits such as paging remain protocol-owned in
  `contract.protocol.ProtocolInteractionLimits`
- Boundary: `BookkeepingReadPagePublishedLanguageTranslator` and
  `BookkeepingReadReportPublishedLanguageTranslator` are the only owners that map these types to
  `AccountPage`, `PostingPage`, `AccountBalanceSnapshot`, `TrialBalanceReport`,
  `AccountLedgerReport`, and `PeriodSummaryReport`

## `CashFlowStatementCriteria`, `CashFlowRowView`, `CashFlowSectionView`, And `CashFlowStatementView`

These exported `executor.bookkeeping` types are the local cash receipts/payments read model used
by `BookkeepingReadStore`, `BookkeepingReadService`, SQLite read helpers, and reporting
translators before any public cash-basis report DTOs are projected.

```java
public record CashFlowStatementCriteria(...)
public record CashFlowRowView(...)
public record CashFlowSectionView(...)
public record CashFlowStatementView(...)
```

- Purpose: keep cash-basis criteria, section rows, section totals, and articulated opening and
  closing cash totals inside the local bookkeeping context
- Boundary: `BookkeepingReadStatementPublishedLanguageTranslator` is the only owner that maps
  these types to `CashFlowStatementReport`

## `PostingDraft`, `PostingCommitResult`, `PostingIdGenerator`, And `StoredRequestPosting`

These exported `executor.spi` types keep durable posting commit explicit at the store seam.

```java
public record PostingDraft(...)
public sealed interface PostingCommitResult
public interface PostingIdGenerator
public record StoredRequestPosting(...)
```

- `PostingDraft`: commit-ready posting material that defers durable posting-id allocation until the
  store accepts the write
- Variants: `Committed`, `Rejected`
- `PostingCommitResult`: distinguishes accepted durable writes from ordinary domain rejections
  without throwing
- Boundary: `Rejected` carries local `BookkeepingPostingRejection` values until an outer
  application service translates them into public `PostingRejection`
- `PostingIdGenerator`: keeps posting-id allocation explicit and injectable at the durable commit
  boundary
- `StoredRequestPosting`: pairs one committed posting fact with its persisted `RequestFingerprint`
  so idempotent replay compares normalized semantics instead of raw request bytes

## SQLite Runtime And Session Views

The packaged-runtime metadata, SQLite failure taxonomy, and workflow-shaped session boundaries are
owned by [DOC_03_SqliteRuntimeAndSessions.md](./DOC_03_SqliteRuntimeAndSessions.md).

## Protected-Book Pair Publication SPI

The pair-publication decision, binding, recovery request, source identity, and staging outcome are
owned by [DOC_02_BookMaintenanceContracts.md](./DOC_02_BookMaintenanceContracts.md).

## `ProtectedBookMaintenanceService`, `ProtectedBookMaintenanceStore`, `ProtectedBookMaintenanceStore.WorkflowSourceMember`, `ProtectedBookMaintenanceStore.WorkflowSourceMembers`, `ProtectedBookMaintenanceStore.WorkflowScopeAcquisition`, `ProtectedBookMaintenanceStore.HeldWorkflowScope`, `ProtectedBookMaintenanceStore.WorkflowScopeBusy`, `StagedBackupPair`, `StagedRestoredBookPair`, And `SqliteProtectedBookMaintenanceStore`

Protected-book maintenance belongs to one executor-owned lifecycle boundary. Its base store SPI
owns artifact verification, leases, staging, and no-clobber pair publication; the attested
extension owns the signed operation-chain transaction and artifact-manifest verification.

```java
public final class ProtectedBookMaintenanceService
public interface ProtectedBookMaintenanceStore
public record ProtectedBookMaintenanceStore.WorkflowSourceMember(...)
public record ProtectedBookMaintenanceStore.WorkflowSourceMembers(...)
public sealed interface ProtectedBookMaintenanceStore.WorkflowScopeAcquisition
public non-sealed interface ProtectedBookMaintenanceStore.HeldWorkflowScope
public record ProtectedBookMaintenanceStore.WorkflowScopeBusy(...)
public interface StagedBackupPair
public interface StagedRestoredBookPair
public final class SqliteProtectedBookMaintenanceStore
```

- `ProtectedBookMaintenanceService`: opens the necessary signing session and adapts the attested
  backup, restore, and rekey workflow to published results
- `ProtectedBookMaintenanceStore`: narrow SPI for initialized-book verification, exclusive
  artifact leases, staged pair publication, and destination-admission doctrine. Its path-admission
  methods are intentionally non-interchangeable: `normalizeOptionalInspectionArtifact(...)` is
  only for an inspectable live-book state, `normalizeExistingSource(...)` is the mandatory
  lifecycle-source gate, and `normalizeFinalTarget(...)` is the only boundary that can admit a
  caller-selected output parent. `WorkflowSourceMembers` is the nonempty immutable, role-tagged
  set of every selected file-backed source: the live book or backup artifact and, when selected,
  its companion key file. It rejects duplicate normalized spellings; SQLite additionally rejects
  a later source role that resolves to the same physical object as an earlier role with the typed
  `source-artifact-identity-duplicated` path failure. `acquireWorkflowScope(...)`
  accepts that complete set exactly once with the two exact final targets; it cannot be widened
  later with a sibling artifact. `HeldWorkflowScope` keeps every source lease through verification
  and staging, transferring only target leases into an admitted pair publication. A
  `WorkflowScopeBusy` reports the exact blocked path and role rather than collapsing a key-source
  conflict into a live-book conflict.
  After holding every source lease, SQLite revalidates every source against its exact locked
  physical identity and repeats the uniqueness check before target admission. A replacement or
  substitution is the typed `source-artifact-identity-changed` path failure; the caller must
  restore the trustworthy intended source, keep every source stable, and rerun the complete
  maintenance operation.
- `StagedBackupPair`: staged backup publication that verifies the staged backup before final
  publication and retains its private artifacts when the workflow relinquishes authority
- `StagedRestoredBookPair`: staged restored-book publication that verifies the staged restored book
  already opens with the staged destination key file before final publication and retains its
  private artifacts when the workflow relinquishes authority
- `SqliteProtectedBookMaintenanceStore`: verifies protected-book artifacts through SQLite, rejects
  non-initialized and noncanonical sources, and performs staged filesystem/native work
- Boundary: maintenance doctrine lives above SQLite, while SQLite owns verification, staged
  publication, and the attested transaction implementation
- Path-admission boundary: every existing maintenance source and final target parent is
  validation-only. Before canonicalization, SQLite scans every lexical component from the root
  through the selected parent without following links and rejects any symbolic-link or
  non-directory component, including a direct-parent alias. A lifecycle mutation source must
  already be a regular non-symlink file before SQLite prepares any final-target parent;
  `inspect-book` instead retains an absent live book as a typed missing state, while attestation
  verification reports its own verification failure after admitting the same path. SQLite then
  proves the private owner-only, non-mutable ancestry and never permission- or ACL-repairs that
  caller-selected parent. Only an absent final-target parent may be created: SQLite preflights
  its creation ancestry, atomically creates it with POSIX `0700`, then postvalidates the canonical
  parent and full ancestry. A lifecycle source parent must already exist; ACL-only final-target
  creation fails closed with
  `atomic-owner-only-protocol-file-creation-unsupported`. It carries only
  `canonicalParent.resolve(fileName)` across leases, recovery records, and public machine paths.
- Pair-target identity boundary: SQLite establishes two existing final targets with
  `Files.isSameFile`. Two absent leaves in one physical parent with exact raw leaf equality or a
  collision after canonical Unicode decomposition plus root-locale case mapping are likewise
  `pair-targets-conflict`. Other distinct leaves remain valid when the filesystem admits them. An
  eligible missing parent may remain; the initial refusal creates no final target, retained
  lease-control file, stage, capability witness, reservation, claim, or pair-recovery-evidence
  artifact.
- Evidence-admission boundary: before a maintenance stage, probe, reservation, or final mutation,
  the store scans the full source-and-target workflow scope for retained pair evidence. The held
  scope includes every selected file-backed source, including a selected live-book or backup key
  file, even where the non-secret recovery record retains only its operation-level source identity.
  A verified owner record binds the original maintenance operation, source identity, canonical book and
  generated-secret targets, secret input identity, and the derived stages that it alone owns.
  A record belonging to another request returns the `maintenance-recovery-pending` `rejected`,
  `precondition`, exit-`7` conflict with non-null `recoveryOperation`, `bookTarget`, and
  `generatedSecretTarget` facts. Recovery reruns only that named operation with complete original
  source, target, and secret inputs. Its top-level `argument` is `null`; `path` is the book target
  and `relatedPaths` contains the generated-secret target. Evidence that cannot establish those
  facts safely fails closed as exit-`4` `protected-book-pair-publication-evidence-blocked`, never
  `maintenance-recovery-pending` or a recoverable uncertainty instruction.
- Coordination boundary: v4 directory-reservation controls retain exact private directory
  admission for final targets, while a private per-user v4 object-control namespace names each
  existing source's activity slots and maintenance exclusion from explicit physical identity.
  Thus a hard-link alias cannot bypass a live-book activity or maintenance lock. Retired v2/v3
  controls are never read as current protocol state and instead block safely. Cutover is manual:
  after an independently verified outage, archive each old private root and affected directory
  control as non-active private evidence; never delete, adopt, merge, or co-run it.
- Pair-recovery boundary: the final book and generated-secret targets are one operation-bound
  recovery unit. Their retained stages are immutable evidence, not disposable pre-final state.
  Neither the store nor a caller may rename, overwrite, delete, recreate, reuse, or manually alter
  pair evidence, a retained stage, or either final member. Pair errors always publish nullable
  `pairPublicationRetention`; a non-null value binds each canonical final member to its exact
  retained stage, while `null` never authorizes cleanup. An uncertainty result returns both
  canonical final members and their strongest established publication states for exact-workflow
  reconciliation; a recovered rekey verifies the generated-key pair before any prior-key access.

## `Attested Protected-Book Maintenance`

`AttestedProtectedBookMaintenanceStore` extends the protected-book store seam with the evidence
and signing-session requirements of backup acknowledgement, restoration, and rekey. The
executor-owned `AttestedProtectedBookLifecycleWorkflow` separates backup staging and exact-tuple
acknowledgement from restore/rekey continuation, so an external artifact can be durable before its
live-book acknowledgement without pretending both resources share one transaction. The workflow
permits only absent destinations, preserves the backup acknowledgement replay invariant, and
requires an attested destination continuation before restore or rekey reports success.
It reports whether a completed pair was newly published, recovered without another maintenance
mutation, or, for an acknowledgement retry, already published. If pair completion is uncertain it
does not manufacture a success or start a replacement pair: it publishes the typed pair-uncertainty
failure. Only verified retained pair evidence permits the exact same operation with its complete
original source, target, and secret inputs to reconcile it; malformed, legacy, or internally
inconsistent current evidence instead produces the distinct evidence-blocked error without a
verified original-operation instruction. A `published` or `recovered` result includes the exact
pair-publication retention evidence; an `already-published` backup acknowledgement alone has no
new pair-publication retention.
The same workflow owns live credential-registry and policy mutations. Its
`ProtectedBookRegistryMutationOutcome` separates a successful appended operation, a deterministic
maintenance refusal, and a live-head authorization refusal before the published adapter projects
them to `AttestationRegistryMutationResult`.

## Protection Boundary

- The encrypted-book boundary is the SQLite book plus encrypted journal/WAL bytes, not every
  artifact around the session.
- `BookAccess` keeps the durable book path coupled to one safe passphrase-source choice, but a
  key file stored beside the selected `.sqlite` path is not protected by SQLite3MC.
- `SqliteConnectionConfigurer` forces `temp_store=memory`; the documented protection boundary
  assumes that policy stays in place, and it now also requires `memory_security=fill` instead of
  silently tolerating runtimes that do not expose that zeroization pragma.
- Query results after decoding, in-process passphrase bytes before best-effort overwrite, crash dumps,
  heap-resident secret copies the JVM GC may preserve beyond the arrays FinGrind overwrites, the
  durable session-scoped passphrase copy held by `SqliteSessionSecret`, copied backups, and
  exported reports all live outside the encrypted-page boundary and need separate operator
  controls.

## `ChartOfAccounts`

`ChartOfAccounts` is the executor-owned aggregate that validates parent-child chart invariants
over one declared account registry snapshot.

```java
public final class ChartOfAccounts
```

- Purpose: keep parent existence, active-parent requirements, account-type compatibility,
  classification-family compatibility, and cycle detection structural instead of scattering those
  checks through CLI parsing or persistence adapters
- Construction: `of(List<RegisteredAccount>)` takes one declared-account snapshot and rejects
  duplicate account codes inside that aggregate view
- Boundary: this aggregate stays local to executor bookkeeping; public callers only see the
  resulting deterministic administration rejections
