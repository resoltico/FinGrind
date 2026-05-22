package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountRole;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.stepId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.workflow.LedgerJournalKind;
import dev.erst.fingrind.contract.workflow.LedgerStep;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingQueryRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.workflow.BookWorkflowAssertion;
import dev.erst.fingrind.executor.workflow.BookWorkflowBoundaryPhase;
import dev.erst.fingrind.executor.workflow.BookWorkflowFact;
import dev.erst.fingrind.executor.workflow.BookWorkflowFailure;
import dev.erst.fingrind.executor.workflow.BookWorkflowJournalDescriptor;
import dev.erst.fingrind.executor.workflow.BookWorkflowPlan;
import dev.erst.fingrind.executor.workflow.BookWorkflowPublishedLanguageTranslator;
import dev.erst.fingrind.executor.workflow.BookWorkflowStep;
import dev.erst.fingrind.executor.workflow.BookWorkflowStepId;
import dev.erst.fingrind.executor.workflow.LedgerPlanFactMapper;
import dev.erst.fingrind.executor.workflow.LedgerPlanOutcomeMapper;
import dev.erst.fingrind.executor.workflow.LedgerPlanStepOutcome;
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
    LedgerStep step =
        new LedgerStep.OpenBook(stepId("open"), ExecutorAccountingTestSupport.openBookCommand());

    var journalEntry =
        LedgerPlanOutcomeMapper.unexpectedExecutionFailure(
            workflowStep(step), FIXED_INSTANT, FIXED_INSTANT, new IllegalStateException("   "));

    assertEquals(
        "Ledger plan execution failed unexpectedly during step 'open'.",
        journalEntry.failure().message());
  }

  @Test
  void unexpectedExecutionFailure_omitsDetailWhenMessageIsNull() {
    LedgerStep step =
        new LedgerStep.OpenBook(stepId("open"), ExecutorAccountingTestSupport.openBookCommand());

    var journalEntry =
        LedgerPlanOutcomeMapper.unexpectedExecutionFailure(
            workflowStep(step), FIXED_INSTANT, FIXED_INSTANT, new IllegalStateException());

    assertEquals(
        "Ledger plan execution failed unexpectedly during step 'open'.",
        journalEntry.failure().message());
  }

  @Test
  void unexpectedExecutionFailure_includesNonBlankDetail() {
    LedgerStep step =
        new LedgerStep.OpenBook(stepId("open"), ExecutorAccountingTestSupport.openBookCommand());

    var journalEntry =
        LedgerPlanOutcomeMapper.unexpectedExecutionFailure(
            workflowStep(step),
            FIXED_INSTANT,
            FIXED_INSTANT,
            new IllegalStateException("database locked"));

    assertEquals(
        "Ledger plan execution failed unexpectedly during step 'open': database locked",
        journalEntry.failure().message());
  }

  @Test
  void unexpectedPlanFailure_recordsPhaseCleanupAndPriorFailureFacts() {
    BookWorkflowStep step =
        workflowStep(
            new LedgerStep.OpenBook(
                stepId("open"), ExecutorAccountingTestSupport.openBookCommand()));

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
        dev.erst.fingrind.contract.workflow.LedgerBoundaryPhase.COMMIT,
        publishedEntry.boundaryPhase());
    assertTrue(journalEntry.failure().message().contains("during commit after step 'open'"));
    assertTrue(
        journalEntry.failure().facts().stream()
            .anyMatch(
                fact ->
                    fact instanceof BookWorkflowFact.Text text
                        && "phase".equals(text.name())
                        && "commit".equals(text.value())));
    assertTrue(
        journalEntry.failure().facts().stream()
            .anyMatch(
                fact ->
                    fact instanceof BookWorkflowFact.Group group
                        && "cleanupFailure".equals(group.name())));
    assertTrue(
        journalEntry.failure().facts().stream()
            .anyMatch(
                fact ->
                    fact instanceof BookWorkflowFact.Group group
                        && "priorFailure".equals(group.name())));
    assertTrue(
        journalEntry.failure().facts().stream()
            .anyMatch(
                fact ->
                    fact instanceof BookWorkflowFact.Text text
                        && "triggerStepId".equals(text.name())
                        && "open".equals(text.value())));
  }

  @Test
  void unexpectedPlanFailure_omitsDetailWhenMessageIsBlank() {
    BookWorkflowStep step =
        workflowStep(
            new LedgerStep.OpenBook(
                stepId("open"), ExecutorAccountingTestSupport.openBookCommand()));

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
    BookWorkflowStep step =
        workflowStep(
            new LedgerStep.OpenBook(
                stepId("open"), ExecutorAccountingTestSupport.openBookCommand()));

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
                new dev.erst.fingrind.contract.workflow.LedgerAssertion.AccountBalanceEquals(
                    new dev.erst.fingrind.core.AccountCode("1000"),
                    null,
                    null,
                    dev.erst.fingrind.core.Money.parse("EUR", "10.00"),
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
                    fact instanceof BookWorkflowFact.Text text
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
            internalStepId("@plan-boundary:commit"),
            new BookWorkflowJournalDescriptor.Boundary(BookWorkflowBoundaryPhase.COMMIT),
            new IllegalStateException("rollback boom"),
            null,
            null);

    assertTrue(
        journalEntry.failure().facts().stream()
            .anyMatch(
                fact ->
                    fact instanceof BookWorkflowFact.Text text
                        && "triggerBoundaryPhase".equals(text.name())
                        && BookWorkflowBoundaryPhase.COMMIT.wireValue().equals(text.value())));
  }

  @Test
  void unexpectedPlanFailure_withoutTriggerStepUsesPlainBoundaryMessages() {
    var begin =
        LedgerPlanOutcomeMapper.unexpectedPlanFailure(
            BookWorkflowBoundaryPhase.BEGIN,
            FIXED_INSTANT,
            FIXED_INSTANT,
            null,
            null,
            new IllegalStateException("begin boom"),
            null,
            null);
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
        "Ledger plan execution failed unexpectedly during begin: begin boom",
        begin.failure().message());
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
  void administrationRejection_projectsEveryLocalAdministrationVariant() {
    var bookNotInitialized =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanOutcomeMapper.administrationRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection
                    .BookNotInitialized());
    var bookContainsSchema =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanOutcomeMapper.administrationRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection
                    .BookContainsSchema());
    var accountTypeConflict =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanOutcomeMapper.administrationRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection
                    .AccountTypeConflict(
                    new AccountCode("1000"), AccountType.ASSET, AccountType.LIABILITY));
    var accountRoleConflict =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanOutcomeMapper.administrationRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection
                    .AccountRoleConflict(
                    new AccountCode("1000"), AccountRole.ORDINARY, AccountRole.CONTRA));

    assertEquals("administration-book-not-initialized", bookNotInitialized.failure().code());
    assertEquals(
        "The selected book does not exist or has not been initialized with an open book step.",
        bookNotInitialized.failure().message());
    assertEquals("book-contains-schema", bookContainsSchema.failure().code());
    assertEquals(
        "The selected SQLite file already contains schema objects and cannot be initialized as a new book.",
        bookContainsSchema.failure().message());
    assertEquals("account-type-conflict", accountTypeConflict.failure().code());
    assertEquals(
        List.of(
            BookWorkflowFact.text("accountCode", "1000"),
            BookWorkflowFact.text("existingAccountType", "ASSET"),
            BookWorkflowFact.text("requestedAccountType", "LIABILITY")),
        accountTypeConflict.failure().facts());
    assertEquals("account-role-conflict", accountRoleConflict.failure().code());
    assertEquals(
        List.of(
            BookWorkflowFact.text("accountCode", "1000"),
            BookWorkflowFact.text("existingAccountRole", "ORDINARY"),
            BookWorkflowFact.text("requestedAccountRole", "CONTRA")),
        accountRoleConflict.failure().facts());
  }

  @Test
  void postingRejection_projectsEveryRemainingPostingVariant() {
    var bookNotInitialized =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanOutcomeMapper.postingRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection
                    .BookNotInitialized());
    var duplicateIdempotencyKey =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanOutcomeMapper.postingRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection
                    .DuplicateIdempotencyKey());
    var reversalTargetNotFound =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanOutcomeMapper.postingRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection
                    .ReversalTargetNotFound(new PostingId("posting-1")));
    var reversalAlreadyExists =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanOutcomeMapper.postingRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection
                    .ReversalAlreadyExists(new PostingId("posting-2")));
    var reversalDoesNotNegateTarget =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanOutcomeMapper.postingRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection
                    .ReversalDoesNotNegateTarget(new PostingId("posting-3")));
    var functionalCurrencyMismatch =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanOutcomeMapper.postingRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection
                    .BookFunctionalCurrencyMismatch(
                    dev.erst.fingrind.core.CurrencyUnit.of("EUR"),
                    dev.erst.fingrind.core.CurrencyUnit.of("USD")));
    var closedPeriodViolation =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanOutcomeMapper.postingRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection
                    .ClosedPeriodViolation(
                    LocalDate.parse("2026-04-07"), LocalDate.parse("2026-04-06")));
    var openingBalanceWindowClosed =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanOutcomeMapper.postingRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection
                    .OpeningBalanceWindowClosed(
                    PostingKind.STANDARD, LocalDate.parse("2026-04-07")));
    var openingBalanceNominalAccount =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanOutcomeMapper.postingRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection
                    .OpeningBalanceTouchesNominalAccount(
                    new AccountCode("4000"), AccountType.REVENUE));
    var retainedEarningsReserved =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanOutcomeMapper.postingRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection
                    .ClosingEquityAccountReserved(new AccountCode("3200")));

    assertEquals("posting-book-not-initialized", bookNotInitialized.failure().code());
    assertEquals("duplicate-idempotency-key", duplicateIdempotencyKey.failure().code());
    assertEquals("reversal-target-not-found", reversalTargetNotFound.failure().code());
    assertEquals("reversal-already-exists", reversalAlreadyExists.failure().code());
    assertEquals("reversal-does-not-negate-target", reversalDoesNotNegateTarget.failure().code());
    assertEquals("book-functional-currency-mismatch", functionalCurrencyMismatch.failure().code());
    assertEquals("closed-period-violation", closedPeriodViolation.failure().code());
    assertEquals("opening-balance-window-closed", openingBalanceWindowClosed.failure().code());
    assertEquals(
        "opening-balance-touches-nominal-account", openingBalanceNominalAccount.failure().code());
    assertEquals("closing-equity-account-reserved", retainedEarningsReserved.failure().code());
    assertEquals(
        List.of(BookWorkflowFact.text("priorPostingId", "posting-1")),
        reversalTargetNotFound.failure().facts());
    assertEquals(
        List.of(BookWorkflowFact.text("priorPostingId", "posting-2")),
        reversalAlreadyExists.failure().facts());
    assertEquals(
        List.of(BookWorkflowFact.text("priorPostingId", "posting-3")),
        reversalDoesNotNegateTarget.failure().facts());
    assertEquals(
        List.of(
            BookWorkflowFact.text("functionalCurrency", "EUR"),
            BookWorkflowFact.text("attemptedCurrency", "USD")),
        functionalCurrencyMismatch.failure().facts());
    assertEquals(
        List.of(
            BookWorkflowFact.text("closedThroughEffectiveDate", "2026-04-07"),
            BookWorkflowFact.text("attemptedEffectiveDate", "2026-04-06")),
        closedPeriodViolation.failure().facts());
    assertEquals(
        List.of(
            BookWorkflowFact.text("firstBlockingPostingKind", PostingKind.STANDARD.wireValue()),
            BookWorkflowFact.text("firstBlockingEffectiveDate", "2026-04-07")),
        openingBalanceWindowClosed.failure().facts());
    assertEquals(
        List.of(
            BookWorkflowFact.text("accountCode", "4000"),
            BookWorkflowFact.text("accountType", AccountType.REVENUE.wireValue())),
        openingBalanceNominalAccount.failure().facts());
    assertEquals(
        List.of(BookWorkflowFact.text("accountCode", "3200")),
        retainedEarningsReserved.failure().facts());
  }

  @Test
  void postingRejection_recordsInactiveAccountViolationsInWorkflowFacts() {
    var rejected =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanOutcomeMapper.postingRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection
                    .AccountStateViolations(
                    List.of(
                        new dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection
                            .InactiveAccount(new AccountCode("2000")))));

    assertEquals("account-state-violations", rejected.failure().code());
    assertEquals(
        BookWorkflowFact.count("violationCount", 1), rejected.failure().facts().getFirst());
    assertEquals(
        BookWorkflowFact.group(
            "violation",
            List.of(
                BookWorkflowFact.text("code", "inactive-account"),
                BookWorkflowFact.text("accountCode", "2000"))),
        rejected.failure().facts().get(1));
  }

  @Test
  void missingBookCode_usesTheBoundaryOwnedRejectionFamilyForEachWorkflowStep() {
    assertEquals(
        BookAdministrationRejection.bookNotInitializedCode(),
        LedgerPlanOutcomeMapper.missingBookCode(
            new BookWorkflowStep.OpenBook(internalStepId("open"), bookIdentity())));
    assertEquals(
        BookAdministrationRejection.bookNotInitializedCode(),
        LedgerPlanOutcomeMapper.missingBookCode(
            new BookWorkflowStep.DeclareAccount(internalStepId("declare"), accountDeclaration())));
    assertEquals(
        PostingRejection.bookNotInitializedCode(),
        LedgerPlanOutcomeMapper.missingBookCode(
            new BookWorkflowStep.PreflightEntry(
                internalStepId("preflight"), postingCommand("idem-1"))));
    assertEquals(
        PostingRejection.bookNotInitializedCode(),
        LedgerPlanOutcomeMapper.missingBookCode(
            new BookWorkflowStep.PostEntry(internalStepId("post"), postingCommand("idem-2"))));
    assertEquals(
        BookkeepingQueryRejection.bookNotInitializedCode(),
        LedgerPlanOutcomeMapper.missingBookCode(
            new BookWorkflowStep.InspectBook(internalStepId("inspect"))));
    assertEquals(
        BookkeepingQueryRejection.bookNotInitializedCode(),
        LedgerPlanOutcomeMapper.missingBookCode(
            new BookWorkflowStep.ListAccounts(
                internalStepId("accounts"), new AccountRegistryQuery(1, Optional.empty()))));
    assertEquals(
        BookkeepingQueryRejection.bookNotInitializedCode(),
        LedgerPlanOutcomeMapper.missingBookCode(
            new BookWorkflowStep.GetPosting(
                internalStepId("posting"), new PostingId("posting-1"))));
    assertEquals(
        BookkeepingQueryRejection.bookNotInitializedCode(),
        LedgerPlanOutcomeMapper.missingBookCode(
            new BookWorkflowStep.ListPostings(
                internalStepId("postings"),
                new PostingHistoryQuery(
                    Optional.empty(), EffectiveDateRange.unbounded(), 1, Optional.empty()))));
    assertEquals(
        BookkeepingQueryRejection.bookNotInitializedCode(),
        LedgerPlanOutcomeMapper.missingBookCode(
            new BookWorkflowStep.AccountBalance(
                internalStepId("balance"),
                new AccountBalanceCriteria(
                    new AccountCode("1000"),
                    EffectiveDateRange.unbounded(),
                    PostingCoverage.ALL_POSTING_KINDS))));
    assertEquals(
        BookkeepingQueryRejection.bookNotInitializedCode(),
        LedgerPlanOutcomeMapper.missingBookCode(
            new BookWorkflowStep.Assert(
                internalStepId("assert"),
                new BookWorkflowAssertion.AccountBalanceEquals(
                    new AccountCode("1000"),
                    EffectiveDateRange.unbounded(),
                    Money.parse("EUR", "10.00"),
                    BalanceSide.DEBIT))));
  }

  private static BookWorkflowStep workflowStep(LedgerStep step) {
    BookWorkflowPlan plan =
        BookWorkflowPublishedLanguageTranslator.fromPublished(
            new dev.erst.fingrind.contract.workflow.LedgerPlan(
                LedgerPlanServiceTestSupport.planId("plan"), java.util.List.of(step)));
    return plan.steps().getFirst();
  }

  private static BookWorkflowStepId internalStepId(String value) {
    return new BookWorkflowStepId(value);
  }

  private static AccountDeclaration accountDeclaration() {
    return new AccountDeclaration(
        new AccountCode("1000"),
        new AccountName("Cash"),
        AccountType.ASSET,
        accountRole(AccountType.ASSET, NormalBalance.DEBIT),
        accountTaxonomy(AccountType.ASSET));
  }

  private static PostEntryCommand postingCommand(String idempotencyKey) {
    return new PostEntryCommand(
        new BookkeepingEntry.CashRevenue(
            LocalDate.parse("2026-05-05"),
            new AccountCode("1000"),
            new AccountCode("2000"),
            MonetaryAmount.of(Money.parse("EUR", "10.00"))),
        accountingEvidence(idempotencyKey),
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
        new dev.erst.fingrind.core.JournalEntry(
            LocalDate.parse("2026-05-05"),
            List.of(
                new dev.erst.fingrind.core.JournalLine(
                    new AccountCode("1000"),
                    dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                    Money.parse("EUR", "10.00")),
                new dev.erst.fingrind.core.JournalLine(
                    new AccountCode("2000"),
                    dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                    Money.parse("EUR", "10.00")))),
        PostingLineageModel.direct(),
        PostingKind.STANDARD,
        accountingEvidence("idem-3"),
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
