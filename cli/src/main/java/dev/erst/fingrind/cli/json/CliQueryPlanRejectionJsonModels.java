package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireValue;

import dev.erst.fingrind.cli.json.CliPlanResultJsonModels.LedgerPlanPayload;

/** Query and workflow rejection details emitted by the CLI transport layer. */
public interface CliQueryPlanRejectionJsonModels {
  /** Sealed category for query and workflow rejection payloads. */
  sealed interface QueryOrPlanRejectionDetails extends CliRejectionJsonModels.RejectionDetails
      permits UnknownAccountDetails, PostingNotFoundDetails, PlanRejectionDetails {}

  record UnknownAccountDetails(String accountCode) implements QueryOrPlanRejectionDetails {
    public UnknownAccountDetails {
      accountCode = requireText(accountCode, "accountCode");
    }
  }

  record PostingNotFoundDetails(String postingId) implements QueryOrPlanRejectionDetails {
    public PostingNotFoundDetails {
      postingId = requireText(postingId, "postingId");
    }
  }

  record PlanRejectionDetails(LedgerPlanPayload plan) implements QueryOrPlanRejectionDetails {
    public PlanRejectionDetails {
      plan = requireValue(plan, "plan");
    }
  }
}
