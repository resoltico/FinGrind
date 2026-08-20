---
afad: "5.0.1"
version: "0.63.0"
domain: SQLITE_SCHEMA_CORE_POSTING_FOREIGN_EXCHANGE
updated: "2026-08-20"
---

# SQLite Schema: Posting Foreign Exchange

**Purpose**: Per-posting owned foreign-exchange facts, posting-origin and functional-currency admissibility rules, and foreign-exchange append-only enforcement.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: `posting_foreign_exchange` and posting-foreign-exchange validation/append-only triggers.

```sql
create table if not exists posting_foreign_exchange (
    posting_id text primary key references posting_fact (posting_id),
    treatment_kind text not null check (
        treatment_kind in (
            'SPOT_TRANSACTION',
            'UNREALIZED_REMEASUREMENT'
        )
    ),
    transaction_currency_code text not null check (
        length(transaction_currency_code) = 3
        and transaction_currency_code glob '[A-Z][A-Z][A-Z]'
    ),
    transaction_amount_minor integer not null check (transaction_amount_minor > 0),
    functional_currency_code text not null check (
        length(functional_currency_code) = 3
        and functional_currency_code glob '[A-Z][A-Z][A-Z]'
    ),
    functional_amount_minor integer not null check (functional_amount_minor > 0),
    quoted_transaction_amount_minor integer not null check (quoted_transaction_amount_minor > 0),
    quoted_functional_amount_minor integer not null check (quoted_functional_amount_minor > 0),
    quoted_on text not null check (
        quoted_on glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'
        and substr(quoted_on, 6, 2) between '01' and '12'
        and (
            (
                substr(quoted_on, 6, 2) in ('01', '03', '05', '07', '08', '10', '12')
                and substr(quoted_on, 9, 2) between '01' and '31'
            )
            or (
                substr(quoted_on, 6, 2) in ('04', '06', '09', '11')
                and substr(quoted_on, 9, 2) between '01' and '30'
            )
            or (
                substr(quoted_on, 6, 2) = '02'
                and (
                    substr(quoted_on, 9, 2) between '01' and '28'
                    or (
                        substr(quoted_on, 9, 2) = '29'
                        and (
                            cast(substr(quoted_on, 1, 4) as integer) % 400 = 0
                            or (
                                cast(substr(quoted_on, 1, 4) as integer) % 4 = 0
                                and cast(substr(quoted_on, 1, 4) as integer) % 100 <> 0
                            )
                        )
                    )
                )
            )
        )
    ),
    quote_source text not null check (length(trim(quote_source)) between 1 and 200),
    check (transaction_currency_code <> functional_currency_code)
) strict;

create trigger if not exists posting_foreign_exchange_validate_origin_on_insert
before insert on posting_foreign_exchange
begin
    select raise(fail, 'posting_foreign_exchange requires one foreign-exchange-capable posting origin.')
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.posting_origin_kind not in (
                'DIRECT_JOURNAL',
                'SALE_SETTLED',
                'SALE_ON_CREDIT',
                'PURCHASE_SETTLED',
                'PURCHASE_ON_CREDIT',
                'INVENTORY_CAPITALIZATION_SETTLED',
                'INVENTORY_CAPITALIZATION_ON_CREDIT',
                'EXPENSE_SETTLED',
                'EXPENSE_ON_CREDIT',
                'OWNER_CONTRIBUTION',
                'OWNER_WITHDRAWAL',
                'FOREIGN_CURRENCY_OBLIGATION',
                'REALIZED_FOREIGN_EXCHANGE_SETTLEMENT',
                'REVERSAL'
            )
    );
    select raise(fail, 'posting_foreign_exchange functional currency must match the book functional currency.')
    where exists (
        select 1
        from book_identity
        where
            book_identity.singleton_id = 1
            and new.functional_currency_code <> book_identity.functional_currency_code
    );
    select raise(fail, 'posting_foreign_exchange typed entry amount must match the retained functional amount.')
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.entry_amount_currency_code is not null
            and (
                posting_fact.entry_amount_currency_code <> new.functional_currency_code
                or posting_fact.entry_amount_minor <> new.functional_amount_minor
            )
    );
end;

create trigger if not exists posting_foreign_exchange_reject_update
before update on posting_foreign_exchange
begin
    select raise(fail, 'posting_foreign_exchange rows are append-only.');
end;

create trigger if not exists posting_foreign_exchange_reject_delete
before delete on posting_foreign_exchange
begin
    select raise(fail, 'posting_foreign_exchange rows are append-only.');
end;
```
