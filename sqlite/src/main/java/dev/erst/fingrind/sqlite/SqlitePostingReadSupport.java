package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.CurrencyBalance;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingPage;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Shared SQLite read helpers for postings, posting lines, and account balances. */
final class SqlitePostingReadSupport {
  Optional<AccountBalanceSnapshot> accountBalance(
      SqliteNativeDatabase activeDatabase, AccountBalanceQuery query) throws SqliteNativeException {
    Optional<DeclaredAccount> account =
        SqliteStatementQuerySupport.findOneAccount(activeDatabase, query.accountCode());
    if (account.isEmpty()) {
      return Optional.empty();
    }
    DeclaredAccount declaredAccount = account.orElseThrow();
    return Optional.of(
        new AccountBalanceSnapshot(
            declaredAccount,
            query.effectiveDateFrom(),
            query.effectiveDateTo(),
            loadCurrencyBalances(activeDatabase, query, declaredAccount)));
  }

  PostingPage loadPostingPage(SqliteNativeDatabase activeDatabase, ListPostingsQuery query)
      throws SqliteNativeException {
    List<PostingFact> postings = new ArrayList<>();
    boolean filterAccount = query.accountCode().isPresent();
    boolean filterEffectiveDateFrom = query.effectiveDateFrom().isPresent();
    boolean filterEffectiveDateTo = query.effectiveDateTo().isPresent();
    String sql =
        SqlitePostingSql.listPostings(
            filterAccount, filterEffectiveDateFrom, filterEffectiveDateTo);
    try (SqliteNativeStatement statement = activeDatabase.prepare(sql)) {
      bindPostingPageQuery(
          statement, query, filterAccount, filterEffectiveDateFrom, filterEffectiveDateTo);
      while (statement.step() == SqliteNativeLibrary.SQLITE_ROW) {
        postings.add(loadPostingRow(activeDatabase, statement));
      }
    }
    boolean hasMore = postings.size() > query.limit();
    List<PostingFact> pageItems = hasMore ? postings.subList(0, query.limit()) : postings;
    return new PostingPage(pageItems, query.limit(), query.offset(), hasMore);
  }

  Optional<PostingFact> findOnePosting(
      SqliteNativeDatabase activeDatabase, String sql, SqliteStatementQuerySupport.Binder binder)
      throws SqliteNativeException {
    return SqliteStatementQuerySupport.findOnePosting(
        activeDatabase, sql, binder, postingId -> loadLines(activeDatabase, postingId));
  }

  private PostingFact loadPostingRow(
      SqliteNativeDatabase activeDatabase, SqliteNativeStatement statement)
      throws SqliteNativeException {
    PostingId postingId =
        new PostingId(SqlitePostingMapper.requiredText(statement, SqlitePostingSql.COL_POSTING_ID));
    return SqlitePostingMapper.postingFact(statement, loadLines(activeDatabase, postingId));
  }

  private List<JournalLine> loadLines(SqliteNativeDatabase activeDatabase, PostingId postingId)
      throws SqliteNativeException {
    try (SqliteNativeStatement statement = activeDatabase.prepare(SqlitePostingSql.LOAD_LINES)) {
      statement.bindText(1, postingId.value());
      return SqlitePostingMapper.journalLines(statement);
    }
  }

  private List<CurrencyBalance> loadCurrencyBalances(
      SqliteNativeDatabase activeDatabase, AccountBalanceQuery query, DeclaredAccount account)
      throws SqliteNativeException {
    boolean filterEffectiveDateFrom = query.effectiveDateFrom().isPresent();
    boolean filterEffectiveDateTo = query.effectiveDateTo().isPresent();
    String sql =
        SqlitePostingSql.loadAccountLinesForBalance(filterEffectiveDateFrom, filterEffectiveDateTo);
    Map<CurrencyCode, Totals> totalsByCurrency = mutableTotalsByCurrency();
    try (SqliteNativeStatement statement = activeDatabase.prepare(sql)) {
      bindAccountBalanceQuery(statement, query, filterEffectiveDateFrom, filterEffectiveDateTo);
      while (statement.step() == SqliteNativeLibrary.SQLITE_ROW) {
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
      balances.add(balance(entry.getKey(), entry.getValue(), account.normalBalance()));
    }
    return List.copyOf(balances);
  }

  private static void bindPostingPageQuery(
      SqliteNativeStatement statement,
      ListPostingsQuery query,
      boolean filterAccount,
      boolean filterEffectiveDateFrom,
      boolean filterEffectiveDateTo)
      throws SqliteNativeException {
    int bindIndex = 1;
    if (filterAccount) {
      statement.bindText(bindIndex, query.accountCode().orElseThrow().value());
      bindIndex++;
    }
    if (filterEffectiveDateFrom) {
      statement.bindText(bindIndex, query.effectiveDateFrom().orElseThrow().toString());
      bindIndex++;
    }
    if (filterEffectiveDateTo) {
      statement.bindText(bindIndex, query.effectiveDateTo().orElseThrow().toString());
      bindIndex++;
    }
    statement.bindInt(bindIndex, query.limit() + 1);
    bindIndex++;
    statement.bindInt(bindIndex, query.offset());
  }

  private static Totals totalsFor(
      Map<CurrencyCode, Totals> totalsByCurrency, CurrencyCode currencyCode) {
    return totalsByCurrency.computeIfAbsent(currencyCode, _ -> new Totals());
  }

  private static Map<CurrencyCode, Totals> mutableTotalsByCurrency() {
    return new HashMap<>();
  }

  private static void bindAccountBalanceQuery(
      SqliteNativeStatement statement,
      AccountBalanceQuery query,
      boolean filterEffectiveDateFrom,
      boolean filterEffectiveDateTo)
      throws SqliteNativeException {
    int bindIndex = 1;
    statement.bindText(bindIndex, query.accountCode().value());
    bindIndex++;
    if (filterEffectiveDateFrom) {
      statement.bindText(bindIndex, query.effectiveDateFrom().orElseThrow().toString());
      bindIndex++;
    }
    if (filterEffectiveDateTo) {
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

  private static CurrencyBalance balance(
      CurrencyCode currencyCode, Totals totals, NormalBalance accountNormalBalance) {
    BigDecimal net = totals.debit.subtract(totals.credit);
    BigDecimal absoluteNet = net.abs();
    NormalBalance balanceSide = net.signum() >= 0 ? NormalBalance.DEBIT : NormalBalance.CREDIT;
    if (absoluteNet.signum() == 0) {
      balanceSide = accountNormalBalance;
    }
    return new CurrencyBalance(
        new Money(currencyCode, totals.debit),
        new Money(currencyCode, totals.credit),
        new Money(currencyCode, absoluteNet),
        balanceSide);
  }

  /** Running debit and credit totals for one account/currency balance bucket. */
  private static final class Totals {
    private BigDecimal debit = BigDecimal.ZERO;
    private BigDecimal credit = BigDecimal.ZERO;
  }
}
