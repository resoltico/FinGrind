package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ComparativeSelection;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.BookReadService;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;

/** Deterministic same-book contention coverage for the public SQLite session surface. */
class SqliteBookSessionContentionTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void secondWriterHonorsBusyTimeoutWhenFirstWriterHoldsImmediateTransaction() {
    Path bookPath = tempDirectory.resolve("contention-book.sqlite");
    try (SqliteNativeDatabase firstWriter = openNativeDatabase(bookAccess(bookPath));
        SqliteNativeDatabase secondWriter = openNativeDatabase(bookAccess(bookPath))) {
      firstWriter.executeStatement("begin immediate");
      setBusyTimeout(secondWriter, 100);
      long startNanos = System.nanoTime();
      SqliteNativeException exception =
          assertThrows(
              SqliteNativeException.class, () -> secondWriter.executeStatement("begin immediate"));
      long elapsedMillis = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
      assertTrue(
          Set.of(
                  SqliteNativeResultCode.code("BUSY"),
                  SqliteNativeResultCode.code("BUSY_TIMEOUT"),
                  SqliteNativeResultCode.code("LOCKED"))
              .contains(exception.resultCode()),
          () -> "Unexpected SQLite contention code: " + exception.resultName());
      assertTrue(
          elapsedMillis >= 50 && elapsedMillis < 2_500,
          () -> "Expected a shortened busy-timeout failure, but elapsedMillis=" + elapsedMillis);
      assertTrue(
          exception.resultName().startsWith("SQLITE_BUSY")
              || exception.resultName().startsWith("SQLITE_LOCKED"),
          () -> "Unexpected SQLite contention name: " + exception.resultName());
      firstWriter.executeStatement("rollback");
    }
  }

  private static void setBusyTimeout(SqliteNativeDatabase database, int timeoutMillis) {
    database.configuration().configureBusyTimeout(timeoutMillis);
  }

  @Test
  void concurrentReadQueriesShareOneInitializedBookWithoutSurfacingTransientLockFailures() {
    Path bookPath = tempDirectory.resolve("concurrent-readers.sqlite");
    BookAccess access = bookAccess(bookPath);
    try (SqlitePostingFactStore postingFactStore = openStore(access)) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      commitPosting(
          postingFactStore, postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty()));
    }
    for (int round = 0; round < 10; round++) {
      int roundNumber = round + 1;
      int completedReaders =
          assertDoesNotThrow(
              () -> runConcurrentReadRound(access, 24, this::broadConcurrentReadAssertion),
              () ->
                  "Concurrent read round " + roundNumber + " surfaced one transient lock failure.");
      assertEquals(24, completedReaders);
    }
  }

  @Test
  void concurrentHotPathReadQueriesRemainAvailableAfterBookRekeyRotation() throws Exception {
    Path bookPath = tempDirectory.resolve("concurrent-hot-path-rekeyed.sqlite");
    BookAccess originalAccess = bookAccess(bookPath);
    String replacementKeyText = "rotated-concurrent-read-key";
    try (SqlitePostingFactStore postingFactStore = openStore(originalAccess)) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      commitPosting(
          postingFactStore, postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty()));
      try (SqliteBookPassphrase replacementPassphrase =
          SqliteBookPassphrase.fromCharacters(
              "concurrent hot-path replacement key", replacementKeyText.toCharArray())) {
        postingFactStore.rekeyBook(replacementPassphrase, Instant.parse("2026-04-09T10:15:30Z"));
      }
    }
    BookAccess rekeyedAccess = bookAccess(bookPath, replacementKeyText);
    for (int round = 0; round < 10; round++) {
      int roundNumber = round + 1;
      int completedReaders =
          assertDoesNotThrow(
              () -> runConcurrentReadRound(rekeyedAccess, 24, this::hotPathConcurrentReadAssertion),
              () ->
                  "Concurrent rekeyed hot-path round "
                      + roundNumber
                      + " surfaced one transient lock failure.");
      assertEquals(24, completedReaders);
    }
  }

  @Test
  void concurrentPublicReadServiceQueriesRemainAvailableAfterBookRekeyRotation() throws Exception {
    Path bookPath = tempDirectory.resolve("concurrent-public-read-rekeyed.sqlite");
    BookAccess originalAccess = bookAccess(bookPath);
    String replacementKeyText = "rotated-public-read-key";
    try (SqlitePostingFactStore postingFactStore = openStore(originalAccess)) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      commitPosting(
          postingFactStore, postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty()));
      try (SqliteBookPassphrase replacementPassphrase =
          SqliteBookPassphrase.fromCharacters(
              "concurrent public read replacement key", replacementKeyText.toCharArray())) {
        postingFactStore.rekeyBook(replacementPassphrase, Instant.parse("2026-04-09T10:15:30Z"));
      }
    }
    BookAccess rekeyedAccess = bookAccess(bookPath, replacementKeyText);
    for (int round = 0; round < 10; round++) {
      int roundNumber = round + 1;
      int completedReaders =
          assertDoesNotThrow(
              () ->
                  runConcurrentPublicReadRound(rekeyedAccess, 24, this::hotPathPublicReadAssertion),
              () ->
                  "Concurrent public read round "
                      + roundNumber
                      + " surfaced one transient lock failure.");
      assertEquals(24, completedReaders);
    }
  }

  private int runConcurrentReadRound(
      BookAccess access,
      int concurrentReaderCount,
      IntFunction<ConcurrentReadAssertion> assertionSelector)
      throws ExecutionException, InterruptedException, TimeoutException {
    CountDownLatch ready = new CountDownLatch(concurrentReaderCount);
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newFixedThreadPool(concurrentReaderCount)) {
      Future<?>[] futures = new Future<?>[concurrentReaderCount];
      for (int index = 0; index < concurrentReaderCount; index++) {
        ConcurrentReadAssertion assertion = assertionSelector.apply(index);
        futures[index] =
            executor.submit(
                () -> {
                  withConcurrentReadSession(access, ready, start, assertion);
                  return null;
                });
      }
      assertTrue(
          ready.await(5, TimeUnit.SECONDS),
          "Timed out waiting for concurrent readers to become ready.");
      start.countDown();
      int completedReaders = 0;
      for (Future<?> future : futures) {
        future.get(10, TimeUnit.SECONDS);
        completedReaders++;
      }
      return completedReaders;
    }
  }

  private int runConcurrentPublicReadRound(
      BookAccess access,
      int concurrentReaderCount,
      IntFunction<ConcurrentPublicReadAssertion> assertionSelector)
      throws ExecutionException, InterruptedException, TimeoutException {
    CountDownLatch ready = new CountDownLatch(concurrentReaderCount);
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newFixedThreadPool(concurrentReaderCount)) {
      Future<?>[] futures = new Future<?>[concurrentReaderCount];
      for (int index = 0; index < concurrentReaderCount; index++) {
        ConcurrentPublicReadAssertion assertion = assertionSelector.apply(index);
        futures[index] =
            executor.submit(
                () -> {
                  withConcurrentPublicReadService(access, ready, start, assertion);
                  return null;
                });
      }
      assertTrue(
          ready.await(5, TimeUnit.SECONDS),
          "Timed out waiting for concurrent public readers to become ready.");
      start.countDown();
      int completedReaders = 0;
      for (Future<?> future : futures) {
        future.get(10, TimeUnit.SECONDS);
        completedReaders++;
      }
      return completedReaders;
    }
  }

  private ConcurrentReadAssertion broadConcurrentReadAssertion(int index) {
    return switch (index % 5) {
      case 0 -> this::assertPostingLookup;
      case 1 -> this::assertPostingListing;
      case 2 -> this::assertAccountBalance;
      case 3 -> this::assertTrialBalance;
      default -> this::assertPeriodSummary;
    };
  }

  private ConcurrentReadAssertion hotPathConcurrentReadAssertion(int index) {
    if (index % 2 == 0) {
      return this::assertListAccounts;
    }
    return this::assertTrialBalance;
  }

  private ConcurrentPublicReadAssertion hotPathPublicReadAssertion(int index) {
    if (index % 2 == 0) {
      return this::assertPublicListAccounts;
    }
    return this::assertPublicTrialBalance;
  }

  private void withConcurrentReadSession(
      BookAccess access,
      CountDownLatch ready,
      CountDownLatch start,
      ConcurrentReadAssertion assertion)
      throws InterruptedException {
    ready.countDown();
    assertTrue(
        start.await(5, TimeUnit.SECONDS),
        "Timed out waiting to start one concurrent read session.");
    try (SqlitePostingFactStore readStore = openStore(access, SqliteStoreAccessMode.READ_ONLY)) {
      assertion.assertAgainst(readStore);
    }
  }

  private void withConcurrentPublicReadService(
      BookAccess access,
      CountDownLatch ready,
      CountDownLatch start,
      ConcurrentPublicReadAssertion assertion)
      throws InterruptedException {
    ready.countDown();
    assertTrue(
        start.await(5, TimeUnit.SECONDS),
        "Timed out waiting to start one concurrent public read session.");
    try (SqlitePostingFactStore readStore = openStore(access, SqliteStoreAccessMode.READ_ONLY);
        SqliteReadSession readSession = SqliteCapabilitySessions.read(readStore)) {
      assertion.assertAgainst(new BookReadService(readSession));
    }
  }

  private void assertPostingLookup(SqlitePostingFactStore readStore) {
    assertTrue(readStore.findPosting(new PostingId("posting-1")).isPresent());
  }

  private void assertPostingListing(SqlitePostingFactStore readStore) {
    PostingHistoryPage page =
        readStore.listPostings(
            postingHistoryQuery(Optional.empty(), null, null, 10, Optional.empty()));
    assertEquals(1, page.postings().size());
  }

  private void assertListAccounts(SqlitePostingFactStore readStore) {
    assertEquals(2, readStore.listAccounts(firstAccountPage()).accounts().size());
  }

  private void assertAccountBalance(SqlitePostingFactStore readStore) {
    assertTrue(
        readStore
            .accountBalance(accountBalanceCriteria(new AccountCode("1000"), null, null))
            .isPresent());
  }

  private void assertTrialBalance(SqlitePostingFactStore readStore) {
    TrialBalanceView view = readStore.trialBalance(trialBalanceCriteria(Optional.empty()));
    assertEquals(2, view.rows().size());
  }

  private void assertPublicListAccounts(BookReadService service) {
    ListAccountsResult result = service.listAccounts(new ListAccountsQuery(20, Optional.empty()));
    assertEquals(2, ((ListAccountsResult.Listed) result).page().accounts().size());
  }

  private void assertPublicTrialBalance(BookReadService service) {
    TrialBalanceResult result =
        service.trialBalance(
            new TrialBalanceQuery(
                Optional.empty(), PostingCoverage.ALL_POSTING_KINDS, ComparativeSelection.none()));
    assertEquals(2, ((TrialBalanceResult.Reported) result).report().rows().size());
  }

  private void assertPeriodSummary(SqlitePostingFactStore readStore) {
    assertEquals(
        1,
        readStore
            .periodSummary(
                periodSummaryCriteria(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")))
            .postingCount());
  }

  /** Assertion callback that verifies one concurrent read-only session against the shared book. */
  @FunctionalInterface
  private interface ConcurrentReadAssertion {
    /** Runs one concurrent read-only assertion against the shared SQLite posting store. */
    void assertAgainst(SqlitePostingFactStore readStore);
  }

  /** Assertion callback that verifies one concurrent public read service over the shared book. */
  @FunctionalInterface
  private interface ConcurrentPublicReadAssertion {
    /** Runs one concurrent public read assertion against one service instance. */
    void assertAgainst(BookReadService service);
  }
}
