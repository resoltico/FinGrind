package dev.erst.fingrind.report.pdf;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.jspecify.annotations.Nullable;

/** Incremental PDF page writer with repeated report mastheads and table pagination. */
final class PdfPageWriter implements AutoCloseable {
  private final PDDocument document;
  private final PdfFonts fonts;
  private final String reportTitle;
  private final Instant generatedAt;
  private final String preparedBy;
  private final PDRectangle currentPageSize;

  private @Nullable PDPageContentStream contentStream;
  private float cursorY;

  PdfPageWriter(
      PDDocument document,
      PdfFonts fonts,
      PageOrientation orientation,
      String reportTitle,
      Instant generatedAt,
      String preparedBy)
      throws IOException {
    this.document = Objects.requireNonNull(document, "document");
    this.fonts = Objects.requireNonNull(fonts, "fonts");
    Objects.requireNonNull(orientation, "orientation");
    this.reportTitle = Objects.requireNonNull(reportTitle, "reportTitle");
    this.generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
    this.preparedBy = Objects.requireNonNull(preparedBy, "preparedBy");
    this.currentPageSize = orientation.pageSize();
    startNewPage();
  }

  void writeKeyValueTable(String heading, List<List<String>> rows) throws IOException {
    writeSectionHeading(heading);
    float labelWidth = keyValueLabelWidth(rows);
    float valueWidth = contentWidth() - labelWidth - PdfReportTheme.KEY_VALUE_COLUMN_GAP;
    for (List<String> row : rows) {
      TextBlockMetrics labelBlock =
          textBlock(
              PdfTextWrapper.wrapText(
                  row.getFirst(), fonts.bold(), PdfReportTheme.BODY_FONT_SIZE, labelWidth),
              fonts.bold(),
              PdfReportTheme.BODY_FONT_SIZE);
      TextBlockMetrics valueBlock =
          textBlock(
              PdfTextWrapper.wrapText(
                  row.get(1), fonts.regular(), PdfReportTheme.BODY_FONT_SIZE, valueWidth),
              fonts.regular(),
              PdfReportTheme.BODY_FONT_SIZE);
      float rowHeight =
          Math.max(labelBlock.height(), valueBlock.height())
              + PdfReportTheme.KEY_VALUE_CELL_PADDING * 2f;
      ensureSpace(rowHeight);
      float rowTop = cursorY;
      drawTextBlock(
          labelBlock,
          PdfReportTheme.PAGE_MARGIN,
          rowTop,
          rowHeight,
          labelWidth,
          PdfTableColumn.CellAlignment.LEFT,
          PdfReportTheme.KEY_VALUE_CELL_PADDING);
      drawTextBlock(
          valueBlock,
          PdfReportTheme.PAGE_MARGIN + labelWidth + PdfReportTheme.KEY_VALUE_COLUMN_GAP,
          rowTop,
          rowHeight,
          valueWidth,
          PdfTableColumn.CellAlignment.LEFT,
          PdfReportTheme.KEY_VALUE_CELL_PADDING);
      cursorY -= rowHeight;
    }
    cursorY -= PdfReportTheme.SECTION_AFTER_TABLE_SPACING;
  }

  void writeTable(String heading, List<PdfTableColumn> columns, List<List<String>> rows)
      throws IOException {
    writeSectionHeading(heading);
    float[] columnWidths = columnWidths(columns);
    drawTableHeader(columns, columnWidths);
    for (List<String> row : rows) {
      float rowHeight = tableRowHeight(row, columnWidths, fonts.regular());
      if (!hasSpace(rowHeight)) {
        startNewPage();
        drawTableHeader(columns, columnWidths);
      }
      drawTableRow(row, columns, columnWidths, rowHeight, false);
    }
    cursorY -= PdfReportTheme.SECTION_AFTER_TABLE_SPACING;
  }

  @Override
  public void close() throws IOException {
    closeActiveContentStream();
    appendPageLabels();
  }

  private void startNewPage() throws IOException {
    closeActiveContentStream();
    PDPage page = new PDPage(currentPageSize);
    document.addPage(page);
    contentStream = new PDPageContentStream(document, page);
    cursorY = currentPageSize.getHeight() - PdfReportTheme.PAGE_MARGIN;
    drawMasthead();
  }

  private void drawMasthead() throws IOException {
    drawText(
        reportTitle,
        fonts.bold(),
        PdfReportTheme.HEADER_TITLE_SIZE,
        PdfReportTheme.PAGE_MARGIN,
        cursorY);
    cursorY -= PdfReportTheme.LINE_HEIGHT + 2f;
    drawText(
        "Generated: " + PdfValueFormatter.instant(generatedAt),
        fonts.regular(),
        PdfReportTheme.HEADER_META_SIZE,
        PdfReportTheme.PAGE_MARGIN,
        cursorY);
    cursorY -= PdfReportTheme.LINE_HEIGHT;
    drawText(
        "Prepared by: " + preparedBy,
        fonts.regular(),
        PdfReportTheme.HEADER_META_SIZE,
        PdfReportTheme.PAGE_MARGIN,
        cursorY);
    cursorY -= PdfReportTheme.LINE_HEIGHT / 2f;
    strokeHorizontalRule(cursorY, PdfReportTheme.SECTION_RULE_RGB);
    cursorY -= PdfReportTheme.LINE_HEIGHT;
  }

  private void writeSectionHeading(String heading) throws IOException {
    ensureSpace(
        PdfReportTheme.LINE_HEIGHT * 2f
            + PdfReportTheme.SECTION_TOP_MARGIN
            + PdfReportTheme.SECTION_BOTTOM_MARGIN);
    cursorY -= PdfReportTheme.SECTION_TOP_MARGIN;
    drawText(
        heading,
        fonts.bold(),
        PdfReportTheme.SECTION_TITLE_SIZE,
        PdfReportTheme.PAGE_MARGIN,
        cursorY);
    cursorY -= PdfReportTheme.LINE_HEIGHT + PdfReportTheme.SECTION_BOTTOM_MARGIN;
  }

  private void drawTableHeader(List<PdfTableColumn> columns, float[] columnWidths)
      throws IOException {
    float headerHeight =
        tableRowHeight(
            columns.stream().map(PdfTableColumn::header).toList(), columnWidths, fonts.bold());
    ensureSpace(headerHeight);
    drawTableRow(
        columns.stream().map(PdfTableColumn::header).toList(),
        columns,
        columnWidths,
        headerHeight,
        true);
  }

  private void drawTableRow(
      List<String> row,
      List<PdfTableColumn> columns,
      float[] columnWidths,
      float rowHeight,
      boolean header)
      throws IOException {
    float rowTop = cursorY;
    float x = PdfReportTheme.PAGE_MARGIN;
    if (header) {
      float normalizedHeaderGray = PdfReportTheme.normalizedGray(PdfReportTheme.HEADER_FILL_RGB);
      activeContentStream()
          .setNonStrokingColor(normalizedHeaderGray, normalizedHeaderGray, normalizedHeaderGray);
      activeContentStream().addRect(x, rowTop - rowHeight, contentWidth(), rowHeight);
      activeContentStream().fill();
      strokeHorizontalRule(rowTop, 0);
    }
    PDFont rowFont = header ? fonts.bold() : fonts.regular();
    for (int index = 0; index < row.size(); index++) {
      TextBlockMetrics block =
          textBlock(
              PdfTextWrapper.wrapText(
                  row.get(index),
                  rowFont,
                  PdfReportTheme.SMALL_FONT_SIZE,
                  columnWidths[index] - PdfReportTheme.TABLE_CELL_PADDING * 2f),
              rowFont,
              PdfReportTheme.SMALL_FONT_SIZE);
      drawTextBlock(
          block,
          x,
          rowTop,
          rowHeight,
          columnWidths[index],
          columns.get(index).alignment(),
          PdfReportTheme.TABLE_CELL_PADDING);
      x += columnWidths[index];
    }
    strokeHorizontalRule(rowTop - rowHeight, 0);
    cursorY -= rowHeight;
  }

  private void drawTextBlock(
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
      float x;
      if (alignment == PdfTableColumn.CellAlignment.LEFT) {
        x = cellX + padding;
      } else {
        x = cellX + cellWidth - padding - stringWidth(line, block.font(), block.fontSize());
      }
      drawText(line, block.font(), block.fontSize(), x, y);
      y -= block.lineAdvance();
    }
  }

  private float[] columnWidths(List<PdfTableColumn> columns) {
    float totalWeight = 0f;
    for (PdfTableColumn column : columns) {
      totalWeight += column.widthWeight();
    }
    float[] widths = new float[columns.size()];
    for (int index = 0; index < columns.size(); index++) {
      widths[index] = contentWidth() * columns.get(index).widthWeight() / totalWeight;
    }
    return widths;
  }

  private float tableRowHeight(List<String> row, float[] columnWidths, PDFont font)
      throws IOException {
    float maxTextHeight = 0f;
    for (int index = 0; index < row.size(); index++) {
      TextBlockMetrics block =
          textBlock(
              PdfTextWrapper.wrapText(
                  row.get(index),
                  font,
                  PdfReportTheme.SMALL_FONT_SIZE,
                  columnWidths[index] - PdfReportTheme.TABLE_CELL_PADDING * 2f),
              font,
              PdfReportTheme.SMALL_FONT_SIZE);
      maxTextHeight = Math.max(maxTextHeight, block.height());
    }
    return maxTextHeight + PdfReportTheme.TABLE_CELL_PADDING * 2f;
  }

  private void drawText(String text, PDFont font, float fontSize, float x, float y)
      throws IOException {
    drawText(activeContentStream(), text, font, fontSize, x, y);
  }

  private void drawText(
      PDPageContentStream target, String text, PDFont font, float fontSize, float x, float y)
      throws IOException {
    float normalizedTextGray = PdfReportTheme.normalizedGray(PdfReportTheme.TEXT_RGB);
    target.setNonStrokingColor(normalizedTextGray, normalizedTextGray, normalizedTextGray);
    target.beginText();
    target.setFont(font, fontSize);
    target.newLineAtOffset(x, y);
    target.showText(text);
    target.endText();
  }

  private void strokeHorizontalRule(float y, int gray) throws IOException {
    float normalizedGray = PdfReportTheme.normalizedGray(gray);
    activeContentStream().setStrokingColor(normalizedGray, normalizedGray, normalizedGray);
    activeContentStream().moveTo(PdfReportTheme.PAGE_MARGIN, y);
    activeContentStream().lineTo(currentPageSize.getWidth() - PdfReportTheme.PAGE_MARGIN, y);
    activeContentStream().stroke();
  }

  private void ensureSpace(float height) throws IOException {
    if (!hasSpace(height)) {
      startNewPage();
    }
  }

  private boolean hasSpace(float height) {
    return cursorY - height >= PdfReportTheme.PAGE_MARGIN;
  }

  private float contentWidth() {
    return currentPageSize.getWidth() - PdfReportTheme.PAGE_MARGIN * 2f;
  }

  private float stringWidth(String text, PDFont font, float fontSize) throws IOException {
    return PdfTextWrapper.stringWidth(text, font, fontSize);
  }

  private TextBlockMetrics textBlock(List<String> lines, PDFont font, float fontSize) {
    float ascent = PdfFontMetrics.ascent(font, fontSize);
    float descent = PdfFontMetrics.descent(font, fontSize);
    float lineAdvance = Math.max(PdfReportTheme.LINE_HEIGHT, ascent + descent + 1f);
    return new TextBlockMetrics(lines, font, fontSize, lineAdvance, ascent, descent);
  }

  private float keyValueLabelWidth(List<List<String>> rows) throws IOException {
    float maxLabelWidth = 0f;
    for (List<String> row : rows) {
      maxLabelWidth =
          Math.max(
              maxLabelWidth,
              stringWidth(row.getFirst(), fonts.bold(), PdfReportTheme.BODY_FONT_SIZE));
    }
    return Math.min(contentWidth() * 0.34f, Math.max(92f, maxLabelWidth + 8f));
  }

  private void closeActiveContentStream() throws IOException {
    if (contentStream != null) {
      contentStream.close();
      contentStream = null;
    }
  }

  private void appendPageLabels() throws IOException {
    int totalPages = document.getNumberOfPages();
    float labelY =
        currentPageSize.getHeight() - PdfReportTheme.PAGE_MARGIN - PdfReportTheme.LINE_HEIGHT - 2f;
    for (int index = 0; index < totalPages; index++) {
      appendPageLabel(index, totalPages, labelY);
    }
  }

  private void appendPageLabel(int pageIndex, int totalPages, float labelY) throws IOException {
    String label = (pageIndex + 1) + " / " + totalPages;
    float labelX =
        currentPageSize.getWidth()
            - PdfReportTheme.PAGE_MARGIN
            - stringWidth(label, fonts.regular(), PdfReportTheme.HEADER_META_SIZE);
    try (PDPageContentStream labelStream =
        new PDPageContentStream(
            document, document.getPage(pageIndex), AppendMode.APPEND, true, true)) {
      drawText(
          labelStream, label, fonts.regular(), PdfReportTheme.HEADER_META_SIZE, labelX, labelY);
    }
  }

  private PDPageContentStream activeContentStream() {
    return Objects.requireNonNull(contentStream, "contentStream");
  }

  private record TextBlockMetrics(
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
