package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Canonical kinds for one durable bookkeeping audit event. */
public enum BookAuditEventKind implements WireValue {
  BOOK_OPENED("BOOK_OPENED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer periodCloseOrder) {
      rejectAccountPostingAndCloseOrder(wireValue(), accountCode, postingId, periodCloseOrder);
    }
  },
  ACCOUNT_DECLARED("ACCOUNT_DECLARED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer periodCloseOrder) {
      Objects.requireNonNull(accountCode, "accountCode");
      rejectPostingAndCloseOrder(wireValue(), postingId, periodCloseOrder);
    }
  },
  ACCOUNT_REACTIVATED("ACCOUNT_REACTIVATED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer periodCloseOrder) {
      Objects.requireNonNull(accountCode, "accountCode");
      rejectPostingAndCloseOrder(wireValue(), postingId, periodCloseOrder);
    }
  },
  POSTING_COMMITTED("POSTING_COMMITTED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer periodCloseOrder) {
      rejectAccountAndCloseOrder(wireValue(), accountCode, periodCloseOrder);
      Objects.requireNonNull(postingId, "postingId");
    }
  },
  POSTING_REVERSED("POSTING_REVERSED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer periodCloseOrder) {
      rejectAccountAndCloseOrder(wireValue(), accountCode, periodCloseOrder);
      Objects.requireNonNull(postingId, "postingId");
    }
  },
  BOOK_REKEYED("BOOK_REKEYED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer periodCloseOrder) {
      rejectAccountPostingAndCloseOrder(wireValue(), accountCode, postingId, periodCloseOrder);
    }
  },
  PERIOD_CLOSED("PERIOD_CLOSED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer periodCloseOrder) {
      rejectAccountAndPosting(wireValue(), accountCode, postingId);
      Objects.requireNonNull(periodCloseOrder, "periodCloseOrder");
    }
  };

  private final String wireValue;

  BookAuditEventKind(String wireValue) {
    this.wireValue = wireValue;
  }

  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns the stable wire values for every durable bookkeeping audit-event kind. */
  public static List<String> wireValues() {
    return WireValue.wireValues(BookAuditEventKind.class);
  }

  abstract void validatePayload(
      @Nullable AccountCode accountCode,
      @Nullable PostingId postingId,
      @Nullable Integer periodCloseOrder);

  private static void rejectAccountPostingAndCloseOrder(
      String wireValue,
      @Nullable AccountCode accountCode,
      @Nullable PostingId postingId,
      @Nullable Integer periodCloseOrder) {
    if (accountCode != null || postingId != null || periodCloseOrder != null) {
      throw new IllegalArgumentException(
          wireValue + " audit events must not carry accountCode, postingId, or periodCloseOrder.");
    }
  }

  private static void rejectPostingAndCloseOrder(
      String wireValue, @Nullable PostingId postingId, @Nullable Integer periodCloseOrder) {
    if (postingId != null || periodCloseOrder != null) {
      throw new IllegalArgumentException(
          wireValue + " audit events must not carry postingId or periodCloseOrder.");
    }
  }

  private static void rejectAccountAndCloseOrder(
      String wireValue, @Nullable AccountCode accountCode, @Nullable Integer periodCloseOrder) {
    if (accountCode != null || periodCloseOrder != null) {
      throw new IllegalArgumentException(
          wireValue + " audit events must not carry accountCode or periodCloseOrder.");
    }
  }

  private static void rejectAccountAndPosting(
      String wireValue, @Nullable AccountCode accountCode, @Nullable PostingId postingId) {
    if (accountCode != null || postingId != null) {
      throw new IllegalArgumentException(
          wireValue + " audit events must not carry accountCode or postingId.");
    }
  }
}
