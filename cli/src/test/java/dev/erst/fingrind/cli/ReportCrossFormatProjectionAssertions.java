package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.reportmodel.ReportModel;
import dev.erst.fingrind.report.pdf.PdfReportService;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/** Shared assertions for human report projections. */
final class ReportCrossFormatProjectionAssertions {
  private ReportCrossFormatProjectionAssertions() {}

  static void assertTextFactsMatch(ReportModel model, String rendered) {
    assertEquals(
        ReportCrossFormatStructuredFacts.readableModelFacts(model),
        ReportCrossFormatTextFacts.fromText(model, rendered));
  }

  static void assertPdfFactsMatch(ReportModel model, String rendered) {
    assertEquals(
        ReportCrossFormatStructuredFacts.readableModelFacts(model),
        ReportCrossFormatPdfFacts.fromPdfText(model, rendered));
  }

  static String pdfText(PdfReportService pdfReportService, ReportModel model) throws IOException {
    try (PDDocument document = Loader.loadPDF(pdfReportService.render(model))) {
      return new PDFTextStripper().getText(document);
    }
  }

  static boolean containsNormalized(String rendered, String snippet) {
    return normalizeWhitespace(rendered).contains(normalizeWhitespace(snippet));
  }

  private static String normalizeWhitespace(String value) {
    return value.replaceAll("\\s+", " ").trim();
  }
}
