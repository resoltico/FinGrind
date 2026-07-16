package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.WeightedAverageCostingMath;
import dev.erst.fingrind.executor.bookkeeping.InventoryAccountState;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.Objects;
import java.util.Optional;

/** Reads one materialized inventory state for transactional posting validation. */
final class SqliteTransactionInventoryValidationLookup {
  private SqliteTransactionInventoryValidationLookup() {}

  static Optional<InventoryAccountState> findState(
      SqliteNativeDatabase activeDatabase,
      RegisteredAccount inventoryAccount,
      dev.erst.fingrind.core.BookIdentity bookIdentity) {
    var unitOfMeasure = Objects.requireNonNull(inventoryAccount.unitOfMeasure(), "unitOfMeasure");
    return SqliteStatementQueries.queryWithStatement(
        activeDatabase,
        SqliteInventoryCostingSql.LOAD_INVENTORY_ON_HAND_BY_ACCOUNT,
        statement -> {
          statement.bindText(1, inventoryAccount.accountCode().value());
          if (statement.step() == SqliteNativeResultCode.code("DONE")) {
            return Optional.empty();
          }
          InventoryAccountState state =
              new InventoryAccountState(
                  new WeightedAverageCostingMath.InventoryPool(
                      Quantity.ofScaledUnits(
                          unitOfMeasure.quantityScale(), statement.columnLong(0)),
                      Money.ofMinorUnits(
                          bookIdentity.functionalCurrency(), statement.columnLong(1))),
                  Optional.of(
                      CanonicalTemporalText.parseLocalDate(
                          SqlitePostingMapper.requiredText(statement, 2),
                          "inventoryOnHand.lastMovementDate")));
          if (statement.step() != SqliteNativeResultCode.code("DONE")) {
            throw new IllegalStateException(
                "SQLite inventory_on_hand query returned more than one row for account "
                    + inventoryAccount.accountCode().value()
                    + ".");
          }
          return Optional.of(state);
        });
  }
}
