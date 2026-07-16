---
afad: "4.0"
version: "0.61.0"
domain: SQLITE_SCHEMA_CORE_INVENTORY_ON_HAND
updated: "2026-07-16"
---

# SQLite Schema: Inventory On-Hand State

**Purpose**: Materialized quantity and cost-pool state plus inventory-account admission gates.
**Source of truth**: [`book_schema.sql`](../../sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql)
**Generation**: This page is rendered from the canonical schema file by `scripts/render-sqlite-schema-doc.py`. **Coverage**: `inventory_on_hand` and inventory-on-hand validation triggers.

```sql
create table if not exists inventory_on_hand (
    inventory_account text primary key references account (account_code),
    quantity integer not null check (quantity >= 0),
    cost_pool_minor integer not null check (cost_pool_minor >= 0),
    last_movement_date text not null check (
        last_movement_date glob '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'
        and substr(last_movement_date, 6, 2) between '01' and '12'
        and (
            (
                substr(last_movement_date, 6, 2) in ('01', '03', '05', '07', '08', '10', '12')
                and substr(last_movement_date, 9, 2) between '01' and '31'
            )
            or (
                substr(last_movement_date, 6, 2) in ('04', '06', '09', '11')
                and substr(last_movement_date, 9, 2) between '01' and '30'
            )
            or (
                substr(last_movement_date, 6, 2) = '02'
                and (
                    substr(last_movement_date, 9, 2) between '01' and '28'
                    or (
                        substr(last_movement_date, 9, 2) = '29'
                        and (
                            cast(substr(last_movement_date, 1, 4) as integer) % 400 = 0
                            or (
                                cast(substr(last_movement_date, 1, 4) as integer) % 4 = 0
                                and cast(substr(last_movement_date, 1, 4) as integer) % 100 <> 0
                            )
                        )
                    )
                )
            )
        )
    ),
    check ((quantity = 0) = (cost_pool_minor = 0))
) strict;

create trigger if not exists inventory_on_hand_validate_inventory_account_on_insert
before insert on inventory_on_hand
begin
    select raise(
        fail,
        'inventory_on_hand rows must reference one active postable inventory account.'
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
end;

create trigger if not exists inventory_on_hand_validate_inventory_account_on_update
before update on inventory_on_hand
begin
    select raise(
        fail,
        'inventory_on_hand rows must reference one active postable inventory account.'
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
end;
```
