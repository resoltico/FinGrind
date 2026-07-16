package dev.erst.fingrind.contract.payroll;

import dev.erst.fingrind.core.WireValue;
import java.util.List;

/** The two indivisible payment obligations created by one admitted Latvian monthly payroll run. */
public enum LatvianPayrollSettlementKind implements WireValue {
  NET_WAGES,
  STATE_REMITTANCE;

  @Override
  public String wireValue() {
    return name();
  }

  /** Returns every stable settlement-kind wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(LatvianPayrollSettlementKind.class);
  }

  /** Parses one published Latvian payroll settlement kind. */
  public static LatvianPayrollSettlementKind fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        LatvianPayrollSettlementKind.class,
        wireValue,
        "Unsupported Latvian payroll settlement kind");
  }
}
