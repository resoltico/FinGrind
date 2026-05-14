package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireValue;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.workflow.LedgerBoundaryPhase;
import dev.erst.fingrind.contract.workflow.LedgerJournalKind;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import dev.erst.fingrind.contract.workflow.LedgerStepStatus;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Ledger-plan JSON records emitted by the CLI transport layer. */
public interface CliPlanJsonModels {

  record LedgerPlanPayload(
      String planId,
      LedgerPlanStatus status,
      PlanResultDetail resultDetail,
      LedgerPlanSummaryPayload summary,
      @Nullable LedgerExecutionJournalPayload journal)
      implements CliSuccessPayload {
    public LedgerPlanPayload {
      planId = requireText(planId, "planId");
      status = requireValue(status, "status");
      resultDetail = requireValue(resultDetail, "resultDetail");
      summary = requireValue(summary, "summary");
      if (resultDetail == PlanResultDetail.FULL) {
        if (journal == null) {
          throw new IllegalArgumentException("journal must be present when resultDetail is full.");
        }
      } else if (journal != null) {
        throw new IllegalArgumentException("journal must be absent unless resultDetail is full.");
      }
    }
  }

  record LedgerPlanSummaryPayload(
      String startedAt,
      String finishedAt,
      int stepCount,
      int succeededStepCount,
      int failedStepCount,
      @Nullable String failedStepId,
      @Nullable String failureCode,
      @Nullable String failureMessage) {
    public LedgerPlanSummaryPayload {
      startedAt = requireText(startedAt, "startedAt");
      finishedAt = requireText(finishedAt, "finishedAt");
      if (stepCount <= 0) {
        throw new IllegalArgumentException("stepCount must be positive.");
      }
      if (succeededStepCount < 0) {
        throw new IllegalArgumentException("succeededStepCount must be non-negative.");
      }
      if (failedStepCount < 0) {
        throw new IllegalArgumentException("failedStepCount must be non-negative.");
      }
      failedStepId = requireOptionalText(failedStepId, "failedStepId");
      failureCode = requireOptionalText(failureCode, "failureCode");
      failureMessage = requireOptionalText(failureMessage, "failureMessage");
    }
  }

  record LedgerExecutionJournalPayload(
      String startedAt, String finishedAt, List<LedgerJournalEntryPayload> steps) {
    public LedgerExecutionJournalPayload {
      startedAt = requireText(startedAt, "startedAt");
      finishedAt = requireText(finishedAt, "finishedAt");
      steps = copyList(steps, "steps");
      if (steps.isEmpty()) {
        throw new IllegalArgumentException("steps must not be empty.");
      }
    }
  }

  record LedgerJournalEntryPayload(
      String stepId,
      LedgerJournalKind kind,
      @Nullable LedgerAssertionKind detailKind,
      @Nullable LedgerBoundaryPhase boundaryPhase,
      LedgerStepStatus status,
      String startedAt,
      String finishedAt,
      List<LedgerFactPayload> facts,
      @Nullable LedgerStepFailurePayload failure) {
    public LedgerJournalEntryPayload {
      stepId = requireText(stepId, "stepId");
      kind = requireValue(kind, "kind");
      status = requireValue(status, "status");
      startedAt = requireText(startedAt, "startedAt");
      finishedAt = requireText(finishedAt, "finishedAt");
      facts = copyList(facts, "facts");
    }
  }

  record LedgerStepFailurePayload(String code, String message, List<LedgerFactPayload> facts) {
    public LedgerStepFailurePayload {
      code = requireText(code, "code");
      message = requireText(message, "message");
      facts = copyList(facts, "facts");
    }
  }

  /** JSON shape for one typed ledger fact observation. */
  sealed interface LedgerFactPayload
      permits TextLedgerFactPayload,
          FlagLedgerFactPayload,
          CountLedgerFactPayload,
          MoneyLedgerFactPayload,
          GroupLedgerFactPayload {}

  record TextLedgerFactPayload(String kind, String name, String value)
      implements LedgerFactPayload {
    public TextLedgerFactPayload {
      kind = requireText(kind, "kind");
      name = requireText(name, "name");
      value = requireText(value, "value");
    }
  }

  record FlagLedgerFactPayload(String kind, String name, boolean value)
      implements LedgerFactPayload {
    public FlagLedgerFactPayload {
      kind = requireText(kind, "kind");
      name = requireText(name, "name");
    }
  }

  record CountLedgerFactPayload(String kind, String name, int value) implements LedgerFactPayload {
    public CountLedgerFactPayload {
      kind = requireText(kind, "kind");
      name = requireText(name, "name");
    }
  }

  record MoneyLedgerFactPayload(String kind, String name, MonetaryAmount value)
      implements LedgerFactPayload {
    public MoneyLedgerFactPayload {
      kind = requireText(kind, "kind");
      name = requireText(name, "name");
      Objects.requireNonNull(value, "value");
    }
  }

  record GroupLedgerFactPayload(String kind, String name, List<LedgerFactPayload> facts)
      implements LedgerFactPayload {
    public GroupLedgerFactPayload {
      kind = requireText(kind, "kind");
      name = requireText(name, "name");
      facts = copyList(facts, "facts");
      if (facts.isEmpty()) {
        throw new IllegalArgumentException("facts must not be empty.");
      }
    }
  }
}
