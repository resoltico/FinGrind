package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.TEST_AUTHORIZER;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.FIXED_CLOCK;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.applicationService;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.command;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.conflictingPosting;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.declareDefaultAccounts;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.initializedBook;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.core.IdempotencyKey;
import org.junit.jupiter.api.Test;

/** Verifies read-safe idempotency decisions before a posting mutation is attempted. */
class PostingPreflightServiceTest {
  @Test
  void preflight_projectsExactReplaysWithoutReservingAnotherPosting() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      PostEntryResult.Committed committed =
          assertInstanceOf(
              PostEntryResult.Committed.class,
              applicationService(bookSession).commit(command("idem-existing"), TEST_AUTHORIZER));
      PostingPreflightService service = new PostingPreflightService(bookSession, FIXED_CLOCK);

      PostEntryResult.PreflightAccepted result =
          assertInstanceOf(
              PostEntryResult.PreflightAccepted.class, service.preflight(command("idem-existing")));

      assertEquals(new IdempotencyKey("idem-existing"), result.idempotencyKey());
      assertEquals(committed.effectiveDate(), result.effectiveDate());
      assertEquals(committed.resolvedJournal(), result.resolvedJournal());
    }
  }

  @Test
  void preflight_rejectsConflictingIdempotencyBeforeACommitCanChangeTheBook() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      bookSession.commit(conflictingPosting("posting-conflicting", "idem-conflicting"));
      PostingPreflightService service = new PostingPreflightService(bookSession, FIXED_CLOCK);

      PostEntryResult.PreflightRejected result =
          assertInstanceOf(
              PostEntryResult.PreflightRejected.class,
              service.preflight(command("idem-conflicting")));

      assertEquals(new IdempotencyKey("idem-conflicting"), result.requestIdempotencyKey());
      assertInstanceOf(PostingRejection.IdempotencyKeyConflict.class, result.rejection());
    }
  }
}
