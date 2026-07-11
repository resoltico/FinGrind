package dev.erst.fingrind.executor.bookkeeping.reporting;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.WeightedAverageCostingMath;
import dev.erst.fingrind.executor.bookkeeping.InventoryValuationCriteria;
import dev.erst.fingrind.executor.bookkeeping.InventoryValuationMovementRecord;
import dev.erst.fingrind.executor.bookkeeping.InventoryValuationView;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Replays the exact inventory ledger into point-in-time per-account valuation views. */
final class InventoryValuationCalculator {
  private InventoryValuationCalculator() {}

  /**
   * Replays durable movements in their canonical account-local order.
   *
   * <p>The projection deliberately derives carrying value from the exact quantity and cost pool.
   * The rounded moving-average unit cost is only produced after replay for operator transparency.
   */
  static List<InventoryValuationView> calculate(
      BookIdentity bookIdentity,
      List<RegisteredAccount> accounts,
      List<InventoryValuationMovementRecord> movements,
      InventoryValuationCriteria criteria) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(accounts, "accounts");
    Objects.requireNonNull(movements, "movements");
    Objects.requireNonNull(criteria, "criteria");

    Map<AccountCode, RegisteredAccount> inventoryAccounts = inventoryAccounts(accounts);
    Map<AccountCode, MutablePool> pools = initialPools(inventoryAccounts);
    Map<AccountCode, List<InventoryValuationMovementRecord>> movementsByAccount =
        movementBuckets(inventoryAccounts);
    for (InventoryValuationMovementRecord movement : canonicalOrder(movements)) {
      RegisteredAccount account = inventoryAccounts.get(movement.inventoryAccount());
      if (account == null) {
        throw new IllegalStateException(
            "Inventory movement references an account that is not an inventory account.");
      }
      Objects.requireNonNull(pools.get(movement.inventoryAccount()), "inventory pool")
          .apply(movement);
      if (criteria.includeMovements()) {
        Objects.requireNonNull(
                movementsByAccount.get(movement.inventoryAccount()), "inventory movement bucket")
            .add(movement);
      }
    }
    return inventoryAccounts.values().stream()
        .sorted(Comparator.comparing(account -> account.accountCode().value()))
        .map(
            account -> {
              MutablePool pool =
                  Objects.requireNonNull(pools.get(account.accountCode()), "inventory pool");
              List<InventoryValuationMovementRecord> accountMovements =
                  criteria.includeMovements()
                      ? Objects.requireNonNull(
                          movementsByAccount.get(account.accountCode()),
                          "inventory movement bucket")
                      : List.of();
              return valuation(account, pool.toInventoryPool(bookIdentity), accountMovements);
            })
        .toList();
  }

  private static Map<AccountCode, RegisteredAccount> inventoryAccounts(
      List<RegisteredAccount> accounts) {
    Map<AccountCode, RegisteredAccount> inventoryAccounts = new ConcurrentHashMap<>();
    accounts.stream()
        .filter(
            account ->
                AccountRole.from(account.accountType(), account.accountTaxonomy())
                    == AccountRole.INVENTORY)
        .forEach(
            account -> {
              if (inventoryAccounts.put(account.accountCode(), account) != null) {
                throw new IllegalStateException(
                    "Inventory account catalog must not contain duplicates.");
              }
            });
    return inventoryAccounts;
  }

  private static Map<AccountCode, MutablePool> initialPools(
      Map<AccountCode, RegisteredAccount> accounts) {
    Map<AccountCode, MutablePool> pools = new ConcurrentHashMap<>();
    accounts.forEach(
        (accountCode, account) ->
            pools.put(
                accountCode,
                new MutablePool(
                    Objects.requireNonNull(account.unitOfMeasure(), "inventory unit of measure")
                        .quantityScale())));
    return pools;
  }

  private static Map<AccountCode, List<InventoryValuationMovementRecord>> movementBuckets(
      Map<AccountCode, RegisteredAccount> accounts) {
    Map<AccountCode, List<InventoryValuationMovementRecord>> buckets = new ConcurrentHashMap<>();
    accounts.keySet().forEach(accountCode -> buckets.put(accountCode, new ArrayList<>()));
    return buckets;
  }

  private static List<InventoryValuationMovementRecord> canonicalOrder(
      List<InventoryValuationMovementRecord> movements) {
    return movements.stream()
        .sorted(
            Comparator.comparing(
                    (InventoryValuationMovementRecord movement) ->
                        movement.inventoryAccount().value())
                .thenComparing(InventoryValuationMovementRecord::effectiveDate)
                .thenComparingLong(InventoryValuationMovementRecord::accountSequence))
        .toList();
  }

  private static InventoryValuationView valuation(
      RegisteredAccount account,
      WeightedAverageCostingMath.InventoryPool pool,
      List<InventoryValuationMovementRecord> movements) {
    Money roundedProjection =
        pool.quantityOnHand().isZero()
            ? null
            : WeightedAverageCostingMath.roundedMovingAverageUnitCostProjection(pool);
    return new InventoryValuationView(account, pool, roundedProjection, movements);
  }

  /**
   * Mutable exact totals are confined to one replay and converted through the immutable pool gate.
   */
  private static final class MutablePool {
    private final int quantityScale;
    private long quantity;
    private long costPoolMinor;

    private MutablePool(int quantityScale) {
      this.quantityScale = quantityScale;
    }

    private void apply(InventoryValuationMovementRecord movement) {
      quantity = Math.addExact(quantity, movement.quantityDelta());
      costPoolMinor = Math.addExact(costPoolMinor, movement.costDeltaMinor());
      if (quantity < 0L || costPoolMinor < 0L) {
        throw new IllegalStateException(
            "Durable inventory replay produced a negative on-hand pool.");
      }
    }

    private WeightedAverageCostingMath.InventoryPool toInventoryPool(BookIdentity bookIdentity) {
      return new WeightedAverageCostingMath.InventoryPool(
          Quantity.ofScaledUnits(quantityScale, quantity),
          Money.ofMinorUnits(bookIdentity.functionalCurrency(), costPoolMinor));
    }
  }
}
