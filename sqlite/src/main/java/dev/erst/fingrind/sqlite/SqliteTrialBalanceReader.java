package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceRowView;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Loads canonical trial-balance rows from SQLite. */
final class SqliteTrialBalanceReader {
  TrialBalanceView trialBalance(SqliteNativeDatabase activeDatabase, TrialBalanceCriteria query) {
    Map<AccountCode, SqliteReportRowValues.AccountTotals> totalsByAccount =
        SqliteReportRowValues.insertionOrderedMap();
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.loadTrialBalanceLines(query))) {
      if (query.effectiveDateTo().isPresent()) {
        statement.bindText(1, query.effectiveDateTo().orElseThrow().toString());
      }
      while (statement.step() == SqliteNativeResultCodes.ROW) {
        RegisteredAccount account = SqlitePostingMapper.registeredAccount(statement);
        CurrencyCode currencyCode = SqliteReportRowValues.reportCurrencyCode(statement);
        BigDecimal amount = SqliteReportRowValues.reportAmount(statement);
        JournalLine.EntrySide entrySide =
            JournalLine.EntrySide.fromWireValue(
                SqlitePostingMapper.requiredText(
                    statement, SqlitePostingSql.COL_REPORT_ENTRY_SIDE));
        SqliteReportRowValues.accountTotalsFor(totalsByAccount, account)
            .add(currencyCode, entrySide, amount);
      }
    }
    List<TrialBalanceRowView> rows = new ArrayList<>();
    totalsByAccount
        .values()
        .forEach(accountTotals -> rows.addAll(accountTotals.trialBalanceRows()));
    return new TrialBalanceView(query.effectiveDateTo(), rows);
  }
}
