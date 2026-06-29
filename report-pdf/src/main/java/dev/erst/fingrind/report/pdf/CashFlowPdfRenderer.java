package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.contract.bookkeeping.CashFlowSection;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementReport;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Renders statements of cash receipts and payments as PDF documents. */
final class CashFlowPdfRenderer {
  private static final String COMPARATIVE_TITLE = "Comparative Cash Receipts And Payments";
  private static final String EMPTY_COMPARATIVE_OUTCOME =
      "No cash-flow lines matched the selected scope.";

  void render(PdfPageWriter pageWriter, CashFlowStatementReport report) throws IOException {
    Objects.requireNonNull(pageWriter, "pageWriter");
    Objects.requireNonNull(report, "report");
    renderSections(pageWriter, report.sections(), "");
    PdfBalanceTableSupport.writeSummaryTable(
        pageWriter, "Opening Cash Totals", report.openingCashTotals());
    PdfBalanceTableSupport.writeSummaryTable(
        pageWriter, "Movement Totals", report.movementTotals());
    PdfBalanceTableSupport.writeSummaryTable(
        pageWriter, "Closing Cash Totals", report.closingCashTotals());
    renderComparative(pageWriter, report);
    pageWriter.writeKeyValueTable(
        "Context",
        PdfStatementMetadataRows.statementParameters(
            report.bookIdentity(),
            report.comparativeEffectiveDateRange(),
            report.postingCoverage(),
            List.of(
                List.of("Effective date from", report.effectiveDateFrom().toString()),
                List.of("Effective date to", report.effectiveDateTo().toString()))));
  }

  private static void renderComparative(PdfPageWriter pageWriter, CashFlowStatementReport report)
      throws IOException {
    if (!hasComparative(report)) {
      return;
    }
    if (!hasComparativeData(report)) {
      List<List<String>> summaryRows =
          new java.util.ArrayList<>(
              List.of(
                  List.of(
                      "Comparative range",
                      PdfTemporalValueFormatter.comparativeRange(
                          report.comparativeEffectiveDateRange())),
                  List.of("Outcome", EMPTY_COMPARATIVE_OUTCOME)));
      List<String> emptySectionLabels = emptySectionLabels(report);
      if (!emptySectionLabels.isEmpty()) {
        summaryRows.add(List.of("Empty sections", String.join(", ", emptySectionLabels)));
      }
      pageWriter.writeKeyValueTable(COMPARATIVE_TITLE, List.copyOf(summaryRows));
      return;
    }
    pageWriter.writeHeading(COMPARATIVE_TITLE);
    renderSections(pageWriter, report.comparativeSections(), "Comparative ");
    if (!report.comparativeOpeningCashTotals().isEmpty()) {
      PdfBalanceTableSupport.writeSummaryTable(
          pageWriter, "Comparative Opening Cash Totals", report.comparativeOpeningCashTotals());
    }
    if (!report.comparativeMovementTotals().isEmpty()) {
      PdfBalanceTableSupport.writeSummaryTable(
          pageWriter, "Comparative Movement Totals", report.comparativeMovementTotals());
    }
    if (!report.comparativeClosingCashTotals().isEmpty()) {
      PdfBalanceTableSupport.writeSummaryTable(
          pageWriter, "Comparative Closing Cash Totals", report.comparativeClosingCashTotals());
    }
  }

  private static void renderSections(
      PdfPageWriter pageWriter, List<CashFlowSection> sections, String titlePrefix)
      throws IOException {
    for (CashFlowSection section : sections) {
      if (section.rows().isEmpty() && section.totals().isEmpty()) {
        continue;
      }
      String sectionTitle =
          titlePrefix + PdfValueFormatter.displayCashFlowSection(section.sectionKind());
      pageWriter.writeTable(
          sectionTitle,
          PdfReportTableLayouts.statementBalanceColumns(),
          section.rows().stream().map(PdfStatementRowRenderers::cashFlowRow).toList());
      if (!section.totals().isEmpty()) {
        PdfBalanceTableSupport.writeSummaryTable(
            pageWriter, sectionTitle + " Totals", section.totals());
      }
    }
  }

  private static boolean hasComparative(CashFlowStatementReport report) {
    return report.comparativeEffectiveDateRange().effectiveDateFrom().isPresent()
        || report.comparativeEffectiveDateRange().effectiveDateTo().isPresent()
        || hasComparativeData(report);
  }

  private static boolean hasComparativeData(CashFlowStatementReport report) {
    return report.comparativeSections().stream().anyMatch(CashFlowPdfRenderer::hasRenderableSection)
        || !report.comparativeOpeningCashTotals().isEmpty()
        || !report.comparativeMovementTotals().isEmpty()
        || !report.comparativeClosingCashTotals().isEmpty();
  }

  private static boolean hasRenderableSection(CashFlowSection section) {
    return !section.rows().isEmpty() || !section.totals().isEmpty();
  }

  private static List<String> emptySectionLabels(CashFlowStatementReport report) {
    return report.comparativeSections().stream()
        .map(section -> PdfValueFormatter.displayCashFlowSection(section.sectionKind()))
        .toList();
  }
}
