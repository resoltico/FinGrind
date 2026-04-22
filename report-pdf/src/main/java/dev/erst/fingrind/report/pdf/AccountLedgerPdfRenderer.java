package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.contract.AccountLedgerReport;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Renders account-ledger reports as PDF documents. */
final class AccountLedgerPdfRenderer {
  void render(PdfPageWriter pageWriter, AccountLedgerReport report) throws IOException {
    Objects.requireNonNull(pageWriter, "pageWriter");
    Objects.requireNonNull(report, "report");
    pageWriter.writeKeyValueTable(
        "Ledger Parameters",
        List.of(
            List.of("Account", report.account().accountCode().value()),
            List.of("Name", report.account().accountName().value()),
            List.of("Normal balance", report.account().normalBalance().wireValue()),
            List.of("Active", Boolean.toString(report.account().active())),
            List.of(
                "Effective date range",
                PdfValueFormatter.effectiveDateRange(report.effectiveDateRange()))));
    pageWriter.writeTable(
        "Opening Balances",
        List.of(
            new PdfTableColumn("Currency", 1.0f, PdfTableColumn.CellAlignment.LEFT),
            new PdfTableColumn("Debit total", 1.1f, PdfTableColumn.CellAlignment.RIGHT),
            new PdfTableColumn("Credit total", 1.1f, PdfTableColumn.CellAlignment.RIGHT),
            new PdfTableColumn("Net amount", 1.1f, PdfTableColumn.CellAlignment.RIGHT),
            new PdfTableColumn("Balance side", 1.0f, PdfTableColumn.CellAlignment.LEFT)),
        report.openingBalances().stream()
            .map(
                balance ->
                    List.of(
                        balance.netAmount().currencyCode().value(),
                        PdfValueFormatter.displayMoney(balance.debitTotal()),
                        PdfValueFormatter.displayMoney(balance.creditTotal()),
                        PdfValueFormatter.displayMoney(balance.netAmount()),
                        balance.balanceSide().wireValue()))
            .toList());
    pageWriter.writeTable(
        "Ledger Entries",
        List.of(
            new PdfTableColumn("Effective date", 0.9f, PdfTableColumn.CellAlignment.LEFT),
            new PdfTableColumn("Posting id", 1.5f, PdfTableColumn.CellAlignment.LEFT),
            new PdfTableColumn("Reversal target", 1.5f, PdfTableColumn.CellAlignment.LEFT),
            new PdfTableColumn("Currency", 0.8f, PdfTableColumn.CellAlignment.LEFT),
            new PdfTableColumn("Debit", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
            new PdfTableColumn("Credit", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
            new PdfTableColumn("Movement", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
            new PdfTableColumn("Running net", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
            new PdfTableColumn("Side", 0.8f, PdfTableColumn.CellAlignment.LEFT)),
        report.entries().stream()
            .map(
                entry ->
                    List.of(
                        entry.postingFact().journalEntry().effectiveDate().toString(),
                        entry.postingFact().postingId().value(),
                        PdfValueFormatter.reversalTarget(entry.postingFact()),
                        entry.movement().netAmount().currencyCode().value(),
                        PdfValueFormatter.displayMoney(entry.movement().debitTotal()),
                        PdfValueFormatter.displayMoney(entry.movement().creditTotal()),
                        PdfValueFormatter.displayMoney(entry.movement().netAmount()),
                        PdfValueFormatter.displayMoney(entry.runningNetAmount()),
                        entry.runningBalanceSide().wireValue()))
            .toList());
    pageWriter.writeTable(
        "Closing Balances",
        List.of(
            new PdfTableColumn("Currency", 1.0f, PdfTableColumn.CellAlignment.LEFT),
            new PdfTableColumn("Debit total", 1.1f, PdfTableColumn.CellAlignment.RIGHT),
            new PdfTableColumn("Credit total", 1.1f, PdfTableColumn.CellAlignment.RIGHT),
            new PdfTableColumn("Net amount", 1.1f, PdfTableColumn.CellAlignment.RIGHT),
            new PdfTableColumn("Balance side", 1.0f, PdfTableColumn.CellAlignment.LEFT)),
        report.closingBalances().stream()
            .map(
                balance ->
                    List.of(
                        balance.netAmount().currencyCode().value(),
                        PdfValueFormatter.displayMoney(balance.debitTotal()),
                        PdfValueFormatter.displayMoney(balance.creditTotal()),
                        PdfValueFormatter.displayMoney(balance.netAmount()),
                        balance.balanceSide().wireValue()))
            .toList());
  }
}
