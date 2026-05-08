package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireValue;

import dev.erst.fingrind.contract.LedgerBoundaryPhase;
import dev.erst.fingrind.contract.LedgerJournalKind;
import dev.erst.fingrind.contract.LedgerPlanStatus;
import dev.erst.fingrind.contract.LedgerStepStatus;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Ledger-plan JSON records emitted by the CLI transport layer. */
public interface CliPlanJsonModels {

  record LedgerPlanPayload(
      String planId, LedgerPlanStatus status, LedgerExecutionJournalPayload journal)
      implements CliSuccessPayload {
    public LedgerPlanPayload {
      planId = requireText(planId, "planId");
      status = requireValue(status, "status");
      journal = requireValue(journal, "journal");
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
