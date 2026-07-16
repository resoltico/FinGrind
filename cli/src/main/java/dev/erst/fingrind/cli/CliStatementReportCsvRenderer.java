package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliReportValueJsonModels;
import dev.erst.fingrind.cli.json.CliStatementReportJsonModels;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Renders the typed CSV row tables for statement report families. */
final class CliStatementReportCsvRenderer {
  private static final List<String> STATEMENT_HEADERS =
      List.of(
          "family",
          "reportPeriod",
          "sectionKind",
          "lineCode",
          "lineName",
          "lineType",
          "financialPositionLineClassification",
          "profitAndLossLineClassification",
          "lineKind",
          "currencyCode",
          "debitTotalCurrencyCode",
          "debitTotalMinorUnits",
          "creditTotalCurrencyCode",
          "creditTotalMinorUnits",
          "netAmountCurrencyCode",
          "netAmountMinorUnits",
          "balanceSide");

  private CliStatementReportCsvRenderer() {}

  static String render(CliStatementReportJsonModels.StatementReportPayload report) {
    return switch (report) {
      case CliStatementReportJsonModels.FinancialPositionPayload financialPosition ->
          render(financialPosition);
      case CliStatementReportJsonModels.IncomeStatementPayload incomeStatement ->
          render(incomeStatement);
      case CliStatementReportJsonModels.CashFlowStatementPayload cashFlow -> render(cashFlow);
      case CliStatementReportJsonModels.ChangesInEquityPayload changesInEquity ->
          render(changesInEquity);
    };
  }

  static String render(CliStatementReportJsonModels.FinancialPositionPayload report) {
    return statements(report.family(), report.sections(), report.comparativeSections());
  }

  static String render(CliStatementReportJsonModels.IncomeStatementPayload report) {
    return statements(report.family(), report.sections(), report.comparativeSections());
  }

  static String render(CliStatementReportJsonModels.CashFlowStatementPayload report) {
    return statements(
        report.family(),
        report.sections(),
        report.comparative() == null ? List.of() : report.comparative().sections());
  }

  static String render(CliStatementReportJsonModels.ChangesInEquityPayload report) {
    List<List<String>> rows = new ArrayList<>();
    appendChangesInEquityRows(rows, report.family(), "current", report.rows());
    appendChangesInEquityRows(
        rows,
        report.family(),
        "comparative",
        report.comparative() == null ? List.of() : report.comparative().rows());
    return CliTextFormat.renderCsv(
        List.of(
            "family",
            "reportPeriod",
            "lineCode",
            "lineName",
            "lineType",
            "financialPositionLineClassification",
            "lineKind",
            "openingDebitTotalCurrencyCode",
            "openingDebitTotalMinorUnits",
            "openingCreditTotalCurrencyCode",
            "openingCreditTotalMinorUnits",
            "movementDebitTotalCurrencyCode",
            "movementDebitTotalMinorUnits",
            "movementCreditTotalCurrencyCode",
            "movementCreditTotalMinorUnits",
            "closingDebitTotalCurrencyCode",
            "closingDebitTotalMinorUnits",
            "closingCreditTotalCurrencyCode",
            "closingCreditTotalMinorUnits"),
        rows);
  }

  private static String statements(
      String family,
      List<CliStatementReportJsonModels.StatementSectionPayload> current,
      List<CliStatementReportJsonModels.StatementSectionPayload> comparative) {
    List<List<String>> rows = new ArrayList<>();
    appendStatementRows(rows, family, "current", current);
    appendStatementRows(rows, family, "comparative", comparative);
    return CliTextFormat.renderCsv(STATEMENT_HEADERS, rows);
  }

  private static void appendStatementRows(
      List<List<String>> target,
      String family,
      String reportPeriod,
      List<CliStatementReportJsonModels.StatementSectionPayload> sections) {
    for (CliStatementReportJsonModels.StatementSectionPayload section : sections) {
      for (CliStatementReportJsonModels.StatementRowPayload row : section.rows()) {
        CliReportValueJsonModels.BalancePayload balance = row.balance();
        target.add(
            List.of(
                family,
                reportPeriod,
                section.sectionKind(),
                row.lineCode(),
                row.lineName(),
                stringOrEmpty(row.lineType()),
                stringOrEmpty(row.financialPositionLineClassification()),
                stringOrEmpty(row.profitAndLossLineClassification()),
                row.lineKind(),
                balance.currencyCode(),
                balance.debitTotal().currencyCode(),
                balance.debitTotal().minorUnits(),
                balance.creditTotal().currencyCode(),
                balance.creditTotal().minorUnits(),
                balance.netAmount().currencyCode(),
                balance.netAmount().minorUnits(),
                balance.balanceSide()));
      }
    }
  }

  private static void appendChangesInEquityRows(
      List<List<String>> target,
      String family,
      String reportPeriod,
      List<CliStatementReportJsonModels.ChangesInEquityRowPayload> rows) {
    for (CliStatementReportJsonModels.ChangesInEquityRowPayload row : rows) {
      target.add(
          List.of(
              family,
              reportPeriod,
              row.lineCode(),
              row.lineName(),
              stringOrEmpty(row.lineType()),
              stringOrEmpty(row.financialPositionLineClassification()),
              row.lineKind(),
              row.openingBalance().debitTotal().currencyCode(),
              row.openingBalance().debitTotal().minorUnits(),
              row.openingBalance().creditTotal().currencyCode(),
              row.openingBalance().creditTotal().minorUnits(),
              row.movement().debitTotal().currencyCode(),
              row.movement().debitTotal().minorUnits(),
              row.movement().creditTotal().currencyCode(),
              row.movement().creditTotal().minorUnits(),
              row.closingBalance().debitTotal().currencyCode(),
              row.closingBalance().debitTotal().minorUnits(),
              row.closingBalance().creditTotal().currencyCode(),
              row.closingBalance().creditTotal().minorUnits()));
    }
  }

  private static String stringOrEmpty(@Nullable String value) {
    return value == null ? "" : value;
  }
}
