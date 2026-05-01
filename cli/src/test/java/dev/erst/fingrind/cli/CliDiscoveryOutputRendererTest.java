package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.ApplicationIdentity;
import dev.erst.fingrind.contract.CommandDescriptor;
import dev.erst.fingrind.contract.ContractResponse;
import dev.erst.fingrind.contract.EnvironmentDescriptor;
import dev.erst.fingrind.contract.EnvironmentDistributionDescriptor;
import dev.erst.fingrind.contract.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.EnvironmentStorageDescriptor;
import dev.erst.fingrind.contract.ExitCodeDescriptor;
import dev.erst.fingrind.contract.HelpDescriptor;
import dev.erst.fingrind.contract.MachineContract;
import dev.erst.fingrind.contract.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.contract.protocol.ExecutionMode;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.PublicCliBundleTarget;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliDiscoveryOutputRenderer}. */
class CliDiscoveryOutputRendererTest {
  @Test
  void renderHelpHuman_rendersFixedAndSelectableStdoutContractsAndEmptyQuickStart() {
    String rendered =
        CliDiscoveryOutputRenderer.renderHelpHuman(
            new HelpDescriptor(
                "FinGrind",
                "0.30.0",
                "desc",
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
                    new dev.erst.fingrind.contract.WorkflowDescriptor(
                        dev.erst.fingrind.contract.WorkflowSurface.BUNDLE_POSIX_SHELL,
                        List.of(dev.erst.fingrind.contract.WorkflowStepDescriptor.note("demo")))),
                List.of(new ExitCodeDescriptor(0, "ok")),
                new ContractResponse.PreflightDescriptor(
                    "advisory", ContractResponse.CommitGuarantee.NOT_GUARANTEED, "desc"),
                new ContractResponse.CurrencyDescriptor("per-entry", "single-entry", "desc"),
                environment()));

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
            new HelpDescriptor(
                "FinGrind",
                "0.30.0",
                "desc",
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
                    new dev.erst.fingrind.contract.WorkflowDescriptor(
                        dev.erst.fingrind.contract.WorkflowSurface.SOURCE_CHECKOUT_POSIX_SHELL,
                        List.of(dev.erst.fingrind.contract.WorkflowStepDescriptor.note("posix"))),
                    new dev.erst.fingrind.contract.WorkflowDescriptor(
                        dev.erst.fingrind.contract.WorkflowSurface.DIRECT_JAVA_WINDOWS_POWERSHELL,
                        List.of(dev.erst.fingrind.contract.WorkflowStepDescriptor.note("java"))),
                    new dev.erst.fingrind.contract.WorkflowDescriptor(
                        dev.erst.fingrind.contract.WorkflowSurface.CONTAINER_DOCKER,
                        List.of(dev.erst.fingrind.contract.WorkflowStepDescriptor.note("docker")))),
                List.of(new ExitCodeDescriptor(0, "ok")),
                new ContractResponse.PreflightDescriptor(
                    "advisory", ContractResponse.CommitGuarantee.NOT_GUARANTEED, "desc"),
                new ContractResponse.CurrencyDescriptor("per-entry", "single-entry", "desc"),
                environment()));

    assertTrue(rendered.contains("Source Checkout Launcher (POSIX Shell)"));
    assertTrue(rendered.contains("Developer Raw JAR (Windows PowerShell)"));
    assertTrue(rendered.contains("Container Image (Docker CLI)"));
  }

  @Test
  void renderHelpHuman_rendersNoneForAbsentQuickStartWorkflows() {
    String rendered =
        CliDiscoveryOutputRenderer.renderHelpHuman(
            new HelpDescriptor(
                "FinGrind",
                "0.30.0",
                "desc",
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
                new ContractResponse.CurrencyDescriptor("per-entry", "single-entry", "desc"),
                environment()));

    assertTrue(rendered.contains("Quick Start"));
    assertTrue(rendered.contains("(none)"));
  }

  @Test
  void renderHelpHuman_rendersCommandScopedHelpWithUsageAndExamples() {
    String rendered =
        CliDiscoveryOutputRenderer.renderHelpHuman(
            new HelpDescriptor(
                "FinGrind",
                "0.30.0",
                "desc",
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
                new ContractResponse.CurrencyDescriptor("per-entry", "single-entry", "desc"),
                environment()));

    assertTrue(rendered.contains("Command"));
    assertTrue(rendered.contains("Usage"));
    assertTrue(rendered.contains("Examples"));
    assertTrue(rendered.contains("post-entry"));
    assertTrue(rendered.contains("Commit one posting request"));
    assertTrue(
        rendered.contains(
            CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
                + " > request.json"));
  }

  @Test
  void renderHelpHuman_rendersScopedHelpFallbackSectionsWhenMetadataIsAbsent() {
    String rendered =
        CliDiscoveryOutputRenderer.renderHelpHuman(
            new HelpDescriptor(
                "FinGrind",
                "0.30.0",
                "desc",
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
                new ContractResponse.CurrencyDescriptor("per-entry", "single-entry", "desc"),
                environment()));

    assertTrue(rendered.contains("Aliases"));
    assertTrue(rendered.contains("--version"));
    assertTrue(rendered.contains("Usage"));
    assertTrue(rendered.contains("Options"));
    assertTrue(rendered.contains("Examples"));
    assertTrue(rendered.contains("(none)"));
  }

  @Test
  void renderHelpHuman_treatsSingleCommandWithQuickStartAsGeneralHelp() {
    String rendered =
        CliDiscoveryOutputRenderer.renderHelpHuman(
            new HelpDescriptor(
                "FinGrind",
                "0.30.0",
                "desc",
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
                    new dev.erst.fingrind.contract.WorkflowDescriptor(
                        dev.erst.fingrind.contract.WorkflowSurface.BUNDLE_POSIX_SHELL,
                        List.of(dev.erst.fingrind.contract.WorkflowStepDescriptor.note("demo")))),
                List.of(new ExitCodeDescriptor(0, "ok")),
                new ContractResponse.PreflightDescriptor(
                    "advisory", ContractResponse.CommitGuarantee.NOT_GUARANTEED, "desc"),
                new ContractResponse.CurrencyDescriptor("per-entry", "single-entry", "desc"),
                environment()));

    assertTrue(rendered.contains("Commands"));
    assertTrue(rendered.contains("Quick Start"));
    assertTrue(rendered.contains("help"));
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
    assertTrue(rendered.contains("0.30.0"));
  }

  private static ApplicationIdentity identity() {
    return new ApplicationIdentity(
        "FinGrind",
        "0.30.0",
        "Command-line double-entry bookkeeping with one encrypted book per business");
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
            SqliteCompileOptionsVerificationStatus.VERIFIED,
            ProtocolCatalog.requiredMinimumSqliteVersion(),
            ProtocolCatalog.requiredSqlite3mcVersion(),
            ProtocolCatalog.requiredSqliteSourceId(),
            SqliteRuntimeStatus.READY,
            SqliteRuntimeProvenance.BUNDLE_MANAGED,
            "/tmp/libsqlite3.dylib",
            ProtocolCatalog.requiredMinimumSqliteVersion(),
            ProtocolCatalog.requiredSqlite3mcVersion(),
            ProtocolCatalog.requiredSqliteSourceId(),
            null));
  }
}
