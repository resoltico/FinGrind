package dev.erst.fingrind.report.pdf;

/** Shared visual constants for FinGrind PDF report rendering. */
final class PdfReportTheme {
  private static final Typography TYPOGRAPHY =
      new Typography(15.5f, 7.5f, 11f, 8.75f, 7.75f, 10.25f);
  private static final Spacing SPACING = new Spacing(30f, 3.25f, 2.5f, 6f, 4.5f, 2.5f, 5f);
  private static final Grayscale GRAYSCALE = new Grayscale(24, 245, 220);

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
