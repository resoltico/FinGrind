package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
          CliArtifactOutputRenderer.renderPdfArtifact(pdfOutputPath) + System.lineSeparator(),
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
          CliPublicPaths.redactedValue(pdfOutputPath),
          envelope.path("artifacts").get(0).path("path").stringValue(),
          spec.commandName());
      assertTrue(envelope.path("code").isMissingNode(), spec.commandName());
      assertTrue(envelope.path("message").isMissingNode(), spec.commandName());
      assertEquals("", result.diagnosticsText(), spec.commandName());
      assertTrue(Files.exists(pdfOutputPath), spec.commandName());
      assertPdfSignature(pdfOutputPath);
    }
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
      assertTrue(envelope.path("code").isTextual(), spec.commandName());
      assertTrue(envelope.path("message").isTextual(), spec.commandName());
      assertTrue(envelope.path("artifacts").isMissingNode(), spec.commandName());
      assertFalse(Files.exists(pdfOutputPath), spec.commandName());
    }
  }

  @Test
  void run_pdfExportFailureFailsEveryPdfCapableReportWithoutPublishingSuccess() throws IOException {
    Path blockedParent = tempDirectory.resolve("blocked output parent");
    Files.writeString(blockedParent, "not a directory", StandardCharsets.UTF_8);

    for (ReportCommandSpec spec : pdfCapableReportCommandSpecs()) {
      Path bookFilePath = tempDirectory.resolve("books").resolve(spec.commandName() + ".sqlite");
      Path bookKeyFilePath = tempDirectory.resolve("keys").resolve(spec.commandName() + ".key");
      Path pdfOutputPath = blockedParent.resolve(spec.commandName() + "-failed-report.pdf");
      ExecutedReportCommand result =
          executeReportCommand(
              spec,
              bookFilePath,
              bookKeyFilePath,
              "json",
              pdfOutputPath,
              spec.successfulWorkflow());

      assertEquals(4, result.exitCode(), spec.commandName());
      assertEquals("", result.outputText(), spec.commandName());
      JsonNode envelope = readJson(result.diagnosticsText());
      assertEquals("error", envelope.path("status").stringValue(), spec.commandName());
      assertEquals("pdf-export-failure", envelope.path("code").stringValue(), spec.commandName());
      assertEquals("--pdf-out", envelope.path("argument").stringValue(), spec.commandName());
      assertTrue(envelope.path("artifacts").isMissingNode(), spec.commandName());
      assertFalse(Files.exists(pdfOutputPath), spec.commandName());
    }
  }
}
