package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliDiscoveryCommonJsonModels;
import dev.erst.fingrind.cli.json.CliDiscoveryHelpJsonModels;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.OperationCategory;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolExampleStep;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Maps help descriptors into command-scoped and overview CLI JSON payloads. */
final class CliDiscoveryHelpPayloadMapper {
  private CliDiscoveryHelpPayloadMapper() {}

  static ProtocolSuccessPayload helpPayload(
      HelpDescriptor helpDescriptor, DiscoveryDetail detail, @Nullable OperationCategory category) {
    Objects.requireNonNull(helpDescriptor, "helpDescriptor");
    Objects.requireNonNull(detail, "detail");
    HelpDescriptor selectedHelp =
        category == null || isCommandScoped(helpDescriptor)
            ? helpDescriptor
            : filteredHelpDescriptor(helpDescriptor, category);
    return isCommandScoped(selectedHelp)
        ? commandHelpPayload(selectedHelp, detail)
        : switch (detail) {
          case MINIMAL -> minimalHelpOverviewPayload(selectedHelp, category);
          case COMPACT -> compactHelpOverviewPayload(selectedHelp, category);
          case FULL -> helpOverviewPayload(selectedHelp, category);
        };
  }

  private static CliDiscoveryHelpJsonModels.HelpOverviewMinimalPayload minimalHelpOverviewPayload(
      HelpDescriptor helpDescriptor, @Nullable OperationCategory category) {
    return new CliDiscoveryHelpJsonModels.HelpOverviewMinimalPayload(
        helpDescriptor.application(),
        helpDescriptor.version(),
        helpDescriptor.protocolVersion(),
        helpDescriptor.description(),
        DiscoveryDetail.MINIMAL,
        category == null ? null : category.wireValue(),
        commandNamePayloads(helpDescriptor.commands()),
        "See --detail compact.",
        "See --detail full.");
  }

  private static CliDiscoveryHelpJsonModels.HelpOverviewCompactPayload compactHelpOverviewPayload(
      HelpDescriptor helpDescriptor, @Nullable OperationCategory category) {
    return new CliDiscoveryHelpJsonModels.HelpOverviewCompactPayload(
        helpDescriptor.application(),
        helpDescriptor.version(),
        helpDescriptor.protocolVersion(),
        helpDescriptor.description(),
        DiscoveryDetail.COMPACT,
        category == null ? null : category.wireValue(),
        commandIndexPayloads(helpDescriptor.commands()),
        helpDescriptor.exitCodes(),
        "Use '"
            + CliInvocationText.commandExample(OperationId.CAPABILITIES)
            + " --output json' for the stable machine-readable command contract.",
        "Use '"
            + CliInvocationText.commandExample(OperationId.HELP)
            + " --output json "
            + ProtocolOptions.DETAIL
            + " full' for exhaustive discovery grammar, templates, and response contract details.");
  }

  private static CliDiscoveryHelpJsonModels.HelpOverviewPayload helpOverviewPayload(
      HelpDescriptor helpDescriptor, @Nullable OperationCategory category) {
    return new CliDiscoveryHelpJsonModels.HelpOverviewPayload(
        helpDescriptor.application(),
        helpDescriptor.version(),
        helpDescriptor.protocolVersion(),
        helpDescriptor.description(),
        DiscoveryDetail.FULL,
        category == null ? null : category.wireValue(),
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

  private static HelpDescriptor filteredHelpDescriptor(
      HelpDescriptor helpDescriptor, OperationCategory category) {
    List<CommandDescriptor> filteredCommands =
        helpDescriptor.commands().stream()
            .filter(command -> ProtocolCatalog.operation(command.name()).category() == category)
            .toList();
    return new HelpDescriptor(
        helpDescriptor.application(),
        helpDescriptor.version(),
        helpDescriptor.protocolVersion(),
        helpDescriptor.description(),
        helpDescriptor.usage(),
        helpDescriptor.bookModel(),
        helpDescriptor.bookkeepingKernel(),
        helpDescriptor.requestShapes(),
        helpDescriptor.requestTemplate(),
        helpDescriptor.declareAccountTemplate(),
        helpDescriptor.declareTaxRegistrationTemplate(),
        helpDescriptor.planTemplate(),
        filteredCommands,
        helpDescriptor.quickStart(),
        helpDescriptor.exitCodes(),
        helpDescriptor.preflight(),
        helpDescriptor.currencyModel());
  }

  private static CliDiscoveryHelpJsonModels.CommandHelpPayload commandHelpPayload(
      HelpDescriptor helpDescriptor, DiscoveryDetail detail) {
    CommandDescriptor command = helpDescriptor.commands().getFirst();
    ProtocolOperation operation = ProtocolCatalog.operation(command.name());
    return new CliDiscoveryHelpJsonModels.CommandHelpPayload(
        helpDescriptor.application(),
        helpDescriptor.version(),
        helpDescriptor.protocolVersion(),
        helpDescriptor.description(),
        detail,
        command,
        operation.usage(),
        helpDescriptor.usage(),
        command.options(),
        requestFileGuidance(helpDescriptor, command.name(), detail).orElse(null),
        commandExamples(operation),
        operatorNotes(operation),
        helpDescriptor.exitCodes());
  }

  private static Optional<CliDiscoveryCommonJsonModels.RequestFileGuidancePayload>
      requestFileGuidance(
          HelpDescriptor helpDescriptor, OperationId operationId, DiscoveryDetail detail) {
    if (isPostingRequestOperation(operationId)) {
      return postingRequestGuidance(helpDescriptor, operationId, detail);
    }
    if (operationId == OperationId.DECLARE_ACCOUNT) {
      return declareAccountRequestGuidance(helpDescriptor, detail);
    }
    if (operationId == OperationId.DECLARE_TAX_REGISTRATION) {
      return declareTaxRegistrationRequestGuidance(helpDescriptor, detail);
    }
    if (operationId == OperationId.EXECUTE_PLAN) {
      return ledgerPlanRequestGuidance(helpDescriptor, detail);
    }
    return Optional.empty();
  }

  private static Optional<CliDiscoveryCommonJsonModels.RequestFileGuidancePayload>
      postingRequestGuidance(
          HelpDescriptor helpDescriptor, OperationId operationId, DiscoveryDetail detail) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().bookkeepingEntry() == null
        || helpDescriptor.requestTemplate() == null) {
      return Optional.empty();
    }
    return Optional.of(
        new CliDiscoveryCommonJsonModels.RequestFileGuidancePayload(
            "Provide a posting JSON document through --request-file <path|->.",
            detail,
            detail == DiscoveryDetail.FULL ? helpDescriptor.requestTemplate() : null,
            null,
            null,
            null,
            detail == DiscoveryDetail.FULL
                ? new ContractRequestShapes.RequestShapesDescriptor(
                    helpDescriptor.requestShapes().schemaDialect(),
                    helpDescriptor.requestShapes().bookkeepingEntry(),
                    null,
                    null,
                    null)
                : null,
            CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                + " "
                + operationId.wireName()));
  }

  private static Optional<CliDiscoveryCommonJsonModels.RequestFileGuidancePayload>
      declareAccountRequestGuidance(HelpDescriptor helpDescriptor, DiscoveryDetail detail) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().declareAccount() == null
        || helpDescriptor.declareAccountTemplate() == null) {
      return Optional.empty();
    }
    return Optional.of(
        new CliDiscoveryCommonJsonModels.RequestFileGuidancePayload(
            "Provide an account-declaration JSON document through --request-file <path|->.",
            detail,
            null,
            detail == DiscoveryDetail.FULL ? helpDescriptor.declareAccountTemplate() : null,
            null,
            null,
            detail == DiscoveryDetail.FULL
                ? new ContractRequestShapes.RequestShapesDescriptor(
                    helpDescriptor.requestShapes().schemaDialect(),
                    null,
                    helpDescriptor.requestShapes().declareAccount(),
                    null,
                    null)
                : null,
            CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                + " "
                + OperationId.DECLARE_ACCOUNT.wireName()));
  }

  private static Optional<CliDiscoveryCommonJsonModels.RequestFileGuidancePayload>
      declareTaxRegistrationRequestGuidance(HelpDescriptor helpDescriptor, DiscoveryDetail detail) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().declareTaxRegistration() == null
        || helpDescriptor.declareTaxRegistrationTemplate() == null) {
      return Optional.empty();
    }
    return Optional.of(
        new CliDiscoveryCommonJsonModels.RequestFileGuidancePayload(
            "Provide a tax-registration declaration JSON document through --request-file <path|->.",
            detail,
            null,
            null,
            detail == DiscoveryDetail.FULL ? helpDescriptor.declareTaxRegistrationTemplate() : null,
            null,
            detail == DiscoveryDetail.FULL
                ? new ContractRequestShapes.RequestShapesDescriptor(
                    helpDescriptor.requestShapes().schemaDialect(),
                    null,
                    null,
                    helpDescriptor.requestShapes().declareTaxRegistration(),
                    null)
                : null,
            CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                + " "
                + OperationId.DECLARE_TAX_REGISTRATION.wireName()));
  }

  private static Optional<CliDiscoveryCommonJsonModels.RequestFileGuidancePayload>
      ledgerPlanRequestGuidance(HelpDescriptor helpDescriptor, DiscoveryDetail detail) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().ledgerPlan() == null
        || helpDescriptor.planTemplate() == null) {
      return Optional.empty();
    }
    if (detail == DiscoveryDetail.FULL) {
      helpDescriptor.planTemplate().canonicalPostingScaffoldStep();
    }
    return Optional.of(
        new CliDiscoveryCommonJsonModels.RequestFileGuidancePayload(
            "Provide a ledger plan JSON document through --request-file <path|->.",
            detail,
            null,
            null,
            null,
            detail == DiscoveryDetail.FULL ? helpDescriptor.planTemplate() : null,
            detail == DiscoveryDetail.FULL
                ? new ContractRequestShapes.RequestShapesDescriptor(
                    helpDescriptor.requestShapes().schemaDialect(),
                    null,
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

  private static boolean isPostingRequestOperation(OperationId operationId) {
    return operationId == OperationId.POST_ENTRY
        || operationId == OperationId.PREFLIGHT_ENTRY
        || operationId == OperationId.RECORD_SALE_SETTLED
        || operationId == OperationId.RECORD_SALE_ON_CREDIT
        || operationId == OperationId.RECORD_EXPENSE_SETTLED
        || operationId == OperationId.RECORD_EXPENSE_ON_CREDIT
        || operationId == OperationId.RECORD_RECEIPT
        || operationId == OperationId.RECORD_PAYMENT
        || operationId == OperationId.RECORD_OWNER_CONTRIBUTION
        || operationId == OperationId.RECORD_OWNER_WITHDRAWAL
        || operationId == OperationId.RECORD_OPENING_POSITION
        || operationId == OperationId.RECORD_REVERSAL;
  }

  private static boolean isCommandScoped(HelpDescriptor helpDescriptor) {
    return helpDescriptor.commands().size() == 1 && helpDescriptor.quickStart().isEmpty();
  }

  private static List<CliDiscoveryCommonJsonModels.CommandIndexPayload> commandIndexPayloads(
      List<CommandDescriptor> commands) {
    return commands.stream()
        .map(
            command ->
                new CliDiscoveryCommonJsonModels.CommandIndexPayload(
                    command.name(),
                    ProtocolCatalog.operation(command.name()).category().wireValue(),
                    command.summary()))
        .toList();
  }

  private static List<CliDiscoveryCommonJsonModels.CommandNamePayload> commandNamePayloads(
      List<CommandDescriptor> commands) {
    return commands.stream()
        .map(
            command ->
                new CliDiscoveryCommonJsonModels.CommandNamePayload(
                    command.name(),
                    ProtocolCatalog.operation(command.name()).category().wireValue()))
        .toList();
  }
}
