package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.executor.workflow.BookWorkflowFact;
import dev.erst.fingrind.executor.workflow.BookWorkflowFailure;
import dev.erst.fingrind.executor.workflow.LedgerPlanStepOutcome;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers constructor invariants for internal ledger-plan step outcomes. */
class LedgerPlanStepOutcomeTest {
  @Test
  void succeeded_rejectsNullFactsWithContext() {
    assertEquals(
        "facts",
        assertThrows(
                NullPointerException.class, () -> new LedgerPlanStepOutcome.Succeeded(nullOf()))
            .getMessage());
  }

  @Test
  void succeeded_defensivelyCopiesFacts() {
    List<BookWorkflowFact> facts =
        new ArrayList<>(List.of(BookWorkflowFact.text("postingId", "posting-1")));
    LedgerPlanStepOutcome.Succeeded outcome = new LedgerPlanStepOutcome.Succeeded(facts);
    facts.clear();
    assertEquals(List.of(BookWorkflowFact.text("postingId", "posting-1")), outcome.facts());
  }

  @Test
  void failureOutcomes_delegateFactsFromFailurePayload() {
    BookWorkflowFailure failure =
        new BookWorkflowFailure(
            "rejected", "Rejected.", List.of(BookWorkflowFact.text("code", "unknown")));
    assertEquals(failure.facts(), new LedgerPlanStepOutcome.Rejected(failure).facts());
    assertEquals(failure.facts(), new LedgerPlanStepOutcome.AssertionFailed(failure).facts());
  }
}
