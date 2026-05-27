package dev.erst.fingrind.report.pdf;

/** Shared visual constants for FinGrind PDF report rendering. */
final class PdfReportTheme {
  private static final Typography TYPOGRAPHY = new Typography(18f, 9f, 13f, 9f, 8f, 12f);
  private static final Spacing SPACING = new Spacing(40f, 4.5f, 3.5f, 10f, 8f, 4f, 8f);
  private static final Grayscale GRAYSCALE = new Grayscale(0, 238, 200);

  private PdfReportTheme() {}

  static Typography typography() {
    return TYPOGRAPHY;
  }

  static Spacing spacing() {
    return SPACING;
  }

  static Grayscale grayscale() {
    return GRAYSCALE;
  }

  record Typography(
      float headerTitleSize,
      float headerMetaSize,
      float sectionTitleSize,
      float bodyFontSize,
      float smallFontSize,
      float lineHeight) {}

  record Spacing(
      float pageMargin,
      float tableCellPadding,
      float keyValueCellPadding,
      float keyValueColumnGap,
      float sectionTopMargin,
      float sectionBottomMargin,
      float sectionAfterTableSpacing) {}

  record Grayscale(int textRgb, int headerFillRgb, int sectionRuleRgb) {
    float normalized(int rgb) {
      if (rgb < 0 || rgb > 255) {
        throw new IllegalArgumentException("RGB gray values must be within 0..255.");
      }
      return rgb / 255f;
    }
  }
}
