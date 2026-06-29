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
    @Nullable Integer closeOperationOrder) {
  /** Validates one append-only bookkeeping audit event before durable persistence. */
  public BookAuditEvent {
    Objects.requireNonNull(recordedAt, "recordedAt");
    Objects.requireNonNull(kind, "kind");
    kind.validatePayload(accountCode, postingId, closeOperationOrder);
  }

  /** Returns one audit event recording that a book session opened successfully. */
  public static BookAuditEvent bookOpened(Instant recordedAt) {
    return new BookAuditEvent(recordedAt, BookAuditEventKind.BOOK_OPENED, null, null, null);
  }

  /** Returns one audit event recording an account declaration. */
  public static BookAuditEvent accountDeclared(Instant recordedAt, AccountCode accountCode) {
    return new BookAuditEvent(
        recordedAt, BookAuditEventKind.ACCOUNT_DECLARED, accountCode, null, null);
  }

  /** Returns one audit event recording an account reactivation. */
  public static BookAuditEvent accountReactivated(Instant recordedAt, AccountCode accountCode) {
    return new BookAuditEvent(
        recordedAt, BookAuditEventKind.ACCOUNT_REACTIVATED, accountCode, null, null);
  }

  /** Returns one audit event recording an account rename. */
  public static BookAuditEvent accountRenamed(Instant recordedAt, AccountCode accountCode) {
    return new BookAuditEvent(
        recordedAt, BookAuditEventKind.ACCOUNT_RENAMED, accountCode, null, null);
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

  /** Returns one audit event recording a successful protected-book backup export. */
  public static BookAuditEvent backupCreated(Instant recordedAt) {
    return new BookAuditEvent(recordedAt, BookAuditEventKind.BACKUP_CREATED, null, null, null);
  }

  /** Returns one audit event recording a successful protected-book backup restore. */
  public static BookAuditEvent backupRestored(Instant recordedAt) {
    return new BookAuditEvent(recordedAt, BookAuditEventKind.BACKUP_RESTORED, null, null, null);
  }

  /** Returns one audit event compensating a previously appended backup-created fact. */
  public static BookAuditEvent backupCreatedCompensated(Instant recordedAt) {
    return new BookAuditEvent(
        recordedAt, BookAuditEventKind.BACKUP_CREATED_COMPENSATED, null, null, null);
  }

  /** Returns one audit event recording a successful protected-book rollback restore. */
  public static BookAuditEvent rekeyRollbackRestored(Instant recordedAt) {
    return new BookAuditEvent(
        recordedAt, BookAuditEventKind.REKEY_ROLLBACK_RESTORED, null, null, null);
  }

  /** Returns one audit event recording a successful protected-book rollback deletion. */
  public static BookAuditEvent rekeyRollbackDeleted(Instant recordedAt) {
    return new BookAuditEvent(
        recordedAt, BookAuditEventKind.REKEY_ROLLBACK_DELETED, null, null, null);
  }

  /** Returns one audit event compensating a previously appended rollback-deleted fact. */
  public static BookAuditEvent rekeyRollbackDeletedCompensated(Instant recordedAt) {
    return new BookAuditEvent(
        recordedAt, BookAuditEventKind.REKEY_ROLLBACK_DELETED_COMPENSATED, null, null, null);
  }

  /** Returns one audit event recording that one interim result sweep closed durably. */
  public static BookAuditEvent interimResultSwept(Instant recordedAt, int closeOperationOrder) {
    return new BookAuditEvent(
        recordedAt, BookAuditEventKind.INTERIM_RESULT_SWEPT, null, null, closeOperationOrder);
  }

  /** Returns one audit event recording that one fiscal-year close committed durably. */
  public static BookAuditEvent fiscalYearClosed(Instant recordedAt, int closeOperationOrder) {
    return new BookAuditEvent(
        recordedAt, BookAuditEventKind.FISCAL_YEAR_CLOSED, null, null, closeOperationOrder);
  }
}
