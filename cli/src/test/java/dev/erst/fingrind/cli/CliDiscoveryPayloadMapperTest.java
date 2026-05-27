package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliDiscoveryJsonModels;
import dev.erst.fingrind.contract.discovery.ApplicationIdentity;
import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.RuntimeDistribution;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliDiscoveryPayloadMapper}. */
class CliDiscoveryPayloadMapperTest extends CliResponseWriterTestSupport {
  @Test
  void helpPayload_mapsRootHelpToMinimalOverviewPayload() {
    CliDiscoveryJsonModels.HelpOverviewMinimalPayload payload =
        assertInstanceOf(
            CliDiscoveryJsonModels.HelpOverviewMinimalPayload.class,
            minimalHelpPayload(MachineContract.help(identity(), environment())));

    assertEquals("FinGrind", payload.application());
    assertEquals(DiscoveryDetail.MINIMAL, payload.detail());
    assertFalse(payload.commands().isEmpty());
    assertTrue(payload.compactDetailHint().contains("--detail compact"));
    assertTrue(payload.fullDetailHint().contains("--detail full"));
  }

  @Test
  void helpPayload_mapsRootHelpToOverviewPayload() {
    CliDiscoveryJsonModels.HelpOverviewCompactPayload payload =
        assertInstanceOf(
            CliDiscoveryJsonModels.HelpOverviewCompactPayload.class,
            compactHelpPayload(MachineContract.help(identity(), environment())));

    assertEquals("FinGrind", payload.application());
    assertEquals(DiscoveryDetail.COMPACT, payload.detail());
    assertFalse(payload.commands().isEmpty());
    assertTrue(payload.capabilitiesHint().contains("capabilities --output json"));
    assertTrue(payload.fullDetailHint().contains("--detail full"));
  }

  @Test
  void helpPayload_mapsRootHelpFullToOverviewPayloadWithFullContract() {
    CliDiscoveryJsonModels.HelpOverviewPayload payload =
        assertInstanceOf(
            CliDiscoveryJsonModels.HelpOverviewPayload.class,
            fullHelpPayload(MachineContract.help(identity(), environment())));

    assertEquals(DiscoveryDetail.FULL, payload.detail());
    assertNotNull(payload.fullContract());
    assertEquals("FinGrind", payload.fullContract().application());
    assertFalse(payload.fullContract().quickStart().isEmpty());
  }

  @Test
  void capabilitiesPayloads_mapMinimalAndFullDiscoveryVariants() {
    CapabilitiesDescriptor capabilitiesDescriptor = MachineContract.capabilities(identity());

    CliDiscoveryJsonModels.CapabilitiesMinimalPayload minimal =
        assertInstanceOf(
            CliDiscoveryJsonModels.CapabilitiesMinimalPayload.class,
            CliDiscoveryPayloadMapper.capabilitiesPayloadAny(
                capabilitiesDescriptor, DiscoveryDetail.MINIMAL));
    CliDiscoveryJsonModels.CapabilitiesCompactPayload compact =
        assertInstanceOf(
            CliDiscoveryJsonModels.CapabilitiesCompactPayload.class,
            CliDiscoveryPayloadMapper.capabilitiesPayload(
                capabilitiesDescriptor, DiscoveryDetail.COMPACT));
    CliDiscoveryJsonModels.CapabilitiesPayload full =
        assertInstanceOf(
            CliDiscoveryJsonModels.CapabilitiesPayload.class,
            CliDiscoveryPayloadMapper.capabilitiesPayload(
                capabilitiesDescriptor, DiscoveryDetail.FULL));

    assertEquals(DiscoveryDetail.MINIMAL, minimal.detail());
    assertTrue(minimal.compactDetailHint().contains("--detail compact"));
    assertEquals(DiscoveryDetail.COMPACT, compact.detail());
    assertFalse(compact.commands().isEmpty());
    assertNotNull(compact.requestInput());
    assertEquals(DiscoveryDetail.FULL, full.detail());
    assertNotNull(full.fullContract());
  }

  @Test
  void helpPayload_treatsSingleCommandWithQuickStartAsOverviewPayload() {
    HelpDescriptor canonical = MachineContract.help(identity(), environment(), OperationId.HELP);

    Object payload =
        compactHelpPayload(
            new HelpDescriptor(
                canonical.application(),
                canonical.version(),
                canonical.description(),
                canonical.usage(),
                canonical.bookModel(),
                canonical.bookkeepingKernel(),
                canonical.requestShapes(),
                canonical.requestTemplate(),
                canonical.declareAccountTemplate(),
                canonical.planTemplate(),
                canonical.commands(),
                java.util.List.of(
                    new dev.erst.fingrind.contract.discovery.WorkflowDescriptor(
                        dev.erst.fingrind.contract.discovery.WorkflowSurface.BUNDLE_POSIX_SHELL,
                        java.util.List.of(
                            dev.erst.fingrind.contract.discovery.WorkflowStepDescriptor.note(
                                "demo")))),
                canonical.exitCodes(),
                canonical.preflight(),
                canonical.currencyModel()));

    assertInstanceOf(CliDiscoveryJsonModels.HelpOverviewCompactPayload.class, payload);
  }

  @Test
  void helpPayload_mapsNonRequestFileCommandWithoutRequestGuidance() {
    CliDiscoveryJsonModels.CommandHelpPayload payload =
        assertInstanceOf(
            CliDiscoveryJsonModels.CommandHelpPayload.class,
            compactHelpPayload(
                MachineContract.help(identity(), environment(), OperationId.VERSION)));

    assertNull(payload.requestFile());
  }

  @Test
  void helpPayload_mapsPostingRequestGuidanceForPostingCommands() {
    assertPostingGuidance(OperationId.POST_ENTRY);
    assertPostingGuidance(OperationId.PREFLIGHT_ENTRY);
  }

  @Test
  void helpPayload_mapsDeclareAccountAndLedgerPlanRequestGuidance() {
    CliDiscoveryJsonModels.CommandHelpPayload declarePayload =
        assertInstanceOf(
            CliDiscoveryJsonModels.CommandHelpPayload.class,
            fullHelpPayload(
                MachineContract.help(identity(), environment(), OperationId.DECLARE_ACCOUNT)));
    CliDiscoveryJsonModels.CommandHelpPayload compactDeclarePayload =
        assertInstanceOf(
            CliDiscoveryJsonModels.CommandHelpPayload.class,
            compactHelpPayload(
                MachineContract.help(identity(), environment(), OperationId.DECLARE_ACCOUNT)));
    CliDiscoveryJsonModels.CommandHelpPayload planPayload =
        assertInstanceOf(
            CliDiscoveryJsonModels.CommandHelpPayload.class,
            fullHelpPayload(
                MachineContract.help(identity(), environment(), OperationId.EXECUTE_PLAN)));
    CliDiscoveryJsonModels.CommandHelpPayload compactPlanPayload =
        assertInstanceOf(
            CliDiscoveryJsonModels.CommandHelpPayload.class,
            compactHelpPayload(
                MachineContract.help(identity(), environment(), OperationId.EXECUTE_PLAN)));

    assertNotNull(declarePayload.requestFile());
    assertNull(declarePayload.requestFile().postingTemplate());
    assertNotNull(declarePayload.requestFile().declareAccountTemplate());
    assertNull(declarePayload.requestFile().ledgerPlanTemplate());
    assertTrue(
        Objects.requireNonNull(declarePayload.requestFile().shortcutCommand())
            .contains("declare-account"));
    assertNotNull(compactDeclarePayload.requestFile());
    assertNull(compactDeclarePayload.requestFile().postingTemplate());
    assertNull(compactDeclarePayload.requestFile().declareAccountTemplate());
    assertNull(compactDeclarePayload.requestFile().ledgerPlanTemplate());
    assertNull(compactDeclarePayload.requestFile().requestShapes());
    assertTrue(
        Objects.requireNonNull(compactDeclarePayload.requestFile().shortcutCommand())
            .contains("declare-account"));

    assertNotNull(planPayload.requestFile());
    assertNull(planPayload.requestFile().postingTemplate());
    assertNull(planPayload.requestFile().declareAccountTemplate());
    assertNotNull(planPayload.requestFile().ledgerPlanTemplate());
    assertEquals(
        CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE),
        planPayload.requestFile().shortcutCommand());
    assertNotNull(compactPlanPayload.requestFile());
    assertNull(compactPlanPayload.requestFile().postingTemplate());
    assertNull(compactPlanPayload.requestFile().declareAccountTemplate());
    assertNull(compactPlanPayload.requestFile().ledgerPlanTemplate());
    assertNull(compactPlanPayload.requestFile().requestShapes());
    assertEquals(
        CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE),
        compactPlanPayload.requestFile().shortcutCommand());
  }

  @Test
  void helpPayload_omitsRequestGuidanceWhenArtifactsAreMissing() {
    HelpDescriptor postEntry =
        MachineContract.help(identity(), environment(), OperationId.POST_ENTRY);
    HelpDescriptor declareAccount =
        MachineContract.help(identity(), environment(), OperationId.DECLARE_ACCOUNT);
    HelpDescriptor executePlan =
        MachineContract.help(identity(), environment(), OperationId.EXECUTE_PLAN);

    CliDiscoveryJsonModels.CommandHelpPayload postEntryPayload =
        assertInstanceOf(
            CliDiscoveryJsonModels.CommandHelpPayload.class,
            compactHelpPayload(
                new HelpDescriptor(
                    postEntry.application(),
                    postEntry.version(),
                    postEntry.description(),
                    postEntry.usage(),
                    postEntry.bookModel(),
                    postEntry.bookkeepingKernel(),
                    null,
                    postEntry.requestTemplate(),
                    postEntry.declareAccountTemplate(),
                    postEntry.planTemplate(),
                    postEntry.commands(),
                    postEntry.quickStart(),
                    postEntry.exitCodes(),
                    postEntry.preflight(),
                    postEntry.currencyModel())));
    CliDiscoveryJsonModels.CommandHelpPayload declarePayload =
        assertInstanceOf(
            CliDiscoveryJsonModels.CommandHelpPayload.class,
            compactHelpPayload(
                new HelpDescriptor(
                    declareAccount.application(),
                    declareAccount.version(),
                    declareAccount.description(),
                    declareAccount.usage(),
                    declareAccount.bookModel(),
                    declareAccount.bookkeepingKernel(),
                    declareAccount.requestShapes(),
                    declareAccount.requestTemplate(),
                    null,
                    declareAccount.planTemplate(),
                    declareAccount.commands(),
                    declareAccount.quickStart(),
                    declareAccount.exitCodes(),
                    declareAccount.preflight(),
                    declareAccount.currencyModel())));
    CliDiscoveryJsonModels.CommandHelpPayload planPayload =
        assertInstanceOf(
            CliDiscoveryJsonModels.CommandHelpPayload.class,
            compactHelpPayload(
                new HelpDescriptor(
                    executePlan.application(),
                    executePlan.version(),
                    executePlan.description(),
                    executePlan.usage(),
                    executePlan.bookModel(),
                    executePlan.bookkeepingKernel(),
                    executePlan.requestShapes(),
                    executePlan.requestTemplate(),
                    executePlan.declareAccountTemplate(),
                    null,
                    executePlan.commands(),
                    executePlan.quickStart(),
                    executePlan.exitCodes(),
                    executePlan.preflight(),
                    executePlan.currencyModel())));

    assertNull(postEntryPayload.requestFile());
    assertNull(declarePayload.requestFile());
    assertNull(planPayload.requestFile());
  }

  @Test
  void helpPayload_omitsPostingRequestGuidanceWhenTemplateIsMissing() {
    HelpDescriptor postEntry =
        MachineContract.help(identity(), environment(), OperationId.POST_ENTRY);

    CliDiscoveryJsonModels.CommandHelpPayload payload =
        assertInstanceOf(
            CliDiscoveryJsonModels.CommandHelpPayload.class,
            compactHelpPayload(
                new HelpDescriptor(
                    postEntry.application(),
                    postEntry.version(),
                    postEntry.description(),
                    postEntry.usage(),
                    postEntry.bookModel(),
                    postEntry.bookkeepingKernel(),
                    postEntry.requestShapes(),
                    null,
                    postEntry.declareAccountTemplate(),
                    postEntry.planTemplate(),
                    postEntry.commands(),
                    postEntry.quickStart(),
                    postEntry.exitCodes(),
                    postEntry.preflight(),
                    postEntry.currencyModel())));

    assertNull(payload.requestFile());
  }

  @Test
  void helpPayload_omitsRequestGuidanceWhenRequestShapesAreMissing() {
    HelpDescriptor postEntry =
        MachineContract.help(identity(), environment(), OperationId.POST_ENTRY);
    HelpDescriptor declareAccount =
        MachineContract.help(identity(), environment(), OperationId.DECLARE_ACCOUNT);
    HelpDescriptor executePlan =
        MachineContract.help(identity(), environment(), OperationId.EXECUTE_PLAN);

    CliDiscoveryJsonModels.CommandHelpPayload postEntryPayload =
        assertInstanceOf(
            CliDiscoveryJsonModels.CommandHelpPayload.class,
            compactHelpPayload(
                new HelpDescriptor(
                    postEntry.application(),
                    postEntry.version(),
                    postEntry.description(),
                    postEntry.usage(),
                    postEntry.bookModel(),
                    postEntry.bookkeepingKernel(),
                    null,
                    postEntry.requestTemplate(),
                    postEntry.declareAccountTemplate(),
                    postEntry.planTemplate(),
                    postEntry.commands(),
                    postEntry.quickStart(),
                    postEntry.exitCodes(),
                    postEntry.preflight(),
                    postEntry.currencyModel())));
    CliDiscoveryJsonModels.CommandHelpPayload declarePayload =
        assertInstanceOf(
            CliDiscoveryJsonModels.CommandHelpPayload.class,
            compactHelpPayload(
                new HelpDescriptor(
                    declareAccount.application(),
                    declareAccount.version(),
                    declareAccount.description(),
                    declareAccount.usage(),
                    declareAccount.bookModel(),
                    declareAccount.bookkeepingKernel(),
                    null,
                    declareAccount.requestTemplate(),
                    declareAccount.declareAccountTemplate(),
                    declareAccount.planTemplate(),
                    declareAccount.commands(),
                    declareAccount.quickStart(),
                    declareAccount.exitCodes(),
                    declareAccount.preflight(),
                    declareAccount.currencyModel())));
    CliDiscoveryJsonModels.CommandHelpPayload planPayload =
        assertInstanceOf(
            CliDiscoveryJsonModels.CommandHelpPayload.class,
            compactHelpPayload(
                new HelpDescriptor(
                    executePlan.application(),
                    executePlan.version(),
                    executePlan.description(),
                    executePlan.usage(),
                    executePlan.bookModel(),
                    executePlan.bookkeepingKernel(),
                    null,
                    executePlan.requestTemplate(),
                    executePlan.declareAccountTemplate(),
                    executePlan.planTemplate(),
                    executePlan.commands(),
                    executePlan.quickStart(),
                    executePlan.exitCodes(),
                    executePlan.preflight(),
                    executePlan.currencyModel())));

    assertNull(postEntryPayload.requestFile());
    assertNull(declarePayload.requestFile());
    assertNull(planPayload.requestFile());
  }

  @Test
  void helpPayload_omitsRequestGuidanceWhenScopedRequestShapeIsMissing() {
    HelpDescriptor postEntry =
        MachineContract.help(identity(), environment(), OperationId.POST_ENTRY);
    HelpDescriptor declareAccount =
        MachineContract.help(identity(), environment(), OperationId.DECLARE_ACCOUNT);
    HelpDescriptor executePlan =
        MachineContract.help(identity(), environment(), OperationId.EXECUTE_PLAN);

    CliDiscoveryJsonModels.CommandHelpPayload postEntryPayload =
        assertInstanceOf(
            CliDiscoveryJsonModels.CommandHelpPayload.class,
            compactHelpPayload(
                new HelpDescriptor(
                    postEntry.application(),
                    postEntry.version(),
                    postEntry.description(),
                    postEntry.usage(),
                    postEntry.bookModel(),
                    postEntry.bookkeepingKernel(),
                    new dev.erst.fingrind.contract.discovery.ContractRequestShapes
                        .RequestShapesDescriptor(
                        Objects.requireNonNull(postEntry.requestShapes()).schemaDialect(),
                        null,
                        postEntry.requestShapes().declareAccount(),
                        postEntry.requestShapes().ledgerPlan()),
                    postEntry.requestTemplate(),
                    postEntry.declareAccountTemplate(),
                    postEntry.planTemplate(),
                    postEntry.commands(),
                    postEntry.quickStart(),
                    postEntry.exitCodes(),
                    postEntry.preflight(),
                    postEntry.currencyModel())));
    CliDiscoveryJsonModels.CommandHelpPayload declarePayload =
        assertInstanceOf(
            CliDiscoveryJsonModels.CommandHelpPayload.class,
            compactHelpPayload(
                new HelpDescriptor(
                    declareAccount.application(),
                    declareAccount.version(),
                    declareAccount.description(),
                    declareAccount.usage(),
                    declareAccount.bookModel(),
                    declareAccount.bookkeepingKernel(),
                    new dev.erst.fingrind.contract.discovery.ContractRequestShapes
                        .RequestShapesDescriptor(
                        Objects.requireNonNull(declareAccount.requestShapes()).schemaDialect(),
                        declareAccount.requestShapes().postEntry(),
                        null,
                        declareAccount.requestShapes().ledgerPlan()),
                    declareAccount.requestTemplate(),
                    declareAccount.declareAccountTemplate(),
                    declareAccount.planTemplate(),
                    declareAccount.commands(),
                    declareAccount.quickStart(),
                    declareAccount.exitCodes(),
                    declareAccount.preflight(),
                    declareAccount.currencyModel())));
    CliDiscoveryJsonModels.CommandHelpPayload planPayload =
        assertInstanceOf(
            CliDiscoveryJsonModels.CommandHelpPayload.class,
            compactHelpPayload(
                new HelpDescriptor(
                    executePlan.application(),
                    executePlan.version(),
                    executePlan.description(),
                    executePlan.usage(),
                    executePlan.bookModel(),
                    executePlan.bookkeepingKernel(),
                    new dev.erst.fingrind.contract.discovery.ContractRequestShapes
                        .RequestShapesDescriptor(
                        Objects.requireNonNull(executePlan.requestShapes()).schemaDialect(),
                        executePlan.requestShapes().postEntry(),
                        executePlan.requestShapes().declareAccount(),
                        null),
                    executePlan.requestTemplate(),
                    executePlan.declareAccountTemplate(),
                    executePlan.planTemplate(),
                    executePlan.commands(),
                    executePlan.quickStart(),
                    executePlan.exitCodes(),
                    executePlan.preflight(),
                    executePlan.currencyModel())));

    assertNull(postEntryPayload.requestFile());
    assertNull(declarePayload.requestFile());
    assertNull(planPayload.requestFile());
  }

  @Test
  void requestFileGuidancePayload_requiresAtLeastOneArtifact() {
    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CliDiscoveryJsonModels.RequestFileGuidancePayload(
                    "desc", DiscoveryDetail.COMPACT, null, null, null, null, null));

    assertTrue(
        Objects.requireNonNull(failure.getMessage())
            .contains("At least one request-file guidance artifact"));
  }

  @Test
  void requestFileGuidancePayload_allowsSingleTemplateArtifactWithoutRequestShapes() {
    CliDiscoveryJsonModels.RequestFileGuidancePayload payload =
        new CliDiscoveryJsonModels.RequestFileGuidancePayload(
            "Provide one posting JSON object through --request-file <path|->.",
            DiscoveryDetail.COMPACT,
            MachineContract.requestTemplate(),
            null,
            null,
            null,
            null);

    assertNotNull(payload.postingTemplate());
    assertNull(payload.requestShapes());
    assertNull(payload.shortcutCommand());
  }

  @Test
  void requestFileGuidancePayload_allowsRequestShapesWithoutTemplateArtifact() {
    HelpDescriptor postEntry =
        MachineContract.help(identity(), environment(), OperationId.POST_ENTRY);
    dev.erst.fingrind.contract.discovery.ContractRequestShapes.RequestShapesDescriptor
        requestShapes = Objects.requireNonNull(postEntry.requestShapes());
    CliDiscoveryJsonModels.RequestFileGuidancePayload payload =
        new CliDiscoveryJsonModels.RequestFileGuidancePayload(
            "Provide one posting JSON object through --request-file <path|->.",
            DiscoveryDetail.COMPACT,
            null,
            null,
            null,
            new dev.erst.fingrind.contract.discovery.ContractRequestShapes.RequestShapesDescriptor(
                requestShapes.schemaDialect(), requestShapes.postEntry(), null, null),
            null);

    assertNull(payload.postingTemplate());
    assertNotNull(payload.requestShapes());
  }

  private static void assertPostingGuidance(OperationId operationId) {
    CliDiscoveryJsonModels.CommandHelpPayload payload =
        assertInstanceOf(
            CliDiscoveryJsonModels.CommandHelpPayload.class,
            fullHelpPayload(MachineContract.help(identity(), environment(), operationId)));

    assertNotNull(payload.requestFile());
    assertNotNull(payload.requestFile().postingTemplate());
    assertNull(payload.requestFile().declareAccountTemplate());
    assertNull(payload.requestFile().ledgerPlanTemplate());
    assertNotNull(payload.requestFile().requestShapes());
    assertEquals(
        CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE),
        payload.requestFile().shortcutCommand());
  }

  private static Object compactHelpPayload(HelpDescriptor helpDescriptor) {
    return CliDiscoveryPayloadMapper.helpPayload(helpDescriptor, DiscoveryDetail.COMPACT);
  }

  private static Object minimalHelpPayload(HelpDescriptor helpDescriptor) {
    return CliDiscoveryPayloadMapper.helpPayload(helpDescriptor, DiscoveryDetail.MINIMAL);
  }

  private static Object fullHelpPayload(HelpDescriptor helpDescriptor) {
    return CliDiscoveryPayloadMapper.helpPayload(helpDescriptor, DiscoveryDetail.FULL);
  }

  private static ApplicationIdentity identity() {
    return new ApplicationIdentity(
        "FinGrind",
        "0.48.0",
        "Command-line double-entry bookkeeping with one protected book per accounting entity");
  }

  private static EnvironmentDescriptor environment() {
    return environmentDescriptor(
        RuntimeDistribution.SELF_CONTAINED_BUNDLE.wireValue(),
        SqliteCompileOptionsVerificationStatus.VERIFIED,
        "ready",
        ProtocolCatalog.managedSqlite().requiredMinimumSqliteVersion(),
        ProtocolCatalog.managedSqlite().requiredSqlite3mcVersion(),
        null);
  }
}
