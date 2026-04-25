package dev.erst.fingrind.jazzer.support;

import java.util.Objects;

/** Closed replay-harness vocabulary that owns the stable harness keys. */
public enum JazzerHarnessKind {
  CLI_REQUEST("cli-request"),
  LEDGER_PLAN_REQUEST("ledger-plan-request"),
  POSTING_WORKFLOW("posting-workflow"),
  SQLITE_BOOK_ROUND_TRIP("sqlite-book-roundtrip");

  private final String key;

  JazzerHarnessKind(String key) {
    this.key = Objects.requireNonNull(key, "key");
  }

  /** Returns the stable external key used by wrappers, topology, and regression metadata. */
  public String key() {
    return key;
  }

  /** Resolves one harness kind from its stable external key. */
  public static JazzerHarnessKind fromKey(String key) {
    Objects.requireNonNull(key, "key must not be null");
    return switch (key) {
      case "cli-request" -> CLI_REQUEST;
      case "ledger-plan-request" -> LEDGER_PLAN_REQUEST;
      case "posting-workflow" -> POSTING_WORKFLOW;
      case "sqlite-book-roundtrip" -> SQLITE_BOOK_ROUND_TRIP;
      default -> throw new IllegalArgumentException("Unknown Jazzer harness: " + key);
    };
  }
}
