package dev.erst.fingrind.contract.tax;

import dev.erst.fingrind.core.WireValue;
import java.util.List;

/** Declares how one tax code participates in sale or expense recognition. */
public enum TaxApplicationKind implements WireValue {
  OUTPUT_SALE,
  INPUT_EXPENSE_RECOVERABLE,
  INPUT_EXPENSE_NONRECOVERABLE;

  @Override
  public String wireValue() {
    return name();
  }

  /** Returns every stable public tax-application-kind wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(TaxApplicationKind.class);
  }

  /** Parses one stable public tax-application-kind wire value. */
  public static TaxApplicationKind fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        TaxApplicationKind.class, wireValue, "Unsupported tax application kind");
  }

  @Override
  public String toString() {
    return wireValue();
  }
}
