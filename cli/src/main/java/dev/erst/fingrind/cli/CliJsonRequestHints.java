package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;

/** Request-recovery hints for CLI JSON input failures. */
final class CliJsonRequestHints {
  private CliJsonRequestHints() {}

  static String postEntryRequestHint() {
    return "Use '"
        + CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
        + "' for a starter request file, then replace its placeholder evidence and provenance values before real-world use. For accepted entry fields, run '"
        + CliInvocationText.commandExample(OperationId.HELP)
        + " "
        + OperationId.POST_ENTRY.wireName()
        + " --output json --detail full'.";
  }

  static String declareAccountRequestHint() {
    return "Use '"
        + CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
        + " "
        + ProtocolCatalog.operationName(OperationId.DECLARE_ACCOUNT)
        + "' for a starter "
        + ProtocolCatalog.operationName(OperationId.DECLARE_ACCOUNT)
        + " request file, then replace its sample account values before real-world use. For accepted declaration fields, run '"
        + CliInvocationText.commandExample(OperationId.HELP)
        + " "
        + OperationId.DECLARE_ACCOUNT.wireName()
        + " --output json --detail full'.";
  }

  static String ledgerPlanRequestHint() {
    return "Use '"
        + CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE)
        + "' for a starter ledger plan, then replace its placeholder evidence and provenance values before real-world use. For accepted step fields and assertion shapes, run '"
        + CliInvocationText.commandExample(OperationId.HELP)
        + " "
        + OperationId.EXECUTE_PLAN.wireName()
        + " --output json --detail full'.";
  }
}
