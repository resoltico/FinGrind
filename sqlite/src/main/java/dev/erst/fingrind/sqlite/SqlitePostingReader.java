package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.CurrencyBalance;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingPage;
import dev.erst.fingrind.contract.PostingPageCursor;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Shared SQLite read helpers for postings, posting lines, and account balances. */
final class SqlitePostingReader {
  Optional<AccountBalanceSnapshot> accountBalance(
      SqliteNativeDatabase activeDatabase, AccountBalanceQuery query) {
    Optional<DeclaredAccount> account =
        SqliteStatementQueries.findOneAccount(activeDatabase, query.accountCode());
    if (account.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(accountBalance(activeDatabase, query, account.orElseThrow()));
  }

  AccountBalanceSnapshot accountBalance(
      SqliteNativeDatabase activeDatabase,
      AccountBalanceQuery query,
      DeclaredAccount declaredAccount) {
    return new AccountBalanceSnapshot(
        declaredAccount,
        query.effectiveDateFrom(),
        query.effectiveDateTo(),
        loadCurrencyBalances(activeDatabase, query));
  }

  PostingPage loadPostingPage(SqliteNativeDatabase activeDatabase, ListPostingsQuery query) {
    List<PostingFact> postings = new ArrayList<>();
    String sql = SqlitePostingSql.listPostings(query);
    try (SqliteNativeStatement statement = activeDatabase.prepare(sql)) {
      bindPostingPageQuery(statement, query);
      while (statement.step() == SqliteNativeResultCodes.ROW) {
        postings.add(loadPostingRow(activeDatabase, statement));
      }
    }
    boolean hasMore = postings.size() > query.limit();
    List<PostingFact> pageItems = hasMore ? postings.subList(0, query.limit()) : postings;
    Optional<PostingPageCursor> nextCursor =
        hasMore
            ? Optional.of(PostingPageCursor.fromPosting(pageItems.getLast()))
            : Optional.empty();
    return new PostingPage(pageItems, query.limit(), nextCursor);
  }

  Optional<PostingFact> findOnePosting(
      SqliteNativeDatabase activeDatabase, String sql, SqliteStatementQueries.Binder binder) {
    return SqliteStatementQueries.findOnePosting(
        activeDatabase, sql, binder, postingId -> loadLines(activeDatabase, postingId));
  }

  List<PostingFact> loadPostingFacts(
      SqliteNativeDatabase activeDatabase, String sql, SqliteStatementQueries.Binder binder) {
    List<PostingFact> postings = new ArrayList<>();
    try (SqliteNativeStatement statement = activeDatabase.prepare(sql)) {
      binder.bind(statement);
      while (statement.step() == SqliteNativeResultCodes.ROW) {
        postings.add(loadPostingRow(activeDatabase, statement));
      }
    }
    return List.copyOf(postings);
  }

  private PostingFact loadPostingRow(
      SqliteNativeDatabase activeDatabase, SqliteNativeStatement statement) {
    PostingId postingId =
        new PostingId(SqlitePostingMapper.requiredText(statement, SqlitePostingSql.COL_POSTING_ID));
    return SqlitePostingMapper.postingFact(statement, loadLines(activeDatabase, postingId));
  }

  private List<JournalLine> loadLines(SqliteNativeDatabase activeDatabase, PostingId postingId) {
    try (SqliteNativeStatement statement = activeDatabase.prepare(SqlitePostingSql.LOAD_LINES)) {
      statement.bindText(1, postingId.value());
      return SqlitePostingMapper.journalLines(statement);
    }
  }

  private List<CurrencyBalance> loadCurrencyBalances(
      SqliteNativeDatabase activeDatabase, AccountBalanceQuery query) {
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
      SqliteNativeStatement statement, ListPostingsQuery query) {
    int bindIndex = 1;
    if (query.accountCode().isPresent()) {
      statement.bindText(bindIndex, query.accountCode().orElseThrow().value());
      bindIndex++;
    }
    if (query.effectiveDateFrom().isPresent()) {
      statement.bindText(bindIndex, query.effectiveDateFrom().orElseThrow().toString());
      bindIndex++;
    }
    if (query.effectiveDateTo().isPresent()) {
      statement.bindText(bindIndex, query.effectiveDateTo().orElseThrow().toString());
      bindIndex++;
    }
    if (query.cursor().isPresent()) {
      PostingPageCursor cursor = query.cursor().orElseThrow();
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
      SqliteNativeStatement statement, AccountBalanceQuery query) {
    int bindIndex = 1;
    statement.bindText(bindIndex, query.accountCode().value());
    bindIndex++;
    if (query.effectiveDateFrom().isPresent()) {
      statement.bindText(bindIndex, query.effectiveDateFrom().orElseThrow().toString());
      bindIndex++;
    }
    if (query.effectiveDateTo().isPresent()) {
      statement.bindText(bindIndex, query.effectiveDateTo().orElseThrow().toString());
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

  /** Running debit and credit totals for one account/currency balance bucket. */
  private static final class Totals {
    private BigDecimal debit = BigDecimal.ZERO;
    private BigDecimal credit = BigDecimal.ZERO;
  }
}
