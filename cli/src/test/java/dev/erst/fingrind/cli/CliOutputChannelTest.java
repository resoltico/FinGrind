package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliDiscoveryHelpJsonModels;
import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.ProtocolEnvelopeStatus;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliOutputChannel}. */
class CliOutputChannelTest {
  @Test
  void writeSuccess_rendersCompactEnvelopeJson() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    CliOutputChannel outputChannel =
        new CliOutputChannel(new PrintStream(output, true, StandardCharsets.UTF_8));

    outputChannel.writeSuccess(
        new CliDiscoveryHelpJsonModels.HelpOverviewPayload(
            "FinGrind",
            "0.57.0",
            MachineContract.protocolVersion(),
            "CLI help",
            DiscoveryDetail.COMPACT,
            null,
            List.of(),
            List.of("help"),
            List.of(),
            "hint",
            null));

    String rendered = output.toString(StandardCharsets.UTF_8);
    assertTrue(rendered.startsWith("{\"status\":\"ok\",\"payload\":{"));
    assertTrue(rendered.contains("\"application\":\"FinGrind\""));
    assertFalse(rendered.contains("\n  \"status\""));
  }

  @Test
  void writeMutationRejection_routesMachineReadableEnvelopeToDiagnosticsStream() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
    CliOutputChannel outputChannel =
        new CliOutputChannel(
            new PrintStream(output, true, StandardCharsets.UTF_8),
            new PrintStream(diagnostics, true, StandardCharsets.UTF_8));

    outputChannel.writeMutationRejection(
        new CliEnvelopeJsonModels.Envelope<>(
            ProtocolEnvelopeStatus.REJECTED,
            null,
            "idempotency-key-conflict",
            "The book already contains one posting committed from a different committed posting request.",
            "Use one fresh idempotency key for each logical posting request.",
            null,
            null,
            null,
            null,
            null,
            null,
            null));

    assertEquals("", output.toString(StandardCharsets.UTF_8));
    String rendered = diagnostics.toString(StandardCharsets.UTF_8);
    assertTrue(rendered.contains("\"status\":\"rejected\""), rendered);
    assertTrue(rendered.contains("\"code\":\"idempotency-key-conflict\""), rendered);
  }

  @Test
  void writeDiagnosticEnvelope_preservesNonEnvelopeDiagnosticRecords() {
    ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
    CliOutputChannel outputChannel =
        new CliOutputChannel(
            new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
            new PrintStream(diagnostics, true, StandardCharsets.UTF_8));

    outputChannel.writeDiagnosticEnvelope(
        new CliFailure("invalid-request", "Unsupported argument.", null, "--bogus"));

    String rendered = diagnostics.toString(StandardCharsets.UTF_8);
    assertTrue(rendered.contains("\"code\":\"invalid-request\""), rendered);
    assertTrue(rendered.contains("\"argument\":\"--bogus\""), rendered);
  }
}
