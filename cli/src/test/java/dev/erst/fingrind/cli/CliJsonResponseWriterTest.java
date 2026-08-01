package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRetention;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureDetails;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Unit tests for JSON output, failure, and discovery-template response projections. */
class CliJsonResponseWriterTest extends CliResponseWriterTestSupport {
  @Test
  void writeJson_serializationFailureDoesNotEmitPartialOutput() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliOutputChannel responseWriter =
        CliTestOutputChannels.forOutput(utf8PrintStream(outputStream));
    SelfReferentialValue cyclic = new SelfReferentialValue();
    assertThrows(RuntimeException.class, () -> responseWriter.writeJson(cyclic));
    assertEquals("", outputStream.toString(StandardCharsets.UTF_8));
  }

  @Test
  void writeJson_writesStandaloneJsonPayload() throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliOutputChannel responseWriter =
        CliTestOutputChannels.forOutput(utf8PrintStream(outputStream));
    responseWriter.writeJson(Map.of("status", "ok", "count", 2));
    JsonNode json = readJson(outputStream);
    assertEquals("ok", json.path("status").stringValue());
    assertEquals(2, json.path("count").asInt());
  }

  @Test
  void writeFailure_emitsCanonicalJsonEnvelope() throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliFailureResponseWriterFixture responseWriter =
        new CliFailureResponseWriterFixture(utf8PrintStream(outputStream));
    responseWriter.writeFailure(
        new CliFailure("invalid-request", "Unsupported argument: --bogus", "Try help", "--bogus"));
    JsonNode json = readJson(outputStream);
    assertEquals("error", json.path("status").stringValue());
    assertEquals("invalid-request", json.path("code").stringValue());
    assertEquals("Unsupported argument: --bogus", json.path("message").stringValue());
    assertEquals("Try help", json.path("hint").stringValue());
    assertEquals("--bogus", json.path("argument").stringValue());
  }

  @Test
  void writeFailure_preservesStructuredDetails() throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliFailureResponseWriterFixture responseWriter =
        new CliFailureResponseWriterFixture(utf8PrintStream(outputStream));
    responseWriter.writeFailure(
        new CliFailure(
            "invalid-request",
            "Journal entry is invalid.",
            "Fix the request.",
            null,
            new CliErrorJsonModels.InvalidRequestDetails(
                List.of("Journal entry must balance debits and credits."))));
    JsonNode json = readJson(outputStream);
    assertEquals("error", json.path("status").stringValue());
    assertEquals("invalid-request", json.path("code").stringValue());
    assertEquals(
        "Journal entry must balance debits and credits.",
        json.path("details").path("violations").get(0).stringValue());
  }

  @Test
  void writeFailure_serializesProtectedBookPairUncertaintyAsOneTypedErrorDetail() throws Exception {
    Path bookTarget = Path.of("books/recovered.sqlite").toAbsolutePath().normalize();
    Path secretTarget = Path.of("keys/recovered.book-key").toAbsolutePath().normalize();
    ProtectedBookPairPublicationRetention retention =
        new ProtectedBookPairPublicationRetention(
            new ArtifactPublicationResult(
                bookTarget,
                new ArtifactPublicationRetention(
                    bookTarget.resolveSibling(".recovered-book-stage"))),
            new ArtifactPublicationResult(
                secretTarget,
                new ArtifactPublicationRetention(
                    secretTarget.resolveSibling(".recovered-secret-stage"))));
    CliFailure failure =
        CliFailureMapper.contractFailure(
            ContractErrors.protectedBookPairPublicationUncertainFailure(
                OperationId.RESTORE_BOOK,
                new ContractFailureDetails.PairPublication(
                    new ContractFailureDetails.PairPublicationMember(
                        bookTarget,
                        ProtectedBookPairPublicationMemberState.PUBLISHED_DURABILITY_UNCONFIRMED),
                    new ContractFailureDetails.PairPublicationMember(
                        secretTarget, ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED),
                    null,
                    retention)));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliFailureResponseWriterFixture responseWriter =
        new CliFailureResponseWriterFixture(utf8PrintStream(outputStream));

    responseWriter.writeFailure(failure);

    JsonNode json = readJson(outputStream);
    assertEquals("error", json.path("status").stringValue());
    assertEquals("protected-book-pair-publication-uncertain", json.path("code").stringValue());
    assertEquals("precondition", json.path("category").stringValue());
    assertTrue(json.path("argument").isMissingNode());
    assertEquals(CliPublicPaths.absoluteValue(bookTarget), json.path("path").stringValue());
    assertEquals(
        CliPublicPaths.absoluteValue(secretTarget), json.path("relatedPaths").get(0).stringValue());
    assertEquals(
        CliPublicPaths.absoluteValue(retention.bookPublication().retention().retainedStagePath()),
        json.path("relatedPaths").get(1).stringValue());
    assertEquals(
        CliPublicPaths.absoluteValue(
            retention.generatedSecretPublication().retention().retainedStagePath()),
        json.path("relatedPaths").get(2).stringValue());
    assertEquals("restore-book", json.path("details").path("operation").stringValue());
    assertEquals(
        "published-durability-unconfirmed",
        json.path("details")
            .path("pairPublication")
            .path("bookTarget")
            .path("state")
            .stringValue());
    assertTrue(json.path("details").path("pairPublication").path("recoveryRecordState").isNull());
    assertEquals(
        CliPublicPaths.absoluteValue(retention.bookPublication().retention().retainedStagePath()),
        json.path("details")
            .path("pairPublication")
            .path("pairPublicationRetention")
            .path("bookPublication")
            .path("retainedStage")
            .stringValue());
  }

  @Test
  void writeFailure_marksUnestablishedPairEvidenceWithoutInventingRetainedStageFacts()
      throws Exception {
    Path bookTarget = Path.of("books/unknown.sqlite").toAbsolutePath().normalize();
    Path secretTarget = Path.of("keys/unknown.book-key").toAbsolutePath().normalize();
    CliFailure failure =
        CliFailureMapper.contractFailure(
            ContractErrors.protectedBookPairPublicationEvidenceBlockedFailure(
                new ContractFailureDetails.PairPublication(
                    new ContractFailureDetails.PairPublicationMember(
                        bookTarget, ProtectedBookPairPublicationMemberState.UNESTABLISHED),
                    new ContractFailureDetails.PairPublicationMember(
                        secretTarget, ProtectedBookPairPublicationMemberState.UNESTABLISHED),
                    null,
                    null)));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliFailureResponseWriterFixture responseWriter =
        new CliFailureResponseWriterFixture(utf8PrintStream(outputStream));

    responseWriter.writeFailure(failure);

    JsonNode json = readJson(outputStream);
    assertEquals(
        "protected-book-pair-publication-evidence-blocked", json.path("code").stringValue());
    assertEquals(CliPublicPaths.absoluteValue(bookTarget), json.path("path").stringValue());
    assertEquals(1, json.path("relatedPaths").size());
    assertEquals(
        CliPublicPaths.absoluteValue(secretTarget), json.path("relatedPaths").get(0).stringValue());
    assertTrue(
        json.path("details").path("pairPublication").path("pairPublicationRetention").isNull());
  }

  @Test
  void writeDeterministicFailure_emitsCanonicalJsonEnvelope() throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliFailureResponseWriterFixture responseWriter =
        new CliFailureResponseWriterFixture(utf8PrintStream(outputStream));

    responseWriter.writeFailure(
        new CliFailure(
            "protected-book-verification-failed",
            "FinGrind could not verify the selected protected book with the supplied passphrase source.",
            "Inspect the passphrase source and the protected book, then rerun the command.",
            null));

    JsonNode json = readJson(outputStream);
    assertEquals("error", json.path("status").stringValue());
    assertEquals("protected-book-verification-failed", json.path("code").stringValue());
    assertTrue(
        json.path("hint")
            .stringValue()
            .contains("Inspect the passphrase source and the protected book"));
  }

  @Test
  void writeFailure_writesErrorEnvelope() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliFailureResponseWriterFixture responseWriter =
        new CliFailureResponseWriterFixture(utf8PrintStream(outputStream));
    responseWriter.writeFailure("invalid-request", "bad request");
    assertJsonContains(outputStream, "\"status\":\"error\"");
  }

  @Test
  void writeFailure_writesStructuredInvalidRequestDetails() throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliFailureResponseWriterFixture responseWriter =
        new CliFailureResponseWriterFixture(utf8PrintStream(outputStream));
    responseWriter.writeFailure(
        new CliFailure(
            "invalid-request",
            "Journal entry is invalid.",
            "Fix the request.",
            null,
            new CliErrorJsonModels.InvalidRequestDetails(
                List.of("Journal entry must balance debits and credits."))));
    JsonNode json = readJson(outputStream);
    assertEquals("error", json.path("status").stringValue());
    assertEquals("invalid-request", json.path("code").stringValue());
    assertEquals(
        "Journal entry must balance debits and credits.",
        json.path("details").path("violations").get(0).stringValue());
  }

  @Test
  void writeFailure_routesMachineReadableEnvelopeToDiagnosticsStream() throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsStream = new ByteArrayOutputStream();
    CliFailureResponseWriterFixture responseWriter =
        new CliFailureResponseWriterFixture(
            utf8PrintStream(outputStream), utf8PrintStream(diagnosticsStream));

    responseWriter.writeFailure("invalid-request", "bad request");

    assertEquals("", outputStream.toString(StandardCharsets.UTF_8));
    JsonNode json = readJson(diagnosticsStream);
    assertEquals("error", json.path("status").stringValue());
    assertEquals("invalid-request", json.path("code").stringValue());
    assertEquals("bad request", json.path("message").stringValue());
  }

  @Test
  void writeRequestTemplate_writesCanonicalRawJsonTemplate() throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliDiscoveryResponseWriterFixture responseWriter =
        new CliDiscoveryResponseWriterFixture(utf8PrintStream(outputStream));
    String expected = CliWireJson.prettyJsonText(MachineContract.requestTemplate());

    responseWriter.writeRequestTemplate(MachineContract.requestTemplate());

    assertEquals(expected, outputStream.toString(StandardCharsets.UTF_8).trim());
  }

  @Test
  void writePlanTemplate_writesCanonicalRawJsonTemplate() throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliDiscoveryResponseWriterFixture responseWriter =
        new CliDiscoveryResponseWriterFixture(utf8PrintStream(outputStream));
    String expected = CliWireJson.prettyJsonText(MachineContract.planTemplate());

    responseWriter.writePlanTemplate(MachineContract.planTemplate());

    assertEquals(expected, outputStream.toString(StandardCharsets.UTF_8).trim());
  }
}
