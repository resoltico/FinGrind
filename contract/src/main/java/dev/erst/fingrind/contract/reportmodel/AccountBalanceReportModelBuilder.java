package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import java.util.List;

/** Builds the shared report model for account-balance snapshots. */
public final class AccountBalanceReportModelBuilder
    implements ReportModelBuilder<AccountBalanceSnapshot> {
  /** Shared reusable builder instance. */
  public static final AccountBalanceReportModelBuilder INSTANCE =
      new AccountBalanceReportModelBuilder();

  private AccountBalanceReportModelBuilder() {}

  @Override
  public ReportModel build(AccountBalanceSnapshot snapshot) {
    return buildModel(snapshot);
  }

  /** Builds one account-balance report model. */
  public static ReportModel buildModel(AccountBalanceSnapshot snapshot) {
    return new ReportModel(
        dev.erst.fingrind.contract.protocol.OperationId.ACCOUNT_BALANCE.wireName(),
        "Account Balance",
        ReportModel.Orientation.PORTRAIT,
        ReportModelSupport.context(
            snapshot.bookIdentity(),
            snapshot.postingCoverage(),
            snapshot.effectiveDateFrom().orElse(null),
            snapshot.effectiveDateTo().orElse(null),
            null,
            dev.erst.fingrind.core.EffectiveDateRange.unbounded(),
            List.of(
                new ReportVerdict("Account", ReportModelDisplay.accountLabel(snapshot.account())),
                new ReportVerdict(
                    "Account type",
                    ReportModelDisplay.displayLineType(snapshot.account().accountType())),
                new ReportVerdict(
                    "Normal balance",
                    ReportModelDisplay.displayNormalBalance(snapshot.account().normalBalance())),
                new ReportVerdict(
                    "Active", ReportModelDisplay.displayBoolean(snapshot.account().active())))),
        List.of(
            new ReportVerdict("Account", ReportModelDisplay.accountLabel(snapshot.account())),
            new ReportVerdict(
                "Effective date range",
                ReportModelNarrative.dateRange(
                    snapshot.effectiveDateFrom().orElse(null),
                    snapshot.effectiveDateTo().orElse(null)))),
        List.of(
            ReportModelSupport.section(
                "balances",
                "Per-Currency Balances",
                snapshot.balances().isEmpty()
                    ? List.of(
                        new ReportVerdict("Outcome", ReportModelNarrative.noMatches("balances")))
                    : List.of(),
                ReportModelSupport.balanceColumns(),
                ReportModelSupport.balanceRows(snapshot.balances()),
                List.of())));
  }
}
