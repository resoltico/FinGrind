# SQLite Agent Protocol

**Version:** 1.0.0
**Updated:** 2026-06-13
**Baseline:** SQLite **3.53.4**
**Inherits:** [.codex/UNIVERSAL_ENGINEERING_CONTRACT.md](./UNIVERSAL_ENGINEERING_CONTRACT.md) v3.0.0+
**Layered by:** [.codex/AGENTS_SQLITE3MC.md](./AGENTS_SQLITE3MC.md) for encrypted databases (SQLite3 Multiple Ciphers).
**Scope:** projects that build, vendor, link, wrap, configure, distribute, test, or operate SQLite 3.53.4 — C and C++ integrations, amalgamation builds, static or shared library packaging, embedded applications, CLIs, services, language bindings (JNI/JNA, Python/Rust/Node/.NET/Java/Kotlin), SQL migrations, PRAGMA/URI configuration, WAL/journal behavior, build flags, and cross-platform distribution. For at-rest encryption, load the SQLite3MC layer in addition to this file.

## 0. Scope and inheritance

This protocol inherits the Universal Engineering Contract. The universal contract defines the meta-questions every change must answer — Truth, Evidence, Consequence, Invariant, Justification, Re-cueing — and frames the agent as a *transient theory-holder*. Apply the universal contract before any rule below; do not restate it here. When SQLite is used from Java, Kotlin, Python, Rust, C, C++, or another runtime, apply this protocol in addition to the relevant language protocol.

This protocol adds SQLite-specific content for which the universal contract is intentionally silent: file-format state, native-library identity across compile and runtime, SQL/SQLite version compatibility, FFI safety, and durability behavior.

**Primary objective:** preserve data integrity, SQLite compatibility, build reproducibility, and clear ownership of database/file-format contracts.

**Optimization order:**

```text
data integrity → file-format compatibility → source-of-truth clarity → portability → observability → performance where measured → terseness
```

Convenience loses to data safety. Local build success loses to runtime link correctness.

### 0.1 SQLite 3.53 tacit gaps

Per the Naurian frame, some theory the agent typically does not bring in cold and must surface rather than paper over. Watch especially for:

- Whether the headers at compile time, the static or shared library linked at build time, and the dynamic library actually loaded at runtime are the same SQLite version. A single file will not answer this; the agent must verify across phases.
- Whether the application loads the intended SQLite, or quietly resolves to a different system SQLite. "Drop-in replacement" is a code property, not a runtime guarantee.
- Whether SQLite 3.52.0 (withdrawn upstream) is still pinned anywhere as a fallback baseline.
- Whether code or migrations silently require a 3.53.4 SQL feature that an older deployed runtime will not provide.

Where the answer is not derivable from code, history, or conversation, surface the gap explicitly; do not assume the convenient answer.

---

## 1. Repository intake before touching SQLite surfaces

Before editing anything related to SQLite, determine the repository's actual integration model.

Inspect the relevant subset of:

- vendored source files, especially any shipped `sqlite3.c` / `sqlite3.h` / `sqlite3ext.h` copies, patches, generated amalgamation scripts, and third-party manifests;
- version pins, release tags, commit hashes, checksums, package metadata, lock files, SBOM entries, release notes, and any source-ID assertions;
- build systems: Autotools, CMake, Premake, GNU Make, MSBuild/Visual Studio, Meson, Bazel, Gradle, Cargo build scripts, Python extension builds, npm native builds, or project-specific wrappers;
- compiler and linker flags, `SQLITE_*` options, enabled extensions, ICU/ZLIB/MINIZ configuration, and platform-specific defines;
- whether the repository links SQLite directly, uses the amalgamation, or consumes a language binding that bundles SQLite;
- runtime library resolution: static vs dynamic linking, DLL/shared-object search paths, rpath/install-name, package manager behavior, container images, Android/iOS/WASM targets, and CI artifacts;
- SQL and API usage, including backup APIs and language-binding equivalents;
- database lifecycle: initial creation, open, migration, attach/detach, backup, restore, VACUUM, WAL checkpointing, compaction, corruption handling, and deletion;
- file-format assumptions: page size, `user_version`, schema migrations, and database compatibility fixtures;
- journaling and temp behavior: rollback journal, WAL, shared memory files, temporary tables, in-memory databases, temp-store configuration, and file-permission policy;
- tests and evidence: fixture files, migration tests, cross-platform CI, sanitizer runs, Valgrind, fuzzers, SQL logic tests, and production observability;
- the universal contract's six concerns (truth, evidence, consequence, invariant, justification, re-cueing) for the touched surface.

Classify the touched surface before designing the change:

- **Vendored native dependency:** version, patches, compile options, and source provenance are contracts.
- **Application database:** file format, migrations, backups, durability, and restore behavior are contracts.
- **Published binding/package:** ABI/API, binary compatibility, platform wheels/artifacts, runtime linking, docs, examples, and package metadata are contracts.
- **Internal wrapper:** error handling, connection lifecycle, and safe defaults are contracts.
- **CLI/tooling:** command-line flags, stdout/stderr shape, exit codes, script compatibility, and non-interactive behavior are contracts.
- **Embedded/mobile/WASM build:** target support, compile flags, filesystem/VFS behavior, memory constraints, and package size are contracts.

---

## 2. Change loop in SQLite terms

### 2.1 Minimum system map

For every non-trivial SQLite change, apply the universal contract §1 system map (Truth / Evidence / Consequence / Invariant / Justification / Re-cueing) to the touched surface. SQLite-specific anchors:

- **Truth:** canonical owner of SQLite source version, compile options, binding/runtime package version; canonical owner of database schema, migrations, page format, and fixtures; derived/generated copies (amalgamation, headers, wrappers, package metadata, docs, CI images, lock files).
- **Evidence:** native build correctness, runtime link correctness, migration, backup/restore, language binding behavior; missing feedback worth adding.
- **Consequence:** direct (callers, wrappers, SQL scripts, migrations, bindings, tests, packaging, CLI tools, deployment images); indirect (stored database files, backups, restore tools, support workflows, monitoring, user data, release process).
- **Invariant:** data, file-format, ABI/API, migration, or compatibility rule that must remain true.
- **Justification:** why each page-size / compile-option / journal-mode choice is the way it is, and which are inherited rather than deliberately chosen. If the answer is not available, surface that gap.
- **Re-cueing:** where the learned theory belongs — build manifest, test fixture, migration note, wrapper API, comment, runbook, AFAD-managed doc, release checklist, CI assertion. Flag the parts that cannot be written down, and who currently holds them.

Keep this lightweight for low-risk edits. Do not skip it for changes that affect persisted files, build flags, runtime linking, or migrations.

### 2.2 Red → Green → Refactor

Per universal contract §2. SQLite-typical "smallest failing proofs": open/read/write roundtrip; SQL migration test; native build/link test; language-binding integration test; WAL/journal/backup/restore test; sanitizer or memory-leak reproduction; CLI invocation with deterministic output mode; cross-version fixture.

Then make the smallest coherent implementation and immediately refactor until the touched surface has clearer ownership, fewer hidden states, and better verification.

### 2.3 Narrow-to-wide verification

Per universal contract §2 and §7 (Feedback must match risk). For SQLite, widening usually means verifying both compile-time and runtime facts: the code compiled against the intended headers and also loaded the intended library at runtime. The two are independent; a green compile-time check does not prove the runtime answer.

### 2.4 Root-cause fixes only

Per universal contract §0 and §2 (read the actual failure). When verification fails, distinguish among SQLite-specific root causes: stale generated source, mixed headers/library, runtime library shadowing, unsupported SQL, file permissions, WAL/journal mode, platform target, or actual corruption.

Do not:

- swallow SQLite errors or collapse them into vague application errors;
- mix SQLite headers from one version with a different runtime library;
- regenerate or edit amalgamation artifacts without updating the canonical generation path;
- change page sizes or compile options without a migration and fixture evidence.

---

## 3. Baseline posture: SQLite 3.53.4

### 3.1 Version baseline

For repositories governed by this protocol, assume SQLite **3.53.4**. Use the repository's pinned version when it is more specific. Do not upgrade or downgrade SQLite without a compatibility judgment, migration-risk assessment, and verification plan.

The SQLite 3.53.x line fixes the WAL-reset database corruption bug (the fix landed in 3.53.0 and is carried forward in 3.53.4). Do not downgrade to a pre-fix SQLite baseline without explicitly accepting the risk and recording the justification (per universal contract §1.5).

### 3.2 SQLite 3.53.4 feature posture

Use SQLite 3.53.4 capabilities only when the deployed runtime is guaranteed to be SQLite 3.53.4 or newer.

Notable 3.53 behavior for agents:

- `ALTER TABLE` can add and remove `NOT NULL` and `CHECK` constraints. Use this only when migration compatibility is acceptable.
- `REINDEX EXPRESSIONS` can rebuild expression indexes. Prefer it when repairing stale expression-index state rather than inventing application-level workarounds.
- The self-healing index feature addresses stale expression-index problems, but it does not replace tests for migration and query correctness.
- `json_array_insert()` and `jsonb_array_insert()` SQL functions are available in the 3.53 baseline.
- The body of `TEMP` triggers may modify and/or query tables in the main schema.
- The CLI output defaults changed for interactive sessions through QRF (box-drawing result formatting). Tests and scripts must set explicit output modes instead of relying on operator-oriented defaults.
- Bare semicolons at the end of dot-commands are silently ignored. Treat CLI script compatibility deliberately.
- New C interfaces such as `sqlite3_str_truncate()`, `sqlite3_str_free()`, `sqlite3_carray_bind_v2()`, `SQLITE_PREPARE_FROM_DDL`, `SQLITE_UTF8_ZT`, `SQLITE_LIMIT_PARSER_DEPTH`, and `SQLITE_DBCONFIG_FP_DIGITS` are available only when the runtime really is 3.53+.
- Floating-point text conversion now rounds by default to 17 significant digits instead of the previous 15. Review golden outputs, text dumps, hash inputs, and deterministic serialization tests.

Do not write code or migrations that silently require 3.53.4 if production, tests, system packages, or bundled artifacts may still load an older SQLite.

### 3.3 SQLite 3.52 warning

SQLite 3.52.0 was withdrawn upstream; SQLite 3.53.0 is its re-release with the stale-expression-index fixes. Do not select SQLite 3.52.0 as a fallback baseline. If a repository already contains that version (see §0.1), surface the issue and prefer moving to SQLite 3.53.4 or a project-approved fixed baseline.

---

## 4. Canonical ownership and provenance

### 4.1 One owner for version and build facts

Per universal contract §5 (canonical ownership of contract facts). For SQLite, the contract facts that need a single owner include: SQLite source version, release tag, commit hash, checksums, compile flags, enabled extensions, and platform artifact versions.

Acceptable owners include: a third-party dependency manifest; a vendoring manifest; a build-system version catalog; a lock file plus package metadata; a dedicated `third_party/sqlite/README` or manifest; a generated-source script with checksum assertions.

Do not hard-code the SQLite source ID or compile options independently across build scripts, docs, wrappers, and tests. Derive, generate, or validate secondary surfaces from the canonical owner.

### 4.2 Provenance checks

When adding or updating SQLite:

- use an authoritative upstream release, source archive, package, or repository tag;
- record the SQLite version and source ID;
- verify checksums or signed provenance when the repository supports it;
- preserve local patches as small, named, reviewable patches;
- update package metadata, lock files, SBOM, docs, and CI images together;
- run fixture tests against existing databases before release.

If the repository uses prebuilt binaries, verify that binary provenance and compile options are inspectable. Opaque binaries are a supply-chain and compatibility risk.

### 4.3 Header/library/runtime coherence

The following must agree unless the repository has an explicit compatibility shim:

- headers used at compile time;
- static or shared library linked at build time;
- dynamic library loaded at runtime;
- package metadata;
- `sqlite3_libversion()` and `sqlite3_sourceid()` observations;
- compile-option observations such as `PRAGMA compile_options` or `sqlite3_compileoption_get()`;
- language-binding reported versions.

A common failure mode — and the headline tacit gap from §0.1 — is compiling against the intended SQLite headers while loading a different system SQLite library at runtime. Always verify runtime identity when touching packaging, dynamic linking, containers, or language bindings.

---

## 5. Build, linking, and packaging discipline

### 5.1 Amalgamation discipline

When using the amalgamation:

- treat the generated amalgamation as derived unless the repository explicitly vendors it as the source of truth;
- do not manually edit generated amalgamation code except for clearly named, documented emergency patches;
- keep headers, source, generated files, build flags, and docs in sync;
- preserve a reproducible regeneration path;
- validate the resulting source ID, version, and compile options.

### 5.2 Compile-time options

Compile-time options are contract facts. Changing them can alter SQL availability, file behavior, performance, and compatibility.

Pay special attention to:

- `SQLITE_TEMP_STORE`;
- `SQLITE_SECURE_DELETE`;
- `SQLITE_USE_URI`;
- enabled extensions such as FTS, JSON, RTREE, GEOPOLY, CARRAY, CSV, SHA3, UUID, FILEIO, REGEXP, SERIES, and user authentication;
- platform-specific flags for WASM, Android, Windows, or cross-compilation.

Changing compile options requires tests and documentation because runtime SQL behavior and file handling may change even when application source code does not.

### 5.3 Platform-specific builds

For Windows, verify architecture naming, CRT expectations, DLL placement, `.lib` import libraries, Visual Studio/MSBuild files, and MinGW/GNU Make variants.

For Linux and macOS, verify Autotools/CMake or project-specific build output, install names, rpath, shared-library versioning, pkg-config files, and container images.

For Android/iOS/mobile, verify ABI splits, bundled native libraries, filesystem behavior, and backup behavior.

For WebAssembly, verify VFS behavior (including `opfs` and `opfs-wl`), exported C APIs, memory model, JS glue, and OPFS or browser storage behavior.

For language bindings, verify both the native artifact and the high-level package. The package version alone is insufficient evidence.

---

## 6. SQLite API, SQL, and migration discipline

### 6.1 SQLite error handling

Expose enough SQLite detail to debug real failures.

Prefer preserving:

- SQLite primary and extended error codes;
- connection/path context;
- operation phase: open, migrate, query, backup, checkpoint;
- whether failure was corrupt file, permission failure, lock contention, or runtime link mismatch.

Do not convert all SQLite failures into generic booleans or generic exceptions.

### 6.2 SQL feature compatibility

SQLite SQL compatibility is a runtime contract.

Before using a 3.53.4 SQL feature in migrations or generated SQL, verify that all deployment targets load SQLite 3.53.4 or newer.

Be especially cautious with: `ALTER TABLE` constraint changes; `REINDEX EXPRESSIONS`; JSONB functions; temp triggers touching the main schema; query plans that rely on new optimizer behavior; deterministic text output involving floating-point values.

If a repository supports multiple SQLite baselines, write migrations and SQL to the lowest supported runtime or guard/version-check the new feature.

### 6.3 CLI scripts and golden outputs

SQLite 3.53 changed reader-oriented CLI formatting through QRF.

For tests and automation:

- set `.mode`, `.headers`, `.nullvalue`, `.separator`, and other output controls explicitly;
- avoid comparing default interactive output;
- quote dot-commands deliberately;
- test batch and non-interactive behavior separately from interactive usability.

### 6.4 Generated code and migrations

If SQL is generated by an ORM, migration tool, code generator, or binding:

- update the generator or schema source of truth, not only generated SQL;
- regenerate in a deterministic path;
- test generated migrations against real fixtures;
- preserve `user_version`, migration history, and compatibility checks.

---

## 7. Database-file, journal, WAL, temp, and backup safety

### 7.1 WAL and rollback journals

When a database uses WAL or rollback journaling:

- test checkpoints, crash recovery, and reopen behavior;
- preserve file permissions for `-wal`, `-shm`, and journal files;
- avoid deleting sidecar files as a substitute for proper checkpoint/recovery logic;
- test multiple connections if the application uses them.

SQLite 3.53 fixes a WAL-reset corruption bug, but this does not remove the need for connection, checkpoint, and backup discipline.

### 7.2 Backup, restore, VACUUM, and export

Rules:

- use SQLite backup APIs, `VACUUM INTO`, or application-specific copy flows deliberately;
- check whether `VACUUM INTO` target URI parameters affect the generated database copy;
- test restore from real fixtures, not only creation of new databases;
- document and test whether backups preserve page size and format.

### 7.3 File permissions and deletion

Preserve or improve:

- restrictive permissions on database, WAL, SHM, journal, backup, and temp directories;
- secure deletion policy where the repository relies on it;
- cleanup of temporary exports and test fixtures;
- platform-specific backup exclusion where applicable.

---

## 8. Language binding and FFI rules

### 8.1 Apply both protocols

When SQLite is used through a language binding, use this protocol plus the relevant language protocol. Examples:

- Java/JDBC or JNI/JNA: apply Java protocol and verify native library loading, classpath/resource packaging, and thread/connection lifecycle.
- Kotlin/SQLDelight or JVM/native wrappers: apply Kotlin protocol and verify Gradle metadata, generated database code, and native packaging.
- Python/APSW-style bindings or extension modules: apply Python protocol and verify wheels, ABI, free-threaded CPython posture, and runtime native identity.
- Rust FFI or crates bundling SQLite: apply Rust protocol and verify `build.rs`, `links`, bindgen output, `unsafe` boundaries, and feature flags.
- Node/Electron/native modules: verify prebuilds, Electron ABI, install scripts, and runtime platform selection.
- .NET/native bundles: verify RID-specific packaging and native asset resolution.

### 8.2 FFI safety

For FFI surfaces:

- treat SQLite handles, statement handles, allocated strings, and callback pointers as ownership-sensitive;
- pair every allocation/free convention correctly;
- define thread ownership and callback threading;
- prevent exceptions/panics from crossing C ABI boundaries;
- test with sanitizers where practical;
- document safety preconditions in the native language's idiom.

---

## 9. Testing and verification

### 9.1 Native verification

Use the repository's exact commands. Where no commands exist, useful checks may include:

```text
native build for each supported platform/configuration
runtime sqlite3_libversion() / sqlite3_sourceid() assertion
PRAGMA compile_options assertion
unit/integration tests using the packaged artifact
ASan/UBSan/Valgrind leak checks where feasible
cross-platform CI smoke tests
package install/uninstall tests
```

For release artifacts, test the installed package, not only the build-tree binary.

### 9.2 Compatibility fixtures

Maintain real fixture files when compatibility matters: current-format fixture; old application-version fixture; corrupted/truncated fixture where recovery behavior matters; WAL/journal fixture when sidecar handling matters.

Do not replace all fixture tests with mock-level tests. The file format is the contract.

### 9.3 Concurrency and durability tests

If the application uses multiple connections, WAL, background workers, or concurrent readers/writers, add or preserve tests for: multiple connections; lock contention and busy timeouts; WAL checkpoint behavior; crash/restart or process-kill recovery where feasible; backup during active use; thread ownership rules in the language binding.

### 9.4 Performance tests

Measure before optimizing. Performance-sensitive changes should consider: page size; cache size; WAL vs rollback journal; synchronous mode; hardware acceleration and target CPU features; binding overhead; query planner changes in SQLite 3.53.4.

Do not weaken durability or compatibility for unmeasured performance claims.

---

## 10. Deletion and blast-radius rules

Per universal contract §8 (deletion and simplification require proof). SQLite-specific blast-radius surfaces beyond the universal list:

- native source files and generated amalgamation paths;
- headers and exported symbols;
- package artifacts, installers, Docker images, mobile bundles, and WASM glue;
- static and dynamic link references;
- language bindings and generated wrappers;
- SQL migrations and CLI scripts;
- fixtures and support tools;
- docs, examples, runbooks, and release checklists;
- production data files and backups.

Removing a compile option or wrapper method can strand existing databases. Treat such deletion as a data-migration decision, not cleanup. Naur's "amorphous additions" warning applies in reverse: a deletion made without the file-format theory destroys structure that *looks* redundant but is in fact load-bearing for some existing on-disk file the agent has never seen.

---

## 11. Documentation and re-cueing

Use `.codex/PROTOCOL_AFAD.md` for docs that describe SQLite integration, public APIs, migrations, operational procedures, or code/documentation synchronization.

Per universal contract §1.6 (re-cueing), preserve the cues that let the next reader rebuild the relevant slice of theory. SQLite-specific homes: version/build facts in the canonical dependency manifest; compile options in build manifests and CI assertions; compatibility fixtures in tests; operational recovery in runbooks.

The repository root `README.md` remains a storefront per `AGENTS.md` §4.

---

## 12. Completion checklist

The universal contract §10 (stop conditions) covers the cross-language stops, and §9 defines the agent output template. The checks below are SQLite-specific additions; do not duplicate the universal output template here.

```text
Baseline:
- Did I verify the intended SQLite version at build time AND runtime?

Truth:
- Did I preserve one canonical owner for version, compile options, and migration state?

Evidence:
- Did I verify against real fixtures, not only freshly created scratch databases?

Consequence:
- Did I trace packaging, linking, language bindings, stored files, backups, and support tools?

Invariant:
- Did data integrity, ABI/API compatibility, and migration safety remain intact?

Justification:
- Can I explain why each touched page-size, journal-mode, or compile-option choice is the way it is — or have I surfaced that as a known gap?

Re-cueing:
- Did I update tests, fixtures, build assertions, docs, runbooks, or comments where the learned theory belongs?
```

Do not claim completion if runtime library identity is unverified or existing database compatibility is unknown.
