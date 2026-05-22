package dev.erst.fingrind.core;

import java.util.List;

/** Canonical persisted accounting-policy profile selected when one book is initialized. */
public enum AccountingPolicyProfile implements WireValue {
  INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1;

  @Override
  public String wireValue() {
    return switch (this) {
      case INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1 -> "INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1";
    };
  }

  /** Returns every stable policy-profile wire value. */
  public static List<String> wireValues() {
    return WireValue.wireValues(AccountingPolicyProfile.class);
  }

  /** Parses one stable policy-profile wire value. */
  public static AccountingPolicyProfile fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        AccountingPolicyProfile.class, wireValue, "Unsupported policyProfile");
  }
}
