package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.cli.json.CliDiscoveryCapabilitiesJsonModels;
import dev.erst.fingrind.cli.json.CliDiscoveryCommonJsonModels;
import dev.erst.fingrind.cli.json.CliDiscoveryHelpJsonModels;
import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.contract.discovery.ApplicationIdentity;
import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.DiscoveryFocus;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolEnvelopeStatus;
import dev.erst.fingrind.contract.protocol.RuntimeDistribution;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Validates machine-discovery payload parity with the canonical contract. */
class CliDiscoveryJsonModelValidationTest {
  @Test
  void discoveryPayloads_requireFullContractParity() {
    HelpDescriptor helpDescriptor = MachineContract.help(identity(), environment());
    CapabilitiesDescriptor capabilitiesDescriptor = MachineContract.capabilities(identity());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliDiscoveryHelpJsonModels.HelpOverviewPayload(
                "FinGrind",
                "0.57.0",
                MachineContract.protocolVersion(),
                "Discovery overview",
                DiscoveryDetail.FULL,
                null,
                List.of(),
                List.of(),
                List.of(),
                "Run fingrind capabilities --output json.",
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliDiscoveryHelpJsonModels.HelpOverviewPayload(
                "FinGrind",
                "0.57.0",
                MachineContract.protocolVersion(),
                "Discovery overview",
                DiscoveryDetail.COMPACT,
                null,
                List.of(),
                List.of(),
                List.of(),
                "Run fingrind capabilities --output json.",
                helpDescriptor));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliDiscoveryCapabilitiesJsonModels.CapabilitiesPayload(
                "FinGrind",
                "0.57.0",
                MachineContract.protocolVersion(),
                DiscoveryDetail.FULL,
                DiscoveryFocus.OVERVIEW,
                capabilitiesDescriptor.storage(),
                capabilitiesDescriptor.commands(),
                capabilitiesDescriptor.requestInput(),
                List.of("Prefer --output json for agents."),
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliDiscoveryCapabilitiesJsonModels.CapabilitiesPayload(
                "FinGrind",
                "0.57.0",
                MachineContract.protocolVersion(),
                DiscoveryDetail.COMPACT,
                DiscoveryFocus.OVERVIEW,
                capabilitiesDescriptor.storage(),
                capabilitiesDescriptor.commands(),
                capabilitiesDescriptor.requestInput(),
                List.of("Prefer --output json for agents."),
                capabilitiesDescriptor));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliDiscoveryHelpJsonModels.HelpOverviewMinimalPayload(
                "FinGrind",
                "0.57.0",
                MachineContract.protocolVersion(),
                "Discovery overview",
                DiscoveryDetail.COMPACT,
                null,
                List.of(),
                "Run fingrind help --output json --detail compact.",
                "Run fingrind help --output json --detail full."));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliDiscoveryCapabilitiesJsonModels.CapabilitiesMinimalPayload(
                "FinGrind",
                "0.57.0",
                MachineContract.protocolVersion(),
                DiscoveryDetail.FULL,
                DiscoveryFocus.OVERVIEW,
                capabilitiesDescriptor.bookkeepingKernel().scope(),
                capabilitiesDescriptor.bookkeepingKernel().builtInStatements(),
                capabilitiesDescriptor.storage().bookBoundary(),
                capabilitiesDescriptor.currencyModel().scope(),
                capabilitiesDescriptor.currencyModel().multiCurrencyStatus(),
                new CliDiscoveryCommonJsonModels.RequestInputCompactPayload(
                    "--book-file",
                    List.of(
                        "--book-key-file", "--book-passphrase-stdin", "--book-passphrase-prompt"),
                    "--request-file",
                    List.of("post-entry"),
                    "-",
                    "--output"),
                "Run fingrind capabilities --output json --detail compact.",
                "Run fingrind capabilities --output json --detail full."));

    CliEnvelopeJsonModels.Envelope<CliDiscoveryCapabilitiesJsonModels.CapabilitiesPayload>
        envelope =
            new CliEnvelopeJsonModels.Envelope<>(
                ProtocolEnvelopeStatus.OK,
                new CliDiscoveryCapabilitiesJsonModels.CapabilitiesPayload(
                    "FinGrind",
                    "0.57.0",
                    MachineContract.protocolVersion(),
                    DiscoveryDetail.FULL,
                    DiscoveryFocus.OVERVIEW,
                    capabilitiesDescriptor.storage(),
                    capabilitiesDescriptor.commands(),
                    capabilitiesDescriptor.requestInput(),
                    List.of("Prefer --output json for agents."),
                    capabilitiesDescriptor),
                null,
                null,
                null,
                null,
                null,
                null,
                new ArrayList<>(
                    List.of(
                        new CliEnvelopeJsonModels.SuccessArtifact(
                            "pdf", "/tmp/report.pdf", "/tmp/.fingrind-report.stage"))),
                null,
                null,
                null);
    List<CliEnvelopeJsonModels.SuccessArtifact> artifacts = envelope.artifacts();
    assertNotNull(artifacts);
    assertEquals(1, artifacts.size());
    assertThrows(
        UnsupportedOperationException.class,
        () ->
            artifacts.add(
                new CliEnvelopeJsonModels.SuccessArtifact(
                    "json", "/tmp/out.json", "/tmp/.fingrind-out.stage")));
  }

  private static ApplicationIdentity identity() {
    return new ApplicationIdentity(
        "FinGrind",
        "0.57.0",
        "Command-line double-entry bookkeeping with one protected book per accounting entity");
  }

  private static EnvironmentDescriptor environment() {
    return CliResponseWriterTestSupport.environmentDescriptor(
        RuntimeDistribution.SELF_CONTAINED_BUNDLE.wireValue(),
        dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus.VERIFIED,
        "ready",
        ProtocolCatalog.managedSqlite().requiredMinimumSqliteVersion(),
        ProtocolCatalog.managedSqlite().requiredSqlite3mcVersion(),
        null);
  }
}
