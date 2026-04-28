---
afad: "4.0"
version: "0.28.0"
domain: USER_CLI
updated: "2026-04-28"
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
`print-request-template` returns one raw JSON scaffold document so it can be redirected into a file
or piped into another process.
`print-plan-template` returns one raw JSON ledger-plan scaffold that already includes `open-book`,
account declarations, one posting step, and one balance assertion.
Both scaffold commands emit `actorType: "AGENT"` plus
`replace-before-commit-effective-date` and `replace-before-commit-*` provenance placeholders that
must be replaced before submission. Idempotency keys are single-use per book once one posting
commits successfully.
`generate-book-key-file` creates one new owner-only key file that contains a generated passphrase.
`open-book` explicitly initializes one new protected book.
`rekey-book` rotates the passphrase that protects one existing initialized book and restores the
pre-rekey file automatically if replacement-passphrase verification fails.
The supported backup path today is one closed-book encrypted file copy: stop using the book,
copy the `.sqlite` file to protected storage, keep the key file protected separately, and restore
by replacing the closed `.sqlite` file from that copy before reopening it.
`declare-account` inserts or reactivates one account in the selected book.
`inspect-book` reports lifecycle state, format metadata, and compatibility for one selected book.
`list-accounts` returns one stable page of the current account registry.
`get-posting`, `list-postings`, and `account-balance` expose read/query access to committed history.
`trial-balance`, `account-ledger`, and `period-summary` answer standard office-worker reporting
questions in one command.
`execute-plan` runs one ordered ledger plan atomically and returns one fixed JSON execution-journal
envelope on stdout; it does not negotiate `--output`.
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
`capabilities.commands` publishes those command contracts as grouped `CommandDescriptor` objects,
so automation can read the per-command `executionMode`, `outputModes`, `artifactOutputs`, aliases,
options, and summary directly instead of inferring stdout behavior from one global mode list.
Commands that advertise `--output` keep JSON as the default machine surface. Discovery,
administration, write, and query/report commands can render operator-facing `--output human`,
and the tabular read/report commands also accept `--output csv`. The report commands
`account-balance`, `trial-balance`, `account-ledger`, and `period-summary` can additionally write
one PDF artifact through `--pdf-out <path>`. If the report itself succeeds but the PDF write later
fails, FinGrind still returns the primary report on stdout and emits a repair warning on the
diagnostics stream instead of changing the command exit to `runtime-failure`. Commands that do not
advertise `--output` still publish one fixed stdout contract, either one raw JSON document or one
fixed JSON envelope.

## Commands

The command table below is generated from the canonical protocol catalog and contract-linted.

<!-- BEGIN GENERATED USER_CLI COMMAND TABLE -->
<table>
  <thead>
    <tr><th>Command</th><th>Aliases</th><th>Extra Arguments</th><th>Result</th></tr>
  </thead>
  <tbody>
    <tr><td><code>help</code></td><td><code>--help</code><br><code>-h</code></td><td><code>[--output &lt;json|human&gt;]</code></td><td>Print command usage, examples, and workflow guidance.</td></tr>
    <tr><td><code>version</code></td><td><code>--version</code></td><td><code>[--output &lt;json|human&gt;]</code></td><td>Print application identity, version, and description.</td></tr>
    <tr><td><code>capabilities</code></td><td>none</td><td><code>[--output &lt;json|human&gt;]</code></td><td>Print the canonical machine-readable contract for commands, request shapes, and responses.</td></tr>
    <tr><td><code>print-request-template</code></td><td><code>--print-request-template</code></td><td>none</td><td>Print the canonical minimal posting request scaffold JSON document.</td></tr>
    <tr><td><code>print-plan-template</code></td><td><code>--print-plan-template</code></td><td>none</td><td>Print the canonical minimal AI-agent ledger plan scaffold JSON document.</td></tr>
    <tr><td><code>generate-book-key-file</code></td><td>none</td><td><code>--book-key-file &lt;path&gt;</code><br><code>[--output &lt;json|human&gt;]</code></td><td>Create one new owner-only UTF-8 book key file with a generated high-entropy passphrase.</td></tr>
    <tr><td><code>open-book</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--output &lt;json|human&gt;]</code></td><td>Initialize a new book file with the canonical schema.</td></tr>
    <tr><td><code>rekey-book</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--new-book-key-file &lt;path&gt; | --new-book-passphrase-stdin | --new-book-passphrase-prompt</code><br><code>[--output &lt;json|human&gt;]</code></td><td>Rotate the passphrase that protects one existing book.</td></tr>
    <tr><td><code>declare-account</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>[--output &lt;json|human&gt;]</code></td><td>Declare or reactivate one account in the selected book.</td></tr>
    <tr><td><code>inspect-book</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--output &lt;json|human&gt;]</code></td><td>Inspect one selected book for lifecycle state, format version, and compatibility.</td></tr>
    <tr><td><code>list-accounts</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--limit &lt;1-200&gt;]</code><br><code>[--cursor &lt;cursor&gt;]</code><br><code>[--output &lt;json|human|csv&gt;]</code></td><td>List one stable page of declared accounts in the selected book using keyset pagination.</td></tr>
    <tr><td><code>get-posting</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--posting-id &lt;posting-id&gt;</code><br><code>[--output &lt;json|human&gt;]</code></td><td>Return one committed posting by durable posting identifier.</td></tr>
    <tr><td><code>list-postings</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--account-code &lt;account-code&gt;]</code><br><code>[--effective-date-from &lt;YYYY-MM-DD&gt;]</code><br><code>[--effective-date-to &lt;YYYY-MM-DD&gt;]</code><br><code>[--limit &lt;1-200&gt;]</code><br><code>[--cursor &lt;cursor&gt;]</code><br><code>[--output &lt;json|human|csv&gt;]</code></td><td>List one filtered page of committed postings in stable reverse-chronological order using keyset pagination.</td></tr>
    <tr><td><code>account-balance</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--account-code &lt;account-code&gt;</code><br><code>[--effective-date-from &lt;YYYY-MM-DD&gt;]</code><br><code>[--effective-date-to &lt;YYYY-MM-DD&gt;]</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|human|csv&gt;]</code></td><td>Compute grouped per-currency balances for one declared account.</td></tr>
    <tr><td><code>trial-balance</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--effective-date-to &lt;YYYY-MM-DD&gt;]</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|human|csv&gt;]</code></td><td>Compute one book-wide trial balance as of the selected effective date or the current durable posting horizon when no date filter is supplied.</td></tr>
    <tr><td><code>account-ledger</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--account-code &lt;account-code&gt;</code><br><code>[--effective-date-from &lt;YYYY-MM-DD&gt;]</code><br><code>[--effective-date-to &lt;YYYY-MM-DD&gt;]</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|human|csv&gt;]</code></td><td>Compute the running ledger for one account, including opening balances, per-posting movement, and closing balances.</td></tr>
    <tr><td><code>period-summary</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--effective-date-from &lt;YYYY-MM-DD&gt;</code><br><code>--effective-date-to &lt;YYYY-MM-DD&gt;</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|human|csv&gt;]</code></td><td>Compute one bounded office-work period summary with posting totals, currency totals, and per-account activity.</td></tr>
    <tr><td><code>execute-plan</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code></td><td>Execute one ordered AI-agent ledger plan inside a single atomic book transaction.</td></tr>
    <tr><td><code>preflight-entry</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>[--output &lt;json|human&gt;]</code></td><td>Validate one posting request without committing it.</td></tr>
    <tr><td><code>post-entry</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>[--output &lt;json|human&gt;]</code></td><td>Commit one posting request into the selected SQLite book.</td></tr>
  </tbody>
</table>
<!-- END GENERATED USER_CLI COMMAND TABLE -->

## Packaged CLI

Public FinGrind CLI downloads are self-contained bundle archives, not a standalone JAR.
The current public target set is:
- `macos-aarch64`
- `macos-x86_64`
- `linux-x86_64`
- `linux-aarch64`
- `windows-x86_64`

The protocol contract also declares `windows-aarch64` explicitly as an unsupported public bundle
target so machine clients can distinguish "known but not currently shipped" from "unknown target."

Linux bundles are built on Ubuntu GitHub-hosted runners and therefore target ordinary glibc Linux
hosts. They are not presented as a universal Linux binary for every libc variant.
Windows bundles are built on Windows GitHub-hosted runners with the native MSVC toolchain and are
published as `.zip` archives with the `bin\fingrind.ps1` launcher.
They also include `bin\fingrind.cmd` as a compatibility wrapper.

Each extracted archive also contains:
- a top-level `README.md` with the local quick start
- a top-level generated `bundle-manifest.json` with machine-readable distribution metadata and
  canonical bootstrap commands that point back to `help`, `capabilities`, and the request/plan
  template operations

Those bundle metadata surfaces disclose the same canonical target matrix and managed-SQLite
version pins that the source checkout, release automation, and shell acceptance verifiers use.

One public Unix bundle flow:

```bash
tar -xzf fingrind-0.28.0-macos-aarch64.tar.gz
./fingrind-0.28.0-macos-aarch64/bin/fingrind help
./fingrind-0.28.0-macos-aarch64/bin/fingrind \
  print-request-template > ./request.json
```

Edit `./request.json` and replace `replace-before-commit-effective-date` plus every
`replace-before-commit-*` provenance placeholder before using it with `preflight-entry` or
`post-entry`.

One public Windows bundle flow:

```powershell
Expand-Archive fingrind-0.28.0-windows-x86_64.zip -DestinationPath .
.\fingrind-0.28.0-windows-x86_64\bin\fingrind.ps1 help
.\fingrind-0.28.0-windows-x86_64\bin\fingrind.ps1 `
  print-request-template > .\request.json
```

Edit `.\request.json` and replace `replace-before-commit-effective-date` plus every
`replace-before-commit-*` provenance placeholder before using it with `preflight-entry` or
`post-entry`.

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
| unreadable or missing `--request-file` payload | `1` | `invalid-request` | `Request file does not exist: ...`, `Request file is not readable: ...`, or `Failed to read request file: ...` |
| malformed JSON or invalid request shape | `1` | `invalid-request` | `Failed to read request JSON.`, `Failed to read request JSON from standard input.`, or domain-validation text |
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
  cannot be paired with `--request-file -`. Feed that stdin route from a file or secret-fetching
  process rather than embedding the passphrase literal in shell history.
- `--book-passphrase-prompt` reads the passphrase from the controlling terminal without echo.
- `rekey-book` requires one current passphrase source plus one replacement passphrase source.
  The replacement options are `--new-book-key-file`, `--new-book-passphrase-stdin`, and
  `--new-book-passphrase-prompt`.
- `--new-book-passphrase-prompt` asks for the replacement secret twice and rejects mismatched
  entries.
- `rekey-book` rejects using the same key-file path for both current and replacement secrets, and
  standard input cannot supply both current and replacement secrets in the same invocation.
- The supported backup/restore workflow is one encrypted closed-book copy plus later file
  replacement. Do not copy a book while FinGrind is actively mutating it, and keep the copied
  `.sqlite` file under the same protected filesystem stance as the live book.
- The packaged CLI does not require an external `sqlite3` binary and does not shell out to
  `sqlite3`.
- The public packaged CLI bundles its own Java 26 runtime and managed SQLite 3.53.0 /
  SQLite3 Multiple Ciphers 2.3.3 native library.
- `capabilities.environment.distribution.runtimeDistribution` tells you whether the current
  process is running from a self-contained bundle, container image, source-checkout Gradle launch,
  or direct `java -jar` invocation.
- `capabilities.environment.distribution.supportedPublicCliBundleTargets` and
  `capabilities.environment.distribution.unsupportedPublicCliBundleTargets` expose the public
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
  `environment.distribution.unsupportedPublicCliBundleTargets`,
  `environment.sqlite.libraryEnvironmentVariable`,
  `environment.sqlite.bundleHomeSystemProperty`,
  `environment.sqlite.requiredCompileOptions`,
  `environment.sqlite.requiredMinimumSqliteVersion`,
  `environment.sqlite.requiredSqlite3mcVersion`,
  `environment.sqlite.requiredSqliteSourceId`,
  `environment.sqlite.compileOptionsVerification`,
  `environment.sqlite.runtimeStatus`,
  `environment.sqlite.runtimeProvenance`,
  `environment.sqlite.loadedLibraryPath`,
  `environment.sqlite.loadedSqliteVersion`,
  `environment.sqlite.loadedSqlite3mcVersion`,
  `environment.sqlite.loadedSqliteSourceId`,
  `environment.storage.bookProtectionMode`, and
  `environment.storage.defaultBookCipher`.
- `environment.sqlite.compileOptionsVerification` is `verified` only when the managed runtime is
  ready, `failed` when the loaded library is present but missing one or more required compile
  options, and "not-verified" when the runtime is unavailable or an earlier compatibility gate
  prevents a compile-option verdict.
- `capabilities` also reports `preflight.semantics`, `preflight.commitGuarantee`, and
  `currencyModel` so agents can discover the advisory preflight contract and single-currency
  scope without reading source code.
- Gradle-driven local runs and the container image use a managed SQLite 3.53.0 / SQLite3 Multiple
  Ciphers 2.3.3 shared library.
- The developer-only `java -jar` path relies on `FINGRIND_SQLITE_LIBRARY` pointing at the managed
  SQLite3MC library produced by `prepareManagedSqlite` and `--enable-native-access=ALL-UNNAMED`
  on the `java` command line.
- `capabilities` is the best machine-readable contract surface.
- `capabilities.requestInput.outputOption` publishes the canonical stdout-selection flag, while
  `capabilities.commands.<group>[]` publishes the authoritative per-command stdout and artifact
  contract through grouped `CommandDescriptor` objects.
- `capabilities.commands`, command groups, usage lines, aliases, output modes, artifact outputs,
  and summaries are rendered from the contract protocol catalog rather than copied into the CLI
  renderer.
- `print-request-template` intentionally omits committed audit fields. Callers must not send
  `provenance.recordedAt` or `provenance.sourceChannel`.
- `print-request-template` and `print-plan-template` intentionally emit
  `replace-before-commit-effective-date` plus `replace-before-commit-*` provenance placeholders so
  callers must provide a real posting date plus real actor, command, idempotency, and causation
  identifiers before submission.
- `print-plan-template` is the fastest machine bootstrap for a new book because it already includes
  `open-book` and a matching assertion step, but it remains a scaffold until those placeholders
  are replaced.
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
