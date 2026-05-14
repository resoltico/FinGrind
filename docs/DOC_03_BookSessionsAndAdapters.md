---
afad: "4.0"
version: "0.36.0"
domain: ADAPTERS
updated: "2026-05-14"
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
    CommittedProvenance provenance)
```

- Purpose: represent one committed posting independently of any concrete storage adapter
- Surface: `reversalReference()` and `reversalReason()` delegate to the typed `PostingLineage`
- Validation: rejects `null` posting id, journal entry, posting lineage, and provenance

## `CommittedPosting`

`CommittedPosting` is the local bookkeeping committed-posting record used inside executor and
storage seams before the public `PostingFact` projection is rendered.

```java
public record CommittedPosting(
    PostingId postingId,
    JournalEntry journalEntry,
    PostingLineageModel postingLineage,
    PostingKind postingKind,
    CommittedProvenance provenance)
```

- Purpose: preserve bookkeeping-local lineage typing and provenance while one write is being
  stored, queried, or journaled
- Added fact: `postingKind` keeps standard, opening-balance, and period-close postings distinct
  inside local bookkeeping seams
- Boundary: projected to `PostingFact` only at the public published-language edge

## `AccountDeclaration`, `AccountDeclarationOutcome`, `BookOpeningOutcome`, And `RegisteredAccount`

These local bookkeeping administration types carry one translated account declaration, its outcome,
book-opening results, and one registry snapshot across session and store seams.

```java
public record AccountDeclaration(
    AccountCode accountCode,
    AccountName accountName,
    AccountType accountType,
    AccountRole accountRole)
public sealed interface AccountDeclarationOutcome
public sealed interface BookOpeningOutcome
public record RegisteredAccount(...)
```

- `AccountDeclaration`: bookkeeping-local declaration request after the public command crosses the
  translator boundary
- `AccountDeclarationOutcome`: closed family of accepted-versus-rejected declaration outcomes
- `BookOpeningOutcome`: closed family of accepted-versus-rejected initialization outcomes
- `RegisteredAccount`: local registry snapshot that owns redeclare/reactivate semantics and
  preserves declared-at time while keeping `accountType` and `accountRole` immutable after the
  first declaration while deriving `normalBalance()` from `AccountSemantics`

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
  `POSTING_COMMITTED`, `POSTING_REVERSED`, and `BOOK_REKEYED`
- Storage boundary: SQLite persists these rows in `audit_event` and rejects direct update/delete
  mutation through append-only triggers

## `BookStore`

`BookStore` is the executor-owned public application seam for one selected book boundary.

```java
public interface BookStore extends PostingValidationStore
```

- Surface: `inspectBook()`, `openBook(...)`, `declareAccount(...)`, `listAccounts(...)`,
  `listPostings(...)`, `accountBalance(...)`, `accountTotals(...)`, `trialBalance(...)`,
  `accountLedger(...)`, `periodSummary(...)`, and durable `commit(PostingDraft, PostingIdGenerator)`
- Purpose: keep initialization, administration, read/report, lookup, and ordinary posting commit
  on one explicit selected-book seam instead of fragmenting them into parallel narrow interfaces
- Lifecycle: the outer workflow or adapter owns `close()`, not this executor seam
- Initialization fact: `openBook(...)` takes both the initialization instant and one
  `BookIdentity`

## `AccountCurrencyTotals`

`AccountCurrencyTotals` is the executor-owned aggregate row used by statement reads and close
generation.

```java
public record AccountCurrencyTotals(
    RegisteredAccount account,
    CurrencyUnit currencyUnit,
    CurrencyBalance balance)
```

- Purpose: move per-account, per-currency exact totals across the `BookStore.accountTotals(...)`
  seam without materializing full posting streams for statement computation
- Boundary: stores compute these totals; statement and close services consume them as local
  aggregate truth

## `PostingValidationStore`

`PostingValidationStore` is the minimal lookup and lifecycle seam shared by preflight and
transactional commit validation.

```java
public interface PostingValidationStore
```

- Surface: `inspectBook()`, `findAccount(...)`, `findExistingPosting(...)`, `findPosting(...)`,
  and `findReversalFor(...)`
- Purpose: let application preflight and commit-time validation reuse one authoritative
  initialized-book lookup contract

## `AtomicBookStore`

`AtomicBookStore` extends `BookStore` with the ledger-plan transaction boundary.

```java
public interface AtomicBookStore extends BookStore
```

- Surface: `beginLedgerPlanTransaction()`, `commitLedgerPlanTransaction()`,
  `rollbackLedgerPlanTransaction()`
- Purpose: keep atomic plan execution explicit without leaking transaction control into ordinary
  administration or reporting workflows

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
  `BookkeepingReadPublishedLanguageTranslator` projects them into public `BookQueryRejection`

## `AccountRegistryCursor`, `AccountRegistryQuery`, `AccountRegistryPage`, `PostingHistoryCursor`, `PostingHistoryQuery`, `PostingHistoryPage`, `AccountBalanceCriteria`, `AccountBalanceView`, `TrialBalanceCriteria`, `TrialBalanceRowView`, `TrialBalanceView`, `AccountLedgerCriteria`, `AccountLedgerEntryView`, `AccountLedgerView`, `PeriodSummaryCriteria`, `PeriodCurrencySummaryView`, `PeriodAccountActivityView`, And `PeriodSummaryView`

These exported `executor.bookkeeping` types are the local bookkeeping read model used by
`BookStore`, `BookkeepingReadService`, SQLite read helpers, and workflow/query execution before any
public report DTOs are projected.

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
- Boundary: `BookkeepingReadPublishedLanguageTranslator` is the only owner that maps these types to
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

## `SqliteBookKeyFile`, `SqliteBookKeyFileGenerator`, And `SqliteBookKeyFileGenerator.GeneratedKeyFile`

These public helpers own secure UTF-8 key-file loading and generation.

```java
public final class SqliteBookKeyFile
public final class SqliteBookKeyFileGenerator
```

- `SqliteBookKeyFile`: loads one secure UTF-8 key file into `SqliteBookPassphrase`
- `SqliteBookKeyFileGenerator`: creates one new owner-only key file and returns non-secret
  `SqliteBookKeyFileGenerator.GeneratedKeyFile` metadata
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
  checked against the embedded FinGrind digest resource and their sibling `.sha256` file, while
  `environment-configured` runtimes are only checked against the sibling `.sha256` sidecar
- `UnsupportedSqliteCompileOptionsException`: loaded runtime is missing required hardening options
- `SqliteStorageFailureException`: storage operation failed after the runtime was already available

## `SqliteBookSession`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions`

`SqliteBookSession` is the public SQLite-backed FinGrind session surface, while
`SqliteBookSessionMode` names the caller intent, `SqlitePassphraseIntent` and
`SqlitePassphraseResolver` describe secret resolution without leaking CLI-specific prompt policy,
and `SqliteBookSessions` owns session creation.

```java
public interface SqliteBookSession extends AtomicBookStore, AutoCloseable
public enum SqliteBookSessionMode
public enum SqlitePassphraseIntent
public interface SqlitePassphraseResolver
public final class SqliteBookSessions
```

- Purpose: expose one stable public session API for CLI, tooling, and fuzz harnesses without
  exporting the internal store/lifecycle implementation types
- `SqliteBookSessionMode`: distinguishes `READ_ONLY`, `READ_WRITE_EXISTING`,
  `READ_WRITE_CREATE`, and `PLAN_EXECUTION`
- `SqlitePassphraseIntent`: distinguishes whether the caller is resolving an existing book secret
  or a confirmed replacement/new secret before `openBook(...)` or `rekeyBook(...)`
- `SqlitePassphraseResolver`: resolves the contract-level `BookAccess.PassphraseSource` plus one
  `SqlitePassphraseIntent` into a `SqliteBookPassphrase` whose owned buffers are best-effort
  overwritten after use, so external tooling can stay on the neutral `BookAccess` seam instead of
  passing adapter-native secret objects around
- `SqliteBookSessions.open(...)`: constructs one SQLite-backed session boundary for the selected
  caller intent without requiring an eager SQLite open; overloads accept either an already-resolved
  `SqliteBookPassphrase` or the higher-level `BookAccess` plus `SqlitePassphraseResolver`
- `SqliteBookSessions.openResolved(...)`: primes the session according to the selected access mode
  and transfers ownership only after that priming succeeds; missing books may still resolve lazily
  for read-only or existing-only flows that intentionally defer opening, while create and plan
  modes resolve secrets eagerly so initialization and rekey prompts can enforce new-secret policy
- Session shape: `SqliteBookSession` keeps the administration, posting, read, and ledger-plan
  seams on one boundary while still exposing direct `findAccount(...)`, `findExistingPosting(...)`,
  and `rekeyBook(...)` helpers needed by CLI/tooling flows; rekeying now consumes a
  `BookAccess.PassphraseSource` plus `SqlitePassphraseResolver` so the same safe source-resolution
  policy applies to both open and rotate flows
- Internal split: `SqlitePostingFactStore` is now a thin session wrapper over one immutable
  `SqliteStoreContext` dependency bundle plus one mutable `SqliteStoreLifecycle` state owner;
  `SqliteSessionSecret` owns the reusable session secret, `SqliteBookLifecycleInspectionMapper`
  owns local lifecycle projection, `SqliteTransactionValidationBook` owns focused validation
  lookups, `SqliteStoreReadOperations` delegates query/report reads through focused readers, and
  `SqliteStoreMutationOperations` owns durable mutation and rekey flows behind the public session
  API

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
