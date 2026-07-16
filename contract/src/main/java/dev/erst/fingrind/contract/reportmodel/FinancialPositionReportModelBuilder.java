package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.core.BalanceSide;
import java.util.ArrayList;
import java.util.List;

/** Builds the shared report model for financial-position statements. */
public final class FinancialPositionReportModelBuilder
    implements ReportModelBuilder<FinancialPositionReport> {
  /** Shared reusable builder instance. */
  public static final FinancialPositionReportModelBuilder INSTANCE =
      new FinancialPositionReportModelBuilder();

  private FinancialPositionReportModelBuilder() {}

  @Override
  public ReportModel build(FinancialPositionReport report) {
    return buildModel(report);
  }

  /** Builds one financial-position report model. */
  public static ReportModel buildModel(FinancialPositionReport report) {
    List<ReportSection> sections = new ArrayList<>();
    List<String> currentSectionsWithData =
        ReportStatementModelSupport.renderableAccountTypeSectionLabels(
            report.sections(),
            section ->
                ReportStatementModelSupport.hasRenderableContent(section.rows(), section.totals()),
            FinancialPositionSection::accountType);
    List<String> currentEmptySections =
        ReportStatementModelSupport.emptyAccountTypeSectionLabels(
            report.sections(),
            section ->
                ReportStatementModelSupport.hasRenderableContent(section.rows(), section.totals()),
            FinancialPositionSection::accountType);
    sections.addAll(renderSections("current", "", report.sections()));
    boolean hasComparativeReference =
        report.comparativeEffectiveDateRange().effectiveDateFrom().isPresent()
            || report.comparativeEffectiveDateRange().effectiveDateTo().isPresent();
    if (hasComparativeReference || !report.comparativeSections().isEmpty()) {
      List<String> comparativeSectionsWithData =
          ReportStatementModelSupport.renderableAccountTypeSectionLabels(
              report.comparativeSections(),
              section ->
                  ReportStatementModelSupport.hasRenderableContent(
                      section.rows(), section.totals()),
              FinancialPositionSection::accountType);
      List<String> comparativeEmptySections =
          ReportStatementModelSupport.emptyAccountTypeSectionLabels(
              report.comparativeSections(),
              section ->
                  ReportStatementModelSupport.hasRenderableContent(
                      section.rows(), section.totals()),
              FinancialPositionSection::accountType);
      sections.add(
          ReportModelSupport.section(
              "comparativeSummary",
              "Comparative Financial Position",
              comparativeVerdicts(report, comparativeSectionsWithData, comparativeEmptySections),
              List.of(),
              List.of(),
              List.of()));
      sections.addAll(renderSections("comparative", "Comparative ", report.comparativeSections()));
    }
    return new ReportModel(
        dev.erst.fingrind.contract.protocol.OperationId.FINANCIAL_POSITION.wireName(),
        ReportModelSupport.reportTitle(
            dev.erst.fingrind.contract.protocol.OperationId.FINANCIAL_POSITION),
        ReportModel.Orientation.LANDSCAPE,
        ReportModelSupport.context(
            report.bookIdentity(),
            report.postingCoverage(),
            null,
            null,
            report.resolvedEffectiveDateAsOf().orElse(null),
            report.comparativeEffectiveDateRange(),
            List.of()),
        List.copyOf(summaryVerdicts(report, currentSectionsWithData, currentEmptySections)),
        List.copyOf(sections));
  }

  private static List<ReportVerdict> summaryVerdicts(
      FinancialPositionReport report,
      List<String> currentSectionsWithData,
      List<String> currentEmptySections) {
    List<ReportVerdict> verdicts = new ArrayList<>();
    verdicts.add(
        new ReportVerdict(
            "As of",
            report
                .resolvedEffectiveDateAsOf()
                .map(java.time.LocalDate::toString)
                .orElse("(none)")));
    verdicts.add(
        new ReportVerdict(
            "Accounting equation",
            report.accountingEquationBalanced() ? "Balanced" : "Imbalanced"));
    int contraNormalRows = contraNormalRowCount(report.sections());
    if (contraNormalRows > 0) {
      verdicts.add(new ReportVerdict("Contra-normal rows", countLabel(contraNormalRows, "row")));
    }
    if (currentSectionsWithData.isEmpty()) {
      verdicts.add(
          new ReportVerdict("Outcome", ReportModelNarrative.noMatches("financial position lines")));
    } else {
      verdicts.add(
          new ReportVerdict("Sections with data", String.join(", ", currentSectionsWithData)));
    }
    if (!currentEmptySections.isEmpty()) {
      verdicts.add(new ReportVerdict("Empty sections", String.join(", ", currentEmptySections)));
    }
    return verdicts;
  }

  private static List<ReportVerdict> comparativeVerdicts(
      FinancialPositionReport report,
      List<String> comparativeSectionsWithData,
      List<String> comparativeEmptySections) {
    List<ReportVerdict> verdicts = new ArrayList<>();
    verdicts.add(
        new ReportVerdict(
            "As of",
            report
                .comparativeEffectiveDateRange()
                .effectiveDateTo()
                .map(java.time.LocalDate::toString)
                .orElse("(none)")));
    verdicts.add(
        new ReportVerdict(
            "Comparative reference",
            ReportModelNarrative.comparativeRange(report.comparativeEffectiveDateRange())));
    int contraNormalRows = contraNormalRowCount(report.comparativeSections());
    if (contraNormalRows > 0) {
      verdicts.add(new ReportVerdict("Contra-normal rows", countLabel(contraNormalRows, "row")));
    }
    if (comparativeSectionsWithData.isEmpty()) {
      verdicts.add(
          new ReportVerdict("Outcome", ReportModelNarrative.noMatches("financial position lines")));
    } else {
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
      String sectionPrefix, String titlePrefix, List<FinancialPositionSection> sections) {
    return sections.stream()
        .filter(
            section ->
                ReportStatementModelSupport.hasRenderableContent(section.rows(), section.totals()))
        .map(
            section ->
                ReportStatementModelSupport.accountTypeStatementSection(
                    sectionPrefix,
                    titlePrefix,
                    section.accountType(),
                    sectionRows(section),
                    section.totals()))
        .toList();
  }

  private static int contraNormalRowCount(List<FinancialPositionSection> sections) {
    return Math.toIntExact(
        sections.stream()
            .flatMap(section -> section.rows().stream())
            .filter(FinancialPositionReportModelBuilder::contraNormalRow)
            .count());
  }

  private static boolean contraNormalRow(FinancialPositionRow row) {
    if (row.balance().balanceSide() == BalanceSide.ZERO || row.lineClassification().isEmpty()) {
      return false;
    }
    BalanceSide normalBalanceSide =
        switch (row.lineClassification().orElseThrow().normalBalance()) {
          case DEBIT -> BalanceSide.DEBIT;
          case CREDIT -> BalanceSide.CREDIT;
        };
    return row.balance().balanceSide() != normalBalanceSide;
  }

  private static String countLabel(int count, String singular) {
    return count == 1 ? "1 %s".formatted(singular) : "%d %ss".formatted(count, singular);
  }

  private static List<ReportRow> sectionRows(FinancialPositionSection section) {
    return section.rows().stream()
        .map(
            row ->
                ReportStatementModelSupport.statementSectionRow(
                    row.lineCode(),
                    row.balance().netAmount().currencyUnit().code(),
                    row.lineName(),
                    row.lineKind(),
                    ReportModelClassificationDisplay.displayFinancialPositionClassification(
                        row.lineClassification()),
                    row.balance().netAmount(),
                    row.balance().balanceSide()))
        .toList();
  }
}
