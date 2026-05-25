package dev.erst.fingrind.core;

import java.util.Objects;

/** Canonical posting-kind inclusion vocabulary exposed by read/query/report surfaces. */
public enum PostingCoverage implements WireValue {
  ALL_POSTING_KINDS("all-posting-kinds"),
  NON_CLOSING_POSTINGS("non-closing-postings");

  private final String wireValue;

  PostingCoverage(String wireValue) {
    this.wireValue = wireValue;
  }

  /** Returns the stable machine-readable wire token for this coverage mode. */
  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable public wire token in declaration order. */
  public static java.util.List<String> wireValues() {
    return WireValue.wireValues(PostingCoverage.class);
  }

  /** Parses one stable public wire token into the canonical coverage mode. */
  public static PostingCoverage fromWireValue(String wireValue) {
    return WireValue.fromWireValue(PostingCoverage.class, wireValue, "Unsupported postingCoverage");
  }

  /** Returns whether this coverage mode excludes generated transfer postings. */
  public boolean isNonClosingOnly() {
    return this == NON_CLOSING_POSTINGS;
  }

  /** Returns whether one posting kind belongs to this public coverage mode. */
  public boolean includes(PostingKind postingKind) {
    Objects.requireNonNull(postingKind, "postingKind");
    return switch (this) {
      case ALL_POSTING_KINDS -> true;
      case NON_CLOSING_POSTINGS -> postingKind != PostingKind.PERIOD_RESULT_TRANSFER;
    };
  }
}
