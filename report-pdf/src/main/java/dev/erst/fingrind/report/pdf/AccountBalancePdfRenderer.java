package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Renders account-balance snapshots as PDF reports. */
final class AccountBalancePdfRenderer {
  void render(PdfPageWriter pageWriter, AccountBalanceSnapshot snapshot) throws IOException {
    Objects.requireNonNull(pageWriter, "pageWriter");
    Objects.requireNonNull(snapshot, "snapshot");
    pageWriter.writeKeyValueTable(
        "Snapshot",
        List.of(
            List.of("Account", snapshot.account().accountCode().value()),
            List.of("Name", snapshot.account().accountName().value()),
            List.of("Normal balance", snapshot.account().normalBalance().wireValue()),
            List.of("Active", Boolean.toString(snapshot.account().active())),
            List.of(
                "Effective date range",
                PdfValueFormatter.optionalDateRange(
                    snapshot.effectiveDateFrom().orElse(null),
                    snapshot.effectiveDateTo().orElse(null)))));
    pageWriter.writeTable(
        "Per-Currency Balances",
        List.of(
            new PdfTableColumn("Currency", 1.1f, PdfTableColumn.CellAlignment.LEFT),
            new PdfTableColumn("Debit total", 1.1f, PdfTableColumn.CellAlignment.RIGHT),
            new PdfTableColumn("Credit total", 1.1f, PdfTableColumn.CellAlignment.RIGHT),
            new PdfTableColumn("Net amount", 1.1f, PdfTableColumn.CellAlignment.RIGHT),
            new PdfTableColumn("Balance side", 1.0f, PdfTableColumn.CellAlignment.LEFT)),
        snapshot.balances().stream()
            .map(
                balance ->
                    List.of(
                        balance.netAmount().currencyUnit().code(),
                        PdfValueFormatter.displayMoney(balance.debitTotal()),
                        PdfValueFormatter.displayMoney(balance.creditTotal()),
                        PdfValueFormatter.displayMoney(balance.netAmount()),
                        balance.balanceSide().wireValue()))
            .toList());
  }
}
