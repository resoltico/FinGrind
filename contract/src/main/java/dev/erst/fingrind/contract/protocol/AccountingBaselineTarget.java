package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;

/** Declared accounting-foundation maturity target for the current FinGrind product surface. */
public enum AccountingBaselineTarget implements WireValue {
  BOOKKEEPING_KERNEL_ONLY("bookkeeping-kernel-only"),
  INTERNAL_MANAGEMENT_STATEMENTS("internal-management-statements"),
  BASIC_STANDARD_REPORTING_FOUNDATION("basic-standard-reporting-foundation"),
  IFRS_FOR_SMES_PARITY("ifrs-for-smes-parity"),
  FULL_LOCAL_GAAP_OR_STATUTORY_PACK("full-local-gaap-or-statutory-pack");

  private final String wireValue;

  AccountingBaselineTarget(String wireValue) {
    this.wireValue = wireValue;
  }

  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(AccountingBaselineTarget.class);
  }

  /** Parses one stable public wire value. */
  public static AccountingBaselineTarget fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        AccountingBaselineTarget.class, wireValue, "Unsupported accountingBaselineTarget");
  }
}
