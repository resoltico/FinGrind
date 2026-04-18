---
afad: "3.5"
version: "0.17.0"
domain: SQLITE_SCHEMA_CORE
updated: "2026-04-18"
route:
  keywords: [fingrind, sqlite, schema, book_meta, account, posting_fact, journal_line, idempotency, canonical-schema, book-file, reversal]
  questions: ["what is the current fingrind sqlite schema", "which tables exist in the fingrind book file", "how is idempotency stored in the sqlite book", "what tables and indexes exist in a fingrind book"]
---

# SQLite Core Schema

**Purpose**: Current durable schema for one FinGrind book file.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)

## Canonical SQL

```sql
create table if not exists book_meta (
    key text primary key,
    value text not null
) strict;

create table if not exists account (
    account_code text primary key,
    account_name text not null,
    normal_balance text not null check (normal_balance in ('DEBIT', 'CREDIT')),
    active integer not null check (active in (0, 1)),
    declared_at text not null
) strict;

create table if not exists posting_fact (
    posting_id text primary key,
    effective_date text not null,
    recorded_at text not null,
    actor_id text not null,
    actor_type text not null check (actor_type in ('USER', 'SYSTEM', 'AGENT')),
    command_id text not null,
    idempotency_key text not null,
    causation_id text not null,
    correlation_id text,
    reason text,
    source_channel text not null check (source_channel in ('CLI')),
    prior_posting_id text,
    unique (idempotency_key),
    foreign key (prior_posting_id) references posting_fact(posting_id),
    check (
        (prior_posting_id is null and reason is null)
        or
        (prior_posting_id is not null and reason is not null)
    )
) strict;

create table if not exists journal_line (
    posting_id text not null,
    line_order integer not null check (line_order >= 0),
    account_code text not null,
    entry_side text not null check (entry_side in ('DEBIT', 'CREDIT')),
    currency_code text not null,
    amount text not null,
    primary key (posting_id, line_order),
    foreign key (posting_id) references posting_fact(posting_id),
    foreign key (account_code) references account(account_code)
) strict;

create index if not exists posting_fact_by_prior_posting_id
    on posting_fact (prior_posting_id);

create index if not exists posting_fact_by_effective_recorded_posting
    on posting_fact (effective_date desc, recorded_at desc, posting_id desc);

create index if not exists journal_line_by_account_code
    on journal_line (account_code, posting_id, line_order);

create unique index if not exists posting_fact_one_reversal_per_target
    on posting_fact (prior_posting_id)
    where prior_posting_id is not null;
```

## Table Responsibilities

### `book_meta`

`book_meta` stores singleton book-level metadata values keyed by stable string names.

Important rules:
- `initializedAt` is stored here when `open-book` creates one initialized FinGrind book
- the table is `STRICT`, so SQLite rejects non-lossless type mismatches at the storage layer

### `account`

`account` stores the declared account registry for one book.

Important rules:
- `account_code` is the durable primary key for one declared account
- `normal_balance` is constrained to `DEBIT` or `CREDIT`
- `active` is constrained to `0` or `1`
- `declared_at` records the original declaration instant
- the table is `STRICT`, so SQLite rejects non-lossless type mismatches at the storage layer

### `posting_fact`

One row in `posting_fact` is one committed posting fact in one book.

Field groups:
- posting identity: `posting_id`
- financial date: `effective_date`
- audit time: `recorded_at`
- request provenance: `actor_*`, `command_id`, `idempotency_key`, `causation_id`, `correlation_id`
- committed audit channel: `source_channel`
- reversal linkage: `prior_posting_id` plus the coupled `reason`

Important rules:
- `idempotency_key` is unique inside one selected book file
- `prior_posting_id` must reference another committed posting when reversal linkage is present
- `prior_posting_id` and `reason` must appear together or not at all
- only one reversal row may point at the same `prior_posting_id`
- the table is `STRICT`, so SQLite rejects non-lossless type mismatches at the storage layer

### `journal_line`

`journal_line` stores the ordered debit and credit lines belonging to one posting.

Important rules:
- rows are ordered by `line_order`
- the composite primary key is `(posting_id, line_order)`
- `posting_id` must exist in `posting_fact`
- `account_code` must exist in `account`
- `entry_side` is constrained to `DEBIT` or `CREDIT`
- `line_order` must be zero or greater
- balancing is enforced in the domain model before persistence, not by SQL triggers
- the table is `STRICT`, so SQLite rejects non-lossless type mismatches at the storage layer

## Indexes

- `posting_fact_by_prior_posting_id` accelerates reversal-target lookups
- `posting_fact_by_effective_recorded_posting` accelerates reverse-chronological posting-history
  pages, including cursor-resume reads bounded by effective date, recorded-at, and posting id
- `journal_line_by_account_code` accelerates account-balance reads and account-filtered posting-history scans
- `posting_fact_one_reversal_per_target` enforces the one-reversal-per-target rule

## Schema Invariants

- One SQLite file is one book.
- One `posting_fact` row is one committed posting fact.
- One `account` row is one declared account.
- One `book_meta` row is one keyed book-level metadata fact.
- Request provenance and committed audit metadata are both durable.
- Reversal linkage is additive and optional.
- Journal lines are children of one committed posting fact.
- Journal lines must reference declared accounts.
- Book-local idempotency is durable through the unique constraint on `idempotency_key`.
- Reversal uniqueness is durable through a partial unique index on `prior_posting_id`.
- All durable tables are SQLite `STRICT` tables.

## Connection Hardening

- FinGrind opens book connections with `pragma foreign_keys = on`.
- FinGrind opens writable book connections with `pragma journal_mode = delete`.
- FinGrind opens book connections with `pragma synchronous = extra`.
- FinGrind opens book connections with `pragma trusted_schema = off`.
- FinGrind opens book connections with `pragma secure_delete = on`.
- FinGrind opens book connections with `pragma temp_store = memory`.

## Schema Posture

- This is the only current schema file for new books.
- There is no schema version table.
- There are no migration files.
- FinGrind publishes sequential in-place migration as the book policy. The current supported book
  format is `1`, so there are no historical upgrade steps bundled yet.
