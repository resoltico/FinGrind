package dev.erst.fingrind.cli;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import dev.erst.fingrind.application.BookAdministrationService;
import dev.erst.fingrind.application.DeclaredAccount;
import dev.erst.fingrind.application.PostEntryCommand;
import dev.erst.fingrind.application.PostEntryResult;
import dev.erst.fingrind.application.PostEntryResult.Committed;
import dev.erst.fingrind.application.PostEntryResult.PreflightAccepted;
import dev.erst.fingrind.application.PostEntryResult.Rejected;
import dev.erst.fingrind.application.InMemoryBookSession;
import dev.erst.fingrind.application.PostingApplicationService;
import dev.erst.fingrind.application.PostingFact;
import dev.erst.fingrind.application.PostingRejection;
import java.util.Optional;

/** Fuzzes posting workflow invariants above the book-session seam using an in-memory book. */
public class PostingWorkflowFuzzTest {
  @FuzzTest
  void exercisePostingWorkflow(FuzzedDataProvider data) {
    byte[] input = data.consumeRemainingAsBytes();
    try {
      PostEntryCommand command = CliFuzzSupport.readPostEntryCommand(input);
      InMemoryBookSession bookSession = new InMemoryBookSession();
      BookAdministrationService administrationService =
          CliFuzzSupport.administrationService(bookSession);
      PostingApplicationService applicationService =
          new PostingApplicationService(
              bookSession,
              CliFuzzSupport.postingIdGenerator(input),
              CliFuzzSupport.fixedClock());

      assertRejected(
          applicationService.preflight(command), PostingRejection.BookNotInitialized.class);
      assertRejected(applicationService.commit(command), PostingRejection.BookNotInitialized.class);

      CliFuzzSupport.openBook(administrationService);

      assertRejected(
          applicationService.preflight(command), PostingRejection.UnknownAccount.class);
      assertRejected(applicationService.commit(command), PostingRejection.UnknownAccount.class);

      var declaredAccounts = CliFuzzSupport.declarePostingAccounts(administrationService, command);
      if (CliFuzzSupport.listAccounts(administrationService).size() != declaredAccounts.size()) {
        throw new IllegalStateException("Declared-account listing drifted from setup declarations.");
      }
      DeclaredAccount primaryAccount = declaredAccounts.getFirst();
      bookSession.deactivateAccount(primaryAccount.accountCode());

      assertRejected(
          applicationService.preflight(command), PostingRejection.InactiveAccount.class);
      assertRejected(applicationService.commit(command), PostingRejection.InactiveAccount.class);

      CliFuzzSupport.reactivateAccount(administrationService, primaryAccount);
      if (!CliFuzzSupport
          .listAccounts(administrationService)
          .stream()
          .anyMatch(account -> account.accountCode().equals(primaryAccount.accountCode()) && account.active())) {
        throw new IllegalStateException("Account reactivation did not persist in the registry.");
      }

      PostEntryResult preflight = applicationService.preflight(command);
      PostEntryResult committedResult = applicationService.commit(command);
      if (preflight instanceof PreflightAccepted accepted) {
        if (!accepted.idempotencyKey().equals(command.requestProvenance().idempotencyKey())) {
          throw new IllegalStateException("Preflight changed the idempotency key.");
        }
        if (!accepted.effectiveDate().equals(command.journalEntry().effectiveDate())) {
          throw new IllegalStateException("Preflight changed the effective date.");
        }
        if (!(committedResult instanceof Committed committed)) {
          throw new IllegalStateException("Accepted preflight should commit on a fresh valid book.");
        }

        Optional<PostingFact> storedPosting =
            bookSession.findByIdempotency(command.requestProvenance().idempotencyKey());
        if (storedPosting.isEmpty()) {
          throw new IllegalStateException("Committed posting fact was not persisted.");
        }
        PostingFact postingFact = storedPosting.orElseThrow();
        if (!postingFact.postingId().equals(committed.postingId())) {
          throw new IllegalStateException("Stored posting id differs from the commit result.");
        }
        if (!postingFact.journalEntry().equals(command.journalEntry())) {
          throw new IllegalStateException("Stored journal entry differs from the parsed command.");
        }
        if (!postingFact.reversalReference().equals(command.reversalReference())) {
          throw new IllegalStateException("Stored reversal differs from the parsed command.");
        }
        if (!postingFact.provenance().requestProvenance().equals(command.requestProvenance())) {
          throw new IllegalStateException(
              "Stored request provenance differs from the parsed command.");
        }
        if (!postingFact.provenance().recordedAt().equals(CliFuzzSupport.fixedClock().instant())) {
          throw new IllegalStateException(
              "Stored recorded-at differs from the deterministic clock.");
        }
        if (postingFact.provenance().sourceChannel() != command.sourceChannel()) {
          throw new IllegalStateException("Stored source channel differs from the parsed command.");
        }

        PostEntryResult duplicateResult = applicationService.commit(command);
        if (!(duplicateResult instanceof Rejected rejected)) {
          throw new IllegalStateException("Duplicate commit should be rejected.");
        }
        if (!(rejected.rejection() instanceof PostingRejection.DuplicateIdempotencyKey)) {
          throw new IllegalStateException("Duplicate commit returned the wrong rejection code.");
        }
      } else if (preflight instanceof Rejected preflightRejected) {
        if (!(committedResult instanceof Rejected commitRejected)) {
          throw new IllegalStateException("Rejected preflight should remain rejected on commit.");
        }
        if (!commitRejected.rejection().equals(preflightRejected.rejection())) {
          throw new IllegalStateException("Commit changed the deterministic rejection.");
        }
        if (bookSession.findByIdempotency(command.requestProvenance().idempotencyKey()).isPresent()) {
          throw new IllegalStateException("Rejected command must not persist a posting fact.");
        }
      } else {
        throw new IllegalStateException("Unexpected preflight result type.");
      }
    } catch (IllegalArgumentException expected) {
      // Malformed JSON and invalid request/domain shapes are expected for many fuzz inputs.
    }
  }

  private static void assertRejected(
      PostEntryResult result, Class<? extends PostingRejection> rejectionType) {
    if (!(result instanceof Rejected rejected)) {
      throw new IllegalStateException("Expected deterministic rejection during lifecycle setup.");
    }
    if (!rejectionType.isInstance(rejected.rejection())) {
      throw new IllegalStateException(
          "Lifecycle setup returned the wrong rejection type: " + rejected.rejection());
    }
  }
}
