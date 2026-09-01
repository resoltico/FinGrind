package dev.erst.fingrind.report.pdf;

import java.io.IOException;
import java.util.List;
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
    // Emit code points separately so the embedded-font ToUnicode map remains faithful to the
    // report's logical text instead of collapsing sequences such as "ff" into presentation
    // ligature code points during extraction or assistive reading.
    for (String codePointText : codePointTexts(text)) {
      target.showText(codePointText);
    }
    target.endText();
  }

  private static List<String> codePointTexts(String text) {
    return text.codePoints().mapToObj(Character::toString).toList();
  }
}
