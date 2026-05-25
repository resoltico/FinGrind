package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.CommandCatalogDescriptor;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.protocol.OperationCategory;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolExampleStep;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.runtime.StorageSurfaceDescriptor;
import dev.erst.fingrind.contract.runtime.VersionDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Renders discovery descriptors in operator-readable CLI text. */
final class CliDiscoveryOutputRenderer {
  private static final int TEXT_WRAP_WIDTH = 96;

  private CliDiscoveryOutputRenderer() {}

  static String renderHelpText(HelpDescriptor helpDescriptor) {
    Objects.requireNonNull(helpDescriptor, "helpDescriptor");
    if (isCommandScoped(helpDescriptor)) {
      return renderCommandHelpText(helpDescriptor);
    }
    CommandCatalogDescriptor commandCatalog = groupedCommands(helpDescriptor.commands());
    String header =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Version", helpDescriptor.version()),
                List.of("Description", helpDescriptor.description())),
            TEXT_WRAP_WIDTH);
    String startHere =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of(
                    "Task guide",
                    "Run '"
                        + CliInvocationText.commandExample(OperationId.HELP)
                        + " <command>' for syntax, request guidance, and runnable examples."),
                List.of(
                    "Machine contract",
                    "Run '"
                        + CliInvocationText.commandExample(OperationId.CAPABILITIES)
                        + " --output json' for the canonical machine-readable command inventory."),
                List.of(
                    "Runtime evidence",
                    "Run '"
                        + CliInvocationText.commandExample(OperationId.ENVIRONMENT)
                        + " --output json' for live runtime, distribution, and SQLite provenance facts."),
                List.of(
                    "Output defaults",
                    "Selectable commands default to text on interactive terminals and json on redirected stdout. Use --output when a command advertises selectable formats.")),
            TEXT_WRAP_WIDTH);
    String commandGroups =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Discovery", joinCommandNames(commandCatalog.discovery())),
                List.of("Administration", joinCommandNames(commandCatalog.administration())),
                List.of("Query and reports", joinCommandNames(commandCatalog.query())),
                List.of("Write", joinCommandNames(commandCatalog.write()))),
            TEXT_WRAP_WIDTH);
    String exitCodes =
        CliTextFormat.renderKeyValueBlock(
            helpDescriptor.exitCodes().stream()
                .map(exitCode -> List.of(Integer.toString(exitCode.code()), exitCode.meaning()))
                .toList(),
            TEXT_WRAP_WIDTH);
    return CliTextFormat.renderTitledBlock(
        "FinGrind Help",
        joinSections(
            header,
            section("Start Here", startHere),
            section("Command Groups", commandGroups),
            section("Exit Codes", exitCodes)));
  }

  private static String renderCommandHelpText(HelpDescriptor helpDescriptor) {
    CommandDescriptor command = helpDescriptor.commands().getFirst();
    ProtocolOperation operation = ProtocolCatalog.operation(command.name());
    String summary = CliTextFormat.wrap(command.summary(), TEXT_WRAP_WIDTH);
    String usage =
        helpDescriptor.usage().isEmpty()
            ? "(none)"
            : CliTextFormat.renderLiteralBlock(helpDescriptor.usage(), "");
    String options =
        command.options().isEmpty()
            ? "(none)"
            : CliTextFormat.renderLiteralBlock(command.options(), "");
    String requestGuidance = renderRequestGuidance(helpDescriptor, command.name());
    return CliTextFormat.renderTitledBlock(
        command.name().wireName(),
        joinSections(
            summary,
            section("Quick Start", renderCommandExamples(operation)),
            section("Invocation", usage),
            section("Options", options),
            requestGuidance));
  }

  static String renderCapabilitiesText(CapabilitiesDescriptor capabilitiesDescriptor) {
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
                    CliTextFormat.joined(
                        storageDescriptor.engines().stream().map(Object::toString).toList()))),
            TEXT_WRAP_WIDTH);
    String useThisFor =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of(
                    "Use this page",
                    "Inspect shared machine-surface entry points and request-document rules without the operator task guide."),
                List.of(
                    "One command help",
                    "Run '"
                        + CliInvocationText.commandExample(OperationId.HELP)
                        + " <command> --output json' when you only need one command descriptor."),
                List.of(
                    "Machine inventory",
                    "Run '"
                        + CliInvocationText.commandExample(OperationId.CAPABILITIES)
                        + " --output json' for the canonical machine-readable inventory."),
                List.of(
                    "Runtime evidence",
                    "Run '"
                        + CliInvocationText.commandExample(OperationId.ENVIRONMENT)
                        + " --output json' for live runtime and SQLite provenance facts.")),
            TEXT_WRAP_WIDTH);
    String machineSurfaces =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Discovery guide", CliInvocationText.commandExample(OperationId.HELP)),
                List.of(
                    "Canonical inventory",
                    CliInvocationText.commandExample(OperationId.CAPABILITIES) + " --output json"),
                List.of(
                    "Live runtime evidence",
                    CliInvocationText.commandExample(OperationId.ENVIRONMENT) + " --output json"),
                List.of(
                    "Command families",
                    Integer.toString(commandCatalog.discovery().size())
                        + " discovery, "
                        + commandCatalog.administration().size()
                        + " administration, "
                        + commandCatalog.query().size()
                        + " query/report, "
                        + commandCatalog.write().size()
                        + " write")),
            TEXT_WRAP_WIDTH);
    String requestInput =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of(
                    "Selectable stdout flag", capabilitiesDescriptor.requestInput().outputOption()),
                List.of("Book file flag", capabilitiesDescriptor.requestInput().bookFileOption()),
                List.of(
                    "Passphrase routes",
                    String.join(
                        ", ", capabilitiesDescriptor.requestInput().bookPassphraseOptions())),
                List.of(
                    "Request document flag",
                    capabilitiesDescriptor.requestInput().requestFileOption()),
                List.of(
                    "Request document commands",
                    String.join(", ", capabilitiesDescriptor.requestInput().requestFileCommands())),
                List.of("Preflight semantics", capabilitiesDescriptor.preflight().semantics()),
                List.of("PDF reports", pdfCapableReportSummary())),
            TEXT_WRAP_WIDTH);
    return CliTextFormat.renderTitledBlock(
        "FinGrind Capabilities",
        joinSections(
            header,
            section("Use This For", useThisFor),
            section("Machine Surfaces", machineSurfaces),
            section("Shared CLI Contract", requestInput)));
  }

  static String renderEnvironmentText(EnvironmentDescriptor environmentDescriptor) {
    Objects.requireNonNull(environmentDescriptor, "environmentDescriptor");
    EnvironmentSqliteDescriptor.RuntimeState runtime = environmentDescriptor.sqlite().runtime();
    String summary =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Runtime status", runtime.status().wireValue()),
                List.of(
                    "Runtime",
                    environmentDescriptor.distribution().runtimeDistribution().wireValue()),
                List.of(
                    "Storage",
                    environmentDescriptor.storage().storageDriver().wireValue()
                        + " / "
                        + environmentDescriptor.storage().storageEngine().wireValue()),
                List.of(
                    "Protection", environmentDescriptor.storage().bookProtectionMode().wireValue()),
                List.of(
                    "Book format",
                    "v"
                        + environmentDescriptor
                            .storage()
                            .defaultProtectedBookFormat()
                            .formatVersion()
                        + " / "
                        + environmentDescriptor
                            .storage()
                            .defaultProtectedBookFormat()
                            .cipher()
                            .wireValue()),
                List.of("SQLite runtime", environmentDescriptor.sqlite().libraryMode().wireValue()),
                List.of("SQLite", loadedSqliteVersion(runtime)),
                List.of("SQLite3MC", loadedSqlite3mcVersion(runtime)),
                List.of("Issue", runtimeIssue(runtime)),
                List.of(
                    "Full inventory",
                    CliInvocationText.commandExample(OperationId.ENVIRONMENT) + " --output json")),
            TEXT_WRAP_WIDTH);
    return CliTextFormat.renderTitledBlock("FinGrind Environment", summary);
  }

  private static String loadedSqliteVersion(EnvironmentSqliteDescriptor.RuntimeState runtime) {
    return switch (runtime) {
      case EnvironmentSqliteDescriptor.ReadyRuntime ready -> ready.loadedSqliteVersion();
      case EnvironmentSqliteDescriptor.IncompatibleRuntime incompatible ->
          incompatible.loadedSqliteVersion();
      case EnvironmentSqliteDescriptor.FailedRuntime ignored -> "(none)";
      case EnvironmentSqliteDescriptor.UnavailableRuntime ignored -> "(none)";
    };
  }

  private static String loadedSqlite3mcVersion(EnvironmentSqliteDescriptor.RuntimeState runtime) {
    return switch (runtime) {
      case EnvironmentSqliteDescriptor.ReadyRuntime ready -> ready.loadedSqlite3mcVersion();
      case EnvironmentSqliteDescriptor.IncompatibleRuntime incompatible ->
          incompatible.loadedSqlite3mcVersion();
      case EnvironmentSqliteDescriptor.FailedRuntime ignored -> "(none)";
      case EnvironmentSqliteDescriptor.UnavailableRuntime ignored -> "(none)";
    };
  }

  private static String runtimeIssue(EnvironmentSqliteDescriptor.RuntimeState runtime) {
    return switch (runtime) {
      case EnvironmentSqliteDescriptor.ReadyRuntime ignored -> "(none)";
      case EnvironmentSqliteDescriptor.UnavailableRuntime unavailable -> unavailable.runtimeIssue();
      case EnvironmentSqliteDescriptor.FailedRuntime failed -> failed.runtimeIssue();
      case EnvironmentSqliteDescriptor.IncompatibleRuntime incompatible ->
          incompatible.runtimeIssue();
    };
  }

  static String renderVersionText(VersionDescriptor versionDescriptor) {
    Objects.requireNonNull(versionDescriptor, "versionDescriptor");
    return CliTextFormat.renderTitledBlock(
        versionDescriptor.application(),
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Version", versionDescriptor.version()),
                List.of("Description", versionDescriptor.description())),
            TEXT_WRAP_WIDTH));
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

  private static String pdfCapableReportSummary() {
    return String.join(
            ", ",
            CliInvocationText.commandExample(OperationId.ACCOUNT_BALANCE),
            CliInvocationText.commandExample(OperationId.TRIAL_BALANCE),
            CliInvocationText.commandExample(OperationId.ACCOUNT_LEDGER),
            CliInvocationText.commandExample(OperationId.PERIOD_SUMMARY),
            CliInvocationText.commandExample(OperationId.FINANCIAL_POSITION),
            CliInvocationText.commandExample(OperationId.INCOME_STATEMENT))
        + ", and "
        + CliInvocationText.commandExample(OperationId.CHANGES_IN_EQUITY)
        + " can emit pdf via --pdf-out <path>.";
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
            : CliTextFormat.renderShellCommandBlock(commandExamples, TEXT_WRAP_WIDTH));
    if (!notes.isEmpty()) {
      sections.add(
          "Notes:"
              + System.lineSeparator()
              + CliTextFormat.renderBulletedBlock(notes, TEXT_WRAP_WIDTH));
    }
    return String.join(System.lineSeparator() + System.lineSeparator(), sections);
  }

  private static String indent(String text, String prefix) {
    return text.lines()
        .map(line -> prefix + line)
        .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
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
    return section(
        "Request Document",
        requestFileGuidance(
            "Provide one JSON object through --request-file <path|-> and use the scaffold command to generate one valid starting document.",
            CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                + " "
                + operationId.wireName(),
            operationId));
  }

  private static String renderDeclareAccountRequestGuidance(HelpDescriptor helpDescriptor) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().declareAccount() == null
        || helpDescriptor.declareAccountTemplate() == null) {
      return "";
    }
    return section(
        "Request Document",
        requestFileGuidance(
            "Provide one JSON object through --request-file <path|-> and use the scaffold command to generate one valid starting document.",
            CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                + " "
                + OperationId.DECLARE_ACCOUNT.wireName(),
            OperationId.DECLARE_ACCOUNT));
  }

  private static String renderLedgerPlanRequestGuidance(HelpDescriptor helpDescriptor) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().ledgerPlan() == null
        || helpDescriptor.planTemplate() == null) {
      return "";
    }
    return section(
        "Request Document",
        requestFileGuidance(
            "Provide one ledger plan JSON object through --request-file <path|-> and use the scaffold command to generate one valid starting document.",
            CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE),
            OperationId.EXECUTE_PLAN));
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

  private static String requestFileGuidance(
      String introduction, String shortcutCommand, OperationId operationId) {
    List<String> blocks = new ArrayList<>();
    blocks.add(CliTextFormat.wrap(introduction, TEXT_WRAP_WIDTH));
    blocks.add(labeledLiteralBlock("Scaffold command", List.of(shortcutCommand), "$ "));
    blocks.add(
        labeledLiteralBlock(
            "Machine schema",
            List.of(
                CliInvocationText.commandExample(OperationId.HELP)
                    + " "
                    + ProtocolCatalog.operationName(operationId)
                    + " --output json"),
            "$ "));
    return String.join(System.lineSeparator() + System.lineSeparator(), blocks);
  }

  private static String labeledLiteralBlock(String label, List<String> lines, String prefix) {
    return label + System.lineSeparator() + CliTextFormat.renderLiteralBlock(lines, prefix);
  }
}
