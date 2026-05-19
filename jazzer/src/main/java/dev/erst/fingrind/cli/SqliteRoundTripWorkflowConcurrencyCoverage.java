package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.CommitRejected;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.Committed;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.executor.PostingApplicationService;
import dev.erst.fingrind.executor.UuidV7PostingIdGenerator;
import dev.erst.fingrind.sqlite.SqliteBookSessionMode;
import dev.erst.fingrind.sqlite.SqliteBookSessions;
import dev.erst.fingrind.sqlite.SqliteFuzzAssertions;
import dev.erst.fingrind.sqlite.SqlitePassphraseIntent;
import dev.erst.fingrind.sqlite.SqlitePostingSession;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/** Covers competing-writer behavior for SQLite round-trip fuzzing and replay. */
final class SqliteRoundTripWorkflowConcurrencyCoverage {
  private static final long CONCURRENT_WRITER_TIMEOUT_SECONDS = 30L;
  private static final long EXECUTOR_TERMINATION_TIMEOUT_SECONDS = 5L;
  private static final String CONCURRENT_WRITER_INTERRUPTED_MESSAGE =
      "Concurrent writer coverage was interrupted.";
  private static final String CONCURRENT_WRITER_TIMEOUT_MESSAGE =
      "Concurrent writer coverage timed out unexpectedly.";
  private static final String CONCURRENT_WRITER_FAILURE_MESSAGE =
      "Concurrent writer coverage failed unexpectedly.";
  private static final String CONCURRENT_WRITER_NON_WINNER_MESSAGE =
      "Concurrent writer coverage saw an unexpected accepted non-winning outcome: ";

  private SqliteRoundTripWorkflowConcurrencyCoverage() {}

  private record ConcurrentCommitTask(
      CountDownLatch ready,
      CountDownLatch start,
      Semaphore setupTurn,
      BookAccess bookAccess,
      PostEntryCommand concurrentCommand)
      implements Callable<ConcurrentCommitOutcome> {
    @Override
    public ConcurrentCommitOutcome call() {
      boolean setupTurnHeld = false;
      try {
        setupTurn.acquire();
        setupTurnHeld = true;
        try (SqlitePostingSession bookSession =
            SqliteBookSessions.openPosting(
                bookAccess,
                SqliteBookSessionMode.READ_WRITE_EXISTING,
                SqliteRoundTripWorkflowResources.passphraseResolver(),
                SqlitePassphraseIntent.EXISTING_SECRET)) {
          setupTurn.release();
          setupTurnHeld = false;
          PostingApplicationService applicationService =
              CliFuzzFixtures.postingApplicationService(
                  bookSession, bookSession, new UuidV7PostingIdGenerator());
          ready.countDown();
          awaitConcurrentGate(
              start,
              CONCURRENT_WRITER_TIMEOUT_SECONDS,
              TimeUnit.SECONDS,
              "Concurrent writer start gate timed out.");
          return new ConcurrentCommitDecision(
              ContractDecision.accepted(
                  CliFuzzFixtures.commit(applicationService, concurrentCommand)));
        }
      } catch (InterruptedException exception) {
        throw concurrentWriterInterrupted(exception);
      } catch (RuntimeException runtimeException) {
        return new ConcurrentCommitRuntimeFailure(runtimeException);
      } finally {
        if (setupTurnHeld) {
          setupTurn.release();
        }
      }
    }
  }

  static void exerciseConcurrentWriterCoverage(PostEntryCommand command, Path concurrentRoot)
      throws IOException {
    Path bookPath = concurrentRoot.resolve("books").resolve("entity.sqlite");
    Path keyPath = concurrentRoot.resolve("keys").resolve("entity.book-key");
    SqliteFuzzAssertions.writeDeterministicBookKeyFile(keyPath);
    BookAccess bookAccess = SqliteRoundTripWorkflowResources.keyFileBookAccess(bookPath, keyPath);
    SqliteCliBookWorkflow workflow = SqliteRoundTripWorkflowResources.sqliteWorkflow();
    PostEntryCommand concurrentCommand =
        SqliteRoundTripWorkflowCommandDerivation.syntheticDirectCommand(command, "concurrent");

    SqliteRoundTripWorkflowRenderingAssertions.assertOpened(
        workflow.openBook(
            bookAccess,
            CliFuzzFixtures.openBookCommand(concurrentCommand.journalEntry().currencyUnit())),
        bookPath,
        OutputMode.JSON,
        "\"initializedAt\"");
    for (var declareAccountCommand :
        CliFuzzFixtures.declarePostingAccountCommands(concurrentCommand)) {
      SqliteRoundTripWorkflowLifecycleAssertions.requireDeclared(
          workflow.declareAccount(bookAccess, declareAccountCommand));
    }

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    Semaphore setupTurn = new Semaphore(1, true);
    var executor = newConcurrentWriterExecutor(); // NOPMD
    List<Future<ConcurrentCommitOutcome>> futures = List.of();
    try {
      Callable<ConcurrentCommitOutcome> task =
          new ConcurrentCommitTask(ready, start, setupTurn, bookAccess, concurrentCommand);
      var firstFuture = executor.submit(task); // NOPMD
      var secondFuture = executor.submit(task); // NOPMD
      futures = List.of(firstFuture, secondFuture);
      awaitConcurrentGate(
          ready,
          CONCURRENT_WRITER_TIMEOUT_SECONDS,
          TimeUnit.SECONDS,
          "Concurrent writer setup timed out.");
      start.countDown();

      List<ConcurrentCommitOutcome> outcomes =
          List.of(awaitCommitOutcome(futures.getFirst()), awaitCommitOutcome(futures.getLast()));
      boolean storedFactPresent;
      try (SqlitePostingSession store = SqliteFuzzAssertions.openStore(bookPath)) {
        storedFactPresent =
            store
                .findExistingPosting(concurrentCommand.requestProvenance().idempotencyKey())
                .isPresent();
      }
      assertConcurrentOutcomeSet(outcomes, storedFactPresent);
    } finally {
      cancelOutstandingFutures(futures);
      shutdownConcurrentWriterExecutor(executor);
    }
  }

  static ExecutorService newConcurrentWriterExecutor() { // NOPMD
    AtomicInteger threadCounter = new AtomicInteger(1);
    ThreadFactory threadFactory =
        runnable -> {
          var thread =
              new Thread( // NOPMD - deterministic daemon contender threads for concurrency
                  // coverage.
                  runnable, "fingrind-sqlite-concurrent-writer-" + threadCounter.getAndIncrement());
          thread.setDaemon(true);
          return thread;
        };
    return Executors.newFixedThreadPool(2, threadFactory); // NOPMD
  }

  static void cancelOutstandingFutures(List<? extends Future<?>> futures) {
    for (Future<?> future : futures) {
      future.cancel(true);
    }
  }

  static void shutdownConcurrentWriterExecutor(
      ExecutorService executor) { // NOPMD - explicit executor teardown.
    executor.shutdownNow(); // NOPMD - explicit executor teardown.
    try {
      boolean terminated =
          executor.awaitTermination( // NOPMD - explicit executor teardown.
              EXECUTOR_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (!terminated) {
        throw new IllegalStateException(
            "Concurrent writer coverage executor did not terminate after cancellation.");
      }
    } catch (InterruptedException exception) {
      throw concurrentWriterInterrupted(exception);
    }
  }

  static void assertConcurrentOutcomeSet(
      List<ConcurrentCommitOutcome> outcomes, boolean storedFactPresent) throws IOException {
    long committedCount = 0;
    long nonWinningCount = 0;
    for (ConcurrentCommitOutcome outcome : outcomes) {
      switch (outcome) {
        case ConcurrentCommitRuntimeFailure(RuntimeException runtimeFailure) -> {
          nonWinningCount++;
          SqliteRoundTripWorkflowRenderingAssertions.assertRenderedRuntimeFailure(
              runtimeFailure, OutputMode.JSON, null);
        }
        case ConcurrentCommitDecision(ContractDecision<CommitEntryResult> decision) -> {
          if (decision instanceof ContractDecision.Rejected<CommitEntryResult>(var failure)) {
            nonWinningCount++;
            SqliteRoundTripWorkflowRenderingAssertions.assertRenderedFailure(
                failure, OutputMode.JSON, null);
          } else {
            CommitEntryResult result = decision.requireAccepted();
            SqliteRoundTripWorkflowRenderingAssertions.assertRenderedAccepted(
                ContractDecision.accepted(result),
                OutputMode.JSON,
                (writer, accepted) ->
                    writer.writePostEntryResult((PostEntryResult) accepted, OutputMode.JSON),
                null);
            switch (result) {
              case Committed ignored -> committedCount++;
              case CommitRejected rejected -> {
                if (rejected.rejection() instanceof PostingRejection.DuplicateIdempotencyKey) {
                  nonWinningCount++;
                } else {
                  throw new IllegalStateException(CONCURRENT_WRITER_NON_WINNER_MESSAGE + rejected);
                }
              }
            }
          }
        }
      }
    }
    if (committedCount != 1 || nonWinningCount != 1) {
      throw new IllegalStateException(
          "Concurrent writer coverage expected one committed winner and one non-winning contender, but saw committedCount="
              + committedCount
              + " nonWinningCount="
              + nonWinningCount);
    }
    if (!storedFactPresent) {
      throw new IllegalStateException(
          "Concurrent writer coverage failed to persist the winning posting fact.");
    }
  }

  static void awaitConcurrentGate(
      CountDownLatch latch, long timeout, TimeUnit unit, String timeoutMessage) {
    try {
      if (!latch.await(timeout, unit)) {
        throw new IllegalStateException(timeoutMessage);
      }
    } catch (InterruptedException exception) {
      throw concurrentWriterInterrupted(exception);
    }
  }

  static IllegalStateException concurrentWriterInterrupted(InterruptedException exception) {
    Thread.currentThread().interrupt(); // NOPMD - preserve interrupt status.
    return new IllegalStateException(CONCURRENT_WRITER_INTERRUPTED_MESSAGE, exception);
  }

  static ConcurrentCommitOutcome awaitCommitOutcome(Future<ConcurrentCommitOutcome> future) {
    try {
      return future.get(CONCURRENT_WRITER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException exception) {
      throw concurrentWriterInterrupted(exception);
    } catch (TimeoutException exception) {
      throw new IllegalStateException(CONCURRENT_WRITER_TIMEOUT_MESSAGE, exception);
    } catch (ExecutionException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw new IllegalStateException(
            CONCURRENT_WRITER_FAILURE_MESSAGE + ": " + runtimeException.getMessage(), exception);
      }
      throw new IllegalStateException(CONCURRENT_WRITER_FAILURE_MESSAGE, exception);
    }
  }
}
