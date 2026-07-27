package dev.erst.fingrind.executor.workflow;

import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.CLOCK;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.EXECUTED_AT;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.POSTING_ID;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.accountDeclaration;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.accountQuery;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.balanceCriteria;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.initializedBook;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.postEntryCommand;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.postingQuery;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.stepId;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.taxRegistrationCommand;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.workflow.LedgerPlanFailure;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.executor.InMemoryBookSession;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies credential-free step dispatch never grants a mutation path. */
class LedgerPlanReadOnlyStepExecutorTest {
  @Test
  void execute_routesEveryReadStepAndRejectsEveryMutationStep() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      LedgerPlanReadOnlyStepExecutor executor =
          new LedgerPlanReadOnlyStepExecutor(bookSession, CLOCK);

      assertEquals(bookSession.inspectBook(), executor.inspectBook());
      assertTrue(executor.allowsInitializedWorkflow());
      assertMutationForbidden(
          executor.execute(
              new BookWorkflowStep.DeclareAccount(
                  stepId("declare-account"), accountDeclaration())));
      assertMutationForbidden(
          executor.execute(
              new BookWorkflowStep.DeclareTaxRegistration(
                  stepId("declare-tax"), taxRegistrationCommand())));
      assertMutationForbidden(
          executor.execute(
              new BookWorkflowStep.PostEntry(stepId("post"), postEntryCommand("post"))));

      assertInstanceOf(
          BookWorkflowJournalEntry.Rejected.class,
          executor.execute(
              new BookWorkflowStep.PreflightEntry(
                  stepId("preflight"), postEntryCommand("preflight"))));
      assertInstanceOf(
          BookWorkflowJournalEntry.Succeeded.class,
          executor.execute(new BookWorkflowStep.InspectBook(stepId("inspect"))));
      assertInstanceOf(
          BookWorkflowJournalEntry.Succeeded.class,
          executor.execute(new BookWorkflowStep.ListAccounts(stepId("accounts"), accountQuery())));
      assertInstanceOf(
          BookWorkflowJournalEntry.Rejected.class,
          executor.execute(new BookWorkflowStep.GetPosting(stepId("posting"), POSTING_ID)));
      assertInstanceOf(
          BookWorkflowJournalEntry.Succeeded.class,
          executor.execute(new BookWorkflowStep.ListPostings(stepId("postings"), postingQuery())));
      assertInstanceOf(
          BookWorkflowJournalEntry.Rejected.class,
          executor.execute(
              new BookWorkflowStep.AccountBalance(stepId("balance"), balanceCriteria())));
      assertInstanceOf(
          BookWorkflowJournalEntry.AssertionFailed.class,
          executor.execute(
              new BookWorkflowAssertionStep(
                  stepId("assert"),
                  new BookWorkflowAssertion.AccountDeclared(new AccountCode("1000")))));
    }
  }

  @Test
  void missingBookEntry_preservesTheFirstStepAndDirectsTheCallerToBookInitialization() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      LedgerPlanReadOnlyStepExecutor executor =
          new LedgerPlanReadOnlyStepExecutor(bookSession, CLOCK);
      BookWorkflowStep.InspectBook step = new BookWorkflowStep.InspectBook(stepId("inspect"));

      BookWorkflowJournalEntry.Rejected rejected = executor.missingBookEntry(step, EXECUTED_AT);

      assertEquals(step.stepId(), rejected.stepId());
      assertEquals("query-book-not-initialized", rejected.failure().code());
      assertTrue(rejected.failure().message().contains("open-book"));
      assertEquals(List.of(), rejected.facts());
    }
  }

  private static void assertMutationForbidden(BookWorkflowJournalEntry entry) {
    BookWorkflowJournalEntry.Rejected rejected =
        assertInstanceOf(BookWorkflowJournalEntry.Rejected.class, entry);

    assertEquals(
        LedgerPlanFailure.READ_ONLY_PLAN_MUTATION_FORBIDDEN.code(), rejected.failure().code());
    assertTrue(
        rejected.failure().facts().stream()
            .anyMatch(
                fact ->
                    fact instanceof BookWorkflowFact.Text text && "stepId".equals(text.name())));
  }
}
