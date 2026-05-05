---
afad: "4.0"
version: "0.31.0"
domain: ADAPTERS
updated: "2026-05-05"
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
    CommittedProvenance provenance)
```

- Purpose: preserve bookkeeping-local lineage typing and provenance while one write is being
  stored, queried, or journaled
- Boundary: projected to `PostingFact` only at the public published-language edge

## `AccountDeclaration`, `AccountDeclarationOutcome`, `BookOpeningOutcome`, And `RegisteredAccount`

These local bookkeeping administration types carry one translated account declaration, its outcome,
book-opening results, and one registry snapshot across session and store seams.

```java
public record AccountDeclaration(AccountCode accountCode, AccountName accountName, NormalBalance normalBalance)
public sealed interface AccountDeclarationOutcome
public sealed interface BookOpeningOutcome
public record RegisteredAccount(...)
```

- `AccountDeclaration`: bookkeeping-local declaration request after the public command crosses the
  translator boundary
- `AccountDeclarationOutcome`: closed family of accepted-versus-rejected declaration outcomes
- `BookOpeningOutcome`: closed family of accepted-versus-rejected initialization outcomes
- `RegisteredAccount`: local registry snapshot that owns redeclare/reactivate semantics and
  preserves declared-at time

## `BookAdministrationSession`

`BookAdministrationSession` is the executor-owned account-registry write seam over an already-open
book boundary.

```java
public interface BookAdministrationSession
```

- Surface: `openBook(...)`, `declareAccount(...)`
- Purpose: separate book initialization and account declaration from posting and reporting work
- Lifecycle: the outer workflow or store owns `close()`, not the narrowed session view

## `PostingValidationBook`

`PostingValidationBook` is the minimal lookup seam shared by preflight and transactional commit
validation.

```java
public interface PostingValidationBook
```

- Surface: `isInitialized()`, `findAccount(...)`, `findExistingPosting(...)`, `findPosting(...)`,
  `findReversalFor(...)`
- Purpose: let application preflight and commit-time validation reuse the same contract

## `PostingBookSession`

`PostingBookSession` is the executor-owned posting seam over an already-open book boundary.

```java
public interface PostingBookSession extends PostingValidationBook
```

- Surface: `commit(PostingDraft, PostingIdGenerator)`, fixture-oriented `commit(CommittedPosting)`
- Purpose: keep durable commit explicit and allow the store to allocate `postingId` only after
  acceptance
- Lifecycle: the outer workflow or store owns `close()`, not the narrowed session view

## `BookReadSession`

`BookReadSession` is the executor-owned unified read seam for lifecycle inspection, listings,
posting history, balances, and office-worker reports over an already-open book boundary.

```java
public interface BookReadSession
```

- Surface: `inspectBook()`, `isInitialized()`, `listAccounts(...)`, `findAccount(...)`,
  `findPosting(...)`, `listPostings(...)`, `accountBalance(...)`, `trialBalance(...)`,
  `accountLedger(...)`, `periodSummary(...)`
- Purpose: expose one authoritative read model without splitting query and reporting families into
  parallel seams
- Lifecycle: the outer workflow or store owns `close()`, not the narrowed session view

## `LedgerPlanSession`

`LedgerPlanSession` is the atomic transaction seam used by `LedgerPlanService`.

```java
public interface LedgerPlanSession
```

- Views: exposes `administrationSession()`, `postingSession()`, and `readSession()` as narrow
  operation seams bound to the same transaction boundary
- Surface: `beginLedgerPlanTransaction()`, `commitLedgerPlanTransaction()`,
  `rollbackLedgerPlanTransaction()`

## `PostingCommitResult`

`PostingCommitResult` is the closed family of ordinary posting-session commit outcomes.

```java
public sealed interface PostingCommitResult
```

- Variants: `Committed`, `Rejected`
- Purpose: distinguish accepted durable writes from ordinary domain rejections without throwing

## `SqliteBookPassphrase`

`SqliteBookPassphrase` is the resolved zeroizable UTF-8 passphrase payload used by the SQLite
adapter.

```java
public final class SqliteBookPassphrase implements AutoCloseable
```

- Purpose: hold normalized passphrase bytes only after the CLI has resolved a safe source
- Lifecycle: copied into native memory for `sqlite3_key()` / `sqlite3_rekey()` and then zeroized

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

## `ManagedSqliteRuntimeUnavailableException`, `UnsupportedSqliteCompileOptionsException`, And `SqliteStorageFailureException`

These public exception types distinguish important SQLite failure categories.

```java
public final class ManagedSqliteRuntimeUnavailableException extends IllegalStateException
public final class UnsupportedSqliteCompileOptionsException extends IllegalStateException
public final class SqliteStorageFailureException extends IllegalStateException
```

- `ManagedSqliteRuntimeUnavailableException`: managed runtime not found or unusable on this host
- `UnsupportedSqliteCompileOptionsException`: loaded runtime is missing required hardening options
- `SqliteStorageFailureException`: storage operation failed after the runtime was already available

## `SqliteBookSession`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions`

`SqliteBookSession` is the public SQLite-backed FinGrind session surface, while
`SqliteBookSessionMode` names the caller intent, `SqlitePassphraseIntent` and
`SqlitePassphraseResolver` describe secret resolution without leaking CLI-specific prompt policy,
and `SqliteBookSessions` owns session creation.

```java
public interface SqliteBookSession extends LedgerPlanSession, AutoCloseable
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
  `SqlitePassphraseIntent` into a zeroizable `SqliteBookPassphrase`, so external tooling can stay
  on the neutral `BookAccess` seam instead of passing adapter-native secret objects around
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
  `SqliteSessionSecret` owns the reusable session secret, `SqliteStoreReadOperations` delegates
  query/report reads through focused readers, and `SqliteStoreMutationOperations` owns durable
  mutation and rekey flows behind the public session API

## Protection Boundary

- The encrypted-book boundary is the SQLite book plus encrypted journal/WAL bytes, not every
  artifact around the session.
- `BookAccess` keeps the durable book path coupled to one safe passphrase-source choice, but a
  key file stored beside the selected `.sqlite` path is not protected by SQLite3MC.
- `SqliteConnectionConfigurer` forces `temp_store=memory`; the documented protection boundary
  assumes that policy stays in place.
- Query results after decoding, in-process passphrase bytes before zeroization, crash dumps,
  copied backups, and exported reports all live outside the encrypted-page boundary and need
  separate operator controls.
