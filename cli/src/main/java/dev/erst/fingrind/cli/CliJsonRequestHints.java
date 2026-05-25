package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;

/** Request-recovery hints for CLI JSON input failures. */
final class CliJsonRequestHints {
  private CliJsonRequestHints() {}

  static String postEntryRequestHint() {
    return "Run '"
        + CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
        + "' for the canonical request scaffold, then replace its placeholder evidence and provenance values before real-world use, or run '"
        + CliInvocationText.commandExample(OperationId.CAPABILITIES)
        + "' for accepted enums and fields.";
  }

  static String declareAccountRequestHint() {
    return "Run '"
        + CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
        + " "
        + ProtocolCatalog.operationName(OperationId.DECLARE_ACCOUNT)
        + "' for the canonical "
        + ProtocolCatalog.operationName(OperationId.DECLARE_ACCOUNT)
        + " sample document, then replace its sample account values before real-world use, or run '"
        + CliInvocationText.commandExample(OperationId.CAPABILITIES)
        + "' for the accepted account-declaration fields and enums.";
  }

  static String ledgerPlanRequestHint() {
    return "Run '"
        + CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE)
        + "' for the canonical ledger plan scaffold, then replace its placeholder evidence and provenance values before real-world use, or run '"
        + CliInvocationText.commandExample(OperationId.CAPABILITIES)
        + "' for accepted enums and fields.";
  }
}
