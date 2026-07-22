---
afad: "5.0.1"
version: "0.61.0"
domain: USER_CLI
updated: "2026-07-21"
route:
  keywords: [fingrind, cli, commands, exit-codes, java26, sqlite, sqlite3mc, ffm, request-file, book-file, book-key-file, book-passphrase-stdin, book-passphrase-prompt, inspect-book, list-accounts, list-postings, account-balance, trial-balance, account-ledger, period-summary, output-mode, print-plan-template, execute-plan, declare-tax-registration, list-tax-registrations, tax-obligation, fixed-assets, financing, realized-foreign-exchange]
  questions: ["how do I run the fingrind cli", "what commands does fingrind expose", "how do I inspect a fingrind book before mutating it", "how do I page declared accounts in fingrind", "how do I run an AI-agent ledger plan in fingrind", "what exit codes does the fingrind cli use", "how do I declare tax registrations or compute tax obligations in fingrind", "how do I record fixed assets financing or realized foreign exchange"]
---

# CLI Guide

**Purpose**: Run the packaged FinGrind CLI and understand its command, file, and exit behavior.
**Prerequisites**: For public use, download one self-contained FinGrind release bundle that
matches your host and unpack it. The published Linux bundles require glibc `2.34` or newer; treat
[USER_INSTALL.md](./USER_INSTALL.md) and the extracted `bundle-manifest.json` as the authoritative
compatibility owners. No separate Java install is required for that path. For source-driven local runs,
`./gradlew :cli:run` manages SQLite 3.53.3 / SQLite3 Multiple Ciphers 2.3.6 automatically.
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
Request-file commands such as `declare-account`, `record-sale-settled`,
`record-purchase-settled`, `record-inventory-capitalization-settled`,
`record-inventory-write-down`, `record-inventory-shrinkage`, `record-inventory-count-increase`,
`record-expense-settled`, `record-owner-contribution`,
`record-owner-withdrawal`, `record-opening-position`, `record-reversal`, `post-entry`,
`preflight-entry`, `record-fixed-asset-capitalization`, `record-fixed-asset-depreciation`,
`record-fixed-asset-disposal`, `record-financing-borrowing`,
`record-financing-principal-repayment`, `record-financing-interest-accrual`,
`record-financing-interest-payment`, `record-foreign-currency-obligation`,
`record-realized-foreign-exchange-settlement`, `declare-tax-registration`, `amend-account`,
`retire-account`, and `execute-plan` also inline the accepted request shape, one canonical
template, and the relevant enum vocabulary so an operator or agent can form a valid payload from
the CLI alone. Text help publishes the caller-submittable fields only; machine help keeps the full
presence-marked request shape, including forbidden fields that remain part of the truthful
contract metadata. On `execute-plan`, both help surfaces keep the posting contract nested under the ledger-plan request
shape at `steps[].posting`, instead of reappearing as a second top-level accepted posting
document.
`print-request-template` returns one raw JSON scaffold document so it can be redirected into a file
or piped into another process. With no topic it emits the canonical minimal sale posting
scaffold; with `declare-account` it emits the canonical account-declaration scaffold; with
`declare-tax-registration` it emits the canonical tax-registration scaffold; and each posting
topic (`post-entry`, `preflight-entry`, or one of the `record-*` commands) emits the matching
caller-submittable posting shape.
`preflight-entry` keeps the full published posting-request family, while `post-entry` emits the raw
`DIRECT_JOURNAL` scaffold only, and that raw path still has to move at least one declared
cash-and-cash-equivalent asset account while rejecting any inventory account line.
`print-plan-template` returns one raw JSON ledger-plan scaffold. With no topic it emits the
general workflow scaffold; `tax-setup`, `fixed-asset-setup`, and `financing-setup` select the
corresponding atomic prerequisite-account setup. Adjust every placeholder and business fact before
execution, and run `open-book` first: plans only target an already initialized attested book. The tax setup declares its payable and recoverable accounts before it declares the tax
registration, while a direct `declare-tax-registration` command remains pure. Posting scaffolds
expose their optional tax block from the same field-contract metadata that validates a submitted
request. Idempotency keys are single-use per book once one posting commits successfully.
`generate-book-key-file` creates one new owner-only key file that contains a generated passphrase.
The easiest path is one missing private parent directory that FinGrind can create securely, or one
existing parent directory that you have already tightened to owner-only permissions.
`open-book` explicitly initializes a new protected book from the selected built-in seed template,
explicit accounting basis, and one through five aligned attestation-founder credential triples.
Its founder keys establish the genesis authorization registry; a ledger plan cannot initialize an
attested book. `OWNER_MANAGED_TRADING` additionally requires
`--inventory-costing WEIGHTED_AVERAGE`; service-template books reject that option because they do
not own inventory costing.
`rekey-book` attests its key-rotation operation with one through five aligned credential triples,
rotates the passphrase that protects one existing initialized book, and restores the pre-rekey file
automatically if new-passphrase verification fails. `backup-book` publishes one
manifest-attested encrypted backup pair for a closed book and requires an explicit `--backup-id`;
if external publication succeeds before acknowledgement, rerun the exact command to resume that
same acknowledgement. `restore-book` verifies the backup manifest and internal immutable chain,
restores only to an absent live-book destination, and re-encrypts the restored book under a new
`--new-book-key-file`. `verify-book`, `attestation-review`, `export-attestation-receipt`, and
`verify-receipt` inspect attestation truth without mutating ordinary book state.

If key generation or maintenance is forcibly stopped while staging a generated secret or
protected-book copy, the next relevant key-generation or maintenance attempt removes only staging
files backed by FinGrind's durable ownership records. If a generated key was already published, the
attempt first checks whether that key opens its paired book: a complete pair is preserved, while an
incomplete pair is rolled back only when the published key is still the exact owned staged file. A
similarly named file without that record is left untouched. No public rollback-artifact command
exists: recovery remains inside the verified atomic maintenance workflow.

If filesystem I/O prevents FinGrind from removing one of its owned stages after a pair reaches its
publication boundary, the command reports `storage-runtime-failure` rather than a normal collision
or rejection. Fix the filesystem problem before retrying; the durable owner record lets the next
relevant maintenance command recover only FinGrind's own stage.
`declare-account` inserts or reactivates one account in the selected book, with immutable
`accountType`, immutable declared taxonomy, and derived `normalBalance`. An optional
`contraOfAccountCode` names the postable account this account reduces; it must have the same type
and compatible statement classification, and its derived normal balance is the opposite of that
target. Asset accounts also
declare `cashFlowAssetClassification` so FinGrind can distinguish cash and cash-equivalent assets
from non-cash assets. Inventory accounts additionally require one nested
`unitOfMeasure { token, quantityScale }` object, while non-inventory accounts must not carry
that field. When present, the same inventory unit metadata is echoed back by `declare-account`,
`list-accounts`, and ledger-plan account-declaration results.
`declare-tax-registration` declares or updates one owned tax registration in the selected book,
including the already-declared payable and recoverable tax accounts, filing frequency, due offset,
and declared tax-code catalog. It never creates prerequisite accounts implicitly; use the atomic
tax-setup plan when a clean book needs the complete setup.
`amend-account` replaces one never-posted account definition only while no tax registration or
child account refers to it. `retire-account` leaves history intact and blocks new ordinary authored
postings only after the current balance is zero and live tax-registration and child-account bindings
are absent. Historical reversals remain admissible after retirement, so a later reversal can move
the retired account's historical balance. There is no delete-account command.
`interim-result-sweep` sweeps all unswept profit-and-loss movement through one inclusive effective
date selected with `--through` into one policy-selected active declared result-holding account,
and successful results surface the derived reporting period, the selected result-holding account
code, and the per-currency swept totals that moved into equity. The first sweep derives its start
from book start in the selected book; after one sweep is recorded, later sweeps derive their
start from the day after the transferred-through horizon and must remain inside one fiscal year.
Declare
exactly one active and postable `EQUITY` account classified as `RESULT_HOLDING` before sweeping.
`fiscal-year-close` closes the fiscal year selected with `--year` by deriving that year's
boundaries from the initialized book identity, sweeping any unswept remaining profit-and-loss
movement into `RESULT_HOLDING`, settling owner withdrawals into capital, and accumulating current
year result into one active `RETAINED_ACCUMULATED` equity account. Declare exactly one active
`EQUITY` account for each required close target: `EQUITY_CONTRIBUTION`, `RESULT_HOLDING`, and
`RETAINED_ACCUMULATED`.
`inspect-book` reports lifecycle state, format metadata, compatibility, and the active hard-break
migration policy for one selected book.
`list-accounts` returns one stable page of the current account registry, and
`list-tax-registrations` returns one stable page of the current tax-registration registry.
`get-posting` and `list-postings` expose read/query access to committed history.
`account-balance` and `tax-obligation` answer one focused account or filing-period report
question, while `trial-balance`, `account-ledger`, `period-summary`, `financial-position`,
`inventory-valuation`, `accrual-cutoff-schedule`, `fixed-asset-register`, `financing-register`,
`realized-foreign-exchange-register`, `income-statement`, `cash-flow-statement`, and
`changes-in-equity` answer broader office-worker reporting questions in one command.
`trial-balance`, `financial-position`, `income-statement`, `cash-flow-statement`, and
`changes-in-equity` accept one opt-in `--comparative` selector so operators can request
`none`, one fiscal-year-anchored prior period, or one explicit comparison window.

Fixed assets, financing, and realized foreign exchange are separately bounded public contexts.
Use `print-request-template <command>` before submitting one of their lifecycle events, and use
their dedicated registers to reconcile the retained lifecycle state rather than inferring it from
generic account balances. Their exact supported boundaries and exclusions are defined in the
[Fixed Assets ADR](./ADR_FIXED_ASSETS.md), [Financing ADR](./ADR_FINANCING.md), and
[Realized Foreign Exchange ADR](./ADR_REALIZED_FOREIGN_EXCHANGE.md). Read
[Primary Sources And Statutory Inputs](./DOC_00_PrimarySources.md) before selecting an accounting
policy, interpreting a financial instrument, or treating a foreign-exchange quote as suitable
evidence; FinGrind does not determine those matters.
`execute-plan` runs one ordered ledger plan atomically and returns either a human-readable text
summary or one JSON execution-journal envelope on stdout. Built-in default output is text;
`--output json` keeps the machine envelope, and `--result-detail full` includes the per-step
journal in either surface. A plan that contains any mutation requires one through five aligned
attestation credential triples; a query-only or assertion-only plan does not. A mutating plan
creates exactly one signed `execute-plan` chain operation for all of its successful child changes.
`preflight-entry`, the typed `record-*` commit commands, and raw `post-entry` all require an
already initialized book plus declared active accounts for every referenced account, and they
surface those failures as `account-state-violations` with structured `details.violations`. That
same rejection family also owns inventory quantity-floor protection: any quantity-aware inventory
decrease that would drive exact quantity on hand below zero is rejected before commit, including
trading-sale `inventoryRelief.quantity` and other inventory-command decreases. Raw direct journals
cannot touch inventory accounts at all. Inventory request facts that contradict the selected
inventory account's declared `unitOfMeasure` scale or exact acquisition-costing rules reject as
`entry-semantics-violations` before commit rather than leaking core exception text.
Settled and on-credit sales, purchases, inventory capitalizations, and expenses may carry one nested
`tax` selector naming one declared tax registration and one declared tax code. FinGrind does not
expose one neutral free-form tax payload on the other entry families.
`DIRECT_JOURNAL`, `SALE_SETTLED`, `EXPENSE_SETTLED`, `OWNER_CONTRIBUTION`,
`OWNER_WITHDRAWAL`, and `REVERSAL` request documents may also carry one nested
`foreignExchange` object when the business event happened in another currency. That object
retains the transaction amount, translated functional amount, quoted-rate evidence, and treatment
kind, while every journal line still stays in the selected book functional currency.
`preflight-entry` is advisory only: FinGrind re-checks commit-time durability rules inside
the write transaction before any committing write command succeeds.
Every journal line and every typed entry amount still use the selected book functional currency.
Foreign-currency business events are retained only through `foreignExchange`, and mixed-currency
journal lines inside one entry remain unsupported.
Every journal-line amount must be greater than zero.
Protected books use SQLite3 Multiple Ciphers 2.3.6 with the upstream default `chacha20` cipher.
The operation catalog rendered in `help` and `capabilities` is contract-owned protocol metadata,
so CLI help, parser aliases, output modes, summaries, query limits, and the separation between
executable examples and operator notes share one source.
`capabilities --output json` defaults to the compact grouped command surface, and
`capabilities --output json --detail full` publishes the exhaustive grouped `CommandDescriptor`
contract, so automation can read the per-command
`executionMode`, `outputModes`, `artifactOutputs`, aliases, options, and summary directly
instead of inferring stdout behavior from one global mode list.
The same full descriptor publishes the canonical ordered `capabilityCatalog`; use
`capabilities --output json --focus capability-catalog` when automation needs only the published
capability scope and operative boundaries for partial capabilities.
Discovery JSON payloads from `help`, `capabilities`, and `version` also publish one
`protocolVersion` field. The current hard-break line is `"32"`.
Commands that advertise `--output` default successful stdout to text. A per-command `--output ...`
flag selects a supported alternative.
Discovery, administration, write, and query/report commands can render operator-facing
`--output text`, and the tabular read/report commands also accept `--output csv`. Structured JSON
stdout uses one compact canonical layout across discovery, query, administration, and write
surfaces, including raw request or plan template emission where applicable. The report commands
`account-balance`, `trial-balance`, `account-ledger`, `period-summary`, `financial-position`,
`inventory-valuation`, `accrual-cutoff-schedule`, `fixed-asset-register`, `financing-register`,
`realized-foreign-exchange-register`, `income-statement`, `cash-flow-statement`,
`changes-in-equity`, and `tax-obligation` can additionally write one PDF artifact through
`--pdf-out <path>`. Successful exports publish one PDF artifact beside the primary result.
When the primary stdout result is JSON, the success envelope also carries one `artifacts[]` entry
with `format: "pdf"` plus its canonical absolute path in `path`; when `--output text` is
selected together with `--pdf-out`, stdout renders one
artifact confirmation block instead of the full report body. `--output csv` cannot be combined
with `--pdf-out`. If the PDF artifact fails, FinGrind returns one deterministic `pdf-export-failure` instead of publishing a
successful report result. Commands that do not advertise
`--output` still publish one fixed stdout contract, either one raw JSON document or one fixed JSON
envelope.
Successful primary results always own stdout. Deterministic failures and rejections use the
diagnostics stream: a valid explicit `--output json` produces the JSON envelope, including for an
unknown command, while absent, malformed, duplicate, or invalid output selection uses text
diagnostics. Explicit `--output text` remains text; CSV has no failure-row grammar and also uses
text diagnostics. `execute-plan` remains a primary-result surface: rejected or assertion-failed
plan journals are returned on stdout inside the plan envelope.

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
    <tr><td><code>capabilities</code></td><td>none</td><td><code>[--output &lt;json|text&gt;]</code><br><code>[--detail &lt;minimal|compact|full&gt; (json only)]</code><br><code>[--focus &lt;overview|commands|storage|request-input|currency-model|bookkeeping-kernel|capability-catalog|response-contract&gt; (json only)]</code><br><code>[--category &lt;discovery|administration|query|write&gt; (json only)]</code></td><td>Print the canonical machine-readable contract for commands, request shapes, and responses.</td></tr>
    <tr><td><code>environment</code></td><td>none</td><td><code>[--output &lt;json|text&gt;]</code></td><td>Print live runtime, distribution, and SQLite provenance facts for this launcher instance.</td></tr>
    <tr><td><code>print-request-template</code></td><td><code>--print-request-template</code></td><td><code>[post-entry|preflight-entry|record-sale-settled|record-sale-on-credit|record-purchase-settled|record-purchase-on-credit|record-inventory-capitalization-settled|record-inventory-capitalization-on-credit|record-inventory-write-down|record-inventory-shrinkage|record-inventory-count-increase|record-prepayment|record-deferred-revenue|record-accrued-expense|record-accrual-cutoff-recognition|record-accrued-expense-settlement|record-latvian-monthly-payroll|record-latvian-payroll-net-wage-settlement|record-latvian-payroll-state-remittance|record-fixed-asset-capitalization|record-fixed-asset-depreciation|record-fixed-asset-disposal|record-financing-borrowing|record-financing-principal-repayment|record-financing-interest-accrual|record-financing-interest-payment|record-foreign-currency-obligation|record-realized-foreign-exchange-settlement|record-expense-settled|record-expense-on-credit|record-receipt|record-payment|record-owner-contribution|record-owner-withdrawal|record-opening-position|record-reversal|declare-account|amend-account|retire-account|declare-tax-registration]</code><br><code>[--book-template-id &lt;OWNER_MANAGED_SERVICE|OWNER_MANAGED_TRADING&gt;]</code></td><td>Print the canonical minimal request scaffold JSON document for a request-file command.</td></tr>
    <tr><td><code>print-plan-template</code></td><td><code>--print-plan-template</code></td><td><code>[general|tax-setup|fixed-asset-setup|financing-setup]</code></td><td>Print a topic-specific executable ledger-plan scaffold JSON document.</td></tr>
    <tr><td><code>generate-book-key-file</code></td><td>none</td><td><code>--new-book-key-file &lt;path&gt;</code><br><code>[--tighten-parents]</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Create a new owner-only UTF-8 book key file with a generated high-entropy passphrase.</td></tr>
    <tr><td><code>open-book</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--entity-name &lt;text&gt;</code><br><code>--book-template-id &lt;OWNER_MANAGED_SERVICE|OWNER_MANAGED_TRADING&gt;</code><br><code>--accounting-basis &lt;CASH|ACCRUAL&gt;</code><br><code>[--inventory-costing &lt;WEIGHTED_AVERAGE&gt;] (required for OWNER_MANAGED_TRADING)</code><br><code>--functional-currency &lt;currency-code&gt;</code><br><code>--fiscal-year-start &lt;MM-DD&gt;</code><br><code>--attestation-founder-principal-id &lt;uuid&gt; (repeat one through five aligned founder triples)</code><br><code>--attestation-founder-key-file &lt;path&gt; (repeat one through five aligned founder triples)</code><br><code>--attestation-founder-passphrase-file &lt;path&gt; (repeat one through five aligned founder triples)</code><br><code>[--tighten-parents]</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Initialize a new book file with the canonical schema, selected seed template, explicit accounting basis, and the inventory costing doctrine required by trading templates.</td></tr>
    <tr><td><code>rekey-book</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--new-book-key-file &lt;path&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Re-encrypt an existing book under a newly generated, absent-target key file.</td></tr>
    <tr><td><code>backup-book</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--backup-file &lt;path&gt;</code><br><code>--new-backup-key-file &lt;path&gt;</code><br><code>--backup-id &lt;uuid&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Export a manifest-attested encrypted-book backup artifact without overwriting any existing destination.</td></tr>
    <tr><td><code>restore-book</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--new-book-key-file &lt;path&gt;</code><br><code>--backup-file &lt;path&gt;</code><br><code>--backup-key-file &lt;path&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Restore a manifest-attested backup artifact to a missing destination as a signed derived continuation.</td></tr>
    <tr><td><code>enroll-key</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Append a public Ed25519 credential binding for a principal.</td></tr>
    <tr><td><code>rollover-key</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Append a replacement public Ed25519 credential binding for an active principal credential.</td></tr>
    <tr><td><code>revoke-key</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Permanently revoke an enrolled public Ed25519 credential binding.</td></tr>
    <tr><td><code>alter-policy</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Append future quorum, principal-capability grant, and autonomous workflow policy facts.</td></tr>
    <tr><td><code>declare-account</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Declare or reactivate an account in the selected book.</td></tr>
    <tr><td><code>amend-account</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Replace the definition of a never-posted, unreferenced account without erasing its identity or lifecycle history.</td></tr>
    <tr><td><code>retire-account</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Retire a zero-balance account from new ordinary authored postings while preserving its ledger history and admitting historical reversals.</td></tr>
    <tr><td><code>declare-tax-registration</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Declare or update an owned tax registration using already-declared payable and recoverable accounts; this command never creates accounts implicitly.</td></tr>
    <tr><td><code>interim-result-sweep</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--through &lt;YYYY-MM-DD&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Sweep the derived contiguous reporting window into the policy-selected result-holding account.</td></tr>
    <tr><td><code>fiscal-year-close</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--year &lt;YYYY&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Close the fiscal year by settling owner withdrawals into capital and accumulating current-year result into retained accumulated equity.</td></tr>
    <tr><td><code>inspect-book</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Inspect the selected book for lifecycle state, format version, and compatibility.</td></tr>
    <tr><td><code>verify-book</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--require-clean-attestation]</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Verify every immutable attestation structure from genesis and report the first exact structural break, if any.</td></tr>
    <tr><td><code>attestation-review</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Report non-persisted compromise-review findings from a structurally valid attestation chain.</td></tr>
    <tr><td><code>export-attestation-receipt</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--receipt-file &lt;path&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt;</code><br><code>--attestation-key-file &lt;path&gt;</code><br><code>--attestation-passphrase-file &lt;path&gt;</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Publish an independently retained quorum-signed receipt without changing the selected book.</td></tr>
    <tr><td><code>verify-receipt</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--receipt-file &lt;path&gt;</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Verify an independently retained receipt against the selected book's complete immutable chain.</td></tr>
    <tr><td><code>list-accounts</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--limit &lt;1-200&gt;]</code><br><code>[--cursor &lt;cursor&gt;]</code><br><code>[--with-context]</code><br><code>[--output &lt;json|text|csv&gt;]</code></td><td>List a stable page of declared accounts in the selected book using keyset pagination.</td></tr>
    <tr><td><code>list-tax-registrations</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--limit &lt;1-200&gt;]</code><br><code>[--cursor &lt;cursor&gt;]</code><br><code>[--with-context]</code><br><code>[--output &lt;json|text|csv&gt;]</code></td><td>List a stable page of declared tax registrations in the selected book using keyset pagination.</td></tr>
    <tr><td><code>tax-obligation</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--tax-registration-id &lt;tax-registration-id&gt;</code><br><code>--period-start &lt;YYYY-MM-DD&gt;</code><br><code>--period-end &lt;YYYY-MM-DD&gt;</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|text|csv&gt;]</code></td><td>Compute a bounded tax-obligation report for the selected declared tax registration.</td></tr>
    <tr><td><code>get-posting</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--posting-id &lt;posting-id&gt;</code><br><code>[--with-context]</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Return a committed posting by durable posting identifier.</td></tr>
    <tr><td><code>list-postings</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--account-code &lt;account-code&gt;]</code><br><code>[--effective-date-from &lt;YYYY-MM-DD&gt;]</code><br><code>[--effective-date-to &lt;YYYY-MM-DD&gt;]</code><br><code>[--limit &lt;1-200&gt;]</code><br><code>[--cursor &lt;cursor&gt;]</code><br><code>[--with-context]</code><br><code>[--output &lt;json|text|csv&gt;]</code></td><td>List a filtered page of committed postings in stable reverse-chronological order using keyset pagination.</td></tr>
    <tr><td><code>account-balance</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--account-code &lt;account-code&gt;</code><br><code>[--effective-date-from &lt;YYYY-MM-DD&gt;]</code><br><code>[--effective-date-to &lt;YYYY-MM-DD&gt;]</code><br><code>[--posting-coverage &lt;all-posting-kinds|non-closing-postings&gt;]</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|text|csv&gt;]</code></td><td>Compute grouped per-currency balances for a declared account.</td></tr>
    <tr><td><code>trial-balance</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--effective-date-as-of &lt;YYYY-MM-DD&gt;]</code><br><code>[--comparative &lt;none|prior-period|..YYYY-MM-DD&gt;]</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|text|csv&gt;]</code></td><td>Compute a book-wide trial balance as of the selected effective date or the latest effective date in the selected book when no date filter is supplied.</td></tr>
    <tr><td><code>account-ledger</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--account-code &lt;account-code&gt;</code><br><code>[--effective-date-from &lt;YYYY-MM-DD&gt;]</code><br><code>[--effective-date-to &lt;YYYY-MM-DD&gt;]</code><br><code>[--posting-coverage &lt;all-posting-kinds|non-closing-postings&gt;]</code><br><code>[--limit &lt;1-200&gt;]</code><br><code>[--cursor &lt;cursor&gt;]</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|text|csv&gt;]</code></td><td>Compute a stable ascending keyset page of an account's running ledger, including opening balances, per-posting movement, and whole-range closing balances.</td></tr>
    <tr><td><code>period-summary</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--period-start &lt;YYYY-MM-DD&gt;</code><br><code>--period-end &lt;YYYY-MM-DD&gt;</code><br><code>[--posting-coverage &lt;all-posting-kinds|non-closing-postings&gt;]</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|text|csv&gt;]</code></td><td>Compute a bounded accounting-period summary with posting totals, currency totals, and per-account activity.</td></tr>
    <tr><td><code>financial-position</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--effective-date-as-of &lt;YYYY-MM-DD&gt;]</code><br><code>[--comparative &lt;none|prior-period|..YYYY-MM-DD&gt;]</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|text|csv&gt;]</code></td><td>Compute a statement of financial position as of the selected effective date or the latest effective date in the selected book when no date filter is supplied.</td></tr>
    <tr><td><code>inventory-valuation</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--as-of &lt;YYYY-MM-DD&gt;]</code><br><code>[--movements]</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|text|csv&gt;]</code></td><td>Compute exact per-account inventory quantity and carrying value from the canonical inventory movement replay order. The rounded moving-average unit-cost projection is informational only.</td></tr>
    <tr><td><code>accrual-cutoff-schedule</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--as-of &lt;YYYY-MM-DD&gt;]</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|text|csv&gt;]</code></td><td>Compute durable prepayment, deferred-revenue, and accrued-expense lifecycle balances from the append-only cut-off aggregate facts.</td></tr>
    <tr><td><code>fixed-asset-register</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--as-of &lt;YYYY-MM-DD&gt;]</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|text|csv&gt;]</code></td><td>Compute durable fixed-asset cost, depreciation, carrying value, and disposal state from immutable lifecycle facts.</td></tr>
    <tr><td><code>financing-register</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|text|csv&gt;]</code></td><td>Compute durable financing principal, accrued interest, paid interest, and outstanding balances from immutable lifecycle facts.</td></tr>
    <tr><td><code>realized-foreign-exchange-register</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|text|csv&gt;]</code></td><td>Compute durable foreign-currency receivable carrying amounts, settlements, and realized gain or loss from immutable lifecycle facts.</td></tr>
    <tr><td><code>latvian-payroll-register</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|text|csv&gt;]</code></td><td>Compute immutable Latvian payroll calculations and complete settlement posting lineage from the protected book's durable payroll facts.</td></tr>
    <tr><td><code>income-statement</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--period-start &lt;YYYY-MM-DD&gt;</code><br><code>--period-end &lt;YYYY-MM-DD&gt;</code><br><code>[--comparative &lt;none|prior-period|YYYY-MM-DD..YYYY-MM-DD&gt;]</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|text|csv&gt;]</code></td><td>Compute a bounded income statement for the selected reporting period.</td></tr>
    <tr><td><code>cash-flow-statement</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--period-start &lt;YYYY-MM-DD&gt;</code><br><code>--period-end &lt;YYYY-MM-DD&gt;</code><br><code>[--comparative &lt;none|prior-period|YYYY-MM-DD..YYYY-MM-DD&gt;]</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|text|csv&gt;]</code></td><td>Compute a bounded statement of cash receipts and payments for the selected reporting period.</td></tr>
    <tr><td><code>changes-in-equity</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--period-start &lt;YYYY-MM-DD&gt;</code><br><code>--period-end &lt;YYYY-MM-DD&gt;</code><br><code>[--comparative &lt;none|prior-period|YYYY-MM-DD..YYYY-MM-DD&gt;]</code><br><code>[--pdf-out &lt;path&gt;]</code><br><code>[--output &lt;json|text|csv&gt;]</code></td><td>Compute a bounded statement of changes in equity for the selected reporting period.</td></tr>
    <tr><td><code>execute-plan</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code><br><code>[--result-detail &lt;summary|full&gt;]</code></td><td>Execute an ordered AI-agent ledger plan inside a single atomic book transaction. Summary output is the default; request the full execution journal explicitly when needed.</td></tr>
    <tr><td><code>preflight-entry</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Validate a posting request from the typed business-entry family or the raw direct-journal path without committing it.</td></tr>
    <tr><td><code>record-sale-settled</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Commit a settled sale entry into the selected SQLite book.</td></tr>
    <tr><td><code>record-sale-on-credit</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Commit a sale-on-credit entry into the selected SQLite book.</td></tr>
    <tr><td><code>record-purchase-settled</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Commit a settled inventory purchase entry into the selected trading-template SQLite book.</td></tr>
    <tr><td><code>record-purchase-on-credit</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Commit a purchase-on-credit inventory entry into the selected trading-template SQLite book.</td></tr>
    <tr><td><code>record-inventory-capitalization-settled</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Commit a settled landed-cost capitalization into an existing inventory pool.</td></tr>
    <tr><td><code>record-inventory-capitalization-on-credit</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Commit a payable landed-cost capitalization into an existing inventory pool.</td></tr>
    <tr><td><code>record-inventory-write-down</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Commit a carrying-cost write-down against an existing inventory pool.</td></tr>
    <tr><td><code>record-inventory-shrinkage</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Commit a quantity shrinkage adjustment with executor-derived carrying cost.</td></tr>
    <tr><td><code>record-inventory-count-increase</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Commit a count-discovered inventory increase at an exact per-unit carrying cost.</td></tr>
    <tr><td><code>record-prepayment</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Commit a cash-funded prepayment with an inclusive recognition interval.</td></tr>
    <tr><td><code>record-deferred-revenue</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Commit a cash-funded deferred-revenue liability with an inclusive recognition interval.</td></tr>
    <tr><td><code>record-accrued-expense</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Commit an unpaid accrued expense.</td></tr>
    <tr><td><code>record-accrual-cutoff-recognition</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Recognize a permitted amount from an existing prepayment or deferred-revenue balance.</td></tr>
    <tr><td><code>record-accrued-expense-settlement</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Settle a permitted amount of an existing accrued-expense liability.</td></tr>
    <tr><td><code>record-latvian-monthly-payroll</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Commit an executor-resolved Latvian 2026 ordinary monthly-payroll accrual.</td></tr>
    <tr><td><code>record-latvian-payroll-net-wage-settlement</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Settle the exact net-wage obligation of the active retained Latvian payroll run.</td></tr>
    <tr><td><code>record-latvian-payroll-state-remittance</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Remit the exact state obligation of the active retained Latvian payroll run.</td></tr>
    <tr><td><code>record-fixed-asset-capitalization</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Capitalizes a fixed asset with its owned useful-life and depreciation facts.</td></tr>
    <tr><td><code>record-fixed-asset-depreciation</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Records the admissible periodic depreciation amount for a retained fixed asset.</td></tr>
    <tr><td><code>record-fixed-asset-disposal</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Disposes a retained fixed asset and preserves its lifecycle lineage.</td></tr>
    <tr><td><code>record-financing-borrowing</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Records a borrowing and opens its retained financing arrangement.</td></tr>
    <tr><td><code>record-financing-principal-repayment</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Repays principal against a retained financing arrangement.</td></tr>
    <tr><td><code>record-financing-interest-accrual</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Accrues interest against a retained financing arrangement.</td></tr>
    <tr><td><code>record-financing-interest-payment</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Pays accrued interest against a retained financing arrangement.</td></tr>
    <tr><td><code>record-foreign-currency-obligation</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Records a foreign-currency receivable with its functional-currency carrying amount.</td></tr>
    <tr><td><code>record-realized-foreign-exchange-settlement</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Settles a retained foreign-currency obligation and derives the realized gain or loss.</td></tr>
    <tr><td><code>record-expense-settled</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Commit a settled expense entry into the selected SQLite book.</td></tr>
    <tr><td><code>record-expense-on-credit</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Commit an expense-on-credit entry into the selected SQLite book.</td></tr>
    <tr><td><code>record-receipt</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Commit a trade-receivable settlement entry into the selected SQLite book.</td></tr>
    <tr><td><code>record-payment</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Commit a trade-payable settlement entry into the selected SQLite book.</td></tr>
    <tr><td><code>record-owner-contribution</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Commit an owner-contribution entry into the selected SQLite book.</td></tr>
    <tr><td><code>record-owner-withdrawal</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Commit an owner-withdrawal entry into the selected SQLite book.</td></tr>
    <tr><td><code>record-opening-position</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Commit an opening-position entry into the selected SQLite book.</td></tr>
    <tr><td><code>record-reversal</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Commit a reversal entry into the selected SQLite book.</td></tr>
    <tr><td><code>post-entry</code></td><td>none</td><td><code>--book-file &lt;path&gt;</code><br><code>--book-key-file &lt;path&gt; | --book-passphrase-stdin | --book-passphrase-prompt</code><br><code>--request-file &lt;path|-&gt;</code><br><code>--attestation-principal-id &lt;uuid&gt; --attestation-key-file &lt;path&gt; --attestation-passphrase-file &lt;path&gt; (repeat one through five aligned triples)</code><br><code>[--output &lt;json|text&gt;]</code></td><td>Commit a raw direct-journal posting request into the selected SQLite book. Prefer the record-* commands when a typed business-entry command matches the operator's intent; raw direct-journal requests do not admit inventory accounts.</td></tr>
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
`preflight-entry` or `record-sale-settled`. Run `print-request-template post-entry` when you explicitly need
the raw direct-journal scaffold. When the selected book doctrine is `OWNER_MANAGED_TRADING`, sale
help and request templates surface the owned `inventoryRelief` block before first rejection, the
typed sale requires it so one committed event carries both revenue and cost-of-sales relief, and
trading-template books keep purchase, capitalization, write-down, shrinkage, and count-increase
workflows on their corresponding `record-*` commands. Purchase and count-increase requests carry
exact quantity plus unit cost; sale and shrinkage requests carry quantity while FinGrind derives
the authoritative carrying cost from the inventory pool. `inventory-valuation` makes that exact
pool visible by inventory account: `--as-of` replays through the selected date and `--movements`
adds ordered durable movement evidence. Its rounded moving-average unit-cost projection is
informational only; carrying value never equals quantity times that rounded display value.

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
| `2` | deterministic refusal after the command was understood | `rejected` for single-command business refusals, or `rejected` with `payload.status: "rejected"` for `execute-plan` |
| `3` | valid `execute-plan` request whose assertion step failed | `error` with `payload.status: "assertion-failed"` |
| `4` | classified runtime failure while executing an otherwise valid invocation | `error` with code `storage-runtime-failure` or `pdf-export-failure` |
| `5` | interactive prompt or managed runtime environment precondition failure | `error` with code `interactive-prompt-unavailable`, `interactive-prompt-failed`, or `managed-runtime-failure` |
| `6` | protected-book passphrase, key-file, or verification failure | `error` with code `protected-book-verification-failed`, `invalid-book-key-file`, or `invalid-book-passphrase-source` |
| `7` | protected-book maintenance precondition or destination-collision failure | `rejected` with code `secret-target-occupied`, `book-destination-occupied`, `backup-destination-already-exists`, `book-has-blocking-artifacts`, `backup-source-has-blocking-artifacts`, or `artifact-busy`; also `error` with code `artifact-output-already-exists` or `book-maintenance-in-progress` |
| `70` | internal software defect, deterministic internal contract defect, or leaked persistence invariant outside the published runtime families | `error` with code `internal-defect` or `internal-error`, depending on which internal failure family fired |

## Common Failures

| Situation | Exit | Envelope Code | Typical Message |
|:----------|:-----|:--------------|:----------------|
| unsupported command | `1` | `unknown-command` | `Unsupported command: ...` |
| missing `--book-file` | `1` | `invalid-request` | `A --book-file argument is required.` |
| generated key-file target already exists | `7` | `secret-target-occupied` | `Generated secret target already exists and will not be overwritten.` |
| missing book passphrase source | `1` | `invalid-request` | `Exactly one book passphrase source is required: ...` |
| missing `--new-book-key-file` on `rekey-book` | `1` | `invalid-request` | `A --new-book-key-file argument is required.` |
| missing attestation credentials for `rekey-book` | `1` | `invalid-request` | `Provide one through five aligned attestation credential triples: ...` |
| missing `--request-file` | `1` | `invalid-request` | `A --request-file argument is required.` |
| multiple passphrase sources | `1` | `invalid-request` | `Exactly one book passphrase source is permitted per command.` |
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
| duplicate idempotency or reversal policy refusal | `2` | `idempotency-key-conflict`, `reversal-target-not-found`, and similar | request was understood but refused by current book state |
| wrong book key, damaged/truncated protected book, or unsupported protected SQLite variant | `6` | `protected-book-verification-failed` | `FinGrind could not verify the selected protected book with the supplied passphrase source.` |
| invalid key-file contents, file permissions, parent-directory permissions, or unreadable key-file path | `6` | `invalid-book-key-file` | `Book access refused because the selected book key file path, permissions, parent directory, or contents do not satisfy the protected-book contract.` |
| unreadable, oversized, malformed, empty, or control-character passphrase payload on stdin or another selected passphrase route | `6` | `invalid-book-passphrase-source` | `Failed to read the FinGrind book passphrase from standard input.`, `The FinGrind book passphrase source exceeded the 4096-byte UTF-8 limit: ...`, or UTF-8/single-line passphrase validation text |
| unsupported prompt environment | `5` | `interactive-prompt-unavailable` | `FinGrind cannot prompt for a book passphrase because no interactive console is available.` |
| requested PDF artifact path already exists | `7` | `artifact-output-already-exists` | `Artifact publication refused because the selected output destination already exists and FinGrind will not overwrite it.` |
| requested PDF artifact written successfully after a successful report result | `0` | no separate diagnostics code | JSON success envelopes publish `artifacts[].format` plus the canonical absolute artifact `artifacts[].path`; `--output text --pdf-out <path>` writes one artifact confirmation block to stdout instead of the full report body |
| requested PDF artifact cannot be written for one report command that requested `--pdf-out` | `4` | `pdf-export-failure` | the command fails atomically because the requested PDF artifact was not produced |
| extracted bundle is incomplete, a prepared checkout is missing its managed SQLite build, or a custom direct-Java launch cannot resolve the managed library | `5` | `managed-runtime-failure` | SQLite runtime guidance describing the missing or incompatible managed library |
| runtime storage failure while opening, reading, or mutating a selected book | `4` | `storage-runtime-failure` | `Failed to open SQLite book connection.` and similar storage/runtime errors |
| one typed bookkeeping command builds a journal that resolves to a different published event class than the command contract promised | `70` | `internal-defect` | the failure message names the mismatched typed command and resolved event class so the defect is truthful without pretending the caller can repair it |
| SQLite persistence rejects one write through `CONSTRAINT_CHECK` after FinGrind accepted the request | `70` | `internal-error` | opaque public failure stating that one upstream invariant should have rejected the request before commit |
| other unexpected software defect outside the managed-runtime and storage families | `70` | `internal-error` | opaque public failure carrying one error id in one JSON diagnostics envelope without a raw stack trace |

## Operational Notes

See [USER_CLI_OPERATIONAL_NOTES.md](./USER_CLI_OPERATIONAL_NOTES.md) for cross-command diagnostics, protected-book handling, query and report output, runtime and discovery facts, and failure boundaries.
