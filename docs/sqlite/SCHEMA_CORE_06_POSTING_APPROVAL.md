---
afad: "5.0.1"
version: "0.61.0"
domain: SQLITE_SCHEMA_CORE_POSTING_APPROVAL
updated: "2026-07-17"
---

# SQLite Schema: Posting Approvals

**Purpose**: Durable approval references for committed postings.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: `posting_approval`.

```sql
create table if not exists posting_approval (
    posting_id text not null,
    approval_order integer not null check (approval_order >= 0),
    approval_id text not null check (
        length(approval_id) between 1 and 255
        and approval_id glob '[A-Za-z0-9]*'
        and approval_id not glob '*[^A-Za-z0-9._:/-]*'
    ),
    approval_type text not null check (
        length(approval_type) between 1 and 64
        and approval_type glob '[A-Za-z0-9]*'
        and approval_type not glob '*[^A-Za-z0-9._:/-]*'
    ),
    approver_reference text not null check (length(trim(approver_reference)) > 0),
    approver_type text not null check (
        length(approver_type) between 1 and 64
        and approver_type glob '[A-Za-z0-9]*'
        and approver_type not glob '*[^A-Za-z0-9._:/-]*'
    ),
    decision text not null check (decision in ('APPROVED', 'REJECTED')),
    approved_at text not null check (
        (
            approved_at glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]Z'
            or (
                substr(approved_at, 1, 19) glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]'
                and substr(approved_at, 20, 1) = '.'
                and substr(approved_at, length(approved_at), 1) = 'Z'
                and (
                    (length(approved_at) = 24 and substr(approved_at, 21, 3) not glob '*[^0-9]*')
                    or (length(approved_at) = 27 and substr(approved_at, 21, 6) not glob '*[^0-9]*')
                    or (length(approved_at) = 30 and substr(approved_at, 21, 9) not glob '*[^0-9]*')
                )
            )
        )
        and substr(approved_at, 6, 2) between '01' and '12'
        and (
            (
                substr(approved_at, 6, 2) in ('01', '03', '05', '07', '08', '10', '12')
                and substr(approved_at, 9, 2) between '01' and '31'
            )
            or (
                substr(approved_at, 6, 2) in ('04', '06', '09', '11')
                and substr(approved_at, 9, 2) between '01' and '30'
            )
            or (
                substr(approved_at, 6, 2) = '02'
                and (
                    substr(approved_at, 9, 2) between '01' and '28'
                    or (
                        substr(approved_at, 9, 2) = '29'
                        and (
                            cast(substr(approved_at, 1, 4) as integer) % 400 = 0
                            or (
                                cast(substr(approved_at, 1, 4) as integer) % 4 = 0
                                and cast(substr(approved_at, 1, 4) as integer) % 100 <> 0
                            )
                        )
                    )
                )
            )
        )
        and substr(approved_at, 12, 2) between '00' and '23'
        and substr(approved_at, 15, 2) between '00' and '59'
        and substr(approved_at, 18, 2) between '00' and '59'
    ),
    primary key (posting_id, approval_order),
    unique (posting_id, approval_id),
    foreign key (posting_id) references posting_fact (posting_id)
) strict;
```
