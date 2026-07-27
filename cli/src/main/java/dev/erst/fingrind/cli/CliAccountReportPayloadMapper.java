package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAccountReportJsonModels;
import dev.erst.fingrind.cli.json.CliReportJsonModels;
import dev.erst.fingrind.cli.json.CliReportValueJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerPageCursor;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.core.CurrencyBalance;
import java.time.Instant;

/** Projects account-centric canonical reports into semantic machine payloads. */
final class CliAccountReportPayloadMapper {
  private CliAccountReportPayloadMapper() {}

  static CliAccountReportJsonModels.AccountBalancePayload accountBalance(
      AccountBalanceSnapshot report, Instant generatedAt) {
    return new CliAccountReportJsonModels.AccountBalancePayload(
        CliReportPayloadMappingSupport.family(OperationId.ACCOUNT_BALANCE),
        CliReportPayloadMappingSupport.bookIdentity(report.bookIdentity()),
        new CliReportJsonModels.AccountBalanceResolvedQuery(
            report.account().accountCode().value(),
            CliReportPayloadMappingSupport.date(report.effectiveDateFrom().orElse(null)),
            CliReportPayloadMappingSupport.date(report.effectiveDateTo().orElse(null)),
            report.postingCoverage().name()),
        CliReportPayloadMappingSupport.instant(generatedAt),
        CliReportPayloadMappingSupport.account(report.account()),
        CliReportPayloadMappingSupport.balances(report.balances()));
  }

  static CliAccountReportJsonModels.TrialBalancePayload trialBalance(
      TrialBalanceReport report, Instant generatedAt) {
    CliReportJsonModels.@org.jspecify.annotations.Nullable ComparativeRangePayload
        comparativeRange =
            CliReportPayloadMappingSupport.comparativeRange(report.comparativeEffectiveDateRange());
    CliAccountReportJsonModels.@org.jspecify.annotations.Nullable TrialBalanceComparativePayload
        comparative =
            report.comparativeRows().isEmpty() && report.comparativeTotals().isEmpty()
                ? null
                : new CliAccountReportJsonModels.TrialBalanceComparativePayload(
                    CliReportPayloadMappingSupport.balanceState(report.comparativeBalanced()),
                    report.comparativeRows().stream()
                        .map(CliAccountReportPayloadMapper::trialBalanceRow)
                        .toList(),
                    CliReportPayloadMappingSupport.balances(report.comparativeTotals()));
    return new CliAccountReportJsonModels.TrialBalancePayload(
        CliReportPayloadMappingSupport.family(OperationId.TRIAL_BALANCE),
        CliReportPayloadMappingSupport.bookIdentity(report.bookIdentity()),
        new CliReportJsonModels.TrialBalanceResolvedQuery(
            CliReportPayloadMappingSupport.date(report.resolvedEffectiveDateAsOf().orElse(null)),
            report.postingCoverage().name(),
            comparativeRange),
        CliReportPayloadMappingSupport.instant(generatedAt),
        CliReportPayloadMappingSupport.balanceState(report.balanced()),
        report.rows().stream().map(CliAccountReportPayloadMapper::trialBalanceRow).toList(),
        CliReportPayloadMappingSupport.balances(report.totals()),
        comparative);
  }

  static CliAccountReportJsonModels.AccountLedgerPayload accountLedger(
      AccountLedgerReport report, Instant generatedAt) {
    return new CliAccountReportJsonModels.AccountLedgerPayload(
        CliReportPayloadMappingSupport.family(OperationId.ACCOUNT_LEDGER),
        CliReportPayloadMappingSupport.bookIdentity(report.bookIdentity()),
        new CliReportJsonModels.AccountLedgerResolvedQuery(
            report.account().accountCode().value(),
            CliReportPayloadMappingSupport.date(
                report.effectiveDateRange().effectiveDateFrom().orElse(null)),
            CliReportPayloadMappingSupport.date(
                report.effectiveDateRange().effectiveDateTo().orElse(null)),
            report.postingCoverage().name(),
            new CliReportJsonModels.PaginationPayload(
                report.pagination().limit(),
                report.pagination().cursor().map(AccountLedgerPageCursor::wireValue).orElse(null))),
        CliReportPayloadMappingSupport.instant(generatedAt),
        CliReportPayloadMappingSupport.account(report.account()),
        CliReportPayloadMappingSupport.balances(report.openingBalances()),
        report.entries().stream().map(CliAccountReportPayloadMapper::accountLedgerRow).toList(),
        CliReportPayloadMappingSupport.balances(report.closingBalances()),
        report.pagination().nextCursor().map(AccountLedgerPageCursor::wireValue).orElse(null));
  }

  static CliAccountReportJsonModels.PeriodSummaryPayload periodSummary(
      PeriodSummaryReport report, Instant generatedAt) {
    return new CliAccountReportJsonModels.PeriodSummaryPayload(
        CliReportPayloadMappingSupport.family(OperationId.PERIOD_SUMMARY),
        CliReportPayloadMappingSupport.bookIdentity(report.bookIdentity()),
        CliReportPayloadMappingSupport.periodQuery(
            report.effectiveDateFrom(), report.effectiveDateTo(), report.postingCoverage(), null),
        CliReportPayloadMappingSupport.instant(generatedAt),
        report.postingCount(),
        report.postingLineCount(),
        report.accountsTouched(),
        report.currencyTotals().stream()
            .map(total -> CliReportPayloadMappingSupport.balance(total.totals()))
            .toList(),
        report.accountActivity().stream()
            .map(row -> accountBalanceRow(row.account(), row.movement()))
            .toList());
  }

  private static CliAccountReportJsonModels.AccountBalanceRowPayload trialBalanceRow(
      TrialBalanceRow row) {
    return accountBalanceRow(row.account(), row.balance());
  }

  private static CliAccountReportJsonModels.AccountBalanceRowPayload accountBalanceRow(
      DeclaredAccount account, CurrencyBalance balance) {
    CliReportValueJsonModels.AccountPayload accountPayload =
        CliReportPayloadMappingSupport.account(account);
    CliReportValueJsonModels.BalancePayload balancePayload =
        CliReportPayloadMappingSupport.balance(balance);
    return new CliAccountReportJsonModels.AccountBalanceRowPayload(
        accountPayload.accountCode(),
        accountPayload.accountName(),
        accountPayload.accountType(),
        accountPayload.normalBalance(),
        accountPayload.active(),
        balancePayload.currencyCode(),
        balancePayload.debitTotal(),
        balancePayload.creditTotal(),
        balancePayload.netAmount(),
        balancePayload.balanceSide());
  }

  private static CliAccountReportJsonModels.AccountLedgerRowPayload accountLedgerRow(
      AccountLedgerEntry row) {
    return new CliAccountReportJsonModels.AccountLedgerRowPayload(
        row.postingFact().postingId().value(),
        row.postingFact().journalEntry().effectiveDate().toString(),
        CliReportPayloadMappingSupport.balance(row.movement()),
        CliReportPayloadMappingSupport.money(row.runningNetAmount()),
        row.runningBalanceSide().name(),
        CliAttestationCommitPresentation.payload(row.attestationCommit()));
  }
}
