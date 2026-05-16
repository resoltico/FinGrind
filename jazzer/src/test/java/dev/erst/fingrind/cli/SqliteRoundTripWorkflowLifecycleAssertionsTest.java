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
import dev.erst.fingrind.jazzer.tool.PostingLifecycleStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SqliteRoundTripWorkflowLifecycleAssertionsTest {
  @Test
  void storage_and_duplicate_helpers_cover_failure_guards() {
    assertThrows(
        IllegalStateException.class,
        () -> SqliteRoundTripWorkflowLifecycleAssertions.requireStoredPosting(Optional.empty()));
    assertThrows(
        IllegalStateException.class,
        () -> SqliteRoundTripWorkflowLifecycleAssertions.verifyDeclaredAccountListing(1, 2));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowLifecycleAssertions.requireDuplicateRejection(
                SqliteRoundTripWorkflowTestSupport.commitRejected(
                    new PostingRejection.ReversalTargetNotFound(new PostingId("posting-9")))));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowLifecycleAssertions.verifyRejectedCommitConsistency(
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
            SqliteRoundTripWorkflowLifecycleAssertions.verifyReloadedPosting(
                SqliteRoundTripWorkflowTestSupport.matchingPostingFact(
                    command, new PostingId("posting-2")),
                committed,
                command));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowLifecycleAssertions.verifyReloadedPosting(
                new PostingFact(
                    baseFact.postingId(),
                    new JournalEntry(
                        command.journalEntry().effectiveDate(),
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
                    baseFact.provenance()),
                committed,
                command));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowLifecycleAssertions.verifyReloadedPosting(
                new PostingFact(
                    baseFact.postingId(),
                    baseFact.journalEntry(),
                    PostingLineage.reversal(
                        new ReversalReference(new PostingId("posting-0")),
                        new ReversalReason("unexpected reversal")),
                    PostingKind.STANDARD,
                    baseFact.provenance()),
                committed,
                command));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowLifecycleAssertions.verifyReloadedPosting(
                new PostingFact(
                    baseFact.postingId(),
                    baseFact.journalEntry(),
                    baseFact.postingLineage(),
                    PostingKind.STANDARD,
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
            SqliteRoundTripWorkflowLifecycleAssertions.verifyReloadedPosting(
                new PostingFact(
                    baseFact.postingId(),
                    baseFact.journalEntry(),
                    baseFact.postingLineage(),
                    PostingKind.STANDARD,
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
        SqliteRoundTripWorkflowLifecycleAssertions.rejectionStatus(
            new PostingRejection.BookNotInitialized()));
    assertEquals(
        PostingLifecycleStatus.UNKNOWN_ACCOUNT,
        SqliteRoundTripWorkflowLifecycleAssertions.rejectionStatus(
            new PostingRejection.AccountStateViolations(
                List.of(new PostingRejection.UnknownAccount(new AccountCode("1000"))))));
    assertEquals(
        PostingLifecycleStatus.INACTIVE_ACCOUNT,
        SqliteRoundTripWorkflowLifecycleAssertions.rejectionStatus(
            new PostingRejection.AccountStateViolations(
                List.of(new PostingRejection.InactiveAccount(new AccountCode("1000"))))));
    assertEquals(
        PostingLifecycleStatus.ACCOUNT_STATE_VIOLATIONS,
        SqliteRoundTripWorkflowLifecycleAssertions.rejectionStatus(
            new PostingRejection.AccountStateViolations(
                List.of(
                    new PostingRejection.UnknownAccount(new AccountCode("1000")),
                    new PostingRejection.InactiveAccount(new AccountCode("2000"))))));
    assertEquals(
        PostingLifecycleStatus.DUPLICATE_IDEMPOTENCY_KEY,
        SqliteRoundTripWorkflowLifecycleAssertions.rejectionStatus(
            new PostingRejection.DuplicateIdempotencyKey()));
    assertEquals(
        PostingLifecycleStatus.POSTING_KIND_RESERVED,
        SqliteRoundTripWorkflowLifecycleAssertions.rejectionStatus(
            new PostingRejection.PostingKindReserved(
                dev.erst.fingrind.core.PostingKind.PERIOD_CLOSE)));
    assertEquals(
        PostingLifecycleStatus.BOOK_FUNCTIONAL_CURRENCY_MISMATCH,
        SqliteRoundTripWorkflowLifecycleAssertions.rejectionStatus(
            new PostingRejection.BookFunctionalCurrencyMismatch(
                dev.erst.fingrind.core.CurrencyUnit.of("USD"),
                dev.erst.fingrind.core.CurrencyUnit.of("EUR"))));
    assertEquals(
        PostingLifecycleStatus.CLOSED_PERIOD_VIOLATION,
        SqliteRoundTripWorkflowLifecycleAssertions.rejectionStatus(
            new PostingRejection.ClosedPeriodViolation(
                java.time.LocalDate.parse("2026-04-07"), java.time.LocalDate.parse("2026-04-08"))));
    assertEquals(
        PostingLifecycleStatus.OPENING_BALANCE_WINDOW_CLOSED,
        SqliteRoundTripWorkflowLifecycleAssertions.rejectionStatus(
            new PostingRejection.OpeningBalanceWindowClosed(
                dev.erst.fingrind.core.PostingKind.STANDARD,
                java.time.LocalDate.parse("2026-04-08"))));
    assertEquals(
        PostingLifecycleStatus.OPENING_BALANCE_TOUCHES_NOMINAL_ACCOUNT,
        SqliteRoundTripWorkflowLifecycleAssertions.rejectionStatus(
            new PostingRejection.OpeningBalanceTouchesNominalAccount(
                new AccountCode("4100"), dev.erst.fingrind.core.AccountType.REVENUE)));
    assertEquals(
        PostingLifecycleStatus.CLOSING_EQUITY_ACCOUNT_RESERVED,
        SqliteRoundTripWorkflowLifecycleAssertions.rejectionStatus(
            new PostingRejection.ClosingEquityAccountReserved(new AccountCode("3200"))));
    assertEquals(
        PostingLifecycleStatus.REVERSAL_TARGET_NOT_FOUND,
        SqliteRoundTripWorkflowLifecycleAssertions.rejectionStatus(
            new PostingRejection.ReversalTargetNotFound(new PostingId("posting-1"))));
    assertEquals(
        PostingLifecycleStatus.REVERSAL_ALREADY_EXISTS,
        SqliteRoundTripWorkflowLifecycleAssertions.rejectionStatus(
            new PostingRejection.ReversalAlreadyExists(new PostingId("posting-1"))));
    assertEquals(
        PostingLifecycleStatus.REVERSAL_DOES_NOT_NEGATE_TARGET,
        SqliteRoundTripWorkflowLifecycleAssertions.rejectionStatus(
            new PostingRejection.ReversalDoesNotNegateTarget(new PostingId("posting-1"))));
  }

  @Test
  void duplicate_and_reversal_specific_assertions_cover_wrong_shapes() {
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowLifecycleAssertions.assertDuplicateWorkflowPreflightRejected(
                new dev.erst.fingrind.contract.bookkeeping.PostEntryResult.PreflightAccepted(
                    new IdempotencyKey("idem-1"), java.time.LocalDate.parse("2026-04-07"))));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowLifecycleAssertions.assertDuplicateWorkflowPreflightRejected(
                new dev.erst.fingrind.contract.bookkeeping.PostEntryResult.PreflightRejected(
                    new IdempotencyKey("idem-1"),
                    new PostingRejection.ReversalTargetNotFound(new PostingId("posting-1")))));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowLifecycleAssertions.assertDuplicateWorkflowCommitRejected(
                SqliteRoundTripWorkflowTestSupport.committed("posting-1")));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowLifecycleAssertions.assertDuplicateWorkflowCommitRejected(
                SqliteRoundTripWorkflowTestSupport.commitRejected(
                    new PostingRejection.ReversalTargetNotFound(new PostingId("posting-1")))));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowLifecycleAssertions.assertNearMissReversalRejected(
                SqliteRoundTripWorkflowTestSupport.commitRejected(
                    new PostingRejection.DuplicateIdempotencyKey())));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowLifecycleAssertions.assertDuplicateReversalRejected(
                SqliteRoundTripWorkflowTestSupport.commitRejected(
                    new PostingRejection.DuplicateIdempotencyKey())));
  }

  @Test
  void account_reactivation_guard_rejects_persisted_inactive_state() {
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowLifecycleAssertions.assertRejectedStateDidNotPersistPosting(
                Optional.of(
                    SqliteRoundTripWorkflowTestSupport.matchingPostingFact(
                        SqliteRoundTripWorkflowTestSupport.basicValidCommand(),
                        new PostingId("posting-1")))));

    IllegalStateException inactive =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteRoundTripWorkflowLifecycleAssertions.assertAccountReactivationPersisted(
                    new SqliteRoundTripWorkflowTestSupport.StubSqliteBookSession(
                        Optional.of(
                            SqliteRoundTripWorkflowTestSupport.declaredAccount(
                                new AccountCode("1000"), false))),
                    new AccountCode("1000")));
    SqliteRoundTripWorkflowTestSupport.assertMessageContains(inactive, "did not persist");
  }
}
