---
afad: "5.0.1"
version: "0.62.0"
domain: SQLITE_SCHEMA_CORE_JOURNAL_LINES
updated: "2026-07-30"
---

# SQLite Schema: Journal Lines

**Purpose**: Committed journal-line storage and journal-line-side admission gates.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: `journal_line` and journal-line validation triggers.

```sql
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
    foreign key (posting_id) references posting_fact (posting_id),
    foreign key (account_code) references account (account_code)
) strict;

create trigger if not exists journal_line_validate_active_account_on_insert
before insert on journal_line
begin
    select raise(fail, 'journal-line accounts must be active.')
    where exists (
        select 1
        from account
        where
            account.account_code = new.account_code
            and account.active = 0
    )
    and not exists (
        select 1
        from posting_fact
        where posting_fact.posting_id = new.posting_id
            and posting_fact.prior_posting_id is not null
    );
    select raise(fail, 'journal-line accounts must be postable.')
    where exists (
        select 1
        from account
        where
            account.account_code = new.account_code
            and account.account_node_kind <> 'POSTABLE'
    );
end;

create trigger if not exists journal_line_validate_functional_currency_on_insert
before insert on journal_line
begin
    select raise(fail, 'journal-line currency must match the book functional currency.')
    where exists (
        select 1
        from book_identity
        where
            book_identity.singleton_id = 1
            and new.currency_code <> book_identity.functional_currency_code
    );
end;

create trigger if not exists journal_line_validate_opening_balance_account_type_on_insert
before insert on journal_line
begin
    select raise(fail, 'opening-balance postings may touch only asset, liability, or equity accounts.')
    where exists (
        select 1
        from posting_fact
        inner join account on account.account_code = new.account_code
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.posting_kind = 'OPENING_BALANCE'
            and account.account_type not in ('ASSET', 'LIABILITY', 'EQUITY')
    );
end;
```
