package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
@NullUnmarked
class SqliteQueryFailureHandlingTest extends SqlitePostingFactStoreTestSupport {

  @Test
  void queryMethods_wrapFailuresForInvalidBookFiles() throws IOException {
    Path invalidBookPath = tempDirectory.resolve("query-not-a-sqlite-file.sqlite");
    Files.writeString(invalidBookPath, "not sqlite", StandardCharsets.UTF_8);
    RegisteredAccount cashAccount =
        new RegisteredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(IllegalStateException.class, postingFactStore::inspectBook);
      assertProtectedBookVerificationFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.listPostings(
                      new ListPostingsQuery(Optional.empty(), null, null, 10, Optional.empty())));
      assertProtectedBookVerificationFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.accountBalance(
                      new AccountBalanceQuery(new AccountCode("1000"), null, null)));
      assertProtectedBookVerificationFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.trialBalance(new TrialBalanceQuery(Optional.empty())));
      assertProtectedBookVerificationFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.accountLedger(
                      new AccountLedgerQuery(new AccountCode("1000"), null, null), cashAccount));
      assertProtectedBookVerificationFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.periodSummary(
                      new PeriodSummaryQuery(
                          LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30"))));
      assertProtectedBookVerificationFailure(exception);
    }
  }

  @Test
  void queryMethods_wrapNativeFailuresAfterDatabaseOpen() throws Exception {
    Path bookPath = tempDirectory.resolve("query-stale.sqlite");
    initializeBookOnDisk(bookPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));
      RegisteredAccount cashAccount =
          new RegisteredAccount(
              new AccountCode("1000"),
              new AccountName("Cash"),
              NormalBalance.DEBIT,
              true,
              Instant.parse("2026-04-07T10:15:30Z"));

      IllegalStateException inspectFailure =
          assertThrows(IllegalStateException.class, postingFactStore::inspectBook);
      assertTrue(inspectFailure.getMessage().contains("Failed to inspect SQLite book."));

      IllegalStateException listFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.listPostings(
                      new ListPostingsQuery(Optional.empty(), null, null, 10, Optional.empty())));
      assertTrue(listFailure.getMessage().contains("Failed to query SQLite book."));

      IllegalStateException balanceFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.accountBalance(
                      new AccountBalanceQuery(new AccountCode("1000"), null, null)));
      assertTrue(balanceFailure.getMessage().contains("Failed to query SQLite book."));

      IllegalStateException trialBalanceFailure =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.trialBalance(new TrialBalanceQuery(Optional.empty())));
      assertTrue(trialBalanceFailure.getMessage().contains("Failed to query SQLite book."));

      IllegalStateException accountLedgerFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.accountLedger(
                      new AccountLedgerQuery(new AccountCode("1000"), null, null), cashAccount));
      assertTrue(accountLedgerFailure.getMessage().contains("Failed to query SQLite book."));

      IllegalStateException periodSummaryFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.periodSummary(
                      new PeriodSummaryQuery(
                          LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30"))));
      assertTrue(periodSummaryFailure.getMessage().contains("Failed to query SQLite book."));

      var readView = postingFactStore.readSession();
      {
        IllegalStateException readTrialBalanceFailure =
            assertThrows(
                IllegalStateException.class,
                () -> readView.trialBalance(new TrialBalanceQuery(Optional.empty())));
        assertTrue(readTrialBalanceFailure.getMessage().contains("Failed to query SQLite book."));

        IllegalStateException readAccountLedgerFailure =
            assertThrows(
                IllegalStateException.class,
                () ->
                    readView.accountLedger(
                        new AccountLedgerQuery(new AccountCode("1000"), null, null), cashAccount));
        assertTrue(readAccountLedgerFailure.getMessage().contains("Failed to query SQLite book."));

        IllegalStateException readPeriodSummaryFailure =
            assertThrows(
                IllegalStateException.class,
                () ->
                    readView.periodSummary(
                        new PeriodSummaryQuery(
                            LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30"))));
        assertTrue(readPeriodSummaryFailure.getMessage().contains("Failed to query SQLite book."));
        setStoreDatabase(postingFactStore, null);
      }
    }
  }

  @Test
  void findByIdempotency_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("query-native-failure.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findExistingPosting(new IdempotencyKey("idem-query")));

      assertTrue(exception.getMessage().contains("Failed to query SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void isInitialized_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("initialized-stale-handle.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

      IllegalStateException exception =
          assertThrows(IllegalStateException.class, postingFactStore::isInitialized);

      assertTrue(exception.getMessage().contains("Failed to query SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void findAccount_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("account-stale-handle.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findAccount(new AccountCode("1000")));

      assertTrue(exception.getMessage().contains("Failed to query SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void findAccount_wrapsQueryFailureFromStaleInitializedDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("account-stale-initialized-handle.sqlite");
    initializeBookOnDisk(bookPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));
      setStoreCachedBookState(
          postingFactStore,
          new SqliteBookStateSnapshot(
              SqliteBookContract.APPLICATION_ID,
              SqliteBookContract.FORMAT_VERSION,
              SqliteBookState.INITIALIZED_FINGRIND));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findAccount(new AccountCode("1000")));

      assertTrue(exception.getMessage().contains("Failed to query SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void findAccounts_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("accounts-stale-handle.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findAccounts(Set.of(new AccountCode("1000"))));

      assertTrue(exception.getMessage().contains("Failed to query SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void declareAccount_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("declare-stale-handle.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.declareAccount(
                      new AccountCode("1000"),
                      new AccountName("Cash"),
                      NormalBalance.DEBIT,
                      Instant.parse("2026-04-07T10:15:30Z")));

      assertTrue(exception.getMessage().contains("Failed to declare SQLite book account."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void listAccounts_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("list-stale-handle.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class, () -> postingFactStore.listAccounts(firstAccountPage()));

      assertTrue(exception.getMessage().contains("Failed to query SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void findByPostingId_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("posting-id-stale-handle.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findPosting(new PostingId("posting-1")));

      assertTrue(exception.getMessage().contains("Failed to query SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void findReversalFor_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("reversal-stale-handle.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findReversalFor(new PostingId("posting-1")));

      assertTrue(exception.getMessage().contains("Failed to query SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }
}
