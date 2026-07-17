---
afad: "4.0"
version: "0.61.0"
domain: SQLITE_SCHEMA_CORE_INVENTORY_MOVEMENT
updated: "2026-07-16"
---

# SQLite Schema: Inventory Movement Ledger

**Purpose**: Append-only inventory movement replay, ordering, provenance, and opening-balance admission gates.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: `inventory_movement` and inventory-movement validation triggers.

```sql
create table if not exists inventory_movement (
    movement_id text primary key check (length(trim(movement_id)) > 0),
    inventory_account text not null references account (account_code),
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
    account_sequence integer not null check (account_sequence >= 1),
    kind text not null check (
        kind in (
            'ACQUISITION',
            'CAPITALIZATION',
            'COUNT_INCREASE',
            'OPENING',
            'DISPOSAL',
            'WRITE_DOWN',
            'SHRINKAGE',
            'REVERSAL_COMP'
        )
    ),
    quantity_delta integer not null,
    cost_delta_minor integer not null,
    posting_id text not null references posting_fact (posting_id),
    unique (inventory_account, account_sequence),
    check (quantity_delta <> 0 or cost_delta_minor <> 0)
) strict;

create trigger if not exists inventory_movement_validate_inventory_account_on_insert
before insert on inventory_movement
begin
    select raise(
        fail,
        'inventory movements must reference one active postable inventory account.'
    )
    where exists (
        select 1
        from account
        where
            account.account_code = new.inventory_account
            and (
                account.active = 0
                or account.account_type <> 'ASSET'
                or account.account_node_kind <> 'POSTABLE'
                or account.financial_position_line_classification <> 'INVENTORY'
            )
    );
    select raise(
        fail,
        'inventory movement effective date must match the referenced posting effective date.'
    )
    where exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and posting_fact.effective_date <> new.effective_date
    );
end;

create trigger if not exists inventory_movement_validate_account_horizon_on_insert
before insert on inventory_movement
when exists (select 1 from inventory_movement where inventory_account = new.inventory_account)
begin
    select raise(
        fail,
        'inventory movements must append in non-decreasing effective-date order per account.'
    )
    where new.effective_date < (
        select max(effective_date)
        from inventory_movement
        where inventory_account = new.inventory_account
    );
end;

create trigger if not exists inventory_movement_validate_account_sequence_on_insert
before insert on inventory_movement
begin
    select raise(
        fail,
        'inventory movements must append with the next store-owned account sequence per account.'
    )
    where new.account_sequence <> (
        select coalesce(max(account_sequence), 0) + 1
        from inventory_movement
        where inventory_account = new.inventory_account
    );
end;

create trigger if not exists inventory_movement_validate_typed_posting_origin_on_insert
before insert on inventory_movement
begin
    select raise(
        fail,
        'inventory movements require a matching typed posting origin.'
    )
    where not exists (
        select 1
        from posting_fact
        where
            posting_fact.posting_id = new.posting_id
            and (
                (
                    new.kind = 'ACQUISITION'
                    and posting_fact.posting_origin_kind in ('PURCHASE_SETTLED', 'PURCHASE_ON_CREDIT')
                )
                or (
                    new.kind = 'CAPITALIZATION'
                    and posting_fact.posting_origin_kind in (
                        'INVENTORY_CAPITALIZATION_SETTLED',
                        'INVENTORY_CAPITALIZATION_ON_CREDIT'
                    )
                )
                or (
                    new.kind = 'COUNT_INCREASE'
                    and posting_fact.posting_origin_kind = 'INVENTORY_COUNT_INCREASE'
                )
                or (
                    new.kind = 'OPENING'
                    and posting_fact.posting_origin_kind = 'OPENING_POSITION'
                )
                or (
                    new.kind = 'DISPOSAL'
                    and posting_fact.posting_origin_kind in ('SALE_SETTLED', 'SALE_ON_CREDIT')
                )
                or (
                    new.kind = 'WRITE_DOWN'
                    and posting_fact.posting_origin_kind = 'INVENTORY_WRITE_DOWN'
                )
                or (
                    new.kind = 'SHRINKAGE'
                    and posting_fact.posting_origin_kind = 'INVENTORY_SHRINKAGE'
                )
                or (
                    new.kind = 'REVERSAL_COMP'
                    and posting_fact.posting_origin_kind = 'REVERSAL'
                )
            )
    );
end;

create trigger if not exists inventory_movement_validate_opening_on_insert
before insert on inventory_movement
when new.kind = 'OPENING'
begin
    select raise(
        fail,
        'inventory opening movements must be the first durable movement for their inventory account.'
    )
    where exists (
        select 1
        from inventory_movement
        where inventory_account = new.inventory_account
    );
end;
```
