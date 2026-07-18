package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import java.util.Objects;

/** Request-recovery hints for CLI JSON input failures. */
final class CliJsonRequestHints {
  private CliJsonRequestHints() {}

  static String postEntryRequestHint(OperationId templateOperation) {
    OperationId operation = Objects.requireNonNull(templateOperation, "templateOperation");
    String operationName = ProtocolCatalog.operationName(operation);
    return "Use '"
        + CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
        + " "
        + operationName
        + "' for a starter request file, then replace its placeholder evidence and provenance values before real-world use. For accepted entry fields, run '"
        + CliInvocationText.commandExample(OperationId.HELP)
        + " "
        + operationName
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

  static String declareTaxRegistrationRequestHint() {
    return "Use '"
        + CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
        + " "
        + ProtocolCatalog.operationName(OperationId.DECLARE_TAX_REGISTRATION)
        + "' for a starter "
        + ProtocolCatalog.operationName(OperationId.DECLARE_TAX_REGISTRATION)
        + " request file, then replace its sample tax registration values before real-world use. For accepted declaration fields, run '"
        + CliInvocationText.commandExample(OperationId.HELP)
        + " "
        + OperationId.DECLARE_TAX_REGISTRATION.wireName()
        + " --output json --detail full'.";
  }

  static String amendAccountRequestHint() {
    return "Use '"
        + CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
        + " "
        + OperationId.AMEND_ACCOUNT.wireName()
        + "' for a starter account-definition request file. Amendments are admitted only while the account has no postings, tax registrations, or child accounts.";
  }

  static String retireAccountRequestHint() {
    return "Provide the accountCode to retire. Retirement requires a zero current balance and no active operational reference; it preserves ledger history and permits historical reversals.";
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
