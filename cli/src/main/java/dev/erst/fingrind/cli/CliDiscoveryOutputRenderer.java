package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.CommandCatalogDescriptor;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolExampleStep;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import dev.erst.fingrind.contract.runtime.StorageSurfaceDescriptor;
import dev.erst.fingrind.contract.runtime.VersionDescriptor;
import java.util.List;
import java.util.Objects;

/** Renders discovery descriptors in human-readable CLI text. */
final class CliDiscoveryOutputRenderer {
  private CliDiscoveryOutputRenderer() {}

  static String renderHelpHuman(HelpDescriptor helpDescriptor) {
    Objects.requireNonNull(helpDescriptor, "helpDescriptor");
    if (isCommandScoped(helpDescriptor)) {
      return renderCommandHelpHuman(helpDescriptor);
    }
    String header =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Application", helpDescriptor.application()),
                List.of("Version", helpDescriptor.version()),
                List.of("Description", helpDescriptor.description())));
    String commands =
        CliTextFormat.renderTable(
            List.of("Command", "Stdout", "Summary"),
            helpDescriptor.commands().stream()
                .map(
                    command ->
                        List.of(
                            command.name().wireName(),
                            command.stdoutContractSummary(),
                            command.summary()))
                .toList());
    String gettingStarted =
        String.join(
            System.lineSeparator(),
            List.of(
                "Run 'fingrind help <command>' for command-specific usage, request grammar, and examples.",
                "Run 'fingrind capabilities --output json' for the full machine-readable contract."));
    String exitCodes =
        CliTextFormat.renderTable(
            List.of("Code", "Meaning"),
            helpDescriptor.exitCodes().stream()
                .map(exitCode -> List.of(Integer.toString(exitCode.code()), exitCode.meaning()))
                .toList(),
            0);
    return CliTextFormat.renderTitledBlock(
        "FinGrind Help",
        joinSections(
            header,
            section("Commands", commands),
            section("Getting Started", gettingStarted),
            section("Exit Codes", exitCodes)));
  }

  private static String renderCommandHelpHuman(HelpDescriptor helpDescriptor) {
    CommandDescriptor command = helpDescriptor.commands().getFirst();
    ProtocolOperation operation = ProtocolCatalog.operation(command.name());
    String header =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Application", helpDescriptor.application()),
                List.of("Version", helpDescriptor.version()),
                List.of("Description", helpDescriptor.description())));
    String commandDetails =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Command", command.name().wireName()),
                List.of("Summary", command.summary()),
                List.of("Stdout", command.stdoutContractSummary()),
                List.of("Artifacts", artifactSummary(command)),
                List.of(
                    "Aliases",
                    command.aliases().isEmpty()
                        ? "(none)"
                        : String.join(", ", command.aliases()))));
    String usage =
        helpDescriptor.usage().isEmpty()
            ? "(none)"
            : String.join(System.lineSeparator(), helpDescriptor.usage());
    String options =
        command.options().isEmpty()
            ? "(none)"
            : String.join(System.lineSeparator(), command.options());
    String requestGuidance = renderRequestGuidance(helpDescriptor, command.name());
    String examples = renderCommandExamples(operation);
    String operatorNotes = renderOperatorNotes(operation);
    String exitCodes =
        CliTextFormat.renderTable(
            List.of("Code", "Meaning"),
            helpDescriptor.exitCodes().stream()
                .map(exitCode -> List.of(Integer.toString(exitCode.code()), exitCode.meaning()))
                .toList(),
            0);
    return CliTextFormat.renderTitledBlock(
        "FinGrind Help",
        joinSections(
            header,
            section("Command", commandDetails),
            section("Usage", usage),
            section("Options", options),
            requestGuidance,
            section("Examples", examples),
            operatorNotes,
            section("Exit Codes", exitCodes)));
  }

  static String renderCapabilitiesHuman(CapabilitiesDescriptor capabilitiesDescriptor) {
    Objects.requireNonNull(capabilitiesDescriptor, "capabilitiesDescriptor");
    StorageSurfaceDescriptor storageDescriptor = capabilitiesDescriptor.storage();
    CommandCatalogDescriptor commandCatalog = capabilitiesDescriptor.commands();
    String header =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Application", capabilitiesDescriptor.application()),
                List.of("Version", capabilitiesDescriptor.version()),
                List.of("Book boundary", storageDescriptor.bookBoundary()),
                List.of(
                    "Storage",
                    String.join(
                        ", ", storageDescriptor.engines().stream().map(Object::toString).toList())),
                List.of("Timestamp", capabilitiesDescriptor.timestamp())));
    String commands =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Discovery", joinCommandNames(commandCatalog.discovery())),
                List.of("Administration", joinCommandNames(commandCatalog.administration())),
                List.of("Query", joinCommandNames(commandCatalog.query())),
                List.of("Write", joinCommandNames(commandCatalog.write()))));
    String commandContracts =
        CliTextFormat.renderTable(
            List.of("Command", "Stdout", "Artifacts"),
            commandCatalog.allCommands().stream()
                .map(
                    command ->
                        List.of(
                            command.name().wireName(),
                            command.stdoutContractSummary(),
                            artifactSummary(command)))
                .toList());
    String requestInput =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of(
                    "Selectable stdout flag", capabilitiesDescriptor.requestInput().outputOption()),
                List.of(
                    "Default selectable stdout (interactive terminal)",
                    capabilitiesDescriptor
                        .requestInput()
                        .interactiveDefaultSelectableOutputMode()
                        .wireValue()),
                List.of(
                    "Default selectable stdout (redirected)",
                    capabilitiesDescriptor
                        .requestInput()
                        .redirectedDefaultSelectableOutputMode()
                        .wireValue()),
                List.of("Book file flag", capabilitiesDescriptor.requestInput().bookFileOption()),
                List.of(
                    "Request document flag",
                    capabilitiesDescriptor.requestInput().requestFileOption()),
                List.of(
                    "Request document commands",
                    String.join(", ", capabilitiesDescriptor.requestInput().requestFileCommands())),
                List.of(
                    "Direct-argument commands",
                    String.join(
                        ", ", capabilitiesDescriptor.requestInput().directArgumentCommands())),
                List.of("Preflight semantics", capabilitiesDescriptor.preflight().semantics())));
    return CliTextFormat.renderTitledBlock(
        "FinGrind Capabilities",
        joinSections(
            header,
            section("Command Groups", commands),
            section("Command Contracts", commandContracts),
            section("Request Input", requestInput)));
  }

  static String renderVersionHuman(VersionDescriptor versionDescriptor) {
    Objects.requireNonNull(versionDescriptor, "versionDescriptor");
    return CliTextFormat.renderTitledBlock(
        versionDescriptor.application(),
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Version", versionDescriptor.version()),
                List.of("Description", versionDescriptor.description()))));
  }

  private static String joinSections(String... sections) {
    return java.util.Arrays.stream(sections)
        .filter(section -> !section.isBlank())
        .collect(
            java.util.stream.Collectors.joining(System.lineSeparator() + System.lineSeparator()));
  }

  private static boolean isCommandScoped(HelpDescriptor helpDescriptor) {
    return helpDescriptor.commands().size() == 1 && helpDescriptor.quickStart().isEmpty();
  }

  private static String section(String title, String body) {
    return title
        + System.lineSeparator()
        + "-".repeat(title.length())
        + System.lineSeparator()
        + body;
  }

  private static String joinCommandNames(List<CommandDescriptor> commands) {
    return String.join(", ", commands.stream().map(command -> command.name().wireName()).toList());
  }

  private static String renderCommandExamples(ProtocolOperation operation) {
    List<String> commandExamples =
        operation.exampleSteps().stream()
            .filter(ProtocolExampleStep.Command.class::isInstance)
            .map(ProtocolExampleStep::text)
            .map(CliInvocationText::rewriteInvocationPrefix)
            .toList();
    return commandExamples.isEmpty()
        ? "(none)"
        : String.join(System.lineSeparator(), commandExamples);
  }

  private static String renderOperatorNotes(ProtocolOperation operation) {
    List<String> notes =
        operation.exampleSteps().stream()
            .filter(ProtocolExampleStep.Note.class::isInstance)
            .map(ProtocolExampleStep::text)
            .toList();
    return notes.isEmpty()
        ? ""
        : section("Operator Notes", String.join(System.lineSeparator(), notes));
  }

  private static String indent(String text, String prefix) {
    return text.lines()
        .map(line -> prefix + line)
        .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
  }

  private static String artifactSummary(CommandDescriptor command) {
    if (command.artifactOutputs().isEmpty()) {
      return "(none)";
    }
    return String.join(
        ", ",
        command.artifactOutputs().stream()
            .map(artifact -> artifact.format() + " via " + artifact.option())
            .toList());
  }

  private static String renderRequestGuidance(
      HelpDescriptor helpDescriptor, OperationId operationId) {
    return switch (operationId) {
      case POST_ENTRY, PREFLIGHT_ENTRY -> renderPostingRequestGuidance(helpDescriptor);
      case DECLARE_ACCOUNT -> renderDeclareAccountRequestGuidance(helpDescriptor);
      case EXECUTE_PLAN -> renderLedgerPlanRequestGuidance(helpDescriptor);
      default -> "";
    };
  }

  private static String renderPostingRequestGuidance(HelpDescriptor helpDescriptor) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().postEntry() == null
        || helpDescriptor.requestTemplate() == null) {
      return "";
    }
    ContractRequestShapes.PostEntryRequestShapeDescriptor postEntry =
        helpDescriptor.requestShapes().postEntry();
    return section(
        "Request File",
        joinSections(
            "Provide one JSON object through --request-file <path|->.",
            section(
                "Template",
                renderJsonTemplate(
                    helpDescriptor.requestTemplate(),
                    CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE))),
            renderFieldGroup("Top-Level Fields", postEntry.topLevelFields()),
            renderFieldGroup("Journal Line Fields", postEntry.lineFields()),
            renderFieldGroup("Provenance Fields", postEntry.provenanceFields()),
            renderFieldGroup("Reversal Fields", postEntry.reversalFields()),
            renderEnumVocabularyBlock(postEntry.enumVocabularies())));
  }

  private static String renderDeclareAccountRequestGuidance(HelpDescriptor helpDescriptor) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().declareAccount() == null
        || helpDescriptor.declareAccountTemplate() == null) {
      return "";
    }
    ContractRequestShapes.DeclareAccountRequestShapeDescriptor declareAccount =
        helpDescriptor.requestShapes().declareAccount();
    return section(
        "Request File",
        joinSections(
            "Provide one JSON object through --request-file <path|->.",
            section(
                "Template",
                renderJsonTemplate(
                    helpDescriptor.declareAccountTemplate(),
                    CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                        + " "
                        + OperationId.DECLARE_ACCOUNT.wireName())),
            renderFieldGroup("Top-Level Fields", declareAccount.topLevelFields()),
            renderEnumVocabularyBlock(declareAccount.enumVocabularies())));
  }

  private static String renderLedgerPlanRequestGuidance(HelpDescriptor helpDescriptor) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().ledgerPlan() == null
        || helpDescriptor.planTemplate() == null) {
      return "";
    }
    ContractRequestShapes.LedgerPlanRequestShapeDescriptor ledgerPlan =
        helpDescriptor.requestShapes().ledgerPlan();
    return section(
        "Request File",
        joinSections(
            "Provide one ledger plan JSON object through --request-file <path|->.",
            section(
                "Template",
                renderJsonTemplate(
                    helpDescriptor.planTemplate(),
                    CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE))),
            renderFieldGroup("Top-Level Fields", ledgerPlan.topLevelFields()),
            renderFieldGroup("Step Fields", ledgerPlan.stepFields()),
            renderFieldGroup("Query Fields", ledgerPlan.queryFields()),
            renderFieldGroup("Assertion Fields", ledgerPlan.assertionFields()),
            renderLedgerPlanVocabularies(ledgerPlan)));
  }

  static String renderJsonTemplate(
      Object templateDescriptor, @org.jspecify.annotations.Nullable String shortcutCommand) {
    try {
      String template = CliWireJson.prettyJsonText(templateDescriptor);
      String templateBlock = indent(template, "  ");
      if (shortcutCommand == null) {
        return templateBlock;
      }
      return "Shortcut: "
          + shortcutCommand
          + System.lineSeparator()
          + System.lineSeparator()
          + templateBlock;
    } catch (RuntimeException exception) {
      throw new IllegalStateException(
          "Failed to render CLI help request template JSON.", exception);
    }
  }

  private static String renderFieldGroup(
      String title, List<ContractRequestShapes.RequestFieldDescriptor> fields) {
    return section(
        title,
        CliTextFormat.renderTable(
            List.of("Field", "Presence", "Description"),
            fields.stream()
                .map(
                    field ->
                        List.of(field.name(), field.presence().wireValue(), field.description()))
                .toList()));
  }

  private static String renderEnumVocabularyBlock(
      List<ContractRequestShapes.EnumVocabularyDescriptor> enumVocabularies) {
    if (enumVocabularies.isEmpty()) {
      return "";
    }
    return section(
        "Enum Vocabulary",
        CliTextFormat.renderKeyValueBlock(
            enumVocabularies.stream()
                .map(
                    vocabulary ->
                        List.of(vocabulary.name(), String.join(", ", vocabulary.values())))
                .toList()));
  }

  private static String renderLedgerPlanVocabularies(
      ContractRequestShapes.LedgerPlanRequestShapeDescriptor ledgerPlan) {
    return section(
        "Enum Vocabulary",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of(
                    "administrationStepKinds",
                    joinWireValues(ledgerPlan.administrationStepKinds())),
                List.of("queryStepKinds", joinWireValues(ledgerPlan.queryStepKinds())),
                List.of("writeStepKinds", joinWireValues(ledgerPlan.writeStepKinds())),
                List.of("assertStepKind", ledgerPlan.assertStepKind().wireValue()),
                List.of("assertionKinds", joinWireValues(ledgerPlan.assertionKinds())))));
  }

  private static String joinWireValues(List<? extends dev.erst.fingrind.core.WireValue> values) {
    return values.stream()
        .map(dev.erst.fingrind.core.WireValue::wireValue)
        .collect(java.util.stream.Collectors.joining(", "));
  }
}
