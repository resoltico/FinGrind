package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryCursor;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        account, query.effectiveDateRange(), loadCurrencyBalances(activeDatabase, query));
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
        activeDatabase, sql, binder, postingId -> loadLines(activeDatabase, postingId));
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

  private CommittedPosting loadPostingRow(
      SqliteNativeDatabase activeDatabase, SqliteNativeStatement statement) {
    PostingId postingId =
        new PostingId(SqlitePostingMapper.requiredText(statement, SqlitePostingSql.COL_POSTING_ID));
    return SqlitePostingMapper.committedPosting(statement, loadLines(activeDatabase, postingId));
  }

  private List<JournalLine> loadLines(SqliteNativeDatabase activeDatabase, PostingId postingId) {
    try (SqliteNativeStatement statement = activeDatabase.prepare(SqlitePostingSql.LOAD_LINES)) {
      statement.bindText(1, postingId.value());
      return SqlitePostingMapper.journalLines(statement);
    }
  }

  private List<CurrencyBalance> loadCurrencyBalances(
      SqliteNativeDatabase activeDatabase, AccountBalanceCriteria query) {
    String sql = SqlitePostingSql.loadAccountLinesForBalance(query);
    Map<CurrencyCode, Totals> totalsByCurrency = mutableTotalsByCurrency();
    try (SqliteNativeStatement statement = activeDatabase.prepare(sql)) {
      bindAccountBalanceQuery(statement, query);
      while (statement.step() == SqliteNativeResultCodes.ROW) {
        JournalLine.EntrySide side = readEntrySide(statement);
        CurrencyCode currencyCode = readCurrencyCode(statement);
        BigDecimal amount = readAmount(statement);
        Totals totals = totalsFor(totalsByCurrency, currencyCode);
        if (side == JournalLine.EntrySide.DEBIT) {
          totals.debit = totals.debit.add(amount);
        } else {
          totals.credit = totals.credit.add(amount);
        }
      }
    }
    List<CurrencyBalance> balances = new ArrayList<>();
    List<Map.Entry<CurrencyCode, Totals>> orderedTotals =
        totalsByCurrency.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().value()))
            .toList();
    for (Map.Entry<CurrencyCode, Totals> entry : orderedTotals) {
      balances.add(balance(entry.getKey(), entry.getValue()));
    }
    return List.copyOf(balances);
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
          bindIndex, query.effectiveDateRange().effectiveDateFrom().orElseThrow().toString());
      bindIndex++;
    }
    if (query.effectiveDateRange().effectiveDateTo().isPresent()) {
      statement.bindText(
          bindIndex, query.effectiveDateRange().effectiveDateTo().orElseThrow().toString());
      bindIndex++;
    }
    if (query.cursor().isPresent()) {
      PostingHistoryCursor cursor = query.cursor().orElseThrow();
      statement.bindText(bindIndex, cursor.effectiveDate().toString());
      bindIndex++;
      statement.bindText(bindIndex, cursor.effectiveDate().toString());
      bindIndex++;
      statement.bindText(bindIndex, cursor.recordedAt().toString());
      bindIndex++;
      statement.bindText(bindIndex, cursor.effectiveDate().toString());
      bindIndex++;
      statement.bindText(bindIndex, cursor.recordedAt().toString());
      bindIndex++;
      statement.bindText(bindIndex, cursor.postingId().value());
      bindIndex++;
    }
    statement.bindInt(bindIndex, query.limit() + 1);
  }

  private static Totals totalsFor(
      Map<CurrencyCode, Totals> totalsByCurrency, CurrencyCode currencyCode) {
    return totalsByCurrency.computeIfAbsent(currencyCode, _ -> new Totals());
  }

  private static Map<CurrencyCode, Totals> mutableTotalsByCurrency() {
    return new HashMap<>();
  }

  private static void bindAccountBalanceQuery(
      SqliteNativeStatement statement, AccountBalanceCriteria query) {
    int bindIndex = 1;
    statement.bindText(bindIndex, query.accountCode().value());
    bindIndex++;
    if (query.effectiveDateRange().effectiveDateFrom().isPresent()) {
      statement.bindText(
          bindIndex, query.effectiveDateRange().effectiveDateFrom().orElseThrow().toString());
      bindIndex++;
    }
    if (query.effectiveDateRange().effectiveDateTo().isPresent()) {
      statement.bindText(
          bindIndex, query.effectiveDateRange().effectiveDateTo().orElseThrow().toString());
    }
  }

  private static JournalLine.EntrySide readEntrySide(SqliteNativeStatement statement) {
    return JournalLine.EntrySide.fromWireValue(SqlitePostingMapper.requiredText(statement, 0));
  }

  private static CurrencyCode readCurrencyCode(SqliteNativeStatement statement) {
    return new CurrencyCode(SqlitePostingMapper.requiredText(statement, 1));
  }

  private static BigDecimal readAmount(SqliteNativeStatement statement) {
    return new BigDecimal(SqlitePostingMapper.requiredText(statement, 2));
  }

  private static CurrencyBalance balance(CurrencyCode currencyCode, Totals totals) {
    return SqliteBalanceMath.currencyBalance(currencyCode, totals.debit, totals.credit);
  }

  private static PostingHistoryCursor postingHistoryCursor(CommittedPosting posting) {
    return new PostingHistoryCursor(
        posting.journalEntry().effectiveDate(),
        posting.provenance().recordedAt(),
        posting.postingId());
  }

  /** Running debit and credit totals for one account/currency balance bucket. */
  private static final class Totals {
    private BigDecimal debit = BigDecimal.ZERO;
    private BigDecimal credit = BigDecimal.ZERO;
  }
}
