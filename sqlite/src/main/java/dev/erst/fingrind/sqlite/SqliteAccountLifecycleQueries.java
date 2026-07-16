package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRegistryDependency;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Reads the durable Account Registry facts needed to admit lifecycle transitions. */
final class SqliteAccountLifecycleQueries {
  private SqliteAccountLifecycleQueries() {}

  static List<AccountRegistryDependency> amendmentDependencies(
      SqliteNativeDatabase activeDatabase, AccountCode accountCode) {
    return dependencies(activeDatabase, accountCode, true);
  }

  static List<AccountRegistryDependency> retirementDependencies(
      SqliteNativeDatabase activeDatabase, AccountCode accountCode) {
    return dependencies(activeDatabase, accountCode, false);
  }

  static boolean currentBalanceZero(SqliteNativeDatabase activeDatabase, AccountCode accountCode) {
    return !exists(
        activeDatabase, SqliteAccountLifecycleSql.ACCOUNT_HAS_NON_ZERO_BALANCE, accountCode);
  }

  private static List<AccountRegistryDependency> dependencies(
      SqliteNativeDatabase activeDatabase, AccountCode accountCode, boolean includePostings) {
    Objects.requireNonNull(activeDatabase, "activeDatabase");
    Objects.requireNonNull(accountCode, "accountCode");
    List<AccountRegistryDependency> dependencies = new ArrayList<>();
    if (includePostings
        && exists(activeDatabase, SqliteAccountLifecycleSql.ACCOUNT_HAS_POSTINGS, accountCode)) {
      dependencies.add(AccountRegistryDependency.POSTINGS);
    }
    if (existsTaxRegistration(activeDatabase, accountCode)) {
      dependencies.add(AccountRegistryDependency.TAX_REGISTRATIONS);
    }
    if (exists(activeDatabase, SqliteAccountLifecycleSql.ACCOUNT_HAS_CHILDREN, accountCode)) {
      dependencies.add(AccountRegistryDependency.CHILD_ACCOUNTS);
    }
    return List.copyOf(dependencies);
  }

  private static boolean existsTaxRegistration(
      SqliteNativeDatabase activeDatabase, AccountCode accountCode) {
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqliteAccountLifecycleSql.ACCOUNT_HAS_TAX_REGISTRATIONS)) {
      statement.bindText(1, accountCode.value());
      statement.bindText(2, accountCode.value());
      return statement.step() == SqliteNativeResultCode.code("ROW");
    }
  }

  private static boolean exists(
      SqliteNativeDatabase activeDatabase, String sql, AccountCode accountCode) {
    try (SqliteNativeStatement statement = activeDatabase.prepare(sql)) {
      statement.bindText(1, accountCode.value());
      return statement.step() == SqliteNativeResultCode.code("ROW");
    }
  }
}
