---
afad: "4.0"
version: "0.60.0"
domain: DEVELOPER_SECURITY
updated: "2026-07-11"
route:
  keywords: [fingrind, security, threat-boundary, protected-book, sqlite3mc, key-lifecycle, runtime-provenance, ciphertext, passphrase, compile-options]
  questions: ["what is the fingrind security model", "what does protected-book-verification-failed mean", "what security boundary does fingrind promise", "how does fingrind handle passphrases and sqlite runtime identity"]
---

# Security Model Reference

**Purpose**: Capture the current FinGrind security model, threat boundary, secret-transport rules,
runtime-identity rules, and protected-book failure semantics in one canonical developer reference.
**Prerequisites**: Familiarity with [DEVELOPER_SQLITE.md](./DEVELOPER_SQLITE.md) and
[USER_CLI.md](./USER_CLI.md).

## Canonical Security Surfaces

FinGrind's current security model is built from four contract owners:
- one explicit `BookAccess` tuple: durable book path plus one selected passphrase source
- one managed SQLite runtime contract: SQLite 3.53.3 / SQLite3 Multiple Ciphers 2.3.6 plus the
  required compile options `THREADSAFE=1`, `OMIT_LOAD_EXTENSION`, `TEMP_STORE=3`,
  `SECURE_DELETE`, the forbidden compile option `USE_URI`, and secure-memory support enabled with
  `SQLITE3MC_SECURE_MEMORY=1`
- one connection-hardening contract: `secure_delete=on`, `temp_store=memory`, and
  `memory_security=fill` on every opened SQLite handle
- one persisted protected-book format contract: `cipher=chacha20`, `legacyMode=false`,
  `pageSize=4096`, `reservedBytes=32`, `legacyPageSize=4096`, `kdfIter=64007`, and
  `plaintextHeaderSize=0`
- one deterministic failure contract for verification-time book mismatch:
  `protected-book-verification-failed`

Those facts are not independent knobs today. FinGrind's deliberate stance is one managed runtime,
one protected-book profile, and one explicit passphrase-source model rather than a menu of
cipher, KDF, or transport variants.

## Threat Boundary

What FinGrind protects at rest:
- encrypted SQLite book pages
- encrypted rollback-journal and WAL bytes for that book
- absence of temporary spill files under the supported runtime because SQLite temp storage is kept
  in memory

What FinGrind does not protect automatically:
- decoded query results in process memory
- the durable session-scoped passphrase copy that remains in memory until the owning SQLite
  session closes or rotates to a replacement secret
- short-lived working passphrase copies between native handoff and best-effort heap overwrite
- any heap-resident secret copies the JVM GC, heap dump tooling, or crash handling may preserve
  beyond the specific arrays FinGrind overwrites
- crash dumps, live debuggers, or host-level memory inspection
- copied backups, exported JSON, CSV, or PDF artifacts
- stale `*.rekey-rollback-*.sqlite` artifacts left behind by an interrupted rekey before the next
  operator review
- key files stored beside the book file
- any operator-modified runtime that weakens the canonical temp-store or compile-option contract

`plaintextHeaderSize=0` is deliberate. FinGrind does not expose a partially plaintext SQLite
header. If that value changes in the future, the header bytes become a newly exposed metadata
surface and the change must be treated as a real threat-boundary expansion, not a cosmetic format
change.

## Secret Transport And Lifecycle

Supported passphrase routes:
- `--book-key-file`
- `--book-passphrase-stdin`
- `--book-passphrase-prompt`

Rejected passphrase routes:
- plaintext CLI passphrase arguments
- environment-variable passphrase transport
- SQLite URI `key=` or `hexkey=` transport
- SQL-text key transport such as `PRAGMA key`, `PRAGMA rekey`, or other SQL strings that embed
  secrets

Current lifecycle rules:
- FinGrind normalizes one trailing line ending away from key-file or stdin passphrase input,
  validates UTF-8, rejects empty secrets, and rejects key-file, standard-input, or interactive
  prompt payloads larger than 4096 bytes after UTF-8 normalization
- the SQLite adapter applies secrets through native `sqlite3_key()` and `sqlite3_rekey()`, not
  SQL text
- `SqliteSessionSecret` owns one durable session-scoped passphrase copy so the store can reopen
  or rekey without asking the caller to re-resolve the secret on every operation
- each native open or rekey call borrows one working passphrase copy from that durable session
  secret and best-effort overwrites the working copy immediately after native handoff
- the durable session copy is best-effort overwritten when the owning session closes or rotates to
  a new secret
- key files must remain owner-only (`0400` or `0600` on POSIX hosts, owner-only ACL on Windows),
  and the owner-only parent directory must also remain owner-only so another principal cannot
  browse, replace, or remove the secret path through directory access alone
- the public quick-start and example docs keep key files under a separate `./secrets/` tree and
  encrypted books under `./books/` so ordinary book-copy workflows do not automatically copy the
  unlocking secret too
- Java heap overwrite is best-effort only: FinGrind overwrites the arrays and direct buffers it
  owns, but the JVM does not promise elimination of GC-relocated copies or heap-dump visibility

## Protected-Book Format Rationale

Why the current format facts exist:
- `chacha20`, `pageSize=4096`, `reservedBytes=32`, and `kdfIter=64007` are the active SQLite3MC
  `sqleet` defaults used by the managed runtime, and FinGrind intentionally freezes that upstream
  profile into one compatibility contract instead of exposing format-selection drift to callers
- `legacyMode=false` is deliberate because FinGrind does not publish a mixed legacy-cipher or
  downgraded compatibility surface
- `plaintextHeaderSize=0` is deliberate because FinGrind wants a fully protected SQLite header,
  not a format that leaks the ordinary SQLite file magic or header metadata

The deliberate engineering choice is the single-profile stance. FinGrind is not claiming that each
scalar was independently retuned from upstream defaults for a separate local cryptographic budget.
What is owned locally is the decision to pin, verify, fixture-test, and publish one upstream
profile as the only supported protected-book format.

## Runtime Identity And Provenance

The runtime contract has to hold at compile time and at live runtime. FinGrind currently publishes
two runtime-provenance values:
- `bundle-managed`: the self-contained public bundle loaded its own packaged SQLite library
- `source-checkout-managed`: a prepared source checkout resolved the managed library from the
  generated checkout layout

Runtime identity rules:
- `bundle-managed` is bundle-sidecar-consistency: the public bundle ships one sibling
  digest sidecar (`<library>.sha256`), FinGrind copies that pair into one private
  verification snapshot, and verifies the extracted library against that sidecar before the
  verified snapshot is loaded
- `source-checkout-managed` is source-checkout-sidecar-consistency: FinGrind verifies the locally prepared
  library against the checkout-local `.sha256` sidecar, but that proof is
  one source-checkout build identity check rather than one public-release publisher attestation
- machine consumers read `environment.sqlite.runtime.runtimeProvenance` together with
  `environment.sqlite.runtime.runtimeTrustBasis`: the machine-readable `runtimeTrustBasis` field
  reports `bundle-sidecar-consistency` for `bundle-managed`,
  `source-checkout-sidecar-consistency` for `source-checkout-managed`
- environment.sqlite.runtime.runtimeTrustBasis distinguishes bundle-sidecar-consistency bundle runtimes from source-verified local-build runtimes
- environment.sqlite.runtime.runtimeTrustBasis distinguishes bundle-sidecar-consistency bundle runtimes from source-checkout-sidecar-consistency source-checkout runtimes without requiring agents to infer that distinction from prose alone
- the source-checkout wrapper and developer raw-JAR wrapper publish both the source-checkout root
  and the active root-project build directory, so relocated Gradle build roots resolve the same
  managed library tree that Gradle actually prepared instead of guessing at `repo/build/...`
- the source-checkout wrapper and developer raw-JAR wrapper both launch through the Gradle-owned
  Java 26 toolchain executable recorded in the generated source-checkout runtime manifest, so the
  supported local runtime surface cannot drift onto an unrelated ambient shell JDK

Current verification paths:
- `scripts/verify-source-checkout-sqlite-runtime.sh` proves the source-checkout wrapper
  reports the canonical source-checkout runtime distribution with `source-checkout-managed`
  provenance; `scripts/verify-source-checkout-sqlite-runtime.ps1` proves that same contract on
  the Windows PowerShell release surface
- `scripts/verify-direct-java-sqlite-runtime.sh` proves the developer direct-Java wrapper reports
  the canonical direct-Java runtime distribution with `source-checkout-managed` provenance;
  `scripts/verify-direct-java-sqlite-runtime.ps1` proves that same contract on the Windows
  PowerShell release surface
- `scripts/test-source-checkout-launcher.sh` proves the source-checkout wrapper and the prepared
  developer direct-Java wrapper both resolve the managed runtime without leaking native-access
  warnings, and that they heal corrupted source-checkout runtime manifests before launch
- `./scripts/docker-smoke.sh` proves the staged Docker context carries the managed SQLite
  library together with its checksum and provenance files from the same Gradle-owned native build
  path used by the source-checkout and direct-Java runtimes
- `./scripts/verify-security-policy-surface.sh` is the live GitHub verifier for the repository's
  private vulnerability reporting surface, and `./scripts/verify-github-release.sh` calls it
  during public release verification

## Release Integrity And Disclosure

Public release integrity rules:
- every published CLI archive and every published archive checksum file receives one GitHub artifact attestation from `.github/workflows/release.yml`, and those attestations are created from the exact bytes downloaded back from the published release object rather than from per-runner local bundle outputs
- `./scripts/verify-github-release.sh` verifies the release object, downloads the published
  assets, verifies their attestations with `gh attestation verify`, and rejects releases whose
  source archives leak repo-owned agent metadata
- `.sha256` files remain convenience digests for operators; publisher authenticity comes from the
  GitHub attestation, not from storing a checksum beside the artifact in the same release object
- [DEVELOPER_RELEASE_PUBLICATION.md](./DEVELOPER_RELEASE_PUBLICATION.md) is the maintainer-facing
  theory holder for the release publication topology, cross-platform attestation behavior, and
  workflow-repair path behind those integrity rules

Disclosure rules:
- `SECURITY.md` is the canonical public security-policy surface
- GitHub private vulnerability reporting is enabled for this repository, so reporters should use
  the repository's private advisory/reporting flow instead of a public issue
- `./scripts/verify-security-policy-surface.sh` is the executable evidence owner for that GitHub
  repository setting

## Failure Semantics

`protected-book-verification-failed` means FinGrind could not verify the selected protected book
with the supplied passphrase source.

Publicly reported causes include:
- wrong secret
- damaged or truncated ciphertext
- a protected SQLite file outside the supported FinGrind protected-book format

FinGrind intentionally does not invent false precision at this boundary. The encrypted-header
validation path can converge on storage verification families such as `SQLITE_NOTADB`,
`SQLITE_IOERR_BADKEY`, and `SQLITE_IOERR_CODEC` for wrong-key, damaged ciphertext, and
unsupported protected-file variants. The public contract therefore reports one truthful
verification failure instead of pretending every such failure is a passphrase mistake.

## Evidence

Current evidence that this model is implemented:
- `verifyManagedSqliteSource` pins the vendored SQLite3MC source hash before the managed runtime
  is built
- the Docker image verifies that same vendored source before compile
- `SqliteConnectionConfigurer` rejects any opened handle that cannot prove `secure_delete=on`,
  `temp_store=memory`, and `memory_security=fill`
- `SqliteProtectedBookCompatibilityFixtureTest` proves the committed protected-book fixture reopens,
  rejects mismatched verification, and restores from a closed-book encrypted copy
- the CLI and store tests cover wrong-key, corrupted-book, and truncated-book failures through the
  deterministic `protected-book-verification-failed` contract
- `SqliteAuditEventStreamTest` proves durable bookkeeping audit rows are appended in the same book
  as the protected facts they describe and are rejected for in-place update/delete mutation
- `SqliteManagedLibraryIdentityTest` proves bundle-managed runtimes require the sibling
  `.sha256` sidecar and source-checkout-managed runtimes verify one checkout-local build identity
  through that same sidecar contract
- `SqliteRekeyRollbackFileTest` proves stale rollback-artifact discovery only matches the
  same-book rollback naming contract before one warning is reported
- `scripts/test-verify-github-release.sh` proves release verification now requires attested
  published bundle assets instead of metadata-only checks
- `scripts/test-verify-security-policy-surface.sh` proves the live GitHub security-policy verifier
  rejects repositories whose private reporting surface is disabled
- `capabilities` publishes the runtime distribution, runtime provenance, compile-option
  verification, runtime trust basis, loaded library path, and canonical protected-book format
  facts for machine checks

For storage architecture details, see [DEVELOPER_SQLITE.md](./DEVELOPER_SQLITE.md). For container
runtime specifics, see [DEVELOPER_DOCKER.md](./DEVELOPER_DOCKER.md). For user-facing CLI behavior,
see [USER_CLI.md](./USER_CLI.md). For coordinated disclosure, see
[../SECURITY.md](../SECURITY.md).
