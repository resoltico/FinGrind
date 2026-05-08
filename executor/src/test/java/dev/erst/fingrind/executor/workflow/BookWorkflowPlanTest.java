package dev.erst.fingrind.executor.workflow;

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
        () -> new BookWorkflowPlan("   ", List.of(new BookWorkflowStep.InspectBook("inspect"))));
  }

  @Test
  void bookWorkflowPlan_rejectsMissingSteps() {
    assertEquals(
        "steps",
        assertThrows(NullPointerException.class, () -> new BookWorkflowPlan("plan-1", nullOf()))
            .getMessage());
  }

  @Test
  void bookWorkflowPlan_rejectsEmptySteps() {
    assertEquals(
        "Workflow plan must contain at least one step.",
        assertThrows(
                IllegalArgumentException.class, () -> new BookWorkflowPlan("plan-1", List.of()))
            .getMessage());
  }

  @Test
  void bookWorkflowPlan_rejectsOversizedStepCollections() {
    List<BookWorkflowStep> oversizedSteps =
        IntStream.range(0, InteractionLimits.LEDGER_PLAN_STEP_MAX + 1)
            .<BookWorkflowStep>mapToObj(index -> new BookWorkflowStep.InspectBook("step-" + index))
            .toList();
    assertThrows(
        IllegalArgumentException.class, () -> new BookWorkflowPlan("plan-1", oversizedSteps));
  }

  @Test
  void bookWorkflowPlan_rejectsDuplicateStepIds() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookWorkflowPlan(
                "plan-1",
                List.of(
                    new BookWorkflowStep.InspectBook("duplicate"),
                    new BookWorkflowStep.InspectBook("duplicate"))));
  }

  @Test
  void bookWorkflowPlan_rejectsOpenBookOutsideTheFirstStep() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookWorkflowPlan(
                "plan-1",
                List.of(
                    new BookWorkflowStep.InspectBook("inspect"),
                    new BookWorkflowStep.OpenBook("open"))));
  }

  @Test
  void bookWorkflowPlan_reportsWhetherTheFirstStepOpensTheBook() {
    BookWorkflowPlan openBookPlan =
        new BookWorkflowPlan("plan-1", List.of(new BookWorkflowStep.OpenBook("open")));
    BookWorkflowPlan inspectionPlan =
        new BookWorkflowPlan("plan-2", List.of(new BookWorkflowStep.InspectBook("inspect")));
    assertTrue(openBookPlan.beginsWithOpenBook());
    assertFalse(inspectionPlan.beginsWithOpenBook());
  }

  @Test
  void bookWorkflowStep_rejectsBlankStepIds() {
    assertThrows(IllegalArgumentException.class, () -> new BookWorkflowStep.InspectBook(" \t "));
  }
}
