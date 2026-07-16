package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliPlanJsonModels;
import java.util.List;

/** Renders assertion and transaction-boundary facts in full execute-plan journal output. */
final class CliPlanControlTextRenderer {
  private CliPlanControlTextRenderer() {}

  static String renderStepData(CliPlanJsonModels.LedgerControlStepDataPayload dataPayload) {
    return switch (dataPayload) {
      case CliPlanJsonModels.AccountCodeAssertionStepDataPayload accountCodeAssertion ->
          CliTextFormat.renderKeyValueBlock(
              List.of(List.of("Account code", accountCodeAssertion.accountCode())));
      case CliPlanJsonModels.PostingIdAssertionStepDataPayload postingIdAssertion ->
          CliTextFormat.renderKeyValueBlock(
              List.of(List.of("Posting id", postingIdAssertion.postingId())));
      case CliPlanJsonModels.PlanBoundaryStepDataPayload boundary ->
          CliTextFormat.renderKeyValueBlock(
              List.of(List.of("Checkpoint", CliTextDisplay.wireLabel(boundary.checkpoint()))));
    };
  }
}
