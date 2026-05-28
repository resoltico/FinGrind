---
afad: "4.0"
version: "0.49.0"
domain: ADAPTERS
updated: "2026-05-28"
route:
  keywords: [fingrind, adapters, seams, sqlite, sqlite3mc, session, posting-fact, ffm, key-file, runtime, classifier]
  questions: ["how are committed facts stored in fingrind", "what are the storage seams in fingrind", "what does the sqlite adapter do in fingrind", "how does fingrind describe its sqlite runtime"]
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
- Added fact: `postingKind` keeps standard, opening-balance, and period-result-transfer postings distinct
  inside local bookkeeping seams, while `postingOriginKind` preserves which published entry family
  produced one committed posting and `evidence` keeps the retained justification bundle attached
- Boundary: projected to `PostingFact` only at the public published-language edge

## `AccountDeclaration`, `AccountDeclarationOutcome`, `BookOpeningOutcome`, And `RegisteredAccount`

These local bookkeeping administration types carry one translated account declaration, its outcome,
book-opening results, and one registry snapshot across session and store seams.

```java
public record AccountDeclaration(
    AccountCode accountCode,
    AccountName accountName,
    AccountType accountType,
    AccountRole accountRole,
    AccountTaxonomy accountTaxonomy)
public sealed interface AccountDeclarationOutcome
public sealed interface BookOpeningOutcome
public record RegisteredAccount(...)
```

- `AccountDeclaration`: bookkeeping-local declaration request after the public command crosses the
  translator boundary
- `AccountDeclarationOutcome`: closed family of accepted-versus-rejected declaration outcomes
- `BookOpeningOutcome`: closed family of accepted-versus-rejected initialization outcomes
- `RegisteredAccount`: local registry snapshot that owns redeclare/reactivate semantics and
  preserves declared-at time while keeping `accountType`, `accountRole`, and `accountTaxonomy`
  immutable after the first declaration while deriving `normalBalance()` from `AccountSemantics`

## `BookAuditEvent` And `BookAuditEventKind`

These local bookkeeping types own the durable append-only audit stream written beside account and
posting facts.

```java
public record BookAuditEvent(...)
public enum BookAuditEventKind implements WireValue
```

- `BookAuditEvent`: one validated durable audit fact carrying event time plus the local account or
  posting identity when the event kind requires it
- `BookAuditEventKind`: the closed durable audit vocabulary
- Current kinds: `BOOK_OPENED`, `ACCOUNT_DECLARED`, `ACCOUNT_REACTIVATED`,
  `POSTING_COMMITTED`, `POSTING_REVERSED`, `BOOK_REKEYED`, `BACKUP_CREATED`,
  `BACKUP_RESTORED`, `REKEY_ROLLBACK_RESTORED`, `REKEY_ROLLBACK_DELETED`,
  `BACKUP_CREATED_COMPENSATED`, `REKEY_ROLLBACK_DELETED_COMPENSATED`, and `PERIOD_RESULT_TRANSFERRED`
- Storage boundary: SQLite persists these rows in `audit_event` and rejects direct update/delete
  mutation through append-only triggers

## `BookLifecycleReader`, `BookAdministrationStore`, `AccountLookupStore`, `AccountCatalogStore`, `PostingLookupStore`, `PostingHistoryStore`, `PostingRangeStore`, `BookkeepingReportStore`, `BookkeepingReadStore`, `PostingCommitStore`, `PeriodResultTransferStore`, And `LedgerPlanTransaction`

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
public interface PeriodResultTransferStore
public interface LedgerPlanTransaction
```

- `BookLifecycleReader`: local lifecycle inspection without mutation
- `BookAdministrationStore`: book opening plus account-registry declaration/reactivation writes
- `AccountLookupStore`: one-account and batched account lookup by semantic account code
- `AccountCatalogStore`: full ordered registry reads plus paginated account-catalog views
- `PostingLookupStore`: idempotency, posting-id, and reversal lookup for committed facts
- `PostingHistoryStore`: paginated posting-history reads
- `PostingRangeStore`: effective-date posting streams plus earliest-posting and close-horizon facts
- `BookkeepingReportStore`: grouped totals plus account-balance, trial-balance, account-ledger,
  and period-summary local report views
- `BookkeepingReadStore`: the composite read-side seam that combines lifecycle, lookup, history,
  and report ports for application read services
- `PostingCommitStore`: durable posting commit boundary
- `PeriodResultTransferStore`: durable generated transfer-period-result commit boundary
- `LedgerPlanTransaction`: explicit begin/commit/rollback boundary for atomic ledger-plan
  execution
- Purpose: keep lifecycle, administration, lookup, history, reporting, durable commit, and
  transaction ownership on auditable narrow seams instead of one god-port
- Lifecycle: the outer workflow or adapter owns `close()`, not these executor seams

## `AccountCurrencyTotals`

`AccountCurrencyTotals` is the executor-owned aggregate row used by statement reads and close
generation.

```java
public record AccountCurrencyTotals(
    RegisteredAccount account,
    CurrencyUnit currencyUnit,
    CurrencyBalance balance)
```

- Purpose: move per-account, per-currency exact totals across the
  `BookkeepingReportStore.accountTotals(...)` seam without materializing full posting streams for
  statement computation
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

## `BookkeepingReadService` And `BookkeepingLookupOutcome`

`BookkeepingReadService` owns local bookkeeping inspection, lookup, query, and reporting semantics,
and `BookkeepingLookupOutcome` preserves lifecycle rejection, ordinary absence, and presence
distinctly for internal callers.

```java
public final class BookkeepingReadService
public sealed interface BookkeepingLookupOutcome<T>
```

- `BookkeepingReadService`: keeps local read/report behavior inside the bookkeeping context before
  any public DTO or public query-rejection family is projected
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

## `MaintenanceDecision`, `MaintenanceCompletion`, And `MaintenanceFailure`

These local maintenance support types keep accepted-versus-failed workflow outcomes separate from
the public `ContractDecision` surface.

```java
public sealed interface MaintenanceDecision<T>
public enum MaintenanceCompletion
public record MaintenanceFailure(...)
```

- `MaintenanceDecision`: local accepted-or-failed decision type used by the maintenance workflow
  and store seam before public projection
- `MaintenanceCompletion`: sentinel success value for local maintenance steps that do not need
  extra payload
- `MaintenanceFailure`: local runtime failure value that isolates protected-book maintenance from
  the public contract failure envelope until the published-language adapter translates it outward

## `ProtectedBookBackupOutcome`, `ProtectedBookRestoreOutcome`, And `ProtectedBookRecoveryOutcome`

These local maintenance result families keep backup, restore, and rollback-recovery outcomes inside
the maintenance context until the published-language translator projects them into public contract
types.

```java
public sealed interface ProtectedBookBackupOutcome
public sealed interface ProtectedBookRestoreOutcome
public sealed interface ProtectedBookRecoveryOutcome
```

- `ProtectedBookBackupOutcome`: accepted or rejected result for verified encrypted backup export
- `ProtectedBookRestoreOutcome`: accepted or rejected result for verified backup restore
- `ProtectedBookRecoveryOutcome`: accepted or rejected result for rollback inspection, rollback
  restore, and rollback deletion
- Boundary: each local outcome carries local `Path` values and local maintenance rejections rather
  than public redacted path hints

## `ProtectedBookMaintenanceArtifactRole`, `ProtectedBookMaintenanceRejection`, And `ProtectedBookMaintenanceWorkflow`

These local maintenance types own protected-book maintenance semantics, deterministic refusals, and
artifact-role vocabulary behind the public maintenance adapter.

```java
public enum ProtectedBookMaintenanceArtifactRole
public sealed interface ProtectedBookMaintenanceRejection
public final class ProtectedBookMaintenanceWorkflow
```

- `ProtectedBookMaintenanceArtifactRole`: local role vocabulary for live-book, backup-source,
  rollback-artifact, and restored-target verification and busy-lease outcomes
- `ProtectedBookMaintenanceRejection`: local deterministic refusal family for blocking artifacts,
  same-path restore, busy artifacts, verification failures, and rollback-artifact selection
- `ProtectedBookMaintenanceWorkflow`: local owner for lease ordering, source verification,
  side-effect-free rollback inspection, staged backup publication, staged restore, rollback
  restore, rollback deletion, and audit retraction when an external commit fails after audit
  staging

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

- `ProtectedBookVerificationFailure`: local verification vocabulary for missing,
  blank-SQLite, foreign-SQLite, unsupported-format-version, incomplete-FinGrind, and generic
  protected-book verification failures discovered before backup, restore, rollback inspection, or
  rollback restore is allowed to proceed
- `ProtectedBookMaintenancePublishedLanguageTranslator`: the only exported translator that may
  project local maintenance outcomes into `BackupBookResult`, `RestoreBookResult`,
  `RekeyRollbackResult`, and `BookMaintenanceRejection`
- Redaction rule: the translator converts filesystem `Path` values into `PublicPathHint` values
  and converts local artifact-role and verification-failure vocabularies into the stable public
  contract types instead of leaking SQLite or filesystem implementation detail across the boundary

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
- Shared kernel: these local types reuse `core.EffectiveDateRange`, `core.CurrencyBalance`, and
  `core.InteractionLimits` where the concept is genuinely common to public and local bookkeeping
  language
- Boundary: `BookkeepingReadPagePublishedLanguageTranslator` and
  `BookkeepingReadReportPublishedLanguageTranslator` are the only owners that map these types to
  `AccountPage`, `PostingPage`, `AccountBalanceSnapshot`, `TrialBalanceReport`,
  `AccountLedgerReport`, and `PeriodSummaryReport`

## `PostingDraft`, `PostingCommitResult`, And `PostingIdGenerator`

These exported `executor.spi` types keep durable posting commit explicit at the store seam.

```java
public record PostingDraft(...)
public sealed interface PostingCommitResult
public interface PostingIdGenerator
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

## `SqliteBookPassphrase`

`SqliteBookPassphrase` is the resolved UTF-8 passphrase payload used by the SQLite
adapter.

```java
public final class SqliteBookPassphrase implements AutoCloseable
```

- Purpose: hold normalized passphrase bytes only after the CLI has resolved a safe source
- Lifecycle: copied into native memory for `sqlite3_key()` / `sqlite3_rekey()` and then
  best-effort overwritten on the buffers FinGrind owns

## `SqliteSessionSecret`

`SqliteSessionSecret` is the internal adapter owner for one reusable session-scoped secret.

```java
final class SqliteSessionSecret implements AutoCloseable
```

- Purpose: keep one durable passphrase copy attached to the openable SQLite session boundary while
  native calls borrow short-lived working copies
- Lifecycle: each borrow creates one working `SqliteBookPassphrase` copy for the immediate native
  call, best-effort overwrites that working copy after handoff, and keeps the durable session
  copy until the session closes or rotates to a replacement secret

## `GeneratedBookKeyFile`, `SqliteBookKeyFile`, And `SqliteBookKeyFileGenerator`

These public helpers own secure UTF-8 key-file loading and generation.

```java
public record GeneratedBookKeyFile(...)
public final class SqliteBookKeyFile
public final class SqliteBookKeyFileGenerator
```

- `GeneratedBookKeyFile`: non-secret generated key-file metadata returned to the public contract
- `SqliteBookKeyFile`: loads one secure UTF-8 key file into `SqliteBookPassphrase`
- `SqliteBookKeyFileGenerator`: creates one new owner-only key file and returns non-secret
  `GeneratedBookKeyFile` metadata
- Contract: generated key files are base64url-no-padding, 256 bits of entropy, and never
  overwritten in place

## `SqliteRuntime`, `SqliteRuntime.Probe`, And `SqliteRuntime.Status`

`SqliteRuntime` is the public runtime metadata owner for the packaged SQLite adapter.

```java
public final class SqliteRuntime
```

- Purpose: publish the managed SQLite driver contract, required versions, compile options, and
  discovery probe surface
- Surface: `probe()`, `sqliteVersion()`, `sqlite3MultipleCiphersVersion()`, and public constants
  such as `REQUIRED_MINIMUM_SQLITE_VERSION`
- `SqliteRuntime.Probe`: machine-facing runtime snapshot carrying loaded versions, required
  minimums, readiness status, and any issue detail; late probe failures preserve any already-known
  library provenance and path facts instead of collapsing back to bare unavailability
- `SqliteRuntime.Status`: stable wire vocabulary with `ready`, `unavailable`, `failed`, and
  `incompatible`

## `SqliteFailureClassifier` And `SqliteFailureClassifier.Category`

`SqliteFailureClassifier` classifies runtime failures for higher-level hint generation.

```java
public final class SqliteFailureClassifier
```

- Purpose: separate managed-runtime failures from storage failures and unrelated errors
- `SqliteFailureClassifier.Category`: stable classification family with `MANAGED_RUNTIME`,
  `STORAGE`, and `OTHER`

## `ManagedSqliteRuntimeUnavailableException`, `UnsupportedManagedSqliteLibraryIdentityException`, `UnsupportedSqliteCompileOptionsException`, And `SqliteStorageFailureException`

These public exception types distinguish important SQLite failure categories.

```java
public final class ManagedSqliteRuntimeUnavailableException extends IllegalStateException
public final class UnsupportedManagedSqliteLibraryIdentityException extends IllegalStateException
public final class UnsupportedSqliteCompileOptionsException extends IllegalStateException
public final class SqliteStorageFailureException extends IllegalStateException
```

- `ManagedSqliteRuntimeUnavailableException`: managed runtime not found or unusable on this host
- `UnsupportedManagedSqliteLibraryIdentityException`: selected managed library failed the trusted
  managed-runtime identity check before any native symbol lookup; publisher-owned runtimes are
  checked against the publisher-owned `.trusted.sha256` sidecar and their sibling `.sha256` file,
  while source-checkout-managed runtimes are checked against checkout-local `.trusted.sha256` plus
  sibling `.sha256` sidecars
- `UnsupportedSqliteCompileOptionsException`: loaded runtime is missing required hardening options
- `SqliteStorageFailureException`: storage operation failed after the runtime was already available

## `SqliteAdministrationSession`, `SqliteReadSession`, `SqlitePostingSession`, `SqlitePeriodResultTransferSession`, `SqlitePlanExecutionSession`, `SqliteRekeySession`, `SqliteAdministrationSessions`, `SqliteReadSessions`, `SqlitePostingSessions`, `SqlitePeriodResultTransferSessions`, `SqlitePlanExecutionSessions`, `SqliteRekeySessions`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions`

FinGrind now publishes one family of workflow-shaped SQLite session views instead of one composite
god-session. `SqliteBookSessionMode` names the caller intent, `SqlitePassphraseIntent` and
`SqlitePassphraseResolver` describe secret resolution without leaking CLI-specific prompt policy,
workflow-specific opener classes resolve access into the right session view, and
`SqliteBookSessions` remains the shared store-opening seam.

```java
public interface SqliteAdministrationSession extends AutoCloseable
public interface SqliteReadSession extends AutoCloseable
public interface SqlitePostingSession extends AutoCloseable
public interface SqlitePeriodResultTransferSession extends AutoCloseable
public interface SqlitePlanExecutionSession extends AutoCloseable
public interface SqliteRekeySession extends AutoCloseable
public final class SqliteAdministrationSessions
public final class SqliteReadSessions
public final class SqlitePostingSessions
public final class SqlitePeriodResultTransferSessions
public final class SqlitePlanExecutionSessions
public final class SqliteRekeySessions
public enum SqliteBookSessionMode
public enum SqlitePassphraseIntent
public interface SqlitePassphraseResolver
public final class SqliteBookSessions
```

- Purpose: expose only the public workflow surface each caller needs instead of one all-capability
  session seam
- `SqliteAdministrationSession`: lifecycle and account-registry workflows
- `SqliteReadSession`: inspection, lookup, list, and report workflows
- `SqlitePostingSession`: administration, reads, validation, and commit for ordinary posting flows
- `SqlitePeriodResultTransferSession`: period-result-transfer workflows
- `SqlitePlanExecutionSession`: plan execution plus transaction ownership
- `SqliteRekeySession`: rekey workflows
- `SqliteAdministrationSessions`: resolves one protected-book access tuple into an administration session
- `SqliteReadSessions`: resolves one protected-book access tuple into a read session
- `SqlitePostingSessions`: resolves one protected-book access tuple into a posting session
- `SqlitePeriodResultTransferSessions`: resolves one protected-book access tuple into a transfer session
- `SqlitePlanExecutionSessions`: resolves one protected-book access tuple into a plan-execution session
- `SqliteRekeySessions`: resolves one protected-book access tuple into a rekey session
- `SqliteBookSessionMode`: distinguishes `READ_ONLY`, `READ_WRITE_EXISTING`,
  `READ_WRITE_CREATE`, and `PLAN_EXECUTION`
- `SqlitePassphraseIntent`: distinguishes whether the caller is resolving an existing book secret
  or a confirmed replacement/new secret before `openBook(...)` or `rekeyBook(...)`
- `SqlitePassphraseResolver`: resolves the contract-level `BookAccess.PassphraseSource` plus one
  `SqlitePassphraseIntent` into a `SqliteBookPassphrase` whose owned buffers are best-effort
  overwritten after use
- `SqliteBookSessions`: opens or projects the shared `SqlitePostingFactStore` seam for cases that
  need direct store access or one workflow-neutral open-store entry point
- Internal split: one package-private `SqlitePostingFactStore` implements the narrow public views
  over one immutable `SqliteStoreContext` plus one mutable `SqliteStoreLifecycle`; the factory
  projects that internal store into the public workflow-shaped interfaces instead of publishing the
  full implementation seam

## `ProtectedBookMaintenanceAuditKind`

This executor-owned maintenance vocabulary names the successful protected-book workflows that are
durably recorded inside the encrypted bookkeeping audit stream.

```java
public enum ProtectedBookMaintenanceAuditKind
```

- `ProtectedBookMaintenanceAuditKind`: the closed successful-maintenance vocabulary:
  `BACKUP_CREATED`, `BACKUP_RESTORED`, `REKEY_ROLLBACK_RESTORED`, and
  `REKEY_ROLLBACK_DELETED`
- Storage projection: `SqliteProtectedBookMaintenanceStore` maps these maintenance audit kinds
  onto `BookAuditEventKind` values and inserts them into the protected book's `audit_event` table
- Boundary: side-effect-free inspection does not emit one maintenance audit fact

## `ProtectedBookMaintenanceAuditCompensationKind`

This executor-owned maintenance vocabulary names the compensating protected-book workflows that
durably retract a prior successful maintenance fact inside the encrypted bookkeeping audit stream.

```java
public enum ProtectedBookMaintenanceAuditCompensationKind
```

- `ProtectedBookMaintenanceAuditCompensationKind`: the closed compensation vocabulary:
  `BACKUP_CREATED` and `REKEY_ROLLBACK_DELETED`
- Storage projection: `SqliteProtectedBookMaintenanceStore` maps these compensation kinds onto
  `BookAuditEventKind.BACKUP_CREATED_COMPENSATED` and
  `BookAuditEventKind.REKEY_ROLLBACK_DELETED_COMPENSATED`
- Boundary: compensation facts exist only when a previously published maintenance fact must be
  durably retracted after later failure cleanup

## `ProtectedBookMaintenanceService`, `ProtectedBookMaintenanceStore`, `StagedBackupPair`, `StagedBookReplacement`, `StagedRollbackArtifactDeletion`, And `SqliteProtectedBookMaintenanceStore`

Protected-book maintenance now belongs to one executor-owned maintenance boundary with one narrow
SQLite store SPI and one encrypted in-book maintenance audit stream.

```java
public final class ProtectedBookMaintenanceService
public interface ProtectedBookMaintenanceStore
public interface StagedBackupPair
public interface StagedBookReplacement
public interface StagedRollbackArtifactDeletion
public final class SqliteProtectedBookMaintenanceStore
```

- `ProtectedBookMaintenanceService`: owns backup, rollback inspection, rollback restore,
  rollback deletion, and restore doctrine plus typed rejections and published maintenance results
- `ProtectedBookMaintenanceStore`: narrow SPI for initialized-book verification, reversible book
  replacement, rollback-artifact selection, and encrypted maintenance-audit append/retract
- `StagedBackupPair`: reversible staged backup publication that can verify the staged backup
  before final publish
- `StagedBookReplacement`: reversible staged replacement prepared for restore-style workflows
- `StagedRollbackArtifactDeletion`: reversible staged deletion prepared for rollback-artifact
  cleanup
- `SqliteProtectedBookMaintenanceStore`: verifies protected-book artifacts through SQLite, rejects
  non-initialized and noncanonical sources, performs reversible replacement work, and records
  successful maintenance facts in the selected book's `audit_event` stream
- Boundary: maintenance doctrine now lives above SQLite, while SQLite owns only verification and
  filesystem/native execution details

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
