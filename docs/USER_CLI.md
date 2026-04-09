---
afad: "3.5"
version: "0.1.0-SNAPSHOT"
domain: USER_CLI
updated: "2026-04-08"
route:
  keywords: [fingrind, cli, commands, exit-codes, java26, sqlite3, request-file, book-file, stdin]
  questions: ["how do I run the fingrind cli", "what commands does fingrind expose", "what exit codes does the fingrind cli use"]
---

# CLI Guide

**Purpose**: Run the packaged FinGrind CLI and understand its command and exit behavior.
**Prerequisites**: Java 26 or newer, a built CLI JAR from `./gradlew :cli:shadowJar`, and a SQLite 3.51.3 binary. The recommended path is the repo-managed sqlite.org bootstrap plus `FINGRIND_SQLITE3_BINARY`.

## Overview

FinGrind reads one command, writes structured JSON to standard output, and exits.
The `--book-file` path is the selected book identity, and there is no default database location.

`help` is returned when no command is supplied.
`help`, `version`, and `capabilities` return pretty JSON envelopes for discovery.
`print-request-template` returns one raw JSON document so it can be redirected into a file or piped into another process.
Both `preflight-entry` and `post-entry` require `--book-file` and `--request-file`, and both check duplicate idempotency inside the selected book.

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

```bash
./scripts/ensure-sqlite.sh
./gradlew :cli:shadowJar
FINGRIND_SQLITE3_BINARY="$(./scripts/ensure-sqlite.sh)" \
  java -jar cli/build/libs/fingrind.jar help
java -jar cli/build/libs/fingrind.jar \
  print-request-template > /tmp/fingrind-request.json
```

`--request-file -` means read the request JSON from standard input.
Use `java -jar` for real process exit codes; `./gradlew :cli:run` wraps non-zero application exits as a Gradle task failure.

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
| runtime environment failure | `1` | `runtime-failure` | `Failed to start sqlite3.` and similar runtime errors |

## Notes

- Error envelopes may include `hint` and `argument` fields to help an agent or human repair the call without consulting docs.
- `help`, `version`, `capabilities`, and `print-request-template` reject extra arguments.
- FinGrind creates missing parent directories for nested `--book-file` paths.
- Repo-local development should use `./scripts/ensure-sqlite.sh`, which provisions the pinned SQLite 3.51.3 toolchain from `sqlite.org`.
- `FINGRIND_SQLITE3_BINARY` is the intended pinning hook for local packaged-JAR runs and any controlled runtime that should not depend on ambient `PATH`.
- `capabilities` is the best machine-readable contract surface.
- Example payloads live under [examples/](./examples/).
