package dev.erst.fingrind.executor.workflow;

import static dev.erst.fingrind.executor.NullTestSupport.nullOf;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.accountDeclaration;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.accountQuery;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.balanceCriteria;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.postEntryCommand;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.postingQuery;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.stepId;
import static dev.erst.fingrind.executor.workflow.BookWorkflowTestFixtures.taxRegistrationCommand;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for executor-owned workflow-plan validation. */
class BookWorkflowPlanTest {

  @Test
  void bookWorkflowPlan_rejectsBlankPlanId() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookWorkflowPlan(
                new BookWorkflowPlanId("   "),
                List.of(new BookWorkflowStep.InspectBook(stepId("inspect")))));
  }

  @Test
  void bookWorkflowPlan_rejectsMissingSteps() {
    assertEquals(
        "steps",
        assertThrows(
                NullPointerException.class, () -> new BookWorkflowPlan(planId("plan-1"), nullOf()))
            .getMessage());
  }

  @Test
  void bookWorkflowPlan_rejectsEmptySteps() {
    assertEquals(
        "Workflow plan must contain at least one step.",
        assertThrows(
                IllegalArgumentException.class,
                () -> new BookWorkflowPlan(planId("plan-1"), List.of()))
            .getMessage());
  }

  @Test
  void bookWorkflowPlan_reportsWhetherTheFirstStepEnsuresTheBook() {
    BookWorkflowPlan inspectionPlan =
        new BookWorkflowPlan(
            planId("plan-2"), List.of(new BookWorkflowStep.InspectBook(stepId("inspect"))));
    assertEquals(planId("plan-2"), inspectionPlan.planId());
  }

  @Test
  void bookWorkflowPlan_classifiesEveryStepFamilyAndReturnsTheFirstMutation() {
    BookWorkflowStep.DeclareAccount declareAccount =
        new BookWorkflowStep.DeclareAccount(stepId("declare-account"), accountDeclaration());
    BookWorkflowStep.DeclareTaxRegistration declareTaxRegistration =
        new BookWorkflowStep.DeclareTaxRegistration(
            stepId("declare-tax"), taxRegistrationCommand());
    BookWorkflowStep.PostEntry postEntry =
        new BookWorkflowStep.PostEntry(stepId("post"), postEntryCommand("post"));

    assertMutation(declareAccount);
    assertMutation(declareTaxRegistration);
    assertMutation(postEntry);
    assertReadOnly(
        new BookWorkflowStep.PreflightEntry(stepId("preflight"), postEntryCommand("pre")));
    assertReadOnly(new BookWorkflowStep.InspectBook(stepId("inspect")));
    assertReadOnly(new BookWorkflowStep.ListAccounts(stepId("accounts"), accountQuery()));
    assertReadOnly(
        new BookWorkflowStep.GetPosting(
            stepId("posting"), new PostingId("019f8e4b-771b-7b8b-8f7f-4ce8e1b2ea8c")));
    assertReadOnly(new BookWorkflowStep.ListPostings(stepId("postings"), postingQuery()));
    assertReadOnly(new BookWorkflowStep.AccountBalance(stepId("balance"), balanceCriteria()));
    assertReadOnly(
        new BookWorkflowAssertionStep(
            stepId("assert"), new BookWorkflowAssertion.AccountDeclared(new AccountCode("1000"))));

    BookWorkflowPlan mixedPlan =
        new BookWorkflowPlan(
            planId("mixed-plan"),
            List.of(
                new BookWorkflowStep.InspectBook(stepId("before")),
                declareTaxRegistration,
                declareAccount,
                postEntry));

    assertEquals(declareTaxRegistration, mixedPlan.firstBookMutationStep().orElseThrow());
  }

  @Test
  void bookWorkflowStep_rejectsBlankStepIds() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new BookWorkflowStep.InspectBook(new BookWorkflowStepId(" \t ")));
  }

  private static BookWorkflowPlanId planId(String value) {
    return new BookWorkflowPlanId(value);
  }

  private static void assertMutation(BookWorkflowStep step) {
    BookWorkflowPlan plan =
        new BookWorkflowPlan(planId("mutation-" + step.stepId().value()), List.of(step));

    assertTrue(step.mutatesBook());
    assertTrue(plan.containsBookMutation());
    assertEquals(step, plan.firstBookMutationStep().orElseThrow());
  }

  private static void assertReadOnly(BookWorkflowStep step) {
    BookWorkflowPlan plan =
        new BookWorkflowPlan(planId("read-" + step.stepId().value()), List.of(step));

    assertFalse(step.mutatesBook());
    assertFalse(plan.containsBookMutation());
    assertTrue(plan.firstBookMutationStep().isEmpty());
  }
}
