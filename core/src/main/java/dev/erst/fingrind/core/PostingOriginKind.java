package dev.erst.fingrind.core;

import java.util.List;

/** Canonical durable origin kinds preserved for committed postings in one protected book. */
public enum PostingOriginKind implements WireValue {
  DIRECT_JOURNAL,
  SALE_SETTLED,
  SALE_ON_CREDIT,
  PURCHASE_SETTLED,
  PURCHASE_ON_CREDIT,
  EXPENSE_SETTLED,
  EXPENSE_ON_CREDIT,
  RECEIPT,
  PAYMENT,
  OWNER_CONTRIBUTION,
  OWNER_WITHDRAWAL,
  OPENING_POSITION,
  REVERSAL,
  INTERIM_RESULT_SWEEP,
  FISCAL_YEAR_CLOSE;

  @Override
  public String wireValue() {
    return name();
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
