package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.PostingRequest;
import dev.erst.fingrind.contract.workflow.LedgerAssertion;
import dev.erst.fingrind.contract.workflow.LedgerExecutionJournal;
import dev.erst.fingrind.contract.workflow.LedgerJournalEntry;
import dev.erst.fingrind.contract.workflow.LedgerJournalStep;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import dev.erst.fingrind.contract.workflow.LedgerStepFailure;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for derived accessors across date-range, posting, and ledger-plan models. */
class ContractDerivedAccessorsTest extends ContractTestSupport {
  @Test
  void dateRangesLineagesPostingShapesAndPlanResultsExposeDerivedAccessors() {
    ReversalReference reversalReference = new ReversalReference(new PostingId("posting-1"));
    EffectiveDateRange unbounded = EffectiveDateRange.unbounded();
    EffectiveDateRange from = EffectiveDateRange.of(LocalDate.parse("2026-04-01"), null);
    EffectiveDateRange to = EffectiveDateRange.of(null, LocalDate.parse("2026-04-30"));
    EffectiveDateRange bounded =
        EffectiveDateRange.of(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30"));
    PostingLineage direct = PostingLineage.direct();
    ReversalReason reversalReason = new ReversalReason("operator reversal");
    PostingLineage reversal = PostingLineage.reversal(reversalReference, reversalReason);
    RequestProvenance requestProvenance =
        new RequestProvenance(
            new ActorId("actor-1"),
            ActorType.AGENT,
            new CommandId("command-1"),
            new IdempotencyKey("idem-1"),
            new CausationId("cause-1"),
            Optional.empty());
    dev.erst.fingrind.core.JournalEntry requestJournalEntry = journalEntry();
    PostEntryCommand command =
        new PostEntryCommand(
            PostingKind.STANDARD,
            requestJournalEntry,
            reversal,
            requestProvenance,
            SourceChannel.CLI);
    PostingFact postingFact =
        new PostingFact(
            new PostingId("posting-1"),
            requestJournalEntry,
            reversal,
            PostingKind.STANDARD,
            new CommittedProvenance(
                requestProvenance, Instant.parse("2026-04-07T10:15:30Z"), SourceChannel.CLI));
    PostingRequest postingRequest =
        new PostingRequest() {
          @Override
          public dev.erst.fingrind.core.JournalEntry journalEntry() {
            return requestJournalEntry;
          }

          @Override
          public PostingLineage postingLineage() {
            return reversal;
          }

          @Override
          public RequestProvenance requestProvenance() {
            return requestProvenance;
          }
        };
    LedgerAssertion.AccountBalanceEquals assertion =
        new LedgerAssertion.AccountBalanceEquals(
            new AccountCode("1000"), bounded, money("10.00"), BalanceSide.DEBIT);
    PostEntryResult.CommitRejected commitRejected =
        new PostEntryResult.CommitRejected(
            new IdempotencyKey("idem-1"), new PostingRejection.DuplicateIdempotencyKey());
    Instant startedAt = Instant.parse("2026-04-17T10:15:30Z");
    Instant finishedAt = Instant.parse("2026-04-17T10:15:31Z");
    LedgerPlanResult succeededResult =
        new LedgerPlanResult.Succeeded(
            planId("plan-succeeded"),
            new LedgerExecutionJournal(
                startedAt,
                finishedAt,
                List.of(
                    new LedgerJournalEntry.Succeeded(
                        stepId("open"),
                        LedgerJournalStep.standard(
                            dev.erst.fingrind.contract.protocol.LedgerStepKind.OPEN_BOOK),
                        startedAt,
                        finishedAt,
                        List.of()))));
    LedgerPlanResult rejectedResult =
        new LedgerPlanResult.Rejected(
            planId("plan-rejected"),
            new LedgerExecutionJournal(
                startedAt,
                finishedAt,
                List.of(
                    new LedgerJournalEntry.Rejected(
                        stepId("post"),
                        LedgerJournalStep.standard(
                            dev.erst.fingrind.contract.protocol.LedgerStepKind.POST_ENTRY),
                        startedAt,
                        finishedAt,
                        List.of(),
                        new LedgerStepFailure("rejected", "Rejected.", List.of())))));
    LedgerPlanResult assertionFailedResult =
        new LedgerPlanResult.AssertionFailed(
            planId("plan-assertion"),
            new LedgerExecutionJournal(
                startedAt,
                finishedAt,
                List.of(
                    new LedgerJournalEntry.AssertionFailed(
                        stepId("assert"),
                        LedgerJournalStep.assertion(
                            dev.erst.fingrind.contract.protocol.LedgerAssertionKind
                                .ACCOUNT_BALANCE_EQUALS),
                        startedAt,
                        finishedAt,
                        List.of(),
                        new LedgerStepFailure("assertion-failed", "Mismatch.", List.of())))));
    assertEquals(Optional.empty(), unbounded.effectiveDateFrom());
    assertTrue(unbounded.contains(LocalDate.parse("2026-04-15")));
    assertTrue(from.contains(LocalDate.parse("2026-04-15")));
    assertFalse(from.contains(LocalDate.parse("2026-03-31")));
    assertTrue(to.contains(LocalDate.parse("2026-04-15")));
    assertFalse(to.contains(LocalDate.parse("2026-05-01")));
    assertTrue(bounded.contains(LocalDate.parse("2026-04-15")));
    assertFalse(bounded.contains(LocalDate.parse("2026-05-01")));
    assertEquals(List.of("unbounded", "from", "to", "bounded"), EffectiveDateRange.variantNames());
    assertFalse(direct.isReversal());
    assertEquals(Optional.empty(), direct.reversalReference());
    assertEquals(Optional.empty(), direct.reversalReason());
    assertTrue(reversal.isReversal());
    assertEquals(Optional.of(reversalReference), reversal.reversalReference());
    assertEquals(Optional.of(reversalReason), reversal.reversalReason());
    assertEquals(Optional.of(reversalReference), command.reversalReference());
    assertEquals(Optional.of(reversalReason), command.reversalReason());
    assertEquals(Optional.of(reversalReference), ((PostingRequest) command).reversalReference());
    assertEquals(Optional.of(reversalReason), ((PostingRequest) command).reversalReason());
    assertEquals(Optional.of(reversalReference), postingRequest.reversalReference());
    assertEquals(Optional.of(reversalReason), postingRequest.reversalReason());
    assertEquals(Optional.of(reversalReference), postingFact.reversalReference());
    assertEquals(Optional.of(reversalReason), postingFact.reversalReason());
    assertEquals(Optional.of(LocalDate.parse("2026-04-01")), assertion.effectiveDateFrom());
    assertEquals(Optional.of(LocalDate.parse("2026-04-30")), assertion.effectiveDateTo());
    assertEquals(bounded, assertion.query().effectiveDateRange());
    assertEquals(new IdempotencyKey("idem-1"), commitRejected.requestIdempotencyKey());
    assertEquals(LedgerPlanStatus.SUCCEEDED, succeededResult.status());
    assertEquals(LedgerPlanStatus.REJECTED, rejectedResult.status());
    assertEquals(LedgerPlanStatus.ASSERTION_FAILED, assertionFailedResult.status());
  }
}
