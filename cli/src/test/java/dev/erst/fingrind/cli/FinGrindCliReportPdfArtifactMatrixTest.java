package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Matrix coverage for PDF-capable report commands. */
class FinGrindCliReportPdfArtifactMatrixTest extends CliReportPdfArtifactCommandTestSupport {

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
      assertEquals(
          expectedPdfArtifactOutput(pdfOutputPath.toRealPath()),
          result.outputText(),
          spec.commandName());
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
      Path retainedStage = retainedPdfStageFor(pdfOutputPath);
      assertEquals(
          CliPublicPaths.absoluteValue(retainedStage),
          envelope.path("artifacts").get(0).path("retainedStage").stringValue(),
          spec.commandName());
      assertTrue(Files.isSameFile(pdfOutputPath, retainedStage), spec.commandName());
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

  private static String expectedPdfArtifactOutput(Path canonicalArtifactPath) throws IOException {
    return CliArtifactOutputRenderer.renderPdfArtifact(
            new ArtifactPublicationResult(
                canonicalArtifactPath,
                new ArtifactPublicationRetention(retainedPdfStageFor(canonicalArtifactPath))))
        + System.lineSeparator();
  }

  private static Path retainedPdfStageFor(Path finalArtifact) throws IOException {
    Path canonicalFinalArtifact = finalArtifact.toRealPath();
    try (Stream<Path> siblings = Files.list(canonicalFinalArtifact.getParent())) {
      for (Path candidate : siblings.toList()) {
        String fileName = candidate.getFileName().toString();
        if (candidate.equals(canonicalFinalArtifact)
            || !fileName.startsWith(".fingrind-pdf-")
            || !fileName.endsWith(".tmp")) {
          continue;
        }
        if (Files.isSameFile(canonicalFinalArtifact, candidate)) {
          return candidate.toRealPath();
        }
      }
    }
    throw new AssertionError("Expected a retained PDF stage linked to " + canonicalFinalArtifact);
  }
}
