package dev.erst.fingrind.core;

import java.util.List;

/** Canonical durable origin kinds preserved for committed postings in one protected book. */
public enum PostingOriginKind implements WireValue {
  JOURNAL,
  CASH_REVENUE,
  CASH_EXPENSE,
  EQUITY_CONTRIBUTION,
  EQUITY_WITHDRAWAL,
  OPEN_ACCOUNTING_POSITION,
  REVERSAL_ADJUSTMENT,
  PERIOD_RESULT_TRANSFER;

  @Override
  public String wireValue() {
    return switch (this) {
      case JOURNAL -> "JOURNAL";
      case CASH_REVENUE -> "CASH_REVENUE";
      case CASH_EXPENSE -> "CASH_EXPENSE";
      case EQUITY_CONTRIBUTION -> "EQUITY_CONTRIBUTION";
      case EQUITY_WITHDRAWAL -> "EQUITY_WITHDRAWAL";
      case OPEN_ACCOUNTING_POSITION -> "OPEN_ACCOUNTING_POSITION";
      case REVERSAL_ADJUSTMENT -> "REVERSAL_ADJUSTMENT";
      case PERIOD_RESULT_TRANSFER -> "PERIOD_RESULT_TRANSFER";
    };
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(PostingOriginKind.class);
  }

  /** Parses one stable origin-kind wire value. */
  public static PostingOriginKind fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        PostingOriginKind.class, wireValue, "Unsupported postingOriginKind");
  }
}
