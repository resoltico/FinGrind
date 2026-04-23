---
afad: "3.5"
version: "0.24.0"
domain: USER_CLI
updated: "2026-04-23"
route:
  keywords: [fingrind, cli, commands, exit-codes, java26, sqlite, sqlite3mc, ffm, request-file, book-file, book-key-file, book-passphrase-stdin, book-passphrase-prompt, inspect-book, list-accounts, list-postings, account-balance, trial-balance, account-ledger, period-summary, output-mode, print-plan-template, execute-plan]
  questions: ["how do I run the fingrind cli", "what commands does fingrind expose", "how do I inspect a fingrind book before mutating it", "how do I page declared accounts in fingrind", "how do I run an AI-agent ledger plan in fingrind", "what exit codes does the fingrind cli use"]
---

# CLI Guide

**Purpose**: Run the packaged FinGrind CLI and understand its command, file, and exit behavior.
**Prerequisites**: For public use, download one self-contained FinGrind release bundle and unpack it.
No separate Java install is required for that path. For source-driven local runs,
`./gradlew :cli:run` manages SQLite 3.53.0 / SQLite3 Multiple Ciphers 2.3.3 automatically.
The raw `java -jar` route remains developer-only and requires `./gradlew prepareManagedSqlite`
plus `FINGRIND_SQLITE_LIBRARY` and `--enable-native-access=ALL-UNNAMED`.

## Overview

FinGrind reads one command, writes deterministic output to standard output, and exits.
The `--book-file` path is the selected book identity, and there is no default database location.
Every book-bound command also requires exactly one passphrase source:
- `--book-key-file <path>` for a dedicated UTF-8 passphrase file
- `--book-passphrase-stdin` for one UTF-8 passphrase payload from standard input
- `--book-passphrase-prompt` for an interactive non-echo terminal prompt

`help` is returned when no command is supplied.
`help`, `version`, and `capabilities` default to human-readable discovery output and also accept
`--output json` for machine parsing.
`print-request-template` returns one raw JSON document so it can be redirected into a file or piped
into another process.
`print-plan-template` returns one raw JSON ledger-plan scaffold that already includes `open-book`,
account declarations, one posting step, and one balance assertion.
`generate-book-key-file` creates one new owner-only key file that contains a generated passphrase.
`open-book` explicitly initializes one new protected book.
`rekey-book` rotates the passphrase that protects one existing initialized book.
`declare-account` inserts or reactivates one account in the selected book.
`inspect-book` reports lifecycle state, format metadata, and compatibility for one selected book.
`list-accounts` returns one stable page of the current account registry.
`get-posting`, `list-postings`, and `account-balance` expose read/query access to committed history.
`trial-balance`, `account-ledger`, and `period-summary` answer standard office-worker reporting
questions in one command.
`execute-plan` runs one ordered ledger plan atomically and returns a structured execution journal.
`preflight-entry` and `post-entry` both require an already initialized book and declared active
accounts for every journal line they touch, and surface those failures as
`account-state-violations` with structured `details.violations`.
`preflight-entry` is advisory only: FinGrind still re-checks commit-time durability rules inside
the write transaction before `post-entry` succeeds.
Every journal entry is single-currency; mixed-currency lines inside one entry are not supported.
Every journal-line amount must be greater than zero.
Protected books use SQLite3 Multiple Ciphers 2.3.3 with the upstream default `chacha20` cipher.
The operation catalog rendered in `help` and `capabilities` is contract-owned protocol metadata,
so CLI help, parser aliases, output modes, summaries, and query limits share one source.
Commands that advertise `--output` keep JSON as the default machine surface. Discovery,
administration, write, and query/report commands can render operator-facing `--output human`,
and the tabular read/report commands also accept `--output csv`. The report commands
`account-balance`, `trial-balance`, `account-ledger`, and `period-summary` can additionally write
one PDF artifact through `--pdf-out <path>`. If the report itself succeeds but the PDF write later
fails, FinGrind still returns the primary report on stdout and emits a repair warning on the
diagnostics stream instead of changing the command exit to `runtime-failure`.

## Commands

| Command | Aliases | Extra Arguments | Result |
|:--------|:--------|:----------------|:-------|
| `help` | `--help`, `-h` | optional `--output` | returns application, version, usage, quick-start, and error guidance |
| `version` | `--version` | optional `--output` | returns application name, version, and description |
| `capabilities` | none | optional `--output` | returns storage, command, typed request-field descriptors, executable request schemas, response descriptors, and account-registry capabilities |
| `print-request-template` | `--print-request-template` | none | returns a minimal valid posting request JSON document |
| `print-plan-template` | `--print-plan-template` | none | returns a runnable AI-agent ledger-plan scaffold as raw JSON |
| `generate-book-key-file` | none | `--book-key-file`, optional `--output` | creates one new owner-only key file and returns only non-secret metadata |
| `open-book` | none | `--book-file`, exactly one of `--book-key-file`, `--book-passphrase-stdin`, or `--book-passphrase-prompt`, optional `--output` | creates one initialized protected book with the canonical schema |
| `rekey-book` | none | `--book-file`, exactly one current passphrase source (`--book-key-file`, `--book-passphrase-stdin`, or `--book-passphrase-prompt`), exactly one replacement passphrase source (`--new-book-key-file`, `--new-book-passphrase-stdin`, or `--new-book-passphrase-prompt`), optional `--output` | rotates the passphrase that protects the selected existing book |
| `declare-account` | none | `--book-file`, exactly one passphrase source, `--request-file`, optional `--output` | declares or reactivates one account in the selected book |
| `inspect-book` | none | `--book-file`, exactly one passphrase source, optional `--output` | returns lifecycle state, compatibility, and book-format metadata for the selected book |
| `list-accounts` | none | `--book-file`, exactly one passphrase source, optional `--limit`, optional `--cursor`, optional `--output` | returns one stable keyset-paginated slice of the selected book's declared account registry |
| `get-posting` | none | `--book-file`, exactly one passphrase source, `--posting-id`, optional `--output` | returns one committed posting by durable posting id |
| `list-postings` | none | `--book-file`, exactly one passphrase source, optional account/date filters, optional `--limit`, optional `--cursor`, optional `--output` | returns one reverse-chronological page of committed posting history |
| `account-balance` | none | `--book-file`, exactly one passphrase source, `--account-code`, optional date filters, optional `--output`, optional `--pdf-out` | returns grouped per-currency balances for one declared account and can also export one PDF report |
| `trial-balance` | none | `--book-file`, exactly one passphrase source, optional `--effective-date-to`, optional `--output`, optional `--pdf-out` | returns one trial balance for the selected book as JSON, human-readable text, or CSV and can also export one PDF report |
| `account-ledger` | none | `--book-file`, exactly one passphrase source, `--account-code`, optional date filters, optional `--output`, optional `--pdf-out` | returns one account ledger with opening balances, running activity, and closing balances, and can also export one PDF report |
| `period-summary` | none | `--book-file`, exactly one passphrase source, `--effective-date-from`, `--effective-date-to`, optional `--output`, optional `--pdf-out` | returns one bounded period summary with currency totals and account activity, and can also export one PDF report |
| `execute-plan` | none | `--book-file`, exactly one passphrase source, `--request-file` | executes one ordered ledger plan atomically and returns a durable per-step journal |
| `preflight-entry` | none | `--book-file`, exactly one passphrase source, `--request-file`, optional `--output` | validates one posting request without committing it |
| `post-entry` | none | `--book-file`, exactly one passphrase source, `--request-file`, optional `--output` | commits one posting fact into the selected book |

## Packaged CLI

Public FinGrind CLI downloads are self-contained bundle archives, not a standalone JAR.
The current public target set is:
- `macos-aarch64`
- `macos-x86_64`
- `linux-x86_64`
- `linux-aarch64`
- `windows-x86_64`

Linux bundles are built on Ubuntu GitHub-hosted runners and therefore target ordinary glibc Linux
hosts. They are not presented as a universal Linux binary for every libc variant.
Windows bundles are built on Windows GitHub-hosted runners with the native MSVC toolchain and are
published as `.zip` archives with the `bin\fingrind.ps1` launcher.
They also include `bin\fingrind.cmd` as a compatibility wrapper.

Each extracted archive also contains:
- a top-level `README.md` with the local quick start
- a top-level `bundle-manifest.json` with machine-readable distribution metadata and canonical
  bootstrap commands that point back to `help`, `capabilities`, and the request/plan template
  operations

One public Unix bundle flow:

```bash
tar -xzf fingrind-0.24.0-macos-aarch64.tar.gz
./fingrind-0.24.0-macos-aarch64/bin/fingrind help
./fingrind-0.24.0-macos-aarch64/bin/fingrind \
  print-request-template > ./request.json
```

One public Windows bundle flow:

```powershell
Expand-Archive fingrind-0.24.0-windows-x86_64.zip -DestinationPath .
.\fingrind-0.24.0-windows-x86_64\bin\fingrind.ps1 help
.\fingrind-0.24.0-windows-x86_64\bin\fingrind.ps1 `
  print-request-template > .\request.json
```

In the examples below, `fingrind` means the extracted bundle launcher.

For source-driven local use, prefer:

```bash
./gradlew :cli:run --args="help"
```

For local bundle verification from a source checkout:

```bash
./gradlew :cli:bundleCliArchive
./scripts/bundle-smoke.sh
```

The raw `java -jar` path is still available for advanced contributor work, but it is not the
public FinGrind download contract:

These example paths assume the project `build/` tree stays inside the checkout. On fragile mounted
filesystems, `./gradlew` may relocate that tree into the wrapper-owned local cache.

```bash
./gradlew :cli:shadowJar
./gradlew prepareManagedSqlite
export FINGRIND_SQLITE_LIBRARY="$(find "$PWD/build/managed-sqlite" -type f \( -name 'libsqlite3.dylib' -o -name 'libsqlite3.so.0' \) | head -n 1)"
java --enable-native-access=ALL-UNNAMED -jar cli/build/libs/fingrind.jar help
```

`--request-file -` means read the request JSON from standard input.
`--book-passphrase-stdin` means read the book passphrase from standard input instead.
Those two stdin modes cannot be combined in one invocation.
Use the extracted bundle launcher or `java -jar` for real process exit codes;
`./gradlew :cli:run` wraps non-zero application exits as a Gradle task failure.

## Exit Codes

| Exit Code | Meaning | Typical Output |
|:----------|:--------|:---------------|
| `0` | successful command | `ok`, raw request or plan template JSON, `preflight-accepted`, `committed` |
| `1` | invalid invocation or malformed request | `error` with code `unknown-command`, `invalid-request`, `invalid-page-cursor`, and similar |
| `2` | deterministic refusal after the command was understood | `error`, `rejected`, `plan-rejected` |
| `3` | valid `execute-plan` request whose assertion step failed | `plan-assertion-failed` |
| `4` | runtime or environment failure | `error` with code `managed-runtime-failure`, `storage-runtime-failure`, or `runtime-failure` |

## Common Failures

| Situation | Exit | Envelope Code | Typical Message |
|:----------|:-----|:--------------|:----------------|
| unsupported command | `1` | `unknown-command` | `Unsupported command: ...` |
| missing `--book-file` | `1` | `invalid-request` | `A --book-file argument is required.` |
| key-file generation target already exists | `2` | `book-key-file-already-exists` | `The FinGrind book key file already exists and will not be overwritten.` |
| missing book passphrase source | `1` | `invalid-request` | `Exactly one book passphrase source is required: ...` |
| missing replacement passphrase source on `rekey-book` | `1` | `invalid-request` | `Exactly one replacement book passphrase source is required: ...` |
| missing `--request-file` | `1` | `invalid-request` | `A --request-file argument is required.` |
| multiple passphrase sources | `1` | `invalid-request` | `Exactly one book passphrase source is permitted per command.` |
| multiple replacement passphrase sources on `rekey-book` | `1` | `invalid-request` | `Exactly one replacement book passphrase source is permitted per command.` |
| same path used for both files | `1` | `invalid-request` | `--book-file and --request-file must not point to the same path.` and similar |
| stdin requested for both passphrase and JSON | `1` | `invalid-request` | `Standard input cannot supply both the book passphrase and the request JSON.` |
| malformed JSON or invalid request shape | `1` | `invalid-request` | `Failed to read request JSON.` or domain-validation text |
| malformed `list-accounts --cursor` or `list-postings --cursor` | `1` | `invalid-page-cursor` | `Unsupported account page cursor: ...` or `Unsupported posting page cursor: ...` |
| book is missing or never opened | `2` | `administration-book-not-initialized`, `query-book-not-initialized`, or `posting-book-not-initialized` | `The selected book does not exist or has not been initialized with open-book.` |
| query names an undeclared account | `2` | `unknown-account` | `Account '...' is not declared in this book.` |
| posting uses undeclared or inactive accounts | `2` | `account-state-violations` | `Posting references undeclared or inactive accounts.` plus `details.violations` |
| duplicate idempotency or reversal policy refusal | `2` | `duplicate-idempotency-key`, `reversal-target-not-found`, and similar | request was understood but refused by current book state |
| wrong book key or plaintext legacy book | `2` | `book-authentication-failed` | `FinGrind could not authenticate the selected protected book with the supplied passphrase source.` |
| invalid key-file contents or permissions | `2` | `invalid-book-key-file` | `Book access refused because the selected book key file path, permissions, or contents do not satisfy the protected-book contract.` |
| unsupported prompt environment | `2` | `interactive-prompt-unavailable` | `FinGrind cannot prompt for a book passphrase because no interactive console is available.` |
| requested PDF artifact cannot be written after a successful report result | `0` | diagnostics warning pdf-export-warning | primary report remains on stdout and the warning explains how to repair the `--pdf-out` path |
| extracted bundle is incomplete, or developer-only `java -jar` is missing `FINGRIND_SQLITE_LIBRARY` | `4` | `managed-runtime-failure` | SQLite runtime guidance describing the missing or incompatible managed library |
| runtime storage failure while opening, reading, or mutating a selected book | `4` | `storage-runtime-failure` | `Failed to open SQLite book connection.` and similar storage/runtime errors |
| other unexpected runtime failure outside the managed-runtime and storage families | `4` | `runtime-failure` | generic runtime-failure envelope with the thrown message and repair hint |

## Notes

- Error envelopes may include `hint` and `argument` fields to help an agent or human repair the
  call without consulting docs.
- `help`, `version`, `capabilities`, `print-request-template`, and `print-plan-template` reject
  extra arguments.
- `open-book` creates missing parent directories for nested `--book-file` paths.
- `generate-book-key-file` creates one new owner-only UTF-8 key file and refuses to overwrite an
  existing path. Generated files report `0600` on POSIX filesystems and `owner-only-acl` on
  Windows.
- `--book-key-file` must point to a non-empty single-line UTF-8 passphrase file; one trailing LF
  or CRLF is tolerated and stripped, but embedded control characters are rejected.
- Book key files must use POSIX owner-only permissions (`0400` or `0600`) on macOS/Linux or a
  Windows owner-only ACL on Windows, or the runtime rejects them.
- `--book-passphrase-stdin` reads one UTF-8 passphrase payload from standard input and therefore
  cannot be paired with `--request-file -`.
- `--book-passphrase-prompt` reads the passphrase from the controlling terminal without echo.
- `rekey-book` requires one current passphrase source plus one replacement passphrase source.
  The replacement options are `--new-book-key-file`, `--new-book-passphrase-stdin`, and
  `--new-book-passphrase-prompt`.
- `--new-book-passphrase-prompt` asks for the replacement secret twice and rejects mismatched
  entries.
- `rekey-book` rejects using the same key-file path for both current and replacement secrets, and
  standard input cannot supply both current and replacement secrets in the same invocation.
- The packaged CLI does not require an external `sqlite3` binary and does not shell out to
  `sqlite3`.
- The public packaged CLI bundles its own Java 26 runtime and managed SQLite 3.53.0 /
  SQLite3 Multiple Ciphers 2.3.3 native library.
- `capabilities.environment.distribution.runtimeDistribution` tells you whether the current
  process is running from a self-contained bundle, container image, source-checkout Gradle launch,
  or direct `java -jar` invocation.
- `capabilities.environment.distribution.supportedPublicCliBundleTargets` and
  `capabilities.environment.distribution.unsupportedPublicCliOperatingSystems` expose the public
  distribution matrix directly to automation.
- `capabilities.requestShapes.schemaDialect` declares the JSON Schema dialect, and
  `capabilities.requestShapes.*.schema` publishes executable request schemas alongside the field
  descriptor arrays.
- Request JSON must be one object document; duplicate keys and unknown fields are rejected at every
  object level.
- `inspect-book` is the safest machine-readable probe before `open-book`, `declare-account`, or
  `post-entry`, because it reports initialization state, detected book-format version, supported
  book-format version, and compatibility with the current binary.
- `list-accounts` returns paginated payloads with `limit`, `accounts`, and an optional opaque
  `nextCursor` that can be passed back through `--cursor`.
- `list-postings` returns paginated payloads with `limit`, `postings`, and an optional opaque
  `nextCursor` that can be passed back through `--cursor`.
- `inspect-book`, `list-accounts`, `list-postings`, `account-balance`, `trial-balance`,
  `account-ledger`, and `period-summary` accept `--output human`; all tabular read/report
  commands except `inspect-book` and `get-posting` also accept `--output csv`.
- `account-balance`, `trial-balance`, `account-ledger`, and `period-summary` can also write one
  PDF artifact through `--pdf-out <path>`. PDF export is explicit file output, not another stdout
  output mode. If the primary report succeeds but the PDF artifact fails, stdout still carries the
  report result and diagnostics emit a human warning with code pdf-export-warning.
- JSON amount fields remain canonical decimal strings without forced display scale, while
  `--output human` and `--output csv` render accounting-grade currency scale for operators and
  spreadsheet import.
- `print-plan-template` emits the accepted `execute-plan` request shape, including the generic
  nested `assertion` object for assertion steps.
- `execute-plan` reuses the same posting and query rules as the single-command surface, but runs
  the whole plan inside one atomic transaction and returns the resulting journal in
  `payload.journal` with `status: "plan-committed"` on success, or in `details.plan.journal` with
  `status: "plan-rejected"` / `status: "plan-assertion-failed"` on deterministic plan failure.
  Journal facts are typed objects with `kind`, `name`, and either `value` or nested grouped
  `facts`; successful `list-accounts` and `list-postings` steps keep both pagination facts and
  structured row groups instead of collapsing to counts alone.
- `capabilities` reports runtime-contract details under nested environment descriptors:
  `environment.distribution.publicCliDistribution`,
  `environment.distribution.sourceCheckoutJava`,
  `environment.distribution.runtimeDistribution`,
  `environment.distribution.supportedPublicCliBundleTargets`,
  `environment.distribution.unsupportedPublicCliOperatingSystems`,
  `environment.sqlite.libraryEnvironmentVariable`,
  `environment.sqlite.bundleHomeSystemProperty`,
  `environment.sqlite.requiredMinimumSqliteVersion`,
  `environment.sqlite.requiredSqlite3mcVersion`,
  `environment.sqlite.runtimeStatus`,
  `environment.sqlite.loadedSqliteVersion`,
  `environment.sqlite.loadedSqlite3mcVersion`,
  `environment.storage.bookProtectionMode`, and
  `environment.storage.defaultBookCipher`.
- `capabilities` also reports `preflight.semantics`, `preflight.commitGuarantee`, and
  `currencyModel` so agents can discover the advisory preflight contract and single-currency
  scope without reading source code.
- Gradle-driven local runs and the container image use a managed SQLite 3.53.0 / SQLite3 Multiple
  Ciphers 2.3.3 shared library.
- The developer-only `java -jar` path relies on `FINGRIND_SQLITE_LIBRARY` pointing at the managed
  SQLite3MC library produced by `prepareManagedSqlite` and `--enable-native-access=ALL-UNNAMED`
  on the `java` command line.
- `capabilities` is the best machine-readable contract surface.
- `capabilities.commands`, command groups, usage lines, aliases, output modes, and summaries are
  rendered from the contract protocol catalog rather than copied into the CLI renderer.
- `print-request-template` intentionally omits committed audit fields. Callers must not send
  `provenance.recordedAt` or `provenance.sourceChannel`.
- `print-plan-template` is the fastest machine bootstrap for a new book because it already includes
  `open-book` and a matching assertion step.
- `--book-passphrase-prompt` either reads from a supported controlling terminal or fails
  deterministically with `interactive-prompt-unavailable` and a repair hint that points to
  `--book-key-file` or `--book-passphrase-stdin`.
- FinGrind does not accept SQLite URI `key=` or `hexkey=` transport, plaintext CLI passphrase
  arguments, or environment-variable passphrase transport. The protected-book contract is always
  one explicit safe passphrase source plus the upstream default `chacha20` cipher.
- successful `post-entry` responses carry a FinGrind-generated UUID v7 `postingId`
- posting-side account failures are reported as `account-state-violations` with one or more
  structured issue objects in `details.violations`
- Wrong passphrases and non-FinGrind plaintext SQLite files are reported as the deterministic
  `book-authentication-failed` error instead of leaking raw SQLite symptoms such as `SQLITE_NOTADB`.
- In a source checkout, example payloads live under [docs/examples/](./examples/).
  Public release bundles do not ship those repository fixture paths.
