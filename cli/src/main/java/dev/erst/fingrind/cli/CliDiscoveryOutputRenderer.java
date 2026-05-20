package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.CommandCatalogDescriptor;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.discovery.SelectableOutputDefaultsDescriptor;
import dev.erst.fingrind.contract.protocol.OperationCategory;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolExampleStep;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentStorageDescriptor;
import dev.erst.fingrind.contract.runtime.StorageSurfaceDescriptor;
import dev.erst.fingrind.contract.runtime.VersionDescriptor;
import dev.erst.fingrind.core.WireValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Renders discovery descriptors in human-readable CLI text. */
final class CliDiscoveryOutputRenderer {
  private static final int HUMAN_WRAP_WIDTH = 96;

  private CliDiscoveryOutputRenderer() {}

  static String renderHelpHuman(HelpDescriptor helpDescriptor) {
    Objects.requireNonNull(helpDescriptor, "helpDescriptor");
    if (isCommandScoped(helpDescriptor)) {
      return renderCommandHelpHuman(helpDescriptor);
    }
    CommandCatalogDescriptor commandCatalog = groupedCommands(helpDescriptor.commands());
    String header =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Version", helpDescriptor.version()),
                List.of("Description", helpDescriptor.description())),
            HUMAN_WRAP_WIDTH);
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
                    "Selectable commands use human on interactive terminals and json on redirected stdout. Use --output when a command advertises selectable formats.")),
            HUMAN_WRAP_WIDTH);
    String commandGroups =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Discovery", joinCommandNames(commandCatalog.discovery())),
                List.of("Administration", joinCommandNames(commandCatalog.administration())),
                List.of("Query and reports", joinCommandNames(commandCatalog.query())),
                List.of("Write", joinCommandNames(commandCatalog.write()))),
            HUMAN_WRAP_WIDTH);
    String exitCodes =
        CliTextFormat.renderKeyValueBlock(
            helpDescriptor.exitCodes().stream()
                .map(exitCode -> List.of(Integer.toString(exitCode.code()), exitCode.meaning()))
                .toList(),
            HUMAN_WRAP_WIDTH);
    return CliTextFormat.renderTitledBlock(
        "FinGrind Help",
        joinSections(
            header,
            section("Start Here", startHere),
            section("Command Groups", commandGroups),
            section("Exit Codes", exitCodes)));
  }

  private static String renderCommandHelpHuman(HelpDescriptor helpDescriptor) {
    CommandDescriptor command = helpDescriptor.commands().getFirst();
    ProtocolOperation operation = ProtocolCatalog.operation(command.name());
    String summary = CliTextFormat.wrap(command.summary(), HUMAN_WRAP_WIDTH);
    String behavior = CliTextFormat.renderKeyValueBlock(behaviorRows(command), HUMAN_WRAP_WIDTH);
    String usage =
        helpDescriptor.usage().isEmpty()
            ? "(none)"
            : CliTextFormat.wrapLineBlock(helpDescriptor.usage(), HUMAN_WRAP_WIDTH);
    String options =
        command.options().isEmpty()
            ? "(none)"
            : CliTextFormat.wrapLineBlock(command.options(), HUMAN_WRAP_WIDTH);
    String requestGuidance = renderRequestGuidance(helpDescriptor, command.name());
    return CliTextFormat.renderTitledBlock(
        command.name().wireName(),
        joinSections(
            summary,
            section("Invocation", usage),
            section("Options", options),
            section("Output Contract", behavior),
            requestGuidance,
            section("Examples", renderCommandExamples(operation))));
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
                    CliTextFormat.joined(
                        storageDescriptor.engines().stream().map(Object::toString).toList()))),
            HUMAN_WRAP_WIDTH);
    String useThisFor =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of(
                    "Use this page",
                    "Inspect shared machine-surface entry points and request-document rules without the human task guide."),
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
            HUMAN_WRAP_WIDTH);
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
            HUMAN_WRAP_WIDTH);
    String requestInput =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of(
                    "Selectable stdout flag", capabilitiesDescriptor.requestInput().outputOption()),
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
                List.of("Preflight semantics", capabilitiesDescriptor.preflight().semantics()),
                List.of("PDF reports", pdfCapableReportSummary())),
            HUMAN_WRAP_WIDTH);
    return CliTextFormat.renderTitledBlock(
        "FinGrind Capabilities",
        joinSections(
            header,
            section("Use This For", useThisFor),
            section("Machine Surfaces", machineSurfaces),
            section("Shared CLI Contract", requestInput)));
  }

  static String renderEnvironmentHuman(EnvironmentDescriptor environmentDescriptor) {
    Objects.requireNonNull(environmentDescriptor, "environmentDescriptor");
    EnvironmentSqliteDescriptor.RuntimeState runtime = environmentDescriptor.sqlite().runtime();
    String distribution =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of(
                    "Runtime distribution",
                    environmentDescriptor.distribution().runtimeDistribution().wireValue()),
                List.of(
                    "Public CLI distribution",
                    environmentDescriptor.distribution().publicCliDistribution().wireValue()),
                List.of(
                    "Source checkout runtime",
                    environmentDescriptor.distribution().sourceCheckoutJava()),
                List.of(
                    "Supported bundle targets",
                    CliTextFormat.joined(
                        environmentDescriptor
                            .distribution()
                            .supportedPublicCliBundleTargets()
                            .stream()
                            .map(WireValue::wireValue)
                            .toList())),
                List.of(
                    "Unsupported bundle targets",
                    CliTextFormat.joined(
                        environmentDescriptor
                            .distribution()
                            .unsupportedPublicCliBundleTargets()
                            .stream()
                            .map(WireValue::wireValue)
                            .toList()))),
            HUMAN_WRAP_WIDTH);
    String storage =
        CliTextFormat.renderKeyValueBlock(
            storageRows(environmentDescriptor.storage()), HUMAN_WRAP_WIDTH);
    String sqlite =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Library mode", environmentDescriptor.sqlite().libraryMode().wireValue()),
                List.of("Runtime status", runtime.status().wireValue()),
                List.of(
                    "Compile-options verification",
                    runtime.compileOptionsVerification().wireValue()),
                List.of("Runtime provenance", runtimeProvenance(runtime)),
                List.of("Runtime trust basis", runtimeTrustBasis(runtime)),
                List.of("Loaded library path", loadedLibraryPath(runtime)),
                List.of("Loaded SQLite version", loadedSqliteVersion(runtime)),
                List.of("Loaded SQLite3MC version", loadedSqlite3mcVersion(runtime)),
                List.of("Loaded SQLite source id", loadedSqliteSourceId(runtime)),
                List.of("Issue", runtimeIssue(runtime))),
            HUMAN_WRAP_WIDTH);
    return CliTextFormat.renderTitledBlock(
        "FinGrind Environment",
        joinSections(
            section("Distribution", distribution),
            section("Storage Surface", storage),
            section("SQLite Runtime", sqlite)));
  }

  private static String runtimeProvenance(EnvironmentSqliteDescriptor.RuntimeState runtime) {
    return switch (runtime) {
      case EnvironmentSqliteDescriptor.ReadyRuntime ready -> ready.runtimeProvenance().wireValue();
      case EnvironmentSqliteDescriptor.FailedRuntime failed ->
          failed.runtimeProvenance().wireValue();
      case EnvironmentSqliteDescriptor.IncompatibleRuntime incompatible ->
          incompatible.runtimeProvenance().wireValue();
      case EnvironmentSqliteDescriptor.UnavailableRuntime ignored -> "(none)";
    };
  }

  private static String runtimeTrustBasis(EnvironmentSqliteDescriptor.RuntimeState runtime) {
    return switch (runtime) {
      case EnvironmentSqliteDescriptor.ReadyRuntime ready -> ready.runtimeTrustBasis().wireValue();
      case EnvironmentSqliteDescriptor.FailedRuntime failed ->
          failed.runtimeTrustBasis().wireValue();
      case EnvironmentSqliteDescriptor.IncompatibleRuntime incompatible ->
          incompatible.runtimeTrustBasis().wireValue();
      case EnvironmentSqliteDescriptor.UnavailableRuntime ignored -> "(none)";
    };
  }

  private static String loadedLibraryPath(EnvironmentSqliteDescriptor.RuntimeState runtime) {
    return switch (runtime) {
      case EnvironmentSqliteDescriptor.ReadyRuntime ready -> ready.loadedLibraryPath();
      case EnvironmentSqliteDescriptor.FailedRuntime failed -> failed.loadedLibraryPath();
      case EnvironmentSqliteDescriptor.IncompatibleRuntime incompatible ->
          incompatible.loadedLibraryPath();
      case EnvironmentSqliteDescriptor.UnavailableRuntime ignored -> "(none)";
    };
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

  private static String loadedSqliteSourceId(EnvironmentSqliteDescriptor.RuntimeState runtime) {
    return switch (runtime) {
      case EnvironmentSqliteDescriptor.ReadyRuntime ready -> ready.loadedSqliteSourceId();
      case EnvironmentSqliteDescriptor.IncompatibleRuntime incompatible ->
          incompatible.loadedSqliteSourceId();
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

  static String renderVersionHuman(VersionDescriptor versionDescriptor) {
    Objects.requireNonNull(versionDescriptor, "versionDescriptor");
    return CliTextFormat.renderTitledBlock(
        versionDescriptor.application(),
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Version", versionDescriptor.version()),
                List.of("Description", versionDescriptor.description())),
            HUMAN_WRAP_WIDTH));
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

  private static List<List<String>> behaviorRows(CommandDescriptor command) {
    List<List<String>> rows = new ArrayList<>();
    if (!command.aliases().isEmpty()) {
      rows.add(List.of("Aliases", String.join(", ", command.aliases())));
    }
    rows.add(List.of("Stdout", command.stdoutContractSummary()));
    rows.add(List.of("Machine framing", command.executionMode().wireValue()));
    rows.add(List.of("Artifact outputs", artifactSummary(command)));
    rows.add(
        List.of(
            "Selectable defaults", selectableDefaultsSummary(command.selectableOutputDefaults())));
    return List.copyOf(rows);
  }

  private static List<List<String>> storageRows(EnvironmentStorageDescriptor storageDescriptor) {
    return List.of(
        List.of("Driver", storageDescriptor.storageDriver().wireValue()),
        List.of("Engine", storageDescriptor.storageEngine().wireValue()),
        List.of("Protection mode", storageDescriptor.bookProtectionMode().wireValue()),
        List.of(
            "Default protected-book format",
            "v"
                + storageDescriptor.defaultProtectedBookFormat().formatVersion()
                + " / "
                + storageDescriptor.defaultProtectedBookFormat().cipher().wireValue()),
        List.of(
            "Application id",
            Integer.toString(storageDescriptor.defaultProtectedBookFormat().applicationId())),
        List.of(
            "Page size",
            Integer.toString(storageDescriptor.defaultProtectedBookFormat().pageSize())),
        List.of(
            "Reserved bytes",
            Integer.toString(storageDescriptor.defaultProtectedBookFormat().reservedBytes())),
        List.of(
            "Legacy page size",
            Integer.toString(storageDescriptor.defaultProtectedBookFormat().legacyPageSize())),
        List.of(
            "KDF iterations",
            Integer.toString(storageDescriptor.defaultProtectedBookFormat().kdfIter())),
        List.of(
            "Plaintext header bytes",
            Integer.toString(
                storageDescriptor.defaultProtectedBookFormat().plaintextHeaderSize())));
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
            : CliTextFormat.wrapLineBlock(commandExamples, HUMAN_WRAP_WIDTH));
    if (!notes.isEmpty()) {
      sections.add(
          "Notes:"
              + System.lineSeparator()
              + CliTextFormat.renderBulletedBlock(notes, HUMAN_WRAP_WIDTH));
    }
    return String.join(System.lineSeparator() + System.lineSeparator(), sections);
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
    ContractRequestShapes.PostEntryRequestShapeDescriptor postEntryShape =
        helpDescriptor.requestShapes().postEntry();
    return section(
        "Request Document",
        requestFileGuidance(
            "Provide one JSON object through --request-file <path|->.",
            CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                + " "
                + operationId.wireName(),
            operationId,
            acceptedValueRows(postEntryShape.enumVocabularies())));
  }

  private static String renderDeclareAccountRequestGuidance(HelpDescriptor helpDescriptor) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().declareAccount() == null
        || helpDescriptor.declareAccountTemplate() == null) {
      return "";
    }
    ContractRequestShapes.DeclareAccountRequestShapeDescriptor declareAccountShape =
        helpDescriptor.requestShapes().declareAccount();
    return section(
        "Request Document",
        requestFileGuidance(
            "Provide one JSON object through --request-file <path|->.",
            CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                + " "
                + OperationId.DECLARE_ACCOUNT.wireName(),
            OperationId.DECLARE_ACCOUNT,
            acceptedValueRows(declareAccountShape.enumVocabularies())));
  }

  private static String renderLedgerPlanRequestGuidance(HelpDescriptor helpDescriptor) {
    if (helpDescriptor.requestShapes() == null
        || helpDescriptor.requestShapes().ledgerPlan() == null
        || helpDescriptor.planTemplate() == null) {
      return "";
    }
    ContractRequestShapes.LedgerPlanRequestShapeDescriptor ledgerPlanShape =
        helpDescriptor.requestShapes().ledgerPlan();
    return section(
        "Request Document",
        requestFileGuidance(
            "Provide one ledger plan JSON object through --request-file <path|->.",
            CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE),
            OperationId.EXECUTE_PLAN,
            ledgerPlanAcceptedValueRows(ledgerPlanShape)));
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
      String introduction,
      String shortcutCommand,
      OperationId operationId,
      List<List<String>> acceptedValuesRows) {
    List<String> paragraphs = new ArrayList<>();
    paragraphs.add(CliTextFormat.wrap(introduction, HUMAN_WRAP_WIDTH));
    paragraphs.add(
        CliTextFormat.wrap(
            "Generate a runnable sample document with: " + shortcutCommand, HUMAN_WRAP_WIDTH));
    paragraphs.add(
        CliTextFormat.wrap(
            "Inspect the machine-readable contract with: "
                + CliInvocationText.commandExample(OperationId.HELP)
                + " "
                + ProtocolCatalog.operationName(operationId)
                + " --output json",
            HUMAN_WRAP_WIDTH));
    if (!acceptedValuesRows.isEmpty()) {
      paragraphs.add(
          "Accepted value vocabularies:"
              + System.lineSeparator()
              + CliTextFormat.renderKeyValueBlock(
                  List.copyOf(acceptedValuesRows), HUMAN_WRAP_WIDTH));
    }
    return String.join(System.lineSeparator() + System.lineSeparator(), paragraphs);
  }

  private static List<List<String>> acceptedValueRows(
      List<ContractRequestShapes.EnumVocabularyDescriptor> enumVocabularies) {
    if (enumVocabularies.isEmpty()) {
      return List.of();
    }
    return enumVocabularies.stream()
        .map(descriptor -> List.of(descriptor.name(), String.join(", ", descriptor.values())))
        .toList();
  }

  private static List<List<String>> ledgerPlanAcceptedValueRows(
      ContractRequestShapes.LedgerPlanRequestShapeDescriptor ledgerPlanShape) {
    return List.of(
        List.of(
            "steps[].kind (administration)",
            joinWireValues(ledgerPlanShape.administrationStepKinds())),
        List.of("steps[].kind (query)", joinWireValues(ledgerPlanShape.queryStepKinds())),
        List.of("steps[].kind (write)", joinWireValues(ledgerPlanShape.writeStepKinds())),
        List.of("steps[].kind (assert)", ledgerPlanShape.assertStepKind().wireValue()),
        List.of("steps[].assertion.kind", joinWireValues(ledgerPlanShape.assertionKinds())));
  }

  private static String joinWireValues(List<? extends WireValue> wireValues) {
    return wireValues.stream()
        .map(WireValue::wireValue)
        .collect(java.util.stream.Collectors.joining(", "));
  }

  private static String selectableDefaultsSummary(
      @org.jspecify.annotations.Nullable SelectableOutputDefaultsDescriptor defaults) {
    if (defaults == null) {
      return "(fixed)";
    }
    if (defaults.interactiveTerminal() == defaults.redirectedStdout()) {
      return defaults.interactiveTerminal().wireValue();
    }
    return defaults.interactiveTerminal().wireValue()
        + " interactive / "
        + defaults.redirectedStdout().wireValue()
        + " redirected";
  }
}
