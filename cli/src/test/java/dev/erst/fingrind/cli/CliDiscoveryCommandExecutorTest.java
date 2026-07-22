package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
        CliWireJson.prettyJsonText(CliDiscoveryCommandExecutor.requestTemplateFor(null, null)),
        outputStream.toString(StandardCharsets.UTF_8).trim());
  }

  @Test
  void requestTemplateFor_supportsPostingAndAdministrativeTopics() {
    String postingTemplate =
        CliWireJson.prettyJsonText(CliDiscoveryCommandExecutor.requestTemplateFor(null, null));
    String postEntryTemplate =
        CliWireJson.prettyJsonText(
            CliDiscoveryCommandExecutor.requestTemplateFor(OperationId.POST_ENTRY, null));
    String preflightTemplate =
        CliWireJson.prettyJsonText(
            CliDiscoveryCommandExecutor.requestTemplateFor(OperationId.PREFLIGHT_ENTRY, null));
    String declareAccountTemplate =
        CliWireJson.prettyJsonText(
            CliDiscoveryCommandExecutor.requestTemplateFor(OperationId.DECLARE_ACCOUNT, null));
    String amendAccountTemplate =
        CliWireJson.prettyJsonText(
            CliDiscoveryCommandExecutor.requestTemplateFor(OperationId.AMEND_ACCOUNT, null));
    String retireAccountTemplate =
        CliWireJson.prettyJsonText(
            CliDiscoveryCommandExecutor.requestTemplateFor(OperationId.RETIRE_ACCOUNT, null));
    String declareTaxRegistrationTemplate =
        CliWireJson.prettyJsonText(
            CliDiscoveryCommandExecutor.requestTemplateFor(
                OperationId.DECLARE_TAX_REGISTRATION, null));

    assertTrue(postingTemplate.contains("\"entryKind\""));
    assertTrue(postEntryTemplate.contains("\"entryKind\""));
    assertTrue(preflightTemplate.contains("\"entryKind\""));
    assertTrue(declareAccountTemplate.contains("\"accountCode\""));
    assertTrue(declareAccountTemplate.contains("\"accountNodeKind\""));
    assertEquals(declareAccountTemplate, amendAccountTemplate);
    assertTrue(retireAccountTemplate.contains("\"accountCode\""));
    assertFalse(retireAccountTemplate.contains("\"accountNodeKind\""));
    assertTrue(declareTaxRegistrationTemplate.contains("\"taxRegistrationId\""));
    assertTrue(declareTaxRegistrationTemplate.contains("\"obligationFrequency\""));
    assertTrue(
        declareTaxRegistrationTemplate.contains("\"jurisdiction\" : \"<ISO-3166-alpha-2>\""));
    assertFalse(declareTaxRegistrationTemplate.contains("\"jurisdiction\" : {"));
  }

  @Test
  void requestTemplateFor_supportsEveryTypedEntryTopic() {
    Map<OperationId, String> topicMarkers =
        Map.of(
            OperationId.RECORD_SALE_SETTLED, "\"revenueAccountCode\"",
            OperationId.RECORD_EXPENSE_SETTLED, "\"expenseAccountCode\"",
            OperationId.RECORD_OWNER_CONTRIBUTION, "\"equityAccountCode\"",
            OperationId.RECORD_OWNER_WITHDRAWAL, "\"equityAccountCode\"",
            OperationId.RECORD_OPENING_POSITION, "\"openingBalances\"",
            OperationId.RECORD_REVERSAL, "\"reversal\"");

    topicMarkers.forEach(
        (operationId, marker) ->
            assertTrue(
                CliWireJson.prettyJsonText(
                        CliDiscoveryCommandExecutor.requestTemplateFor(operationId, null))
                    .contains(marker),
                operationId.wireName()));
  }

  @Test
  void requestTemplateFor_supportsEveryAttestationRegistryMutationTopic() {
    List.of(
            OperationId.ENROLL_KEY,
            OperationId.ROLLOVER_KEY,
            OperationId.REVOKE_KEY,
            OperationId.ALTER_POLICY)
        .forEach(
            operationId ->
                assertEquals(
                    CliWireJson.prettyJsonText(
                        MachineContract.attestationRegistryTemplate(operationId)),
                    CliWireJson.prettyJsonText(
                        CliDiscoveryCommandExecutor.requestTemplateFor(operationId, null)),
                    operationId.wireName()));
  }

  @Test
  void requestTemplateFor_rejectsUnsupportedTopics() {
    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () -> CliDiscoveryCommandExecutor.requestTemplateFor(OperationId.EXECUTE_PLAN, null));

    String message = Objects.requireNonNull(failure.getMessage());
    assertTrue(message.contains("Request templates are available only for"));
    assertTrue(message.contains(OperationId.POST_ENTRY.wireName()));
    assertTrue(message.contains(OperationId.PREFLIGHT_ENTRY.wireName()));
    assertTrue(message.contains(OperationId.DECLARE_ACCOUNT.wireName()));
    assertTrue(message.contains(OperationId.DECLARE_TAX_REGISTRATION.wireName()));
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
