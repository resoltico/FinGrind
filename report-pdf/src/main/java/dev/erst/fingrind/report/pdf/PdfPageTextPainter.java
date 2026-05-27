package dev.erst.fingrind.report.pdf;

import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;

/** Shared low-level text painting for PDF report pages. */
final class PdfPageTextPainter {
  private PdfPageTextPainter() {}

  static void drawText(
      PDPageContentStream target, String text, PDFont font, float fontSize, float x, float y)
      throws IOException {
    float normalizedTextGray =
        PdfReportTheme.grayscale().normalized(PdfReportTheme.grayscale().textRgb());
    target.setNonStrokingColor(normalizedTextGray, normalizedTextGray, normalizedTextGray);
    target.beginText();
    target.setFont(font, fontSize);
    target.newLineAtOffset(x, y);
    target.showText(text);
    target.endText();
  }
}
