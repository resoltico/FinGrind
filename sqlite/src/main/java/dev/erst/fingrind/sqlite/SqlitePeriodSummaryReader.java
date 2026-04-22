package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.PeriodCurrencySummary;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.PeriodSummaryReport;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.NormalBalance;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads bounded period-summary rows from SQLite. */
final class SqlitePeriodSummaryReader {
  PeriodSummaryReport periodSummary(SqliteNativeDatabase activeDatabase, PeriodSummaryQuery query) {
    Map<AccountCode, SqliteReportRowValues.AccountTotals> accountActivity =
        SqliteReportRowValues.insertionOrderedMap();
    Map<CurrencyCode, SqliteReportRowValues.Totals> currencyTotals =
        SqliteReportRowValues.insertionOrderedMap();
    Set<String> postingIds = SqliteReportRowValues.insertionOrderedSet();
    int postingLineCount = 0;
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.loadPeriodSummaryLines())) {
      statement.bindText(1, query.effectiveDateFrom().toString());
      statement.bindText(2, query.effectiveDateTo().toString());
      while (statement.step() == SqliteNativeResultCodes.ROW) {
        postingIds.add(
            SqlitePostingMapper.requiredText(statement, SqlitePostingSql.COL_REPORT_POSTING_ID));
        postingLineCount++;
        DeclaredAccount account = SqlitePostingMapper.declaredAccount(statement);
        CurrencyCode currencyCode = SqliteReportRowValues.reportCurrencyCode(statement);
        BigDecimal amount = SqliteReportRowValues.reportAmount(statement);
        JournalLine.EntrySide entrySide =
            JournalLine.EntrySide.fromWireValue(
                SqlitePostingMapper.requiredText(
                    statement, SqlitePostingSql.COL_REPORT_ENTRY_SIDE));
        SqliteReportRowValues.accountTotalsFor(accountActivity, account)
            .add(currencyCode, entrySide, amount);
        SqliteReportRowValues.totalsFor(currencyTotals, currencyCode).add(entrySide, amount);
      }
    }
    List<PeriodCurrencySummary> currencySummaryRows =
        currencyTotals.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(SqliteReportRowValues.CURRENCY_CODE_ORDER))
            .map(
                entry ->
                    new PeriodCurrencySummary(
                        SqliteBalanceMath.currencyBalance(
                            entry.getKey(),
                            entry.getValue().debit(),
                            entry.getValue().credit(),
                            NormalBalance.DEBIT)))
            .toList();
    List<PeriodAccountActivityRow> activityRows =
        accountActivity.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(SqliteReportRowValues.ACCOUNT_CODE_ORDER))
            .flatMap(entry -> entry.getValue().periodActivityRows().stream())
            .toList();
    return new PeriodSummaryReport(
        query.effectiveDateFrom(),
        query.effectiveDateTo(),
        postingIds.size(),
        postingLineCount,
        accountActivity.size(),
        currencySummaryRows,
        activityRows);
  }
}
