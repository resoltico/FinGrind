---
afad: "5.0.1"
version: "0.64.0"
domain: SQLITE_SCHEMA_CORE_FINANCING
updated: "2026-09-01"
---

# SQLite Schema: Financing Lifecycle

**Purpose**: Immutable borrowing arrangements plus principal and interest applications.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: `financing_arrangement`, `financing_application`, compensating reversal links, and lifecycle validation/append-only triggers.

```sql
create table if not exists financing_arrangement (
    financing_arrangement_id text primary key check (
        length(financing_arrangement_id) between 1 and 120
        and financing_arrangement_id glob '[a-z0-9]*'
        and financing_arrangement_id not glob '*[^a-z0-9-]*'
        and financing_arrangement_id not like '-%'
        and financing_arrangement_id not like '%-'
        and financing_arrangement_id not like '%--%'
    ),
    origin_posting_id text not null unique references posting_fact (posting_id),
    originated_on text not null,
    principal_liability_account_code text not null references account (account_code),
    interest_payable_account_code text not null references account (account_code),
    currency_code text not null check (currency_code glob '[A-Z][A-Z][A-Z]'),
    original_principal_minor integer not null check (original_principal_minor > 0),
    check (principal_liability_account_code <> interest_payable_account_code)
) strict;

create table if not exists financing_application (
    application_posting_id text primary key references posting_fact (posting_id),
    financing_arrangement_id text not null references financing_arrangement (financing_arrangement_id),
    application_kind text not null check (application_kind in ('PRINCIPAL_REPAYMENT', 'INTEREST_ACCRUAL', 'INTEREST_PAYMENT')),
    effective_date text not null,
    currency_code text not null check (currency_code glob '[A-Z][A-Z][A-Z]'),
    amount_minor integer not null check (amount_minor > 0)
) strict;

create table if not exists financing_arrangement_reversal (
    reversal_posting_id text primary key references posting_fact (posting_id),
    financing_arrangement_id text not null unique references financing_arrangement (financing_arrangement_id)
) strict;

create table if not exists financing_application_reversal (
    reversal_posting_id text primary key references posting_fact (posting_id),
    application_posting_id text not null unique references financing_application (application_posting_id)
) strict;

create trigger if not exists financing_arrangement_validate_origin_on_insert
before insert on financing_arrangement
begin
    select raise(fail, 'financing_arrangement origin must be the matching typed borrowing posting.')
    where not exists (
        select 1 from posting_fact
        where posting_id = new.origin_posting_id and posting_origin_kind = 'FINANCING_BORROWING'
            and effective_date = new.originated_on and entry_amount_currency_code = new.currency_code
            and entry_amount_minor = new.original_principal_minor
    );
    select raise(fail, 'financing_arrangement requires liability accounts.')
    where not exists (select 1 from account where account_code = new.principal_liability_account_code and account_type = 'LIABILITY')
        or not exists (select 1 from account where account_code = new.interest_payable_account_code and account_type = 'LIABILITY' and financial_position_line_classification = 'CURRENT_LIABILITY');
end;

create trigger if not exists financing_application_validate_on_insert
before insert on financing_application
begin
    select raise(fail, 'financing_application must use the arrangement currency.')
    where exists (select 1 from financing_arrangement where financing_arrangement_id = new.financing_arrangement_id and currency_code <> new.currency_code);
    select raise(fail, 'financing_application must use the matching typed posting kind.')
    where not exists (
        select 1 from posting_fact
        where posting_id = new.application_posting_id and effective_date = new.effective_date
            and ((new.application_kind = 'PRINCIPAL_REPAYMENT' and posting_origin_kind = 'FINANCING_PRINCIPAL_REPAYMENT')
                or (new.application_kind = 'INTEREST_ACCRUAL' and posting_origin_kind = 'FINANCING_INTEREST_ACCRUAL')
                or (new.application_kind = 'INTEREST_PAYMENT' and posting_origin_kind = 'FINANCING_INTEREST_PAYMENT'))
    );
    select raise(fail, 'financing_application must match retained typed posting facts.')
    where not exists (
        select 1 from posting_fact
        where posting_id = new.application_posting_id
            and entry_amount_currency_code = new.currency_code
            and entry_amount_minor = new.amount_minor
    );
    select raise(fail, 'financing_application must not precede the arrangement lifecycle horizon.')
    where exists (select 1 from financing_arrangement where financing_arrangement_id = new.financing_arrangement_id and new.effective_date < originated_on)
        or exists (select 1 from financing_application where financing_arrangement_id = new.financing_arrangement_id and effective_date > new.effective_date);
    select raise(fail, 'financing principal repayment must not exceed principal outstanding.')
    where new.application_kind = 'PRINCIPAL_REPAYMENT' and exists (
        select 1 from financing_arrangement where financing_arrangement_id = new.financing_arrangement_id and new.amount_minor + coalesce((
            select sum(amount_minor) from financing_application
            where financing_arrangement_id = new.financing_arrangement_id
                and application_kind = 'PRINCIPAL_REPAYMENT'
                and not exists (
                    select 1 from financing_application_reversal reversal
                    where reversal.application_posting_id = financing_application.application_posting_id
                )
        ), 0) > original_principal_minor
    );
    select raise(fail, 'financing interest payment must not exceed accrued interest.')
    where new.application_kind = 'INTEREST_PAYMENT' and new.amount_minor > coalesce((
        select sum(case when application_kind = 'INTEREST_ACCRUAL' then amount_minor else -amount_minor end)
        from financing_application
        where financing_arrangement_id = new.financing_arrangement_id
            and application_kind in ('INTEREST_ACCRUAL', 'INTEREST_PAYMENT')
            and not exists (
                select 1 from financing_application_reversal reversal
                where reversal.application_posting_id = financing_application.application_posting_id
            )
    ), 0);
end;

create trigger if not exists financing_arrangement_reversal_validate_on_insert
before insert on financing_arrangement_reversal
begin
    select raise(fail, 'financing arrangement reversal must negate its borrowing posting.')
    where not exists (
        select 1
        from financing_arrangement arrangement
        inner join posting_fact reversal on reversal.posting_id = new.reversal_posting_id
        where arrangement.financing_arrangement_id = new.financing_arrangement_id
            and reversal.posting_origin_kind = 'REVERSAL'
            and reversal.prior_posting_id = arrangement.origin_posting_id
    );
    select raise(fail, 'financing borrowing cannot be reversed while active lifecycle applications remain.')
    where exists (
        select 1
        from financing_application application
        where application.financing_arrangement_id = new.financing_arrangement_id
            and not exists (
                select 1 from financing_application_reversal reversal
                where reversal.application_posting_id = application.application_posting_id
            )
    );
end;

create trigger if not exists financing_application_reversal_validate_on_insert
before insert on financing_application_reversal
begin
    select raise(fail, 'financing application reversal must negate its lifecycle posting.')
    where not exists (
        select 1
        from financing_application application
        inner join posting_fact reversal on reversal.posting_id = new.reversal_posting_id
        where application.application_posting_id = new.application_posting_id
            and reversal.posting_origin_kind = 'REVERSAL'
            and reversal.prior_posting_id = application.application_posting_id
    );
end;

create trigger if not exists financing_arrangement_reject_update before update on financing_arrangement begin select raise(fail, 'financing_arrangement rows are append-only.'); end;
create trigger if not exists financing_arrangement_reject_delete before delete on financing_arrangement begin select raise(fail, 'financing_arrangement rows are append-only.'); end;
create trigger if not exists financing_application_reject_update before update on financing_application begin select raise(fail, 'financing_application rows are append-only.'); end;
create trigger if not exists financing_application_reject_delete before delete on financing_application begin select raise(fail, 'financing_application rows are append-only.'); end;
create trigger if not exists financing_arrangement_reversal_reject_update before update on financing_arrangement_reversal begin select raise(fail, 'financing_arrangement_reversal rows are append-only.'); end;
create trigger if not exists financing_arrangement_reversal_reject_delete before delete on financing_arrangement_reversal begin select raise(fail, 'financing_arrangement_reversal rows are append-only.'); end;
create trigger if not exists financing_application_reversal_reject_update before update on financing_application_reversal begin select raise(fail, 'financing_application_reversal rows are append-only.'); end;
create trigger if not exists financing_application_reversal_reject_delete before delete on financing_application_reversal begin select raise(fail, 'financing_application_reversal rows are append-only.'); end;
```
