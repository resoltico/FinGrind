package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BalanceMath;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.executor.bookkeeping.PeriodAccountActivityView;
import dev.erst.fingrind.executor.bookkeeping.PeriodCurrencySummaryView;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads bounded period-summary rows from SQLite. */
final class SqlitePeriodSummaryReader {
  PeriodSummaryView periodSummary(
      SqliteNativeDatabase activeDatabase, PeriodSummaryCriteria query) {
    Map<AccountCode, SqliteReportRowValues.AccountTotals> accountActivity =
        SqliteReportRowValues.insertionOrderedMap();
    Map<CurrencyUnit, SqliteReportRowValues.Totals> currencyTotals =
        SqliteReportRowValues.insertionOrderedMap();
    Set<String> postingIds = SqliteReportRowValues.insertionOrderedSet();
    int postingLineCount = 0;
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.loadPeriodSummaryLines(query))) {
      statement.bindText(1, query.effectiveDateFrom().toString());
      statement.bindText(2, query.effectiveDateTo().toString());
      if (query.postingCoverage().isNonClosingOnly()) {
        statement.bindText(3, dev.erst.fingrind.core.PostingKind.PERIOD_CLOSE.wireValue());
      }
      while (statement.step() == SqliteNativeResultCodes.ROW) {
        postingIds.add(
            SqlitePostingMapper.requiredText(statement, SqlitePostingSql.COL_REPORT_POSTING_ID));
        postingLineCount++;
        RegisteredAccount account = SqlitePostingMapper.registeredAccount(statement);
        CurrencyUnit currencyCode = SqliteReportRowValues.reportCurrencyCode(statement);
        long amountMinor = SqliteReportRowValues.reportAmountMinor(statement);
        JournalLine.EntrySide entrySide =
            JournalLine.EntrySide.fromWireValue(
                SqlitePostingMapper.requiredText(
                    statement, SqlitePostingSql.COL_REPORT_ENTRY_SIDE));
        SqliteReportRowValues.accountTotalsFor(accountActivity, account)
            .add(currencyCode, entrySide, amountMinor);
        SqliteReportRowValues.totalsFor(currencyTotals, currencyCode).add(entrySide, amountMinor);
      }
    }
    List<PeriodCurrencySummaryView> currencySummaryRows =
        currencyTotals.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(SqliteReportRowValues.CURRENCY_CODE_ORDER))
            .map(
                entry ->
                    new PeriodCurrencySummaryView(
                        BalanceMath.currencyBalance(
                            entry.getKey(), entry.getValue().debit(), entry.getValue().credit())))
            .toList();
    List<PeriodAccountActivityView> activityRows =
        accountActivity.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(SqliteReportRowValues.ACCOUNT_CODE_ORDER))
            .flatMap(entry -> entry.getValue().periodActivityRows().stream())
            .toList();
    return new PeriodSummaryView(
        query.effectiveDateFrom(),
        query.effectiveDateTo(),
        query.postingCoverage(),
        postingIds.size(),
        postingLineCount,
        accountActivity.size(),
        currencySummaryRows,
        activityRows);
  }
}
