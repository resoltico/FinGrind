package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.InventoryMovementKind;
import dev.erst.fingrind.core.PostingId;
import java.time.LocalDate;
import java.util.Objects;

/** Shared SQLite write helpers for the durable inventory movement ledger and on-hand state. */
final class SqliteInventoryCostingWriter {
  private SqliteInventoryCostingWriter() {}

  static int insertInventoryMovement(
      SqliteNativeDatabase activeDatabase,
      String movementId,
      AccountCode inventoryAccount,
      LocalDate effectiveDate,
      InventoryMovementKind kind,
      long quantityDelta,
      long costDeltaMinor,
      PostingId postingId) {
    Objects.requireNonNull(activeDatabase, "activeDatabase");
    Objects.requireNonNull(movementId, "movementId");
    Objects.requireNonNull(inventoryAccount, "inventoryAccount");
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(postingId, "postingId");
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqliteInventoryCostingSql.INSERT_INVENTORY_MOVEMENT)) {
      statement.bindText(1, movementId);
      statement.bindText(2, inventoryAccount.value());
      statement.bindText(3, CanonicalTemporalText.formatLocalDate(effectiveDate));
      statement.bindText(4, kind.wireValue());
      statement.bindLong(5, quantityDelta);
      statement.bindLong(6, costDeltaMinor);
      statement.bindText(7, postingId.value());
      statement.bindText(8, inventoryAccount.value());
      if (statement.step() != SqliteNativeResultCode.code("ROW")) {
        throw new IllegalStateException(
            "SQLite inventory movement insert returned no account sequence.");
      }
      int accountSequence = statement.columnInt(0);
      if (statement.step() != SqliteNativeResultCode.code("DONE")) {
        throw new IllegalStateException(
            "SQLite inventory movement insert returned more than one account sequence.");
      }
      return accountSequence;
    }
  }

  static void upsertInventoryOnHand(
      SqliteNativeDatabase activeDatabase,
      AccountCode inventoryAccount,
      long quantity,
      long costPoolMinor,
      LocalDate lastMovementDate) {
    Objects.requireNonNull(activeDatabase, "activeDatabase");
    Objects.requireNonNull(inventoryAccount, "inventoryAccount");
    Objects.requireNonNull(lastMovementDate, "lastMovementDate");
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqliteInventoryCostingSql.UPSERT_INVENTORY_ON_HAND)) {
      statement.bindText(1, inventoryAccount.value());
      statement.bindLong(2, quantity);
      statement.bindLong(3, costPoolMinor);
      statement.bindText(4, CanonicalTemporalText.formatLocalDate(lastMovementDate));
      statement.step();
    }
  }
}
