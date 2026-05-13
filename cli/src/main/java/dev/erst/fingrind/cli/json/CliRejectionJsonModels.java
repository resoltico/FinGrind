package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireValue;

import java.util.List;

/** Rejection-detail JSON records emitted by the CLI transport layer. */
public interface CliRejectionJsonModels extends CliPlanJsonModels {

  /** Sealed marker for machine-readable CLI rejection detail payloads. */
  sealed interface RejectionDetails
      permits AccountStateViolationsDetails,
          AccountTypeConflictDetails,
          PriorPostingDetails,
          AccountRoleConflictDetails,
          RetainedEarningsAccountDetails,
          PeriodCloseStartDetails,
          ClosedPeriodViolationDetails,
          UnknownAccountDetails,
          PostingNotFoundDetails,
          PlanRejectionDetails {}

  record AccountStateViolationsDetails(List<AccountStateViolationPayload> violations)
      implements RejectionDetails {
    public AccountStateViolationsDetails {
      violations = copyList(violations, "violations");
      if (violations.isEmpty()) {
        throw new IllegalArgumentException("violations must not be empty.");
      }
    }
  }

  record AccountStateViolationPayload(String code, String accountCode) {
    public AccountStateViolationPayload {
      code = requireText(code, "code");
      accountCode = requireText(accountCode, "accountCode");
    }
  }

  record PriorPostingDetails(String priorPostingId) implements RejectionDetails {
    public PriorPostingDetails {
      priorPostingId = requireText(priorPostingId, "priorPostingId");
    }
  }

  record AccountRoleConflictDetails(
      String accountCode, String existingAccountRole, String requestedAccountRole)
      implements RejectionDetails {
    public AccountRoleConflictDetails {
      accountCode = requireText(accountCode, "accountCode");
      existingAccountRole = requireText(existingAccountRole, "existingAccountRole");
      requestedAccountRole = requireText(requestedAccountRole, "requestedAccountRole");
    }
  }

  record AccountTypeConflictDetails(
      String accountCode, String existingAccountType, String requestedAccountType)
      implements RejectionDetails {
    public AccountTypeConflictDetails {
      accountCode = requireText(accountCode, "accountCode");
      existingAccountType = requireText(existingAccountType, "existingAccountType");
      requestedAccountType = requireText(requestedAccountType, "requestedAccountType");
    }
  }

  record RetainedEarningsAccountDetails(String accountCode) implements RejectionDetails {
    public RetainedEarningsAccountDetails {
      accountCode = requireText(accountCode, "accountCode");
    }
  }

  record PeriodCloseStartDetails(String requiredEffectiveDateFrom) implements RejectionDetails {
    public PeriodCloseStartDetails {
      requiredEffectiveDateFrom =
          requireText(requiredEffectiveDateFrom, "requiredEffectiveDateFrom");
    }
  }

  record ClosedPeriodViolationDetails(
      String closedThroughEffectiveDate, String attemptedEffectiveDate)
      implements RejectionDetails {
    public ClosedPeriodViolationDetails {
      closedThroughEffectiveDate =
          requireText(closedThroughEffectiveDate, "closedThroughEffectiveDate");
      attemptedEffectiveDate = requireText(attemptedEffectiveDate, "attemptedEffectiveDate");
    }
  }

  record UnknownAccountDetails(String accountCode) implements RejectionDetails {
    public UnknownAccountDetails {
      accountCode = requireText(accountCode, "accountCode");
    }
  }

  record PostingNotFoundDetails(String postingId) implements RejectionDetails {
    public PostingNotFoundDetails {
      postingId = requireText(postingId, "postingId");
    }
  }

  record PlanRejectionDetails(LedgerPlanPayload plan) implements RejectionDetails {
    public PlanRejectionDetails {
      plan = requireValue(plan, "plan");
    }
  }
}
