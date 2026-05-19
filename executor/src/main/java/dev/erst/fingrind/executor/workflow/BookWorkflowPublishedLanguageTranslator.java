package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.workflow.LedgerAssertion;
import dev.erst.fingrind.contract.workflow.LedgerBoundaryPhase;
import dev.erst.fingrind.contract.workflow.LedgerExecutionJournal;
import dev.erst.fingrind.contract.workflow.LedgerFact;
import dev.erst.fingrind.contract.workflow.LedgerJournalEntry;
import dev.erst.fingrind.contract.workflow.LedgerJournalStep;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanId;
import dev.erst.fingrind.contract.workflow.LedgerStep;
import dev.erst.fingrind.contract.workflow.LedgerStepFailure;
import dev.erst.fingrind.contract.workflow.LedgerStepId;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingReadPublishedLanguageTranslator;
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
    Objects.requireNonNull(descriptor, "descriptor");
    return switch (descriptor) {
      case BookWorkflowJournalDescriptor.Step stepDescriptor ->
          toPublishedJournalStep(stepDescriptor.step());
      case BookWorkflowJournalDescriptor.Boundary boundary ->
          LedgerJournalStep.boundary(toPublishedBoundaryPhase(boundary.phase()));
    };
  }

  /** Projects one internal workflow journal entry into the public journal entry. */
  public static LedgerJournalEntry toPublished(BookWorkflowJournalEntry entry) {
    Objects.requireNonNull(entry, "entry");
    return switch (entry) {
      case BookWorkflowJournalEntry.Succeeded succeeded ->
          new LedgerJournalEntry.Succeeded(
              toPublishedStepId(succeeded.stepId()),
              toPublishedJournalStep(succeeded.descriptor()),
              succeeded.startedAt(),
              succeeded.finishedAt(),
              succeeded.facts().stream()
                  .map(BookWorkflowPublishedLanguageTranslator::toPublished)
                  .toList());
      case BookWorkflowJournalEntry.Rejected rejected ->
          new LedgerJournalEntry.Rejected(
              toPublishedStepId(rejected.stepId()),
              toPublishedJournalStep(rejected.descriptor()),
              rejected.startedAt(),
              rejected.finishedAt(),
              rejected.facts().stream()
                  .map(BookWorkflowPublishedLanguageTranslator::toPublished)
                  .toList(),
              toPublished(rejected.failure()));
      case BookWorkflowJournalEntry.AssertionFailed assertionFailed ->
          new LedgerJournalEntry.AssertionFailed(
              toPublishedStepId(assertionFailed.stepId()),
              toPublishedJournalStep(assertionFailed.descriptor()),
              assertionFailed.startedAt(),
              assertionFailed.finishedAt(),
              assertionFailed.facts().stream()
                  .map(BookWorkflowPublishedLanguageTranslator::toPublished)
                  .toList(),
              toPublished(assertionFailed.failure()));
    };
  }

  /** Projects one internal workflow execution journal into the public journal record. */
  public static LedgerExecutionJournal toPublished(BookWorkflowExecutionJournal journal) {
    Objects.requireNonNull(journal, "journal");
    return new LedgerExecutionJournal(
        journal.startedAt(),
        journal.finishedAt(),
        journal.entries().stream()
            .map(BookWorkflowPublishedLanguageTranslator::toPublished)
            .toList());
  }

  private static LedgerStepFailure toPublished(BookWorkflowFailure failure) {
    Objects.requireNonNull(failure, "failure");
    return new LedgerStepFailure(
        failure.code(),
        failure.message(),
        failure.facts().stream()
            .map(BookWorkflowPublishedLanguageTranslator::toPublished)
            .toList());
  }

  private static LedgerFact toPublished(BookWorkflowFact fact) {
    Objects.requireNonNull(fact, "fact");
    return switch (fact) {
      case BookWorkflowFact.Text text -> LedgerFact.text(text.name(), text.value());
      case BookWorkflowFact.Flag flag -> LedgerFact.flag(flag.name(), flag.value());
      case BookWorkflowFact.Count count -> LedgerFact.count(count.name(), count.value());
      case BookWorkflowFact.Money money -> LedgerFact.money(money.name(), money.value());
      case BookWorkflowFact.Group group ->
          LedgerFact.group(
              group.name(),
              group.facts().stream()
                  .map(BookWorkflowPublishedLanguageTranslator::toPublished)
                  .toList());
    };
  }

  private static LedgerBoundaryPhase toPublishedBoundaryPhase(BookWorkflowBoundaryPhase phase) {
    Objects.requireNonNull(phase, "phase");
    return switch (phase) {
      case BEGIN -> LedgerBoundaryPhase.BEGIN;
      case INITIALIZATION_CHECK -> LedgerBoundaryPhase.INITIALIZATION_CHECK;
      case COMMIT -> LedgerBoundaryPhase.COMMIT;
      case ROLLBACK -> LedgerBoundaryPhase.ROLLBACK;
    };
  }

  private static LedgerJournalStep toPublishedJournalStep(BookWorkflowStep step) {
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
              fromPublished(preflightEntry.stepId()),
              BookkeepingPublishedLanguageTranslator.fromPublished(preflightEntry.command()));
      case LedgerStep.PostEntry postEntry ->
          new BookWorkflowStep.PostEntry(
              fromPublished(postEntry.stepId()),
              BookkeepingPublishedLanguageTranslator.fromPublished(postEntry.command()));
      case LedgerStep.InspectBook inspectBook ->
          new BookWorkflowStep.InspectBook(fromPublished(inspectBook.stepId()));
      case LedgerStep.ListAccounts listAccounts ->
          new BookWorkflowStep.ListAccounts(
              fromPublished(listAccounts.stepId()),
              BookkeepingReadPublishedLanguageTranslator.fromPublished(listAccounts.query()));
      case LedgerStep.GetPosting getPosting ->
          new BookWorkflowStep.GetPosting(
              fromPublished(getPosting.stepId()), getPosting.postingId());
      case LedgerStep.ListPostings listPostings ->
          new BookWorkflowStep.ListPostings(
              fromPublished(listPostings.stepId()),
              BookkeepingReadPublishedLanguageTranslator.fromPublished(listPostings.query()));
      case LedgerStep.AccountBalance accountBalance ->
          new BookWorkflowStep.AccountBalance(
              fromPublished(accountBalance.stepId()),
              BookkeepingReadPublishedLanguageTranslator.fromPublished(accountBalance.query()));
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
