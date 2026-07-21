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
        @Nullable Integer closeOperationOrder) {
      rejectAccountPostingAndCloseOrder(wireValue(), accountCode, postingId, closeOperationOrder);
    }
  },
  ACCOUNT_DECLARED("ACCOUNT_DECLARED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer closeOperationOrder) {
      Objects.requireNonNull(accountCode, "accountCode");
      rejectPostingAndCloseOrder(wireValue(), postingId, closeOperationOrder);
    }
  },
  ACCOUNT_REACTIVATED("ACCOUNT_REACTIVATED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer closeOperationOrder) {
      Objects.requireNonNull(accountCode, "accountCode");
      rejectPostingAndCloseOrder(wireValue(), postingId, closeOperationOrder);
    }
  },
  ACCOUNT_RENAMED("ACCOUNT_RENAMED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer closeOperationOrder) {
      Objects.requireNonNull(accountCode, "accountCode");
      rejectPostingAndCloseOrder(wireValue(), postingId, closeOperationOrder);
    }
  },
  ACCOUNT_AMENDED("ACCOUNT_AMENDED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer closeOperationOrder) {
      Objects.requireNonNull(accountCode, "accountCode");
      rejectPostingAndCloseOrder(wireValue(), postingId, closeOperationOrder);
    }
  },
  ACCOUNT_RETIRED("ACCOUNT_RETIRED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer closeOperationOrder) {
      Objects.requireNonNull(accountCode, "accountCode");
      rejectPostingAndCloseOrder(wireValue(), postingId, closeOperationOrder);
    }
  },
  POSTING_COMMITTED("POSTING_COMMITTED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer closeOperationOrder) {
      rejectAccountAndCloseOrder(wireValue(), accountCode, closeOperationOrder);
      Objects.requireNonNull(postingId, "postingId");
    }
  },
  POSTING_REVERSED("POSTING_REVERSED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer closeOperationOrder) {
      rejectAccountAndCloseOrder(wireValue(), accountCode, closeOperationOrder);
      Objects.requireNonNull(postingId, "postingId");
    }
  },
  BOOK_REKEYED("BOOK_REKEYED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer closeOperationOrder) {
      rejectAccountPostingAndCloseOrder(wireValue(), accountCode, postingId, closeOperationOrder);
    }
  },
  BACKUP_CREATED("BACKUP_CREATED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer closeOperationOrder) {
      rejectAccountPostingAndCloseOrder(wireValue(), accountCode, postingId, closeOperationOrder);
    }
  },
  BACKUP_RESTORED("BACKUP_RESTORED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer closeOperationOrder) {
      rejectAccountPostingAndCloseOrder(wireValue(), accountCode, postingId, closeOperationOrder);
    }
  },
  BACKUP_CREATED_COMPENSATED("BACKUP_CREATED_COMPENSATED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer closeOperationOrder) {
      rejectAccountPostingAndCloseOrder(wireValue(), accountCode, postingId, closeOperationOrder);
    }
  },
  INTERIM_RESULT_SWEPT("INTERIM_RESULT_SWEPT") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer closeOperationOrder) {
      rejectAccountAndPosting(wireValue(), accountCode, postingId);
      Objects.requireNonNull(closeOperationOrder, "closeOperationOrder");
    }
  },
  FISCAL_YEAR_CLOSED("FISCAL_YEAR_CLOSED") {
    @Override
    void validatePayload(
        @Nullable AccountCode accountCode,
        @Nullable PostingId postingId,
        @Nullable Integer closeOperationOrder) {
      rejectAccountAndPosting(wireValue(), accountCode, postingId);
      Objects.requireNonNull(closeOperationOrder, "closeOperationOrder");
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
      @Nullable Integer closeOperationOrder);

  private static void rejectAccountPostingAndCloseOrder(
      String wireValue,
      @Nullable AccountCode accountCode,
      @Nullable PostingId postingId,
      @Nullable Integer closeOperationOrder) {
    if (accountCode != null || postingId != null || closeOperationOrder != null) {
      throw new IllegalArgumentException(
          wireValue
              + " audit events must not carry accountCode, postingId, or closeOperationOrder.");
    }
  }

  private static void rejectPostingAndCloseOrder(
      String wireValue, @Nullable PostingId postingId, @Nullable Integer closeOperationOrder) {
    if (postingId != null || closeOperationOrder != null) {
      throw new IllegalArgumentException(
          wireValue + " audit events must not carry postingId or closeOperationOrder.");
    }
  }

  private static void rejectAccountAndCloseOrder(
      String wireValue, @Nullable AccountCode accountCode, @Nullable Integer closeOperationOrder) {
    if (accountCode != null || closeOperationOrder != null) {
      throw new IllegalArgumentException(
          wireValue + " audit events must not carry accountCode or closeOperationOrder.");
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
