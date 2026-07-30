---
afad: "5.0.1"
version: "0.62.0"
domain: DEVELOPER_SQLITE_RUNTIME
updated: "2026-07-30"
scope:
  paths: [build.gradle.kts, contract/src/main/resources/dev/erst/fingrind/contract/protocol/managed-sqlite-contract.json, sqlite, scripts]
  symbols: [verifyManagedSqliteSource, prepareManagedSqlite, SqliteNativeBootstrap, SqliteRuntime]
route:
  keywords: [fingrind, sqlite, sqlite3mc, managed-runtime, runtime-provenance, ffm, native-access, bundle, docker, jazzer]
  questions: ["how does FinGrind build and verify managed SQLite", "why does FinGrind use FFM-backed SQLite", "which SQLite runtime do bundles and containers use"]
---

# SQLite Runtime Build And Native Bridge Reference

**Purpose**: Define the managed SQLite build, distribution, and native-bridge contract.
**Companion reference**: [DEVELOPER_SQLITE.md](./DEVELOPER_SQLITE.md) owns storage semantics and
adapter composition; [DOC_03_SqliteRuntimeAndSessions.md](./DOC_03_SqliteRuntimeAndSessions.md)
owns the public runtime and session API reference.

## Current Runtime Policy

- Root Gradle verification, the nested Jazzer build, `:cli:run`, GitHub workflows, and the Docker
  image build the vendored official SQLite3 Multiple Ciphers 2.4.0 amalgamation under
  [third_party/sqlite/sqlite3mc-amalgamation-2.4.0-sqlite-3530400/](../third_party/sqlite/sqlite3mc-amalgamation-2.4.0-sqlite-3530400).
- [`verifyManagedSqliteSource`](../build.gradle.kts) verifies the pinned upstream manifest,
  including the amalgamation and companion headers, with LF-normalized digests before any managed
  native library is used. Checkout line endings and header drift therefore cannot silently change
  runtime provenance.
- [`managed-sqlite-contract.json`](../contract/src/main/resources/dev/erst/fingrind/contract/protocol/managed-sqlite-contract.json)
  owns the managed SQLite version, source ID, required and forbidden compile options, and
  secure-memory requirement. Build logic, runtime discovery, bundle metadata, and shell
  verification derive from this resource rather than duplicating private literals.
- [`prepareManagedSqlite`](../build.gradle.kts) compiles the host-native shared library from that
  source and stages it in the prepared source-checkout layout. The independent nested `jazzer/`
  build mirrors the same contract so fuzzing and regression replay cannot drift from it.
- The Docker image verifies the same vendored source hash, compiles with flags derived from the
  same contract, launches through `fingrind.bundle.home`, and resolves the packaged native library
  from `/opt/fingrind/lib/native/`.
- Public bundles likewise set `fingrind.bundle.home` and resolve their managed library only from
  `lib/native/` in the extracted bundle. The source-checkout wrapper refreshes its raw JAR,
  runtime manifest, and managed runtime when the checkout has moved past the prepared build.
- `./scripts/direct-java-cli.sh` and `./scripts/direct-java-cli.ps1` are the supported
  non-bundle Java entrypoints. They use the Gradle-owned Java 26 executable, prepare the managed
  runtime, grant native access only to module `fingrind`, and refresh stale checkout artifacts.
- `:cli:bundleCliArchive` is the public-artifact packaging entrypoint. `:cli:shadowJar` packages
  Java only; standalone local verification must run `prepareManagedSqlite` first, after which a
  JAR beneath the prepared checkout resolves the managed library automatically.

## Why FFM-Backed SQLite

FinGrind calls its managed SQLite3MC runtime through Java 26 FFM because it eliminates an external
`sqlite3` dependency and subprocess quoting, retains one native handle for real commit-time
transaction scope, exposes typed SQLite results, and keeps the design intentionally SQLite-specific
without an ORM, generic SQL abstraction, or JNI glue.

Managed targets build SQLite 3.53.4 / SQLite3 Multiple Ciphers 2.4.0 on macOS and Linux. Bundle,
container, source-checkout, and direct-Java launchers grant native access only to module
`fingrind`; selected Gradle `Test` and `JavaExec` owners retain explicit classpath-era native-access
flags because they execute from the unnamed module. Controlled surfaces resolve managed libraries
only through `fingrind.bundle.home` or source-checkout discovery. Public bundle archives and the
public container image package a private `jlink` runtime, so they never depend on ambient host Java.

## Native Bridge Invariants

- `SqliteNativeBootstrap` retains its SQLite symbol arena for the JVM lifetime because downcall
  handles outlive individual book sessions. Lookup has no platform-default fallback: public
  launchers use extracted bundle home, while generated and direct-Java launchers use prepared
  checkout discovery.
- Initialization validates both SQLite and SQLite3MC versions and required compile-option
  hardening before any book operation. Key application precedes schema statements or pragma
  configuration.
- Opened sessions pin `journal_mode=DELETE`, `synchronous=EXTRA`, `secure_delete=ON`,
  `temp_store=MEMORY`, `foreign_keys=ON`, and `trusted_schema=OFF`, rejecting drift rather than
  trusting host defaults. [ADR_SQLITE_JOURNAL_MODE.md](./ADR_SQLITE_JOURNAL_MODE.md) records the
  journal-mode decision.
- Writable-session activity and destructive maintenance use retained v4 owner-only controls and
  held locks rather than PID metadata or reclaimable markers. Directory reservations protect exact
  target admission; object controls use explicit POSIX device/inode or Windows volume/file identity
  so aliases converge. Invalid, unavailable, retired, or held controls fail closed. Native atomic
  owner-only creation and exact-handle validation apply only to FinGrind-owned controls; caller
  paths and generic stage or evidence artifacts remain validation-only and fail closed when their
  boundary cannot be established.
- Bound text uses SQLite's `SQLITE_TRANSIENT` lifetime contract. Error messages and version strings
  read exact C-string lengths; stale-handle and close failures use `sqlite3_errstr(resultCode)`;
  `sqlite3_exec` failures prefer SQLite's owned error buffer and otherwise use that same fallback.
- Ordinary session close attempts `sqlite3_shutdown()` after the process-wide active-handle count
  reaches zero. FinGrind does not rely on a JVM shutdown hook for that cleanup.
