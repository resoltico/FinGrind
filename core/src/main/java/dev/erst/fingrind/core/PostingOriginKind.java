package dev.erst.fingrind.core;

import java.util.List;

/** Canonical durable origin kinds preserved for committed postings in one protected book. */
public enum PostingOriginKind implements WireValue {
  DIRECT_JOURNAL,
  SALE,
  EXPENSE,
  OWNER_CONTRIBUTION,
  OWNER_WITHDRAWAL,
  OPENING_POSITION,
  REVERSAL,
  INTERIM_RESULT_SWEEP,
  FISCAL_YEAR_CLOSE;

  @Override
  public String wireValue() {
    return switch (this) {
      case DIRECT_JOURNAL -> "DIRECT_JOURNAL";
      case SALE -> "SALE";
      case EXPENSE -> "EXPENSE";
      case OWNER_CONTRIBUTION -> "OWNER_CONTRIBUTION";
      case OWNER_WITHDRAWAL -> "OWNER_WITHDRAWAL";
      case OPENING_POSITION -> "OPENING_POSITION";
      case REVERSAL -> "REVERSAL";
      case INTERIM_RESULT_SWEEP -> "INTERIM_RESULT_SWEEP";
      case FISCAL_YEAR_CLOSE -> "FISCAL_YEAR_CLOSE";
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
