package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.jazzer.support.PostingLifecycleStatusMapper;
import dev.erst.fingrind.jazzer.tool.PostingLifecycleStatus;
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
            SqliteRoundTripWorkflowPersistenceAssertions.requireDuplicateRejection(
                SqliteRoundTripWorkflowTestSupport.commitRejected(
                    new PostingRejection.ReversalTargetNotFound(new PostingId("posting-9")))));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowPersistenceAssertions.verifyRejectedCommitConsistency(
                SqliteRoundTripWorkflowTestSupport.commitRejected(
                    new PostingRejection.DuplicateIdempotencyKey()),
                SqliteRoundTripWorkflowTestSupport.commitRejected(
                    new PostingRejection.ReversalTargetNotFound(new PostingId("posting-7")))));
  }

  @Test
  void reload_verification_covers_field_mismatch_paths() {
    PostEntryCommand command = SqliteRoundTripWorkflowTestSupport.basicValidCommand();
    PostingFact baseFact =
        SqliteRoundTripWorkflowTestSupport.matchingPostingFact(command, new PostingId("posting-1"));
    var committed = SqliteRoundTripWorkflowTestSupport.committed("posting-1");

    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowPersistenceAssertions.verifyReloadedPosting(
                SqliteRoundTripWorkflowTestSupport.matchingPostingFact(
                    command, new PostingId("posting-2")),
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
                    dev.erst.fingrind.core.PostingOriginKind.REVERSAL_ADJUSTMENT,
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
                        new ReversalReference(new PostingId("posting-0")),
                        new ReversalReason("unexpected reversal")),
                    PostingKind.STANDARD,
                    dev.erst.fingrind.core.PostingOriginKind.REVERSAL_ADJUSTMENT,
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
                    dev.erst.fingrind.core.PostingOriginKind.REVERSAL_ADJUSTMENT,
                    new dev.erst.fingrind.core.AccountingEvidence(
                        java.util.List.of(
                            new dev.erst.fingrind.core.SourceDocumentReference(
                                new dev.erst.fingrind.core.SourceDocumentId("document-idem-2"),
                                new dev.erst.fingrind.core.SourceDocumentType("cash-receipt"),
                                java.time.LocalDate.parse("2026-04-07"),
                                java.time.Instant.parse("2026-04-07T12:00:00Z"),
                                new dev.erst.fingrind.core.StorageLocator(
                                    "s3://evidence/document-idem-2.pdf"),
                                new dev.erst.fingrind.core.ContentSha256(
                                    "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"))),
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
                    dev.erst.fingrind.core.PostingOriginKind.REVERSAL_ADJUSTMENT,
                    baseFact.evidence(),
                    new CommittedProvenance(
                        new RequestProvenance(
                            new ActorId("actor-2"),
                            ActorType.AGENT,
                            new CommandId("command-2"),
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
                    dev.erst.fingrind.core.PostingOriginKind.REVERSAL_ADJUSTMENT,
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
        PostingLifecycleStatus.DUPLICATE_IDEMPOTENCY_KEY,
        PostingLifecycleStatusMapper.forRejection(new PostingRejection.DuplicateIdempotencyKey()));
    assertEquals(
        PostingLifecycleStatus.BOOK_FUNCTIONAL_CURRENCY_MISMATCH,
        PostingLifecycleStatusMapper.forRejection(
            new PostingRejection.BookFunctionalCurrencyMismatch(
                dev.erst.fingrind.core.CurrencyUnit.of("USD"),
                dev.erst.fingrind.core.CurrencyUnit.of("EUR"))));
    assertEquals(
        PostingLifecycleStatus.CLOSED_PERIOD_VIOLATION,
        PostingLifecycleStatusMapper.forRejection(
            new PostingRejection.TransferredPeriodResultViolation(
                java.time.LocalDate.parse("2026-04-07"), java.time.LocalDate.parse("2026-04-08"))));
    assertEquals(
        PostingLifecycleStatus.OPEN_ACCOUNTING_POSITION_WINDOW_CLOSED,
        PostingLifecycleStatusMapper.forRejection(
            new PostingRejection.OpenAccountingPositionWindowClosed(
                dev.erst.fingrind.core.PostingKind.STANDARD,
                java.time.LocalDate.parse("2026-04-08"))));
    assertEquals(
        PostingLifecycleStatus.OPEN_ACCOUNTING_POSITION_TOUCHES_NOMINAL_ACCOUNT,
        PostingLifecycleStatusMapper.forRejection(
            new PostingRejection.OpenAccountingPositionTouchesNominalAccount(
                new AccountCode("4100"), dev.erst.fingrind.core.AccountType.REVENUE)));
    assertEquals(
        PostingLifecycleStatus.RESULT_HOLDING_ACCOUNT_RESERVED,
        PostingLifecycleStatusMapper.forRejection(
            new PostingRejection.ResultHoldingAccountReserved(new AccountCode("3200"))));
    assertEquals(
        PostingLifecycleStatus.REVERSAL_TARGET_NOT_FOUND,
        PostingLifecycleStatusMapper.forRejection(
            new PostingRejection.ReversalTargetNotFound(new PostingId("posting-1"))));
    assertEquals(
        PostingLifecycleStatus.REVERSAL_ALREADY_EXISTS,
        PostingLifecycleStatusMapper.forRejection(
            new PostingRejection.ReversalAlreadyExists(new PostingId("posting-1"))));
    assertEquals(
        PostingLifecycleStatus.REVERSAL_DOES_NOT_NEGATE_TARGET,
        PostingLifecycleStatusMapper.forRejection(
            new PostingRejection.ReversalDoesNotNegateTarget(new PostingId("posting-1"))));
  }

  @Test
  void duplicate_and_reversal_specific_assertions_cover_wrong_shapes() {
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowDecisionAssertions.assertDuplicateWorkflowPreflightRejected(
                new dev.erst.fingrind.contract.bookkeeping.PostEntryResult.PreflightAccepted(
                    new IdempotencyKey("idem-1"), java.time.LocalDate.parse("2026-04-07"))));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowDecisionAssertions.assertDuplicateWorkflowPreflightRejected(
                new dev.erst.fingrind.contract.bookkeeping.PostEntryResult.PreflightRejected(
                    new IdempotencyKey("idem-1"),
                    new PostingRejection.ReversalTargetNotFound(new PostingId("posting-1")))));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowDecisionAssertions.assertDuplicateWorkflowCommitRejected(
                SqliteRoundTripWorkflowTestSupport.committed("posting-1")));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowDecisionAssertions.assertDuplicateWorkflowCommitRejected(
                SqliteRoundTripWorkflowTestSupport.commitRejected(
                    new PostingRejection.ReversalTargetNotFound(new PostingId("posting-1")))));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowDecisionAssertions.assertNearMissReversalRejected(
                SqliteRoundTripWorkflowTestSupport.commitRejected(
                    new PostingRejection.DuplicateIdempotencyKey())));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowDecisionAssertions.assertDuplicateReversalRejected(
                SqliteRoundTripWorkflowTestSupport.commitRejected(
                    new PostingRejection.DuplicateIdempotencyKey())));
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
                        new PostingId("posting-1")))));

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
