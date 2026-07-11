package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.WeightedAverageCostingMath;
import java.math.BigInteger;
import java.util.Objects;
import java.util.SplittableRandom;

/** Shared inventory-costing scenario generation and exact arithmetic helpers for Jazzer support. */
final class InventoryCostingScenarioSupport {
  private static final CurrencyUnit EUR = CurrencyUnit.of("EUR");

  private InventoryCostingScenarioSupport() {}

  static long seedFrom(byte[] input) {
    Objects.requireNonNull(input, "input must not be null");
    long seed = 0L;
    for (byte value : input) {
      seed = seed * 131L + (value & 0xFFL);
    }
    return seed;
  }

  static ScenarioSample randomScenario(SplittableRandom random) {
    Objects.requireNonNull(random, "random must not be null");
    WeightedAverageCostingMath.InventoryPool pool = randomPool(random);
    return scenarioSampleFromPoolAndDisposedQuantity(
        pool, randomDisposedQuantity(random, pool.quantityOnHand()));
  }

  static ScenarioSample knownProjectionMismatchScenario() {
    return scenarioSampleFromPoolAndDisposedQuantity(
        new WeightedAverageCostingMath.InventoryPool(
            Quantity.ofScaledUnits(0, 3L), Money.ofMinorUnits(EUR, 4L)),
        Quantity.ofScaledUnits(0, 2L));
  }

  static ScenarioSample seedDerivedProjectionMismatchScenario(long seed) {
    long totalMinorUnits = Math.addExact(4L, Math.floorMod(seed, 96L));
    if (totalMinorUnits % 3L == 0L) {
      totalMinorUnits = Math.addExact(totalMinorUnits, 1L);
    }
    WeightedAverageCostingMath.InventoryPool pool =
        WeightedAverageCostingMath.InventoryPool.zero(EUR, 0);
    pool =
        WeightedAverageCostingMath.acquire(
            pool, Quantity.ofScaledUnits(0, 1L), Money.ofMinorUnits(EUR, 1L));
    pool =
        WeightedAverageCostingMath.acquire(
            pool, Quantity.ofScaledUnits(0, 1L), Money.ofMinorUnits(EUR, 1L));
    pool =
        WeightedAverageCostingMath.acquire(
            pool,
            Quantity.ofScaledUnits(0, 1L),
            Money.ofMinorUnits(EUR, Math.subtractExact(totalMinorUnits, 2L)));
    return scenarioSampleFromPoolAndDisposedQuantity(pool, Quantity.ofScaledUnits(0, 2L));
  }

  private static ScenarioSample scenarioSampleFromPoolAndDisposedQuantity(
      WeightedAverageCostingMath.InventoryPool pool, Quantity disposedQuantity) {
    Money projection = WeightedAverageCostingMath.roundedMovingAverageUnitCostProjection(pool);
    WeightedAverageCostingMath.Disposal disposal =
        WeightedAverageCostingMath.dispose(pool, disposedQuantity);
    Money exactCostOfSales = directExactCostOfSales(pool, disposedQuantity);
    Money projectionBasedCostOfSales = projectionBasedCost(projection, disposedQuantity);
    return new ScenarioSample(
        pool, disposedQuantity, projection, disposal, exactCostOfSales, projectionBasedCostOfSales);
  }

  private static WeightedAverageCostingMath.InventoryPool randomPool(SplittableRandom random) {
    int quantityScale = random.nextInt(0, 4);
    WeightedAverageCostingMath.InventoryPool pool =
        WeightedAverageCostingMath.InventoryPool.zero(EUR, quantityScale);
    long scaleFactor = powerOfTen(quantityScale);
    int lotCount = random.nextInt(1, 8);
    for (int lot = 0; lot < lotCount; lot++) {
      long acquiredScaledUnits = random.nextLong(1L, 40L);
      long costPerScaledUnitMinor = random.nextLong(1L, 30L);
      pool =
          WeightedAverageCostingMath.acquire(
              pool,
              Quantity.ofScaledUnits(quantityScale, acquiredScaledUnits),
              Money.ofMinorUnits(EUR, Math.multiplyExact(costPerScaledUnitMinor, scaleFactor)));
    }
    return pool;
  }

  private static Quantity randomDisposedQuantity(SplittableRandom random, Quantity quantityOnHand) {
    long scaledUnits = random.nextLong(1L, Math.addExact(quantityOnHand.scaledUnits(), 1L));
    return Quantity.ofScaledUnits(quantityOnHand.scale(), scaledUnits);
  }

  private static Money directExactCostOfSales(
      WeightedAverageCostingMath.InventoryPool pool, Quantity disposedQuantity) {
    Quantity remainingQuantity = pool.quantityOnHand().minus(disposedQuantity);
    if (remainingQuantity.isZero()) {
      return pool.costPool();
    }
    BigInteger numerator =
        BigInteger.valueOf(pool.costPool().minorUnits())
            .multiply(BigInteger.valueOf(disposedQuantity.scaledUnits()));
    BigInteger divisor = BigInteger.valueOf(pool.quantityOnHand().scaledUnits());
    return Money.ofMinorUnits(pool.costPool().currencyUnit(), roundedHalfUp(numerator, divisor));
  }

  private static Money projectionBasedCost(Money projection, Quantity quantity) {
    BigInteger numerator =
        BigInteger.valueOf(projection.minorUnits())
            .multiply(BigInteger.valueOf(quantity.scaledUnits()));
    BigInteger divisor = BigInteger.valueOf(powerOfTen(quantity.scale()));
    return Money.ofMinorUnits(projection.currencyUnit(), roundedHalfUp(numerator, divisor));
  }

  private static long roundedHalfUp(BigInteger numerator, BigInteger divisor) {
    BigInteger[] quotientAndRemainder = numerator.divideAndRemainder(divisor);
    BigInteger rounded = quotientAndRemainder[0];
    if (quotientAndRemainder[1].shiftLeft(1).compareTo(divisor) >= 0) {
      rounded = rounded.add(BigInteger.ONE);
    }
    return rounded.longValueExact();
  }

  private static long powerOfTen(int exponent) {
    long value = 1L;
    for (int index = 0; index < exponent; index++) {
      value = Math.multiplyExact(value, 10L);
    }
    return value;
  }

  record ScenarioSample(
      WeightedAverageCostingMath.InventoryPool pool,
      Quantity disposedQuantity,
      Money projection,
      WeightedAverageCostingMath.Disposal disposal,
      Money exactCostOfSales,
      Money projectionBasedCostOfSales) {
    ScenarioSample {
      Objects.requireNonNull(pool, "pool must not be null");
      Objects.requireNonNull(disposedQuantity, "disposedQuantity must not be null");
      Objects.requireNonNull(projection, "projection must not be null");
      Objects.requireNonNull(disposal, "disposal must not be null");
      Objects.requireNonNull(exactCostOfSales, "exactCostOfSales must not be null");
      Objects.requireNonNull(
          projectionBasedCostOfSales, "projectionBasedCostOfSales must not be null");
    }
  }
}
