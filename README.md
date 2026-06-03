# FinGrind — command-line double-entry bookkeeping with one protected book per accounting entity

FinGrind is a command-line bookkeeping tool for one accounting entity per protected SQLite book.
The current public kernel is one narrow internal-management cash-bookkeeping profile with one
seeded owner-managed service starter chart. You initialize the book explicitly, extend that chart
when needed, commit typed bookkeeping entries or explicit administrative adjustments, and query
the same file for balances, ledgers, and built-in statements. Humans use the task guide in
`help`; automation uses `capabilities --output json`. Invalid writes are rejected before they
change the book.

- Open one encrypted book per accounting entity, protected by a generated key file
- Start from one seeded owner-managed service starter chart, then declare supplemental accounts
  and chart nodes when the built-in template is not enough
- Post typed bookkeeping entries with retained evidence, provenance, and idempotency keys; reserve
  raw journals for explicit administrative adjustments
- Scaffold runnable request and plan documents with `print-request-template` and
  `print-plan-template`
- Read back account balances, trial balances with totals and balanced verdicts, account ledgers,
  period summaries, and the built-in financial position, income statement, and changes in equity
  reports
- Export any report as operator-readable text tables, JSON, CSV, or PDF

**Project status: Alpha.** FinGrind is under active development and is not yet production-ready.

## Quick Start

Every command reads from or writes to the same protected file. The key file is required every
time. If the key is lost, the book cannot be opened. Keep the key outside the book directory so a
copy of the book does not automatically include the unlocking key. If `./secrets/` or `./books/`
does not exist, FinGrind creates it with owner-only permissions. If either directory already
exists, keep it owner-only before you ask FinGrind to write a key or book there.

The example below uses the checked-in request files under `docs/examples/` and the seeded starter
chart that `open-book` creates. The separate `declare-account-supplemental-*` examples are
template-extension flows, not alternate first-run starters. For a bundle-first walkthrough that
creates the JSON request file locally, use [docs/USER_QUICK_START.md](docs/USER_QUICK_START.md).

```bash
# Create one protected book
fingrind generate-book-key-file --book-key-file ./secrets/acme.book-key
fingrind open-book --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --entity-name "Acme Studio" --functional-currency EUR \
  --fiscal-year-start 01-01

# Review the seeded starter chart
fingrind list-accounts --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --limit 10

# Post one balanced entry
fingrind post-entry --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --request-file ./docs/examples/basic-posting-request.json

# Read the trial balance back
fingrind trial-balance --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --effective-date-as-of 2026-04-08 --output text
```

The interface is layered: `help` is the operator guide, `print-request-template` scaffolds one
runnable sample document, and `capabilities --output json` exposes the machine-readable discovery
surface. Rerun discovery with `--detail compact` for stable command and output descriptors or
`--detail full` for the exhaustive embedded schemas and doctrine surface.

```bash
fingrind help post-entry
fingrind print-request-template post-entry > request.json
fingrind capabilities --output json
```

```
Trial Balance
=============

As of         : 2026-04-08
Balance state : Balanced

Current totals
--------------
Currency | Debit total | Credit total | Net amount | Balance side
---------+-------------+--------------+------------+-------------
EUR      |       10.00 |        10.00 |       0.00 | Zero

Accounts
--------
Account         | Name            | Currency | Debit total | Credit total | Net amount | Balance side
----------------+-----------------+----------+-------------+--------------+------------+-------------
cash            | Cash            | EUR      |       10.00 |         0.00 |      10.00 | Debit
service-revenue | Service Revenue | EUR      |        0.00 |        10.00 |      10.00 | Credit

Context
-------
Entity              : Acme Studio
Accounting kernel   : internal-management-cash-bookkeeping-kernel
Accounting basis    : CASH_BASIS
Framework posture   : NON_STATUTORY_INTERNAL_MANAGEMENT
Entity form         : OWNER_MANAGED_SINGLE_ENTITY
Book template       : OWNER_MANAGED_SERVICE_CASH
Functional currency : EUR
Fiscal year start   : 01-01
Posting coverage    : All posting kinds
```

Invalid entries are rejected before commit. The CLI reports specific causes such as unbalanced
lines, undeclared accounts, invalid evidence metadata, or duplicate idempotency keys.

## Documentation

Start with the docs index: [docs/README.md](docs/README.md)

For deep API and symbol routing, use: [docs/DOC_00_Index.md](docs/DOC_00_Index.md)

## Legal

FinGrind is MIT-licensed. Its self-contained bundle vendors Jackson and Apache PDFBox (Apache 2.0),
Noto Sans (SIL OFL 1.1), and SQLite3 Multiple Ciphers with SQLite (MIT / public domain).
See [NOTICE](NOTICE) for the complete attribution list and [PATENTS.md](PATENTS.md) for
patent considerations.

[LICENSE](LICENSE) | [NOTICE](NOTICE) | [PATENTS.md](PATENTS.md) | [LICENSE-APACHE-2.0](LICENSE-APACHE-2.0) | [LICENSE-SIL-OFL-1.1](LICENSE-SIL-OFL-1.1) | [LICENSE-SQLITE3MULTIPLECIPHERS](LICENSE-SQLITE3MULTIPLECIPHERS)
