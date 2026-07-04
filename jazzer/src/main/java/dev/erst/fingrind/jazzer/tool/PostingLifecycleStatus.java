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
  IDEMPOTENT_REPLAY("idempotent-replay"),
  RELOADED("reloaded"),
  BOOK_NOT_INITIALIZED(PostingRejection.bookNotInitializedCode()),
  ACCOUNT_STATE_VIOLATIONS("account-state-violations"),
  ENTRY_SEMANTICS_VIOLATIONS("entry-semantics-violations"),
  UNKNOWN_ACCOUNT("unknown-account"),
  INACTIVE_ACCOUNT("inactive-account"),
  IDEMPOTENCY_KEY_CONFLICT("idempotency-key-conflict"),
  POSTING_EFFECTIVE_DATE_IN_FUTURE("posting-effective-date-in-future"),
  BOOK_FUNCTIONAL_CURRENCY_MISMATCH("book-functional-currency-mismatch"),
  CLOSED_PERIOD_VIOLATION("closed-period-violation"),
  OPEN_ACCOUNTING_POSITION_WINDOW_CLOSED("open-accounting-position-window-closed"),
  OPEN_ACCOUNTING_POSITION_TOUCHES_NOMINAL_ACCOUNT(
      "open-accounting-position-touches-nominal-account"),
  RESERVED_RESULT_CLASSIFICATION("reserved-result-classification"),
  REVERSAL_TARGET_NOT_FOUND("reversal-target-not-found"),
  REVERSAL_TARGET_IS_REVERSAL("reversal-target-is-reversal"),
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
