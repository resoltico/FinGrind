package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.contract.TrialBalanceReport;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Renders trial-balance reports as PDF documents. */
final class TrialBalancePdfRenderer {
  void render(PdfPageWriter pageWriter, TrialBalanceReport report) throws IOException {
    Objects.requireNonNull(pageWriter, "pageWriter");
    Objects.requireNonNull(report, "report");
    pageWriter.writeKeyValueTable(
        "Parameters",
        List.of(
            List.of("Effective date to", PdfRenderSupport.optionalDate(report.effectiveDateTo()))));
    pageWriter.writeTable(
        "Trial Balance",
        List.of(
            new PdfTableColumn("Account", 0.9f, PdfTableColumn.CellAlignment.LEFT),
            new PdfTableColumn("Name", 1.6f, PdfTableColumn.CellAlignment.LEFT),
            new PdfTableColumn("Normal", 0.9f, PdfTableColumn.CellAlignment.LEFT),
            new PdfTableColumn("Active", 0.6f, PdfTableColumn.CellAlignment.LEFT),
            new PdfTableColumn("Currency", 0.8f, PdfTableColumn.CellAlignment.LEFT),
            new PdfTableColumn("Debit", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
            new PdfTableColumn("Credit", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
            new PdfTableColumn("Net", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
            new PdfTableColumn("Side", 0.8f, PdfTableColumn.CellAlignment.LEFT)),
        report.rows().stream()
            .map(
                row ->
                    List.of(
                        row.account().accountCode().value(),
                        row.account().accountName().value(),
                        row.account().normalBalance().wireValue(),
                        Boolean.toString(row.account().active()),
                        row.balance().netAmount().currencyCode().value(),
                        PdfRenderSupport.displayMoney(row.balance().debitTotal()),
                        PdfRenderSupport.displayMoney(row.balance().creditTotal()),
                        PdfRenderSupport.displayMoney(row.balance().netAmount()),
                        row.balance().balanceSide().wireValue()))
            .toList());
  }
}
