package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.CommitEntryResult;
import dev.erst.fingrind.contract.PreflightEntryResult;
import dev.erst.fingrind.contract.PostEntryResult;
import dev.erst.fingrind.contract.PostEntryResult.CommitRejected;
import dev.erst.fingrind.contract.PostEntryResult.Committed;
import dev.erst.fingrind.contract.PostEntryResult.PreflightAccepted;
import dev.erst.fingrind.contract.PostEntryResult.PreflightRejected;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.LedgerStep;
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.executor.InMemoryBookSession;
import dev.erst.fingrind.executor.PostingApplicationService;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.cli.CliFuzzSupport;
import dev.erst.fingrind.sqlite.SqliteFuzzAssertions;
import dev.erst.fingrind.jazzer.support.JazzerHarness;
import dev.erst.fingrind.sqlite.SqlitePostingFactStore;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Replays raw committed Jazzer inputs outside active fuzzing and classifies their outcome. */
public final class JazzerReplaySupport {
  private static final String NOT_PARSED = "NOT_PARSED";
  private static final String NONE = "NONE";

  private JazzerReplaySupport() {}

  /** Returns the stable replay expectation captured from one replay outcome. */
  public static ReplayExpectation expectationFor(ReplayOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome must not be null");
    return new ReplayExpectation(outcomeKind(outcome), outcome.details());
  }

  /** Replays one raw input against the selected harness and returns a structured outcome. */
  public static ReplayOutcome replay(JazzerHarness harness, byte[] input) {
    Objects.requireNonNull(harness, "harness must not be null");
    Objects.requireNonNull(input, "input must not be null");
    return switch (harness.key()) {
      case "cli-request" -> replayCliRequest(input);
      case "ledger-plan-request" -> replayLedgerPlanRequest(input);
      case "posting-workflow" -> replayPostingWorkflow(input);
      case "sqlite-book-roundtrip" -> replaySqliteBookRoundTrip(input);
      default -> throw new IllegalArgumentException("Unknown Jazzer harness: " + harness.key());
    };
  }

  /** Returns the stable external outcome kind used in committed seed metadata. */
  public static String outcomeKind(ReplayOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome must not be null");
    return switch (outcome) {
      case ReplayOutcome.Success _ -> "SUCCESS";
      case ReplayOutcome.ExpectedInvalid _ -> "EXPECTED_INVALID";
      case ReplayOutcome.UnexpectedFailure _ -> "UNEXPECTED_FAILURE";
    };
  }

  private static ReplayOutcome replayCliRequest(byte[] input) {
    try {
      PostEntryCommand command = CliFuzzSupport.readPostEntryCommand(input);
      return new ReplayOutcome.Success(
          JazzerHarness.cliRequest().key(),
          cliRequestDetails(command, "PARSED", normalizedMessage(null)));
    } catch (IllegalArgumentException expected) {
      return new ReplayOutcome.ExpectedInvalid(
          JazzerHarness.cliRequest().key(),
          expected.getClass().getSimpleName(),
          normalizedMessage(expected),
          cliRequestFailureDetails("INVALID_REQUEST", expected));
    } catch (RuntimeException unexpected) {
      return unexpectedFailure(
          JazzerHarness.cliRequest(),
          unexpected,
          cliRequestFailureDetails("UNEXPECTED_FAILURE", unexpected));
    }
  }

  private static ReplayOutcome replayLedgerPlanRequest(byte[] input) {
    try {
      LedgerPlan plan = CliFuzzSupport.readLedgerPlan(input);
      return new ReplayOutcome.Success(
          JazzerHarness.ledgerPlanRequest().key(),
          ledgerPlanDetails(plan, "PARSED", normalizedMessage(null)));
    } catch (IllegalArgumentException expected) {
      return new ReplayOutcome.ExpectedInvalid(
          JazzerHarness.ledgerPlanRequest().key(),
          expected.getClass().getSimpleName(),
          normalizedMessage(expected),
          ledgerPlanFailureDetails("INVALID_REQUEST", expected));
    } catch (RuntimeException unexpected) {
      return unexpectedFailure(
          JazzerHarness.ledgerPlanRequest(),
          unexpected,
          ledgerPlanFailureDetails("UNEXPECTED_FAILURE", unexpected));
    }
  }

  private static ReplayOutcome replayPostingWorkflow(byte[] input) {
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
          rejectionStatus(
              requiredPreflightRejected(applicationService.preflight(command)).rejection());
      uninitializedCommitStatus =
          rejectionStatus(requiredCommitRejected(applicationService.commit(command)).rejection());

      CliFuzzSupport.openBook(administrationService);

      undeclaredPreflightStatus =
          rejectionStatus(
              requiredPreflightRejected(applicationService.preflight(command)).rejection());
      undeclaredCommitStatus =
          rejectionStatus(requiredCommitRejected(applicationService.commit(command)).rejection());

      List<DeclaredAccount> declaredAccounts =
          CliFuzzSupport.declarePostingAccounts(administrationService, command);
      if (CliFuzzSupport.listAccounts(bookSession).size() != declaredAccounts.size()) {
        throw new IllegalStateException("Declared-account listing drifted from setup declarations.");
      }
      DeclaredAccount primaryAccount = declaredAccounts.getFirst();
      bookSession.deactivateAccount(primaryAccount.accountCode());
      inactivePreflightStatus =
          rejectionStatus(
              requiredPreflightRejected(applicationService.preflight(command)).rejection());
      inactiveCommitStatus =
          rejectionStatus(requiredCommitRejected(applicationService.commit(command)).rejection());

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
        duplicateStatus = rejectionStatus(rejected.rejection());
      } else if (preflight instanceof PreflightRejected preflightRejected) {
        finalPreflightStatus = rejectionStatus(preflightRejected.rejection());
        if (!(committedResult instanceof CommitRejected commitRejected)) {
          throw new IllegalStateException("Rejected preflight should remain rejected on commit.");
        }
        if (!commitRejected.rejection().equals(preflightRejected.rejection())) {
          throw new IllegalStateException("Commit changed the deterministic rejection.");
        }
        finalCommitStatus = rejectionStatus(commitRejected.rejection());
      } else {
        throw new IllegalStateException("Unexpected preflight result type.");
      }

      return new ReplayOutcome.Success(
          JazzerHarness.postingWorkflow().key(),
          postingWorkflowDetails(
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
              NONE));
    } catch (IllegalArgumentException expected) {
      return new ReplayOutcome.ExpectedInvalid(
          JazzerHarness.postingWorkflow().key(),
          expected.getClass().getSimpleName(),
          normalizedMessage(expected),
          postingWorkflowDetails(
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
              normalizedMessage(expected)));
    } catch (RuntimeException unexpected) {
      return unexpectedFailure(
          JazzerHarness.postingWorkflow(),
          unexpected,
          postingWorkflowDetails(
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
              normalizedMessage(unexpected)));
    }
  }

  private static ReplayOutcome replaySqliteBookRoundTrip(byte[] input) {
    PostEntryCommand command = null;
    String uninitializedCommitStatus = "NOT_RUN";
    String undeclaredCommitStatus = "NOT_RUN";
    String inactiveCommitStatus = "NOT_RUN";
    String finalCommitStatus = "NOT_RUN";
    String reloadStatus = "NOT_RUN";
    String duplicateStatus = "NOT_RUN";
    boolean storedFactPresent = false;
    Path bookDirectory = null;
    try {
      command = CliFuzzSupport.readPostEntryCommand(input);
      bookDirectory = Files.createTempDirectory("fingrind-jazzer-replay-");
      Path bookPath = bookDirectory.resolve("nested").resolve("entity-book.sqlite");

      try (SqlitePostingFactStore postingFactStore = SqliteFuzzAssertions.openStore(bookPath)) {
        BookAdministrationService administrationService =
            CliFuzzSupport.administrationService(postingFactStore.administrationSession());
        PostingApplicationService applicationService =
            new PostingApplicationService(
                postingFactStore.postingSession(),
                CliFuzzSupport.postingIdGenerator(input),
                CliFuzzSupport.fixedClock());

        uninitializedCommitStatus =
            rejectionStatus(requiredCommitRejected(applicationService.commit(command)).rejection());

        CliFuzzSupport.openBook(administrationService);

        undeclaredCommitStatus =
            rejectionStatus(requiredCommitRejected(applicationService.commit(command)).rejection());

        List<DeclaredAccount> declaredAccounts =
            CliFuzzSupport.declarePostingAccounts(administrationService, command);
        if (CliFuzzSupport.listAccounts(postingFactStore.querySession()).size()
            != declaredAccounts.size()) {
          throw new IllegalStateException("Declared-account listing drifted from setup declarations.");
        }
        DeclaredAccount primaryAccount = declaredAccounts.getFirst();
        SqliteFuzzAssertions.updateAccountActiveFlag(bookPath, primaryAccount.accountCode().value(), false);
        inactiveCommitStatus =
            rejectionStatus(requiredCommitRejected(applicationService.commit(command)).rejection());

        CliFuzzSupport.reactivateAccount(administrationService, primaryAccount);

        CommitEntryResult committedResult = applicationService.commit(command);
        if (committedResult instanceof Committed committed) {
          finalCommitStatus = "COMMITTED";
          try (SqlitePostingFactStore reloadedStore = SqliteFuzzAssertions.openStore(bookPath)) {
            Optional<PostingFact> storedPosting =
                reloadedStore.findExistingPosting(command.requestProvenance().idempotencyKey());
            if (storedPosting.isEmpty()) {
              throw new IllegalStateException("Committed posting fact was not persisted to SQLite.");
            }
            PostingFact postingFact = storedPosting.orElseThrow();
            if (!postingFact.postingId().equals(committed.postingId())) {
              throw new IllegalStateException("Reloaded posting id differs from the commit result.");
            }
            if (!postingFact.journalEntry().equals(command.journalEntry())) {
              throw new IllegalStateException(
                  "Reloaded journal entry differs from the parsed command.");
            }
            if (!postingFact.reversalReference().equals(command.reversalReference())) {
              throw new IllegalStateException(
                  "Reloaded reversal differs from the parsed command.");
            }
            if (!postingFact.provenance().requestProvenance().equals(command.requestProvenance())) {
              throw new IllegalStateException(
                  "Reloaded request provenance differs from the parsed command.");
            }
            if (!postingFact
                .provenance()
                .recordedAt()
                .equals(CliFuzzSupport.fixedClock().instant())) {
              throw new IllegalStateException(
                  "Reloaded recorded-at differs from the deterministic clock.");
            }
            if (postingFact.provenance().sourceChannel() != command.sourceChannel()) {
              throw new IllegalStateException(
                  "Reloaded source channel differs from the parsed command.");
            }
            storedFactPresent = true;
            reloadStatus = "RELOADED";

            PostingApplicationService duplicateService =
                new PostingApplicationService(
                    reloadedStore.postingSession(),
                    CliFuzzSupport.postingIdGenerator(input),
                    CliFuzzSupport.fixedClock());
            CommitEntryResult duplicateResult = duplicateService.commit(command);
            if (!(duplicateResult instanceof CommitRejected rejected)) {
              throw new IllegalStateException("Duplicate SQLite commit should be rejected.");
            }
            if (!(rejected.rejection() instanceof PostingRejection.DuplicateIdempotencyKey)) {
              throw new IllegalStateException(
                  "Duplicate SQLite commit returned the wrong rejection code.");
            }
            duplicateStatus = rejectionStatus(rejected.rejection());
          }
        } else if (committedResult instanceof CommitRejected rejected) {
          finalCommitStatus = rejectionStatus(rejected.rejection());
          CommitEntryResult repeatedResult = applicationService.commit(command);
          if (!(repeatedResult instanceof CommitRejected repeatedRejected)) {
            throw new IllegalStateException("Rejected SQLite command should remain rejected.");
          }
          if (!repeatedRejected.rejection().equals(rejected.rejection())) {
            throw new IllegalStateException("Repeated SQLite rejection changed unexpectedly.");
          }
          duplicateStatus = rejectionStatus(repeatedRejected.rejection());
        } else {
          throw new IllegalStateException("Unexpected SQLite commit result type.");
        }
      }

      return new ReplayOutcome.Success(
          JazzerHarness.sqliteBookRoundTrip().key(),
          sqliteBookRoundTripDetails(
              command,
              "PARSED",
              uninitializedCommitStatus,
              undeclaredCommitStatus,
              inactiveCommitStatus,
              finalCommitStatus,
              reloadStatus,
              duplicateStatus,
              storedFactPresent,
              NONE));
    } catch (IllegalArgumentException expected) {
      return new ReplayOutcome.ExpectedInvalid(
          JazzerHarness.sqliteBookRoundTrip().key(),
          expected.getClass().getSimpleName(),
          normalizedMessage(expected),
          sqliteBookRoundTripDetails(
              command,
              "INVALID_REQUEST",
              uninitializedCommitStatus,
              undeclaredCommitStatus,
              inactiveCommitStatus,
              finalCommitStatus,
              reloadStatus,
              duplicateStatus,
              storedFactPresent,
              normalizedMessage(expected)));
    } catch (IOException unexpected) {
      return unexpectedFailure(
          JazzerHarness.sqliteBookRoundTrip(),
          unexpected,
          sqliteBookRoundTripDetails(
              command,
              command == null ? "UNEXPECTED_FAILURE" : "PARSED",
              uninitializedCommitStatus,
              undeclaredCommitStatus,
              inactiveCommitStatus,
              finalCommitStatus,
              reloadStatus,
              duplicateStatus,
              storedFactPresent,
              normalizedMessage(unexpected)));
    } catch (RuntimeException unexpected) {
      return unexpectedFailure(
          JazzerHarness.sqliteBookRoundTrip(),
          unexpected,
          sqliteBookRoundTripDetails(
              command,
              command == null ? "UNEXPECTED_FAILURE" : "PARSED",
              uninitializedCommitStatus,
              undeclaredCommitStatus,
              inactiveCommitStatus,
              finalCommitStatus,
              reloadStatus,
              duplicateStatus,
              storedFactPresent,
              normalizedMessage(unexpected)));
    } finally {
      deleteRecursively(bookDirectory);
    }
  }

  private static CliRequestReplayDetails cliRequestDetails(
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

  private static CliRequestReplayDetails cliRequestFailureDetails(
      String requestStatus, Throwable error) {
    return new CliRequestReplayDetails(
        requestStatus, NOT_PARSED, NOT_PARSED, 0, false, NOT_PARSED, NOT_PARSED, normalizedMessage(error));
  }

  private static LedgerPlanReplayDetails ledgerPlanDetails(
      LedgerPlan plan, String requestStatus, String failureMessage) {
    return new LedgerPlanReplayDetails(
        requestStatus,
        plan.planId(),
        plan.steps().size(),
        plan.steps().getFirst().kind().wireValue(),
        plan.steps().getLast().kind().wireValue(),
        assertionStepCount(plan),
        plan.beginsWithOpenBook(),
        failureMessage);
  }

  private static LedgerPlanReplayDetails ledgerPlanFailureDetails(
      String requestStatus, Throwable error) {
    return new LedgerPlanReplayDetails(
        requestStatus, NOT_PARSED, 0, NOT_PARSED, NOT_PARSED, 0, false, normalizedMessage(error));
  }

  private static PostingWorkflowReplayDetails postingWorkflowDetails(
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

  private static SqliteBookRoundTripReplayDetails sqliteBookRoundTripDetails(
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

  private static ReplayOutcome unexpectedFailure(
      JazzerHarness harness, Throwable error, ReplayDetails details) {
    return new ReplayOutcome.UnexpectedFailure(
        harness.key(),
        error.getClass().getSimpleName(),
        normalizedMessage(error),
        stackTrace(error),
        details);
  }

  private static void deleteRecursively(Path directory) {
    if (directory == null) {
      return;
    }
    try {
      if (!Files.exists(directory)) {
        return;
      }
      Files.walk(directory)
          .sorted((left, right) -> right.compareTo(left))
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (java.io.IOException ignored) {
                  // Best-effort cleanup for replay scratch space.
                }
              });
    } catch (java.io.IOException ignored) {
      // Best-effort cleanup for replay scratch space.
    }
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

  private static String rejectionStatus(PostingRejection rejection) {
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

  private static PreflightRejected requiredPreflightRejected(PreflightEntryResult result) {
    if (!(result instanceof PreflightRejected rejected)) {
      throw new IllegalStateException(
          "Expected deterministic preflight rejection during replay lifecycle setup.");
    }
    return rejected;
  }

  private static CommitRejected requiredCommitRejected(CommitEntryResult result) {
    if (!(result instanceof CommitRejected rejected)) {
      throw new IllegalStateException(
          "Expected deterministic commit rejection during replay lifecycle setup.");
    }
    return rejected;
  }

  private static String normalizedMessage(Throwable error) {
    if (error == null) {
      return NONE;
    }
    return Objects.requireNonNullElse(error.getMessage(), error.getClass().getSimpleName());
  }

  private static String stackTrace(Throwable error) {
    StringWriter output = new StringWriter();
    error.printStackTrace(new PrintWriter(output, true));
    return output.toString();
  }
}
