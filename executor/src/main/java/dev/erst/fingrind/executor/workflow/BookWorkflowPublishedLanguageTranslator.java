package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.workflow.LedgerAssertion;
import dev.erst.fingrind.contract.workflow.LedgerExecutionJournal;
import dev.erst.fingrind.contract.workflow.LedgerJournalEntry;
import dev.erst.fingrind.contract.workflow.LedgerJournalStep;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanId;
import dev.erst.fingrind.contract.workflow.LedgerStep;
import dev.erst.fingrind.contract.workflow.LedgerStepId;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryCursor;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryCursor;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import java.util.Objects;

/** Translates between the public execute-plan schema and the local workflow model. */
public final class BookWorkflowPublishedLanguageTranslator {
  private BookWorkflowPublishedLanguageTranslator() {}

  /** Translates one public ledger plan into the local workflow model. */
  public static BookWorkflowPlan fromPublished(LedgerPlan plan) {
    Objects.requireNonNull(plan, "plan");
    return new BookWorkflowPlan(
        fromPublished(plan.planId()),
        plan.steps().stream().map(BookWorkflowPublishedLanguageTranslator::fromPublished).toList());
  }

  /** Translates one workflow plan identifier into the public contract wrapper. */
  public static LedgerPlanId toPublishedPlanId(BookWorkflowPlanId planId) {
    Objects.requireNonNull(planId, "planId");
    return new LedgerPlanId(planId.value());
  }

  /** Translates one internal workflow step identifier into the public contract wrapper. */
  public static LedgerStepId toPublishedStepId(BookWorkflowStepId stepId) {
    Objects.requireNonNull(stepId, "stepId");
    return new LedgerStepId(stepId.value());
  }

  /** Projects one internal workflow journal descriptor into the public journal-step identity. */
  public static LedgerJournalStep toPublishedJournalStep(BookWorkflowJournalDescriptor descriptor) {
    return BookWorkflowPublishedJournalTranslator.toPublishedJournalStep(descriptor);
  }

  /** Projects one internal workflow journal entry into the public journal entry. */
  public static LedgerJournalEntry toPublished(BookWorkflowJournalEntry entry) {
    return BookWorkflowPublishedJournalTranslator.toPublished(entry);
  }

  /** Projects one internal workflow execution journal into the public journal record. */
  public static LedgerExecutionJournal toPublished(BookWorkflowExecutionJournal journal) {
    return BookWorkflowPublishedJournalTranslator.toPublished(journal);
  }

  private static BookWorkflowStep fromPublished(LedgerStep step) {
    return switch (step) {
      case LedgerStep.OpenBook openBook ->
          new BookWorkflowStep.OpenBook(
              fromPublished(openBook.stepId()),
              BookkeepingPublishedLanguageTranslator.fromPublished(openBook.command()));
      case LedgerStep.DeclareAccount declareAccount ->
          new BookWorkflowStep.DeclareAccount(
              fromPublished(declareAccount.stepId()),
              BookkeepingPublishedLanguageTranslator.fromPublished(declareAccount.command()));
      case LedgerStep.PreflightEntry preflightEntry ->
          new BookWorkflowStep.PreflightEntry(
              fromPublished(preflightEntry.stepId()), preflightEntry.command());
      case LedgerStep.PostEntry postEntry ->
          new BookWorkflowStep.PostEntry(fromPublished(postEntry.stepId()), postEntry.command());
      case LedgerStep.InspectBook inspectBook ->
          new BookWorkflowStep.InspectBook(fromPublished(inspectBook.stepId()));
      case LedgerStep.ListAccounts listAccounts ->
          new BookWorkflowStep.ListAccounts(
              fromPublished(listAccounts.stepId()), fromPublished(listAccounts.query()));
      case LedgerStep.GetPosting getPosting ->
          new BookWorkflowStep.GetPosting(
              fromPublished(getPosting.stepId()), getPosting.postingId());
      case LedgerStep.ListPostings listPostings ->
          new BookWorkflowStep.ListPostings(
              fromPublished(listPostings.stepId()), fromPublished(listPostings.query()));
      case LedgerStep.AccountBalance accountBalance ->
          new BookWorkflowStep.AccountBalance(
              fromPublished(accountBalance.stepId()), fromPublished(accountBalance.query()));
      case LedgerStep.Assert assertion ->
          new BookWorkflowStep.Assert(
              fromPublished(assertion.stepId()), fromPublished(assertion.assertion()));
    };
  }

  private static BookWorkflowPlanId fromPublished(LedgerPlanId planId) {
    Objects.requireNonNull(planId, "planId");
    return new BookWorkflowPlanId(planId.value());
  }

  private static BookWorkflowStepId fromPublished(LedgerStepId stepId) {
    Objects.requireNonNull(stepId, "stepId");
    return new BookWorkflowStepId(stepId.value());
  }

  private static BookWorkflowAssertion fromPublished(LedgerAssertion assertion) {
    Objects.requireNonNull(assertion, "assertion");
    return switch (assertion) {
      case LedgerAssertion.AccountDeclared accountDeclared ->
          new BookWorkflowAssertion.AccountDeclared(accountDeclared.accountCode());
      case LedgerAssertion.AccountActive accountActive ->
          new BookWorkflowAssertion.AccountActive(accountActive.accountCode());
      case LedgerAssertion.PostingExists postingExists ->
          new BookWorkflowAssertion.PostingExists(postingExists.postingId());
      case LedgerAssertion.AccountBalanceEquals balanceEquals ->
          new BookWorkflowAssertion.AccountBalanceEquals(
              balanceEquals.accountCode(),
              EffectiveDateRange.of(
                  balanceEquals.effectiveDateFrom().orElse(null),
                  balanceEquals.effectiveDateTo().orElse(null)),
              balanceEquals.netAmount(),
              balanceEquals.balanceSide());
    };
  }

  private static AccountRegistryQuery fromPublished(
      dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery query) {
    Objects.requireNonNull(query, "query");
    return new AccountRegistryQuery(
        query.limit(),
        query.cursor().map(cursor -> new AccountRegistryCursor(cursor.accountCode())));
  }

  private static PostingHistoryQuery fromPublished(
      dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery query) {
    Objects.requireNonNull(query, "query");
    return new PostingHistoryQuery(
        query.accountCode(),
        query.effectiveDateRange(),
        query.limit(),
        query
            .cursor()
            .map(
                cursor ->
                    new PostingHistoryCursor(
                        cursor.effectiveDate(), cursor.recordedAt(), cursor.postingId())));
  }

  private static AccountBalanceCriteria fromPublished(
      dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery query) {
    Objects.requireNonNull(query, "query");
    return new AccountBalanceCriteria(
        query.accountCode(), query.effectiveDateRange(), query.postingCoverage());
  }
}
