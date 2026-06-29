package dev.erst.fingrind.contract.tax;

import dev.erst.fingrind.core.WireValue;
import java.util.List;

/** Filing cadence owned by one declared tax registration. */
public enum TaxObligationFrequency implements WireValue {
  MONTHLY,
  QUARTERLY,
  ANNUAL;

  @Override
  public String wireValue() {
    return name();
  }

  /** Returns every stable public tax-obligation-frequency wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(TaxObligationFrequency.class);
  }

  /** Parses one stable public tax-obligation-frequency wire value. */
  public static TaxObligationFrequency fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        TaxObligationFrequency.class, wireValue, "Unsupported tax obligation frequency");
  }

  @Override
  public String toString() {
    return wireValue();
  }
}
