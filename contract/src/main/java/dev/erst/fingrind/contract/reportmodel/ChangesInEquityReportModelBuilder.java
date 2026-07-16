package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import java.util.ArrayList;
import java.util.List;

/** Builds the shared report model for changes-in-equity statements. */
public final class ChangesInEquityReportModelBuilder
    implements ReportModelBuilder<ChangesInEquityReport> {
  /** Shared reusable builder instance. */
  public static final ChangesInEquityReportModelBuilder INSTANCE =
      new ChangesInEquityReportModelBuilder();

  private ChangesInEquityReportModelBuilder() {}

  @Override
  public ReportModel build(ChangesInEquityReport report) {
    return buildModel(report);
  }

  /** Builds one changes-in-equity report model. */
  public static ReportModel buildModel(ChangesInEquityReport report) {
    List<ReportSection> sections = new ArrayList<>();
    sections.add(currentSection("equityLines", "Equity Lines", report.rows()));
    maybeAddCurrentTotalsSection(sections, report);
    appendComparativeSections(sections, report);
    return new ReportModel(
        dev.erst.fingrind.contract.protocol.OperationId.CHANGES_IN_EQUITY.wireName(),
        ReportModelSupport.reportTitle(
            dev.erst.fingrind.contract.protocol.OperationId.CHANGES_IN_EQUITY),
        ReportModel.Orientation.LANDSCAPE,
        ReportModelSupport.context(
            report.bookIdentity(),
            report.postingCoverage(),
            report.effectiveDateFrom(),
            report.effectiveDateTo(),
            null,
            report.comparativeEffectiveDateRange(),
            List.of()),
        currentVerdicts(report),
        List.copyOf(sections));
  }

  private static void maybeAddCurrentTotalsSection(
      List<ReportSection> sections, ChangesInEquityReport report) {
    if (hasTotals(report.openingTotals(), report.movementTotals(), report.closingTotals())) {
      sections.add(
          totalsSection(
              "equityTotals",
              "Equity Totals",
              report.openingTotals(),
              report.movementTotals(),
              report.closingTotals(),
              false));
    }
  }

  private static void appendComparativeSections(
      List<ReportSection> sections, ChangesInEquityReport report) {
    if (hasComparativePresentation(report)) {
      sections.add(comparativeSummarySection(report));
    }
    if (!report.comparativeRows().isEmpty()) {
      sections.add(
          currentSection(
              "comparativeEquityLines", "Comparative Changes In Equity", report.comparativeRows()));
    }
    if (hasTotals(
        report.comparativeOpeningTotals(),
        report.comparativeMovementTotals(),
        report.comparativeClosingTotals())) {
      sections.add(
          totalsSection(
              "comparativeEquityTotals",
              "Comparative Equity Totals",
              report.comparativeOpeningTotals(),
              report.comparativeMovementTotals(),
              report.comparativeClosingTotals(),
              true));
    }
  }

  private static boolean hasComparativePresentation(ChangesInEquityReport report) {
    return hasComparativeReference(report)
        || !report.comparativeRows().isEmpty()
        || hasTotals(
            report.comparativeOpeningTotals(),
            report.comparativeMovementTotals(),
            report.comparativeClosingTotals());
  }

  private static boolean hasComparativeReference(ChangesInEquityReport report) {
    return report.comparativeEffectiveDateRange().effectiveDateFrom().isPresent()
        || report.comparativeEffectiveDateRange().effectiveDateTo().isPresent();
  }

  private static boolean hasTotals(
      List<dev.erst.fingrind.core.CurrencyBalance> opening,
      List<dev.erst.fingrind.core.CurrencyBalance> movement,
      List<dev.erst.fingrind.core.CurrencyBalance> closing) {
    return !opening.isEmpty() || !movement.isEmpty() || !closing.isEmpty();
  }

  private static ReportSection comparativeSummarySection(ChangesInEquityReport report) {
    return ReportModelSupport.section(
        "comparativeSummary",
        "Comparative Changes In Equity",
        comparativeVerdicts(report),
        List.of(),
        List.of(),
        List.of());
  }

  private static ReportSection currentSection(
      String key,
      String title,
      List<dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow> rows) {
    return ReportModelSupport.section(
        key,
        title,
        rows.isEmpty()
            ? List.of(new ReportVerdict("Outcome", ReportModelNarrative.noMatches("equity lines")))
            : List.of(),
        sectionColumns(),
        rows.stream()
            .map(
                row ->
                    ReportModelSupport.row(
                        row.lineCode()
                            + ":"
                            + row.closingBalance().netAmount().currencyUnit().code(),
                        ReportModelDisplay.displayStatementLineCode(row.lineCode(), row.lineKind()),
                        row.lineName(),
                        ReportModelDisplay.displayStatementLineKind(row.lineKind()),
                        ReportModelClassificationDisplay.displayFinancialPositionClassification(
                            row.lineClassification()),
                        ReportModelDisplay.displayMoney(row.openingBalance().netAmount()),
                        ReportModelDisplay.displayMoney(row.movement().netAmount()),
                        ReportModelDisplay.displayMoney(row.closingBalance().netAmount()),
                        ReportModelDisplay.displayBalanceSide(row.closingBalance().balanceSide())))
            .toList(),
        List.of());
  }

  private static List<ReportVerdict> currentVerdicts(ChangesInEquityReport report) {
    List<ReportVerdict> verdicts = new ArrayList<>();
    verdicts.add(new ReportVerdict("Period start", report.effectiveDateFrom().toString()));
    verdicts.add(new ReportVerdict("Period end", report.effectiveDateTo().toString()));
    if (!report.openingTotals().isEmpty()) {
      verdicts.add(
          new ReportVerdict(
              "Opening totals", ReportModelNarrative.joinedBalancesText(report.openingTotals())));
    }
    if (!report.movementTotals().isEmpty()) {
      verdicts.add(
          new ReportVerdict(
              "Movement totals", ReportModelNarrative.joinedBalancesText(report.movementTotals())));
    }
    if (!report.closingTotals().isEmpty()) {
      verdicts.add(
          new ReportVerdict(
              "Closing totals", ReportModelNarrative.joinedBalancesText(report.closingTotals())));
    }
    if (report.rows().isEmpty()) {
      verdicts.add(new ReportVerdict("Outcome", ReportModelNarrative.noMatches("equity lines")));
    }
    return List.copyOf(verdicts);
  }

  private static List<ReportVerdict> comparativeVerdicts(ChangesInEquityReport report) {
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
    if (!report.comparativeOpeningTotals().isEmpty()) {
      verdicts.add(
          new ReportVerdict(
              "Comparative opening totals",
              ReportModelNarrative.joinedBalancesText(report.comparativeOpeningTotals())));
    }
    if (!report.comparativeMovementTotals().isEmpty()) {
      verdicts.add(
          new ReportVerdict(
              "Comparative movement totals",
              ReportModelNarrative.joinedBalancesText(report.comparativeMovementTotals())));
    }
    if (!report.comparativeClosingTotals().isEmpty()) {
      verdicts.add(
          new ReportVerdict(
              "Comparative closing totals",
              ReportModelNarrative.joinedBalancesText(report.comparativeClosingTotals())));
    }
    if (report.comparativeRows().isEmpty()
        && report.comparativeOpeningTotals().isEmpty()
        && report.comparativeMovementTotals().isEmpty()
        && report.comparativeClosingTotals().isEmpty()) {
      verdicts.add(new ReportVerdict("Outcome", ReportModelNarrative.noMatches("equity lines")));
    }
    return List.copyOf(verdicts);
  }

  private static ReportSection totalsSection(
      String key,
      String title,
      List<dev.erst.fingrind.core.CurrencyBalance> opening,
      List<dev.erst.fingrind.core.CurrencyBalance> movement,
      List<dev.erst.fingrind.core.CurrencyBalance> closing,
      boolean comparative) {
    return ReportModelSupport.section(
        key,
        title,
        List.of(
            new ReportVerdict(
                comparative ? "Comparative Opening Totals" : "Opening Totals",
                ReportModelNarrative.joinedBalancesText(opening)),
            new ReportVerdict(
                comparative ? "Comparative Movement Totals" : "Movement Totals",
                ReportModelNarrative.joinedBalancesText(movement)),
            new ReportVerdict(
                comparative ? "Comparative Closing Totals" : "Closing Totals",
                ReportModelNarrative.joinedBalancesText(closing))),
        List.of(),
        List.of(),
        List.of());
  }

  private static List<ReportColumn> sectionColumns() {
    return List.of(
        ReportModelSupport.leftColumn("lineCode", "Line code"),
        ReportModelSupport.leftColumn("lineName", "Line name"),
        ReportModelSupport.leftColumn("lineKind", "Row kind"),
        ReportModelSupport.leftColumn("classification", "Classification"),
        ReportModelSupport.rightColumn("opening", "Opening"),
        ReportModelSupport.rightColumn("movement", "Movement"),
        ReportModelSupport.rightColumn("closing", "Closing"),
        ReportModelSupport.leftColumn("closingSide", "Closing side"));
  }
}
