---
afad: "5.0.1"
version: "0.63.0"
domain: SQLITE_SCHEMA_CORE_FISCAL_YEAR_CLOSE_TABLE
updated: "2026-08-20"
---

# SQLite Schema: Fiscal Year Close Table

**Purpose**: Year-close range facts and required target-account pointers.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: `fiscal_year_close`.

```sql
create table if not exists fiscal_year_close (
    fiscal_year_close_order integer primary key,
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
    capital_account_code text not null references account (account_code),
    result_holding_account_code text not null references account (account_code),
    retained_accumulated_account_code text not null references account (account_code),
    closed_at text not null check (
        (
            closed_at glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]Z'
            or (
                substr(closed_at, 1, 19) glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]'
                and substr(closed_at, 20, 1) = '.'
                and substr(closed_at, length(closed_at), 1) = 'Z'
                and (
                    (length(closed_at) = 24 and substr(closed_at, 21, 3) not glob '*[^0-9]*')
                    or (length(closed_at) = 27 and substr(closed_at, 21, 6) not glob '*[^0-9]*')
                    or (length(closed_at) = 30 and substr(closed_at, 21, 9) not glob '*[^0-9]*')
                )
            )
        )
        and substr(closed_at, 6, 2) between '01' and '12'
        and (
            (
                substr(closed_at, 6, 2) in ('01', '03', '05', '07', '08', '10', '12')
                and substr(closed_at, 9, 2) between '01' and '31'
            )
            or (
                substr(closed_at, 6, 2) in ('04', '06', '09', '11')
                and substr(closed_at, 9, 2) between '01' and '30'
            )
            or (
                substr(closed_at, 6, 2) = '02'
                and (
                    substr(closed_at, 9, 2) between '01' and '28'
                    or (
                        substr(closed_at, 9, 2) = '29'
                        and (
                            cast(substr(closed_at, 1, 4) as integer) % 400 = 0
                            or (
                                cast(substr(closed_at, 1, 4) as integer) % 4 = 0
                                and cast(substr(closed_at, 1, 4) as integer) % 100 <> 0
                            )
                        )
                    )
                )
            )
        )
        and substr(closed_at, 12, 2) between '00' and '23'
        and substr(closed_at, 15, 2) between '00' and '59'
        and substr(closed_at, 18, 2) between '00' and '59'
    ),
    check (effective_date_from <= effective_date_to)
) strict;
```
