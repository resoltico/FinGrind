package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/** Shared SQLite statement helpers for single-row lookups and pragma reads. */
final class SqliteStatementQuerySupport {
  /** Binds parameters onto a prepared SQLite statement before execution. */
  @FunctionalInterface
  interface Binder {
    /** Applies all statement bindings required by one query. */
    void bind(SqliteNativeStatement statement) throws SqliteNativeException;
  }

  /** Loads journal lines for one posting identifier while mapping a posting row. */
  @FunctionalInterface
  interface PostingLineLoader {
    /** Returns the journal lines that belong to the supplied posting. */
    List<JournalLine> load(PostingId postingId) throws SqliteNativeException;
  }

  /** Runs one mapped query against a prepared statement. */
  @FunctionalInterface
  private interface StatementQuery<T> {
    /** Executes one query body against the supplied prepared statement. */
    T query(SqliteNativeStatement statement) throws SqliteNativeException;
  }

  private SqliteStatementQuerySupport() {}

  static Optional<PostingFact> findOnePosting(
      SqliteNativeDatabase activeDatabase, String sql, Binder binder, PostingLineLoader loadLines)
      throws SqliteNativeException {
    return withStatement(
        activeDatabase,
        sql,
        statement -> {
          binder.bind(statement);
          if (statement.step() == SqliteNativeLibrary.SQLITE_DONE) {
            return Optional.empty();
          }
          PostingId postingId =
              new PostingId(
                  SqlitePostingMapper.requiredText(statement, SqlitePostingSql.COL_POSTING_ID));
          return Optional.of(SqlitePostingMapper.postingFact(statement, loadLines.load(postingId)));
        });
  }

  static Optional<DeclaredAccount> findOneAccount(
      SqliteNativeDatabase activeDatabase, AccountCode accountCode) throws SqliteNativeException {
    return withStatement(
        activeDatabase,
        SqlitePostingSql.FIND_ACCOUNT_BY_CODE,
        statement -> {
          statement.bindText(1, accountCode.value());
          if (statement.step() == SqliteNativeLibrary.SQLITE_DONE) {
            return Optional.empty();
          }
          return Optional.of(SqlitePostingMapper.declaredAccount(statement));
        });
  }

  @SuppressWarnings("PMD.UseConcurrentHashMap")
  static Map<AccountCode, DeclaredAccount> findAccounts(
      SqliteNativeDatabase activeDatabase, Set<AccountCode> accountCodes)
      throws SqliteNativeException {
    List<AccountCode> orderedCodes = List.copyOf(accountCodes);
    return withStatement(
        activeDatabase,
        SqlitePostingSql.findAccountsByCodeCount(orderedCodes.size()),
        statement -> {
          int bindIndex = 1;
          for (AccountCode accountCode : orderedCodes) {
            statement.bindText(bindIndex, accountCode.value());
            bindIndex++;
          }
          Map<AccountCode, DeclaredAccount> accounts = new LinkedHashMap<>();
          while (statement.step() == SqliteNativeLibrary.SQLITE_ROW) {
            DeclaredAccount account = SqlitePostingMapper.declaredAccount(statement);
            accounts.put(account.accountCode(), account);
          }
          return Map.copyOf(accounts);
        });
  }

  static AccountPage loadAccountPage(SqliteNativeDatabase activeDatabase, ListAccountsQuery query)
      throws SqliteNativeException {
    List<DeclaredAccount> accounts = new ArrayList<>();
    withStatement(
        activeDatabase,
        SqlitePostingSql.listAccounts(),
        statement -> {
          statement.bindInt(1, query.limit() + 1);
          statement.bindInt(2, query.offset());
          while (statement.step() == SqliteNativeLibrary.SQLITE_ROW) {
            accounts.add(SqlitePostingMapper.declaredAccount(statement));
          }
          return Boolean.TRUE;
        });
    boolean hasMore = accounts.size() > query.limit();
    List<DeclaredAccount> pageItems = hasMore ? accounts.subList(0, query.limit()) : accounts;
    return new AccountPage(pageItems, query.limit(), query.offset(), hasMore);
  }

  static boolean existsRow(SqliteNativeDatabase activeDatabase, String sql, Binder binder)
      throws SqliteNativeException {
    return withStatement(
        activeDatabase,
        sql,
        statement -> {
          binder.bind(statement);
          return statement.step() == SqliteNativeLibrary.SQLITE_ROW;
        });
  }

  static Optional<Instant> loadInitializedAt(SqliteNativeDatabase activeDatabase)
      throws SqliteNativeException {
    return withStatement(
        activeDatabase,
        SqlitePostingSql.FIND_BOOK_INITIALIZED_AT,
        statement -> {
          statement.bindText(1, SqlitePostingSql.INITIALIZED_AT_META_KEY);
          if (statement.step() == SqliteNativeLibrary.SQLITE_DONE) {
            return Optional.empty();
          }
          return Optional.of(Instant.parse(SqlitePostingMapper.requiredText(statement, 0)));
        });
  }

  static int querySingleInt(SqliteNativeDatabase activeDatabase, String sql)
      throws SqliteNativeException {
    OptionalInt value = queryOptionalInt(activeDatabase, sql);
    if (value.isEmpty()) {
      throw new IllegalStateException("SQLite integer query returned no rows: " + sql);
    }
    return value.orElseThrow();
  }

  static OptionalInt queryOptionalInt(SqliteNativeDatabase activeDatabase, String sql)
      throws SqliteNativeException {
    return withStatement(
        activeDatabase,
        sql,
        statement -> {
          if (statement.step() != SqliteNativeLibrary.SQLITE_ROW) {
            return OptionalInt.empty();
          }
          int value = statement.columnInt(0);
          if (statement.step() != SqliteNativeLibrary.SQLITE_DONE) {
            throw new IllegalStateException(
                "SQLite integer query returned more than one row: " + sql);
          }
          return OptionalInt.of(value);
        });
  }

  static String querySingleText(SqliteNativeDatabase activeDatabase, String sql)
      throws SqliteNativeException {
    return withStatement(
        activeDatabase,
        sql,
        statement -> {
          if (statement.step() != SqliteNativeLibrary.SQLITE_ROW) {
            throw new IllegalStateException("SQLite text query returned no rows: " + sql);
          }
          String value =
              Objects.requireNonNull(
                  statement.columnText(0), "SQLite text query returned NULL: " + sql);
          if (statement.step() != SqliteNativeLibrary.SQLITE_DONE) {
            throw new IllegalStateException("SQLite text query returned more than one row: " + sql);
          }
          return value;
        });
  }

  @SuppressWarnings("PMD.UseTryWithResources")
  private static <T> T withStatement(
      SqliteNativeDatabase activeDatabase, String sql, StatementQuery<T> query)
      throws SqliteNativeException {
    SqliteNativeStatement statement = activeDatabase.prepare(sql);
    try {
      return query.query(statement);
    } finally {
      statement.close();
    }
  }
}
