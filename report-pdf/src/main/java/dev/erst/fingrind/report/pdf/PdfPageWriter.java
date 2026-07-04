package dev.erst.fingrind.report.pdf;

import java.io.IOException;
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
    float labelWidth = PdfTableTextSupport.keyValueLabelWidth(this, rows);
    float valueWidth = contentWidth() - labelWidth - PdfReportTheme.spacing().keyValueColumnGap();
    for (List<String> row : rows) {
      PdfTableTextSupport.TextBlockMetrics labelBlock =
          PdfTableTextSupport.textBlock(
              PdfTextWrapper.wrapText(
                  row.getFirst(),
                  fonts.bold(),
                  PdfReportTheme.typography().bodyFontSize(),
                  labelWidth),
              fonts.bold(),
              PdfReportTheme.typography().bodyFontSize());
      PdfTableTextSupport.TextBlockMetrics valueBlock =
          PdfTableTextSupport.textBlock(
              PdfTextWrapper.wrapText(
                  row.get(1),
                  fonts.regular(),
                  PdfReportTheme.typography().bodyFontSize(),
                  valueWidth),
              fonts.regular(),
              PdfReportTheme.typography().bodyFontSize());
      float rowHeight =
          Math.max(labelBlock.height(), valueBlock.height())
              + PdfReportTheme.spacing().keyValueCellPadding() * 2f;
      ensureSpace(rowHeight);
      float rowTop = cursorY;
      PdfTableTextSupport.drawTextBlock(
          this,
          labelBlock,
          PdfReportTheme.spacing().pageMargin(),
          rowTop,
          rowHeight,
          labelWidth,
          PdfTableColumn.CellAlignment.LEFT,
          PdfReportTheme.spacing().keyValueCellPadding());
      PdfTableTextSupport.drawTextBlock(
          this,
          valueBlock,
          PdfReportTheme.spacing().pageMargin()
              + labelWidth
              + PdfReportTheme.spacing().keyValueColumnGap(),
          rowTop,
          rowHeight,
          valueWidth,
          PdfTableColumn.CellAlignment.LEFT,
          PdfReportTheme.spacing().keyValueCellPadding());
      cursorY -= rowHeight;
    }
    strokeHorizontalRule(cursorY, 0);
    cursorY -= PdfReportTheme.spacing().sectionAfterTableSpacing();
  }

  void writeTable(String heading, List<PdfTableColumn> columns, List<List<String>> rows)
      throws IOException {
    writeSectionHeading(heading);
    float[] columnWidths = PdfTableTextSupport.columnWidths(this, columns);
    drawTableHeader(columns, columnWidths);
    for (List<String> row : rows) {
      float rowHeight =
          PdfTableTextSupport.tableRowHeight(row, columnWidths, fonts.regular(), this);
      if (cursorY - rowHeight < PdfReportTheme.spacing().pageMargin()) {
        startNewPage();
        drawTableHeader(columns, columnWidths);
      }
      drawTableRow(row, columns, columnWidths, rowHeight, false);
    }
    cursorY -= PdfReportTheme.spacing().sectionAfterTableSpacing();
  }

  @Override
  public void close() throws IOException {
    closeActiveContentStream();
    PdfPageLabelAppender.appendPageLabels(document, currentPageSize, fonts);
  }

  private void startNewPage() throws IOException {
    closeActiveContentStream();
    PDPage page = new PDPage(currentPageSize);
    document.addPage(page);
    contentStream = new PDPageContentStream(document, page);
    cursorY = currentPageSize.getHeight() - PdfReportTheme.spacing().pageMargin();
    drawMasthead();
  }

  private void drawMasthead() throws IOException {
    drawText(
        reportTitle,
        fonts.bold(),
        PdfReportTheme.typography().headerTitleSize(),
        PdfReportTheme.spacing().pageMargin(),
        cursorY);
    cursorY -= PdfReportTheme.typography().lineHeight() + 2f;
    drawText(
        "Generated "
            + PdfTemporalValueFormatter.instant(generatedAt)
            + " / Prepared by "
            + preparedBy,
        fonts.regular(),
        PdfReportTheme.typography().headerMetaSize(),
        PdfReportTheme.spacing().pageMargin(),
        cursorY);
    cursorY -= PdfReportTheme.typography().lineHeight() / 2f;
    strokeHorizontalRule(cursorY, PdfReportTheme.grayscale().sectionRuleRgb());
    cursorY -= PdfReportTheme.typography().lineHeight();
  }

  private void writeSectionHeading(String heading) throws IOException {
    float sectionTitleAscent =
        fontAscent(fonts.bold(), PdfReportTheme.typography().sectionTitleSize());
    ensureSpace(
        PdfReportTheme.typography().lineHeight() * 2f
            + PdfReportTheme.spacing().sectionTopMargin()
            + PdfReportTheme.spacing().sectionBottomMargin());
    cursorY -= PdfReportTheme.spacing().sectionTopMargin();
    drawText(
        heading,
        fonts.bold(),
        PdfReportTheme.typography().sectionTitleSize(),
        PdfReportTheme.spacing().pageMargin(),
        cursorY - sectionTitleAscent);
    cursorY -=
        PdfReportTheme.typography().lineHeight() + PdfReportTheme.spacing().sectionBottomMargin();
  }

  private static float fontAscent(PDFont font, float fontSize) {
    var descriptor = Objects.requireNonNull(font, "font").getFontDescriptor();
    float ascent = descriptor == null ? 750f : descriptor.getAscent();
    return (ascent / 1000f) * fontSize;
  }

  private void drawTableHeader(List<PdfTableColumn> columns, float[] columnWidths)
      throws IOException {
    float headerHeight =
        PdfTableTextSupport.tableRowHeight(
            columns.stream().map(PdfTableColumn::header).toList(),
            columnWidths,
            fonts.bold(),
            this);
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
    float x = PdfReportTheme.spacing().pageMargin();
    if (header) {
      float normalizedHeaderGray =
          PdfReportTheme.grayscale().normalized(PdfReportTheme.grayscale().headerFillRgb());
      activeContentStream()
          .setNonStrokingColor(normalizedHeaderGray, normalizedHeaderGray, normalizedHeaderGray);
      activeContentStream().addRect(x, rowTop - rowHeight, contentWidth(), rowHeight);
      activeContentStream().fill();
      strokeHorizontalRule(rowTop, 0);
    }
    PDFont rowFont = header ? fonts.bold() : fonts.regular();
    for (int index = 0; index < row.size(); index++) {
      PdfTableTextSupport.TextBlockMetrics block =
          PdfTableTextSupport.textBlock(
              PdfTextWrapper.wrapText(
                  row.get(index),
                  rowFont,
                  PdfReportTheme.typography().smallFontSize(),
                  columnWidths[index] - PdfReportTheme.spacing().tableCellPadding() * 2f),
              rowFont,
              PdfReportTheme.typography().smallFontSize());
      PdfTableTextSupport.drawTextBlock(
          this,
          block,
          x,
          rowTop,
          rowHeight,
          columnWidths[index],
          columns.get(index).alignment(),
          PdfReportTheme.spacing().tableCellPadding());
      x += columnWidths[index];
    }
    strokeHorizontalRule(rowTop - rowHeight, 0);
    cursorY -= rowHeight;
  }

  void drawText(String text, PDFont font, float fontSize, float x, float y) throws IOException {
    PdfPageTextPainter.drawText(activeContentStream(), text, font, fontSize, x, y);
  }

  private void strokeHorizontalRule(float y, int gray) throws IOException {
    float normalizedGray = PdfReportTheme.grayscale().normalized(gray);
    activeContentStream().setStrokingColor(normalizedGray, normalizedGray, normalizedGray);
    activeContentStream().moveTo(PdfReportTheme.spacing().pageMargin(), y);
    activeContentStream()
        .lineTo(currentPageSize.getWidth() - PdfReportTheme.spacing().pageMargin(), y);
    activeContentStream().stroke();
  }

  private void ensureSpace(float height) throws IOException {
    if (cursorY - height < PdfReportTheme.spacing().pageMargin()) {
      startNewPage();
    }
  }

  float contentWidth() {
    return currentPageSize.getWidth() - PdfReportTheme.spacing().pageMargin() * 2f;
  }

  private void closeActiveContentStream() throws IOException {
    if (contentStream != null) {
      contentStream.close();
      contentStream = null;
    }
  }

  private PDPageContentStream activeContentStream() {
    return Objects.requireNonNull(contentStream, "contentStream");
  }

  PdfFonts fonts() {
    return fonts;
  }
}
