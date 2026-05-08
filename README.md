[![FinGrind Art](https://raw.githubusercontent.com/resoltico/FinGrind/main/images/FinGrind.png)](https://github.com/resoltico/FinGrind)

[![Release](https://img.shields.io/github/v/release/resoltico/FinGrind?label=release)](https://github.com/resoltico/FinGrind/releases)
[![CI](https://github.com/resoltico/FinGrind/actions/workflows/ci.yml/badge.svg)](https://github.com/resoltico/FinGrind/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Java 26](https://img.shields.io/badge/java-26-orange.svg)](https://openjdk.org/projects/jdk/26/)
[![Platform](https://img.shields.io/badge/platform-macOS%20%7C%20Linux%20%7C%20Windows-lightgrey.svg)](https://github.com/resoltico/FinGrind/releases)
[![Encrypted](https://img.shields.io/badge/storage-encrypted%20SQLite-blueviolet.svg)](https://utelle.github.io/SQLite3MultipleCiphers/)

# FinGrind — command-line double-entry bookkeeping with one protected book per accounting entity

FinGrind is a command-line bookkeeping tool. Each accounting entity gets one encrypted SQLite
file. Every entry is validated before it commits. Balances, ledgers, and period summaries come
back as tables, JSON, CSV, or PDF.

Every bookkeeping setup hits the same morning problem: you need to know where things stand and the
answer is spread across notes, tabs, and half-finished checks. With FinGrind the daily grind stays
clean — one protected file, one command to read it back, and bad entries rejected before they
ever reach the book.

- Open one encrypted book per accounting entity, protected by a generated key file
- Declare accounts before posting; unbalanced entries and undeclared accounts are rejected at commit
- Post double-entry journal entries with provenance and idempotency keys
- Read back account balances, trial balances, account ledgers, and period summaries
- Export any report as human-readable tables, JSON, CSV, or PDF

[Quick start](docs/USER_QUICK_START.md) · [Command guide](docs/USER_CLI.md)

## The Daily Grind

Every command reads from or writes to the same protected file. The key file is required every time
— lose the key and the book stays locked. Keep the key outside the book directory so copies of the
book do not automatically carry the unlocking secret with them:

```bash
# Create one protected book
fingrind generate-book-key-file --book-key-file ./secrets/acme.book-key
fingrind open-book --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key

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

Wrong entries come back as clear errors before they land: unbalanced lines, undeclared accounts,
duplicate idempotency keys — all rejected at the point where they happen.

## Where It Fits

Finance-minded owners, small teams, and operators who want explicit bookkeeping without a
spreadsheet. One book per accounting entity, one tool to post and read back.

## Get It

[Download for macOS, Linux, or Windows →](https://github.com/resoltico/FinGrind/releases/latest)

The download is self-contained — no separate Java install needed. The
[quick start](docs/USER_QUICK_START.md) walks from download to first posted entry.

## Legal

FinGrind is MIT-licensed. Its self-contained bundle vendors Jackson and Apache PDFBox (Apache 2.0),
Noto Sans (SIL OFL 1.1), and SQLite3 Multiple Ciphers with SQLite (MIT / public domain).
See [NOTICE](NOTICE) for the complete attribution list and [PATENTS.md](PATENTS.md) for
patent considerations.

The FinGrind README and first-party project graphics are Copyright (c) 2026 Ervins Strauhmanis.

[LICENSE](LICENSE) | [NOTICE](NOTICE) | [PATENTS.md](PATENTS.md) | [LICENSE-APACHE-2.0](LICENSE-APACHE-2.0) | [LICENSE-SIL-OFL-1.1](LICENSE-SIL-OFL-1.1) | [LICENSE-SQLITE3MULTIPLECIPHERS](LICENSE-SQLITE3MULTIPLECIPHERS)
