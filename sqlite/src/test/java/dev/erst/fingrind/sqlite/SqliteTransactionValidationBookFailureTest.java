package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Verifies that every transaction-validation read translates a stale SQLite handle consistently.
 */
class SqliteTransactionValidationBookFailureTest extends SqlitePostingFactStoreTestSupport {

  @Test
  void staleDatabaseReads_areTranslatedIntoTheValidationBoundaryFailure() throws Exception {
    Path bookPath = tempDirectory.resolve("validation-stale.sqlite");
    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath))) {
      SqliteTransactionValidationBook validationBook =
          new SqliteTransactionValidationBook(staleDatabaseHandle(bookPath), store.postingReader());

      assertQueryFailure(validationBook::inspectBook);
      assertQueryFailure(() -> validationBook.findAccount(new AccountCode("1000")));
      assertAccrualCutoffQueryFailure(
          () -> validationBook.findAccrualCutoff(new AccrualCutoffId("cutoff-1")));
      assertQueryFailure(
          () ->
              validationBook.findAccounts(
                  Set.of(new AccountCode("1000"), new AccountCode("2000"))));
      assertQueryFailure(() -> validationBook.findExistingPosting(new IdempotencyKey("idem-1")));
      assertQueryFailure(() -> validationBook.postings(EffectiveDateRange.unbounded()));
      assertQueryFailure(validationBook::earliestPostingEffectiveDate);
      assertQueryFailure(validationBook::transferredThroughEffectiveDate);
    }
  }

  private static void assertQueryFailure(org.junit.jupiter.api.function.Executable query) {
    IllegalStateException exception = assertThrows(IllegalStateException.class, query);
    assertTrue(NullTestSupport.messageOf(exception).contains("Failed to query SQLite book."));
  }

  private static void assertAccrualCutoffQueryFailure(
      org.junit.jupiter.api.function.Executable query) {
    IllegalStateException exception = assertThrows(IllegalStateException.class, query);
    assertTrue(
        NullTestSupport.messageOf(exception).contains("Failed to query SQLite accrual cut-off."));
  }
}
