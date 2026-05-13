---
afad: "4.0"
version: "0.35.0"
domain: SQLITE_SCHEMA_CORE
updated: "2026-05-13"
route:
  keywords: [fingrind, sqlite, schema, book_meta, account, posting_fact, journal_line, audit_event, idempotency, canonical-schema, book-file, reversal]
  questions: ["what is the current fingrind sqlite schema", "which tables exist in the fingrind book file", "how is idempotency stored in the sqlite book", "what tables and indexes exist in a fingrind book"]
---

# SQLite Core Schema

**Purpose**: Current durable schema for one FinGrind book file.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This document is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. Do not hand-edit the derived schema inventory below.

## Canonical SQL

```sql
pragma application_id = 1179079236;
pragma user_version = 2;

create table if not exists book_meta (
    key text primary key,
    value text not null
) strict;

create table if not exists account (
    account_code text primary key check (
        length(account_code) between 1 and 255
        and account_code glob '[A-Za-z0-9]*'
        and account_code not glob '*[^A-Za-z0-9._:/-]*'
    ),
    account_name text not null check (length(trim(account_name)) > 0),
    account_type text not null check (account_type in ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')),
    normal_balance text not null check (normal_balance in ('DEBIT', 'CREDIT')),
    active integer not null check (active in (0, 1)),
    declared_at text not null
) strict;

create table if not exists posting_fact (
    posting_id text primary key,
    effective_date text not null,
    recorded_at text not null,
    actor_id text not null check (length(trim(actor_id)) > 0),
    actor_type text not null check (actor_type in ('HUMAN', 'SYSTEM', 'AGENT')),
    command_id text not null check (length(trim(command_id)) > 0),
    idempotency_key text not null check (
        length(idempotency_key) between 1 and 128
        and idempotency_key glob '[A-Za-z0-9]*'
        and idempotency_key not glob '*[^A-Za-z0-9._:/-]*'
    ),
    causation_id text not null check (length(trim(causation_id)) > 0),
    correlation_id text check (correlation_id is null or length(trim(correlation_id)) > 0),
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
    account_code text not null check (
        length(account_code) between 1 and 255
        and account_code glob '[A-Za-z0-9]*'
        and account_code not glob '*[^A-Za-z0-9._:/-]*'
    ),
    entry_side text not null check (entry_side in ('DEBIT', 'CREDIT')),
    currency_code text not null check (
        length(currency_code) = 3
        and currency_code glob '[A-Z][A-Z][A-Z]'
    ),
    amount_minor integer not null check (amount_minor > 0),
    primary key (posting_id, line_order),
    foreign key (posting_id) references posting_fact(posting_id),
    foreign key (account_code) references account(account_code)
) strict;

create table if not exists audit_event (
    audit_event_order integer primary key,
    recorded_at text not null check (length(trim(recorded_at)) > 0),
    event_kind text not null check (
        event_kind in (
            'BOOK_OPENED',
            'ACCOUNT_DECLARED',
            'ACCOUNT_REACTIVATED',
            'POSTING_COMMITTED',
            'POSTING_REVERSED',
            'BOOK_REKEYED'
        )
    ),
    account_code text,
    posting_id text,
    foreign key (account_code) references account(account_code),
    foreign key (posting_id) references posting_fact(posting_id),
    check (
        (event_kind in ('BOOK_OPENED', 'BOOK_REKEYED') and account_code is null and posting_id is null)
        or
        (event_kind in ('ACCOUNT_DECLARED', 'ACCOUNT_REACTIVATED') and account_code is not null and posting_id is null)
        or
        (event_kind in ('POSTING_COMMITTED', 'POSTING_REVERSED') and account_code is null and posting_id is not null)
    )
) strict;

create index if not exists posting_fact_by_prior_posting_id
    on posting_fact (prior_posting_id);

create index if not exists posting_fact_by_effective_recorded_posting
    on posting_fact (effective_date desc, recorded_at desc, posting_id desc);

create index if not exists journal_line_by_account_code
    on journal_line (account_code, posting_id, line_order);

create index if not exists audit_event_by_recorded_at
    on audit_event (recorded_at, audit_event_order);

create unique index if not exists posting_fact_one_reversal_per_target
    on posting_fact (prior_posting_id)
    where prior_posting_id is not null;

create trigger if not exists posting_fact_reject_update
before update on posting_fact
begin
    select raise(fail, 'posting_fact rows are append-only.');
end;

create trigger if not exists posting_fact_reject_delete
before delete on posting_fact
begin
    select raise(fail, 'posting_fact rows are append-only.');
end;

create trigger if not exists journal_line_reject_update
before update on journal_line
begin
    select raise(fail, 'journal_line rows are append-only.');
end;

create trigger if not exists journal_line_reject_delete
before delete on journal_line
begin
    select raise(fail, 'journal_line rows are append-only.');
end;

create trigger if not exists audit_event_reject_update
before update on audit_event
begin
    select raise(fail, 'audit_event rows are append-only.');
end;

create trigger if not exists audit_event_reject_delete
before delete on audit_event
begin
    select raise(fail, 'audit_event rows are append-only.');
end;
```

## Durable Tables

### `book_meta`

Columns:
- `key`: `text primary key`
- `value`: `text not null`

Table-level constraints:
- None.

### `account`

Columns:
- `account_code`: `text primary key check ( length(account_code) between 1 and 255 and account_code glob '[A-Za-z0-9]*' and account_code not glob '*[^A-Za-z0-9._:/-]*' )`
- `account_name`: `text not null check (length(trim(account_name)) > 0)`
- `account_type`: `text not null check (account_type in ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE'))`
- `normal_balance`: `text not null check (normal_balance in ('DEBIT', 'CREDIT'))`
- `active`: `integer not null check (active in (0, 1))`
- `declared_at`: `text not null`

Table-level constraints:
- None.

### `posting_fact`

Columns:
- `posting_id`: `text primary key`
- `effective_date`: `text not null`
- `recorded_at`: `text not null`
- `actor_id`: `text not null check (length(trim(actor_id)) > 0)`
- `actor_type`: `text not null check (actor_type in ('HUMAN', 'SYSTEM', 'AGENT'))`
- `command_id`: `text not null check (length(trim(command_id)) > 0)`
- `idempotency_key`: `text not null check ( length(idempotency_key) between 1 and 128 and idempotency_key glob '[A-Za-z0-9]*' and idempotency_key not glob '*[^A-Za-z0-9._:/-]*' )`
- `causation_id`: `text not null check (length(trim(causation_id)) > 0)`
- `correlation_id`: `text check (correlation_id is null or length(trim(correlation_id)) > 0)`
- `reason`: `text`
- `source_channel`: `text not null check (source_channel in ('CLI'))`
- `prior_posting_id`: `text`

Table-level constraints:
- `unique (idempotency_key)`
- `foreign key (prior_posting_id) references posting_fact(posting_id)`
- `check ( (prior_posting_id is null and reason is null) or (prior_posting_id is not null and reason is not null) )`

### `journal_line`

Columns:
- `posting_id`: `text not null`
- `line_order`: `integer not null check (line_order >= 0)`
- `account_code`: `text not null check ( length(account_code) between 1 and 255 and account_code glob '[A-Za-z0-9]*' and account_code not glob '*[^A-Za-z0-9._:/-]*' )`
- `entry_side`: `text not null check (entry_side in ('DEBIT', 'CREDIT'))`
- `currency_code`: `text not null check ( length(currency_code) = 3 and currency_code glob '[A-Z][A-Z][A-Z]' )`
- `amount_minor`: `integer not null check (amount_minor > 0)`

Table-level constraints:
- `primary key (posting_id, line_order)`
- `foreign key (posting_id) references posting_fact(posting_id)`
- `foreign key (account_code) references account(account_code)`

### `audit_event`

Columns:
- `audit_event_order`: `integer primary key`
- `recorded_at`: `text not null check (length(trim(recorded_at)) > 0)`
- `event_kind`: `text not null check ( event_kind in ( 'BOOK_OPENED', 'ACCOUNT_DECLARED', 'ACCOUNT_REACTIVATED', 'POSTING_COMMITTED', 'POSTING_REVERSED', 'BOOK_REKEYED' ) )`
- `account_code`: `text`
- `posting_id`: `text`

Table-level constraints:
- `foreign key (account_code) references account(account_code)`
- `foreign key (posting_id) references posting_fact(posting_id)`
- `check ( (event_kind in ('BOOK_OPENED', 'BOOK_REKEYED') and account_code is null and posting_id is null) or (event_kind in ('ACCOUNT_DECLARED', 'ACCOUNT_REACTIVATED') and account_code is not null and posting_id is null) or (event_kind in ('POSTING_COMMITTED', 'POSTING_REVERSED') and account_code is null and posting_id is not null) )`

## Durable Indexes

- `posting_fact_by_prior_posting_id` on `posting_fact`: `create index if not exists posting_fact_by_prior_posting_id on posting_fact (prior_posting_id);`
- `posting_fact_by_effective_recorded_posting` on `posting_fact`: `create index if not exists posting_fact_by_effective_recorded_posting on posting_fact (effective_date desc, recorded_at desc, posting_id desc);`
- `journal_line_by_account_code` on `journal_line`: `create index if not exists journal_line_by_account_code on journal_line (account_code, posting_id, line_order);`
- `audit_event_by_recorded_at` on `audit_event`: `create index if not exists audit_event_by_recorded_at on audit_event (recorded_at, audit_event_order);`
- `posting_fact_one_reversal_per_target` on `posting_fact`: `create unique index if not exists posting_fact_one_reversal_per_target on posting_fact (prior_posting_id) where prior_posting_id is not null;`

## Runtime Integrity Semantics

- Initialized FinGrind books record both `book_meta.initialized_at` and `book_meta.schema_fingerprint_sha256`.
- An opened book is accepted as canonical only when `PRAGMA integrity_check` returns `ok`, `PRAGMA foreign_key_check` returns no rows, the recorded schema fingerprint matches the live canonical schema-object fingerprint, every persisted posting owns journal lines and balances to zero inside one currency bucket, and every persisted money triple decodes through the exact-money codec.
- Posting commits stage journal lines in temporary `pending_journal_line` rows and persist them only after the SQL aggregate gate proves at least two lines, at least one debit, at least one credit, exactly one currency bucket, and a zero signed minor-unit total.

## Schema Posture

- `application_id`: `1179079236`
- `user_version`: `2`
- Canonical durable tables: `book_meta`, `account`, `posting_fact`, `journal_line`, `audit_event`
- Canonical durable indexes: `posting_fact_by_prior_posting_id`, `posting_fact_by_effective_recorded_posting`, `journal_line_by_account_code`, `audit_event_by_recorded_at`, `posting_fact_one_reversal_per_target`
- There is no schema version table.
- There are no migration files.
- The current public line rejects non-matching book formats instead of upgrading them in place.
