package dev.erst.fingrind.core;

import java.util.List;

/** Canonical business-event vocabulary above the raw journal-entry escape hatch. */
public enum BusinessEventKind implements WireValue {
  ISSUE_INVOICE,
  RECEIVE_PAYMENT,
  RECORD_BILL,
  PAY_BILL,
  RECORD_SALE,
  PURCHASE_INVENTORY,
  RECORD_PAYROLL_RUN,
  RECORD_OWNER_DRAW,
  RECORD_BANK_FEE,
  RECORD_TAX_PAYMENT,
  OPENING_BALANCE_STATEMENT,
  PRIOR_PERIOD_ADJUSTMENT;

  @Override
  public String wireValue() {
    return switch (this) {
      case ISSUE_INVOICE -> "ISSUE_INVOICE";
      case RECEIVE_PAYMENT -> "RECEIVE_PAYMENT";
      case RECORD_BILL -> "RECORD_BILL";
      case PAY_BILL -> "PAY_BILL";
      case RECORD_SALE -> "RECORD_SALE";
      case PURCHASE_INVENTORY -> "PURCHASE_INVENTORY";
      case RECORD_PAYROLL_RUN -> "RECORD_PAYROLL_RUN";
      case RECORD_OWNER_DRAW -> "RECORD_OWNER_DRAW";
      case RECORD_BANK_FEE -> "RECORD_BANK_FEE";
      case RECORD_TAX_PAYMENT -> "RECORD_TAX_PAYMENT";
      case OPENING_BALANCE_STATEMENT -> "OPENING_BALANCE_STATEMENT";
      case PRIOR_PERIOD_ADJUSTMENT -> "PRIOR_PERIOD_ADJUSTMENT";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(BusinessEventKind.class);
  }

  /** Parses one stable public wire value. */
  public static BusinessEventKind fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        BusinessEventKind.class, wireValue, "Unsupported businessEventKind");
  }
}
