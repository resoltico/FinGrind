package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.Committed;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.core.PostingId;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteRoundTripWorkflowConcurrencyCoverageTest {
  @TempDir Path tempDirectory;

  @Test
  void concurrent_outcome_helpers_cover_success_and_error_shapes() throws Exception {
    var committedOutcome =
        new ConcurrentCommitDecision(
            ContractDecision.accepted(SqliteRoundTripWorkflowTestSupport.committed("posting-1")));
    var replayOutcome =
        new ConcurrentCommitDecision(
            ContractDecision.accepted(
                SqliteRoundTripWorkflowTestSupport.committed("posting-1", true)));
    var runtimeOutcome =
        new ConcurrentCommitRuntimeFailure(new IllegalStateException("synthetic runtime"));

    assertDoesNotThrow(
        () ->
            SqliteRoundTripWorkflowConcurrencyCoverage.assertConcurrentOutcomeSet(
                List.of(committedOutcome, replayOutcome), true));

    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowConcurrencyCoverage.assertConcurrentOutcomeSet(
                List.of(committedOutcome, runtimeOutcome), true));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowConcurrencyCoverage.assertConcurrentOutcomeSet(
                List.of(
                    committedOutcome,
                    new ConcurrentCommitDecision(
                        ContractDecision.rejected(
                            SqliteRoundTripWorkflowTestSupport.contractFailure(
                                "concurrent rejection")))),
                true));

    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowConcurrencyCoverage.assertConcurrentOutcomeSet(
                List.of(
                    committedOutcome,
                    new ConcurrentCommitDecision(
                        ContractDecision.accepted(
                            SqliteRoundTripWorkflowTestSupport.commitRejected(
                                new PostingRejection.ReversalTargetNotFound(
                                    new PostingId("6d857901-cb53-3986-a1d7-2f64319c76ce")))))),
                true));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowConcurrencyCoverage.assertConcurrentOutcomeSet(
                List.of(committedOutcome, replayOutcome), false));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowConcurrencyCoverage.assertConcurrentOutcomeSet(
                List.of(
                    committedOutcome,
                    replayOutcome,
                    new ConcurrentCommitDecision(
                        ContractDecision.rejected(
                            SqliteRoundTripWorkflowTestSupport.contractFailure(
                                "extra rejection")))),
                true));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowConcurrencyCoverage.assertConcurrentOutcomeSet(
                List.of(committedOutcome, committedOutcome), true));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteRoundTripWorkflowConcurrencyCoverage.assertConcurrentOutcomeSet(
                List.of(committedOutcome, replayOutcome, replayOutcome), true));
    CommitEntryResult replayResult =
        SqliteRoundTripWorkflowTestSupport.committed("posting-1", true);
    assertTrue(replayResult instanceof Committed committed && committed.idempotentReplay());
    CommitEntryResult reversalTargetResult =
        SqliteRoundTripWorkflowTestSupport.commitRejected(
            new PostingRejection.ReversalTargetNotFound(new PostingId("eed4f82c-57ee-3caf-9afb-271b05f48aa9")));
    assertFalse(
        reversalTargetResult
                instanceof
                dev.erst.fingrind.contract.bookkeeping.PostEntryResult.CommitRejected rejected
            && rejected.rejection() instanceof PostingRejection.IdempotencyKeyConflict);
    CommitEntryResult committedResult =
        SqliteRoundTripWorkflowTestSupport.committed("posting-duplicate");
    assertFalse(committedResult instanceof Committed committed && committed.idempotentReplay());
  }

  @Test
  void concurrentOutcomeTally_rejects_negative_counts() throws Exception {
    IllegalArgumentException negativeCommittedCount =
        assertThrows(IllegalArgumentException.class, () -> newConcurrentOutcomeTally(-1, 0));
    SqliteRoundTripWorkflowTestSupport.assertMessageContains(
        negativeCommittedCount, "must be non-negative");

    IllegalArgumentException negativeReplayCount =
        assertThrows(IllegalArgumentException.class, () -> newConcurrentOutcomeTally(0, -1));
    SqliteRoundTripWorkflowTestSupport.assertMessageContains(
        negativeReplayCount, "must be non-negative");
  }

  @Test
  void private_concurrency_error_paths_are_exercised() throws Exception {
    Object tally = newConcurrentOutcomeTally(0, 0);

    IllegalStateException runtime =
        assertThrows(
            IllegalStateException.class,
            () ->
                invokeConcurrencyPrivate(
                    "tallyConcurrentOutcome",
                    new Class<?>[] {tally.getClass(), ConcurrentCommitOutcome.class},
                    new Object[] {
                      tally,
                      new ConcurrentCommitRuntimeFailure(
                          new IllegalStateException("synthetic runtime"))
                    }));
    SqliteRoundTripWorkflowTestSupport.assertMessageContains(runtime, "synthetic runtime");

    IllegalStateException rejectedDecision =
        assertThrows(
            IllegalStateException.class,
            () ->
                invokeConcurrencyPrivate(
                    "tallyCommitDecision",
                    new Class<?>[] {tally.getClass(), ContractDecision.class},
                    new Object[] {
                      tally,
                      ContractDecision.rejected(
                          SqliteRoundTripWorkflowTestSupport.contractFailure("synthetic failure"))
                    }));
    SqliteRoundTripWorkflowTestSupport.assertMessageContains(rejectedDecision, "synthetic failure");

    IllegalStateException acceptedRejection =
        assertThrows(
            IllegalStateException.class,
            () ->
                invokeConcurrencyPrivate(
                    "tallyAcceptedCommitResult",
                    new Class<?>[] {tally.getClass(), CommitEntryResult.class},
                    new Object[] {
                      tally,
                      SqliteRoundTripWorkflowTestSupport.commitRejected(
                          new PostingRejection.ReversalTargetNotFound(
                              new PostingId("0feb0a6f-51da-3617-95a0-1a85821f337d")))
                    }));
    SqliteRoundTripWorkflowTestSupport.assertMessageContains(acceptedRejection, "posting-private");
  }

  @Test
  void exercise_concurrent_writer_coverage_runs_end_to_end() {
    assertDoesNotThrow(
        () ->
            SqliteRoundTripWorkflowConcurrencyCoverage.exerciseConcurrentWriterCoverage(
                SqliteRoundTripWorkflowTestSupport.basicValidCommand(),
                tempDirectory.resolve("concurrent-roundtrip")));
  }

  @Test
  void future_and_gate_helpers_cover_runtime_checked_timeout_and_interrupt_paths()
      throws IOException {
    var outcome =
        new ConcurrentCommitDecision(
            ContractDecision.accepted(SqliteRoundTripWorkflowTestSupport.committed("posting-1")));
    assertEquals(
        outcome,
        SqliteRoundTripWorkflowConcurrencyCoverage.awaitCommitOutcome(
            CompletableFuture.completedFuture(outcome)));

    IllegalStateException runtime =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteRoundTripWorkflowConcurrencyCoverage.awaitCommitOutcome(
                    SqliteRoundTripWorkflowTestSupport.exceptionalFuture(
                        new java.util.concurrent.ExecutionException(
                            new IllegalStateException("future boom")))));
    SqliteRoundTripWorkflowTestSupport.assertMessageContains(runtime, "future boom");

    IllegalStateException checked =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteRoundTripWorkflowConcurrencyCoverage.awaitCommitOutcome(
                    SqliteRoundTripWorkflowTestSupport.exceptionalFuture(
                        new java.util.concurrent.ExecutionException(new IOException("checked")))));
    SqliteRoundTripWorkflowTestSupport.assertMessageContains(
        checked, "Concurrent writer coverage failed unexpectedly");

    IllegalStateException interruptedFuture =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteRoundTripWorkflowConcurrencyCoverage.awaitCommitOutcome(
                    SqliteRoundTripWorkflowTestSupport.interruptedFuture(
                        new InterruptedException("future interrupted"))));
    SqliteRoundTripWorkflowTestSupport.assertMessageContains(
        interruptedFuture, "Concurrent writer coverage was interrupted");
    assertInstanceOf(InterruptedException.class, interruptedFuture.getCause());
    assertTrue(Thread.currentThread().isInterrupted());
    Thread.interrupted();

    IllegalStateException timeout =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteRoundTripWorkflowConcurrencyCoverage.awaitCommitOutcome(
                    SqliteRoundTripWorkflowTestSupport.timeoutFuture(
                        new TimeoutException("slow future"))));
    SqliteRoundTripWorkflowTestSupport.assertMessageContains(timeout, "timed out unexpectedly");

    var openGate = new java.util.concurrent.CountDownLatch(0);
    SqliteRoundTripWorkflowConcurrencyCoverage.awaitConcurrentGate(
        openGate, 30, TimeUnit.SECONDS, "gate should already be open");
    assertEquals(0L, openGate.getCount());

    IllegalStateException timedOutGate =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteRoundTripWorkflowConcurrencyCoverage.awaitConcurrentGate(
                    new java.util.concurrent.CountDownLatch(1),
                    0,
                    TimeUnit.NANOSECONDS,
                    "gate timed out"));
    SqliteRoundTripWorkflowTestSupport.assertMessageContains(timedOutGate, "gate timed out");
    try {
      Thread.currentThread().interrupt();
      IllegalStateException interruptedGate =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteRoundTripWorkflowConcurrencyCoverage.awaitConcurrentGate(
                      new java.util.concurrent.CountDownLatch(1),
                      30,
                      TimeUnit.SECONDS,
                      "gate interrupted"));
      SqliteRoundTripWorkflowTestSupport.assertMessageContains(
          interruptedGate, "Concurrent writer coverage was interrupted");
      assertInstanceOf(InterruptedException.class, interruptedGate.getCause());
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }

    ContractDecision<CommitEntryResult> acceptedDecision =
        assertInstanceOf(
                ConcurrentCommitDecision.class,
                new ConcurrentCommitDecision(
                    ContractDecision.accepted(
                        SqliteRoundTripWorkflowTestSupport.committed("posting-2"))))
            .decision();
    assertEquals(
        "posting-2",
        assertInstanceOf(Committed.class, acceptedDecision.requireAccepted()).postingId().value());

    SqliteRoundTripWorkflowTestSupport.assertMessageContains(
        new ConcurrentCommitRuntimeFailure(new IllegalStateException("captured runtime"))
            .runtimeFailure(),
        "captured runtime");

    IllegalStateException interrupted =
        SqliteRoundTripWorkflowConcurrencyCoverage.concurrentWriterInterrupted(
            new InterruptedException("stop"));
    SqliteRoundTripWorkflowTestSupport.assertMessageContains(
        interrupted, "Concurrent writer coverage was interrupted");
    assertInstanceOf(InterruptedException.class, interrupted.getCause());
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void executor_helpers_use_daemon_threads_and_cancel_pending_tasks() throws Exception {
    ExecutorService executor =
        SqliteRoundTripWorkflowConcurrencyCoverage.newConcurrentWriterExecutor();
    CountDownLatch started = new CountDownLatch(1);
    CompletableFuture<Boolean> interrupted = new CompletableFuture<>();
    try {
      Future<Boolean> daemonThreadCheck = executor.submit(() -> Thread.currentThread().isDaemon());
      Future<?> blockedFuture =
          executor.submit(
              (Callable<Void>)
                  () -> {
                    started.countDown();
                    try {
                      Thread.sleep(TimeUnit.SECONDS.toMillis(30));
                      interrupted.complete(false);
                    } catch (InterruptedException exception) {
                      interrupted.complete(true);
                    }
                    return null;
                  });

      assertTrue(daemonThreadCheck.get(30, TimeUnit.SECONDS));
      SqliteRoundTripWorkflowConcurrencyCoverage.awaitConcurrentGate(
          started, 30, TimeUnit.SECONDS, "blocking task did not start");
      SqliteRoundTripWorkflowConcurrencyCoverage.cancelOutstandingFutures(List.of(blockedFuture));
    } finally {
      SqliteRoundTripWorkflowConcurrencyCoverage.shutdownConcurrentWriterExecutor(executor);
    }
    assertTrue(interrupted.get(30, TimeUnit.SECONDS));
    assertTrue(executor.isTerminated());
  }

  @Test
  void concurrent_commit_task_releases_setup_turn_when_session_opening_fails() throws Exception {
    CountDownLatch ready = new CountDownLatch(1);
    CountDownLatch start = new CountDownLatch(1);
    Semaphore setupTurn = new Semaphore(1, true);
    BookAccess missingKeyBookAccess =
        new BookAccess(
            tempDirectory.resolve("missing-book.sqlite"),
            new BookAccess.PassphraseSource.KeyFile(tempDirectory.resolve("missing-book.key")));

    ConcurrentCommitOutcome outcome =
        concurrentCommitTask(
                ready,
                start,
                setupTurn,
                missingKeyBookAccess,
                SqliteRoundTripWorkflowTestSupport.basicValidCommand())
            .call();

    assertInstanceOf(ConcurrentCommitRuntimeFailure.class, outcome);
    assertEquals(1L, ready.getCount());
    assertEquals(1L, start.getCount());
    assertEquals(1, setupTurn.availablePermits());
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void shutdown_concurrent_writer_executor_rejects_nonterminating_executor() {
    ExecutorService nonTerminatingDelegate =
        new AbstractExecutorService() {
          private boolean shutdown;

          @Override
          public void shutdown() {
            shutdown = true;
          }

          @Override
          public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
          }

          @Override
          public boolean isShutdown() {
            return shutdown;
          }

          @Override
          public boolean isTerminated() {
            return false;
          }

          @Override
          public boolean awaitTermination(long timeout, TimeUnit unit) {
            return false;
          }

          @Override
          public void execute(Runnable command) {
            throw new UnsupportedOperationException("No tasks should be submitted.");
          }
        };
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteRoundTripWorkflowConcurrencyCoverage.shutdownConcurrentWriterExecutor(
                    nonTerminatingDelegate));

    SqliteRoundTripWorkflowTestSupport.assertMessageContains(
        exception, "did not terminate after cancellation");
    assertTrue(nonTerminatingDelegate.isShutdown());
  }

  @Test
  void concurrent_commit_task_wraps_interrupts_and_preserves_interrupt_status() throws Exception {
    CountDownLatch ready = new CountDownLatch(1);
    CountDownLatch start = new CountDownLatch(1);
    Semaphore setupTurn = new Semaphore(1, true);
    BookAccess bookAccess =
        new BookAccess(
            tempDirectory.resolve("interrupt-book.sqlite"),
            new BookAccess.PassphraseSource.KeyFile(tempDirectory.resolve("interrupt-book.key")));

    try {
      Thread.currentThread().interrupt();
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  concurrentCommitTask(
                          ready,
                          start,
                          setupTurn,
                          bookAccess,
                          SqliteRoundTripWorkflowTestSupport.basicValidCommand())
                      .call());

      SqliteRoundTripWorkflowTestSupport.assertMessageContains(
          exception, "Concurrent writer coverage was interrupted");
      assertInstanceOf(InterruptedException.class, exception.getCause());
      assertTrue(Thread.currentThread().isInterrupted());
      assertEquals(1, setupTurn.availablePermits());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void shutdown_concurrent_writer_executor_wraps_interrupts_and_preserves_interrupt_status() {
    ExecutorService interruptingDelegate =
        new AbstractExecutorService() {
          private boolean shutdown;

          @Override
          public void shutdown() {
            shutdown = true;
          }

          @Override
          public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
          }

          @Override
          public boolean isShutdown() {
            return shutdown;
          }

          @Override
          public boolean isTerminated() {
            return false;
          }

          @Override
          public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            throw new InterruptedException("synthetic interrupt");
          }

          @Override
          public void execute(Runnable command) {
            throw new UnsupportedOperationException("No tasks should be submitted.");
          }
        };
    try {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteRoundTripWorkflowConcurrencyCoverage.shutdownConcurrentWriterExecutor(
                      interruptingDelegate));

      SqliteRoundTripWorkflowTestSupport.assertMessageContains(
          exception, "Concurrent writer coverage was interrupted");
      assertInstanceOf(InterruptedException.class, exception.getCause());
      assertTrue(Thread.currentThread().isInterrupted());
      assertTrue(interruptingDelegate.isShutdown());
    } finally {
      Thread.interrupted();
    }
  }

  @SuppressWarnings("unchecked")
  private static Callable<ConcurrentCommitOutcome> concurrentCommitTask(
      CountDownLatch ready,
      CountDownLatch start,
      Semaphore setupTurn,
      BookAccess bookAccess,
      PostEntryCommand concurrentCommand)
      throws ReflectiveOperationException {
    Class<?> taskClass =
        Class.forName(
            "dev.erst.fingrind.cli.SqliteRoundTripWorkflowConcurrencyCoverage$ConcurrentCommitTask");
    Constructor<?> constructor =
        taskClass.getDeclaredConstructor(
            CountDownLatch.class,
            CountDownLatch.class,
            Semaphore.class,
            BookAccess.class,
            PostEntryCommand.class);
    constructor.setAccessible(true);
    return (Callable<ConcurrentCommitOutcome>)
        constructor.newInstance(ready, start, setupTurn, bookAccess, concurrentCommand);
  }

  private static Object newConcurrentOutcomeTally(long committedCount, long nonWinningCount)
      throws ReflectiveOperationException {
    Class<?> tallyClass =
        Class.forName(
            "dev.erst.fingrind.cli.SqliteRoundTripWorkflowConcurrencyCoverage$ConcurrentOutcomeTally");
    Constructor<?> constructor = tallyClass.getDeclaredConstructor(long.class, long.class);
    constructor.setAccessible(true);
    try {
      return constructor.newInstance(committedCount, nonWinningCount);
    } catch (java.lang.reflect.InvocationTargetException exception) {
      if (exception.getCause() instanceof IllegalArgumentException illegalArgumentException) {
        throw illegalArgumentException;
      }
      throw exception;
    }
  }

  private static Object invokeConcurrencyPrivate(
      String methodName, Class<?>[] parameterTypes, Object[] arguments)
      throws ReflectiveOperationException {
    var method =
        SqliteRoundTripWorkflowConcurrencyCoverage.class.getDeclaredMethod(
            methodName, parameterTypes);
    method.setAccessible(true);
    try {
      return method.invoke(null, arguments);
    } catch (java.lang.reflect.InvocationTargetException exception) {
      if (exception.getCause() instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      if (exception.getCause() instanceof Error error) {
        throw error;
      }
      throw exception;
    }
  }
}
