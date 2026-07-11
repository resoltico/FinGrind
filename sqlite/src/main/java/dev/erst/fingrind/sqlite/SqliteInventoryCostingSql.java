package dev.erst.fingrind.sqlite;

/** Canonical SQLite SQL literals for the inventory movement ledger and on-hand materialization. */
final class SqliteInventoryCostingSql {
  static final String INSERT_INVENTORY_MOVEMENT =
      """
      insert into inventory_movement (
          movement_id,
          inventory_account,
          effective_date,
          account_sequence,
          kind,
          quantity_delta,
          cost_delta_minor,
          posting_id
      )
      select
          ?,
          ?,
          ?,
          coalesce(max(account_sequence), 0) + 1,
          ?,
          ?,
          ?,
          ?
      from inventory_movement
      where inventory_account = ?
      returning account_sequence
      """;

  static final String UPSERT_INVENTORY_ON_HAND =
      """
      insert into inventory_on_hand (
          inventory_account,
          quantity,
          cost_pool_minor,
          last_movement_date
      ) values (?, ?, ?, ?)
      on conflict (inventory_account) do update set
          quantity = excluded.quantity,
          cost_pool_minor = excluded.cost_pool_minor,
          last_movement_date = excluded.last_movement_date
      """;

  static final String LOAD_INVENTORY_MOVEMENT_REPLAY_ROWS =
      """
      select
          inventory_account,
          effective_date,
          account_sequence,
          quantity_delta,
          cost_delta_minor
      from inventory_movement
      order by inventory_account, effective_date, account_sequence
      """;

  static final String LOAD_INVENTORY_MOVEMENTS_BY_POSTING_ID =
      """
      select
          inventory_account,
          effective_date,
          kind,
          quantity_delta,
          cost_delta_minor
      from inventory_movement
      where posting_id = ?
      order by effective_date, account_sequence
      """;

  static final String LOAD_INVENTORY_VALUATION_MOVEMENTS =
      """
      select
          inventory_account,
          effective_date,
          account_sequence,
          kind,
          quantity_delta,
          cost_delta_minor,
          posting_id
      from inventory_movement
      where (? is null or effective_date <= ?)
      order by inventory_account, effective_date, account_sequence
      """;

  static final String LOAD_COSTED_SALE_MOVEMENT =
      """
      select
          inventory_movement.inventory_account,
          inventory_movement.effective_date,
          inventory_movement.account_sequence,
          inventory_movement.quantity_delta,
          inventory_movement.cost_delta_minor,
          account.quantity_scale
      from inventory_movement
      inner join account on account.account_code = inventory_movement.inventory_account
      where inventory_movement.posting_id = ?
          and inventory_movement.kind = 'DISPOSAL'
      order by inventory_movement.inventory_account, inventory_movement.account_sequence
      """;

  static final String LOAD_INVENTORY_MOVEMENTS_BEFORE =
      """
      select quantity_delta, cost_delta_minor
      from inventory_movement
      where inventory_account = ?
          and (
              effective_date < ?
              or (effective_date = ? and account_sequence < ?)
          )
      order by effective_date, account_sequence
      """;

  static final String LOAD_OPENING_INVENTORY_QUANTITIES_BY_POSTING_ID =
      """
      select
          inventory_movement.inventory_account,
          inventory_movement.quantity_delta,
          account.quantity_scale
      from inventory_movement
      inner join account on account.account_code = inventory_movement.inventory_account
      where inventory_movement.posting_id = ?
          and inventory_movement.kind = 'OPENING'
      order by inventory_movement.account_sequence
      """;

  static final String LOAD_INVENTORY_ON_HAND_ROWS =
      """
      select
          inventory_account,
          quantity,
          cost_pool_minor,
          last_movement_date
      from inventory_on_hand
      order by inventory_account
      """;

  static final String LOAD_INVENTORY_ON_HAND_BY_ACCOUNT =
      """
      select
          quantity,
          cost_pool_minor,
          last_movement_date
      from inventory_on_hand
      where inventory_account = ?
      limit 1
      """;

  private SqliteInventoryCostingSql() {}
}
