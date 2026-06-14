package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceRowView;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Loads canonical trial-balance rows from SQLite. */
final class SqliteTrialBalanceReader {
  TrialBalanceView trialBalance(SqliteNativeDatabase activeDatabase, TrialBalanceCriteria query) {
    var resolvedEffectiveDateAsOf =
        query.effectiveDateAsOf().isPresent()
            ? query.effectiveDateAsOf()
            : latestPostingEffectiveDate(activeDatabase);
    Map<AccountCode, SqliteReportRowValues.AccountTotals> totalsByAccount =
        SqliteReportRowValues.insertionOrderedMap();
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.loadTrialBalanceLines(query))) {
      if (query.effectiveDateAsOf().isPresent()) {
        statement.bindText(
            1, CanonicalTemporalText.formatLocalDate(query.effectiveDateAsOf().orElseThrow()));
      }
      while (statement.step() == SqliteNativeResultCode.code("ROW")) {
        RegisteredAccount account = SqlitePostingMapper.registeredAccount(statement);
        CurrencyUnit currencyCode = SqliteReportRowValues.reportCurrencyCode(statement);
        long amountMinor = SqliteReportRowValues.reportAmountMinor(statement);
        JournalLine.EntrySide entrySide =
            JournalLine.EntrySide.fromWireValue(
                SqlitePostingMapper.requiredText(
                    statement, SqlitePostingSql.COL_REPORT_ENTRY_SIDE));
        SqliteReportRowValues.accountTotalsFor(totalsByAccount, account)
            .add(currencyCode, entrySide, amountMinor);
      }
    }
    List<TrialBalanceRowView> rows = new ArrayList<>();
    totalsByAccount
        .values()
        .forEach(accountTotals -> rows.addAll(accountTotals.trialBalanceRows()));
    BookIdentity bookIdentity =
        SqliteStatementQueries.loadBookIdentity(activeDatabase)
            .orElseThrow(
                () ->
                    new IllegalStateException("Initialized SQLite book is missing book identity."));
    return new TrialBalanceView(
        bookIdentity,
        query.effectiveDateAsOf(),
        resolvedEffectiveDateAsOf,
        dev.erst.fingrind.core.EffectiveDateRange.unbounded(),
        query.postingCoverage(),
        rows,
        List.of());
  }

  private static java.util.Optional<java.time.LocalDate> latestPostingEffectiveDate(
      SqliteNativeDatabase activeDatabase) {
    return SqliteStatementQueries.loadOptionalText(
            activeDatabase, SqlitePostingSql.FIND_LATEST_POSTING_EFFECTIVE_DATE, statement -> {})
        .map(java.time.LocalDate::parse);
  }
}
