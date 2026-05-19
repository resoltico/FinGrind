package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.discovery.ApplicationIdentity;
import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.CommandCatalogDescriptor;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.discovery.SelectableOutputDefaultsDescriptor;
import dev.erst.fingrind.contract.discovery.WorkflowDescriptor;
import dev.erst.fingrind.contract.discovery.WorkflowStepDescriptor;
import dev.erst.fingrind.contract.discovery.WorkflowSurface;
import dev.erst.fingrind.contract.protocol.ExecutionMode;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliDiscoveryOutputRenderer}. */
class CliDiscoveryOutputRendererTest {
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
    assertTrue(rendered.contains("json | human"));
    assertTrue(rendered.contains("json envelope"));
    assertTrue(rendered.contains("raw json"));
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
  void renderHelpHuman_requestGuidance_omitsEmptyFieldGroups() {
    HelpDescriptor canonical =
        MachineContract.help(identity(), environment(), OperationId.EXECUTE_PLAN);
    ContractRequestShapes.LedgerPlanRequestShapeDescriptor ledgerPlan =
        Objects.requireNonNull(
            Objects.requireNonNull(canonical.requestShapes(), "requestShapes").ledgerPlan(),
            "ledgerPlan");
    HelpDescriptor helpDescriptor =
        new HelpDescriptor(
            canonical.application(),
            canonical.version(),
            canonical.description(),
            canonical.usage(),
            canonical.bookModel(),
            canonical.accountingBaseline(),
            new ContractRequestShapes.RequestShapesDescriptor(
                canonical.requestShapes().schemaDialect(),
                canonical.requestShapes().postEntry(),
                canonical.requestShapes().declareAccount(),
                new ContractRequestShapes.LedgerPlanRequestShapeDescriptor(
                    ledgerPlan.topLevelFields(),
                    ledgerPlan.stepFields(),
                    ledgerPlan.queryFields(),
                    List.of(),
                    ledgerPlan.administrationStepKinds(),
                    ledgerPlan.queryStepKinds(),
                    ledgerPlan.writeStepKinds(),
                    ledgerPlan.assertStepKind(),
                    ledgerPlan.assertionKinds(),
                    ledgerPlan.execution(),
                    ledgerPlan.schema())),
            canonical.requestTemplate(),
            canonical.declareAccountTemplate(),
            canonical.planTemplate(),
            canonical.commands(),
            canonical.quickStart(),
            canonical.exitCodes(),
            canonical.preflight(),
            canonical.currencyModel(),
            canonical.extensionSurface());

    String rendered = CliDiscoveryOutputRenderer.renderHelpHuman(helpDescriptor);

    assertTrue(rendered.contains("Generate a scaffold with:"));
    assertTrue(rendered.contains("Inspect the machine-readable contract with:"));
    assertTrue(rendered.contains("Accepted values:"));
    assertTrue(rendered.contains("steps[].kind (administration)"));
    assertTrue(rendered.contains("steps[].assertion.kind"));
    assertFalse(rendered.contains("Required fields:"));
    assertFalse(rendered.contains("Assertion fields:"));
  }

  @Test
  void renderHelpHuman_rendersAcceptedValuesForRequestFileCommands() {
    String postEntryRendered =
        CliDiscoveryOutputRenderer.renderHelpHuman(
            MachineContract.help(identity(), environment(), OperationId.POST_ENTRY));
    String declareAccountRendered =
        CliDiscoveryOutputRenderer.renderHelpHuman(
            MachineContract.help(identity(), environment(), OperationId.DECLARE_ACCOUNT));

    assertTrue(postEntryRendered.contains("Accepted values:"));
    assertTrue(postEntryRendered.contains("postingKind"));
    assertTrue(postEntryRendered.contains("STANDARD, OPENING_BALANCE"));
    assertTrue(postEntryRendered.contains("lineSide"));
    assertTrue(postEntryRendered.contains("DEBIT, CREDIT"));
    assertTrue(postEntryRendered.contains("actorType"));
    assertTrue(postEntryRendered.contains("HUMAN, SYSTEM, AGENT"));

    assertTrue(declareAccountRendered.contains("Accepted values:"));
    assertTrue(declareAccountRendered.contains("accountType"));
    assertTrue(declareAccountRendered.contains("accountRole"));
    assertTrue(declareAccountRendered.contains("financialPositionLineClassification"));
    assertTrue(declareAccountRendered.contains("profitAndLossLineClassification"));
    assertTrue(
        declareAccountRendered.contains("CURRENT_ASSET, NONCURRENT_ASSET, CURRENT_LIABILITY"));
    assertTrue(declareAccountRendered.contains("OPERATING_REVENUE, OTHER_REVENUE, FINANCE_INCOME"));
  }

  @Test
  void renderHelpHuman_rendersMaintenanceOperatorNotesAndCorrectedCloseExample() {
    String keyHelp =
        CliDiscoveryOutputRenderer.renderHelpHuman(
            MachineContract.help(identity(), environment(), OperationId.GENERATE_BOOK_KEY_FILE));
    String restoreHelp =
        CliDiscoveryOutputRenderer.renderHelpHuman(
            MachineContract.help(identity(), environment(), OperationId.RESTORE_BOOK));
    String closeHelp =
        CliDiscoveryOutputRenderer.renderHelpHuman(
            MachineContract.help(identity(), environment(), OperationId.CLOSE_PERIOD));

    assertTrue(keyHelp.contains("Choose one missing private parent directory"));
    assertTrue(
        restoreHelp.contains("reopen the restored live book with that same backup key file"));
    assertTrue(closeHelp.contains("FREELANCER and SOLE_PROPRIETORSHIP require OWNER_CAPITAL"));
    assertTrue(closeHelp.contains("PARTNERSHIP requires PARTNER_CURRENT"));
    assertTrue(closeHelp.contains("2026-04-30"));
    assertFalse(closeHelp.contains("--closing-equity-account"));
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
                + " "
                + OperationId.POST_ENTRY.wireName()));
    assertTrue(rendered.contains("Replace scaffold placeholders such as effectiveDate"));
  }

  @Test
  void renderHelpHuman_rendersPreflightRequestGuidanceWithCommandScopedContractLookup() {
    HelpDescriptor canonical =
        MachineContract.help(identity(), environment(), OperationId.PREFLIGHT_ENTRY);
    String rendered =
        CliDiscoveryOutputRenderer.renderHelpHuman(
            helpDescriptor(
                identity(),
                List.of("fingrind preflight-entry --book-file <path> --request-file <path|->"),
                canonical.bookModel(),
                List.of(
                    new CommandDescriptor(
                        OperationId.PREFLIGHT_ENTRY,
                        List.of(),
                        List.of("--book-file <path>", "--request-file <path|->"),
                        ExecutionMode.JSON_ENVELOPE,
                        List.of(
                            dev.erst.fingrind.contract.protocol.OutputMode.JSON,
                            dev.erst.fingrind.contract.protocol.OutputMode.HUMAN),
                        List.of(),
                        "Validate one posting request")),
                List.of(),
                List.of(new ExitCodeDescriptor(0, "ok")),
                canonical.preflight(),
                canonical.currencyModel(),
                canonical.requestShapes()));

    assertTrue(
        rendered.contains(
            CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                + " "
                + OperationId.PREFLIGHT_ENTRY.wireName()));
    assertTrue(
        rendered.contains(
            CliInvocationText.commandExample(OperationId.HELP) + " preflight-entry --output json"));
    assertFalse(
        rendered.contains(
            CliInvocationText.commandExample(OperationId.HELP) + " post-entry --output json"));
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
    assertTrue(rendered.contains("Generate a scaffold with:"));
    assertTrue(rendered.contains("declare-account"));
    assertTrue(rendered.contains("Inspect the machine-readable contract with:"));
    assertTrue(
        rendered.contains(
            CliInvocationText.commandExample(OperationId.HELP) + " declare-account --output json"));
    assertFalse(rendered.contains("Top-Level Fields"));
    assertFalse(rendered.contains("Template"));
    assertFalse(rendered.contains("Shortcut: fingrind"));
    assertFalse(rendered.contains("Enum Vocabulary"));
    assertFalse(rendered.contains("Required fields:"));
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
            "Generate a scaffold with: "
                + CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE)));
    assertTrue(rendered.contains("Inspect the machine-readable contract with:"));
    assertTrue(
        rendered.contains(
            CliInvocationText.commandExample(OperationId.HELP) + " execute-plan --output json"));
    assertFalse(rendered.contains("Required fields:"));
    assertFalse(rendered.contains("Step fields:"));
    assertFalse(rendered.contains("Query fields:"));
    assertFalse(rendered.contains("Assertion fields:"));
    assertFalse(rendered.contains("Enum Vocabulary"));
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
                postEntryCanonical.extensionSurface()));
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
                declareCanonical.extensionSurface()));
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
                executePlanCanonical.extensionSurface()));

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
                postEntryCanonical.extensionSurface()));
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
                declareCanonical.extensionSurface()));
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
                declareCanonical.extensionSurface()));
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
                executePlanCanonical.extensionSurface()));
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
                executePlanCanonical.extensionSurface()));

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
                        List.of(WorkflowStepDescriptor.note("bundle bootstrap note body")))),
                List.of(new ExitCodeDescriptor(0, "ok")),
                new ContractResponse.PreflightDescriptor(
                    "advisory", ContractResponse.CommitGuarantee.NOT_GUARANTEED, "desc"),
                new ContractResponse.CurrencyDescriptor("per-entry", "single-entry", "desc")));

    assertFalse(rendered.contains("bundle bootstrap note body"));
    assertTrue(rendered.contains("Getting Started"));
  }

  @Test
  void renderCapabilitiesHuman_rendersCommandGroupsContractsAndRequestInput() {
    String rendered =
        CliDiscoveryOutputRenderer.renderCapabilitiesHuman(
            MachineContract.capabilities(identity()));

    assertTrue(rendered.contains("FinGrind Capabilities"));
    assertTrue(rendered.contains("Command Groups"));
    assertTrue(rendered.contains("Command Contracts"));
    assertTrue(rendered.contains("Request Input"));
    assertTrue(rendered.contains("Discovery"));
    assertTrue(rendered.contains("trial-balance"));
    assertTrue(rendered.contains("json | human | csv"));
    assertTrue(rendered.contains("Targeted Retrieval"));
    assertTrue(rendered.contains("Selectable stdout flag"));
    assertTrue(rendered.contains("Targeted Retrieval"));
    assertFalse(rendered.contains("Timestamp"));
    assertFalse(rendered.contains("Reporting position"));
    assertFalse(rendered.contains("Implemented extension seams"));
  }

  @Test
  void renderCapabilitiesHuman_rendersSharedSelectableDefaultsAsOneValue() {
    CapabilitiesDescriptor canonical = MachineContract.capabilities(identity());
    CapabilitiesDescriptor customized =
        new CapabilitiesDescriptor(
            canonical.application(),
            canonical.version(),
            canonical.storage(),
            new CommandCatalogDescriptor(
                List.of(
                    new CommandDescriptor(
                        OperationId.HELP,
                        List.of(),
                        List.of(),
                        ExecutionMode.JSON_ENVELOPE,
                        List.of(OutputMode.JSON, OutputMode.HUMAN),
                        new SelectableOutputDefaultsDescriptor(OutputMode.JSON, OutputMode.JSON),
                        List.of(),
                        "Show help")),
                List.of(),
                List.of(),
                List.of()),
            canonical.requestInput(),
            canonical.requestShapes(),
            canonical.responseModel(),
            canonical.planExecution(),
            canonical.audit(),
            canonical.accountRegistry(),
            canonical.reversals(),
            canonical.preflight(),
            canonical.currencyModel(),
            canonical.accountingBaseline(),
            canonical.extensionSurface());

    String rendered = CliDiscoveryOutputRenderer.renderCapabilitiesHuman(customized);

    assertTrue(rendered.contains("json | human"));
    assertFalse(rendered.contains("json interactive / json redirected"));
  }

  @Test
  void renderCapabilitiesHuman_omitsDeepBoundaryDoctrineFromHumanSurface() {
    var canonical = MachineContract.capabilities(identity());
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
                    canonical.accountingBaseline().currentTarget(),
                    canonical.accountingBaseline().nextTarget(),
                    canonical.accountingBaseline().doctrineSources(),
                    canonical.accountingBaseline().builtInStatements(),
                    canonical.accountingBaseline().deliberateExclusions(),
                    canonical.accountingBaseline().nonClaims(),
                    canonical.accountingBaseline().reportCapabilities(),
                    canonical.accountingBaseline().defaultPolicyPack(),
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
                    canonical.extensionSurface().defaultPolicyPackId(),
                    List.of("ifrs-ias-iso-fx-ar-ap-gaap-playbook", "oci"),
                    canonical.extensionSurface().policySeams(),
                    canonical.extensionSurface().description())));

    assertFalse(rendered.contains("Financial position"));
    assertFalse(rendered.contains("Extension model"));
    assertFalse(rendered.contains("IFRS IAS ISO FX AR AP GAAP Playbook"));
    assertFalse(rendered.contains("OCI"));
  }

  @Test
  void renderEnvironmentHuman_coversExplicitRuntimeStateFamilies() {
    String readyRendered =
        CliDiscoveryOutputRenderer.renderEnvironmentHuman(
            environmentWithRuntime(
                new EnvironmentSqliteDescriptor.ReadyRuntime(
                    SqliteRuntimeProvenance.BUNDLE_MANAGED,
                    SqliteRuntimeTrustBasis.PUBLISHER_AUTHENTICATED,
                    "<redacted>/libsqlite3.dylib",
                    ProtocolCatalog.requiredMinimumSqliteVersion(),
                    ProtocolCatalog.requiredSqlite3mcVersion(),
                    ProtocolCatalog.requiredSqliteSourceId())));
    String failedRendered =
        CliDiscoveryOutputRenderer.renderEnvironmentHuman(
            environmentWithRuntime(
                new EnvironmentSqliteDescriptor.FailedRuntime(
                    SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                    SqliteRuntimeTrustBasis.SOURCE_VERIFIED_LOCAL_BUILD,
                    "<redacted>/libsqlite3.dylib",
                    "load failed")));
    String incompatibleRendered =
        CliDiscoveryOutputRenderer.renderEnvironmentHuman(
            environmentWithRuntime(
                new EnvironmentSqliteDescriptor.IncompatibleRuntime(
                    SqliteCompileOptionsVerificationStatus.FAILED,
                    SqliteRuntimeProvenance.ENVIRONMENT_CONFIGURED,
                    SqliteRuntimeTrustBasis.UNSAFE_LOCAL_OVERRIDE,
                    "<redacted>/libsqlite3.dylib",
                    "3.53.1",
                    "2.3.4",
                    "source-id",
                    "compile options mismatch")));
    String unavailableRendered =
        CliDiscoveryOutputRenderer.renderEnvironmentHuman(
            environmentWithRuntime(
                new EnvironmentSqliteDescriptor.UnavailableRuntime("no SQLite runtime available")));

    assertTrue(readyRendered.contains("Runtime provenance"));
    assertTrue(readyRendered.contains("bundle-managed"));
    assertTrue(readyRendered.contains("publisher-authenticated"));
    assertTrue(readyRendered.contains("<redacted>/libsqlite3.dylib"));
    assertTrue(readyRendered.contains("Issue"));
    assertTrue(readyRendered.contains("(none)"));

    assertTrue(failedRendered.contains("Runtime status"));
    assertTrue(failedRendered.contains("failed"));
    assertTrue(failedRendered.contains("source-checkout-managed"));
    assertTrue(failedRendered.contains("source-verified-local-build"));
    assertTrue(failedRendered.contains("load failed"));
    assertTrue(failedRendered.contains("Loaded SQLite version"));
    assertTrue(failedRendered.contains("(none)"));

    assertTrue(incompatibleRendered.contains("Runtime status"));
    assertTrue(incompatibleRendered.contains("incompatible"));
    assertTrue(incompatibleRendered.contains("environment-configured"));
    assertTrue(incompatibleRendered.contains("unsafe-local-override"));
    assertTrue(incompatibleRendered.contains("compile options mismatch"));
    assertTrue(incompatibleRendered.contains("3.53.1"));
    assertTrue(incompatibleRendered.contains("2.3.4"));
    assertTrue(incompatibleRendered.contains("source-id"));

    assertTrue(unavailableRendered.contains("Runtime status"));
    assertTrue(unavailableRendered.contains("unavailable"));
    assertTrue(unavailableRendered.contains("Runtime provenance"));
    assertTrue(unavailableRendered.contains("Runtime trust basis"));
    assertTrue(unavailableRendered.contains("no SQLite runtime available"));
    assertTrue(unavailableRendered.contains("(none)"));
  }

  @Test
  void renderVersionHuman_rendersTitleAndKeyValues() {
    String rendered =
        CliDiscoveryOutputRenderer.renderVersionHuman(MachineContract.version(identity()));

    assertTrue(rendered.contains("FinGrind"));
    assertTrue(rendered.contains("Version"));
    assertTrue(rendered.contains("0.41.0"));
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
        canonical.extensionSurface());
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
        "0.41.0",
        "Command-line double-entry bookkeeping with one protected book per accounting entity");
  }

  private static EnvironmentDescriptor environment() {
    return environmentWithRuntime(
        EnvironmentSqliteDescriptor.runtime(
            SqliteCompileOptionsVerificationStatus.VERIFIED,
            SqliteRuntimeStatus.READY,
            SqliteRuntimeProvenance.BUNDLE_MANAGED,
            SqliteRuntimeTrustBasis.PUBLISHER_AUTHENTICATED,
            "<redacted>/libsqlite3.dylib",
            ProtocolCatalog.requiredMinimumSqliteVersion(),
            ProtocolCatalog.requiredSqlite3mcVersion(),
            ProtocolCatalog.requiredSqliteSourceId(),
            null));
  }

  private static EnvironmentDescriptor environmentWithRuntime(
      EnvironmentSqliteDescriptor.RuntimeState runtime) {
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
            ProtocolCatalog.sqliteOperatorTrustSystemProperty(),
            ProtocolCatalog.sqliteBundleHomeSystemProperty(),
            ProtocolCatalog.requiredSqliteCompileOptions(),
            ProtocolCatalog.forbiddenSqliteCompileOptions(),
            ProtocolCatalog.requiresSecureMemorySupport(),
            ProtocolCatalog.requiredMinimumSqliteVersion(),
            ProtocolCatalog.requiredSqlite3mcVersion(),
            ProtocolCatalog.requiredSqliteSourceId(),
            runtime));
  }
}
