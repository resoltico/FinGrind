package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.discovery.ApplicationIdentity;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.protocol.ExecutionMode;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.PublicCliBundleTarget;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeStatus;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeTrustBasis;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentDistributionDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentStorageDescriptor;
import dev.erst.fingrind.contract.runtime.ExitCodeDescriptor;
import dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliDiscoveryOutputRenderer}. */
class CliDiscoveryOutputRendererTest {
  @Test
  void renderHelpHuman_rendersFixedAndSelectableStdoutContractsAndEmptyQuickStart() {
    String rendered =
        CliDiscoveryOutputRenderer.renderHelpHuman(
            helpDescriptor(
                identity(),
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
                            dev.erst.fingrind.contract.protocol.OutputMode.HUMAN),
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
                List.of(
                    new dev.erst.fingrind.contract.discovery.WorkflowDescriptor(
                        dev.erst.fingrind.contract.discovery.WorkflowSurface.BUNDLE_POSIX_SHELL,
                        List.of(
                            dev.erst.fingrind.contract.discovery.WorkflowStepDescriptor.note(
                                "demo")))),
                List.of(new ExitCodeDescriptor(0, "ok")),
                new ContractResponse.PreflightDescriptor(
                    "advisory", ContractResponse.CommitGuarantee.NOT_GUARANTEED, "desc"),
                new ContractResponse.CurrencyDescriptor("per-entry", "single-entry", "desc")));

    assertTrue(rendered.contains("FinGrind Help"));
    assertTrue(rendered.contains("help"));
    assertTrue(rendered.contains("json, human (via --output)"));
    assertTrue(rendered.contains("json envelope (fixed)"));
    assertTrue(rendered.contains("raw json (fixed)"));
    assertTrue(rendered.contains("Quick Start"));
    assertTrue(rendered.contains("Self-Contained Bundle (POSIX Shell)"));
    assertTrue(rendered.contains("demo"));
  }

  @Test
  void renderHelpHuman_rendersExpandedRuntimeSpecificQuickStartTitles() {
    String rendered =
        CliDiscoveryOutputRenderer.renderHelpHuman(
            helpDescriptor(
                identity(),
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
                            dev.erst.fingrind.contract.protocol.OutputMode.HUMAN),
                        List.of(),
                        "Show help")),
                List.of(
                    new dev.erst.fingrind.contract.discovery.WorkflowDescriptor(
                        dev.erst.fingrind.contract.discovery.WorkflowSurface
                            .SOURCE_CHECKOUT_POSIX_SHELL,
                        List.of(
                            dev.erst.fingrind.contract.discovery.WorkflowStepDescriptor.note(
                                "posix"))),
                    new dev.erst.fingrind.contract.discovery.WorkflowDescriptor(
                        dev.erst.fingrind.contract.discovery.WorkflowSurface
                            .DIRECT_JAVA_WINDOWS_POWERSHELL,
                        List.of(
                            dev.erst.fingrind.contract.discovery.WorkflowStepDescriptor.note(
                                "java"))),
                    new dev.erst.fingrind.contract.discovery.WorkflowDescriptor(
                        dev.erst.fingrind.contract.discovery.WorkflowSurface.CONTAINER_DOCKER,
                        List.of(
                            dev.erst.fingrind.contract.discovery.WorkflowStepDescriptor.note(
                                "docker")))),
                List.of(new ExitCodeDescriptor(0, "ok")),
                new ContractResponse.PreflightDescriptor(
                    "advisory", ContractResponse.CommitGuarantee.NOT_GUARANTEED, "desc"),
                new ContractResponse.CurrencyDescriptor("per-entry", "single-entry", "desc")));

    assertTrue(rendered.contains("Source Checkout Launcher (POSIX Shell)"));
    assertTrue(rendered.contains("Developer Raw JAR (Windows PowerShell)"));
    assertTrue(rendered.contains("Container Image (Docker CLI)"));
  }

  @Test
  void renderHelpHuman_rendersNoneForAbsentQuickStartWorkflows() {
    String rendered =
        CliDiscoveryOutputRenderer.renderHelpHuman(
            helpDescriptor(
                identity(),
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
                            dev.erst.fingrind.contract.protocol.OutputMode.HUMAN),
                        List.of(),
                        "Show help"),
                    new CommandDescriptor(
                        OperationId.VERSION,
                        List.of("--version"),
                        List.of(),
                        ExecutionMode.JSON_ENVELOPE,
                        List.of(
                            dev.erst.fingrind.contract.protocol.OutputMode.JSON,
                            dev.erst.fingrind.contract.protocol.OutputMode.HUMAN),
                        List.of(),
                        "Show version")),
                List.of(),
                List.of(new ExitCodeDescriptor(0, "ok")),
                new ContractResponse.PreflightDescriptor(
                    "advisory", ContractResponse.CommitGuarantee.NOT_GUARANTEED, "desc"),
                new ContractResponse.CurrencyDescriptor("per-entry", "single-entry", "desc")));

    assertTrue(rendered.contains("Quick Start"));
    assertTrue(rendered.contains("(none)"));
  }

  @Test
  void renderHelpHuman_rendersCommandScopedHelpWithUsageAndExamples() {
    String rendered =
        CliDiscoveryOutputRenderer.renderHelpHuman(
            helpDescriptor(
                identity(),
                List.of("fingrind post-entry --book-file <path>"),
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
                            dev.erst.fingrind.contract.protocol.OutputMode.HUMAN),
                        List.of(),
                        "Commit one posting request")),
                List.of(),
                List.of(new ExitCodeDescriptor(0, "ok")),
                new ContractResponse.PreflightDescriptor(
                    "advisory", ContractResponse.CommitGuarantee.NOT_GUARANTEED, "desc"),
                new ContractResponse.CurrencyDescriptor("per-entry", "single-entry", "desc")));

    assertTrue(rendered.contains("Command"));
    assertTrue(rendered.contains("Usage"));
    assertTrue(rendered.contains("Examples"));
    assertTrue(rendered.contains("Operator Notes"));
    assertTrue(rendered.contains("post-entry"));
    assertTrue(rendered.contains("Commit one posting request"));
    assertTrue(
        rendered.contains(
            CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                + " > request.json"));
    assertTrue(rendered.contains("Replace scaffold placeholders such as effectiveDate"));
  }

  @Test
  void renderHelpHuman_rendersScopedHelpFallbackSectionsWhenMetadataIsAbsent() {
    String rendered =
        CliDiscoveryOutputRenderer.renderHelpHuman(
            helpDescriptor(
                identity(),
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
                            dev.erst.fingrind.contract.protocol.OutputMode.HUMAN),
                        List.of(),
                        "Show version")),
                List.of(),
                List.of(new ExitCodeDescriptor(0, "ok")),
                new ContractResponse.PreflightDescriptor(
                    "advisory", ContractResponse.CommitGuarantee.NOT_GUARANTEED, "desc"),
                new ContractResponse.CurrencyDescriptor("per-entry", "single-entry", "desc")));

    assertTrue(rendered.contains("Aliases"));
    assertTrue(rendered.contains("--version"));
    assertTrue(rendered.contains("Usage"));
    assertTrue(rendered.contains("Options"));
    assertTrue(rendered.contains("Examples"));
    assertTrue(rendered.contains("(none)"));
  }

  @Test
  void renderHelpHuman_rendersDeclareAccountRequestGuidanceWithoutShortcutOrEnumVocabulary() {
    HelpDescriptor canonical = MachineContract.help(identity(), environment());
    ContractRequestShapes.RequestShapesDescriptor requestShapes =
        withoutDeclareAccountEnumVocabulary(canonical.requestShapes());
    String rendered =
        CliDiscoveryOutputRenderer.renderHelpHuman(
            helpDescriptor(
                identity(),
                List.of("fingrind declare-account --request-file <path|->"),
                canonical.bookModel(),
                List.of(
                    new CommandDescriptor(
                        OperationId.DECLARE_ACCOUNT,
                        List.of(),
                        List.of("--book-file <path>", "--request-file <path|->"),
                        ExecutionMode.JSON_ENVELOPE,
                        List.of(
                            dev.erst.fingrind.contract.protocol.OutputMode.JSON,
                            dev.erst.fingrind.contract.protocol.OutputMode.HUMAN),
                        List.of(),
                        "Declare one account")),
                List.of(),
                List.of(new ExitCodeDescriptor(0, "ok")),
                canonical.preflight(),
                canonical.currencyModel(),
                requestShapes));

    assertTrue(rendered.contains("Request File"));
    assertTrue(rendered.contains("Provide one JSON object through --request-file <path|->."));
    assertTrue(rendered.contains("\"accountType\""));
    assertTrue(rendered.contains("Top-Level Fields"));
    assertTrue(rendered.contains("accountRole"));
    assertTrue(rendered.contains("required"));
    assertTrue(rendered.contains("accountType"));
    assertTrue(rendered.contains("Template"));
    assertTrue(rendered.contains("{"));
    assertTrue(rendered.contains("}"));
    assertFalse(rendered.contains("Shortcut: fingrind"));
    assertFalse(rendered.contains("Enum Vocabulary"));
  }

  @Test
  void renderHelpHuman_rendersExecutePlanRequestGuidanceAndVocabulary() {
    HelpDescriptor canonical = MachineContract.help(identity(), environment());
    String rendered =
        CliDiscoveryOutputRenderer.renderHelpHuman(
            helpDescriptor(
                identity(),
                List.of("fingrind execute-plan --request-file <path|->"),
                canonical.bookModel(),
                List.of(
                    new CommandDescriptor(
                        OperationId.EXECUTE_PLAN,
                        List.of(),
                        List.of("--book-file <path>", "--request-file <path|->"),
                        ExecutionMode.JSON_ENVELOPE,
                        List.of(
                            dev.erst.fingrind.contract.protocol.OutputMode.JSON,
                            dev.erst.fingrind.contract.protocol.OutputMode.HUMAN),
                        List.of(),
                        "Execute one ledger plan")),
                List.of(),
                List.of(new ExitCodeDescriptor(0, "ok")),
                canonical.preflight(),
                canonical.currencyModel(),
                canonical.requestShapes()));

    assertTrue(
        rendered.contains("Provide one ledger plan JSON object through --request-file <path|->."));
    assertTrue(rendered.contains("Shortcut: fingrind print-plan-template"));
    assertTrue(rendered.contains("Step Fields"));
    assertTrue(rendered.contains("Query Fields"));
    assertTrue(rendered.contains("Assertion Fields"));
    assertTrue(rendered.contains("administrationStepKinds"));
    assertTrue(rendered.contains("queryStepKinds"));
    assertTrue(rendered.contains("writeStepKinds"));
    assertTrue(rendered.contains("assertStepKind"));
    assertTrue(rendered.contains("assertionKinds"));
    assertTrue(rendered.contains("open-book"));
    assertTrue(rendered.contains("declare-account"));
    assertTrue(rendered.contains("assert-account-balance"));
  }

  @Test
  void renderHelpHuman_treatsSingleCommandWithQuickStartAsGeneralHelp() {
    String rendered =
        CliDiscoveryOutputRenderer.renderHelpHuman(
            helpDescriptor(
                identity(),
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
                        List.of("--help", "-h"),
                        List.of(),
                        ExecutionMode.JSON_ENVELOPE,
                        List.of(
                            dev.erst.fingrind.contract.protocol.OutputMode.JSON,
                            dev.erst.fingrind.contract.protocol.OutputMode.HUMAN),
                        List.of(),
                        "Show help")),
                List.of(
                    new dev.erst.fingrind.contract.discovery.WorkflowDescriptor(
                        dev.erst.fingrind.contract.discovery.WorkflowSurface.BUNDLE_POSIX_SHELL,
                        List.of(
                            dev.erst.fingrind.contract.discovery.WorkflowStepDescriptor.note(
                                "demo")))),
                List.of(new ExitCodeDescriptor(0, "ok")),
                new ContractResponse.PreflightDescriptor(
                    "advisory", ContractResponse.CommitGuarantee.NOT_GUARANTEED, "desc"),
                new ContractResponse.CurrencyDescriptor("per-entry", "single-entry", "desc")));

    assertTrue(rendered.contains("Commands"));
    assertTrue(rendered.contains("Quick Start"));
    assertTrue(rendered.contains("help"));
  }

  @Test
  void renderJsonTemplate_wrapsSerializationFailuresWithCliHelpContext() throws Exception {
    IllegalStateException failure =
        assertInstanceOf(
            IllegalStateException.class,
            org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () ->
                    CliDiscoveryOutputRenderer.renderJsonTemplate(
                        new CliResponseWriterTestSupport.SelfReferentialValue(),
                        OperationId.HELP)));
    assertTrue(
        Objects.requireNonNull(failure.getMessage())
            .contains("Failed to render CLI help request template JSON."));
    assertNotNull(failure.getCause());
  }

  @Test
  void renderCapabilitiesHuman_rendersCommandGroupsContractsAndRequestInput() {
    String rendered =
        CliDiscoveryOutputRenderer.renderCapabilitiesHuman(
            MachineContract.capabilities(
                identity(), environment(), Instant.parse("2026-04-19T08:00:00Z")));

    assertTrue(rendered.contains("FinGrind Capabilities"));
    assertTrue(rendered.contains("Command Groups"));
    assertTrue(rendered.contains("Command Contracts"));
    assertTrue(rendered.contains("Request Input"));
    assertTrue(rendered.contains("Discovery"));
    assertTrue(rendered.contains("trial-balance"));
    assertTrue(rendered.contains("json, human, csv (via --output)"));
    assertTrue(rendered.contains("Selectable stdout flag"));
  }

  @Test
  void renderVersionHuman_rendersTitleAndKeyValues() {
    String rendered =
        CliDiscoveryOutputRenderer.renderVersionHuman(MachineContract.version(identity()));

    assertTrue(rendered.contains("FinGrind"));
    assertTrue(rendered.contains("Version"));
    assertTrue(rendered.contains("0.35.0"));
  }

  private static HelpDescriptor helpDescriptor(
      ApplicationIdentity applicationIdentity,
      List<String> usage,
      ContractResponse.BookModelDescriptor bookModel,
      List<CommandDescriptor> commands,
      List<dev.erst.fingrind.contract.discovery.WorkflowDescriptor> quickStart,
      List<ExitCodeDescriptor> exitCodes,
      ContractResponse.PreflightDescriptor preflight,
      ContractResponse.CurrencyDescriptor currencyModel) {
    return helpDescriptor(
        applicationIdentity,
        usage,
        bookModel,
        commands,
        quickStart,
        exitCodes,
        preflight,
        currencyModel,
        MachineContract.help(applicationIdentity, environment()).requestShapes());
  }

  private static HelpDescriptor helpDescriptor(
      ApplicationIdentity applicationIdentity,
      List<String> usage,
      ContractResponse.BookModelDescriptor bookModel,
      List<CommandDescriptor> commands,
      List<dev.erst.fingrind.contract.discovery.WorkflowDescriptor> quickStart,
      List<ExitCodeDescriptor> exitCodes,
      ContractResponse.PreflightDescriptor preflight,
      ContractResponse.CurrencyDescriptor currencyModel,
      ContractRequestShapes.RequestShapesDescriptor requestShapes) {
    HelpDescriptor canonical = MachineContract.help(applicationIdentity, environment());
    return new HelpDescriptor(
        applicationIdentity.application(),
        applicationIdentity.version(),
        applicationIdentity.description(),
        usage,
        bookModel,
        requestShapes,
        canonical.requestTemplate(),
        canonical.declareAccountTemplate(),
        canonical.planTemplate(),
        commands,
        quickStart,
        exitCodes,
        preflight,
        currencyModel,
        environment());
  }

  private static ContractRequestShapes.RequestShapesDescriptor withoutDeclareAccountEnumVocabulary(
      ContractRequestShapes.RequestShapesDescriptor requestShapes) {
    return new ContractRequestShapes.RequestShapesDescriptor(
        requestShapes.schemaDialect(),
        requestShapes.postEntry(),
        new ContractRequestShapes.DeclareAccountRequestShapeDescriptor(
            requestShapes.declareAccount().topLevelFields(),
            List.of(),
            requestShapes.declareAccount().schema()),
        requestShapes.ledgerPlan());
  }

  private static ApplicationIdentity identity() {
    return new ApplicationIdentity(
        "FinGrind",
        "0.35.0",
        "Command-line double-entry bookkeeping with one protected book per accounting entity");
  }

  private static EnvironmentDescriptor environment() {
    return new EnvironmentDescriptor(
        new EnvironmentDistributionDescriptor(
            ProtocolCatalog.bundleRuntimeDistribution(),
            ProtocolCatalog.publicCliDistribution(),
            List.of(PublicCliBundleTarget.MACOS_AARCH64, PublicCliBundleTarget.WINDOWS_X86_64),
            List.of(),
            ProtocolCatalog.sourceCheckoutJava()),
        new EnvironmentStorageDescriptor(
            ProtocolCatalog.storageDriver(),
            ProtocolCatalog.storageEngine(),
            ProtocolCatalog.bookProtectionMode(),
            ProtocolCatalog.protectedBookFormat()),
        new EnvironmentSqliteDescriptor(
            ProtocolCatalog.sqliteLibraryMode(),
            ProtocolCatalog.sqliteLibraryEnvironmentVariable(),
            ProtocolCatalog.sqliteBundleHomeSystemProperty(),
            ProtocolCatalog.requiredSqliteCompileOptions(),
            ProtocolCatalog.forbiddenSqliteCompileOptions(),
            ProtocolCatalog.requiresSecureMemorySupport(),
            ProtocolCatalog.requiredMinimumSqliteVersion(),
            ProtocolCatalog.requiredSqlite3mcVersion(),
            ProtocolCatalog.requiredSqliteSourceId(),
            EnvironmentSqliteDescriptor.runtime(
                SqliteCompileOptionsVerificationStatus.VERIFIED,
                SqliteRuntimeStatus.READY,
                SqliteRuntimeProvenance.BUNDLE_MANAGED,
                SqliteRuntimeTrustBasis.PUBLISHER_AUTHENTICATED,
                "/tmp/libsqlite3.dylib",
                ProtocolCatalog.requiredMinimumSqliteVersion(),
                ProtocolCatalog.requiredSqlite3mcVersion(),
                ProtocolCatalog.requiredSqliteSourceId(),
                null)));
  }
}
