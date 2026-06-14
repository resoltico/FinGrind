# FinGrind — command-line double-entry bookkeeping with one protected book per accounting entity

FinGrind is a command-line bookkeeping tool for one accounting entity per protected SQLite book.
The current public kernel is one narrow internal-management cash-bookkeeping profile with one
seeded owner-managed service starter chart. You initialize the book explicitly, extend that chart
when needed, seed first balances through one structured opening-position flow, commit typed
cash-kernel bookkeeping entries or one ordered AI-agent ledger plan, manage the same protected book
through explicit rekey and backup-recovery commands, and query that file for balances, ledgers, and
built-in statements. Humans use the task guide in `help`; automation uses `capabilities --output json`.
Invalid writes and invalid maintenance mutations are rejected before they change the selected book.

- Open one encrypted book per accounting entity, protected by a generated key file
- Start from one seeded owner-managed service starter chart, then declare supplemental accounts
  and chart nodes when the built-in template is not enough
- Post typed cash-kernel bookkeeping entries with retained evidence, provenance, and idempotency
  keys, or run one ordered AI-agent ledger plan; use the structured opening-position flow for first
  balances and reserve administrative entries for explicit reversals
- Rekey protected books, export verified encrypted backup pairs, and inspect, restore, or delete
  interrupted rekey rollback artifacts through explicit maintenance commands
- Scaffold placeholder-first request and plan documents with `print-request-template` and
  `print-plan-template`
- Read back account balances, trial balances with totals and balanced verdicts, account ledgers,
  period summaries, and the built-in financial position, income statement, and changes in equity
  reports
- Export any report as operator-readable text tables, JSON, CSV, or PDF

**Project status: Alpha.** FinGrind is under active development and is not yet production-ready.
Public self-contained downloads are published for Linux targets. On macOS or Windows, use the
published container workflow or a source checkout.

## Quick Start

Every command reads from or writes to the same protected file. The key file is required every
time. If the key is lost, the book cannot be opened. Keep the key outside the book directory so a
copy of the book does not automatically include the unlocking key. If `./secrets/` or `./books/`
does not exist, FinGrind creates it with owner-only permissions. If either directory already
exists, keep it owner-only before you ask FinGrind to write a key or book there.

The example below is launcher-neutral and bundle-safe: it creates the request JSON locally from
the live CLI instead of depending on repository fixtures. The seeded starter chart created by
`open-book` already includes `cash` and `service-revenue`, so the first posting does not need
supplemental chart setup. For the Linux bundle-first walkthrough and the exact public package
matrix, use
[docs/USER_QUICK_START.md](docs/USER_QUICK_START.md) and [docs/USER_INSTALL.md](docs/USER_INSTALL.md).

```bash
# Create one protected book
fingrind generate-book-key-file --book-key-file ./secrets/acme.book-key
fingrind open-book --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --entity-name "Acme Studio" --functional-currency EUR \
  --fiscal-year-start 01-01

# Review the seeded starter chart
fingrind list-accounts --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --limit 10

# Copy the concrete first-post sample that ships with public bundles, or emit the canonical
# placeholder-first request scaffold directly on source-checkout and container paths.
cp ./quick-start-request.json ./request.json
# Alternative when that bundled sample is not present:
fingrind print-request-template > ./request.json

# Edit ./request.json so it replaces every replace-before-commit token with real evidence and
# provenance values, then post one balanced entry
fingrind post-entry --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --request-file ./request.json

# Read the trial balance back
fingrind trial-balance --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --effective-date-as-of 2026-04-08 --output text
```

The interface is layered: `help` is the operator guide, `print-request-template` and
`print-plan-template` emit placeholder-first scaffold documents, and `capabilities --output json`
exposes the machine-readable discovery surface. The default JSON discovery detail is compact; rerun
with `--detail minimal` for the terse overview or `--detail full` for the exhaustive embedded
schemas and doctrine surface. For shell automation or agent sessions that prefer structured stdout
by default, set `FINGRIND_DEFAULT_OUTPUT=json`; an explicit per-command `--output ...` flag still
wins when you need a one-off text or CSV result.

```bash
fingrind help post-entry
fingrind print-request-template > request.json
fingrind print-plan-template > plan.json
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
Starter chart       : Owner-managed service starter chart
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
