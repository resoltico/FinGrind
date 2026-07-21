package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Verifies that every transaction-validation read translates a stale SQLite handle consistently.
 */
class SqliteTransactionValidationBookFailureTest extends SqlitePostingFactStoreTestSupport {
  private static final String BOOK_QUERY_FAILURE = "Failed to query SQLite book.";
  private static final String ACCRUAL_CUTOFF_QUERY_FAILURE =
      "Failed to query SQLite accrual cut-off.";

  @Test
  void staleDatabaseReads_areTranslatedIntoTheValidationBoundaryFailure() throws Exception {
    Path bookPath = tempDirectory.resolve("validation-stale.sqlite");
    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath))) {
      SqliteTransactionValidationBook validationBook =
          new SqliteTransactionValidationBook(staleDatabaseHandle(bookPath), store.postingReader());
      SqliteTransactionValidationPostingCapabilityView postingCapability = validationBook;

      assertQueryFailure(validationBook::inspectBook);
      assertQueryFailure(() -> validationBook.findAccount(new AccountCode("1000")));
      assertAccrualCutoffQueryFailure(
          () -> validationBook.findAccrualCutoff(new AccrualCutoffId("cutoff-1")));
      assertQueryFailure(
          () ->
              validationBook.findAccounts(
                  Set.of(new AccountCode("1000"), new AccountCode("2000"))));
      assertQueryFailure(() -> validationBook.findTaxRegistration(new TaxRegistrationId("vat-lv")));
      assertQueryFailure(
          () -> postingCapability.findTaxRegistration(new TaxRegistrationId("vat-lv")));
      assertQueryFailure(() -> validationBook.findExistingPosting(new IdempotencyKey("idem-1")));
      assertQueryFailure(
          () -> validationBook.findPosting(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")));
      assertQueryFailure(
          () ->
              validationBook.findReversalFor(
                  new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")));
      assertQueryFailure(() -> validationBook.postings(EffectiveDateRange.unbounded()));
      assertQueryFailure(validationBook::earliestPostingEffectiveDate);
      assertQueryFailure(validationBook::transferredThroughEffectiveDate);
    }
  }

  @Test
  void blankDatabaseIsNotAnInitializedValidationWorkflow() {
    Path bookPath = tempDirectory.resolve("validation-blank.sqlite");
    createEmptySqliteFile(bookPath);
    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath))) {
      assertFalse(
          new SqliteTransactionValidationBook(
                  store.storeLifecycle().database(), store.postingReader())
              .allowsInitializedWorkflow());
    }
  }

  private static void assertQueryFailure(org.junit.jupiter.api.function.Executable query) {
    IllegalStateException exception = assertThrows(IllegalStateException.class, query);
    assertTrue(NullTestSupport.messageOf(exception).contains(BOOK_QUERY_FAILURE));
  }

  private static void assertAccrualCutoffQueryFailure(
      org.junit.jupiter.api.function.Executable query) {
    IllegalStateException exception = assertThrows(IllegalStateException.class, query);
    assertTrue(NullTestSupport.messageOf(exception).contains(ACCRUAL_CUTOFF_QUERY_FAILURE));
  }
}
