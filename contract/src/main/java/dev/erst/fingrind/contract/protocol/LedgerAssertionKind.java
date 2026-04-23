package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Canonical wire kinds accepted inside ledger-plan assertion payloads. */
public enum LedgerAssertionKind implements WireValue {
  ACCOUNT_DECLARED("assert-account-declared"),
  ACCOUNT_ACTIVE("assert-account-active"),
  POSTING_EXISTS("assert-posting-exists"),
  ACCOUNT_BALANCE_EQUALS("assert-account-balance");

  private final String wireValue;

  LedgerAssertionKind(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  /** Returns the stable wire value for this assertion kind. */
  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(LedgerAssertionKind.class);
  }

  /** Parses one stable wire assertion kind. */
  public static LedgerAssertionKind fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        LedgerAssertionKind.class, wireValue, "Unsupported ledger assertion kind");
  }
}
