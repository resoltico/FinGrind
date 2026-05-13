package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** One append-only bookkeeping audit event destined for durable SQLite storage. */
public record BookAuditEvent(
    Instant recordedAt,
    BookAuditEventKind kind,
    @Nullable AccountCode accountCode,
    @Nullable PostingId postingId,
    @Nullable Integer periodCloseOrder) {
  /** Validates one append-only bookkeeping audit event before durable persistence. */
  public BookAuditEvent {
    Objects.requireNonNull(recordedAt, "recordedAt");
    Objects.requireNonNull(kind, "kind");
    kind.validatePayload(accountCode, postingId, periodCloseOrder);
  }

  /** Returns one audit event recording that a book session opened successfully. */
  public static BookAuditEvent bookOpened(Instant recordedAt) {
    return new BookAuditEvent(recordedAt, BookAuditEventKind.BOOK_OPENED, null, null, null);
  }

  /** Returns one audit event recording an account declaration or account reactivation. */
  public static BookAuditEvent accountDeclared(
      Instant recordedAt, AccountCode accountCode, boolean reactivated) {
    return new BookAuditEvent(
        recordedAt,
        reactivated ? BookAuditEventKind.ACCOUNT_REACTIVATED : BookAuditEventKind.ACCOUNT_DECLARED,
        accountCode,
        null,
        null);
  }

  /** Returns one audit event derived from a committed posting or reversal posting. */
  public static BookAuditEvent postingCommitted(CommittedPosting posting) {
    Objects.requireNonNull(posting, "posting");
    return new BookAuditEvent(
        posting.provenance().recordedAt(),
        posting.postingLineage().isReversal()
            ? BookAuditEventKind.POSTING_REVERSED
            : BookAuditEventKind.POSTING_COMMITTED,
        null,
        posting.postingId(),
        null);
  }

  /** Returns one audit event recording that book encryption was rekeyed successfully. */
  public static BookAuditEvent bookRekeyed(Instant recordedAt) {
    return new BookAuditEvent(recordedAt, BookAuditEventKind.BOOK_REKEYED, null, null, null);
  }

  /** Returns one audit event recording that one reporting period was closed durably. */
  public static BookAuditEvent periodClosed(Instant recordedAt, int periodCloseOrder) {
    return new BookAuditEvent(
        recordedAt, BookAuditEventKind.PERIOD_CLOSED, null, null, periodCloseOrder);
  }
}
