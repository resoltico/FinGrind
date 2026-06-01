package dev.erst.fingrind.report.pdf;

import java.util.List;

/** Shared PDF table layouts for bookkeeping reports. */
final class PdfReportTableLayouts {
  private PdfReportTableLayouts() {}

  static List<PdfTableColumn> statementBalanceColumns() {
    return statementColumns(1.7f, 1.1f, balanceAmountColumns("Debit", "Credit", "Net", "Side"));
  }

  static List<PdfTableColumn> currencyBalanceSummaryColumns() {
    List<PdfTableColumn> balanceColumns = balanceAmountColumns("Debit", "Credit", "Net", "Side");
    return List.of(
        leftColumn("Currency", 0.9f),
        balanceColumns.get(0),
        balanceColumns.get(1),
        balanceColumns.get(2),
        balanceColumns.get(3));
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
    return statementColumns(
        1.5f, 1.0f, balanceAmountColumns("Opening", "Movement", "Closing", "Closing side"));
  }

  static List<PdfTableColumn> equityTotalsColumns() {
    return balanceSummaryColumns("Basis");
  }

  private static List<PdfTableColumn> statementColumns(
      float lineNameWidth, float lineKindWidth, List<PdfTableColumn> balanceColumns) {
    return List.of(
        leftColumn("Line code", 1.0f),
        leftColumn("Line name", lineNameWidth),
        leftColumn("Role", 1.0f),
        leftColumn("Classification", 1.2f),
        leftColumn("Line kind", lineKindWidth),
        leftColumn("Currency", 0.8f),
        balanceColumns.get(0),
        balanceColumns.get(1),
        balanceColumns.get(2),
        balanceColumns.get(3));
  }

  private static List<PdfTableColumn> balanceSummaryColumns(String basisHeader) {
    List<PdfTableColumn> balanceColumns = balanceAmountColumns("Debit", "Credit", "Net", "Side");
    return List.of(
        leftColumn(basisHeader, 1.0f),
        leftColumn("Currency", 0.8f),
        balanceColumns.get(0),
        balanceColumns.get(1),
        balanceColumns.get(2),
        balanceColumns.get(3));
  }

  private static List<PdfTableColumn> balanceAmountColumns(
      String firstAmountHeader,
      String secondAmountHeader,
      String netAmountHeader,
      String sideHeader) {
    return List.of(
        rightColumn(firstAmountHeader, 0.9f),
        rightColumn(secondAmountHeader, 0.9f),
        rightColumn(netAmountHeader, 0.9f),
        leftColumn(sideHeader, 0.8f));
  }

  private static PdfTableColumn leftColumn(String header, float width) {
    return new PdfTableColumn(header, width, PdfTableColumn.CellAlignment.LEFT);
  }

  private static PdfTableColumn rightColumn(String header, float width) {
    return new PdfTableColumn(header, width, PdfTableColumn.CellAlignment.RIGHT);
  }
}
