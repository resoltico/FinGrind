package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.CommandCatalogDescriptor;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.discovery.WorkflowDescriptor;
import dev.erst.fingrind.contract.discovery.WorkflowStepDescriptor;
import dev.erst.fingrind.contract.protocol.OperationCategory;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolExampleStep;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentRuntimeDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Renders operator-facing help text for discovery and per-command guidance. */
final class CliDiscoveryHelpTextRenderer {
  private CliDiscoveryHelpTextRenderer() {}

  static String renderHelpText(
      HelpDescriptor helpDescriptor,
      EnvironmentDescriptor environmentDescriptor,
      boolean terseTopLevel) {
    if (isCommandScoped(helpDescriptor)) {
      return renderCommandHelpText(helpDescriptor);
    }
    if (terseTopLevel) {
      return renderTopLevelSynopsis(helpDescriptor, environmentDescriptor);
    }
    CommandCatalogDescriptor commandCatalog = groupedCommands(helpDescriptor.commands());
    String header =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Version", helpDescriptor.version()),
                List.of("Protocol version", helpDescriptor.protocolVersion()),
                List.of("Description", helpDescriptor.description()),
                List.of(
                    "Default output mode", defaultOutputLabel(environmentDescriptor.runtime()))),
            CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
    String firstSuccessfulRun = renderQuickStart(helpDescriptor.quickStart());
    String reference =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of(
                    "Inspect a command",
                    CliInvocationText.commandExample(OperationId.HELP) + " <command>"),
                List.of(
                    "Print a request scaffold",
                    CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)),
                List.of(
                    "Print a plan scaffold",
                    CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE))),
            CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
    String commandFamilies =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Discovery", joinCommandNames(commandCatalog.discovery())),
                List.of("Administration", joinCommandNames(commandCatalog.administration())),
                List.of("Query and reports", joinCommandNames(commandCatalog.query())),
                List.of("Write", joinCommandNames(commandCatalog.write()))),
            CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
    return CliTextFormat.renderTitledBlock(
        "FinGrind Help",
        CliDiscoveryTextSupport.joinSections(
            header,
            keyFilePathGuidanceSection(),
            CliDiscoveryTextSupport.section("Quick Start", firstSuccessfulRun),
            CliDiscoveryTextSupport.section("Reference", reference),
            CliDiscoveryTextSupport.section("Command Catalog", commandFamilies)));
  }

  private static String renderTopLevelSynopsis(
      HelpDescriptor helpDescriptor, EnvironmentDescriptor environmentDescriptor) {
    CommandCatalogDescriptor commandCatalog = groupedCommands(helpDescriptor.commands());
    String header =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Version", helpDescriptor.version()),
                List.of("Protocol version", helpDescriptor.protocolVersion()),
                List.of("Description", helpDescriptor.description()),
                List.of("Default output mode", defaultOutputLabel(environmentDescriptor.runtime())),
                List.of("Full guide", CliInvocationText.commandExample(OperationId.HELP))),
            CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
    String shortcuts =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of(
                    "Command help",
                    CliInvocationText.commandExample(OperationId.HELP) + " <command>"),
                List.of(
                    "Commands",
                    joinCommandNames(helpDescriptor.commands().stream().limit(8).toList())),
                List.of("Categories", "discovery, administration, query/report, write")),
            CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
    String commandFamilies =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Discovery", joinCommandNames(commandCatalog.discovery())),
                List.of("Administration", joinCommandNames(commandCatalog.administration())),
                List.of("Query and reports", joinCommandNames(commandCatalog.query())),
                List.of("Write", joinCommandNames(commandCatalog.write()))),
            CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
    return CliTextFormat.renderTitledBlock(
        "FinGrind",
        CliDiscoveryTextSupport.joinSections(
            header,
            keyFilePathGuidanceSection(),
            CliDiscoveryTextSupport.section("Shortcuts", shortcuts),
            CliDiscoveryTextSupport.section("Command Catalog", commandFamilies)));
  }

  static String renderJsonTemplate(
      Object templateDescriptor, @org.jspecify.annotations.Nullable String shortcutCommand) {
    try {
      String template = CliWireJson.prettyJsonText(templateDescriptor);
      String templateBlock = CliDiscoveryTextSupport.indent(template, "  ");
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

  private static String renderCommandHelpText(HelpDescriptor helpDescriptor) {
    return CliDiscoveryCommandHelpSupport.renderCommandHelpText(helpDescriptor);
  }

  private static boolean isCommandScoped(HelpDescriptor helpDescriptor) {
    return helpDescriptor.commands().size() == 1 && helpDescriptor.quickStart().isEmpty();
  }

  private static String renderQuickStart(List<WorkflowDescriptor> quickStart) {
    if (quickStart.isEmpty()) {
      return CliTextFormat.renderKeyValueBlock(
          List.of(
              List.of(
                  "Generate a key file",
                  CliDiscoveryCommandHelpSupport.primaryCommandExample(
                      OperationId.GENERATE_BOOK_KEY_FILE)),
              List.of(
                  "Open a protected book",
                  CliDiscoveryCommandHelpSupport.primaryCommandExample(OperationId.OPEN_BOOK)),
              List.of(
                  "Review the seeded accounts",
                  CliDiscoveryCommandHelpSupport.primaryCommandExample(OperationId.LIST_ACCOUNTS)),
              List.of(
                  "Create the first settled-sale request",
                  CliDiscoveryCommandHelpSupport.primaryStarterRequestCommand(
                      OperationId.RECORD_SALE_SETTLED)),
              List.of(
                  "Validate the first settled-sale request",
                  CliDiscoveryCommandHelpSupport.primaryCommandExample(
                      OperationId.PREFLIGHT_ENTRY)),
              List.of(
                  "Commit the first settled sale",
                  CliDiscoveryCommandHelpSupport.primaryCommandExample(
                      OperationId.RECORD_SALE_SETTLED)),
              List.of(
                  "Read the first report",
                  CliDiscoveryCommandHelpSupport.primaryCommandExample(OperationId.TRIAL_BALANCE))),
          CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
    }
    WorkflowDescriptor workflow = quickStart.getFirst();
    List<String> sections = new ArrayList<>();
    workflow.steps().stream()
        .filter(WorkflowStepDescriptor.Note.class::isInstance)
        .map(WorkflowStepDescriptor.Note.class::cast)
        .findFirst()
        .ifPresent(
            note ->
                sections.add(
                    CliTextFormat.wrap(note.text(), CliDiscoveryTextSupport.TEXT_WRAP_WIDTH)));
    List<String> commands =
        workflow.steps().stream()
            .filter(WorkflowStepDescriptor.Command.class::isInstance)
            .map(WorkflowStepDescriptor.Command.class::cast)
            .map(WorkflowStepDescriptor.Command::text)
            .toList();
    if (!commands.isEmpty()) {
      sections.add(renderQuickStartCommands(commands));
    }
    List<String> notes =
        workflow.steps().stream()
            .filter(WorkflowStepDescriptor.Note.class::isInstance)
            .map(WorkflowStepDescriptor.Note.class::cast)
            .skip(1)
            .map(WorkflowStepDescriptor.Note::text)
            .toList();
    if (!notes.isEmpty()) {
      sections.add(
          "Operator guidance:"
              + System.lineSeparator()
              + CliTextFormat.renderBulletedBlock(notes, CliDiscoveryTextSupport.TEXT_WRAP_WIDTH));
    }
    return String.join(System.lineSeparator() + System.lineSeparator(), sections);
  }

  private static String renderQuickStartCommands(List<String> commands) {
    List<String> sections = new ArrayList<>();
    for (int index = 0; index < commands.size(); index++) {
      String command = commands.get(index);
      sections.add(
          quickStartCommandLabel(command, index)
              + System.lineSeparator()
              + CliTextFormat.renderShellCommandBlock(
                  List.of(command), CliDiscoveryTextSupport.TEXT_WRAP_WIDTH));
    }
    return String.join(System.lineSeparator() + System.lineSeparator(), sections);
  }

  private static String quickStartCommandLabel(String command, int index) {
    if (command.contains(ProtocolCatalog.operationName(OperationId.GENERATE_BOOK_KEY_FILE))) {
      return "Generate a key file";
    }
    if (command.contains(ProtocolCatalog.operationName(OperationId.OPEN_BOOK))) {
      return "Open a protected book";
    }
    if (command.contains(ProtocolCatalog.operationName(OperationId.LIST_ACCOUNTS))) {
      return "Review the seeded accounts";
    }
    if (command.contains("quick-start-request.json")
        || command.contains(ProtocolCatalog.operationName(OperationId.PRINT_REQUEST_TEMPLATE))) {
      return "Create the first settled-sale request";
    }
    if (command.contains(ProtocolCatalog.operationName(OperationId.PREFLIGHT_ENTRY))) {
      return "Validate the first settled-sale request";
    }
    if (command.contains(ProtocolCatalog.operationName(OperationId.RECORD_SALE_SETTLED))) {
      return "Commit the first settled sale";
    }
    if (command.contains(ProtocolCatalog.operationName(OperationId.POST_ENTRY))) {
      return "Commit the first entry";
    }
    if (command.contains(ProtocolCatalog.operationName(OperationId.TRIAL_BALANCE))) {
      return "Read the first report";
    }
    return "Step " + (index + 1);
  }

  private static String keyFilePathGuidanceSection() {
    return keyFilePathGuidanceSection(keyFilePathGuidance());
  }

  static String keyFilePathGuidanceSection(String guidance) {
    return guidance.isBlank() ? "" : CliDiscoveryTextSupport.section("Key-File Path", guidance);
  }

  private static String keyFilePathGuidance() {
    return ProtocolCatalog.operation(OperationId.GENERATE_BOOK_KEY_FILE).exampleSteps().stream()
        .filter(ProtocolExampleStep.Note.class::isInstance)
        .map(ProtocolExampleStep.Note.class::cast)
        .map(ProtocolExampleStep.Note::text)
        .findFirst()
        .map(note -> CliTextFormat.wrap(note, CliDiscoveryTextSupport.TEXT_WRAP_WIDTH))
        .orElse("");
  }

  private static String joinCommandNames(List<CommandDescriptor> commands) {
    return String.join(", ", commands.stream().map(command -> command.name().wireName()).toList());
  }

  private static String defaultOutputLabel(EnvironmentRuntimeDescriptor runtimeDescriptor) {
    String mode = runtimeDescriptor.defaultOutputMode().wireValue();
    return runtimeDescriptor.defaultOutputModeSource() == null
        ? mode + " (built in)"
        : mode + " via " + runtimeDescriptor.defaultOutputModeSource();
  }

  private static CommandCatalogDescriptor groupedCommands(List<CommandDescriptor> commands) {
    Map<OperationCategory, List<CommandDescriptor>> commandsByCategory =
        commands.stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    command -> ProtocolCatalog.operation(command.name()).category()));
    return new CommandCatalogDescriptor(
        commandsByCategory.getOrDefault(OperationCategory.DISCOVERY, List.of()),
        commandsByCategory.getOrDefault(OperationCategory.ADMINISTRATION, List.of()),
        commandsByCategory.getOrDefault(OperationCategory.QUERY, List.of()),
        commandsByCategory.getOrDefault(OperationCategory.WRITE, List.of()));
  }
}
