package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Focused coverage tests for single-row SQLite statement helpers. */
class SqliteStatementQueriesTest extends SqlitePostingFactStoreTestSupport {
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
}
