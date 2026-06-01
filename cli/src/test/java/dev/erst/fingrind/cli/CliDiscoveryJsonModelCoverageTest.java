package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.cli.json.CliDiscoveryJsonModels;
import dev.erst.fingrind.contract.discovery.ApplicationIdentity;
import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.DiscoveryFocus;
import dev.erst.fingrind.contract.protocol.OperationCategory;
import dev.erst.fingrind.contract.protocol.OperationId;
import org.junit.jupiter.api.Test;

/**
 * Covers compact discovery JSON model invariants and the default request-template command model.
 */
class CliDiscoveryJsonModelCoverageTest {
  @Test
  void compactDiscoveryPayloads_acceptCompactDetailAndRejectOtherModes() {
    CapabilitiesDescriptor capabilitiesDescriptor = MachineContract.capabilities(identity());
    CliDiscoveryJsonModels.RequestInputCompactPayload requestInput =
        new CliDiscoveryJsonModels.RequestInputCompactPayload(
            "--book-file",
            java.util.List.of("--book-key-file"),
            "--request-file",
            java.util.List.of("post-entry"),
            "-",
            "--output");
    CliDiscoveryJsonModels.HelpOverviewCompactPayload helpOverview =
        new CliDiscoveryJsonModels.HelpOverviewCompactPayload(
            "FinGrind",
            "0.50.0",
            "Compact help overview",
            DiscoveryDetail.COMPACT,
            null,
            java.util.List.of(
                new CliDiscoveryJsonModels.CommandIndexPayload(
                    dev.erst.fingrind.contract.protocol.OperationId.POST_ENTRY,
                    "mutation",
                    "Commit one entry.")),
            java.util.List.of(),
            "Run fingrind capabilities --output json --detail compact.",
            "Run fingrind help --output json --detail full.");
    CliDiscoveryJsonModels.CapabilitiesCompactPayload capabilities =
        new CliDiscoveryJsonModels.CapabilitiesCompactPayload(
            "FinGrind",
            "0.50.0",
            DiscoveryDetail.COMPACT,
            DiscoveryFocus.OVERVIEW,
            capabilitiesDescriptor.storage().bookBoundary(),
            capabilitiesDescriptor.storage().engines().stream().map(Enum::name).toList(),
            requestInput,
            java.util.List.of(new CliDiscoveryJsonModels.CommandCountPayload("write", 1)),
            "Run fingrind capabilities --output json --detail full.");
    CliDiscoveryJsonModels.CapabilitiesPayload compactPayload =
        new CliDiscoveryJsonModels.CapabilitiesPayload(
            "FinGrind",
            "0.50.0",
            DiscoveryDetail.COMPACT,
            DiscoveryFocus.OVERVIEW,
            capabilitiesDescriptor.storage(),
            capabilitiesDescriptor.commands(),
            capabilitiesDescriptor.requestInput(),
            java.util.List.of("Prefer --output json for agents."),
            null);

    assertEquals(DiscoveryDetail.COMPACT, helpOverview.detail());
    assertEquals(DiscoveryDetail.COMPACT, capabilities.detail());
    assertEquals(DiscoveryDetail.COMPACT, compactPayload.detail());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliDiscoveryJsonModels.HelpOverviewCompactPayload(
                "FinGrind",
                "0.50.0",
                "Compact help overview",
                DiscoveryDetail.MINIMAL,
                null,
                java.util.List.of(),
                java.util.List.of(),
                "Run fingrind capabilities --output json --detail compact.",
                "Run fingrind help --output json --detail full."));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliDiscoveryJsonModels.CapabilitiesCompactPayload(
                "FinGrind",
                "0.50.0",
                DiscoveryDetail.FULL,
                DiscoveryFocus.OVERVIEW,
                capabilitiesDescriptor.storage().bookBoundary(),
                capabilitiesDescriptor.storage().engines().stream().map(Enum::name).toList(),
                requestInput,
                java.util.List.of(new CliDiscoveryJsonModels.CommandCountPayload("write", 1)),
                "Run fingrind capabilities --output json --detail full."));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliDiscoveryJsonModels.CapabilitiesCompactPayload(
                "FinGrind",
                "0.50.0",
                DiscoveryDetail.COMPACT,
                DiscoveryFocus.COMMANDS,
                capabilitiesDescriptor.storage().bookBoundary(),
                capabilitiesDescriptor.storage().engines().stream().map(Enum::name).toList(),
                requestInput,
                java.util.List.of(new CliDiscoveryJsonModels.CommandCountPayload("write", 1)),
                "Run fingrind capabilities --output json --detail full."));
  }

  @Test
  void discoveryJsonModels_coverFocusedSliceAndOverviewInvariants() {
    CapabilitiesDescriptor capabilitiesDescriptor = MachineContract.capabilities(identity());
    CliDiscoveryJsonModels.RequestInputCompactPayload requestInput =
        new CliDiscoveryJsonModels.RequestInputCompactPayload(
            "--book-file",
            java.util.List.of("--book-key-file"),
            "--request-file",
            java.util.List.of("post-entry"),
            "-",
            "--output");
    CliDiscoveryJsonModels.CapabilitiesSlicePayload slicePayload =
        new CliDiscoveryJsonModels.CapabilitiesSlicePayload(
            "FinGrind",
            "0.50.0",
            DiscoveryDetail.MINIMAL,
            DiscoveryFocus.REQUEST_INPUT,
            null,
            new CliDiscoveryJsonModels.CapabilitiesRequestInputSlicePayload(requestInput, null),
            java.util.List.of("next"));
    CliDiscoveryJsonModels.CapabilitiesCommandsSlicePayload commandsSlice =
        new CliDiscoveryJsonModels.CapabilitiesCommandsSlicePayload(
            OperationCategory.WRITE.wireValue(),
            java.util.List.of(
                new CliDiscoveryJsonModels.CommandNamePayload(
                    OperationId.POST_ENTRY, OperationCategory.WRITE.wireValue())),
            java.util.List.of(
                new CliDiscoveryJsonModels.CommandSurfacePayload(
                    OperationId.POST_ENTRY,
                    OperationCategory.WRITE.wireValue(),
                    "Commit one posting request.",
                    java.util.List.of(),
                    java.util.List.of("--request-file <path|->"),
                    "json-envelope",
                    java.util.List.of("json", "text"),
                    null,
                    java.util.List.of("pdf via --pdf-out"),
                    true)),
            java.util.List.of(firstCommand(capabilitiesDescriptor)));
    CliDiscoveryJsonModels.CapabilitiesResponseContractSlicePayload responseSlice =
        new CliDiscoveryJsonModels.CapabilitiesResponseContractSlicePayload(
            capabilitiesDescriptor.responseModel(),
            capabilitiesDescriptor.planExecution(),
            capabilitiesDescriptor.audit(),
            capabilitiesDescriptor.accountRegistry(),
            capabilitiesDescriptor.reversals(),
            capabilitiesDescriptor.preflight());

    assertEquals(DiscoveryFocus.REQUEST_INPUT, slicePayload.focus());
    assertNotNull(commandsSlice.commandSurfaces());
    assertNotNull(commandsSlice.fullCommands());
    assertNotNull(responseSlice.preflight());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliDiscoveryJsonModels.CapabilitiesMinimalPayload(
                "FinGrind",
                "0.50.0",
                DiscoveryDetail.MINIMAL,
                DiscoveryFocus.COMMANDS,
                "scope",
                java.util.List.of("trial-balance"),
                "single-sqlite-file",
                "book-functional-currency",
                "not-supported",
                requestInput,
                "compact",
                "full"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliDiscoveryJsonModels.CapabilitiesPayload(
                "FinGrind",
                "0.50.0",
                DiscoveryDetail.FULL,
                DiscoveryFocus.COMMANDS,
                capabilitiesDescriptor.storage(),
                capabilitiesDescriptor.commands(),
                capabilitiesDescriptor.requestInput(),
                java.util.List.of("guidance"),
                capabilitiesDescriptor));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliDiscoveryJsonModels.CapabilitiesSlicePayload(
                "FinGrind",
                "0.50.0",
                DiscoveryDetail.MINIMAL,
                DiscoveryFocus.OVERVIEW,
                null,
                new CliDiscoveryJsonModels.CapabilitiesStorageSlicePayload(
                    capabilitiesDescriptor.storage()),
                java.util.List.of("next")));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliDiscoveryJsonModels.CommandCountPayload("query", -1));
  }

  @Test
  void discoveryJsonModels_coverCommandHelpAndCompactRequestInputRecords() {
    CapabilitiesDescriptor capabilitiesDescriptor = MachineContract.capabilities(identity());
    CliDiscoveryJsonModels.CommandSurfacePayload commandSurface =
        new CliDiscoveryJsonModels.CommandSurfacePayload(
            OperationId.HELP,
            OperationCategory.DISCOVERY.wireValue(),
            "Show command and workflow guidance.",
            java.util.List.of("-h"),
            java.util.List.of("--output <json|text>"),
            "json-envelope",
            java.util.List.of("json", "text"),
            null,
            java.util.List.of(),
            false);
    CliDiscoveryJsonModels.CapabilitiesRequestInputSlicePayload requestInputSlice =
        new CliDiscoveryJsonModels.CapabilitiesRequestInputSlicePayload(
            new CliDiscoveryJsonModels.RequestInputCompactPayload(
                "--book-file",
                java.util.List.of("--book-key-file"),
                "--request-file",
                java.util.List.of("post-entry"),
                "-",
                "--output"),
            capabilitiesDescriptor.requestInput());

    assertEquals(OperationId.HELP, commandSurface.name());
    assertNotNull(requestInputSlice.fullRequestInput());
  }

  @Test
  void printRequestTemplate_defaultConstructorLeavesTopicUnsetAndKeepsJsonFailureMode() {
    PrintRequestTemplate command = new PrintRequestTemplate();

    assertEquals(null, command.commandTopic());
    assertEquals(dev.erst.fingrind.contract.protocol.OutputMode.JSON, command.failureOutputMode());
  }

  private static ApplicationIdentity identity() {
    return new ApplicationIdentity(
        "FinGrind",
        "0.50.0",
        "Command-line double-entry bookkeeping with one protected book per accounting entity");
  }

  private static CommandDescriptor firstCommand(CapabilitiesDescriptor capabilitiesDescriptor) {
    return capabilitiesDescriptor.commands().allCommands().getFirst();
  }
}
