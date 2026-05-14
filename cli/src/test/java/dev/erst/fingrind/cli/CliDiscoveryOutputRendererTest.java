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
import dev.erst.fingrind.contract.discovery.WorkflowDescriptor;
import dev.erst.fingrind.contract.discovery.WorkflowStepDescriptor;
import dev.erst.fingrind.contract.discovery.WorkflowSurface;
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
    assertTrue(rendered.contains("Getting Started"));
    assertFalse(rendered.contains("Quick Start"));
    assertFalse(rendered.contains("Self-Contained Bundle (POSIX Shell)"));
    assertFalse(rendered.contains("demo"));
  }

  @Test
  void renderHelpHuman_rootHelpOmitsRuntimeSpecificQuickStartTitles() {
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

    assertFalse(rendered.contains("Source Checkout Launcher (POSIX Shell)"));
    assertFalse(rendered.contains("Developer Raw JAR (Windows PowerShell)"));
    assertFalse(rendered.contains("Container Image (Docker CLI)"));
    assertTrue(rendered.contains("Getting Started"));
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

    assertFalse(rendered.contains("Quick Start"));
    assertTrue(rendered.contains("Getting Started"));
  }

  @Test
  void renderHelpHuman_omitsGuidanceBlockWhenQuickStartHasOnlyExecutableSteps() {
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
                    new WorkflowDescriptor(
                        WorkflowSurface.BUNDLE_POSIX_SHELL,
                        List.of(
                            WorkflowStepDescriptor.command("./bin/fingrind version"),
                            WorkflowStepDescriptor.edit("./request.json", "{\"ok\":true}")))),
                List.of(new ExitCodeDescriptor(0, "ok")),
                new ContractResponse.PreflightDescriptor(
                    "advisory", ContractResponse.CommitGuarantee.NOT_GUARANTEED, "desc"),
                new ContractResponse.CurrencyDescriptor("per-entry", "single-entry", "desc")));

    assertFalse(rendered.contains("Guidance"));
    assertFalse(rendered.contains("Steps"));
    assertFalse(rendered.contains("Create ./request.json"));
    assertTrue(rendered.contains("Getting Started"));
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
    HelpDescriptor canonical =
        MachineContract.help(identity(), environment(), OperationId.DECLARE_ACCOUNT);
    ContractRequestShapes.RequestShapesDescriptor requestShapes =
        withoutDeclareAccountEnumVocabulary(Objects.requireNonNull(canonical.requestShapes()));
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
    HelpDescriptor canonical =
        MachineContract.help(identity(), environment(), OperationId.EXECUTE_PLAN);
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
                Objects.requireNonNull(canonical.requestShapes())));

    assertTrue(
        rendered.contains("Provide one ledger plan JSON object through --request-file <path|->."));
    assertTrue(
        rendered.contains(
            "Shortcut: " + CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE)));
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
  void renderHelpHuman_omitsRequestGuidanceWhenScopedMetadataIsMissing() {
    HelpDescriptor postEntryCanonical =
        MachineContract.help(identity(), environment(), OperationId.POST_ENTRY);
    HelpDescriptor declareCanonical =
        MachineContract.help(identity(), environment(), OperationId.DECLARE_ACCOUNT);
    HelpDescriptor executePlanCanonical =
        MachineContract.help(identity(), environment(), OperationId.EXECUTE_PLAN);

    String postEntryRendered =
        CliDiscoveryOutputRenderer.renderHelpHuman(
            new HelpDescriptor(
                postEntryCanonical.application(),
                postEntryCanonical.version(),
                postEntryCanonical.description(),
                postEntryCanonical.usage(),
                postEntryCanonical.bookModel(),
                postEntryCanonical.accountingBaseline(),
                postEntryCanonical.requestShapes(),
                null,
                postEntryCanonical.declareAccountTemplate(),
                postEntryCanonical.planTemplate(),
                postEntryCanonical.commands(),
                postEntryCanonical.quickStart(),
                postEntryCanonical.exitCodes(),
                postEntryCanonical.preflight(),
                postEntryCanonical.currencyModel(),
                postEntryCanonical.extensionSurface(),
                postEntryCanonical.environment()));
    String declareRendered =
        CliDiscoveryOutputRenderer.renderHelpHuman(
            new HelpDescriptor(
                declareCanonical.application(),
                declareCanonical.version(),
                declareCanonical.description(),
                declareCanonical.usage(),
                declareCanonical.bookModel(),
                declareCanonical.accountingBaseline(),
                declareCanonical.requestShapes(),
                declareCanonical.requestTemplate(),
                null,
                declareCanonical.planTemplate(),
                declareCanonical.commands(),
                declareCanonical.quickStart(),
                declareCanonical.exitCodes(),
                declareCanonical.preflight(),
                declareCanonical.currencyModel(),
                declareCanonical.extensionSurface(),
                declareCanonical.environment()));
    String executePlanRendered =
        CliDiscoveryOutputRenderer.renderHelpHuman(
            new HelpDescriptor(
                executePlanCanonical.application(),
                executePlanCanonical.version(),
                executePlanCanonical.description(),
                executePlanCanonical.usage(),
                executePlanCanonical.bookModel(),
                executePlanCanonical.accountingBaseline(),
                executePlanCanonical.requestShapes(),
                executePlanCanonical.requestTemplate(),
                executePlanCanonical.declareAccountTemplate(),
                null,
                executePlanCanonical.commands(),
                executePlanCanonical.quickStart(),
                executePlanCanonical.exitCodes(),
                executePlanCanonical.preflight(),
                executePlanCanonical.currencyModel(),
                executePlanCanonical.extensionSurface(),
                executePlanCanonical.environment()));

    assertFalse(postEntryRendered.contains("Request File"));
    assertFalse(declareRendered.contains("Request File"));
    assertFalse(executePlanRendered.contains("Request File"));
  }

  @Test
  void renderHelpHuman_omitsRequestGuidanceWhenRequestShapesOrScopedShapeIsMissing() {
    HelpDescriptor postEntryCanonical =
        MachineContract.help(identity(), environment(), OperationId.POST_ENTRY);
    HelpDescriptor declareCanonical =
        MachineContract.help(identity(), environment(), OperationId.DECLARE_ACCOUNT);
    HelpDescriptor executePlanCanonical =
        MachineContract.help(identity(), environment(), OperationId.EXECUTE_PLAN);

    String postEntryWithoutPostShape =
        CliDiscoveryOutputRenderer.renderHelpHuman(
            new HelpDescriptor(
                postEntryCanonical.application(),
                postEntryCanonical.version(),
                postEntryCanonical.description(),
                postEntryCanonical.usage(),
                postEntryCanonical.bookModel(),
                postEntryCanonical.accountingBaseline(),
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
                postEntryCanonical.currencyModel(),
                postEntryCanonical.extensionSurface(),
                postEntryCanonical.environment()));
    String declareWithoutRequestShapes =
        CliDiscoveryOutputRenderer.renderHelpHuman(
            new HelpDescriptor(
                declareCanonical.application(),
                declareCanonical.version(),
                declareCanonical.description(),
                declareCanonical.usage(),
                declareCanonical.bookModel(),
                declareCanonical.accountingBaseline(),
                null,
                declareCanonical.requestTemplate(),
                declareCanonical.declareAccountTemplate(),
                declareCanonical.planTemplate(),
                declareCanonical.commands(),
                declareCanonical.quickStart(),
                declareCanonical.exitCodes(),
                declareCanonical.preflight(),
                declareCanonical.currencyModel(),
                declareCanonical.extensionSurface(),
                declareCanonical.environment()));
    String declareWithoutDeclareShape =
        CliDiscoveryOutputRenderer.renderHelpHuman(
            new HelpDescriptor(
                declareCanonical.application(),
                declareCanonical.version(),
                declareCanonical.description(),
                declareCanonical.usage(),
                declareCanonical.bookModel(),
                declareCanonical.accountingBaseline(),
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
                declareCanonical.currencyModel(),
                declareCanonical.extensionSurface(),
                declareCanonical.environment()));
    String executePlanWithoutRequestShapes =
        CliDiscoveryOutputRenderer.renderHelpHuman(
            new HelpDescriptor(
                executePlanCanonical.application(),
                executePlanCanonical.version(),
                executePlanCanonical.description(),
                executePlanCanonical.usage(),
                executePlanCanonical.bookModel(),
                executePlanCanonical.accountingBaseline(),
                null,
                executePlanCanonical.requestTemplate(),
                executePlanCanonical.declareAccountTemplate(),
                executePlanCanonical.planTemplate(),
                executePlanCanonical.commands(),
                executePlanCanonical.quickStart(),
                executePlanCanonical.exitCodes(),
                executePlanCanonical.preflight(),
                executePlanCanonical.currencyModel(),
                executePlanCanonical.extensionSurface(),
                executePlanCanonical.environment()));
    String executePlanWithoutLedgerShape =
        CliDiscoveryOutputRenderer.renderHelpHuman(
            new HelpDescriptor(
                executePlanCanonical.application(),
                executePlanCanonical.version(),
                executePlanCanonical.description(),
                executePlanCanonical.usage(),
                executePlanCanonical.bookModel(),
                executePlanCanonical.accountingBaseline(),
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
                executePlanCanonical.currencyModel(),
                executePlanCanonical.extensionSurface(),
                executePlanCanonical.environment()));

    assertFalse(postEntryWithoutPostShape.contains("Request File"));
    assertFalse(declareWithoutRequestShapes.contains("Request File"));
    assertFalse(declareWithoutDeclareShape.contains("Request File"));
    assertFalse(executePlanWithoutRequestShapes.contains("Request File"));
    assertFalse(executePlanWithoutLedgerShape.contains("Request File"));
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
    assertTrue(rendered.contains("Getting Started"));
    assertTrue(rendered.contains("help"));
    assertFalse(rendered.contains("Quick Start"));
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
                        CliInvocationText.commandExample(OperationId.HELP))));
    assertTrue(
        Objects.requireNonNull(failure.getMessage())
            .contains("Failed to render CLI help request template JSON."));
    assertNotNull(failure.getCause());
  }

  @Test
  void renderJsonTemplate_withoutShortcutRendersIndentedJsonOnly() {
    String rendered =
        CliDiscoveryOutputRenderer.renderJsonTemplate(MachineContract.requestTemplate(), null);

    assertTrue(rendered.startsWith("  {"));
    assertTrue(rendered.contains("\"postingKind\" : \"STANDARD\""));
    assertFalse(rendered.contains("Shortcut:"));
  }

  @Test
  void renderHelpHuman_frontDoorHelpOmitsQuickStartWorkflowNoteBodies() {
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
                        List.of(WorkflowStepDescriptor.note("guidance")))),
                List.of(new ExitCodeDescriptor(0, "ok")),
                new ContractResponse.PreflightDescriptor(
                    "advisory", ContractResponse.CommitGuarantee.NOT_GUARANTEED, "desc"),
                new ContractResponse.CurrencyDescriptor("per-entry", "single-entry", "desc")));

    assertFalse(rendered.contains("guidance"));
    assertTrue(rendered.contains("Getting Started"));
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
    assertFalse(rendered.contains("Reporting position"));
    assertFalse(rendered.contains("Implemented extension seams"));
  }

  @Test
  void renderCapabilitiesHuman_omitsDeepBoundaryDoctrineFromHumanSurface() {
    var canonical = MachineContract.capabilities(identity(), environment(), Instant.now());
    String rendered =
        CliDiscoveryOutputRenderer.renderCapabilitiesHuman(
            new dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor(
                canonical.application(),
                canonical.version(),
                canonical.storage(),
                canonical.commands(),
                canonical.requestInput(),
                canonical.requestShapes(),
                canonical.responseModel(),
                canonical.planExecution(),
                canonical.audit(),
                canonical.accountRegistry(),
                canonical.reversals(),
                canonical.preflight(),
                canonical.currencyModel(),
                new ContractResponse.AccountingBaselineDescriptor(
                    OperationId.FINANCIAL_POSITION.wireName(),
                    canonical.accountingBaseline().doctrineSources(),
                    canonical.accountingBaseline().builtInStatements(),
                    canonical.accountingBaseline().deliberateExclusions(),
                    canonical.accountingBaseline().standardsPosition(),
                    canonical.accountingBaseline().reportingPosition(),
                    canonical.accountingBaseline().chartModelPosition(),
                    canonical.accountingBaseline().smallEntityPosition(),
                    canonical.accountingBaseline().operationalPosition(),
                    canonical.accountingBaseline().taxPosition(),
                    canonical.accountingBaseline().organizationalPosition(),
                    canonical.accountingBaseline().isoClarification()),
                new ContractResponse.ExtensionSurfaceDescriptor(
                    "ifrs-ias-iso-fx-ar-ap-gaap-playbook",
                    List.of("ifrs-ias-iso-fx-ar-ap-gaap-playbook", "oci"),
                    canonical.extensionSurface().futureContexts(),
                    canonical.extensionSurface().description()),
                canonical.environment(),
                canonical.timestamp()));

    assertFalse(rendered.contains("Financial position"));
    assertFalse(rendered.contains("Extension model"));
    assertFalse(rendered.contains("IFRS IAS ISO FX AR AP GAAP Playbook"));
    assertFalse(rendered.contains("OCI"));
  }

  @Test
  void renderVersionHuman_rendersTitleAndKeyValues() {
    String rendered =
        CliDiscoveryOutputRenderer.renderVersionHuman(MachineContract.version(identity()));

    assertTrue(rendered.contains("FinGrind"));
    assertTrue(rendered.contains("Version"));
    assertTrue(rendered.contains("0.37.0"));
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
      ContractRequestShapes.@org.jspecify.annotations.Nullable RequestShapesDescriptor
          requestShapes) {
    OperationId commandTopic = commands.size() == 1 ? commands.getFirst().name() : null;
    HelpDescriptor canonical =
        MachineContract.help(applicationIdentity, environment(), commandTopic);
    return new HelpDescriptor(
        applicationIdentity.application(),
        applicationIdentity.version(),
        applicationIdentity.description(),
        usage,
        bookModel,
        canonical.accountingBaseline(),
        requestShapes,
        canonical.requestTemplate(),
        canonical.declareAccountTemplate(),
        canonical.planTemplate(),
        commands,
        quickStart,
        exitCodes,
        preflight,
        currencyModel,
        canonical.extensionSurface(),
        environment());
  }

  private static ContractRequestShapes.RequestShapesDescriptor withoutDeclareAccountEnumVocabulary(
      ContractRequestShapes.RequestShapesDescriptor requestShapes) {
    ContractRequestShapes.DeclareAccountRequestShapeDescriptor declareAccount =
        Objects.requireNonNull(requestShapes.declareAccount());
    return new ContractRequestShapes.RequestShapesDescriptor(
        requestShapes.schemaDialect(),
        requestShapes.postEntry(),
        new ContractRequestShapes.DeclareAccountRequestShapeDescriptor(
            declareAccount.topLevelFields(), List.of(), declareAccount.schema()),
        requestShapes.ledgerPlan());
  }

  private static ApplicationIdentity identity() {
    return new ApplicationIdentity(
        "FinGrind",
        "0.37.0",
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
