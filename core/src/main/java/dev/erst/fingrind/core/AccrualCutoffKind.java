package dev.erst.fingrind.core;

import java.util.List;

/** Durable lifecycle kinds owned by the accrual cut-off context. */
public enum AccrualCutoffKind implements WireValue {
  PREPAYMENT,
  DEFERRED_REVENUE,
  ACCRUED_EXPENSE;

  @Override
  public String wireValue() {
    return name();
  }

  /** Returns every stable lifecycle-kind value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(AccrualCutoffKind.class);
  }

  /** Parses one stable lifecycle-kind value. */
  public static AccrualCutoffKind fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        AccrualCutoffKind.class, wireValue, "Unsupported accrual cut-off kind");
  }
}
