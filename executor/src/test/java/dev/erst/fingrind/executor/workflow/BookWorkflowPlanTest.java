package dev.erst.fingrind.executor.workflow;

import static dev.erst.fingrind.executor.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
  void bookWorkflowStep_rejectsBlankStepIds() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new BookWorkflowStep.InspectBook(new BookWorkflowStepId(" \t ")));
  }

  private static BookWorkflowPlanId planId(String value) {
    return new BookWorkflowPlanId(value);
  }

  private static BookWorkflowStepId stepId(String value) {
    return new BookWorkflowStepId(value);
  }
}
