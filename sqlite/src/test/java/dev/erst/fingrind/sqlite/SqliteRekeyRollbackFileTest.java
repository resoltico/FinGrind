package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for rollback-copy lifecycle behavior around SQLite rekey attempts. */
@NullUnmarked
class SqliteRekeyRollbackFileTest {
  @TempDir Path tempDirectory;

  @Test
  void createRestoreAndDeleteQuietly_roundTripOneRollbackCopy() throws java.io.IOException {
    Path bookPath = tempDirectory.resolve("acme.sqlite");
    Files.writeString(bookPath, "original book bytes");

    SqliteRekeyRollbackFile rollbackFile = SqliteRekeyRollbackFile.create(bookPath);
    Path rollbackPath = rollbackFile.path();
    assertNotNull(rollbackPath);
    assertTrue(Files.exists(rollbackPath));

    Files.writeString(bookPath, "rotated book bytes");
    rollbackFile.restore(bookPath);
    assertEquals("original book bytes", Files.readString(bookPath));

    rollbackFile.deleteQuietly();
    assertFalse(Files.exists(rollbackPath));
  }

  @Test
  void createRejectsInvalidOrUnreadableBookPaths() {
    Path missingBookPath = tempDirectory.resolve("missing.sqlite");
    SqliteStorageFailureException missingBook =
        assertThrows(
            SqliteStorageFailureException.class,
            () -> SqliteRekeyRollbackFile.create(missingBookPath));
    assertTrue(missingBook.getMessage().contains("rekey rollback copy"));

    IllegalArgumentException rootPath =
        assertThrows(
            IllegalArgumentException.class,
            () -> SqliteRekeyRollbackFile.create(tempDirectory.getRoot()));
    assertTrue(rootPath.getMessage().contains("parent directory"));
  }

  @Test
  void restoreAndDeleteQuietly_preserveBestEffortFailureSemantics() throws java.io.IOException {
    Path bookPath = tempDirectory.resolve("book.sqlite");
    Files.writeString(bookPath, "original");

    SqliteRekeyRollbackFile rollbackFile = SqliteRekeyRollbackFile.create(bookPath);

    SqliteStorageFailureException restoreFailure =
        assertThrows(
            SqliteStorageFailureException.class, () -> rollbackFile.restore(tempDirectory));
    assertTrue(restoreFailure.getMessage().contains("rekey rollback copy"));

    Path nonEmptyDirectory = tempDirectory.resolve("rollback-dir");
    Files.createDirectories(nonEmptyDirectory);
    Files.writeString(nonEmptyDirectory.resolve("sentinel.txt"), "keep");

    SqliteRekeyRollbackFile directoryRollback = new SqliteRekeyRollbackFile(nonEmptyDirectory);
    assertDoesNotThrow(directoryRollback::deleteQuietly);
    assertTrue(Files.exists(nonEmptyDirectory));
  }
}
