package dev.erst.fingrind.sqlite;

import java.util.Objects;

/** Internal SQL observation primitives shared by Jazzer SQLite assertions. */
final class SqliteFuzzQueryAssertions {
  private SqliteFuzzQueryAssertions() {}

  static void assertQueryInt(SqliteNativeDatabase database, String sql, int expectedValue) {
    try (SqliteNativeStatement statement = SqliteNativeStatements.prepare(database, sql)) {
      if (statement.step() != SqliteNativeResultCode.code("ROW")) {
        throw new IllegalStateException("Expected one SQLite row for hardening assertion: " + sql);
      }
      int actualValue = statement.columnInt(0);
      if (statement.step() != SqliteNativeResultCode.code("DONE")) {
        throw new IllegalStateException(
            "Expected one SQLite row only for hardening assertion: " + sql);
      }
      if (actualValue != expectedValue) {
        throw new IllegalStateException(
            "Unexpected SQLite pragma/query value for '" + sql + "': " + actualValue);
      }
    }
  }

  static void assertQueryText(SqliteNativeDatabase database, String sql, String expectedValue) {
    try (SqliteNativeStatement statement = SqliteNativeStatements.prepare(database, sql)) {
      if (statement.step() != SqliteNativeResultCode.code("ROW")) {
        throw new IllegalStateException("Expected one SQLite row for hardening assertion: " + sql);
      }
      String actualValue = statement.columnText(0);
      if (statement.step() != SqliteNativeResultCode.code("DONE")) {
        throw new IllegalStateException(
            "Expected one SQLite row only for hardening assertion: " + sql);
      }
      if (!expectedValue.equalsIgnoreCase(actualValue)) {
        throw new IllegalStateException(
            "Unexpected SQLite pragma/query value for '" + sql + "': " + actualValue);
      }
    }
  }

  static String escapeSqlLiteral(String text) {
    return text.replace("'", "''");
  }

  static SqlitePostingFactStore requireOwnedStore(AutoCloseable session) {
    Objects.requireNonNull(session, "session");
    if (session instanceof SqlitePostingFactStore store) {
      return store;
    }
    if (session instanceof SqliteDelegatingSession delegatingSession) {
      return delegatingSession.store;
    }
    throw new IllegalArgumentException(
        "Unsupported owned SQLite store or capability wrapper: " + session.getClass().getName());
  }
}
