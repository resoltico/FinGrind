package dev.erst.fingrind.core;

/** Stable public comparative-mode vocabulary for report capabilities and CLI parsing. */
public enum ComparativeMode implements WireValue {
  NONE("none"),
  PRIOR_PERIOD("same-period-prior-year"),
  RANGE("range");

  private final String wireValue;

  ComparativeMode(String wireValue) {
    this.wireValue = wireValue;
  }

  @Override
  public String wireValue() {
    return wireValue;
  }
}
