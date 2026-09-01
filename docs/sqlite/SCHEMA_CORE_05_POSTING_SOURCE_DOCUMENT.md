---
afad: "5.0.1"
version: "0.64.0"
domain: SQLITE_SCHEMA_CORE_POSTING_SOURCE_DOCUMENT
updated: "2026-09-01"
---

# SQLite Schema: Posting Source Documents

**Purpose**: Durable source-document attribution for committed postings.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: `posting_source_document`.

```sql
create table if not exists posting_source_document (
    posting_id text not null,
    source_document_order integer not null check (source_document_order >= 0),
    source_document_id text not null check (
        length(source_document_id) between 1 and 255
        and source_document_id glob '[A-Za-z0-9]*'
        and source_document_id not glob '*[^A-Za-z0-9._:/-]*'
    ),
    source_document_type text not null check (
        length(source_document_type) between 1 and 64
        and source_document_type glob '[A-Za-z0-9]*'
        and source_document_type not glob '*[^A-Za-z0-9._:/-]*'
    ),
    document_date text not null check (
        document_date glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'
        and substr(document_date, 6, 2) between '01' and '12'
        and (
            (
                substr(document_date, 6, 2) in ('01', '03', '05', '07', '08', '10', '12')
                and substr(document_date, 9, 2) between '01' and '31'
            )
            or (
                substr(document_date, 6, 2) in ('04', '06', '09', '11')
                and substr(document_date, 9, 2) between '01' and '30'
            )
            or (
                substr(document_date, 6, 2) = '02'
                and (
                    substr(document_date, 9, 2) between '01' and '28'
                    or (
                        substr(document_date, 9, 2) = '29'
                        and (
                            cast(substr(document_date, 1, 4) as integer) % 400 = 0
                            or (
                                cast(substr(document_date, 1, 4) as integer) % 4 = 0
                                and cast(substr(document_date, 1, 4) as integer) % 100 <> 0
                            )
                        )
                    )
                )
            )
        )
    ),
    primary key (posting_id, source_document_order),
    unique (posting_id, source_document_id),
    foreign key (posting_id) references posting_fact (posting_id)
) strict;
```
