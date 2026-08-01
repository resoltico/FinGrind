package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests for the SQLite FFM binding layer. */
class SqliteNativeInitializationFailureTest extends SqliteNativeBridgeTestSupport {
  @Test
  void nativeInitializationFailure_unwrapsIllegalStateCause() {
    UnsupportedSqliteVersionException cause =
        new UnsupportedSqliteVersionException("3.51.0", "3.53.4", "system");
    IllegalStateException exception =
        SqliteNativeBootstrap.nativeInitializationFailure(new ExceptionInInitializerError(cause));
    assertEquals(cause, exception);
  }

  @Test
  void nativeInitializationFailure_wrapsUnexpectedCause() {
    RuntimeException cause = new RuntimeException("boom");
    IllegalStateException exception =
        SqliteNativeBootstrap.nativeInitializationFailure(new ExceptionInInitializerError(cause));
    assertEquals("boom", exception.getMessage());
    assertEquals(cause, exception.getCause());
  }

  @Test
  void nativeInitializationFailure_wrapsInitializerErrorWhenCauseIsMissing() {
    ExceptionInInitializerError error = new ExceptionInInitializerError();
    IllegalStateException exception = SqliteNativeBootstrap.nativeInitializationFailure(error);
    assertEquals("Failed to initialize SQLite native library.", exception.getMessage());
    assertEquals(error, exception.getCause());
  }

  @Test
  void initialize_wrapsInitializerErrorsIntoStableIllegalStateExceptions() {
    RuntimeException cause = new RuntimeException("boom");
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteNativeBootstrap.initialize(
                    () -> {
                      throw new ExceptionInInitializerError(cause);
                    }));
    assertEquals("boom", exception.getMessage());
    assertEquals(cause, exception.getCause());
  }
}
