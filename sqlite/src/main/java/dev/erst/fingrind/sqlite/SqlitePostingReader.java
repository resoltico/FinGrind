package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.BalanceMath;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryCursor;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Shared SQLite read helpers for postings, posting lines, and account balances. */
final class SqlitePostingReader {
  Optional<AccountBalanceView> accountBalance(
      SqliteNativeDatabase activeDatabase, AccountBalanceCriteria query) {
    Optional<RegisteredAccount> account =
        SqliteStatementQueries.findOneAccount(activeDatabase, query.accountCode());
    if (account.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(accountBalance(activeDatabase, query, account.orElseThrow()));
  }

  AccountBalanceView accountBalance(
      SqliteNativeDatabase activeDatabase,
      AccountBalanceCriteria query,
      RegisteredAccount account) {
    return new AccountBalanceView(
        account,
        query.effectiveDateRange(),
        query.postingCoverage(),
        loadCurrencyBalances(activeDatabase, query));
  }

  PostingHistoryPage loadPostingPage(
      SqliteNativeDatabase activeDatabase, PostingHistoryQuery query) {
    List<CommittedPosting> postings = new ArrayList<>();
    String sql = SqlitePostingSql.listPostings(query);
    try (SqliteNativeStatement statement = activeDatabase.prepare(sql)) {
      bindPostingPageQuery(statement, query);
      while (statement.step() == SqliteNativeResultCodes.ROW) {
        postings.add(loadPostingRow(activeDatabase, statement));
      }
    }
    boolean hasMore = postings.size() > query.limit();
    List<CommittedPosting> pageItems = hasMore ? postings.subList(0, query.limit()) : postings;
    Optional<PostingHistoryCursor> nextCursor =
        hasMore ? Optional.of(postingHistoryCursor(pageItems.getLast())) : Optional.empty();
    return new PostingHistoryPage(pageItems, query.limit(), nextCursor);
  }

  Optional<CommittedPosting> findOneCommittedPosting(
      SqliteNativeDatabase activeDatabase, String sql, SqliteStatementQueries.Binder binder) {
    return SqliteStatementQueries.findOneCommittedPosting(
        activeDatabase, sql, binder, postingId -> loadAttachments(activeDatabase, postingId));
  }

  List<CommittedPosting> loadCommittedPostings(
      SqliteNativeDatabase activeDatabase, String sql, SqliteStatementQueries.Binder binder) {
    List<CommittedPosting> postings = new ArrayList<>();
    try (SqliteNativeStatement statement = activeDatabase.prepare(sql)) {
      binder.bind(statement);
      while (statement.step() == SqliteNativeResultCodes.ROW) {
        postings.add(loadPostingRow(activeDatabase, statement));
      }
    }
    return List.copyOf(postings);
  }

  List<AccountCurrencyTotals> loadAccountTotals(
      SqliteNativeDatabase activeDatabase,
      EffectiveDateRange effectiveDateRange,
      PostingCoverage postingCoverage) {
    String sql = SqlitePostingSql.loadAccountTotals(effectiveDateRange, postingCoverage);
    List<AccountCurrencyTotals> totals = new ArrayList<>();
    try (SqliteNativeStatement statement = activeDatabase.prepare(sql)) {
      int bindIndex = 1;
      if (effectiveDateRange.effectiveDateFrom().isPresent()) {
        statement.bindText(
            bindIndex,
            CanonicalTemporalText.formatLocalDate(
                effectiveDateRange.effectiveDateFrom().orElseThrow()));
        bindIndex++;
      }
      if (effectiveDateRange.effectiveDateTo().isPresent()) {
        statement.bindText(
            bindIndex,
            CanonicalTemporalText.formatLocalDate(
                effectiveDateRange.effectiveDateTo().orElseThrow()));
      }
      while (statement.step() == SqliteNativeResultCodes.ROW) {
        totals.add(
            new AccountCurrencyTotals(
                SqlitePostingMapper.registeredAccount(statement),
                SqlitePersistedMoneyCodec.readCurrencyUnit(
                    statement, SqlitePostingSql.COL_TOTAL_CURRENCY_CODE),
                statement.columnLong(SqlitePostingSql.COL_TOTAL_DEBIT_MINOR),
                statement.columnLong(SqlitePostingSql.COL_TOTAL_CREDIT_MINOR)));
      }
    }
    return List.copyOf(totals);
  }

  private CommittedPosting loadPostingRow(
      SqliteNativeDatabase activeDatabase, SqliteNativeStatement statement) {
    PostingId postingId =
        new PostingId(SqlitePostingMapper.requiredText(statement, SqlitePostingSql.COL_POSTING_ID));
    SqliteStatementQueries.PostingAttachments attachments =
        loadAttachments(activeDatabase, postingId);
    return SqlitePostingMapper.committedPosting(
        statement, attachments.lines(), attachments.evidence());
  }

  private List<JournalLine> loadLines(SqliteNativeDatabase activeDatabase, PostingId postingId) {
    try (SqliteNativeStatement statement = activeDatabase.prepare(SqlitePostingSql.LOAD_LINES)) {
      statement.bindText(1, postingId.value());
      return SqlitePostingMapper.journalLines(statement);
    }
  }

  private SqliteStatementQueries.PostingAttachments loadAttachments(
      SqliteNativeDatabase activeDatabase, PostingId postingId) {
    return new SqliteStatementQueries.PostingAttachments(
        loadLines(activeDatabase, postingId), loadEvidence(activeDatabase, postingId));
  }

  private dev.erst.fingrind.core.AccountingEvidence loadEvidence(
      SqliteNativeDatabase activeDatabase, PostingId postingId) {
    try (SqliteNativeStatement sourceDocumentRows =
            activeDatabase.prepare(SqlitePostingSql.LOAD_SOURCE_DOCUMENTS);
        SqliteNativeStatement approvalRows =
            activeDatabase.prepare(SqlitePostingSql.LOAD_APPROVALS)) {
      sourceDocumentRows.bindText(1, postingId.value());
      approvalRows.bindText(1, postingId.value());
      return SqlitePostingMapper.accountingEvidence(sourceDocumentRows, approvalRows);
    }
  }

  private List<CurrencyBalance> loadCurrencyBalances(
      SqliteNativeDatabase activeDatabase, AccountBalanceCriteria query) {
    String sql = SqlitePostingSql.loadAccountLinesForBalance(query);
    Map<CurrencyUnit, Totals> totalsByCurrency = mutableTotalsByCurrency();
    try (SqliteNativeStatement statement = activeDatabase.prepare(sql)) {
      bindAccountBalanceQuery(statement, query);
      while (statement.step() == SqliteNativeResultCodes.ROW) {
        JournalLine.EntrySide side = readEntrySide(statement);
        CurrencyUnit currencyCode = readCurrencyCode(statement);
        long amountMinor = readAmountMinor(statement);
        Totals totals = totalsFor(totalsByCurrency, currencyCode);
        if (side == JournalLine.EntrySide.DEBIT) {
          totals.debit = Math.addExact(totals.debit, amountMinor);
        } else {
          totals.credit = Math.addExact(totals.credit, amountMinor);
        }
      }
    }
    List<CurrencyBalance> balances = new ArrayList<>();
    for (CurrencyUnit currencyCode : orderedCurrencyCodes(totalsByCurrency.keySet())) {
      Totals totals = Objects.requireNonNull(totalsByCurrency.get(currencyCode));
      balances.add(balance(currencyCode, totals));
    }
    return List.copyOf(balances);
  }

  static List<CurrencyUnit> orderedCurrencyCodes(Iterable<CurrencyUnit> currencyCodes) {
    List<CurrencyUnit> ordered = new ArrayList<>();
    currencyCodes.forEach(ordered::add);
    ordered.sort(Comparator.comparing(CurrencyUnit::code));
    return List.copyOf(ordered);
  }

  private static void bindPostingPageQuery(
      SqliteNativeStatement statement, PostingHistoryQuery query) {
    int bindIndex = 1;
    if (query.accountCode().isPresent()) {
      statement.bindText(bindIndex, query.accountCode().orElseThrow().value());
      bindIndex++;
    }
    if (query.effectiveDateRange().effectiveDateFrom().isPresent()) {
      statement.bindText(
          bindIndex,
          CanonicalTemporalText.formatLocalDate(
              query.effectiveDateRange().effectiveDateFrom().orElseThrow()));
      bindIndex++;
    }
    if (query.effectiveDateRange().effectiveDateTo().isPresent()) {
      statement.bindText(
          bindIndex,
          CanonicalTemporalText.formatLocalDate(
              query.effectiveDateRange().effectiveDateTo().orElseThrow()));
      bindIndex++;
    }
    if (query.cursor().isPresent()) {
      PostingHistoryCursor cursor = query.cursor().orElseThrow();
      statement.bindText(bindIndex, CanonicalTemporalText.formatLocalDate(cursor.effectiveDate()));
      bindIndex++;
      statement.bindText(bindIndex, CanonicalTemporalText.formatLocalDate(cursor.effectiveDate()));
      bindIndex++;
      statement.bindText(bindIndex, CanonicalTemporalText.formatUtcInstant(cursor.recordedAt()));
      bindIndex++;
      statement.bindText(bindIndex, CanonicalTemporalText.formatLocalDate(cursor.effectiveDate()));
      bindIndex++;
      statement.bindText(bindIndex, CanonicalTemporalText.formatUtcInstant(cursor.recordedAt()));
      bindIndex++;
      statement.bindText(bindIndex, cursor.postingId().value());
      bindIndex++;
    }
    statement.bindInt(bindIndex, query.limit() + 1);
  }

  private static Totals totalsFor(
      Map<CurrencyUnit, Totals> totalsByCurrency, CurrencyUnit currencyCode) {
    return totalsByCurrency.computeIfAbsent(currencyCode, _ -> new Totals());
  }

  private static Map<CurrencyUnit, Totals> mutableTotalsByCurrency() {
    return SqliteReportRowValues.insertionOrderedMap();
  }

  private static void bindAccountBalanceQuery(
      SqliteNativeStatement statement, AccountBalanceCriteria query) {
    int bindIndex = 1;
    statement.bindText(bindIndex, query.accountCode().value());
    bindIndex++;
    if (query.postingCoverage().isNonClosingOnly()) {
      statement.bindText(
          bindIndex, dev.erst.fingrind.core.PostingKind.PERIOD_RESULT_TRANSFER.wireValue());
      bindIndex++;
    }
    if (query.effectiveDateRange().effectiveDateFrom().isPresent()) {
      statement.bindText(
          bindIndex,
          CanonicalTemporalText.formatLocalDate(
              query.effectiveDateRange().effectiveDateFrom().orElseThrow()));
      bindIndex++;
    }
    if (query.effectiveDateRange().effectiveDateTo().isPresent()) {
      statement.bindText(
          bindIndex,
          CanonicalTemporalText.formatLocalDate(
              query.effectiveDateRange().effectiveDateTo().orElseThrow()));
    }
  }

  private static JournalLine.EntrySide readEntrySide(SqliteNativeStatement statement) {
    return JournalLine.EntrySide.fromWireValue(SqlitePostingMapper.requiredText(statement, 0));
  }

  private static CurrencyUnit readCurrencyCode(SqliteNativeStatement statement) {
    return SqlitePersistedMoneyCodec.readCurrencyUnit(statement, 1);
  }

  private static long readAmountMinor(SqliteNativeStatement statement) {
    return statement.columnLong(2);
  }

  private static CurrencyBalance balance(CurrencyUnit currencyCode, Totals totals) {
    return BalanceMath.currencyBalance(currencyCode, totals.debit, totals.credit);
  }

  private static PostingHistoryCursor postingHistoryCursor(CommittedPosting posting) {
    return new PostingHistoryCursor(
        posting.journalEntry().effectiveDate(),
        posting.provenance().recordedAt(),
        posting.postingId());
  }

  /** Running debit and credit totals for one account/currency balance bucket. */
  private static final class Totals {
    private long debit;
    private long credit;
  }
}
