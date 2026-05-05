package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.LedgerAssertion;
import dev.erst.fingrind.contract.LedgerJournalStep;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.LedgerPlanId;
import dev.erst.fingrind.contract.LedgerStep;
import dev.erst.fingrind.contract.LedgerStepId;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import java.util.Objects;

/** Translates between the public execute-plan schema and the local workflow model. */
public final class BookWorkflowPublishedLanguageTranslator {
  private BookWorkflowPublishedLanguageTranslator() {}

  /** Translates one public ledger plan into the local workflow model. */
  public static BookWorkflowPlan fromPublished(LedgerPlan plan) {
    Objects.requireNonNull(plan, "plan");
    return new BookWorkflowPlan(
        plan.planId().value(),
        plan.steps().stream().map(BookWorkflowPublishedLanguageTranslator::fromPublished).toList());
  }

  /** Translates one workflow plan identifier into the public contract wrapper. */
  public static LedgerPlanId toPublishedPlanId(String planId) {
    Objects.requireNonNull(planId, "planId");
    return new LedgerPlanId(planId);
  }

  /** Translates one internal workflow step identifier into the public contract wrapper. */
  public static LedgerStepId toPublishedStepId(String stepId) {
    Objects.requireNonNull(stepId, "stepId");
    return new LedgerStepId(stepId);
  }

  /** Projects one internal workflow step into the public journal step identity. */
  public static LedgerJournalStep toPublishedJournalStep(BookWorkflowStep step) {
    Objects.requireNonNull(step, "step");
    return switch (step) {
      case BookWorkflowStep.OpenBook _ -> LedgerJournalStep.standard(LedgerStepKind.OPEN_BOOK);
      case BookWorkflowStep.DeclareAccount _ ->
          LedgerJournalStep.standard(LedgerStepKind.DECLARE_ACCOUNT);
      case BookWorkflowStep.PreflightEntry _ ->
          LedgerJournalStep.standard(LedgerStepKind.PREFLIGHT_ENTRY);
      case BookWorkflowStep.PostEntry _ -> LedgerJournalStep.standard(LedgerStepKind.POST_ENTRY);
      case BookWorkflowStep.InspectBook _ ->
          LedgerJournalStep.standard(LedgerStepKind.INSPECT_BOOK);
      case BookWorkflowStep.ListAccounts _ ->
          LedgerJournalStep.standard(LedgerStepKind.LIST_ACCOUNTS);
      case BookWorkflowStep.GetPosting _ -> LedgerJournalStep.standard(LedgerStepKind.GET_POSTING);
      case BookWorkflowStep.ListPostings _ ->
          LedgerJournalStep.standard(LedgerStepKind.LIST_POSTINGS);
      case BookWorkflowStep.AccountBalance _ ->
          LedgerJournalStep.standard(LedgerStepKind.ACCOUNT_BALANCE);
      case BookWorkflowStep.Assert assertion ->
          LedgerJournalStep.assertion(assertionKind(assertion.assertion()));
    };
  }

  private static BookWorkflowStep fromPublished(LedgerStep step) {
    return switch (step) {
      case LedgerStep.OpenBook openBook -> new BookWorkflowStep.OpenBook(openBook.stepId().value());
      case LedgerStep.DeclareAccount declareAccount ->
          new BookWorkflowStep.DeclareAccount(
              declareAccount.stepId().value(),
              BookkeepingPublishedLanguageTranslator.fromPublished(declareAccount.command()));
      case LedgerStep.PreflightEntry preflightEntry ->
          new BookWorkflowStep.PreflightEntry(
              preflightEntry.stepId().value(),
              BookkeepingPublishedLanguageTranslator.fromPublished(preflightEntry.command()));
      case LedgerStep.PostEntry postEntry ->
          new BookWorkflowStep.PostEntry(
              postEntry.stepId().value(),
              BookkeepingPublishedLanguageTranslator.fromPublished(postEntry.command()));
      case LedgerStep.InspectBook inspectBook ->
          new BookWorkflowStep.InspectBook(inspectBook.stepId().value());
      case LedgerStep.ListAccounts listAccounts ->
          new BookWorkflowStep.ListAccounts(listAccounts.stepId().value(), listAccounts.query());
      case LedgerStep.GetPosting getPosting ->
          new BookWorkflowStep.GetPosting(getPosting.stepId().value(), getPosting.postingId());
      case LedgerStep.ListPostings listPostings ->
          new BookWorkflowStep.ListPostings(listPostings.stepId().value(), listPostings.query());
      case LedgerStep.AccountBalance accountBalance ->
          new BookWorkflowStep.AccountBalance(
              accountBalance.stepId().value(), accountBalance.query());
      case LedgerStep.Assert assertion ->
          new BookWorkflowStep.Assert(
              assertion.stepId().value(), fromPublished(assertion.assertion()));
    };
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
              balanceEquals.effectiveDateFrom().orElse(null),
              balanceEquals.effectiveDateTo().orElse(null),
              balanceEquals.netAmount(),
              balanceEquals.balanceSide());
    };
  }

  private static LedgerAssertionKind assertionKind(BookWorkflowAssertion assertion) {
    Objects.requireNonNull(assertion, "assertion");
    return switch (assertion) {
      case BookWorkflowAssertion.AccountDeclared _ -> LedgerAssertionKind.ACCOUNT_DECLARED;
      case BookWorkflowAssertion.AccountActive _ -> LedgerAssertionKind.ACCOUNT_ACTIVE;
      case BookWorkflowAssertion.PostingExists _ -> LedgerAssertionKind.POSTING_EXISTS;
      case BookWorkflowAssertion.AccountBalanceEquals _ ->
          LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS;
    };
  }
}
