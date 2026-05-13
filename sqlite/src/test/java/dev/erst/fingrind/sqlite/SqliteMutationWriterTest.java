package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for SQLite write-side staging invariants. */
class SqliteMutationWriterTest extends SqlitePostingFactStoreTestSupport {
  private static final MethodHandle REQUIRE_BALANCED_PENDING_JOURNAL_LINE_TABLE =
      mutationWriterHelper("requireBalancedPendingJournalLineTable");

  @Test
  void balancedPendingJournalLineTable_rejectsUnbalancedOrMalformedRows() {
    Path bookPath = tempDirectory.resolve("pending-journal-line.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          database.executeStatement(SqlitePostingSql.CREATE_PENDING_JOURNAL_LINE);
          database.executeStatement(
              """
              insert into pending_journal_line (
                  line_order,
                  account_code,
                  entry_side,
                  currency_code,
                  amount_minor
              ) values (
                  0,
                  '1000',
                  'DEBIT',
                  'EUR',
                  1000
              )
              """);

          IllegalStateException exception =
              assertThrows(
                  IllegalStateException.class,
                  () -> requireBalancedPendingJournalLineTable(database));
          assertEquals(
              "SQLite journal-line staging rejected one unbalanced or malformed posting.",
              exception.getMessage());
        });
  }

  @Test
  void insertPeriodClose_requiresExactlyOneReturnedCloseOrderRow() {
    Path bookPath = tempDirectory.resolve("period-close-return-shapes.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          SqliteStatementRedirectingDatabase noRowDatabase =
              new SqliteStatementRedirectingDatabase(
                  database,
                  sql ->
                      database.prepare(
                          SqlitePostingSql.INSERT_PERIOD_CLOSE.equals(sql)
                              ? "select ?1 as close_order where 0 and ?2 is not null and ?3 is not null"
                              : sql));
          IllegalStateException noRowFailure =
              assertThrows(
                  IllegalStateException.class,
                  () ->
                      SqliteMutationWriter.insertPeriodClose(
                          noRowDatabase,
                          new dev.erst.fingrind.core.ReportingPeriod(
                              LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
                          new AccountCode("3200"),
                          List.of(),
                          Instant.parse("2026-04-30T10:15:30Z"),
                          List.of()));
          assertEquals(
              "SQLite period close insert returned no close order.", noRowFailure.getMessage());

          SqliteStatementRedirectingDatabase extraRowDatabase =
              new SqliteStatementRedirectingDatabase(
                  database,
                  sql ->
                      database.prepare(
                          SqlitePostingSql.INSERT_PERIOD_CLOSE.equals(sql)
                              ? "select ?1 as close_order union all select ?2 where ?3 is not null"
                              : sql));
          IllegalStateException extraRowFailure =
              assertThrows(
                  IllegalStateException.class,
                  () ->
                      SqliteMutationWriter.insertPeriodClose(
                          extraRowDatabase,
                          new dev.erst.fingrind.core.ReportingPeriod(
                              LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
                          new AccountCode("3200"),
                          List.of(),
                          Instant.parse("2026-04-30T10:15:30Z"),
                          List.of()));
          assertEquals(
              "SQLite period close insert returned more than one close order.",
              extraRowFailure.getMessage());
        });
  }

  private static void requireBalancedPendingJournalLineTable(SqliteNativeDatabase activeDatabase) {
    try {
      REQUIRE_BALANCED_PENDING_JOURNAL_LINE_TABLE.invokeExact(activeDatabase);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError(
          "Failed to invoke SQLite pending-journal-line validation helper.", throwable);
    }
  }

  private static MethodHandle mutationWriterHelper(String methodName) {
    try {
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(SqliteMutationWriter.class, MethodHandles.lookup());
      return lookup.findStatic(
          SqliteMutationWriter.class,
          methodName,
          MethodType.methodType(void.class, SqliteNativeDatabase.class));
    } catch (IllegalAccessException | NoSuchMethodException exception) {
      throw new LinkageError(
          "Failed to bind SQLite mutation-writer helper: " + methodName, exception);
    }
  }
}
