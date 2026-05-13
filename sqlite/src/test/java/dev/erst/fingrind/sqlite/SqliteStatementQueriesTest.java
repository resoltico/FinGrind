package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** Focused coverage tests for single-row SQLite statement helpers. */
class SqliteStatementQueriesTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void integerAndExistenceHelpers_coverSingleAndOptionalRowPaths() {
    Path bookPath = tempDirectory.resolve("statement-query-integers.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          assertEquals(
              OptionalInt.empty(),
              SqliteStatementQueries.queryOptionalInt(database, "select 1 where 0"));
          assertEquals(
              OptionalInt.of(7), SqliteStatementQueries.queryOptionalInt(database, "select 7"));
          assertEquals(7, SqliteStatementQueries.querySingleInt(database, "select 7"));
          assertEquals("value", SqliteStatementQueries.querySingleText(database, "select 'value'"));
          org.junit.jupiter.api.Assertions.assertTrue(
              SqliteStatementQueries.existsRow(database, "select 1", statement -> {}));
          org.junit.jupiter.api.Assertions.assertFalse(
              SqliteStatementQueries.existsRow(database, "select 1 where 0", statement -> {}));
        });
  }

  @Test
  void loadOptionalText_returnsEmptyForMissingRowsAndRejectsMultipleRows() {
    Path bookPath = tempDirectory.resolve("load-optional-text.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          assertEquals(
              Optional.empty(),
              SqliteStatementQueries.loadOptionalText(
                  database, "select 'x' where 0", statement -> {}));

          IllegalStateException exception =
              assertThrows(
                  IllegalStateException.class,
                  () ->
                      SqliteStatementQueries.loadOptionalText(
                          database, "select 'x' union all select 'y'", statement -> {}));
          assertEquals(
              "SQLite text query returned more than one row: select 'x' union all select 'y'",
              exception.getMessage());
        });
  }

  @Test
  void loadOptionalTextRow_reportsEmptyExactAndMultiRowShapes() {
    Path bookPath = tempDirectory.resolve("load-optional-text-row.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          assertEquals(
              new SqliteStatementQueries.OptionalTextRow(Optional.empty(), true),
              SqliteStatementQueries.loadOptionalTextRow(
                  database, "select 'x' where 0", statement -> {}));
          assertEquals(
              new SqliteStatementQueries.OptionalTextRow(Optional.of("x"), true),
              SqliteStatementQueries.loadOptionalTextRow(database, "select 'x'", statement -> {}));
          assertEquals(
              new SqliteStatementQueries.OptionalTextRow(Optional.of("x"), false),
              SqliteStatementQueries.loadOptionalTextRow(
                  database, "select 'x' union all select 'y'", statement -> {}));
        });
  }
}
