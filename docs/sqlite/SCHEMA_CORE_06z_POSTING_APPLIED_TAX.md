---
afad: "5.0.1"
version: "0.62.2"
domain: SQLITE_SCHEMA_CORE_POSTING_APPLIED_TAX
updated: "2026-08-09"
---

# SQLite Schema: Posting Applied Tax

**Purpose**: Per-posting resolved tax facts, posting-origin tax admissibility rules, and applied-tax append-only enforcement.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: `posting_applied_tax` and posting-applied-tax validation/append-only triggers.

```sql
create table if not exists posting_applied_tax (
    posting_id text primary key references posting_fact (posting_id),
    tax_registration_id text not null references tax_registration (tax_registration_id),
    tax_code text not null check (
        length(tax_code) between 1 and 120
        and tax_code glob '[a-z0-9]*'
        and tax_code not glob '*[^a-z0-9-]*'
        and tax_code not like '-%'
        and tax_code not like '%-'
        and tax_code not like '%--%'
    ),
    tax_code_name text not null check (length(trim(tax_code_name)) between 1 and 200),
    rate_parts_per_million_of_whole integer not null check (rate_parts_per_million_of_whole between 0 and 1000000),
    inclusion_mode text not null check (inclusion_mode in ('INCLUSIVE', 'EXCLUSIVE')),
    application_kind text not null check (application_kind in ('OUTPUT_SALE', 'INPUT_EXPENSE_RECOVERABLE', 'INPUT_EXPENSE_NONRECOVERABLE')),
    currency_code text not null check (
        length(currency_code) = 3
        and currency_code glob '[A-Z][A-Z][A-Z]'
    ),
    taxable_amount_minor integer not null check (taxable_amount_minor >= 0),
    tax_amount_minor integer not null check (tax_amount_minor >= 0),
    gross_amount_minor integer not null check (gross_amount_minor >= 0),
    tax_account_code text references account (account_code),
    check (gross_amount_minor = taxable_amount_minor + tax_amount_minor),
    check (
        (
            application_kind in ('OUTPUT_SALE', 'INPUT_EXPENSE_RECOVERABLE')
            and tax_account_code is not null
        )
        or (
            application_kind = 'INPUT_EXPENSE_NONRECOVERABLE'
            and tax_account_code is null
        )
    )
) strict;

create trigger if not exists posting_applied_tax_validate_origin_on_insert
before insert on posting_applied_tax
begin
    select raise(fail, 'posting_applied_tax requires sale, purchase, capitalization, or expense posting origin.')
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.posting_origin_kind not in (
                'SALE_SETTLED',
                'SALE_ON_CREDIT',
                'PURCHASE_SETTLED',
                'PURCHASE_ON_CREDIT',
                'INVENTORY_CAPITALIZATION_SETTLED',
                'INVENTORY_CAPITALIZATION_ON_CREDIT',
                'EXPENSE_SETTLED',
                'EXPENSE_ON_CREDIT'
            )
    );
    select raise(fail, 'sale tax application must use OUTPUT_SALE.')
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.posting_origin_kind in ('SALE_SETTLED', 'SALE_ON_CREDIT')
            and new.application_kind <> 'OUTPUT_SALE'
    );
    select raise(fail, 'input tax application cannot use OUTPUT_SALE.')
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.posting_origin_kind in (
                'EXPENSE_SETTLED',
                'EXPENSE_ON_CREDIT',
                'PURCHASE_SETTLED',
                'PURCHASE_ON_CREDIT',
                'INVENTORY_CAPITALIZATION_SETTLED',
                'INVENTORY_CAPITALIZATION_ON_CREDIT'
            )
            and new.application_kind = 'OUTPUT_SALE'
    );
end;

create trigger if not exists posting_applied_tax_reject_update
before update on posting_applied_tax
begin
    select raise(fail, 'posting_applied_tax rows are append-only.');
end;

create trigger if not exists posting_applied_tax_reject_delete
before delete on posting_applied_tax
begin
    select raise(fail, 'posting_applied_tax rows are append-only.');
end;
```
