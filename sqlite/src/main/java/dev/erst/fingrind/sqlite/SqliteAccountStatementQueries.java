package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryCursor;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Shared SQLite statement helpers for account catalog lookups and pages. */
final class SqliteAccountStatementQueries {
  /** Runs one mapped account query against a prepared statement. */
  @FunctionalInterface
  private interface StatementQuery<T> {
    /** Executes one account query body against the supplied prepared statement. */
    T query(SqliteNativeStatement statement);
  }

  private SqliteAccountStatementQueries() {}

  static Optional<RegisteredAccount> findOneAccount(
      SqliteNativeDatabase activeDatabase, AccountCode accountCode) {
    Objects.requireNonNull(accountCode, "accountCode");
    return withStatement(
        activeDatabase,
        SqlitePostingReadWriteSql.FIND_ACCOUNT_BY_CODE,
        statement -> {
          statement.bindText(1, accountCode.value());
          if (statement.step() == SqliteNativeResultCode.code("DONE")) {
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
        SqlitePostingQuerySql.findAccountsByCodeCount(orderedCodes.size()),
        statement -> {
          int bindIndex = 1;
          for (AccountCode accountCode : orderedCodes) {
            statement.bindText(bindIndex, accountCode.value());
            bindIndex++;
          }
          List<RegisteredAccount> accounts = new ArrayList<>();
          while (statement.step() == SqliteNativeResultCode.code("ROW")) {
            accounts.add(SqlitePostingMapper.registeredAccount(statement));
          }
          return accounts.stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      RegisteredAccount::accountCode, Function.identity()));
        });
  }

  static List<RegisteredAccount> loadAllAccounts(SqliteNativeDatabase activeDatabase, String sql) {
    return withStatement(
        activeDatabase,
        sql,
        statement -> {
          List<RegisteredAccount> accounts = new ArrayList<>();
          while (statement.step() == SqliteNativeResultCode.code("ROW")) {
            accounts.add(SqlitePostingMapper.registeredAccount(statement));
          }
          return List.copyOf(accounts);
        });
  }

  static AccountRegistryPage loadAccountPage(
      SqliteNativeDatabase activeDatabase, AccountRegistryQuery query) {
    Objects.requireNonNull(query, "query");
    List<RegisteredAccount> accounts = new ArrayList<>();
    withStatement(
        activeDatabase,
        SqlitePostingQuerySql.listAccounts(),
        statement -> {
          String cursorAccountCode =
              query
                  .cursor()
                  .map(AccountRegistryCursor::accountCode)
                  .map(AccountCode::value)
                  .orElse(null);
          statement.bindText(1, cursorAccountCode);
          statement.bindText(2, cursorAccountCode);
          statement.bindInt(3, query.limit() + 1);
          while (statement.step() == SqliteNativeResultCode.code("ROW")) {
            accounts.add(SqlitePostingMapper.registeredAccount(statement));
          }
          return Boolean.TRUE;
        });
    boolean hasMore = accounts.size() > query.limit();
    List<RegisteredAccount> pageItems = hasMore ? accounts.subList(0, query.limit()) : accounts;
    return new AccountRegistryPage(
        pageItems,
        query.limit(),
        hasMore
            ? Optional.of(new AccountRegistryCursor(pageItems.getLast().accountCode()))
            : Optional.empty());
  }

  private static <T> T withStatement(
      SqliteNativeDatabase activeDatabase, String sql, StatementQuery<T> query) {
    try (SqliteNativeStatement statement = activeDatabase.prepare(sql)) {
      return query.query(statement);
    }
  }
}
