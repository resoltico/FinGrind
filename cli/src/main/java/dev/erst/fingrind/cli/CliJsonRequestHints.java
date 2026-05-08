package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;

/** Request-recovery hints for CLI JSON input failures. */
final class CliJsonRequestHints {
  private CliJsonRequestHints() {}

  static String postEntryRequestHint() {
    return "Run '"
        + CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
        + "' for the canonical request scaffold, then replace its scaffold placeholders before submission, or run '"
        + CliInvocationText.commandExample(OperationId.CAPABILITIES)
        + "' for accepted enums and fields.";
  }

  static String declareAccountRequestHint() {
    return "Run '"
        + CliInvocationText.commandExample(OperationId.CAPABILITIES)
        + "' for the accepted account-declaration request fields and enums.";
  }

  static String ledgerPlanRequestHint() {
    return "Run '"
        + CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE)
        + "' for the canonical ledger plan scaffold, then replace its scaffold placeholders before submission, or run '"
        + CliInvocationText.commandExample(OperationId.CAPABILITIES)
        + "' for accepted enums and fields.";
  }
}
