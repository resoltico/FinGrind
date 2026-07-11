# FinGrind — protected-book CLI bookkeeping for one accounting entity per SQLite file

FinGrind is a command-line bookkeeping tool for one accounting entity in one protected SQLite book. Choose a built-in service or trading doctrine, post typed business events with retained evidence, and query statements from the same protected book. FinGrind rejects inadmissible writes and maintenance mutations before they change the book.

- One protected SQLite book and generated key file per accounting entity
- Typed sales, purchases, inventory maintenance, expenses, settlements, owner transactions, opening positions, and reversals with provenance and idempotency
- Per-book tax registrations, optional foreign-exchange facts, tax-obligation reporting, and reporting-period close commands
- Trial balance, account balance and ledger, period summary, financial position, income statement, cash-flow statement, changes in equity, inventory valuation, and tax-obligation outputs in text, JSON, CSV, or PDF
- Explicit backup, restore, rekey, and interrupted-rekey recovery commands

**Status:** Alpha. FinGrind is under active development and is not yet production-ready.

Public self-contained bundles support macOS on Apple Silicon and Intel, Linux on `x86_64` and `aarch64` with glibc `2.34+`, and Windows `x86_64`. A public container image supports `linux/amd64` and `linux/arm64`. See [docs/USER_INSTALL.md](docs/USER_INSTALL.md) for the live package matrix, checksum verification, and attestation flow.

## Quick Start

The following launcher-neutral example works wherever `fingrind` resolves to the CLI entrypoint.

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
# Replace every replace-before-commit value in request.json.

fingrind preflight-entry --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --request-file ./request.json
fingrind record-sale-settled --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --request-file ./request.json
fingrind trial-balance --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --output text
```

For a goods-trading book, choose `OWNER_MANAGED_TRADING` and add `--inventory-costing WEIGHTED_AVERAGE` to `open-book`. Purchases and count increases carry exact `quantity` plus `unitCost`; sales and shrinkage carry quantity while FinGrind derives authoritative cost of sales from the exact moving-average pool. Use `inventory-valuation` to inspect exact quantity and carrying value. Raw journals cannot touch inventory accounts.

Humans should begin with `fingrind help`. Automation should begin with `fingrind capabilities --output json`.

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

## Documentation

- [docs/USER_INSTALL.md](docs/USER_INSTALL.md) for package selection, checksums, and attestation
- [docs/USER_QUICK_START.md](docs/USER_QUICK_START.md) for the complete first-run path
- [docs/USER_CONTAINER.md](docs/USER_CONTAINER.md) for the mounted-container workflow
- [docs/USER_CLI.md](docs/USER_CLI.md) for commands and exit behavior
- [docs/USER_REQUESTS.md](docs/USER_REQUESTS.md) for request shapes and inventory rules
- [docs/USER_RESPONSES.md](docs/USER_RESPONSES.md) for response envelopes and deterministic failures
- [docs/USER_EXAMPLES.md](docs/USER_EXAMPLES.md) for longer workflows
- [docs/README.md](docs/README.md) for the full documentation index

## Legal

FinGrind is MIT-licensed. Its self-contained bundles vendor Jackson and Apache PDFBox (Apache 2.0), Noto Sans (SIL OFL 1.1), and SQLite3 Multiple Ciphers with SQLite (MIT / public domain). See [NOTICE](NOTICE) for complete attribution and [PATENTS.md](PATENTS.md) for patent considerations.

[LICENSE](LICENSE) | [NOTICE](NOTICE) | [PATENTS.md](PATENTS.md) | [LICENSE-APACHE-2.0](LICENSE-APACHE-2.0) | [LICENSE-SIL-OFL-1.1](LICENSE-SIL-OFL-1.1) | [LICENSE-SQLITE3MULTIPLECIPHERS](LICENSE-SQLITE3MULTIPLECIPHERS)
