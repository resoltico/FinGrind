package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.PreflightAccepted;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.PreflightRejected;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.InMemoryBookFixtureMutations;
import dev.erst.fingrind.executor.InMemoryBookSession;
import dev.erst.fingrind.executor.PostingApplicationService;
import dev.erst.fingrind.jazzer.support.PostingWorkflowInvariantAssertions;

/** Shared invariant owner for in-memory posting-workflow fuzz entrypoints. */
final class PostingWorkflowFuzzAssertions {
  private PostingWorkflowFuzzAssertions() {}

  static void exercisePostingWorkflow(byte[] input) {
    try {
      exerciseParsedPostingWorkflow(CliFuzzFixtures.readPostEntryCommand(input), input);
    } catch (IllegalArgumentException expected) {
      // Malformed JSON and invalid request/domain shapes are expected for many fuzz inputs.
    }
  }

  static void exerciseParsedPostingWorkflow(PostEntryCommand command, byte[] input) {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookAdministrationService administrationService =
          CliFuzzWorkflowFixtures.administrationService(bookSession);
      PostingApplicationService applicationService =
          CliFuzzWorkflowFixtures.postingApplicationService(
              bookSession, bookSession, CliFuzzFixtures.postingIdGenerator(input));

      driveLifecycleToReadyBook(command, bookSession, administrationService, applicationService);

      PreflightEntryResult preflight =
          CliFuzzWorkflowFixtures.preflight(applicationService, command);
      CommitEntryResult committedResult =
          CliFuzzWorkflowFixtures.commit(applicationService, command);
      switch (preflight) {
        case PreflightAccepted accepted -> {
          PostingWorkflowInvariantAssertions.verifyAcceptedPreflight(accepted, command);
          var committed =
              PostingWorkflowInvariantAssertions.requireCommittedAfterAcceptedPreflight(
                  committedResult);
          var postingFact =
              PostingWorkflowInvariantAssertions.requireStoredPosting(
                  CliFuzzWorkflowFixtures.publishedStoredPosting(
                      bookSession, command.requestProvenance().idempotencyKey()));
          PostingWorkflowInvariantAssertions.verifyStoredPosting(postingFact, committed, command);
          PostingWorkflowInvariantAssertions.requireDuplicateRejection(
              CliFuzzWorkflowFixtures.commit(applicationService, command));
        }
        case PreflightRejected preflightRejected -> {
          PostingWorkflowInvariantAssertions.verifyRejectedPreflightAndCommit(
              preflightRejected, committedResult);
          PostingWorkflowInvariantAssertions.assertRejectedStateDidNotPersistPosting(
              CliFuzzWorkflowFixtures.publishedStoredPosting(
                  bookSession, command.requestProvenance().idempotencyKey()));
        }
      }
    }
  }

  private static void driveLifecycleToReadyBook(
      PostEntryCommand command,
      InMemoryBookSession bookSession,
      BookAdministrationService administrationService,
      PostingApplicationService applicationService) {
    PostingWorkflowInvariantAssertions.assertRejected(
        CliFuzzWorkflowFixtures.preflight(applicationService, command),
        PostingRejection.BookNotInitialized.class);
    PostingWorkflowInvariantAssertions.assertRejected(
        CliFuzzWorkflowFixtures.commit(applicationService, command),
        PostingRejection.BookNotInitialized.class);

    CliFuzzWorkflowFixtures.openBook(
        administrationService, CliFuzzFixtures.journalEntry(command).currencyUnit());

    PostingWorkflowInvariantAssertions.assertAccountStateRejected(
        CliFuzzWorkflowFixtures.preflight(applicationService, command),
        PostingRejection.UnknownAccount.class);
    PostingWorkflowInvariantAssertions.assertAccountStateRejected(
        CliFuzzWorkflowFixtures.commit(applicationService, command),
        PostingRejection.UnknownAccount.class);

    var declaredAccounts =
        CliFuzzAccountFixtures.declarePostingAccounts(administrationService, command);
    PostingWorkflowInvariantAssertions.verifyDeclaredAccountListing(
        CliFuzzAccountFixtures.listAccounts(bookSession).size(), declaredAccounts.size());
    DeclaredAccount primaryAccount = declaredAccounts.getFirst();
    InMemoryBookFixtureMutations.deactivateAccount(bookSession, primaryAccount.accountCode());

    PostingWorkflowInvariantAssertions.assertAccountStateRejected(
        CliFuzzWorkflowFixtures.preflight(applicationService, command),
        PostingRejection.InactiveAccount.class);
    PostingWorkflowInvariantAssertions.assertAccountStateRejected(
        CliFuzzWorkflowFixtures.commit(applicationService, command),
        PostingRejection.InactiveAccount.class);

    CliFuzzAccountFixtures.reactivateAccount(administrationService, primaryAccount);
    PostingWorkflowInvariantAssertions.assertAccountReactivationPersisted(
        CliFuzzAccountFixtures.listAccounts(bookSession), primaryAccount);
  }
}
