# FinGrind — command-line double-entry bookkeeping with one protected book per accounting entity

FinGrind is a command-line bookkeeping tool. Each accounting entity gets one encrypted SQLite
file. Every entry is validated before it commits. Balances, ledgers, and period summaries come
back as tables, JSON, CSV, or PDF.

Most bookkeeping setups have the same problem: the current position is spread across multiple
places, so confirming balances or recent activity takes manual reconstruction. FinGrind keeps one
protected book per accounting entity. You post validated entries to that book and query the same
file for balances, ledgers, and summaries. Invalid entries are rejected before they change the
book.

- Open one encrypted book per accounting entity, protected by a generated key file
- Declare accounts before posting; unbalanced entries and undeclared accounts are rejected at commit
- Post double-entry journal entries with provenance and idempotency keys
- Read back account balances, trial balances, account ledgers, and period summaries
- Export any report as human-readable tables, JSON, CSV, or PDF

**Project status: Alpha.** FinGrind is under active development and is not yet production-ready.

## The Daily Grind

Every command reads from or writes to the same protected file. The key file is required every
time. If the key is lost, the book cannot be opened. Keep the key outside the book directory so a
copy of the book does not automatically include the unlocking key:

```bash
# Create one protected book
fingrind generate-book-key-file --book-key-file ./secrets/acme.book-key
fingrind open-book --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --entity-name "Acme Studio" --functional-currency EUR --fiscal-year-start 01-01

# Declare accounts, then post a balanced entry
fingrind declare-account --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --request-file ./cash-account.json
fingrind post-entry --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --request-file ./entry.json

# Read the trial balance back
fingrind trial-balance --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key
```

```
Account | Name    | Currency | Debit total | Credit total | Net amount | Balance side
--------+---------+----------+-------------+--------------+------------+-------------
1000    | Cash    | EUR      |      811.00 |         0.00 |     811.00 | DEBIT
2000    | Revenue | EUR      |        0.00 |       811.00 |     811.00 | CREDIT
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
