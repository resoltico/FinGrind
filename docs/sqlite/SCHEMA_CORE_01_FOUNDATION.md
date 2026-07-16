---
afad: "4.0"
version: "0.61.0"
domain: SQLITE_SCHEMA_CORE_FOUNDATION
updated: "2026-07-16"
---

# SQLite Schema: Foundation

**Purpose**: Application id, format version, book metadata, and book identity bootstrap.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: `pragma application_id`, `pragma user_version`, `book_meta`, and `book_identity`.

```sql
pragma application_id = 1179079236;
pragma user_version = 46;

create table if not exists book_meta (
    meta_key text primary key check (
        meta_key in ('initialized_at', 'schema_fingerprint_sha256')
    ),
    value text not null check (
        (
            meta_key = 'initialized_at'
            and (
                value glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]Z'
                or (
                    substr(value, 1, 19) glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]'
                    and substr(value, 20, 1) = '.'
                    and substr(value, length(value), 1) = 'Z'
                    and (
                        (length(value) = 24 and substr(value, 21, 3) not glob '*[^0-9]*')
                        or (length(value) = 27 and substr(value, 21, 6) not glob '*[^0-9]*')
                        or (length(value) = 30 and substr(value, 21, 9) not glob '*[^0-9]*')
                    )
                )
            )
            and substr(value, 6, 2) between '01' and '12'
            and (
                (
                    substr(value, 6, 2) in ('01', '03', '05', '07', '08', '10', '12')
                    and substr(value, 9, 2) between '01' and '31'
                )
                or (
                    substr(value, 6, 2) in ('04', '06', '09', '11')
                    and substr(value, 9, 2) between '01' and '30'
                )
                or (
                    substr(value, 6, 2) = '02'
                    and (
                        substr(value, 9, 2) between '01' and '28'
                        or (
                            substr(value, 9, 2) = '29'
                            and (
                                cast(substr(value, 1, 4) as integer) % 400 = 0
                                or (
                                    cast(substr(value, 1, 4) as integer) % 4 = 0
                                    and cast(substr(value, 1, 4) as integer) % 100 <> 0
                                )
                            )
                        )
                    )
                )
            )
            and substr(value, 12, 2) between '00' and '23'
            and substr(value, 15, 2) between '00' and '59'
            and substr(value, 18, 2) between '00' and '59'
        )
        or (
            meta_key = 'schema_fingerprint_sha256'
            and length(value) = 64
            and value glob '[0-9a-f]*'
            and value not glob '*[^0-9a-f]*'
        )
    )
) strict;

create table if not exists book_identity (
    singleton_id integer primary key check (singleton_id = 1),
    entity_name text not null check (length(trim(entity_name)) > 0),
    accounting_kernel_profile text not null check (
        length(accounting_kernel_profile) between 1 and 120
        and accounting_kernel_profile not glob '*[^a-z0-9-]*'
        and accounting_kernel_profile not like '-%'
        and accounting_kernel_profile not like '%-'
        and accounting_kernel_profile not like '%--%'
    ),
    accounting_basis text not null check (
        accounting_basis in ('CASH', 'ACCRUAL')
    ),
    accounting_framework_position text not null check (
        accounting_framework_position in ('NON_STATUTORY_INTERNAL_MANAGEMENT')
    ),
    entity_form text not null check (
        entity_form in ('OWNER_MANAGED_SINGLE_ENTITY')
    ),
    book_template_id text not null check (
        book_template_id in ('OWNER_MANAGED_SERVICE', 'OWNER_MANAGED_TRADING')
    ),
    costing_doctrine text check (
        costing_doctrine is null
        or costing_doctrine in ('WEIGHTED_AVERAGE')
    ),
    functional_currency_code text not null check (
        length(functional_currency_code) = 3
        and functional_currency_code glob '[A-Z][A-Z][A-Z]'
    ),
    fiscal_year_start_month integer not null check (
        fiscal_year_start_month between 1 and 12
    ),
    fiscal_year_start_day integer not null check (
        fiscal_year_start_day between 1 and 31
    ),
    check (
        (
            fiscal_year_start_month in (1, 3, 5, 7, 8, 10, 12)
            and fiscal_year_start_day between 1 and 31
        )
        or
        (
            fiscal_year_start_month in (4, 6, 9, 11)
            and fiscal_year_start_day between 1 and 30
        )
        or
        (
            fiscal_year_start_month = 2
            and fiscal_year_start_day between 1 and 29
        )
    ),
    check (
        (
            book_template_id = 'OWNER_MANAGED_SERVICE'
            and costing_doctrine is null
        )
        or (
            book_template_id = 'OWNER_MANAGED_TRADING'
            and coalesce(costing_doctrine, '') = 'WEIGHTED_AVERAGE'
        )
    )
) strict;
```
