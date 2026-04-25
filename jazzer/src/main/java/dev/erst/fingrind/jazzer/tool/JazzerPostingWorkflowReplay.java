package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.cli.CliFuzzFixtures;
import dev.erst.fingrind.contract.CommitEntryResult;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PostEntryResult.CommitRejected;
import dev.erst.fingrind.contract.PostEntryResult.Committed;
import dev.erst.fingrind.contract.PostEntryResult.PreflightAccepted;
import dev.erst.fingrind.contract.PostEntryResult.PreflightRejected;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PreflightEntryResult;
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.InMemoryBookSession;
import dev.erst.fingrind.executor.PostingApplicationService;
import dev.erst.fingrind.jazzer.support.JazzerHarness;
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
          JazzerReplayDetailsMapper.normalizedMessage(expected),
          JazzerReplayDetailsMapper.unparsedPostingWorkflowDetails());
    } catch (RuntimeException unexpected) {
      return JazzerReplayDetailsMapper.unexpectedFailure(
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
          CliFuzzFixtures.administrationService(bookSession);
      PostingApplicationService applicationService =
          new PostingApplicationService(
              bookSession, CliFuzzFixtures.postingIdGenerator(input), CliFuzzFixtures.fixedClock());

      state.uninitializedPreflightStatus =
          JazzerReplayDetailsMapper.rejectionStatus(
              JazzerReplayDetailsMapper.requiredPreflightRejected(
                      applicationService.preflight(command))
                  .rejection());
      state.uninitializedCommitStatus =
          JazzerReplayDetailsMapper.rejectionStatus(
              JazzerReplayDetailsMapper.requiredCommitRejected(applicationService.commit(command))
                  .rejection());

      CliFuzzFixtures.openBook(administrationService);

      state.undeclaredPreflightStatus =
          JazzerReplayDetailsMapper.rejectionStatus(
              JazzerReplayDetailsMapper.requiredPreflightRejected(
                      applicationService.preflight(command))
                  .rejection());
      state.undeclaredCommitStatus =
          JazzerReplayDetailsMapper.rejectionStatus(
              JazzerReplayDetailsMapper.requiredCommitRejected(applicationService.commit(command))
                  .rejection());

      List<DeclaredAccount> declaredAccounts =
          CliFuzzFixtures.declarePostingAccounts(administrationService, command);
      PostingWorkflowReplayVerifier.verifyDeclaredAccountListing(
          CliFuzzFixtures.listAccounts(bookSession).size(), declaredAccounts.size());
      DeclaredAccount primaryAccount = declaredAccounts.getFirst();
      bookSession.deactivateAccount(primaryAccount.accountCode());
      state.inactivePreflightStatus =
          JazzerReplayDetailsMapper.rejectionStatus(
              JazzerReplayDetailsMapper.requiredPreflightRejected(
                      applicationService.preflight(command))
                  .rejection());
      state.inactiveCommitStatus =
          JazzerReplayDetailsMapper.rejectionStatus(
              JazzerReplayDetailsMapper.requiredCommitRejected(applicationService.commit(command))
                  .rejection());

      CliFuzzFixtures.reactivateAccount(administrationService, primaryAccount);

      PreflightEntryResult preflight = applicationService.preflight(command);
      CommitEntryResult committedResult = applicationService.commit(command);
      switch (preflight) {
        case PreflightAccepted accepted -> {
          PostingWorkflowReplayVerifier.verifyAcceptedPreflight(accepted, command);
          state.finalPreflightStatus = PostingLifecycleStatus.PREFLIGHT_ACCEPTED;
          Committed committed = requireCommittedAfterAcceptedPreflight(committedResult);
          state.finalCommitStatus = PostingLifecycleStatus.COMMITTED;

          PostingFact postingFact =
              PostingWorkflowReplayVerifier.requireStoredPosting(
                  bookSession.findExistingPosting(command.requestProvenance().idempotencyKey()));
          PostingWorkflowReplayVerifier.verifyStoredPosting(postingFact, committed, command);
          state.storedFactPresent = true;
          state.duplicateStatus =
              PostingWorkflowReplayVerifier.requireDuplicateRejection(
                  applicationService.commit(command));
        }
        case PreflightRejected preflightRejected -> {
          state.finalPreflightStatus =
              JazzerReplayDetailsMapper.rejectionStatus(preflightRejected.rejection());
          state.finalCommitStatus =
              PostingWorkflowReplayVerifier.verifyRejectedPreflightAndCommit(
                  preflightRejected, committedResult);
        }
      }
    }
  }

  static Committed requireCommittedAfterAcceptedPreflight(CommitEntryResult committedResult) {
    return switch (committedResult) {
      case Committed committed -> committed;
      case CommitRejected _ ->
          throw new IllegalStateException(
              "Accepted preflight should commit on a fresh valid book.");
    };
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
