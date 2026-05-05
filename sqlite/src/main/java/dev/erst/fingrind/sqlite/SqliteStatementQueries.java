package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.AccountPageCursor;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Shared SQLite statement helpers for single-row lookups and pragma reads. */
final class SqliteStatementQueries {
  /** Binds parameters onto a prepared SQLite statement before execution. */
  @FunctionalInterface
  interface Binder {
    /** Applies all statement bindings required by one query. */
    void bind(SqliteNativeStatement statement);
  }

  /** Loads journal lines for one posting identifier while mapping a posting row. */
  @FunctionalInterface
  interface PostingLineLoader {
    /** Returns the journal lines that belong to the supplied posting. */
    List<JournalLine> load(PostingId postingId);
  }

  /** Runs one mapped query against a prepared statement. */
  @FunctionalInterface
  private interface StatementQuery<T> {
    /** Executes one query body against the supplied prepared statement. */
    T query(SqliteNativeStatement statement);
  }

  private SqliteStatementQueries() {}

  static Optional<CommittedPosting> findOneCommittedPosting(
      SqliteNativeDatabase activeDatabase, String sql, Binder binder, PostingLineLoader loadLines) {
    return withStatement(
        activeDatabase,
        sql,
        statement -> {
          binder.bind(statement);
          if (statement.step() == SqliteNativeResultCodes.DONE) {
            return Optional.empty();
          }
          PostingId postingId =
              new PostingId(
                  SqlitePostingMapper.requiredText(statement, SqlitePostingSql.COL_POSTING_ID));
          return Optional.of(
              SqlitePostingMapper.committedPosting(statement, loadLines.load(postingId)));
        });
  }

  static Optional<RegisteredAccount> findOneAccount(
      SqliteNativeDatabase activeDatabase, AccountCode accountCode) {
    return withStatement(
        activeDatabase,
        SqlitePostingSql.FIND_ACCOUNT_BY_CODE,
        statement -> {
          statement.bindText(1, accountCode.value());
          if (statement.step() == SqliteNativeResultCodes.DONE) {
            return Optional.empty();
          }
          return Optional.of(SqlitePostingMapper.registeredAccount(statement));
        });
  }

  static Map<AccountCode, RegisteredAccount> findAccounts(
      SqliteNativeDatabase activeDatabase, Set<AccountCode> accountCodes) {
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
          List<RegisteredAccount> accounts = new ArrayList<>();
          while (statement.step() == SqliteNativeResultCodes.ROW) {
            accounts.add(SqlitePostingMapper.registeredAccount(statement));
          }
          return accounts.stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      RegisteredAccount::accountCode, Function.identity()));
        });
  }

  static AccountPage loadAccountPage(SqliteNativeDatabase activeDatabase, ListAccountsQuery query) {
    List<DeclaredAccount> accounts = new ArrayList<>();
    withStatement(
        activeDatabase,
        SqlitePostingSql.listAccounts(),
        statement -> {
          String cursorAccountCode =
              query
                  .cursor()
                  .map(AccountPageCursor::accountCode)
                  .map(AccountCode::value)
                  .orElse(null);
          statement.bindText(1, cursorAccountCode);
          statement.bindText(2, cursorAccountCode);
          statement.bindInt(3, query.limit() + 1);
          while (statement.step() == SqliteNativeResultCodes.ROW) {
            accounts.add(SqlitePostingMapper.declaredAccount(statement));
          }
          return Boolean.TRUE;
        });
    boolean hasMore = accounts.size() > query.limit();
    List<DeclaredAccount> pageItems = hasMore ? accounts.subList(0, query.limit()) : accounts;
    return new AccountPage(
        pageItems,
        query.limit(),
        hasMore
            ? Optional.of(AccountPageCursor.fromAccount(pageItems.getLast()))
            : Optional.empty());
  }

  static boolean existsRow(SqliteNativeDatabase activeDatabase, String sql, Binder binder) {
    return withStatement(
        activeDatabase,
        sql,
        statement -> {
          binder.bind(statement);
          return statement.step() == SqliteNativeResultCodes.ROW;
        });
  }

  static Optional<Instant> loadInitializedAt(SqliteNativeDatabase activeDatabase) {
    return withStatement(
        activeDatabase,
        SqlitePostingSql.FIND_BOOK_INITIALIZED_AT,
        statement -> {
          statement.bindText(1, SqlitePostingSql.INITIALIZED_AT_META_KEY);
          if (statement.step() == SqliteNativeResultCodes.DONE) {
            return Optional.empty();
          }
          return Optional.of(Instant.parse(SqlitePostingMapper.requiredText(statement, 0)));
        });
  }

  static int querySingleInt(SqliteNativeDatabase activeDatabase, String sql) {
    OptionalInt value = queryOptionalInt(activeDatabase, sql);
    if (value.isEmpty()) {
      throw new IllegalStateException("SQLite integer query returned no rows: " + sql);
    }
    return value.orElseThrow();
  }

  static OptionalInt queryOptionalInt(SqliteNativeDatabase activeDatabase, String sql) {
    return withStatement(
        activeDatabase,
        sql,
        statement -> {
          if (statement.step() != SqliteNativeResultCodes.ROW) {
            return OptionalInt.empty();
          }
          int value = statement.columnInt(0);
          if (statement.step() != SqliteNativeResultCodes.DONE) {
            throw new IllegalStateException(
                "SQLite integer query returned more than one row: " + sql);
          }
          return OptionalInt.of(value);
        });
  }

  static String querySingleText(SqliteNativeDatabase activeDatabase, String sql) {
    return withStatement(
        activeDatabase,
        sql,
        statement -> {
          if (statement.step() != SqliteNativeResultCodes.ROW) {
            throw new IllegalStateException("SQLite text query returned no rows: " + sql);
          }
          String value =
              Objects.requireNonNull(
                  statement.columnText(0), "SQLite text query returned NULL: " + sql);
          if (statement.step() != SqliteNativeResultCodes.DONE) {
            throw new IllegalStateException("SQLite text query returned more than one row: " + sql);
          }
          return value;
        });
  }

  private static <T> T withStatement(
      SqliteNativeDatabase activeDatabase, String sql, StatementQuery<T> query) {
    try (SqliteNativeStatement statement = activeDatabase.prepare(sql)) {
      return query.query(statement);
    }
  }
}
