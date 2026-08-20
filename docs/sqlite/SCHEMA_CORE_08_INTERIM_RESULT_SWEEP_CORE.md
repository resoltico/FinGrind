---
afad: "5.0.1"
version: "0.63.0"
domain: SQLITE_SCHEMA_CORE_INTERIM_RESULT_SWEEP_CORE
updated: "2026-08-20"
---

# SQLite Schema: Interim Result Sweep Core

**Purpose**: Sweep-range facts and target-account doctrine for contiguous interim closes.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: `interim_result_sweep` and interim-result-sweep range/target triggers.

```sql
create table if not exists interim_result_sweep (
    interim_result_sweep_order integer primary key,
    effective_date_from text not null check (
        effective_date_from glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'
        and substr(effective_date_from, 6, 2) between '01' and '12'
        and (
            (
                substr(effective_date_from, 6, 2) in ('01', '03', '05', '07', '08', '10', '12')
                and substr(effective_date_from, 9, 2) between '01' and '31'
            )
            or (
                substr(effective_date_from, 6, 2) in ('04', '06', '09', '11')
                and substr(effective_date_from, 9, 2) between '01' and '30'
            )
            or (
                substr(effective_date_from, 6, 2) = '02'
                and (
                    substr(effective_date_from, 9, 2) between '01' and '28'
                    or (
                        substr(effective_date_from, 9, 2) = '29'
                        and (
                            cast(substr(effective_date_from, 1, 4) as integer) % 400 = 0
                            or (
                                cast(substr(effective_date_from, 1, 4) as integer) % 4 = 0
                                and cast(substr(effective_date_from, 1, 4) as integer) % 100 <> 0
                            )
                        )
                    )
                )
            )
        )
    ),
    effective_date_to text not null check (
        effective_date_to glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'
        and substr(effective_date_to, 6, 2) between '01' and '12'
        and (
            (
                substr(effective_date_to, 6, 2) in ('01', '03', '05', '07', '08', '10', '12')
                and substr(effective_date_to, 9, 2) between '01' and '31'
            )
            or (
                substr(effective_date_to, 6, 2) in ('04', '06', '09', '11')
                and substr(effective_date_to, 9, 2) between '01' and '30'
            )
            or (
                substr(effective_date_to, 6, 2) = '02'
                and (
                    substr(effective_date_to, 9, 2) between '01' and '28'
                    or (
                        substr(effective_date_to, 9, 2) = '29'
                        and (
                            cast(substr(effective_date_to, 1, 4) as integer) % 400 = 0
                            or (
                                cast(substr(effective_date_to, 1, 4) as integer) % 4 = 0
                                and cast(substr(effective_date_to, 1, 4) as integer) % 100 <> 0
                            )
                        )
                    )
                )
            )
        )
    ),
    result_holding_account_code text not null references account (account_code),
    swept_at text not null check (
        (
            swept_at glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]Z'
            or (
                substr(swept_at, 1, 19) glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]'
                and substr(swept_at, 20, 1) = '.'
                and substr(swept_at, length(swept_at), 1) = 'Z'
                and (
                    (length(swept_at) = 24 and substr(swept_at, 21, 3) not glob '*[^0-9]*')
                    or (length(swept_at) = 27 and substr(swept_at, 21, 6) not glob '*[^0-9]*')
                    or (length(swept_at) = 30 and substr(swept_at, 21, 9) not glob '*[^0-9]*')
                )
            )
        )
        and substr(swept_at, 6, 2) between '01' and '12'
        and (
            (
                substr(swept_at, 6, 2) in ('01', '03', '05', '07', '08', '10', '12')
                and substr(swept_at, 9, 2) between '01' and '31'
            )
            or (
                substr(swept_at, 6, 2) in ('04', '06', '09', '11')
                and substr(swept_at, 9, 2) between '01' and '30'
            )
            or (
                substr(swept_at, 6, 2) = '02'
                and (
                    substr(swept_at, 9, 2) between '01' and '28'
                    or (
                        substr(swept_at, 9, 2) = '29'
                        and (
                            cast(substr(swept_at, 1, 4) as integer) % 400 = 0
                            or (
                                cast(substr(swept_at, 1, 4) as integer) % 4 = 0
                                and cast(substr(swept_at, 1, 4) as integer) % 100 <> 0
                            )
                        )
                    )
                )
            )
        )
        and substr(swept_at, 12, 2) between '00' and '23'
        and substr(swept_at, 15, 2) between '00' and '59'
        and substr(swept_at, 18, 2) between '00' and '59'
    ),
    check (effective_date_from <= effective_date_to),
    unique (effective_date_from, effective_date_to)
) strict;

create trigger if not exists interim_result_sweep_validate_result_holding_account_on_insert
before insert on interim_result_sweep
begin
    select raise(
        fail,
        'interim-result-sweep target must be one active RESULT_HOLDING equity account.'
    )
    where exists (
        select 1
        from account
        where
            account.account_code = new.result_holding_account_code
            and (
                account.account_type <> 'EQUITY'
                or account.active = 0
                or account.financial_position_line_classification <> 'RESULT_HOLDING'
            )
    );
end;

create trigger if not exists interim_result_sweep_validate_contiguous_horizon_on_insert
before insert on interim_result_sweep
when exists (select 1 from interim_result_sweep)
begin
    select raise(
        fail,
        'interim-result-sweep ranges must append contiguously from the prior swept-through date.'
    )
    where new.effective_date_from <> (
        select date(max(interim_result_sweep.effective_date_to), '+1 day')
        from interim_result_sweep
    );
end;
```
