package dev.erst.fingrind.report.pdf;

/** Shared visual constants for FinGrind PDF report rendering. */
final class PdfReportTheme {
  static final float PAGE_MARGIN = 40f;
  static final float HEADER_TITLE_SIZE = 18f;
  static final float HEADER_META_SIZE = 9f;
  static final float SECTION_TITLE_SIZE = 13f;
  static final float BODY_FONT_SIZE = 9f;
  static final float SMALL_FONT_SIZE = 8f;
  static final float LINE_HEIGHT = 12f;
  static final float CELL_PADDING = 4f;
  static final int HEADER_FILL_RGB = 238;
  static final int SECTION_RULE_RGB = 200;

  private PdfReportTheme() {}

  static float normalizedGray(int rgb) {
    if (rgb < 0 || rgb > 255) {
      throw new IllegalArgumentException("RGB gray values must be within 0..255.");
    }
    return rgb / 255f;
  }
}
