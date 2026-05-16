package dev.erst.fingrind.report.pdf;

import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;

/** Shared PDF font-metric helpers used by table and text layout. */
final class PdfFontMetrics {
  private PdfFontMetrics() {}

  static float ascent(PDFont font, float fontSize) {
    PDFontDescriptor descriptor = font.getFontDescriptor();
    float rawAscent = descriptor == null ? 0f : descriptor.getAscent();
    if (rawAscent <= 0f) {
      return fontSize * 0.8f;
    }
    return rawAscent / 1000f * fontSize;
  }

  static float descent(PDFont font, float fontSize) {
    PDFontDescriptor descriptor = font.getFontDescriptor();
    float rawDescent = descriptor == null ? 0f : descriptor.getDescent();
    if (rawDescent >= 0f) {
      return fontSize * 0.25f;
    }
    return Math.abs(rawDescent / 1000f * fontSize);
  }
}
