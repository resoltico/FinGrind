---
afad: "4.0"
version: "0.57.0"
domain: USER_CLI
updated: "2026-06-19"
route:
  keywords: [fingrind, cli, commands, exit-codes, java26, sqlite, sqlite3mc, ffm, request-file, book-file, book-key-file, book-passphrase-stdin, book-passphrase-prompt, inspect-book, list-accounts, list-postings, account-balance, trial-balance, account-ledger, period-summary, output-mode, print-plan-template, execute-plan]
  questions: ["how do I run the fingrind cli", "what commands does fingrind expose", "how do I inspect a fingrind book before mutating it", "how do I page declared accounts in fingrind", "how do I run an AI-agent ledger plan in fingrind", "what exit codes does the fingrind cli use"]
---

# CLI Guide

**Purpose**: Run the packaged FinGrind CLI and understand its command, file, and exit behavior.
**Prerequisites**: For public use, download one self-contained FinGrind release bundle that
matches your host and unpack it. The published Linux bundles require glibc `2.34` or newer; treat
[USER_INSTALL.md](./USER_INSTALL.md) and the extracted `bundle-manifest.json` as the authoritative
compatibility owners. No separate Java install is required for that path. For source-driven local runs,
`./gradlew :cli:run` manages SQLite 3.53.2 / SQLite3 Multiple Ciphers 2.3.5 automatically.
For exact public package names, checksum commands, and the published container reference, start
with [USER_INSTALL.md](./USER_INSTALL.md). The source-checkout wrapper
`./scripts/source-checkout-cli.sh` or `.\scripts\source-checkout-cli.ps1` and the direct-Java
wrapper `./scripts/direct-java-cli.sh` or `.\scripts\direct-java-cli.ps1` both publish the same
managed runtime-distribution, checkout roots, and module-scoped native access as the other
developer launch surfaces, and they refresh the raw JAR plus the Gradle-owned Java 26 toolchain
manifest automatically when the checkout has moved.

## Overview

FinGrind reads one command, writes deterministic output to standard output, and exits.
The `--book-file` path is the selected book identity, and there is no default database location.
Every book-bound command also requires exactly one passphrase source:
- `--book-key-file <path>` for a dedicated UTF-8 passphrase file
- `--book-passphrase-stdin` for one UTF-8 passphrase payload from standard input
- `--book-passphrase-prompt` for an interactive non-echo terminal prompt

`help` is returned when no command is supplied.
`help`, `version`, `capabilities`, and `environment` default to plain-language discovery output
unless you explicitly select `--output json`.
`help` and `capabilities` additionally accept `--detail minimal`, `--detail compact`, or
`--detail full` only when the resolved output mode is JSON. `capabilities` also accepts `--focus`
for one discovery concern, and both discovery commands accept `--category` for one command-family
slice in JSON mode. On the `capabilities` surface, `--category` alone selects command-focused
discovery automatically.
Bare `help` is intentionally one short front-door overview. `capabilities` is the deep machine
contract. `help <command>` and `<command> --help` both return command-scoped usage, options,
executable examples, operator notes, and exit-code guidance for one selected command.
JSON discovery output defaults to `minimal`; use `--detail compact` for stable usage, options,
and output-contract descriptors, `--detail full` when you need embedded templates, schemas, enum
vocabularies, or doctrine bodies, and `--focus` / `--category` when you want one machine-retrieval
slice instead of the full catalog family.
Request-file commands such as `declare-account`, `post-entry`, `preflight-entry`, and
`execute-plan` also inline the accepted request shape, one canonical template, and the relevant
enum vocabulary so an operator or agent can form a valid payload from the CLI alone. Text help
publishes the caller-submittable fields only; machine help keeps the full presence-marked request
shape, including forbidden fields that remain part of the truthful contract metadata. On
`execute-plan`, both help surfaces keep the posting contract nested under the ledger-plan request
shape at `steps[].posting`, instead of reappearing as a second top-level accepted posting
document.
`print-request-template` returns one raw JSON scaffold document so it can be redirected into a file
or piped into another process. With no topic it emits the canonical posting scaffold; with
`declare-account` it emits the canonical account-declaration scaffold; `post-entry` and
`preflight-entry` are accepted posting-scaffold topics.
`print-plan-template` returns one raw JSON ledger-plan scaffold that already includes
`ensure-book`, one posting step, and one balance assertion.
Both scaffold commands emit placeholder-first sample documents. Replace every scaffold evidence and
provenance token before real-world use. Idempotency keys are single-use per book once one posting
commits successfully.
`generate-book-key-file` creates one new owner-only key file that contains a generated passphrase.
The easiest path is one missing private parent directory that FinGrind can create securely, or one
existing parent directory that you have already tightened to owner-only permissions.
`open-book` explicitly initializes one new protected book.
`rekey-book` rotates the passphrase that protects one existing initialized book and restores the
pre-rekey file automatically if new-passphrase verification fails.
`backup-book` exports one verified encrypted backup pair for a closed book, `restore-book`
verifies that pair before replacing the live book path and leaves the restored live book protected
by that backup key file, `inspect-rekey-rollback` reports stale same-directory rollback artifacts,
`restore-rekey-rollback` rewinds one interrupted rekey from one selected rollback artifact, and
`delete-rekey-rollback` removes one stale rollback artifact without touching the live book path
after verifying one initialized live book through one explicit passphrase source.
`declare-account` inserts or reactivates one account in the selected book, with immutable
`accountType`, immutable `accountRole`, immutable declared taxonomy, and derived
`normalBalance`.
`transfer-period-result` transfers one contiguous reporting period into one policy-selected active declared
result-holding account, and successful results surface that selected result-holding account code
plus the per-currency transferred totals that were moved into equity. The first transfer may begin before
the earliest posting date; after one transfer is recorded, later transfers must start on the day after
the transferred-through horizon. Built-in period-result transfer uses one neutral accumulated-result target: declare
exactly one active and postable `EQUITY` account classified as `RESULT_HOLDING` before
transferring one period result.
`inspect-book` reports lifecycle state, format metadata, compatibility, and the active hard-break
migration policy for one selected book.
`list-accounts` returns one stable page of the current account registry.
`get-posting`, `list-postings`, and `account-balance` expose read/query access to committed history.
`trial-balance`, `account-ledger`, `period-summary`, `financial-position`, `income-statement`, and
`changes-in-equity` answer standard office-worker reporting questions in one command.
`execute-plan` runs one ordered ledger plan atomically and returns either a human-readable text
summary or one JSON execution-journal envelope on stdout. Built-in default output is text;
`--output json` keeps the machine envelope, and `--result-detail full` includes the per-step
journal in either surface.
`preflight-entry` and `post-entry` both require an already initialized book and declared active
accounts for every journal line they touch, and surface those failures as
`account-state-violations` with structured `details.violations`.
`preflight-entry` is advisory only: FinGrind re-checks commit-time durability rules inside
the write transaction before `post-entry` succeeds.
Every journal entry is single-currency; mixed-currency lines inside one entry are not supported.
Every journal-line amount must be greater than zero.
Protected books use SQLite3 Multiple Ciphers 2.3.5 with the upstream default `chacha20` cipher.
The operation catalog rendered in `help` and `capabilities` is contract-owned protocol metadata,
so CLI help, parser aliases, output modes, summaries, query limits, and the separation between
executable examples and operator notes share one source.
`capabilities --output json` defaults to the compact grouped command surface, and
`capabilities --output json --detail full` publishes the exhaustive grouped `CommandDescriptor`
contract, so automation can read the per-command
`executionMode`, `outputModes`, `artifactOutputs`, aliases, options, and summary directly
instead of inferring stdout behavior from one global mode list.
Commands that advertise `--output` default successful stdout to text unless you set
`FINGRIND_DEFAULT_OUTPUT=json` for the current session. `FINGRIND_DEFAULT_OUTPUT=text` restores the
text default explicitly, and per-command `--output ...` always wins over the session default.
Discovery, administration, write, and query/report commands can render operator-facing
`--output text`, and the tabular read/report commands also accept `--output csv`. Structured JSON
stdout uses one compact canonical layout across discovery, query, administration, and write
surfaces, including raw request or plan template emission where applicable. The report commands
`account-balance`, `trial-balance`, `account-ledger`, `period-summary`, `financial-position`,
`income-statement`, and `changes-in-equity` can additionally write one PDF artifact through
`--pdf-out <path>`. Successful exports keep the main stdout result unchanged and publish one PDF
artifact hint beside that primary result. When the primary stdout result is JSON, the success
envelope also carries one `artifacts[]` entry with `format: "pdf"` plus one redacted public-path
hint in `path`; text and CSV modes report the same hint on the diagnostics stream. If the PDF
artifact fails, FinGrind returns one deterministic `pdf-export-failure` instead of publishing a
successful report result. Commands that do not advertise
`--output` still publish one fixed stdout contract, either one raw JSON document or one fixed JSON
envelope.
Successful primary results always own stdout. Deterministic failures and rejections use the
diagnostics stream instead: every non-plan deterministic failure or single-command business
rejection is emitted as one canonical JSON diagnostics envelope on stderr, regardless of the
selected success output mode. `--output text` and `--output csv` affect successful stdout only.
When stdout and stderr are merged by the caller, the same JSON diagnostics document remains the
only failure payload. `execute-plan` remains a primary-result surface: rejected or
assertion-failed plan journals are returned on stdout inside the plan envelope.

## Commands

The command table below is generated from the canonical protocol catalog and contract-linted.

<!-- BEGIN GENERATED USER_CLI COMMAND TABLE -->
<table>
  <thead>
    <tr><th>Command</th><th>Aliases</th><th>Extra Arguments</th><th>Result</th></tr>
  </thead>
  <tbody>
    <tr><td><code>help</code></td><td><code>--help</code><br><code>-h</code></td><td><code>[&lt;command&gt;]</code><br><code>[--output &lt;json|text&gt;]</code><br><code>[--detail &lt;minimal|compact|full&gt; (json only)]</code><br><code>[--category &lt;discovery|administration|query|write&gt; (json only)]</code></td><td>Print command usage, examples, and workflow guidance.</td></tr>
    <tr><td><code>version</code></td><td><code>--version</code></td><td><code>[--output &lt;json|text&gt;]</code></td><td>Print application identity, version, and description.</td></tr>
    <tr><td><code>capabilities</code></td><td>none</td><td><code>[--output &lt;json|text&gt;]</code><br><code>[--detail &lt;minimal|compact|full&gt; (json only)]</code><br><code>[--focus &lt;overview|commands|storage|request-input|currency-model|bookkeeping-kernel|response-contract&gt; (json only)]</code><br><code>[--category &lt;discovery|administration|query|write&gt; (json only)]</code></td><td>Print the canonical machine-readable contract for commands, request shapes, and responses.</td></tr>
    <tr><td><code>environment</code></td><td>none</td><td><code>[--output &lt;json|text&gt;]</code></td><td>Print live runtime, distribution, and SQLite provenance facts for this launcher instance.</td></tr>
    <tr><td><code>print-request-template</code></td><td><code>--print-request-template</code></td><td><code>[post-entry|preflight-entry|declare-account]</code></td><td>Print the canonical minimal request scaffold JSON document for one request-file command.</td></tr>
    <tr><td><code>print-plan-template</code></td><td><code>--print-plan-template</code></td><td>none</td><td>Print the canonical minimal AI-agent ledger plan scaffold JSON document.</td></tr>
    <tr><td><code>generate-book-key-file</code></td><td>none</td><td><code>--book-key-file &lt;path&gt;</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Create one new owner-only UTF-8 book key file with a generated high-entropy passphrase.</td></tr>
    <tr><td><code>open-book</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--entity-name &lt;text&gt;</code><br><code>--functional-currency &lt;currency-code&gt;</code><br><code>--fiscal-year-start &lt;MM-DD&gt;</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Initialize a new book file with the canonical schema.</td></tr>
    <tr><td><code>rekey-book</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--new-book-key-file &lt;existing-path&gt; | --new-book-passphrase-stdin | --new-book-passphrase-prompt</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Rotate the passphrase that protects one existing book.</td></tr>
    <tr><td><code>backup-book</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--backup-book-file-out &lt;path&gt;</code><br><code>--backup-book-key-file-out &lt;path&gt;</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Export one closed encrypted-book backup pair without overwriting any existing destination.</td></tr>
    <tr><td><code>restore-book</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--backup-book-file &lt;path&gt;</code><br><code>--backup-book-key-file &lt;path&gt;</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Restore one verified encrypted-book backup pair onto the selected live book path.</td></tr>
    <tr><td><code>inspect-rekey-rollback</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Inspect stale sibling rekey rollback artifacts for the selected book path.</td></tr>
    <tr><td><code>delete-rekey-rollback</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>[--rollback-book-file &lt;path&gt;]</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Delete one selected stale sibling rekey rollback artifact.</td></tr>
    <tr><td><code>restore-rekey-rollback</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>[--rollback-book-file &lt;path&gt;]</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Restore one selected stale sibling rekey rollback artifact onto the live book path.</td></tr>
    <tr><td><code>declare-account</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Declare or reactivate one account in the selected book.</td></tr>
    <tr><td><code>transfer-period-result</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--period-start &lt;YYYY-MM-DD&gt;</code><br><code>--period-end &lt;YYYY-MM-DD&gt;</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Transfer one contiguous reporting period into one policy-selected result-holding account.</td></tr>
    <tr><td><code>inspect-book</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Inspect one selected book for lifecycle state, format version, and compatibility.</td></tr>
    <tr><td><code>list-accounts</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--limit &lt;1-200&gt;]</code><br><code>[--cursor &lt;cursor&gt;]</code><br><code>[--output &lt;json|text|csv&gt;]</code></td><td>List one stable page of declared accounts in the selected book using keyset pagination.</td></tr>
    <tr><td><code>get-posting</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--posting-id &lt;posting-id&gt;</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Return one committed posting by durable posting identifier.</td></tr>
    <tr><td><code>list-postings</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--account-code &lt;account-code&gt;]</code><br><code>[--effective-date-from &lt;YYYY-MM-DD&gt;]</code><br><code>[--effective-date-to &lt;YYYY-MM-DD&gt;]</code><br><code>[--limit &lt;1-200&gt;]</code><br><code>[--cursor &lt;cursor&gt;]</code><br><code>[--output &lt;json|text|csv&gt;]</code></td><td>List one filtered page of committed postings in stable reverse-chronological order using keyset pagination.</td></tr>
    <tr><td><code>account-balance</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--account-code &lt;account-code&gt;</code><br><code>[--effective-date-from &lt;YYYY-MM-DD&gt;]</code><br><code>[--effective-date-to &lt;YYYY-MM-DD&gt;]</code><br><code>[--posting-coverage &lt;all-posting-kinds|non-closing-postings&gt;]</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|text|csv&gt;]</code></td><td>Compute grouped per-currency balances for one declared account.</td></tr>
    <tr><td><code>trial-balance</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--effective-date-as-of &lt;YYYY-MM-DD&gt;]</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|text|csv&gt;]</code></td><td>Compute one book-wide trial balance as of the selected effective date or the latest effective date in the selected book when no date filter is supplied.</td></tr>
    <tr><td><code>account-ledger</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--account-code &lt;account-code&gt;</code><br><code>[--effective-date-from &lt;YYYY-MM-DD&gt;]</code><br><code>[--effective-date-to &lt;YYYY-MM-DD&gt;]</code><br><code>[--posting-coverage &lt;all-posting-kinds|non-closing-postings&gt;]</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|text|csv&gt;]</code></td><td>Compute the running ledger for one account, including opening balances, per-posting movement, and closing balances.</td></tr>
    <tr><td><code>period-summary</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--period-start &lt;YYYY-MM-DD&gt;</code><br><code>--period-end &lt;YYYY-MM-DD&gt;</code><br><code>[--posting-coverage &lt;all-posting-kinds|non-closing-postings&gt;]</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|text|csv&gt;]</code></td><td>Compute one bounded accounting-period summary with posting totals, currency totals, and per-account activity.</td></tr>
    <tr><td><code>financial-position</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--effective-date-as-of &lt;YYYY-MM-DD&gt;]</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|text|csv&gt;]</code></td><td>Compute one statement of financial position as of the selected effective date or the latest effective date in the selected book when no date filter is supplied.</td></tr>
    <tr><td><code>income-statement</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--period-start &lt;YYYY-MM-DD&gt;</code><br><code>--period-end &lt;YYYY-MM-DD&gt;</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|text|csv&gt;]</code></td><td>Compute one bounded income statement for the selected reporting period.</td></tr>
    <tr><td><code>changes-in-equity</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--period-start &lt;YYYY-MM-DD&gt;</code><br><code>--period-end &lt;YYYY-MM-DD&gt;</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|text|csv&gt;]</code></td><td>Compute one bounded statement of changes in equity for the selected reporting period.</td></tr>
    <tr><td><code>execute-plan</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>[--output &lt;json|text&gt;]</code><br><code>[--result-detail &lt;summary|full&gt;]</code></td><td>Execute one ordered AI-agent ledger plan inside a single atomic book transaction. Summary output is the default; request the full execution journal explicitly when needed.</td></tr>
    <tr><td><code>preflight-entry</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Validate one posting request without committing it.</td></tr>
    <tr><td><code>post-entry</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Commit one posting request into the selected SQLite book.</td></tr>
  </tbody>
</table>
<!-- END GENERATED USER_CLI COMMAND TABLE -->

## Packaged CLI

Public FinGrind CLI downloads are self-contained bundle archives, not a standalone JAR.
The current published public target set is:
- `macos-aarch64`
- `macos-x86_64`
- `linux-x86_64`
- `linux-aarch64`
- `windows-x86_64`

The protocol contract also declares these known-but-not-published public bundle targets so machine
clients can distinguish "not published" from "unknown target":
- `windows-aarch64`

Linux bundle compatibility is contract-owned by the compatibility labels in
[USER_INSTALL.md](./USER_INSTALL.md) and by each extracted `bundle-manifest.json`. The current
published Linux targets require glibc `2.34` or newer. They are not presented as universal Linux
binaries for every libc variant.
macOS and Windows bundles are published as unsigned release assets. Verify them through the
published checksum and GitHub attestation before first run.

Each extracted archive also contains:
- a top-level `README.md` with the local quick start
- a top-level `quick-start-request.json` with one concrete first-post request example
- a top-level generated `bundle-manifest.json` with machine-readable distribution metadata and
  canonical bootstrap commands that point back to `help`, `capabilities`, and the request/plan
  template operations

Those bundle metadata surfaces disclose the same canonical target matrix and managed-SQLite
version pins that the source checkout, release automation, and shell acceptance verifiers use.
Each published bundle archive also ships with one sibling `.sha256` digest file on the GitHub
Release and one GitHub artifact attestation. Use `gh attestation verify --repo resoltico/FinGrind
<downloaded-archive>` when you need bundle-sidecar-consistency provenance, and treat the `.sha256`
file as a convenience integrity digest rather than the only trust anchor.
Use [USER_INSTALL.md](./USER_INSTALL.md) for the exact archive-name matrix, checksum commands, and
the published container image surface.

One published Linux bundle flow:

```bash
bundle_archive="$(printf '%s\n' ./fingrind-*-linux-x86_64.tar.gz | head -n 1)"
bundle_root="${bundle_archive#./}"
bundle_root="${bundle_root%.tar.gz}"
tar -xzf "${bundle_archive}"
"./${bundle_root}/bin/fingrind" help
cp "./${bundle_root}/quick-start-request.json" ./request.json
```

Edit `./request.json` and replace the placeholder evidence and provenance values before using it with
`preflight-entry` or `post-entry`.

In the examples below, `fingrind` means the extracted Linux bundle launcher.
Command-scoped help and repair hints emitted from a self-contained bundle use that same launcher
path, such as `./bin/fingrind` on published Linux bundles.

For copy-paste use from one extracted bundle session, define `fingrind` once first.

```bash
fingrind() { "./<bundle-root>/bin/fingrind" "$@"; }
```

For source-driven local use, prefer:

```bash
./gradlew :cli:run --args="help"
```

For a source-checkout wrapper that behaves like a local checkout-managed executable:

```bash
./scripts/source-checkout-cli.sh help
```

That wrapper resolves the active CLI build directory, then launches the raw JAR through the
Gradle-owned Java 26 toolchain executable with the native-access flag, the source-checkout
runtime-distribution marker, and the managed-SQLite checkout lookup already baked in. When the
checkout source set or toolchain manifest has moved ahead of the cached build outputs, the wrapper
refreshes the raw JAR, runtime manifest, and managed SQLite runtime before it runs, so the
supported local wrapper cannot silently publish an older request or plan contract than the current
sources.

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
./scripts/direct-java-cli.sh help
```

That wrapper resolves the active CLI build directory and then runs the prepared application module
through the same Gradle-owned Java 26 toolchain executable that the source-checkout wrapper uses.
When the module stays under the prepared checkout layout, it auto-discovers the managed SQLite
library and grants native access only to the `fingrind` module. Like the source-checkout wrapper,
it refreshes the cached raw JAR, runtime manifest, and managed SQLite runtime before execution
when the current checkout has moved.

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
| `1` | invalid invocation or malformed request | `error` with code `unknown-command`, `invalid-request`, `invalid-page-cursor`, and similar |
| `2` | deterministic refusal after the command was understood | `rejected` for single-command business refusals, or `ok` with `payload.status: "rejected"` for `execute-plan` |
| `3` | valid `execute-plan` request whose assertion step failed | `ok` with `payload.status: "assertion-failed"` |
| `4` | classified runtime failure while executing an otherwise valid invocation | `error` with code `storage-runtime-failure` or `pdf-export-failure` |
| `5` | interactive prompt or managed runtime environment precondition failure | `error` with code `interactive-prompt-unavailable`, `interactive-prompt-failed`, or `managed-runtime-failure` |
| `6` | protected-book passphrase, key-file, or verification failure | `error` with code `protected-book-verification-failed`, `invalid-book-key-file`, or `invalid-book-passphrase-source` |
| `7` | protected-book maintenance precondition or destination-collision failure | `rejected` with code `backup-destination-already-exists`, `backup-key-file-already-exists`, `book-has-blocking-artifacts`, `backup-source-has-blocking-artifacts`, or `artifact-busy`; also `error` with code `book-key-file-already-exists`, `artifact-output-already-exists`, or `book-maintenance-in-progress` |
| `70` | internal software defect or leaked persistence invariant outside the published runtime families | `error` with code `internal-error` plus one opaque error id and one parseable diagnostics envelope |

## Common Failures

| Situation | Exit | Envelope Code | Typical Message |
|:----------|:-----|:--------------|:----------------|
| unsupported command | `1` | `unknown-command` | `Unsupported command: ...` |
| missing `--book-file` | `1` | `invalid-request` | `A --book-file argument is required.` |
| key-file generation target already exists | `7` | `book-key-file-already-exists` | `The FinGrind book key file already exists and will not be overwritten.` |
| missing book passphrase source | `1` | `invalid-request` | `Exactly one book passphrase source is required: ...` |
| missing new passphrase source on `rekey-book` | `1` | `invalid-request` | `Exactly one new book passphrase source is required: ...` |
| missing `--request-file` | `1` | `invalid-request` | `A --request-file argument is required.` |
| multiple passphrase sources | `1` | `invalid-request` | `Exactly one book passphrase source is permitted per command.` |
| multiple new passphrase sources on `rekey-book` | `1` | `invalid-request` | `Exactly one new book passphrase source is permitted per command.` |
| same path used for both files | `1` | `invalid-request` | `--book-file and --request-file must not point to the same path.` and similar |
| stdin requested for both passphrase and JSON | `1` | `invalid-request` | `Standard input cannot supply both the book passphrase and the request JSON.` |
| oversized request JSON payload from file or standard input | `1` | `invalid-request` | `Request file exceeded the supported 1048576-byte UTF-8 limit: ...` or `Request JSON from standard input exceeded the supported 1048576-byte UTF-8 limit.` |
| unreadable or missing `--request-file` payload | `1` | `invalid-request` | `Request file does not exist: ...`, `Request file is not readable: ...`, or `Failed to read request file: ...` |
| malformed JSON or invalid request shape | `1` | `invalid-request` | `Failed to read request JSON at line ..., column ....`, `Failed to read request JSON from standard input.`, or domain-validation text; malformed JSON also publishes `details.parseMessage`, `details.line`, and `details.column`, and journal grammar failures publish `details.violations` |
| malformed `list-accounts --cursor` or `list-postings --cursor` | `1` | `invalid-page-cursor` | `Unsupported account page cursor: ...` or `Unsupported posting page cursor: ...` |
| book is missing or never opened | `2` | `administration-book-not-initialized`, `query-book-not-initialized`, or `posting-book-not-initialized` | `The selected book does not exist or has not been initialized with open-book.` |
| query names an undeclared account | `2` | `unknown-account` | `Account '...' is not declared in this book.` |
| posting uses undeclared, inactive, or non-postable accounts | `2` | `account-state-violations` | stable top-level summary message plus ordered `details.violations[]` items carrying `code`, `field`, `message`, `category`, `repair`, `accountCode`, and optional `accountNodeKind` |
| posting violates the selected entry semantics | `2` | `entry-semantics-violations` | stable top-level summary message plus ordered `details.violations[]`, where each item publishes `code`, `field`, `message`, `category`, and `repair` |
| duplicate idempotency or reversal policy refusal | `2` | `duplicate-idempotency-key`, `reversal-target-not-found`, and similar | request was understood but refused by current book state |
| wrong book key, damaged/truncated protected book, or unsupported protected SQLite variant | `6` | `protected-book-verification-failed` | `FinGrind could not verify the selected protected book with the supplied passphrase source.` |
| invalid key-file contents, file permissions, parent-directory permissions, or unreadable key-file path | `6` | `invalid-book-key-file` | `Book access refused because the selected book key file path, permissions, parent directory, or contents do not satisfy the protected-book contract.` |
| unreadable, oversized, malformed, empty, or control-character passphrase payload on stdin or another selected passphrase route | `6` | `invalid-book-passphrase-source` | `Failed to read the FinGrind book passphrase from standard input.`, `The FinGrind book passphrase source exceeded the 4096-byte UTF-8 limit: ...`, or UTF-8/single-line passphrase validation text |
| unsupported prompt environment | `5` | `interactive-prompt-unavailable` | `FinGrind cannot prompt for a book passphrase because no interactive console is available.` |
| requested PDF artifact path already exists | `7` | `artifact-output-already-exists` | `Artifact publication refused because the selected output destination already exists and FinGrind will not overwrite it.` |
| requested PDF artifact written successfully after a successful report result | `0` | diagnostics info pdf-exported | primary report remains on stdout; JSON success envelopes also publish `artifacts[].format` plus one redacted public-path-hint `artifacts[].path`, and diagnostics report the same path hint for text/CSV flows |
| requested PDF artifact cannot be written for one report command that requested `--pdf-out` | `4` | `pdf-export-failure` | the command fails atomically because the requested PDF artifact was not produced |
| extracted bundle is incomplete, a prepared checkout is missing its managed SQLite build, or a custom direct-Java launch cannot resolve the managed library | `5` | `managed-runtime-failure` | SQLite runtime guidance describing the missing or incompatible managed library |
| runtime storage failure while opening, reading, or mutating a selected book | `4` | `storage-runtime-failure` | `Failed to open SQLite book connection.` and similar storage/runtime errors |
| SQLite persistence rejects one write through `CONSTRAINT_CHECK` after FinGrind accepted the request | `70` | `internal-error` | opaque public failure stating that one upstream invariant should have rejected the request before commit |
| other unexpected software defect outside the managed-runtime and storage families | `70` | `internal-error` | opaque public failure carrying one error id in one JSON diagnostics envelope without a raw stack trace |

## Notes

- Error envelopes may include `hint` and `argument` fields to help an agent or operator repair the
  call without consulting docs.
- Rejected and error responses for non-plan commands are written to stderr so stdout remains
  reserved for successful primary results, fixed-output scaffolds, and other success-only
  contracts.
- Malformed-input probing no longer needs `--output json`: invalid invocations use the same JSON
  diagnostics envelope even when the selected success mode is text.
- `help`, `version`, `capabilities`, `print-request-template`, and `print-plan-template` reject
  extra arguments.
- `open-book` creates missing parent directories for nested `--book-file` paths with owner-only
  protection. When the parent already exists, FinGrind requires it to remain owner-only.
- `generate-book-key-file` creates one new owner-only UTF-8 key file and refuses to overwrite an
  existing path. When the selected parent directory does not exist, FinGrind creates it with
  owner-only protection; when the parent already exists, FinGrind requires it to remain
  owner-only. Generated files report `0600` on POSIX filesystems and `owner-only-acl` on
  Windows.
- `backup-book` creates missing parent directories for nested `--backup-book-file-out` and
  `--backup-book-key-file-out` paths with owner-only protection. When either parent directory
  already exists, FinGrind requires it to remain owner-only before the backup pair is published.
- `restore-book` creates missing parent directories for nested `--book-file` targets with
  owner-only protection. When the selected parent directory already exists, FinGrind requires it
  to remain owner-only before the restored live book is published.
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
- `--book-passphrase-prompt` reads the passphrase from the controlling terminal without echo, the
  accepted prompt payload is capped at 4096 UTF-8 bytes after normalization, and this prompt route
  is accepted only with `--output text`.
- `--request-file <path>` reads one UTF-8 JSON object document capped at `1048576` bytes.
- `--request-file -` reads one UTF-8 JSON object document from standard input under that same
  `1048576`-byte limit.
- `rekey-book` requires one current passphrase source plus one new passphrase source.
  The new passphrase options are `--new-book-key-file`, `--new-book-passphrase-stdin`, and
  `--new-book-passphrase-prompt`.
- `--new-book-key-file` must point to an existing generated or operator-supplied secret
  file. Generate that file first with `generate-book-key-file` if you want FinGrind to create it
  for you.
- `--new-book-passphrase-prompt` asks for the new secret twice and rejects mismatched
  entries.
- `rekey-book` rejects using the same key-file path for both current and new secrets, and
  standard input cannot supply both current and new secrets in the same invocation.
- `rekey-book` uses one same-directory encrypted rollback copy while rotation is in progress. If a
  crash or forced stop interrupts cleanup, that stale `*.rekey-rollback-*.sqlite` file remains in
  the book directory until you inspect or delete it, and later opens warn when they detect it.
- The supported backup/restore workflow is one encrypted closed-book copy plus later file
  replacement. Do not copy a book while FinGrind is actively mutating it, and keep the copied
  `.sqlite` file under the same protected filesystem stance as the live book while storing key
  material separately from the copied book tree.
- `restore-book` reuses the supplied backup key file as the secret for the restored live book, so
  reopen the restored `--book-file` path with that same key file after the restore completes.
- The packaged CLI does not require an external `sqlite3` binary and does not shell out to
  `sqlite3`.
- The public packaged CLI bundles its own Java 26 runtime and managed SQLite 3.53.2 /
  SQLite3 Multiple Ciphers 2.3.5 native library.
- `environment.runtime.runtimeDistribution` tells you whether the current process is running from a
  self-contained bundle, container image, source-checkout Gradle launch, or direct Java wrapper
  invocation.
- `environment.publication.supportedPublicCliBundleTargets` and
  `environment.publication.unsupportedPublicCliBundleTargets` expose the public distribution matrix
  directly to automation.
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
  `changes-in-equity` accept `--output text`; all tabular read/report commands except
  `inspect-book` and `get-posting` also accept `--output csv`.
- `account-balance`, `trial-balance`, `account-ledger`, `period-summary`, `financial-position`,
  `income-statement`, and `changes-in-equity` can also write one PDF artifact through
  `--pdf-out <path>`. PDF export is explicit file output, not another stdout output mode. If the
  primary report succeeds, JSON success envelopes publish one redacted artifact-path hint under
  `artifacts[]`, while text/CSV flows emit a text info block with code `pdf-exported` and the
  same hint on diagnostics. If the PDF artifact fails, the command returns one deterministic
  `pdf-export-failure` error instead of publishing a successful report result.
- JSON money fields are typed exact-money objects with `currencyCode` and `minorUnits`,
  while `--output text` and `--output csv` render accounting-grade currency scale for operators
  and spreadsheet import.
- `print-plan-template` emits the accepted `execute-plan` request shape, including the generic
  nested `assertion` object for assertion steps.
- `execute-plan` reuses the same posting and query rules as the single-command surface, but runs
  the whole plan inside one atomic transaction and returns a bounded `payload.summary` by default.
  `--result-detail full` additionally includes `payload.journal` on success or
  `details.plan.journal` on deterministic plan failure. Journal steps now carry typed `data`
  records; successful `list-accounts` and `list-postings` steps keep both pagination fields and
  structured row arrays instead of collapsing to counts alone.
- `environment` reports runtime-contract details directly under:
  `payload.runtime.runtimeDistribution`,
  `payload.publication.publicCliDistribution`,
  `payload.publication.sourceCheckoutJava`,
  `payload.publication.supportedPublicCliBundleTargets`,
  `payload.publication.unsupportedPublicCliBundleTargets`,
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
- Gradle-driven local runs, the source-checkout wrapper, and the container image use a
  managed SQLite 3.53.2 / SQLite3 Multiple Ciphers 2.3.5 shared library.
- The developer direct-Java wrappers auto-discover that managed SQLite3MC library and scoped
  native access when they run from a prepared checkout. Direct-Java launches outside that checkout
  shape are unsupported.
- `capabilities` is the best machine-readable contract surface.
- `capabilities.requestInput.outputOption` publishes the canonical stdout-selection flag, while
  `capabilities --output json` and `capabilities --output json --detail full` publish the authoritative per-command
  stdout and artifact contract through grouped `CommandDescriptor` objects.
- `capabilities.commands`, command groups, usage lines, aliases, output modes, artifact outputs,
  and summaries are rendered from the contract protocol catalog rather than copied into the CLI
  renderer.
- `print-request-template` intentionally omits committed audit fields. Callers must not send
  `provenance.recordedAt` or `provenance.sourceChannel`.
- `print-request-template` and `print-plan-template` intentionally emit placeholder-first sample
  documents whose evidence and provenance values must be replaced before real-world use.
- `print-plan-template` is the fastest machine bootstrap for a new book because it already includes
  `ensure-book` and a matching assertion step.
- `--book-passphrase-prompt` is accepted only with `--output text`; selecting `json` or `csv`
  with that prompt route is rejected deterministically as `invalid-request` with a repair hint
  that points back to `--output text`, `--book-key-file`, or `--book-passphrase-stdin`.
- When `--output text` is selected, `--book-passphrase-prompt` either reads from a supported
  controlling terminal or fails deterministically with `interactive-prompt-unavailable` and a
  repair hint that points to `--book-key-file` or `--book-passphrase-stdin`.
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
  structured issue objects in `details.violations[]`; their machine envelope keeps a stable summary
  and omits a top-level repair `hint`
- posting-side entry-semantic failures are reported as `entry-semantics-violations` with one or
  more ordered issue objects in `details.violations[]`, and each issue carries stable `category`
  plus action-first `repair` guidance; their machine envelope likewise keeps a stable summary and
  omits a top-level repair `hint`
- the operator-facing `--output text` projection for those two nested repairable posting families
  renders one top-level `Summary` row plus one `Issue N | <code>` section per violation; checked-in
  examples live at [examples/account-state-violations-text.txt](./examples/account-state-violations-text.txt)
  and [examples/entry-semantics-violations-text.txt](./examples/entry-semantics-violations-text.txt)
- Wrong passphrases, damaged or truncated protected books, and unsupported protected SQLite files
  are reported as the deterministic `protected-book-verification-failed` error instead of leaking
  raw SQLite symptoms such as `SQLITE_NOTADB`.
- In a source checkout, example payloads live under [docs/examples/](./examples/).
  Public release bundles do not ship those repository fixture paths.
