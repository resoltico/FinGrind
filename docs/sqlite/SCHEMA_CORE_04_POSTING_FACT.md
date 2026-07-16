---
afad: "4.0"
version: "0.60.0"
domain: SQLITE_SCHEMA_CORE_POSTING_FACT
updated: "2026-07-11"
---

# SQLite Schema: Posting Fact

**Purpose**: Persisted posting identity, provenance, and replay fingerprint.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: `posting_fact`.

```sql
create table if not exists posting_fact (
    posting_order integer primary key,
    posting_id text not null unique,
    posting_kind text not null check (
        posting_kind in (
            'STANDARD',
            'OPENING_BALANCE',
            'INTERIM_RESULT_SWEEP',
            'FISCAL_YEAR_CLOSE'
        )
    ),
    posting_origin_kind text not null check (
        posting_origin_kind in (
            'DIRECT_JOURNAL',
            'SALE_SETTLED',
            'SALE_ON_CREDIT',
            'PURCHASE_SETTLED',
            'PURCHASE_ON_CREDIT',
            'INVENTORY_CAPITALIZATION_SETTLED',
            'INVENTORY_CAPITALIZATION_ON_CREDIT',
            'INVENTORY_WRITE_DOWN',
            'INVENTORY_SHRINKAGE',
            'INVENTORY_COUNT_INCREASE',
            'PREPAYMENT',
            'DEFERRED_REVENUE',
            'ACCRUED_EXPENSE',
            'ACCRUAL_CUTOFF_RECOGNITION',
            'ACCRUED_EXPENSE_SETTLEMENT',
            'LATVIAN_MONTHLY_PAYROLL',
            'LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT',
            'LATVIAN_PAYROLL_STATE_REMITTANCE',
            'FIXED_ASSET_CAPITALIZATION',
            'FIXED_ASSET_DEPRECIATION',
            'FIXED_ASSET_DISPOSAL',
            'FINANCING_BORROWING',
            'FINANCING_PRINCIPAL_REPAYMENT',
            'FINANCING_INTEREST_ACCRUAL',
            'FINANCING_INTEREST_PAYMENT',
            'FOREIGN_CURRENCY_OBLIGATION',
            'REALIZED_FOREIGN_EXCHANGE_SETTLEMENT',
            'EXPENSE_SETTLED',
            'EXPENSE_ON_CREDIT',
            'RECEIPT',
            'PAYMENT',
            'OWNER_CONTRIBUTION',
            'OWNER_WITHDRAWAL',
            'OPENING_POSITION',
            'REVERSAL',
            'INTERIM_RESULT_SWEEP',
            'FISCAL_YEAR_CLOSE'
        )
    ),
    entry_primary_debit_account_code text,
    entry_primary_credit_account_code text,
    entry_adjunct_account_code text,
    entry_amount_currency_code text check (
        entry_amount_currency_code is null
        or entry_amount_currency_code glob '[A-Z][A-Z][A-Z]'
    ),
    entry_amount_minor integer check (
        entry_amount_minor is null or entry_amount_minor > 0
    ),
    entry_adjunct_amount_minor integer check (
        entry_adjunct_amount_minor is null or entry_adjunct_amount_minor > 0
    ),
    entry_quantity text check (
        entry_quantity is null
        or (
            length(entry_quantity) between 1 and 64
            and entry_quantity = trim(entry_quantity)
            and entry_quantity not like '+%'
            and entry_quantity not like '-%'
            and entry_quantity not glob '*[^0-9.]*'
        )
    ),
    entry_unit_cost_currency_code text check (
        entry_unit_cost_currency_code is null
        or entry_unit_cost_currency_code glob '[A-Z][A-Z][A-Z]'
    ),
    entry_unit_cost_minor integer check (
        entry_unit_cost_minor is null or entry_unit_cost_minor > 0
    ),
    effective_date text not null check (
        effective_date glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'
        and substr(effective_date, 6, 2) between '01' and '12'
        and (
            (
                substr(effective_date, 6, 2) in ('01', '03', '05', '07', '08', '10', '12')
                and substr(effective_date, 9, 2) between '01' and '31'
            )
            or (
                substr(effective_date, 6, 2) in ('04', '06', '09', '11')
                and substr(effective_date, 9, 2) between '01' and '30'
            )
            or (
                substr(effective_date, 6, 2) = '02'
                and (
                    substr(effective_date, 9, 2) between '01' and '28'
                    or (
                        substr(effective_date, 9, 2) = '29'
                        and (
                            cast(substr(effective_date, 1, 4) as integer) % 400 = 0
                            or (
                                cast(substr(effective_date, 1, 4) as integer) % 4 = 0
                                and cast(substr(effective_date, 1, 4) as integer) % 100 <> 0
                            )
                        )
                    )
                )
            )
        )
    ),
    recorded_at text not null check (
        (
            recorded_at glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]Z'
            or (
                substr(recorded_at, 1, 19) glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]'
                and substr(recorded_at, 20, 1) = '.'
                and substr(recorded_at, length(recorded_at), 1) = 'Z'
                and (
                    (length(recorded_at) = 24 and substr(recorded_at, 21, 3) not glob '*[^0-9]*')
                    or (length(recorded_at) = 27 and substr(recorded_at, 21, 6) not glob '*[^0-9]*')
                    or (length(recorded_at) = 30 and substr(recorded_at, 21, 9) not glob '*[^0-9]*')
                )
            )
        )
        and substr(recorded_at, 6, 2) between '01' and '12'
        and (
            (
                substr(recorded_at, 6, 2) in ('01', '03', '05', '07', '08', '10', '12')
                and substr(recorded_at, 9, 2) between '01' and '31'
            )
            or (
                substr(recorded_at, 6, 2) in ('04', '06', '09', '11')
                and substr(recorded_at, 9, 2) between '01' and '30'
            )
            or (
                substr(recorded_at, 6, 2) = '02'
                and (
                    substr(recorded_at, 9, 2) between '01' and '28'
                    or (
                        substr(recorded_at, 9, 2) = '29'
                        and (
                            cast(substr(recorded_at, 1, 4) as integer) % 400 = 0
                            or (
                                cast(substr(recorded_at, 1, 4) as integer) % 4 = 0
                                and cast(substr(recorded_at, 1, 4) as integer) % 100 <> 0
                            )
                        )
                    )
                )
            )
        )
        and substr(recorded_at, 12, 2) between '00' and '23'
        and substr(recorded_at, 15, 2) between '00' and '59'
        and substr(recorded_at, 18, 2) between '00' and '59'
    ),
    actor_id text not null check (length(trim(actor_id)) > 0),
    actor_type text not null check (actor_type in ('PERSON', 'SYSTEM', 'AGENT')),
    command_id text not null check (length(trim(command_id)) > 0),
    idempotency_key text not null check (
        length(idempotency_key) between 1 and 128
        and idempotency_key glob '[A-Za-z0-9]*'
        and idempotency_key not glob '*[^A-Za-z0-9._:/-]*'
    ),
    causation_id text not null check (length(trim(causation_id)) > 0),
    correlation_id text check (correlation_id is null or length(trim(correlation_id)) > 0),
    reason text,
    source_channel text not null check (source_channel in ('CLI', 'SYSTEM')),
    prior_posting_id text,
    request_fingerprint_version integer not null check (request_fingerprint_version >= 1),
    request_fingerprint_sha256 text not null check (
        length(request_fingerprint_sha256) = 64
        and request_fingerprint_sha256 glob '[0-9a-f]*'
        and request_fingerprint_sha256 not glob '*[^0-9a-f]*'
    ),
    unique (idempotency_key),
    foreign key (entry_primary_debit_account_code) references account (account_code),
    foreign key (entry_primary_credit_account_code) references account (account_code),
    foreign key (entry_adjunct_account_code) references account (account_code),
    foreign key (prior_posting_id) references posting_fact (posting_id),
    check (
        (prior_posting_id is null and reason is null)
        or
        (prior_posting_id is not null and reason is not null)
    ),
    check (
        (
            posting_origin_kind in (
                'SALE_SETTLED',
                'SALE_ON_CREDIT',
                'INVENTORY_CAPITALIZATION_SETTLED',
                'INVENTORY_CAPITALIZATION_ON_CREDIT',
                'INVENTORY_WRITE_DOWN',
                'FIXED_ASSET_CAPITALIZATION',
                'FIXED_ASSET_DEPRECIATION',
                'FIXED_ASSET_DISPOSAL',
                'FINANCING_BORROWING',
                'FINANCING_PRINCIPAL_REPAYMENT',
                'FINANCING_INTEREST_ACCRUAL',
                'FINANCING_INTEREST_PAYMENT',
                'FOREIGN_CURRENCY_OBLIGATION',
                'REALIZED_FOREIGN_EXCHANGE_SETTLEMENT',
                'EXPENSE_SETTLED',
                'EXPENSE_ON_CREDIT',
                'OWNER_CONTRIBUTION',
                'OWNER_WITHDRAWAL'
            )
            and entry_primary_debit_account_code is not null
            and entry_primary_credit_account_code is not null
            and entry_adjunct_account_code is null
            and entry_amount_currency_code is not null
            and entry_amount_minor is not null
            and entry_adjunct_amount_minor is null
            and entry_unit_cost_currency_code is null
            and entry_unit_cost_minor is null
        )
        or (
            posting_origin_kind in (
                'PREPAYMENT',
                'DEFERRED_REVENUE',
                'ACCRUED_EXPENSE',
                'ACCRUAL_CUTOFF_RECOGNITION',
                'ACCRUED_EXPENSE_SETTLEMENT'
            )
            and entry_primary_debit_account_code is not null
            and entry_primary_credit_account_code is not null
            and entry_adjunct_account_code is null
            and entry_amount_currency_code is not null
            and entry_amount_minor is not null
            and entry_adjunct_amount_minor is null
            and entry_quantity is null
            and entry_unit_cost_currency_code is null
            and entry_unit_cost_minor is null
        )
        or (
            posting_origin_kind in (
                'PURCHASE_SETTLED',
                'PURCHASE_ON_CREDIT',
                'INVENTORY_COUNT_INCREASE'
            )
            and entry_primary_debit_account_code is not null
            and entry_primary_credit_account_code is not null
            and entry_adjunct_account_code is null
            and entry_amount_currency_code is null
            and entry_amount_minor is null
            and entry_adjunct_amount_minor is null
            and entry_quantity is not null
            and entry_unit_cost_currency_code is not null
            and entry_unit_cost_minor is not null
        )
        or (
            posting_origin_kind = 'INVENTORY_SHRINKAGE'
            and entry_primary_debit_account_code is not null
            and entry_primary_credit_account_code is not null
            and entry_adjunct_account_code is null
            and entry_amount_currency_code is null
            and entry_amount_minor is null
            and entry_adjunct_amount_minor is null
            and entry_quantity is not null
            and entry_unit_cost_currency_code is null
            and entry_unit_cost_minor is null
        )
        or (
            posting_origin_kind in (
                'SALE_SETTLED',
                'SALE_ON_CREDIT',
                'INVENTORY_CAPITALIZATION_SETTLED',
                'INVENTORY_CAPITALIZATION_ON_CREDIT',
                'INVENTORY_WRITE_DOWN',
                'FIXED_ASSET_CAPITALIZATION',
                'FIXED_ASSET_DEPRECIATION',
                'FIXED_ASSET_DISPOSAL',
                'FINANCING_BORROWING',
                'FINANCING_PRINCIPAL_REPAYMENT',
                'FINANCING_INTEREST_ACCRUAL',
                'FINANCING_INTEREST_PAYMENT',
                'FOREIGN_CURRENCY_OBLIGATION',
                'REALIZED_FOREIGN_EXCHANGE_SETTLEMENT',
                'EXPENSE_SETTLED',
                'EXPENSE_ON_CREDIT',
                'OWNER_CONTRIBUTION',
                'OWNER_WITHDRAWAL'
            )
            and entry_quantity is null
            and entry_unit_cost_currency_code is null
            and entry_unit_cost_minor is null
        )
        or (
            posting_origin_kind in ('RECEIPT', 'PAYMENT')
            and entry_primary_debit_account_code is not null
            and entry_primary_credit_account_code is not null
            and entry_amount_currency_code is not null
            and entry_amount_minor is not null
            and entry_quantity is null
            and entry_unit_cost_currency_code is null
            and entry_unit_cost_minor is null
            and (
                (
                    entry_adjunct_account_code is null
                    and entry_adjunct_amount_minor is null
                )
                or (
                    entry_adjunct_account_code is not null
                    and entry_adjunct_amount_minor is not null
                )
            )
        )
        or (
            posting_origin_kind in (
                'DIRECT_JOURNAL',
                'OPENING_POSITION',
                'REVERSAL',
                'INTERIM_RESULT_SWEEP',
                'FISCAL_YEAR_CLOSE',
                'LATVIAN_MONTHLY_PAYROLL',
                'LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT',
                'LATVIAN_PAYROLL_STATE_REMITTANCE'
            )
            and entry_primary_debit_account_code is null
            and entry_primary_credit_account_code is null
            and entry_adjunct_account_code is null
            and entry_amount_currency_code is null
            and entry_amount_minor is null
            and entry_adjunct_amount_minor is null
            and entry_quantity is null
            and entry_unit_cost_currency_code is null
            and entry_unit_cost_minor is null
        )
    )
) strict;
```
