package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.contract.bookkeeping.CashFlowRow;
import dev.erst.fingrind.contract.bookkeeping.CashFlowSection;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementReport;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Builds the shared report model for cash-flow statements. */
public final class CashFlowStatementReportModelBuilder
    implements ReportModelBuilder<CashFlowStatementReport> {
  /** Shared reusable builder instance. */
  public static final CashFlowStatementReportModelBuilder INSTANCE =
      new CashFlowStatementReportModelBuilder();

  private CashFlowStatementReportModelBuilder() {}

  @Override
  public ReportModel build(CashFlowStatementReport report) {
    return buildModel(report);
  }

  /** Builds one cash-flow statement report model. */
  public static ReportModel buildModel(CashFlowStatementReport report) {
    List<ReportSection> sections = new ArrayList<>();
    boolean hasComparativeReference =
        report.comparativeEffectiveDateRange().effectiveDateFrom().isPresent()
            || report.comparativeEffectiveDateRange().effectiveDateTo().isPresent();
    sections.add(
        summarySection(
            "currentSummary",
            "Current Summary",
            null,
            report.openingCashTotals(),
            report.movementTotals(),
            report.closingCashTotals(),
            emptySectionLabels(report.sections()),
            renderableSectionLabels(report.sections())));
    sections.addAll(renderSections("current", "", report.sections()));
    if (hasComparativeReference
        || !report.comparativeSections().isEmpty()
        || !report.comparativeOpeningCashTotals().isEmpty()
        || !report.comparativeMovementTotals().isEmpty()
        || !report.comparativeClosingCashTotals().isEmpty()) {
      sections.add(
          summarySection(
              "comparativeSummary",
              "Comparative Cash Receipts And Payments",
              report.comparativeEffectiveDateRange(),
              report.comparativeOpeningCashTotals(),
              report.comparativeMovementTotals(),
              report.comparativeClosingCashTotals(),
              emptySectionLabels(report.comparativeSections()),
              renderableSectionLabels(report.comparativeSections())));
      sections.addAll(renderSections("comparative", "Comparative ", report.comparativeSections()));
    }
    return new ReportModel(
        dev.erst.fingrind.contract.protocol.OperationId.CASH_FLOW_STATEMENT.wireName(),
        "Cash Receipts And Payments",
        ReportModel.Orientation.LANDSCAPE,
        ReportModelSupport.context(
            report.bookIdentity(),
            report.postingCoverage(),
            report.effectiveDateFrom(),
            report.effectiveDateTo(),
            null,
            report.comparativeEffectiveDateRange(),
            List.of()),
        List.of(
            new ReportVerdict("Period start", report.effectiveDateFrom().toString()),
            new ReportVerdict("Period end", report.effectiveDateTo().toString())),
        List.copyOf(sections));
  }

  private static ReportSection summarySection(
      String key,
      String title,
      @Nullable EffectiveDateRange comparativeRange,
      List<CurrencyBalance> opening,
      List<CurrencyBalance> movement,
      List<CurrencyBalance> closing,
      List<String> emptySections,
      List<String> sectionsWithData) {
    boolean comparative = comparativeRange != null;
    List<ReportVerdict> verdicts = new ArrayList<>();
    if (comparative) {
      EffectiveDateRange comparativeWindow =
          java.util.Objects.requireNonNull(comparativeRange, "comparativeRange");
      verdicts.add(
          new ReportVerdict(
              "Comparative reference", ReportModelNarrative.comparativeRange(comparativeWindow)));
      if (!opening.isEmpty()) {
        verdicts.add(
            new ReportVerdict(
                "Comparative Opening Cash Totals",
                ReportModelNarrative.joinedBalancesText(opening)));
      }
      if (!movement.isEmpty()) {
        verdicts.add(
            new ReportVerdict(
                "Comparative Movement Totals", ReportModelNarrative.joinedBalancesText(movement)));
      }
      if (!closing.isEmpty()) {
        verdicts.add(
            new ReportVerdict(
                "Comparative Closing Cash Totals",
                ReportModelNarrative.joinedBalancesText(closing)));
      }
    } else {
      verdicts.add(
          new ReportVerdict(
              "Opening Cash Totals", ReportModelNarrative.joinedBalancesText(opening)));
      verdicts.add(
          new ReportVerdict("Movement Totals", ReportModelNarrative.joinedBalancesText(movement)));
      verdicts.add(
          new ReportVerdict(
              "Closing Cash Totals", ReportModelNarrative.joinedBalancesText(closing)));
    }
    if (!sectionsWithData.isEmpty()) {
      verdicts.add(new ReportVerdict("Sections with data", String.join(", ", sectionsWithData)));
    }
    if (!emptySections.isEmpty()) {
      verdicts.add(new ReportVerdict("Empty sections", String.join(", ", emptySections)));
    }
    if (sectionsWithData.isEmpty() && movement.isEmpty()) {
      verdicts.add(new ReportVerdict("Outcome", ReportModelNarrative.noMatches("cash-flow lines")));
    }
    return ReportModelSupport.section(
        key, title, List.copyOf(verdicts), List.of(), List.of(), List.of());
  }

  private static List<ReportSection> renderSections(
      String sectionPrefix, String titlePrefix, List<CashFlowSection> sections) {
    return sections.stream()
        .filter(section -> !section.rows().isEmpty() || !section.totals().isEmpty())
        .map(
            section ->
                ReportModelSupport.section(
                    sectionPrefix + "-" + section.sectionKind().wireValue(),
                    titlePrefix
                        + ReportModelClassificationDisplay.displayCashFlowSection(
                            section.sectionKind()),
                    List.of(),
                    sectionColumns(),
                    section.rows().stream()
                        .map(CashFlowStatementReportModelBuilder::sectionRow)
                        .toList(),
                    section.totals().isEmpty()
                        ? List.of()
                        : List.of(
                            ReportModelSupport.totals(
                                sectionPrefix + "-" + section.sectionKind().wireValue() + "-totals",
                                titlePrefix
                                    + ReportModelClassificationDisplay.displayCashFlowSection(
                                        section.sectionKind())
                                    + " Totals",
                                ReportModelSupport.balanceColumns(),
                                ReportModelSupport.balanceRows(section.totals())))))
        .toList();
  }

  private static List<ReportColumn> sectionColumns() {
    return List.of(
        ReportModelSupport.leftColumn("lineCode", "Line code"),
        ReportModelSupport.leftColumn("lineName", "Line name"),
        ReportModelSupport.leftColumn("classification", "Classification"),
        ReportModelSupport.rightColumn("netAmount", "Net amount"),
        ReportModelSupport.leftColumn("balanceSide", "Balance side"));
  }

  private static ReportRow sectionRow(CashFlowRow row) {
    return ReportModelSupport.row(
        row.lineCode() + ":" + row.movement().netAmount().currencyUnit().code(),
        ReportModelDisplay.displayStatementLineCode(row.lineCode(), row.lineKind()),
        row.lineName(),
        ReportModelClassificationDisplay.displayCashFlowClassification(
            row.lineType(),
            row.financialPositionLineClassification(),
            row.profitAndLossLineClassification()),
        ReportModelDisplay.displayMoney(row.movement().netAmount()),
        ReportModelDisplay.displayBalanceSide(row.movement().balanceSide()));
  }

  private static List<String> emptySectionLabels(List<CashFlowSection> sections) {
    return sections.stream()
        .filter(section -> section.rows().isEmpty() && section.totals().isEmpty())
        .map(
            section ->
                ReportModelClassificationDisplay.displayCashFlowSection(section.sectionKind()))
        .toList();
  }

  private static List<String> renderableSectionLabels(List<CashFlowSection> sections) {
    return sections.stream()
        .filter(section -> !section.rows().isEmpty() || !section.totals().isEmpty())
        .map(
            section ->
                ReportModelClassificationDisplay.displayCashFlowSection(section.sectionKind()))
        .toList();
  }
}
