package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliDiscoveryCapabilitiesJsonModels;
import dev.erst.fingrind.cli.json.CliDiscoveryCapabilitiesSliceJsonModels;
import dev.erst.fingrind.cli.json.CliDiscoveryCommonJsonModels;
import dev.erst.fingrind.cli.json.CliDiscoveryHelpJsonModels;
import dev.erst.fingrind.cli.json.CliDiscoveryRequestInputSliceJsonModels;
import dev.erst.fingrind.contract.discovery.ApplicationIdentity;
import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.CommandCatalogDescriptor;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.DiscoveryFocus;
import dev.erst.fingrind.contract.protocol.ExecutionMode;
import dev.erst.fingrind.contract.protocol.OperationCategory;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.RuntimeDistribution;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliDiscoveryPayloadMapper}. */
class CliDiscoveryPayloadMapperTest extends CliResponseWriterTestSupport {
  @Test
  void helpPayload_mapsRootHelpToMinimalOverviewPayload() {
    CliDiscoveryHelpJsonModels.HelpOverviewMinimalPayload payload =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.HelpOverviewMinimalPayload.class,
            minimalHelpPayload(MachineContract.help(identity(), environment())));

    assertEquals("FinGrind", payload.application());
    assertEquals(MachineContract.protocolVersion(), payload.protocolVersion());
    assertEquals(DiscoveryDetail.MINIMAL, payload.detail());
    assertFalse(payload.commands().isEmpty());
    assertTrue(payload.compactDetailHint().contains("--detail compact"));
    assertTrue(payload.fullDetailHint().contains("--detail full"));
  }

  @Test
  void helpPayload_mapsRootHelpToOverviewPayload() {
    CliDiscoveryHelpJsonModels.HelpOverviewCompactPayload payload =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.HelpOverviewCompactPayload.class,
            compactHelpPayload(MachineContract.help(identity(), environment())));

    assertEquals("FinGrind", payload.application());
    assertEquals(MachineContract.protocolVersion(), payload.protocolVersion());
    assertEquals(DiscoveryDetail.COMPACT, payload.detail());
    assertFalse(payload.commands().isEmpty());
    assertTrue(payload.capabilitiesHint().contains("capabilities --output json"));
    assertTrue(payload.fullDetailHint().contains("--detail full"));
  }

  @Test
  void helpPayload_mapsRootHelpFullToOverviewPayloadWithFullContract() {
    CliDiscoveryHelpJsonModels.HelpOverviewPayload payload =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.HelpOverviewPayload.class,
            fullHelpPayload(MachineContract.help(identity(), environment())));

    assertEquals(DiscoveryDetail.FULL, payload.detail());
    assertEquals(MachineContract.protocolVersion(), payload.protocolVersion());
    assertNotNull(payload.fullContract());
    assertEquals("FinGrind", payload.fullContract().application());
    assertFalse(payload.fullContract().quickStart().isEmpty());
  }

  @Test
  void capabilitiesPayloads_mapMinimalAndFullDiscoveryVariants() {
    CapabilitiesDescriptor capabilitiesDescriptor = MachineContract.capabilities(identity());

    CliDiscoveryCapabilitiesJsonModels.CapabilitiesMinimalPayload minimal =
        assertInstanceOf(
            CliDiscoveryCapabilitiesJsonModels.CapabilitiesMinimalPayload.class,
            CliDiscoveryPayloadMapper.capabilitiesPayloadAny(
                capabilitiesDescriptor, DiscoveryDetail.MINIMAL, overviewSelections()));
    CliDiscoveryCapabilitiesJsonModels.CapabilitiesCompactPayload compact =
        assertInstanceOf(
            CliDiscoveryCapabilitiesJsonModels.CapabilitiesCompactPayload.class,
            CliDiscoveryPayloadMapper.capabilitiesPayload(
                capabilitiesDescriptor, DiscoveryDetail.COMPACT, overviewSelections()));
    CliDiscoveryCapabilitiesJsonModels.CapabilitiesPayload full =
        assertInstanceOf(
            CliDiscoveryCapabilitiesJsonModels.CapabilitiesPayload.class,
            CliDiscoveryPayloadMapper.capabilitiesPayload(
                capabilitiesDescriptor, DiscoveryDetail.FULL, overviewSelections()));

    assertEquals(DiscoveryDetail.MINIMAL, minimal.detail());
    assertEquals(MachineContract.protocolVersion(), minimal.protocolVersion());
    assertEquals(DiscoveryFocus.OVERVIEW, minimal.focus());
    assertTrue(minimal.compactDetailHint().contains("--detail compact"));
    assertEquals(DiscoveryDetail.COMPACT, compact.detail());
    assertEquals(MachineContract.protocolVersion(), compact.protocolVersion());
    assertEquals(DiscoveryFocus.OVERVIEW, compact.focus());
    assertFalse(compact.commands().isEmpty());
    assertTrue(compact.commands().stream().anyMatch(command -> "query".equals(command.category())));
    assertNotNull(compact.requestInput());
    assertEquals(DiscoveryDetail.FULL, full.detail());
    assertEquals(MachineContract.protocolVersion(), full.protocolVersion());
    assertEquals(DiscoveryFocus.OVERVIEW, full.focus());
    assertNotNull(full.fullContract());
    assertEquals(
        capabilitiesDescriptor.capabilityCatalog(), full.fullContract().capabilityCatalog());
  }

  @Test
  void capabilitiesPayload_mapsCanonicalCapabilityCatalogSlice() {
    CapabilitiesDescriptor capabilitiesDescriptor = MachineContract.capabilities(identity());

    CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload slice =
        assertInstanceOf(
            CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload.class,
            CliDiscoveryPayloadMapper.capabilitiesPayload(
                capabilitiesDescriptor,
                DiscoveryDetail.FULL,
                new CliDiscoverySelections(DiscoveryFocus.CAPABILITY_CATALOG, null)));

    CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesCatalogSlicePayload catalog =
        assertInstanceOf(
            CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesCatalogSlicePayload.class,
            slice.data());
    assertEquals(DiscoveryFocus.CAPABILITY_CATALOG, slice.focus());
    assertEquals(capabilitiesDescriptor.capabilityCatalog(), catalog.capabilityCatalog());
    assertTrue(slice.nextHints().getFirst().contains("operative boundary"));
  }

  @Test
  void capabilitiesPayload_mapsFocusedCommandSlicesAcrossDetailsAndCategories() {
    CapabilitiesDescriptor capabilitiesDescriptor = MachineContract.capabilities(identity());

    CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload minimal =
        assertInstanceOf(
            CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload.class,
            CliDiscoveryPayloadMapper.capabilitiesPayload(
                capabilitiesDescriptor,
                DiscoveryDetail.MINIMAL,
                new CliDiscoverySelections(DiscoveryFocus.COMMANDS, OperationCategory.QUERY)));
    CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload compact =
        assertInstanceOf(
            CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload.class,
            CliDiscoveryPayloadMapper.capabilitiesPayload(
                capabilitiesDescriptor,
                DiscoveryDetail.COMPACT,
                new CliDiscoverySelections(DiscoveryFocus.COMMANDS, OperationCategory.QUERY)));
    CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload full =
        assertInstanceOf(
            CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload.class,
            CliDiscoveryPayloadMapper.capabilitiesPayload(
                capabilitiesDescriptor,
                DiscoveryDetail.FULL,
                new CliDiscoverySelections(DiscoveryFocus.COMMANDS, null)));

    assertEquals(DiscoveryFocus.COMMANDS, minimal.focus());
    assertEquals(OperationCategory.QUERY.wireValue(), minimal.category());
    CliDiscoveryCommonJsonModels.CapabilitiesCommandsSlicePayload minimalCommands =
        assertInstanceOf(
            CliDiscoveryCommonJsonModels.CapabilitiesCommandsSlicePayload.class, minimal.data());
    assertFalse(minimalCommands.commands().isEmpty());
    assertTrue(
        minimalCommands.commands().stream()
            .allMatch(command -> OperationCategory.QUERY.wireValue().equals(command.category())));
    assertNull(minimalCommands.commandSurfaces());
    assertNull(minimalCommands.fullCommands());
    assertTrue(minimal.nextHints().getFirst().contains("--category"));

    assertEquals(DiscoveryFocus.COMMANDS, compact.focus());
    assertEquals(OperationCategory.QUERY.wireValue(), compact.category());
    CliDiscoveryCommonJsonModels.CapabilitiesCommandsSlicePayload compactCommands =
        assertInstanceOf(
            CliDiscoveryCommonJsonModels.CapabilitiesCommandsSlicePayload.class, compact.data());
    assertFalse(Objects.requireNonNull(compactCommands.commandSurfaces()).isEmpty());
    assertNull(compactCommands.fullCommands());
    assertTrue(
        compactCommands.commandSurfaces().stream()
            .allMatch(
                commandSurface ->
                    OperationCategory.QUERY.wireValue().equals(commandSurface.category())));
    assertTrue(compact.nextHints().get(1).contains("--detail compact"));

    assertEquals(DiscoveryFocus.COMMANDS, full.focus());
    assertNull(full.category());
    CliDiscoveryCommonJsonModels.CapabilitiesCommandsSlicePayload fullCommands =
        assertInstanceOf(
            CliDiscoveryCommonJsonModels.CapabilitiesCommandsSlicePayload.class, full.data());
    assertEquals(
        capabilitiesDescriptor.commands().allCommands().size(), fullCommands.commands().size());
    assertNull(fullCommands.commandSurfaces());
    assertFalse(Objects.requireNonNull(fullCommands.fullCommands()).isEmpty());

    CliDiscoveryCommonJsonModels.CapabilitiesCommandsSlicePayload minimalWithoutCategory =
        assertInstanceOf(
            CliDiscoveryCommonJsonModels.CapabilitiesCommandsSlicePayload.class,
            assertInstanceOf(
                    CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload.class,
                    CliDiscoveryPayloadMapper.capabilitiesPayload(
                        capabilitiesDescriptor,
                        DiscoveryDetail.MINIMAL,
                        new CliDiscoverySelections(DiscoveryFocus.COMMANDS, null)))
                .data());
    CliDiscoveryCommonJsonModels.CapabilitiesCommandsSlicePayload compactWithoutCategory =
        assertInstanceOf(
            CliDiscoveryCommonJsonModels.CapabilitiesCommandsSlicePayload.class,
            assertInstanceOf(
                    CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload.class,
                    CliDiscoveryPayloadMapper.capabilitiesPayload(
                        capabilitiesDescriptor,
                        DiscoveryDetail.COMPACT,
                        new CliDiscoverySelections(DiscoveryFocus.COMMANDS, null)))
                .data());
    CliDiscoveryCommonJsonModels.CapabilitiesCommandsSlicePayload fullWithCategory =
        assertInstanceOf(
            CliDiscoveryCommonJsonModels.CapabilitiesCommandsSlicePayload.class,
            assertInstanceOf(
                    CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload.class,
                    CliDiscoveryPayloadMapper.capabilitiesPayload(
                        capabilitiesDescriptor,
                        DiscoveryDetail.FULL,
                        new CliDiscoverySelections(
                            DiscoveryFocus.COMMANDS, OperationCategory.WRITE)))
                .data());

    assertNull(minimalWithoutCategory.category());
    assertNull(compactWithoutCategory.category());
    assertEquals(OperationCategory.WRITE.wireValue(), fullWithCategory.category());

    List<OperationCategory> categories = List.of(OperationCategory.values());
    List<CliDiscoverySelections> selections =
        categories.stream()
            .map(category -> new CliDiscoverySelections(DiscoveryFocus.COMMANDS, category))
            .toList();
    for (int index = 0; index < categories.size(); index++) {
      OperationCategory category = categories.get(index);
      CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload filtered =
          assertInstanceOf(
              CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload.class,
              CliDiscoveryPayloadMapper.capabilitiesPayload(
                  capabilitiesDescriptor, DiscoveryDetail.MINIMAL, selections.get(index)));
      CliDiscoveryCommonJsonModels.CapabilitiesCommandsSlicePayload filteredCommands =
          assertInstanceOf(
              CliDiscoveryCommonJsonModels.CapabilitiesCommandsSlicePayload.class, filtered.data());
      assertTrue(
          filteredCommands.commands().stream()
              .allMatch(command -> category.wireValue().equals(command.category())));
    }
  }

  @Test
  void capabilitiesPayload_mapsFocusedStorageRequestInputCurrencyKernelAndResponseSlices() {
    CapabilitiesDescriptor capabilitiesDescriptor = MachineContract.capabilities(identity());

    CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload storage =
        assertInstanceOf(
            CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload.class,
            CliDiscoveryPayloadMapper.capabilitiesPayload(
                capabilitiesDescriptor,
                DiscoveryDetail.MINIMAL,
                new CliDiscoverySelections(DiscoveryFocus.STORAGE, null)));
    CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload requestInputCompact =
        assertInstanceOf(
            CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload.class,
            CliDiscoveryPayloadMapper.capabilitiesPayload(
                capabilitiesDescriptor,
                DiscoveryDetail.COMPACT,
                new CliDiscoverySelections(DiscoveryFocus.REQUEST_INPUT, null)));
    CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload requestInputFull =
        assertInstanceOf(
            CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload.class,
            CliDiscoveryPayloadMapper.capabilitiesPayload(
                capabilitiesDescriptor,
                DiscoveryDetail.FULL,
                new CliDiscoverySelections(DiscoveryFocus.REQUEST_INPUT, null)));
    CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload currency =
        assertInstanceOf(
            CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload.class,
            CliDiscoveryPayloadMapper.capabilitiesPayload(
                capabilitiesDescriptor,
                DiscoveryDetail.MINIMAL,
                new CliDiscoverySelections(DiscoveryFocus.CURRENCY_MODEL, null)));
    CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload kernel =
        assertInstanceOf(
            CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload.class,
            CliDiscoveryPayloadMapper.capabilitiesPayload(
                capabilitiesDescriptor,
                DiscoveryDetail.MINIMAL,
                new CliDiscoverySelections(DiscoveryFocus.BOOKKEEPING_KERNEL, null)));
    CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload response =
        assertInstanceOf(
            CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload.class,
            CliDiscoveryPayloadMapper.capabilitiesPayload(
                capabilitiesDescriptor,
                DiscoveryDetail.MINIMAL,
                new CliDiscoverySelections(DiscoveryFocus.RESPONSE_CONTRACT, null)));

    assertInstanceOf(
        CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesStorageSlicePayload.class,
        storage.data());
    assertTrue(storage.nextHints().getFirst().contains("environment --output json"));

    CliDiscoveryRequestInputSliceJsonModels.CapabilitiesRequestInputSlicePayload
        compactRequestInput =
            assertInstanceOf(
                CliDiscoveryRequestInputSliceJsonModels.CapabilitiesRequestInputSlicePayload.class,
                requestInputCompact.data());
    assertNull(compactRequestInput.fullRequestInput());
    assertTrue(requestInputCompact.nextHints().getFirst().contains("print-request-template"));

    CliDiscoveryRequestInputSliceJsonModels.CapabilitiesRequestInputSlicePayload fullRequestInput =
        assertInstanceOf(
            CliDiscoveryRequestInputSliceJsonModels.CapabilitiesRequestInputSlicePayload.class,
            requestInputFull.data());
    assertNotNull(fullRequestInput.fullRequestInput());

    assertInstanceOf(
        CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesCurrencySlicePayload.class,
        currency.data());
    assertTrue(currency.nextHints().getFirst().contains("--detail full"));
    assertInstanceOf(
        CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesKernelSlicePayload.class,
        kernel.data());
    assertTrue(kernel.nextHints().getFirst().contains("--detail full"));
    assertInstanceOf(
        CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesResponseContractSummaryPayload.class,
        response.data());
    assertTrue(response.nextHints().getFirst().contains("--detail full"));
  }

  @Test
  void capabilitiesPayload_mapsResponseContractSliceAcrossDiscoveryDetails() {
    CapabilitiesDescriptor capabilitiesDescriptor = MachineContract.capabilities(identity());

    CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload minimal =
        assertInstanceOf(
            CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload.class,
            CliDiscoveryPayloadMapper.capabilitiesPayload(
                capabilitiesDescriptor,
                DiscoveryDetail.MINIMAL,
                new CliDiscoverySelections(DiscoveryFocus.RESPONSE_CONTRACT, null)));
    CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload compact =
        assertInstanceOf(
            CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload.class,
            CliDiscoveryPayloadMapper.capabilitiesPayload(
                capabilitiesDescriptor,
                DiscoveryDetail.COMPACT,
                new CliDiscoverySelections(DiscoveryFocus.RESPONSE_CONTRACT, null)));
    CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload full =
        assertInstanceOf(
            CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload.class,
            CliDiscoveryPayloadMapper.capabilitiesPayload(
                capabilitiesDescriptor,
                DiscoveryDetail.FULL,
                new CliDiscoverySelections(DiscoveryFocus.RESPONSE_CONTRACT, null)));

    assertEquals(DiscoveryDetail.MINIMAL, minimal.detail());
    assertInstanceOf(
        CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesResponseContractSummaryPayload.class,
        minimal.data());
    assertTrue(minimal.nextHints().getFirst().contains("--detail full"));

    assertEquals(DiscoveryDetail.COMPACT, compact.detail());
    assertInstanceOf(
        CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesResponseContractCompactPayload.class,
        compact.data());
    assertTrue(compact.nextHints().getFirst().contains("--detail full"));

    assertEquals(DiscoveryDetail.FULL, full.detail());
    assertInstanceOf(
        CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesResponseContractSlicePayload.class,
        full.data());
    assertTrue(full.nextHints().getFirst().contains("exhaustive descriptor surface"));
  }

  @Test
  void capabilitiesPayload_mapsRawJsonExecutionModeInCompactCommandSurface() {
    CapabilitiesDescriptor canonical = MachineContract.capabilities(identity());
    CapabilitiesDescriptor customized =
        new CapabilitiesDescriptor(
            canonical.application(),
            canonical.version(),
            canonical.protocolVersion(),
            canonical.storage(),
            new CommandCatalogDescriptor(
                List.of(
                    new CommandDescriptor(
                        OperationId.PRINT_PLAN_TEMPLATE,
                        List.of(),
                        List.of(),
                        ExecutionMode.RAW_JSON,
                        List.of(),
                        List.of(),
                        "Emit one ledger plan template")),
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
            canonical.bookkeepingKernel(),
            canonical.capabilityCatalog());

    CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload payload =
        assertInstanceOf(
            CliDiscoveryCapabilitiesSliceJsonModels.CapabilitiesSlicePayload.class,
            CliDiscoveryPayloadMapper.capabilitiesPayload(
                customized,
                DiscoveryDetail.COMPACT,
                new CliDiscoverySelections(DiscoveryFocus.COMMANDS, OperationCategory.DISCOVERY)));
    CliDiscoveryCommonJsonModels.CapabilitiesCommandsSlicePayload commandSlice =
        assertInstanceOf(
            CliDiscoveryCommonJsonModels.CapabilitiesCommandsSlicePayload.class, payload.data());

    assertEquals(
        "raw-json",
        Objects.requireNonNull(commandSlice.commandSurfaces()).getFirst().executionMode());
  }

  @Test
  void helpPayload_filtersTopLevelOverviewByCategoryAcrossDetails() {
    HelpDescriptor helpDescriptor = MachineContract.help(identity(), environment());

    CliDiscoveryHelpJsonModels.HelpOverviewMinimalPayload minimal =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.HelpOverviewMinimalPayload.class,
            CliDiscoveryPayloadMapper.helpPayload(
                helpDescriptor, DiscoveryDetail.MINIMAL, OperationCategory.QUERY));
    CliDiscoveryHelpJsonModels.HelpOverviewCompactPayload compact =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.HelpOverviewCompactPayload.class,
            CliDiscoveryPayloadMapper.helpPayload(
                helpDescriptor, DiscoveryDetail.COMPACT, OperationCategory.QUERY));
    CliDiscoveryHelpJsonModels.HelpOverviewPayload full =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.HelpOverviewPayload.class,
            CliDiscoveryPayloadMapper.helpPayload(
                helpDescriptor, DiscoveryDetail.FULL, OperationCategory.QUERY));

    assertEquals(OperationCategory.QUERY.wireValue(), minimal.category());
    assertFalse(minimal.commands().isEmpty());
    assertTrue(
        minimal.commands().stream()
            .allMatch(command -> OperationCategory.QUERY.wireValue().equals(command.category())));
    assertEquals(OperationCategory.QUERY.wireValue(), compact.category());
    assertFalse(compact.commands().isEmpty());
    assertTrue(
        compact.commands().stream()
            .allMatch(command -> OperationCategory.QUERY.wireValue().equals(command.category())));
    assertEquals(OperationCategory.QUERY.wireValue(), full.category());
    assertFalse(full.commands().isEmpty());
    assertTrue(
        full.commands().stream()
            .allMatch(
                command ->
                    ProtocolCatalog.operation(command.name()).category()
                        == OperationCategory.QUERY));
  }

  @Test
  void helpPayload_ignoresCategoryWhenHelpIsAlreadyCommandScoped() {
    HelpDescriptor commandScoped =
        MachineContract.help(identity(), environment(), OperationId.POST_ENTRY);

    CliDiscoveryHelpJsonModels.CommandHelpPayload payload =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.CommandHelpPayload.class,
            CliDiscoveryPayloadMapper.helpPayload(
                commandScoped, DiscoveryDetail.COMPACT, OperationCategory.ADMINISTRATION));

    assertEquals(OperationId.POST_ENTRY, payload.command().name());
    assertEquals(ProtocolCatalog.operation(OperationId.POST_ENTRY).usage(), payload.syntax());
  }

  @Test
  void helpPayload_mapsCompactPostingRequestGuidanceWithoutFullArtifacts() {
    CliDiscoveryHelpJsonModels.CommandHelpPayload payload =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.CommandHelpPayload.class,
            compactHelpPayload(
                MachineContract.help(identity(), environment(), OperationId.POST_ENTRY)));

    assertNotNull(payload.requestFile());
    assertNull(payload.requestFile().postingTemplate());
    assertNull(payload.requestFile().requestShapes());
    assertEquals(ProtocolCatalog.operation(OperationId.POST_ENTRY).usage(), payload.syntax());
    assertEquals(
        CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
            + " "
            + OperationId.POST_ENTRY.wireName(),
        payload.requestFile().shortcutCommand());
  }

  @Test
  void helpPayload_treatsSingleCommandWithQuickStartAsOverviewPayload() {
    HelpDescriptor canonical = MachineContract.help(identity(), environment(), OperationId.HELP);

    Object payload =
        compactHelpPayload(
            new HelpDescriptor(
                canonical.application(),
                canonical.version(),
                canonical.protocolVersion(),
                canonical.description(),
                canonical.usage(),
                canonical.bookModel(),
                canonical.bookkeepingKernel(),
                canonical.requestShapes(),
                canonical.requestTemplate(),
                canonical.declareAccountTemplate(),
                canonical.declareTaxRegistrationTemplate(),
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

    assertInstanceOf(CliDiscoveryHelpJsonModels.HelpOverviewCompactPayload.class, payload);
  }

  @Test
  void helpPayload_mapsNonRequestFileCommandWithoutRequestGuidance() {
    CliDiscoveryHelpJsonModels.CommandHelpPayload payload =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.CommandHelpPayload.class,
            compactHelpPayload(
                MachineContract.help(identity(), environment(), OperationId.VERSION)));

    assertNull(payload.requestFile());
    assertEquals(ProtocolCatalog.operation(OperationId.VERSION).usage(), payload.syntax());
  }

  @Test
  void helpPayload_omitsRequestGuidanceWhenArtifactsAreMissing() {
    HelpDescriptor postEntry =
        MachineContract.help(identity(), environment(), OperationId.POST_ENTRY);
    HelpDescriptor declareAccount =
        MachineContract.help(identity(), environment(), OperationId.DECLARE_ACCOUNT);
    HelpDescriptor executePlan =
        MachineContract.help(identity(), environment(), OperationId.EXECUTE_PLAN);

    CliDiscoveryHelpJsonModels.CommandHelpPayload postEntryPayload =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.CommandHelpPayload.class,
            compactHelpPayload(
                new HelpDescriptor(
                    postEntry.application(),
                    postEntry.version(),
                    postEntry.protocolVersion(),
                    postEntry.description(),
                    postEntry.usage(),
                    postEntry.bookModel(),
                    postEntry.bookkeepingKernel(),
                    null,
                    postEntry.requestTemplate(),
                    postEntry.declareAccountTemplate(),
                    postEntry.declareTaxRegistrationTemplate(),
                    postEntry.planTemplate(),
                    postEntry.commands(),
                    postEntry.quickStart(),
                    postEntry.exitCodes(),
                    postEntry.preflight(),
                    postEntry.currencyModel())));
    CliDiscoveryHelpJsonModels.CommandHelpPayload declarePayload =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.CommandHelpPayload.class,
            compactHelpPayload(
                new HelpDescriptor(
                    declareAccount.application(),
                    declareAccount.version(),
                    declareAccount.protocolVersion(),
                    declareAccount.description(),
                    declareAccount.usage(),
                    declareAccount.bookModel(),
                    declareAccount.bookkeepingKernel(),
                    declareAccount.requestShapes(),
                    declareAccount.requestTemplate(),
                    null,
                    declareAccount.declareTaxRegistrationTemplate(),
                    declareAccount.planTemplate(),
                    declareAccount.commands(),
                    declareAccount.quickStart(),
                    declareAccount.exitCodes(),
                    declareAccount.preflight(),
                    declareAccount.currencyModel())));
    CliDiscoveryHelpJsonModels.CommandHelpPayload planPayload =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.CommandHelpPayload.class,
            compactHelpPayload(
                new HelpDescriptor(
                    executePlan.application(),
                    executePlan.version(),
                    executePlan.protocolVersion(),
                    executePlan.description(),
                    executePlan.usage(),
                    executePlan.bookModel(),
                    executePlan.bookkeepingKernel(),
                    executePlan.requestShapes(),
                    executePlan.requestTemplate(),
                    executePlan.declareAccountTemplate(),
                    executePlan.declareTaxRegistrationTemplate(),
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

    CliDiscoveryHelpJsonModels.CommandHelpPayload payload =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.CommandHelpPayload.class,
            compactHelpPayload(
                new HelpDescriptor(
                    postEntry.application(),
                    postEntry.version(),
                    postEntry.protocolVersion(),
                    postEntry.description(),
                    postEntry.usage(),
                    postEntry.bookModel(),
                    postEntry.bookkeepingKernel(),
                    postEntry.requestShapes(),
                    null,
                    postEntry.declareAccountTemplate(),
                    postEntry.declareTaxRegistrationTemplate(),
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

    CliDiscoveryHelpJsonModels.CommandHelpPayload postEntryPayload =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.CommandHelpPayload.class,
            compactHelpPayload(
                new HelpDescriptor(
                    postEntry.application(),
                    postEntry.version(),
                    postEntry.protocolVersion(),
                    postEntry.description(),
                    postEntry.usage(),
                    postEntry.bookModel(),
                    postEntry.bookkeepingKernel(),
                    null,
                    postEntry.requestTemplate(),
                    postEntry.declareAccountTemplate(),
                    postEntry.declareTaxRegistrationTemplate(),
                    postEntry.planTemplate(),
                    postEntry.commands(),
                    postEntry.quickStart(),
                    postEntry.exitCodes(),
                    postEntry.preflight(),
                    postEntry.currencyModel())));
    CliDiscoveryHelpJsonModels.CommandHelpPayload declarePayload =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.CommandHelpPayload.class,
            compactHelpPayload(
                new HelpDescriptor(
                    declareAccount.application(),
                    declareAccount.version(),
                    declareAccount.protocolVersion(),
                    declareAccount.description(),
                    declareAccount.usage(),
                    declareAccount.bookModel(),
                    declareAccount.bookkeepingKernel(),
                    null,
                    declareAccount.requestTemplate(),
                    declareAccount.declareAccountTemplate(),
                    declareAccount.declareTaxRegistrationTemplate(),
                    declareAccount.planTemplate(),
                    declareAccount.commands(),
                    declareAccount.quickStart(),
                    declareAccount.exitCodes(),
                    declareAccount.preflight(),
                    declareAccount.currencyModel())));
    CliDiscoveryHelpJsonModels.CommandHelpPayload planPayload =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.CommandHelpPayload.class,
            compactHelpPayload(
                new HelpDescriptor(
                    executePlan.application(),
                    executePlan.version(),
                    executePlan.protocolVersion(),
                    executePlan.description(),
                    executePlan.usage(),
                    executePlan.bookModel(),
                    executePlan.bookkeepingKernel(),
                    null,
                    executePlan.requestTemplate(),
                    executePlan.declareAccountTemplate(),
                    executePlan.declareTaxRegistrationTemplate(),
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

    CliDiscoveryHelpJsonModels.CommandHelpPayload postEntryPayload =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.CommandHelpPayload.class,
            compactHelpPayload(
                new HelpDescriptor(
                    postEntry.application(),
                    postEntry.version(),
                    postEntry.protocolVersion(),
                    postEntry.description(),
                    postEntry.usage(),
                    postEntry.bookModel(),
                    postEntry.bookkeepingKernel(),
                    new dev.erst.fingrind.contract.discovery.ContractRequestShapes
                        .RequestShapesDescriptor(
                        Objects.requireNonNull(postEntry.requestShapes()).schemaDialect(),
                        null,
                        postEntry.requestShapes().declareAccount(),
                        postEntry.requestShapes().retireAccount(),
                        postEntry.requestShapes().declareTaxRegistration(),
                        postEntry.requestShapes().ledgerPlan()),
                    postEntry.requestTemplate(),
                    postEntry.declareAccountTemplate(),
                    postEntry.declareTaxRegistrationTemplate(),
                    postEntry.planTemplate(),
                    postEntry.commands(),
                    postEntry.quickStart(),
                    postEntry.exitCodes(),
                    postEntry.preflight(),
                    postEntry.currencyModel())));
    CliDiscoveryHelpJsonModels.CommandHelpPayload declarePayload =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.CommandHelpPayload.class,
            compactHelpPayload(
                new HelpDescriptor(
                    declareAccount.application(),
                    declareAccount.version(),
                    declareAccount.protocolVersion(),
                    declareAccount.description(),
                    declareAccount.usage(),
                    declareAccount.bookModel(),
                    declareAccount.bookkeepingKernel(),
                    new dev.erst.fingrind.contract.discovery.ContractRequestShapes
                        .RequestShapesDescriptor(
                        Objects.requireNonNull(declareAccount.requestShapes()).schemaDialect(),
                        declareAccount.requestShapes().bookkeepingEntry(),
                        null,
                        declareAccount.requestShapes().retireAccount(),
                        declareAccount.requestShapes().declareTaxRegistration(),
                        declareAccount.requestShapes().ledgerPlan()),
                    declareAccount.requestTemplate(),
                    declareAccount.declareAccountTemplate(),
                    declareAccount.declareTaxRegistrationTemplate(),
                    declareAccount.planTemplate(),
                    declareAccount.commands(),
                    declareAccount.quickStart(),
                    declareAccount.exitCodes(),
                    declareAccount.preflight(),
                    declareAccount.currencyModel())));
    CliDiscoveryHelpJsonModels.CommandHelpPayload planPayload =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.CommandHelpPayload.class,
            compactHelpPayload(
                new HelpDescriptor(
                    executePlan.application(),
                    executePlan.version(),
                    executePlan.protocolVersion(),
                    executePlan.description(),
                    executePlan.usage(),
                    executePlan.bookModel(),
                    executePlan.bookkeepingKernel(),
                    new dev.erst.fingrind.contract.discovery.ContractRequestShapes
                        .RequestShapesDescriptor(
                        Objects.requireNonNull(executePlan.requestShapes()).schemaDialect(),
                        executePlan.requestShapes().bookkeepingEntry(),
                        executePlan.requestShapes().declareAccount(),
                        executePlan.requestShapes().retireAccount(),
                        executePlan.requestShapes().declareTaxRegistration(),
                        null),
                    executePlan.requestTemplate(),
                    executePlan.declareAccountTemplate(),
                    executePlan.declareTaxRegistrationTemplate(),
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
                new CliDiscoveryCommonJsonModels.RequestFileGuidancePayload(
                    "desc", DiscoveryDetail.COMPACT, null, null, null, null, null, null));

    assertTrue(
        Objects.requireNonNull(failure.getMessage())
            .contains("At least one request-file guidance artifact"));
  }

  @Test
  void requestFileGuidancePayload_allowsSingleTemplateArtifactWithoutRequestShapes() {
    CliDiscoveryCommonJsonModels.RequestFileGuidancePayload payload =
        new CliDiscoveryCommonJsonModels.RequestFileGuidancePayload(
            "Provide a posting JSON document through --request-file <path|->.",
            DiscoveryDetail.COMPACT,
            MachineContract.requestTemplate(),
            null,
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
    CliDiscoveryCommonJsonModels.RequestFileGuidancePayload payload =
        new CliDiscoveryCommonJsonModels.RequestFileGuidancePayload(
            "Provide a posting JSON document through --request-file <path|->.",
            DiscoveryDetail.COMPACT,
            null,
            null,
            null,
            null,
            new dev.erst.fingrind.contract.discovery.ContractRequestShapes.RequestShapesDescriptor(
                requestShapes.schemaDialect(),
                requestShapes.bookkeepingEntry(),
                null,
                requestShapes.retireAccount(),
                requestShapes.declareTaxRegistration(),
                null),
            null);

    assertNull(payload.postingTemplate());
    assertNotNull(payload.requestShapes());
  }

  static Object compactHelpPayload(HelpDescriptor helpDescriptor) {
    return CliDiscoveryPayloadMapper.helpPayload(helpDescriptor, DiscoveryDetail.COMPACT, null);
  }

  private static Object minimalHelpPayload(HelpDescriptor helpDescriptor) {
    return CliDiscoveryPayloadMapper.helpPayload(helpDescriptor, DiscoveryDetail.MINIMAL, null);
  }

  static Object fullHelpPayload(HelpDescriptor helpDescriptor) {
    return CliDiscoveryPayloadMapper.helpPayload(helpDescriptor, DiscoveryDetail.FULL, null);
  }

  private static CliDiscoverySelections overviewSelections() {
    return CliDiscoverySelections.overview();
  }

  static ApplicationIdentity identity() {
    return new ApplicationIdentity(
        "FinGrind",
        "0.57.0",
        "Command-line double-entry bookkeeping with one protected book per accounting entity");
  }

  static EnvironmentDescriptor environment() {
    return environmentDescriptor(
        RuntimeDistribution.SELF_CONTAINED_BUNDLE.wireValue(),
        SqliteCompileOptionsVerificationStatus.VERIFIED,
        "ready",
        ProtocolCatalog.managedSqlite().requiredMinimumSqliteVersion(),
        ProtocolCatalog.managedSqlite().requiredSqlite3mcVersion(),
        null);
  }
}
