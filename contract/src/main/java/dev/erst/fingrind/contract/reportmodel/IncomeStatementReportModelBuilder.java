package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.contract.bookkeeping.IncomeStatementGrossProfitSupport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementPresentationSupport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementPresentationSupport.PresentationSection;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementPresentationSupport.SectionCode;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import java.util.ArrayList;
import java.util.List;

/** Builds the shared report model for income statements. */
public final class IncomeStatementReportModelBuilder
    implements ReportModelBuilder<IncomeStatementReport> {
  /** Shared reusable builder instance. */
  public static final IncomeStatementReportModelBuilder INSTANCE =
      new IncomeStatementReportModelBuilder();

  private IncomeStatementReportModelBuilder() {}

  @Override
  public ReportModel build(IncomeStatementReport report) {
    return buildModel(report);
  }

  /** Builds one income-statement report model. */
  public static ReportModel buildModel(IncomeStatementReport report) {
    List<ReportSection> sections = new ArrayList<>();
    List<dev.erst.fingrind.core.CurrencyBalance> currentGrossProfitTotals =
        IncomeStatementGrossProfitSupport.grossProfitTotals(report);
    List<dev.erst.fingrind.core.CurrencyBalance> comparativeGrossProfitTotals =
        IncomeStatementGrossProfitSupport.comparativeGrossProfitTotals(report);
    List<PresentationSection> currentPresentationSections =
        IncomeStatementPresentationSupport.currentSections(report);
    List<String> currentSectionsWithData = renderableSectionLabels(currentPresentationSections);
    List<String> currentEmptySections = emptySectionLabels(currentPresentationSections);
    sections.addAll(
        renderSections(
            "current",
            "",
            currentPresentationSections,
            currentGrossProfitTotals,
            report.netIncomeTotals()));
    boolean hasComparativeReference =
        report.comparativeEffectiveDateRange().effectiveDateFrom().isPresent()
            || report.comparativeEffectiveDateRange().effectiveDateTo().isPresent();
    if (hasComparativeReference
        || !report.comparativeSections().isEmpty()
        || !report.comparativeNetIncomeTotals().isEmpty()) {
      List<PresentationSection> comparativePresentationSections =
          IncomeStatementPresentationSupport.comparativeSections(report);
      List<String> comparativeSectionsWithData =
          renderableSectionLabels(comparativePresentationSections);
      List<String> comparativeEmptySections = emptySectionLabels(comparativePresentationSections);
      sections.add(
          ReportModelSupport.section(
              "comparativeSummary",
              "Comparative Income Statement",
              comparativeVerdicts(report, comparativeSectionsWithData, comparativeEmptySections),
              List.of(),
              List.of(),
              List.of()));
      sections.addAll(
          renderSections(
              "comparative",
              "Comparative ",
              comparativePresentationSections,
              comparativeGrossProfitTotals,
              report.comparativeNetIncomeTotals()));
    }
    List<ReportVerdict> verdicts = new ArrayList<>();
    verdicts.add(new ReportVerdict("Period start", report.effectiveDateFrom().toString()));
    verdicts.add(new ReportVerdict("Period end", report.effectiveDateTo().toString()));
    verdicts.add(
        currentSectionsWithData.isEmpty()
            ? new ReportVerdict("Outcome", ReportModelNarrative.noMatches("income statement lines"))
            : new ReportVerdict("Sections with data", String.join(", ", currentSectionsWithData)));
    if (!currentEmptySections.isEmpty()) {
      verdicts.add(new ReportVerdict("Empty sections", String.join(", ", currentEmptySections)));
    }
    return new ReportModel(
        dev.erst.fingrind.contract.protocol.OperationId.INCOME_STATEMENT.wireName(),
        "Income Statement",
        ReportModel.Orientation.LANDSCAPE,
        ReportModelSupport.context(
            report.bookIdentity(),
            report.postingCoverage(),
            report.effectiveDateFrom(),
            report.effectiveDateTo(),
            null,
            report.comparativeEffectiveDateRange(),
            List.of()),
        List.copyOf(verdicts),
        List.copyOf(sections));
  }

  private static List<ReportVerdict> comparativeVerdicts(
      IncomeStatementReport report,
      List<String> comparativeSectionsWithData,
      List<String> comparativeEmptySections) {
    List<ReportVerdict> verdicts = new ArrayList<>();
    verdicts.add(
        new ReportVerdict(
            "Comparative reference",
            ReportModelNarrative.comparativeRange(report.comparativeEffectiveDateRange())));
    verdicts.add(
        new ReportVerdict(
            "Period start",
            report
                .comparativeEffectiveDateRange()
                .effectiveDateFrom()
                .map(java.time.LocalDate::toString)
                .orElse(report.effectiveDateFrom().toString())));
    verdicts.add(
        new ReportVerdict(
            "Period end",
            report
                .comparativeEffectiveDateRange()
                .effectiveDateTo()
                .map(java.time.LocalDate::toString)
                .orElse(report.effectiveDateTo().toString())));
    if (comparativeSectionsWithData.isEmpty() && report.comparativeNetIncomeTotals().isEmpty()) {
      verdicts.add(
          new ReportVerdict("Outcome", ReportModelNarrative.noMatches("income statement lines")));
    } else if (!comparativeSectionsWithData.isEmpty()) {
      verdicts.add(
          new ReportVerdict("Sections with data", String.join(", ", comparativeSectionsWithData)));
    }
    if (!comparativeEmptySections.isEmpty()) {
      verdicts.add(
          new ReportVerdict("Empty sections", String.join(", ", comparativeEmptySections)));
    }
    return List.copyOf(verdicts);
  }

  private static List<ReportSection> renderSections(
      String sectionPrefix,
      String titlePrefix,
      List<PresentationSection> sections,
      List<dev.erst.fingrind.core.CurrencyBalance> grossProfitTotals,
      List<dev.erst.fingrind.core.CurrencyBalance> netIncomeTotals) {
    List<ReportSection> rendered = new ArrayList<>();
    boolean grossProfitInserted = false;
    boolean hasCostOfSalesSection =
        sections.stream()
            .anyMatch(
                section ->
                    section.sectionCode() == SectionCode.COST_OF_SALES
                        && section.hasRenderableContent());
    for (PresentationSection section : sections) {
      if (section.hasRenderableContent()) {
        rendered.add(
            ReportStatementModelSupport.statementSection(
                sectionPrefix + "-" + section.sectionCode().wireValue(),
                titlePrefix + section.title(),
                sectionRows(section.rows()),
                section.totals()));
      }
      grossProfitInserted =
          insertGrossProfitSectionIfNeeded(
              rendered,
              sectionPrefix,
              titlePrefix,
              section,
              hasCostOfSalesSection,
              grossProfitInserted,
              grossProfitTotals);
    }
    rendered.add(
        ReportModelSupport.section(
            sectionPrefix + "-net-income",
            titlePrefix + "Net Income Totals",
            netIncomeTotals.isEmpty()
                ? List.of(
                    new ReportVerdict(
                        "Outcome", ReportModelNarrative.joinedBalancesText(netIncomeTotals)))
                : List.of(),
            ReportModelSupport.balanceColumns(),
            ReportModelSupport.balanceRows(netIncomeTotals),
            List.of()));
    return List.copyOf(rendered);
  }

  static boolean insertGrossProfitSectionIfNeeded(
      List<ReportSection> rendered,
      String sectionPrefix,
      String titlePrefix,
      PresentationSection section,
      boolean hasCostOfSalesSection,
      boolean grossProfitInserted,
      List<dev.erst.fingrind.core.CurrencyBalance> grossProfitTotals) {
    if (grossProfitInserted || grossProfitTotals.isEmpty()) {
      return grossProfitInserted;
    }
    if (section.sectionCode() == SectionCode.COST_OF_SALES && section.hasRenderableContent()) {
      return addGrossProfitSection(rendered, sectionPrefix, titlePrefix, grossProfitTotals);
    }
    if (!hasCostOfSalesSection
        && section.sectionCode() == SectionCode.REVENUE
        && section.hasRenderableContent()) {
      return addGrossProfitSection(rendered, sectionPrefix, titlePrefix, grossProfitTotals);
    }
    return false;
  }

  private static boolean addGrossProfitSection(
      List<ReportSection> rendered,
      String sectionPrefix,
      String titlePrefix,
      List<dev.erst.fingrind.core.CurrencyBalance> grossProfitTotals) {
    rendered.add(
        ReportModelSupport.section(
            sectionPrefix + "-gross-profit",
            titlePrefix + "Gross Profit",
            List.of(),
            ReportModelSupport.balanceColumns(),
            ReportModelSupport.balanceRows(grossProfitTotals),
            List.of()));
    return true;
  }

  private static List<String> renderableSectionLabels(List<PresentationSection> sections) {
    return sections.stream()
        .filter(PresentationSection::hasRenderableContent)
        .map(PresentationSection::title)
        .toList();
  }

  private static List<String> emptySectionLabels(List<PresentationSection> sections) {
    return sections.stream()
        .filter(section -> !section.hasRenderableContent())
        .map(PresentationSection::title)
        .toList();
  }

  private static List<ReportRow> sectionRows(
      List<dev.erst.fingrind.contract.bookkeeping.IncomeStatementRow> rows) {
    return rows.stream()
        .map(
            row ->
                ReportStatementModelSupport.statementSectionRow(
                    row.lineCode(),
                    row.movement().netAmount().currencyUnit().code(),
                    row.lineName(),
                    row.lineKind(),
                    ReportModelClassificationDisplay.displayProfitAndLossClassification(
                        row.lineClassification()),
                    row.movement().netAmount(),
                    row.movement().balanceSide()))
        .toList();
  }
}
