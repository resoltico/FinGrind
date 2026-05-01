package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import dev.erst.fingrind.executor.BookReadSession;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Proves that thread-confined SQLite seams reject cross-thread access explicitly. */
@NullUnmarked
class SqliteThreadConfinementTest extends SqlitePostingFactStoreTestSupport {
  @Test
  @SuppressWarnings("PMD.CloseResource")
  void postingFactStore_rejectsCrossThreadPrime() throws ExecutionException, InterruptedException {
    Path bookPath = tempDirectory.resolve("cross-thread-prime.sqlite");
    SqlitePostingFactStore store =
        createOnThread(
            "sqlite-owner-session", () -> new SqlitePostingFactStore(bookAccess(bookPath)));

    IllegalStateException exception =
        captureIllegalStateOnThread("sqlite-reader-session", store::prime);

    assertEquals(
        "SQLite book session is thread-confined and is owned by thread 'sqlite-owner-session' but was accessed from thread 'sqlite-reader-session'.",
        exception.getMessage());
  }

  @Test
  void readSessionView_rejectsCrossThreadInspection()
      throws ExecutionException, InterruptedException {
    Path bookPath = tempDirectory.resolve("cross-thread-read-session.sqlite");
    BookReadSession readSession =
        createOnThread(
            "sqlite-owner-read-session",
            () -> new SqlitePostingFactStore(bookAccess(bookPath)).readSession());

    IllegalStateException exception =
        captureIllegalStateOnThread("sqlite-reader-read-session", readSession::inspectBook);

    assertEquals(
        "SQLite book session is thread-confined and is owned by thread 'sqlite-owner-read-session' but was accessed from thread 'sqlite-reader-read-session'.",
        exception.getMessage());
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void nativeDatabase_rejectsCrossThreadHandleAccess()
      throws ExecutionException, InterruptedException {
    SqliteNativeDatabase database =
        createOnThread("sqlite-owner-native", () -> new SqliteNativeDatabase(MemorySegment.NULL));

    IllegalStateException exception =
        captureIllegalStateOnThread("sqlite-reader-native", database::handle);

    assertEquals(
        "SQLite native database handle is thread-confined and is owned by thread 'sqlite-owner-native' but was accessed from thread 'sqlite-reader-native'.",
        exception.getMessage());
  }

  private static <T> T createOnThread(String threadName, Callable<T> supplier)
      throws ExecutionException, InterruptedException {
    FutureTask<T> task = new FutureTask<>(supplier);
    Thread thread = new Thread(task, threadName);
    thread.start();
    return task.get();
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
      fail("Expected cross-thread SQLite access to be rejected.");
      return null;
    } catch (ExecutionException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof IllegalStateException illegalStateException) {
        return illegalStateException;
      }
      throw new AssertionError("Expected IllegalStateException but saw: " + cause, exception);
    }
  }
}
