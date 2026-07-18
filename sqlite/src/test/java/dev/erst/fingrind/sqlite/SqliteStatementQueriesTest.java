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
              new SqliteOptionalTextRow(Optional.empty(), true),
              SqliteStatementQueries.loadOptionalTextRow(
                  database, "select 'x' where 0", statement -> {}));
          assertEquals(
              new SqliteOptionalTextRow(Optional.of("x"), true),
              SqliteStatementQueries.loadOptionalTextRow(database, "select 'x'", statement -> {}));
          assertEquals(
              new SqliteOptionalTextRow(Optional.of("x"), false),
              SqliteStatementQueries.loadOptionalTextRow(
                  database, "select 'x' union all select 'y'", statement -> {}));
        });
  }

  @Test
  void loadBookIdentity_rejectsDuplicateSingletonRows() {
    Path bookPath = tempDirectory.resolve("load-book-identity-duplicates.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          SqliteStoreFixtureSupport.insertCanonicalInitializedBookMetadata(database);

          IllegalStateException identityCoreFailure =
              assertThrows(
                  IllegalStateException.class,
                  () ->
                      SqliteStatementQueries.loadBookIdentity(
                          redirectedDatabase(
                              database,
                              SqlitePostingSql.FIND_BOOK_IDENTITY_CORE,
                              """
                              select
                                  entity_name,
                                  accounting_kernel_profile,
                                  accounting_basis,
                                  accounting_framework_position,
                                  entity_form,
                                  book_template_id,
                                  costing_doctrine,
                                  functional_currency_code,
                                  fiscal_year_start_month,
                                  fiscal_year_start_day,
                                  book_start_effective_date
                              from book_identity
                              where singleton_id = 1
                              union all
                              select
                                  entity_name,
                                  accounting_kernel_profile,
                                  accounting_basis,
                                  accounting_framework_position,
                                  entity_form,
                                  book_template_id,
                                  costing_doctrine,
                                  functional_currency_code,
                                  fiscal_year_start_month,
                                  fiscal_year_start_day,
                                  book_start_effective_date
                              from book_identity
                              where singleton_id = 1
                              """)));
          assertEquals(
              "SQLite book identity core query returned more than one row.",
              identityCoreFailure.getMessage());
        });
  }

  @Test
  void loadBookIdentity_rejectsMismatchedPersistedCostingDoctrine() {
    Path bookPath = tempDirectory.resolve("load-book-identity-costing-mismatch.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          SqliteStoreFixtureSupport.insertCanonicalInitializedBookMetadata(database);

          IllegalStateException mismatch =
              assertThrows(
                  IllegalStateException.class,
                  () ->
                      SqliteStatementQueries.loadBookIdentity(
                          redirectedDatabase(
                              database,
                              SqlitePostingSql.FIND_BOOK_IDENTITY_CORE,
                              """
                              select
                                  entity_name,
                                  accounting_kernel_profile,
                                  accounting_basis,
                                  accounting_framework_position,
                                  entity_form,
                                  book_template_id,
                                  'WEIGHTED_AVERAGE' as costing_doctrine,
                                  functional_currency_code,
                                  fiscal_year_start_month,
                                  fiscal_year_start_day,
                                  book_start_effective_date
                              from book_identity
                              where singleton_id = 1
                              limit 1
                              """)));
          assertEquals(
              "Persisted SQLite book identity carries an inventory costing doctrine that does not match the selected book template.",
              mismatch.getMessage());
        });
  }

  private static SqliteStatementRedirectingDatabase redirectedDatabase(
      SqliteNativeDatabase database, String targetSql, String replacementSql) {
    return new SqliteStatementRedirectingDatabase(
        database, sql -> database.prepare(targetSql.equals(sql) ? replacementSql : sql));
  }
}
