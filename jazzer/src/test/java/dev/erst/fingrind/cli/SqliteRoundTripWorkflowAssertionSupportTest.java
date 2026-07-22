package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostingEffectiveDateBeforeBookStart;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.jazzer.support.JazzerPostEntryResultFixtures;
import dev.erst.fingrind.jazzer.support.PostingLifecycleStatusMapper;
import dev.erst.fingrind.jazzer.tool.PostingLifecycleStatus;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SqliteRoundTripWorkflowAssertionSupportTest {
  @Test
  void storage_and_duplicate_helpers_cover_failure_guards() {
    assertThrows(
        IllegalStateException.class,
        () -> SqliteRoundTripWorkflowPersistenceAssertions.requireStoredPosting(Optional.empty()));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowPersistenceAssertions.verifyDeclaredAccountListing(
                List.of(
                    SqliteRoundTripWorkflowTestSupport.declaredAccount(
                        new AccountCode("1000"), true)),
                List.of(
                    SqliteRoundTripWorkflowTestSupport.declaredAccount(
                        new AccountCode("1000"), true),
                    SqliteRoundTripWorkflowTestSupport.declaredAccount(
                        new AccountCode("2000"), true))));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowPersistenceAssertions.requireIdempotentReplay(
                SqliteRoundTripWorkflowTestSupport.commitRejected(
                    new PostingRejection.ReversalTargetNotFound(
                        new PostingId("7982b5de-2f28-355e-9911-9ca85b4f5a67"))),
                SqliteRoundTripWorkflowTestSupport.committed("posting-1")));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowPersistenceAssertions.verifyRejectedCommitConsistency(
                SqliteRoundTripWorkflowTestSupport.commitRejected(
                    new PostingRejection.IdempotencyKeyConflict()),
                SqliteRoundTripWorkflowTestSupport.commitRejected(
                    new PostingRejection.ReversalTargetNotFound(
                        new PostingId("bb61d564-0257-33c2-99c7-ace95bae05f6")))));
  }

  @Test
  void reload_verification_covers_field_mismatch_paths() {
    PostEntryCommand command = SqliteRoundTripWorkflowTestSupport.basicValidCommand();
    PostingFact baseFact =
        SqliteRoundTripWorkflowTestSupport.matchingPostingFact(
            command, JazzerPostEntryResultFixtures.fixturePostingId("posting-1"));
    var committed = SqliteRoundTripWorkflowTestSupport.committed("posting-1");

    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowPersistenceAssertions.verifyReloadedPosting(
                SqliteRoundTripWorkflowTestSupport.matchingPostingFact(
                    command, JazzerPostEntryResultFixtures.fixturePostingId("posting-2")),
                committed,
                command));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowPersistenceAssertions.verifyReloadedPosting(
                new PostingFact(
                    baseFact.postingId(),
                    new JournalEntry(
                        CliFuzzFixtures.journalEntry(command).effectiveDate(),
                        List.of(
                            new JournalLine(
                                new AccountCode("1000"),
                                JournalLine.EntrySide.DEBIT,
                                Money.parse("EUR", "11.00")),
                            new JournalLine(
                                new AccountCode("2000"),
                                JournalLine.EntrySide.CREDIT,
                                Money.parse("EUR", "11.00")))),
                    baseFact.postingLineage(),
                    PostingKind.STANDARD,
                    dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
                    baseFact.evidence(),
                    baseFact.provenance()),
                committed,
                command));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowPersistenceAssertions.verifyReloadedPosting(
                new PostingFact(
                    baseFact.postingId(),
                    baseFact.journalEntry(),
                    PostingLineage.reversal(
                        new ReversalReference(
                            new PostingId("e888fd00-a501-341d-9a6b-8d9059757d1b")),
                        new ReversalReason("unexpected reversal")),
                    PostingKind.STANDARD,
                    dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
                    baseFact.evidence(),
                    baseFact.provenance()),
                committed,
                command));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowPersistenceAssertions.verifyReloadedPosting(
                new PostingFact(
                    baseFact.postingId(),
                    baseFact.journalEntry(),
                    baseFact.postingLineage(),
                    PostingKind.STANDARD,
                    dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
                    new dev.erst.fingrind.core.AccountingEvidence(
                        java.util.List.of(
                            new dev.erst.fingrind.core.SourceDocumentReference(
                                new dev.erst.fingrind.core.SourceDocumentId("document-idem-2"),
                                new dev.erst.fingrind.core.SourceDocumentType("cash-receipt"),
                                java.time.LocalDate.parse("2026-04-07"))),
                        java.util.List.of()),
                    baseFact.provenance()),
                committed,
                command));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowPersistenceAssertions.verifyReloadedPosting(
                new PostingFact(
                    baseFact.postingId(),
                    baseFact.journalEntry(),
                    baseFact.postingLineage(),
                    PostingKind.STANDARD,
                    dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
                    baseFact.evidence(),
                    new CommittedProvenance(
                        new RequestProvenance(
                            new CommandId("01a7741f-8643-3942-80a3-c689bc5aa8f6"),
                            new IdempotencyKey("idem-2"),
                            new CausationId("cause-2"),
                            Optional.empty()),
                        baseFact.provenance().recordedAt(),
                        baseFact.provenance().sourceChannel())),
                committed,
                command));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowPersistenceAssertions.verifyReloadedPosting(
                new PostingFact(
                    baseFact.postingId(),
                    baseFact.journalEntry(),
                    baseFact.postingLineage(),
                    PostingKind.STANDARD,
                    dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
                    baseFact.evidence(),
                    new CommittedProvenance(
                        baseFact.provenance().requestProvenance(),
                        baseFact.provenance().recordedAt().plusSeconds(1),
                        baseFact.provenance().sourceChannel())),
                committed,
                command));
  }

  @Test
  void rejection_status_helpers_cover_every_rejection_family() {
    assertEquals(
        PostingLifecycleStatus.BOOK_NOT_INITIALIZED,
        PostingLifecycleStatusMapper.forRejection(new PostingRejection.BookNotInitialized()));
    assertEquals(
        PostingLifecycleStatus.UNKNOWN_ACCOUNT,
        PostingLifecycleStatusMapper.forRejection(
            new PostingRejection.AccountStateViolations(
                List.of(new PostingRejection.UnknownAccount(new AccountCode("1000"))))));
    assertEquals(
        PostingLifecycleStatus.INACTIVE_ACCOUNT,
        PostingLifecycleStatusMapper.forRejection(
            new PostingRejection.AccountStateViolations(
                List.of(new PostingRejection.InactiveAccount(new AccountCode("1000"))))));
    assertEquals(
        PostingLifecycleStatus.ACCOUNT_STATE_VIOLATIONS,
        PostingLifecycleStatusMapper.forRejection(
            new PostingRejection.AccountStateViolations(
                List.of(
                    new PostingRejection.UnknownAccount(new AccountCode("1000")),
                    new PostingRejection.InactiveAccount(new AccountCode("2000"))))));
    assertEquals(
        PostingLifecycleStatus.ENTRY_SEMANTICS_VIOLATIONS,
        PostingLifecycleStatusMapper.forRejection(
            new PostingRejection.EntrySemanticsViolations(
                List.of(
                    new PostingRejection.EntrySemanticsViolation(
                        "account-type-mismatch",
                        "entry.cashAccountCode",
                        "Cash revenue requires an asset account.")))));
    assertEquals(
        PostingLifecycleStatus.IDEMPOTENCY_KEY_CONFLICT,
        PostingLifecycleStatusMapper.forRejection(new PostingRejection.IdempotencyKeyConflict()));
    assertEquals(
        PostingLifecycleStatus.POSTING_EFFECTIVE_DATE_BEFORE_BOOK_START,
        PostingLifecycleStatusMapper.forRejection(
            new PostingEffectiveDateBeforeBookStart(
                java.time.LocalDate.parse("2026-06-29"), java.time.LocalDate.parse("2026-06-30"))));
    assertEquals(
        PostingLifecycleStatus.POSTING_EFFECTIVE_DATE_IN_FUTURE,
        PostingLifecycleStatusMapper.forRejection(
            new PostingRejection.PostingEffectiveDateInFuture(
                java.time.LocalDate.parse("2026-07-01"), java.time.LocalDate.parse("2026-06-30"))));
    assertEquals(
        PostingLifecycleStatus.BOOK_FUNCTIONAL_CURRENCY_MISMATCH,
        PostingLifecycleStatusMapper.forRejection(
            new PostingRejection.BookFunctionalCurrencyMismatch(
                dev.erst.fingrind.core.CurrencyUnit.of("USD"),
                dev.erst.fingrind.core.CurrencyUnit.of("EUR"))));
    assertEquals(
        PostingLifecycleStatus.CLOSED_PERIOD_VIOLATION,
        PostingLifecycleStatusMapper.forRejection(
            new PostingRejection.SweptInterimResultViolation(
                java.time.LocalDate.parse("2026-04-07"), java.time.LocalDate.parse("2026-04-08"))));
    assertEquals(
        PostingLifecycleStatus.OPEN_ACCOUNTING_POSITION_WINDOW_CLOSED,
        PostingLifecycleStatusMapper.forRejection(
            new PostingRejection.OpeningPositionWindowClosed(
                dev.erst.fingrind.core.PostingKind.STANDARD,
                java.time.LocalDate.parse("2026-04-08"))));
    assertEquals(
        PostingLifecycleStatus.OPEN_ACCOUNTING_POSITION_TOUCHES_NOMINAL_ACCOUNT,
        PostingLifecycleStatusMapper.forRejection(
            new PostingRejection.OpeningPositionTouchesNominalAccount(
                new AccountCode("4100"), dev.erst.fingrind.core.AccountType.REVENUE)));
    assertEquals(
        PostingLifecycleStatus.RESERVED_RESULT_CLASSIFICATION,
        PostingLifecycleStatusMapper.forRejection(
            new PostingRejection.ReservedResultClassification(
                new AccountCode("3200"), FinancialPositionLineClassification.RESULT_HOLDING)));
    assertEquals(
        PostingLifecycleStatus.REVERSAL_TARGET_NOT_FOUND,
        PostingLifecycleStatusMapper.forRejection(
            new PostingRejection.ReversalTargetNotFound(
                new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"))));
    assertEquals(
        PostingLifecycleStatus.REVERSAL_TARGET_IS_REVERSAL,
        PostingLifecycleStatusMapper.forRejection(
            new dev.erst.fingrind.contract.bookkeeping.ReversalTargetIsReversal(
                new PostingId("3e0b1363-80c6-3fac-bcbd-d7655386483f"))));
    assertEquals(
        PostingLifecycleStatus.REVERSAL_ALREADY_EXISTS,
        PostingLifecycleStatusMapper.forRejection(
            new PostingRejection.ReversalAlreadyExists(
                new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"))));
    assertEquals(
        PostingLifecycleStatus.REVERSAL_DOES_NOT_NEGATE_TARGET,
        PostingLifecycleStatusMapper.forRejection(
            new PostingRejection.ReversalDoesNotNegateTarget(
                new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"))));
  }

  @Test
  void rejection_status_helpers_reject_detail_dispatch_for_top_level_owned_families()
      throws Exception {
    Method detailedStatus =
        PostingLifecycleStatusMapper.class.getDeclaredMethod(
            "detailedStatus", PostingRejection.class);
    detailedStatus.setAccessible(true);

    InvocationTargetException exception =
        assertThrows(
            InvocationTargetException.class,
            () -> detailedStatus.invoke(null, new PostingRejection.BookNotInitialized()));
    SqliteRoundTripWorkflowTestSupport.assertMessageContains(
        java.util.Objects.requireNonNull(exception.getCause(), "expected reflective cause"),
        "owned elsewhere");
  }

  @Test
  void duplicate_and_reversal_specific_assertions_cover_wrong_shapes() {
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowDecisionAssertions.requireDuplicateWorkflowPreflightAccepted(
                new dev.erst.fingrind.contract.bookkeeping.PostEntryResult.PreflightRejected(
                    new IdempotencyKey("idem-1"),
                    new PostingRejection.ReversalTargetNotFound(
                        new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")))));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowDecisionAssertions.requireCommittedReplay(
                SqliteRoundTripWorkflowTestSupport.committed("posting-1"),
                SqliteRoundTripWorkflowTestSupport.committed("posting-1")));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowDecisionAssertions.requireCommittedReplay(
                SqliteRoundTripWorkflowTestSupport.commitRejected(
                    new PostingRejection.ReversalTargetNotFound(
                        new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"))),
                SqliteRoundTripWorkflowTestSupport.committed("posting-1")));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowDecisionAssertions.requireCommittedReplay(
                new dev.erst.fingrind.contract.bookkeeping.PostEntryResult.Committed(
                    new PostingId("41a95cd2-4a5f-3ef3-8a33-c2771905f362"),
                    new IdempotencyKey("idem-1"),
                    java.time.LocalDate.parse("2026-04-07"),
                    java.time.Instant.parse("2026-04-07T12:00:00Z"),
                    true,
                    JazzerPostEntryResultFixtures.resolvedJournal(
                        SqliteRoundTripWorkflowTestSupport.basicValidCommand())),
                SqliteRoundTripWorkflowTestSupport.committed("posting-1")));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowDecisionAssertions.requireCommittedReplay(
                new dev.erst.fingrind.contract.bookkeeping.PostEntryResult.Committed(
                    new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
                    new IdempotencyKey("idem-2"),
                    java.time.LocalDate.parse("2026-04-07"),
                    java.time.Instant.parse("2026-04-07T12:00:00Z"),
                    true,
                    JazzerPostEntryResultFixtures.resolvedJournal(
                        SqliteRoundTripWorkflowTestSupport.basicValidCommand())),
                SqliteRoundTripWorkflowTestSupport.committed("posting-1")));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowDecisionAssertions.requireCommittedReplay(
                new dev.erst.fingrind.contract.bookkeeping.PostEntryResult.Committed(
                    new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
                    new IdempotencyKey("idem-1"),
                    java.time.LocalDate.parse("2026-04-08"),
                    java.time.Instant.parse("2026-04-07T12:00:00Z"),
                    true,
                    JazzerPostEntryResultFixtures.resolvedJournal(
                        SqliteRoundTripWorkflowTestSupport.basicValidCommand())),
                SqliteRoundTripWorkflowTestSupport.committed("posting-1")));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowDecisionAssertions.requireCommittedReplay(
                new dev.erst.fingrind.contract.bookkeeping.PostEntryResult.Committed(
                    new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
                    new IdempotencyKey("idem-1"),
                    java.time.LocalDate.parse("2026-04-07"),
                    java.time.Instant.parse("2026-04-07T12:00:01Z"),
                    true,
                    JazzerPostEntryResultFixtures.resolvedJournal(
                        SqliteRoundTripWorkflowTestSupport.basicValidCommand())),
                SqliteRoundTripWorkflowTestSupport.committed("posting-1")));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowDecisionAssertions.assertNearMissReversalRejected(
                SqliteRoundTripWorkflowTestSupport.commitRejected(
                    new PostingRejection.IdempotencyKeyConflict())));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowDecisionAssertions.assertDuplicateReversalRejected(
                SqliteRoundTripWorkflowTestSupport.commitRejected(
                    new PostingRejection.IdempotencyKeyConflict())));
  }

  @Test
  void account_reactivation_guard_rejects_persisted_inactive_state() {
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowPersistenceAssertions.assertRejectedStateDidNotPersistPosting(
                Optional.of(
                    SqliteRoundTripWorkflowTestSupport.matchingPostingFact(
                        SqliteRoundTripWorkflowTestSupport.basicValidCommand(),
                        new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")))));

    IllegalStateException inactive =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteRoundTripWorkflowPersistenceAssertions.assertAccountReactivationPersisted(
                    new SqliteRoundTripWorkflowTestSupport.StubSqliteReadSession(
                        Optional.of(
                            SqliteRoundTripWorkflowTestSupport.declaredAccount(
                                new AccountCode("1000"), false))),
                    new AccountCode("1000")));
    SqliteRoundTripWorkflowTestSupport.assertMessageContains(inactive, "did not persist");
  }
}
