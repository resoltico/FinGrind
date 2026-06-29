package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for append-only bookkeeping audit events and their durable kinds. */
class BookAuditEventTest {
  private static final Instant FIXED_INSTANT = Instant.parse("2026-05-12T12:34:56Z");

  @Test
  void factories_buildCanonicalAuditEvents() {
    AccountCode accountCode = new AccountCode("1000");
    CommittedPosting standardPosting = committedPosting("posting-1", PostingLineageModel.direct());
    CommittedPosting reversalPosting =
        committedPosting(
            "posting-2",
            PostingLineageModel.reversal(
                new dev.erst.fingrind.core.ReversalReference(new PostingId("posting-1")),
                new dev.erst.fingrind.core.ReversalReason("correction")));

    assertEquals(
        new BookAuditEvent(FIXED_INSTANT, BookAuditEventKind.BOOK_OPENED, null, null, null),
        BookAuditEvent.bookOpened(FIXED_INSTANT));
    assertEquals(
        new BookAuditEvent(
            FIXED_INSTANT, BookAuditEventKind.ACCOUNT_DECLARED, accountCode, null, null),
        BookAuditEvent.accountDeclared(FIXED_INSTANT, accountCode));
    assertEquals(
        new BookAuditEvent(
            FIXED_INSTANT, BookAuditEventKind.ACCOUNT_REACTIVATED, accountCode, null, null),
        BookAuditEvent.accountReactivated(FIXED_INSTANT, accountCode));
    assertEquals(
        new BookAuditEvent(
            FIXED_INSTANT, BookAuditEventKind.ACCOUNT_RENAMED, accountCode, null, null),
        BookAuditEvent.accountRenamed(FIXED_INSTANT, accountCode));
    assertEquals(
        new BookAuditEvent(
            FIXED_INSTANT,
            BookAuditEventKind.POSTING_COMMITTED,
            null,
            standardPosting.postingId(),
            null),
        BookAuditEvent.postingCommitted(standardPosting));
    assertEquals(
        new BookAuditEvent(
            FIXED_INSTANT,
            BookAuditEventKind.POSTING_REVERSED,
            null,
            reversalPosting.postingId(),
            null),
        BookAuditEvent.postingCommitted(reversalPosting));
    assertEquals(
        new BookAuditEvent(FIXED_INSTANT, BookAuditEventKind.BOOK_REKEYED, null, null, null),
        BookAuditEvent.bookRekeyed(FIXED_INSTANT));
    assertEquals(
        new BookAuditEvent(FIXED_INSTANT, BookAuditEventKind.BACKUP_CREATED, null, null, null),
        BookAuditEvent.backupCreated(FIXED_INSTANT));
    assertEquals(
        new BookAuditEvent(FIXED_INSTANT, BookAuditEventKind.BACKUP_RESTORED, null, null, null),
        BookAuditEvent.backupRestored(FIXED_INSTANT));
    assertEquals(
        new BookAuditEvent(
            FIXED_INSTANT, BookAuditEventKind.BACKUP_CREATED_COMPENSATED, null, null, null),
        BookAuditEvent.backupCreatedCompensated(FIXED_INSTANT));
    assertEquals(
        new BookAuditEvent(
            FIXED_INSTANT, BookAuditEventKind.REKEY_ROLLBACK_RESTORED, null, null, null),
        BookAuditEvent.rekeyRollbackRestored(FIXED_INSTANT));
    assertEquals(
        new BookAuditEvent(
            FIXED_INSTANT, BookAuditEventKind.REKEY_ROLLBACK_DELETED, null, null, null),
        BookAuditEvent.rekeyRollbackDeleted(FIXED_INSTANT));
    assertEquals(
        new BookAuditEvent(
            FIXED_INSTANT, BookAuditEventKind.REKEY_ROLLBACK_DELETED_COMPENSATED, null, null, null),
        BookAuditEvent.rekeyRollbackDeletedCompensated(FIXED_INSTANT));
    assertEquals(
        new BookAuditEvent(FIXED_INSTANT, BookAuditEventKind.INTERIM_RESULT_SWEPT, null, null, 7),
        BookAuditEvent.interimResultSwept(FIXED_INSTANT, 7));
    assertEquals(
        new BookAuditEvent(FIXED_INSTANT, BookAuditEventKind.FISCAL_YEAR_CLOSED, null, null, 8),
        BookAuditEvent.fiscalYearClosed(FIXED_INSTANT, 8));
  }

  @Test
  void constructor_rejectsFieldShapesThatDoNotMatchTheAuditKind() {
    AccountCode accountCode = new AccountCode("1000");
    PostingId postingId = new PostingId("posting-1");

    IllegalArgumentException bookOpenedFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BookAuditEvent(
                    FIXED_INSTANT, BookAuditEventKind.BOOK_OPENED, accountCode, null, null));
    assertEquals(
        "BOOK_OPENED audit events must not carry accountCode, postingId, or closeOperationOrder.",
        bookOpenedFailure.getMessage());

    IllegalArgumentException bookOpenedCloseOrderFailure =
        assertThrows(
            IllegalArgumentException.class,
            () -> new BookAuditEvent(FIXED_INSTANT, BookAuditEventKind.BOOK_OPENED, null, null, 1));
    assertEquals(
        "BOOK_OPENED audit events must not carry accountCode, postingId, or closeOperationOrder.",
        bookOpenedCloseOrderFailure.getMessage());

    IllegalArgumentException accountDeclaredFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BookAuditEvent(
                    FIXED_INSTANT,
                    BookAuditEventKind.ACCOUNT_DECLARED,
                    accountCode,
                    postingId,
                    null));
    assertEquals(
        "ACCOUNT_DECLARED audit events must not carry postingId or closeOperationOrder.",
        accountDeclaredFailure.getMessage());

    IllegalArgumentException postingFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BookAuditEvent(
                    FIXED_INSTANT,
                    BookAuditEventKind.POSTING_COMMITTED,
                    accountCode,
                    postingId,
                    null));
    assertEquals(
        "POSTING_COMMITTED audit events must not carry accountCode or closeOperationOrder.",
        postingFailure.getMessage());

    IllegalArgumentException interimResultSweepFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BookAuditEvent(
                    FIXED_INSTANT, BookAuditEventKind.INTERIM_RESULT_SWEPT, accountCode, null, 1));
    assertEquals(
        "INTERIM_RESULT_SWEPT audit events must not carry accountCode or postingId.",
        interimResultSweepFailure.getMessage());

    IllegalArgumentException interimResultSweepPostingFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BookAuditEvent(
                    FIXED_INSTANT, BookAuditEventKind.INTERIM_RESULT_SWEPT, null, postingId, 1));
    assertEquals(
        "INTERIM_RESULT_SWEPT audit events must not carry accountCode or postingId.",
        interimResultSweepPostingFailure.getMessage());

    IllegalArgumentException fiscalYearCloseFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BookAuditEvent(
                    FIXED_INSTANT, BookAuditEventKind.FISCAL_YEAR_CLOSED, accountCode, null, 1));
    assertEquals(
        "FISCAL_YEAR_CLOSED audit events must not carry accountCode or postingId.",
        fiscalYearCloseFailure.getMessage());

    IllegalArgumentException bookRekeyedFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BookAuditEvent(
                    FIXED_INSTANT, BookAuditEventKind.BOOK_REKEYED, null, postingId, null));
    assertEquals(
        "BOOK_REKEYED audit events must not carry accountCode, postingId, or closeOperationOrder.",
        bookRekeyedFailure.getMessage());

    IllegalArgumentException accountReactivatedFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BookAuditEvent(
                    FIXED_INSTANT, BookAuditEventKind.ACCOUNT_REACTIVATED, accountCode, null, 1));
    assertEquals(
        "ACCOUNT_REACTIVATED audit events must not carry postingId or closeOperationOrder.",
        accountReactivatedFailure.getMessage());

    IllegalArgumentException accountRenamedFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BookAuditEvent(
                    FIXED_INSTANT, BookAuditEventKind.ACCOUNT_RENAMED, accountCode, null, 1));
    assertEquals(
        "ACCOUNT_RENAMED audit events must not carry postingId or closeOperationOrder.",
        accountRenamedFailure.getMessage());

    IllegalArgumentException postingReversedFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BookAuditEvent(
                    FIXED_INSTANT, BookAuditEventKind.POSTING_REVERSED, null, postingId, 1));
    assertEquals(
        "POSTING_REVERSED audit events must not carry accountCode or closeOperationOrder.",
        postingReversedFailure.getMessage());
  }

  @Test
  void constructor_requiresMandatoryFieldsForEachAuditKind() {
    NullPointerException accountCodeFailure =
        assertThrows(
            NullPointerException.class,
            () ->
                new BookAuditEvent(
                    FIXED_INSTANT, BookAuditEventKind.ACCOUNT_REACTIVATED, null, null, null));
    assertEquals("accountCode", accountCodeFailure.getMessage());

    NullPointerException renamedAccountCodeFailure =
        assertThrows(
            NullPointerException.class,
            () ->
                new BookAuditEvent(
                    FIXED_INSTANT, BookAuditEventKind.ACCOUNT_RENAMED, null, null, null));
    assertEquals("accountCode", renamedAccountCodeFailure.getMessage());

    NullPointerException postingIdFailure =
        assertThrows(
            NullPointerException.class,
            () ->
                new BookAuditEvent(
                    FIXED_INSTANT, BookAuditEventKind.POSTING_REVERSED, null, null, null));
    assertEquals("postingId", postingIdFailure.getMessage());

    NullPointerException transferOrderFailure =
        assertThrows(
            NullPointerException.class,
            () ->
                new BookAuditEvent(
                    FIXED_INSTANT, BookAuditEventKind.INTERIM_RESULT_SWEPT, null, null, null));
    assertEquals("closeOperationOrder", transferOrderFailure.getMessage());

    NullPointerException fiscalYearCloseOrderFailure =
        assertThrows(
            NullPointerException.class,
            () ->
                new BookAuditEvent(
                    FIXED_INSTANT, BookAuditEventKind.FISCAL_YEAR_CLOSED, null, null, null));
    assertEquals("closeOperationOrder", fiscalYearCloseOrderFailure.getMessage());
  }

  @Test
  void auditEventKinds_publishStableWireValues() {
    assertEquals("BOOK_OPENED", BookAuditEventKind.BOOK_OPENED.wireValue());
    assertEquals(
        List.of(
            "BOOK_OPENED",
            "ACCOUNT_DECLARED",
            "ACCOUNT_REACTIVATED",
            "ACCOUNT_RENAMED",
            "POSTING_COMMITTED",
            "POSTING_REVERSED",
            "BOOK_REKEYED",
            "BACKUP_CREATED",
            "BACKUP_RESTORED",
            "REKEY_ROLLBACK_RESTORED",
            "REKEY_ROLLBACK_DELETED",
            "BACKUP_CREATED_COMPENSATED",
            "REKEY_ROLLBACK_DELETED_COMPENSATED",
            "INTERIM_RESULT_SWEPT",
            "FISCAL_YEAR_CLOSED"),
        BookAuditEventKind.wireValues());
  }

  private static CommittedPosting committedPosting(
      String postingId, PostingLineageModel postingLineage) {
    return new CommittedPosting(
        new PostingId(postingId),
        new JournalEntry(
            LocalDate.parse("2026-05-12"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    Money.parse("EUR", "1.00")),
                new JournalLine(
                    new AccountCode("2000"),
                    JournalLine.EntrySide.CREDIT,
                    Money.parse("EUR", "1.00")))),
        postingLineage,
        PostingKind.STANDARD,
        dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
        accountingEvidence("idem-1"),
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.AGENT,
                new CommandId("command-1"),
                new IdempotencyKey("idem-1"),
                new CausationId("cause-1"),
                Optional.empty()),
            FIXED_INSTANT,
            SourceChannel.CLI));
  }
}
