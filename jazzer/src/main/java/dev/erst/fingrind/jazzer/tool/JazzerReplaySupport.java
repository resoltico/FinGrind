package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.application.PostEntryCommand;
import dev.erst.fingrind.application.PostEntryResult;
import dev.erst.fingrind.application.PostEntryResult.Committed;
import dev.erst.fingrind.application.PostEntryResult.PreflightAccepted;
import dev.erst.fingrind.application.PostEntryResult.Rejected;
import dev.erst.fingrind.application.PostingApplicationService;
import dev.erst.fingrind.application.PostingRejectionCode;
import dev.erst.fingrind.cli.CliFuzzSupport;
import dev.erst.fingrind.jazzer.support.JazzerHarness;
import dev.erst.fingrind.runtime.InMemoryPostingFactStore;
import dev.erst.fingrind.runtime.PostingFact;
import dev.erst.fingrind.sqlite.SqlitePostingFactStore;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
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
    return switch (harness) {
      case CLI_REQUEST -> replayCliRequest(input);
      case POSTING_WORKFLOW -> replayPostingWorkflow(input);
      case SQLITE_BOOK_ROUND_TRIP -> replaySqliteBookRoundTrip(input);
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
          JazzerHarness.CLI_REQUEST.key(),
          cliRequestDetails(command, "PARSED", normalizedMessage(null)));
    } catch (IllegalArgumentException expected) {
      return new ReplayOutcome.ExpectedInvalid(
          JazzerHarness.CLI_REQUEST.key(),
          expected.getClass().getSimpleName(),
          normalizedMessage(expected),
          cliRequestFailureDetails("INVALID_REQUEST", expected));
    } catch (RuntimeException unexpected) {
      return unexpectedFailure(
          JazzerHarness.CLI_REQUEST,
          unexpected,
          cliRequestFailureDetails("UNEXPECTED_FAILURE", unexpected));
    }
  }

  private static ReplayOutcome replayPostingWorkflow(byte[] input) {
    PostEntryCommand command = null;
    String preflightStatus = "NOT_RUN";
    String firstCommitStatus = "NOT_RUN";
    String duplicateStatus = "NOT_RUN";
    boolean storedFactPresent = false;
    try {
      command = CliFuzzSupport.readPostEntryCommand(input);
      InMemoryPostingFactStore postingFactStore = new InMemoryPostingFactStore();
      PostingApplicationService applicationService =
          new PostingApplicationService(postingFactStore, CliFuzzSupport.postingIdSupplier(input));

      PostEntryResult preflight = applicationService.preflight(command);
      if (!(preflight instanceof PreflightAccepted accepted)) {
        throw new IllegalStateException("Expected a preflight acceptance for a fresh valid book.");
      }
      if (!accepted.idempotencyKey().equals(command.provenance().idempotencyKey())) {
        throw new IllegalStateException("Preflight changed the idempotency key.");
      }
      if (!accepted.effectiveDate().equals(command.journalEntry().effectiveDate())) {
        throw new IllegalStateException("Preflight changed the effective date.");
      }
      preflightStatus = "PREFLIGHT_ACCEPTED";

      PostEntryResult committedResult = applicationService.commit(command);
      if (!(committedResult instanceof Committed committed)) {
        throw new IllegalStateException("Expected a committed result for a fresh valid book.");
      }
      firstCommitStatus = "COMMITTED";

      Optional<PostingFact> storedPosting =
          postingFactStore.findByIdempotency(command.provenance().idempotencyKey());
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
      if (!postingFact.provenance().equals(command.provenance())) {
        throw new IllegalStateException("Stored provenance differs from the parsed command.");
      }
      storedFactPresent = true;

      PostEntryResult duplicateResult = applicationService.commit(command);
      if (!(duplicateResult instanceof Rejected rejected)) {
        throw new IllegalStateException("Duplicate commit should be rejected.");
      }
      if (rejected.code() != PostingRejectionCode.DUPLICATE_IDEMPOTENCY_KEY) {
        throw new IllegalStateException("Duplicate commit returned the wrong rejection code.");
      }
      duplicateStatus = "REJECTED_DUPLICATE_IDEMPOTENCY_KEY";

      return new ReplayOutcome.Success(
          JazzerHarness.POSTING_WORKFLOW.key(),
          postingWorkflowDetails(
              command,
              "PARSED",
              preflightStatus,
              firstCommitStatus,
              duplicateStatus,
              storedFactPresent,
              NONE));
    } catch (IllegalArgumentException expected) {
      return new ReplayOutcome.ExpectedInvalid(
          JazzerHarness.POSTING_WORKFLOW.key(),
          expected.getClass().getSimpleName(),
          normalizedMessage(expected),
          postingWorkflowDetails(
              command,
              "INVALID_REQUEST",
              preflightStatus,
              firstCommitStatus,
              duplicateStatus,
              storedFactPresent,
              normalizedMessage(expected)));
    } catch (RuntimeException unexpected) {
      return unexpectedFailure(
          JazzerHarness.POSTING_WORKFLOW,
          unexpected,
          postingWorkflowDetails(
              command,
              command == null ? "UNEXPECTED_FAILURE" : "PARSED",
              preflightStatus,
              firstCommitStatus,
              duplicateStatus,
              storedFactPresent,
              normalizedMessage(unexpected)));
    }
  }

  private static ReplayOutcome replaySqliteBookRoundTrip(byte[] input) {
    PostEntryCommand command = null;
    String firstCommitStatus = "NOT_RUN";
    String reloadStatus = "NOT_RUN";
    String duplicateStatus = "NOT_RUN";
    boolean storedFactPresent = false;
    Path bookDirectory = null;
    try {
      command = CliFuzzSupport.readPostEntryCommand(input);
      bookDirectory = Files.createTempDirectory("fingrind-jazzer-replay-");
      Path bookPath = bookDirectory.resolve("nested").resolve("entity-book.sqlite");

      Committed committed;
      try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(bookPath)) {
        PostingApplicationService applicationService =
            new PostingApplicationService(postingFactStore, CliFuzzSupport.postingIdSupplier(input));
        PostEntryResult committedResult = applicationService.commit(command);
        if (!(committedResult instanceof Committed committedValue)) {
          throw new IllegalStateException("Expected a committed result for a fresh valid SQLite book.");
        }
        committed = committedValue;
        firstCommitStatus = "COMMITTED";
      }

      try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(bookPath)) {
        Optional<PostingFact> storedPosting =
            postingFactStore.findByIdempotency(command.provenance().idempotencyKey());
        if (storedPosting.isEmpty()) {
          throw new IllegalStateException("Committed posting fact was not persisted to SQLite.");
        }
        PostingFact postingFact = storedPosting.orElseThrow();
        if (!postingFact.postingId().equals(committed.postingId())) {
          throw new IllegalStateException("Reloaded posting id differs from the commit result.");
        }
        if (!postingFact.journalEntry().equals(command.journalEntry())) {
          throw new IllegalStateException("Reloaded journal entry differs from the parsed command.");
        }
        if (!postingFact.provenance().equals(command.provenance())) {
          throw new IllegalStateException("Reloaded provenance differs from the parsed command.");
        }
        storedFactPresent = true;
        reloadStatus = "RELOADED";

        PostingApplicationService applicationService =
            new PostingApplicationService(postingFactStore, CliFuzzSupport.postingIdSupplier(input));
        PostEntryResult duplicateResult = applicationService.commit(command);
        if (!(duplicateResult instanceof Rejected rejected)) {
          throw new IllegalStateException("Duplicate SQLite commit should be rejected.");
        }
        if (rejected.code() != PostingRejectionCode.DUPLICATE_IDEMPOTENCY_KEY) {
          throw new IllegalStateException("Duplicate SQLite commit returned the wrong rejection code.");
        }
        duplicateStatus = "REJECTED_DUPLICATE_IDEMPOTENCY_KEY";
      }

      return new ReplayOutcome.Success(
          JazzerHarness.SQLITE_BOOK_ROUND_TRIP.key(),
          sqliteBookRoundTripDetails(
              command,
              "PARSED",
              firstCommitStatus,
              reloadStatus,
              duplicateStatus,
              storedFactPresent,
              NONE));
    } catch (IllegalArgumentException expected) {
      return new ReplayOutcome.ExpectedInvalid(
          JazzerHarness.SQLITE_BOOK_ROUND_TRIP.key(),
          expected.getClass().getSimpleName(),
          normalizedMessage(expected),
          sqliteBookRoundTripDetails(
              command,
              "INVALID_REQUEST",
              firstCommitStatus,
              reloadStatus,
              duplicateStatus,
              storedFactPresent,
              normalizedMessage(expected)));
    } catch (IOException unexpected) {
      return unexpectedFailure(
          JazzerHarness.SQLITE_BOOK_ROUND_TRIP,
          unexpected,
          sqliteBookRoundTripDetails(
              command,
              command == null ? "UNEXPECTED_FAILURE" : "PARSED",
              firstCommitStatus,
              reloadStatus,
              duplicateStatus,
              storedFactPresent,
              normalizedMessage(unexpected)));
    } catch (RuntimeException unexpected) {
      return unexpectedFailure(
          JazzerHarness.SQLITE_BOOK_ROUND_TRIP,
          unexpected,
          sqliteBookRoundTripDetails(
              command,
              command == null ? "UNEXPECTED_FAILURE" : "PARSED",
              firstCommitStatus,
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
        correctionPresent(command),
        actorType(command),
        sourceChannel(command),
        failureMessage);
  }

  private static CliRequestReplayDetails cliRequestFailureDetails(
      String requestStatus, Throwable error) {
    return new CliRequestReplayDetails(
        requestStatus, NOT_PARSED, NOT_PARSED, 0, false, NOT_PARSED, NOT_PARSED, normalizedMessage(error));
  }

  private static PostingWorkflowReplayDetails postingWorkflowDetails(
      PostEntryCommand command,
      String requestStatus,
      String preflightStatus,
      String firstCommitStatus,
      String duplicateStatus,
      boolean storedFactPresent,
      String failureMessage) {
    return new PostingWorkflowReplayDetails(
        requestStatus,
        effectiveDate(command),
        idempotencyKey(command),
        lineCount(command),
        correctionPresent(command),
        preflightStatus,
        firstCommitStatus,
        duplicateStatus,
        storedFactPresent,
        failureMessage);
  }

  private static SqliteBookRoundTripReplayDetails sqliteBookRoundTripDetails(
      PostEntryCommand command,
      String requestStatus,
      String firstCommitStatus,
      String reloadStatus,
      String duplicateStatus,
      boolean storedFactPresent,
      String failureMessage) {
    return new SqliteBookRoundTripReplayDetails(
        requestStatus,
        effectiveDate(command),
        idempotencyKey(command),
        lineCount(command),
        correctionPresent(command),
        firstCommitStatus,
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
    return command == null ? NOT_PARSED : command.provenance().idempotencyKey().value();
  }

  private static int lineCount(PostEntryCommand command) {
    return command == null ? 0 : command.journalEntry().lines().size();
  }

  private static boolean correctionPresent(PostEntryCommand command) {
    return command != null && command.journalEntry().correctionReference().isPresent();
  }

  private static String actorType(PostEntryCommand command) {
    return command == null ? NOT_PARSED : command.provenance().actorType().name();
  }

  private static String sourceChannel(PostEntryCommand command) {
    return command == null ? NOT_PARSED : command.provenance().sourceChannel().name();
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
