package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link SqliteFileCleanup}. */
class SqliteFileCleanupTest {
  @TempDir Path tempDirectory;

  @Test
  void deleteQuietly_deletesExistingPathWithoutRaisingCleanupNoise() throws Exception {
    Path temporaryFile = Files.createFile(tempDirectory.resolve("maintenance.tmp"));

    assertDoesNotThrow(() -> SqliteFileCleanup.deleteQuietly(temporaryFile));

    assertFalse(Files.exists(temporaryFile));
  }

  @Test
  void deleteQuietly_reportsIoFailureWithoutDisplacingThePrimaryOutcome() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath failingPath =
          fileSystem
              .path("\\maintenance\\rollback.tmp")
              .failDeleteIfExistsWith(new IOException("cleanup"));
      failingPath.exists = true;
      failingPath.regularFile = true;
      AtomicReference<String> capturedAction = new AtomicReference<>("");
      AtomicReference<Exception> capturedException = new AtomicReference<>();

      assertDoesNotThrow(
          () ->
              SqliteFileCleanup.deleteQuietly(
                  failingPath,
                  (action, exception) -> {
                    capturedAction.set(action);
                    capturedException.set(exception);
                  }));

      assertEquals("deleting one temporary SQLite maintenance path", capturedAction.get());
      Exception exception = capturedException.get();
      assertNotNull(exception);
      assertEquals("cleanup", exception.getMessage());
      assertInstanceOf(IOException.class, exception);
      assertTrue(failingPath.existsValue());
    }
  }
}
