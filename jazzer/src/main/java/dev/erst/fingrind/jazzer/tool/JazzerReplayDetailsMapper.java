package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.contract.CommitEntryResult;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.LedgerStep;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PostEntryResult.CommitRejected;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.contract.PreflightEntryResult;
import dev.erst.fingrind.contract.PostEntryResult.PreflightRejected;
import dev.erst.fingrind.jazzer.support.JazzerHarness;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

/** Shared outcome-detail builders for Jazzer replay classification. */
final class JazzerReplayDetailsMapper {
  static final String NOT_PARSED = "NOT_PARSED";
  static final String NONE = "NONE";

  private JazzerReplayDetailsMapper() {}

  static CliRequestReplayDetails cliRequestDetails(
      PostEntryCommand command, String requestStatus, String failureMessage) {
    return new CliRequestReplayDetails(
        requestStatus,
        effectiveDate(command),
        idempotencyKey(command),
        lineCount(command),
        reversalPresent(command),
        actorType(command),
        sourceChannel(command),
        failureMessage);
  }

  static CliRequestReplayDetails cliRequestFailureDetails(String requestStatus, Throwable error) {
    return new CliRequestReplayDetails(
        requestStatus,
        NOT_PARSED,
        NOT_PARSED,
        0,
        false,
        NOT_PARSED,
        NOT_PARSED,
        normalizedMessage(error));
  }

  static LedgerPlanReplayDetails ledgerPlanDetails(
      LedgerPlan plan, String requestStatus, String failureMessage) {
    return new LedgerPlanReplayDetails(
        requestStatus,
        plan.planId().value(),
        plan.steps().size(),
        plan.steps().getFirst().kind().wireValue(),
        plan.steps().getLast().kind().wireValue(),
        assertionStepCount(plan),
        plan.beginsWithOpenBook(),
        failureMessage);
  }

  static LedgerPlanReplayDetails ledgerPlanFailureDetails(String requestStatus, Throwable error) {
    return new LedgerPlanReplayDetails(
        requestStatus, NOT_PARSED, 0, NOT_PARSED, NOT_PARSED, 0, false, normalizedMessage(error));
  }

  static PostingWorkflowReplayDetails postingWorkflowDetails(
      PostEntryCommand command,
      String requestStatus,
      String uninitializedPreflightStatus,
      String uninitializedCommitStatus,
      String undeclaredPreflightStatus,
      String undeclaredCommitStatus,
      String inactivePreflightStatus,
      String inactiveCommitStatus,
      String finalPreflightStatus,
      String finalCommitStatus,
      String duplicateStatus,
      boolean storedFactPresent,
      String failureMessage) {
    return new PostingWorkflowReplayDetails(
        requestStatus,
        effectiveDate(command),
        idempotencyKey(command),
        lineCount(command),
        reversalPresent(command),
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
        failureMessage);
  }

  static SqliteBookRoundTripReplayDetails sqliteBookRoundTripDetails(
      PostEntryCommand command,
      String requestStatus,
      String uninitializedCommitStatus,
      String undeclaredCommitStatus,
      String inactiveCommitStatus,
      String finalCommitStatus,
      String reloadStatus,
      String duplicateStatus,
      boolean storedFactPresent,
      String failureMessage) {
    return new SqliteBookRoundTripReplayDetails(
        requestStatus,
        effectiveDate(command),
        idempotencyKey(command),
        lineCount(command),
        reversalPresent(command),
        uninitializedCommitStatus,
        undeclaredCommitStatus,
        inactiveCommitStatus,
        finalCommitStatus,
        reloadStatus,
        duplicateStatus,
        storedFactPresent,
        failureMessage);
  }

  static ReplayOutcome unexpectedFailure(
      JazzerHarness harness, Throwable error, ReplayDetails details) {
    return new ReplayOutcome.UnexpectedFailure(
        harness.key(),
        error.getClass().getSimpleName(),
        normalizedMessage(error),
        stackTrace(error),
        details);
  }

  static String rejectionStatus(PostingRejection rejection) {
    return switch (rejection) {
      case PostingRejection.BookNotInitialized _ -> "REJECTED_BOOK_NOT_INITIALIZED";
      case PostingRejection.AccountStateViolations accountStateViolations ->
          accountStateViolationStatus(accountStateViolations);
      case PostingRejection.DuplicateIdempotencyKey _ -> "REJECTED_DUPLICATE_IDEMPOTENCY_KEY";
      case PostingRejection.ReversalTargetNotFound _ -> "REJECTED_REVERSAL_TARGET_NOT_FOUND";
      case PostingRejection.ReversalAlreadyExists _ -> "REJECTED_REVERSAL_ALREADY_EXISTS";
      case PostingRejection.ReversalDoesNotNegateTarget _ ->
          "REJECTED_REVERSAL_DOES_NOT_NEGATE_TARGET";
    };
  }

  static PreflightRejected requiredPreflightRejected(PreflightEntryResult result) {
    if (!(result instanceof PreflightRejected rejected)) {
      throw new IllegalStateException(
          "Expected deterministic preflight rejection during replay lifecycle setup.");
    }
    return rejected;
  }

  static CommitRejected requiredCommitRejected(CommitEntryResult result) {
    if (!(result instanceof CommitRejected rejected)) {
      throw new IllegalStateException(
          "Expected deterministic commit rejection during replay lifecycle setup.");
    }
    return rejected;
  }

  static String normalizedMessage(Throwable error) {
    if (error == null) {
      return NONE;
    }
    return Objects.requireNonNullElse(error.getMessage(), error.getClass().getSimpleName());
  }

  private static String effectiveDate(PostEntryCommand command) {
    return command == null ? NOT_PARSED : command.journalEntry().effectiveDate().toString();
  }

  private static String idempotencyKey(PostEntryCommand command) {
    return command == null ? NOT_PARSED : command.requestProvenance().idempotencyKey().value();
  }

  private static int lineCount(PostEntryCommand command) {
    return command == null ? 0 : command.journalEntry().lines().size();
  }

  private static boolean reversalPresent(PostEntryCommand command) {
    return command != null && command.reversalReference().isPresent();
  }

  private static int assertionStepCount(LedgerPlan plan) {
    return (int) plan.steps().stream().filter(LedgerStep.Assert.class::isInstance).count();
  }

  private static String actorType(PostEntryCommand command) {
    return command == null ? NOT_PARSED : command.requestProvenance().actorType().wireValue();
  }

  private static String sourceChannel(PostEntryCommand command) {
    return command == null ? NOT_PARSED : command.sourceChannel().wireValue();
  }

  private static String accountStateViolationStatus(
      PostingRejection.AccountStateViolations accountStateViolations) {
    boolean allUnknown =
        accountStateViolations.violations().stream()
            .allMatch(PostingRejection.UnknownAccount.class::isInstance);
    if (allUnknown) {
      return "REJECTED_UNKNOWN_ACCOUNT";
    }
    boolean allInactive =
        accountStateViolations.violations().stream()
            .allMatch(PostingRejection.InactiveAccount.class::isInstance);
    if (allInactive) {
      return "REJECTED_INACTIVE_ACCOUNT";
    }
    return "REJECTED_ACCOUNT_STATE_VIOLATIONS";
  }

  private static String stackTrace(Throwable error) {
    StringWriter output = new StringWriter();
    error.printStackTrace(new PrintWriter(output, true));
    return output.toString();
  }
}
