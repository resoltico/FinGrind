---
afad: "5.0.1"
version: "0.61.0"
domain: SQLITE_RUNTIME_AND_SESSIONS
updated: "2026-07-26"
scope:
  paths: ["sqlite", "executor", "cli", "docs"]
  symbols: ["SqliteRuntime", "SqliteFailureClassifier", "ManagedSqliteRuntimeUnavailableException", "UnsupportedManagedSqliteLibraryIdentityException", "UnsupportedSqliteCompileOptionsException", "SqlitePersistenceInvariantException", "SqliteProtectedBookVerificationException", "SqliteStorageFailureException", "SqliteOpenBookCompletionUncertainException", "SqliteBookSessions", "SqlitePlanReadOnlySession"]
route:
  keywords: [fingrind, sqlite, runtime, managed-library, protected-book, session, plan-read-only, passphrase, query-only, workflow]
  questions: ["how does FinGrind inspect the SQLite runtime", "which SQLite workflow session should a FinGrind caller use", "what does the credential-free plan session allow", "which SQLite failure types does FinGrind publish"]
---

# SQLite Runtime And Session API Reference

This document is the canonical runtime metadata, failure, and workflow-shaped SQLite session owner.
[DOC_03_BookSessionsAndAdapters.md](./DOC_03_BookSessionsAndAdapters.md) owns the broader
adapter/store seam that these runtime and session types serve.

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

## `SqliteBookPassphrase`, `SqliteBookPassphraseSourceBytes`, `SqliteSessionSecret`, `GeneratedBookKeyFile`, `SqliteBookKeyFile`, And `SqliteBookKeyFileGenerator`

These types own SQLite passphrase material and generated book-key files without broadening an
ordinary session's authority.

```java
public final class SqliteBookPassphrase implements AutoCloseable
public final class SqliteBookPassphraseSourceBytes
public static final class SqliteBookPassphraseSourceBytes.OversizedBookPassphraseSourceException
public record GeneratedBookKeyFile(...)
public final class SqliteBookKeyFile
public final class SqliteBookKeyFileGenerator
```

- `SqliteBookPassphrase` holds normalized UTF-8 passphrase bytes only after the CLI resolves a
  safe source. Native `sqlite3_key()` and `sqlite3_rekey()` calls receive copied native bytes, and
  FinGrind best-effort overwrites buffers it owns afterwards.
- `SqliteBookPassphraseSourceBytes` keeps stdin-backed and key-file-backed byte loading on one
  bounded zeroizing path. It reads at most
  `ProtocolInteractionLimits.BOOK_PASSPHRASE_MAX_UTF8_BYTES + 1` bytes and throws
  `OversizedBookPassphraseSourceException` when the source exceeds the canonical ceiling.
- The package-private `SqliteSessionSecret` holds one reusable session-scoped secret; each borrow
  creates a short-lived `SqliteBookPassphrase` working copy and retains the session copy only until
  close or rotation.
- `GeneratedBookKeyFile` is non-secret metadata. `SqliteBookKeyFile` loads one secure UTF-8 key
  file into a `SqliteBookPassphrase`, while `SqliteBookKeyFileGenerator` creates a new owner-only
  file with base64url-no-padding 256-bit entropy and never overwrites it in place.
- Existing caller-selected live-book and book-key parents are validation-only, real, owner-only,
  and non-mutable. `open-book` may create a missing live-book parent only through atomic POSIX
  `0700` creation followed by validation; ACL-only filesystems fail closed.

## `SqliteFailureClassifier` And `SqliteFailureClassifier.Category`

`SqliteFailureClassifier` classifies runtime failures for higher-level hint generation.

```java
public final class SqliteFailureClassifier
```

- Purpose: separate managed-runtime failures, persistence-invariant breaches, storage failures,
  and unrelated errors
- `SqliteFailureClassifier.Category`: stable classification family with `MANAGED_RUNTIME`,
  `PERSISTENCE_INVARIANT`, `STORAGE`, and `OTHER`

## `ManagedSqliteRuntimeUnavailableException`, `UnsupportedManagedSqliteLibraryIdentityException`, `UnsupportedSqliteCompileOptionsException`, `SqlitePersistenceInvariantException`, `SqliteProtectedBookVerificationException`, `SqliteStorageFailureException`, And `SqliteOpenBookCompletionUncertainException`

These public exception types distinguish important SQLite failure categories.

```java
public final class ManagedSqliteRuntimeUnavailableException extends IllegalStateException
public final class UnsupportedManagedSqliteLibraryIdentityException extends IllegalStateException
public final class UnsupportedSqliteCompileOptionsException extends IllegalStateException
public final class SqlitePersistenceInvariantException extends IllegalStateException
public final class SqliteProtectedBookVerificationException extends IllegalStateException
public final class SqliteStorageFailureException extends IllegalStateException
public final class SqliteOpenBookCompletionUncertainException extends IllegalStateException
```

- `ManagedSqliteRuntimeUnavailableException`: managed runtime not found or unusable on this host
- `UnsupportedManagedSqliteLibraryIdentityException`: selected managed library failed the
  managed-runtime identity check before any native symbol lookup. Bundle-managed and
  source-checkout-managed runtimes copy their library and sibling `.sha256` sidecar to a retained
  owner-only snapshot, retain its verified SHA-256, then reopen it with no-follow access and
  re-hash it immediately before Java's pathname-based native load. This is defense in depth under
  the normal cooperative-filesystem boundary, not a proof that arbitrary same-owner replacement
  cannot change the bytes Java ultimately loads.
- `UnsupportedSqliteCompileOptionsException`: loaded runtime is missing required hardening options
- `SqlitePersistenceInvariantException`: SQLite rejected one write through a persistence invariant
  that FinGrind should have rejected before commit, so the CLI classifies it as `internal-error`
- `SqliteProtectedBookVerificationException`: the protected-book key or authentication material
  did not verify the selected SQLite file, so the CLI emits the dedicated verification refusal
- `SqliteStorageFailureException`: storage operation failed after the runtime was already available
- `SqliteOpenBookCompletionUncertainException`: initialization COMMIT did not acknowledge and a
  fresh post-rollback state observation did not prove the selected file blank; it carries the
  prebuilt verified genesis facts so callers retain founder custody and reconcile the book instead
  of retrying or deleting it

## `SqliteAdministrationSession`, `SqliteReadSession`, `SqlitePostingSession`, `SqliteReportingPeriodCloseSession`, `SqlitePlanExecutionSession`, `SqliteAdministrationSessions`, `SqliteReadSessions`, `SqlitePostingSessions`, `SqliteReportingPeriodCloseSessions`, `SqlitePlanExecutionSessions`, `SqliteBookSessionMode`, `SqlitePassphraseIntent`, `SqlitePassphraseResolver`, And `SqliteBookSessions`

FinGrind publishes workflow-shaped SQLite session views instead of one composite god-session.
`SqliteBookSessionMode` names the caller intent, `SqlitePassphraseIntent` and
`SqlitePassphraseResolver` describe secret resolution without leaking CLI-specific prompt policy,
workflow-specific opener classes resolve access into the right session view, and
`SqliteBookSessions` remains the shared store-opening seam.

```java
public interface SqliteAdministrationSession extends AutoCloseable
public interface SqliteReadSession extends AutoCloseable
public interface SqlitePostingSession extends AutoCloseable
public interface SqliteReportingPeriodCloseSession extends AutoCloseable
public interface SqlitePlanExecutionSession extends SqliteReadSession, LedgerPlanExecutionStore
public final class SqliteAdministrationSessions
public final class SqliteReadSessions
public final class SqlitePostingSessions
public final class SqliteReportingPeriodCloseSessions
public final class SqlitePlanExecutionSessions
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
- `SqliteReportingPeriodCloseSession`: interim-result-sweep and fiscal-year-close workflows
- `SqlitePlanExecutionSession`: one SQLite-backed plan capability that combines the read surface
  with `LedgerPlanExecutionStore`; it binds plan reads, plan-specific child persistence, verified
  posting linkage, final aggregate append, and transaction ownership to one protected-book session.
  It does not inherit ordinary direct-mutation session capabilities.
- `SqliteAdministrationSessions`: resolves one protected-book access tuple into an administration session
- `SqliteReadSessions`: resolves one protected-book access tuple into a read session
- `SqlitePostingSessions`: resolves one protected-book access tuple into a posting session
- `SqliteReportingPeriodCloseSessions`: resolves one protected-book access tuple into a close-operation
  session
- `SqlitePlanExecutionSessions`: resolves one protected-book access tuple into a plan-execution session
- `SqliteBookSessionMode`: distinguishes `READ_ONLY`, `PLAN_READ_ONLY`, `READ_WRITE_EXISTING`,
  `READ_WRITE_CREATE`, and `PLAN_EXECUTION`. `PLAN_READ_ONLY` is not generic read mode: it preserves
  the canonical missing-book workflow rejection while retaining native read-only, query-only,
  noncreating SQLite access.
- `SqlitePassphraseIntent`: distinguishes existing-book, plan-setup, and newly chosen secret
  resolution without publishing a broad mutation session
- `SqlitePassphraseResolver`: resolves the contract-level `BookAccess.PassphraseSource` plus one
  `SqlitePassphraseIntent` into a `SqliteBookPassphrase` whose owned buffers are best-effort
  overwritten after use
- `SqliteBookSessions`: opens or projects the shared `SqlitePostingFactStore` seam for cases that
  need direct store access or one workflow-neutral open-store entry point
- Internal split: one package-private `SqlitePostingFactStore` implements the narrow public views
  over one immutable `SqliteStoreContext` plus one mutable `SqliteStoreLifecycle`; the factory
  projects that internal store into the public workflow-shaped interfaces instead of publishing the
  full implementation seam

## `SqlitePlanReadOnlySession` And `SqlitePlanReadOnlySessions`

These types expose the credential-free SQLite capability for a ledger plan that cannot mutate a
protected book.

```java
public interface SqlitePlanReadOnlySession
    extends SqliteReadSession, LedgerPlanReadOnlyExecutionStore
public final class SqlitePlanReadOnlySessions
```

- `SqlitePlanReadOnlySession`: combines the read surface with
  `LedgerPlanReadOnlyExecutionStore`. It uses native SQLite `READ_ONLY` with `query_only=1`, cannot
  create a database or obtain a mutation port, and defers a missing-book open so the workflow
  returns its canonical initialized-book journal rejection.
- `SqlitePlanReadOnlySessions`: resolves one protected-book access tuple into that credential-free
  read-only plan session.
- Boundary: this is not a compatibility view over `SqlitePlanExecutionSession`; the two session
  types expose physically disjoint plan authorities.
