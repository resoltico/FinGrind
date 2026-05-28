package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.CommandCatalogDescriptor;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.protocol.OperationCategory;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolExampleStep;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import java.util.ArrayList;
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
    String startHere =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of(
                    "Create one protected book",
                    CliInvocationText.commandExample(OperationId.HELP)
                        + " "
                        + OperationId.OPEN_BOOK.wireName()),
                List.of(
                    "Declare the chart",
                    CliInvocationText.commandExample(OperationId.HELP)
                        + " "
                        + OperationId.DECLARE_ACCOUNT.wireName()),
                List.of(
                    "Preflight or commit bookkeeping",
                    CliInvocationText.commandExample(OperationId.HELP)
                        + " "
                        + OperationId.PREFLIGHT_ENTRY.wireName()
                        + " / "
                        + OperationId.POST_ENTRY.wireName()),
                List.of(
                    "Browse the command index",
                    CliInvocationText.commandExample(OperationId.HELP) + " <command>")),
            CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
    String automation =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of(
                    "Command contract",
                    CliInvocationText.commandExample(OperationId.CAPABILITIES) + " --output json"),
                List.of(
                    "Runtime evidence",
                    CliInvocationText.commandExample(OperationId.ENVIRONMENT) + " --output json")),
            CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
    String commandGroups =
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
            CliDiscoveryTextSupport.section("Start Here", startHere),
            CliDiscoveryTextSupport.section("Command Groups", commandGroups),
            CliDiscoveryTextSupport.section("Automation", automation)));
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
    String requestGuidance = renderRequestGuidance(helpDescriptor, command.name());
    String run = CliDiscoveryTextSupport.section("Run", usage);
    String renderedOptions =
        "(none)".equals(options) ? "" : CliDiscoveryTextSupport.section("Options", options);
    String moreDetail = renderMoreDetailSection(command.name());
    return CliTextFormat.renderTitledBlock(
        command.name().wireName(),
        CliDiscoveryTextSupport.joinSections(
            summary,
            CliDiscoveryTextSupport.section("Try It", renderCommandExamples(operation)),
            requestGuidance,
            run,
            renderedOptions,
            moreDetail));
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
                List.of(
                    CliDiscoveryExampleSelector.selectPrimaryCommandExample(
                        operation.id(), commandExamples)),
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

  private static String renderRequestGuidance(
      HelpDescriptor helpDescriptor, OperationId operationId) {
    return switch (operationId) {
      case POST_ENTRY, PREFLIGHT_ENTRY -> renderPostingRequestGuidance(helpDescriptor, operationId);
      case DECLARE_ACCOUNT -> renderDeclareAccountRequestGuidance(helpDescriptor);
      case EXECUTE_PLAN -> renderLedgerPlanRequestGuidance(helpDescriptor);
      default -> "";
    };
  }

  private static String renderPostingRequestGuidance(
      HelpDescriptor helpDescriptor, OperationId operationId) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().postEntry() == null
        || helpDescriptor.requestTemplate() == null) {
      return "";
    }
    return CliDiscoveryTextSupport.section(
        "Input",
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
        "Input",
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
        "Input",
        requestFileGuidance(
            "Pass one ledger plan JSON object through --request-file <path|->.",
            CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE)));
  }

  private static String requestFileGuidance(String introduction, String shortcutCommand) {
    List<String> blocks = new ArrayList<>();
    blocks.add(CliTextFormat.wrap(introduction, CliDiscoveryTextSupport.TEXT_WRAP_WIDTH));
    blocks.add(
        "Generate a starter document"
            + System.lineSeparator()
            + CliTextFormat.renderLiteralBlock(List.of(shortcutCommand), "$ "));
    return String.join(System.lineSeparator() + System.lineSeparator(), blocks);
  }

  private static String renderMoreDetailSection(OperationId operationId) {
    return CliDiscoveryTextSupport.section(
        "More Detail",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of(
                    "JSON contract",
                    CliInvocationText.commandExample(OperationId.HELP)
                        + " "
                        + ProtocolCatalog.operationName(operationId)
                        + " --output json"))));
  }
}
