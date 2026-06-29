package dev.erst.fingrind.contract.tax;

/** Exact tax rate carried as parts per million of one whole amount. */
public record TaxRate(int partsPerMillionOfWhole) {
  public static final int WHOLE = 1_000_000;

  /** Validates one exact tax rate. */
  public TaxRate {
    if (partsPerMillionOfWhole < 0 || partsPerMillionOfWhole > WHOLE) {
      throw new IllegalArgumentException(
          "Tax rate partsPerMillionOfWhole must be between 0 and %d.".formatted(WHOLE));
    }
  }

  /** Returns the canonical percentage text with four fractional digits. */
  public String canonicalPercent() {
    int wholePercent = partsPerMillionOfWhole / 10_000;
    int fractional = partsPerMillionOfWhole % 10_000;
    return "%d.%04d".formatted(wholePercent, fractional);
  }
}
