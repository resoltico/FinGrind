---
afad: "3.5"
version: "0.4.0"
domain: USER_CLI
updated: "2026-04-10"
route:
  keywords: [fingrind, cli, commands, exit-codes, java26, sqlite, ffm, request-file, book-file, stdin, preflight]
  questions: ["how do I run the fingrind cli", "what commands does fingrind expose", "what exit codes does the fingrind cli use"]
---

# CLI Guide

**Purpose**: Run the packaged FinGrind CLI and understand its command, file, and exit behavior.
**Prerequisites**: Java 26 or newer. For source-driven local runs, `./gradlew :cli:run` manages
SQLite 3.53.0 automatically. For standalone `java -jar`, provide `FINGRIND_SQLITE_LIBRARY` or a
host `libsqlite3` at version 3.53.0 or newer.

## Overview

FinGrind reads one command, writes structured JSON to standard output, and exits.
The `--book-file` path is the selected book identity, and there is no default database location.

`help` is returned when no command is supplied.
`help`, `version`, and `capabilities` return pretty JSON envelopes for discovery.
`print-request-template` returns one raw JSON document so it can be redirected into a file or piped into another process.
Both `preflight-entry` and `post-entry` require `--book-file` and `--request-file`, and both check duplicate idempotency inside the selected book.
`preflight-entry` does not create a missing book file. `post-entry` creates parent directories and initializes the current canonical schema on first commit.

## Commands

| Command | Aliases | Extra Arguments | Result |
|:--------|:--------|:----------------|:-------|
| `help` | `--help`, `-h` | none | returns application, version, usage, quick-start, and error guidance |
| `version` | `--version` | none | returns application name, version, and description |
| `capabilities` | none | none | returns machine-readable storage, request, response, and command capabilities |
| `print-request-template` | `--print-request-template` | none | returns a minimal valid posting request JSON document |
| `preflight-entry` | none | `--book-file`, `--request-file` | validates one request without committing it |
| `post-entry` | none | `--book-file`, `--request-file` | commits one posting fact into the selected book |

## Packaged CLI

For source-driven local use, prefer:

```bash
./gradlew :cli:run --args="help"
```

For standalone JAR execution:

```bash
./gradlew :cli:shadowJar
java -jar cli/build/libs/fingrind.jar help
java -jar cli/build/libs/fingrind.jar \
  print-request-template > /tmp/fingrind-request.json
```

`--request-file -` means read the request JSON from standard input.
Use `java -jar` for real process exit codes; `./gradlew :cli:run` wraps non-zero application exits as a Gradle task failure.
`./gradlew :cli:run` is the easiest local route because Gradle automatically compiles and injects
the managed SQLite 3.53.0 runtime.

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
| missing `--request-file` | `2` | `invalid-request` | `A --request-file argument is required.` |
| duplicate option | `2` | `invalid-request` | `Duplicate argument: --book-file` and similar |
| same path used for both files | `2` | `invalid-request` | `--book-file and --request-file must not point to the same path.` |
| malformed JSON or invalid request shape | `2` | `invalid-request` | `Failed to read request JSON.` or domain-validation text |
| deterministic business rejection | `2` | `duplicate-idempotency-key`, `reversal-target-not-found`, and similar | request was understood but refused by book state or reversal policy |
| runtime environment failure | `1` | `runtime-failure` | `Failed to open SQLite book connection.` and similar storage/runtime errors |

## Notes

- Error envelopes may include `hint` and `argument` fields to help an agent or human repair the call without consulting docs.
- `help`, `version`, `capabilities`, and `print-request-template` reject extra arguments.
- FinGrind creates missing parent directories for nested `--book-file` paths.
- The packaged CLI does not require an external `sqlite3` binary and does not shell out to
  `sqlite3`.
- The packaged CLI enforces SQLite 3.53.0 or newer.
- `capabilities` reports `sqliteLibrarySource`, `requiredMinimumSqliteVersion`,
  `sqliteRuntimeStatus`, and `loadedSqliteVersion` so agents can verify the runtime contract
  directly.
- Gradle-driven local runs and the container image use a managed SQLite 3.53.0 shared library.
- Standalone `java -jar` execution still relies on `FINGRIND_SQLITE_LIBRARY` or a compatible host
  `libsqlite3`.
- `capabilities` is the best machine-readable contract surface.
- `print-request-template` intentionally omits committed audit fields. Callers must not send `provenance.recordedAt` or `provenance.sourceChannel`.
- Example payloads live under [examples/](./examples/).
