package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliDiscoveryJsonModels;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolExampleStep;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Maps discovery descriptors onto narrower CLI JSON payloads. */
final class CliDiscoveryPayloadMapper {
  private CliDiscoveryPayloadMapper() {}

  static ProtocolSuccessPayload helpPayload(HelpDescriptor helpDescriptor) {
    Objects.requireNonNull(helpDescriptor, "helpDescriptor");
    return isCommandScoped(helpDescriptor)
        ? commandHelpPayload(helpDescriptor)
        : helpOverviewPayload(helpDescriptor);
  }

  private static CliDiscoveryJsonModels.HelpOverviewPayload helpOverviewPayload(
      HelpDescriptor helpDescriptor) {
    return new CliDiscoveryJsonModels.HelpOverviewPayload(
        helpDescriptor.application(),
        helpDescriptor.version(),
        helpDescriptor.description(),
        helpDescriptor.commands(),
        List.of(
            "Run '"
                + CliInvocationText.commandExample(OperationId.HELP)
                + " <command>' for command-specific usage, operator notes, and examples.",
            "Run '"
                + CliInvocationText.commandExample(OperationId.CAPABILITIES)
                + " --output json' for the full machine-readable contract."),
        helpDescriptor.exitCodes(),
        "Run '"
            + CliInvocationText.commandExample(OperationId.CAPABILITIES)
            + " --output json' for the full machine-readable contract.");
  }

  private static CliDiscoveryJsonModels.CommandHelpPayload commandHelpPayload(
      HelpDescriptor helpDescriptor) {
    CommandDescriptor command = helpDescriptor.commands().getFirst();
    ProtocolOperation operation = ProtocolCatalog.operation(command.name());
    return new CliDiscoveryJsonModels.CommandHelpPayload(
        helpDescriptor.application(),
        helpDescriptor.version(),
        helpDescriptor.description(),
        command,
        helpDescriptor.usage(),
        command.options(),
        requestFileGuidance(helpDescriptor, command.name()).orElse(null),
        commandExamples(operation),
        operatorNotes(operation),
        helpDescriptor.exitCodes());
  }

  private static Optional<CliDiscoveryJsonModels.RequestFileGuidancePayload> requestFileGuidance(
      HelpDescriptor helpDescriptor, OperationId operationId) {
    return switch (operationId) {
      case POST_ENTRY, PREFLIGHT_ENTRY -> postingRequestGuidance(helpDescriptor);
      case DECLARE_ACCOUNT -> declareAccountRequestGuidance(helpDescriptor);
      case EXECUTE_PLAN -> ledgerPlanRequestGuidance(helpDescriptor);
      case HELP,
          CAPABILITIES,
          VERSION,
          PRINT_REQUEST_TEMPLATE,
          PRINT_PLAN_TEMPLATE,
          GENERATE_BOOK_KEY_FILE,
          OPEN_BOOK,
          REKEY_BOOK,
          BACKUP_BOOK,
          RESTORE_BOOK,
          RECOVER_REKEY,
          CLOSE_PERIOD,
          INSPECT_BOOK,
          LIST_ACCOUNTS,
          GET_POSTING,
          LIST_POSTINGS,
          ACCOUNT_BALANCE,
          TRIAL_BALANCE,
          ACCOUNT_LEDGER,
          PERIOD_SUMMARY,
          FINANCIAL_POSITION,
          INCOME_STATEMENT,
          CHANGES_IN_EQUITY ->
          Optional.empty();
    };
  }

  private static Optional<CliDiscoveryJsonModels.RequestFileGuidancePayload> postingRequestGuidance(
      HelpDescriptor helpDescriptor) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().postEntry() == null
        || helpDescriptor.requestTemplate() == null) {
      return Optional.empty();
    }
    return Optional.of(
        new CliDiscoveryJsonModels.RequestFileGuidancePayload(
            "Provide one posting JSON object through --request-file <path|->.",
            helpDescriptor.requestTemplate(),
            null,
            null,
            new ContractRequestShapes.RequestShapesDescriptor(
                helpDescriptor.requestShapes().schemaDialect(),
                helpDescriptor.requestShapes().postEntry(),
                null,
                null),
            CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)));
  }

  private static Optional<CliDiscoveryJsonModels.RequestFileGuidancePayload>
      declareAccountRequestGuidance(HelpDescriptor helpDescriptor) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().declareAccount() == null
        || helpDescriptor.declareAccountTemplate() == null) {
      return Optional.empty();
    }
    return Optional.of(
        new CliDiscoveryJsonModels.RequestFileGuidancePayload(
            "Provide one account-declaration JSON object through --request-file <path|->.",
            null,
            helpDescriptor.declareAccountTemplate(),
            null,
            new ContractRequestShapes.RequestShapesDescriptor(
                helpDescriptor.requestShapes().schemaDialect(),
                null,
                helpDescriptor.requestShapes().declareAccount(),
                null),
            CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                + " "
                + OperationId.DECLARE_ACCOUNT.wireName()));
  }

  private static Optional<CliDiscoveryJsonModels.RequestFileGuidancePayload>
      ledgerPlanRequestGuidance(HelpDescriptor helpDescriptor) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().ledgerPlan() == null
        || helpDescriptor.planTemplate() == null) {
      return Optional.empty();
    }
    return Optional.of(
        new CliDiscoveryJsonModels.RequestFileGuidancePayload(
            "Provide one ledger plan JSON object through --request-file <path|->.",
            null,
            null,
            helpDescriptor.planTemplate(),
            new ContractRequestShapes.RequestShapesDescriptor(
                helpDescriptor.requestShapes().schemaDialect(),
                null,
                null,
                helpDescriptor.requestShapes().ledgerPlan()),
            CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE)));
  }

  private static List<String> commandExamples(ProtocolOperation operation) {
    return operation.exampleSteps().stream()
        .filter(ProtocolExampleStep.Command.class::isInstance)
        .map(ProtocolExampleStep::text)
        .map(CliInvocationText::rewriteInvocationPrefix)
        .toList();
  }

  private static List<String> operatorNotes(ProtocolOperation operation) {
    return operation.exampleSteps().stream()
        .filter(ProtocolExampleStep.Note.class::isInstance)
        .map(ProtocolExampleStep::text)
        .toList();
  }

  private static boolean isCommandScoped(HelpDescriptor helpDescriptor) {
    return helpDescriptor.commands().size() == 1 && helpDescriptor.quickStart().isEmpty();
  }
}
