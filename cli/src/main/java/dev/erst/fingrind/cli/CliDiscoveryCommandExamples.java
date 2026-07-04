package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolExampleStep;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Renders operator-facing example blocks for one command-help surface. */
final class CliDiscoveryCommandExamples {
  private static final Set<OperationId> ENTRY_REQUEST_OPERATIONS =
      Set.of(
          OperationId.POST_ENTRY,
          OperationId.PREFLIGHT_ENTRY,
          OperationId.RECORD_SALE_SETTLED,
          OperationId.RECORD_SALE_ON_CREDIT,
          OperationId.RECORD_EXPENSE_SETTLED,
          OperationId.RECORD_EXPENSE_ON_CREDIT,
          OperationId.RECORD_RECEIPT,
          OperationId.RECORD_PAYMENT,
          OperationId.RECORD_OWNER_CONTRIBUTION,
          OperationId.RECORD_OWNER_WITHDRAWAL,
          OperationId.RECORD_OPENING_POSITION,
          OperationId.RECORD_REVERSAL);

  private CliDiscoveryCommandExamples() {}

  static String primaryCommandExample(OperationId operationId) {
    ProtocolOperation operation =
        dev.erst.fingrind.contract.protocol.ProtocolCatalog.operation(operationId);
    List<String> commandExamples =
        operation.exampleSteps().stream()
            .filter(ProtocolExampleStep.Command.class::isInstance)
            .map(ProtocolExampleStep::text)
            .map(CliInvocationText::rewriteInvocationPrefix)
            .toList();
    return CliDiscoveryExampleSelector.selectPrimaryCommandExample(operationId, commandExamples);
  }

  static String primaryStarterRequestCommand(OperationId operationId) {
    if (FinGrindCli.BUNDLE_RUNTIME_DISTRIBUTION.equals(FinGrindCli.runtimeDistribution())
        && operationId == OperationId.RECORD_SALE_SETTLED) {
      return "cp ./quick-start-request.json ./request.json";
    }
    return switch (operationId) {
      case POST_ENTRY ->
          CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
              + " "
              + OperationId.POST_ENTRY.wireName()
              + " > request.json";
      case PREFLIGHT_ENTRY ->
          CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE) + " > request.json";
      default ->
          CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
              + " "
              + operationId.wireName()
              + " > request.json";
    };
  }

  static String renderCommandExamples(ProtocolOperation operation) {
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
    if (commandExamples.isEmpty()) {
      sections.add("(none)");
    } else {
      List<String> primaryExamples = primaryOperatorExamples(operation.id(), commandExamples);
      sections.add(
          CliTextFormat.renderShellCommandBlock(
              primaryExamples, CliDiscoveryTextSupport.TEXT_WRAP_WIDTH));
      List<String> additionalExamples =
          commandExamples.stream().filter(example -> !primaryExamples.contains(example)).toList();
      if (!additionalExamples.isEmpty()) {
        sections.add(
            "Additional examples"
                + System.lineSeparator()
                + CliTextFormat.renderLiteralBlock(additionalExamples, "$ "));
      }
    }
    if (!notes.isEmpty()) {
      sections.add(
          "Operational notes:"
              + System.lineSeparator()
              + CliTextFormat.renderBulletedBlock(notes, CliDiscoveryTextSupport.TEXT_WRAP_WIDTH));
    }
    return String.join(System.lineSeparator() + System.lineSeparator(), sections);
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
              + " > "
              + OperationId.DECLARE_ACCOUNT.wireName()
              + ".json",
          primaryCommandExample);
    }
    if (ENTRY_REQUEST_OPERATIONS.contains(operationId)) {
      return List.of(primaryStarterRequestCommand(operationId), primaryCommandExample);
    }
    if (operationId == OperationId.EXECUTE_PLAN) {
      return List.of(
          CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE) + " > ledger-plan.json",
          primaryCommandExample);
    }
    return List.of(primaryCommandExample);
  }
}
