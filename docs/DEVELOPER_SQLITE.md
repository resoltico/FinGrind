---
afad: "3.5"
version: "0.3.1"
domain: DEVELOPER_SQLITE
updated: "2026-04-10"
route:
  keywords: [fingrind, sqlite, ffm, java26, storage, single-book, filesystem-path, schema, canonical-schema, no-migrations]
  questions: ["how does fingrind use sqlite now", "why does fingrind use java ffm for sqlite", "how does the sqlite adapter initialize a new book"]
---

# SQLite Developer Reference

**Purpose**: Storage rationale and implementation notes for FinGrind's SQLite adapter.
**Schema references**:
- [sqlite/SCHEMA_CORE.md](./sqlite/SCHEMA_CORE.md)

## Hard-Break Storage Stance

FinGrind currently treats one SQLite file as one book for one entity.

That means:
- the selected file path is the book identity
- the file may live anywhere on the operating-system filesystem
- there is no default database location
- `--book-file` is required for both preflight and commit
- duplicate idempotency is enforced within the selected book, not globally across files
- one canonical current schema defines every newly initialized book
- there is no schema migration framework, version table, or compatibility layer

Older book shapes are out of scope for the current foundation.

## Current Adapter Choice

FinGrind's durable adapter is [`SqlitePostingFactStore`](/Users/erst/Tools/FinGrind/sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePostingFactStore.java).

Current implementation choice:
- use Java 26 FFM to call a configured SQLite shared library directly
- keep one open native SQLite handle per opened book store
- initialize the schema from the canonical embedded SQL resource through `sqlite3_exec`
- use prepared statements through the native SQLite C API
- rely on the chosen book path as the durable boundary

Why this is the current design:
- it keeps the adapter explicit and SQLite-first instead of introducing a generic SQL abstraction
- it removes stderr string-matching and subprocess-per-call overhead from the old shell-out model
- it gives one real SQLite transaction boundary per commit attempt
- it keeps prepared statements and typed SQLite result codes close to the actual C API surface
- the packaged CLI no longer requires an external `sqlite3` binary
- controlled FinGrind surfaces can now pin one audited SQLite source version instead of inheriting
  host-library drift

Observed implementation note:
- we also reproduced a local `sqlite-jdbc` native-library load failure on this Java 26 macOS
  environment during the Phase 1 rewrite, but that was an environment-specific observation rather
  than the primary architecture reason for choosing FFM

Current runtime policy:
- root Gradle verification, the nested Jazzer build, `:cli:run`, GitHub workflows, and the Docker
  image all build from the vendored official SQLite 3.53.0 amalgamation under
  [`third_party/sqlite/sqlite-amalgamation-3530000/`](/Users/erst/Tools/FinGrind/third_party/sqlite/sqlite-amalgamation-3530000)
- [`verifyManagedSqliteSource`](/Users/erst/Tools/FinGrind/build.gradle.kts) asserts the pinned
  `sqlite3.c` SHA3-256 before the managed native library is used
- [`prepareManagedSqlite`](/Users/erst/Tools/FinGrind/build.gradle.kts) compiles the host-native
  shared library and injects it through `FINGRIND_SQLITE_LIBRARY`
- the nested `jazzer/` build mirrors that same contract independently so local fuzzing and
  regression replay do not fall back to an older host library
- standalone `java -jar` execution still allows a compatible external library, but
  [`SqliteNativeLibrary`](/Users/erst/Tools/FinGrind/sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteNativeLibrary.java)
  rejects anything older than SQLite 3.53.0

## Adapter Composition

The SQLite adapter is split into focused collaborators:
- [`SqlitePostingFactStore`](/Users/erst/Tools/FinGrind/sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePostingFactStore.java): owns one thread-confined book session, lookup paths, transaction-scoped duplicate checks, and durable commit outcomes
- [`SqliteNativeLibrary`](/Users/erst/Tools/FinGrind/sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteNativeLibrary.java): minimal FFM binding surface to the SQLite C API, including configured-library selection, version enforcement, and `sqlite3_exec` for canonical schema application
- [`SqliteNativeDatabase`](/Users/erst/Tools/FinGrind/sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteNativeDatabase.java): one open native SQLite database handle with distinct control-statement and script helpers
- [`SqliteNativeStatement`](/Users/erst/Tools/FinGrind/sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteNativeStatement.java): single-use prepared statement wrapper with statement-scoped native memory
- [`SqliteSchemaManager`](/Users/erst/Tools/FinGrind/sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteSchemaManager.java): lazily loads and caches the canonical schema resource, then applies it on the writable connection
- [`SqlitePostingSql`](/Users/erst/Tools/FinGrind/sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePostingSql.java): holds canonical lookup and insert SQL strings
- [`SqlitePostingMapper`](/Users/erst/Tools/FinGrind/sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePostingMapper.java): reconstructs domain facts from native SQLite result rows
- [`SqliteRuntime`](/Users/erst/Tools/FinGrind/sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteRuntime.java): exposes machine-readable runtime probe metadata to the CLI surface

## Runtime Behavior

- Reads and preflight against a missing book return empty state and do not create a file.
- The first commit creates parent directories if needed and initializes the canonical schema on the opened writable connection.
- Schema bootstrap is intentionally separate from the posting transaction because it is idempotent
  book initialization, not one accounting fact commit.
- Book-local uniqueness enforces duplicate idempotency durably.
- SQLite also enforces one reversal per target through a partial unique index.
- Correction linkage is durable and references `posting_fact(posting_id)` through a foreign key.
- Runtime probes distinguish `managed` versus `system` library source and report
  `requiredMinimumSqliteVersion`, `sqliteRuntimeStatus`, and `loadedSqliteVersion` through
  `capabilities`.

The runtime seam distinguishes ordinary duplicate outcomes from true runtime failures:
- duplicate idempotency returns `PostingCommitResult.DuplicateIdempotency`
- duplicate reversal target returns `PostingCommitResult.DuplicateReversalTarget`
- other SQLite-native, bridge, or filesystem failures stay `IllegalStateException` and become CLI
  `runtime-failure`

## Transaction Model

- one `SqlitePostingFactStore` instance owns at most one open native SQLite handle
- read methods reuse that handle when it exists
- commit uses SQLite's `begin immediate` transaction mode and performs ordinary duplicate checks
  before insert on the same native handle
- ordinary duplicate outcomes are decided before `insert into posting_fact`, not inferred after a
  rolled-back write failure
- commit rolls back on failure and closes the handle when the store closes

This keeps ordinary duplicate outcomes deterministic without parsing human-readable SQLite error
text or re-querying after rollback.

## Canonical Schema Policy

- The canonical schema resource is [`book_schema.sql`](/Users/erst/Tools/FinGrind/sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql).
- There are no versioned migration file names such as `V1__...`.
- There is no migration step between old and new book shapes.
- If the schema changes again during this hard-break phase, new books are created from the new canonical file.

## Why FFM-Backed SQLite

Reasons for the current design:
- the packaged runtime no longer shells out and no longer requires an external `sqlite3` binary
- prepared statements replace manual quoting
- one native handle enables real commit-time transaction scope
- typed SQLite result codes replace subprocess stderr interpretation
- the design stays explicit and SQLite-specific without introducing an ORM or generic SQL abstraction
- Java 26 FFM works directly against a managed or compatible external `libsqlite3` without
  reintroducing JNI glue code into FinGrind itself

Managed runtime targets currently build SQLite 3.53.0 from the vendored amalgamation on macOS and
Linux. The CLI JAR declares `Enable-Native-Access: ALL-UNNAMED`, Gradle `Test` and `JavaExec`
tasks are configured with `--enable-native-access=ALL-UNNAMED`, and controlled surfaces inject the
managed library through `FINGRIND_SQLITE_LIBRARY`. Standalone execution may still load a system
library, but only if it satisfies the same 3.53.0 minimum.

Native bridge notes:
- the SQLite symbol arena in [`SqliteNativeLibrary`](/Users/erst/Tools/FinGrind/sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteNativeLibrary.java) intentionally lives for the JVM lifetime because the downcall handles outlive any individual book session
- native library lookup prefers `FINGRIND_SQLITE_LIBRARY`, then falls back to the platform default
  `libsqlite3`
- runtime initialization validates the loaded SQLite version before any book operation is allowed
- text parameters use SQLite's `SQLITE_TRANSIENT` contract so bound text does not rely on statement
  arena lifetime conventions
- error messages and SQLite version strings read exact C-string lengths rather than a guessed fixed
  byte cap
- `sqlite3_exec` failure reporting prefers the exec-owned error buffer when SQLite provides one,
  then falls back to `sqlite3_errmsg(db)`

This is a deliberate hard-break correction to the earlier shell-out design, not an accidental
runtime experiment.
