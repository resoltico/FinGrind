---
afad: "4.0"
version: "0.59.0"
domain: SQLITE_SCHEMA_CORE_ACCOUNT_TABLE
updated: "2026-07-04"
---

# SQLite Schema: Account Table

**Purpose**: Declared-account storage, classifications, and parent pointers.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: `account`.

```sql
create table if not exists account (
    account_code text primary key check (
        length(account_code) between 1 and 255
        and account_code glob '[A-Za-z0-9]*'
        and account_code not glob '*[^A-Za-z0-9._:/-]*'
    ),
    account_name text not null check (length(trim(account_name)) > 0),
    account_type text not null check (
        account_type in ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')
    ),
    account_node_kind text not null check (account_node_kind in ('HEADER', 'POSTABLE')),
    parent_account_code text references account (account_code),
    financial_position_line_classification text check (
        financial_position_line_classification is null
        or financial_position_line_classification in (
            'CURRENT_ASSET',
            'NONCURRENT_ASSET',
            'TRADE_RECEIVABLE',
            'CURRENT_LIABILITY',
            'NONCURRENT_LIABILITY',
            'TRADE_PAYABLE',
            'EQUITY_CONTRIBUTION',
            'EQUITY_WITHDRAWAL',
            'RESULT_HOLDING',
            'RETAINED_ACCUMULATED',
            'RESERVE',
            'OTHER_EQUITY'
        )
    ),
    cash_flow_asset_classification text check (
        cash_flow_asset_classification is null
        or cash_flow_asset_classification in (
            'CASH_AND_CASH_EQUIVALENT',
            'NON_CASH'
        )
    ),
    profit_and_loss_line_classification text check (
        profit_and_loss_line_classification is null or profit_and_loss_line_classification in (
            'OPERATING_REVENUE',
            'SALES_DISCOUNT_ALLOWANCE',
            'OTHER_REVENUE',
            'FINANCE_INCOME',
            'COST_OF_SALES',
            'OPERATING_EXPENSE',
            'DEPRECIATION_AND_AMORTIZATION',
            'SETTLEMENT_FEE',
            'BAD_DEBT_WRITE_OFF',
            'FINANCE_EXPENSE',
            'OTHER_EXPENSE'
        )
    ),
    active integer not null check (active in (0, 1)),
    declared_at text not null check (
        (
            declared_at glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]Z'
            or (
                substr(declared_at, 1, 19) glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]'
                and substr(declared_at, 20, 1) = '.'
                and substr(declared_at, length(declared_at), 1) = 'Z'
                and (
                    (length(declared_at) = 24 and substr(declared_at, 21, 3) not glob '*[^0-9]*')
                    or (length(declared_at) = 27 and substr(declared_at, 21, 6) not glob '*[^0-9]*')
                    or (length(declared_at) = 30 and substr(declared_at, 21, 9) not glob '*[^0-9]*')
                )
            )
        )
        and substr(declared_at, 6, 2) between '01' and '12'
        and (
            (
                substr(declared_at, 6, 2) in ('01', '03', '05', '07', '08', '10', '12')
                and substr(declared_at, 9, 2) between '01' and '31'
            )
            or (
                substr(declared_at, 6, 2) in ('04', '06', '09', '11')
                and substr(declared_at, 9, 2) between '01' and '30'
            )
            or (
                substr(declared_at, 6, 2) = '02'
                and (
                    substr(declared_at, 9, 2) between '01' and '28'
                    or (
                        substr(declared_at, 9, 2) = '29'
                        and (
                            cast(substr(declared_at, 1, 4) as integer) % 400 = 0
                            or (
                                cast(substr(declared_at, 1, 4) as integer) % 4 = 0
                                and cast(substr(declared_at, 1, 4) as integer) % 100 <> 0
                            )
                        )
                    )
                )
            )
        )
        and substr(declared_at, 12, 2) between '00' and '23'
        and substr(declared_at, 15, 2) between '00' and '59'
        and substr(declared_at, 18, 2) between '00' and '59'
    ),
    check (
        parent_account_code is null or parent_account_code <> account_code
    ),
    check (
        (
            account_type = 'ASSET'
            and financial_position_line_classification in (
                'CURRENT_ASSET', 'NONCURRENT_ASSET', 'TRADE_RECEIVABLE'
            )
            and cash_flow_asset_classification in ('CASH_AND_CASH_EQUIVALENT', 'NON_CASH')
            and profit_and_loss_line_classification is null
        )
        or
        (
            account_type = 'LIABILITY'
            and financial_position_line_classification in (
                'CURRENT_LIABILITY', 'NONCURRENT_LIABILITY', 'TRADE_PAYABLE'
            )
            and cash_flow_asset_classification is null
            and profit_and_loss_line_classification is null
        )
        or
        (
            account_type = 'EQUITY'
            and financial_position_line_classification in (
                'EQUITY_CONTRIBUTION',
                'EQUITY_WITHDRAWAL',
                'RESULT_HOLDING',
                'RETAINED_ACCUMULATED',
                'RESERVE',
                'OTHER_EQUITY'
            )
            and cash_flow_asset_classification is null
            and profit_and_loss_line_classification is null
        )
        or
        (
            account_type = 'REVENUE'
            and financial_position_line_classification is null
            and cash_flow_asset_classification is null
            and profit_and_loss_line_classification in (
                'OPERATING_REVENUE',
                'SALES_DISCOUNT_ALLOWANCE',
                'OTHER_REVENUE',
                'FINANCE_INCOME'
            )
        )
        or
        (
            account_type = 'EXPENSE'
            and financial_position_line_classification is null
            and cash_flow_asset_classification is null
            and profit_and_loss_line_classification in (
                'COST_OF_SALES',
                'OPERATING_EXPENSE',
                'DEPRECIATION_AND_AMORTIZATION',
                'SETTLEMENT_FEE',
                'BAD_DEBT_WRITE_OFF',
                'FINANCE_EXPENSE',
                'OTHER_EXPENSE'
            )
        )
    )
) strict;
```
