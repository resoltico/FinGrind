package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.cli.CliFuzzAccountFixtures;
import dev.erst.fingrind.cli.CliFuzzFixtures;
import dev.erst.fingrind.cli.CliFuzzWorkflowFixtures;
import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.CommitRejected;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.Committed;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.PreflightAccepted;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.PreflightRejected;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.InMemoryBookFixtureMutations;
import dev.erst.fingrind.executor.InMemoryBookSession;
import dev.erst.fingrind.executor.PostingApplicationService;
import dev.erst.fingrind.jazzer.support.JazzerHarness;
import dev.erst.fingrind.jazzer.support.PostingWorkflowInvariantAssertions;
import java.util.List;

/** Replays posting-command workflows against the in-memory bookkeeping harness. */
final class JazzerPostingWorkflowReplay {
  private JazzerPostingWorkflowReplay() {}

  static ReplayOutcome replay(byte[] input) {
    return replay(
        input,
        CliFuzzFixtures::readPostEntryCommand,
        JazzerPostingWorkflowReplay::exerciseWorkflow);
  }

  static ReplayOutcome replay(
      byte[] input, PostEntryCommandParser parser, PostingWorkflowExercise workflowExercise) {
    PostEntryCommand command = null;
    PostingWorkflowReplayState state = new PostingWorkflowReplayState();
    try {
      command = parser.parse(input);
      workflowExercise.exercise(command, input, state);
      return new ReplayOutcome.Success(
          JazzerHarness.postingWorkflow().key(), state.details(command));
    } catch (IllegalArgumentException expected) {
      return new ReplayOutcome.ExpectedInvalid(
          JazzerHarness.postingWorkflow().key(),
          expected.getClass().getSimpleName(),
          JazzerReplayOutcomeSupport.normalizedMessage(expected),
          JazzerReplayDetailsMapper.unparsedPostingWorkflowDetails());
    } catch (RuntimeException unexpected) {
      return JazzerReplayOutcomeSupport.unexpectedFailure(
          JazzerHarness.postingWorkflow(),
          unexpected,
          command == null
              ? JazzerReplayDetailsMapper.unparsedPostingWorkflowDetails()
              : state.details(command));
    }
  }

  private static void exerciseWorkflow(
      PostEntryCommand command, byte[] input, PostingWorkflowReplayState state) {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookAdministrationService administrationService =
          CliFuzzWorkflowFixtures.administrationService(bookSession);
      PostingApplicationService applicationService =
          CliFuzzWorkflowFixtures.postingApplicationService(
              bookSession, bookSession, CliFuzzFixtures.postingIdGenerator(input));

      state.uninitializedPreflightStatus =
          JazzerReplayOutcomeSupport.rejectionStatus(
              JazzerReplayOutcomeSupport.requiredPreflightRejected(
                      CliFuzzWorkflowFixtures.preflight(applicationService, command))
                  .rejection());
      state.uninitializedCommitStatus =
          JazzerReplayOutcomeSupport.rejectionStatus(
              JazzerReplayOutcomeSupport.requiredCommitRejected(
                      CliFuzzWorkflowFixtures.commit(applicationService, command))
                  .rejection());

      CliFuzzWorkflowFixtures.openBook(
          administrationService, CliFuzzFixtures.journalEntry(command).currencyUnit());

      state.undeclaredPreflightStatus =
          JazzerReplayOutcomeSupport.rejectionStatus(
              JazzerReplayOutcomeSupport.requiredPreflightRejected(
                      CliFuzzWorkflowFixtures.preflight(applicationService, command))
                  .rejection());
      state.undeclaredCommitStatus =
          JazzerReplayOutcomeSupport.rejectionStatus(
              JazzerReplayOutcomeSupport.requiredCommitRejected(
                      CliFuzzWorkflowFixtures.commit(applicationService, command))
                  .rejection());

      List<DeclaredAccount> declaredAccounts =
          CliFuzzAccountFixtures.declarePostingAccounts(administrationService, command);
      List<DeclaredAccount> listedAccounts = CliFuzzAccountFixtures.listAccounts(bookSession);
      PostingWorkflowInvariantAssertions.verifyDeclaredAccountListing(
          listedAccounts, declaredAccounts);
      DeclaredAccount primaryAccount = declaredAccounts.getFirst();
      InMemoryBookFixtureMutations.deactivateAccount(bookSession, primaryAccount.accountCode());
      state.inactivePreflightStatus =
          JazzerReplayOutcomeSupport.rejectionStatus(
              JazzerReplayOutcomeSupport.requiredPreflightRejected(
                      CliFuzzWorkflowFixtures.preflight(applicationService, command))
                  .rejection());
      state.inactiveCommitStatus =
          JazzerReplayOutcomeSupport.rejectionStatus(
              JazzerReplayOutcomeSupport.requiredCommitRejected(
                      CliFuzzWorkflowFixtures.commit(applicationService, command))
                  .rejection());

      CliFuzzAccountFixtures.reactivateAccount(administrationService, primaryAccount);
      PostingWorkflowInvariantAssertions.assertAccountReactivationPersisted(
          CliFuzzAccountFixtures.listAccounts(bookSession), primaryAccount);

      PreflightEntryResult preflight =
          CliFuzzWorkflowFixtures.preflight(applicationService, command);
      CommitEntryResult committedResult =
          CliFuzzWorkflowFixtures.commit(applicationService, command);
      switch (preflight) {
        case PreflightAccepted accepted -> {
          PostingWorkflowInvariantAssertions.verifyAcceptedPreflight(accepted, command);
          state.finalPreflightStatus = PostingLifecycleStatus.PREFLIGHT_ACCEPTED;
          Committed committed =
              PostingWorkflowInvariantAssertions.requireCommittedAfterAcceptedPreflight(
                  committedResult);
          state.finalCommitStatus = PostingLifecycleStatus.COMMITTED;

          PostingFact postingFact =
              PostingWorkflowInvariantAssertions.requireStoredPosting(
                  CliFuzzWorkflowFixtures.publishedStoredPosting(
                      bookSession, command.requestProvenance().idempotencyKey()));
          PostingWorkflowInvariantAssertions.verifyStoredPosting(postingFact, committed, command);
          state.storedFactPresent = true;
          state.duplicateStatus =
              JazzerReplayOutcomeSupport.rejectionStatus(
                  PostingWorkflowInvariantAssertions.requireDuplicateRejection(
                          CliFuzzWorkflowFixtures.commit(applicationService, command))
                      .rejection());
        }
        case PreflightRejected preflightRejected -> {
          state.finalPreflightStatus =
              JazzerReplayOutcomeSupport.rejectionStatus(preflightRejected.rejection());
          CommitRejected commitRejected =
              PostingWorkflowInvariantAssertions.verifyRejectedPreflightAndCommit(
                  preflightRejected, committedResult);
          state.finalCommitStatus =
              JazzerReplayOutcomeSupport.rejectionStatus(commitRejected.rejection());
          PostingWorkflowInvariantAssertions.assertRejectedStateDidNotPersistPosting(
              CliFuzzWorkflowFixtures.publishedStoredPosting(
                  bookSession, command.requestProvenance().idempotencyKey()));
        }
      }
    }
  }

  /** Parses one posting-workflow replay input into the production posting command model. */
  @FunctionalInterface
  interface PostEntryCommandParser {
    /** Parses one raw replay payload into a posting command. */
    PostEntryCommand parse(byte[] input);
  }

  /** Exercises one parsed posting command against the in-memory replay workflow. */
  @FunctionalInterface
  interface PostingWorkflowExercise {
    /** Applies one parsed command to the replay workflow and records the resulting state. */
    void exercise(PostEntryCommand command, byte[] input, PostingWorkflowReplayState state);
  }

  /** Collects lifecycle checkpoints and the final outcome for one replayed posting workflow. */
  static final class PostingWorkflowReplayState {
    private PostingLifecycleStatus uninitializedPreflightStatus = PostingLifecycleStatus.NOT_RUN;
    private PostingLifecycleStatus uninitializedCommitStatus = PostingLifecycleStatus.NOT_RUN;
    private PostingLifecycleStatus undeclaredPreflightStatus = PostingLifecycleStatus.NOT_RUN;
    private PostingLifecycleStatus undeclaredCommitStatus = PostingLifecycleStatus.NOT_RUN;
    private PostingLifecycleStatus inactivePreflightStatus = PostingLifecycleStatus.NOT_RUN;
    private PostingLifecycleStatus inactiveCommitStatus = PostingLifecycleStatus.NOT_RUN;
    private PostingLifecycleStatus finalPreflightStatus = PostingLifecycleStatus.NOT_RUN;
    private PostingLifecycleStatus finalCommitStatus = PostingLifecycleStatus.NOT_RUN;
    private PostingLifecycleStatus duplicateStatus = PostingLifecycleStatus.NOT_RUN;
    private boolean storedFactPresent;

    private PostingWorkflowReplayDetails details(PostEntryCommand command) {
      return JazzerReplayDetailsMapper.postingWorkflowDetails(
          command, lifecycleDetails(), outcomeDetails());
    }

    private PostingWorkflowLifecycleDetails lifecycleDetails() {
      return new PostingWorkflowLifecycleDetails(
          new PostingGateDetails(uninitializedPreflightStatus, uninitializedCommitStatus),
          new PostingGateDetails(undeclaredPreflightStatus, undeclaredCommitStatus),
          new PostingGateDetails(inactivePreflightStatus, inactiveCommitStatus));
    }

    private PostingWorkflowOutcomeDetails outcomeDetails() {
      return new PostingWorkflowOutcomeDetails(
          finalPreflightStatus, finalCommitStatus, duplicateStatus, storedFactPresent);
    }
  }
}
