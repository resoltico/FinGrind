package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.ResolvedInventoryCosting;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.WeightedAverageCostingMath;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Reconstructs sale costing transparency facts from the canonical inventory movement ledger. */
final class SqliteResolvedInventoryCostingReader {
  private SqliteResolvedInventoryCostingReader() {}

  static @Nullable BookkeepingEntry resolve(
      SqliteNativeDatabase activeDatabase,
      PostingId postingId,
      @Nullable BookkeepingEntry callerAuthoredEntry) {
    Objects.requireNonNull(activeDatabase, "activeDatabase");
    Objects.requireNonNull(postingId, "postingId");
    return switch (callerAuthoredEntry) {
      case BookkeepingEntry.SaleSettled sale when sale.inventoryRelief() != null ->
          resolvedSaleSettled(activeDatabase, postingId, sale);
      case BookkeepingEntry.SaleOnCredit sale when sale.inventoryRelief() != null ->
          resolvedSaleOnCredit(activeDatabase, postingId, sale);
      case null, default -> null;
    };
  }

  private static BookkeepingEntry.@Nullable SaleSettled resolvedSaleSettled(
      SqliteNativeDatabase activeDatabase, PostingId postingId, BookkeepingEntry.SaleSettled sale) {
    @Nullable ResolvedInventoryCosting resolvedCosting =
        resolvedCosting(activeDatabase, postingId, sale.amount().toMoney());
    if (resolvedCosting == null) {
      return null;
    }
    return new BookkeepingEntry.SaleSettled(
        sale.effectiveDate(),
        sale.cashAccountCode(),
        sale.revenueAccountCode(),
        sale.amount(),
        sale.inventoryRelief(),
        resolvedCosting,
        sale.foreignExchangeDetails(),
        sale.taxSelection(),
        sale.appliedTax());
  }

  private static BookkeepingEntry.@Nullable SaleOnCredit resolvedSaleOnCredit(
      SqliteNativeDatabase activeDatabase,
      PostingId postingId,
      BookkeepingEntry.SaleOnCredit sale) {
    @Nullable ResolvedInventoryCosting resolvedCosting =
        resolvedCosting(activeDatabase, postingId, sale.amount().toMoney());
    if (resolvedCosting == null) {
      return null;
    }
    return new BookkeepingEntry.SaleOnCredit(
        sale.effectiveDate(),
        sale.receivableAccountCode(),
        sale.revenueAccountCode(),
        sale.amount(),
        sale.inventoryRelief(),
        resolvedCosting,
        sale.foreignExchangeDetails(),
        sale.taxSelection(),
        sale.appliedTax());
  }

  private static @Nullable ResolvedInventoryCosting resolvedCosting(
      SqliteNativeDatabase activeDatabase, PostingId postingId, Money functionalCurrencyAmount) {
    @Nullable CostedSaleMovement movement = loadCostedSaleMovement(activeDatabase, postingId);
    if (movement == null) {
      return null;
    }
    Quantity quantityRelieved =
        Quantity.ofScaledUnits(
            movement.quantityScale(), Math.negateExact(movement.quantityDelta()));
    WeightedAverageCostingMath.InventoryPool poolBeforeDisposal =
        poolBeforeDisposal(activeDatabase, movement, functionalCurrencyAmount);
    Money costOfSales =
        Money.ofMinorUnits(
            functionalCurrencyAmount.currencyUnit(), Math.negateExact(movement.costDeltaMinor()));
    Money computedCostOfSales =
        WeightedAverageCostingMath.dispose(poolBeforeDisposal, quantityRelieved).costOfSales();
    if (!computedCostOfSales.equals(costOfSales)) {
      throw new IllegalStateException(
          "Persisted inventory disposal cost does not match exact weighted-average replay.");
    }
    return new ResolvedInventoryCosting(
        costOfSales,
        quantityRelieved,
        WeightedAverageCostingMath.roundedMovingAverageUnitCostProjection(poolBeforeDisposal));
  }

  private static @Nullable CostedSaleMovement loadCostedSaleMovement(
      SqliteNativeDatabase activeDatabase, PostingId postingId) {
    return SqliteStatementQueries.<Optional<CostedSaleMovement>>queryWithStatement(
            activeDatabase,
            SqliteInventoryCostingSql.LOAD_COSTED_SALE_MOVEMENT,
            statement -> {
              statement.bindText(1, postingId.value());
              if (statement.step() != SqliteNativeResultCode.code("ROW")) {
                return Optional.empty();
              }
              CostedSaleMovement movement =
                  new CostedSaleMovement(
                      new AccountCode(SqlitePostingMapper.requiredText(statement, 0)),
                      CanonicalTemporalText.parseLocalDate(
                          SqlitePostingMapper.requiredText(statement, 1),
                          "inventoryMovement.effectiveDate"),
                      statement.columnLong(2),
                      statement.columnLong(3),
                      statement.columnLong(4),
                      statement.columnInt(5));
              if (statement.step() != SqliteNativeResultCode.code("DONE")) {
                throw new IllegalStateException(
                    "Costed sale must resolve exactly one inventory disposal movement.");
              }
              if (movement.quantityDelta() >= 0L || movement.costDeltaMinor() >= 0L) {
                throw new IllegalStateException(
                    "Costed sale inventory disposal movement must decrease quantity and carrying cost.");
              }
              return Optional.of(movement);
            })
        .orElse(null);
  }

  private static WeightedAverageCostingMath.InventoryPool poolBeforeDisposal(
      SqliteNativeDatabase activeDatabase,
      CostedSaleMovement target,
      Money functionalCurrencyAmount) {
    long quantity = 0L;
    long costPoolMinor = 0L;
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqliteInventoryCostingSql.LOAD_INVENTORY_MOVEMENTS_BEFORE)) {
      statement.bindText(1, target.inventoryAccount().value());
      String effectiveDate = CanonicalTemporalText.formatLocalDate(target.effectiveDate());
      statement.bindText(2, effectiveDate);
      statement.bindText(3, effectiveDate);
      statement.bindLong(4, target.accountSequence());
      while (statement.step() == SqliteNativeResultCode.code("ROW")) {
        quantity = Math.addExact(quantity, statement.columnLong(0));
        costPoolMinor = Math.addExact(costPoolMinor, statement.columnLong(1));
      }
    }
    return new WeightedAverageCostingMath.InventoryPool(
        Quantity.ofScaledUnits(target.quantityScale(), quantity),
        Money.ofMinorUnits(functionalCurrencyAmount.currencyUnit(), costPoolMinor));
  }

  private record CostedSaleMovement(
      AccountCode inventoryAccount,
      LocalDate effectiveDate,
      long accountSequence,
      long quantityDelta,
      long costDeltaMinor,
      int quantityScale) {}
}
