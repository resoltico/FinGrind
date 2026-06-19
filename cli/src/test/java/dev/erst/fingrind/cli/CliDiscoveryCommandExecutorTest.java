package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.OperationId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliDiscoveryCommandExecutor}. */
class CliDiscoveryCommandExecutorTest {
  @Test
  void writeRequestTemplate_withoutTopicWritesCanonicalPostingTemplate() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliDiscoveryCommandExecutor executor =
        new CliDiscoveryCommandExecutor(
            CliResponseWriterTestSupport.discoveryWriter(outputStream), metadata());

    int exitCode = executor.writeRequestTemplate();

    assertEquals(0, exitCode);
    assertEquals(
        CliWireJson.prettyJsonText(CliDiscoveryCommandExecutor.requestTemplateFor(null)),
        outputStream.toString(StandardCharsets.UTF_8).trim());
  }

  @Test
  void requestTemplateFor_supportsPostingAndDeclareAccountTopics() {
    String postingTemplate =
        CliWireJson.prettyJsonText(CliDiscoveryCommandExecutor.requestTemplateFor(null));
    String postEntryTemplate =
        CliWireJson.prettyJsonText(
            CliDiscoveryCommandExecutor.requestTemplateFor(OperationId.POST_ENTRY));
    String preflightTemplate =
        CliWireJson.prettyJsonText(
            CliDiscoveryCommandExecutor.requestTemplateFor(OperationId.PREFLIGHT_ENTRY));
    String declareAccountTemplate =
        CliWireJson.prettyJsonText(
            CliDiscoveryCommandExecutor.requestTemplateFor(OperationId.DECLARE_ACCOUNT));

    assertTrue(postingTemplate.contains("\"entryKind\""));
    assertTrue(postEntryTemplate.contains("\"entryKind\""));
    assertTrue(preflightTemplate.contains("\"entryKind\""));
    assertTrue(declareAccountTemplate.contains("\"accountCode\""));
    assertTrue(declareAccountTemplate.contains("\"accountNodeKind\""));
  }

  @Test
  void requestTemplateFor_rejectsUnsupportedTopics() {
    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () -> CliDiscoveryCommandExecutor.requestTemplateFor(OperationId.EXECUTE_PLAN));

    String message = Objects.requireNonNull(failure.getMessage());
    assertTrue(message.contains("Request templates are available only for"));
    assertTrue(message.contains(OperationId.POST_ENTRY.wireName()));
    assertTrue(message.contains(OperationId.PREFLIGHT_ENTRY.wireName()));
    assertTrue(message.contains(OperationId.DECLARE_ACCOUNT.wireName()));
  }

  private static CliMetadata metadata() {
    return new CliMetadata(
        new ByteArrayInputStream(
            """
            name=FinGrind
            version=0.57.0
            description=Command-line double-entry bookkeeping
            """
                .getBytes(StandardCharsets.UTF_8)));
  }
}
