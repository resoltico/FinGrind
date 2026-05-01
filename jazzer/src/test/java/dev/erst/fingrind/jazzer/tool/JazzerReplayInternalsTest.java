package dev.erst.fingrind.jazzer.tool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.CliFuzzFixtures;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PostEntryResult.CommitRejected;
import dev.erst.fingrind.contract.PostEntryResult.Committed;
import dev.erst.fingrind.contract.PostEntryResult.PreflightAccepted;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingLineage;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.PostingId;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Covers replay helpers, invariant verifiers, and deterministic Jazzer model seams. */
class JazzerReplayInternalsTest {
  @Test
  void requestReplay_returnsUnexpectedFailuresThroughInjectedSeams() {
    byte[] input = JazzerReplayRequestFixtures.basicValidRequest().getBytes(UTF_8);
    LedgerPlan parsedPlan =
        CliFuzzFixtures.readLedgerPlan(
            JazzerReplayLedgerPlanFixtures.basicValidLedgerPlan().getBytes(UTF_8));

    ReplayOutcome cliUnexpected =
        JazzerRequestReplay.replayCliRequest(
            input,
            ignored -> {
              throw new IllegalStateException("cli boom");
            });
    ReplayOutcome ledgerUnexpected =
        JazzerRequestReplay.replayLedgerPlanRequest(
            input,
            ignored -> parsedPlan,
            (plan, rawInput) -> {
              throw new IllegalStateException("ledger boom");
            });
    ReplayOutcome ledgerParserUnexpected =
        JazzerRequestReplay.replayLedgerPlanRequest(
            input,
            ignored -> {
              throw new IllegalStateException("ledger parse boom");
            },
            (plan, rawInput) -> {
              throw new AssertionError("executor must not run when parsing already failed");
            });

    ReplayOutcome.UnexpectedFailure cliFailure =
        assertInstanceOf(ReplayOutcome.UnexpectedFailure.class, cliUnexpected);
    assertEquals(new UnparsedCliRequestReplayDetails(), cliFailure.details());
    assertEquals("cli boom", cliFailure.message());
    ReplayOutcome.UnexpectedFailure ledgerFailure =
        assertInstanceOf(ReplayOutcome.UnexpectedFailure.class, ledgerUnexpected);
    assertEquals(
        new ParsedLedgerPlanShapeReplayDetails(
            new LedgerPlanShapeDetails(
                "plan-1",
                5,
                parsedPlan.steps().getFirst().kind(),
                parsedPlan.steps().getLast().kind(),
                1,
                true)),
        ledgerFailure.details());
    assertEquals("ledger boom", ledgerFailure.message());
    assertEquals(
        new UnparsedLedgerPlanReplayDetails(),
        assertInstanceOf(ReplayOutcome.UnexpectedFailure.class, ledgerParserUnexpected).details());
  }

  @Test
  void workflow_replays_returnUnexpectedFailuresForInjectedParserAndExerciseFaults() {
    byte[] input = JazzerReplayRequestFixtures.basicValidRequest().getBytes(UTF_8);
    PostEntryCommand command = parsedCommand();

    ReplayOutcome parserFailure =
        JazzerPostingWorkflowReplay.replay(
            input,
            ignored -> {
              throw new IllegalStateException("parse boom");
            },
            (parsedCommand, rawInput, state) -> {});
    ReplayOutcome exerciseFailure =
        JazzerPostingWorkflowReplay.replay(
            input,
            ignored -> command,
            (parsedCommand, rawInput, state) -> {
              throw new IllegalStateException("exercise boom");
            });

    ReplayOutcome.UnexpectedFailure unexpectedParserFailure =
        assertInstanceOf(ReplayOutcome.UnexpectedFailure.class, parserFailure);
    assertEquals(new UnparsedPostingWorkflowReplayDetails(), unexpectedParserFailure.details());
    ReplayOutcome.UnexpectedFailure unexpectedExerciseFailure =
        assertInstanceOf(ReplayOutcome.UnexpectedFailure.class, exerciseFailure);
    assertEquals(
        new PostingWorkflowReplayDetails(
            JazzerReplayDetailsMapper.parsedPostingCommandDetails(command),
            new PostingWorkflowLifecycleDetails(
                new PostingGateDetails(
                    PostingLifecycleStatus.NOT_RUN, PostingLifecycleStatus.NOT_RUN),
                new PostingGateDetails(
                    PostingLifecycleStatus.NOT_RUN, PostingLifecycleStatus.NOT_RUN),
                new PostingGateDetails(
                    PostingLifecycleStatus.NOT_RUN, PostingLifecycleStatus.NOT_RUN)),
            new PostingWorkflowOutcomeDetails(
                PostingLifecycleStatus.NOT_RUN,
                PostingLifecycleStatus.NOT_RUN,
                PostingLifecycleStatus.NOT_RUN,
                false)),
        unexpectedExerciseFailure.details());
  }

  @Test
  void sqliteRoundTripReplay_returnsUnexpectedFailuresForInjectedParserAndExerciseFaults() {
    byte[] input = JazzerReplayRequestFixtures.basicValidRequest().getBytes(UTF_8);
    PostEntryCommand command = parsedCommand();

    ReplayOutcome parserFailure =
        JazzerSqliteBookRoundTripReplay.replay(
            input,
            ignored -> {
              throw new IllegalStateException("parse boom");
            },
            (parsedCommand, rawInput, state) -> {});
    ReplayOutcome ioFailure =
        JazzerSqliteBookRoundTripReplay.replay(
            input,
            ignored -> command,
            (parsedCommand, rawInput, state) -> {
              throw new IOException("disk boom");
            });
    ReplayOutcome runtimeFailure =
        JazzerSqliteBookRoundTripReplay.replay(
            input,
            ignored -> command,
            (parsedCommand, rawInput, state) -> {
              throw new IllegalStateException("runtime boom");
            });

    assertEquals(
        new UnparsedSqliteBookRoundTripReplayDetails(),
        assertInstanceOf(ReplayOutcome.UnexpectedFailure.class, parserFailure).details());
    assertEquals(
        "disk boom", assertInstanceOf(ReplayOutcome.UnexpectedFailure.class, ioFailure).message());
    assertEquals(
        "runtime boom",
        assertInstanceOf(ReplayOutcome.UnexpectedFailure.class, runtimeFailure).message());
  }

  @Test
  void replayOutcome_default_message_and_kind_accessors_remain_typed() {
    ReplayOutcome success =
        new ReplayOutcome.Success("cli-request", new UnparsedCliRequestReplayDetails());
    ReplayOutcome.ExpectedInvalid expectedInvalid =
        new ReplayOutcome.ExpectedInvalid(
            "cli-request",
            "validation",
            "expected invalid input",
            new UnparsedCliRequestReplayDetails());
    ReplayOutcome.UnexpectedFailure unexpectedFailure =
        new ReplayOutcome.UnexpectedFailure(
            "cli-request",
            "IllegalStateException",
            "boom",
            "synthetic stack trace",
            new UnparsedCliRequestReplayDetails());

    assertEquals(ReplayOutcomeKind.SUCCESS, success.kind());
    assertEquals(ReplayOutcome.SUCCESS_MESSAGE, success.message());
    assertEquals(ReplayOutcomeKind.EXPECTED_INVALID, ((ReplayOutcome) expectedInvalid).kind());
    assertEquals("expected invalid input", ((ReplayOutcome) expectedInvalid).message());
    assertEquals(ReplayOutcomeKind.UNEXPECTED_FAILURE, ((ReplayOutcome) unexpectedFailure).kind());
    assertEquals("boom", ((ReplayOutcome) unexpectedFailure).message());
  }

  @Test
  void replayDetailsMapper_maps_rejections_and_rejects_drifted_result_shapes() {
    AccountCode accountCode = new AccountCode("1000");

    assertEquals(
        PostingLifecycleStatus.BOOK_NOT_INITIALIZED,
        JazzerReplayDetailsMapper.rejectionStatus(new PostingRejection.BookNotInitialized()));
    assertEquals(
        PostingLifecycleStatus.UNKNOWN_ACCOUNT,
        JazzerReplayDetailsMapper.rejectionStatus(
            new PostingRejection.AccountStateViolations(
                java.util.List.of(new PostingRejection.UnknownAccount(accountCode)))));
    assertEquals(
        PostingLifecycleStatus.INACTIVE_ACCOUNT,
        JazzerReplayDetailsMapper.rejectionStatus(
            new PostingRejection.AccountStateViolations(
                java.util.List.of(new PostingRejection.InactiveAccount(accountCode)))));
    assertEquals(
        PostingLifecycleStatus.ACCOUNT_STATE_VIOLATIONS,
        JazzerReplayDetailsMapper.rejectionStatus(
            new PostingRejection.AccountStateViolations(
                java.util.List.of(
                    new PostingRejection.UnknownAccount(accountCode),
                    new PostingRejection.InactiveAccount(accountCode)))));
    assertEquals(
        PostingLifecycleStatus.DUPLICATE_IDEMPOTENCY_KEY,
        JazzerReplayDetailsMapper.rejectionStatus(new PostingRejection.DuplicateIdempotencyKey()));
    assertEquals(
        PostingLifecycleStatus.REVERSAL_ALREADY_EXISTS,
        JazzerReplayDetailsMapper.rejectionStatus(
            new PostingRejection.ReversalAlreadyExists(new PostingId("posting-1"))));
    assertEquals(
        PostingLifecycleStatus.REVERSAL_DOES_NOT_NEGATE_TARGET,
        JazzerReplayDetailsMapper.rejectionStatus(
            new PostingRejection.ReversalDoesNotNegateTarget(new PostingId("posting-2"))));
    assertEquals(
        PostingLifecycleStatus.REVERSAL_TARGET_NOT_FOUND,
        JazzerReplayDetailsMapper.rejectionStatus(
            new PostingRejection.ReversalTargetNotFound(new PostingId("posting-3"))));

    PostEntryCommand command = parsedCommand();
    assertEquals(
        command.requestProvenance().idempotencyKey().value(),
        JazzerReplayDetailsMapper.parsedPostingCommandDetails(command).idempotencyKey());
    assertEquals(
        RuntimeException.class.getSimpleName(),
        JazzerReplayDetailsMapper.normalizedMessage(new RuntimeException()));
    ReplayOutcome.UnexpectedFailure unexpectedFailure =
        assertInstanceOf(
            ReplayOutcome.UnexpectedFailure.class,
            JazzerReplayDetailsMapper.unexpectedFailure(
                dev.erst.fingrind.jazzer.support.JazzerHarness.cliRequest(),
                new IllegalStateException("boom"),
                new UnparsedCliRequestReplayDetails()));
    assertTrue(unexpectedFailure.stackTrace().contains("IllegalStateException"));

    assertThrows(
        IllegalStateException.class,
        () ->
            JazzerReplayDetailsMapper.requiredPreflightRejected(
                new PreflightAccepted(
                    command.requestProvenance().idempotencyKey(),
                    command.journalEntry().effectiveDate())));
    assertThrows(
        IllegalStateException.class,
        () ->
            JazzerReplayDetailsMapper.requiredCommitRejected(
                new Committed(
                    new PostingId("posting-1"),
                    command.requestProvenance().idempotencyKey(),
                    command.journalEntry().effectiveDate(),
                    CliFuzzFixtures.fixedClock().instant())));
  }

  @Test
  void sqliteRoundTripReplayVerifier_rejects_reloaded_posting_id_drift() {
    PostEntryCommand command = parsedCommand();

    IllegalStateException mismatch =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteRoundTripReplayVerifier.verifyReloadedPosting(
                    postingFact(command, "posting-2"), committed(command, "posting-1"), command));

    assertTrue(String.valueOf(mismatch.getMessage()).contains("posting id differs"));
  }

  @Test
  void sqliteRoundTripReplayVerifier_enforces_roundTrip_invariants() {
    PostEntryCommand command = parsedCommand();
    PostingFact postingFact = postingFact(command, "posting-1");
    PostEntryCommand reversalCommand = reversalCommand();
    CommitRejected duplicateRejected =
        new CommitRejected(
            command.requestProvenance().idempotencyKey(),
            new PostingRejection.DuplicateIdempotencyKey());

    SqliteRoundTripReplayVerifier.verifyDeclaredAccountListing(2, 2);
    assertEquals(
        postingFact,
        SqliteRoundTripReplayVerifier.requireStoredPosting(java.util.Optional.of(postingFact)));
    SqliteRoundTripReplayVerifier.verifyReloadedPosting(
        postingFact, committed(command, "posting-1"), command);
    assertEquals(
        PostingLifecycleStatus.DUPLICATE_IDEMPOTENCY_KEY,
        SqliteRoundTripReplayVerifier.requireDuplicateRejection(duplicateRejected));
    assertEquals(
        PostingLifecycleStatus.DUPLICATE_IDEMPOTENCY_KEY,
        SqliteRoundTripReplayVerifier.verifyRejectedCommitConsistency(
            duplicateRejected, duplicateRejected));

    assertThrows(
        IllegalStateException.class,
        () -> SqliteRoundTripReplayVerifier.verifyDeclaredAccountListing(1, 2));
    assertThrows(
        IllegalStateException.class,
        () -> SqliteRoundTripReplayVerifier.requireStoredPosting(java.util.Optional.empty()));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripReplayVerifier.verifyReloadedPosting(
                new PostingFact(
                    new PostingId("posting-1"),
                    command.journalEntry(),
                    PostingLineage.direct(),
                    new CommittedProvenance(
                        command.requestProvenance(),
                        CliFuzzFixtures.fixedClock().instant(),
                        command.sourceChannel())),
                committed(command, "posting-1"),
                reversalCommand));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripReplayVerifier.verifyReloadedPosting(
                postingFact(
                    "posting-1",
                    command.journalEntry(),
                    reversalCommand.postingLineage(),
                    command.requestProvenance(),
                    CliFuzzFixtures.fixedClock().instant(),
                    command.sourceChannel()),
                committed(command, "posting-1"),
                command));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripReplayVerifier.verifyReloadedPosting(
                postingFact(
                    "posting-1",
                    command.journalEntry(),
                    command.postingLineage(),
                    reversalCommand.requestProvenance(),
                    CliFuzzFixtures.fixedClock().instant(),
                    command.sourceChannel()),
                committed(command, "posting-1"),
                command));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripReplayVerifier.verifyReloadedPosting(
                postingFact(
                    "posting-1",
                    command.journalEntry(),
                    command.postingLineage(),
                    command.requestProvenance(),
                    CliFuzzFixtures.fixedClock().instant().plusSeconds(1),
                    command.sourceChannel()),
                committed(command, "posting-1"),
                command));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripReplayVerifier.requireDuplicateRejection(
                committed(command, "posting-1")));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripReplayVerifier.requireDuplicateRejection(
                new CommitRejected(
                    command.requestProvenance().idempotencyKey(),
                    new PostingRejection.BookNotInitialized())));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripReplayVerifier.verifyRejectedCommitConsistency(
                duplicateRejected, committed(command, "posting-1")));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripReplayVerifier.verifyRejectedCommitConsistency(
                duplicateRejected,
                new CommitRejected(
                    command.requestProvenance().idempotencyKey(),
                    new PostingRejection.BookNotInitialized())));
  }

  @Test
  void replay_model_types_expose_wire_vocabularies_and_path_normalization() {
    CliRequestReplayDetails details =
        new CliRequestReplayDetails(
            new ParsedPostingCommandDetails("2026-04-07", "idem-1", 2, false),
            dev.erst.fingrind.core.ActorType.AGENT,
            dev.erst.fingrind.core.SourceChannel.CLI);

    FindingArtifact findingArtifact =
        new FindingArtifact(
            " cli-request ",
            " crash ",
            " crash-abcd ",
            " /tmp/crash-abcd ",
            ReplayFindingClassification.fromOutcome(
                new ReplayOutcome.ExpectedInvalid("cli-request", "Invalid", "boom", details)),
            " expected invalid ");
    RegressionSeedMetadata metadata =
        new RegressionSeedMetadata(
            " cli-request ",
            " src/fuzz/resources/../resources/example.json ",
            new ReplayExpectation(ReplayOutcomeKind.SUCCESS, " ok ", details));

    assertEquals("cli-request", findingArtifact.targetKey());
    assertEquals("expected-invalid", findingArtifact.replayClassification().wireValue());
    assertEquals(
        java.util.List.of("replay-clean", "expected-invalid", "unexpected-failure"),
        ReplayFindingClassification.wireValues());
    assertEquals(
        ReplayFindingClassification.REPLAY_CLEAN,
        ReplayFindingClassification.fromWireValue("replay-clean"));
    assertEquals(
        java.util.List.of("success", "expected-invalid", "unexpected-failure"),
        ReplayOutcomeKind.wireValues());
    assertEquals(ReplayOutcomeKind.SUCCESS, ReplayOutcomeKind.fromWireValue("success"));
    assertTrue(PostingLifecycleStatus.wireValues().contains("duplicate-idempotency-key"));
    assertEquals(
        PostingLifecycleStatus.DUPLICATE_IDEMPOTENCY_KEY,
        PostingLifecycleStatus.fromWireValue("duplicate-idempotency-key"));
    assertEquals(
        Path.of("/tmp/project/src/fuzz/resources/example.json"),
        metadata.inputPath(Path.of("/tmp/project")));
    assertEquals(ReplayOutcomeKind.SUCCESS, metadata.expectation().outcomeKind());
    assertEquals(
        ReplayOutcome.SUCCESS_MESSAGE, new ReplayOutcome.Success("cli-request", details).message());
    assertEquals(
        "expected-invalid",
        new ReplayOutcome.ExpectedInvalid("cli-request", "Invalid", "boom", details)
            .kind()
            .wireValue());
    assertEquals(
        ReplayOutcomeKind.UNEXPECTED_FAILURE,
        new ReplayOutcome.UnexpectedFailure("cli-request", "Bug", "boom", "stack", details).kind());
    assertEquals(
        "boom",
        new ReplayOutcome.UnexpectedFailure("cli-request", "Bug", "boom", "stack", details)
            .message());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RegressionSeedMetadata(
                "cli-request", Path.of("/tmp/absolute.json").toString(), metadata.expectation()));
    assertThrows(
        NullPointerException.class,
        () -> new ParsedLedgerPlanShapeReplayDetails(nullLedgerPlanShapeDetails()));
    assertFalse(
        new ParsedPostingCommandDetails("2026-04-07", "idem-1", 2, false).reversalPresent());
  }

  private static PostEntryCommand parsedCommand() {
    return CliFuzzFixtures.readPostEntryCommand(
        JazzerReplayRequestFixtures.basicValidRequest().getBytes(UTF_8));
  }

  private static PostEntryCommand reversalCommand() {
    return CliFuzzFixtures.readPostEntryCommand(
        JazzerReplayRequestFixtures.reversalTargetMissingRequest().getBytes(UTF_8));
  }

  private static PostingFact postingFact(PostEntryCommand command, String postingId) {
    return postingFact(
        postingId,
        command.journalEntry(),
        command.postingLineage(),
        command.requestProvenance(),
        CliFuzzFixtures.fixedClock().instant(),
        command.sourceChannel());
  }

  private static PostingFact postingFact(
      String postingId,
      dev.erst.fingrind.core.JournalEntry journalEntry,
      PostingLineage postingLineage,
      dev.erst.fingrind.core.RequestProvenance requestProvenance,
      java.time.Instant recordedAt,
      dev.erst.fingrind.core.SourceChannel sourceChannel) {
    return new PostingFact(
        new PostingId(postingId),
        journalEntry,
        postingLineage,
        new CommittedProvenance(requestProvenance, recordedAt, sourceChannel));
  }

  private static Committed committed(PostEntryCommand command, String postingId) {
    return new Committed(
        new PostingId(postingId),
        command.requestProvenance().idempotencyKey(),
        command.journalEntry().effectiveDate(),
        CliFuzzFixtures.fixedClock().instant());
  }

  @SuppressWarnings("NullAway")
  private static LedgerPlanShapeDetails nullLedgerPlanShapeDetails() {
    return null;
  }
}
