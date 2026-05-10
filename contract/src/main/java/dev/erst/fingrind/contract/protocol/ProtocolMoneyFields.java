package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical field names for machine-facing exact money objects. */
public final class ProtocolMoneyFields {
  private ProtocolMoneyFields() {}

  /** Returns exact-money object fields in stable wire order. */
  public static List<String> fields() {
    return List.of(CURRENCY_CODE, MINOR_UNITS);
  }

  public static final String CURRENCY_CODE = ProtocolSharedRequestFields.CURRENCY_CODE;
  public static final String MINOR_UNITS = "minorUnits";
}
