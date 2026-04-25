package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.contract.LedgerPlanStatus;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import java.util.Objects;

/** Stable replay details for committed ledger-plan request seeds. */
public record LedgerPlanReplayDetails(
    LedgerPlanShapeDetails plan, LedgerPlanExecutionDetails execution) implements ReplayDetails {
  public LedgerPlanReplayDetails {
    Objects.requireNonNull(plan, "plan must not be null");
    Objects.requireNonNull(execution, "execution must not be null");
  }
}

/**
 * Replay details for ledger-plan inputs that parsed successfully but never produced an execution
 * snapshot.
 */
record ParsedLedgerPlanShapeReplayDetails(LedgerPlanShapeDetails plan) implements ReplayDetails {
  ParsedLedgerPlanShapeReplayDetails {
    Objects.requireNonNull(plan, "plan must not be null");
  }
}

/** Replay details for ledger-plan inputs that never produced a parsed plan. */
record UnparsedLedgerPlanReplayDetails() implements ReplayDetails {}

/** Parsed ledger-plan shape facts recorded for deterministic replay. */
record LedgerPlanShapeDetails(
    String planId,
    int stepCount,
    LedgerStepKind firstStepKind,
    LedgerStepKind lastStepKind,
    int assertionStepCount,
    boolean beginsWithOpenBook) {
  LedgerPlanShapeDetails {
    planId = ReplayModelValidation.requireText(planId, "planId");
    stepCount = ReplayModelValidation.requireNonNegative(stepCount, "stepCount");
    assertionStepCount =
        ReplayModelValidation.requireNonNegative(assertionStepCount, "assertionStepCount");
    Objects.requireNonNull(firstStepKind, "firstStepKind must not be null");
    Objects.requireNonNull(lastStepKind, "lastStepKind must not be null");
  }
}

/** Parsed execution facts recorded after one deterministic ledger-plan replay. */
record LedgerPlanExecutionDetails(
    LedgerPlanStatus executionStatus,
    int journalStepCount,
    int listQueryStepCount,
    int structuredListQueryStepCount) {
  LedgerPlanExecutionDetails {
    Objects.requireNonNull(executionStatus, "executionStatus must not be null");
    journalStepCount =
        ReplayModelValidation.requireNonNegative(journalStepCount, "journalStepCount");
    listQueryStepCount =
        ReplayModelValidation.requireNonNegative(listQueryStepCount, "listQueryStepCount");
    structuredListQueryStepCount =
        ReplayModelValidation.requireNonNegative(
            structuredListQueryStepCount, "structuredListQueryStepCount");
    if (structuredListQueryStepCount > listQueryStepCount) {
      throw new IllegalArgumentException(
          "structuredListQueryStepCount must not exceed listQueryStepCount");
    }
  }
}
