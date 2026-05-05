---
afad: "4.0"
version: "0.30.0"
domain: DEVELOPER_SECURITY
updated: "2026-05-05"
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
- one managed SQLite runtime contract: SQLite 3.53.0 / SQLite3 Multiple Ciphers 2.3.3 plus the
  required compile options `THREADSAFE=1`, `OMIT_LOAD_EXTENSION`, `TEMP_STORE=3`, and
  `SECURE_DELETE`
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
- decoded query results or passphrase bytes in process memory before zeroization
- crash dumps, live debuggers, or host-level memory inspection
- copied backups, exported JSON, CSV, or PDF artifacts
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
  validates UTF-8, and rejects empty secrets
- the SQLite adapter applies secrets through native `sqlite3_key()` and `sqlite3_rekey()`, not
  SQL text
- transient passphrase copies are zeroized after native handoff
- key files must remain owner-only (`0400` or `0600` on POSIX hosts, owner-only ACL on Windows)

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
three runtime-provenance values:
- `bundle-managed`: the self-contained public bundle loaded its own packaged SQLite library
- `source-checkout-managed`: a prepared source checkout resolved the managed library from the
  generated checkout layout
- `environment-configured`: the current process received the managed library path through the
  canonical environment-variable route

Current verification paths:
- `scripts/verify-source-checkout-sqlite-runtime.sh` proves the generated source-checkout launcher
  reports the canonical source-checkout runtime distribution with `source-checkout-managed`
  provenance
- `scripts/verify-environment-configured-sqlite-runtime.sh` proves the Gradle JavaExec path
  reports the canonical direct-Java runtime distribution with `environment-configured` provenance
- `scripts/test-source-checkout-launcher.sh` proves the generated launcher and the prepared
  developer `java -jar` wrapper both resolve the managed runtime without leaking native-access
  warnings
- `scripts/render-managed-sqlite-compiler-flags.py` makes Docker compile the native library from
  the same canonical compile-option contract used elsewhere, and
  `scripts/test-render-managed-sqlite-compiler-flags.sh` guards that renderer

## Failure Semantics

`protected-book-verification-failed` means FinGrind could not verify the selected protected book
with the supplied passphrase source.

Publicly reported causes include:
- wrong secret
- damaged or truncated ciphertext
- a protected SQLite file outside the supported FinGrind protected-book format

FinGrind intentionally does not invent false precision at this boundary. The encrypted-header
validation path can converge on the same `SQLITE_NOTADB` storage symptom for wrong-key, damaged
ciphertext, and unsupported protected-file variants. The public contract therefore reports one
truthful verification failure instead of pretending every such failure is a passphrase mistake.

## Evidence

Current evidence that this model is implemented:
- `verifyManagedSqliteSource` pins the vendored SQLite3MC source hash before the managed runtime
  is built
- the Docker image verifies that same vendored source before compile
- `SqliteProtectedBookCompatibilityFixtureTest` proves the committed protected-book fixture reopens,
  rejects mismatched verification, and restores from a closed-book encrypted copy
- the CLI and store tests cover wrong-key, corrupted-book, and truncated-book failures through the
  deterministic `protected-book-verification-failed` contract
- `capabilities` publishes the runtime distribution, runtime provenance, compile-option
  verification, loaded library path, and canonical protected-book format facts for machine checks

For storage architecture details, see [DEVELOPER_SQLITE.md](./DEVELOPER_SQLITE.md). For container
runtime specifics, see [DEVELOPER_DOCKER.md](./DEVELOPER_DOCKER.md). For user-facing CLI behavior,
see [USER_CLI.md](./USER_CLI.md).
