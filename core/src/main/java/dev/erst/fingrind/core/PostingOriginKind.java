package dev.erst.fingrind.core;

import java.util.List;

/** Canonical durable origin kinds preserved for committed postings in one protected book. */
public enum PostingOriginKind implements WireValue {
  CASH_REVENUE,
  CASH_EXPENSE,
  EQUITY_CONTRIBUTION,
  EQUITY_WITHDRAWAL,
  OPENING_BALANCE_ADJUSTMENT,
  CORRECTION_ADJUSTMENT,
  REVERSAL_ADJUSTMENT,
  PERIOD_RESULT_TRANSFER;

  @Override
  public String wireValue() {
    return switch (this) {
      case CASH_REVENUE -> "CASH_REVENUE";
      case CASH_EXPENSE -> "CASH_EXPENSE";
      case EQUITY_CONTRIBUTION -> "EQUITY_CONTRIBUTION";
      case EQUITY_WITHDRAWAL -> "EQUITY_WITHDRAWAL";
      case OPENING_BALANCE_ADJUSTMENT -> "OPENING_BALANCE_ADJUSTMENT";
      case CORRECTION_ADJUSTMENT -> "CORRECTION_ADJUSTMENT";
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
