package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
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
