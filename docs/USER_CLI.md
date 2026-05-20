---
afad: "4.0"
version: "0.42.0"
domain: USER_CLI
updated: "2026-05-20"
route:
  keywords: [fingrind, cli, commands, exit-codes, java26, sqlite, sqlite3mc, ffm, request-file, book-file, book-key-file, book-passphrase-stdin, book-passphrase-prompt, inspect-book, list-accounts, list-postings, account-balance, trial-balance, account-ledger, period-summary, output-mode, print-plan-template, execute-plan]
  questions: ["how do I run the fingrind cli", "what commands does fingrind expose", "how do I inspect a fingrind book before mutating it", "how do I page declared accounts in fingrind", "how do I run an AI-agent ledger plan in fingrind", "what exit codes does the fingrind cli use"]
---

# CLI Guide

**Purpose**: Run the packaged FinGrind CLI and understand its command, file, and exit behavior.
**Prerequisites**: For public use, download one self-contained FinGrind release bundle and unpack it.
No separate Java install is required for that path. For source-driven local runs,
`./gradlew :cli:run` manages SQLite 3.53.1 / SQLite3 Multiple Ciphers 2.3.4 automatically.
The generated source-checkout launcher from `./gradlew :cli:installShadowDist prepareManagedSqlite`
also carries the managed native-access, source-checkout runtime-distribution, and managed-SQLite
checkout-discovery defaults for you. For direct Java execution from a prepared checkout, use
`./scripts/direct-java-cli.sh` or `.\scripts\direct-java-cli.ps1`; those wrappers publish the
same managed runtime-distribution, checkout roots, and module-scoped native access as the other
developer launchers.

## Overview

FinGrind reads one command, writes deterministic output to standard output, and exits.
The `--book-file` path is the selected book identity, and there is no default database location.
Every book-bound command also requires exactly one passphrase source:
- `--book-key-file <path>` for a dedicated UTF-8 passphrase file
- `--book-passphrase-stdin` for one UTF-8 passphrase payload from standard input
- `--book-passphrase-prompt` for an interactive non-echo terminal prompt

`help` is returned when no command is supplied.
`help`, `version`, and `capabilities` default to human-readable discovery output on an interactive
terminal and to JSON when stdout is redirected or captured; they also accept `--output json` or
`--output human` explicitly.
Bare `help` is intentionally one short front-door overview. `capabilities` is the deep machine
contract. `help <command>` and `<command> --help` both return command-scoped usage, options,
executable examples, operator notes, and exit-code guidance for one selected command.
Request-file commands such as `declare-account`, `post-entry`, `preflight-entry`, and
`execute-plan` also inline the accepted request shape, one canonical template, and the relevant
enum vocabulary so an operator or agent can form a valid payload from the CLI alone.
`print-request-template` returns one raw JSON scaffold document so it can be redirected into a file
or piped into another process. With no topic it emits the canonical posting scaffold; with
`declare-account` it emits the canonical account-declaration scaffold; `post-entry` and
`preflight-entry` are accepted posting-scaffold topics.
`print-plan-template` returns one raw JSON ledger-plan scaffold that already includes `open-book`,
account declarations, one posting step, and one balance assertion.
Both scaffold commands emit `actorType: "AGENT"` plus
`replace-before-commit-effective-date` and `replace-before-commit-*` provenance placeholders that
must be replaced before submission. Idempotency keys are single-use per book once one posting
commits successfully.
`generate-book-key-file` creates one new owner-only key file that contains a generated passphrase.
The easiest path is one missing private parent directory that FinGrind can create securely, or one
existing parent directory that you have already tightened to owner-only permissions.
`open-book` explicitly initializes one new protected book.
`rekey-book` rotates the passphrase that protects one existing initialized book and restores the
pre-rekey file automatically if replacement-passphrase verification fails.
`backup-book` exports one verified encrypted backup pair for a closed book, `restore-book`
verifies that pair before replacing the live book path and leaves the restored live book protected
by that backup key file, `inspect-rekey-rollback` reports stale same-directory rollback artifacts,
`restore-rekey-rollback` rewinds one interrupted rekey from one selected rollback artifact, and
`delete-rekey-rollback` removes one stale rollback artifact without touching the live book path
after verifying one initialized live book through one explicit passphrase source.
`declare-account` inserts or reactivates one account in the selected book, with immutable
`accountType`, immutable `accountRole`, immutable declared taxonomy, and derived
`normalBalance`.
`close-period` closes one contiguous reporting period into one policy-selected active declared
closing equity account, and successful results surface that selected closing-equity account code
plus the per-currency closed totals that were moved into equity. The first close may begin before
the earliest posting date; after one close is recorded, later closes must start on the day after
the closed-through horizon. Built-in closing-equity mapping is entity-form specific:
`FREELANCER` and `SOLE_PROPRIETORSHIP` require `OWNER_CAPITAL`, `COMPANY` and `BRANCH` require
`RETAINED_EARNINGS`, `PARTNERSHIP` requires `PARTNER_CURRENT`, `NONPROFIT` requires
`ACCUMULATED_SURPLUS`, and `OTHER` requires `OTHER_EQUITY`.
`inspect-book` reports lifecycle state, format metadata, compatibility, and the active hard-break
migration policy for one selected book.
`list-accounts` returns one stable page of the current account registry.
`get-posting`, `list-postings`, and `account-balance` expose read/query access to committed history.
`trial-balance`, `account-ledger`, `period-summary`, `financial-position`, `income-statement`, and
`changes-in-equity` answer standard office-worker reporting questions in one command.
`execute-plan` runs one ordered ledger plan atomically and returns one fixed JSON execution-journal
envelope on stdout; it does not negotiate `--output`.
`preflight-entry` and `post-entry` both require an already initialized book and declared active
accounts for every journal line they touch, and surface those failures as
`account-state-violations` with structured `details.violations`.
`preflight-entry` is advisory only: FinGrind still re-checks commit-time durability rules inside
the write transaction before `post-entry` succeeds.
Every journal entry is single-currency; mixed-currency lines inside one entry are not supported.
Every journal-line amount must be greater than zero.
Protected books use SQLite3 Multiple Ciphers 2.3.4 with the upstream default `chacha20` cipher.
The operation catalog rendered in `help` and `capabilities` is contract-owned protocol metadata,
so CLI help, parser aliases, output modes, summaries, query limits, and the separation between
executable examples and operator notes share one source.
`capabilities.commands` publishes those command contracts as grouped `CommandDescriptor` objects,
so automation can read the per-command `executionMode`, `outputModes`, `artifactOutputs`, aliases,
options, and summary directly instead of inferring stdout behavior from one global mode list.
Commands that advertise `--output` default successful stdout to human text on an interactive
terminal and to JSON when stdout is redirected or captured. Discovery, administration, write, and
query/report commands can render operator-facing `--output human`, and the tabular read/report
commands also accept `--output csv`. Invalid invocation failures default to human repair guidance
unless one recognized machine output mode is selected explicitly, such as `--output json`. The report commands
`account-balance`, `trial-balance`, `account-ledger`, `period-summary`, `financial-position`,
`income-statement`, and `changes-in-equity` can additionally write one PDF artifact through
`--pdf-out <path>`. Successful exports keep the main stdout result unchanged. When the primary
stdout result is JSON, the success envelope also carries one `artifacts[]` entry with
`format: "pdf"` plus the normalized written `path`; human and CSV modes report the same path on
the diagnostics stream. If the report itself succeeds but the PDF write later fails, FinGrind
still returns the primary report on stdout and emits a repair warning on the diagnostics stream
instead of changing the command exit to `runtime-failure`. Commands that do not advertise
`--output` still publish one fixed stdout contract, either one raw JSON document or one fixed JSON
envelope.

## Commands

The command table below is generated from the canonical protocol catalog and contract-linted.

<!-- BEGIN GENERATED USER_CLI COMMAND TABLE -->
<table>
  <thead>
    <tr><th>Command</th><th>Aliases</th><th>Extra Arguments</th><th>Result</th></tr>
  </thead>
  <tbody>
    <tr><td><code>help</code></td><td><code>--help</code><br><code>-h</code></td><td><code>[&lt;command&gt;]</code><br><code>[--output &lt;json|human&gt;]</code></td><td>Print command usage, examples, and workflow guidance.</td></tr>
    <tr><td><code>version</code></td><td><code>--version</code></td><td><code>[--output &lt;json|human&gt;]</code></td><td>Print application identity, version, and description.</td></tr>
    <tr><td><code>capabilities</code></td><td>none</td><td><code>[--output &lt;json|human&gt;]</code></td><td>Print the canonical machine-readable contract for commands, request shapes, and responses.</td></tr>
    <tr><td><code>environment</code></td><td>none</td><td><code>[--output &lt;json|human&gt;]</code></td><td>Print live runtime, distribution, and SQLite provenance facts for this launcher instance.</td></tr>
    <tr><td><code>print-request-template</code></td><td><code>--print-request-template</code></td><td><code>[post-entry|preflight-entry|declare-account]</code></td><td>Print the canonical minimal request scaffold JSON document for one request-file command.</td></tr>
    <tr><td><code>print-plan-template</code></td><td><code>--print-plan-template</code></td><td>none</td><td>Print the canonical minimal AI-agent ledger plan scaffold JSON document.</td></tr>
    <tr><td><code>generate-book-key-file</code></td><td>none</td><td><code>--book-key-file &lt;path&gt;</code><br><code>[--output &lt;json|human&gt;]</code></td><td>Create one new owner-only UTF-8 book key file with a generated high-entropy passphrase.</td></tr>
    <tr><td><code>open-book</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--entity-name &lt;text&gt;</code><br><code>--entity-form &lt;entity-form&gt;</code><br><code>[--owner-model &lt;owner-model&gt;]</code><br><code>[--reporting-obligation-status &lt;reporting-obligation-status&gt;]</code><br><code>[--business-activity-tag &lt;business-activity-tag&gt; ...]</code><br><code>--functional-currency &lt;currency-code&gt;</code><br><code>--fiscal-year-start &lt;MM-DD&gt;</code><br><code>--accounting-basis &lt;accounting-basis&gt;</code><br><code>[--output &lt;json|human&gt;]</code></td><td>Initialize a new book file with the canonical schema.</td></tr>
    <tr><td><code>rekey-book</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--replacement-book-key-file &lt;existing-path&gt; | --replacement-book-passphrase-stdin | --replacement-book-passphrase-prompt</code><br><code>[--output &lt;json|human&gt;]</code></td><td>Rotate the passphrase that protects one existing book.</td></tr>
    <tr><td><code>backup-book</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--backup-file &lt;path&gt;</code><br><code>--backup-book-key-file &lt;path&gt;</code><br><code>[--output &lt;json|human&gt;]</code></td><td>Export one closed encrypted-book backup pair without overwriting any existing destination.</td></tr>
    <tr><td><code>restore-book</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--backup-file &lt;path&gt;</code><br><code>--backup-book-key-file &lt;path&gt;</code><br><code>[--output &lt;json|human&gt;]</code></td><td>Restore one verified encrypted-book backup pair onto the selected live book path.</td></tr>
    <tr><td><code>inspect-rekey-rollback</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>[--output &lt;json|human&gt;]</code></td><td>Inspect stale sibling rekey rollback artifacts for the selected book path.</td></tr>
    <tr><td><code>delete-rekey-rollback</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>[--rollback-file &lt;path&gt;]</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--output &lt;json|human&gt;]</code></td><td>Delete one selected stale sibling rekey rollback artifact.</td></tr>
    <tr><td><code>restore-rekey-rollback</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>[--rollback-file &lt;path&gt;]</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--output &lt;json|human&gt;]</code></td><td>Restore one selected stale sibling rekey rollback artifact onto the live book path.</td></tr>
    <tr><td><code>declare-account</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>[--output &lt;json|human&gt;]</code></td><td>Declare or reactivate one account in the selected book.</td></tr>
    <tr><td><code>close-period</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--effective-date-from &lt;YYYY-MM-DD&gt;</code><br><code>--effective-date-to &lt;YYYY-MM-DD&gt;</code><br><code>[--output &lt;json|human&gt;]</code></td><td>Close one contiguous reporting period into one policy-selected closing equity account.</td></tr>
    <tr><td><code>inspect-book</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--output &lt;json|human&gt;]</code></td><td>Inspect one selected book for lifecycle state, format version, and compatibility.</td></tr>
    <tr><td><code>list-accounts</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--limit &lt;1-200&gt;]</code><br><code>[--cursor &lt;cursor&gt;]</code><br><code>[--output &lt;json|human|csv&gt;]</code></td><td>List one stable page of declared accounts in the selected book using keyset pagination.</td></tr>
    <tr><td><code>get-posting</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--posting-id &lt;posting-id&gt;</code><br><code>[--output &lt;json|human&gt;]</code></td><td>Return one committed posting by durable posting identifier.</td></tr>
    <tr><td><code>list-postings</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--account-code &lt;account-code&gt;]</code><br><code>[--effective-date-from &lt;YYYY-MM-DD&gt;]</code><br><code>[--effective-date-to &lt;YYYY-MM-DD&gt;]</code><br><code>[--limit &lt;1-200&gt;]</code><br><code>[--cursor &lt;cursor&gt;]</code><br><code>[--output &lt;json|human|csv&gt;]</code></td><td>List one filtered page of committed postings in stable reverse-chronological order using keyset pagination.</td></tr>
    <tr><td><code>account-balance</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--account-code &lt;account-code&gt;</code><br><code>[--effective-date-from &lt;YYYY-MM-DD&gt;]</code><br><code>[--effective-date-to &lt;YYYY-MM-DD&gt;]</code><br><code>[--posting-coverage &lt;all-posting-kinds|non-closing-postings&gt;]</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|human|csv&gt;]</code></td><td>Compute grouped per-currency balances for one declared account.</td></tr>
    <tr><td><code>trial-balance</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--effective-date-to &lt;YYYY-MM-DD&gt;]</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|human|csv&gt;]</code></td><td>Compute one book-wide trial balance as of the selected effective date or the latest committed posting date when no date filter is supplied.</td></tr>
    <tr><td><code>account-ledger</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--account-code &lt;account-code&gt;</code><br><code>[--effective-date-from &lt;YYYY-MM-DD&gt;]</code><br><code>[--effective-date-to &lt;YYYY-MM-DD&gt;]</code><br><code>[--posting-coverage &lt;all-posting-kinds|non-closing-postings&gt;]</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|human|csv&gt;]</code></td><td>Compute the running ledger for one account, including opening balances, per-posting movement, and closing balances.</td></tr>
    <tr><td><code>period-summary</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--effective-date-from &lt;YYYY-MM-DD&gt;</code><br><code>--effective-date-to &lt;YYYY-MM-DD&gt;</code><br><code>[--posting-coverage &lt;all-posting-kinds|non-closing-postings&gt;]</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|human|csv&gt;]</code></td><td>Compute one bounded accounting-period summary with posting totals, currency totals, and per-account activity.</td></tr>
    <tr><td><code>financial-position</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--effective-date-to &lt;YYYY-MM-DD&gt;]</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|human|csv&gt;]</code></td><td>Compute one statement of financial position as of the selected effective date or the latest committed posting date when no date filter is supplied.</td></tr>
    <tr><td><code>income-statement</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--effective-date-from &lt;YYYY-MM-DD&gt;</code><br><code>--effective-date-to &lt;YYYY-MM-DD&gt;</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|human|csv&gt;]</code></td><td>Compute one bounded income statement for the selected reporting period.</td></tr>
    <tr><td><code>changes-in-equity</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--effective-date-from &lt;YYYY-MM-DD&gt;</code><br><code>--effective-date-to &lt;YYYY-MM-DD&gt;</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|human|csv&gt;]</code></td><td>Compute one bounded statement of changes in equity for the selected reporting period.</td></tr>
    <tr><td><code>execute-plan</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>[--result-detail &lt;summary|full&gt;]</code></td><td>Execute one ordered AI-agent ledger plan inside a single atomic book transaction. Summary output is the default; request the full execution journal explicitly when needed.</td></tr>
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
Each published bundle archive also ships with one sibling `.sha256` digest file on the GitHub
Release and one GitHub artifact attestation. Use `gh attestation verify --repo resoltico/FinGrind
<downloaded-archive>` when you need publisher-authenticated provenance, and treat the `.sha256`
file as a convenience integrity digest rather than the only trust anchor.

One public Unix bundle flow:

```bash
bundle_archive="$(printf '%s\n' ./fingrind-*-macos-aarch64.tar.gz | head -n 1)"
bundle_root="${bundle_archive#./}"
bundle_root="${bundle_root%.tar.gz}"
tar -xzf "${bundle_archive}"
"./${bundle_root}/bin/fingrind" help
"./${bundle_root}/bin/fingrind" \
  print-request-template > ./request.json
```

Edit `./request.json` and replace `replace-before-commit-effective-date` plus every
`replace-before-commit-*` provenance placeholder before using it with `preflight-entry` or
`post-entry`.

One public Windows bundle flow:

```powershell
$bundleArchive = (Get-ChildItem -LiteralPath . -Filter 'fingrind-*-windows-x86_64.zip' | Select-Object -First 1).FullName
$bundleRoot = Join-Path . ([System.IO.Path]::GetFileNameWithoutExtension($bundleArchive))
Expand-Archive $bundleArchive -DestinationPath . -Force
& (Join-Path $bundleRoot 'bin\fingrind.ps1') help
& (Join-Path $bundleRoot 'bin\fingrind.ps1') `
  print-request-template > .\request.json
```

Edit `.\request.json` and replace `replace-before-commit-effective-date` plus every
`replace-before-commit-*` provenance placeholder before using it with `preflight-entry` or
`post-entry`.

In the examples below, `fingrind` means the extracted bundle launcher.
Command-scoped help and repair hints emitted from a self-contained bundle use that same launcher
path, such as `./bin/fingrind` on POSIX bundles or `.\bin\fingrind.ps1` on Windows bundles.

For copy-paste use from one extracted bundle session, define `fingrind` once first.

```bash
fingrind() { "./<bundle-root>/bin/fingrind" "$@"; }
```

```powershell
function fingrind { & .\<bundle-root>\bin\fingrind.ps1 @args }
```

For source-driven local use, prefer:

```bash
./gradlew :cli:run --args="help"
```

For a source-checkout launcher that behaves like a local installed executable:

```bash
./gradlew :cli:installShadowDist prepareManagedSqlite
./scripts/source-checkout-cli.sh help
```

That wrapper resolves the active CLI build directory, then invokes the generated launcher with the
native-access flag, the source-checkout runtime-distribution marker, and the managed-SQLite
checkout lookup already baked in.

For local bundle verification from a source checkout:

```bash
./gradlew :cli:bundleCliArchive
./scripts/bundle-smoke.sh
```

If you restage a local bundle repeatedly from a source checkout, FinGrind prunes older
`fingrind-*` staging roots under the active CLI Gradle build directory before writing the current
versioned bundle tree, and `./gradlew :cli:bundleCliArchive` removes obsolete `fingrind-*`
archives and checksum files from both the active distribution directory and any legacy in-checkout
`cli/build/distributions/` leftovers before writing the current host bundle artifact. After a
successful run, the task prints the exact archive path and checksum path it produced so the bundle
can be inspected or passed straight to `./scripts/bundle-smoke.sh` even when the active build
directory lives outside the checkout.

The direct-Java wrapper is the supported advanced contributor path outside Gradle, and it is not
the public FinGrind download contract:

```bash
./gradlew :cli:shadowJar prepareManagedSqlite
./scripts/direct-java-cli.sh help
```

That wrapper resolves the active CLI build directory and then runs the prepared application module.
When the module stays under the prepared checkout layout, it auto-discovers the managed SQLite
library and grants native access only to the `fingrind` module.

`--request-file -` means read the request JSON from standard input.
`--book-passphrase-stdin` means read the book passphrase from standard input instead.
Those two stdin modes cannot be combined in one invocation.
Whether it comes from a file or standard input, one request JSON document must fit within the
`1048576`-byte UTF-8 payload limit.
Use the extracted bundle launcher or the direct-Java wrapper for real process exit codes;
`./gradlew :cli:run` wraps non-zero application exits as a Gradle task failure.

## Exit Codes

| Exit Code | Meaning | Typical Output |
|:----------|:--------|:---------------|
| `0` | successful command | `ok`, including request templates, query/report payloads, preflight payloads, committed posting payloads, and succeeded plan payloads |
| `1` | invalid invocation or malformed request | human repair text by default, or `error` with code `unknown-command`, `invalid-request`, `invalid-page-cursor`, and similar when a recognized machine output mode is selected explicitly |
| `2` | deterministic refusal after the command was understood | human `Rejected`, `error`, `rejected`, or `ok` with `payload.status: "rejected"` for `execute-plan` |
| `3` | valid `execute-plan` request whose assertion step failed | `ok` with `payload.status: "assertion-failed"` |
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
| oversized request JSON payload from file or standard input | `1` | `invalid-request` | `Request file exceeded the supported 1048576-byte UTF-8 limit: ...` or `Request JSON from standard input exceeded the supported 1048576-byte UTF-8 limit.` |
| unreadable or missing `--request-file` payload | `1` | `invalid-request` | `Request file does not exist: ...`, `Request file is not readable: ...`, or `Failed to read request file: ...` |
| malformed JSON or invalid request shape | `1` | `invalid-request` | `Failed to read request JSON at line ..., column ....`, `Failed to read request JSON from standard input.`, or domain-validation text; malformed JSON also publishes `details.parseMessage`, `details.line`, and `details.column`, and journal grammar failures publish `details.violations` |
| malformed `list-accounts --cursor` or `list-postings --cursor` | `1` | `invalid-page-cursor` | `Unsupported account page cursor: ...` or `Unsupported posting page cursor: ...` |
| book is missing or never opened | `2` | `administration-book-not-initialized`, `query-book-not-initialized`, or `posting-book-not-initialized` | `The selected book does not exist or has not been initialized with open-book.` |
| query names an undeclared account | `2` | `unknown-account` | `Account '...' is not declared in this book.` |
| posting uses undeclared or inactive accounts | `2` | `account-state-violations` | `Posting references undeclared or inactive accounts.` plus `details.violations` |
| duplicate idempotency or reversal policy refusal | `2` | `duplicate-idempotency-key`, `reversal-target-not-found`, and similar | request was understood but refused by current book state |
| wrong book key, damaged/truncated protected book, or unsupported protected SQLite variant | `2` | `protected-book-verification-failed` | `FinGrind could not verify the selected protected book with the supplied passphrase source.` |
| invalid key-file contents, file permissions, parent-directory permissions, or unreadable key-file path | `2` | `invalid-book-key-file` | `Book access refused because the selected book key file path, permissions, parent directory, or contents do not satisfy the protected-book contract.` |
| unreadable, oversized, malformed, empty, or control-character passphrase payload on stdin or another selected passphrase route | `2` | `invalid-book-passphrase-source` | `Failed to read the FinGrind book passphrase from standard input.`, `The FinGrind book passphrase source exceeded the 4096-byte UTF-8 limit: ...`, or UTF-8/single-line passphrase validation text |
| unsupported prompt environment | `2` | `interactive-prompt-unavailable` | `FinGrind cannot prompt for a book passphrase because no interactive console is available.` |
| requested PDF artifact written successfully after a successful report result | `0` | diagnostics info pdf-exported | primary report remains on stdout; JSON success envelopes also publish `artifacts[].format` plus normalized `artifacts[].path`, and diagnostics report the same PDF path for human/CSV flows |
| requested PDF artifact cannot be written after a successful report result | `0` | diagnostics warning pdf-export-warning | primary report remains on stdout and the warning explains how to repair the `--pdf-out` path |
| extracted bundle is incomplete, a prepared checkout is missing its managed SQLite build, or a custom direct-Java launch cannot resolve the managed library | `4` | `managed-runtime-failure` | SQLite runtime guidance describing the missing or incompatible managed library |
| runtime storage failure while opening, reading, or mutating a selected book | `4` | `storage-runtime-failure` | `Failed to open SQLite book connection.` and similar storage/runtime errors |
| other unexpected runtime failure outside the managed-runtime and storage families | `4` | `runtime-failure` | generic runtime-failure envelope with the thrown message and repair hint |

## Notes

- Error envelopes may include `hint` and `argument` fields to help an agent or human repair the
  call without consulting docs, and human `Rejected` renders now surface the same repair hint plus
  any structured rejection details that the machine envelope carries.
- If you want one machine envelope while probing malformed input, add `--output json` on
  commands that advertise it; otherwise invalid invocations default to human repair text.
- `help`, `version`, `capabilities`, `print-request-template`, and `print-plan-template` reject
  extra arguments.
- `open-book` creates missing parent directories for nested `--book-file` paths with owner-only
  protection. When the parent already exists, FinGrind requires it to remain owner-only.
- `generate-book-key-file` creates one new owner-only UTF-8 key file and refuses to overwrite an
  existing path. When the selected parent directory does not exist, FinGrind creates it with
  owner-only protection; when the parent already exists, FinGrind requires it to remain
  owner-only. Generated files report `0600` on POSIX filesystems and `owner-only-acl` on
  Windows.
- `--book-key-file` must point to a non-empty single-line UTF-8 passphrase file no larger than
  4096 bytes; one trailing LF or CRLF is tolerated and stripped, but embedded control characters
  are rejected.
- Book key files must use POSIX owner-only permissions (`0400` or `0600`) on macOS/Linux or a
  Windows owner-only ACL on Windows, their containing directory must also remain owner-only, and
  the public examples keep those files under a separate `./secrets/` tree instead of beside the
  book.
- `--book-passphrase-stdin` reads one UTF-8 passphrase payload from standard input and therefore
  cannot be paired with `--request-file -`. The accepted stdin payload is capped at 4096 bytes.
  Feed that stdin route from a file or secret-fetching process rather than embedding the
  passphrase literal in shell history.
- `--book-passphrase-prompt` reads the passphrase from the controlling terminal without echo, and
  the accepted prompt payload is also capped at 4096 UTF-8 bytes after normalization.
- `--request-file <path>` reads one UTF-8 JSON object document capped at `1048576` bytes.
- `--request-file -` reads one UTF-8 JSON object document from standard input under that same
  `1048576`-byte limit.
- `rekey-book` requires one current passphrase source plus one replacement passphrase source.
  The replacement options are `--replacement-book-key-file`, `--replacement-book-passphrase-stdin`, and
  `--replacement-book-passphrase-prompt`.
- `--replacement-book-key-file` must point to an existing generated or operator-supplied secret
  file. Generate that file first with `generate-book-key-file` if you want FinGrind to create it
  for you.
- `--replacement-book-passphrase-prompt` asks for the replacement secret twice and rejects mismatched
  entries.
- `rekey-book` rejects using the same key-file path for both current and replacement secrets, and
  standard input cannot supply both current and replacement secrets in the same invocation.
- `rekey-book` uses one same-directory encrypted rollback copy while rotation is in progress. If a
  crash or forced stop interrupts cleanup, that stale `*.rekey-rollback-*.sqlite` file remains in
  the book directory until you inspect or delete it, and later opens warn when they detect it.
- The supported backup/restore workflow is one encrypted closed-book copy plus later file
  replacement. Do not copy a book while FinGrind is actively mutating it, and keep the copied
  `.sqlite` file under the same protected filesystem stance as the live book while storing key
  material separately from the copied book tree.
- `restore-book` reuses the supplied backup key file as the secret for the restored live book, so
  reopen the restored `--book-file` path with that same key file after the replacement completes.
- The packaged CLI does not require an external `sqlite3` binary and does not shell out to
  `sqlite3`.
- The public packaged CLI bundles its own Java 26 runtime and managed SQLite 3.53.1 /
  SQLite3 Multiple Ciphers 2.3.4 native library.
- `environment.distribution.runtimeDistribution` tells you whether the current
  process is running from a self-contained bundle, container image, source-checkout Gradle launch,
  or direct Java wrapper invocation.
- `environment.distribution.supportedPublicCliBundleTargets` and
  `environment.distribution.unsupportedPublicCliBundleTargets` expose the public
  distribution matrix directly to automation.
- `capabilities.requestShapes.schemaDialect` declares the JSON Schema dialect, and
  `capabilities.requestShapes.*.schema` publishes executable request schemas alongside the field
  descriptor arrays.
- Request JSON must be one object document; duplicate keys and unknown fields are rejected at every
  object level.
- `inspect-book` is the safest machine-readable probe before `open-book`, `declare-account`, or
  `post-entry`, because it reports initialization state, detected book-format version, supported
  book-format version, and compatibility with the current binary.
- Read-oriented commands do not repair book-file permissions as a side effect. Permission repair
  happens on mutation-capable opens such as `open-book` and `rekey-book`.
- `list-accounts` returns paginated payloads with `limit`, `accounts`, and an optional opaque
  `nextCursor` that can be passed back through `--cursor`.
- `list-postings` returns paginated payloads with `limit`, `postings`, and an optional opaque
  `nextCursor` that can be passed back through `--cursor`.
- `inspect-book`, `list-accounts`, `list-postings`, `account-balance`, `trial-balance`,
  `account-ledger`, `period-summary`, `financial-position`, `income-statement`, and
  `changes-in-equity` accept `--output human`; all tabular read/report commands except
  `inspect-book` and `get-posting` also accept `--output csv`.
- `account-balance`, `trial-balance`, `account-ledger`, `period-summary`, `financial-position`,
  `income-statement`, and `changes-in-equity` can also write one PDF artifact through
  `--pdf-out <path>`. PDF export is explicit file output, not another stdout output mode. If the
  primary report succeeds, JSON success envelopes publish the normalized artifact under
  `artifacts[]`, while human/CSV flows emit a human info block with code `pdf-exported` and the
  same path on diagnostics. If the PDF artifact fails, stdout still carries the report result and
  diagnostics emit a human warning with code `pdf-export-warning`.
- JSON money fields are typed exact-money objects with `currencyCode` and `minorUnits`,
  while `--output human` and `--output csv` render accounting-grade currency scale for operators
  and spreadsheet import.
- `print-plan-template` emits the accepted `execute-plan` request shape, including the generic
  nested `assertion` object for assertion steps.
- `execute-plan` reuses the same posting and query rules as the single-command surface, but runs
  the whole plan inside one atomic transaction and returns a bounded `payload.summary` by default.
  `--result-detail full` additionally includes `payload.journal` on success or
  `details.plan.journal` on deterministic plan failure. Journal facts are typed objects with
  `kind`, `name`, and either `value` or nested grouped `facts`; successful `list-accounts` and
  `list-postings` steps keep both pagination facts and structured row groups instead of collapsing
  to counts alone.
- `environment` reports runtime-contract details directly under:
  `payload.distribution.publicCliDistribution`,
  `payload.distribution.sourceCheckoutJava`,
  `payload.distribution.runtimeDistribution`,
  `payload.distribution.supportedPublicCliBundleTargets`,
  `payload.distribution.unsupportedPublicCliBundleTargets`,
  `payload.sqlite.bundleHomeSystemProperty`,
  `payload.sqlite.requiredCompileOptions`,
  `payload.sqlite.forbiddenCompileOptions`,
  `payload.sqlite.requiresSecureMemorySupport`,
  `payload.sqlite.requiredMinimumSqliteVersion`,
  `payload.sqlite.requiredSqlite3mcVersion`,
  `payload.sqlite.requiredSqliteSourceId`,
  `payload.sqlite.runtime.compileOptionsVerification`,
  `payload.sqlite.runtime.status`,
  `payload.sqlite.runtime.runtimeProvenance`,
  `payload.sqlite.runtime.runtimeTrustBasis`,
  `payload.sqlite.runtime.loadedLibraryPath` as a redacted public path hint,
  `payload.sqlite.runtime.loadedSqliteVersion`,
  `payload.sqlite.runtime.loadedSqlite3mcVersion`,
  `payload.sqlite.runtime.loadedSqliteSourceId`,
  `payload.storage.bookProtectionMode`, and
  `payload.storage.defaultProtectedBookFormat.cipher`,
  `payload.storage.defaultProtectedBookFormat.legacyMode`,
  `payload.storage.defaultProtectedBookFormat.pageSize`,
  `payload.storage.defaultProtectedBookFormat.reservedBytes`,
  `environment.storage.defaultProtectedBookFormat.kdfIter`, and
  `environment.storage.defaultProtectedBookFormat.plaintextHeaderSize`.
- `environment.sqlite.runtime.compileOptionsVerification` is `verified` only when the managed
  runtime is ready, `failed` when the loaded library is present but violates the compile-option
  contract by missing required options or exposing forbidden options, and `not-verified` when the
  runtime is unavailable, when the probe resolved one runtime target but aborted before
  verification could finish, or when an earlier compatibility gate prevents a compile-option
  verdict.
- `capabilities` also reports `preflight.semantics`, `preflight.commitGuarantee`, and
  `currencyModel` so agents can discover the advisory preflight contract and single-currency
  scope without reading source code.
- Gradle-driven local runs, the generated source-checkout launcher, and the container image use a
  managed SQLite 3.53.1 / SQLite3 Multiple Ciphers 2.3.4 shared library.
- The developer direct-Java wrappers auto-discover that managed SQLite3MC library and scoped
  native access when they run from a prepared checkout. Direct-Java launches outside that checkout
  shape are unsupported.
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
- Protected-book encryption covers the SQLite book bytes themselves, but not decoded query
  results in process memory, copied backups, exported reports, or key files stored beside the
  database. Treat those artifacts as separate protection problems.
- FinGrind forces SQLite temp storage into memory. If an operator changes that policy outside the
  supported runtime, any temp spill files fall outside the documented encrypted-book boundary.
- successful `post-entry` responses carry a FinGrind-generated UUID v7 `postingId`
- posting-side account failures are reported as `account-state-violations` with one or more
  structured issue objects in `details.violations`
- Wrong passphrases, damaged or truncated protected books, and unsupported protected SQLite files
  are reported as the deterministic `protected-book-verification-failed` error instead of leaking
  raw SQLite symptoms such as `SQLITE_NOTADB`.
- In a source checkout, example payloads live under [docs/examples/](./examples/).
  Public release bundles do not ship those repository fixture paths.
