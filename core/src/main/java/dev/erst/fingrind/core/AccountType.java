package dev.erst.fingrind.core;

import java.util.List;

/** Canonical chart-of-accounts classification for one declared account. */
public enum AccountType implements WireValue {
  ASSET,
  LIABILITY,
  EQUITY,
  REVENUE,
  EXPENSE;

  /** Returns the stable public wire value for this account classification. */
  @Override
  public String wireValue() {
    return switch (this) {
      case ASSET -> "ASSET";
      case LIABILITY -> "LIABILITY";
      case EQUITY -> "EQUITY";
      case REVENUE -> "REVENUE";
      case EXPENSE -> "EXPENSE";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(AccountType.class);
  }

  /** Parses one stable public wire value. */
  public static AccountType fromWireValue(String wireValue) {
    return WireValue.fromWireValue(AccountType.class, wireValue, "Unsupported accountType");
  }
}
