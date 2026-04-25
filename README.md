<!--
RETRIEVAL_HINTS:
  keywords: [fingrind, bookkeeping, protected book, balances, reports, pdf, automation, quick start]
  answers: [what is fingrind, who is fingrind for, what changes with fingrind, where is the quick start]
  related: [docs/USER_QUICK_START.md, docs/USER_CLI.md, docs/USER_EXAMPLES.md, docs/README.md]
-->

# FinGrind — calmer bookkeeping in one protected book

*FinGrind is a bookkeeping tool for people who want one protected book per business, strict checks before entries land, and clear answers when they need balances or reports.*

## At a Glance

- Keep one protected book for one business instead of letting records sprawl.
- Catch bad posting input before it becomes cleanup work later.
- Read back balances, ledgers, trial balances, and period summaries from the same place you post.
- Export the same reporting work as readable output, CSV, or PDF when the day calls for it.

## Who It Helps

FinGrind fits operators, finance-minded owners, and small teams who want bookkeeping to stay
explicit. It works especially well when a person and a repeatable workflow both need the same clear
rules instead of a loose spreadsheet process.

It is a good fit if you want:
- one book file per business,
- declared accounts before posting,
- predictable rejection of bad input,
- reporting you can read yourself or hand to automation,
- one machine-readable contract surface, via `capabilities` and the template commands, for the
  same request and plan rules humans use, including typed field descriptors and executable JSON
  Schema for posting and ledger-plan payloads.

## What Changes

- You stop wondering whether a book is ready. Books are opened explicitly, and their state stays
  visible.
- You stop finding some problems too late. FinGrind rejects missing accounts, inactive accounts,
  duplicate request keys, and invalid reversals at the point where they happen.
- You stop piecing basic answers together by hand. The same tool can show posting history, account
  balances, a trial balance, an account ledger, or a period summary.

## In the Flow of a Day

You open the book you care about, do the work you need to do, and then read back where things
stand. That makes FinGrind a calm first stop when you want the morning picture to be clear instead
of spread across notes, spreadsheets, and half-finished checks.

## Proof and Trust

- Books stay protected at rest, and wrong-key failures come back as clear FinGrind errors rather
  than raw storage noise.
- Public downloads are ready to run on macOS, Linux, and Windows.
- The self-contained bundle and the public container disclose the same runtime contract instead of
  drifting onto separate packaging stories.
- The machine-readable discovery and template surfaces come from the same canonical contract facts
  the CLI, bundle, Docker image, and shell verifiers use.
- The reporting surface already covers the questions most people ask first: account balance, trial
  balance, account ledger, and period summary.
- The model stays intentionally strict: one book per business, one currency per entry, positive line
  amounts, and balanced entries only.
- FinGrind is open source under MIT, with bundled dependency license texts, notices, and patent
  notes linked below.

## Start Here

If this sounds like the right shape of bookkeeping, start with:

- [the quick start](./docs/USER_QUICK_START.md)
- [example flows and outputs](./docs/USER_EXAMPLES.md)
- [the full user docs](./docs/README.md)
- [developer and verification guides](./docs/DEVELOPER.md)
- [the latest download](https://github.com/resoltico/FinGrind/releases/latest)

## Questions

### Is this one book for one company, or one tool for many companies?

FinGrind is one tool for many books, but each book file is for one business. That boundary is part
of the design, because it keeps ownership, protection, and reporting scope easy to reason about.

### Do I need to be a developer to use it?

No. You do need to be comfortable using a command-line tool and following a short setup flow. The
public downloads are self-contained, and the quick start walks through the first run directly.

### What can I ask it for after I post entries?

You can inspect the book, list accounts, look up postings, page through posting history, check one
account balance, and generate a trial balance, account ledger, or period summary. Those readbacks
can also be rendered as readable output, CSV, or PDF, and optional PDF write problems come back as
warnings without replacing the primary report result.

### What is it not trying to be?

It is not a free-form spreadsheet, and it is not a broad multi-user desktop accounting suite.
FinGrind is strongest when you want explicit rules, one protected book per business, and a workflow
that stays legible.

---

## Legal

- [LICENSE](./LICENSE)
- [LICENSE-APACHE-2.0](./LICENSE-APACHE-2.0)
- [LICENSE-SIL-OFL-1.1](./LICENSE-SIL-OFL-1.1)
- [NOTICE](./NOTICE)
- [PATENTS.md](./PATENTS.md)
- [LICENSE-SQLITE3MULTIPLECIPHERS](./LICENSE-SQLITE3MULTIPLECIPHERS)
