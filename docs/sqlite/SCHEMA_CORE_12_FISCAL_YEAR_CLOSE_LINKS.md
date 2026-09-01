---
afad: "5.0.1"
version: "0.64.0"
domain: SQLITE_SCHEMA_CORE_FISCAL_YEAR_CLOSE_LINKS
updated: "2026-09-01"
---

# SQLite Schema: Fiscal Year Close Links

**Purpose**: Generated fiscal-year-close posting linkage and posting-side invariants.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: `fiscal_year_close_posting` and posting-link validation triggers.

```sql
create table if not exists fiscal_year_close_posting (
    fiscal_year_close_order integer not null,
    posting_id text not null,
    primary key (fiscal_year_close_order, posting_id),
    foreign key (fiscal_year_close_order) references fiscal_year_close (fiscal_year_close_order),
    foreign key (posting_id) references posting_fact (posting_id)
) strict;

create trigger if not exists fiscal_year_close_posting_validate_fiscal_year_close_posting_on_insert
before insert on fiscal_year_close_posting
begin
    select raise(
        fail,
        'fiscal-year-close links must reference fiscal-year-close postings.'
    )
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.posting_kind <> 'FISCAL_YEAR_CLOSE'
    );
    select raise(fail, 'fiscal-year-close links must reference system-generated postings.')
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.source_channel <> 'SYSTEM'
    );
    select raise(
        fail,
        'fiscal-year-close posting effective date must match the closed-through date.'
    )
    where exists (
        select 1
        from fiscal_year_close
        inner join posting_fact on posting_fact.posting_id = new.posting_id
        where
            fiscal_year_close.fiscal_year_close_order = new.fiscal_year_close_order
            and posting_fact.effective_date <> fiscal_year_close.effective_date_to
    );
end;
```
