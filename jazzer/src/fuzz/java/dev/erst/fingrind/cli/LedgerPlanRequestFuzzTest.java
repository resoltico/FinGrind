package dev.erst.fingrind.cli;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.LedgerStep;
import dev.erst.fingrind.contract.protocol.OperationId;

/** Fuzzes ledger-plan CLI request decoding from raw JSON payloads. */
public class LedgerPlanRequestFuzzTest {
  @FuzzTest
  void readLedgerPlan(FuzzedDataProvider data) {
    byte[] input = data.consumeRemainingAsBytes();
    try {
      LedgerPlan plan = CliFuzzSupport.readLedgerPlan(input);
      if (plan == null) {
        throw new IllegalStateException("readLedgerPlan returned null");
      }
      if (plan.planId().isBlank()) {
        throw new IllegalStateException("Parsed ledger plan retained a blank plan id.");
      }
      if (plan.steps().isEmpty()) {
        throw new IllegalStateException("Parsed ledger plan retained no steps.");
      }
      long openBookSteps = plan.steps().stream().filter(LedgerStep.OpenBook.class::isInstance).count();
      if (openBookSteps > 0 && !plan.beginsWithOpenBook()) {
        throw new IllegalStateException("Parsed ledger plan accepted open-book after the first step.");
      }
      for (LedgerStep step : plan.steps()) {
        String stepKind = step.kind().wireValue();
        if (stepKind.isBlank()) {
          throw new IllegalStateException("Parsed ledger plan retained a blank step kind.");
        }
        if (OperationId.EXECUTE_PLAN.wireName().equals(stepKind)) {
          throw new IllegalStateException("Plan journal step kind must not collapse to execute-plan.");
        }
      }
    } catch (IllegalArgumentException expected) {
      // Malformed JSON and invalid plan/domain shapes are expected for many fuzz inputs.
    }
  }
}
