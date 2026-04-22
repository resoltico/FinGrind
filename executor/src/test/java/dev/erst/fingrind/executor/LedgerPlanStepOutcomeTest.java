package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.LedgerFact;
import dev.erst.fingrind.contract.LedgerStepFailure;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Covers constructor invariants for internal ledger-plan step outcomes. */
@NullUnmarked
class LedgerPlanStepOutcomeTest {
  @Test
  void succeeded_coalescesNullFactsToEmptyImmutableList() {
    LedgerPlanStepOutcome.Succeeded outcome = new LedgerPlanStepOutcome.Succeeded(null);

    assertEquals(List.of(), outcome.facts());
    assertThrows(
        UnsupportedOperationException.class,
        () -> outcome.facts().add(LedgerFact.text("ignored", "ignored")));
  }

  @Test
  void succeeded_defensivelyCopiesFacts() {
    List<LedgerFact> facts = new ArrayList<>(List.of(LedgerFact.text("postingId", "posting-1")));

    LedgerPlanStepOutcome.Succeeded outcome = new LedgerPlanStepOutcome.Succeeded(facts);
    facts.clear();

    assertEquals(List.of(LedgerFact.text("postingId", "posting-1")), outcome.facts());
  }

  @Test
  void failureOutcomes_delegateFactsFromFailurePayload() {
    LedgerStepFailure failure =
        new LedgerStepFailure("rejected", "Rejected.", List.of(LedgerFact.text("code", "unknown")));

    assertEquals(failure.facts(), new LedgerPlanStepOutcome.Rejected(failure).facts());
    assertEquals(failure.facts(), new LedgerPlanStepOutcome.AssertionFailed(failure).facts());
  }
}
