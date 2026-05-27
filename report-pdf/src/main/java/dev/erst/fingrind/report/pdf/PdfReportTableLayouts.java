package dev.erst.fingrind.report.pdf;

import java.util.List;

/** Shared PDF table layouts for bookkeeping reports. */
final class PdfReportTableLayouts {
  private PdfReportTableLayouts() {}

  static List<PdfTableColumn> statementBalanceColumns() {
    return List.of(
        new PdfTableColumn("Line code", 1.0f, PdfTableColumn.CellAlignment.LEFT),
        new PdfTableColumn("Line name", 1.7f, PdfTableColumn.CellAlignment.LEFT),
        new PdfTableColumn("Role", 1.0f, PdfTableColumn.CellAlignment.LEFT),
        new PdfTableColumn("Classification", 1.2f, PdfTableColumn.CellAlignment.LEFT),
        new PdfTableColumn("Line kind", 1.1f, PdfTableColumn.CellAlignment.LEFT),
        new PdfTableColumn("Currency", 0.8f, PdfTableColumn.CellAlignment.LEFT),
        new PdfTableColumn("Debit", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
        new PdfTableColumn("Credit", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
        new PdfTableColumn("Net", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
        new PdfTableColumn("Side", 0.8f, PdfTableColumn.CellAlignment.LEFT));
  }

  static List<PdfTableColumn> currencyBalanceSummaryColumns() {
    return List.of(
        new PdfTableColumn("Currency", 0.8f, PdfTableColumn.CellAlignment.LEFT),
        new PdfTableColumn("Debit", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
        new PdfTableColumn("Credit", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
        new PdfTableColumn("Net", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
        new PdfTableColumn("Side", 0.8f, PdfTableColumn.CellAlignment.LEFT));
  }

  static List<PdfTableColumn> detailedCurrencyBalanceColumns() {
    return List.of(
        new PdfTableColumn("Currency", 1.0f, PdfTableColumn.CellAlignment.LEFT),
        new PdfTableColumn("Debit total", 1.1f, PdfTableColumn.CellAlignment.RIGHT),
        new PdfTableColumn("Credit total", 1.1f, PdfTableColumn.CellAlignment.RIGHT),
        new PdfTableColumn("Net amount", 1.1f, PdfTableColumn.CellAlignment.RIGHT),
        new PdfTableColumn("Balance side", 1.0f, PdfTableColumn.CellAlignment.LEFT));
  }

  static List<PdfTableColumn> accountActivityColumns() {
    return List.of(
        new PdfTableColumn("Account", 0.9f, PdfTableColumn.CellAlignment.LEFT),
        new PdfTableColumn("Name", 1.5f, PdfTableColumn.CellAlignment.LEFT),
        new PdfTableColumn("Type", 0.9f, PdfTableColumn.CellAlignment.LEFT),
        new PdfTableColumn("Role", 1.0f, PdfTableColumn.CellAlignment.LEFT),
        new PdfTableColumn("Normal", 0.8f, PdfTableColumn.CellAlignment.LEFT),
        new PdfTableColumn("Active", 0.6f, PdfTableColumn.CellAlignment.LEFT),
        new PdfTableColumn("Currency", 0.8f, PdfTableColumn.CellAlignment.LEFT),
        new PdfTableColumn("Debit", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
        new PdfTableColumn("Credit", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
        new PdfTableColumn("Net", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
        new PdfTableColumn("Side", 0.8f, PdfTableColumn.CellAlignment.LEFT));
  }

  static List<PdfTableColumn> changesInEquityColumns() {
    return List.of(
        new PdfTableColumn("Line code", 1.0f, PdfTableColumn.CellAlignment.LEFT),
        new PdfTableColumn("Line name", 1.5f, PdfTableColumn.CellAlignment.LEFT),
        new PdfTableColumn("Role", 1.0f, PdfTableColumn.CellAlignment.LEFT),
        new PdfTableColumn("Classification", 1.2f, PdfTableColumn.CellAlignment.LEFT),
        new PdfTableColumn("Line kind", 1.0f, PdfTableColumn.CellAlignment.LEFT),
        new PdfTableColumn("Currency", 0.8f, PdfTableColumn.CellAlignment.LEFT),
        new PdfTableColumn("Opening", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
        new PdfTableColumn("Movement", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
        new PdfTableColumn("Closing", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
        new PdfTableColumn("Closing side", 0.8f, PdfTableColumn.CellAlignment.LEFT));
  }

  static List<PdfTableColumn> equityTotalsColumns() {
    return List.of(
        new PdfTableColumn("Basis", 1.0f, PdfTableColumn.CellAlignment.LEFT),
        new PdfTableColumn("Currency", 0.8f, PdfTableColumn.CellAlignment.LEFT),
        new PdfTableColumn("Debit", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
        new PdfTableColumn("Credit", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
        new PdfTableColumn("Net", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
        new PdfTableColumn("Side", 0.8f, PdfTableColumn.CellAlignment.LEFT));
  }
}
