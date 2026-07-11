package dev.erst.fingrind.core;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Pure weighted-average inventory costing math over one exact quantity and one carrying-cost pool
 * whose authoritative money truth remains at the currency minor-unit boundary.
 */
public final class WeightedAverageCostingMath {
  private WeightedAverageCostingMath() {}

  /** Adds one acquired quantity into the inventory pool at one exact per-whole-unit cost. */
  public static InventoryPool acquire(
      InventoryPool pool, Quantity acquiredQuantity, Money unitCost) {
    Objects.requireNonNull(pool, "pool");
    requirePositiveQuantity(acquiredQuantity, "acquiredQuantity");
    requirePositiveMoney(unitCost, "unitCost");
    Quantity updatedQuantity = pool.quantityOnHand.plus(acquiredQuantity);
    Money acquisitionCost = exactAcquisitionCost(acquiredQuantity, unitCost);
    return new InventoryPool(updatedQuantity, pool.costPool.plus(acquisitionCost));
  }

  /**
   * Disposes one quantity from the pool and returns both the exact cost of sales and the remaining
   * pool.
   */
  public static Disposal dispose(InventoryPool pool, Quantity disposedQuantity) {
    Objects.requireNonNull(pool, "pool");
    requirePositiveQuantity(disposedQuantity, "disposedQuantity");
    if (disposedQuantity.compareTo(pool.quantityOnHand) > 0) {
      throw new DisposedQuantityExceedsOnHandException(disposedQuantity, pool.quantityOnHand);
    }
    Quantity remainingQuantity = pool.quantityOnHand.minus(disposedQuantity);
    Money costOfSales =
        remainingQuantity.isZero()
            ? pool.costPool
            : proportionallyRoundedMoney(
                pool.costPool, disposedQuantity.scaledUnits(), pool.quantityOnHand.scaledUnits());
    Money remainingCostPool =
        remainingQuantity.isZero()
            ? Money.zero(pool.costPool.currencyUnit())
            : pool.costPool.minus(costOfSales);
    return new Disposal(new InventoryPool(remainingQuantity, remainingCostPool), costOfSales);
  }

  /** Applies one write-down against the pool while preserving exact quantity-on-hand. */
  public static InventoryPool writeDown(InventoryPool pool, Money writeDownAmount) {
    Objects.requireNonNull(pool, "pool");
    requirePositiveMoney(writeDownAmount, "writeDownAmount");
    Money remainingCostPool = pool.costPool.minus(writeDownAmount);
    return new InventoryPool(pool.quantityOnHand, remainingCostPool);
  }

  /** Restores one prior write-down amount back into the pool without changing quantity-on-hand. */
  public static InventoryPool reversalRestore(InventoryPool pool, Money restoredAmount) {
    Objects.requireNonNull(pool, "pool");
    requirePositiveMoney(restoredAmount, "restoredAmount");
    return new InventoryPool(pool.quantityOnHand, pool.costPool.plus(restoredAmount));
  }

  /**
   * Returns the rounded moving-average unit-cost projection for one remaining pool as one
   * non-authoritative display value.
   */
  public static Money roundedMovingAverageUnitCostProjection(InventoryPool pool) {
    Objects.requireNonNull(pool, "pool");
    if (pool.quantityOnHand.isZero()) {
      throw new IllegalArgumentException(
          "Moving-average unit cost projection is undefined for zero quantity on hand.");
    }
    long scaleFactor = powerOfTen(pool.quantityOnHand.scale());
    BigInteger numerator =
        BigInteger.valueOf(pool.costPool.minorUnits()).multiply(BigInteger.valueOf(scaleFactor));
    BigInteger rounded =
        roundHalfUpDivide(numerator, BigInteger.valueOf(pool.quantityOnHand.scaledUnits()));
    return Money.ofMinorUnits(pool.costPool.currencyUnit(), toMoneyMinorUnits(rounded));
  }

  private static Money exactAcquisitionCost(Quantity quantity, Money unitCost) {
    BigInteger numerator =
        BigInteger.valueOf(quantity.scaledUnits())
            .multiply(BigInteger.valueOf(unitCost.minorUnits()));
    BigInteger divisor = BigInteger.valueOf(powerOfTen(quantity.scale()));
    BigInteger[] quotientAndRemainder = numerator.divideAndRemainder(divisor);
    if (quotientAndRemainder[1].signum() != 0) {
      throw new InexactAcquisitionCostException(quantity, unitCost);
    }
    return Money.ofMinorUnits(unitCost.currencyUnit(), toMoneyMinorUnits(quotientAndRemainder[0]));
  }

  private static Money proportionallyRoundedMoney(Money amount, long numerator, long denominator) {
    BigInteger scaledNumerator =
        BigInteger.valueOf(amount.minorUnits()).multiply(BigInteger.valueOf(numerator));
    BigInteger rounded = roundHalfUpDivide(scaledNumerator, BigInteger.valueOf(denominator));
    return Money.ofMinorUnits(amount.currencyUnit(), toMoneyMinorUnits(rounded));
  }

  private static BigInteger roundHalfUpDivide(BigInteger numerator, BigInteger divisor) {
    BigInteger[] quotientAndRemainder = numerator.divideAndRemainder(divisor);
    BigInteger rounded = quotientAndRemainder[0];
    if (quotientAndRemainder[1].shiftLeft(1).compareTo(divisor) >= 0) {
      rounded = rounded.add(BigInteger.ONE);
    }
    return rounded;
  }

  private static long toMoneyMinorUnits(BigInteger value) {
    if (value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
      throw new IllegalArgumentException(
          "Inventory costing result is outside the supported money range.");
    }
    return value.longValueExact();
  }

  private static void requirePositiveQuantity(Quantity quantity, String fieldName) {
    Objects.requireNonNull(quantity, fieldName);
    if (!quantity.isPositive()) {
      throw new IllegalArgumentException(fieldName + " must be positive.");
    }
  }

  private static void requirePositiveMoney(Money money, String fieldName) {
    Objects.requireNonNull(money, fieldName);
    if (!money.isPositive()) {
      throw new IllegalArgumentException(fieldName + " must be positive.");
    }
  }

  private static long powerOfTen(int exponent) {
    long value = 1L;
    for (int index = 0; index < exponent; index++) {
      value = Math.multiplyExact(value, 10L);
    }
    return value;
  }

  /**
   * Exact on-hand pool state over one quantity and one monetary carrying-cost pool.
   *
   * <p>Because no hidden sub-minor remainder is stored, positive pools must stay representable at
   * the currency minor-unit boundary so half-up partial disposals cannot strand positive quantity
   * against a zero remaining cost pool.
   */
  public record InventoryPool(Quantity quantityOnHand, Money costPool) {
    /** Validates one exact on-hand pool state against the zero-to-zero truth boundary. */
    public InventoryPool {
      Objects.requireNonNull(quantityOnHand, "quantityOnHand");
      Objects.requireNonNull(costPool, "costPool");
      if (quantityOnHand.isZero() != costPool.isZero()) {
        throw new InventoryPoolZeroEquivalenceException(quantityOnHand, costPool);
      }
      requireMinorUnitFloor(quantityOnHand, costPool);
    }

    /** Returns one zero pool for the selected currency and quantity scale. */
    public static InventoryPool zero(CurrencyUnit currencyUnit, int quantityScale) {
      return new InventoryPool(Quantity.zero(quantityScale), Money.zero(currencyUnit));
    }
  }

  /** Exact disposal outcome carrying both cost of sales and the remaining pool. */
  public record Disposal(InventoryPool remainingPool, Money costOfSales) {
    /** Validates one exact disposal outcome. */
    public Disposal {
      Objects.requireNonNull(remainingPool, "remainingPool");
      Objects.requireNonNull(costOfSales, "costOfSales");
      if (!remainingPool.costPool.currencyUnit().equals(costOfSales.currencyUnit())) {
        throw new IllegalArgumentException(
            "Disposal cost of sales must share the remaining pool currency unit.");
      }
    }
  }

  /**
   * Raised when one disposal attempts to relieve more quantity than the exact on-hand pool
   * contains.
   */
  public static final class DisposedQuantityExceedsOnHandException
      extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final transient Quantity disposedQuantity;
    private final transient Quantity quantityOnHand;

    /** Creates one disposal overdraw failure against the exact on-hand quantity owner. */
    public DisposedQuantityExceedsOnHandException(
        Quantity disposedQuantity, Quantity quantityOnHand) {
      super("Disposed quantity must not exceed quantity on hand.");
      this.disposedQuantity = Objects.requireNonNull(disposedQuantity, "disposedQuantity");
      this.quantityOnHand = Objects.requireNonNull(quantityOnHand, "quantityOnHand");
    }

    /** Returns the rejected disposed quantity. */
    public Quantity disposedQuantity() {
      return disposedQuantity;
    }

    /** Returns the exact on-hand quantity available at the time of the rejected disposal. */
    public Quantity quantityOnHand() {
      return quantityOnHand;
    }
  }

  /** Raised when one pool breaks the exact zero-to-zero truth between quantity and cost. */
  public static final class InventoryPoolZeroEquivalenceException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final transient Quantity quantityOnHand;
    private final transient Money costPool;

    /** Creates one zero-equivalence failure over the attempted exact pool state. */
    public InventoryPoolZeroEquivalenceException(Quantity quantityOnHand, Money costPool) {
      super("Inventory pool quantity and cost pool must both be zero or both be positive.");
      this.quantityOnHand = Objects.requireNonNull(quantityOnHand, "quantityOnHand");
      this.costPool = Objects.requireNonNull(costPool, "costPool");
    }

    /** Returns the attempted quantity-on-hand state. */
    public Quantity quantityOnHand() {
      return quantityOnHand;
    }

    /** Returns the attempted carrying-cost pool state. */
    public Money costPool() {
      return costPool;
    }
  }

  /**
   * Raised when one positive pool lacks enough minor-unit carrying cost to preserve zero-to-zero
   * disposal truth at the stored money boundary.
   */
  public static final class InventoryPoolMinorUnitFloorException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final transient Quantity quantityOnHand;
    private final transient Money costPool;
    private final long minimumRequiredMinorUnits;

    /** Creates one minor-unit floor failure over the attempted exact pool state. */
    public InventoryPoolMinorUnitFloorException(
        Quantity quantityOnHand, Money costPool, long minimumRequiredMinorUnits) {
      super(
          "Positive inventory pools must carry at least one currency minor unit per smallest quantity increment to preserve zero-to-zero disposal truth.");
      this.quantityOnHand = Objects.requireNonNull(quantityOnHand, "quantityOnHand");
      this.costPool = Objects.requireNonNull(costPool, "costPool");
      this.minimumRequiredMinorUnits = minimumRequiredMinorUnits;
    }

    /** Returns the attempted quantity-on-hand state. */
    public Quantity quantityOnHand() {
      return quantityOnHand;
    }

    /** Returns the attempted carrying-cost pool state. */
    public Money costPool() {
      return costPool;
    }

    /** Returns the minimum minor-unit carrying cost required by the pool invariant. */
    public long minimumRequiredMinorUnits() {
      return minimumRequiredMinorUnits;
    }
  }

  /**
   * Raised when one acquired quantity and unit cost cannot compose one exact carrying-cost amount
   * at the currency minor-unit boundary.
   */
  public static final class InexactAcquisitionCostException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final transient Quantity quantity;
    private final transient Money unitCost;

    /** Creates one exact-acquisition-cost failure over the attempted quantity and unit cost. */
    public InexactAcquisitionCostException(Quantity quantity, Money unitCost) {
      super(
          "Quantity and unit cost must compose one exact money amount at the currency minor-unit scale.");
      this.quantity = Objects.requireNonNull(quantity, "quantity");
      this.unitCost = Objects.requireNonNull(unitCost, "unitCost");
    }

    /** Returns the attempted acquired quantity. */
    public Quantity quantity() {
      return quantity;
    }

    /** Returns the attempted per-unit carrying cost. */
    public Money unitCost() {
      return unitCost;
    }
  }

  /**
   * Positive pools must clear this admission floor because carrying-cost truth is stored only as
   * money minor units, with no higher-resolution remainder carried alongside the pool.
   */
  private static void requireMinorUnitFloor(Quantity quantityOnHand, Money costPool) {
    if (quantityOnHand.isPositive() && costPool.minorUnits() < quantityOnHand.scaledUnits()) {
      throw new InventoryPoolMinorUnitFloorException(
          quantityOnHand, costPool, quantityOnHand.scaledUnits());
    }
  }
}
