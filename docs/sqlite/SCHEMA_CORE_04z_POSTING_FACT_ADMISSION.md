---
afad: "5.0.1"
version: "0.61.0"
domain: SQLITE_SCHEMA_CORE_POSTING_FACT_ADMISSION
updated: "2026-07-17"
---

# SQLite Schema: Posting Fact Admission

**Purpose**: Posting effective-date, close-provenance, and opening-window admission gates.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: Posting-fact admission triggers.

```sql
create trigger if not exists posting_fact_validate_opening_balance_window_on_insert
before insert on posting_fact
when new.posting_kind = 'OPENING_BALANCE'
begin
    select raise(fail, 'opening-balance postings must be committed before all other postings.')
    where exists (
        select 1
        from posting_fact
    );
end;

create trigger if not exists posting_fact_validate_closed_period_on_insert
before insert on posting_fact
when new.posting_kind not in ('INTERIM_RESULT_SWEEP', 'FISCAL_YEAR_CLOSE')
begin
    select raise(fail, 'posting effective date is already closed.')
    where exists (
        select 1
        from interim_result_sweep
        where interim_result_sweep.effective_date_to >= new.effective_date
    );
end;

create trigger if not exists posting_fact_validate_generated_close_provenance_on_insert
before insert on posting_fact
when new.posting_kind in ('INTERIM_RESULT_SWEEP', 'FISCAL_YEAR_CLOSE')
begin
    select raise(fail, 'generated close postings must be system-authored.')
    where new.actor_type <> 'SYSTEM';
    select raise(fail, 'generated close postings must use the system source channel.')
    where new.source_channel <> 'SYSTEM';
    select raise(fail, 'generated close postings cannot reverse earlier postings.')
    where new.prior_posting_id is not null or new.reason is not null;
end;
```
