package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.discovery.WorkflowDescriptor;
import dev.erst.fingrind.contract.discovery.WorkflowStepDescriptor;
import dev.erst.fingrind.contract.protocol.ExecutionMode;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentRuntimeDescriptor;
import dev.erst.fingrind.contract.runtime.ExitCodeDescriptor;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Focused tests for discovery help text rendering and template guidance. */
class CliDiscoveryHelpTextRendererTest {
  private record BrokenTemplate(BrokenEnum status) {}

  /** Deliberately non-wire-safe enum used to exercise JSON template failure handling. */
  private enum BrokenEnum {
    BROKEN
  }

  private static String renderHelpText(HelpDescriptor helpDescriptor) {
    return CliDiscoveryOutputRenderer.renderHelpText(
        helpDescriptor, CliDiscoveryTestSupport.environment(), false);
  }

  private static String renderHelpText(
      HelpDescriptor helpDescriptor, EnvironmentDescriptor environmentDescriptor, boolean terse) {
    return CliDiscoveryOutputRenderer.renderHelpText(helpDescriptor, environmentDescriptor, terse);
  }

  @Test
  void renderJsonTemplate_supportsShortcutAndBareTemplateModes() {
    String bareTemplate =
        CliDiscoveryOutputRenderer.renderJsonTemplate(Map.of("hello", "world"), null);
    String shortcutTemplate =
        CliDiscoveryOutputRenderer.renderJsonTemplate(
            Map.of("hello", "world"), "fingrind print-request-template post-entry");

    assertFalse(bareTemplate.contains("Shortcut:"));
    assertTrue(bareTemplate.contains("\"hello\" : \"world\""));
    assertTrue(shortcutTemplate.contains("Shortcut: fingrind print-request-template post-entry"));
    assertTrue(shortcutTemplate.contains("\"hello\" : \"world\""));
  }

  @Test
  void renderJsonTemplate_wrapsWireValueSerializationFailures() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                CliDiscoveryOutputRenderer.renderJsonTemplate(
                    new BrokenTemplate(BrokenEnum.BROKEN), null));

    String message = java.util.Objects.requireNonNullElse(exception.getMessage(), "");
    assertTrue(message.contains("Failed to render CLI help request template JSON."));
  }

  @Test
  void renderHelpText_rendersRootHelpSections() {
    String rendered =
        renderHelpText(
            CliDiscoveryTestSupport.helpDescriptor(
                CliDiscoveryTestSupport.identity(),
                List.of("fingrind help"),
                new ContractResponse.BookModelDescriptor(
                    "single-sqlite-file",
                    "entity-book",
                    "local-path",
                    "key-file",
                    "explicit-open-book",
                    "declared-accounts",
                    "single-currency-entry"),
                List.of(
                    new CommandDescriptor(
                        OperationId.HELP,
                        List.of(),
                        List.of(),
                        ExecutionMode.JSON_ENVELOPE,
                        List.of(
                            dev.erst.fingrind.contract.protocol.OutputMode.JSON,
                            dev.erst.fingrind.contract.protocol.OutputMode.TEXT),
                        List.of(),
                        "Show help"),
                    new CommandDescriptor(
                        OperationId.EXECUTE_PLAN,
                        List.of(),
                        List.of(),
                        ExecutionMode.JSON_ENVELOPE,
                        List.of(),
                        List.of(),
                        "Execute one plan"),
                    new CommandDescriptor(
                        OperationId.PRINT_PLAN_TEMPLATE,
                        List.of(),
                        List.of(),
                        ExecutionMode.RAW_JSON,
                        List.of(),
                        List.of(),
                        "Print one plan template")),
                List.of(),
                List.of(new ExitCodeDescriptor(0, "ok")),
                new ContractResponse.PreflightDescriptor(
                    "advisory", ContractResponse.CommitGuarantee.NOT_GUARANTEED, "desc"),
                new ContractResponse.CurrencyDescriptor("per-entry", "single-entry", "desc")));

    assertTrue(rendered.contains("FinGrind Help"));
    assertTrue(rendered.contains("Quick Start"));
    assertTrue(rendered.contains("Generate one key file"));
    assertTrue(rendered.contains("Review the seeded starter chart"));
    assertTrue(rendered.contains("Create the first request"));
    assertTrue(rendered.contains("Command Catalog"));
    assertTrue(rendered.contains("Reference"));
  }

  @Test
  void renderHelpText_rendersTerseTopLevelSynopsisWithConfiguredOutputSource() {
    HelpDescriptor helpDescriptor =
        MachineContract.help(
            CliDiscoveryTestSupport.identity(), CliDiscoveryTestSupport.environment());
    EnvironmentDescriptor environmentDescriptor =
        new EnvironmentDescriptor(
            new EnvironmentRuntimeDescriptor(
                CliDiscoveryTestSupport.environment().runtime().runtimeDistribution(),
                dev.erst.fingrind.contract.protocol.OutputMode.JSON,
                "FINGRIND_DEFAULT_OUTPUT"),
            CliDiscoveryTestSupport.environment().publication(),
            CliDiscoveryTestSupport.environment().storage(),
            CliDiscoveryTestSupport.environment().sqlite());

    String rendered = renderHelpText(helpDescriptor, environmentDescriptor, true);

    assertTrue(rendered.contains("FinGrind"));
    assertTrue(rendered.contains("Shortcuts"));
    assertTrue(rendered.contains("Full guide"));
    assertTrue(rendered.contains("json via FINGRIND_DEFAULT_OUTPUT"));
    assertFalse(rendered.contains("Quick Start"));
    assertFalse(rendered.contains("Reference"));
  }

  @Test
  void renderHelpText_placesKeyGenerationBeforeBookOpeningInRootHelp() {
    String rendered =
        renderHelpText(
            MachineContract.help(
                CliDiscoveryTestSupport.identity(), CliDiscoveryTestSupport.environment()));

    int generateKeyIndex = rendered.indexOf("Generate one key file");
    int openBookIndex = rendered.indexOf("Open one protected book");
    int starterChartIndex = rendered.indexOf("Review the seeded starter chart");
    int entryScaffoldIndex = rendered.indexOf("Create the first request");
    int preflightIndex = rendered.indexOf("Validate the first request");
    int postEntryIndex = rendered.indexOf("Commit the first entry");
    int firstReportIndex = rendered.indexOf("Read the first report");

    assertTrue(generateKeyIndex >= 0, rendered);
    assertTrue(openBookIndex >= 0, rendered);
    assertTrue(starterChartIndex >= 0, rendered);
    assertTrue(entryScaffoldIndex >= 0, rendered);
    assertTrue(preflightIndex >= 0, rendered);
    assertTrue(postEntryIndex >= 0, rendered);
    assertTrue(firstReportIndex >= 0, rendered);
    assertTrue(generateKeyIndex < openBookIndex, rendered);
    assertTrue(openBookIndex < starterChartIndex, rendered);
    assertTrue(starterChartIndex < entryScaffoldIndex, rendered);
    assertTrue(entryScaffoldIndex < preflightIndex, rendered);
    assertTrue(preflightIndex < postEntryIndex, rendered);
    assertTrue(postEntryIndex < firstReportIndex, rendered);
  }

  @Test
  void renderHelpText_treatsSingleCommandWithQuickStartAsGeneralHelp() {
    String rendered =
        renderHelpText(
            CliDiscoveryTestSupport.helpDescriptor(
                CliDiscoveryTestSupport.identity(),
                List.of("fingrind help"),
                new ContractResponse.BookModelDescriptor(
                    "single-sqlite-file",
                    "entity-book",
                    "local-path",
                    "key-file",
                    "explicit-open-book",
                    "declared-accounts",
                    "single-currency-entry"),
                List.of(
                    new CommandDescriptor(
                        OperationId.HELP,
                        List.of(),
                        List.of(),
                        ExecutionMode.JSON_ENVELOPE,
                        List.of(
                            dev.erst.fingrind.contract.protocol.OutputMode.JSON,
                            dev.erst.fingrind.contract.protocol.OutputMode.TEXT),
                        List.of(),
                        "Show help")),
                List.of(
                    new dev.erst.fingrind.contract.discovery.WorkflowDescriptor(
                        dev.erst.fingrind.contract.discovery.WorkflowSurface.BUNDLE_POSIX_SHELL,
                        List.of(
                            dev.erst.fingrind.contract.discovery.WorkflowStepDescriptor.note(
                                "bundle bootstrap note body")))),
                List.of(new ExitCodeDescriptor(0, "ok")),
                new ContractResponse.PreflightDescriptor(
                    "advisory", ContractResponse.CommitGuarantee.NOT_GUARANTEED, "desc"),
                new ContractResponse.CurrencyDescriptor("per-entry", "single-entry", "desc")));

    assertTrue(rendered.contains("FinGrind Help"));
    assertTrue(rendered.contains("Quick Start"));
    assertTrue(rendered.contains("bundle bootstrap note body"));
  }

  @Test
  void renderHelpText_rendersCommandScopedHelpWithUsageAndExamples() {
    HelpDescriptor canonical =
        MachineContract.help(
            CliDiscoveryTestSupport.identity(),
            CliDiscoveryTestSupport.environment(),
            OperationId.POST_ENTRY);
    String rendered =
        renderHelpText(
            CliDiscoveryTestSupport.helpDescriptor(
                CliDiscoveryTestSupport.identity(),
                List.of("fingrind post-entry --book-file <path> --request-file <path|->"),
                new ContractResponse.BookModelDescriptor(
                    "single-sqlite-file",
                    "entity-book",
                    "local-path",
                    "key-file",
                    "explicit-open-book",
                    "declared-accounts",
                    "single-currency-entry"),
                List.of(
                    new CommandDescriptor(
                        OperationId.POST_ENTRY,
                        List.of(),
                        List.of("--book-file <path>", "--request-file <path|->"),
                        ExecutionMode.JSON_ENVELOPE,
                        List.of(
                            dev.erst.fingrind.contract.protocol.OutputMode.JSON,
                            dev.erst.fingrind.contract.protocol.OutputMode.TEXT),
                        List.of(),
                        "Commit one posting request")),
                List.of(),
                List.of(new ExitCodeDescriptor(0, "ok")),
                new ContractResponse.PreflightDescriptor(
                    "advisory", ContractResponse.CommitGuarantee.NOT_GUARANTEED, "desc"),
                new ContractResponse.CurrencyDescriptor("per-entry", "single-entry", "desc"),
                canonical.requestShapes()));

    assertTrue(rendered.contains("Examples"));
    assertTrue(rendered.contains("Preparation"));
    assertTrue(rendered.contains("Grammar"));
    assertTrue(rendered.contains("Options"));
    assertTrue(rendered.contains("Input Contract"));
    assertTrue(rendered.contains("print-request-template post-entry > request.json"));
    assertTrue(rendered.contains("Starter file command"));
    assertTrue(
        rendered.contains(
            CliInvocationText.rewriteInvocationPrefix(
                ProtocolCatalog.operation(OperationId.POST_ENTRY).usage())));
  }

  @Test
  void renderHelpText_rendersFallbackSectionsWhenUsageOptionsAndExamplesAreAbsent() {
    String rendered =
        renderHelpText(
            CliDiscoveryTestSupport.helpDescriptor(
                CliDiscoveryTestSupport.identity(),
                List.of(),
                new ContractResponse.BookModelDescriptor(
                    "single-sqlite-file",
                    "entity-book",
                    "local-path",
                    "key-file",
                    "explicit-open-book",
                    "declared-accounts",
                    "single-currency-entry"),
                List.of(
                    new CommandDescriptor(
                        OperationId.VERSION,
                        List.of("--version"),
                        List.of(),
                        ExecutionMode.JSON_ENVELOPE,
                        List.of(
                            dev.erst.fingrind.contract.protocol.OutputMode.JSON,
                            dev.erst.fingrind.contract.protocol.OutputMode.TEXT),
                        List.of(),
                        "Show version")),
                List.of(),
                List.of(new ExitCodeDescriptor(0, "ok")),
                new ContractResponse.PreflightDescriptor(
                    "advisory", ContractResponse.CommitGuarantee.NOT_GUARANTEED, "desc"),
                new ContractResponse.CurrencyDescriptor("per-entry", "single-entry", "desc")));

    assertTrue(rendered.contains("Examples"));
    assertTrue(rendered.contains("Grammar"));
    assertTrue(rendered.contains("(none)"));
    assertFalse(rendered.contains("Options"));
    assertFalse(rendered.contains("Input Contract"));
  }

  @Test
  void renderHelpText_omitsExitBehaviorWhenNoExitCodesArePublished() {
    String rendered =
        renderHelpText(
            CliDiscoveryTestSupport.helpDescriptor(
                CliDiscoveryTestSupport.identity(),
                List.of(),
                new ContractResponse.BookModelDescriptor(
                    "single-sqlite-file",
                    "entity-book",
                    "local-path",
                    "key-file",
                    "explicit-open-book",
                    "declared-accounts",
                    "single-currency-entry"),
                List.of(
                    new CommandDescriptor(
                        OperationId.VERSION,
                        List.of("--version"),
                        List.of(),
                        ExecutionMode.JSON_ENVELOPE,
                        List.of(
                            dev.erst.fingrind.contract.protocol.OutputMode.JSON,
                            dev.erst.fingrind.contract.protocol.OutputMode.TEXT),
                        List.of(),
                        "Show version")),
                List.of(),
                List.of(),
                new ContractResponse.PreflightDescriptor(
                    "advisory", ContractResponse.CommitGuarantee.NOT_GUARANTEED, "desc"),
                new ContractResponse.CurrencyDescriptor("per-entry", "single-entry", "desc")));

    assertFalse(rendered.contains("Exit Behavior"));
  }

  @Test
  void renderHelpText_rendersDeclareAccountAndExecutePlanRequestGuidance() {
    HelpDescriptor declareCanonical =
        MachineContract.help(
            CliDiscoveryTestSupport.identity(),
            CliDiscoveryTestSupport.environment(),
            OperationId.DECLARE_ACCOUNT);
    HelpDescriptor executePlanCanonical =
        MachineContract.help(
            CliDiscoveryTestSupport.identity(),
            CliDiscoveryTestSupport.environment(),
            OperationId.EXECUTE_PLAN);

    String declareRendered =
        renderHelpText(
            CliDiscoveryTestSupport.helpDescriptor(
                CliDiscoveryTestSupport.identity(),
                List.of("fingrind declare-account --request-file <path|->"),
                declareCanonical.bookModel(),
                List.of(
                    new CommandDescriptor(
                        OperationId.DECLARE_ACCOUNT,
                        List.of(),
                        List.of("--book-file <path>", "--request-file <path|->"),
                        ExecutionMode.JSON_ENVELOPE,
                        List.of(
                            dev.erst.fingrind.contract.protocol.OutputMode.JSON,
                            dev.erst.fingrind.contract.protocol.OutputMode.TEXT),
                        List.of(),
                        "Declare one account")),
                List.of(),
                List.of(new ExitCodeDescriptor(0, "ok")),
                declareCanonical.preflight(),
                declareCanonical.currencyModel(),
                CliDiscoveryTestSupport.withoutDeclareAccountEnumVocabulary(
                    Objects.requireNonNull(declareCanonical.requestShapes()))));
    String executePlanRendered =
        renderHelpText(
            CliDiscoveryTestSupport.helpDescriptor(
                CliDiscoveryTestSupport.identity(),
                List.of("fingrind execute-plan --request-file <path|->"),
                executePlanCanonical.bookModel(),
                List.of(
                    new CommandDescriptor(
                        OperationId.EXECUTE_PLAN,
                        List.of(),
                        List.of("--book-file <path>", "--request-file <path|->"),
                        ExecutionMode.JSON_ENVELOPE,
                        List.of(
                            dev.erst.fingrind.contract.protocol.OutputMode.JSON,
                            dev.erst.fingrind.contract.protocol.OutputMode.TEXT),
                        List.of(),
                        "Execute one ledger plan")),
                List.of(),
                List.of(new ExitCodeDescriptor(0, "ok")),
                executePlanCanonical.preflight(),
                executePlanCanonical.currencyModel(),
                Objects.requireNonNull(executePlanCanonical.requestShapes())));

    assertTrue(declareRendered.contains("Input Contract"));
    assertTrue(declareRendered.contains("Starter file command"));
    assertTrue(
        declareRendered.contains(
            CliInvocationText.rewriteInvocationPrefix(
                ProtocolCatalog.operation(OperationId.DECLARE_ACCOUNT).usage())));
    assertTrue(executePlanRendered.contains("Input Contract"));
    assertTrue(executePlanRendered.contains("Starter file command"));
    assertTrue(
        executePlanRendered.contains(
            CliInvocationText.rewriteInvocationPrefix(
                ProtocolCatalog.operation(OperationId.EXECUTE_PLAN).usage())));
  }

  @Test
  void renderHelpText_includesCsvContractForCsvCapableCommands() {
    String rendered =
        renderHelpText(
            MachineContract.help(
                CliDiscoveryTestSupport.identity(),
                CliDiscoveryTestSupport.environment(),
                OperationId.LIST_POSTINGS));

    assertTrue(rendered.contains("Output Contract"));
    assertTrue(rendered.contains("CSV contract"));
    assertTrue(rendered.contains("exportFamily"));
  }

  @Test
  void renderHelpText_usesBundleStarterRequestCopyCommandWhenBundleRuntimeIsSelected() {
    String previousDistribution = System.getProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY);
    System.setProperty(
        FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, FinGrindCli.BUNDLE_RUNTIME_DISTRIBUTION);
    try {
      String rendered =
          renderHelpText(
              MachineContract.help(
                  CliDiscoveryTestSupport.identity(),
                  CliDiscoveryTestSupport.environment(),
                  OperationId.POST_ENTRY));

      assertTrue(rendered.contains("cp ./quick-start-request.json ./request.json"));
    } finally {
      restoreRuntimeDistribution(previousDistribution);
    }
  }

  @Test
  void renderHelpText_rewritesCanonicalSyntaxForDirectJavaRuntime() {
    String previousDistribution = System.getProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY);
    System.setProperty(
        FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, FinGrindCli.DIRECT_JAVA_RUNTIME_DISTRIBUTION);
    try {
      String rendered =
          renderHelpText(
              MachineContract.help(
                  CliDiscoveryTestSupport.identity(),
                  CliDiscoveryTestSupport.environment(),
                  OperationId.OPEN_BOOK));

      assertTrue(rendered.contains("./scripts/direct-java-cli.sh open-book --book-file <path>"));
      assertFalse(rendered.contains("fingrind open-book --book-file <path>"));
    } finally {
      restoreRuntimeDistribution(previousDistribution);
    }
  }

  @Test
  void renderHelpText_omitsRequestGuidanceWhenScopedMetadataIsMissing() {
    HelpDescriptor postEntryCanonical =
        MachineContract.help(
            CliDiscoveryTestSupport.identity(),
            CliDiscoveryTestSupport.environment(),
            OperationId.POST_ENTRY);
    HelpDescriptor declareCanonical =
        MachineContract.help(
            CliDiscoveryTestSupport.identity(),
            CliDiscoveryTestSupport.environment(),
            OperationId.DECLARE_ACCOUNT);
    HelpDescriptor executePlanCanonical =
        MachineContract.help(
            CliDiscoveryTestSupport.identity(),
            CliDiscoveryTestSupport.environment(),
            OperationId.EXECUTE_PLAN);

    String postEntryWithoutRequestShapes =
        renderHelpText(
            new HelpDescriptor(
                postEntryCanonical.application(),
                postEntryCanonical.version(),
                postEntryCanonical.description(),
                postEntryCanonical.usage(),
                postEntryCanonical.bookModel(),
                postEntryCanonical.bookkeepingKernel(),
                null,
                postEntryCanonical.requestTemplate(),
                postEntryCanonical.declareAccountTemplate(),
                postEntryCanonical.planTemplate(),
                postEntryCanonical.commands(),
                postEntryCanonical.quickStart(),
                postEntryCanonical.exitCodes(),
                postEntryCanonical.preflight(),
                postEntryCanonical.currencyModel()));
    String postEntryWithoutShape =
        renderHelpText(
            new HelpDescriptor(
                postEntryCanonical.application(),
                postEntryCanonical.version(),
                postEntryCanonical.description(),
                postEntryCanonical.usage(),
                postEntryCanonical.bookModel(),
                postEntryCanonical.bookkeepingKernel(),
                new ContractRequestShapes.RequestShapesDescriptor(
                    Objects.requireNonNull(postEntryCanonical.requestShapes()).schemaDialect(),
                    null,
                    postEntryCanonical.requestShapes().declareAccount(),
                    postEntryCanonical.requestShapes().ledgerPlan()),
                postEntryCanonical.requestTemplate(),
                postEntryCanonical.declareAccountTemplate(),
                postEntryCanonical.planTemplate(),
                postEntryCanonical.commands(),
                postEntryCanonical.quickStart(),
                postEntryCanonical.exitCodes(),
                postEntryCanonical.preflight(),
                postEntryCanonical.currencyModel()));
    String postEntryWithoutTemplate =
        renderHelpText(
            new HelpDescriptor(
                postEntryCanonical.application(),
                postEntryCanonical.version(),
                postEntryCanonical.description(),
                postEntryCanonical.usage(),
                postEntryCanonical.bookModel(),
                postEntryCanonical.bookkeepingKernel(),
                postEntryCanonical.requestShapes(),
                null,
                postEntryCanonical.declareAccountTemplate(),
                postEntryCanonical.planTemplate(),
                postEntryCanonical.commands(),
                postEntryCanonical.quickStart(),
                postEntryCanonical.exitCodes(),
                postEntryCanonical.preflight(),
                postEntryCanonical.currencyModel()));
    String declareWithoutRequestShapes =
        renderHelpText(
            new HelpDescriptor(
                declareCanonical.application(),
                declareCanonical.version(),
                declareCanonical.description(),
                declareCanonical.usage(),
                declareCanonical.bookModel(),
                declareCanonical.bookkeepingKernel(),
                null,
                declareCanonical.requestTemplate(),
                declareCanonical.declareAccountTemplate(),
                declareCanonical.planTemplate(),
                declareCanonical.commands(),
                declareCanonical.quickStart(),
                declareCanonical.exitCodes(),
                declareCanonical.preflight(),
                declareCanonical.currencyModel()));
    String declareWithoutShape =
        renderHelpText(
            new HelpDescriptor(
                declareCanonical.application(),
                declareCanonical.version(),
                declareCanonical.description(),
                declareCanonical.usage(),
                declareCanonical.bookModel(),
                declareCanonical.bookkeepingKernel(),
                new ContractRequestShapes.RequestShapesDescriptor(
                    Objects.requireNonNull(declareCanonical.requestShapes()).schemaDialect(),
                    declareCanonical.requestShapes().postEntry(),
                    null,
                    declareCanonical.requestShapes().ledgerPlan()),
                declareCanonical.requestTemplate(),
                declareCanonical.declareAccountTemplate(),
                declareCanonical.planTemplate(),
                declareCanonical.commands(),
                declareCanonical.quickStart(),
                declareCanonical.exitCodes(),
                declareCanonical.preflight(),
                declareCanonical.currencyModel()));
    String declareWithoutTemplate =
        renderHelpText(
            new HelpDescriptor(
                declareCanonical.application(),
                declareCanonical.version(),
                declareCanonical.description(),
                declareCanonical.usage(),
                declareCanonical.bookModel(),
                declareCanonical.bookkeepingKernel(),
                declareCanonical.requestShapes(),
                declareCanonical.requestTemplate(),
                null,
                declareCanonical.planTemplate(),
                declareCanonical.commands(),
                declareCanonical.quickStart(),
                declareCanonical.exitCodes(),
                declareCanonical.preflight(),
                declareCanonical.currencyModel()));
    String executePlanWithoutRequestShapes =
        renderHelpText(
            new HelpDescriptor(
                executePlanCanonical.application(),
                executePlanCanonical.version(),
                executePlanCanonical.description(),
                executePlanCanonical.usage(),
                executePlanCanonical.bookModel(),
                executePlanCanonical.bookkeepingKernel(),
                null,
                executePlanCanonical.requestTemplate(),
                executePlanCanonical.declareAccountTemplate(),
                executePlanCanonical.planTemplate(),
                executePlanCanonical.commands(),
                executePlanCanonical.quickStart(),
                executePlanCanonical.exitCodes(),
                executePlanCanonical.preflight(),
                executePlanCanonical.currencyModel()));
    String executePlanWithoutShape =
        renderHelpText(
            new HelpDescriptor(
                executePlanCanonical.application(),
                executePlanCanonical.version(),
                executePlanCanonical.description(),
                executePlanCanonical.usage(),
                executePlanCanonical.bookModel(),
                executePlanCanonical.bookkeepingKernel(),
                new ContractRequestShapes.RequestShapesDescriptor(
                    Objects.requireNonNull(executePlanCanonical.requestShapes()).schemaDialect(),
                    executePlanCanonical.requestShapes().postEntry(),
                    executePlanCanonical.requestShapes().declareAccount(),
                    null),
                executePlanCanonical.requestTemplate(),
                executePlanCanonical.declareAccountTemplate(),
                executePlanCanonical.planTemplate(),
                executePlanCanonical.commands(),
                executePlanCanonical.quickStart(),
                executePlanCanonical.exitCodes(),
                executePlanCanonical.preflight(),
                executePlanCanonical.currencyModel()));
    String executePlanWithoutTemplate =
        renderHelpText(
            new HelpDescriptor(
                executePlanCanonical.application(),
                executePlanCanonical.version(),
                executePlanCanonical.description(),
                executePlanCanonical.usage(),
                executePlanCanonical.bookModel(),
                executePlanCanonical.bookkeepingKernel(),
                executePlanCanonical.requestShapes(),
                executePlanCanonical.requestTemplate(),
                executePlanCanonical.declareAccountTemplate(),
                null,
                executePlanCanonical.commands(),
                executePlanCanonical.quickStart(),
                executePlanCanonical.exitCodes(),
                executePlanCanonical.preflight(),
                executePlanCanonical.currencyModel()));

    assertFalse(postEntryWithoutRequestShapes.contains("\nInput\n"));
    assertFalse(postEntryWithoutShape.contains("\nInput\n"));
    assertFalse(postEntryWithoutTemplate.contains("\nInput\n"));
    assertFalse(declareWithoutRequestShapes.contains("\nInput\n"));
    assertFalse(declareWithoutShape.contains("\nInput\n"));
    assertFalse(declareWithoutTemplate.contains("\nInput\n"));
    assertFalse(executePlanWithoutRequestShapes.contains("\nInput\n"));
    assertFalse(executePlanWithoutShape.contains("\nInput\n"));
    assertFalse(executePlanWithoutTemplate.contains("\nInput\n"));
  }

  @Test
  void renderHelpText_labelsUnrecognizedWorkflowCommandsAsNumberedSteps() {
    String rendered =
        renderHelpText(
            CliDiscoveryTestSupport.helpDescriptor(
                CliDiscoveryTestSupport.identity(),
                List.of("fingrind help"),
                new ContractResponse.BookModelDescriptor(
                    "single-sqlite-file",
                    "entity-book",
                    "local-path",
                    "key-file",
                    "explicit-open-book",
                    "declared-accounts",
                    "single-currency-entry"),
                List.of(
                    new CommandDescriptor(
                        OperationId.HELP,
                        List.of(),
                        List.of(),
                        ExecutionMode.JSON_ENVELOPE,
                        List.of(
                            dev.erst.fingrind.contract.protocol.OutputMode.JSON,
                            dev.erst.fingrind.contract.protocol.OutputMode.TEXT),
                        List.of(),
                        "Show help")),
                List.of(
                    new WorkflowDescriptor(
                        dev.erst.fingrind.contract.discovery.WorkflowSurface.BUNDLE_POSIX_SHELL,
                        List.of(WorkflowStepDescriptor.command("fingrind custom-seam")))),
                List.of(new ExitCodeDescriptor(0, "ok")),
                new ContractResponse.PreflightDescriptor(
                    "advisory", ContractResponse.CommitGuarantee.NOT_GUARANTEED, "desc"),
                new ContractResponse.CurrencyDescriptor("per-entry", "single-entry", "desc")));

    assertTrue(rendered.contains("Step 1"));
    assertTrue(rendered.contains("fingrind custom-seam"));
  }

  private static void restoreRuntimeDistribution(String previousDistribution) {
    if (previousDistribution == null) {
      System.clearProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY);
      return;
    }
    System.setProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, previousDistribution);
  }
}
