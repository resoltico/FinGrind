package dev.erst.fingrind.report.pdf;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.jspecify.annotations.Nullable;

/** Incremental PDF page writer with repeated report mastheads and table pagination. */
final class PdfPageWriter implements AutoCloseable {
  private final PDDocument document;
  private final PdfFonts fonts;
  private final String reportTitle;
  private final String bookFilePath;
  private final Instant generatedAt;
  private final String preparedBy;
  private final PDRectangle currentPageSize;

  private @Nullable PDPageContentStream contentStream;
  private float cursorY;
  private int pageNumber;

  PdfPageWriter(
      PDDocument document,
      PdfFonts fonts,
      PageOrientation orientation,
      String reportTitle,
      Path bookFilePath,
      Instant generatedAt,
      String preparedBy)
      throws IOException {
    this.document = Objects.requireNonNull(document, "document");
    this.fonts = Objects.requireNonNull(fonts, "fonts");
    Objects.requireNonNull(orientation, "orientation");
    this.reportTitle = Objects.requireNonNull(reportTitle, "reportTitle");
    this.bookFilePath =
        PdfRenderSupport.absolutePath(Objects.requireNonNull(bookFilePath, "bookFilePath"));
    this.generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
    this.preparedBy = Objects.requireNonNull(preparedBy, "preparedBy");
    this.currentPageSize = orientation.pageSize();
    startNewPage();
  }

  void writeKeyValueTable(String heading, List<List<String>> rows) throws IOException {
    writeSectionHeading(heading);
    float labelWidth = 120f;
    float valueWidth = contentWidth() - labelWidth - PdfReportTheme.CELL_PADDING * 2f;
    for (List<String> row : rows) {
      List<String> labelLines =
          PdfTextWrapper.wrapText(
              row.getFirst(), fonts.bold(), PdfReportTheme.BODY_FONT_SIZE, labelWidth);
      List<String> valueLines =
          PdfTextWrapper.wrapText(
              row.get(1), fonts.regular(), PdfReportTheme.BODY_FONT_SIZE, valueWidth);
      float rowHeight =
          Math.max(labelLines.size(), valueLines.size()) * PdfReportTheme.LINE_HEIGHT
              + PdfReportTheme.CELL_PADDING * 2f;
      ensureSpace(rowHeight);
      float rowTop = cursorY;
      drawWrappedLines(
          labelLines,
          fonts.bold(),
          PdfReportTheme.BODY_FONT_SIZE,
          PdfReportTheme.PAGE_MARGIN,
          rowTop - PdfReportTheme.CELL_PADDING);
      drawWrappedLines(
          valueLines,
          fonts.regular(),
          PdfReportTheme.BODY_FONT_SIZE,
          PdfReportTheme.PAGE_MARGIN + labelWidth + PdfReportTheme.CELL_PADDING,
          rowTop - PdfReportTheme.CELL_PADDING);
      cursorY -= rowHeight;
    }
    cursorY -= PdfReportTheme.LINE_HEIGHT / 2f;
  }

  void writeTable(String heading, List<PdfTableColumn> columns, List<List<String>> rows)
      throws IOException {
    writeSectionHeading(heading);
    float[] columnWidths = columnWidths(columns);
    drawTableHeader(columns, columnWidths);
    for (List<String> row : rows) {
      float rowHeight = tableRowHeight(row, columnWidths);
      if (!hasSpace(rowHeight)) {
        startNewPage();
        drawTableHeader(columns, columnWidths);
      }
      drawTableRow(row, columns, columnWidths, rowHeight, false);
    }
    cursorY -= PdfReportTheme.LINE_HEIGHT / 2f;
  }

  @Override
  public void close() throws IOException {
    if (contentStream != null) {
      contentStream.close();
      contentStream = null;
    }
  }

  private void startNewPage() throws IOException {
    close();
    PDPage page = new PDPage(currentPageSize);
    document.addPage(page);
    contentStream = new PDPageContentStream(document, page);
    pageNumber++;
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
        "Book: " + bookFilePath,
        fonts.regular(),
        PdfReportTheme.HEADER_META_SIZE,
        PdfReportTheme.PAGE_MARGIN,
        cursorY);
    cursorY -= PdfReportTheme.LINE_HEIGHT;
    drawText(
        "Generated: " + generatedAt,
        fonts.regular(),
        PdfReportTheme.HEADER_META_SIZE,
        PdfReportTheme.PAGE_MARGIN,
        cursorY);
    drawText(
        "Page " + pageNumber,
        fonts.regular(),
        PdfReportTheme.HEADER_META_SIZE,
        currentPageSize.getWidth()
            - PdfReportTheme.PAGE_MARGIN
            - stringWidth("Page " + pageNumber, fonts.regular(), PdfReportTheme.HEADER_META_SIZE),
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
    ensureSpace(PdfReportTheme.LINE_HEIGHT * 2f);
    drawText(
        heading,
        fonts.bold(),
        PdfReportTheme.SECTION_TITLE_SIZE,
        PdfReportTheme.PAGE_MARGIN,
        cursorY);
    cursorY -= PdfReportTheme.LINE_HEIGHT;
  }

  private void drawTableHeader(List<PdfTableColumn> columns, float[] columnWidths)
      throws IOException {
    float headerHeight =
        tableRowHeight(columns.stream().map(PdfTableColumn::header).toList(), columnWidths);
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
    }
    for (int index = 0; index < row.size(); index++) {
      List<String> lines =
          PdfTextWrapper.wrapText(
              row.get(index),
              header ? fonts.bold() : fonts.regular(),
              PdfReportTheme.SMALL_FONT_SIZE,
              columnWidths[index] - PdfReportTheme.CELL_PADDING * 2f);
      drawCellText(
          lines,
          header ? fonts.bold() : fonts.regular(),
          x,
          rowTop,
          columnWidths[index],
          columns.get(index).alignment());
      x += columnWidths[index];
    }
    strokeHorizontalRule(rowTop - rowHeight, 0);
    cursorY -= rowHeight;
  }

  private void drawCellText(
      List<String> lines,
      PDFont font,
      float cellX,
      float rowTop,
      float cellWidth,
      PdfTableColumn.CellAlignment alignment)
      throws IOException {
    float y = rowTop - PdfReportTheme.CELL_PADDING;
    for (String line : lines) {
      float x =
          switch (alignment) {
            case LEFT -> cellX + PdfReportTheme.CELL_PADDING;
            case RIGHT ->
                cellX
                    + cellWidth
                    - PdfReportTheme.CELL_PADDING
                    - stringWidth(line, font, PdfReportTheme.SMALL_FONT_SIZE);
          };
      drawText(line, font, PdfReportTheme.SMALL_FONT_SIZE, x, y);
      y -= PdfReportTheme.LINE_HEIGHT;
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

  private float tableRowHeight(List<String> row, float[] columnWidths) throws IOException {
    int maxLines = 1;
    for (int index = 0; index < row.size(); index++) {
      maxLines =
          Math.max(
              maxLines,
              PdfTextWrapper.wrapText(
                      row.get(index),
                      fonts.regular(),
                      PdfReportTheme.SMALL_FONT_SIZE,
                      columnWidths[index] - PdfReportTheme.CELL_PADDING * 2f)
                  .size());
    }
    return maxLines * PdfReportTheme.LINE_HEIGHT + PdfReportTheme.CELL_PADDING * 2f;
  }

  private void drawWrappedLines(
      List<String> lines, PDFont font, float fontSize, float x, float topY) throws IOException {
    float y = topY;
    for (String line : lines) {
      drawText(line, font, fontSize, x, y);
      y -= PdfReportTheme.LINE_HEIGHT;
    }
  }

  private void drawText(String text, PDFont font, float fontSize, float x, float y)
      throws IOException {
    activeContentStream().beginText();
    activeContentStream().setFont(font, fontSize);
    activeContentStream().newLineAtOffset(x, y);
    activeContentStream().showText(text);
    activeContentStream().endText();
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

  private PDPageContentStream activeContentStream() {
    return Objects.requireNonNull(contentStream, "contentStream");
  }
}
