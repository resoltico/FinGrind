package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for bookkeeping posting-model abstractions. */
class PostingModelTest {
  @Test
  void postingLineageModel_exposesDirectAndReversalMetadata() {
    ReversalReference reversalReference = new ReversalReference(postingId("posting-1"));
    ReversalReason reversalReason = new ReversalReason("refund correction");

    PostingLineageModel direct = PostingLineageModel.direct();
    PostingLineageModel reversal = PostingLineageModel.reversal(reversalReference, reversalReason);

    assertFalse(direct.isReversal());
    assertTrue(direct.reversalReference().isEmpty());
    assertTrue(direct.reversalReason().isEmpty());
    assertTrue(reversal.isReversal());
    assertEquals(Optional.of(reversalReference), reversal.reversalReference());
    assertEquals(Optional.of(reversalReason), reversal.reversalReason());
  }

  @Test
  void postingRequestModel_defaultAccessorsDelegateToLineage() {
    ReversalReference reversalReference = new ReversalReference(postingId("posting-2"));
    ReversalReason reversalReason = new ReversalReason("customer reversal");
    RequestProvenance requestProvenance = requestProvenance("idem-1");
    PostingRequestModel request =
        new PostingCommand(
            PostingKind.STANDARD,
            dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
            testJournalEntry(),
            PostingLineageModel.reversal(reversalReference, reversalReason),
            accountingEvidence("idem-1"),
            requestProvenance,
            dev.erst.fingrind.core.SourceChannel.CLI);

    assertEquals(Optional.of(reversalReference), request.reversalReference());
    assertEquals(Optional.of(reversalReason), request.reversalReason());
    assertEquals(requestProvenance, request.requestProvenance());
  }

  @Test
  void postingRequestModel_defaultCallerAuthoredEntryIsEmpty() {
    PostingRequestModel request =
        new PostingCommand(
            PostingKind.STANDARD,
            dev.erst.fingrind.core.PostingOriginKind.DIRECT_JOURNAL,
            testJournalEntry(),
            PostingLineageModel.direct(),
            accountingEvidence("idem-default-entry"),
            requestProvenance("idem-default-entry"),
            dev.erst.fingrind.core.SourceChannel.CLI);

    assertEquals(Optional.empty(), defaultCallerAuthoredEntry(request));
  }

  @Test
  void postingCommand_rejectsCallerAuthoredEntryWhenOriginKindDrifts() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PostingCommand(
                    PostingKind.STANDARD,
                    dev.erst.fingrind.core.PostingOriginKind.DIRECT_JOURNAL,
                    testJournalEntry(),
                    PostingLineageModel.direct(),
                    accountingEvidence("idem-2"),
                    requestProvenance("idem-2"),
                    dev.erst.fingrind.core.SourceChannel.CLI,
                    saleEntry()));

    assertEquals(
        "originatingEntry postingOriginKind must match the posting command.",
        exception.getMessage());
  }

  @Test
  void postingCommand_rejectsCallerAuthoredEntryWhenPostingKindDrifts() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PostingCommand(
                    PostingKind.OPENING_BALANCE,
                    dev.erst.fingrind.core.PostingOriginKind.SALE,
                    testJournalEntry(),
                    PostingLineageModel.direct(),
                    accountingEvidence("idem-kind-drift"),
                    requestProvenance("idem-kind-drift"),
                    dev.erst.fingrind.core.SourceChannel.CLI,
                    saleEntry()));

    assertEquals(
        "originatingEntry postingKind must match the posting command.", exception.getMessage());
  }

  @Test
  void committedPosting_rejectsCallerAuthoredEntryWhenLineageDrifts() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CommittedPosting(
                    postingId("posting-3"),
                    testJournalEntry(),
                    PostingLineageModel.reversal(
                        new ReversalReference(postingId("posting-2")),
                        new ReversalReason("refund correction")),
                    PostingKind.STANDARD,
                    dev.erst.fingrind.core.PostingOriginKind.SALE,
                    accountingEvidence("idem-3"),
                    committedProvenance("idem-3"),
                    saleEntry()));

    assertEquals(
        "originatingEntry postingLineage must match the committed posting lineage.",
        exception.getMessage());
  }

  @Test
  void postingDraft_rejectsCallerAuthoredReversalWhenLineageKindDrifts() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new dev.erst.fingrind.executor.spi.PostingDraft(
                    testJournalEntry(),
                    PostingLineageModel.direct(),
                    PostingKind.STANDARD,
                    dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
                    accountingEvidence("idem-reversal-kind"),
                    new dev.erst.fingrind.core.RequestFingerprint(
                        dev.erst.fingrind.core.RequestFingerprint.CURRENT_VERSION, "0".repeat(64)),
                    committedProvenance("idem-reversal-kind"),
                    reversalEntry()));

    assertEquals(
        "originatingEntry postingLineage must match the posting draft lineage.",
        exception.getMessage());
  }

  @Test
  void committedPosting_rejectsCallerAuthoredReversalWhenReferenceDrifts() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CommittedPosting(
                    postingId("posting-4"),
                    testJournalEntry(),
                    PostingLineageModel.reversal(
                        new ReversalReference(postingId("posting-other")),
                        new ReversalReason("refund correction")),
                    PostingKind.STANDARD,
                    dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
                    accountingEvidence("idem-4"),
                    committedProvenance("idem-4"),
                    reversalEntry()));

    assertEquals(
        "originatingEntry postingLineage must match the committed posting lineage.",
        exception.getMessage());
  }

  @Test
  void committedPosting_rejectsCallerAuthoredReversalWhenReasonDrifts() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CommittedPosting(
                    postingId("posting-5"),
                    testJournalEntry(),
                    PostingLineageModel.reversal(
                        new ReversalReference(postingId("posting-1")),
                        new ReversalReason("different reason")),
                    PostingKind.STANDARD,
                    dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
                    accountingEvidence("idem-5"),
                    committedProvenance("idem-5"),
                    reversalEntry()));

    assertEquals(
        "originatingEntry postingLineage must match the committed posting lineage.",
        exception.getMessage());
  }

  @Test
  void committedPosting_acceptsMatchingReversalCallerAuthoredEntry() {
    BookkeepingEntry.Reversal reversalEntry = reversalEntry();

    CommittedPosting posting =
        new CommittedPosting(
            postingId("posting-6"),
            reversalEntry.journalEntry(),
            PostingLineageModel.reversal(
                reversalEntry.reversal().reference(), reversalEntry.reversal().reason()),
            reversalEntry.postingKind(),
            reversalEntry.postingOriginKind(),
            accountingEvidence("idem-6"),
            committedProvenance("idem-6"),
            reversalEntry);

    assertEquals(Optional.of(reversalEntry), posting.callerAuthoredEntry());
  }

  private static dev.erst.fingrind.core.PostingId postingId(String value) {
    return new dev.erst.fingrind.core.PostingId(value);
  }

  @SuppressWarnings("unchecked")
  private static Optional<BookkeepingEntry> defaultCallerAuthoredEntry(
      PostingRequestModel request) {
    try {
      MethodHandle defaultMethod =
          MethodHandles.privateLookupIn(PostingRequestModel.class, MethodHandles.lookup())
              .findSpecial(
                  PostingRequestModel.class,
                  "callerAuthoredEntry",
                  MethodType.methodType(Optional.class),
                  PostingRequestModel.class);
      return (Optional<BookkeepingEntry>) defaultMethod.bindTo(request).invokeWithArguments();
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError(
          "Failed to invoke PostingRequestModel default caller-authored-entry accessor.",
          throwable);
    }
  }

  private static BookkeepingEntry.Sale saleEntry() {
    return new BookkeepingEntry.Sale(
        LocalDate.parse("2026-05-05"),
        new AccountCode("1000"),
        new AccountCode("2000"),
        dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(Money.parse("EUR", "10.00")),
        null,
        null,
        null);
  }

  private static BookkeepingEntry.Reversal reversalEntry() {
    return new BookkeepingEntry.Reversal(
        testJournalEntry(),
        new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
            new ReversalReference(postingId("posting-1")), new ReversalReason("refund correction")),
        null);
  }

  private static JournalEntry testJournalEntry() {
    return new JournalEntry(
        LocalDate.parse("2026-05-05"),
        List.of(
            new JournalLine(
                new dev.erst.fingrind.core.AccountCode("1000"),
                JournalLine.EntrySide.DEBIT,
                Money.parse("EUR", "10.00")),
            new JournalLine(
                new dev.erst.fingrind.core.AccountCode("2000"),
                JournalLine.EntrySide.CREDIT,
                Money.parse("EUR", "10.00"))));
  }

  private static RequestProvenance requestProvenance(String idempotencyKey) {
    return new RequestProvenance(
        new ActorId("actor-1"),
        ActorType.AGENT,
        new CommandId("command-1"),
        new IdempotencyKey(idempotencyKey),
        new CausationId("cause-1"),
        Optional.of(new CorrelationId("corr-1")));
  }

  private static dev.erst.fingrind.core.CommittedProvenance committedProvenance(
      String idempotencyKey) {
    return new dev.erst.fingrind.core.CommittedProvenance(
        requestProvenance(idempotencyKey),
        java.time.Instant.parse("2026-05-05T10:15:30Z"),
        dev.erst.fingrind.core.SourceChannel.CLI);
  }
}
