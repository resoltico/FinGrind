package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import org.junit.jupiter.api.Test;

/** Covers direct validation and same-thread/cross-thread behavior for {@link SqliteThreadOwner}. */
class SqliteThreadOwnerTest {
  @Test
  void constructor_rejectsNullLabel() {
    NullPointerException exception =
        assertThrows(
            NullPointerException.class,
            () -> new SqliteThreadOwner(NullTestSupport.nullOf(String.class)));
    assertEquals("resourceLabel must not be null", exception.getMessage());
  }

  @Test
  void constructor_rejectsBlankLabel() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> new SqliteThreadOwner("   "));
    assertEquals("resourceLabel must not be blank", exception.getMessage());
  }

  @Test
  void requireOwnerThread_allowsTheOwnerThread() {
    SqliteThreadOwner owner = new SqliteThreadOwner("SQLite helper");
    assertDoesNotThrow(owner::requireOwnerThread);
  }

  @Test
  void requireOwnerThread_rejectsCrossThreadAccess()
      throws ExecutionException, InterruptedException {
    SqliteThreadOwner owner =
        createOnThread("sqlite-owner-helper", () -> new SqliteThreadOwner("SQLite helper"));
    IllegalStateException exception =
        captureIllegalStateOnThread("sqlite-reader-helper", owner::requireOwnerThread);
    assertEquals(
        "SQLite helper is thread-confined and is owned by thread 'sqlite-owner-helper' but was accessed from thread 'sqlite-reader-helper'.",
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
      throw new AssertionError("Expected cross-thread SQLite access to be rejected.");
    } catch (ExecutionException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof IllegalStateException illegalStateException) {
        return illegalStateException;
      }
      throw new AssertionError("Expected IllegalStateException but saw: " + cause, exception);
    }
  }
}
