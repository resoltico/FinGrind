package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.cli.CliFuzzFixtures;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerStep;
import java.util.Objects;

/** Shared parsed-shape detail builders for replay outcome models. */
final class JazzerReplayShapeDetails {
  private JazzerReplayShapeDetails() {}

  static ParsedPostingCommandDetails parsedPostingCommandDetails(PostEntryCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return new ParsedPostingCommandDetails(
        CliFuzzFixtures.journalEntry(command).effectiveDate().toString(),
        command.requestProvenance().idempotencyKey().value(),
        CliFuzzFixtures.journalEntry(command).lines().size(),
        CliFuzzFixtures.reversalReference(command).isPresent());
  }

  static LedgerPlanShapeDetails ledgerPlanShapeDetails(LedgerPlan plan) {
    Objects.requireNonNull(plan, "plan must not be null");
    return new LedgerPlanShapeDetails(
        plan.planId().value(),
        plan.steps().size(),
        plan.steps().getFirst().kind(),
        plan.steps().getLast().kind(),
        assertionStepCount(plan),
        plan.steps().getFirst().kind().mutatesBook());
  }

  private static int assertionStepCount(LedgerPlan plan) {
    return (int) plan.steps().stream().filter(LedgerStep.Assert.class::isInstance).count();
  }
}
