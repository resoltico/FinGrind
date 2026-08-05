---
afad: "5.0.1"
version: "0.62.1"
domain: DEVELOPER_SQLITE
updated: "2026-08-05"
route:
  keywords: [fingrind, sqlite, sqlite3mc, sqlite3 multiple ciphers, ffm, java26, storage, single-book, filesystem-path, key-file, encryption, canonical-schema, strict, trusted-schema, query-only, application-id, user-version, rekey, no-migrations, pair-targets-conflict, source-artifact-identity-duplicated, source-artifact-identity-changed, target-owner-only-required, protected-book-pair-publication-evidence-blocked]
  questions: ["how does fingrind use sqlite now", "why does fingrind use java ffm for sqlite", "how does the sqlite adapter initialize a new protected book", "how does fingrind protect book files", "how does protected-book pair target identity work", "what does source-artifact-identity-changed mean"]
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
- `rekey-book` reads one current passphrase source and generates one fresh destination secret at
  an absent `--new-book-key-file` target; it stages and verifies the re-encrypted copy before
  atomically replacing the selected book
- key files remain the automation-friendly route; stdin and interactive prompt are the supported
  non-file routes
- key files must use POSIX owner-only permissions (`0400` or `0600`) on macOS/Linux or a
  Windows owner-only ACL on Windows
- protected book files and same-directory SQLite sidecars (`-journal`, `-wal`, and `-shm`) must
  satisfy owner-only protected-book admission; FinGrind does not make a caller-selected existing
  live-book, key file, or parent acceptable by permission or ACL repair
- every caller-selected existing live-book or key-file parent and its resolved ancestry is
  validation-only: it must already be real, owner-only, and non-mutable. `open-book` creates a
  missing live-book parent only through atomic POSIX `0700` creation followed by validation; it
  fails closed on ACL-only filesystems rather than attempting a repair
- FinGrind intentionally rejects plaintext CLI passphrase arguments and environment-variable
  passphrase transport
- newly opened books are protected through SQLite3 Multiple Ciphers 2.4.0 using the upstream
  default `sqleet` / `chacha20` cipher
- duplicate idempotency is enforced within the selected book, not globally across files
- one canonical current schema defines every newly initialized book
- the current supported book format is `57`, owned by `BookFormatContract`
- accepted posting facts persist first-class accounting evidence through
  `posting_source_document` and `posting_approval` child tables keyed by posting id
- `inspect-book` exposes one explicit hard-break migration policy for the active format line:
  no in-place upgrade path, no older-format acceptance, and no newer-format acceptance
- FinGrind is in an alpha hard-break line, so schema evolution replaces the current model
  directly and non-current formats are rejected instead of being migrated in place
- `backup-book` exports one verified encrypted backup pair under an independently generated
  `--new-backup-key-file` and appends its exact acknowledgement; `restore-book` verifies that
  backup pair before publishing an absent live-book path, then re-encrypts it under an absent
  generated `--new-book-key-file`; `rekey-book` owns verified staged pair-publication recovery.
  While it holds its maintenance lease, it revalidates the selected live-book digest immediately
  before generated-secret publication and again before book replacement. The lease coordinates
  FinGrind but cannot prevent a same-owner external filesystem write after validation and before
  the operating-system publication call; that interference is completion-uncertain
  `protected-book-pair-publication-uncertain`, not an atomic-replacement guarantee. Each
  completed pair reports `published`, `recovered`, or, for a backup acknowledgement retry,
  `already-published`. `published` and `recovered` also expose immutable final-and-stage evidence
  for both pair members; the acknowledgement-only outcome has no new pair-publication evidence
- every existing maintenance source and target parent is validation-only: before canonicalization,
  FinGrind scans every lexical component from the root through the selected parent without
  following links and rejects any symbolic-link or non-directory component, including a
  direct-parent alias. It then validates a canonical physical directory with private owner-only,
  non-mutable ancestry and never permission- or ACL-repairs it. Only an absent final-target parent
  may be created: FinGrind preflights creation ancestry, atomically creates it with POSIX `0700`,
  then postvalidates the canonical parent and full ancestry. A lifecycle source parent must
  already exist. ACL-only final-target creation fails closed as `artifact-path-invalid` with
  `pathFailure: "atomic-owner-only-protocol-file-creation-unsupported"`; it never creates a
  readable parent and repairs its ACL. A final target leaf may be absent; if present, a regular
  leaf remains subject to the operation's no-replace or replacement policy rather than to path
  normalization. A lifecycle source leaf must already be a regular non-symlink file before
  final-target preparation. FinGrind carries only `canonicalParent.resolve(fileName)` through
  leases, recovery records, and public machine paths. An existing selected source or
  FinGrind-owned recovery artifact that must be inspected is rejected as
  `artifact-path-invalid` with `pathFailure: "target-owner-only-required"` when it is not
  owner-only. A caller-owned ordinary leaf selected as a no-clobber output is never inspected as
  a FinGrind artifact: its operation-specific occupied-target rejection takes precedence.
- initial pair final-target identity admission occurs after lifecycle-source validation and
  final-target-parent admission, before any final target, retained lease-control file, stage,
  capability witness, reservation, claim, or pair-evidence artifact. When both final targets
  exist, the adapter uses `Files.isSameFile`; a proven one-object pair is the public
  `pair-targets-conflict` rejection. For two absent leaves in one physical parent, exact raw leaf
  equality or a collision after canonical Unicode decomposition plus root-locale case mapping is
  the same conflict. Other distinct leaves remain valid when the filesystem admits them. An
  eligible missing private parent may remain after this initial
  admission. The initial refusal creates no final target, retained lease-control file, stage,
  capability witness, reservation, claim, or pair-evidence artifact
- retained pair evidence binds the exact maintenance operation, source identity, canonical final
  targets, generated-secret input identity, and every derived stage to one owner record. Recovery
  accepts only the complete original source, target, and secret inputs for that owner record;
  neither a generic target tuple nor a sibling operation can adopt its stages. A verified pending
  owner record blocks another request through `maintenance-recovery-pending`. Pair errors always
  publish nullable `details.pairPublication.pairPublicationRetention`; when non-null, its two
  `{path,retainedStage}` members bind exactly to the reported final targets, and `null` never
  authorizes cleanup. Evidence that cannot establish a safe final-member state returns the distinct
  `protected-book-pair-publication-evidence-blocked` error with both member states
  `unestablished`, not a recovery instruction. Completion uncertainty has only established
  final-member facts and can be reconciled only by the exact original workflow.
- the full source, target-book, and target-secret workflow scope is held under FinGrind
  maintenance leases before source verification and before pair admission exchanges target
  references for the record-owned derived-stage references. Every selected file-backed source,
  including a selected key file, is a role-tagged member. The members must have distinct physical
  identities; a later duplicate returns `artifact-path-invalid` with
  `source-artifact-identity-duplicated` before target admission. After all source exclusions are
  held, FinGrind revalidates each source against the exact locked physical identity and repeats
  the cross-source uniqueness check; a replacement or substitution returns
  `source-artifact-identity-changed` before it admits a target. This prevents a concurrent
  FinGrind workflow from scavenging, replacing, or being mistaken for the operation's destination;
  it does not claim a globally atomic cross-process filesystem guarantee. `open-book` likewise
  uses exclusive SQLite creation after its early destination check.
- v4 directory reservations use the retained owner-only
  `.fingrind-maintenance-directory-v4.control` file in each admitted physical directory. Existing
  source objects additionally coordinate through private per-user
  `${user.home}/.fingrind-coordination-v4` controls named from a SHA-256 of an explicit physical
  object identity: a POSIX device/inode tuple or a Windows volume/file identifier. This is not a
  path spelling or provider `fileKey` rendering, so every hard-link alias converges. Activity uses
  one of byte slots `0` through `1023`; a source maintenance exclusion holds the whole range. A
  held lock is the sole liveness fact; an unlocked valid control is inert after a crash. Controls
  are never unlinked or reclaimed. v2/v3 directories, directory controls, object controls, and
  legacy lease names are never read, adopted, migrated, repaired, or co-run: their residue,
  malformed or unavailable current controls, and overlapping locks all block safely. Production
  has no configurable alternate coordination root.
- Moving a live installation to v4 is a manual cold cutover, not recovery. Schedule an outage,
  stop and prevent every pre-v4 process and automation, and independently establish that no old
  process remains. Only then archive, on the same secure filesystem, each old per-user v2/v3
  coordination root and every v2/v3 directory control in every affected live, backup, and target
  parent to a private evidence name that is neither an active control name nor a `.lock` name.
  Never delete, adopt, merge, or co-run that residue. If the old process state cannot be proven
  stopped, do not cut over. A Windows handle that prevents renaming is evidence that the outage is
  incomplete; on POSIX, rename is safe only after the independently verified outage.
- legacy plaintext books and other encryption variants are out of scope for the current
  foundation

## Current Adapter Choice

FinGrind's public durable SQLite session surface is one family of narrow workflow views opened
through [`SqliteBookSessions`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteBookSessions.java):
[`SqliteAdministrationSession`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteAdministrationSession.java),
[`SqliteReadSession`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteReadSession.java),
[`SqlitePostingSession`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePostingSession.java),
[`SqliteReportingPeriodCloseSession`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteReportingPeriodCloseSession.java),
[`SqlitePlanReadOnlySession`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePlanReadOnlySession.java),
[`SqlitePlanExecutionSession`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePlanExecutionSession.java).
The package-private backing implementation remains
[`SqlitePostingFactStore`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePostingFactStore.java).

SQLite exposes two disjoint plan-execution capabilities. `SqlitePlanExecutionSession` binds the
signed mutation-capable plan's reads, validation, plan-specific account/tax/posting child writes,
verified posting-commitment projection, final aggregate attestation, and commit-or-rollback
lifecycle to one protected-book session. The coordinator records an
`AttestationPlanChildMutation` only after a child has persisted, then authorizes the one aggregate
operation with its final-only plan authority. `SqlitePlanReadOnlySession`, opened only through
[`SqlitePlanReadOnlySessions`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePlanReadOnlySessions.java),
instead uses native SQLite read-only/query-only/noncreating access and exposes only the stable
read-plan transaction. Its `PLAN_READ_ONLY` open mode defers a missing-book open so the workflow
still emits the canonical plan journal rejection. Neither session inherits the other's authority;
there is intentionally no generic plan dependency bundle, separately injected transaction port, or
ordinary direct-mutation inheritance that could let a child escape its plan transaction.

Current implementation choice:
- use Java 26 FFM to call a configured SQLite shared library directly
- express book access explicitly as
  [`BookAccess`](../contract/src/main/java/dev/erst/fingrind/contract/runtime/BookAccess.java)
- resolve passphrase sources through
  [`SqlitePassphraseResolver`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePassphraseResolver.java)
  into [`SqliteBookPassphrase`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteBookPassphrase.java)
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
- controlled FinGrind surfaces can now pin one audited SQLite 3.53.4 / SQLite3 Multiple Ciphers
  2.4.0 source contract instead of inheriting host-library drift

Observed implementation note:
- we also reproduced a local `sqlite-jdbc` native-library load failure on this Java 26 macOS
  environment during the earlier rewrite, but that was an environment-specific observation rather
  than the primary architecture reason for choosing FFM

## Source Provenance And License

FinGrind treats the upstream SQLite3 Multiple Ciphers project page as the source-of-truth entry
point for design, configuration, and operator guidance:
- project information: [https://utelle.github.io/SQLite3MultipleCiphers/](https://utelle.github.io/SQLite3MultipleCiphers/)
- upstream configuration guidance on URI key transport:
  [https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_uri/](https://utelle.github.io/SQLite3MultipleCiphers/docs/configuration/config_uri/)
- vendored release asset:
  [https://github.com/utelle/SQLite3MultipleCiphers/releases/download/v2.4.0/sqlite3mc-2.4.0-sqlite-3.53.4-amalgamation.zip](https://github.com/utelle/SQLite3MultipleCiphers/releases/download/v2.4.0/sqlite3mc-2.4.0-sqlite-3.53.4-amalgamation.zip)

License and attribution stance:
- SQLite3 Multiple Ciphers is MIT-licensed; the upstream text is copied verbatim in
  [LICENSE-SQLITE3MULTIPLECIPHERS](../LICENSE-SQLITE3MULTIPLECIPHERS)
- bundled original SQLite sources remain in the public domain
- repository attribution and runtime notes live in [NOTICE](../NOTICE)

## Current Runtime Policy

Managed-runtime build, distribution, FFM rationale, and native-bridge invariants are owned by
[DEVELOPER_SQLITE_RUNTIME.md](./DEVELOPER_SQLITE_RUNTIME.md).

## Adapter Composition

The SQLite adapter is split into focused collaborators:
- [`BookAccess`](../contract/src/main/java/dev/erst/fingrind/contract/runtime/BookAccess.java):
  durable book file plus one explicit passphrase-source selection
- [`SqlitePassphraseResolver`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePassphraseResolver.java)
  and [`SqliteBookPassphrase`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteBookPassphrase.java):
  the public adapter seam resolves the selected contract-level source into normalized UTF-8
  passphrase bytes; the owned passphrase model performs best-effort overwrite of its owned buffers
- [`SqliteAdministrationSession`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteAdministrationSession.java),
  [`SqliteReadSession`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteReadSession.java),
  [`SqlitePostingSession`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePostingSession.java),
  [`SqliteReportingPeriodCloseSession`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteReportingPeriodCloseSession.java),
  [`SqlitePlanReadOnlySession`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePlanReadOnlySession.java),
  [`SqlitePlanExecutionSession`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePlanExecutionSession.java),
  [`SqlitePlanReadOnlySessions`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePlanReadOnlySessions.java),
  [`SqliteBookSessionMode`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteBookSessionMode.java),
  and [`SqliteBookSessions`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteBookSessions.java):
  stable public workflow-shaped session views and factory for CLI, tooling, and fuzz harnesses
- [`SqlitePostingFactStore`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePostingFactStore.java):
  thin package-private session wrapper for one thread-confined protected-book boundary; it composes
  one immutable store context plus one mutable lifecycle owner instead of inheriting one wide
  mutable sink
- [`SqliteStoreOpening`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteStoreOpening.java),
  [`SqliteStoreContext`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteStoreContext.java),
  [`SqliteStoreLifecycle`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteStoreLifecycle.java),
  [`SqliteStoreReadOperations`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteStoreReadOperations.java),
  [`SqliteStoreAdministrationMutationOperations`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteStoreAdministrationMutationOperations.java),
  [`SqliteStoreAccountRegistryMutationOperations`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteStoreAccountRegistryMutationOperations.java),
  [`SqliteStorePostingMutationOperations`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteStorePostingMutationOperations.java),
  and [`SqliteClosingMutationOperations`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteClosingMutationOperations.java):
  focused collaborators for open-time ownership transfer, immutable dependency wiring, mutable
  lifecycle state, query/report reads, and independently owned administration, account-registry,
  posting, and reporting-period-close writes
- [`SqliteSessionSecret`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteSessionSecret.java):
  durable session-secret owner that clones one reusable protected-book passphrase and mints
  short-lived working copies for each native open or rekey handoff
- [`SqliteBookAccessRules`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteBookAccessRules.java):
  canonical same-package rule owner for SQLite file-backed key-file access requirements
- [`SqliteBookLifecycleInspectionMapper`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteBookLifecycleInspectionMapper.java)
  and [`SqliteTransactionValidationBook`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteTransactionValidationBook.java):
  focused helpers that keep local lifecycle mapping and validation lookups separate from the outer
  session wrapper instead of layering multiple narrow session-view classes over the same state
- [`ProtectedBookMaintenanceService`](../executor/src/main/java/dev/erst/fingrind/executor/ProtectedBookMaintenanceService.java),
  [`ProtectedBookMaintenanceStore`](../executor/src/main/java/dev/erst/fingrind/executor/spi/ProtectedBookMaintenanceStore.java),
  [`SqliteProtectedBookMaintenanceStore`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteProtectedBookMaintenanceStore.java),
  and [`BookAuditEvent`](../executor/src/main/java/dev/erst/fingrind/executor/bookkeeping/BookAuditEvent.java):
  executor-owned maintenance doctrine plus SQLite-backed verification, staged protected-book
  replacement, immutable retained artifact evidence, and encrypted in-book maintenance-audit
  persistence
- [`RekeyBookResult`](../contract/src/main/java/dev/erst/fingrind/contract/bookkeeping/RekeyBookResult.java):
  explicit result family for passphrase rotation outcomes
- [`SqliteNativeBootstrap`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteNativeBootstrap.java),
  [`SqliteNativeApiLoader`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteNativeApiLoader.java),
  [`SqliteManagedLibraryTargetLocator`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteManagedLibraryTargetLocator.java),
  [`SqliteNativeCompatibilityPolicy`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteNativeCompatibilityPolicy.java),
  and [`SqliteNativeRuntimeMetadata`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteNativeRuntimeMetadata.java):
  split process-scoped bootstrap from configured managed-library selection, then load and enforce
  the SQLite, SQLite3MC, source-identity, and compile-option contract while exposing the loaded
  runtime metadata
- [`SqliteNativeConnections`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteNativeConnections.java),
  [`SqliteNativeKeyConfiguration`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteNativeKeyConfiguration.java),
  [`SqliteNativeDatabaseConfiguration`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteNativeDatabaseConfiguration.java),
  [`SqliteNativeProtectedBookRuntime`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteNativeProtectedBookRuntime.java),
  [`SqliteNativeStatements`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteNativeStatements.java),
  [`SqliteNativeErrors`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteNativeErrors.java),
  and [`SqliteNativeRuntimeActivity`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteNativeRuntimeActivity.java):
  own native connection lifecycle, key/rekey application and validation, protected-book runtime
  and cipher inspection, statement execution and SQLite-native error decoding, and per-book
  activity accounting; writable native opens acquire one v4 physical-object activity slot in the
  private per-user coordination namespace, while read-only opens retain in-process connection
  accounting without creating filesystem control artifacts
- [`SqliteBookKeyFile`](../sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteBookKeyFile.java):
  validates and loads the file-backed passphrase route into the same normalized
  `SqliteBookPassphrase` model
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
- `open-book` creates a missing live-book parent only through atomic POSIX `0700` creation and
  then validates the full owner-only, non-mutable ancestry; it refuses ACL-only filesystem
  creation because no equivalent atomic primitive exists. Existing caller-selected live-book and
  key-file paths remain validation-only and are never permission- or ACL-repaired. It then applies
  the canonical schema, inserts the authoritative `book_meta.initialized_at` marker, and
  initializes a protected SQLite3MC book file
- `open-book`, account declaration/reactivation, posting commit/reversal, and `rekey-book` append
  durable audit rows inside the same protected book instead of relying only on posting provenance
- `post-entry` no longer initializes a book implicitly; a missing or unopened book returns
  `BookNotInitialized`
- read-oriented sessions (`inspect-book`, `list-accounts`, `get-posting`, `list-postings`,
  `account-balance`, `trial-balance`, `account-ledger`, `period-summary`, and `preflight-entry`)
  open SQLite through `SQLITE_OPEN_READONLY` and then enforce `pragma query_only = on`
- no read or mutation session repairs permissions or ACLs on caller-selected existing live-book,
  key-file, or parent paths. Those paths are validated and refused when they violate the contract;
  only absent FinGrind-created artifacts use owner-only creation primitives, which fail closed
  when unavailable
- opening an existing plaintext SQLite file, loading a damaged or truncated protected book, or
  using the wrong passphrase source fails during key validation, but the public CLI classifies
  those cases as the deterministic
  `protected-book-verification-failed` error instead of leaking raw SQLite symptoms such as
  `SQLITE_NOTADB`
- after a protected book opens successfully, a matching FinGrind `application_id` with a
  non-current `user_version` is a different deterministic precondition:
  `unsupported-book-format-version` carries the detected and supported format versions and
  rejects every operational read, report, verification, and live-book maintenance path without
  migration or compatibility reading; `inspect-book` remains the safe state-reporting path
- initialized FinGrind books are stamped with a fixed `pragma application_id` and
  `pragma user_version`, and foreign or unsupported SQLite files are rejected before ordinary book
  reads proceed
- `rekey-book` may retain private pre-final workflow material while it rekeys and verifies a staged
  copy through the native SQLite path. That material remains owner-record-constrained evidence and
  is never a user-managed recovery input
- a process crash or forced stop never authorizes an operator to rename, overwrite, delete,
  recreate, reuse, or adopt retained pair evidence; rerun only the named original operation with
  its complete original source, target, and secret inputs when FinGrind has verified one. Legacy,
  malformed, or internally inconsistent current residue instead fails closed as
  `protected-book-pair-publication-evidence-blocked`, never `maintenance-recovery-pending`
- backup, restore, rekey, and key-generation workflows retain every created stage as immutable
  evidence. The durable owner record constrains its exact targets and stages; filename-shaped
  siblings remain untouched. A completion-uncertain final book-and-key pair is preserved and can
  be reconciled only through `protected-book-pair-publication-uncertain`; evidence that cannot
  establish a safe final-member state is `protected-book-pair-publication-evidence-blocked`
- posting validation is shared between application preflight and transactional SQLite commit, so
  book lifecycle, account-state, duplicate-idempotency, and reversal-lineage rules do not drift
  between the two paths
- committed `posting_fact`, `journal_line`, and `audit_event` rows are append-only at the schema
  layer; direct update/delete mutation is rejected by SQLite triggers
- opened book handles keep `foreign_keys = on`, `trusted_schema = off`, and the expected
  `query_only` setting for the current access mode
- schema bootstrap is intentionally separate from the posting transaction because it is idempotent
  book initialization, not one accounting fact commit
- book-local uniqueness enforces duplicate idempotency durably
- SQLite enforces declared-account durability through the
  `journal_line.account_code -> account.account_code` foreign key
- SQLite also enforces one reversal per target through a partial unique index
- reversal linkage is durable and references `posting_fact(posting_id)` through a foreign key
- bundle-managed and source-checkout-managed runtimes copy the managed library and sibling
  `.sha256` sidecar into a fresh retained owner-only snapshot, retain the verified SHA-256, then
  reopen the snapshot with no-follow access and re-hash it immediately before Java FFM is asked to
  load its pathname. This is a defense-in-depth identity check under the documented
  cooperative/normal filesystem boundary; Java's later pathname-based load is not identity-bound
  to those verified bytes, so FinGrind does not claim proof against arbitrary same-owner
  replacement
- runtime probes distinguish bundle-managed and source-checkout-managed library provenance and
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
  `environment.sqlite.runtime.loadedLibraryPath` as a canonical absolute path,
  `environment.sqlite.runtime.loadedSqliteVersion`,
  `environment.sqlite.runtime.loadedSqlite3mcVersion`,
  `environment.sqlite.runtime.loadedSqliteSourceId`,
  `environment.storage.bookProtectionMode`, and
  `environment.storage.defaultProtectedBookFormat.cipher`,
  `environment.storage.defaultProtectedBookFormat.legacyMode`,
  `environment.storage.defaultProtectedBookFormat.pageSize`,
  `environment.storage.defaultProtectedBookFormat.reservedBytes`,
  `environment.storage.defaultProtectedBookFormat.kdfIter`, and
  `environment.storage.defaultProtectedBookFormat.plaintextHeaderSize` through the `environment`
  command
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
  errors such as `protected-book-verification-failed`, `unsupported-book-format-version`,
  `invalid-book-key-file`, or
  `interactive-prompt-unavailable`
- SQLite-native, bridge, or filesystem runtime failures that are not deterministic invariant
  breaches become CLI `storage-runtime-failure`
- managed runtime bootstrap failures become `managed-runtime-failure`
- SQLite `CONSTRAINT_CHECK` failures are treated as internal persistence-contract breaches that
  should have been rejected before commit and therefore publish the opaque `internal-error`
  envelope instead of a user-repairable storage hint
- uncategorized software defects above those families also publish the opaque `internal-error`
  envelope instead of one raw runtime message

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
- `rekey-book` may retain private pre-final workflow material until staged-copy verification
  completes, but that material remains owner-record-constrained evidence and is never a
  user-managed recovery input
- crash-interrupted rekeys retain external pair evidence for the named original operation.
  Operators never rename, overwrite, delete, recreate, reuse, or otherwise alter that evidence;
  legacy, malformed, or internally inconsistent current residue is fail-closed as
  `protected-book-pair-publication-evidence-blocked`, never
  `maintenance-recovery-pending`, rather than an operator-recoverable workflow
- `backup-book` is the supported operator export path for a closed book and emits one verified
  encrypted backup pair under an independently generated backup key; `restore-book` verifies that
  pair before publishing an absent live-book path, refuses every existing or racing destination,
  and re-encrypts the restored live book under a new destination key; it has no replacement option
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

- one narrow SQLite workflow session, implemented internally by one `SqlitePostingFactStore` over
  one immutable `SqliteStoreContext` plus one mutable `SqliteStoreLifecycle`, owns at most one
  open native SQLite handle and one explicit ledger-plan transaction and artifact-lifecycle state
- read methods reuse that handle when it exists
- commit uses SQLite's `begin immediate` transaction mode and performs ordinary duplicate checks
  before insert on the same native handle
- ordinary duplicate outcomes are decided before `insert into posting_fact`, not inferred after a
  rolled-back write failure
- commit rolls back on failure and closes the handle when the session closes

This keeps ordinary duplicate outcomes deterministic without parsing plain-language SQLite error
text or re-querying after rollback.

## Canonical Schema Policy

- the canonical schema resource is
  [`book_schema.sql`](../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
- the canonical schema uses SQLite `STRICT` tables for `book_meta`, `account`, `posting_fact`, and
  `journal_line`, plus `audit_event`
- there are no versioned migration file names such as `V1__...`
- `BookFormatContract` is the canonical owner of the supported format version
- current alpha evolution is one explicit hard-break line: the canonical schema advances in
  place, `inspect-book` reports the active no-upgrade migration policy, and non-matching formats
  are rejected instead of being upgraded through legacy-compatibility code

## Why FFM-Backed SQLite

The FFM rationale and native-bridge invariants are owned by
[DEVELOPER_SQLITE_RUNTIME.md](./DEVELOPER_SQLITE_RUNTIME.md).
