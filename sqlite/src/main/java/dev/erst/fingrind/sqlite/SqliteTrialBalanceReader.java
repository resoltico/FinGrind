package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.TrialBalanceReport;
import dev.erst.fingrind.contract.TrialBalanceRow;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.JournalLine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Loads canonical trial-balance rows from SQLite. */
final class SqliteTrialBalanceReader {
  TrialBalanceReport trialBalance(SqliteNativeDatabase activeDatabase, TrialBalanceQuery query) {
    Map<AccountCode, SqliteReportRowValues.AccountTotals> totalsByAccount =
        SqliteReportRowValues.insertionOrderedMap();
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.loadTrialBalanceLines(query))) {
      if (query.effectiveDateTo().isPresent()) {
        statement.bindText(1, query.effectiveDateTo().orElseThrow().toString());
      }
      while (statement.step() == SqliteNativeResultCodes.ROW) {
        DeclaredAccount account = SqlitePostingMapper.declaredAccount(statement);
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
    List<TrialBalanceRow> rows = new ArrayList<>();
    totalsByAccount
        .values()
        .forEach(accountTotals -> rows.addAll(accountTotals.trialBalanceRows()));
    return new TrialBalanceReport(query.effectiveDateTo(), rows);
  }
}
