package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SqliteFailureClassifier}. */
@NullUnmarked
class SqliteFailureClassifierTest {
  @Test
  void classify_distinguishesManagedRuntimeStorageAndOtherFailures() {
    assertEquals(
        SqliteFailureClassifier.Category.MANAGED_RUNTIME,
        SqliteFailureClassifier.classify(
            new RuntimeException(
                new ManagedSqliteRuntimeUnavailableException("managed runtime unavailable"))));
    assertEquals(
        SqliteFailureClassifier.Category.MANAGED_RUNTIME,
        SqliteFailureClassifier.classify(
            new UnsupportedSqliteVersionException("3.45.0", "3.46.0", "bundle")));
    assertEquals(
        SqliteFailureClassifier.Category.MANAGED_RUNTIME,
        SqliteFailureClassifier.classify(
            new UnsupportedSqliteMultipleCiphersVersionException("2.1.0", "2.2.0", "bundle")));
    assertEquals(
        SqliteFailureClassifier.Category.MANAGED_RUNTIME,
        SqliteFailureClassifier.classify(
            new UnsupportedSqliteCompileOptionsException(
                "3.46.0", "2.2.0", "bundle", List.of("SQLITE_SECURE_DELETE"))));
    assertEquals(
        SqliteFailureClassifier.Category.MANAGED_RUNTIME,
        SqliteFailureClassifier.classify(
            new UnsupportedSqliteSourceIdException(
                "unexpected-source-id",
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                "bundle",
                "3.53.0",
                "2.3.3")));

    assertEquals(
        SqliteFailureClassifier.Category.STORAGE,
        SqliteFailureClassifier.classify(
            new RuntimeException(new SqliteStorageFailureException("storage failure"))));
    assertEquals(
        SqliteFailureClassifier.Category.STORAGE,
        SqliteFailureClassifier.classify(new SqliteNativeException(14, "unable to open file")));

    assertEquals(
        SqliteFailureClassifier.Category.OTHER,
        SqliteFailureClassifier.classify(new IllegalStateException("other failure")));
  }

  @Test
  void classify_requiresThrowableAndStorageExceptionPreservesCause() {
    assertThrows(NullPointerException.class, () -> SqliteFailureClassifier.classify(null));

    RuntimeException cause = new RuntimeException("disk");
    SqliteStorageFailureException exception =
        new SqliteStorageFailureException("storage failure", cause);

    assertEquals(
        "storage failure", new SqliteStorageFailureException("storage failure").getMessage());
    assertEquals("storage failure", exception.getMessage());
    assertSame(cause, exception.getCause());
  }
}
