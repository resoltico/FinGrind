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
        PdfStatementMetadataRows.reportParameters(
            snapshot.bookIdentity(),
            snapshot.postingCoverage(),
            List.of(
                List.of("Account", snapshot.account().accountCode().value()),
                List.of("Name", snapshot.account().accountName().value()),
                List.of(
                    "Account type",
                    PdfValueFormatter.displayAccountType(snapshot.account().accountType())),
                List.of(
                    "Account role",
                    PdfValueFormatter.displayAccountRole(snapshot.account().accountRole())),
                List.of(
                    "Normal balance",
                    PdfValueFormatter.displayNormalBalance(snapshot.account().normalBalance())),
                List.of("Active", PdfValueFormatter.displayBoolean(snapshot.account().active())),
                List.of(
                    "Effective date range",
                    PdfTemporalValueFormatter.optionalDateRange(
                        snapshot.effectiveDateFrom().orElse(null),
                        snapshot.effectiveDateTo().orElse(null))))));
    PdfBalanceTableSupport.writeDetailedTable(
        pageWriter, "Per-Currency Balances", snapshot.balances());
  }
}
