package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.discovery.PdfReportCapabilityDescriptorProjection;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Matrix coverage for PDF-capable report commands. */
class FinGrindCliReportPdfArtifactMatrixTest extends CliReportPdfArtifactCommandTestSupport {

  @Test
  void pdfCapableReportFixtureKeys_matchDescriptorPdfOperationsInDescriptorOrder() {
    assertEquals(
        descriptorPdfOperationWireNames(),
        pdfCapableReportCommandSpecs().stream()
            .map(ReportCommandSpec::operationId)
            .map(OperationId::wireName)
            .toList());
  }

  @Test
  void run_textReportWithPdfOut_replacesStdoutWithOneArtifactBlockForEveryPdfCapableReport()
      throws IOException {
    for (ReportCommandSpec spec : pdfCapableReportCommandSpecs()) {
      Path bookFilePath = tempDirectory.resolve("books").resolve(spec.commandName() + ".sqlite");
      Path bookKeyFilePath = tempDirectory.resolve("keys").resolve(spec.commandName() + ".key");
      Path pdfOutputPath =
          tempDirectory.resolve("reports").resolve(spec.commandName() + "-text-report.pdf");
      ExecutedReportCommand result =
          executeReportCommand(
              spec,
              bookFilePath,
              bookKeyFilePath,
              "text",
              pdfOutputPath,
              spec.successfulWorkflow());

      assertEquals(0, result.exitCode(), spec.commandName());
      assertPdfArtifactTransactionOutput(
          pdfOutputPath.toRealPath(), result.outputText(), spec.commandName());
      assertEquals("", result.diagnosticsText(), spec.commandName());
      assertTrue(Files.exists(pdfOutputPath), spec.commandName());
      assertPdfSignature(pdfOutputPath);
    }
  }

  @Test
  void run_jsonReportWithPdfOut_publishesOneArtifactEntryForEveryPdfCapableReport()
      throws IOException {
    for (ReportCommandSpec spec : pdfCapableReportCommandSpecs()) {
      Path bookFilePath = tempDirectory.resolve("books").resolve(spec.commandName() + ".sqlite");
      Path bookKeyFilePath = tempDirectory.resolve("keys").resolve(spec.commandName() + ".key");
      Path pdfOutputPath =
          tempDirectory.resolve("reports").resolve(spec.commandName() + "-json-report.pdf");
      ExecutedReportCommand result =
          executeReportCommand(
              spec,
              bookFilePath,
              bookKeyFilePath,
              "json",
              pdfOutputPath,
              spec.successfulWorkflow());

      assertEquals(0, result.exitCode(), spec.commandName());
      JsonNode envelope = readJson(result.outputText());
      assertEquals("ok", envelope.path("status").stringValue(), spec.commandName());
      assertTrue(envelope.path("payload").isObject(), spec.commandName());
      assertEquals(1, envelope.path("artifacts").size(), spec.commandName());
      assertEquals(
          "pdf",
          envelope.path("artifacts").get(0).path("format").stringValue(),
          spec.commandName());
      assertEquals(
          CliPublicPaths.absoluteValue(pdfOutputPath.toRealPath()),
          envelope.path("artifacts").get(0).path("path").stringValue(),
          spec.commandName());
      assertTrue(
          envelope
              .path("artifacts")
              .get(0)
              .path("publicationTransaction")
              .path("id")
              .stringValue()
              .matches("[0-9a-f]{32}"),
          spec.commandName());
      assertEquals(
          "complete",
          envelope
              .path("artifacts")
              .get(0)
              .path("publicationTransaction")
              .path("state")
              .stringValue(),
          spec.commandName());
      assertEquals(
          "all-committed",
          envelope
              .path("artifacts")
              .get(0)
              .path("publicationTransaction")
              .path("commitOutcome")
              .stringValue(),
          spec.commandName());
      assertEquals(
          "complete",
          envelope
              .path("artifacts")
              .get(0)
              .path("publicationTransaction")
              .path("cleanupOutcome")
              .stringValue(),
          spec.commandName());
      assertTrue(envelope.path("artifacts").get(0).path("retainedStage").isMissingNode());
      assertTrue(envelope.path("code").isMissingNode(), spec.commandName());
      assertTrue(envelope.path("message").isMissingNode(), spec.commandName());
      assertEquals("", result.diagnosticsText(), spec.commandName());
      assertTrue(Files.exists(pdfOutputPath), spec.commandName());
      assertPdfSignature(pdfOutputPath);
    }
  }

  @Test
  void run_pdfOutThroughIntermediateDirectoryAlias_refusesPublication() throws IOException {
    Path physicalReportsDirectory = tempDirectory.resolve("physical-reports");
    Files.createDirectories(physicalReportsDirectory);
    CliTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(physicalReportsDirectory);
    Path reportsAlias = tempDirectory.resolve("reports-alias");
    Files.createSymbolicLink(reportsAlias, tempDirectory);
    Path bookFilePath = tempDirectory.resolve("books").resolve("trial-balance.sqlite");
    Path bookKeyFilePath = tempDirectory.resolve("keys").resolve("trial-balance.key");
    ReportCommandSpec trialBalance =
        pdfCapableReportCommandSpecs().stream()
            .filter(spec -> "trial-balance".equals(spec.commandName()))
            .findFirst()
            .orElseThrow();
    Path requestedJsonPath =
        reportsAlias.resolve("physical-reports").resolve("trial-balance-json.pdf");

    ExecutedReportCommand jsonResult =
        executeReportCommand(
            trialBalance,
            bookFilePath,
            bookKeyFilePath,
            "json",
            requestedJsonPath,
            trialBalance.successfulWorkflow());

    assertEquals(6, jsonResult.exitCode());
    assertEquals("", jsonResult.outputText());
    JsonNode jsonEnvelope = readJson(jsonResult.diagnosticsText());
    assertEquals("error", jsonEnvelope.path("status").stringValue());
    assertEquals("invalid-artifact-output-directory", jsonEnvelope.path("code").stringValue());
    assertEquals("--pdf-out", jsonEnvelope.path("argument").stringValue());
    assertTrue(jsonEnvelope.path("artifacts").isMissingNode());
    assertFalse(Files.exists(requestedJsonPath));
    assertFalse(Files.exists(physicalReportsDirectory.resolve("trial-balance-json.pdf")));
  }

  @Test
  void run_rejectedReportWithPdfOut_skipsArtifactPublicationForEveryPdfCapableReport()
      throws IOException {
    for (ReportCommandSpec spec : pdfCapableReportCommandSpecs()) {
      Path bookFilePath = tempDirectory.resolve("books").resolve(spec.commandName() + ".sqlite");
      Path bookKeyFilePath = tempDirectory.resolve("keys").resolve(spec.commandName() + ".key");
      Path pdfOutputPath =
          tempDirectory.resolve("reports").resolve(spec.commandName() + "-rejected-report.pdf");
      ExecutedReportCommand result =
          executeReportCommand(
              spec, bookFilePath, bookKeyFilePath, "json", pdfOutputPath, spec.rejectedWorkflow());

      assertEquals(2, result.exitCode(), spec.commandName());
      assertEquals("", result.outputText(), spec.commandName());
      JsonNode envelope = readJson(result.diagnosticsText());
      assertEquals("rejected", envelope.path("status").stringValue(), spec.commandName());
      assertTrue(envelope.path("code").isString(), spec.commandName());
      assertTrue(envelope.path("message").isString(), spec.commandName());
      assertTrue(envelope.path("artifacts").isMissingNode(), spec.commandName());
      assertFalse(Files.exists(pdfOutputPath), spec.commandName());
    }
  }

  @Test
  void run_invalidPdfOutputParentFailsEveryPdfCapableReportWithoutPublishingSuccess()
      throws IOException {
    Path blockedParent = tempDirectory.resolve("blocked output parent");
    Files.writeString(blockedParent, "not a directory", StandardCharsets.UTF_8);

    for (ReportCommandSpec spec : pdfCapableReportCommandSpecs()) {
      Path bookFilePath = tempDirectory.resolve("books").resolve(spec.commandName() + ".sqlite");
      Path bookKeyFilePath = tempDirectory.resolve("keys").resolve(spec.commandName() + ".key");
      Path pdfOutputPath = blockedParent.resolve(spec.commandName() + "-failed-report.pdf");
      ExecutedReportCommand result =
          executeReportCommandWithoutPreparingPdfOutputParent(
              spec,
              bookFilePath,
              bookKeyFilePath,
              "json",
              pdfOutputPath,
              spec.successfulWorkflow());

      assertEquals(6, result.exitCode(), spec.commandName());
      assertEquals("", result.outputText(), spec.commandName());
      JsonNode envelope = readJson(result.diagnosticsText());
      assertEquals("error", envelope.path("status").stringValue(), spec.commandName());
      assertEquals(
          "invalid-artifact-output-directory",
          envelope.path("code").stringValue(),
          spec.commandName());
      assertEquals("--pdf-out", envelope.path("argument").stringValue(), spec.commandName());
      assertTrue(envelope.path("artifacts").isMissingNode(), spec.commandName());
      assertFalse(Files.exists(pdfOutputPath), spec.commandName());
    }
  }

  private static void assertPdfArtifactTransactionOutput(
      Path canonicalArtifactPath, String output, String commandName) {
    assertTrue(output.startsWith("Artifact" + System.lineSeparator()), commandName);
    assertTrue(output.contains("Format                  : pdf"), commandName);
    assertTrue(
        output.contains(
            "Path                    : " + CliPublicPaths.redactedValue(canonicalArtifactPath)),
        commandName);
    assertTrue(
        output.matches("(?s).*Publication transaction : [0-9a-f]{32}" + System.lineSeparator()),
        commandName);
    assertFalse(output.contains("Retained stage"), commandName);
  }

  private static List<String> descriptorPdfOperationWireNames() {
    return PdfReportCapabilityDescriptorProjection.pdfReportOperationWireNames(
        MachineContract.capabilities(CliDiscoveryTestSupport.identity()));
  }
}
