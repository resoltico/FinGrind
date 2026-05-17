package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Renders account-ledger reports as PDF documents. */
final class AccountLedgerPdfRenderer {
  void render(PdfPageWriter pageWriter, AccountLedgerReport report) throws IOException {
    Objects.requireNonNull(pageWriter, "pageWriter");
    Objects.requireNonNull(report, "report");
    pageWriter.writeKeyValueTable(
        "Ledger Context",
        PdfStatementMetadataRows.reportParameters(
            report.bookIdentity(),
            report.postingCoverage(),
            List.of(
                List.of(
                    "Account",
                    report.account().accountCode().value()
                        + " — "
                        + report.account().accountName().value()),
                List.of(
                    "Classification",
                    PdfValueFormatter.displayAccountType(report.account().accountType())),
                List.of(
                    "Role and polarity",
                    PdfValueFormatter.displayAccountRole(report.account().accountRole())
                        + " / "
                        + PdfValueFormatter.displayNormalBalance(report.account().normalBalance())
                        + " / "
                        + PdfValueFormatter.displayBoolean(report.account().active())),
                List.of(
                    "Effective date range",
                    PdfValueFormatter.effectiveDateRange(report.effectiveDateRange())))));
    if (hasMeaningfulBalances(report.openingBalances())) {
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
                          balance.netAmount().currencyUnit().code(),
                          PdfValueFormatter.displayMoney(balance.debitTotal()),
                          PdfValueFormatter.displayMoney(balance.creditTotal()),
                          PdfValueFormatter.displayMoney(balance.netAmount()),
                          PdfValueFormatter.displayBalanceSide(balance.balanceSide())))
              .toList());
    }
    pageWriter.writeTable(
        "Ledger Entries",
        List.of(
            new PdfTableColumn("Effective date", 0.95f, PdfTableColumn.CellAlignment.LEFT),
            new PdfTableColumn("Posting id", 1.35f, PdfTableColumn.CellAlignment.LEFT),
            new PdfTableColumn("Entry", 1.55f, PdfTableColumn.CellAlignment.LEFT),
            new PdfTableColumn("Debit", 0.8f, PdfTableColumn.CellAlignment.RIGHT),
            new PdfTableColumn("Credit", 0.8f, PdfTableColumn.CellAlignment.RIGHT),
            new PdfTableColumn("Running net", 0.95f, PdfTableColumn.CellAlignment.RIGHT),
            new PdfTableColumn("Side", 0.65f, PdfTableColumn.CellAlignment.LEFT),
            new PdfTableColumn("Counterpart accounts", 1.0f, PdfTableColumn.CellAlignment.LEFT)),
        report.entries().stream()
            .map(
                entry ->
                    List.of(
                        entry.postingFact().journalEntry().effectiveDate().toString(),
                        entry.postingFact().postingId().value(),
                        postingEntrySummary(entry.postingFact()),
                        PdfValueFormatter.displayMoney(entry.movement().debitTotal()),
                        PdfValueFormatter.displayMoney(entry.movement().creditTotal()),
                        PdfValueFormatter.displayMoney(entry.runningNetAmount()),
                        PdfValueFormatter.displayBalanceSide(entry.runningBalanceSide()),
                        counterpartAccounts(report.account().accountCode(), entry.postingFact())))
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
                        balance.netAmount().currencyUnit().code(),
                        PdfValueFormatter.displayMoney(balance.debitTotal()),
                        PdfValueFormatter.displayMoney(balance.creditTotal()),
                        PdfValueFormatter.displayMoney(balance.netAmount()),
                        PdfValueFormatter.displayBalanceSide(balance.balanceSide())))
            .toList());
  }

  private static boolean hasMeaningfulBalances(
      List<dev.erst.fingrind.core.CurrencyBalance> balances) {
    return balances.stream()
        .anyMatch(balance -> !balance.debitTotal().isZero() || !balance.creditTotal().isZero());
  }

  private static String postingEntrySummary(
      dev.erst.fingrind.contract.bookkeeping.PostingFact postingFact) {
    String postingKind = PdfValueFormatter.displayPostingKind(postingFact.postingKind());
    return postingFact
        .reversalReference()
        .map(
            reference ->
                postingKind + " / Reversal posting of " + reference.priorPostingId().value())
        .orElse(postingKind + " / Direct posting");
  }

  private static String counterpartAccounts(
      dev.erst.fingrind.core.AccountCode accountCode,
      dev.erst.fingrind.contract.bookkeeping.PostingFact postingFact) {
    List<String> counterpartAccounts = new ArrayList<>();
    postingFact.journalEntry().lines().stream()
        .map(line -> line.accountCode().value())
        .filter(candidate -> !candidate.equals(accountCode.value()))
        .distinct()
        .forEach(counterpartAccounts::add);
    return counterpartAccounts.isEmpty() ? "(self)" : String.join(", ", counterpartAccounts);
  }
}
