package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.OutputMode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Unit tests for {@link CliResponseWriter}. */
@NullUnmarked
class CliJsonResponseWriterTest extends CliResponseWriterTestSupport {

  @Test
  void writeJson_serializationFailureDoesNotEmitPartialOutput() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    SelfReferentialValue cyclic = new SelfReferentialValue();

    assertThrows(RuntimeException.class, () -> responseWriter.writeJson(cyclic));
    assertEquals("", outputStream.toString(StandardCharsets.UTF_8));
  }

  @Test
  void writeJson_writesStandaloneJsonPayload() throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeJson(Map.of("status", "ok", "count", 2));

    JsonNode json = readJson(outputStream);

    assertEquals("ok", json.path("status").asText());
    assertEquals(2, json.path("count").asInt());
  }

  @Test
  void writeFailure_supportsHumanOutput() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeFailure(
        new CliFailure("invalid-request", "Unsupported argument: --bogus", "Try help", "--bogus"),
        OutputMode.HUMAN);

    String text = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(text.contains("Error"));
    assertTrue(text.contains("invalid-request"));
    assertTrue(text.contains("Unsupported argument: --bogus"));
    assertTrue(text.contains("Try help"));
  }

  @Test
  void writeFailure_writesErrorEnvelope() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));

    responseWriter.writeFailure("invalid-request", "bad request");

    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"status\":\"error\""));
  }
}
