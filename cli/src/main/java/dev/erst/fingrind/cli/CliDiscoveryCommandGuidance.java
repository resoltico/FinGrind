package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.ExitCodeDescriptor;
import java.util.List;
import java.util.Set;

/** Builds operator-facing preparation, request, and exit guidance for command help. */
final class CliDiscoveryCommandGuidance {
  private static final Set<OperationId> BOOK_READ_OPERATIONS =
      Set.of(
          OperationId.ACCOUNT_BALANCE,
          OperationId.ACCOUNT_LEDGER,
          OperationId.TRIAL_BALANCE,
          OperationId.FINANCIAL_POSITION,
          OperationId.INCOME_STATEMENT,
          OperationId.CHANGES_IN_EQUITY,
          OperationId.PERIOD_SUMMARY,
          OperationId.LIST_POSTINGS,
          OperationId.GET_POSTING);

  private static final Set<OperationId> ENTRY_REQUEST_OPERATIONS =
      Set.of(OperationId.POST_ENTRY, OperationId.PREFLIGHT_ENTRY);

  private CliDiscoveryCommandGuidance() {}

  static String renderPreparation(OperationId operationId) {
    List<List<String>> rows = preparationRows(operationId);
    return rows.isEmpty()
        ? ""
        : CliDiscoveryTextSupport.section(
            "Before You Run", CliTextFormat.renderKeyValueBlock(rows));
  }

  static String renderRequestGuidance(HelpDescriptor helpDescriptor, OperationId operationId) {
    if (ENTRY_REQUEST_OPERATIONS.contains(operationId)) {
      return renderPostingRequestGuidance(helpDescriptor, operationId);
    }
    if (operationId == OperationId.DECLARE_ACCOUNT) {
      return renderDeclareAccountRequestGuidance(helpDescriptor);
    }
    if (operationId == OperationId.EXECUTE_PLAN) {
      return renderLedgerPlanRequestGuidance(helpDescriptor);
    }
    return "";
  }

  static String renderExitBehavior(List<ExitCodeDescriptor> exitCodes) {
    List<List<String>> rows =
        exitCodes.stream()
            .<List<String>>map(
                exitCode -> List.of(Integer.toString(exitCode.code()), exitCode.meaning()))
            .toList();
    return rows.isEmpty()
        ? ""
        : CliDiscoveryTextSupport.section("Exit Behavior", CliTextFormat.renderKeyValueBlock(rows));
  }

  static String requestTemplateHint(OperationId operationId) {
    if (ENTRY_REQUEST_OPERATIONS.contains(operationId)) {
      return CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
          + " "
          + operationId.wireName();
    }
    if (operationId == OperationId.DECLARE_ACCOUNT) {
      return CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
          + " "
          + OperationId.DECLARE_ACCOUNT.wireName();
    }
    if (operationId == OperationId.EXECUTE_PLAN) {
      return CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE);
    }
    return "(not applicable)";
  }

  private static List<List<String>> preparationRows(OperationId operationId) {
    if (operationId == OperationId.DECLARE_ACCOUNT) {
      return List.of(
          List.of("Needs", "One opened protected book."),
          List.of(
              "Next step after success",
              CliDiscoveryCommandExamples.primaryCommandExample(OperationId.LIST_ACCOUNTS)));
    }
    if (ENTRY_REQUEST_OPERATIONS.contains(operationId)) {
      return List.of(
          List.of("Needs", "One opened protected book and every referenced account declared."),
          List.of(
              "Next step after success",
              CliDiscoveryCommandExamples.primaryCommandExample(OperationId.TRIAL_BALANCE)));
    }
    if (BOOK_READ_OPERATIONS.contains(operationId)) {
      return List.of(List.of("Needs", "One opened protected book."));
    }
    if (operationId == OperationId.EXECUTE_PLAN) {
      return List.of(
          List.of("Needs", "One ledger plan JSON document passed through --request-file."),
          List.of(
              "Starter file", CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE)));
    }
    return List.of();
  }

  private static String renderPostingRequestGuidance(
      HelpDescriptor helpDescriptor, OperationId operationId) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().postEntry() == null
        || helpDescriptor.requestTemplate() == null) {
      return "";
    }
    return CliDiscoveryTextSupport.section(
        "Request File",
        requestFileGuidance(
            "Pass one JSON object through --request-file <path|->.",
            CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                + " "
                + operationId.wireName()));
  }

  private static String renderDeclareAccountRequestGuidance(HelpDescriptor helpDescriptor) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().declareAccount() == null
        || helpDescriptor.declareAccountTemplate() == null) {
      return "";
    }
    return CliDiscoveryTextSupport.section(
        "Request File",
        requestFileGuidance(
            "Pass one JSON object through --request-file <path|->.",
            CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                + " "
                + OperationId.DECLARE_ACCOUNT.wireName()));
  }

  private static String renderLedgerPlanRequestGuidance(HelpDescriptor helpDescriptor) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().ledgerPlan() == null
        || helpDescriptor.planTemplate() == null) {
      return "";
    }
    return CliDiscoveryTextSupport.section(
        "Request File",
        requestFileGuidance(
            "Pass one ledger plan JSON object through --request-file <path|->.",
            CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE)));
  }

  private static String requestFileGuidance(String introduction, String shortcutCommand) {
    return String.join(
        System.lineSeparator() + System.lineSeparator(),
        CliTextFormat.wrap(introduction, CliDiscoveryTextSupport.TEXT_WRAP_WIDTH),
        "Starter file command"
            + System.lineSeparator()
            + CliTextFormat.renderLiteralBlock(List.of(shortcutCommand), "$ "));
  }
}
