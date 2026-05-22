package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Closed replay vocabulary for posting-workflow and SQLite lifecycle checkpoints. */
public enum PostingLifecycleStatus implements WireValue {
  NOT_RUN("not-run"),
  PREFLIGHT_ACCEPTED("preflight-accepted"),
  COMMITTED("committed"),
  RELOADED("reloaded"),
  BOOK_NOT_INITIALIZED(PostingRejection.bookNotInitializedCode()),
  ACCOUNT_STATE_VIOLATIONS("account-state-violations"),
  UNKNOWN_ACCOUNT("unknown-account"),
  INACTIVE_ACCOUNT("inactive-account"),
  DUPLICATE_IDEMPOTENCY_KEY("duplicate-idempotency-key"),
  BOOK_FUNCTIONAL_CURRENCY_MISMATCH("book-functional-currency-mismatch"),
  CLOSED_PERIOD_VIOLATION("closed-period-violation"),
  OPENING_BALANCE_WINDOW_CLOSED("opening-balance-window-closed"),
  OPENING_BALANCE_TOUCHES_NOMINAL_ACCOUNT("opening-balance-touches-nominal-account"),
  CLOSING_EQUITY_ACCOUNT_RESERVED("closing-equity-account-reserved"),
  REVERSAL_TARGET_NOT_FOUND("reversal-target-not-found"),
  REVERSAL_ALREADY_EXISTS("reversal-already-exists"),
  REVERSAL_DOES_NOT_NEGATE_TARGET("reversal-does-not-negate-target");

  private final String wireValue;

  PostingLifecycleStatus(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable lifecycle-status wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(PostingLifecycleStatus.class);
  }

  /** Parses one stable lifecycle-status wire value. */
  public static PostingLifecycleStatus fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        PostingLifecycleStatus.class, wireValue, "Unsupported posting lifecycle status");
  }
}
