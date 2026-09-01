---
afad: "5.0.1"
version: "0.64.0"
domain: SQLITE_SCHEMA_CORE_ACCRUAL_CUTOFF_APPLICATIONS
updated: "2026-09-01"
---

# SQLite Schema: Accrual Cut-off Applications

**Purpose**: Append-only recognition, settlement, and compensating-reversal facts for accrual cut-offs.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: `accrual_cutoff_application`, lifecycle application validation, and accrual-cut-off append-only triggers.

```sql
create table if not exists accrual_cutoff_application (
    application_posting_id text primary key references posting_fact (posting_id),
    accrual_cutoff_id text not null references accrual_cutoff (accrual_cutoff_id),
    application_kind text not null check (application_kind in ('RECOGNITION', 'SETTLEMENT', 'ORIGIN_REVERSAL', 'APPLICATION_REVERSAL')),
    effective_date text not null,
    amount_currency_code text not null check (amount_currency_code glob '[A-Z][A-Z][A-Z]'),
    amount_minor integer not null check (amount_minor <> 0)
) strict;

create trigger if not exists accrual_cutoff_application_validate_on_insert
before insert on accrual_cutoff_application
begin
    select raise(fail, 'accrual_cutoff_application must use the cut-off currency.')
    where exists (
        select 1
        from accrual_cutoff
        where accrual_cutoff_id = new.accrual_cutoff_id
            and amount_currency_code <> new.amount_currency_code
    );
    select raise(fail, 'accrual_cutoff_application posting facts must match the application facts.')
    where not exists (
        select 1
        from posting_fact
        where posting_fact.posting_id = new.application_posting_id
            and posting_fact.effective_date = new.effective_date
            and (
                new.application_kind in ('ORIGIN_REVERSAL', 'APPLICATION_REVERSAL')
                or (
                    posting_fact.entry_amount_currency_code = new.amount_currency_code
                    and posting_fact.entry_amount_minor = abs(new.amount_minor)
                )
            )
    );
    select raise(fail, 'accrual_cutoff_application must use the matching typed application event and accounts.')
    where not exists (
        select 1
        from accrual_cutoff
        inner join posting_fact on posting_fact.posting_id = new.application_posting_id
        where accrual_cutoff.accrual_cutoff_id = new.accrual_cutoff_id
            and (
                (
                    new.application_kind = 'RECOGNITION'
                    and accrual_cutoff.kind = 'PREPAYMENT'
                    and posting_fact.posting_origin_kind = 'ACCRUAL_CUTOFF_RECOGNITION'
                    and posting_fact.entry_primary_debit_account_code = accrual_cutoff.recognition_account_code
                    and posting_fact.entry_primary_credit_account_code = accrual_cutoff.cutoff_account_code
                )
                or (
                    new.application_kind = 'RECOGNITION'
                    and accrual_cutoff.kind = 'DEFERRED_REVENUE'
                    and posting_fact.posting_origin_kind = 'ACCRUAL_CUTOFF_RECOGNITION'
                    and posting_fact.entry_primary_debit_account_code = accrual_cutoff.cutoff_account_code
                    and posting_fact.entry_primary_credit_account_code = accrual_cutoff.recognition_account_code
                )
                or (
                    new.application_kind = 'SETTLEMENT'
                    and accrual_cutoff.kind = 'ACCRUED_EXPENSE'
                    and posting_fact.posting_origin_kind = 'ACCRUED_EXPENSE_SETTLEMENT'
                    and posting_fact.entry_primary_debit_account_code = accrual_cutoff.cutoff_account_code
                )
                or (
                    new.application_kind = 'ORIGIN_REVERSAL'
                    and posting_fact.posting_origin_kind = 'REVERSAL'
                    and posting_fact.prior_posting_id = accrual_cutoff.origin_posting_id
                )
                or (
                    new.application_kind = 'APPLICATION_REVERSAL'
                    and posting_fact.posting_origin_kind = 'REVERSAL'
                    and exists (
                        select 1
                        from accrual_cutoff_application original_application
                        where original_application.application_posting_id = posting_fact.prior_posting_id
                            and original_application.accrual_cutoff_id = accrual_cutoff.accrual_cutoff_id
                            and original_application.application_kind in ('RECOGNITION', 'SETTLEMENT')
                            and original_application.amount_currency_code = new.amount_currency_code
                            and original_application.amount_minor = -new.amount_minor
                    )
                )
            )
    );
    select raise(fail, 'accrual_cutoff_application must not precede its origin posting.')
    where exists (
        select 1
        from accrual_cutoff
        where accrual_cutoff_id = new.accrual_cutoff_id
            and new.effective_date < originated_on
    );
    select raise(fail, 'accrual_cutoff_application must append in non-decreasing effective-date order.')
    where exists (
        select 1
        from accrual_cutoff_application
        where accrual_cutoff_id = new.accrual_cutoff_id
            and effective_date > new.effective_date
    );
    select raise(fail, 'accrual cut-off recognition must remain inside its declared inclusive interval.')
    where exists (
        select 1
        from accrual_cutoff
        where accrual_cutoff_id = new.accrual_cutoff_id
            and new.application_kind = 'RECOGNITION'
            and (
                new.effective_date < recognition_start_date
                or new.effective_date > recognition_end_date
            )
    );
    select raise(fail, 'accrued-expense settlement requires a declared cash credit account.')
    where exists (
        select 1
        from accrual_cutoff
        where accrual_cutoff.accrual_cutoff_id = new.accrual_cutoff_id
            and accrual_cutoff.kind = 'ACCRUED_EXPENSE'
            and new.application_kind = 'SETTLEMENT'
    )
    and not exists (
        select 1
        from posting_fact
        inner join account on account.account_code = posting_fact.entry_primary_credit_account_code
        where posting_fact.posting_id = new.application_posting_id
            and account.account_type = 'ASSET'
            and account.cash_flow_asset_classification = 'CASH_AND_CASH_EQUIVALENT'
    );
    select raise(fail, 'accrual_cutoff_application amount must not exceed the remaining cut-off amount.')
    where exists (
        select 1
        from accrual_cutoff
        where accrual_cutoff_id = new.accrual_cutoff_id
            and original_amount_minor < (
                new.amount_minor + coalesce((
                    select sum(amount_minor)
                    from accrual_cutoff_application
                    where accrual_cutoff_id = new.accrual_cutoff_id
                ), 0)
            )
    );
    select raise(fail, 'accrual_cutoff_application amount must not make the applied cut-off amount negative.')
    where exists (
        select 1
        from accrual_cutoff
        where accrual_cutoff_id = new.accrual_cutoff_id
            and 0 > (
                new.amount_minor + coalesce((
                    select sum(amount_minor)
                    from accrual_cutoff_application
                    where accrual_cutoff_id = new.accrual_cutoff_id
                ), 0)
            )
    );
end;

create trigger if not exists accrual_cutoff_reject_update
before update on accrual_cutoff
begin
    select raise(fail, 'accrual_cutoff rows are append-only.');
end;

create trigger if not exists accrual_cutoff_reject_delete
before delete on accrual_cutoff
begin
    select raise(fail, 'accrual_cutoff rows are append-only.');
end;

create trigger if not exists accrual_cutoff_application_reject_update
before update on accrual_cutoff_application
begin
    select raise(fail, 'accrual_cutoff_application rows are append-only.');
end;

create trigger if not exists accrual_cutoff_application_reject_delete
before delete on accrual_cutoff_application
begin
    select raise(fail, 'accrual_cutoff_application rows are append-only.');
end;
```
