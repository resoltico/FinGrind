# FinGrind — protected-book CLI bookkeeping for one accounting entity per SQLite file

FinGrind is a command-line bookkeeping tool for one accounting entity in one protected SQLite book. Choose a built-in service or trading doctrine, post typed business events with retained evidence, and query statements from the same protected book. FinGrind rejects inadmissible writes and maintenance mutations before they change the book.

- One protected SQLite book and generated key file per accounting entity
- Typed sales, purchases, inventory maintenance, expenses, fixed assets, financing, realized foreign exchange, Latvian monthly payroll, accrual cut-offs, settlements, owner transactions, opening positions, and reversals with provenance and idempotency
- Atomic tax, fixed-asset, and financing setup plans; explicit account amendment, retirement, and contra-account rules; per-book tax registrations; tax-obligation reporting; and reporting-period close commands
- Executable ledger plans that combine attested bookkeeping and administration steps with credential-free read and assertion steps, while preserving one explicit result for the whole plan
- Trial balance, account balance and ledger, period summary, financial position, income statement, cash-flow statement, changes in equity, inventory valuation, accrual-cutoff schedule, fixed-asset, financing, and realized-foreign-exchange register outputs in text, JSON, CSV, or PDF, with keyset pagination for account-ledger and collection queries
- A retained Latvian payroll register in text, JSON, CSV, and PDF, including payroll runs, settlements, and compensating-reversal lineage
- Immutable Ed25519-attested book mutations, verification and compromise review, no-clobber backups, signed restores, and independently retained receipts

**Status:** Alpha. FinGrind is under active development and is not yet production-ready.

Public self-contained bundles support macOS on Apple Silicon and Intel, Linux on `x86_64` and `aarch64` with glibc `2.34+`, and Windows `x86_64`. A public container image supports `linux/amd64` and `linux/arm64`. See [docs/USER_INSTALL.md](docs/USER_INSTALL.md) for the live package matrix, checksum verification, and attestation flow.

## Quick Start

The following launcher-neutral example works wherever `fingrind` resolves to the CLI entrypoint.

```bash
mkdir -p -m 700 ./secrets ./books
fingrind generate-book-key-file --new-book-key-file ./secrets/acme.book-key
# Before opening, prepare a separate nonempty owner-only UTF-8 passphrase file at
# ./secrets/acme-founder.passphrase. FinGrind creates the absent founder key at
# ./secrets/acme-founder.fgatk exactly once; do not reuse the book key or its passphrase.
fingrind open-book --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --entity-name "Acme Studio" \
  --book-template-id OWNER_MANAGED_SERVICE \
  --accounting-basis CASH \
  --functional-currency EUR \
  --fiscal-year-start 01-01 \
  --book-start-effective-date 2026-01-01 \
  --attestation-custodian file-pkcs8 --attestation-founder-principal-id 123e4567-e89b-12d3-a456-426614174000 \
  --attestation-founder-key-file ./secrets/acme-founder.fgatk \
  --attestation-founder-passphrase-file ./secrets/acme-founder.passphrase

fingrind list-accounts --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --limit 10

fingrind print-request-template > ./request.json
# Replace every replace-before-commit value in request.json.

fingrind preflight-entry --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --request-file ./request.json
fingrind record-sale-settled --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --request-file ./request.json \
  --attestation-custodian file-pkcs8 --attestation-principal-id 123e4567-e89b-12d3-a456-426614174000 \
  --attestation-key-file ./secrets/acme-founder.fgatk \
  --attestation-passphrase-file ./secrets/acme-founder.passphrase
fingrind trial-balance --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --output text
```

For a goods-trading book, choose `OWNER_MANAGED_TRADING` and add `--inventory-costing WEIGHTED_AVERAGE` to `open-book`. Purchases and count increases carry exact `quantity` plus `unitCost`; sales and shrinkage carry quantity while FinGrind derives authoritative cost of sales from the exact moving-average pool. Use `inventory-valuation` to inspect exact quantity and carrying value. Raw journals cannot touch inventory accounts.

On an accrual-basis book, `record-prepayment`, `record-deferred-revenue`, and `record-accrued-expense` create durable cut-offs. Use `record-accrual-cutoff-recognition` or `record-accrued-expense-settlement` for exact manual lifecycle amounts and `accrual-cutoff-schedule` to inspect what remains. FinGrind does not infer allocation schedules or combine these events with tax or foreign-exchange resolution.

`record-fixed-asset-capitalization`, `record-fixed-asset-depreciation`, and `record-fixed-asset-disposal` maintain a durable straight-line cost-model register. `record-financing-borrowing`, `record-financing-principal-repayment`, `record-financing-interest-accrual`, and `record-financing-interest-payment` retain nominal-principal and stated-interest history. `record-foreign-currency-obligation` plus `record-realized-foreign-exchange-settlement` retain one receivable and derive its realized gain or loss. Review the [fixed-asset](docs/ADR_FIXED_ASSETS.md), [financing](docs/ADR_FINANCING.md), and [realized-FX](docs/ADR_REALIZED_FOREIGN_EXCHANGE.md) boundaries and primary authorities before use.

For the deliberately narrow Latvian 2026 payroll profile, `record-latvian-monthly-payroll` requires explicit `taxBookHeldAtEmployer: true` and `dependantCount: 0` facts alongside gross EUR wages; it rejects other withholding profiles rather than assuming them. Its settlement commands discharge only retained obligations, and `latvian-payroll-register` retains each run, settlement, and reversal lineage for reconciliation. Review [the supported-profile and authority-source reference](docs/DOC_02_LatvianPayroll.md) before use; FinGrind does not determine a worker's legal status or submit statutory filings.

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
Entity                    : Acme Studio
Seed template             : Owner-managed service seed template
Accounting basis          : Cash basis
Functional currency       : EUR
Fiscal year start         : 01-01
Book start effective date : 2026-01-01
Posting coverage          : All posting kinds
As of                     : 2026-04-07
```

## Documentation

- [docs/USER_INSTALL.md](docs/USER_INSTALL.md) for package selection, checksums, and attestation
- [docs/USER_QUICK_START.md](docs/USER_QUICK_START.md) for the complete first-run path
- [docs/USER_CONTAINER.md](docs/USER_CONTAINER.md) for the mounted-container workflow
- [docs/USER_CLI.md](docs/USER_CLI.md) for commands and exit behavior
- [docs/USER_BOOK_ATTESTATION.md](docs/USER_BOOK_ATTESTATION.md) for founder credentials, verification, backups, and retained receipts
- [docs/USER_REQUESTS.md](docs/USER_REQUESTS.md) for request shapes and inventory rules
- [docs/USER_RESPONSES.md](docs/USER_RESPONSES.md) for response envelopes and deterministic failures
- [docs/USER_REJECTIONS.md](docs/USER_REJECTIONS.md) for actionable recovery from protected-book and attestation refusals
- [docs/USER_EXAMPLES.md](docs/USER_EXAMPLES.md) for longer workflows
- [docs/DOC_00_PrimarySources.md](docs/DOC_00_PrimarySources.md) for official legal, authority, and data sources used by jurisdiction-specific material
- [docs/DOC_02_LatvianPayroll.md](docs/DOC_02_LatvianPayroll.md) for the bounded Latvian payroll profile, lifecycle, exclusions, and authority links
- [docs/README.md](docs/README.md) for the full documentation index

## Legal

FinGrind is MIT-licensed. Its self-contained bundles vendor Jackson and Apache PDFBox (Apache 2.0), Noto Sans (SIL OFL 1.1), and SQLite3 Multiple Ciphers with SQLite (MIT / public domain). See [NOTICE](NOTICE) for complete attribution and [PATENTS.md](PATENTS.md) for patent considerations.

[LICENSE](LICENSE) | [NOTICE](NOTICE) | [PATENTS.md](PATENTS.md) | [LICENSE-APACHE-2.0](LICENSE-APACHE-2.0) | [LICENSE-SIL-OFL-1.1](LICENSE-SIL-OFL-1.1) | [LICENSE-SQLITE3MULTIPLECIPHERS](LICENSE-SQLITE3MULTIPLECIPHERS)
