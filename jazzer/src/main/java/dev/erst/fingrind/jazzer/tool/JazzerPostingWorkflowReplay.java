package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.cli.CliFuzzSupport;
import dev.erst.fingrind.contract.CommitEntryResult;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PostEntryResult.CommitRejected;
import dev.erst.fingrind.contract.PostEntryResult.Committed;
import dev.erst.fingrind.contract.PostEntryResult.PreflightAccepted;
import dev.erst.fingrind.contract.PostEntryResult.PreflightRejected;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.contract.PreflightEntryResult;
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.InMemoryBookSession;
import dev.erst.fingrind.executor.PostingApplicationService;
import dev.erst.fingrind.jazzer.support.JazzerHarness;
import java.util.List;
import java.util.Optional;

/** Replays posting-command workflows against the in-memory bookkeeping harness. */
final class JazzerPostingWorkflowReplay {
  private JazzerPostingWorkflowReplay() {}

  static ReplayOutcome replay(byte[] input) {
    PostEntryCommand command = null;
    String uninitializedPreflightStatus = "NOT_RUN";
    String uninitializedCommitStatus = "NOT_RUN";
    String undeclaredPreflightStatus = "NOT_RUN";
    String undeclaredCommitStatus = "NOT_RUN";
    String inactivePreflightStatus = "NOT_RUN";
    String inactiveCommitStatus = "NOT_RUN";
    String finalPreflightStatus = "NOT_RUN";
    String finalCommitStatus = "NOT_RUN";
    String duplicateStatus = "NOT_RUN";
    boolean storedFactPresent = false;
    try {
      command = CliFuzzSupport.readPostEntryCommand(input);
      InMemoryBookSession bookSession = new InMemoryBookSession();
      BookAdministrationService administrationService =
          CliFuzzSupport.administrationService(bookSession);
      PostingApplicationService applicationService =
          new PostingApplicationService(
              bookSession,
              CliFuzzSupport.postingIdGenerator(input),
              CliFuzzSupport.fixedClock());

      uninitializedPreflightStatus =
          JazzerReplayDetailsSupport.rejectionStatus(
              JazzerReplayDetailsSupport
                  .requiredPreflightRejected(applicationService.preflight(command))
                  .rejection());
      uninitializedCommitStatus =
          JazzerReplayDetailsSupport.rejectionStatus(
              JazzerReplayDetailsSupport.requiredCommitRejected(applicationService.commit(command))
                  .rejection());

      CliFuzzSupport.openBook(administrationService);

      undeclaredPreflightStatus =
          JazzerReplayDetailsSupport.rejectionStatus(
              JazzerReplayDetailsSupport
                  .requiredPreflightRejected(applicationService.preflight(command))
                  .rejection());
      undeclaredCommitStatus =
          JazzerReplayDetailsSupport.rejectionStatus(
              JazzerReplayDetailsSupport.requiredCommitRejected(applicationService.commit(command))
                  .rejection());

      List<DeclaredAccount> declaredAccounts =
          CliFuzzSupport.declarePostingAccounts(administrationService, command);
      if (CliFuzzSupport.listAccounts(bookSession).size() != declaredAccounts.size()) {
        throw new IllegalStateException("Declared-account listing drifted from setup declarations.");
      }
      DeclaredAccount primaryAccount = declaredAccounts.getFirst();
      bookSession.deactivateAccount(primaryAccount.accountCode());
      inactivePreflightStatus =
          JazzerReplayDetailsSupport.rejectionStatus(
              JazzerReplayDetailsSupport
                  .requiredPreflightRejected(applicationService.preflight(command))
                  .rejection());
      inactiveCommitStatus =
          JazzerReplayDetailsSupport.rejectionStatus(
              JazzerReplayDetailsSupport.requiredCommitRejected(applicationService.commit(command))
                  .rejection());

      CliFuzzSupport.reactivateAccount(administrationService, primaryAccount);

      PreflightEntryResult preflight = applicationService.preflight(command);
      CommitEntryResult committedResult = applicationService.commit(command);
      if (preflight instanceof PreflightAccepted accepted) {
        if (!accepted.idempotencyKey().equals(command.requestProvenance().idempotencyKey())) {
          throw new IllegalStateException("Preflight changed the idempotency key.");
        }
        if (!accepted.effectiveDate().equals(command.journalEntry().effectiveDate())) {
          throw new IllegalStateException("Preflight changed the effective date.");
        }
        finalPreflightStatus = "PREFLIGHT_ACCEPTED";
        if (!(committedResult instanceof Committed committed)) {
          throw new IllegalStateException("Accepted preflight should commit on a fresh valid book.");
        }
        finalCommitStatus = "COMMITTED";

        Optional<PostingFact> storedPosting =
            bookSession.findExistingPosting(command.requestProvenance().idempotencyKey());
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
        storedFactPresent = true;

        CommitEntryResult duplicateResult = applicationService.commit(command);
        if (!(duplicateResult instanceof CommitRejected rejected)) {
          throw new IllegalStateException("Duplicate commit should be rejected.");
        }
        if (!(rejected.rejection() instanceof PostingRejection.DuplicateIdempotencyKey)) {
          throw new IllegalStateException("Duplicate commit returned the wrong rejection code.");
        }
        duplicateStatus = JazzerReplayDetailsSupport.rejectionStatus(rejected.rejection());
      } else if (preflight instanceof PreflightRejected preflightRejected) {
        finalPreflightStatus =
            JazzerReplayDetailsSupport.rejectionStatus(preflightRejected.rejection());
        if (!(committedResult instanceof CommitRejected commitRejected)) {
          throw new IllegalStateException("Rejected preflight should remain rejected on commit.");
        }
        if (!commitRejected.rejection().equals(preflightRejected.rejection())) {
          throw new IllegalStateException("Commit changed the deterministic rejection.");
        }
        finalCommitStatus = JazzerReplayDetailsSupport.rejectionStatus(commitRejected.rejection());
      } else {
        throw new IllegalStateException("Unexpected preflight result type.");
      }

      return new ReplayOutcome.Success(
          JazzerHarness.postingWorkflow().key(),
          JazzerReplayDetailsSupport.postingWorkflowDetails(
              command,
              "PARSED",
              uninitializedPreflightStatus,
              uninitializedCommitStatus,
              undeclaredPreflightStatus,
              undeclaredCommitStatus,
              inactivePreflightStatus,
              inactiveCommitStatus,
              finalPreflightStatus,
              finalCommitStatus,
              duplicateStatus,
              storedFactPresent,
              JazzerReplayDetailsSupport.NONE));
    } catch (IllegalArgumentException expected) {
      return new ReplayOutcome.ExpectedInvalid(
          JazzerHarness.postingWorkflow().key(),
          expected.getClass().getSimpleName(),
          JazzerReplayDetailsSupport.normalizedMessage(expected),
          JazzerReplayDetailsSupport.postingWorkflowDetails(
              command,
              "INVALID_REQUEST",
              uninitializedPreflightStatus,
              uninitializedCommitStatus,
              undeclaredPreflightStatus,
              undeclaredCommitStatus,
              inactivePreflightStatus,
              inactiveCommitStatus,
              finalPreflightStatus,
              finalCommitStatus,
              duplicateStatus,
              storedFactPresent,
              JazzerReplayDetailsSupport.normalizedMessage(expected)));
    } catch (RuntimeException unexpected) {
      return JazzerReplayDetailsSupport.unexpectedFailure(
          JazzerHarness.postingWorkflow(),
          unexpected,
          JazzerReplayDetailsSupport.postingWorkflowDetails(
              command,
              command == null ? "UNEXPECTED_FAILURE" : "PARSED",
              uninitializedPreflightStatus,
              uninitializedCommitStatus,
              undeclaredPreflightStatus,
              undeclaredCommitStatus,
              inactivePreflightStatus,
              inactiveCommitStatus,
              finalPreflightStatus,
              finalCommitStatus,
              duplicateStatus,
              storedFactPresent,
              JazzerReplayDetailsSupport.normalizedMessage(unexpected)));
    }
  }
}
