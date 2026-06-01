package dev.erst.fingrind.report.pdf;

/** Shared visual constants for FinGrind PDF report rendering. */
final class PdfReportTheme {
  private static final Typography TYPOGRAPHY = new Typography(17f, 8f, 12f, 8.5f, 7.5f, 11f);
  private static final Spacing SPACING = new Spacing(38f, 4.0f, 3.0f, 8f, 6f, 3f, 7f);
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
