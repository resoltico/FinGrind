package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.ProtocolArtifactOutput;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Capability, environment, version, and template discovery tests for {@link FinGrindCli}. */
class FinGrindCliDiscoveryMetadataCommandTest extends FinGrindCliDiscoveryCommandTestSupport {
  @Test
  void run_returnsCapabilities() throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode = cli.run(new String[] {"capabilities", "--detail", "full", "--output", "json"});
    assertEquals(0, exitCode);
    String json = outputStream.toString(StandardCharsets.UTF_8);
    JsonNode payload = new ObjectMapper().readTree(json).path("payload");
    assertTrue(json.contains("\"administration\""));
    assertTrue(json.contains("\"query\""));
    assertTrue(json.contains("\"administration-book-not-initialized\""));
    assertTrue(json.contains("\"query-book-not-initialized\""));
    assertTrue(json.contains("\"posting-book-not-initialized\""));
    assertTrue(json.contains("\"close-target-account-candidate-missing\""));
    assertTrue(json.contains("\"posting-not-found\""));
    assertCapabilitiesCommandCatalog(payload);
    assertCapabilitiesRequestShapes(payload);
    assertCapabilitiesRequestInput(payload);
    assertCapabilitiesResponseModel(payload);
    assertFalse(payload.has("environment"));
  }

  @Test
  void run_returnsEnvironment() throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[] {"environment", "--output", "json"});

    assertEquals(0, exitCode);
    JsonNode payload =
        new ObjectMapper().readTree(outputStream.toString(StandardCharsets.UTF_8)).path("payload");
    assertEnvironmentRuntimeContract(payload);
  }

  @Test
  void run_returnsVersion() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode = cli.run(new String[] {"version", "--output", "json"});
    assertEquals(0, exitCode);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"application\""));
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"version\""));
  }

  @Test
  void environmentDescriptor_reportsUnavailableRuntimeWhenSqliteProbeFails() {
    EnvironmentDescriptor environmentDescriptor = unavailableRuntimeEnvironmentDescriptor();
    assertUnavailableRuntimeEnvironmentDescriptor(environmentDescriptor);
  }

  @Test
  void run_generatesBookKeyFileWithNonSecretMetadata() throws Exception {
    Path keyFilePath = tempDirectory.resolve("secrets").resolve("entity.book-key");
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode =
        cli.run(
            new String[] {
              "generate-book-key-file",
              "--book-key-file",
              keyFilePath.toString(),
              "--output",
              "json"
            });
    assertEquals(0, exitCode);
    assertTrue(Files.isRegularFile(keyFilePath));
    JsonNode envelope = new ObjectMapper().readTree(outputStream.toByteArray());
    JsonNode payload = envelope.path("payload");
    assertGeneratedKeyFileIsSecure(keyFilePath, payload.path("permissions").stringValue());
    assertTrue(payload.path("bookKeyFile").isMissingNode());
    assertEquals(
        ProtocolArtifactOutput.bookKeyFileFormat(),
        envelope.path("artifacts").get(0).path("format").stringValue());
    assertEquals(
        CliPublicPaths.redactedValue(keyFilePath),
        envelope.path("artifacts").get(0).path("path").stringValue());
    assertEquals("base64url-no-padding", payload.path("encoding").stringValue());
    assertEquals(256, payload.path("entropyBits").asInt());
    assertFalse(
        outputStream.toString(StandardCharsets.UTF_8).contains(Files.readString(keyFilePath)));
  }

  @Test
  void run_reportsDeterministicFailureWhenGeneratedKeyFileAlreadyExists() throws Exception {
    Path keyFilePath = tempDirectory.resolve("secrets").resolve("existing.book-key");
    writeSecureKey(keyFilePath, "already-present");
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode =
        cli.run(
            new String[] {
              "generate-book-key-file",
              "--book-key-file",
              keyFilePath.toString(),
              "--output",
              "json"
            });
    assertEquals(7, exitCode);
    JsonNode failureEnvelope = new ObjectMapper().readTree(outputStream.toByteArray());
    assertEquals(
        ContractErrors.Descriptor.BOOK_KEY_FILE_ALREADY_EXISTS.code(),
        failureEnvelope.path("code").stringValue());
    assertTrue(failureEnvelope.path("message").stringValue().contains("already exists"));
  }

  @Test
  void run_printsRequestTemplateWithoutCallerSuppliedCommitFields() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode = cli.run(new String[] {"print-request-template"});
    assertEquals(0, exitCode);
    String json = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(json.contains("\"2026-01-15\""));
    assertTrue(json.contains("\"effectiveDate\""));
    assertTrue(json.contains("\"provenance\""));
    assertFalse(json.contains("recordedAt"));
    assertFalse(json.contains("sourceChannel"));
  }

  @Test
  void run_printsPlanTemplateForAgentWorkflows() throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode = cli.run(new String[] {"print-plan-template"});
    assertEquals(0, exitCode);
    JsonNode json = new ObjectMapper().readTree(outputStream.toString(StandardCharsets.UTF_8));
    assertEquals("plan-1", json.path("planId").stringValue());
    assertFalse(json.has("executionPolicy"));
    assertEquals("ensure-book", json.path("steps").get(0).path("stepId").stringValue());
    assertEquals("assert-cash-balance", json.path("steps").get(2).path("stepId").stringValue());
    assertEquals(
        "assert-account-balance",
        json.path("steps").get(2).path("assertion").path("kind").stringValue());
    assertTrue(json.path("steps").get(2).has("assertion"));
    assertFalse(json.path("steps").get(2).has("accountBalanceAssertion"));
  }
}
