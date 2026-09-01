---
afad: "5.0.1"
version: "0.64.0"
domain: SQLITE_SCHEMA_CORE_LATVIAN_PAYROLL_SETTLEMENTS
updated: "2026-09-01"
---

# SQLite Schema: Latvian Payroll Settlements

**Purpose**: Immutable Latvian payroll-obligation settlements and compensating reversals.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: `latvian_payroll_settlement`, `latvian_payroll_settlement_reversal`, and settlement validation/append-only triggers.

```sql
create table if not exists latvian_payroll_settlement (
    origin_posting_id text primary key references posting_fact (posting_id),
    payroll_run_id text not null references latvian_payroll_run (payroll_run_id),
    settlement_kind text not null check (settlement_kind in ('NET_WAGES', 'STATE_REMITTANCE')),
    effective_date text not null,
    cash_account_code text not null references account (account_code)
) strict;

create table if not exists latvian_payroll_settlement_reversal (
    reversal_posting_id text primary key references posting_fact (posting_id),
    origin_posting_id text not null unique references latvian_payroll_settlement (origin_posting_id)
) strict;

create trigger if not exists latvian_payroll_settlement_validate_on_insert
before insert on latvian_payroll_settlement
begin
    select raise(fail, 'latvian_payroll_settlement requires one active payroll run.')
    where not exists (
        select 1
        from latvian_payroll_run
        where payroll_run_id = new.payroll_run_id
            and not exists (
                select 1
                from latvian_payroll_run_reversal
                where latvian_payroll_run_reversal.payroll_run_id = latvian_payroll_run.payroll_run_id
            )
    );
    select raise(fail, 'latvian_payroll_settlement effective date cannot precede its payroll run.')
    where exists (
        select 1
        from latvian_payroll_run
        where payroll_run_id = new.payroll_run_id
            and new.effective_date < effective_date
    );
    select raise(fail, 'latvian_payroll_settlement origin must be the matching typed payroll-settlement posting.')
    where not exists (
        select 1
        from posting_fact
        where posting_id = new.origin_posting_id
            and effective_date = new.effective_date
            and (
                (new.settlement_kind = 'NET_WAGES' and posting_origin_kind = 'LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT')
                or (new.settlement_kind = 'STATE_REMITTANCE' and posting_origin_kind = 'LATVIAN_PAYROLL_STATE_REMITTANCE')
            )
    );
    select raise(fail, 'latvian_payroll_settlement requires a cash-and-cash-equivalent asset account.')
    where not exists (
        select 1
        from account
        where account_code = new.cash_account_code
            and account_type = 'ASSET'
            and cash_flow_asset_classification = 'CASH_AND_CASH_EQUIVALENT'
    );
    select raise(fail, 'latvian_payroll_settlement may have only one active settlement per payroll obligation.')
    where exists (
        select 1
        from latvian_payroll_settlement existing_settlement
        where existing_settlement.payroll_run_id = new.payroll_run_id
            and existing_settlement.settlement_kind = new.settlement_kind
            and not exists (
                select 1
                from latvian_payroll_settlement_reversal existing_reversal
                where existing_reversal.origin_posting_id = existing_settlement.origin_posting_id
            )
    );
    select raise(fail, 'latvian_payroll_settlement journal must exactly match its immutable payroll obligation.')
    where exists (
        select 1
        from journal_line
        inner join latvian_payroll_run on latvian_payroll_run.payroll_run_id = new.payroll_run_id
        where posting_id = new.origin_posting_id
            and not (
                (new.settlement_kind = 'NET_WAGES'
                    and account_code = latvian_payroll_run.net_wages_payable_account_code
                    and entry_side = 'DEBIT'
                    and journal_line.currency_code = latvian_payroll_run.currency_code
                    and amount_minor = latvian_payroll_run.net_wages_minor)
                or (new.settlement_kind = 'NET_WAGES'
                    and account_code = new.cash_account_code
                    and entry_side = 'CREDIT'
                    and journal_line.currency_code = latvian_payroll_run.currency_code
                    and amount_minor = latvian_payroll_run.net_wages_minor)
                or (new.settlement_kind = 'STATE_REMITTANCE'
                    and latvian_payroll_run.employee_social_contribution_minor > 0
                    and account_code = latvian_payroll_run.employee_social_payable_account_code
                    and entry_side = 'DEBIT'
                    and journal_line.currency_code = latvian_payroll_run.currency_code
                    and amount_minor = latvian_payroll_run.employee_social_contribution_minor)
                or (new.settlement_kind = 'STATE_REMITTANCE'
                    and latvian_payroll_run.employer_social_contribution_minor > 0
                    and account_code = latvian_payroll_run.employer_social_payable_account_code
                    and entry_side = 'DEBIT'
                    and journal_line.currency_code = latvian_payroll_run.currency_code
                    and amount_minor = latvian_payroll_run.employer_social_contribution_minor)
                or (new.settlement_kind = 'STATE_REMITTANCE'
                    and latvian_payroll_run.personal_income_tax_minor > 0
                    and account_code = latvian_payroll_run.personal_income_tax_payable_account_code
                    and entry_side = 'DEBIT'
                    and journal_line.currency_code = latvian_payroll_run.currency_code
                    and amount_minor = latvian_payroll_run.personal_income_tax_minor)
                or (new.settlement_kind = 'STATE_REMITTANCE'
                    and account_code = new.cash_account_code
                    and entry_side = 'CREDIT'
                    and journal_line.currency_code = latvian_payroll_run.currency_code
                    and amount_minor = latvian_payroll_run.employee_social_contribution_minor + latvian_payroll_run.employer_social_contribution_minor + latvian_payroll_run.personal_income_tax_minor)
            )
    )
    or not exists (
        select 1
        from journal_line
        inner join latvian_payroll_run on latvian_payroll_run.payroll_run_id = new.payroll_run_id
        where posting_id = new.origin_posting_id
            and (
                (new.settlement_kind = 'NET_WAGES'
                    and account_code = latvian_payroll_run.net_wages_payable_account_code
                    and entry_side = 'DEBIT'
                    and journal_line.currency_code = latvian_payroll_run.currency_code
                    and amount_minor = latvian_payroll_run.net_wages_minor)
                or (new.settlement_kind = 'STATE_REMITTANCE'
                    and account_code = new.cash_account_code
                    and entry_side = 'CREDIT'
                    and journal_line.currency_code = latvian_payroll_run.currency_code
                    and amount_minor = latvian_payroll_run.employee_social_contribution_minor + latvian_payroll_run.employer_social_contribution_minor + latvian_payroll_run.personal_income_tax_minor)
            )
    )
    or (
        new.settlement_kind = 'NET_WAGES'
        and not exists (
            select 1
            from journal_line
            inner join latvian_payroll_run on latvian_payroll_run.payroll_run_id = new.payroll_run_id
            where posting_id = new.origin_posting_id
                and account_code = new.cash_account_code
                and entry_side = 'CREDIT'
                and journal_line.currency_code = latvian_payroll_run.currency_code
                and amount_minor = latvian_payroll_run.net_wages_minor
        )
    )
    or (
        new.settlement_kind = 'STATE_REMITTANCE'
        and exists (
            select 1
            from latvian_payroll_run
            where payroll_run_id = new.payroll_run_id
                and employee_social_contribution_minor > 0
        )
        and not exists (
            select 1
            from journal_line
            inner join latvian_payroll_run on latvian_payroll_run.payroll_run_id = new.payroll_run_id
            where posting_id = new.origin_posting_id
                and account_code = latvian_payroll_run.employee_social_payable_account_code
                and entry_side = 'DEBIT'
                and journal_line.currency_code = latvian_payroll_run.currency_code
                and amount_minor = latvian_payroll_run.employee_social_contribution_minor
        )
    )
    or (
        new.settlement_kind = 'STATE_REMITTANCE'
        and exists (
            select 1
            from latvian_payroll_run
            where payroll_run_id = new.payroll_run_id
                and employer_social_contribution_minor > 0
        )
        and not exists (
            select 1
            from journal_line
            inner join latvian_payroll_run on latvian_payroll_run.payroll_run_id = new.payroll_run_id
            where posting_id = new.origin_posting_id
                and account_code = latvian_payroll_run.employer_social_payable_account_code
                and entry_side = 'DEBIT'
                and journal_line.currency_code = latvian_payroll_run.currency_code
                and amount_minor = latvian_payroll_run.employer_social_contribution_minor
        )
    )
    or (
        new.settlement_kind = 'STATE_REMITTANCE'
        and exists (
            select 1
            from latvian_payroll_run
            where payroll_run_id = new.payroll_run_id
                and personal_income_tax_minor > 0
        )
        and not exists (
            select 1
            from journal_line
            inner join latvian_payroll_run on latvian_payroll_run.payroll_run_id = new.payroll_run_id
            where posting_id = new.origin_posting_id
                and account_code = latvian_payroll_run.personal_income_tax_payable_account_code
                and entry_side = 'DEBIT'
                and journal_line.currency_code = latvian_payroll_run.currency_code
                and amount_minor = latvian_payroll_run.personal_income_tax_minor
        )
    );
end;

create trigger if not exists latvian_payroll_settlement_reversal_validate_on_insert
before insert on latvian_payroll_settlement_reversal
begin
    select raise(fail, 'latvian_payroll_settlement reversal must negate its originating payroll settlement posting.')
    where not exists (
        select 1
        from latvian_payroll_settlement
        inner join posting_fact on posting_fact.posting_id = new.reversal_posting_id
        where latvian_payroll_settlement.origin_posting_id = new.origin_posting_id
            and posting_fact.posting_origin_kind = 'REVERSAL'
            and posting_fact.prior_posting_id = latvian_payroll_settlement.origin_posting_id
    );
end;

create trigger if not exists latvian_payroll_settlement_reject_update
before update on latvian_payroll_settlement
begin
    select raise(fail, 'latvian_payroll_settlement rows are append-only.');
end;

create trigger if not exists latvian_payroll_settlement_reject_delete
before delete on latvian_payroll_settlement
begin
    select raise(fail, 'latvian_payroll_settlement rows are append-only.');
end;

create trigger if not exists latvian_payroll_settlement_reversal_reject_update
before update on latvian_payroll_settlement_reversal
begin
    select raise(fail, 'latvian_payroll_settlement_reversal rows are append-only.');
end;

create trigger if not exists latvian_payroll_settlement_reversal_reject_delete
before delete on latvian_payroll_settlement_reversal
begin
    select raise(fail, 'latvian_payroll_settlement_reversal rows are append-only.');
end;
```
