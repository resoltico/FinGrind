package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.stepId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.BookAdministrationRejection;
import dev.erst.fingrind.contract.LedgerJournalKind;
import dev.erst.fingrind.contract.LedgerStep;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingCommand;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.workflow.BookWorkflowAssertion;
import dev.erst.fingrind.executor.workflow.BookWorkflowBoundaryPhase;
import dev.erst.fingrind.executor.workflow.BookWorkflowFailure;
import dev.erst.fingrind.executor.workflow.BookWorkflowJournalDescriptor;
import dev.erst.fingrind.executor.workflow.BookWorkflowPlan;
import dev.erst.fingrind.executor.workflow.BookWorkflowPublishedLanguageTranslator;
import dev.erst.fingrind.executor.workflow.BookWorkflowStep;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct coverage for unexpected ledger-plan failure mapping branches. */
class LedgerPlanOutcomeMapperTest {
  private static final Instant FIXED_INSTANT = Instant.parse("2026-04-29T10:15:30Z");

  @Test
  void unexpectedExecutionFailure_omitsDetailWhenMessageIsBlank() {
    LedgerStep step = new LedgerStep.OpenBook(stepId("open"));

    var journalEntry =
        LedgerPlanOutcomeMapper.unexpectedExecutionFailure(
            workflowStep(step), FIXED_INSTANT, FIXED_INSTANT, new IllegalStateException("   "));

    assertEquals(
        "Ledger plan execution failed unexpectedly during step 'open'.",
        journalEntry.failure().message());
  }

  @Test
  void unexpectedExecutionFailure_omitsDetailWhenMessageIsNull() {
    LedgerStep step = new LedgerStep.OpenBook(stepId("open"));

    var journalEntry =
        LedgerPlanOutcomeMapper.unexpectedExecutionFailure(
            workflowStep(step), FIXED_INSTANT, FIXED_INSTANT, new IllegalStateException());

    assertEquals(
        "Ledger plan execution failed unexpectedly during step 'open'.",
        journalEntry.failure().message());
  }

  @Test
  void unexpectedPlanFailure_recordsPhaseCleanupAndPriorFailureFacts() {
    BookWorkflowStep step = workflowStep(new LedgerStep.OpenBook(stepId("open")));

    var journalEntry =
        LedgerPlanOutcomeMapper.unexpectedPlanFailure(
            BookWorkflowBoundaryPhase.COMMIT,
            FIXED_INSTANT,
            FIXED_INSTANT,
            step.stepId(),
            new BookWorkflowJournalDescriptor.Step(step),
            new IllegalStateException("commit boom"),
            new IllegalStateException("rollback boom"),
            new BookWorkflowFailure(
                BookAdministrationRejection.wireCode(
                    new BookAdministrationRejection.BookAlreadyInitialized()),
                "already initialized",
                java.util.List.of()));
    var publishedEntry = BookWorkflowPublishedLanguageTranslator.toPublished(journalEntry);

    assertEquals("unexpected-plan-failure", journalEntry.failure().code());
    assertEquals(LedgerJournalKind.PLAN_BOUNDARY, publishedEntry.kind());
    assertEquals(
        dev.erst.fingrind.contract.LedgerBoundaryPhase.COMMIT, publishedEntry.boundaryPhase());
    assertTrue(journalEntry.failure().message().contains("during commit after step 'open'"));
    assertTrue(
        journalEntry.failure().facts().stream()
            .anyMatch(
                fact ->
                    fact instanceof dev.erst.fingrind.contract.LedgerFact.Text text
                        && "phase".equals(text.name())
                        && "commit".equals(text.value())));
    assertTrue(
        journalEntry.failure().facts().stream()
            .anyMatch(
                fact ->
                    fact instanceof dev.erst.fingrind.contract.LedgerFact.Group group
                        && "cleanupFailure".equals(group.name())));
    assertTrue(
        journalEntry.failure().facts().stream()
            .anyMatch(
                fact ->
                    fact instanceof dev.erst.fingrind.contract.LedgerFact.Group group
                        && "priorFailure".equals(group.name())));
    assertTrue(
        journalEntry.failure().facts().stream()
            .anyMatch(
                fact ->
                    fact instanceof dev.erst.fingrind.contract.LedgerFact.Text text
                        && "triggerStepId".equals(text.name())
                        && "open".equals(text.value())));
  }

  @Test
  void unexpectedPlanFailure_omitsDetailWhenMessageIsBlank() {
    BookWorkflowStep step = workflowStep(new LedgerStep.OpenBook(stepId("open")));

    var journalEntry =
        LedgerPlanOutcomeMapper.unexpectedPlanFailure(
            BookWorkflowBoundaryPhase.COMMIT,
            FIXED_INSTANT,
            FIXED_INSTANT,
            step.stepId(),
            new BookWorkflowJournalDescriptor.Step(step),
            new IllegalStateException("   "),
            null,
            null);

    assertEquals(
        "Ledger plan execution failed unexpectedly during commit after step 'open'.",
        journalEntry.failure().message());
  }

  @Test
  void unexpectedPlanFailure_omitsDetailWhenMessageIsNull() {
    BookWorkflowStep step = workflowStep(new LedgerStep.OpenBook(stepId("open")));

    var journalEntry =
        LedgerPlanOutcomeMapper.unexpectedPlanFailure(
            BookWorkflowBoundaryPhase.COMMIT,
            FIXED_INSTANT,
            FIXED_INSTANT,
            step.stepId(),
            new BookWorkflowJournalDescriptor.Step(step),
            new IllegalStateException(),
            null,
            null);

    assertEquals(
        "Ledger plan execution failed unexpectedly during commit after step 'open'.",
        journalEntry.failure().message());
  }

  @Test
  void unexpectedPlanFailure_recordsAssertionDetailKindWhenTriggerWasAssertion() {
    BookWorkflowStep step =
        workflowStep(
            new LedgerStep.Assert(
                stepId("assert-balance"),
                new dev.erst.fingrind.contract.LedgerAssertion.AccountBalanceEquals(
                    new dev.erst.fingrind.core.AccountCode("1000"),
                    null,
                    null,
                    new dev.erst.fingrind.core.Money(
                        new dev.erst.fingrind.core.CurrencyCode("EUR"),
                        new java.math.BigDecimal("10.00")),
                    dev.erst.fingrind.core.BalanceSide.DEBIT)));

    var journalEntry =
        LedgerPlanOutcomeMapper.unexpectedPlanFailure(
            BookWorkflowBoundaryPhase.ROLLBACK,
            FIXED_INSTANT,
            FIXED_INSTANT,
            step.stepId(),
            new BookWorkflowJournalDescriptor.Step(step),
            new IllegalStateException("rollback boom"),
            null,
            null);

    assertTrue(
        journalEntry.failure().facts().stream()
            .anyMatch(
                fact ->
                    fact instanceof dev.erst.fingrind.contract.LedgerFact.Text text
                        && "triggerDetailKind".equals(text.name())
                        && LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS
                            .wireValue()
                            .equals(text.value())));
  }

  @Test
  void unexpectedPlanFailure_recordsBoundaryTriggerFactsWhenDescriptorIsBoundary() {
    var journalEntry =
        LedgerPlanOutcomeMapper.unexpectedPlanFailure(
            BookWorkflowBoundaryPhase.ROLLBACK,
            FIXED_INSTANT,
            FIXED_INSTANT,
            "@plan-boundary:commit",
            new BookWorkflowJournalDescriptor.Boundary(BookWorkflowBoundaryPhase.COMMIT),
            new IllegalStateException("rollback boom"),
            null,
            null);

    assertTrue(
        journalEntry.failure().facts().stream()
            .anyMatch(
                fact ->
                    fact instanceof dev.erst.fingrind.contract.LedgerFact.Text text
                        && "triggerBoundaryPhase".equals(text.name())
                        && BookWorkflowBoundaryPhase.COMMIT.wireValue().equals(text.value())));
  }

  @Test
  void unexpectedPlanFailure_withoutTriggerStepUsesPlainBoundaryMessages() {
    var initializationCheck =
        LedgerPlanOutcomeMapper.unexpectedPlanFailure(
            BookWorkflowBoundaryPhase.INITIALIZATION_CHECK,
            FIXED_INSTANT,
            FIXED_INSTANT,
            null,
            null,
            new IllegalStateException("init boom"),
            null,
            null);
    var commit =
        LedgerPlanOutcomeMapper.unexpectedPlanFailure(
            BookWorkflowBoundaryPhase.COMMIT,
            FIXED_INSTANT,
            FIXED_INSTANT,
            null,
            null,
            new IllegalStateException("commit boom"),
            null,
            null);
    var rollback =
        LedgerPlanOutcomeMapper.unexpectedPlanFailure(
            BookWorkflowBoundaryPhase.ROLLBACK,
            FIXED_INSTANT,
            FIXED_INSTANT,
            null,
            null,
            new IllegalStateException("rollback boom"),
            null,
            null);

    assertEquals(
        "Ledger plan execution failed unexpectedly during initialization-check: init boom",
        initializationCheck.failure().message());
    assertEquals(
        "Ledger plan execution failed unexpectedly during commit: commit boom",
        commit.failure().message());
    assertEquals(
        "Ledger plan execution failed unexpectedly during rollback: rollback boom",
        rollback.failure().message());
  }

  @Test
  void postingFacts_forCommittedPostingMatchesPublishedFactMapping() {
    CommittedPosting posting = committedPosting();

    assertEquals(
        LedgerPlanFactMapper.postingFacts(posting), LedgerPlanOutcomeMapper.postingFacts(posting));
  }

  @Test
  void missingBookCode_usesTheBoundaryOwnedRejectionFamilyForEachWorkflowStep() {
    assertEquals(
        BookAdministrationRejection.bookNotInitializedCode(),
        LedgerPlanOutcomeMapper.missingBookCode(new BookWorkflowStep.OpenBook("open")));
    assertEquals(
        BookAdministrationRejection.bookNotInitializedCode(),
        LedgerPlanOutcomeMapper.missingBookCode(
            new BookWorkflowStep.DeclareAccount("declare", accountDeclaration())));
    assertEquals(
        PostingRejection.bookNotInitializedCode(),
        LedgerPlanOutcomeMapper.missingBookCode(
            new BookWorkflowStep.PreflightEntry("preflight", postingCommand("idem-1"))));
    assertEquals(
        PostingRejection.bookNotInitializedCode(),
        LedgerPlanOutcomeMapper.missingBookCode(
            new BookWorkflowStep.PostEntry("post", postingCommand("idem-2"))));
    assertEquals(
        dev.erst.fingrind.contract.BookQueryRejection.bookNotInitializedCode(),
        LedgerPlanOutcomeMapper.missingBookCode(new BookWorkflowStep.InspectBook("inspect")));
    assertEquals(
        dev.erst.fingrind.contract.BookQueryRejection.bookNotInitializedCode(),
        LedgerPlanOutcomeMapper.missingBookCode(
            new BookWorkflowStep.ListAccounts(
                "accounts", new AccountRegistryQuery(1, Optional.empty()))));
    assertEquals(
        dev.erst.fingrind.contract.BookQueryRejection.bookNotInitializedCode(),
        LedgerPlanOutcomeMapper.missingBookCode(
            new BookWorkflowStep.GetPosting("posting", new PostingId("posting-1"))));
    assertEquals(
        dev.erst.fingrind.contract.BookQueryRejection.bookNotInitializedCode(),
        LedgerPlanOutcomeMapper.missingBookCode(
            new BookWorkflowStep.ListPostings(
                "postings",
                new PostingHistoryQuery(
                    Optional.empty(), EffectiveDateRange.unbounded(), 1, Optional.empty()))));
    assertEquals(
        dev.erst.fingrind.contract.BookQueryRejection.bookNotInitializedCode(),
        LedgerPlanOutcomeMapper.missingBookCode(
            new BookWorkflowStep.AccountBalance(
                "balance",
                new AccountBalanceCriteria(
                    new AccountCode("1000"), EffectiveDateRange.unbounded()))));
    assertEquals(
        dev.erst.fingrind.contract.BookQueryRejection.bookNotInitializedCode(),
        LedgerPlanOutcomeMapper.missingBookCode(
            new BookWorkflowStep.Assert(
                "assert",
                new BookWorkflowAssertion.AccountBalanceEquals(
                    new AccountCode("1000"),
                    null,
                    null,
                    new Money(new CurrencyCode("EUR"), new BigDecimal("10.00")),
                    BalanceSide.DEBIT))));
  }

  private static BookWorkflowStep workflowStep(LedgerStep step) {
    BookWorkflowPlan plan =
        BookWorkflowPublishedLanguageTranslator.fromPublished(
            new dev.erst.fingrind.contract.LedgerPlan(
                LedgerPlanServiceTestSupport.planId("plan"), java.util.List.of(step)));
    return plan.steps().getFirst();
  }

  private static AccountDeclaration accountDeclaration() {
    return new AccountDeclaration(
        new AccountCode("1000"), new AccountName("Cash"), NormalBalance.DEBIT);
  }

  private static PostingCommand postingCommand(String idempotencyKey) {
    return new PostingCommand(
        new JournalEntry(
            LocalDate.parse("2026-05-05"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    new Money(new CurrencyCode("EUR"), new BigDecimal("10.00"))),
                new JournalLine(
                    new AccountCode("2000"),
                    JournalLine.EntrySide.CREDIT,
                    new Money(new CurrencyCode("EUR"), new BigDecimal("10.00"))))),
        PostingLineageModel.direct(),
        new RequestProvenance(
            new ActorId("actor-1"),
            ActorType.AGENT,
            new CommandId("command-1"),
            new IdempotencyKey(idempotencyKey),
            new CausationId("cause-1"),
            Optional.of(new CorrelationId("corr-1"))),
        SourceChannel.CLI);
  }

  private static CommittedPosting committedPosting() {
    return new CommittedPosting(
        new PostingId("posting-1"),
        postingCommand("idem-3").journalEntry(),
        PostingLineageModel.direct(),
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.AGENT,
                new CommandId("command-1"),
                new IdempotencyKey("idem-3"),
                new CausationId("cause-1"),
                Optional.of(new CorrelationId("corr-1"))),
            FIXED_INSTANT,
            SourceChannel.CLI));
  }
}
