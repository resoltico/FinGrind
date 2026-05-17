package dev.erst.fingrind.core;

import java.util.List;

/** Canonical evidence-document vocabulary for accounting-event audit support. */
public enum SourceDocumentType implements WireValue {
  INVOICE,
  CREDIT_NOTE,
  BILL,
  PAYMENT_RECEIPT,
  BANK_STATEMENT,
  TAX_NOTICE,
  PAYROLL_REGISTER,
  CONTRACT,
  INVENTORY_RECEIPT,
  INVENTORY_ISSUE,
  JOURNAL_MEMO,
  OTHER;

  @Override
  public String wireValue() {
    return switch (this) {
      case INVOICE -> "INVOICE";
      case CREDIT_NOTE -> "CREDIT_NOTE";
      case BILL -> "BILL";
      case PAYMENT_RECEIPT -> "PAYMENT_RECEIPT";
      case BANK_STATEMENT -> "BANK_STATEMENT";
      case TAX_NOTICE -> "TAX_NOTICE";
      case PAYROLL_REGISTER -> "PAYROLL_REGISTER";
      case CONTRACT -> "CONTRACT";
      case INVENTORY_RECEIPT -> "INVENTORY_RECEIPT";
      case INVENTORY_ISSUE -> "INVENTORY_ISSUE";
      case JOURNAL_MEMO -> "JOURNAL_MEMO";
      case OTHER -> "OTHER";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(SourceDocumentType.class);
  }

  /** Parses one stable public wire value. */
  public static SourceDocumentType fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        SourceDocumentType.class, wireValue, "Unsupported sourceDocumentType");
  }
}
