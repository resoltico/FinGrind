---
afad: "5.0.1"
version: "0.64.0"
domain: SQLITE_SCHEMA_CORE_FISCAL_YEAR_CLOSE_TARGET_RULES
updated: "2026-09-01"
---

# SQLite Schema: Fiscal Year Close Target Rules

**Purpose**: Capital, result-holding, and retained-accumulated target-account validation.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: Fiscal-year-close target-account validation trigger.

```sql
create trigger if not exists fiscal_year_close_validate_target_accounts_on_insert
before insert on fiscal_year_close
begin
    select raise(
        fail,
        'fiscal-year-close capital target must be one active EQUITY_CONTRIBUTION equity account.'
    )
    where exists (
        select 1
        from account
        where
            account.account_code = new.capital_account_code
            and (
                account.account_type <> 'EQUITY'
                or account.active = 0
                or account.financial_position_line_classification <> 'EQUITY_CONTRIBUTION'
            )
    );
    select raise(
        fail,
        'fiscal-year-close result-holding target must be one active RESULT_HOLDING equity account.'
    )
    where exists (
        select 1
        from account
        where
            account.account_code = new.result_holding_account_code
            and (
                account.account_type <> 'EQUITY'
                or account.active = 0
                or account.financial_position_line_classification <> 'RESULT_HOLDING'
            )
    );
    select raise(
        fail,
        'fiscal-year-close retained-accumulated target must be one active RETAINED_ACCUMULATED equity account.'
    )
    where exists (
        select 1
        from account
        where
            account.account_code = new.retained_accumulated_account_code
            and (
                account.account_type <> 'EQUITY'
                or account.active = 0
                or account.financial_position_line_classification <> 'RETAINED_ACCUMULATED'
            )
    );
end;
```
