package dev.erst.fingrind.jazzer.tool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.CliFuzzFixtures;
import dev.erst.fingrind.cli.SqliteRoundTripWorkflowPersistenceAssertions;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.CommitRejected;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.Committed;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.PreflightAccepted;
import dev.erst.fingrind.contract.bookkeeping.PostingEffectiveDateBeforeBookStart;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.ApprovalDecision;
import dev.erst.fingrind.core.ApprovalId;
import dev.erst.fingrind.core.ApprovalReference;
import dev.erst.fingrind.core.ApprovalType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import dev.erst.fingrind.jazzer.support.JazzerPostEntryResultFixtures;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
                4,
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
            JazzerReplayShapeDetails.parsedPostingCommandDetails(command),
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
        JazzerReplayOutcomeSupport.rejectionStatus(new PostingRejection.BookNotInitialized()));
    assertEquals(
        PostingLifecycleStatus.UNKNOWN_ACCOUNT,
        JazzerReplayOutcomeSupport.rejectionStatus(
            new PostingRejection.AccountStateViolations(
                java.util.List.of(new PostingRejection.UnknownAccount(accountCode)))));
    assertEquals(
        PostingLifecycleStatus.INACTIVE_ACCOUNT,
        JazzerReplayOutcomeSupport.rejectionStatus(
            new PostingRejection.AccountStateViolations(
                java.util.List.of(new PostingRejection.InactiveAccount(accountCode)))));
    assertEquals(
        PostingLifecycleStatus.ACCOUNT_STATE_VIOLATIONS,
        JazzerReplayOutcomeSupport.rejectionStatus(
            new PostingRejection.AccountStateViolations(
                java.util.List.of(
                    new PostingRejection.UnknownAccount(accountCode),
                    new PostingRejection.InactiveAccount(accountCode)))));
    assertEquals(
        PostingLifecycleStatus.ENTRY_SEMANTICS_VIOLATIONS,
        JazzerReplayOutcomeSupport.rejectionStatus(
            new PostingRejection.EntrySemanticsViolations(
                java.util.List.of(
                    new PostingRejection.EntrySemanticsViolation(
                        "source-document-type-not-accepted",
                        "evidence.sourceDocuments[].sourceDocumentType",
                        "Cash revenue does not accept invoice evidence.")))));
    assertEquals(
        PostingLifecycleStatus.IDEMPOTENCY_KEY_CONFLICT,
        JazzerReplayOutcomeSupport.rejectionStatus(new PostingRejection.IdempotencyKeyConflict()));
    assertEquals(
        PostingLifecycleStatus.POSTING_EFFECTIVE_DATE_BEFORE_BOOK_START,
        JazzerReplayOutcomeSupport.rejectionStatus(
            new PostingEffectiveDateBeforeBookStart(
                java.time.LocalDate.parse("2026-06-29"), java.time.LocalDate.parse("2026-06-30"))));
    assertEquals(
        PostingLifecycleStatus.POSTING_EFFECTIVE_DATE_IN_FUTURE,
        JazzerReplayOutcomeSupport.rejectionStatus(
            new PostingRejection.PostingEffectiveDateInFuture(
                java.time.LocalDate.parse("2026-07-01"), java.time.LocalDate.parse("2026-06-30"))));
    assertEquals(
        PostingLifecycleStatus.BOOK_FUNCTIONAL_CURRENCY_MISMATCH,
        JazzerReplayOutcomeSupport.rejectionStatus(
            new PostingRejection.BookFunctionalCurrencyMismatch(
                dev.erst.fingrind.core.CurrencyUnit.of("USD"),
                dev.erst.fingrind.core.CurrencyUnit.of("EUR"))));
    assertEquals(
        PostingLifecycleStatus.CLOSED_PERIOD_VIOLATION,
        JazzerReplayOutcomeSupport.rejectionStatus(
            new PostingRejection.SweptInterimResultViolation(
                java.time.LocalDate.parse("2026-04-07"), java.time.LocalDate.parse("2026-04-08"))));
    assertEquals(
        PostingLifecycleStatus.OPEN_ACCOUNTING_POSITION_WINDOW_CLOSED,
        JazzerReplayOutcomeSupport.rejectionStatus(
            new PostingRejection.OpeningPositionWindowClosed(
                PostingKind.STANDARD, java.time.LocalDate.parse("2026-04-08"))));
    assertEquals(
        PostingLifecycleStatus.OPEN_ACCOUNTING_POSITION_TOUCHES_NOMINAL_ACCOUNT,
        JazzerReplayOutcomeSupport.rejectionStatus(
            new PostingRejection.OpeningPositionTouchesNominalAccount(
                accountCode, dev.erst.fingrind.core.AccountType.REVENUE)));
    assertEquals(
        PostingLifecycleStatus.RESERVED_RESULT_CLASSIFICATION,
        JazzerReplayOutcomeSupport.rejectionStatus(
            new PostingRejection.ReservedResultClassification(
                accountCode, FinancialPositionLineClassification.RESULT_HOLDING)));
    assertEquals(
        PostingLifecycleStatus.REVERSAL_TARGET_IS_REVERSAL,
        JazzerReplayOutcomeSupport.rejectionStatus(
            new dev.erst.fingrind.contract.bookkeeping.ReversalTargetIsReversal(
                new PostingId("3e0b1363-80c6-3fac-bcbd-d7655386483f"))));
    assertEquals(
        PostingLifecycleStatus.REVERSAL_ALREADY_EXISTS,
        JazzerReplayOutcomeSupport.rejectionStatus(
            new PostingRejection.ReversalAlreadyExists(
                new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"))));
    assertEquals(
        PostingLifecycleStatus.REVERSAL_DOES_NOT_NEGATE_TARGET,
        JazzerReplayOutcomeSupport.rejectionStatus(
            new PostingRejection.ReversalDoesNotNegateTarget(
                new PostingId("41a95cd2-4a5f-3ef3-8a33-c2771905f362"))));
    assertEquals(
        PostingLifecycleStatus.REVERSAL_TARGET_NOT_FOUND,
        JazzerReplayOutcomeSupport.rejectionStatus(
            new PostingRejection.ReversalTargetNotFound(
                new PostingId("6d857901-cb53-3986-a1d7-2f64319c76ce"))));

    PostEntryCommand command = parsedCommand();
    assertEquals(
        command.requestProvenance().idempotencyKey().value(),
        JazzerReplayShapeDetails.parsedPostingCommandDetails(command).idempotencyKey());
    assertEquals(
        RuntimeException.class.getSimpleName(),
        JazzerReplayOutcomeSupport.normalizedMessage(new RuntimeException()));
    ReplayOutcome.UnexpectedFailure unexpectedFailure =
        assertInstanceOf(
            ReplayOutcome.UnexpectedFailure.class,
            JazzerReplayOutcomeSupport.unexpectedFailure(
                dev.erst.fingrind.jazzer.support.JazzerHarness.cliRequest(),
                new IllegalStateException("boom"),
                new UnparsedCliRequestReplayDetails()));
    assertTrue(unexpectedFailure.stackTrace().contains("IllegalStateException"));

    assertThrows(
        IllegalStateException.class,
        () ->
            JazzerReplayOutcomeSupport.requiredPreflightRejected(
                new PreflightAccepted(
                    command.requestProvenance().idempotencyKey(),
                    CliFuzzFixtures.journalEntry(command).effectiveDate(),
                    JazzerPostEntryResultFixtures.resolvedJournal(command))));
    assertThrows(
        IllegalStateException.class,
        () ->
            JazzerReplayOutcomeSupport.requiredCommitRejected(
                new Committed(
                    new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
                    command.requestProvenance().idempotencyKey(),
                    CliFuzzFixtures.journalEntry(command).effectiveDate(),
                    CliFuzzFixtures.fixedClock().instant(),
                    false,
                    JazzerPostEntryResultFixtures.resolvedJournal(command))));
  }

  @Test
  void sqliteRoundTripReplayVerifier_rejects_reloaded_posting_id_drift() {
    PostEntryCommand command = parsedCommand();

    IllegalStateException mismatch =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteRoundTripWorkflowPersistenceAssertions.verifyReloadedPosting(
                    postingFact(command, "posting-2"), committed(command, "posting-1"), command));

    assertTrue(String.valueOf(mismatch.getMessage()).contains("posting id differs"));
  }

  @Test
  void sqliteRoundTripReplayVerifier_enforces_roundTrip_invariants() {
    PostEntryCommand command = parsedCommand();
    PostingFact postingFact = postingFact(command, "posting-1");
    PostEntryCommand reversalCommand = reversalCommand();
    Committed replayedCommit = committed(command, "posting-1", true);
    CommitRejected duplicateRejected =
        new CommitRejected(
            command.requestProvenance().idempotencyKey(),
            new PostingRejection.IdempotencyKeyConflict());

    SqliteRoundTripWorkflowPersistenceAssertions.verifyDeclaredAccountListing(
        java.util.List.of(
            declaredAccount(new AccountCode("cash"), true),
            declaredAccount(new AccountCode("1000"), true),
            declaredAccount(new AccountCode("2000"), true)),
        java.util.List.of(
            declaredAccount(new AccountCode("1000"), true),
            declaredAccount(new AccountCode("2000"), true)));
    assertEquals(
        postingFact,
        SqliteRoundTripWorkflowPersistenceAssertions.requireStoredPosting(
            java.util.Optional.of(postingFact)));
    SqliteRoundTripWorkflowPersistenceAssertions.verifyReloadedPosting(
        postingFact, committed(command, "posting-1"), command);
    assertEquals(
        PostingLifecycleStatus.IDEMPOTENT_REPLAY,
        SqliteRoundTripWorkflowPersistenceAssertions.requireIdempotentReplay(
            replayedCommit, committed(command, "posting-1")));
    assertEquals(
        PostingLifecycleStatus.IDEMPOTENCY_KEY_CONFLICT,
        SqliteRoundTripWorkflowPersistenceAssertions.verifyRejectedCommitConsistency(
            duplicateRejected, duplicateRejected));

    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowPersistenceAssertions.verifyDeclaredAccountListing(
                java.util.List.of(declaredAccount(new AccountCode("1000"), true)),
                java.util.List.of(
                    declaredAccount(new AccountCode("1000"), true),
                    declaredAccount(new AccountCode("2000"), true))));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowPersistenceAssertions.requireStoredPosting(
                java.util.Optional.empty()));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowPersistenceAssertions.verifyReloadedPosting(
                new PostingFact(
                    new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
                    CliFuzzFixtures.journalEntry(command),
                    PostingLineage.direct(),
                    PostingKind.STANDARD,
                    dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
                    command.evidence(),
                    new CommittedProvenance(
                        command.requestProvenance(),
                        CliFuzzFixtures.fixedClock().instant(),
                        command.sourceChannel())),
                committed(command, "posting-1"),
                reversalCommand));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowPersistenceAssertions.verifyReloadedPosting(
                postingFact(
                    "posting-1",
                    CliFuzzFixtures.journalEntry(command),
                    CliFuzzFixtures.postingLineage(reversalCommand),
                    command.entry().postingOriginKind(),
                    command.evidence(),
                    command.requestProvenance(),
                    CliFuzzFixtures.fixedClock().instant(),
                    command.sourceChannel()),
                committed(command, "posting-1"),
                command));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowPersistenceAssertions.verifyReloadedPosting(
                postingFact(
                    "posting-1",
                    CliFuzzFixtures.journalEntry(command),
                    CliFuzzFixtures.postingLineage(command),
                    command.entry().postingOriginKind(),
                    mismatchedEvidence(),
                    command.requestProvenance(),
                    CliFuzzFixtures.fixedClock().instant(),
                    command.sourceChannel()),
                committed(command, "posting-1"),
                command));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowPersistenceAssertions.verifyReloadedPosting(
                postingFact(
                    "posting-1",
                    CliFuzzFixtures.journalEntry(command),
                    CliFuzzFixtures.postingLineage(command),
                    command.entry().postingOriginKind(),
                    command.evidence(),
                    reversalCommand.requestProvenance(),
                    CliFuzzFixtures.fixedClock().instant(),
                    command.sourceChannel()),
                committed(command, "posting-1"),
                command));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowPersistenceAssertions.verifyReloadedPosting(
                postingFact(
                    "posting-1",
                    CliFuzzFixtures.journalEntry(command),
                    CliFuzzFixtures.postingLineage(command),
                    command.entry().postingOriginKind(),
                    command.evidence(),
                    command.requestProvenance(),
                    CliFuzzFixtures.fixedClock().instant().plusSeconds(1),
                    command.sourceChannel()),
                committed(command, "posting-1"),
                command));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowPersistenceAssertions.requireIdempotentReplay(
                committed(command, "posting-1"), committed(command, "posting-1")));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowPersistenceAssertions.requireIdempotentReplay(
                new CommitRejected(
                    command.requestProvenance().idempotencyKey(),
                    new PostingRejection.BookNotInitialized()),
                committed(command, "posting-1")));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowPersistenceAssertions.verifyRejectedCommitConsistency(
                duplicateRejected, committed(command, "posting-1")));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowPersistenceAssertions.verifyRejectedCommitConsistency(
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
            " minimal replay example ",
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
    assertTrue(PostingLifecycleStatus.wireValues().contains("idempotency-key-conflict"));
    assertTrue(PostingLifecycleStatus.wireValues().contains("idempotent-replay"));
    assertTrue(PostingLifecycleStatus.wireValues().contains("closed-period-violation"));
    assertTrue(PostingLifecycleStatus.wireValues().contains("entry-semantics-violations"));
    assertTrue(
        PostingLifecycleStatus.wireValues().contains("posting-effective-date-before-book-start"));
    assertTrue(PostingLifecycleStatus.wireValues().contains("posting-effective-date-in-future"));
    assertTrue(
        PostingLifecycleStatus.wireValues().contains("open-accounting-position-window-closed"));
    assertTrue(PostingLifecycleStatus.wireValues().contains("reserved-result-classification"));
    assertTrue(PostingLifecycleStatus.wireValues().contains("reversal-target-is-reversal"));
    assertEquals(
        PostingLifecycleStatus.IDEMPOTENCY_KEY_CONFLICT,
        PostingLifecycleStatus.fromWireValue("idempotency-key-conflict"));
    assertEquals(
        PostingLifecycleStatus.IDEMPOTENT_REPLAY,
        PostingLifecycleStatus.fromWireValue("idempotent-replay"));
    assertEquals(
        PostingLifecycleStatus.CLOSED_PERIOD_VIOLATION,
        PostingLifecycleStatus.fromWireValue("closed-period-violation"));
    assertEquals(
        PostingLifecycleStatus.ENTRY_SEMANTICS_VIOLATIONS,
        PostingLifecycleStatus.fromWireValue("entry-semantics-violations"));
    assertEquals(
        PostingLifecycleStatus.POSTING_EFFECTIVE_DATE_BEFORE_BOOK_START,
        PostingLifecycleStatus.fromWireValue("posting-effective-date-before-book-start"));
    assertEquals(
        PostingLifecycleStatus.POSTING_EFFECTIVE_DATE_IN_FUTURE,
        PostingLifecycleStatus.fromWireValue("posting-effective-date-in-future"));
    assertEquals(
        PostingLifecycleStatus.OPEN_ACCOUNTING_POSITION_WINDOW_CLOSED,
        PostingLifecycleStatus.fromWireValue("open-accounting-position-window-closed"));
    assertEquals(
        PostingLifecycleStatus.RESERVED_RESULT_CLASSIFICATION,
        PostingLifecycleStatus.fromWireValue("reserved-result-classification"));
    assertEquals(
        PostingLifecycleStatus.REVERSAL_TARGET_IS_REVERSAL,
        PostingLifecycleStatus.fromWireValue("reversal-target-is-reversal"));
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
                "cli-request",
                Path.of("/tmp/absolute.json").toString(),
                metadata.coverageIntent(),
                metadata.expectation()));
    assertThrows(
        NullPointerException.class,
        () -> new ParsedLedgerPlanShapeReplayDetails(nullLedgerPlanShapeDetails()));
    assertFalse(
        new ParsedPostingCommandDetails("2026-04-07", "idem-1", 2, false).reversalPresent());
  }

  @Test
  void postingWorkflowReplay_unknownAccountPreDeclarationClassifier_acceptsOnlyPureUnknowns()
      throws ReflectiveOperationException {
    var helper =
        JazzerPostingWorkflowReplay.class.getDeclaredMethod(
            "isUnknownAccountPreDeclarationState", PostingRejection.class);
    helper.setAccessible(true);
    PostingRejection unknownOnly =
        new PostingRejection.AccountStateViolations(
            List.of(new PostingRejection.UnknownAccount(new AccountCode("1000"))));
    PostingRejection mixed =
        new PostingRejection.AccountStateViolations(
            List.of(
                new PostingRejection.UnknownAccount(new AccountCode("1000")),
                new PostingRejection.InactiveAccount(new AccountCode("2000"))));

    assertTrue((boolean) helper.invoke(null, unknownOnly));
    assertFalse((boolean) helper.invoke(null, mixed));
    assertFalse((boolean) helper.invoke(null, new PostingRejection.BookNotInitialized()));
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
        CliFuzzFixtures.journalEntry(command),
        CliFuzzFixtures.postingLineage(command),
        command.entry().postingOriginKind(),
        command.evidence(),
        command.requestProvenance(),
        CliFuzzFixtures.fixedClock().instant(),
        command.sourceChannel());
  }

  private static PostingFact postingFact(
      String postingId,
      dev.erst.fingrind.core.JournalEntry journalEntry,
      PostingLineage postingLineage,
      dev.erst.fingrind.core.PostingOriginKind postingOriginKind,
      dev.erst.fingrind.core.AccountingEvidence evidence,
      dev.erst.fingrind.core.RequestProvenance requestProvenance,
      java.time.Instant recordedAt,
      dev.erst.fingrind.core.SourceChannel sourceChannel) {
    return new PostingFact(
        new PostingId(
            java.util
                .UUID
                .nameUUIDFromBytes(
                    ("fingrind-test-postingid:" + postingId)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString()),
        journalEntry,
        postingLineage,
        PostingKind.STANDARD,
        postingLineage.reversalReference().isPresent()
            ? dev.erst.fingrind.core.PostingOriginKind.REVERSAL
            : postingOriginKind,
        evidence,
        new CommittedProvenance(requestProvenance, recordedAt, sourceChannel));
  }

  private static Committed committed(PostEntryCommand command, String postingId) {
    return committed(command, postingId, false);
  }

  private static Committed committed(
      PostEntryCommand command, String postingId, boolean idempotentReplay) {
    return JazzerPostEntryResultFixtures.committed(command, postingId, idempotentReplay);
  }

  private static AccountingEvidence mismatchedEvidence() {
    return new AccountingEvidence(
        List.of(
            new SourceDocumentReference(
                new SourceDocumentId("document-evidence-mismatch"),
                new SourceDocumentType("cash-receipt"),
                LocalDate.parse("2026-04-07"))),
        List.of(
            new ApprovalReference(
                new ApprovalId("approval-evidence-mismatch"),
                new ApprovalType("manager-signoff"),
                "agent-evidence-mismatch",
                "AGENT",
                ApprovalDecision.REJECTED,
                Instant.parse("2026-04-07T13:00:00Z"))));
  }

  private static DeclaredAccount declaredAccount(AccountCode accountCode, boolean active) {
    return new DeclaredAccount(
        accountCode,
        new AccountName("Synthetic " + accountCode.value()),
        AccountType.ASSET,
        new AccountTaxonomy(
            dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
            Optional.empty(),
            Optional.empty(),
            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
            Optional.empty(),
            Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT)),
        active,
        CliFuzzFixtures.fixedClock().instant());
  }

  @SuppressWarnings("NullAway")
  private static LedgerPlanShapeDetails nullLedgerPlanShapeDetails() {
    return null;
  }
}
