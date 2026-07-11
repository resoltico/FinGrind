package dev.erst.fingrind.core;

import java.util.Objects;

/**
 * Exact non-negative quantity represented in scaled integer units without embedded unit meaning.
 */
public final class Quantity implements Comparable<Quantity> {
  private static final int MAX_SUPPORTED_SCALE = 9;

  private final int scale;
  private final long scaledUnits;

  private Quantity(int scale, long scaledUnits) {
    requireSupportedScale(scale);
    if (scaledUnits < 0L) {
      throw new IllegalArgumentException("Quantity scaled units must not be negative.");
    }
    this.scale = scale;
    this.scaledUnits = scaledUnits;
  }

  /** Creates one exact quantity directly from scaled integer units. */
  public static Quantity ofScaledUnits(int scale, long scaledUnits) {
    return new Quantity(scale, scaledUnits);
  }

  /** Returns the largest exact quantity scale FinGrind accepts in the shared kernel. */
  public static int maxSupportedScale() {
    return MAX_SUPPORTED_SCALE;
  }

  /** Returns the maximum supported ASCII-digit count for exact non-negative scaled units. */
  public static int maxScaledUnitsDigitCount() {
    return QuantityTextSupport.maxScaledUnitsDigitCount();
  }

  /** Returns one zero quantity at the selected exact scale. */
  public static Quantity zero(int scale) {
    return new Quantity(scale, 0L);
  }

  /** Parses one non-negative plain-decimal quantity at the selected exact scale. */
  public static Quantity parse(int scale, String quantityText) {
    return QuantityTextSupport.parse(scale, quantityText);
  }

  /** Returns the exact decimal scale carried by this quantity. */
  public int scale() {
    return scale;
  }

  /** Returns the authoritative exact scaled integer units. */
  public long scaledUnits() {
    return scaledUnits;
  }

  /** Returns whether this quantity is zero. */
  public boolean isZero() {
    return scaledUnits == 0L;
  }

  /** Returns whether this quantity is strictly positive. */
  public boolean isPositive() {
    return scaledUnits > 0L;
  }

  /** Returns one exact canonical decimal string at this quantity's scale. */
  public String canonicalDecimal() {
    return QuantityTextSupport.canonicalDecimal(scaledUnits, scale);
  }

  /** Adds two quantities at the same exact scale. */
  public Quantity plus(Quantity other) {
    requireSameScale(other);
    return new Quantity(scale, Math.addExact(scaledUnits, other.scaledUnits));
  }

  /** Subtracts one smaller or equal quantity at the same exact scale. */
  public Quantity minus(Quantity other) {
    requireSameScale(other);
    if (scaledUnits < other.scaledUnits) {
      throw new IllegalArgumentException("Quantity subtraction would produce a negative result.");
    }
    return new Quantity(scale, Math.subtractExact(scaledUnits, other.scaledUnits));
  }

  @Override
  public int compareTo(Quantity other) {
    requireSameScale(other);
    return Long.compare(scaledUnits, other.scaledUnits);
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || (other instanceof Quantity that
            && scale == that.scale
            && scaledUnits == that.scaledUnits);
  }

  @Override
  public int hashCode() {
    return Objects.hash(scale, scaledUnits);
  }

  @Override
  public String toString() {
    return "Quantity[scale="
        + scale
        + ", scaledUnits="
        + scaledUnits
        + ", canonicalDecimal="
        + canonicalDecimal()
        + "]";
  }

  static void requireSupportedScale(int scale) {
    if (scale < 0 || scale > MAX_SUPPORTED_SCALE) {
      throw new IllegalArgumentException(
          "Quantity scale must be between 0 and %d inclusive.".formatted(MAX_SUPPORTED_SCALE));
    }
  }

  private void requireSameScale(Quantity other) {
    Objects.requireNonNull(other, "other");
    if (scale != other.scale) {
      throw new IllegalArgumentException("Quantity values must share one exact scale.");
    }
  }
}
