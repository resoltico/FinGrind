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
          PriorPostingDetails,
          NormalBalanceConflictDetails,
          UnknownAccountDetails,
          PostingNotFoundDetails,
          PlanRejectionDetails {}

  record AccountStateViolationsDetails(List<AccountStateViolationPayload> violations)
      implements RejectionDetails {
    public AccountStateViolationsDetails {
      violations = copyList(violations);
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

  record NormalBalanceConflictDetails(
      String accountCode, String existingNormalBalance, String requestedNormalBalance)
      implements RejectionDetails {
    public NormalBalanceConflictDetails {
      accountCode = requireText(accountCode, "accountCode");
      existingNormalBalance = requireText(existingNormalBalance, "existingNormalBalance");
      requestedNormalBalance = requireText(requestedNormalBalance, "requestedNormalBalance");
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
