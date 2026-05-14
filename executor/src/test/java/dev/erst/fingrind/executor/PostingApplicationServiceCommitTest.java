package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.initializedLifecycleInspection;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.FIXED_CLOCK;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.applicationService;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.command;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.commitRejected;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.declareDefaultAccounts;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.existingPosting;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.initializedBook;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.mappedOutcomeBookSession;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.reversalJournalEntry;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.reversalReference;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookStore;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests covering commit behavior in {@link PostingApplicationService}. */
class PostingApplicationServiceCommitTest {
  @Test
  void commit_returnsCommittedWhenRequestIsAdmissible() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult result = applicationService.commit(command("idem-1"));

      assertEquals(
          new PostEntryResult.Committed(
              new PostingId("posting-new"),
              new IdempotencyKey("idem-1"),
              LocalDate.parse("2026-04-07"),
              FIXED_CLOCK.instant()),
          result);
    }
  }

  @Test
  void commit_rejectsBookNotInitializedBeforeGeneratingPostingId() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      PostingApplicationService applicationService =
          new PostingApplicationService(
              bookSession,
              () -> {
                throw new AssertionError("postingIdGenerator should not be called");
              },
              FIXED_CLOCK);

      PostEntryResult result = applicationService.commit(command("idem-1"));

      assertEquals(
          commitRejected(new IdempotencyKey("idem-1"), new PostingRejection.BookNotInitialized()),
          result);
    }
  }

  @Test
  void commit_returnsCommittedForValidReversal() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      bookSession.commit(existingPosting("posting-1", "idem-original"));
      PostingApplicationService applicationService = applicationService(bookSession);

      PostEntryResult result =
          applicationService.commit(
              command(
                  "idem-1",
                  reversalReference("posting-1"),
                  Optional.of(new ReversalReason("full reversal")),
                  reversalJournalEntry()));

      assertEquals(
          new PostEntryResult.Committed(
              new PostingId("posting-new"),
              new IdempotencyKey("idem-1"),
              LocalDate.parse("2026-04-07"),
              FIXED_CLOCK.instant()),
          result);
    }
  }

  @Test
  void commit_mapsOrdinaryBookSessionOutcomes() {
    BookStore bookSession = mappedOutcomeBookSession();
    PostingApplicationService applicationService = applicationService(bookSession);

    assertEquals(
        commitRejected(
            new IdempotencyKey("idem-book-not-initialized"),
            new PostingRejection.BookNotInitialized()),
        applicationService.commit(command("idem-book-not-initialized")));
    assertEquals(
        commitRejected(
            new IdempotencyKey("idem-unknown-account"),
            new PostingRejection.AccountStateViolations(
                List.of(new PostingRejection.UnknownAccount(new AccountCode("1000"))))),
        applicationService.commit(command("idem-unknown-account")));
    assertEquals(
        commitRejected(
            new IdempotencyKey("idem-inactive-account"),
            new PostingRejection.AccountStateViolations(
                List.of(new PostingRejection.InactiveAccount(new AccountCode("1000"))))),
        applicationService.commit(command("idem-inactive-account")));
    assertEquals(
        commitRejected(
            new IdempotencyKey("idem-duplicate"), new PostingRejection.DuplicateIdempotencyKey()),
        applicationService.commit(command("idem-duplicate")));
    assertEquals(
        commitRejected(
            new IdempotencyKey("idem-reversal-duplicate"),
            new PostingRejection.ReversalAlreadyExists(new PostingId("posting-1"))),
        applicationService.commit(
            command(
                "idem-reversal-duplicate",
                reversalReference("posting-1"),
                Optional.of(new ReversalReason("full reversal")),
                reversalJournalEntry())));
  }

  @Test
  void commit_propagatesUnexpectedBookSessionFailure() {
    BookStore bookSession =
        new PostingApplicationServiceTestSupport.DelegatingPostingBookSession() {
          @Override
          public BookLifecycleInspection inspectBook() {
            return initializedLifecycleInspection(1001, 1, 1, FIXED_CLOCK.instant());
          }

          @Override
          public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
            return Optional.of(
                registeredAccount(
                    accountCode,
                    new AccountName("Synthetic"),
                    "1000".equals(accountCode.value()) ? AccountType.ASSET : AccountType.REVENUE,
                    "1000".equals(accountCode.value()) ? NormalBalance.DEBIT : NormalBalance.CREDIT,
                    true,
                    FIXED_CLOCK.instant()));
          }

          @Override
          public PostingCommitResult commit(
              dev.erst.fingrind.executor.spi.PostingDraft postingDraft,
              dev.erst.fingrind.executor.spi.PostingIdGenerator postingIdGenerator) {
            throw new IllegalStateException("boom");
          }
        };
    PostingApplicationService applicationService = applicationService(bookSession);

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class, () -> applicationService.commit(command("idem-1")));

    assertEquals("boom", thrown.getMessage());
  }
}
