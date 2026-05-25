package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.cli.json.CliDiscoveryJsonModels;
import dev.erst.fingrind.contract.discovery.ApplicationIdentity;
import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
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
    CliDiscoveryJsonModels.CommandSurfacePayload commandSurface =
        new CliDiscoveryJsonModels.CommandSurfacePayload(
            dev.erst.fingrind.contract.protocol.OperationId.POST_ENTRY,
            "mutation",
            "Commit one entry.",
            java.util.List.of(),
            java.util.List.of("--book-file", "--request-file"),
            "single-command",
            java.util.List.of("text", "json"),
            null,
            java.util.List.of(),
            true);

    CliDiscoveryJsonModels.HelpOverviewCompactPayload helpOverview =
        new CliDiscoveryJsonModels.HelpOverviewCompactPayload(
            "FinGrind",
            "0.46.0",
            "Compact help overview",
            DiscoveryDetail.COMPACT,
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
            "0.46.0",
            DiscoveryDetail.COMPACT,
            capabilitiesDescriptor.storage().bookBoundary(),
            capabilitiesDescriptor.storage().engines().stream().map(Enum::name).toList(),
            requestInput,
            java.util.List.of(commandSurface),
            "Run fingrind capabilities --output json --detail full.");
    CliDiscoveryJsonModels.CapabilitiesPayload compactPayload =
        new CliDiscoveryJsonModels.CapabilitiesPayload(
            "FinGrind",
            "0.46.0",
            DiscoveryDetail.COMPACT,
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
                "0.46.0",
                "Compact help overview",
                DiscoveryDetail.MINIMAL,
                java.util.List.of(),
                java.util.List.of(),
                "Run fingrind capabilities --output json --detail compact.",
                "Run fingrind help --output json --detail full."));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliDiscoveryJsonModels.CapabilitiesCompactPayload(
                "FinGrind",
                "0.46.0",
                DiscoveryDetail.FULL,
                capabilitiesDescriptor.storage().bookBoundary(),
                capabilitiesDescriptor.storage().engines().stream().map(Enum::name).toList(),
                requestInput,
                java.util.List.of(commandSurface),
                "Run fingrind capabilities --output json --detail full."));
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
        "0.46.0",
        "Command-line double-entry bookkeeping with one protected book per accounting entity");
  }
}
