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
        @Nullable Integer periodResultTransferOrder) {
      rejectAccountPostingAndCloseOrder(
          wireValue(), accountCode, postingId, periodResultTransferOrder);
    }
  },
  ACCOUNT_DECLARED("ACCOUNT_DECLARED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer periodResultTransferOrder) {
      Objects.requireNonNull(accountCode, "accountCode");
      rejectPostingAndCloseOrder(wireValue(), postingId, periodResultTransferOrder);
    }
  },
  ACCOUNT_REACTIVATED("ACCOUNT_REACTIVATED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer periodResultTransferOrder) {
      Objects.requireNonNull(accountCode, "accountCode");
      rejectPostingAndCloseOrder(wireValue(), postingId, periodResultTransferOrder);
    }
  },
  POSTING_COMMITTED("POSTING_COMMITTED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer periodResultTransferOrder) {
      rejectAccountAndCloseOrder(wireValue(), accountCode, periodResultTransferOrder);
      Objects.requireNonNull(postingId, "postingId");
    }
  },
  POSTING_REVERSED("POSTING_REVERSED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer periodResultTransferOrder) {
      rejectAccountAndCloseOrder(wireValue(), accountCode, periodResultTransferOrder);
      Objects.requireNonNull(postingId, "postingId");
    }
  },
  BOOK_REKEYED("BOOK_REKEYED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer periodResultTransferOrder) {
      rejectAccountPostingAndCloseOrder(
          wireValue(), accountCode, postingId, periodResultTransferOrder);
    }
  },
  BACKUP_CREATED("BACKUP_CREATED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer periodResultTransferOrder) {
      rejectAccountPostingAndCloseOrder(
          wireValue(), accountCode, postingId, periodResultTransferOrder);
    }
  },
  BACKUP_RESTORED("BACKUP_RESTORED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer periodResultTransferOrder) {
      rejectAccountPostingAndCloseOrder(
          wireValue(), accountCode, postingId, periodResultTransferOrder);
    }
  },
  REKEY_ROLLBACK_RESTORED("REKEY_ROLLBACK_RESTORED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer periodResultTransferOrder) {
      rejectAccountPostingAndCloseOrder(
          wireValue(), accountCode, postingId, periodResultTransferOrder);
    }
  },
  REKEY_ROLLBACK_DELETED("REKEY_ROLLBACK_DELETED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer periodResultTransferOrder) {
      rejectAccountPostingAndCloseOrder(
          wireValue(), accountCode, postingId, periodResultTransferOrder);
    }
  },
  BACKUP_CREATED_COMPENSATED("BACKUP_CREATED_COMPENSATED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer periodResultTransferOrder) {
      rejectAccountPostingAndCloseOrder(
          wireValue(), accountCode, postingId, periodResultTransferOrder);
    }
  },
  REKEY_ROLLBACK_DELETED_COMPENSATED("REKEY_ROLLBACK_DELETED_COMPENSATED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer periodResultTransferOrder) {
      rejectAccountPostingAndCloseOrder(
          wireValue(), accountCode, postingId, periodResultTransferOrder);
    }
  },
  PERIOD_RESULT_TRANSFERRED("PERIOD_RESULT_TRANSFERRED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer periodResultTransferOrder) {
      rejectAccountAndPosting(wireValue(), accountCode, postingId);
      Objects.requireNonNull(periodResultTransferOrder, "periodResultTransferOrder");
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
      @Nullable Integer periodResultTransferOrder);

  private static void rejectAccountPostingAndCloseOrder(
      String wireValue,
      @Nullable AccountCode accountCode,
      @Nullable PostingId postingId,
      @Nullable Integer periodResultTransferOrder) {
    if (accountCode != null || postingId != null || periodResultTransferOrder != null) {
      throw new IllegalArgumentException(
          wireValue
              + " audit events must not carry accountCode, postingId, or periodResultTransferOrder.");
    }
  }

  private static void rejectPostingAndCloseOrder(
      String wireValue,
      @Nullable PostingId postingId,
      @Nullable Integer periodResultTransferOrder) {
    if (postingId != null || periodResultTransferOrder != null) {
      throw new IllegalArgumentException(
          wireValue + " audit events must not carry postingId or periodResultTransferOrder.");
    }
  }

  private static void rejectAccountAndCloseOrder(
      String wireValue,
      @Nullable AccountCode accountCode,
      @Nullable Integer periodResultTransferOrder) {
    if (accountCode != null || periodResultTransferOrder != null) {
      throw new IllegalArgumentException(
          wireValue + " audit events must not carry accountCode or periodResultTransferOrder.");
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
