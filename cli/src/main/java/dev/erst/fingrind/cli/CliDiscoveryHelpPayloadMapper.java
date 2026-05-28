package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliDiscoveryJsonModels;
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

/** Maps help descriptors into command-scoped and overview CLI JSON payloads. */
final class CliDiscoveryHelpPayloadMapper {
  private CliDiscoveryHelpPayloadMapper() {}

  static ProtocolSuccessPayload helpPayload(HelpDescriptor helpDescriptor, DiscoveryDetail detail) {
    Objects.requireNonNull(helpDescriptor, "helpDescriptor");
    Objects.requireNonNull(detail, "detail");
    return isCommandScoped(helpDescriptor)
        ? commandHelpPayload(helpDescriptor, detail)
        : switch (detail) {
          case MINIMAL -> minimalHelpOverviewPayload(helpDescriptor);
          case COMPACT -> compactHelpOverviewPayload(helpDescriptor);
          case FULL -> helpOverviewPayload(helpDescriptor);
        };
  }

  private static CliDiscoveryJsonModels.HelpOverviewMinimalPayload minimalHelpOverviewPayload(
      HelpDescriptor helpDescriptor) {
    return new CliDiscoveryJsonModels.HelpOverviewMinimalPayload(
        helpDescriptor.application(),
        helpDescriptor.version(),
        helpDescriptor.description(),
        DiscoveryDetail.MINIMAL,
        commandNamePayloads(helpDescriptor.commands()),
        "Rerun with '"
            + CliInvocationText.commandExample(OperationId.HELP)
            + " --output json "
            + ProtocolOptions.DETAIL
            + " compact' for stable usage, options, and output-contract descriptors.",
        "Rerun with '"
            + CliInvocationText.commandExample(OperationId.HELP)
            + " --output json "
            + ProtocolOptions.DETAIL
            + " full' for the exhaustive discovery descriptor.");
  }

  private static CliDiscoveryJsonModels.HelpOverviewCompactPayload compactHelpOverviewPayload(
      HelpDescriptor helpDescriptor) {
    return new CliDiscoveryJsonModels.HelpOverviewCompactPayload(
        helpDescriptor.application(),
        helpDescriptor.version(),
        helpDescriptor.description(),
        DiscoveryDetail.COMPACT,
        commandIndexPayloads(helpDescriptor.commands()),
        helpDescriptor.exitCodes(),
        "Run '"
            + CliInvocationText.commandExample(OperationId.CAPABILITIES)
            + " --output json' for the stable machine-readable command contract.",
        "Run '"
            + CliInvocationText.commandExample(OperationId.HELP)
            + " --output json "
            + ProtocolOptions.DETAIL
            + " full' for exhaustive discovery grammar, templates, and response contract details.");
  }

  private static CliDiscoveryJsonModels.HelpOverviewPayload helpOverviewPayload(
      HelpDescriptor helpDescriptor) {
    return new CliDiscoveryJsonModels.HelpOverviewPayload(
        helpDescriptor.application(),
        helpDescriptor.version(),
        helpDescriptor.description(),
        DiscoveryDetail.FULL,
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
        helpDescriptor);
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
    if (operationId == OperationId.POST_ENTRY || operationId == OperationId.PREFLIGHT_ENTRY) {
      return postingRequestGuidance(helpDescriptor, detail);
    }
    if (operationId == OperationId.DECLARE_ACCOUNT) {
      return declareAccountRequestGuidance(helpDescriptor, detail);
    }
    if (operationId == OperationId.EXECUTE_PLAN) {
      return ledgerPlanRequestGuidance(helpDescriptor, detail);
    }
    return Optional.empty();
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

  private static List<CliDiscoveryJsonModels.CommandIndexPayload> commandIndexPayloads(
      List<CommandDescriptor> commands) {
    return commands.stream()
        .map(
            command ->
                new CliDiscoveryJsonModels.CommandIndexPayload(
                    command.name(),
                    ProtocolCatalog.operation(command.name()).category().wireValue(),
                    command.summary()))
        .toList();
  }

  private static List<CliDiscoveryJsonModels.CommandNamePayload> commandNamePayloads(
      List<CommandDescriptor> commands) {
    return commands.stream()
        .map(
            command ->
                new CliDiscoveryJsonModels.CommandNamePayload(
                    command.name(),
                    ProtocolCatalog.operation(command.name()).category().wireValue()))
        .toList();
  }
}
