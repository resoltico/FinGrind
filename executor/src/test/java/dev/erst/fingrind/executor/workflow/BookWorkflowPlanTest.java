package dev.erst.fingrind.executor.workflow;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static dev.erst.fingrind.executor.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.InteractionLimits;
import java.util.List;
import java.util.stream.IntStream;
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
  void bookWorkflowPlan_rejectsOversizedStepCollections() {
    List<BookWorkflowStep> oversizedSteps =
        IntStream.range(0, InteractionLimits.LEDGER_PLAN_STEP_MAX + 1)
            .<BookWorkflowStep>mapToObj(
                index -> new BookWorkflowStep.InspectBook(stepId("step-" + index)))
            .toList();
    assertThrows(
        IllegalArgumentException.class,
        () -> new BookWorkflowPlan(planId("plan-1"), oversizedSteps));
  }

  @Test
  void bookWorkflowPlan_rejectsDuplicateStepIds() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookWorkflowPlan(
                planId("plan-1"),
                List.of(
                    new BookWorkflowStep.InspectBook(stepId("duplicate")),
                    new BookWorkflowStep.InspectBook(stepId("duplicate")))));
  }

  @Test
  void bookWorkflowPlan_rejectsOpenBookOutsideTheFirstStep() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookWorkflowPlan(
                planId("plan-1"),
                List.of(
                    new BookWorkflowStep.InspectBook(stepId("inspect")),
                    new BookWorkflowStep.OpenBook(stepId("open"), bookIdentity()))));
  }

  @Test
  void bookWorkflowPlan_reportsWhetherTheFirstStepOpensTheBook() {
    BookWorkflowPlan openBookPlan =
        new BookWorkflowPlan(
            planId("plan-1"),
            List.of(new BookWorkflowStep.OpenBook(stepId("open"), bookIdentity())));
    BookWorkflowPlan inspectionPlan =
        new BookWorkflowPlan(
            planId("plan-2"), List.of(new BookWorkflowStep.InspectBook(stepId("inspect"))));
    assertTrue(openBookPlan.beginsWithOpenBook());
    assertFalse(inspectionPlan.beginsWithOpenBook());
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
