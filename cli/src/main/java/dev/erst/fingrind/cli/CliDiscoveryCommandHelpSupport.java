package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolExampleStep;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Builds per-command help sections for operator-facing discovery text. */
final class CliDiscoveryCommandHelpSupport {
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

  private CliDiscoveryCommandHelpSupport() {}

  static String renderCommandHelpText(HelpDescriptor helpDescriptor) {
    CommandDescriptor command = helpDescriptor.commands().getFirst();
    ProtocolOperation operation = ProtocolCatalog.operation(command.name());
    String summary = CliTextFormat.wrap(command.summary(), CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
    String usage =
        helpDescriptor.usage().isEmpty()
            ? "(none)"
            : CliTextFormat.renderLiteralBlock(helpDescriptor.usage(), "");
    String options =
        command.options().isEmpty()
            ? "(none)"
            : CliTextFormat.renderLiteralBlock(command.options(), "");
    String run = CliDiscoveryTextSupport.section("Command", usage);
    String renderedOptions =
        "(none)".equals(options) ? "" : CliDiscoveryTextSupport.section("Options", options);
    return CliTextFormat.renderTitledBlock(
        command.name().wireName(),
        CliDiscoveryTextSupport.joinSections(
            summary,
            renderPreparation(command.name()),
            CliDiscoveryTextSupport.section("Try It", renderCommandExamples(operation)),
            renderRequestGuidance(helpDescriptor, command.name()),
            run,
            renderedOptions,
            renderMoreDetailSection(command.name())));
  }

  static String primaryCommandExample(OperationId operationId) {
    ProtocolOperation operation = ProtocolCatalog.operation(operationId);
    List<String> commandExamples =
        operation.exampleSteps().stream()
            .filter(ProtocolExampleStep.Command.class::isInstance)
            .map(ProtocolExampleStep::text)
            .map(CliInvocationText::rewriteInvocationPrefix)
            .toList();
    return CliDiscoveryExampleSelector.selectPrimaryCommandExample(operationId, commandExamples);
  }

  private static String renderCommandExamples(ProtocolOperation operation) {
    List<String> commandExamples =
        operation.exampleSteps().stream()
            .filter(ProtocolExampleStep.Command.class::isInstance)
            .map(ProtocolExampleStep::text)
            .map(CliInvocationText::rewriteInvocationPrefix)
            .toList();
    List<String> notes =
        operation.exampleSteps().stream()
            .filter(ProtocolExampleStep.Note.class::isInstance)
            .map(ProtocolExampleStep::text)
            .toList();
    List<String> sections = new ArrayList<>();
    sections.add(
        commandExamples.isEmpty()
            ? "(none)"
            : CliTextFormat.renderShellCommandBlock(
                primaryOperatorExamples(operation.id(), commandExamples),
                CliDiscoveryTextSupport.TEXT_WRAP_WIDTH));
    if (commandExamples.size() > 1) {
      sections.add(
          "More examples"
              + System.lineSeparator()
              + CliTextFormat.renderLiteralBlock(commandExamples, "$ "));
    }
    if (!notes.isEmpty()) {
      sections.add(
          "Notes:"
              + System.lineSeparator()
              + CliTextFormat.renderBulletedBlock(notes, CliDiscoveryTextSupport.TEXT_WRAP_WIDTH));
    }
    return String.join(System.lineSeparator() + System.lineSeparator(), sections);
  }

  private static String renderPreparation(OperationId operationId) {
    List<List<String>> rows = preparationRows(operationId);
    return rows.isEmpty()
        ? ""
        : CliDiscoveryTextSupport.section(
            "Before You Run", CliTextFormat.renderKeyValueBlock(rows));
  }

  private static List<List<String>> preparationRows(OperationId operationId) {
    if (operationId == OperationId.DECLARE_ACCOUNT) {
      return List.of(
          List.of("Needs", "One opened protected book."),
          List.of("Next step after success", primaryCommandExample(OperationId.LIST_ACCOUNTS)));
    }
    if (ENTRY_REQUEST_OPERATIONS.contains(operationId)) {
      return List.of(
          List.of("Needs", "One opened protected book and every referenced account declared."),
          List.of("Next step after success", primaryCommandExample(OperationId.TRIAL_BALANCE)));
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

  private static String renderRequestGuidance(
      HelpDescriptor helpDescriptor, OperationId operationId) {
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
        "Need a starter file?"
            + System.lineSeparator()
            + CliTextFormat.renderLiteralBlock(List.of(shortcutCommand), "$ "));
  }

  private static String renderMoreDetailSection(OperationId operationId) {
    return CliDiscoveryTextSupport.section(
        "Need JSON Contract",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of(
                    "One command contract",
                    CliInvocationText.commandExample(OperationId.HELP)
                        + " "
                        + ProtocolCatalog.operationName(operationId)
                        + " --output json"))));
  }

  private static List<String> primaryOperatorExamples(
      OperationId operationId, List<String> commandExamples) {
    String primaryCommandExample =
        CliDiscoveryExampleSelector.selectPrimaryCommandExample(operationId, commandExamples);
    if (operationId == OperationId.DECLARE_ACCOUNT) {
      return List.of(
          CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
              + " "
              + OperationId.DECLARE_ACCOUNT.wireName()
              + " > declare-account-cash.json",
          primaryCommandExample);
    }
    if (ENTRY_REQUEST_OPERATIONS.contains(operationId)) {
      return List.of(
          CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
              + " "
              + operationId.wireName()
              + " > request.json",
          primaryCommandExample);
    }
    if (operationId == OperationId.EXECUTE_PLAN) {
      return List.of(
          CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE) + " > ledger-plan.json",
          primaryCommandExample);
    }
    return List.of(primaryCommandExample);
  }
}
