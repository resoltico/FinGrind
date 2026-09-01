package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link WeightedAverageCostingMath}. */
class WeightedAverageCostingMathTest {
  private static final CurrencyUnit EUR = CurrencyUnit.of("EUR");

  @Test
  void inventoryPool_enforcesZeroEquivalenceAndMinorUnitFloor() {
    assertDoesNotThrow(
        () ->
            new WeightedAverageCostingMath.InventoryPool(
                Quantity.parse(2, "0.25"), Money.parse("EUR", "0.25")));
    WeightedAverageCostingMath.InventoryPoolZeroEquivalenceException positiveQuantityZeroCost =
        assertThrows(
            WeightedAverageCostingMath.InventoryPoolZeroEquivalenceException.class,
            () ->
                new WeightedAverageCostingMath.InventoryPool(
                    Quantity.parse(2, "0.25"), Money.zero(EUR)));
    assertEquals(
        "Inventory pool quantity and cost pool must both be zero or both be positive.",
        positiveQuantityZeroCost.getMessage());
    assertEquals(Quantity.parse(2, "0.25"), positiveQuantityZeroCost.quantityOnHand());
    assertEquals(Money.zero(EUR), positiveQuantityZeroCost.costPool());
    WeightedAverageCostingMath.InventoryPoolZeroEquivalenceException zeroQuantityPositiveCost =
        assertThrows(
            WeightedAverageCostingMath.InventoryPoolZeroEquivalenceException.class,
            () ->
                new WeightedAverageCostingMath.InventoryPool(
                    Quantity.zero(2), Money.parse("EUR", "1.00")));
    assertEquals(
        "Inventory pool quantity and cost pool must both be zero or both be positive.",
        zeroQuantityPositiveCost.getMessage());
    assertEquals(Quantity.zero(2), zeroQuantityPositiveCost.quantityOnHand());
    assertEquals(Money.parse("EUR", "1.00"), zeroQuantityPositiveCost.costPool());
    WeightedAverageCostingMath.InventoryPoolMinorUnitFloorException minorUnitFloorFailure =
        assertThrows(
            WeightedAverageCostingMath.InventoryPoolMinorUnitFloorException.class,
            () ->
                new WeightedAverageCostingMath.InventoryPool(
                    Quantity.parse(2, "0.25"), Money.parse("EUR", "0.01")));
    assertEquals(
        "Positive inventory pools must carry at least one currency minor unit per smallest quantity increment to preserve zero-to-zero disposal truth.",
        minorUnitFloorFailure.getMessage());
    assertEquals(Quantity.parse(2, "0.25"), minorUnitFloorFailure.quantityOnHand());
    assertEquals(Money.parse("EUR", "0.01"), minorUnitFloorFailure.costPool());
    assertEquals(25L, minorUnitFloorFailure.minimumRequiredMinorUnits());
  }

  @Test
  void minorUnitFloor_preservesPositiveCostAcrossEveryPositivePartialDisposal() {
    WeightedAverageCostingMath.InventoryPool pool =
        new WeightedAverageCostingMath.InventoryPool(
            Quantity.parse(2, "0.25"), Money.parse("EUR", "0.25"));

    for (long disposedScaledUnits = 1L;
        disposedScaledUnits < pool.quantityOnHand().scaledUnits();
        disposedScaledUnits++) {
      WeightedAverageCostingMath.Disposal disposal =
          WeightedAverageCostingMath.dispose(pool, Quantity.ofScaledUnits(2, disposedScaledUnits));

      assertTrue(disposal.remainingPool().quantityOnHand().isPositive());
      assertTrue(disposal.remainingPool().costPool().isPositive());
      assertEquals(
          disposal.remainingPool().quantityOnHand().isZero(),
          disposal.remainingPool().costPool().isZero());
    }
  }

  @Test
  void minorUnitFloor_isNecessaryToKeepZeroToZeroTruthAtTheMoneyBoundary() {
    Quantity quantityOnHand = Quantity.parse(2, "0.25");
    Money costPool = Money.parse("EUR", "0.01");
    Quantity disposedQuantity = Quantity.parse(2, "0.13");

    Money costOfSales =
        theoreticalRoundedCostOfSales(
            costPool, disposedQuantity.scaledUnits(), quantityOnHand.scaledUnits());
    Quantity remainingQuantity = quantityOnHand.minus(disposedQuantity);
    Money remainingCostPool = costPool.minus(costOfSales);

    assertEquals(Money.parse("EUR", "0.01"), costOfSales);
    assertTrue(remainingQuantity.isPositive());
    assertTrue(remainingCostPool.isZero());
  }

  @Test
  void acquire_addsExactQuantityAndCostAndRejectsInvalidInputs() {
    WeightedAverageCostingMath.InventoryPool pool =
        WeightedAverageCostingMath.InventoryPool.zero(EUR, 2);

    WeightedAverageCostingMath.InventoryPool updatedPool =
        WeightedAverageCostingMath.acquire(
            pool, Quantity.parse(2, "1.25"), Money.parse("EUR", "4.00"));

    assertEquals(Quantity.parse(2, "1.25"), updatedPool.quantityOnHand());
    assertEquals(Money.parse("EUR", "5.00"), updatedPool.costPool());
    assertEquals(
        "acquiredQuantity must be positive.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    WeightedAverageCostingMath.acquire(
                        pool, Quantity.zero(2), Money.parse("EUR", "1.00")))
            .getMessage());
    assertEquals(
        "unitCost must be positive.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    WeightedAverageCostingMath.acquire(
                        pool, Quantity.parse(2, "1.00"), Money.zero(EUR)))
            .getMessage());
    WeightedAverageCostingMath.InexactAcquisitionCostException inexactAcquisitionFailure =
        assertThrows(
            WeightedAverageCostingMath.InexactAcquisitionCostException.class,
            () ->
                WeightedAverageCostingMath.acquire(
                    pool, Quantity.parse(2, "0.25"), Money.parse("EUR", "0.02")));
    assertEquals(
        "Quantity and unit cost must compose one exact money amount at the currency minor-unit scale.",
        inexactAcquisitionFailure.getMessage());
    assertEquals(Quantity.parse(2, "0.25"), inexactAcquisitionFailure.quantity());
    assertEquals(Money.parse("EUR", "0.02"), inexactAcquisitionFailure.unitCost());
    WeightedAverageCostingMath.InventoryPoolMinorUnitFloorException lowCarryingCostFailure =
        assertThrows(
            WeightedAverageCostingMath.InventoryPoolMinorUnitFloorException.class,
            () ->
                WeightedAverageCostingMath.acquire(
                    pool, Quantity.parse(2, "0.25"), Money.parse("EUR", "0.04")));
    assertEquals(
        "Positive inventory pools must carry at least one currency minor unit per smallest quantity increment to preserve zero-to-zero disposal truth.",
        lowCarryingCostFailure.getMessage());
    assertEquals(Quantity.parse(2, "0.25"), lowCarryingCostFailure.quantityOnHand());
    assertEquals(Money.parse("EUR", "0.01"), lowCarryingCostFailure.costPool());
  }

  @Test
  void dispose_derivesExactCostOfSalesAndPreservesPoolToZeroTruth() {
    WeightedAverageCostingMath.InventoryPool pool =
        new WeightedAverageCostingMath.InventoryPool(
            Quantity.ofScaledUnits(0, 3L), Money.ofMinorUnits(EUR, 4L));

    WeightedAverageCostingMath.Disposal disposal =
        WeightedAverageCostingMath.dispose(pool, Quantity.ofScaledUnits(0, 2L));

    assertEquals(Money.ofMinorUnits(EUR, 3L), disposal.costOfSales());
    assertEquals(Quantity.ofScaledUnits(0, 1L), disposal.remainingPool().quantityOnHand());
    assertEquals(Money.ofMinorUnits(EUR, 1L), disposal.remainingPool().costPool());

    WeightedAverageCostingMath.Disposal finalDisposal =
        WeightedAverageCostingMath.dispose(disposal.remainingPool(), Quantity.ofScaledUnits(0, 1L));

    assertEquals(Money.ofMinorUnits(EUR, 1L), finalDisposal.costOfSales());
    assertEquals(Quantity.zero(0), finalDisposal.remainingPool().quantityOnHand());
    assertEquals(Money.zero(EUR), finalDisposal.remainingPool().costPool());
  }

  @Test
  void roundedMovingAverageUnitCostProjection_isDisplayOnlyAndDoesNotDefineCogs() {
    WeightedAverageCostingMath.InventoryPool pool =
        new WeightedAverageCostingMath.InventoryPool(
            Quantity.ofScaledUnits(0, 3L), Money.ofMinorUnits(EUR, 4L));

    Money projection = WeightedAverageCostingMath.roundedMovingAverageUnitCostProjection(pool);
    WeightedAverageCostingMath.Disposal disposal =
        WeightedAverageCostingMath.dispose(pool, Quantity.ofScaledUnits(0, 2L));

    assertEquals(Money.ofMinorUnits(EUR, 1L), projection);
    assertEquals(Money.ofMinorUnits(EUR, 3L), disposal.costOfSales());
    assertEquals(
        Money.ofMinorUnits(EUR, 2L),
        projectionBasedCost(projection, Quantity.ofScaledUnits(0, 2L)));
  }

  @Test
  void writeDownAndReversalRestore_adjustOnlyTheCostPoolWhilePreservingValidity() {
    WeightedAverageCostingMath.InventoryPool pool =
        new WeightedAverageCostingMath.InventoryPool(
            Quantity.ofScaledUnits(0, 3L), Money.ofMinorUnits(EUR, 6L));

    WeightedAverageCostingMath.InventoryPool writtenDown =
        WeightedAverageCostingMath.writeDown(pool, Money.ofMinorUnits(EUR, 2L));
    WeightedAverageCostingMath.InventoryPool restored =
        WeightedAverageCostingMath.reversalRestore(writtenDown, Money.ofMinorUnits(EUR, 1L));

    assertEquals(Quantity.ofScaledUnits(0, 3L), writtenDown.quantityOnHand());
    assertEquals(Money.ofMinorUnits(EUR, 4L), writtenDown.costPool());
    assertEquals(Quantity.ofScaledUnits(0, 3L), restored.quantityOnHand());
    assertEquals(Money.ofMinorUnits(EUR, 5L), restored.costPool());
    assertEquals(
        "writeDownAmount must be positive.",
        assertThrows(
                IllegalArgumentException.class,
                () -> WeightedAverageCostingMath.writeDown(pool, Money.zero(EUR)))
            .getMessage());
    assertEquals(
        "restoredAmount must be positive.",
        assertThrows(
                IllegalArgumentException.class,
                () -> WeightedAverageCostingMath.reversalRestore(pool, Money.zero(EUR)))
            .getMessage());
    WeightedAverageCostingMath.InventoryPoolMinorUnitFloorException writeDownFailure =
        assertThrows(
            WeightedAverageCostingMath.InventoryPoolMinorUnitFloorException.class,
            () -> WeightedAverageCostingMath.writeDown(pool, Money.ofMinorUnits(EUR, 4L)));
    assertEquals(
        "Positive inventory pools must carry at least one currency minor unit per smallest quantity increment to preserve zero-to-zero disposal truth.",
        writeDownFailure.getMessage());
    assertEquals(Quantity.ofScaledUnits(0, 3L), writeDownFailure.quantityOnHand());
    assertEquals(Money.ofMinorUnits(EUR, 2L), writeDownFailure.costPool());
  }

  @Test
  void projectionAndDisposalRejectUndefinedOrOutOfBoundsRequests() {
    WeightedAverageCostingMath.InventoryPool zeroPool =
        WeightedAverageCostingMath.InventoryPool.zero(EUR, 0);
    WeightedAverageCostingMath.InventoryPool pool =
        new WeightedAverageCostingMath.InventoryPool(
            Quantity.ofScaledUnits(0, 3L), Money.ofMinorUnits(EUR, 4L));
    WeightedAverageCostingMath.InventoryPool overflowProjectionPool =
        new WeightedAverageCostingMath.InventoryPool(
            Quantity.ofScaledUnits(9, 1L), Money.ofMinorUnits(EUR, Long.MAX_VALUE));
    WeightedAverageCostingMath.InventoryPool maximumProjectionPool =
        new WeightedAverageCostingMath.InventoryPool(
            Quantity.ofScaledUnits(0, 1L), Money.ofMinorUnits(EUR, Long.MAX_VALUE));

    assertEquals(
        "Moving-average unit cost projection is undefined for zero quantity on hand.",
        assertThrows(
                IllegalArgumentException.class,
                () -> WeightedAverageCostingMath.roundedMovingAverageUnitCostProjection(zeroPool))
            .getMessage());
    WeightedAverageCostingMath.DisposedQuantityExceedsOnHandException overdrawFailure =
        assertThrows(
            WeightedAverageCostingMath.DisposedQuantityExceedsOnHandException.class,
            () -> WeightedAverageCostingMath.dispose(pool, Quantity.ofScaledUnits(0, 4L)));
    assertEquals(
        "Disposed quantity must not exceed quantity on hand.", overdrawFailure.getMessage());
    assertEquals(Quantity.ofScaledUnits(0, 4L), overdrawFailure.disposedQuantity());
    assertEquals(Quantity.ofScaledUnits(0, 3L), overdrawFailure.quantityOnHand());
    assertEquals(
        "disposedQuantity must be positive.",
        assertThrows(
                IllegalArgumentException.class,
                () -> WeightedAverageCostingMath.dispose(pool, Quantity.zero(0)))
            .getMessage());
    assertEquals(
        Money.ofMinorUnits(EUR, Long.MAX_VALUE),
        WeightedAverageCostingMath.roundedMovingAverageUnitCostProjection(maximumProjectionPool));
    assertEquals(
        "Inventory costing result is outside the supported money range.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    WeightedAverageCostingMath.roundedMovingAverageUnitCostProjection(
                        overflowProjectionPool))
            .getMessage());
  }

  @Test
  void randomizedBuyLotsAndDisposeToZero_conservesExactTotalCostAndNeverProducesNegativeState() {
    SplittableRandom random = new SplittableRandom(0x5EEDC0DEL);
    int projectionMismatchCount = 0;

    for (int scenario = 0; scenario < 300; scenario++) {
      int quantityScale = random.nextInt(0, 4);
      WeightedAverageCostingMath.InventoryPool pool =
          WeightedAverageCostingMath.InventoryPool.zero(EUR, quantityScale);
      Money totalAcquisitionCost = Money.zero(EUR);
      int lotCount = random.nextInt(1, 10);
      long scaleFactor = powerOfTen(quantityScale);
      for (int lot = 0; lot < lotCount; lot++) {
        long acquiredScaledUnits = random.nextLong(1L, 50L);
        long costPerScaledUnitMinor = random.nextLong(1L, 25L);
        Quantity acquiredQuantity = Quantity.ofScaledUnits(quantityScale, acquiredScaledUnits);
        Money unitCost =
            Money.ofMinorUnits(EUR, Math.multiplyExact(costPerScaledUnitMinor, scaleFactor));
        totalAcquisitionCost =
            totalAcquisitionCost.plus(
                Money.ofMinorUnits(
                    EUR, Math.multiplyExact(acquiredScaledUnits, costPerScaledUnitMinor)));
        pool = WeightedAverageCostingMath.acquire(pool, acquiredQuantity, unitCost);
        assertEquals(pool.quantityOnHand().isZero(), pool.costPool().isZero());
        assertTrue(pool.quantityOnHand().isZero() || pool.costPool().isPositive());
      }

      Money totalCostOfSales = Money.zero(EUR);
      while (pool.quantityOnHand().isPositive()) {
        long disposedScaledUnits =
            random.nextLong(1L, Math.addExact(pool.quantityOnHand().scaledUnits(), 1L));
        Quantity disposedQuantity = Quantity.ofScaledUnits(quantityScale, disposedScaledUnits);
        Money projection = WeightedAverageCostingMath.roundedMovingAverageUnitCostProjection(pool);
        Money expectedCostOfSales =
            theoreticalRoundedCostOfSales(
                pool.costPool(), disposedScaledUnits, pool.quantityOnHand().scaledUnits());
        WeightedAverageCostingMath.Disposal disposal =
            WeightedAverageCostingMath.dispose(pool, disposedQuantity);
        Money projectionBasedCostOfSales = projectionBasedCost(projection, disposedQuantity);
        totalCostOfSales = totalCostOfSales.plus(disposal.costOfSales());
        assertEquals(expectedCostOfSales, disposal.costOfSales());
        if (!expectedCostOfSales.equals(projectionBasedCostOfSales)) {
          projectionMismatchCount++;
        }
        pool = disposal.remainingPool();
        assertEquals(pool.quantityOnHand().isZero(), pool.costPool().isZero());
        assertTrue(pool.quantityOnHand().isZero() || pool.costPool().isPositive());
      }

      assertEquals(totalAcquisitionCost, totalCostOfSales);
      assertEquals(Quantity.zero(quantityScale), pool.quantityOnHand());
      assertEquals(Money.zero(EUR), pool.costPool());
    }

    assertTrue(
        projectionMismatchCount > 0,
        "Randomized weighted-average exercises should include at least one rounded-projection mismatch case.");
  }

  @Test
  void methodsRejectNullArguments() {
    WeightedAverageCostingMath.InventoryPool pool =
        WeightedAverageCostingMath.InventoryPool.zero(EUR, 0);

    assertThrows(
        NullPointerException.class,
        () ->
            WeightedAverageCostingMath.acquire(
                nullOf(), Quantity.ofScaledUnits(0, 1L), Money.ofMinorUnits(EUR, 1L)));
    assertThrows(
        NullPointerException.class,
        () -> WeightedAverageCostingMath.acquire(pool, nullOf(), Money.ofMinorUnits(EUR, 1L)));
    assertThrows(
        NullPointerException.class,
        () -> WeightedAverageCostingMath.acquire(pool, Quantity.ofScaledUnits(0, 1L), nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> WeightedAverageCostingMath.dispose(nullOf(), Quantity.ofScaledUnits(0, 1L)));
    assertThrows(
        NullPointerException.class, () -> WeightedAverageCostingMath.dispose(pool, nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> WeightedAverageCostingMath.writeDown(nullOf(), Money.ofMinorUnits(EUR, 1L)));
    assertThrows(
        NullPointerException.class, () -> WeightedAverageCostingMath.writeDown(pool, nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> WeightedAverageCostingMath.reversalRestore(nullOf(), Money.ofMinorUnits(EUR, 1L)));
    assertThrows(
        NullPointerException.class,
        () -> WeightedAverageCostingMath.reversalRestore(pool, nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> WeightedAverageCostingMath.roundedMovingAverageUnitCostProjection(nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new WeightedAverageCostingMath.InventoryPool(nullOf(), Money.ofMinorUnits(EUR, 1L)));
    assertThrows(
        NullPointerException.class,
        () ->
            new WeightedAverageCostingMath.InventoryPool(Quantity.ofScaledUnits(0, 1L), nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new WeightedAverageCostingMath.Disposal(nullOf(), Money.ofMinorUnits(EUR, 1L)));
    assertThrows(
        NullPointerException.class, () -> new WeightedAverageCostingMath.Disposal(pool, nullOf()));
  }

  @Test
  void disposal_rejectsCurrencyMismatchBetweenRemainingPoolAndCostOfSales() {
    WeightedAverageCostingMath.InventoryPool remainingPool =
        new WeightedAverageCostingMath.InventoryPool(
            Quantity.ofScaledUnits(0, 1L), Money.ofMinorUnits(EUR, 1L));

    assertEquals(
        "Disposal cost of sales must share the remaining pool currency unit.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new WeightedAverageCostingMath.Disposal(
                        remainingPool, Money.ofMinorUnits(CurrencyUnit.of("USD"), 1L)))
            .getMessage());
  }

  private static Money projectionBasedCost(Money projection, Quantity quantity) {
    long numerator = Math.multiplyExact(projection.minorUnits(), quantity.scaledUnits());
    long divisor = powerOfTen(quantity.scale());
    long quotient = numerator / divisor;
    long remainder = numerator % divisor;
    if (remainder * 2L >= divisor) {
      quotient = Math.addExact(quotient, 1L);
    }
    return Money.ofMinorUnits(projection.currencyUnit(), quotient);
  }

  private static Money theoreticalRoundedCostOfSales(
      Money amount, long numerator, long denominator) {
    long scaledNumerator = Math.multiplyExact(amount.minorUnits(), numerator);
    long quotient = scaledNumerator / denominator;
    long remainder = scaledNumerator % denominator;
    if (Math.multiplyExact(remainder, 2L) >= denominator) {
      quotient = Math.addExact(quotient, 1L);
    }
    return Money.ofMinorUnits(amount.currencyUnit(), quotient);
  }

  private static long powerOfTen(int exponent) {
    long value = 1L;
    for (int index = 0; index < exponent; index++) {
      value = Math.multiplyExact(value, 10L);
    }
    return value;
  }
}
