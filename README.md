# FinGrind — protected-book CLI bookkeeping for one accounting entity per SQLite file

FinGrind is a command-line bookkeeping tool that keeps one accounting entity in one protected SQLite book. You open a book explicitly, choose either `OWNER_MANAGED_SERVICE` or `OWNER_MANAGED_TRADING` on `CASH` or `ACCRUAL`, post typed business events first, drop to the direct balanced journal only when you intentionally need the lower-level path, and read back balances, tax obligations, and built-in statements from the same protected file. Invalid writes and invalid maintenance mutations are rejected before they change the selected book.

- One protected SQLite book plus one generated key file per accounting entity
- Typed sales, purchases, expenses, receipts, payments, owner contributions, owner withdrawals, opening positions, and reversals with retained evidence, provenance, and idempotency
- Per-book tax registrations, bounded tax-obligation reporting, optional foreign-exchange facts, and reporting-period close commands
- Trial balance, account balance, account ledger, period summary, financial position, income statement, cash-flow statement, changes in equity, and tax-obligation outputs in text, JSON, CSV, or PDF
- Explicit maintenance commands for backup, restore, rekey, and interrupted rekey rollback inspection

**Status:** Alpha. FinGrind is under active development and is not yet production-ready.

Published runtime surfaces currently include public self-contained bundles for macOS (Apple Silicon and Intel), Linux (`x86_64` and `aarch64`, glibc `2.34+`), and Windows `x86_64`, plus one published container image. `windows-aarch64` remains on the container or source-checkout path. Use [docs/USER_INSTALL.md](docs/USER_INSTALL.md) for the live package matrix, checksum verification, and attestation flow.

## Quick Start

The example below is launcher-neutral: it works with the public bundle launcher, the container-mounted launcher, or the source-checkout launcher as long as `fingrind` resolves to the CLI entrypoint in your shell.

```bash
fingrind generate-book-key-file --book-key-file ./secrets/acme.book-key
fingrind open-book --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --entity-name "Acme Studio" \
  --book-template-id OWNER_MANAGED_SERVICE \
  --accounting-basis CASH \
  --functional-currency EUR \
  --fiscal-year-start 01-01

fingrind list-accounts --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --limit 10

fingrind print-request-template > ./request.json

fingrind preflight-entry --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --request-file ./request.json

fingrind record-sale-settled --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --request-file ./request.json

fingrind trial-balance --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --output text
```

`print-request-template` emits a placeholder-first request document. Replace every `replace-before-commit` value before committing a real entry. If you want the host-specific bundle walkthrough, the shipped quick-start sample, or the container-mounted flow, start with [docs/USER_QUICK_START.md](docs/USER_QUICK_START.md) and [docs/USER_CONTAINER.md](docs/USER_CONTAINER.md).

Humans should start with `fingrind help`. Automation and agent callers should start with `fingrind capabilities --output json`.

```
Trial Balance
=============

As of         : 2026-04-07
Balance state : Balanced

Accounts
--------
cash | Cash
-----------
Type         : Asset
Normal       : Debit
Active       : Yes
Currency     : EUR
Debit total  : EUR 10.00
Credit total : EUR 0.00
Net amount   : EUR 10.00
Balance side : Debit

service-revenue | Service Revenue
---------------------------------
Type         : Revenue
Normal       : Credit
Active       : Yes
Currency     : EUR
Debit total  : EUR 0.00
Credit total : EUR 10.00
Net amount   : EUR 10.00
Balance side : Credit

Current totals
--------------
Currency | Debit total | Credit total | Net amount | Balance side
---------+-------------+--------------+------------+-------------
EUR      |   EUR 10.00 |    EUR 10.00 |   EUR 0.00 | Zero

Context
-------
Entity              : Acme Studio
Seed template       : Owner-managed service seed template
Accounting basis    : Cash basis
Functional currency : EUR
Fiscal year start   : 01-01
Posting coverage    : All posting kinds
As of               : 2026-04-07
```

## Current Public Scope

The current public bookkeeping kernel is owner-managed internal-management bookkeeping with two built-in starting charts:

- `OWNER_MANAGED_SERVICE` for service books
- `OWNER_MANAGED_TRADING` for goods-trading books

Trading books keep sales and purchases on typed business-event commands. Trading sales carry `inventoryRelief` so one committed event records both revenue and cost-of-sales relief without falling back to the raw journal path.

## Documentation

- [docs/USER_INSTALL.md](docs/USER_INSTALL.md) for package selection, checksums, and attestation
- [docs/USER_QUICK_START.md](docs/USER_QUICK_START.md) for the fastest first-run path
- [docs/USER_CONTAINER.md](docs/USER_CONTAINER.md) for the published container workflow
- [docs/USER_CLI.md](docs/USER_CLI.md) for the command surface and exit behavior
- [docs/USER_REQUESTS.md](docs/USER_REQUESTS.md) for request shapes
- [docs/USER_RESPONSES.md](docs/USER_RESPONSES.md) for response envelopes and deterministic failures
- [docs/USER_EXAMPLES.md](docs/USER_EXAMPLES.md) for longer workflows
- [docs/README.md](docs/README.md) for the full docs index

## Legal

FinGrind is MIT-licensed. Its self-contained bundles vendor Jackson and Apache PDFBox (Apache 2.0), Noto Sans (SIL OFL 1.1), and SQLite3 Multiple Ciphers with SQLite (MIT / public domain). See [NOTICE](NOTICE) for the complete attribution list and [PATENTS.md](PATENTS.md) for patent considerations.

[LICENSE](LICENSE) | [NOTICE](NOTICE) | [PATENTS.md](PATENTS.md) | [LICENSE-APACHE-2.0](LICENSE-APACHE-2.0) | [LICENSE-SIL-OFL-1.1](LICENSE-SIL-OFL-1.1) | [LICENSE-SQLITE3MULTIPLECIPHERS](LICENSE-SQLITE3MULTIPLECIPHERS)
