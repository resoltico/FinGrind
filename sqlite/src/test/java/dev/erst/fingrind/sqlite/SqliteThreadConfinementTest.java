package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Proves that thread-confined SQLite seams reject cross-thread access explicitly. */
class SqliteThreadConfinementTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void postingFactStore_rejectsCrossThreadPrime() throws ExecutionException, InterruptedException {
    Path bookPath = tempDirectory.resolve("cross-thread-prime.sqlite");
    withOwnedResourceOnThread(
        "sqlite-owner-session",
        () -> new SqlitePostingFactStore(bookAccess(bookPath)),
        store -> {
          IllegalStateException exception =
              captureIllegalStateOnThread("sqlite-reader-session", store::prime);
          assertEquals(
              "SQLite book session is thread-confined and is owned by thread 'sqlite-owner-session' but was accessed from thread 'sqlite-reader-session'.",
              exception.getMessage());
        });
  }

  @Test
  void bookStore_rejectsCrossThreadInspection() throws ExecutionException, InterruptedException {
    Path bookPath = tempDirectory.resolve("cross-thread-read-session.sqlite");
    withOwnedResourceOnThread(
        "sqlite-owner-read-session",
        () -> new SqlitePostingFactStore(bookAccess(bookPath)),
        bookStore -> {
          IllegalStateException exception =
              captureIllegalStateOnThread("sqlite-reader-read-session", bookStore::inspectBook);
          assertEquals(
              "SQLite book session is thread-confined and is owned by thread 'sqlite-owner-read-session' but was accessed from thread 'sqlite-reader-read-session'.",
              exception.getMessage());
        });
  }

  @Test
  void nativeDatabase_rejectsCrossThreadHandleAccess()
      throws ExecutionException, InterruptedException {
    Path bookPath = tempDirectory.resolve("cross-thread-native.sqlite");
    withOwnedResourceOnThread(
        "sqlite-owner-native",
        () -> SqliteNativeConnections.open(bookAccess(bookPath)),
        database -> {
          IllegalStateException exception =
              captureIllegalStateOnThread("sqlite-reader-native", database::handle);
          assertEquals(
              "SQLite native database handle is thread-confined and is owned by thread 'sqlite-owner-native' but was accessed from thread 'sqlite-reader-native'.",
              exception.getMessage());
        });
  }

  private static <T extends AutoCloseable> void withOwnedResourceOnThread(
      String threadName, Callable<T> supplier, ThrowingConsumer<T> assertion)
      throws ExecutionException, InterruptedException {
    AtomicReference<T> resource = new AtomicReference<>();
    AtomicReference<Throwable> ownerFailure = new AtomicReference<>();
    CountDownLatch resourceReady = new CountDownLatch(1);
    CountDownLatch releaseOwner = new CountDownLatch(1);
    Thread ownerThread =
        new Thread(
            () -> {
              try (T ownedResource = supplier.call()) {
                resource.set(ownedResource);
                resourceReady.countDown();
                releaseOwner.await();
              } catch (Throwable throwable) {
                ownerFailure.set(throwable);
                resourceReady.countDown();
              }
            },
            threadName);
    ownerThread.start();
    resourceReady.await();
    Throwable setupFailure = ownerFailure.get();
    if (setupFailure != null) {
      ownerThread.join();
      rethrow(setupFailure);
    }
    try {
      assertion.accept(resource.get());
    } finally {
      releaseOwner.countDown();
      ownerThread.join();
    }
    Throwable closeFailure = ownerFailure.get();
    if (closeFailure != null) {
      rethrow(closeFailure);
    }
  }

  private static IllegalStateException captureIllegalStateOnThread(
      String threadName, Runnable runnable) throws InterruptedException {
    FutureTask<Void> task =
        new FutureTask<>(
            () -> {
              runnable.run();
              return null;
            });
    Thread thread = new Thread(task, threadName);
    thread.start();
    try {
      task.get();
      throw new AssertionError("Expected cross-thread SQLite access to be rejected.");
    } catch (ExecutionException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof IllegalStateException illegalStateException) {
        return illegalStateException;
      }
      throw new AssertionError("Expected IllegalStateException but saw: " + cause, exception);
    }
  }

  private static void rethrow(Throwable throwable) throws ExecutionException {
    if (throwable instanceof ExecutionException executionException) {
      throw executionException;
    }
    if (throwable instanceof RuntimeException runtimeException) {
      throw runtimeException;
    }
    if (throwable instanceof Error error) {
      throw error;
    }
    throw new ExecutionException(throwable);
  }

  /** Callback that runs assertions against one owner-thread-managed resource. */
  @FunctionalInterface
  private interface ThrowingConsumer<T> {
    void accept(T value) throws ExecutionException, InterruptedException;
  }
}
