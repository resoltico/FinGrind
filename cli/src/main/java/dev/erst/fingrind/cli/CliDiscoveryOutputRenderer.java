package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.CommandCatalogDescriptor;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.discovery.SelectableOutputDefaultsDescriptor;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolExampleStep;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.runtime.StorageSurfaceDescriptor;
import dev.erst.fingrind.contract.runtime.VersionDescriptor;
import dev.erst.fingrind.core.WireValue;
import java.util.ArrayList;
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
            List.of("Command", "Output", "Summary"),
            helpDescriptor.commands().stream()
                .map(
                    command ->
                        List.of(
                            command.name().wireName(),
                            command.outputModeSummary(),
                            command.summary()))
                .toList());
    String gettingStarted =
        String.join(
            System.lineSeparator(),
            List.of(
                "Run '"
                    + CliInvocationText.commandExample(OperationId.HELP)
                    + " <command>' for command-specific usage, request-file guidance, and examples.",
                "Run '"
                    + CliInvocationText.commandExample(OperationId.CAPABILITIES)
                    + " --output json' for the stable machine-readable command contract.",
                "Run '"
                    + CliInvocationText.commandExample(OperationId.ENVIRONMENT)
                    + " --output json' for live runtime, distribution, and SQLite provenance facts."));
    String stdoutDefaults =
        "Selectable commands default to human on one interactive terminal and json on redirected stdout. Override with --output when one command advertises selectable output modes.";
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
            section("Stdout Defaults", stdoutDefaults),
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
                        ", ",
                        storageDescriptor.engines().stream().map(Object::toString).toList()))));
    String commands =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Discovery", joinCommandNames(commandCatalog.discovery())),
                List.of("Administration", joinCommandNames(commandCatalog.administration())),
                List.of("Query", joinCommandNames(commandCatalog.query())),
                List.of("Write", joinCommandNames(commandCatalog.write()))));
    String commandContracts =
        CliTextFormat.renderTable(
            List.of("Command", "Stdout", "Defaults", "Artifacts"),
            commandCatalog.allCommands().stream()
                .map(
                    command ->
                        List.of(
                            command.name().wireName(),
                            command.outputModeSummary(),
                            selectableDefaultsSummary(command.selectableOutputDefaults()),
                            artifactSummary(command)))
                .toList());
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
                List.of("Preflight semantics", capabilitiesDescriptor.preflight().semantics())));
    String targetedRetrieval =
        String.join(
            System.lineSeparator(),
            List.of(
                "Use '"
                    + CliInvocationText.commandExample(OperationId.HELP)
                    + " <command> --output json' when you only need one command contract.",
                "Use '"
                    + CliInvocationText.commandExample(OperationId.VERSION)
                    + " --output json' for a minimal application identity response.",
                "Use '"
                    + CliInvocationText.commandExample(OperationId.ENVIRONMENT)
                    + " --output json' for live runtime and SQLite provenance facts."));
    return CliTextFormat.renderTitledBlock(
        "FinGrind Capabilities",
        joinSections(
            header,
            section("Command Groups", commands),
            section("Command Contracts", commandContracts),
            section("Request Input", requestInput),
            section("Targeted Retrieval", targetedRetrieval)));
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
                            .toList()))));
    String storage =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of(
                    "Storage driver", environmentDescriptor.storage().storageDriver().wireValue()),
                List.of(
                    "Storage engine", environmentDescriptor.storage().storageEngine().wireValue()),
                List.of(
                    "Book protection",
                    environmentDescriptor.storage().bookProtectionMode().wireValue()),
                List.of(
                    "Protected-book format",
                    renderProtectedBookFormat(
                        environmentDescriptor.storage().defaultProtectedBookFormat()))));
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
                List.of("Issue", runtimeIssue(runtime))));
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

  private static String renderProtectedBookFormat(
      dev.erst.fingrind.contract.protocol.ProtectedBookFormatContract format) {
    return "cipher="
        + format.cipher().wireValue()
        + ", page-size="
        + format.pageSize()
        + ", reserved-bytes="
        + format.reservedBytes()
        + ", kdf-iter="
        + format.kdfIter()
        + ", plaintext-header-size="
        + format.plaintextHeaderSize()
        + ", legacy-mode="
        + format.legacyMode();
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
        "Request File",
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
        "Request File",
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
        "Request File",
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
    paragraphs.add(introduction);
    paragraphs.add("Generate a scaffold with: " + shortcutCommand);
    paragraphs.add(
        "Inspect the machine-readable contract with: "
            + CliInvocationText.commandExample(OperationId.HELP)
            + " "
            + ProtocolCatalog.operationName(operationId)
            + " --output json");
    if (!acceptedValuesRows.isEmpty()) {
      paragraphs.add(
          "Accepted values:"
              + System.lineSeparator()
              + CliTextFormat.renderKeyValueBlock(List.copyOf(acceptedValuesRows)));
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
