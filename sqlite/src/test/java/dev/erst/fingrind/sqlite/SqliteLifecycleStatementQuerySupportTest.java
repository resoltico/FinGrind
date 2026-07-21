package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers collection lifecycle queries for both empty and populated result sets. */
class SqliteLifecycleStatementQuerySupportTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void loadAll_returnsAnImmutableEmptyOrPopulatedCollection() {
    Path databasePath = tempDirectory.resolve("lifecycle-query-support.sqlite");
    withStandaloneDatabase(
        bookAccess(databasePath),
        database -> {
          database.executeStatement("create table coverage_row (value text not null)");

          assertEquals(
              List.of(),
              SqliteLifecycleStatementQuerySupport.loadAll(
                  database,
                  "select value from coverage_row order by value",
                  statement -> SqlitePostingMapper.requiredText(statement, 0)));

          database.executeStatement(
              "insert into coverage_row (value) values ('first'), ('second')");
          assertEquals(
              List.of("first", "second"),
              SqliteLifecycleStatementQuerySupport.loadAll(
                  database,
                  "select value from coverage_row order by value",
                  statement -> SqlitePostingMapper.requiredText(statement, 0)));
        });
  }
}
