---
afad: "5.0.1"
version: "0.62.0"
domain: SQLITE_SCHEMA_CORE_INTERIM_RESULT_SWEEP_LINKS
updated: "2026-07-30"
---

# SQLite Schema: Interim Result Sweep Links

**Purpose**: Per-currency sweep totals and generated sweep-posting linkage.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: `interim_result_sweep_total`, `interim_result_sweep_posting`, and posting-link validation triggers.

```sql
create table if not exists interim_result_sweep_total (
    interim_result_sweep_order integer not null,
    currency_code text not null check (
        length(currency_code) = 3
        and currency_code glob '[A-Z][A-Z][A-Z]'
    ),
    debit_total_minor integer not null check (debit_total_minor >= 0),
    credit_total_minor integer not null check (credit_total_minor >= 0),
    primary key (interim_result_sweep_order, currency_code),
    foreign key (interim_result_sweep_order) references interim_result_sweep (interim_result_sweep_order)
) strict;

create table if not exists interim_result_sweep_posting (
    interim_result_sweep_order integer not null,
    posting_id text not null,
    primary key (interim_result_sweep_order, posting_id),
    foreign key (interim_result_sweep_order) references interim_result_sweep (interim_result_sweep_order),
    foreign key (posting_id) references posting_fact (posting_id)
) strict;

create trigger if not exists interim_result_sweep_posting_validate_interim_result_sweep_posting_on_insert
before insert on interim_result_sweep_posting
begin
    select raise(
        fail,
        'interim-result-sweep links must reference interim-result-sweep postings.'
    )
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.posting_kind <> 'INTERIM_RESULT_SWEEP'
    );
    select raise(fail, 'interim-result-sweep links must reference system-generated postings.')
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.source_channel <> 'SYSTEM'
    );
    select raise(
        fail,
        'interim-result-sweep posting effective date must match the swept-through date.'
    )
    where exists (
        select 1
        from interim_result_sweep
        inner join posting_fact on posting_fact.posting_id = new.posting_id
        where
            interim_result_sweep.interim_result_sweep_order = new.interim_result_sweep_order
            and posting_fact.effective_date <> interim_result_sweep.effective_date_to
    );
end;
```
