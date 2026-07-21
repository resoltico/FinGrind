package dev.erst.fingrind.executor;

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
import dev.erst.fingrind.contract.bookkeeping.PostingEffectiveDateBeforeBookStart;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.workflow.LedgerJournalKind;
import dev.erst.fingrind.contract.workflow.LedgerStep;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
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
import dev.erst.fingrind.executor.workflow.BookWorkflowAssertionStep;
import dev.erst.fingrind.executor.workflow.BookWorkflowBoundaryCheckpoint;
import dev.erst.fingrind.executor.workflow.BookWorkflowFact;
import dev.erst.fingrind.executor.workflow.BookWorkflowFailure;
import dev.erst.fingrind.executor.workflow.BookWorkflowJournalDescriptor;
import dev.erst.fingrind.executor.workflow.BookWorkflowPlan;
import dev.erst.fingrind.executor.workflow.BookWorkflowPublishedLanguageTranslator;
import dev.erst.fingrind.executor.workflow.BookWorkflowStep;
import dev.erst.fingrind.executor.workflow.BookWorkflowStepId;
import dev.erst.fingrind.executor.workflow.LedgerPlanFactMapper;
import dev.erst.fingrind.executor.workflow.LedgerPlanRejectedOutcomes;
import dev.erst.fingrind.executor.workflow.LedgerPlanStepOutcome;
import dev.erst.fingrind.executor.workflow.LedgerPlanStepOutcomes;
import dev.erst.fingrind.executor.workflow.LedgerPlanUnexpectedOutcomes;
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
        new LedgerStep.InspectBook(stepId("open"));

    var journalEntry =
        LedgerPlanUnexpectedOutcomes.unexpectedExecutionFailure(
            workflowStep(step), FIXED_INSTANT, FIXED_INSTANT, new IllegalStateException("   "));

    assertEquals(
        "Ledger plan execution failed unexpectedly during step 'open'.",
        journalEntry.failure().message());
  }

  @Test
  void unexpectedExecutionFailure_omitsDetailWhenMessageIsNull() {
    LedgerStep step =
        new LedgerStep.InspectBook(stepId("open"));

    var journalEntry =
        LedgerPlanUnexpectedOutcomes.unexpectedExecutionFailure(
            workflowStep(step), FIXED_INSTANT, FIXED_INSTANT, new IllegalStateException());

    assertEquals(
        "Ledger plan execution failed unexpectedly during step 'open'.",
        journalEntry.failure().message());
  }

  @Test
  void unexpectedExecutionFailure_includesNonBlankDetail() {
    LedgerStep step =
        new LedgerStep.InspectBook(stepId("open"));

    var journalEntry =
        LedgerPlanUnexpectedOutcomes.unexpectedExecutionFailure(
            workflowStep(step),
            FIXED_INSTANT,
            FIXED_INSTANT,
            new IllegalStateException("database locked"));

    assertEquals(
        "Ledger plan execution failed unexpectedly during step 'open': database locked",
        journalEntry.failure().message());
  }

  @Test
  void unexpectedPlanFailure_recordsCheckpointCleanupAndPriorFailureFacts() {
    BookWorkflowStep step =
        workflowStep(
            new LedgerStep.InspectBook(stepId("open")));

    var journalEntry =
        LedgerPlanUnexpectedOutcomes.unexpectedPlanFailure(
            BookWorkflowBoundaryCheckpoint.COMMIT,
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
    assertEquals(LedgerJournalKind.BoundaryKind.PLAN_BOUNDARY, publishedEntry.kind());
    assertEquals(
        dev.erst.fingrind.contract.workflow.LedgerBoundaryCheckpoint.COMMIT,
        publishedEntry.boundaryCheckpoint());
    assertTrue(journalEntry.failure().message().contains("during commit after step 'open'"));
    assertTrue(
        journalEntry.failure().facts().stream()
            .anyMatch(
                fact ->
                    fact instanceof BookWorkflowFact.Text text
                        && "checkpoint".equals(text.name())
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
            new LedgerStep.InspectBook(stepId("open")));

    var journalEntry =
        LedgerPlanUnexpectedOutcomes.unexpectedPlanFailure(
            BookWorkflowBoundaryCheckpoint.COMMIT,
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
            new LedgerStep.InspectBook(stepId("open")));

    var journalEntry =
        LedgerPlanUnexpectedOutcomes.unexpectedPlanFailure(
            BookWorkflowBoundaryCheckpoint.COMMIT,
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
        LedgerPlanUnexpectedOutcomes.unexpectedPlanFailure(
            BookWorkflowBoundaryCheckpoint.ROLLBACK,
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
        LedgerPlanUnexpectedOutcomes.unexpectedPlanFailure(
            BookWorkflowBoundaryCheckpoint.ROLLBACK,
            FIXED_INSTANT,
            FIXED_INSTANT,
            internalStepId("@plan-boundary:commit"),
            new BookWorkflowJournalDescriptor.Boundary(BookWorkflowBoundaryCheckpoint.COMMIT),
            new IllegalStateException("rollback boom"),
            null,
            null);

    assertTrue(
        journalEntry.failure().facts().stream()
            .anyMatch(
                fact ->
                    fact instanceof BookWorkflowFact.Text text
                        && "triggerBoundaryCheckpoint".equals(text.name())
                        && BookWorkflowBoundaryCheckpoint.COMMIT.wireValue().equals(text.value())));
  }

  @Test
  void unexpectedPlanFailure_withoutTriggerStepUsesPlainBoundaryMessages() {
    var begin =
        LedgerPlanUnexpectedOutcomes.unexpectedPlanFailure(
            BookWorkflowBoundaryCheckpoint.BEGIN,
            FIXED_INSTANT,
            FIXED_INSTANT,
            null,
            null,
            new IllegalStateException("begin boom"),
            null,
            null);
    var initializationCheck =
        LedgerPlanUnexpectedOutcomes.unexpectedPlanFailure(
            BookWorkflowBoundaryCheckpoint.INITIALIZATION_CHECK,
            FIXED_INSTANT,
            FIXED_INSTANT,
            null,
            null,
            new IllegalStateException("init boom"),
            null,
            null);
    var commit =
        LedgerPlanUnexpectedOutcomes.unexpectedPlanFailure(
            BookWorkflowBoundaryCheckpoint.COMMIT,
            FIXED_INSTANT,
            FIXED_INSTANT,
            null,
            null,
            new IllegalStateException("commit boom"),
            null,
            null);
    var rollback =
        LedgerPlanUnexpectedOutcomes.unexpectedPlanFailure(
            BookWorkflowBoundaryCheckpoint.ROLLBACK,
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
        LedgerPlanFactMapper.postingFacts(posting), LedgerPlanStepOutcomes.postingFacts(posting));
  }

  @Test
  void administrationRejection_projectsEveryLocalAdministrationVariant() {
    var bookNotInitialized =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanRejectedOutcomes.administrationRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection
                    .BookNotInitialized());
    var bookContainsSchema =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanRejectedOutcomes.administrationRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection
                    .BookContainsSchema());
    var accountTypeConflict =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanRejectedOutcomes.administrationRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection
                    .AccountTypeConflict(
                    new AccountCode("1000"), AccountType.ASSET, AccountType.LIABILITY));
    AccountTaxonomy existingTaxonomy =
        new AccountTaxonomy(
            dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
            Optional.empty(),
            Optional.empty(),
            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
            Optional.empty(),
            Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT));
    AccountTaxonomy requestedTaxonomy =
        new AccountTaxonomy(
            dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
            Optional.empty(),
            Optional.empty(),
            Optional.of(FinancialPositionLineClassification.NONCURRENT_ASSET),
            Optional.empty(),
            Optional.of(CashFlowAssetClassification.NON_CASH));
    var accountTaxonomyConflict =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanRejectedOutcomes.administrationRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection
                    .AccountTaxonomyConflict(
                    new AccountCode("1000"), existingTaxonomy, requestedTaxonomy));

    assertEquals("administration-book-not-initialized", bookNotInitialized.failure().code());
    assertEquals(
        "The selected book does not exist or has not been opened.",
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
    assertEquals("account-taxonomy-conflict", accountTaxonomyConflict.failure().code());
    assertEquals(
        List.of(
            BookWorkflowFact.text("accountCode", "1000"),
            BookWorkflowFact.group(
                "existingAccountTaxonomy",
                List.of(
                    BookWorkflowFact.text("accountNodeKind", "POSTABLE"),
                    BookWorkflowFact.text("financialPositionLineClassification", "CURRENT_ASSET"))),
            BookWorkflowFact.group(
                "requestedAccountTaxonomy",
                List.of(
                    BookWorkflowFact.text("accountNodeKind", "POSTABLE"),
                    BookWorkflowFact.text(
                        "financialPositionLineClassification", "NONCURRENT_ASSET")))),
        accountTaxonomyConflict.failure().facts());
  }

  @Test
  void postingRejection_projectsEveryRemainingPostingVariant() {
    var bookNotInitialized =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanRejectedOutcomes.postingRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection
                    .BookNotInitialized());
    var duplicateIdempotencyKey =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanRejectedOutcomes.postingRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection
                    .IdempotencyKeyConflict());
    var reversalTargetNotFound =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanRejectedOutcomes.postingRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection
                    .ReversalTargetNotFound(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")));
    var reversalTargetIsReversal =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanRejectedOutcomes.postingRejection(
                new dev.erst.fingrind.executor.bookkeeping.ReversalTargetIsReversal(
                    new PostingId("d66e4aa4-9992-3220-9ea1-17b11ccaee61")));
    var reversalAlreadyExists =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanRejectedOutcomes.postingRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection
                    .ReversalAlreadyExists(new PostingId("41a95cd2-4a5f-3ef3-8a33-c2771905f362")));
    var reversalDoesNotNegateTarget =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanRejectedOutcomes.postingRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection
                    .ReversalDoesNotNegateTarget(
                    new PostingId("6d857901-cb53-3986-a1d7-2f64319c76ce")));
    var functionalCurrencyMismatch =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanRejectedOutcomes.postingRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection
                    .BookFunctionalCurrencyMismatch(
                    dev.erst.fingrind.core.CurrencyUnit.of("EUR"),
                    dev.erst.fingrind.core.CurrencyUnit.of("USD")));
    var transferredPeriodResultViolation =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanRejectedOutcomes.postingRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection
                    .SweptInterimResultViolation(
                    LocalDate.parse("2026-04-07"), LocalDate.parse("2026-04-06")));
    var openingBalanceWindowClosed =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanRejectedOutcomes.postingRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection
                    .OpeningPositionWindowClosed(
                    PostingKind.STANDARD, LocalDate.parse("2026-04-07")));
    var openingBalanceNominalAccount =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanRejectedOutcomes.postingRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection
                    .OpeningPositionTouchesNominalAccount(
                    new AccountCode("4000"), AccountType.REVENUE));
    var resultHoldingReserved =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanRejectedOutcomes.postingRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection
                    .ReservedResultClassification(
                    new AccountCode("3200"), FinancialPositionLineClassification.RESULT_HOLDING));

    assertEquals("posting-book-not-initialized", bookNotInitialized.failure().code());
    assertEquals("idempotency-key-conflict", duplicateIdempotencyKey.failure().code());
    assertEquals("reversal-target-not-found", reversalTargetNotFound.failure().code());
    assertEquals("reversal-target-is-reversal", reversalTargetIsReversal.failure().code());
    assertEquals("reversal-already-exists", reversalAlreadyExists.failure().code());
    assertEquals("reversal-does-not-negate-target", reversalDoesNotNegateTarget.failure().code());
    assertEquals("book-functional-currency-mismatch", functionalCurrencyMismatch.failure().code());
    assertEquals("closed-period-violation", transferredPeriodResultViolation.failure().code());
    assertEquals("opening-position-window-closed", openingBalanceWindowClosed.failure().code());
    assertEquals(
        "opening-position-touches-nominal-account", openingBalanceNominalAccount.failure().code());
    assertEquals("reserved-result-classification", resultHoldingReserved.failure().code());
    assertEquals(
        List.of(BookWorkflowFact.text("priorPostingId", "posting-1")),
        reversalTargetNotFound.failure().facts());
    assertEquals(
        List.of(BookWorkflowFact.text("priorPostingId", "posting-1b")),
        reversalTargetIsReversal.failure().facts());
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
            BookWorkflowFact.text("transferredThroughEffectiveDate", "2026-04-07"),
            BookWorkflowFact.text("attemptedEffectiveDate", "2026-04-06")),
        transferredPeriodResultViolation.failure().facts());
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
        List.of(
            BookWorkflowFact.text("accountCode", "3200"),
            BookWorkflowFact.text("financialPositionLineClassification", "RESULT_HOLDING")),
        resultHoldingReserved.failure().facts());
  }

  @Test
  void publishedPostingRejection_projectsDateBoundaryAndEmptyFactVariants() {
    var bookNotInitialized =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanRejectedOutcomes.postingRejection(new PostingRejection.BookNotInitialized());
    var duplicateIdempotencyKey =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanRejectedOutcomes.postingRejection(
                new PostingRejection.IdempotencyKeyConflict());
    var futureDate =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanRejectedOutcomes.postingRejection(
                new PostingRejection.PostingEffectiveDateInFuture(
                    LocalDate.parse("2026-04-08"), LocalDate.parse("2026-04-07")));
    var beforeBookStart =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanRejectedOutcomes.postingRejection(
                new PostingEffectiveDateBeforeBookStart(
                    LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-02")));

    assertEquals("posting-book-not-initialized", bookNotInitialized.failure().code());
    assertEquals(List.of(), bookNotInitialized.failure().facts());
    assertEquals("idempotency-key-conflict", duplicateIdempotencyKey.failure().code());
    assertEquals(List.of(), duplicateIdempotencyKey.failure().facts());
    assertEquals("posting-effective-date-in-future", futureDate.failure().code());
    assertEquals(
        List.of(
            BookWorkflowFact.text("attemptedEffectiveDate", "2026-04-08"),
            BookWorkflowFact.text("currentUtcDate", "2026-04-07")),
        futureDate.failure().facts());
    assertEquals("posting-effective-date-before-book-start", beforeBookStart.failure().code());
    assertEquals(
        List.of(
            BookWorkflowFact.text("attemptedEffectiveDate", "2026-01-01"),
            BookWorkflowFact.text("bookStartEffectiveDate", "2026-01-02")),
        beforeBookStart.failure().facts());
  }

  @Test
  void postingRejection_recordsInactiveAccountViolationsInWorkflowFacts() {
    var rejected =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanRejectedOutcomes.postingRejection(
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
                BookWorkflowFact.text("field", "lines[].accountCode"),
                BookWorkflowFact.text(
                    "message", "Journal line references inactive account '2000'."),
                BookWorkflowFact.text("category", "account-activation"),
                BookWorkflowFact.text(
                    "repair",
                    "Reactivate the account or replace it with an active posting account before retrying."),
                BookWorkflowFact.text("accountCode", "2000"))),
        rejected.failure().facts().get(1));
  }

  @Test
  void postingRejection_recordsNonPostableAndEntrySemanticsViolationsInWorkflowFacts() {
    var nonPostableRejected =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanRejectedOutcomes.postingRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection
                    .AccountStateViolations(
                    List.of(
                        new dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection
                            .NonPostableAccount(
                            new AccountCode("3000"),
                            dev.erst.fingrind.core.AccountNodeKind.HEADER))));
    var entrySemanticsRejected =
        (LedgerPlanStepOutcome.Rejected)
            LedgerPlanRejectedOutcomes.postingRejection(
                new dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection
                    .EntrySemanticsViolations(
                    List.of(
                        new dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection
                            .EntrySemanticsViolation(
                            "account-type-mismatch",
                            "cashAccountCode",
                            "cash account must be an ASSET"),
                        new dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection
                            .EntrySemanticsViolation(
                            "source-document-type-not-accepted",
                            null,
                            "invoice does not prove cash receipt"))));

    assertEquals("account-state-violations", nonPostableRejected.failure().code());
    assertEquals(
        BookWorkflowFact.group(
            "violation",
            List.of(
                BookWorkflowFact.text("code", "non-postable-account"),
                BookWorkflowFact.text("field", "lines[].accountCode"),
                BookWorkflowFact.text(
                    "message",
                    "Journal line references header account '3000', declared as 'HEADER', which cannot accept direct postings."),
                BookWorkflowFact.text("category", "account-node-kind"),
                BookWorkflowFact.text(
                    "repair",
                    "Replace the header account with a postable account before retrying."),
                BookWorkflowFact.text("accountCode", "3000"),
                BookWorkflowFact.text("accountNodeKind", "HEADER"))),
        nonPostableRejected.failure().facts().get(1));
    assertEquals("entry-semantics-violations", entrySemanticsRejected.failure().code());
    assertEquals(
        BookWorkflowFact.count("violationCount", 2),
        entrySemanticsRejected.failure().facts().getFirst());
    assertEquals(
        BookWorkflowFact.group(
            "violation",
            List.of(
                BookWorkflowFact.text("code", "account-type-mismatch"),
                BookWorkflowFact.text("field", "cashAccountCode"),
                BookWorkflowFact.text("message", "cash account must be an ASSET"))),
        entrySemanticsRejected.failure().facts().get(1));
    assertEquals(
        BookWorkflowFact.group(
            "violation",
            List.of(
                BookWorkflowFact.text("code", "source-document-type-not-accepted"),
                BookWorkflowFact.text("message", "invoice does not prove cash receipt"))),
        entrySemanticsRejected.failure().facts().get(2));
  }

  @Test
  void missingBookCode_usesTheBoundaryOwnedRejectionFamilyForEachWorkflowStep() {
    assertEquals(
        BookAdministrationRejection.bookNotInitializedCode(),
        LedgerPlanStepOutcomes.missingBookCode(
            new BookWorkflowStep.InspectBook(internalStepId("open"))));
    assertEquals(
        BookAdministrationRejection.bookNotInitializedCode(),
        LedgerPlanStepOutcomes.missingBookCode(
            new BookWorkflowStep.DeclareAccount(internalStepId("declare"), accountDeclaration())));
    assertEquals(
        PostingRejection.bookNotInitializedCode(),
        LedgerPlanStepOutcomes.missingBookCode(
            new BookWorkflowStep.PreflightEntry(
                internalStepId("preflight"), postingCommand("idem-1"))));
    assertEquals(
        PostingRejection.bookNotInitializedCode(),
        LedgerPlanStepOutcomes.missingBookCode(
            new BookWorkflowStep.PostEntry(internalStepId("post"), postingCommand("idem-2"))));
    assertEquals(
        BookkeepingQueryRejection.bookNotInitializedCode(),
        LedgerPlanStepOutcomes.missingBookCode(
            new BookWorkflowStep.InspectBook(internalStepId("inspect"))));
    assertEquals(
        BookkeepingQueryRejection.bookNotInitializedCode(),
        LedgerPlanStepOutcomes.missingBookCode(
            new BookWorkflowStep.ListAccounts(
                internalStepId("accounts"), new AccountRegistryQuery(1, Optional.empty()))));
    assertEquals(
        BookkeepingQueryRejection.bookNotInitializedCode(),
        LedgerPlanStepOutcomes.missingBookCode(
            new BookWorkflowStep.GetPosting(
                internalStepId("posting"), new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"))));
    assertEquals(
        BookkeepingQueryRejection.bookNotInitializedCode(),
        LedgerPlanStepOutcomes.missingBookCode(
            new BookWorkflowStep.ListPostings(
                internalStepId("postings"),
                new PostingHistoryQuery(
                    Optional.empty(), EffectiveDateRange.unbounded(), 1, Optional.empty()))));
    assertEquals(
        BookkeepingQueryRejection.bookNotInitializedCode(),
        LedgerPlanStepOutcomes.missingBookCode(
            new BookWorkflowStep.AccountBalance(
                internalStepId("balance"),
                new AccountBalanceCriteria(
                    new AccountCode("1000"),
                    EffectiveDateRange.unbounded(),
                    PostingCoverage.ALL_POSTING_KINDS))));
    assertEquals(
        BookkeepingQueryRejection.bookNotInitializedCode(),
        LedgerPlanStepOutcomes.missingBookCode(
            new BookWorkflowAssertionStep(
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
        accountTaxonomy(AccountType.ASSET, NormalBalance.DEBIT));
  }

  private static PostEntryCommand postingCommand(String idempotencyKey) {
    return new PostEntryCommand(
        new BookkeepingEntry.SaleSettled(
            LocalDate.parse("2026-05-05"),
            new AccountCode("1000"),
            new AccountCode("2000"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null,
            null,
            null,
            null,
            null),
        accountingEvidence(idempotencyKey),
        new RequestProvenance(
            new CommandId("20aea0ba-3b2e-3428-af5b-f9ee3094522c"),
            new IdempotencyKey(idempotencyKey),
            new CausationId("cause-1"),
            Optional.of(new CorrelationId("corr-1"))),
        SourceChannel.CLI);
  }

  private static CommittedPosting committedPosting() {
    return new CommittedPosting(
        new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
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
        dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
        accountingEvidence("idem-3"),
        new CommittedProvenance(
            new RequestProvenance(
                new CommandId("20aea0ba-3b2e-3428-af5b-f9ee3094522c"),
                new IdempotencyKey("idem-3"),
                new CausationId("cause-1"),
                Optional.of(new CorrelationId("corr-1"))),
            FIXED_INSTANT,
            SourceChannel.CLI));
  }
}
