---
afad: "3.5"
version: "0.13.0"
domain: DEVELOPER_SQLITE
updated: "2026-04-14"
route:
  keywords: [fingrind, sqlite, sqlite3mc, sqlite3 multiple ciphers, ffm, java26, storage, single-book, filesystem-path, key-file, encryption, canonical-schema, strict, trusted-schema, query-only, application-id, user-version, rekey, no-migrations]
  questions: ["how does fingrind use sqlite now", "why does fingrind use java ffm for sqlite", "how does the sqlite adapter initialize a new protected book", "how does fingrind protect book files"]
---

# SQLite Developer Reference

**Purpose**: Storage rationale and implementation notes for FinGrind's SQLite adapter.
**Schema references**:
- [sqlite/SCHEMA_CORE.md](./sqlite/SCHEMA_CORE.md)

## Hard-Break Storage Stance

FinGrind currently treats one protected SQLite file as one book for one entity.

That means:
- the selected SQLite file path is the durable book identity
- one `BookAccess` value pairs that durable path with one explicit passphrase-source selection
- the file may live anywhere on the operating-system filesystem
- there is no default database location
- every book-bound command requires `--book-file` plus exactly one of `--book-key-file`,
  `--book-passphrase-stdin`, or `--book-passphrase-prompt`
- `rekey-book` also requires exactly one replacement passphrase source and rotates an existing
  initialized book without introducing a compatibility layer
- key files remain the automation-friendly route; stdin and interactive prompt are the supported
  non-file routes
- key files must live on a POSIX filesystem and use owner-only permissions (`0400` or `0600`)
- FinGrind intentionally rejects plaintext CLI passphrase arguments and environment-variable
  passphrase transport
- newly opened books are protected through SQLite3 Multiple Ciphers 2.3.3 using the upstream
  default `sqleet` / `chacha20` cipher
- duplicate idempotency is enforced within the selected book, not globally across files
- one canonical current schema defines every newly initialized book
- there is no schema migration framework, version table, or compatibility layer
- legacy plaintext books and other encryption variants are out of scope for the current
  foundation

Older book shapes are intentionally unsupported during this hard-break phase.

## Current Adapter Choice

FinGrind's durable adapter is
[`SqlitePostingFactStore`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePostingFactStore.java).

Current implementation choice:
- use Java 26 FFM to call a configured SQLite shared library directly
- express book access explicitly as
  [`BookAccess`](../application/src/main/java/dev/erst/fingrind/application/BookAccess.java)
- resolve passphrase sources into
  [`SqliteBookPassphrase`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteBookPassphrase.java)
  before the storage adapter opens SQLite
- keep one open native SQLite handle per opened book session
- apply the book key immediately after open through `sqlite3_key()`
- validate the configured key before any schema or data access proceeds
- initialize the schema from the canonical embedded SQL resource through `sqlite3_exec`
- create canonical book tables as SQLite `STRICT` tables
- use prepared statements through the native SQLite C API
- rely on the chosen book path as the durable boundary and the resolved passphrase bytes as the
  access secret

Why this is the current design:
- it keeps the adapter explicit and SQLite-first instead of introducing a generic SQL abstraction
- it removes stderr string-matching and subprocess-per-call overhead from the old shell-out model
- it gives one real SQLite transaction boundary per commit attempt
- it keeps prepared statements and typed SQLite result codes close to the actual C API surface
- the packaged CLI no longer requires an external `sqlite3` binary
- controlled FinGrind surfaces can now pin one audited SQLite 3.53.0 / SQLite3 Multiple Ciphers
  2.3.3 source contract instead of inheriting host-library drift

Observed implementation note:
- we also reproduced a local `sqlite-jdbc` native-library load failure on this Java 26 macOS
  environment during the Phase 1 rewrite, but that was an environment-specific observation rather
  than the primary architecture reason for choosing FFM

## Source Provenance And License

FinGrind treats the upstream SQLite3 Multiple Ciphers project page as the source-of-truth entry
point for design, configuration, and operator guidance:
- project information: [https://utelle.github.io/SQLite3MultipleCiphers/](https://utelle.github.io/SQLite3MultipleCiphers/)
- upstream configuration guidance on URI key transport:
  [https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_uri/](https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_uri/)
- vendored release asset:
  [https://github.com/utelle/SQLite3MultipleCiphers/releases/download/v2.3.3/sqlite3mc-2.3.3-sqlite-3.53.0-amalgamation.zip](https://github.com/utelle/SQLite3MultipleCiphers/releases/download/v2.3.3/sqlite3mc-2.3.3-sqlite-3.53.0-amalgamation.zip)

License and attribution stance:
- SQLite3 Multiple Ciphers is MIT-licensed; the upstream text is copied verbatim in
  [LICENSE-SQLITE3MULTIPLECIPHERS](../LICENSE-SQLITE3MULTIPLECIPHERS)
- bundled original SQLite sources remain in the public domain
- repository attribution and runtime notes live in [NOTICE](../NOTICE)

## Current Runtime Policy

- root Gradle verification, the nested Jazzer build, `:cli:run`, GitHub workflows, and the Docker
  image all build from the vendored official SQLite3 Multiple Ciphers 2.3.3 amalgamation under
  [third_party/sqlite/sqlite3mc-amalgamation-2.3.3-sqlite-3530000/](../third_party/sqlite/sqlite3mc-amalgamation-2.3.3-sqlite-3530000)
- [`verifyManagedSqliteSource`](../build.gradle.kts) asserts the pinned
  LF-normalized `sqlite3mc_amalgamation.c` SHA3-256 before the managed native library is used, so
  Git checkout line-ending policy cannot create false integrity failures across machines or CI
- [`prepareManagedSqlite`](../build.gradle.kts) compiles the host-native shared library from that
  source with `SQLITE_THREADSAFE=1`, `SQLITE_OMIT_LOAD_EXTENSION=1`, `SQLITE_TEMP_STORE=3`, and
  `SQLITE_SECURE_DELETE=1`, then injects it through `FINGRIND_SQLITE_LIBRARY`
- the nested `jazzer/` build mirrors that same contract independently so local fuzzing and
  regression replay do not drift away from the managed runtime contract
- the Docker image compiles the same vendored SQLite3MC source during image build
- public CLI bundles are also managed-only: the launcher sets `fingrind.bundle.home`, and the
  runtime resolves the managed SQLite library from `lib/native/` inside the extracted bundle
- standalone `java -jar` execution remains developer-only and must receive
  `FINGRIND_SQLITE_LIBRARY` pointing at the library produced by `prepareManagedSqlite`
- `:cli:bundleCliArchive` is the public-artifact packaging entrypoint
- `:cli:shadowJar` packages only the Java application surface; local standalone verification that
  wants the managed native library must also run `prepareManagedSqlite` first and point
  `FINGRIND_SQLITE_LIBRARY` at the resulting file under `build/managed-sqlite/`

## Adapter Composition

The SQLite adapter is split into focused collaborators:
- [`BookAccess`](../application/src/main/java/dev/erst/fingrind/application/BookAccess.java):
  durable book file plus one explicit passphrase-source selection
- [`SqliteBookPassphrase`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteBookPassphrase.java):
  normalized zeroizable UTF-8 passphrase bytes after CLI-side source resolution
- [`SqlitePostingFactStore`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePostingFactStore.java):
  owns one thread-confined protected-book session, lookup paths, transaction-scoped duplicate
  checks, and durable commit outcomes
- [`RekeyBookResult`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/RekeyBookResult.java):
  explicit result family for SQLite-specific passphrase rotation
- [`SqliteNativeLibrary`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteNativeLibrary.java):
  minimal FFM binding surface to the SQLite C API, including configured-library selection, version
  and compile-option enforcement, key/rekey application, key validation, and `sqlite3_exec` for canonical schema
  application
- [`SqliteBookKeyFile`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteBookKeyFile.java):
  loads the file-backed passphrase route into the same normalized `SqliteBookPassphrase` model
- [`SqliteNativeDatabase`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteNativeDatabase.java):
  one open native SQLite database handle with distinct control-statement and script helpers
- [`SqliteNativeStatement`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteNativeStatement.java):
  single-use prepared statement wrapper with statement-scoped native memory; bound text length is
  derived from the native UTF-8 segment size instead of re-encoding Java strings on every bind
- [`SqliteSchemaManager`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteSchemaManager.java):
  lazily loads and caches the canonical schema resource, then applies it on the writable
  connection
- [`SqlitePostingSql`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePostingSql.java):
  holds canonical lookup and insert SQL strings
- [`SqlitePostingMapper`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePostingMapper.java):
  reconstructs domain facts from native SQLite result rows
- [`SqliteRuntime`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteRuntime.java):
  exposes machine-readable runtime probe metadata to the CLI surface

## Runtime Behavior

- reads and preflight against a missing book return empty state and do not create a file
- `open-book` creates parent directories if needed, applies the canonical schema, inserts the
  authoritative `book_meta.initialized_at` marker, and initializes a protected SQLite3MC book file
- `post-entry` no longer initializes a book implicitly; a missing or unopened book returns
  `BookNotInitialized`
- read-oriented sessions (`list-accounts` and `preflight-entry`) open SQLite through
  `SQLITE_OPEN_READONLY` and then enforce `pragma query_only = on`
- opening an existing plaintext SQLite file or using the wrong passphrase source fails during key
  validation, typically surfacing `SQLITE_NOTADB`
- initialized FinGrind books are stamped with a fixed `pragma application_id` and
  `pragma user_version`, and foreign or unsupported SQLite files are rejected before ordinary book
  reads proceed
- `rekey-book` rotates the passphrase through the native SQLite rekey path, reopens the book, and
  revalidates the replacement passphrase before the command reports success
- posting lines are validated against the declared-account registry before ordinary duplicate
  checks proceed
- opened book handles keep `foreign_keys = on`, `trusted_schema = off`, and the expected
  `query_only` setting for the current access mode
- schema bootstrap is intentionally separate from the posting transaction because it is idempotent
  book initialization, not one accounting fact commit
- book-local uniqueness enforces duplicate idempotency durably
- SQLite enforces declared-account durability through the
  `journal_line.account_code -> account.account_code` foreign key
- SQLite also enforces one reversal per target through a partial unique index
- reversal linkage is durable and references `posting_fact(posting_id)` through a foreign key
- runtime probes distinguish `managed` versus `system` library source and report
  `requiredMinimumSqliteVersion`, `requiredSqlite3mcVersion`, `sqliteRuntimeStatus`,
  `loadedSqliteVersion`, `loadedSqlite3mcVersion`, `bookProtectionMode`, and `defaultBookCipher`
  through `capabilities`

The book-session seam distinguishes ordinary duplicate outcomes from true runtime failures:
- duplicate idempotency returns `PostingCommitResult.DuplicateIdempotency`
- duplicate reversal target returns `PostingCommitResult.DuplicateReversalTarget`
- other SQLite-native, bridge, filesystem, passphrase-source, or cipher failures stay
  `IllegalStateException` and become CLI `runtime-failure`

## Book Protection Contract

- every protected-book session starts from one explicit `BookAccess` value:
  durable book path plus one selected passphrase source
- CLI passphrase resolution currently supports key file, standard input, and interactive prompt
- resolved passphrase bytes are normalized by removing one trailing line ending, validated as
  UTF-8, and rejected if empty
- transient key bytes are zeroized after native handoff
- FinGrind calls `sqlite3_key()` immediately after `sqlite3_open_v2()`
- FinGrind calls `sqlite3_rekey()` for `rekey-book` instead of routing replacement secrets through
  SQL text
- FinGrind validates the configured key by executing `SELECT count(*) FROM sqlite_master;` before
  any schema or business operation can proceed
- FinGrind intentionally relies on the upstream default `sqleet` / `chacha20` cipher and does not
  expose cipher selection through its own API
- FinGrind intentionally avoids the SQL `PRAGMA key` / `PRAGMA rekey` transport even though
  SQLite3MC exposes it, because those routes embed secrets into SQL strings
- FinGrind intentionally avoids SQLite URI `key=` and `hexkey=` transport because the upstream
  SQLite3MC guidance discourages keeping passphrases in URI strings
- FinGrind also intentionally avoids plaintext CLI passphrase arguments and environment-variable
  passphrase transport because those routes expose secrets too broadly in shells, process tables,
  logs, and child-process environments

## Transaction Model

- one `SqlitePostingFactStore` instance owns at most one open native SQLite handle
- read methods reuse that handle when it exists
- commit uses SQLite's `begin immediate` transaction mode and performs ordinary duplicate checks
  before insert on the same native handle
- ordinary duplicate outcomes are decided before `insert into posting_fact`, not inferred after a
  rolled-back write failure
- commit rolls back on failure and closes the handle when the session closes

This keeps ordinary duplicate outcomes deterministic without parsing human-readable SQLite error
text or re-querying after rollback.

## Canonical Schema Policy

- the canonical schema resource is
  [`book_schema.sql`](../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
- the canonical schema uses SQLite `STRICT` tables for `book_meta`, `account`, `posting_fact`, and
  `journal_line`
- there are no versioned migration file names such as `V1__...`
- there is no migration step between old and new book shapes
- if the schema changes again during this hard-break phase, new books are created from the new
  canonical file

## Why FFM-Backed SQLite

Reasons for the current design:
- the packaged runtime no longer shells out and no longer requires an external `sqlite3` binary
- prepared statements replace manual quoting
- one native handle enables real commit-time transaction scope
- typed SQLite result codes replace subprocess stderr interpretation
- the design stays explicit and SQLite-specific without introducing an ORM or generic SQL
  abstraction
- Java 26 FFM works directly against the managed SQLite3MC library without reintroducing JNI glue
  code into FinGrind itself

Managed runtime targets currently build SQLite 3.53.0 / SQLite3 Multiple Ciphers 2.3.3 from the
vendored amalgamation on macOS and Linux. The CLI JAR declares
`Enable-Native-Access: ALL-UNNAMED`, the public bundle launcher starts its private runtime with
`--enable-native-access=ALL-UNNAMED`, Gradle `Test` and `JavaExec` tasks are configured with the
same native-access flag, and controlled surfaces resolve the managed library either through
`fingrind.bundle.home` or `FINGRIND_SQLITE_LIBRARY`.

Native bridge notes:
- the SQLite symbol arena in
  [`SqliteNativeLibrary`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteNativeLibrary.java)
  intentionally lives for the JVM lifetime because the downcall handles outlive any individual book
  session
- native library lookup has no platform-default fallback; it uses extracted bundle home for the
  public launcher and `FINGRIND_SQLITE_LIBRARY` for the developer-only raw-JAR route
- runtime initialization validates both the loaded SQLite version and the loaded SQLite3 Multiple
  Ciphers version before any book operation is allowed
- runtime initialization also validates the required compile-option hardening before the managed
  library is accepted as compatible
- key application happens before any schema statement or pragma configuration
- opened book sessions pin `journal_mode=DELETE`, `synchronous=EXTRA`, `secure_delete=ON`,
  `temp_store=MEMORY`, `foreign_keys=ON`, and `trusted_schema=OFF`, and FinGrind rejects drift in
  those settings instead of trusting host defaults
- text parameters use SQLite's `SQLITE_TRANSIENT` contract so bound text does not rely on statement
  arena lifetime conventions
- error messages and SQLite version strings read exact C-string lengths rather than a guessed fixed
  byte cap
- close-failure and stale-handle failure shaping fall back to `sqlite3_errstr(resultCode)` so
  diagnostics do not dereference invalid database handles just to render an exception message
- `sqlite3_exec` failure reporting prefers the exec-owned error buffer when SQLite provides one,
  then falls back to `sqlite3_errstr(resultCode)`
- the runtime installs a JVM shutdown hook that attempts `sqlite3_shutdown()` after ordinary
  session-close paths have already released active handles, matching SQLite3MC's shutdown
  guidance for auto-extension and VFS cleanup

This is a deliberate hard-break correction to the earlier shell-out design, not an accidental
runtime experiment.
