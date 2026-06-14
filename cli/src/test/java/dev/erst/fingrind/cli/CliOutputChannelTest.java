package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliDiscoveryHelpJsonModels;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
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
            "0.54.0",
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
}
