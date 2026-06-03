package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.CommandCatalogDescriptor;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.protocol.OperationCategory;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import java.util.List;

/** Renders operator-facing help text for discovery and per-command guidance. */
final class CliDiscoveryHelpTextRenderer {
  private CliDiscoveryHelpTextRenderer() {}

  static String renderHelpText(HelpDescriptor helpDescriptor) {
    if (isCommandScoped(helpDescriptor)) {
      return renderCommandHelpText(helpDescriptor);
    }
    CommandCatalogDescriptor commandCatalog = groupedCommands(helpDescriptor.commands());
    String header =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Version", helpDescriptor.version()),
                List.of("Description", helpDescriptor.description())),
            CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
    String firstSuccessfulRun =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of(
                    "Generate one key file",
                    CliDiscoveryCommandHelpSupport.primaryCommandExample(
                        OperationId.GENERATE_BOOK_KEY_FILE)),
                List.of(
                    "Open one protected book",
                    CliDiscoveryCommandHelpSupport.primaryCommandExample(OperationId.OPEN_BOOK)),
                List.of(
                    "Review the seeded starter chart",
                    CliDiscoveryCommandHelpSupport.primaryCommandExample(
                        OperationId.LIST_ACCOUNTS)),
                List.of(
                    "Print the first entry scaffold",
                    CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                        + " "
                        + OperationId.POST_ENTRY.wireName()
                        + " > request.json"),
                List.of(
                    "Preflight or commit one entry",
                    CliDiscoveryCommandHelpSupport.primaryCommandExample(OperationId.POST_ENTRY)),
                List.of(
                    "Read the first report",
                    CliDiscoveryCommandHelpSupport.primaryCommandExample(
                        OperationId.TRIAL_BALANCE))),
            CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
    String reference =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of(
                    "Inspect one command",
                    CliInvocationText.commandExample(OperationId.HELP) + " <command>"),
                List.of(
                    "Print one request scaffold",
                    CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)),
                List.of(
                    "Print one plan scaffold",
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
            CliDiscoveryTextSupport.section("First Successful Run", firstSuccessfulRun),
            CliDiscoveryTextSupport.section("Reference Commands", reference),
            CliDiscoveryTextSupport.section("Command Families", commandFamilies)));
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

  private static String joinCommandNames(List<CommandDescriptor> commands) {
    return String.join(", ", commands.stream().map(command -> command.name().wireName()).toList());
  }

  private static CommandCatalogDescriptor groupedCommands(List<CommandDescriptor> commands) {
    java.util.Map<OperationCategory, List<CommandDescriptor>> commandsByCategory =
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
