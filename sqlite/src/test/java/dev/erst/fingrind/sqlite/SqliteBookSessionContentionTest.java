package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
import java.nio.file.Path;
import java.time.Duration;
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

  private ConcurrentReadAssertion broadConcurrentReadAssertion(int index) {
    return switch (index % 5) {
      case 0 -> this::assertPostingLookup;
      case 1 -> this::assertPostingListing;
      case 2 -> this::assertAccountBalance;
      case 3 -> this::assertTrialBalance;
      default -> this::assertPeriodSummary;
    };
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

  private void assertPostingLookup(SqlitePostingFactStore readStore) {
    assertTrue(
        readStore.findPosting(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")).isPresent());
  }

  private void assertPostingListing(SqlitePostingFactStore readStore) {
    PostingHistoryPage page =
        readStore.listPostings(
            postingHistoryQuery(Optional.empty(), null, null, 10, Optional.empty()));
    assertEquals(1, page.postings().size());
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
}
