---
afad: "5.0.1"
version: "0.62.0"
domain: SQLITE_SCHEMA_CORE_TAX_REGISTRATION
updated: "2026-07-30"
---

# SQLite Schema: Tax Registration

**Purpose**: Declared tax registrations, tax-code doctrine, registration-account validation, and tax-registration append-only rules.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: `tax_registration`, `tax_registration_code`, and tax-registration validation/append-only triggers.

```sql
create table if not exists tax_registration (
    tax_registration_id text primary key check (
        length(tax_registration_id) between 1 and 120
        and tax_registration_id glob '[a-z0-9]*'
        and tax_registration_id not glob '*[^a-z0-9-]*'
        and tax_registration_id not like '-%'
        and tax_registration_id not like '%-'
        and tax_registration_id not like '%--%'
    ),
    tax_registration_name text not null check (length(trim(tax_registration_name)) between 1 and 200),
    jurisdiction text not null check (length(trim(jurisdiction)) between 1 and 120),
    registration_number text check (registration_number is null or length(trim(registration_number)) between 1 and 120),
    payable_account_code text not null references account (account_code),
    recoverable_account_code text not null references account (account_code),
    obligation_frequency text not null check (obligation_frequency in ('MONTHLY', 'QUARTERLY', 'ANNUAL')),
    due_days_after_period_end integer not null check (due_days_after_period_end between 0 and 366),
    declared_at text not null check (
        (
            declared_at glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]Z'
            or (
                substr(declared_at, 1, 19) glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]'
                and substr(declared_at, 20, 1) = '.'
                and substr(declared_at, length(declared_at), 1) = 'Z'
                and (
                    (length(declared_at) = 24 and substr(declared_at, 21, 3) not glob '*[^0-9]*')
                    or (length(declared_at) = 27 and substr(declared_at, 21, 6) not glob '*[^0-9]*')
                    or (length(declared_at) = 30 and substr(declared_at, 21, 9) not glob '*[^0-9]*')
                )
            )
        )
        and substr(declared_at, 6, 2) between '01' and '12'
        and (
            (
                substr(declared_at, 6, 2) in ('01', '03', '05', '07', '08', '10', '12')
                and substr(declared_at, 9, 2) between '01' and '31'
            )
            or (
                substr(declared_at, 6, 2) in ('04', '06', '09', '11')
                and substr(declared_at, 9, 2) between '01' and '30'
            )
            or (
                substr(declared_at, 6, 2) = '02'
                and (
                    substr(declared_at, 9, 2) between '01' and '28'
                    or (
                        substr(declared_at, 9, 2) = '29'
                        and (
                            cast(substr(declared_at, 1, 4) as integer) % 400 = 0
                            or (
                                cast(substr(declared_at, 1, 4) as integer) % 4 = 0
                                and cast(substr(declared_at, 1, 4) as integer) % 100 <> 0
                            )
                        )
                    )
                )
            )
        )
        and substr(declared_at, 12, 2) between '00' and '23'
        and substr(declared_at, 15, 2) between '00' and '59'
        and substr(declared_at, 18, 2) between '00' and '59'
    )
) strict;

create trigger if not exists tax_registration_validate_accounts_on_insert
before insert on tax_registration
begin
    select raise(fail, 'tax payable account must be active liability current-liability postable.')
    where exists (
        select 1
        from account
        where
            account.account_code = new.payable_account_code
            and (
                account.active = 0
                or account.account_type <> 'LIABILITY'
                or account.account_node_kind <> 'POSTABLE'
                or account.financial_position_line_classification <> 'CURRENT_LIABILITY'
            )
    );
    select raise(fail, 'tax recoverable account must be active asset current-asset non-cash postable.')
    where exists (
        select 1
        from account
        where
            account.account_code = new.recoverable_account_code
            and (
                account.active = 0
                or account.account_type <> 'ASSET'
                or account.account_node_kind <> 'POSTABLE'
                or account.financial_position_line_classification <> 'CURRENT_ASSET'
                or account.cash_flow_asset_classification <> 'NON_CASH'
            )
    );
end;

create trigger if not exists tax_registration_validate_accounts_on_update
before update on tax_registration
begin
    select raise(fail, 'tax registration id and declared_at are immutable.')
    where
        old.tax_registration_id <> new.tax_registration_id
        or old.declared_at <> new.declared_at;
    select raise(fail, 'tax payable account must be active liability current-liability postable.')
    where exists (
        select 1
        from account
        where
            account.account_code = new.payable_account_code
            and (
                account.active = 0
                or account.account_type <> 'LIABILITY'
                or account.account_node_kind <> 'POSTABLE'
                or account.financial_position_line_classification <> 'CURRENT_LIABILITY'
            )
    );
    select raise(fail, 'tax recoverable account must be active asset current-asset non-cash postable.')
    where exists (
        select 1
        from account
        where
            account.account_code = new.recoverable_account_code
            and (
                account.active = 0
                or account.account_type <> 'ASSET'
                or account.account_node_kind <> 'POSTABLE'
                or account.financial_position_line_classification <> 'CURRENT_ASSET'
                or account.cash_flow_asset_classification <> 'NON_CASH'
            )
    );
end;

create trigger if not exists tax_registration_reject_delete
before delete on tax_registration
begin
    select raise(fail, 'tax_registration rows are append-only.');
end;

create table if not exists tax_registration_code (
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
    primary key (tax_registration_id, tax_code)
) strict;
```
