---
afad: "3.5"
version: "0.21.0"
domain: ADAPTERS
updated: "2026-04-21"
route:
  keywords: [fingrind, adapters, seams, sqlite, sqlite3mc, session, posting-fact, ffm, key-file, runtime, classifier]
  questions: ["how are committed facts stored in fingrind", "what are the storage seams in fingrind", "what does the sqlite adapter do in fingrind", "how does fingrind describe its sqlite runtime"]
---

# Book Session And Adapter API Reference

This file documents the public seam and adapter layer around the contract/executor core: explicit
book-access tuples, committed facts crossing session boundaries, executor-owned sessions, and the
durable SQLite runtime and store.

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

- Surface: `commit(PostingDraft, PostingIdGenerator)`, fixture-oriented `commit(PostingFact)`
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

## `PostingValidation`

`PostingValidation` owns the shared deterministic posting-rule pass used by both preflight and
transactional commit flows.

```java
public final class PostingValidation
```

- Surface: `rejectionFor(PostingRequest, PostingValidationBook)`
- Purpose: keep idempotency, account-state, and reversal validation in one executor-owned helper

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
- `SqliteRuntime.Probe`: machine-facing runtime snapshot carrying loaded versions, required minimums,
  readiness status, and any issue detail
- `SqliteRuntime.Status`: stable wire vocabulary with `ready`, `unavailable`, and `incompatible`

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

## `SqlitePostingFactStore` And `SqliteStoreAccessMode`

`SqlitePostingFactStore` is the durable SQLite-backed implementation of FinGrind's administration,
posting, unified-read, and ledger-plan seams.

```java
public final class SqlitePostingFactStore implements LedgerPlanSession
```

- Purpose: persist one protected entity book into one selected SQLite file
- `SqliteStoreAccessMode`: distinguishes `READ_ONLY`, `READ_WRITE_EXISTING`,
  `READ_WRITE_CREATE`, and `PLAN_EXECUTION`
- Access modes: support read-only reporting sessions plus writable administration and posting
  sessions through the same durable adapter
- Inspection: exposes lifecycle, format-version, compatibility, and migration-policy metadata
- Helper split: lower-level concerns are factored into focused collaborators such as
  `SqliteConnectionConfigurer`, `SqliteBookStateReader`, `SqliteStatementQueries`,
  `SqlitePostingReader`, and `SqliteMutationWriter`
