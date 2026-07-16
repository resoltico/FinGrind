---
afad: "4.0"
version: "0.61.0"
domain: SQLITE_SCHEMA_CORE_LATVIAN_PAYROLL_RUNS
updated: "2026-07-16"
---

# SQLite Schema: Latvian Payroll Runs

**Purpose**: Immutable Latvian monthly-payroll run origins and compensating reversals.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: `latvian_payroll_run`, `latvian_payroll_run_reversal`, and run validation triggers.

```sql
create table if not exists latvian_payroll_run (
    payroll_run_id text primary key check (
        length(payroll_run_id) between 1 and 120
        and payroll_run_id glob '[a-z0-9]*'
        and payroll_run_id not glob '*[^a-z0-9-]*'
        and payroll_run_id not like '-%'
        and payroll_run_id not like '%-'
        and payroll_run_id not like '%--%'
    ),
    origin_posting_id text not null unique references posting_fact (posting_id),
    employee_reference text not null check (
        length(employee_reference) between 1 and 120
        and employee_reference glob '[a-z0-9]*'
        and employee_reference not glob '*[^a-z0-9-]*'
        and employee_reference not like '-%'
        and employee_reference not like '%-'
        and employee_reference not like '%--%'
    ),
    payroll_month text not null check (
        payroll_month glob '2026-[0-1][0-9]'
        and payroll_month between '2026-01' and '2026-12'
    ),
    effective_date text not null check (
        effective_date = date(payroll_month || '-01', '+1 month', '-1 day')
    ),
    wage_expense_account_code text not null references account (account_code),
    employer_social_expense_account_code text not null references account (account_code),
    net_wages_payable_account_code text not null references account (account_code),
    employee_social_payable_account_code text not null references account (account_code),
    employer_social_payable_account_code text not null references account (account_code),
    personal_income_tax_payable_account_code text not null references account (account_code),
    currency_code text not null check (currency_code = 'EUR'),
    gross_wages_minor integer not null check (gross_wages_minor between 1 and 877500),
    employee_social_contribution_minor integer not null check (employee_social_contribution_minor >= 0),
    employer_social_contribution_minor integer not null check (employer_social_contribution_minor >= 0),
    non_taxable_minimum_minor integer not null check (non_taxable_minimum_minor >= 0),
    personal_income_tax_minor integer not null check (personal_income_tax_minor >= 0),
    net_wages_minor integer not null check (net_wages_minor > 0),
    check (
        employee_social_contribution_minor = (gross_wages_minor * 105000 + 500000) / 1000000
        and employer_social_contribution_minor = (gross_wages_minor * 235900 + 500000) / 1000000
        and non_taxable_minimum_minor = min(55000, gross_wages_minor - employee_social_contribution_minor)
        and personal_income_tax_minor = (
            ((gross_wages_minor - employee_social_contribution_minor - non_taxable_minimum_minor) * 255000 + 500000) / 1000000
        )
        and net_wages_minor = gross_wages_minor - employee_social_contribution_minor - personal_income_tax_minor
    ),
    check (
        wage_expense_account_code <> employer_social_expense_account_code
        and wage_expense_account_code <> net_wages_payable_account_code
        and wage_expense_account_code <> employee_social_payable_account_code
        and wage_expense_account_code <> employer_social_payable_account_code
        and wage_expense_account_code <> personal_income_tax_payable_account_code
        and employer_social_expense_account_code <> net_wages_payable_account_code
        and employer_social_expense_account_code <> employee_social_payable_account_code
        and employer_social_expense_account_code <> employer_social_payable_account_code
        and employer_social_expense_account_code <> personal_income_tax_payable_account_code
        and net_wages_payable_account_code <> employee_social_payable_account_code
        and net_wages_payable_account_code <> employer_social_payable_account_code
        and net_wages_payable_account_code <> personal_income_tax_payable_account_code
        and employee_social_payable_account_code <> employer_social_payable_account_code
        and employee_social_payable_account_code <> personal_income_tax_payable_account_code
        and employer_social_payable_account_code <> personal_income_tax_payable_account_code
    )
) strict;

create table if not exists latvian_payroll_run_reversal (
    reversal_posting_id text primary key references posting_fact (posting_id),
    payroll_run_id text not null unique references latvian_payroll_run (payroll_run_id)
) strict;

create trigger if not exists latvian_payroll_run_validate_on_insert
before insert on latvian_payroll_run
begin
    select raise(fail, 'latvian_payroll_run requires an EUR functional-currency book.')
    where not exists (
        select 1 from book_identity where functional_currency_code = 'EUR'
    );
    select raise(fail, 'latvian_payroll_run origin must be the matching typed payroll posting.')
    where not exists (
        select 1
        from posting_fact
        where posting_id = new.origin_posting_id
            and posting_origin_kind = 'LATVIAN_MONTHLY_PAYROLL'
            and effective_date = new.effective_date
    );
    select raise(fail, 'latvian_payroll_run may have only one active run per employee and month.')
    where exists (
        select 1
        from latvian_payroll_run existing_run
        where existing_run.employee_reference = new.employee_reference
            and existing_run.payroll_month = new.payroll_month
            and not exists (
                select 1
                from latvian_payroll_run_reversal existing_reversal
                where existing_reversal.payroll_run_id = existing_run.payroll_run_id
            )
    );
    select raise(fail, 'latvian_payroll_run requires two expense accounts and four current liabilities.')
    where
        not exists (
            select 1 from account
            where account_code = new.wage_expense_account_code and account_type = 'EXPENSE'
        )
        or not exists (
            select 1 from account
            where account_code = new.employer_social_expense_account_code and account_type = 'EXPENSE'
        )
        or exists (
            select 1
            from account
            where account_code in (
                new.net_wages_payable_account_code,
                new.employee_social_payable_account_code,
                new.employer_social_payable_account_code,
                new.personal_income_tax_payable_account_code
            )
            and (
                account_type <> 'LIABILITY'
                or financial_position_line_classification <> 'CURRENT_LIABILITY'
            )
        )
        or 4 <> (
            select count(*)
            from account
            where account_code in (
                new.net_wages_payable_account_code,
                new.employee_social_payable_account_code,
                new.employer_social_payable_account_code,
                new.personal_income_tax_payable_account_code
            )
                and account_type = 'LIABILITY'
                and financial_position_line_classification = 'CURRENT_LIABILITY'
        );
    select raise(fail, 'latvian_payroll_run journal must exactly match its resolved component facts.')
    where exists (
        select 1
        from journal_line
        where posting_id = new.origin_posting_id
            and not (
                (account_code = new.wage_expense_account_code and entry_side = 'DEBIT' and currency_code = new.currency_code and amount_minor = new.gross_wages_minor)
                or (new.employer_social_contribution_minor > 0 and account_code = new.employer_social_expense_account_code and entry_side = 'DEBIT' and currency_code = new.currency_code and amount_minor = new.employer_social_contribution_minor)
                or (account_code = new.net_wages_payable_account_code and entry_side = 'CREDIT' and currency_code = new.currency_code and amount_minor = new.net_wages_minor)
                or (new.employee_social_contribution_minor > 0 and account_code = new.employee_social_payable_account_code and entry_side = 'CREDIT' and currency_code = new.currency_code and amount_minor = new.employee_social_contribution_minor)
                or (new.employer_social_contribution_minor > 0 and account_code = new.employer_social_payable_account_code and entry_side = 'CREDIT' and currency_code = new.currency_code and amount_minor = new.employer_social_contribution_minor)
                or (new.personal_income_tax_minor > 0 and account_code = new.personal_income_tax_payable_account_code and entry_side = 'CREDIT' and currency_code = new.currency_code and amount_minor = new.personal_income_tax_minor)
            )
    )
    or not exists (
        select 1 from journal_line
        where posting_id = new.origin_posting_id
            and account_code = new.wage_expense_account_code
            and entry_side = 'DEBIT'
            and currency_code = new.currency_code
            and amount_minor = new.gross_wages_minor
    )
    or not exists (
        select 1 from journal_line
        where posting_id = new.origin_posting_id
            and account_code = new.net_wages_payable_account_code
            and entry_side = 'CREDIT'
            and currency_code = new.currency_code
            and amount_minor = new.net_wages_minor
    )
    or (
        new.employee_social_contribution_minor > 0
        and not exists (
            select 1 from journal_line
            where posting_id = new.origin_posting_id
                and account_code = new.employee_social_payable_account_code
                and entry_side = 'CREDIT'
                and currency_code = new.currency_code
                and amount_minor = new.employee_social_contribution_minor
        )
    )
    or (
        new.employer_social_contribution_minor > 0
        and (
            not exists (
                select 1 from journal_line
                where posting_id = new.origin_posting_id
                    and account_code = new.employer_social_expense_account_code
                    and entry_side = 'DEBIT'
                    and currency_code = new.currency_code
                    and amount_minor = new.employer_social_contribution_minor
            )
            or not exists (
                select 1 from journal_line
                where posting_id = new.origin_posting_id
                    and account_code = new.employer_social_payable_account_code
                    and entry_side = 'CREDIT'
                    and currency_code = new.currency_code
                    and amount_minor = new.employer_social_contribution_minor
            )
        )
    )
    or (
        new.personal_income_tax_minor > 0
        and not exists (
            select 1 from journal_line
            where posting_id = new.origin_posting_id
                and account_code = new.personal_income_tax_payable_account_code
                and entry_side = 'CREDIT'
                and currency_code = new.currency_code
                and amount_minor = new.personal_income_tax_minor
        )
    );
end;

create trigger if not exists latvian_payroll_run_reversal_validate_on_insert
before insert on latvian_payroll_run_reversal
begin
    select raise(fail, 'latvian_payroll_run reversal must negate its originating payroll posting.')
    where not exists (
        select 1
        from latvian_payroll_run
        inner join posting_fact on posting_fact.posting_id = new.reversal_posting_id
        where latvian_payroll_run.payroll_run_id = new.payroll_run_id
            and posting_fact.posting_origin_kind = 'REVERSAL'
            and posting_fact.prior_posting_id = latvian_payroll_run.origin_posting_id
    );
    select raise(fail, 'latvian_payroll_run reversal requires every active payroll settlement to be reversed first.')
    where exists (
        select 1
        from latvian_payroll_settlement
        where latvian_payroll_settlement.payroll_run_id = new.payroll_run_id
            and not exists (
                select 1
                from latvian_payroll_settlement_reversal
                where latvian_payroll_settlement_reversal.origin_posting_id = latvian_payroll_settlement.origin_posting_id
            )
    );
end;
```
