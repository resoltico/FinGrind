package dev.erst.fingrind.report.pdf;

import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.font.PDFont;

/** Shared table-measurement and cell-text helpers for FinGrind PDF reports. */
final class PdfTableTextSupport {
  private PdfTableTextSupport() {}

  static float[] columnWidths(PdfPageWriter pageWriter, List<PdfTableColumn> columns) {
    float totalWeight = 0f;
    for (PdfTableColumn column : columns) {
      totalWeight += column.widthWeight();
    }
    float[] widths = new float[columns.size()];
    for (int index = 0; index < columns.size(); index++) {
      widths[index] = pageWriter.contentWidth() * columns.get(index).widthWeight() / totalWeight;
    }
    return widths;
  }

  static float tableRowHeight(
      List<String> row, float[] columnWidths, PDFont font, PdfPageWriter pageWriter)
      throws IOException {
    float maxTextHeight = 0f;
    for (int index = 0; index < row.size(); index++) {
      TextBlockMetrics block =
          textBlock(
              PdfTextWrapper.wrapText(
                  row.get(index),
                  font,
                  PdfReportTheme.typography().smallFontSize(),
                  columnWidths[index] - PdfReportTheme.spacing().tableCellPadding() * 2f),
              font,
              PdfReportTheme.typography().smallFontSize());
      maxTextHeight = Math.max(maxTextHeight, block.height());
    }
    return maxTextHeight + PdfReportTheme.spacing().tableCellPadding() * 2f;
  }

  static float keyValueLabelWidth(PdfPageWriter pageWriter, List<List<String>> rows)
      throws IOException {
    float maxLabelWidth = 0f;
    for (List<String> row : rows) {
      maxLabelWidth =
          Math.max(
              maxLabelWidth,
              stringWidth(
                  row.getFirst(),
                  pageWriter.fonts().bold(),
                  PdfReportTheme.typography().bodyFontSize()));
    }
    return Math.min(pageWriter.contentWidth() * 0.34f, Math.max(92f, maxLabelWidth + 8f));
  }

  static TextBlockMetrics textBlock(List<String> lines, PDFont font, float fontSize) {
    float ascent = PdfFontMetrics.ascent(font, fontSize);
    float descent = PdfFontMetrics.descent(font, fontSize);
    float lineAdvance = Math.max(PdfReportTheme.typography().lineHeight(), ascent + descent + 1f);
    return new TextBlockMetrics(lines, font, fontSize, lineAdvance, ascent, descent);
  }

  static void drawTextBlock(
      PdfPageWriter pageWriter,
      TextBlockMetrics block,
      float cellX,
      float rowTop,
      float rowHeight,
      float cellWidth,
      PdfTableColumn.CellAlignment alignment,
      float padding)
      throws IOException {
    float topInset = (rowHeight - block.height()) / 2f;
    float y = rowTop - topInset - block.ascent();
    for (String line : block.lines()) {
      float x =
          alignment == PdfTableColumn.CellAlignment.LEFT
              ? cellX + padding
              : cellX + cellWidth - padding - stringWidth(line, block.font(), block.fontSize());
      pageWriter.drawText(line, block.font(), block.fontSize(), x, y);
      y -= block.lineAdvance();
    }
  }

  private static float stringWidth(String text, PDFont font, float fontSize) throws IOException {
    return PdfTextWrapper.stringWidth(text, font, fontSize);
  }

  record TextBlockMetrics(
      List<String> lines,
      PDFont font,
      float fontSize,
      float lineAdvance,
      float ascent,
      float descent) {
    float height() {
      return lineAdvance * Math.max(0, lines.size() - 1) + ascent + descent;
    }
  }
}
