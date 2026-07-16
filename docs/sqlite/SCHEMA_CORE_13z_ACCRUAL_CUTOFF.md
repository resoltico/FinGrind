---
afad: "4.0"
version: "0.61.0"
domain: SQLITE_SCHEMA_CORE_ACCRUAL_CUTOFF
updated: "2026-07-16"
---

# SQLite Schema: Accrual Cut-off Aggregate

**Purpose**: Durable origins for accrual-basis prepayments, deferred revenue, and accrued expenses.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: `accrual_cutoff` and origin-account and origin-posting validation triggers.

```sql
create table if not exists accrual_cutoff (
    accrual_cutoff_id text primary key check (
        length(accrual_cutoff_id) between 1 and 120
        and accrual_cutoff_id glob '[a-z0-9]*'
        and accrual_cutoff_id not glob '*[^a-z0-9-]*'
        and accrual_cutoff_id not like '-%'
        and accrual_cutoff_id not like '%-'
        and accrual_cutoff_id not like '%--%'
    ),
    kind text not null check (kind in ('PREPAYMENT', 'DEFERRED_REVENUE', 'ACCRUED_EXPENSE')),
    origin_posting_id text not null unique references posting_fact (posting_id),
    originated_on text not null,
    cutoff_account_code text not null references account (account_code),
    recognition_account_code text not null references account (account_code),
    amount_currency_code text not null check (amount_currency_code glob '[A-Z][A-Z][A-Z]'),
    original_amount_minor integer not null check (original_amount_minor > 0),
    recognition_start_date text,
    recognition_end_date text,
    check (
        (
            kind in ('PREPAYMENT', 'DEFERRED_REVENUE')
            and recognition_start_date is not null
            and recognition_end_date is not null
            and recognition_start_date <= recognition_end_date
            and originated_on <= recognition_start_date
        )
        or (
            kind = 'ACCRUED_EXPENSE'
            and recognition_start_date is null
            and recognition_end_date is null
        )
    )
) strict;

create trigger if not exists accrual_cutoff_validate_origin_on_insert
before insert on accrual_cutoff
begin
    select raise(fail, 'accrual_cutoff origin posting must be the matching typed cut-off event.')
    where not exists (
        select 1
        from posting_fact
        where posting_fact.posting_id = new.origin_posting_id
            and posting_fact.effective_date = new.originated_on
            and posting_fact.entry_amount_currency_code = new.amount_currency_code
            and posting_fact.entry_amount_minor = new.original_amount_minor
            and (
                (
                    new.kind = 'PREPAYMENT'
                    and posting_fact.posting_origin_kind = 'PREPAYMENT'
                    and posting_fact.entry_primary_debit_account_code = new.cutoff_account_code
                )
                or (
                    new.kind = 'DEFERRED_REVENUE'
                    and posting_fact.posting_origin_kind = 'DEFERRED_REVENUE'
                    and posting_fact.entry_primary_credit_account_code = new.cutoff_account_code
                )
                or (
                    new.kind = 'ACCRUED_EXPENSE'
                    and posting_fact.posting_origin_kind = 'ACCRUED_EXPENSE'
                    and posting_fact.entry_primary_debit_account_code = new.recognition_account_code
                    and posting_fact.entry_primary_credit_account_code = new.cutoff_account_code
                )
            )
    );
    select raise(fail, 'prepayment cut-offs require a prepayment asset and expense account.')
    where new.kind = 'PREPAYMENT'
        and (
            not exists (
                select 1 from account
                where account_code = new.cutoff_account_code
                    and account_type = 'ASSET'
                    and financial_position_line_classification = 'PREPAID_EXPENSE'
            )
            or not exists (
                select 1 from account
                where account_code = new.recognition_account_code
                    and account_type = 'EXPENSE'
            )
        );
    select raise(fail, 'prepayment cut-offs require a declared cash credit account.')
    where new.kind = 'PREPAYMENT'
        and not exists (
            select 1
            from posting_fact
            inner join account on account.account_code = posting_fact.entry_primary_credit_account_code
            where posting_fact.posting_id = new.origin_posting_id
                and account.account_type = 'ASSET'
                and account.cash_flow_asset_classification = 'CASH_AND_CASH_EQUIVALENT'
        );
    select raise(fail, 'deferred-revenue cut-offs require a deferred-revenue liability and revenue account.')
    where new.kind = 'DEFERRED_REVENUE'
        and (
            not exists (
                select 1 from account
                where account_code = new.cutoff_account_code
                    and account_type = 'LIABILITY'
                    and financial_position_line_classification = 'DEFERRED_REVENUE'
            )
            or not exists (
                select 1 from account
                where account_code = new.recognition_account_code
                    and account_type = 'REVENUE'
            )
        );
    select raise(fail, 'deferred-revenue cut-offs require a declared cash debit account.')
    where new.kind = 'DEFERRED_REVENUE'
        and not exists (
            select 1
            from posting_fact
            inner join account on account.account_code = posting_fact.entry_primary_debit_account_code
            where posting_fact.posting_id = new.origin_posting_id
                and account.account_type = 'ASSET'
                and account.cash_flow_asset_classification = 'CASH_AND_CASH_EQUIVALENT'
        );
    select raise(fail, 'accrued-expense cut-offs require an accrued-expense liability and expense account.')
    where new.kind = 'ACCRUED_EXPENSE'
        and (
            not exists (
                select 1 from account
                where account_code = new.cutoff_account_code
                    and account_type = 'LIABILITY'
                    and financial_position_line_classification = 'ACCRUED_EXPENSE'
            )
            or not exists (
                select 1 from account
                where account_code = new.recognition_account_code
                    and account_type = 'EXPENSE'
            )
        );
end;
```
