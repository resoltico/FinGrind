package dev.erst.fingrind.contract.tax;

import dev.erst.fingrind.core.WireValue;
import java.util.List;

/** Declares whether the operator-supplied amount excludes or includes tax. */
public enum TaxInclusionMode implements WireValue {
  EXCLUSIVE,
  INCLUSIVE;

  @Override
  public String wireValue() {
    return name();
  }

  /** Returns every stable public tax-inclusion-mode wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(TaxInclusionMode.class);
  }

  /** Parses one stable public tax-inclusion-mode wire value. */
  public static TaxInclusionMode fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        TaxInclusionMode.class, wireValue, "Unsupported tax inclusion mode");
  }

  @Override
  public String toString() {
    return wireValue();
  }
}
