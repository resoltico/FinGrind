package dev.erst.fingrind.report.pdf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.reportmodel.ReportModel;
import dev.erst.fingrind.contract.reportmodel.ReportSection;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

/** Golden raster and heading-gap coverage for the shared PDF report projector. */
class PdfReportLayoutSnapshotTest {
  private static final float SNAPSHOT_DPI = 96f;
  private static final float LAYOUT_DPI = 144f;
  private static final PdfReportService PDF_REPORT_SERVICE =
      new PdfReportService("FinGrind", "0.59.0", PdfReportFixtureSupport.CLOCK);

  @Test
  void publicPdfReportFamiliesKeepTheirGoldenRasterFingerprints() throws IOException {
    List<String> mismatches = new ArrayList<>();
    for (ReportSnapshotCase reportCase : reportSnapshotCases().values()) {
      String actualHash =
          rasterSnapshotHash(PDF_REPORT_SERVICE.render(reportCase.model()), SNAPSHOT_DPI);
      if (!reportCase.expectedSnapshotHash().equals(actualHash)) {
        mismatches.add(reportCase.name() + "=" + actualHash);
      }
    }
    assertTrue(
        mismatches.isEmpty(),
        () ->
            "Snapshot drift detected. Update expected hashes only after verifying the rendered PDF change.\n"
                + String.join("\n", mismatches));
  }

  @Test
  void headingsStaySeparatedFromThePreviousTableRuleAcrossPublicPdfReportFamilies()
      throws IOException {
    List<String> failures = new ArrayList<>();
    for (ReportSnapshotCase reportCase : reportSnapshotCases().values()) {
      failures.addAll(headingSeparationFailures(reportCase));
    }
    assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
  }

  private static Map<String, ReportSnapshotCase> reportSnapshotCases() {
    return Map.ofEntries(
        Map.entry(
            "account-balance",
            new ReportSnapshotCase(
                "account-balance",
                PdfReportLayoutFixtureModels.sampleAccountBalanceModel(),
                "fc00ff00fffffffef800f800ff007ffe00000000000000000000000000000000")),
        Map.entry(
            "trial-balance",
            new ReportSnapshotCase(
                "trial-balance",
                PdfReportLayoutFixtureModels.sampleTrialBalanceModel(),
                "c0000000e000fffefffefffee000f000fffefffefffed000d000c000f0000000")),
        Map.entry(
            "account-ledger",
            new ReportSnapshotCase(
                "account-ledger",
                PdfReportLayoutFixtureModels.sampleAccountLedgerModel(),
                "e000c000f800fc00fffffffffffef800e000f000e000e000fffe000000000000")),
        Map.entry(
            "period-summary",
            new ReportSnapshotCase(
                "period-summary",
                PdfReportLayoutFixtureModels.samplePeriodSummaryModel(),
                "e000c000e000c000fffffffffffffffefffef800e000f0007ffe000000000000")),
        Map.entry(
            "financial-position",
            new ReportSnapshotCase(
                "financial-position",
                PdfReportLayoutFixtureModels.sampleFinancialPositionModel(),
                "e00000000000fffefffefffefffefffefffef000d000fffefffefffef08c0000")),
        Map.entry(
            "income-statement",
            new ReportSnapshotCase(
                "income-statement",
                PdfReportLayoutFixtureModels.sampleIncomeStatementModel(),
                "e00000008000fffefffefffefffefffef000c000fffefffefffffffe80100000")),
        Map.entry(
            "cash-flow-statement",
            new ReportSnapshotCase(
                "cash-flow-statement",
                PdfReportLayoutFixtureModels.sampleCashFlowStatementModel(),
                "f0000000e000c000dc00fffefffefffefffefffefffef800ee00fe00fffe0880")),
        Map.entry(
            "changes-in-equity",
            new ReportSnapshotCase(
                "changes-in-equity",
                PdfReportLayoutFixtureModels.sampleChangesInEquityModel(),
                "e0008000a000fc00fffefffefc00fe00d000fe00ff00fffefe00ff0080000000")));
  }

  private static List<String> expectedHeadings(ReportModel model) {
    List<String> headings = new ArrayList<>();
    int firstSectionIndex = model.verdicts().isEmpty() ? 1 : 0;
    List<ReportSection> sections = model.sections();
    for (int index = Math.min(firstSectionIndex, sections.size());
        index < sections.size();
        index++) {
      ReportSection section = sections.get(index);
      headings.add(section.title());
      section.totals().forEach(totals -> headings.add(totals.title()));
    }
    headings.add("Context");
    return headings.stream().distinct().toList();
  }

  @SuppressWarnings("UnnecessaryAsync")
  private static List<String> headingSeparationFailures(ReportSnapshotCase reportCase)
      throws IOException {
    List<String> failures = new ArrayList<>();
    byte[] pdfBytes = PDF_REPORT_SERVICE.render(reportCase.model());
    float scale = LAYOUT_DPI / 72f;
    int minimumGapPixels =
        Math.max(
            1,
            Math.round(
                    (PdfReportTheme.spacing().sectionTopMargin()
                            + PdfReportTheme.spacing().sectionAfterTableSpacing())
                        * scale)
                - 2);
    List<String> expectedHeadings = expectedHeadings(reportCase.model());
    try (PDDocument document = Loader.loadPDF(pdfBytes)) {
      PDFRenderer renderer = new PDFRenderer(document);
      Map<Integer, BufferedImage> pageImages = new ConcurrentHashMap<>();
      Map<String, HeadingPlacement> headingPlacements =
          headingPlacements(document, expectedHeadings);
      List<String> missingHeadings =
          expectedHeadings.stream()
              .filter(heading -> !headingPlacements.containsKey(heading))
              .toList();
      if (!missingHeadings.isEmpty()) {
        failures.add(reportCase.name() + ": missing heading placements for " + missingHeadings);
        return failures;
      }
      for (String heading : expectedHeadings) {
        HeadingPlacement placement =
            java.util.Objects.requireNonNull(headingPlacements.get(heading), heading);
        BufferedImage pageImage =
            pageImages.computeIfAbsent(
                placement.pageIndex(),
                pageIndex -> renderPageImage(renderer, pageIndex, LAYOUT_DPI));
        int headingTopPixel = Math.max(0, Math.round(placement.topOffset() * scale));
        int rulePixel = nearestHorizontalRuleAbove(pageImage, headingTopPixel);
        if (rulePixel < 0) {
          failures.add(
              reportCase.name()
                  + " / "
                  + heading
                  + " / page "
                  + placement.pageNumber()
                  + ": no prior rule detected");
          continue;
        }
        int gapPixels = headingTopPixel - rulePixel;
        if (gapPixels < minimumGapPixels) {
          failures.add(
              reportCase.name()
                  + " / "
                  + heading
                  + " / page "
                  + placement.pageNumber()
                  + ": gap "
                  + gapPixels
                  + "px < "
                  + minimumGapPixels
                  + "px");
        }
      }
    }
    return failures;
  }

  private static Map<String, HeadingPlacement> headingPlacements(
      PDDocument document, List<String> headings) throws IOException {
    HeadingPositionStripper stripper = new HeadingPositionStripper(headings);
    stripper.getText(document);
    return stripper.headingPlacements();
  }

  private static BufferedImage firstPageImage(byte[] pdfBytes, float dpi) throws IOException {
    try (PDDocument document = Loader.loadPDF(pdfBytes)) {
      return new PDFRenderer(document).renderImageWithDPI(0, dpi, ImageType.GRAY);
    }
  }

  private static BufferedImage renderPageImage(PDFRenderer renderer, int pageIndex, float dpi) {
    try {
      return renderer.renderImageWithDPI(pageIndex, dpi, ImageType.GRAY);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to render PDF page " + (pageIndex + 1) + ".", exception);
    }
  }

  private static String rasterSnapshotHash(byte[] pdfBytes, float dpi) throws IOException {
    return rasterHash(firstPageImage(pdfBytes, dpi), 16, 16);
  }

  private static String rasterHash(BufferedImage image, int gridWidth, int gridHeight) {
    double[][] cells = new double[gridHeight][gridWidth];
    double total = 0d;
    for (int row = 0; row < gridHeight; row++) {
      int startY = row * image.getHeight() / gridHeight;
      int endY = (row + 1) * image.getHeight() / gridHeight;
      for (int column = 0; column < gridWidth; column++) {
        int startX = column * image.getWidth() / gridWidth;
        int endX = (column + 1) * image.getWidth() / gridWidth;
        double average = averageGray(image, startX, endX, startY, endY);
        cells[row][column] = average;
        total += average;
      }
    }
    double threshold = total / (gridWidth * gridHeight);
    StringBuilder hex = new StringBuilder();
    int nibble = 0;
    int nibbleBitCount = 0;
    for (int row = 0; row < gridHeight; row++) {
      for (int column = 0; column < gridWidth; column++) {
        nibble <<= 1;
        if (cells[row][column] < threshold) {
          nibble |= 1;
        }
        nibbleBitCount++;
        if (nibbleBitCount == 4) {
          hex.append(Integer.toHexString(nibble));
          nibble = 0;
          nibbleBitCount = 0;
        }
      }
    }
    return hex.toString();
  }

  private static double averageGray(
      BufferedImage image, int startX, int endX, int startY, int endY) {
    long sum = 0L;
    long count = 0L;
    for (int y = startY; y < endY; y++) {
      for (int x = startX; x < endX; x++) {
        sum += image.getRGB(x, y) & 0xff;
        count++;
      }
    }
    return count == 0L ? 255d : (double) sum / count;
  }

  private static int nearestHorizontalRuleAbove(BufferedImage image, int startRow) {
    float scale = LAYOUT_DPI / 72f;
    int leftMargin = Math.round(PdfReportTheme.spacing().pageMargin() * scale);
    int rightMargin = image.getWidth() - leftMargin;
    int minimumRunLength = Math.round((rightMargin - leftMargin) * 0.55f);
    for (int row = Math.min(startRow - 1, image.getHeight() - 1); row >= 0; row--) {
      int longestRun = longestHorizontalRuleRun(image, row, leftMargin, rightMargin);
      if (longestRun >= minimumRunLength) {
        return row;
      }
    }
    return -1;
  }

  private static int longestHorizontalRuleRun(
      BufferedImage image, int row, int leftMargin, int rightMargin) {
    int longestRun = 0;
    int currentRun = 0;
    for (int column = leftMargin; column < rightMargin; column++) {
      if (hasRuleInk(image, row, column)) {
        currentRun++;
        longestRun = Math.max(longestRun, currentRun);
      } else {
        currentRun = 0;
      }
    }
    return longestRun;
  }

  private static boolean hasRuleInk(BufferedImage image, int row, int column) {
    for (int candidateRow = Math.max(0, row - 1);
        candidateRow <= Math.min(image.getHeight() - 1, row + 1);
        candidateRow++) {
      if ((image.getRGB(column, candidateRow) & 0xff) < 250) {
        return true;
      }
    }
    return false;
  }

  /** Snapshot case tying one public report family to its rendered fingerprint expectation. */
  private record ReportSnapshotCase(String name, ReportModel model, String expectedSnapshotHash) {}

  private record HeadingPlacement(int pageNumber, float topOffset) {
    private int pageIndex() {
      return pageNumber - 1;
    }
  }

  /** Extracts expected heading positions from the rendered PDF text layer. */
  private static final class HeadingPositionStripper extends PDFTextStripper {
    private final Map<String, String> normalizedHeadings;

    @SuppressWarnings("UnnecessaryAsync")
    private final Map<String, HeadingPlacement> headingPlacements = new ConcurrentHashMap<>();

    private HeadingPositionStripper(List<String> headings) throws IOException {
      super();
      this.normalizedHeadings =
          headings.stream()
              .collect(
                  java.util.stream.Collectors.toMap(
                      PdfReportLayoutSnapshotTest::normalizeWhitespace,
                      heading -> heading,
                      (left, right) -> left,
                      LinkedHashMap::new));
      setSortByPosition(true);
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
      String normalizedText = normalizeWhitespace(text);
      String heading = normalizedHeadings.get(normalizedText);
      float largestFontSize =
          textPositions.stream().map(TextPosition::getFontSizeInPt).max(Float::compare).orElse(0f);
      if (heading != null
          && !headingPlacements.containsKey(heading)
          && largestFontSize >= PdfReportTheme.typography().sectionTitleSize() - 0.25f) {
        float topOffset =
            textPositions.stream().map(TextPosition::getYDirAdj).min(Float::compare).orElse(0f);
        headingPlacements.put(heading, new HeadingPlacement(getCurrentPageNo(), topOffset));
      }
      super.writeString(text, textPositions);
    }

    private Map<String, HeadingPlacement> headingPlacements() {
      return Map.copyOf(headingPlacements);
    }
  }

  private static String normalizeWhitespace(String value) {
    return value.replaceAll("\\s+", " ").trim();
  }
}
