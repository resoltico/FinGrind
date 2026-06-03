package dev.erst.fingrind.contract.workflow;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Canonical wire kinds for typed ledger facts recorded in plan journals. */
public enum LedgerFactKind implements WireValue {
  TEXT("text"),
  FLAG("flag"),
  COUNT("count"),
  MONEY("money"),
  GROUP("group");

  private final String wireValue;

  LedgerFactKind(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(LedgerFactKind.class);
  }

  /** Parses one stable wire fact kind. */
  public static LedgerFactKind fromWireValue(String wireValue) {
    return WireValue.fromWireValue(LedgerFactKind.class, wireValue, "Unsupported ledger fact kind");
  }
}
