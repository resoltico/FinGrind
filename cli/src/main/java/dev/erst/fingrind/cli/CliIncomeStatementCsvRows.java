package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.IncomeStatementPresentationSupport.PresentationSection;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementPresentationSupport.SectionCode;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementRow;
import dev.erst.fingrind.core.CurrencyBalance;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Builds CSV row families for the income-statement report surface. */
final class CliIncomeStatementCsvRows {
  private static final String RECORD_KIND = CliCsvExportFamilies.INCOME_STATEMENT;

  private CliIncomeStatementCsvRows() {}

  static Stream<List<String>> rows(
      IncomeStatementReport report,
      String reportBasis,
      List<PresentationSection> sections,
      List<CurrencyBalance> grossProfitTotals,
      List<CurrencyBalance> netIncomeTotals) {
    String effectiveDateFrom =
        CliIncomeStatementCsvRowSupport.effectiveDateFrom(report, reportBasis);
    String effectiveDateTo = CliIncomeStatementCsvRowSupport.effectiveDateTo(report, reportBasis);
    List<List<String>> rows = new ArrayList<>();
    boolean grossProfitInserted = false;
    boolean hasCostOfSalesSection =
        sections.stream()
            .anyMatch(
                section ->
                    section.sectionCode() == SectionCode.COST_OF_SALES
                        && section.hasRenderableContent());
    for (PresentationSection section : sections) {
      rows.addAll(sectionRows(report, reportBasis, effectiveDateFrom, effectiveDateTo, section));
      if (!grossProfitInserted
          && section.hasRenderableContent()
          && section.sectionCode() == SectionCode.COST_OF_SALES) {
        grossProfitTotals.forEach(
            total ->
                rows.add(
                    CliIncomeStatementCsvRowSupport.grossProfitTotalRow(
                        reportBasis, effectiveDateFrom, effectiveDateTo, total)));
        grossProfitInserted = true;
      } else if (!grossProfitInserted
          && !hasCostOfSalesSection
          && section.hasRenderableContent()
          && section.sectionCode() == SectionCode.REVENUE) {
        grossProfitTotals.forEach(
            total ->
                rows.add(
                    CliIncomeStatementCsvRowSupport.grossProfitTotalRow(
                        reportBasis, effectiveDateFrom, effectiveDateTo, total)));
        grossProfitInserted = true;
      }
    }
    if (!grossProfitInserted && !grossProfitTotals.isEmpty()) {
      grossProfitTotals.forEach(
          total ->
              rows.add(
                  CliIncomeStatementCsvRowSupport.grossProfitTotalRow(
                      reportBasis, effectiveDateFrom, effectiveDateTo, total)));
    }
    netIncomeTotals.forEach(
        total ->
            rows.add(
                CliIncomeStatementCsvRowSupport.netIncomeTotalRow(
                    reportBasis, effectiveDateFrom, effectiveDateTo, total)));
    if (!rows.isEmpty()) {
      return rows.stream();
    }
    return Stream.of(
        CliIncomeStatementCsvRowSupport.reportEmptyRow(
            report, reportBasis, effectiveDateFrom, effectiveDateTo));
  }

  private static List<List<String>> sectionRows(
      IncomeStatementReport report,
      String reportBasis,
      String effectiveDateFrom,
      String effectiveDateTo,
      PresentationSection section) {
    List<List<String>> rows = new ArrayList<>();
    section
        .rows()
        .forEach(
            row ->
                rows.add(lineRow(reportBasis, effectiveDateFrom, effectiveDateTo, section, row)));
    section
        .totals()
        .forEach(
            total ->
                rows.add(
                    sectionTotalRow(
                        reportBasis, effectiveDateFrom, effectiveDateTo, section, total)));
    if (rows.isEmpty()) {
      rows.add(sectionEmptyRow(report, reportBasis, effectiveDateFrom, effectiveDateTo, section));
    }
    return List.copyOf(rows);
  }

  private static List<String> lineRow(
      String reportBasis,
      String effectiveDateFrom,
      String effectiveDateTo,
      PresentationSection section,
      IncomeStatementRow row) {
    String rowId = "income-statement-row:" + reportBasis + ":" + row.lineCode();
    return CliStatementCsvSectionRowSupport.valuedRow(
        rowSpec(
            reportBasis,
            effectiveDateFrom,
            effectiveDateTo,
            section,
            row.lineCode(),
            row.lineName(),
            rowId,
            lineDetailColumns(row)),
        "line",
        row.lineKind().wireValue(),
        row.movement());
  }

  private static List<String> sectionTotalRow(
      String reportBasis,
      String effectiveDateFrom,
      String effectiveDateTo,
      PresentationSection section,
      CurrencyBalance total) {
    String lineCode =
        CliIncomeStatementCsvRowSupport.sectionCodeSlug(section.sectionCode()) + "-total";
    String lineName = section.title() + " total";
    return CliStatementCsvSectionRowSupport.valuedRow(
        rowSpec(
            reportBasis,
            effectiveDateFrom,
            effectiveDateTo,
            section,
            lineCode,
            lineName,
            CliIncomeStatementCsvRowSupport.totalRowId(reportBasis, section, total),
            totalDetailColumns()),
        "section-total",
        "SECTION_TOTAL",
        total);
  }

  private static List<String> sectionEmptyRow(
      IncomeStatementReport report,
      String reportBasis,
      String effectiveDateFrom,
      String effectiveDateTo,
      PresentationSection section) {
    return CliStatementCsvSectionRowSupport.emptyRow(
        rowSpec(
            reportBasis,
            effectiveDateFrom,
            effectiveDateTo,
            section,
            "",
            "",
            CliIncomeStatementCsvRowSupport.emptyRowId(reportBasis, section),
            totalDetailColumns()),
        report.bookIdentity().functionalCurrency().code(),
        CliReportRenderSupport.emptySectionLinesMessage(section.title()));
  }

  private static CliStatementCsvSectionRowSupport.StatementRowSpec rowSpec(
      String reportBasis,
      String effectiveDateFrom,
      String effectiveDateTo,
      PresentationSection section,
      String lineCode,
      String lineName,
      String rowId,
      List<String> detailColumns) {
    return new CliStatementCsvSectionRowSupport.StatementRowSpec(
        CliCsvExportFamilies.INCOME_STATEMENT,
        rowId,
        CliIncomeStatementCsvRowSupport.sectionRowId(reportBasis, section),
        reportBasis,
        RECORD_KIND,
        effectiveDateFrom,
        effectiveDateTo,
        section.sectionCode().wireValue(),
        lineCode,
        lineName,
        detailColumns);
  }

  private static List<String> lineDetailColumns(IncomeStatementRow row) {
    return List.of(row.lineType().wireValue(), row.lineClassification().wireValue());
  }

  private static List<String> totalDetailColumns() {
    return List.of("", "");
  }
}
