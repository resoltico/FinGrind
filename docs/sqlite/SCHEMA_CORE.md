---
afad: "3.5"
version: "0.7.0"
domain: SQLITE_SCHEMA_CORE
updated: "2026-04-11"
route:
  keywords: [fingrind, sqlite, schema, posting_fact, journal_line, idempotency, canonical-schema, book-file, reversal]
  questions: ["what is the current fingrind sqlite schema", "which tables exist in the fingrind book file", "how is idempotency stored in the sqlite book"]
---

# SQLite Core Schema

**Purpose**: Current durable schema for one FinGrind book file.
**Source of truth**: [`book_schema.sql`](/Users/erst/Tools/FinGrind/sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)

## Canonical SQL

```sql
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
    foreign key (posting_id) references posting_fact(posting_id)
) strict;

create index if not exists posting_fact_by_prior_posting_id
    on posting_fact (prior_posting_id);

create unique index if not exists posting_fact_one_reversal_per_target
    on posting_fact (prior_posting_id)
    where prior_posting_id is not null;
```

## Table Responsibilities

### `posting_fact`

One row in `posting_fact` is one committed posting fact in one book.

Field groups:
- posting identity: `posting_id`
- financial date: `effective_date`
- audit time: `recorded_at`
- request provenance: `actor_*`, `command_id`, `idempotency_key`, `causation_id`, `correlation_id`, `reason`
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
- `entry_side` is constrained to `DEBIT` or `CREDIT`
- `line_order` must be zero or greater
- balancing is enforced in the domain model before persistence, not by SQL triggers
- the table is `STRICT`, so SQLite rejects non-lossless type mismatches at the storage layer

## Schema Invariants

- One SQLite file is one book.
- One `posting_fact` row is one committed posting fact.
- Request provenance and committed audit metadata are both durable.
- Reversal linkage is additive and optional.
- Journal lines are children of one committed posting fact.
- Book-local idempotency is durable through the unique constraint on `idempotency_key`.
- Reversal uniqueness is durable through a partial unique index on `prior_posting_id`.
- Both durable tables are SQLite `STRICT` tables.

## Connection Hardening

- FinGrind opens book connections with `pragma foreign_keys = on`.
- FinGrind opens book connections with `pragma trusted_schema = off`.

## Schema Posture

- This is the only current schema file for new books.
- There is no schema version table.
- There are no migration files.
- There is no backward-compatibility layer for older book shapes during the current hard-break phase.
