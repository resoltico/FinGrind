package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Renders period-summary reports as PDF documents. */
final class PeriodSummaryPdfRenderer {
  void render(PdfPageWriter pageWriter, PeriodSummaryReport report) throws IOException {
    Objects.requireNonNull(pageWriter, "pageWriter");
    Objects.requireNonNull(report, "report");
    pageWriter.writeKeyValueTable(
        "Summary",
        List.of(
            List.of("Effective date from", report.effectiveDateFrom().toString()),
            List.of("Effective date to", report.effectiveDateTo().toString()),
            List.of("Posting count", Integer.toString(report.postingCount())),
            List.of("Posting line count", Integer.toString(report.postingLineCount())),
            List.of("Accounts touched", Integer.toString(report.accountsTouched()))));
    pageWriter.writeTable(
        "Currency Totals",
        List.of(
            new PdfTableColumn("Currency", 1.0f, PdfTableColumn.CellAlignment.LEFT),
            new PdfTableColumn("Debit total", 1.1f, PdfTableColumn.CellAlignment.RIGHT),
            new PdfTableColumn("Credit total", 1.1f, PdfTableColumn.CellAlignment.RIGHT),
            new PdfTableColumn("Net amount", 1.1f, PdfTableColumn.CellAlignment.RIGHT),
            new PdfTableColumn("Balance side", 1.0f, PdfTableColumn.CellAlignment.LEFT)),
        report.currencyTotals().stream()
            .map(
                summary ->
                    List.of(
                        summary.totals().netAmount().currencyUnit().code(),
                        PdfValueFormatter.displayMoney(summary.totals().debitTotal()),
                        PdfValueFormatter.displayMoney(summary.totals().creditTotal()),
                        PdfValueFormatter.displayMoney(summary.totals().netAmount()),
                        summary.totals().balanceSide().wireValue()))
            .toList());
    pageWriter.writeTable(
        "Account Activity",
        List.of(
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
            new PdfTableColumn("Side", 0.8f, PdfTableColumn.CellAlignment.LEFT)),
        report.accountActivity().stream()
            .map(
                row ->
                    List.of(
                        row.account().accountCode().value(),
                        row.account().accountName().value(),
                        row.account().accountType().wireValue(),
                        row.account().accountRole().wireValue(),
                        row.account().normalBalance().wireValue(),
                        Boolean.toString(row.account().active()),
                        row.movement().netAmount().currencyUnit().code(),
                        PdfValueFormatter.displayMoney(row.movement().debitTotal()),
                        PdfValueFormatter.displayMoney(row.movement().creditTotal()),
                        PdfValueFormatter.displayMoney(row.movement().netAmount()),
                        row.movement().balanceSide().wireValue()))
            .toList());
  }
}
