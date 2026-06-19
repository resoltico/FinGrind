package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SqliteFailureClassifier}. */
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
            new UnsupportedManagedSqliteLibraryIdentityException(
                java.nio.file.Path.of("/tmp/libsqlite3.so.0"),
                "sibling SHA-256 file /tmp/libsqlite3.so.0.sha256",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")));
    assertEquals(
        SqliteFailureClassifier.Category.MANAGED_RUNTIME,
        SqliteFailureClassifier.classify(
            new UnsupportedSqliteSourceIdException(
                "unexpected-source-id",
                SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                "bundle",
                "3.53.2",
                "2.3.5")));
    assertEquals(
        SqliteFailureClassifier.Category.PERSISTENCE_INVARIANT,
        SqliteFailureClassifier.classify(
            new RuntimeException(
                new SqlitePersistenceInvariantException("deterministic invariant leaked"))));
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
    assertThrows(
        NullPointerException.class,
        () -> SqliteFailureClassifier.classify(NullTestSupport.nullOf(Throwable.class)));
    RuntimeException cause = new RuntimeException("disk");
    SqliteStorageFailureException exception =
        new SqliteStorageFailureException("storage failure", cause);
    assertEquals(
        "storage failure", new SqliteStorageFailureException("storage failure").getMessage());
    assertEquals("storage failure", exception.getMessage());
    assertSame(cause, exception.getCause());
  }

  @Test
  void managedRuntimeUnavailableException_withCause_preservesMessageAndClassification() {
    RuntimeException cause = new RuntimeException("boom");

    ManagedSqliteRuntimeUnavailableException exception =
        new ManagedSqliteRuntimeUnavailableException("managed runtime unavailable", cause);

    assertEquals("managed runtime unavailable", exception.getMessage());
    assertSame(cause, exception.getCause());
    assertSame(
        SqliteFailureClassifier.Category.MANAGED_RUNTIME,
        SqliteFailureClassifier.classify(exception));
  }
}
