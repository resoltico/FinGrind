---
afad: "5.0.1"
version: "0.64.0"
domain: SQLITE_SCHEMA_CORE_ACCOUNT_DECLARATION_RULES
updated: "2026-09-01"
---

# SQLite Schema: Account Declaration Rules

**Purpose**: Parent-shape and contra-relationship admission rules when accounts are declared.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: Account parent and contra-account validation triggers on insert.

```sql
create trigger if not exists account_validate_parent_on_insert
before insert on account
when new.parent_account_code is not null
begin
    select raise(fail, 'account parent must be active.')
    where exists (
        select 1
        from account as parent
        where
            parent.account_code = new.parent_account_code
            and parent.active = 0
    );
    select raise(fail, 'account parent must share the child account type.')
    where exists (
        select 1
        from account as parent
        where
            parent.account_code = new.parent_account_code
            and parent.account_type <> new.account_type
    );
    select raise(fail, 'account parent must be a header node.')
    where exists (
        select 1
        from account as parent
        where
            parent.account_code = new.parent_account_code
            and parent.account_node_kind <> 'HEADER'
    );
    select raise(fail, 'account parent must share the child statement classification.')
    where exists (
        select 1
        from account as parent
        where
            parent.account_code = new.parent_account_code
            and (
                coalesce(parent.financial_position_line_classification, '')
                    <> coalesce(new.financial_position_line_classification, '')
                or coalesce(parent.cash_flow_asset_classification, '')
                    <> coalesce(new.cash_flow_asset_classification, '')
                or coalesce(parent.profit_and_loss_line_classification, '')
                    <> coalesce(new.profit_and_loss_line_classification, '')
            )
    );
    with recursive ancestors (account_code, parent_account_code) as (
        select
            account_seed.account_code,
            account_seed.parent_account_code
        from account as account_seed
        where account_seed.account_code = new.parent_account_code
        union all
        select
            account.account_code,
            account.parent_account_code
        from account
        inner join ancestors on account.account_code = ancestors.parent_account_code
    )

    select raise(fail, 'account hierarchy cycle.')
    where exists (
        select 1
        from ancestors
        where ancestors.account_code = new.account_code
    );
end;

create trigger if not exists account_validate_contra_on_insert
before insert on account
when new.contra_of_account_code is not null
begin
    select raise(fail, 'contra account must be postable.')
    where new.account_node_kind <> 'POSTABLE';
    select raise(fail, 'contra account target must be active.')
    where exists (
        select 1 from account as target
        where target.account_code = new.contra_of_account_code and target.active = 0
    );
    select raise(fail, 'contra account target must be postable.')
    where exists (
        select 1 from account as target
        where target.account_code = new.contra_of_account_code and target.account_node_kind <> 'POSTABLE'
    );
    select raise(fail, 'contra account target must not itself be a contra account.')
    where exists (
        select 1 from account as target
        where target.account_code = new.contra_of_account_code and target.contra_of_account_code is not null
    );
    select raise(fail, 'contra account target must share the account type.')
    where exists (
        select 1 from account as target
        where target.account_code = new.contra_of_account_code and target.account_type <> new.account_type
    );
    select raise(fail, 'contra account target must have a compatible statement taxonomy.')
    where exists (
        select 1 from account as target
        where target.account_code = new.contra_of_account_code and (
            not (
                target.account_type = 'REVENUE'
                and target.profit_and_loss_line_classification = 'OPERATING_REVENUE'
                and new.profit_and_loss_line_classification = 'SALES_DISCOUNT_ALLOWANCE'
            )
            and (
                coalesce(target.financial_position_line_classification, '') <> coalesce(new.financial_position_line_classification, '')
                or coalesce(target.cash_flow_asset_classification, '') <> coalesce(new.cash_flow_asset_classification, '')
                or coalesce(target.profit_and_loss_line_classification, '') <> coalesce(new.profit_and_loss_line_classification, '')
            )
        )
    );
end;
```
