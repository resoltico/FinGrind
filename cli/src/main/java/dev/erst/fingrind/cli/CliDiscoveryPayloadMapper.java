package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliDiscoveryJsonModels;
import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolExampleStep;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Maps discovery descriptors onto narrower CLI JSON payloads. */
final class CliDiscoveryPayloadMapper {
  private CliDiscoveryPayloadMapper() {}

  static ProtocolSuccessPayload helpPayload(HelpDescriptor helpDescriptor, DiscoveryDetail detail) {
    Objects.requireNonNull(helpDescriptor, "helpDescriptor");
    Objects.requireNonNull(detail, "detail");
    return isCommandScoped(helpDescriptor)
        ? commandHelpPayload(helpDescriptor, detail)
        : helpOverviewPayload(helpDescriptor, detail);
  }

  static CliDiscoveryJsonModels.CapabilitiesPayload capabilitiesPayload(
      CapabilitiesDescriptor capabilitiesDescriptor, DiscoveryDetail detail) {
    Objects.requireNonNull(capabilitiesDescriptor, "capabilitiesDescriptor");
    Objects.requireNonNull(detail, "detail");
    return new CliDiscoveryJsonModels.CapabilitiesPayload(
        capabilitiesDescriptor.application(),
        capabilitiesDescriptor.version(),
        detail,
        capabilitiesDescriptor.storage(),
        capabilitiesDescriptor.commands(),
        capabilitiesDescriptor.requestInput(),
        List.of(
            "Use compact detail for stable command, storage, and request-entry discovery.",
            "Rerun with '"
                + CliInvocationText.commandExample(OperationId.CAPABILITIES)
                + " --output json "
                + ProtocolOptions.DETAIL
                + " full' when you need the exhaustive schema and response contract."),
        detail == DiscoveryDetail.FULL ? capabilitiesDescriptor : null);
  }

  private static CliDiscoveryJsonModels.HelpOverviewPayload helpOverviewPayload(
      HelpDescriptor helpDescriptor, DiscoveryDetail detail) {
    return new CliDiscoveryJsonModels.HelpOverviewPayload(
        helpDescriptor.application(),
        helpDescriptor.version(),
        helpDescriptor.description(),
        detail,
        helpDescriptor.commands(),
        List.of(
            "Run '"
                + CliInvocationText.commandExample(OperationId.HELP)
                + " <command>' for command-specific usage, operator notes, and examples.",
            "Run '"
                + CliInvocationText.commandExample(OperationId.CAPABILITIES)
                + " --output json' for the stable machine-readable command contract.",
            "Run '"
                + CliInvocationText.commandExample(OperationId.ENVIRONMENT)
                + " --output json' for live runtime, distribution, and SQLite provenance facts."),
        helpDescriptor.exitCodes(),
        "Run '"
            + CliInvocationText.commandExample(OperationId.CAPABILITIES)
            + " --output json' for the stable machine-readable command contract.",
        detail == DiscoveryDetail.FULL ? helpDescriptor : null);
  }

  private static CliDiscoveryJsonModels.CommandHelpPayload commandHelpPayload(
      HelpDescriptor helpDescriptor, DiscoveryDetail detail) {
    CommandDescriptor command = helpDescriptor.commands().getFirst();
    ProtocolOperation operation = ProtocolCatalog.operation(command.name());
    return new CliDiscoveryJsonModels.CommandHelpPayload(
        helpDescriptor.application(),
        helpDescriptor.version(),
        helpDescriptor.description(),
        detail,
        command,
        helpDescriptor.usage(),
        command.options(),
        requestFileGuidance(helpDescriptor, command.name(), detail).orElse(null),
        commandExamples(operation),
        operatorNotes(operation),
        helpDescriptor.exitCodes());
  }

  private static Optional<CliDiscoveryJsonModels.RequestFileGuidancePayload> requestFileGuidance(
      HelpDescriptor helpDescriptor, OperationId operationId, DiscoveryDetail detail) {
    return switch (operationId) {
      case POST_ENTRY, PREFLIGHT_ENTRY -> postingRequestGuidance(helpDescriptor, detail);
      case DECLARE_ACCOUNT -> declareAccountRequestGuidance(helpDescriptor, detail);
      case EXECUTE_PLAN -> ledgerPlanRequestGuidance(helpDescriptor, detail);
      case HELP,
          CAPABILITIES,
          ENVIRONMENT,
          VERSION,
          PRINT_REQUEST_TEMPLATE,
          PRINT_PLAN_TEMPLATE,
          GENERATE_BOOK_KEY_FILE,
          OPEN_BOOK,
          REKEY_BOOK,
          BACKUP_BOOK,
          RESTORE_BOOK,
          INSPECT_REKEY_ROLLBACK,
          DELETE_REKEY_ROLLBACK,
          RESTORE_REKEY_ROLLBACK,
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
      HelpDescriptor helpDescriptor, DiscoveryDetail detail) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().postEntry() == null
        || helpDescriptor.requestTemplate() == null) {
      return Optional.empty();
    }
    return Optional.of(
        new CliDiscoveryJsonModels.RequestFileGuidancePayload(
            "Provide one posting JSON object through --request-file <path|->.",
            detail,
            detail == DiscoveryDetail.FULL ? helpDescriptor.requestTemplate() : null,
            null,
            null,
            detail == DiscoveryDetail.FULL
                ? new ContractRequestShapes.RequestShapesDescriptor(
                    helpDescriptor.requestShapes().schemaDialect(),
                    helpDescriptor.requestShapes().postEntry(),
                    null,
                    null)
                : null,
            CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)));
  }

  private static Optional<CliDiscoveryJsonModels.RequestFileGuidancePayload>
      declareAccountRequestGuidance(HelpDescriptor helpDescriptor, DiscoveryDetail detail) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().declareAccount() == null
        || helpDescriptor.declareAccountTemplate() == null) {
      return Optional.empty();
    }
    return Optional.of(
        new CliDiscoveryJsonModels.RequestFileGuidancePayload(
            "Provide one account-declaration JSON object through --request-file <path|->.",
            detail,
            null,
            detail == DiscoveryDetail.FULL ? helpDescriptor.declareAccountTemplate() : null,
            null,
            detail == DiscoveryDetail.FULL
                ? new ContractRequestShapes.RequestShapesDescriptor(
                    helpDescriptor.requestShapes().schemaDialect(),
                    null,
                    helpDescriptor.requestShapes().declareAccount(),
                    null)
                : null,
            CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                + " "
                + OperationId.DECLARE_ACCOUNT.wireName()));
  }

  private static Optional<CliDiscoveryJsonModels.RequestFileGuidancePayload>
      ledgerPlanRequestGuidance(HelpDescriptor helpDescriptor, DiscoveryDetail detail) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().ledgerPlan() == null
        || helpDescriptor.planTemplate() == null) {
      return Optional.empty();
    }
    return Optional.of(
        new CliDiscoveryJsonModels.RequestFileGuidancePayload(
            "Provide one ledger plan JSON object through --request-file <path|->.",
            detail,
            null,
            null,
            detail == DiscoveryDetail.FULL ? helpDescriptor.planTemplate() : null,
            detail == DiscoveryDetail.FULL
                ? new ContractRequestShapes.RequestShapesDescriptor(
                    helpDescriptor.requestShapes().schemaDialect(),
                    null,
                    null,
                    helpDescriptor.requestShapes().ledgerPlan())
                : null,
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
