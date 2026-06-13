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
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.ApprovalDecision;
import dev.erst.fingrind.core.ApprovalId;
import dev.erst.fingrind.core.ApprovalReference;
import dev.erst.fingrind.core.ApprovalType;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.ContentSha256;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import dev.erst.fingrind.core.StorageLocator;
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
        PostingLifecycleStatus.DUPLICATE_IDEMPOTENCY_KEY,
        JazzerReplayOutcomeSupport.rejectionStatus(new PostingRejection.DuplicateIdempotencyKey()));
    assertEquals(
        PostingLifecycleStatus.BOOK_FUNCTIONAL_CURRENCY_MISMATCH,
        JazzerReplayOutcomeSupport.rejectionStatus(
            new PostingRejection.BookFunctionalCurrencyMismatch(
                dev.erst.fingrind.core.CurrencyUnit.of("USD"),
                dev.erst.fingrind.core.CurrencyUnit.of("EUR"))));
    assertEquals(
        PostingLifecycleStatus.CLOSED_PERIOD_VIOLATION,
        JazzerReplayOutcomeSupport.rejectionStatus(
            new PostingRejection.TransferredPeriodResultViolation(
                java.time.LocalDate.parse("2026-04-07"), java.time.LocalDate.parse("2026-04-08"))));
    assertEquals(
        PostingLifecycleStatus.OPENING_BALANCE_WINDOW_CLOSED,
        JazzerReplayOutcomeSupport.rejectionStatus(
            new PostingRejection.OpeningBalanceWindowClosed(
                PostingKind.STANDARD, java.time.LocalDate.parse("2026-04-08"))));
    assertEquals(
        PostingLifecycleStatus.OPENING_BALANCE_TOUCHES_NOMINAL_ACCOUNT,
        JazzerReplayOutcomeSupport.rejectionStatus(
            new PostingRejection.OpeningBalanceTouchesNominalAccount(
                accountCode, dev.erst.fingrind.core.AccountType.REVENUE)));
    assertEquals(
        PostingLifecycleStatus.RESULT_HOLDING_ACCOUNT_RESERVED,
        JazzerReplayOutcomeSupport.rejectionStatus(
            new PostingRejection.ResultHoldingAccountReserved(accountCode)));
    assertEquals(
        PostingLifecycleStatus.REVERSAL_ALREADY_EXISTS,
        JazzerReplayOutcomeSupport.rejectionStatus(
            new PostingRejection.ReversalAlreadyExists(new PostingId("posting-1"))));
    assertEquals(
        PostingLifecycleStatus.REVERSAL_DOES_NOT_NEGATE_TARGET,
        JazzerReplayOutcomeSupport.rejectionStatus(
            new PostingRejection.ReversalDoesNotNegateTarget(new PostingId("posting-2"))));
    assertEquals(
        PostingLifecycleStatus.REVERSAL_TARGET_NOT_FOUND,
        JazzerReplayOutcomeSupport.rejectionStatus(
            new PostingRejection.ReversalTargetNotFound(new PostingId("posting-3"))));

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
                    CliFuzzFixtures.journalEntry(command).effectiveDate())));
    assertThrows(
        IllegalStateException.class,
        () ->
            JazzerReplayOutcomeSupport.requiredCommitRejected(
                new Committed(
                    new PostingId("posting-1"),
                    command.requestProvenance().idempotencyKey(),
                    CliFuzzFixtures.journalEntry(command).effectiveDate(),
                    CliFuzzFixtures.fixedClock().instant())));
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
    CommitRejected duplicateRejected =
        new CommitRejected(
            command.requestProvenance().idempotencyKey(),
            new PostingRejection.DuplicateIdempotencyKey());

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
        PostingLifecycleStatus.DUPLICATE_IDEMPOTENCY_KEY,
        SqliteRoundTripWorkflowPersistenceAssertions.requireDuplicateRejection(duplicateRejected));
    assertEquals(
        PostingLifecycleStatus.DUPLICATE_IDEMPOTENCY_KEY,
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
                    new PostingId("posting-1"),
                    CliFuzzFixtures.journalEntry(command),
                    PostingLineage.direct(),
                    PostingKind.STANDARD,
                    dev.erst.fingrind.core.PostingOriginKind.REVERSAL_ADJUSTMENT,
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
                    mismatchedEvidence(command),
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
                    command.evidence(),
                    command.requestProvenance(),
                    CliFuzzFixtures.fixedClock().instant().plusSeconds(1),
                    command.sourceChannel()),
                committed(command, "posting-1"),
                command));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowPersistenceAssertions.requireDuplicateRejection(
                committed(command, "posting-1")));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowPersistenceAssertions.requireDuplicateRejection(
                new CommitRejected(
                    command.requestProvenance().idempotencyKey(),
                    new PostingRejection.BookNotInitialized())));
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
    assertTrue(PostingLifecycleStatus.wireValues().contains("duplicate-idempotency-key"));
    assertTrue(PostingLifecycleStatus.wireValues().contains("closed-period-violation"));
    assertTrue(PostingLifecycleStatus.wireValues().contains("entry-semantics-violations"));
    assertTrue(PostingLifecycleStatus.wireValues().contains("opening-balance-window-closed"));
    assertTrue(PostingLifecycleStatus.wireValues().contains("result-holding-account-reserved"));
    assertEquals(
        PostingLifecycleStatus.DUPLICATE_IDEMPOTENCY_KEY,
        PostingLifecycleStatus.fromWireValue("duplicate-idempotency-key"));
    assertEquals(
        PostingLifecycleStatus.CLOSED_PERIOD_VIOLATION,
        PostingLifecycleStatus.fromWireValue("closed-period-violation"));
    assertEquals(
        PostingLifecycleStatus.ENTRY_SEMANTICS_VIOLATIONS,
        PostingLifecycleStatus.fromWireValue("entry-semantics-violations"));
    assertEquals(
        PostingLifecycleStatus.OPENING_BALANCE_WINDOW_CLOSED,
        PostingLifecycleStatus.fromWireValue("opening-balance-window-closed"));
    assertEquals(
        PostingLifecycleStatus.RESULT_HOLDING_ACCOUNT_RESERVED,
        PostingLifecycleStatus.fromWireValue("result-holding-account-reserved"));
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
        command.evidence(),
        command.requestProvenance(),
        CliFuzzFixtures.fixedClock().instant(),
        command.sourceChannel());
  }

  private static PostingFact postingFact(
      String postingId,
      dev.erst.fingrind.core.JournalEntry journalEntry,
      PostingLineage postingLineage,
      dev.erst.fingrind.core.AccountingEvidence evidence,
      dev.erst.fingrind.core.RequestProvenance requestProvenance,
      java.time.Instant recordedAt,
      dev.erst.fingrind.core.SourceChannel sourceChannel) {
    return new PostingFact(
        new PostingId(postingId),
        journalEntry,
        postingLineage,
        PostingKind.STANDARD,
        dev.erst.fingrind.core.PostingOriginKind.REVERSAL_ADJUSTMENT,
        evidence,
        new CommittedProvenance(requestProvenance, recordedAt, sourceChannel));
  }

  private static Committed committed(PostEntryCommand command, String postingId) {
    return new Committed(
        new PostingId(postingId),
        command.requestProvenance().idempotencyKey(),
        CliFuzzFixtures.journalEntry(command).effectiveDate(),
        CliFuzzFixtures.fixedClock().instant());
  }

  private static AccountingEvidence mismatchedEvidence(PostEntryCommand command) {
    return new AccountingEvidence(
        List.of(
            new SourceDocumentReference(
                new SourceDocumentId("document-evidence-mismatch"),
                new SourceDocumentType("cash-receipt"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T12:00:00Z"),
                new StorageLocator("s3://evidence/document-evidence-mismatch.pdf"),
                new ContentSha256(
                    "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"))),
        List.of(
            new ApprovalReference(
                new ApprovalId("approval-evidence-mismatch"),
                new ApprovalType("manager-signoff"),
                command.requestProvenance().actorId(),
                command.requestProvenance().actorType(),
                ApprovalDecision.REJECTED,
                Instant.parse("2026-04-07T13:00:00Z"))));
  }

  private static DeclaredAccount declaredAccount(AccountCode accountCode, boolean active) {
    return new DeclaredAccount(
        accountCode,
        new AccountName("Synthetic " + accountCode.value()),
        AccountType.ASSET,
        AccountRole.ORDINARY,
        new AccountTaxonomy(
            dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
            Optional.empty(),
            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
            Optional.empty()),
        active,
        CliFuzzFixtures.fixedClock().instant());
  }

  @SuppressWarnings("NullAway")
  private static LedgerPlanShapeDetails nullLedgerPlanShapeDetails() {
    return null;
  }
}
