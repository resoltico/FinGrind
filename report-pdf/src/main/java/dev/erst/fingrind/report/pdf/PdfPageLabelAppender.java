package dev.erst.fingrind.report.pdf;

import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/** Appends footer-style page labels after the main report body is rendered. */
final class PdfPageLabelAppender {
  private PdfPageLabelAppender() {}

  static void appendPageLabels(PDDocument document, PDRectangle pageSize, PdfFonts fonts)
      throws IOException {
    int totalPages = document.getNumberOfPages();
    float labelY =
        pageSize.getHeight()
            - PdfReportTheme.spacing().pageMargin()
            - PdfReportTheme.typography().lineHeight()
            - 2f;
    for (int index = 0; index < totalPages; index++) {
      appendPageLabel(document, pageSize, fonts, index, totalPages, labelY);
    }
  }

  private static void appendPageLabel(
      PDDocument document,
      PDRectangle pageSize,
      PdfFonts fonts,
      int pageIndex,
      int totalPages,
      float labelY)
      throws IOException {
    String label = (pageIndex + 1) + " / " + totalPages;
    float labelX =
        pageSize.getWidth()
            - PdfReportTheme.spacing().pageMargin()
            - PdfTextWrapper.stringWidth(
                label, fonts.regular(), PdfReportTheme.typography().headerMetaSize());
    try (PDPageContentStream labelStream =
        new PDPageContentStream(
            document, document.getPage(pageIndex), AppendMode.APPEND, true, true)) {
      PdfPageTextPainter.drawText(
          labelStream,
          label,
          fonts.regular(),
          PdfReportTheme.typography().headerMetaSize(),
          labelX,
          labelY);
    }
  }
}
