package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.generatedEvidence;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.applicationService;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.command;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.declareDefaultAccounts;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.existingPosting;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.initializedBook;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.mismatchedReversalJournalEntry;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.preflightRejected;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.requestProvenance;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.reversalJournalEntry;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.reversalReference;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.JournalRecipeKind;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests covering preflight behavior in {@link PostingApplicationService}. */
class PostingApplicationServicePreflightTest {
  @Test
  void preflight_rejectsBookNotInitialized() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult result = applicationService.preflight(command("idem-1"));

      assertEquals(
          preflightRejected(
              new IdempotencyKey("idem-1"), new PostingRejection.BookNotInitialized()),
          result);
    }
  }

  @Test
  void preflight_rejectsUnknownAndInactiveAccountsBeforeOtherChecks() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult unknownAccountResult = applicationService.preflight(command("idem-1"));
      assertEquals(
          preflightRejected(
              new IdempotencyKey("idem-1"),
              new PostingRejection.AccountStateViolations(
                  List.of(
                      new PostingRejection.UnknownAccount(new AccountCode("1000")),
                      new PostingRejection.UnknownAccount(new AccountCode("2000"))))),
          unknownAccountResult);

      declareDefaultAccounts(bookSession);
      bookSession.deactivateAccount(new AccountCode("1000"));

      PostEntryResult inactiveAccountResult = applicationService.preflight(command("idem-2"));
      assertEquals(
          preflightRejected(
              new IdempotencyKey("idem-2"),
              new PostingRejection.AccountStateViolations(
                  List.of(new PostingRejection.InactiveAccount(new AccountCode("1000"))))),
          inactiveAccountResult);
    }
  }

  @Test
  void preflight_returnsAcceptedWhenRequestIsAdmissible() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult result = applicationService.preflight(command("idem-1"));

      assertEquals(
          new PostEntryResult.PreflightAccepted(
              new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
          result);
    }
  }

  @Test
  void preflight_rejectsTypedEntryWhenAccountsAndEvidenceContradictEntryKind() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      PostingApplicationService applicationService = applicationService(bookSession);
      PostEntryCommand command =
          new PostEntryCommand(
              BookkeepingEntry.cashRevenue(
                  LocalDate.parse("2026-04-07"),
                  new AccountCode("2000"),
                  new AccountCode("1000"),
                  MonetaryAmount.of(Money.parse("EUR", "10.00"))),
              generatedEvidence("idem-semantics", "invoice"),
              requestProvenance("idem-semantics"),
              SourceChannel.CLI);

      PostEntryResult result = applicationService.preflight(command);

      assertEquals(
          preflightRejected(
              new IdempotencyKey("idem-semantics"),
              new PostingRejection.EntrySemanticsViolations(
                  List.of(
                      PostingRejection.accountTypeMismatch(
                          JournalRecipeKind.CASH_REVENUE.wireValue(),
                          "cashAccountCode",
                          new AccountCode("2000"),
                          dev.erst.fingrind.core.AccountType.ASSET,
                          dev.erst.fingrind.core.AccountType.REVENUE),
                      PostingRejection.accountTypeMismatch(
                          JournalRecipeKind.CASH_REVENUE.wireValue(),
                          "revenueAccountCode",
                          new AccountCode("1000"),
                          dev.erst.fingrind.core.AccountType.REVENUE,
                          dev.erst.fingrind.core.AccountType.ASSET),
                      PostingRejection.sourceDocumentTypeNotAccepted(
                          JournalRecipeKind.CASH_REVENUE.wireValue(),
                          new dev.erst.fingrind.core.SourceDocumentType("invoice"),
                          List.of("cash-receipt", "bank-deposit", "card-settlement"))))),
          result);
    }
  }

  @Test
  void preflight_rejectsDuplicateIdempotencyKey() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      bookSession.commit(existingPosting("posting-existing", "idem-1"));
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult result = applicationService.preflight(command("idem-1"));

      assertEquals(
          preflightRejected(
              new IdempotencyKey("idem-1"), new PostingRejection.DuplicateIdempotencyKey()),
          result);
    }
  }

  @Test
  void preflight_rejectsMissingReversalTarget() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult result =
          applicationService.preflight(
              command(
                  "idem-1",
                  Optional.of(new ReversalReference(new PostingId("posting-missing"))),
                  Optional.of(new ReversalReason("operator reversal"))));

      assertEquals(
          preflightRejected(
              new IdempotencyKey("idem-1"),
              new PostingRejection.ReversalTargetNotFound(new PostingId("posting-missing"))),
          result);
    }
  }

  @Test
  void preflight_acceptsReversalWhenTargetExistsAndReasonIsPresent() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      bookSession.commit(existingPosting("posting-1", "idem-existing"));
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult result =
          applicationService.preflight(
              command(
                  "idem-1",
                  reversalReference("posting-1"),
                  Optional.of(new ReversalReason("full reversal")),
                  reversalJournalEntry()));

      assertEquals(
          new PostEntryResult.PreflightAccepted(
              new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
          result);
    }
  }

  @Test
  void preflight_rejectsReversalThatDoesNotNegateTarget() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      bookSession.commit(existingPosting("posting-1", "idem-existing"));
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult result =
          applicationService.preflight(
              command(
                  "idem-1",
                  reversalReference("posting-1"),
                  Optional.of(new ReversalReason("full reversal")),
                  mismatchedReversalJournalEntry()));

      assertEquals(
          preflightRejected(
              new IdempotencyKey("idem-1"),
              new PostingRejection.ReversalDoesNotNegateTarget(new PostingId("posting-1"))),
          result);
    }
  }

  @Test
  void preflight_rejectsReversalWhenTargetAlreadyHasReversal() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      bookSession.commit(existingPosting("posting-1", "idem-original"));
      PostingApplicationService applicationService = applicationService(bookSession);
      applicationService.commit(
          command(
              "idem-existing-reversal",
              reversalReference("posting-1"),
              Optional.of(new ReversalReason("full reversal")),
              reversalJournalEntry()));

      PostEntryResult result =
          applicationService.preflight(
              command(
                  "idem-1",
                  reversalReference("posting-1"),
                  Optional.of(new ReversalReason("full reversal")),
                  reversalJournalEntry()));

      assertEquals(
          preflightRejected(
              new IdempotencyKey("idem-1"),
              new PostingRejection.ReversalAlreadyExists(new PostingId("posting-1"))),
          result);
    }
  }
}
