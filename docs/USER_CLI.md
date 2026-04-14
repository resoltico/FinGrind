---
afad: "3.5"
version: "0.11.0"
domain: USER_CLI
updated: "2026-04-14"
route:
  keywords: [fingrind, cli, commands, exit-codes, java26, sqlite, sqlite3mc, ffm, request-file, book-file, book-key-file, book-passphrase-stdin, book-passphrase-prompt, stdin, open-book, rekey-book, declare-account]
  questions: ["how do I run the fingrind cli", "what commands does fingrind expose", "how do I rotate a fingrind book passphrase", "what exit codes does the fingrind cli use"]
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
Every book-bound command also requires exactly one passphrase source:
- `--book-key-file <path>` for a dedicated UTF-8 passphrase file
- `--book-passphrase-stdin` for one UTF-8 passphrase payload from standard input
- `--book-passphrase-prompt` for an interactive non-echo terminal prompt

`help` is returned when no command is supplied.
`help`, `version`, and `capabilities` return pretty JSON envelopes for discovery.
`print-request-template` returns one raw JSON document so it can be redirected into a file or piped
into another process.
`open-book` explicitly initializes one new protected book.
`rekey-book` rotates the passphrase that protects one existing initialized book.
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
| `open-book` | none | `--book-file`, exactly one of `--book-key-file`, `--book-passphrase-stdin`, or `--book-passphrase-prompt` | creates one initialized protected book with the canonical schema |
| `rekey-book` | none | `--book-file`, exactly one current passphrase source, exactly one replacement passphrase source | rotates the passphrase that protects the selected existing book |
| `declare-account` | none | `--book-file`, exactly one passphrase source, `--request-file` | declares or reactivates one account in the selected book |
| `list-accounts` | none | `--book-file`, exactly one passphrase source | returns the selected book's declared account registry |
| `preflight-entry` | none | `--book-file`, exactly one passphrase source, `--request-file` | validates one posting request without committing it |
| `post-entry` | none | `--book-file`, exactly one passphrase source, `--request-file` | commits one posting fact into the selected book |

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
`--book-passphrase-stdin` means read the book passphrase from standard input instead.
Those two stdin modes cannot be combined in one invocation.
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
| missing book passphrase source | `2` | `invalid-request` | `Exactly one book passphrase source is required: ...` |
| missing replacement passphrase source on `rekey-book` | `2` | `invalid-request` | `Exactly one replacement book passphrase source is required: ...` |
| missing `--request-file` | `2` | `invalid-request` | `A --request-file argument is required.` |
| multiple passphrase sources | `2` | `invalid-request` | `Exactly one book passphrase source is permitted per command.` |
| multiple replacement passphrase sources on `rekey-book` | `2` | `invalid-request` | `Exactly one replacement book passphrase source is permitted per command.` |
| same path used for both files | `2` | `invalid-request` | `--book-file and --request-file must not point to the same path.` and similar |
| stdin requested for both passphrase and JSON | `2` | `invalid-request` | `Standard input cannot supply both the book passphrase and the request JSON.` |
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
- `--book-key-file` must point to a non-empty UTF-8 passphrase file on a POSIX filesystem; one
  trailing LF or CRLF is tolerated and stripped.
- Book key files must use owner-only permissions (`0400` or `0600`), or the runtime rejects them.
- `--book-passphrase-stdin` reads one UTF-8 passphrase payload from standard input and therefore
  cannot be paired with `--request-file -`.
- `--book-passphrase-prompt` reads the passphrase from the controlling terminal without echo.
- `rekey-book` requires one current passphrase source plus one replacement passphrase source, and
  it rejects using the same key-file path for both.
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
  `libsqlite3`, and that external library must satisfy the same SQLite3MC compile-option
  hardening contract.
- `capabilities` is the best machine-readable contract surface.
- `print-request-template` intentionally omits committed audit fields. Callers must not send
  `provenance.recordedAt` or `provenance.sourceChannel`.
- FinGrind does not accept SQLite URI `key=` or `hexkey=` transport, plaintext CLI passphrase
  arguments, or environment-variable passphrase transport. The protected-book contract is always
  one explicit safe passphrase source plus the upstream default `chacha20` cipher.
- successful `post-entry` responses carry a FinGrind-generated UUID v7 `postingId`
- Example payloads live under [examples/](./examples/).
