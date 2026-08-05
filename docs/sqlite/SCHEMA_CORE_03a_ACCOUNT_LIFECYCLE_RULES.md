---
afad: "5.0.1"
version: "0.62.1"
domain: SQLITE_SCHEMA_CORE_ACCOUNT_LIFECYCLE_RULES
updated: "2026-08-05"
---

# SQLite Schema: Account Lifecycle Rules

**Purpose**: Account amendment, retirement, relationship-mutation, and deletion safeguards.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: Account validation triggers on update and append-only delete rejection.

```sql
create trigger if not exists account_validate_lifecycle_update
before update on account
begin
    select raise(fail, 'account code and declared-at are immutable.')
    where
        old.account_code <> new.account_code
        or old.declared_at <> new.declared_at;
    select raise(fail, 'account definition has dependents.')
    where
        (
            old.account_name <> new.account_name
            or old.account_type <> new.account_type
            or old.account_node_kind <> new.account_node_kind
            or coalesce(old.parent_account_code, '') <> coalesce(new.parent_account_code, '')
            or coalesce(old.contra_of_account_code, '') <> coalesce(new.contra_of_account_code, '')
            or coalesce(old.financial_position_line_classification, '')
            <> coalesce(new.financial_position_line_classification, '')
            or coalesce(old.cash_flow_asset_classification, '')
            <> coalesce(new.cash_flow_asset_classification, '')
            or coalesce(old.profit_and_loss_line_classification, '')
            <> coalesce(new.profit_and_loss_line_classification, '')
            or coalesce(old.unit_of_measure, '') <> coalesce(new.unit_of_measure, '')
            or coalesce(old.quantity_scale, -1) <> coalesce(new.quantity_scale, -1)
        )
        and (
            exists (select 1 from journal_line where account_code = old.account_code)
            or exists (
                select 1
                from tax_registration
                where payable_account_code = old.account_code
                    or recoverable_account_code = old.account_code
            )
            or exists (select 1 from account where parent_account_code = old.account_code)
            or exists (select 1 from account where contra_of_account_code = old.account_code)
        );
    select raise(fail, 'retired accounts must have zero current balance.')
    where
        old.active = 1
        and new.active = 0
        and exists (
            select 1
            from journal_line
            where account_code = old.account_code
            group by currency_code
            having sum(case entry_side when 'DEBIT' then amount_minor else -amount_minor end) <> 0
        );
    select raise(fail, 'retired accounts must have no live operational bindings.')
    where
        old.active = 1
        and new.active = 0
        and (
            exists (
                select 1
                from tax_registration
                where payable_account_code = old.account_code
                    or recoverable_account_code = old.account_code
            )
            or exists (select 1 from account where parent_account_code = old.account_code)
            or exists (select 1 from account where contra_of_account_code = old.account_code)
        );
end;

create trigger if not exists account_validate_contra_on_update
before update on account
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

create trigger if not exists account_validate_parent_on_update
before update on account
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

create trigger if not exists account_reject_delete
before delete on account
begin
    select raise(fail, 'account rows are append-only.');
end;
```
