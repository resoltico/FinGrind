package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.CliJsonModelValidation.requireText;
import static dev.erst.fingrind.cli.CliJsonModelValidation.requireValue;

import java.util.List;

/** Rejection-detail JSON records emitted by the CLI transport layer. */
interface CliRejectionJsonModels extends CliPlanJsonModels {

  record AccountStateViolationsDetails(List<AccountStateViolationPayload> violations) {
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

  record PriorPostingDetails(String priorPostingId) {
    public PriorPostingDetails {
      priorPostingId = requireText(priorPostingId, "priorPostingId");
    }
  }

  record NormalBalanceConflictDetails(
      String accountCode, String existingNormalBalance, String requestedNormalBalance) {
    public NormalBalanceConflictDetails {
      accountCode = requireText(accountCode, "accountCode");
      existingNormalBalance = requireText(existingNormalBalance, "existingNormalBalance");
      requestedNormalBalance = requireText(requestedNormalBalance, "requestedNormalBalance");
    }
  }

  record UnknownAccountDetails(String accountCode) {
    public UnknownAccountDetails {
      accountCode = requireText(accountCode, "accountCode");
    }
  }

  record PostingNotFoundDetails(String postingId) {
    public PostingNotFoundDetails {
      postingId = requireText(postingId, "postingId");
    }
  }

  record PlanRejectionDetails(LedgerPlanPayload plan) {
    public PlanRejectionDetails {
      plan = requireValue(plan, "plan");
    }
  }
}
