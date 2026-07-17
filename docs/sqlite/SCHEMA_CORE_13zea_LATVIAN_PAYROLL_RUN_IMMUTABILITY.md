---
afad: "4.0"
version: "0.61.0"
domain: SQLITE_SCHEMA_CORE_LATVIAN_PAYROLL_RUN_IMMUTABILITY
updated: "2026-07-16"
---

# SQLite Schema: Latvian Payroll Run Immutability

**Purpose**: Append-only enforcement for Latvian monthly-payroll runs and their compensating reversals.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: Latvian payroll-run append-only reject-update/reject-delete triggers.

```sql
create trigger if not exists latvian_payroll_run_reject_update
before update on latvian_payroll_run
begin
    select raise(fail, 'latvian_payroll_run rows are append-only.');
end;

create trigger if not exists latvian_payroll_run_reject_delete
before delete on latvian_payroll_run
begin
    select raise(fail, 'latvian_payroll_run rows are append-only.');
end;

create trigger if not exists latvian_payroll_run_reversal_reject_update
before update on latvian_payroll_run_reversal
begin
    select raise(fail, 'latvian_payroll_run_reversal rows are append-only.');
end;

create trigger if not exists latvian_payroll_run_reversal_reject_delete
before delete on latvian_payroll_run_reversal
begin
    select raise(fail, 'latvian_payroll_run_reversal rows are append-only.');
end;
```
