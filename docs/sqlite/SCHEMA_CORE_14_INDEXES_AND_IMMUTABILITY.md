---
afad: "5.0.1"
version: "0.64.0"
domain: SQLITE_SCHEMA_CORE_INDEXES_AND_IMMUTABILITY
updated: "2026-09-01"
---

# SQLite Schema: Indexes And Immutability

**Purpose**: Lookup indexes plus append-only triggers for durable rows that never mutate in place.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: Schema-tail lookup indexes and append-only reject-update/reject-delete triggers.

```sql
create index if not exists latvian_payroll_run_by_employee_month
on latvian_payroll_run (employee_reference, payroll_month, payroll_run_id);

create index if not exists latvian_payroll_settlement_by_run_kind
on latvian_payroll_settlement (payroll_run_id, settlement_kind, effective_date, origin_posting_id);

create index if not exists posting_fact_by_prior_posting_id
on posting_fact (prior_posting_id);

create index if not exists posting_fact_by_effective_recorded_posting
on posting_fact (effective_date desc, recorded_at desc, posting_id desc);

create index if not exists tax_registration_code_by_registration_id
on tax_registration_code (tax_registration_id, tax_code);

create index if not exists posting_applied_tax_by_tax_registration_id
on posting_applied_tax (tax_registration_id, posting_id);

create index if not exists journal_line_by_account_code
on journal_line (account_code, posting_id, line_order);

create index if not exists inventory_movement_by_account_replay
on inventory_movement (inventory_account, effective_date, account_sequence);

create index if not exists inventory_movement_by_posting_id
on inventory_movement (posting_id, inventory_account, account_sequence);

create index if not exists accrual_cutoff_application_by_cutoff_horizon
on accrual_cutoff_application (accrual_cutoff_id, effective_date, application_posting_id);

create index if not exists audit_event_by_recorded_at
on audit_event (recorded_at, audit_event_order);

create index if not exists interim_result_sweep_by_effective_date_to
on interim_result_sweep (effective_date_to desc, interim_result_sweep_order desc);

create index if not exists interim_result_sweep_total_by_currency
on interim_result_sweep_total (currency_code, interim_result_sweep_order);

create index if not exists interim_result_sweep_posting_by_posting_id
on interim_result_sweep_posting (posting_id, interim_result_sweep_order);

create index if not exists fiscal_year_close_by_effective_date_to
on fiscal_year_close (effective_date_to desc, fiscal_year_close_order desc);

create index if not exists fiscal_year_close_posting_by_posting_id
on fiscal_year_close_posting (posting_id, fiscal_year_close_order);

create unique index if not exists posting_fact_one_reversal_per_target
on posting_fact (prior_posting_id)
where prior_posting_id is not null;

create trigger if not exists posting_fact_reject_update
before update on posting_fact
begin
    select raise(fail, 'posting_fact rows are append-only.');
end;

create trigger if not exists posting_fact_reject_delete
before delete on posting_fact
begin
    select raise(fail, 'posting_fact rows are append-only.');
end;

create trigger if not exists journal_line_reject_update
before update on journal_line
begin
    select raise(fail, 'journal_line rows are append-only.');
end;

create trigger if not exists journal_line_reject_delete
before delete on journal_line
begin
    select raise(fail, 'journal_line rows are append-only.');
end;

create trigger if not exists book_identity_reject_update
before update on book_identity
begin
    select raise(fail, 'book_identity rows are append-only.');
end;

create trigger if not exists book_identity_reject_delete
before delete on book_identity
begin
    select raise(fail, 'book_identity rows are append-only.');
end;

create trigger if not exists inventory_movement_reject_update
before update on inventory_movement
begin
    select raise(fail, 'inventory_movement rows are append-only.');
end;

create trigger if not exists inventory_movement_reject_delete
before delete on inventory_movement
begin
    select raise(fail, 'inventory_movement rows are append-only.');
end;

create trigger if not exists audit_event_reject_update
before update on audit_event
begin
    select raise(fail, 'audit_event rows are append-only.');
end;

create trigger if not exists audit_event_reject_delete
before delete on audit_event
begin
    select raise(fail, 'audit_event rows are append-only.');
end;

create trigger if not exists interim_result_sweep_reject_update
before update on interim_result_sweep
begin
    select raise(fail, 'interim_result_sweep rows are append-only.');
end;

create trigger if not exists interim_result_sweep_reject_delete
before delete on interim_result_sweep
begin
    select raise(fail, 'interim_result_sweep rows are append-only.');
end;

create trigger if not exists interim_result_sweep_total_reject_update
before update on interim_result_sweep_total
begin
    select raise(fail, 'interim_result_sweep_total rows are append-only.');
end;

create trigger if not exists interim_result_sweep_total_reject_delete
before delete on interim_result_sweep_total
begin
    select raise(fail, 'interim_result_sweep_total rows are append-only.');
end;

create trigger if not exists interim_result_sweep_posting_reject_update
before update on interim_result_sweep_posting
begin
    select raise(fail, 'interim_result_sweep_posting rows are append-only.');
end;

create trigger if not exists interim_result_sweep_posting_reject_delete
before delete on interim_result_sweep_posting
begin
    select raise(fail, 'interim_result_sweep_posting rows are append-only.');
end;

create trigger if not exists fiscal_year_close_reject_update
before update on fiscal_year_close
begin
    select raise(fail, 'fiscal_year_close rows are append-only.');
end;

create trigger if not exists fiscal_year_close_reject_delete
before delete on fiscal_year_close
begin
    select raise(fail, 'fiscal_year_close rows are append-only.');
end;

create trigger if not exists fiscal_year_close_posting_reject_update
before update on fiscal_year_close_posting
begin
    select raise(fail, 'fiscal_year_close_posting rows are append-only.');
end;

create trigger if not exists fiscal_year_close_posting_reject_delete
before delete on fiscal_year_close_posting
begin
    select raise(fail, 'fiscal_year_close_posting rows are append-only.');
end;

create trigger if not exists attestation_operation_reject_update
before update on attestation_operation
begin
    select raise(fail, 'attestation_operation rows are append-only.');
end;

create trigger if not exists attestation_operation_reject_delete
before delete on attestation_operation
begin
    select raise(fail, 'attestation_operation rows are append-only.');
end;
```
