package dev.erst.fingrind.core;

/** Exact percentage rate stored as basis points. */
public record PercentageRate(int basisPoints) {
  /** Validates one exact percentage rate. */
  public PercentageRate {
    if (basisPoints < 0) {
      throw new IllegalArgumentException("Percentage rate must not be negative.");
    }
    if (basisPoints > 1_000_000) {
      throw new IllegalArgumentException("Percentage rate basis points exceed supported range.");
    }
  }
}
