package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import java.util.Arrays;
import java.util.List;

/** Canonical role groupings for executable ledger-plan step kinds. */
final class MachineContractLedgerPlanRoles {
  /** Internal grouping labels for the published ledger-plan step vocabulary. */
  private enum StepRole {
    ADMINISTRATION,
    QUERY,
    WRITE,
    ASSERT
  }

  private MachineContractLedgerPlanRoles() {}

  static List<LedgerStepKind> administrationStepKinds() {
    return stepKinds(StepRole.ADMINISTRATION);
  }

  static List<LedgerStepKind> queryStepKinds() {
    return stepKinds(StepRole.QUERY);
  }

  static List<LedgerStepKind> writeStepKinds() {
    return stepKinds(StepRole.WRITE);
  }

  static LedgerStepKind assertStepKind() {
    return LedgerStepKind.ASSERT;
  }

  private static List<LedgerStepKind> stepKinds(StepRole role) {
    return Arrays.stream(LedgerStepKind.values()).filter(kind -> stepRole(kind) == role).toList();
  }

  private static StepRole stepRole(LedgerStepKind kind) {
    if (kind.carriesPostingPayload()) {
      return StepRole.WRITE;
    }
    if (kind == LedgerStepKind.ASSERT) {
      return StepRole.ASSERT;
    }
    if (kind == LedgerStepKind.ENSURE_BOOK
        || kind == LedgerStepKind.DECLARE_ACCOUNT
        || kind == LedgerStepKind.DECLARE_TAX_REGISTRATION) {
      return StepRole.ADMINISTRATION;
    }
    return StepRole.QUERY;
  }
}
