package dev.erst.fingrind.executor.workflow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.ProtocolLimits;
import java.util.List;
import java.util.stream.IntStream;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Unit tests for executor-owned workflow-plan validation. */
@NullUnmarked
class BookWorkflowPlanTest {
  @Test
  void bookWorkflowPlan_rejectsBlankPlanId() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new BookWorkflowPlan("   ", List.of(new BookWorkflowStep.InspectBook("inspect"))));
  }

  @Test
  void bookWorkflowPlan_rejectsMissingStepsAfterNormalization() {
    @SuppressWarnings("NullAway")
    List<BookWorkflowStep> missingSteps = null;
    assertThrows(
        IllegalArgumentException.class, () -> new BookWorkflowPlan("plan-1", missingSteps));
  }

  @Test
  void bookWorkflowPlan_rejectsOversizedStepCollections() {
    List<BookWorkflowStep> oversizedSteps =
        IntStream.range(0, ProtocolLimits.LEDGER_PLAN_STEP_MAX + 1)
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
