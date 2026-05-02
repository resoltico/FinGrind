package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.CommitEntryResult;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PostEntryResult.PreflightAccepted;
import dev.erst.fingrind.contract.PostEntryResult.PreflightRejected;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.contract.PreflightEntryResult;
import dev.erst.fingrind.executor.BookAdministrationService;
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
          CliFuzzFixtures.administrationService(bookSession);
      PostingApplicationService applicationService =
          new PostingApplicationService(
              bookSession, CliFuzzFixtures.postingIdGenerator(input), CliFuzzFixtures.fixedClock());

      driveLifecycleToReadyBook(command, bookSession, administrationService, applicationService);

      PreflightEntryResult preflight = applicationService.preflight(command);
      CommitEntryResult committedResult = applicationService.commit(command);
      switch (preflight) {
        case PreflightAccepted accepted -> {
          PostingWorkflowInvariantAssertions.verifyAcceptedPreflight(accepted, command);
          var committed =
              PostingWorkflowInvariantAssertions.requireCommittedAfterAcceptedPreflight(
                  committedResult);
          var postingFact =
              PostingWorkflowInvariantAssertions.requireStoredPosting(
                  bookSession.findExistingPosting(command.requestProvenance().idempotencyKey()));
          PostingWorkflowInvariantAssertions.verifyStoredPosting(postingFact, committed, command);
          PostingWorkflowInvariantAssertions.requireDuplicateRejection(
              applicationService.commit(command));
        }
        case PreflightRejected preflightRejected -> {
          PostingWorkflowInvariantAssertions.verifyRejectedPreflightAndCommit(
              preflightRejected, committedResult);
          PostingWorkflowInvariantAssertions.assertRejectedStateDidNotPersistPosting(
              bookSession.findExistingPosting(command.requestProvenance().idempotencyKey()));
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
        applicationService.preflight(command), PostingRejection.BookNotInitialized.class);
    PostingWorkflowInvariantAssertions.assertRejected(
        applicationService.commit(command), PostingRejection.BookNotInitialized.class);

    CliFuzzFixtures.openBook(administrationService);

    PostingWorkflowInvariantAssertions.assertAccountStateRejected(
        applicationService.preflight(command), PostingRejection.UnknownAccount.class);
    PostingWorkflowInvariantAssertions.assertAccountStateRejected(
        applicationService.commit(command), PostingRejection.UnknownAccount.class);

    var declaredAccounts = CliFuzzFixtures.declarePostingAccounts(administrationService, command);
    PostingWorkflowInvariantAssertions.verifyDeclaredAccountListing(
        CliFuzzFixtures.listAccounts(bookSession).size(), declaredAccounts.size());
    DeclaredAccount primaryAccount = declaredAccounts.getFirst();
    bookSession.deactivateAccount(primaryAccount.accountCode());

    PostingWorkflowInvariantAssertions.assertAccountStateRejected(
        applicationService.preflight(command), PostingRejection.InactiveAccount.class);
    PostingWorkflowInvariantAssertions.assertAccountStateRejected(
        applicationService.commit(command), PostingRejection.InactiveAccount.class);

    CliFuzzFixtures.reactivateAccount(administrationService, primaryAccount);
    PostingWorkflowInvariantAssertions.assertAccountReactivationPersisted(
        CliFuzzFixtures.listAccounts(bookSession), primaryAccount);
  }
}
