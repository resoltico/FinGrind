---
afad: "5.0.1"
version: "0.62.0"
domain: SQLITE_SCHEMA_CORE_FIXED_ASSETS
updated: "2026-07-30"
---

# SQLite Schema: Fixed-Asset Lifecycle

**Purpose**: Immutable fixed-asset capitalizations plus depreciation and disposal applications.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: `fixed_asset`, `fixed_asset_application`, compensating reversal links, and lifecycle validation/append-only triggers.

```sql
create table if not exists fixed_asset (
    fixed_asset_id text primary key check (
        length(fixed_asset_id) between 1 and 120
        and fixed_asset_id glob '[a-z0-9]*'
        and fixed_asset_id not glob '*[^a-z0-9-]*'
        and fixed_asset_id not like '-%'
        and fixed_asset_id not like '%-'
        and fixed_asset_id not like '%--%'
    ),
    origin_posting_id text not null unique references posting_fact (posting_id),
    capitalized_on text not null,
    asset_account_code text not null references account (account_code),
    accumulated_depreciation_account_code text not null references account (account_code),
    depreciation_expense_account_code text not null references account (account_code),
    disposal_gain_account_code text not null references account (account_code),
    disposal_loss_account_code text not null references account (account_code),
    currency_code text not null check (currency_code glob '[A-Z][A-Z][A-Z]'),
    cost_minor integer not null check (cost_minor > 0),
    residual_value_minor integer not null check (residual_value_minor >= 0 and residual_value_minor < cost_minor),
    in_service_date text not null check (in_service_date >= capitalized_on),
    useful_life_months integer not null check (useful_life_months between 1 and 1200),
    check (
        asset_account_code <> accumulated_depreciation_account_code
        and asset_account_code <> depreciation_expense_account_code
        and asset_account_code <> disposal_gain_account_code
        and asset_account_code <> disposal_loss_account_code
        and accumulated_depreciation_account_code <> depreciation_expense_account_code
        and accumulated_depreciation_account_code <> disposal_gain_account_code
        and accumulated_depreciation_account_code <> disposal_loss_account_code
        and depreciation_expense_account_code <> disposal_gain_account_code
        and depreciation_expense_account_code <> disposal_loss_account_code
        and disposal_gain_account_code <> disposal_loss_account_code
    )
) strict;

create table if not exists fixed_asset_application (
    application_posting_id text primary key references posting_fact (posting_id),
    fixed_asset_id text not null references fixed_asset (fixed_asset_id),
    application_kind text not null check (application_kind in ('DEPRECIATION', 'DISPOSAL')),
    effective_date text not null,
    currency_code text not null check (currency_code glob '[A-Z][A-Z][A-Z]'),
    amount_minor integer not null check (amount_minor >= 0)
) strict;

create trigger if not exists fixed_asset_validate_origin_on_insert
before insert on fixed_asset
begin
    select raise(fail, 'fixed_asset origin must be the matching typed capitalization posting.')
    where not exists (
        select 1 from posting_fact
        where posting_id = new.origin_posting_id
            and posting_origin_kind = 'FIXED_ASSET_CAPITALIZATION'
            and effective_date = new.capitalized_on
            and entry_amount_currency_code = new.currency_code
            and entry_amount_minor = new.cost_minor
    );
    select raise(fail, 'fixed_asset accounts must use the required asset, expense, revenue, expense taxonomy.')
    where
        not exists (select 1 from account where account_code = new.asset_account_code and account_type = 'ASSET' and financial_position_line_classification = 'NONCURRENT_ASSET' and cash_flow_asset_classification = 'NON_CASH')
        or not exists (select 1 from account where account_code = new.accumulated_depreciation_account_code and account_type = 'ASSET' and financial_position_line_classification = 'NONCURRENT_ASSET' and cash_flow_asset_classification = 'NON_CASH')
        or not exists (select 1 from account where account_code = new.depreciation_expense_account_code and account_type = 'EXPENSE')
        or not exists (select 1 from account where account_code = new.disposal_gain_account_code and account_type = 'REVENUE')
        or not exists (select 1 from account where account_code = new.disposal_loss_account_code and account_type = 'EXPENSE');
end;

create trigger if not exists fixed_asset_application_validate_on_insert
before insert on fixed_asset_application
begin
    select raise(fail, 'fixed_asset_application must use the fixed asset currency.')
    where exists (select 1 from fixed_asset where fixed_asset_id = new.fixed_asset_id and currency_code <> new.currency_code);
    select raise(fail, 'fixed_asset_application must use the matching typed posting kind.')
    where not exists (
        select 1 from posting_fact
        where posting_id = new.application_posting_id and effective_date = new.effective_date
            and ((new.application_kind = 'DEPRECIATION' and posting_origin_kind = 'FIXED_ASSET_DEPRECIATION')
                or (new.application_kind = 'DISPOSAL' and posting_origin_kind = 'FIXED_ASSET_DISPOSAL'))
    );
    select raise(fail, 'fixed_asset depreciation must match retained typed posting facts.')
    where new.application_kind = 'DEPRECIATION' and not exists (
        select 1 from posting_fact
        where posting_id = new.application_posting_id
            and entry_amount_currency_code = new.currency_code
            and entry_amount_minor = new.amount_minor
    );
    select raise(fail, 'fixed_asset_application must not precede the asset lifecycle horizon.')
    where exists (select 1 from fixed_asset where fixed_asset_id = new.fixed_asset_id and new.effective_date < capitalized_on)
        or exists (select 1 from fixed_asset_application where fixed_asset_id = new.fixed_asset_id and effective_date > new.effective_date);
    select raise(fail, 'fixed_asset depreciation must not exceed depreciable cost.')
    where new.application_kind = 'DEPRECIATION' and exists (
        select 1 from fixed_asset where fixed_asset_id = new.fixed_asset_id and new.amount_minor + coalesce((
            select sum(amount_minor) from fixed_asset_application
            where fixed_asset_id = new.fixed_asset_id and application_kind = 'DEPRECIATION'
                and not exists (
                    select 1 from fixed_asset_application_reversal reversal
                    where reversal.application_posting_id = fixed_asset_application.application_posting_id
                )
        ), 0) > cost_minor - residual_value_minor
    );
    select raise(fail, 'fixed_asset accepts at most one disposal.')
    where new.application_kind = 'DISPOSAL' and exists (
        select 1 from fixed_asset_application
        where fixed_asset_id = new.fixed_asset_id and application_kind = 'DISPOSAL'
            and not exists (
                select 1 from fixed_asset_application_reversal reversal
                where reversal.application_posting_id = fixed_asset_application.application_posting_id
            )
    );
    select raise(fail, 'fixed_asset disposal must use the immutable carrying amount.')
    where new.application_kind = 'DISPOSAL' and not exists (
        select 1
        from fixed_asset asset
        where asset.fixed_asset_id = new.fixed_asset_id
            and new.amount_minor = asset.cost_minor - coalesce((
                select sum(application.amount_minor)
                from fixed_asset_application application
                where application.fixed_asset_id = asset.fixed_asset_id
                    and application.application_kind = 'DEPRECIATION'
                    and not exists (
                        select 1 from fixed_asset_application_reversal reversal
                        where reversal.application_posting_id = application.application_posting_id
                    )
            ), 0)
    );
end;

create trigger if not exists fixed_asset_reject_update before update on fixed_asset begin select raise(fail, 'fixed_asset rows are append-only.'); end;
create trigger if not exists fixed_asset_reject_delete before delete on fixed_asset begin select raise(fail, 'fixed_asset rows are append-only.'); end;
create trigger if not exists fixed_asset_application_reject_update before update on fixed_asset_application begin select raise(fail, 'fixed_asset_application rows are append-only.'); end;
create trigger if not exists fixed_asset_application_reject_delete before delete on fixed_asset_application begin select raise(fail, 'fixed_asset_application rows are append-only.'); end;

create table if not exists fixed_asset_reversal (
    reversal_posting_id text primary key references posting_fact (posting_id),
    fixed_asset_id text not null unique references fixed_asset (fixed_asset_id)
) strict;

create table if not exists fixed_asset_application_reversal (
    reversal_posting_id text primary key references posting_fact (posting_id),
    application_posting_id text not null unique references fixed_asset_application (application_posting_id)
) strict;

create trigger if not exists fixed_asset_reversal_validate_on_insert
before insert on fixed_asset_reversal
begin
    select raise(fail, 'fixed_asset reversal must negate its capitalization posting.')
    where not exists (
        select 1 from fixed_asset
        inner join posting_fact on posting_fact.posting_id = new.reversal_posting_id
        where fixed_asset.fixed_asset_id = new.fixed_asset_id
            and posting_fact.posting_origin_kind = 'REVERSAL'
            and posting_fact.prior_posting_id = fixed_asset.origin_posting_id
    );
    select raise(fail, 'fixed_asset capitalization cannot be reversed while active lifecycle applications remain.')
    where exists (
        select 1 from fixed_asset_application application
        where application.fixed_asset_id = new.fixed_asset_id
            and not exists (
                select 1 from fixed_asset_application_reversal reversal
                where reversal.application_posting_id = application.application_posting_id
            )
    );
end;

create trigger if not exists fixed_asset_application_reversal_validate_on_insert
before insert on fixed_asset_application_reversal
begin
    select raise(fail, 'fixed_asset application reversal must negate its application posting.')
    where not exists (
        select 1 from fixed_asset_application application
        inner join posting_fact on posting_fact.posting_id = new.reversal_posting_id
        where application.application_posting_id = new.application_posting_id
            and posting_fact.posting_origin_kind = 'REVERSAL'
            and posting_fact.prior_posting_id = application.application_posting_id
    );
end;

create trigger if not exists fixed_asset_reversal_reject_update before update on fixed_asset_reversal begin select raise(fail, 'fixed_asset_reversal rows are append-only.'); end;
create trigger if not exists fixed_asset_reversal_reject_delete before delete on fixed_asset_reversal begin select raise(fail, 'fixed_asset_reversal rows are append-only.'); end;
create trigger if not exists fixed_asset_application_reversal_reject_update before update on fixed_asset_application_reversal begin select raise(fail, 'fixed_asset_application_reversal rows are append-only.'); end;
create trigger if not exists fixed_asset_application_reversal_reject_delete before delete on fixed_asset_application_reversal begin select raise(fail, 'fixed_asset_application_reversal rows are append-only.'); end;
```
