---
afad: "3.5"
version: "0.1.0-SNAPSHOT"
domain: SQLITE_SCHEMA_CORE
updated: "2026-04-08"
route:
  keywords: [fingrind, sqlite, schema, posting_fact, journal_line, idempotency, bootstrap, book-file]
  questions: ["what is the current fingrind sqlite schema", "which tables exist in the fingrind book file", "how is idempotency stored in the sqlite book"]
---

# SQLite Core Schema

**Purpose**: Current durable schema for one FinGrind book file.
**Source of truth**: [`V1__bootstrap.sql`](/Users/erst/Tools/FinGrind/sqlite/src/main/resources/dev/erst/fingrind/sqlite/V1__bootstrap.sql)

## Current Bootstrap SQL

```sql
create table if not exists posting_fact (
    posting_id text primary key,
    effective_date text not null,
    recorded_at text not null,
    actor_id text not null,
    actor_type text not null,
    command_id text not null,
    idempotency_key text not null,
    causation_id text not null,
    correlation_id text,
    reason text,
    source_channel text not null,
    correction_kind text,
    prior_posting_id text,
    unique (idempotency_key)
);

create table if not exists journal_line (
    posting_id text not null,
    line_order integer not null,
    account_code text not null,
    entry_side text not null,
    currency_code text not null,
    amount text not null,
    primary key (posting_id, line_order),
    foreign key (posting_id) references posting_fact(posting_id)
);
```

## Table Responsibilities

### `posting_fact`

One row in `posting_fact` is one committed posting fact in one book.

Field groups:
- posting identity: `posting_id`
- financial date: `effective_date`
- audit time: `recorded_at`
- provenance: `actor_*`, `command_id`, `idempotency_key`, `causation_id`, `correlation_id`,
  `reason`, `source_channel`
- correction linkage: `correction_kind`, `prior_posting_id`

Important rule:
- `idempotency_key` is unique inside one selected book file

### `journal_line`

`journal_line` stores the ordered debit and credit lines belonging to one posting.

Important rules:
- rows are ordered by `line_order`
- the composite primary key is `(posting_id, line_order)`
- `posting_id` must exist in `posting_fact`
- balancing is enforced in the domain model before persistence, not by SQL triggers

## Schema Invariants

- One SQLite file is one book.
- One `posting_fact` row is one committed posting fact.
- Provenance is durable, not transient.
- Correction linkage is additive and optional.
- Journal lines are children of one committed posting fact.
- Book-local idempotency is durable through the unique constraint on `idempotency_key`.

## Extension Rules

The core schema is jurisdiction-agnostic.

That means:
- do not redefine debit, credit, journal-entry grammar, or provenance in country-pack tables
- do not split the current core tables by country
- do not replace `posting_fact` with a generic mutable ledger row
- add future jurisdiction-specific tables as explicit extensions around this core, not instead of it

Country-pack documentation policy lives in [SCHEMA_COUNTRY_PACKS.md](./SCHEMA_COUNTRY_PACKS.md).
