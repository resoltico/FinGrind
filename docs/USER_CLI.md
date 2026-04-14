---
afad: "3.5"
version: "0.9.0"
domain: USER_CLI
updated: "2026-04-14"
route:
  keywords: [fingrind, cli, commands, exit-codes, java26, sqlite, sqlite3mc, ffm, request-file, book-file, book-key-file, stdin, open-book, declare-account]
  questions: ["how do I run the fingrind cli", "what commands does fingrind expose", "what exit codes does the fingrind cli use"]
---

# CLI Guide

**Purpose**: Run the packaged FinGrind CLI and understand its command, file, and exit behavior.
**Prerequisites**: Java 26 or newer. For source-driven local runs, `./gradlew :cli:run` manages
SQLite 3.53.0 / SQLite3 Multiple Ciphers 2.3.3 automatically. For standalone `java -jar`, prefer
the managed library from `./gradlew prepareManagedSqlite` via `FINGRIND_SQLITE_LIBRARY`, or
provide a host `libsqlite3` that already satisfies the same SQLite / SQLite3MC contract.

## Overview

FinGrind reads one command, writes structured JSON to standard output, and exits.
The `--book-file` path is the selected book identity, and there is no default database location.
Every book-bound command also requires `--book-key-file`, which points at the UTF-8 passphrase
file used to open the protected book.

`help` is returned when no command is supplied.
`help`, `version`, and `capabilities` return pretty JSON envelopes for discovery.
`print-request-template` returns one raw JSON document so it can be redirected into a file or piped
into another process.
`open-book` explicitly initializes one new protected book.
`declare-account` inserts or reactivates one account in the selected book.
`list-accounts` returns the current account registry.
`preflight-entry` and `post-entry` both require an already initialized book and declared active
accounts for every journal line they touch.
`preflight-entry` is advisory only: FinGrind still re-checks commit-time durability rules inside
the write transaction before `post-entry` succeeds.
Every journal entry is single-currency; mixed-currency lines inside one entry are not supported.
Protected books use SQLite3 Multiple Ciphers 2.3.3 with the upstream default `chacha20` cipher.

## Commands

| Command | Aliases | Extra Arguments | Result |
|:--------|:--------|:----------------|:-------|
| `help` | `--help`, `-h` | none | returns application, version, usage, quick-start, and error guidance |
| `version` | `--version` | none | returns application name, version, and description |
| `capabilities` | none | none | returns machine-readable storage, command, typed request-field descriptors, response descriptors, and account-registry capabilities |
| `print-request-template` | `--print-request-template` | none | returns a minimal valid posting request JSON document |
| `open-book` | none | `--book-file`, `--book-key-file` | creates one initialized protected book with the canonical schema |
| `declare-account` | none | `--book-file`, `--book-key-file`, `--request-file` | declares or reactivates one account in the selected book |
| `list-accounts` | none | `--book-file`, `--book-key-file` | returns the selected book's declared account registry |
| `preflight-entry` | none | `--book-file`, `--book-key-file`, `--request-file` | validates one posting request without committing it |
| `post-entry` | none | `--book-file`, `--book-key-file`, `--request-file` | commits one posting fact into the selected book |

## Packaged CLI

For source-driven local use, prefer:

```bash
./gradlew :cli:run --args="help"
```

For standalone JAR execution:

```bash
./gradlew :cli:shadowJar
./gradlew prepareManagedSqlite
export FINGRIND_SQLITE_LIBRARY="$(find "$PWD/build/managed-sqlite" -type f \( -name 'libsqlite3.dylib' -o -name 'libsqlite3.so.0' \) | head -n 1)"
java -jar cli/build/libs/fingrind.jar help
java -jar cli/build/libs/fingrind.jar \
  print-request-template > /tmp/fingrind-request.json
```

`--request-file -` means read the request JSON from standard input.
Use `java -jar` for real process exit codes; `./gradlew :cli:run` wraps non-zero application exits
as a Gradle task failure.
`./gradlew :cli:run` is the easiest local route because Gradle automatically compiles and injects
the managed SQLite 3.53.0 / SQLite3 Multiple Ciphers 2.3.3 runtime.
If you want the standalone JAR to use the managed runtime from a local source checkout, point
`FINGRIND_SQLITE_LIBRARY` at the file produced under `build/managed-sqlite/`.
The `find .../build/managed-sqlite` export above is the supported checked-in-repo path.

## Exit Codes

| Exit Code | Meaning | Typical Output |
|:----------|:--------|:---------------|
| `0` | successful command | `ok`, raw request template JSON, `preflight-accepted`, `committed` |
| `2` | invalid request or deterministic rejection | `error`, `rejected` |
| `1` | runtime or environment failure | `error` with code `runtime-failure` |

## Common Failures

| Situation | Exit | Envelope Code | Typical Message |
|:----------|:-----|:--------------|:----------------|
| unsupported command | `2` | `unknown-command` | `Unsupported command: ...` |
| missing `--book-file` | `2` | `invalid-request` | `A --book-file argument is required.` |
| missing `--book-key-file` | `2` | `invalid-request` | `A --book-key-file argument is required.` |
| missing `--request-file` | `2` | `invalid-request` | `A --request-file argument is required.` |
| duplicate option | `2` | `invalid-request` | `Duplicate argument: --book-file`, `Duplicate argument: --book-key-file`, and similar |
| same path used for both files | `2` | `invalid-request` | `--book-file and --request-file must not point to the same path.` and similar |
| malformed JSON or invalid request shape | `2` | `invalid-request` | `Failed to read request JSON.` or domain-validation text |
| book is missing or never opened | `2` | `book-not-initialized` | `The selected book does not exist or has not been initialized with open-book.` |
| posting uses an undeclared account | `2` | `unknown-account` | `Account '...' is not declared in this book.` |
| posting uses an inactive account | `2` | `inactive-account` | `Account '...' is inactive in this book.` |
| duplicate idempotency or reversal policy refusal | `2` | `duplicate-idempotency-key`, `reversal-target-not-found`, and similar | request was understood but refused by current book state |
| wrong book key or plaintext legacy book | `1` | `runtime-failure` | storage open failure including `SQLITE_NOTADB` |
| standalone JAR sees an old or incompatible host SQLite library | `1` | `runtime-failure` | SQLite / SQLite3MC version guidance describing the unsupported native library |
| runtime environment failure | `1` | `runtime-failure` | `Failed to open SQLite book connection.` and similar storage/runtime errors |

## Notes

- Error envelopes may include `hint` and `argument` fields to help an agent or human repair the call without consulting docs.
- `help`, `version`, `capabilities`, and `print-request-template` reject extra arguments.
- `open-book` creates missing parent directories for nested `--book-file` paths.
- `--book-key-file` must point to a non-empty UTF-8 passphrase file; one trailing LF or CRLF is
  tolerated and stripped.
- The packaged CLI does not require an external `sqlite3` binary and does not shell out to
  `sqlite3`.
- The packaged CLI enforces SQLite 3.53.0 and SQLite3 Multiple Ciphers 2.3.3.
- `capabilities` reports `sqliteLibrarySource`, `requiredMinimumSqliteVersion`,
  `requiredSqlite3mcVersion`, `sqliteRuntimeStatus`, `loadedSqliteVersion`,
  `loadedSqlite3mcVersion`, `bookProtectionMode`, and `defaultBookCipher` so agents can verify the
  runtime contract directly.
- `capabilities` also reports `preflightSemantics`, `preflight.isCommitGuarantee`, and
  `currencyModel` so agents can discover the advisory preflight contract and single-currency scope
  without reading source code.
- Gradle-driven local runs and the container image use a managed SQLite 3.53.0 / SQLite3 Multiple
  Ciphers 2.3.3 shared library.
- Standalone `java -jar` execution still relies on `FINGRIND_SQLITE_LIBRARY` or a compatible host
  `libsqlite3`.
- `capabilities` is the best machine-readable contract surface.
- `print-request-template` intentionally omits committed audit fields. Callers must not send
  `provenance.recordedAt` or `provenance.sourceChannel`.
- FinGrind does not accept SQLite URI `key=` or `hexkey=` transport and does not expose runtime
  cipher selection; the protected-book contract is always a separate key file plus the upstream
  default `chacha20` cipher.
- successful `post-entry` responses carry a FinGrind-generated UUID v7 `postingId`
- Example payloads live under [examples/](./examples/).
