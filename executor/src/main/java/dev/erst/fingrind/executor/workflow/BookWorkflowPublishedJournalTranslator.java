package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.workflow.LedgerBoundaryCheckpoint;
import dev.erst.fingrind.contract.workflow.LedgerExecutionJournal;
import dev.erst.fingrind.contract.workflow.LedgerFact;
import dev.erst.fingrind.contract.workflow.LedgerJournalEntry;
import dev.erst.fingrind.contract.workflow.LedgerJournalStep;
import dev.erst.fingrind.contract.workflow.LedgerStepFailure;
import java.util.Objects;

/** Projects local workflow journal language into the published execute-plan contract. */
final class BookWorkflowPublishedJournalTranslator {
  private BookWorkflowPublishedJournalTranslator() {}

  static LedgerJournalStep toPublishedJournalStep(BookWorkflowJournalDescriptor descriptor) {
    Objects.requireNonNull(descriptor, "descriptor");
    return switch (descriptor) {
      case BookWorkflowJournalDescriptor.Step stepDescriptor ->
          toPublishedJournalStep(stepDescriptor.step());
      case BookWorkflowJournalDescriptor.Boundary boundary ->
          LedgerJournalStep.boundary(toPublishedBoundaryCheckpoint(boundary.checkpoint()));
    };
  }

  static LedgerJournalEntry toPublished(BookWorkflowJournalEntry entry) {
    Objects.requireNonNull(entry, "entry");
    return switch (entry) {
      case BookWorkflowJournalEntry.Succeeded succeeded ->
          new LedgerJournalEntry.Succeeded(
              BookWorkflowPublishedLanguageTranslator.toPublishedStepId(succeeded.stepId()),
              toPublishedJournalStep(succeeded.descriptor()),
              succeeded.startedAt(),
              succeeded.finishedAt(),
              succeeded.facts().stream()
                  .map(BookWorkflowPublishedJournalTranslator::toPublished)
                  .toList());
      case BookWorkflowJournalEntry.Rejected rejected ->
          new LedgerJournalEntry.Rejected(
              BookWorkflowPublishedLanguageTranslator.toPublishedStepId(rejected.stepId()),
              toPublishedJournalStep(rejected.descriptor()),
              rejected.startedAt(),
              rejected.finishedAt(),
              rejected.facts().stream()
                  .map(BookWorkflowPublishedJournalTranslator::toPublished)
                  .toList(),
              toPublished(rejected.failure()));
      case BookWorkflowJournalEntry.AssertionFailed assertionFailed ->
          new LedgerJournalEntry.AssertionFailed(
              BookWorkflowPublishedLanguageTranslator.toPublishedStepId(assertionFailed.stepId()),
              toPublishedJournalStep(assertionFailed.descriptor()),
              assertionFailed.startedAt(),
              assertionFailed.finishedAt(),
              assertionFailed.facts().stream()
                  .map(BookWorkflowPublishedJournalTranslator::toPublished)
                  .toList(),
              toPublished(assertionFailed.failure()));
    };
  }

  static LedgerExecutionJournal toPublished(BookWorkflowExecutionJournal journal) {
    Objects.requireNonNull(journal, "journal");
    return new LedgerExecutionJournal(
        journal.startedAt(),
        journal.finishedAt(),
        journal.entries().stream()
            .map(BookWorkflowPublishedJournalTranslator::toPublished)
            .toList());
  }

  static LedgerAssertionKind assertionKind(BookWorkflowAssertion assertion) {
    Objects.requireNonNull(assertion, "assertion");
    return switch (assertion) {
      case BookWorkflowAssertion.AccountDeclared _ -> LedgerAssertionKind.ACCOUNT_DECLARED;
      case BookWorkflowAssertion.AccountActive _ -> LedgerAssertionKind.ACCOUNT_ACTIVE;
      case BookWorkflowAssertion.PostingExists _ -> LedgerAssertionKind.POSTING_EXISTS;
      case BookWorkflowAssertion.AccountBalanceEquals _ ->
          LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS;
    };
  }

  private static LedgerStepFailure toPublished(BookWorkflowFailure failure) {
    Objects.requireNonNull(failure, "failure");
    return new LedgerStepFailure(
        failure.code(),
        failure.message(),
        failure.facts().stream().map(BookWorkflowPublishedJournalTranslator::toPublished).toList());
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
                  .map(BookWorkflowPublishedJournalTranslator::toPublished)
                  .toList());
    };
  }

  private static LedgerBoundaryCheckpoint toPublishedBoundaryCheckpoint(
      BookWorkflowBoundaryCheckpoint checkpoint) {
    Objects.requireNonNull(checkpoint, "checkpoint");
    return switch (checkpoint) {
      case BEGIN -> LedgerBoundaryCheckpoint.BEGIN;
      case INITIALIZATION_CHECK -> LedgerBoundaryCheckpoint.INITIALIZATION_CHECK;
      case COMMIT -> LedgerBoundaryCheckpoint.COMMIT;
      case ROLLBACK -> LedgerBoundaryCheckpoint.ROLLBACK;
    };
  }

  private static LedgerJournalStep toPublishedJournalStep(BookWorkflowStep step) {
    Objects.requireNonNull(step, "step");
    return switch (step) {
      case BookWorkflowStep.DeclareAccount _ ->
          LedgerJournalStep.standard(LedgerStepKind.DECLARE_ACCOUNT);
      case BookWorkflowStep.DeclareTaxRegistration _ ->
          LedgerJournalStep.standard(LedgerStepKind.DECLARE_TAX_REGISTRATION);
      case BookWorkflowStep.PreflightEntry _ ->
          LedgerJournalStep.standard(LedgerStepKind.PREFLIGHT_ENTRY);
      case BookWorkflowStep.PostEntry postEntry ->
          LedgerJournalStep.standard(
              LedgerStepKind.forCommittedEntryKind(postEntry.command().entry().entryKind()));
      case BookWorkflowStep.InspectBook _ ->
          LedgerJournalStep.standard(LedgerStepKind.INSPECT_BOOK);
      case BookWorkflowStep.ListAccounts _ ->
          LedgerJournalStep.standard(LedgerStepKind.LIST_ACCOUNTS);
      case BookWorkflowStep.GetPosting _ -> LedgerJournalStep.standard(LedgerStepKind.GET_POSTING);
      case BookWorkflowStep.ListPostings _ ->
          LedgerJournalStep.standard(LedgerStepKind.LIST_POSTINGS);
      case BookWorkflowStep.AccountBalance _ ->
          LedgerJournalStep.standard(LedgerStepKind.ACCOUNT_BALANCE);
      case BookWorkflowAssertionStep assertion ->
          LedgerJournalStep.assertion(assertionKind(assertion.assertion()));
    };
  }
}
