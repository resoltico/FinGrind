---
afad: "4.0"
version: "0.34.0"
domain: DEVELOPER_SQLITE
updated: "2026-05-10"
route:
  keywords: [fingrind, sqlite, sqlite3mc, sqlite3 multiple ciphers, ffm, java26, storage, single-book, filesystem-path, key-file, encryption, canonical-schema, strict, trusted-schema, query-only, application-id, user-version, rekey, no-migrations]
  questions: ["how does fingrind use sqlite now", "why does fingrind use java ffm for sqlite", "how does the sqlite adapter initialize a new protected book", "how does fingrind protect book files"]
---

# SQLite Developer Reference

**Purpose**: Storage rationale and implementation notes for FinGrind's SQLite adapter.
**Schema references**:
- [sqlite/SCHEMA_CORE.md](./sqlite/SCHEMA_CORE.md)

The canonical security model, threat boundary, secret-transport rules, and protected-book
verification semantics now live in [DEVELOPER_SECURITY.md](./DEVELOPER_SECURITY.md). This
reference keeps the SQLite-specific architecture, runtime, and schema details.

## Hard-Break Storage Stance

FinGrind currently treats one protected SQLite file as one book for one accounting entity.

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
- key files must use POSIX owner-only permissions (`0400` or `0600`) on macOS/Linux or a
  Windows owner-only ACL on Windows
- protected book files and same-directory SQLite sidecars (`-journal`, `-wal`, and `-shm`) are
  hardened to owner-only permissions when the host filesystem exposes POSIX permissions or
  Windows ACL views
- FinGrind intentionally rejects plaintext CLI passphrase arguments and environment-variable
  passphrase transport
- newly opened books are protected through SQLite3 Multiple Ciphers 2.3.4 using the upstream
  default `sqleet` / `chacha20` cipher
- duplicate idempotency is enforced within the selected book, not globally across files
- one canonical current schema defines every newly initialized book
- the current supported book format is `1`, owned by `BookFormatContract`
- there is no published migration executor or historical upgrade catalog yet because the public
  line starts at format `1`
- legacy plaintext books and other encryption variants are out of scope for the current
  foundation

Because current FinGrind books start at format `1`, there are no historical upgrade steps bundled
yet.

## Current Adapter Choice

FinGrind's public durable SQLite session surface is
[`SqliteBookSession`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteBookSession.java),
opened through
[`SqliteBookSessions`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteBookSessions.java).
The package-private backing implementation remains
[`SqlitePostingFactStore`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePostingFactStore.java).

Current implementation choice:
- use Java 26 FFM to call a configured SQLite shared library directly
- express book access explicitly as
  [`BookAccess`](../contract/src/main/java/dev/erst/fingrind/contract/BookAccess.java)
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
- controlled FinGrind surfaces can now pin one audited SQLite 3.53.1 / SQLite3 Multiple Ciphers
  2.3.4 source contract instead of inheriting host-library drift

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
  [https://github.com/utelle/SQLite3MultipleCiphers/releases/download/v2.3.4/sqlite3mc-2.3.4-sqlite-3.53.1-amalgamation.zip](https://github.com/utelle/SQLite3MultipleCiphers/releases/download/v2.3.4/sqlite3mc-2.3.4-sqlite-3.53.1-amalgamation.zip)

License and attribution stance:
- SQLite3 Multiple Ciphers is MIT-licensed; the upstream text is copied verbatim in
  [LICENSE-SQLITE3MULTIPLECIPHERS](../LICENSE-SQLITE3MULTIPLECIPHERS)
- bundled original SQLite sources remain in the public domain
- repository attribution and runtime notes live in [NOTICE](../NOTICE)

## Current Runtime Policy

- root Gradle verification, the nested Jazzer build, `:cli:run`, GitHub workflows, and the Docker
  image all build from the vendored official SQLite3 Multiple Ciphers 2.3.4 amalgamation under
  [third_party/sqlite/sqlite3mc-amalgamation-2.3.4-sqlite-3530001/](../third_party/sqlite/sqlite3mc-amalgamation-2.3.4-sqlite-3530001)
- [`verifyManagedSqliteSource`](../build.gradle.kts) asserts the pinned
  LF-normalized `sqlite3mc_amalgamation.c` SHA3-256 before the managed native library is used, so
  Git checkout line-ending policy cannot create false integrity failures across machines or CI
- [`managed-sqlite-contract.json`](../contract/src/main/resources/dev/erst/fingrind/contract/protocol/managed-sqlite-contract.json)
  is the canonical owner for the managed SQLite version, source-id, required compile options,
  forbidden compile options, and secure-memory requirement; build logic, runtime discovery, bundle
  metadata, and shell verification derive from that resource instead of keeping private literals
- [`prepareManagedSqlite`](../build.gradle.kts) compiles the host-native shared library from that
  source with the canonical managed-SQLite compile contract, then injects it through
  `FINGRIND_SQLITE_LIBRARY`
- the nested `jazzer/` build mirrors that same contract independently so local fuzzing and
  regression replay do not drift away from the managed runtime contract
- the Docker image compiles the same vendored SQLite3MC source during image build and now derives
  its compiler flags from the same canonical managed-SQLite contract instead of repeating
  handwritten `SQLITE_*` defines
- the Docker image verifies the vendored SQLite3MC source hash before compile, mirroring the
  managed-source integrity contract used in Gradle
- public CLI bundles are also managed-only: the launcher sets `fingrind.bundle.home`, and the
  runtime resolves the managed SQLite library from `lib/native/` inside the extracted bundle
- generated source-checkout launchers are managed-only as well: after
  `./gradlew :cli:installShadowDist prepareManagedSqlite`, the launcher resolves the managed
  SQLite library from that prepared checkout automatically
- standalone `java -jar` execution remains developer-only, but when it runs from a prepared
  checkout it resolves the same managed SQLite library automatically and reads the native-access
  permission from the JAR manifest
- `:cli:bundleCliArchive` is the public-artifact packaging entrypoint
- `:cli:shadowJar` packages only the Java application surface; local standalone verification that
  wants the managed native library must also run `prepareManagedSqlite` first. When the resulting
  JAR stays under the prepared checkout layout it then resolves the managed library automatically;
  only custom direct-Java launches outside that layout need an explicit
  `FINGRIND_SQLITE_LIBRARY`

## Adapter Composition

The SQLite adapter is split into focused collaborators:
- [`BookAccess`](../contract/src/main/java/dev/erst/fingrind/contract/BookAccess.java):
  durable book file plus one explicit passphrase-source selection
- [`SqliteBookPassphrase`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteBookPassphrase.java):
  normalized UTF-8 passphrase bytes after CLI-side source resolution, with best-effort overwrite
  of owned heap/direct buffers
- [`SqliteBookSession`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteBookSession.java),
  [`SqliteBookSessionMode`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteBookSessionMode.java),
  and [`SqliteBookSessions`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteBookSessions.java):
  stable public session contract and factory for CLI, tooling, and fuzz harnesses
- [`SqlitePostingFactStore`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePostingFactStore.java):
  thin package-private session wrapper for one thread-confined protected-book boundary; it composes
  one immutable store context plus one mutable lifecycle owner instead of inheriting one wide
  mutable sink
- [`SqliteStoreOpening`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteStoreOpening.java),
  [`SqliteStoreContext`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteStoreContext.java),
  [`SqliteStoreLifecycle`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteStoreLifecycle.java),
  [`SqliteStoreReadOperations`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteStoreReadOperations.java),
  and [`SqliteStoreMutationOperations`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteStoreMutationOperations.java):
  focused collaborators for open-time ownership transfer, immutable dependency wiring, mutable
  lifecycle state, query/report reads, rekeying, and durable writes
- [`SqliteSessionSecret`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteSessionSecret.java):
  durable session-secret owner that clones one reusable protected-book passphrase and mints
  short-lived working copies for each native open or rekey handoff
- [`SqliteBookAccessRules`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteBookAccessRules.java):
  canonical same-package rule owner for SQLite file-backed key-file access requirements
- [`SqliteBookLifecycleInspectionMapper`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteBookLifecycleInspectionMapper.java)
  and [`SqliteTransactionValidationBook`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteTransactionValidationBook.java):
  focused helpers that keep local lifecycle mapping and validation lookups separate from the outer
  session wrapper instead of layering multiple narrow session-view classes over the same state
- [`RekeyBookResult`](../contract/src/main/java/dev/erst/fingrind/contract/RekeyBookResult.java):
  explicit result family for passphrase rotation outcomes
- [`SqliteNativeBootstrap`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteNativeBootstrap.java),
  [`SqliteNativeConnections`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteNativeConnections.java),
  [`SqliteNativeStatements`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteNativeStatements.java),
  [`SqliteNativeErrors`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteNativeErrors.java),
  and [`SqliteNativeRuntimePolicy`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteNativeRuntimePolicy.java):
  split native-bridge owners for bootstrap, configured-library selection, version and
  compile-option enforcement, key/rekey application, key validation, statement execution, and
  SQLite-native error decoding
- [`SqliteBookKeyFile`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteBookKeyFile.java):
  loads the file-backed passphrase route into the same normalized `SqliteBookPassphrase` model
- [`SqliteNativeDatabase`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteNativeDatabase.java):
  one open native SQLite database handle with distinct control-statement and script helpers
- [`SqliteNativeStatement`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteNativeStatement.java):
  single-use prepared statement wrapper with statement-scoped native memory; bound text length is
  derived from the native UTF-8 segment size instead of re-encoding Java strings on every bind
- [`SqliteBookSchemaBootstrap`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteBookSchemaBootstrap.java):
  lazily loads and caches the canonical schema resource, then applies it on the writable
  connection
- [`SqlitePostingSql`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePostingSql.java):
  holds canonical lookup and insert SQL strings
- [`SqlitePostingMapper`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePostingMapper.java):
  reconstructs domain facts from native SQLite result rows
- [`SqliteStatementQueries`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteStatementQueries.java),
  [`SqlitePostingReader`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePostingReader.java),
  and [`SqliteReportReader`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteReportReader.java):
  keep single-row lookup, posting-history reconstruction, and report row assembly focused and
  reusable under the store context
- [`SqliteRuntime`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteRuntime.java):
  exposes machine-readable runtime probe metadata to the CLI surface

## Runtime Behavior

- opening a read-oriented session against a missing book stays lazy and does not create a file;
  `inspect-book` reports the `missing` lifecycle state, query/report commands reject
  deterministically as book-not-initialized, and `preflight-entry` rejects as
  `PostingRejection.BookNotInitialized`
- `inspect-book` exposes missing, blank, initialized, foreign, unsupported-version, and incomplete
  states before mutating commands proceed
- `open-book` creates parent directories if needed, applies the canonical schema, inserts the
  authoritative `book_meta.initialized_at` marker, initializes a protected SQLite3MC book file,
  and hardens the book path plus present sidecar files to owner-only permissions when the host
  filesystem supports that security model
- `post-entry` no longer initializes a book implicitly; a missing or unopened book returns
  `BookNotInitialized`
- read-oriented sessions (`inspect-book`, `list-accounts`, `get-posting`, `list-postings`,
  `account-balance`, `trial-balance`, `account-ledger`, `period-summary`, and `preflight-entry`)
  open SQLite through `SQLITE_OPEN_READONLY` and then enforce `pragma query_only = on`
- read-oriented sessions do not rewrite book-file or sidecar permissions; permission repair
  happens only on mutation-capable opens such as `open-book`, writable sessions, and
  `rekey-book`
- opening an existing plaintext SQLite file, loading a damaged or truncated protected book, or
  using the wrong passphrase source fails during key validation, but the public CLI classifies
  those cases as the deterministic
  `protected-book-verification-failed` error instead of leaking raw SQLite symptoms such as
  `SQLITE_NOTADB`
- initialized FinGrind books are stamped with a fixed `pragma application_id` and
  `pragma user_version`, and foreign or unsupported SQLite files are rejected before ordinary book
  reads proceed
- `rekey-book` creates one same-directory rollback copy, rotates the passphrase through the native
  SQLite rekey path, reopens the book, revalidates the replacement passphrase before the command
  reports success, and restores the pre-rekey file automatically if that verification fails
- if a process crash or forced stop interrupts rekey cleanup, the stale same-directory
  `*.rekey-rollback-*.sqlite` artifact remains on disk under the old ciphertext until an operator
  inspects or removes it; later opens warn when they detect that stale artifact
- posting validation is shared between application preflight and transactional SQLite commit, so
  book lifecycle, account-state, duplicate-idempotency, and reversal-lineage rules do not drift
  between the two paths
- opened book handles keep `foreign_keys = on`, `trusted_schema = off`, and the expected
  `query_only` setting for the current access mode
- schema bootstrap is intentionally separate from the posting transaction because it is idempotent
  book initialization, not one accounting fact commit
- book-local uniqueness enforces duplicate idempotency durably
- SQLite enforces declared-account durability through the
  `journal_line.account_code -> account.account_code` foreign key
- SQLite also enforces one reversal per target through a partial unique index
- reversal linkage is durable and references `posting_fact(posting_id)` through a foreign key
- runtime probes distinguish bundle-managed, source-checkout-managed, and
  environment-configured library provenance and
  report `environment.sqlite.requiredCompileOptions`,
  `environment.sqlite.forbiddenCompileOptions`,
  `environment.sqlite.requiresSecureMemorySupport`,
  `environment.sqlite.requiredMinimumSqliteVersion`,
  `environment.sqlite.requiredSqlite3mcVersion`,
  `environment.sqlite.requiredSqliteSourceId`,
  `environment.sqlite.runtime.compileOptionsVerification`,
  `environment.sqlite.runtime.status`,
  `environment.sqlite.runtime.runtimeProvenance`,
  `environment.sqlite.runtime.runtimeTrustBasis`,
  `environment.sqlite.runtime.loadedLibraryPath`,
  `environment.sqlite.runtime.loadedSqliteVersion`,
  `environment.sqlite.runtime.loadedSqlite3mcVersion`,
  `environment.sqlite.runtime.loadedSqliteSourceId`,
  `environment.storage.bookProtectionMode`, and
  `environment.storage.defaultProtectedBookFormat.cipher`,
  `environment.storage.defaultProtectedBookFormat.legacyMode`,
  `environment.storage.defaultProtectedBookFormat.pageSize`,
  `environment.storage.defaultProtectedBookFormat.reservedBytes`,
  `environment.storage.defaultProtectedBookFormat.kdfIter`, and
  `environment.storage.defaultProtectedBookFormat.plaintextHeaderSize` through `capabilities`
- `environment.sqlite.runtime.compileOptionsVerification` is `verified` only when the runtime
  reaches the ready state, `failed` when the loaded library is present but violates the
  compile-option contract by missing required options or exposing forbidden options, and
  `not-verified` when the runtime is unavailable, when a late probe failure already exposed the
  selected runtime but could not finish verification, or when an earlier version/source-id gate
  prevents a compile-option verdict
- `environment.sqlite.runtime.status` is `failed` when the probe already knows the resolved
  runtime provenance and library path but later discovery work aborts before the runtime can be
  classified as ready or incompatible

The posting seam distinguishes ordinary domain outcomes from true runtime failures:
- accepted commits return `PostingCommitResult.Committed`
- ordinary write refusals return `PostingCommitResult.Rejected(...)`
- deterministic passphrase and key-file policy failures are translated into contract-owned CLI
  errors such as `protected-book-verification-failed`, `invalid-book-key-file`, or
  `interactive-prompt-unavailable`
- other SQLite-native, bridge, or filesystem failures stay `IllegalStateException` and become CLI
  `storage-runtime-failure`, while managed runtime bootstrap failures become
  `managed-runtime-failure`

## Book Protection Contract

- every protected-book session starts from one explicit `BookAccess` value:
  durable book path plus one selected passphrase source
- CLI passphrase resolution currently supports key file, standard input, and interactive prompt
- resolved passphrase bytes are normalized by removing one trailing line ending, validated as
  UTF-8, rejected if empty, and rejected when any supported route exceeds 4096 bytes after UTF-8
  normalization
- key files must remain inside owner-only parent directories as well as owner-only files
- transient key bytes are best-effort overwritten after native handoff; Java heap copies outside
  the overwritten arrays remain outside the automatic protection boundary
- FinGrind calls `sqlite3_key()` immediately after `sqlite3_open_v2()`
- FinGrind calls `sqlite3_rekey()` for `rekey-book` instead of routing replacement secrets through
  SQL text
- `rekey-book` preserves one same-directory rollback copy until replacement-passphrase validation
  succeeds, so verification failures restore the pre-rekey file instead of leaving an unverified
  rotated book behind
- crash-interrupted rekeys can leave that rollback artifact on disk; later opens warn about the
  stale encrypted copy so operators can decide whether to recover or delete it
- the supported operator backup path is a closed-book encrypted file copy: stop using the selected
  book, copy the `.sqlite` file to protected storage, preserve the key file separately, and
  restore by replacing the closed `.sqlite` file from that encrypted copy before reopening it
- same-book multi-session access is allowed, but one writer holding `begin immediate` will block
  another writer until SQLite's busy timeout expires and the second writer fails with one busy or
  locked result instead of silently interleaving journal mutations
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
- encrypted-book regression tests now write recognizable sentinel values and assert those strings
  do not appear in the raw database bytes, so mismatched-key coverage is paired with an obvious
  plaintext-leak check
- committed compatibility fixtures under
  [`sqlite/src/test/resources/dev/erst/fingrind/sqlite/fixtures/`](../sqlite/src/test/resources/dev/erst/fingrind/sqlite/fixtures/)
  now record the canonical protected-book format facts directly in fixture metadata and prove that
  the current default protected-book format reopens across test runs, rejects mismatched
  verification deterministically, and remains restorable from one closed-book encrypted copy
  without exposing plaintext

### Protection Boundary

The repository's canonical threat boundary is documented in
[DEVELOPER_SECURITY.md](./DEVELOPER_SECURITY.md). SQLite-specific consequences remain:
- the protected-book contract covers the encrypted SQLite database pages plus the encrypted
  rollback-journal or WAL bytes that SQLite3MC writes for that book
- FinGrind forces `temp_store=MEMORY`; if that policy is weakened, temporary spill artifacts fall
  outside the documented at-rest protection boundary
- `plaintextHeaderSize=0` means the current format does not expose a plaintext SQLite header

## Transaction Model

- one `SqliteBookSession` instance, implemented by one thin `SqlitePostingFactStore` over one
  immutable `SqliteStoreContext` plus one mutable `SqliteStoreLifecycle`, owns at most one open
  native SQLite handle and one explicit ledger-plan transaction/artifact-cleanup state
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
- `BookFormatContract` is the canonical owner of the supported format version
- no upgrade step files are bundled yet because there are no earlier FinGrind on-disk versions
  than the current format `1`

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

Managed runtime targets currently build SQLite 3.53.1 / SQLite3 Multiple Ciphers 2.3.4 from the
vendored amalgamation on macOS and Linux. The public bundle launcher starts its private runtime
with `--enable-native-access=ALL-UNNAMED`, Gradle `Test` and `JavaExec` tasks are configured with
the same native-access flag, generated source-checkout launchers plus the developer raw JAR inherit
that contract automatically from the prepared checkout build, and controlled surfaces resolve the
managed library through `fingrind.bundle.home`, source-checkout discovery, or
`FINGRIND_SQLITE_LIBRARY` as the explicit escape hatch.

Distribution note:
- public bundle archives and the public container image both package a private `jlink` runtime so
  FinGrind's managed SQLite3MC contract does not depend on an ambient host Java installation

Native bridge notes:
- the SQLite symbol arena in
  [`SqliteNativeBootstrap`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteNativeBootstrap.java)
  intentionally lives for the JVM lifetime because the downcall handles outlive any individual book
  session
- native library lookup has no platform-default fallback; it uses extracted bundle home for the
  public launcher, prepared-checkout discovery for generated launchers and developer raw JARs, and
  `FINGRIND_SQLITE_LIBRARY` only as the explicit direct-Java override
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

This is a deliberate architectural correction to the earlier shell-out design, not an accidental
runtime experiment.
