---
afad: "5.0.1"
version: "0.62.1"
domain: SQLITE_SCHEMA_CORE_REALIZED_FOREIGN_EXCHANGE
updated: "2026-08-05"
---

# SQLite Schema: Realized Foreign-Exchange Settlement

**Purpose**: Foreign-currency receivable obligations and their one-time realized-settlement facts.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: `foreign_currency_obligation`, `foreign_currency_obligation_settlement`, compensating reversal links, and lifecycle validation/append-only triggers.

```sql
create table if not exists foreign_currency_obligation (
    foreign_currency_obligation_id text primary key check (
        length(foreign_currency_obligation_id) between 1 and 120
        and foreign_currency_obligation_id glob '[a-z0-9]*'
        and foreign_currency_obligation_id not glob '*[^a-z0-9-]*'
        and foreign_currency_obligation_id not like '-%'
        and foreign_currency_obligation_id not like '%-'
        and foreign_currency_obligation_id not like '%--%'
    ),
    origin_posting_id text not null unique references posting_fact (posting_id),
    originated_on text not null,
    receivable_account_code text not null references account (account_code),
    realized_gain_account_code text not null references account (account_code),
    realized_loss_account_code text not null references account (account_code),
    transaction_currency_code text not null check (transaction_currency_code glob '[A-Z][A-Z][A-Z]'),
    transaction_amount_minor integer not null check (transaction_amount_minor > 0),
    functional_currency_code text not null check (functional_currency_code glob '[A-Z][A-Z][A-Z]'),
    functional_carrying_amount_minor integer not null check (functional_carrying_amount_minor > 0),
    check (transaction_currency_code <> functional_currency_code and receivable_account_code <> realized_gain_account_code and receivable_account_code <> realized_loss_account_code and realized_gain_account_code <> realized_loss_account_code)
) strict;

create table if not exists foreign_currency_obligation_settlement (
    settlement_posting_id text primary key references posting_fact (posting_id),
    foreign_currency_obligation_id text not null references foreign_currency_obligation (foreign_currency_obligation_id),
    effective_date text not null,
    functional_currency_code text not null check (functional_currency_code glob '[A-Z][A-Z][A-Z]'),
    functional_settlement_amount_minor integer not null check (functional_settlement_amount_minor > 0)
) strict;

create table if not exists foreign_currency_obligation_reversal (
    reversal_posting_id text primary key references posting_fact (posting_id),
    foreign_currency_obligation_id text not null unique references foreign_currency_obligation (foreign_currency_obligation_id)
) strict;

create table if not exists foreign_currency_obligation_settlement_reversal (
    reversal_posting_id text primary key references posting_fact (posting_id),
    settlement_posting_id text not null unique references foreign_currency_obligation_settlement (settlement_posting_id)
) strict;

create trigger if not exists foreign_currency_obligation_validate_origin_on_insert
before insert on foreign_currency_obligation
begin
    select raise(fail, 'foreign_currency_obligation origin must be the matching typed receivable posting.')
    where not exists (select 1 from posting_fact where posting_id = new.origin_posting_id and posting_origin_kind = 'FOREIGN_CURRENCY_OBLIGATION' and effective_date = new.originated_on);
    select raise(fail, 'foreign_currency_obligation must match retained posting foreign-exchange facts.')
    where not exists (select 1 from posting_foreign_exchange where posting_id = new.origin_posting_id and transaction_currency_code = new.transaction_currency_code and transaction_amount_minor = new.transaction_amount_minor and functional_currency_code = new.functional_currency_code and functional_amount_minor = new.functional_carrying_amount_minor);
    select raise(fail, 'foreign_currency_obligation requires receivable, revenue, and expense accounts.')
    where not exists (select 1 from account where account_code = new.receivable_account_code and account_type = 'ASSET' and financial_position_line_classification = 'TRADE_RECEIVABLE')
        or not exists (select 1 from account where account_code = new.realized_gain_account_code and account_type = 'REVENUE')
        or not exists (select 1 from account where account_code = new.realized_loss_account_code and account_type = 'EXPENSE');
end;

create trigger if not exists foreign_currency_obligation_settlement_validate_on_insert
before insert on foreign_currency_obligation_settlement
begin
    select raise(fail, 'foreign_currency obligation settlement must use the matching typed settlement posting.')
    where not exists (select 1 from posting_fact where posting_id = new.settlement_posting_id and posting_origin_kind = 'REALIZED_FOREIGN_EXCHANGE_SETTLEMENT' and effective_date = new.effective_date);
    select raise(fail, 'foreign_currency obligation settlement must use the retained functional currency.')
    where exists (select 1 from foreign_currency_obligation where foreign_currency_obligation_id = new.foreign_currency_obligation_id and functional_currency_code <> new.functional_currency_code);
    select raise(fail, 'foreign_currency obligation settlement must match retained transaction and functional foreign-exchange facts.')
    where not exists (
        select 1
        from foreign_currency_obligation obligation
        inner join posting_foreign_exchange foreign_exchange
            on foreign_exchange.posting_id = new.settlement_posting_id
        where obligation.foreign_currency_obligation_id = new.foreign_currency_obligation_id
            and foreign_exchange.transaction_currency_code = obligation.transaction_currency_code
            and foreign_exchange.transaction_amount_minor = obligation.transaction_amount_minor
            and foreign_exchange.functional_currency_code = new.functional_currency_code
            and foreign_exchange.functional_amount_minor = new.functional_settlement_amount_minor
    );
    select raise(fail, 'foreign_currency obligation settlement must not precede its origin.')
    where exists (select 1 from foreign_currency_obligation where foreign_currency_obligation_id = new.foreign_currency_obligation_id and new.effective_date < originated_on);
    select raise(fail, 'foreign_currency obligation settlement must not precede its lifecycle horizon.')
    where exists (
        select 1
        from foreign_currency_obligation_settlement settlement
        where settlement.foreign_currency_obligation_id = new.foreign_currency_obligation_id
            and settlement.effective_date > new.effective_date
    );
    select raise(fail, 'foreign_currency obligation accepts only one active settlement.')
    where exists (
        select 1
        from foreign_currency_obligation_settlement settlement
        where settlement.foreign_currency_obligation_id = new.foreign_currency_obligation_id
            and not exists (
                select 1 from foreign_currency_obligation_settlement_reversal reversal
                where reversal.settlement_posting_id = settlement.settlement_posting_id
            )
    );
end;

create trigger if not exists foreign_currency_obligation_reversal_validate_on_insert
before insert on foreign_currency_obligation_reversal
begin
    select raise(fail, 'foreign_currency obligation reversal must negate its receivable posting.')
    where not exists (
        select 1
        from foreign_currency_obligation obligation
        inner join posting_fact reversal on reversal.posting_id = new.reversal_posting_id
        where obligation.foreign_currency_obligation_id = new.foreign_currency_obligation_id
            and reversal.posting_origin_kind = 'REVERSAL'
            and reversal.prior_posting_id = obligation.origin_posting_id
    );
    select raise(fail, 'foreign_currency obligation cannot be reversed while an active settlement remains.')
    where exists (
        select 1
        from foreign_currency_obligation_settlement settlement
        where settlement.foreign_currency_obligation_id = new.foreign_currency_obligation_id
            and not exists (
                select 1 from foreign_currency_obligation_settlement_reversal reversal
                where reversal.settlement_posting_id = settlement.settlement_posting_id
            )
    );
end;

create trigger if not exists foreign_currency_obligation_settlement_reversal_validate_on_insert
before insert on foreign_currency_obligation_settlement_reversal
begin
    select raise(fail, 'foreign_currency obligation settlement reversal must negate its settlement posting.')
    where not exists (
        select 1
        from foreign_currency_obligation_settlement settlement
        inner join posting_fact reversal on reversal.posting_id = new.reversal_posting_id
        where settlement.settlement_posting_id = new.settlement_posting_id
            and reversal.posting_origin_kind = 'REVERSAL'
            and reversal.prior_posting_id = settlement.settlement_posting_id
    );
end;

create trigger if not exists foreign_currency_obligation_reject_update before update on foreign_currency_obligation begin select raise(fail, 'foreign_currency_obligation rows are append-only.'); end;
create trigger if not exists foreign_currency_obligation_reject_delete before delete on foreign_currency_obligation begin select raise(fail, 'foreign_currency_obligation rows are append-only.'); end;
create trigger if not exists foreign_currency_obligation_settlement_reject_update before update on foreign_currency_obligation_settlement begin select raise(fail, 'foreign_currency_obligation_settlement rows are append-only.'); end;
create trigger if not exists foreign_currency_obligation_settlement_reject_delete before delete on foreign_currency_obligation_settlement begin select raise(fail, 'foreign_currency_obligation_settlement rows are append-only.'); end;
create trigger if not exists foreign_currency_obligation_reversal_reject_update before update on foreign_currency_obligation_reversal begin select raise(fail, 'foreign_currency_obligation_reversal rows are append-only.'); end;
create trigger if not exists foreign_currency_obligation_reversal_reject_delete before delete on foreign_currency_obligation_reversal begin select raise(fail, 'foreign_currency_obligation_reversal rows are append-only.'); end;
create trigger if not exists foreign_currency_obligation_settlement_reversal_reject_update before update on foreign_currency_obligation_settlement_reversal begin select raise(fail, 'foreign_currency_obligation_settlement_reversal rows are append-only.'); end;
create trigger if not exists foreign_currency_obligation_settlement_reversal_reject_delete before delete on foreign_currency_obligation_settlement_reversal begin select raise(fail, 'foreign_currency_obligation_settlement_reversal rows are append-only.'); end;
```
