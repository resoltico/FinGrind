package dev.erst.fingrind.contract.protocol;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Canonical wire kinds accepted inside ledger-plan assertion payloads. */
public enum LedgerAssertionKind {
  ACCOUNT_DECLARED("assert-account-declared"),
  ACCOUNT_ACTIVE("assert-account-active"),
  POSTING_EXISTS("assert-posting-exists"),
  ACCOUNT_BALANCE_EQUALS("assert-account-balance");

  private final String wireValue;

  LedgerAssertionKind(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  /** Returns the stable wire value for this assertion kind. */
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return Arrays.stream(values()).map(LedgerAssertionKind::wireValue).toList();
  }

  /** Parses one stable wire assertion kind. */
  public static LedgerAssertionKind fromWireValue(String wireValue) {
    Objects.requireNonNull(wireValue, "wireValue");
    return Arrays.stream(values())
        .filter(kind -> kind.wireValue.equals(wireValue))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Unsupported ledger assertion kind: " + wireValue));
  }
}
