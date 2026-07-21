package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
class SqliteQueryFailureHandlingTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void queryMethods_wrapFailuresForInvalidBookFiles() throws IOException {
    Path invalidBookPath = tempDirectory.resolve("query-not-a-sqlite-file.sqlite");
    Files.writeString(invalidBookPath, "not sqlite", StandardCharsets.UTF_8);
    RegisteredAccount cashAccount =
        registeredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            dev.erst.fingrind.core.AccountType.ASSET,
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(IllegalStateException.class, postingFactStore::inspectBook);
      assertProtectedBookVerificationFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.listPostings(
                      new PostingHistoryQuery(Optional.empty(), null, null, 10, Optional.empty())));
      assertProtectedBookVerificationFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.accountBalance(
                      AccountBalanceCriteria.unbounded(new AccountCode("1000"))));
      assertProtectedBookVerificationFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.trialBalance(trialBalanceCriteria(Optional.empty())));
      assertProtectedBookVerificationFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.accountLedger(
                      SqliteStoreTestIntrospectionSupport.accountLedgerCriteria(
                          new AccountCode("1000"), null, null),
                      cashAccount));
      assertProtectedBookVerificationFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.periodSummary(
                      new PeriodSummaryCriteria(
                          LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30"))));
      assertProtectedBookVerificationFailure(exception);
    }
  }

  @Test
  void queryMethods_wrapNativeFailuresAfterDatabaseOpen() throws Exception {
    Path bookPath = tempDirectory.resolve("query-stale.sqlite");
    initializeBookOnDisk(bookPath);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));
      RegisteredAccount cashAccount =
          registeredAccount(
              new AccountCode("1000"),
              new AccountName("Cash"),
              dev.erst.fingrind.core.AccountType.ASSET,
              NormalBalance.DEBIT,
              true,
              Instant.parse("2026-04-07T10:15:30Z"));
      IllegalStateException inspectFailure =
          assertThrows(IllegalStateException.class, postingFactStore::inspectBook);
      assertTrue(
          NullTestSupport.messageOf(inspectFailure).contains("Failed to inspect SQLite book."));
      IllegalStateException listFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.listPostings(
                      new PostingHistoryQuery(Optional.empty(), null, null, 10, Optional.empty())));
      assertTrue(NullTestSupport.messageOf(listFailure).contains("Failed to query SQLite book."));
      IllegalStateException balanceFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.accountBalance(
                      AccountBalanceCriteria.unbounded(new AccountCode("1000"))));
      assertTrue(
          NullTestSupport.messageOf(balanceFailure).contains("Failed to query SQLite book."));
      IllegalStateException trialBalanceFailure =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.trialBalance(trialBalanceCriteria(Optional.empty())));
      assertTrue(
          NullTestSupport.messageOf(trialBalanceFailure).contains("Failed to query SQLite book."));
      IllegalStateException accountLedgerFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.accountLedger(
                      SqliteStoreTestIntrospectionSupport.accountLedgerCriteria(
                          new AccountCode("1000"), null, null),
                      cashAccount));
      assertTrue(
          NullTestSupport.messageOf(accountLedgerFailure).contains("Failed to query SQLite book."));
      IllegalStateException periodSummaryFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.periodSummary(
                      new PeriodSummaryCriteria(
                          LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30"))));
      assertTrue(
          NullTestSupport.messageOf(periodSummaryFailure).contains("Failed to query SQLite book."));
      IllegalStateException readTrialBalanceFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  readView(postingFactStore).trialBalance(trialBalanceCriteria(Optional.empty())));
      assertTrue(
          NullTestSupport.messageOf(readTrialBalanceFailure)
              .contains("Failed to query SQLite book."));
      IllegalStateException readAccountLedgerFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  readView(postingFactStore)
                      .accountLedger(
                          SqliteStoreTestIntrospectionSupport.accountLedgerCriteria(
                              new AccountCode("1000"), null, null),
                          cashAccount));
      assertTrue(
          NullTestSupport.messageOf(readAccountLedgerFailure)
              .contains("Failed to query SQLite book."));
      IllegalStateException readPeriodSummaryFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  readView(postingFactStore)
                      .periodSummary(
                          new PeriodSummaryCriteria(
                              LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30"))));
      assertTrue(
          NullTestSupport.messageOf(readPeriodSummaryFailure)
              .contains("Failed to query SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void findByIdempotency_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("query-native-failure.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findExistingPosting(new IdempotencyKey("idem-query")));
      assertTrue(NullTestSupport.messageOf(exception).contains("Failed to query SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void inspectBook_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("initialized-stale-handle.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));
      IllegalStateException exception =
          assertThrows(IllegalStateException.class, postingFactStore::inspectBook);
      assertTrue(NullTestSupport.messageOf(exception).contains("Failed to inspect SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void findAccount_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("account-stale-handle.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findAccount(new AccountCode("1000")));
      assertTrue(NullTestSupport.messageOf(exception).contains("Failed to query SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void findAccount_wrapsQueryFailureFromStaleInitializedDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("account-stale-initialized-handle.sqlite");
    initializeBookOnDisk(bookPath);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
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
      assertTrue(NullTestSupport.messageOf(exception).contains("Failed to query SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void findAccounts_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("accounts-stale-handle.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findAccounts(Set.of(new AccountCode("1000"))));
      assertTrue(NullTestSupport.messageOf(exception).contains("Failed to query SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void declareAccount_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("declare-stale-handle.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  declareAccount(
                      postingFactStore,
                      new AccountCode("1000"),
                      new AccountName("Cash"),
                      dev.erst.fingrind.core.AccountType.ASSET,
                      NormalBalance.DEBIT,
                      Instant.parse("2026-04-07T10:15:30Z")));
      assertTrue(
          NullTestSupport.messageOf(exception).contains("Failed to declare SQLite book account."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void listAccounts_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("list-stale-handle.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class, () -> postingFactStore.listAccounts(firstAccountPage()));
      assertTrue(NullTestSupport.messageOf(exception).contains("Failed to query SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void findByPostingId_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("posting-id-stale-handle.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.findPosting(
                      new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")));
      assertTrue(NullTestSupport.messageOf(exception).contains("Failed to query SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void findReversalFor_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("reversal-stale-handle.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.findReversalFor(
                      new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")));
      assertTrue(NullTestSupport.messageOf(exception).contains("Failed to query SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  private static BookkeepingReadStore readView(SqlitePostingFactStore postingFactStore) {
    return SqliteCapabilitySessions.read(postingFactStore);
  }
}
