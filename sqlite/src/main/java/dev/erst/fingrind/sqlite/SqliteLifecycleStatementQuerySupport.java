package dev.erst.fingrind.sqlite;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Shared one-row, collection, and existence mechanics for durable lifecycle aggregate queries. */
final class SqliteLifecycleStatementQuerySupport {
  private SqliteLifecycleStatementQuerySupport() {}

  static <T> Optional<T> findOne(
      SqliteNativeDatabase database,
      String sql,
      String identifier,
      SqliteStatementBinder binder,
      Function<SqliteNativeStatement, T> rowMapper,
      String aggregateName) {
    return SqliteStatementQueries.queryWithStatement(
        database,
        sql,
        statement -> {
          binder.bind(statement);
          if (statement.step() != SqliteNativeResultCode.code("ROW")) {
            return Optional.empty();
          }
          T record = rowMapper.apply(statement);
          if (statement.step() != SqliteNativeResultCode.code("DONE")) {
            throw new IllegalStateException(
                "SQLite "
                    + aggregateName
                    + " query returned multiple rows for "
                    + identifier
                    + ".");
          }
          return Optional.of(record);
        });
  }

  static <T> List<T> loadAll(
      SqliteNativeDatabase database, String sql, Function<SqliteNativeStatement, T> rowMapper) {
    return SqliteStatementQueries.queryWithStatement(
        database,
        sql,
        statement -> {
          var records = new java.util.ArrayList<T>();
          while (statement.step() == SqliteNativeResultCode.code("ROW")) {
            records.add(rowMapper.apply(statement));
          }
          return List.copyOf(records);
        });
  }

  static boolean exists(
      SqliteNativeDatabase database, String sql, String identifier, SqliteStatementBinder binder) {
    return SqliteStatementQueries.queryWithStatement(
        database,
        sql,
        statement -> {
          binder.bind(statement);
          return statement.step() == SqliteNativeResultCode.code("ROW");
        });
  }
}
