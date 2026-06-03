package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.discovery.ArtifactOutputDescriptor;
import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.CommandCatalogDescriptor;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.discovery.SelectableOutputDefaultsDescriptor;
import dev.erst.fingrind.contract.protocol.BookProtectionMode;
import dev.erst.fingrind.contract.protocol.ExecutionMode;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.RuntimeDistribution;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeTrustBasis;
import dev.erst.fingrind.contract.protocol.StorageDriver;
import dev.erst.fingrind.contract.protocol.StorageEngine;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.contract.runtime.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentStorageDescriptor;
import dev.erst.fingrind.contract.runtime.ExitCodeDescriptor;
import dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Focused tests for runtime and capability discovery rendering. */
class CliDiscoveryRuntimeOutputRendererTest {
  @Test
  void renderCapabilitiesText_rendersCommandGroupsContractsAndRequestInput() {
    String rendered =
        CliDiscoveryOutputRenderer.renderCapabilitiesText(
            MachineContract.capabilities(CliDiscoveryTestSupport.identity()));

    assertTrue(rendered.contains("FinGrind Capabilities"));
    assertTrue(rendered.contains("Operator Overview"));
    assertTrue(rendered.contains("Next Steps"));
    assertTrue(rendered.contains("Operator guide"));
    assertTrue(rendered.contains("Kernel scope"));
    assertTrue(rendered.contains("Built-in statements"));
    assertTrue(rendered.contains("Discovery commands"));
    assertTrue(rendered.contains("Administration commands"));
    assertTrue(rendered.contains("Query and report commands"));
    assertTrue(rendered.contains("Write commands"));
    assertTrue(rendered.contains("PDF-capable reports"));
    assertFalse(rendered.contains("First Successful Run"));
    assertFalse(rendered.contains("Targeted Retrieval"));
    assertFalse(rendered.contains("Timestamp"));
    assertFalse(rendered.contains("Reporting position"));
    assertFalse(rendered.contains("Implemented extension seams"));
  }

  @Test
  void renderCapabilitiesText_rendersSharedSelectableDefaultsAsOneValue() {
    CapabilitiesDescriptor canonical =
        MachineContract.capabilities(CliDiscoveryTestSupport.identity());
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
                        List.of(OutputMode.JSON, OutputMode.TEXT),
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
            canonical.bookkeepingKernel());

    String rendered = CliDiscoveryOutputRenderer.renderCapabilitiesText(customized);

    assertTrue(rendered.contains("Operator Overview"));
    assertTrue(rendered.contains("Discovery commands"));
    assertFalse(rendered.contains("Selectable defaults"));
    assertFalse(rendered.contains("json interactive / json redirected"));
  }

  @Test
  void renderHelpText_commandHelpRendersArtifactOutputsAndCollapsedSelectableDefaults() {
    String rendered =
        CliDiscoveryOutputRenderer.renderHelpText(
            CliDiscoveryTestSupport.helpDescriptor(
                CliDiscoveryTestSupport.identity(),
                List.of("fingrind trial-balance --output json --pdf-out report.pdf"),
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
                        OperationId.TRIAL_BALANCE,
                        List.of(),
                        List.of("--output <json|text>", "--pdf-out <path>"),
                        ExecutionMode.JSON_ENVELOPE,
                        List.of(OutputMode.JSON, OutputMode.TEXT),
                        new SelectableOutputDefaultsDescriptor(OutputMode.JSON, OutputMode.JSON),
                        List.of(
                            new ArtifactOutputDescriptor(
                                "pdf", "--pdf-out <path>", "Write one PDF")),
                        "Read one trial balance")),
                List.of(),
                List.of(new ExitCodeDescriptor(0, "ok")),
                new ContractResponse.PreflightDescriptor(
                    "advisory", ContractResponse.CommitGuarantee.NOT_GUARANTEED, "desc"),
                new ContractResponse.CurrencyDescriptor("per-entry", "single-entry", "desc")));

    assertTrue(rendered.contains("Try It"));
    assertTrue(rendered.contains("Before You Run"));
    assertTrue(rendered.contains("--pdf-out <path>"));
    assertTrue(rendered.contains("Command"));
    assertFalse(rendered.contains("Request File"));
    assertFalse(rendered.contains("Starter file command"));
    assertFalse(rendered.contains("Artifact outputs"));
    assertFalse(rendered.contains("Selectable defaults"));
    assertFalse(rendered.contains("json interactive / json redirected"));
  }

  @Test
  void renderCapabilitiesText_omitsBookkeepingKernelDoctrineFromTextSurface() {
    var canonical = MachineContract.capabilities(CliDiscoveryTestSupport.identity());
    String rendered = CliDiscoveryOutputRenderer.renderCapabilitiesText(canonical);

    String renderedKernelScope =
        java.util.Arrays.stream(canonical.bookkeepingKernel().scope().split("[-_]"))
            .map(
                token ->
                    Character.toUpperCase(token.charAt(0))
                        + token.substring(1).toLowerCase(java.util.Locale.ROOT))
            .collect(java.util.stream.Collectors.joining(" "));
    assertTrue(rendered.contains(renderedKernelScope));
    assertTrue(
        canonical.bookkeepingKernel().builtInStatements().stream().allMatch(rendered::contains));
    assertFalse(rendered.contains(canonical.bookkeepingKernel().description()));
    assertFalse(rendered.contains("builtInStatements"));
    assertFalse(rendered.contains("reportCapabilities"));
  }

  @Test
  void renderCapabilitiesText_normalizesRepeatedKernelScopeSeparators() {
    CapabilitiesDescriptor canonical =
        MachineContract.capabilities(CliDiscoveryTestSupport.identity());
    CapabilitiesDescriptor customized =
        new CapabilitiesDescriptor(
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
            new ContractResponse.BookkeepingKernelDescriptor(
                "cash__single--entity_internal__management---kernel",
                canonical.bookkeepingKernel().builtInStatements(),
                canonical.bookkeepingKernel().reportCapabilities(),
                canonical.bookkeepingKernel().description()));

    String rendered = CliDiscoveryOutputRenderer.renderCapabilitiesText(customized);

    assertTrue(rendered.contains("Cash Single Entity Internal Management Kernel"));
    assertFalse(rendered.contains("Cash  Single"));
    assertFalse(rendered.contains("Single  Entity"));
    assertFalse(rendered.contains("cash__single--entity_internal__management---kernel"));
  }

  @Test
  void renderCapabilitiesText_rendersFallbackBookBoundaryAndCurrencyStatusInOperatorLanguage() {
    CapabilitiesDescriptor canonical =
        MachineContract.capabilities(CliDiscoveryTestSupport.identity());
    CapabilitiesDescriptor customized =
        new CapabilitiesDescriptor(
            canonical.application(),
            canonical.version(),
            new dev.erst.fingrind.contract.runtime.StorageSurfaceDescriptor(
                canonical.storage().engines(), "profile-owned-boundary"),
            canonical.commands(),
            canonical.requestInput(),
            canonical.requestShapes(),
            canonical.responseModel(),
            canonical.planExecution(),
            canonical.audit(),
            canonical.accountRegistry(),
            canonical.reversals(),
            canonical.preflight(),
            new ContractResponse.CurrencyDescriptor(
                "book-functional-currency", "planned-later", "desc"),
            canonical.bookkeepingKernel());

    String rendered = CliDiscoveryOutputRenderer.renderCapabilitiesText(customized);

    assertTrue(rendered.contains("Profile Owned Boundary."));
    assertTrue(rendered.contains("Book Functional Currency; planned later."));
  }

  @Test
  void renderCapabilitiesText_rendersSupportedCurrencyStatusAndProtectedBookBoundary() {
    CapabilitiesDescriptor canonical =
        MachineContract.capabilities(CliDiscoveryTestSupport.identity());
    CapabilitiesDescriptor customized =
        new CapabilitiesDescriptor(
            canonical.application(),
            canonical.version(),
            new dev.erst.fingrind.contract.runtime.StorageSurfaceDescriptor(
                canonical.storage().engines(), "protected-book-file"),
            canonical.commands(),
            canonical.requestInput(),
            canonical.requestShapes(),
            canonical.responseModel(),
            canonical.planExecution(),
            canonical.audit(),
            canonical.accountRegistry(),
            canonical.reversals(),
            canonical.preflight(),
            new ContractResponse.CurrencyDescriptor(
                "book-functional-currency", "supported", "desc"),
            canonical.bookkeepingKernel());

    String rendered = CliDiscoveryOutputRenderer.renderCapabilitiesText(customized);

    assertTrue(rendered.contains("One protected book per file."));
    assertTrue(rendered.contains("additional transaction currencies"));
    assertTrue(rendered.contains("available"));
  }

  @Test
  void renderEnvironmentText_rendersAlternateDistributionLabels() {
    String sourceCheckoutRendered =
        CliDiscoveryOutputRenderer.renderEnvironmentText(
            environmentForDistribution(RuntimeDistribution.SOURCE_CHECKOUT_GRADLE));
    String directJavaRendered =
        CliDiscoveryOutputRenderer.renderEnvironmentText(
            environmentForDistribution(RuntimeDistribution.DIRECT_JAVA_INVOCATION));
    String containerRendered =
        CliDiscoveryOutputRenderer.renderEnvironmentText(
            environmentForDistribution(RuntimeDistribution.CONTAINER_IMAGE));

    assertTrue(sourceCheckoutRendered.contains("Source checkout launcher"));
    assertTrue(directJavaRendered.contains("Developer direct-Java launcher"));
    assertTrue(containerRendered.contains("Container image launcher"));
  }

  @Test
  void renderEnvironmentText_coversExplicitRuntimeStateFamilies() {
    String readyRendered =
        CliDiscoveryOutputRenderer.renderEnvironmentText(
            CliDiscoveryTestSupport.environmentWithRuntime(
                new EnvironmentSqliteDescriptor.ReadyRuntime(
                    SqliteRuntimeProvenance.BUNDLE_MANAGED,
                    SqliteRuntimeTrustBasis.BUNDLE_SIDECAR_CONSISTENCY,
                    "<redacted>/libsqlite3.dylib",
                    ProtocolCatalog.managedSqlite().requiredMinimumSqliteVersion(),
                    ProtocolCatalog.managedSqlite().requiredSqlite3mcVersion(),
                    ProtocolCatalog.managedSqlite().requiredSqliteSourceId())));
    String failedRendered =
        CliDiscoveryOutputRenderer.renderEnvironmentText(
            CliDiscoveryTestSupport.environmentWithRuntime(
                new EnvironmentSqliteDescriptor.FailedRuntime(
                    SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                    SqliteRuntimeTrustBasis.SOURCE_CHECKOUT_SIDECAR_CONSISTENCY,
                    "<redacted>/libsqlite3.dylib",
                    "load failed")));
    String incompatibleRendered =
        CliDiscoveryOutputRenderer.renderEnvironmentText(
            CliDiscoveryTestSupport.environmentWithRuntime(
                new EnvironmentSqliteDescriptor.IncompatibleRuntime(
                    SqliteCompileOptionsVerificationStatus.FAILED,
                    SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                    SqliteRuntimeTrustBasis.SOURCE_CHECKOUT_SIDECAR_CONSISTENCY,
                    "<redacted>/libsqlite3.dylib",
                    "3.53.1",
                    "2.3.4",
                    "source-id",
                    "compile options mismatch")));
    String unavailableRendered =
        CliDiscoveryOutputRenderer.renderEnvironmentText(
            CliDiscoveryTestSupport.environmentWithRuntime(
                new EnvironmentSqliteDescriptor.UnavailableRuntime("no SQLite runtime available")));

    assertTrue(readyRendered.contains("Runtime status"));
    assertTrue(readyRendered.contains("Self-contained public bundle"));
    assertTrue(readyRendered.contains("Full machine inventory"));
    assertTrue(readyRendered.contains("Issue"));
    assertTrue(readyRendered.contains("(none)"));

    assertTrue(failedRendered.contains("Runtime status"));
    assertTrue(failedRendered.contains("failed"));
    assertTrue(failedRendered.contains("load failed"));
    assertTrue(failedRendered.contains("SQLite"));
    assertTrue(failedRendered.contains("(none)"));

    assertTrue(incompatibleRendered.contains("Runtime status"));
    assertTrue(incompatibleRendered.contains("incompatible"));
    assertTrue(incompatibleRendered.contains("compile options mismatch"));
    assertTrue(incompatibleRendered.contains("3.53.1"));
    assertTrue(incompatibleRendered.contains("2.3.4"));
    assertFalse(incompatibleRendered.contains("source-id"));

    assertTrue(unavailableRendered.contains("Runtime status"));
    assertTrue(unavailableRendered.contains("unavailable"));
    assertTrue(unavailableRendered.contains("Full machine inventory"));
    assertTrue(unavailableRendered.contains("no SQLite runtime available"));
    assertTrue(unavailableRendered.contains("(none)"));
  }

  @Test
  void renderVersionText_rendersTitleAndKeyValues() {
    String rendered =
        CliDiscoveryOutputRenderer.renderVersionText(
            MachineContract.version(CliDiscoveryTestSupport.identity()));

    assertTrue(rendered.contains("FinGrind"));
    assertTrue(rendered.contains("Version"));
    assertTrue(rendered.contains("0.51.0"));
  }

  private static dev.erst.fingrind.contract.runtime.EnvironmentDescriptor
      environmentForDistribution(RuntimeDistribution distribution) {
    return new dev.erst.fingrind.contract.runtime.EnvironmentDescriptor(
        new dev.erst.fingrind.contract.runtime.EnvironmentDistributionDescriptor(
            distribution,
            ProtocolCatalog.distribution().publicCliDistribution(),
            List.of(),
            List.of(),
            ProtocolCatalog.distribution().sourceCheckoutJava()),
        new EnvironmentStorageDescriptor(
            StorageDriver.SQLITE_FFM_SQLITE3MC,
            StorageEngine.SQLITE,
            BookProtectionMode.REQUIRED,
            CliDiscoveryTestSupport.environment().storage().defaultProtectedBookFormat()),
        CliDiscoveryTestSupport.environment().sqlite());
  }
}
