---
afad: "5.0.1"
version: "0.62.2"
domain: SQLITE_SCHEMA_CORE_ATTESTATION_OPERATION
updated: "2026-08-09"
---

# SQLite Schema: Attestation Operation Evidence

**Purpose**: Canonical operation envelopes and signed request and effect preimages.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: `attestation_operation`.

```sql
create table if not exists attestation_operation (
    operation_order_hex text primary key check (
        length(operation_order_hex) = 16
        and operation_order_hex glob '[0-9a-f]*'
        and operation_order_hex not glob '*[^0-9a-f]*'
    ),
    operation_envelope_base64 text not null check (length(operation_envelope_base64) > 0),
    request_preimage_base64 text not null check (length(request_preimage_base64) > 0),
    effect_preimage_base64 text not null check (length(effect_preimage_base64) > 0),
    operation_head_hex text not null unique check (
        length(operation_head_hex) = 64
        and operation_head_hex glob '[0-9a-f]*'
        and operation_head_hex not glob '*[^0-9a-f]*'
    )
) strict;
```
