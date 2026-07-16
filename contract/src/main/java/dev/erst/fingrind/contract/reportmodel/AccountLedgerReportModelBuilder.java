package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import java.util.List;

/** Builds the shared report model for account-ledger reports. */
public final class AccountLedgerReportModelBuilder
    implements ReportModelBuilder<AccountLedgerReport> {
  /** Shared reusable builder instance. */
  public static final AccountLedgerReportModelBuilder INSTANCE =
      new AccountLedgerReportModelBuilder();

  private AccountLedgerReportModelBuilder() {}

  @Override
  public ReportModel build(AccountLedgerReport report) {
    return buildModel(report);
  }

  /** Builds one account-ledger report model. */
  public static ReportModel buildModel(AccountLedgerReport report) {
    List<ReportVerdict> verdicts = new java.util.ArrayList<>();
    verdicts.add(new ReportVerdict("Account", ReportModelDisplay.accountLabel(report.account())));
    verdicts.add(
        new ReportVerdict(
            "Effective date range",
            ReportModelNarrative.dateRange(
                report.effectiveDateRange().effectiveDateFrom().orElse(null),
                report.effectiveDateRange().effectiveDateTo().orElse(null))));
    if (ReportModelNarrative.hasMeaningfulBalances(report.openingBalances())) {
      verdicts.add(
          new ReportVerdict(
              "Opening Balances",
              ReportModelNarrative.joinedBalancesText(report.openingBalances())));
    }
    verdicts.add(
        new ReportVerdict(
            "Closing Balances", ReportModelNarrative.joinedBalancesText(report.closingBalances())));
    verdicts.add(new ReportVerdict("Page limit", Integer.toString(report.pagination().limit())));
    verdicts.add(
        new ReportVerdict(
            "Next cursor",
            report.pagination().nextCursor().map(cursor -> cursor.wireValue()).orElse("(none)")));
    verdicts.add(
        new ReportVerdict(
            "Outcome",
            report.entries().isEmpty()
                ? ReportModelNarrative.noMatches("ledger entries")
                : report.entries().size() + " ledger entries"));
    return new ReportModel(
        dev.erst.fingrind.contract.protocol.OperationId.ACCOUNT_LEDGER.wireName(),
        ReportModelSupport.reportTitle(
            dev.erst.fingrind.contract.protocol.OperationId.ACCOUNT_LEDGER),
        ReportModel.Orientation.LANDSCAPE,
        ReportModelSupport.context(
            report.bookIdentity(),
            report.postingCoverage(),
            report.effectiveDateRange().effectiveDateFrom().orElse(null),
            report.effectiveDateRange().effectiveDateTo().orElse(null),
            null,
            dev.erst.fingrind.core.EffectiveDateRange.unbounded(),
            List.of(
                new ReportVerdict(
                    "Effective date from",
                    report
                        .effectiveDateRange()
                        .effectiveDateFrom()
                        .map(java.time.LocalDate::toString)
                        .orElse("book start")),
                new ReportVerdict(
                    "Effective date to",
                    report
                        .effectiveDateRange()
                        .effectiveDateTo()
                        .map(java.time.LocalDate::toString)
                        .orElse("current book horizon")),
                new ReportVerdict(
                    "Account type",
                    ReportModelDisplay.displayLineType(report.account().accountType())),
                new ReportVerdict(
                    "Normal balance",
                    ReportModelDisplay.displayNormalBalance(report.account().normalBalance())),
                new ReportVerdict(
                    "Active", ReportModelDisplay.displayBoolean(report.account().active())))),
        List.copyOf(verdicts),
        List.of(
            ReportModelSupport.section(
                "entries",
                "Ledger Entries",
                List.of(),
                entryColumns(),
                report.entries().stream().map(entry -> entryRow(report.account(), entry)).toList(),
                List.of())));
  }

  private static List<ReportColumn> entryColumns() {
    return List.of(
        ReportModelSupport.leftColumn("effectiveDate", "Effective date"),
        ReportModelSupport.leftColumn("entry", "Entry"),
        ReportModelSupport.rightColumn("debit", "Debit"),
        ReportModelSupport.rightColumn("credit", "Credit"),
        ReportModelSupport.leftColumn("running", "Running"),
        ReportModelSupport.leftColumn("counterparts", "Counterpart account codes"),
        ReportModelSupport.leftColumn("postingRef", "Posting ref"));
  }

  private static ReportRow entryRow(
      dev.erst.fingrind.contract.bookkeeping.DeclaredAccount account, AccountLedgerEntry entry) {
    return ReportModelSupport.row(
        entry.postingFact().postingId().value(),
        entry.postingFact().journalEntry().effectiveDate().toString(),
        ReportModelNarrative.accountLedgerEntrySummary(entry.postingFact()),
        ReportModelDisplay.displayMoney(entry.movement().debitTotal()),
        ReportModelDisplay.displayMoney(entry.movement().creditTotal()),
        ReportModelNarrative.runningBalance(entry),
        ReportModelNarrative.counterpartAccounts(account, entry.postingFact()),
        entry.postingFact().postingId().value());
  }
}
