# FinGrind — command-line double-entry bookkeeping with one protected book per accounting entity

FinGrind is a command-line bookkeeping tool for one accounting entity per protected SQLite book.
You initialize the book explicitly, declare accounts explicitly, commit balanced journal entries,
and query the same file for balances, ledgers, and summaries. Invalid writes are rejected before
they change the book.

- Open one encrypted book per accounting entity, protected by a generated key file
- Declare accounts before posting; unbalanced entries and undeclared accounts are rejected at commit
- Post double-entry journal entries with provenance and idempotency keys
- Read back account balances, trial balances, account ledgers, and period summaries
- Export any report as human-readable tables, JSON, CSV, or PDF

**Project status: Alpha.** FinGrind is under active development and is not yet production-ready.

## Quick Start

Every command reads from or writes to the same protected file. The key file is required every
time. If the key is lost, the book cannot be opened. Keep the key outside the book directory so a
copy of the book does not automatically include the unlocking key.

The example below uses the checked-in request files under `docs/examples/`. For a bundle-first
walkthrough that creates those files locally, use [docs/USER_QUICK_START.md](docs/USER_QUICK_START.md).

```bash
# Create one protected book
fingrind generate-book-key-file --book-key-file ./secrets/acme.book-key
fingrind open-book --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --entity-name "Acme Studio" --entity-form COMPANY --functional-currency EUR \
  --fiscal-year-start 01-01 --accounting-basis ACCRUAL

# Declare the accounts used by the first posting
fingrind declare-account --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --request-file ./docs/examples/declare-account-cash.json
fingrind declare-account --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --request-file ./docs/examples/declare-account-revenue.json

# Post one balanced entry
fingrind post-entry --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --request-file ./docs/examples/basic-posting-request.json

# Read the trial balance back
fingrind trial-balance --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --effective-date-to 2026-04-08 --output human
```

```
Trial Balance
=============

Entity                : Acme Studio
Functional currency   : EUR
Fiscal year start     : 01-01
Posting coverage      : All posting kinds
Comparative reference : book start to 2025-04-08
Effective date to     : 2026-04-08

Account | Name    | Account type | Account role | Normal balance | Active | Currency | Debit total | Credit total | Net amount | Balance side
--------+---------+--------------+--------------+----------------+--------+----------+-------------+--------------+------------+-------------
1000    | Cash    | Asset        | Ordinary     | Debit          | Yes    |      EUR |       10.00 |         0.00 | 10.00      | Debit
2000    | Revenue | Revenue      | Ordinary     | Credit         | Yes    |      EUR |        0.00 |        10.00 | 10.00      | Credit
```

Invalid entries are rejected before commit. The CLI reports specific causes such as unbalanced
lines, undeclared accounts, or duplicate idempotency keys.

## Documentation

Start with the docs index: [docs/README.md](docs/README.md)

For deep API and symbol routing, use: [docs/DOC_00_Index.md](docs/DOC_00_Index.md)

## Legal

FinGrind is MIT-licensed. Its self-contained bundle vendors Jackson and Apache PDFBox (Apache 2.0),
Noto Sans (SIL OFL 1.1), and SQLite3 Multiple Ciphers with SQLite (MIT / public domain).
See [NOTICE](NOTICE) for the complete attribution list and [PATENTS.md](PATENTS.md) for
patent considerations.

[LICENSE](LICENSE) | [NOTICE](NOTICE) | [PATENTS.md](PATENTS.md) | [LICENSE-APACHE-2.0](LICENSE-APACHE-2.0) | [LICENSE-SIL-OFL-1.1](LICENSE-SIL-OFL-1.1) | [LICENSE-SQLITE3MULTIPLECIPHERS](LICENSE-SQLITE3MULTIPLECIPHERS)
