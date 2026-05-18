package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Focused coverage tests for SQLite maintenance helpers and failure-path support seams. */
class SqliteMaintenanceSupportCoverageTest {
  private static final MethodHandle MOVE_REPLACING = maintenanceFilesHelper("moveReplacing");

  @TempDir Path tempDirectory;

  @Test
  void blockingArtifactsForBook_wrapsDirectoryScanFailures() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.failNewDirectoryStreamWith(new IOException("scan-boom"));
      AclFixturePath bookPath = fileSystem.path("\\books\\book.sqlite");
      bookPath.exists = true;
      bookPath.regularFile = true;

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookMaintenanceFiles.blockingArtifactsForBook(bookPath));

      assertTrue(NullTestSupport.messageOf(exception).contains("maintenance sidecars"));
      assertEquals("scan-boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
    }
  }

  @Test
  void copyFreshBook_wrapsSourceCopyFailures() {
    Path missingSource = tempDirectory.resolve("missing.sqlite");
    Path targetPath = tempDirectory.resolve("backup").resolve("copy.sqlite");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteBookMaintenanceFiles.copyFreshBook(missingSource, targetPath));

    assertTrue(NullTestSupport.messageOf(exception).contains("Failed to copy"));
    assertFalse(java.nio.file.Files.exists(targetPath));
  }

  @Test
  void replaceBook_cleansStagedCopiesWhenRestoreFails() throws Exception {
    Path missingSource = tempDirectory.resolve("missing.sqlite");
    Path targetPath = tempDirectory.resolve("restore").resolve("book.sqlite");
    Path targetParent = java.util.Objects.requireNonNull(targetPath.getParent(), "targetParent");
    java.nio.file.Files.createDirectories(targetParent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(targetParent);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteBookMaintenanceFiles.replaceBook(missingSource, targetPath));

    assertTrue(NullTestSupport.messageOf(exception).contains("Failed to restore"));
    try (java.util.stream.Stream<Path> children = java.nio.file.Files.list(targetParent)) {
      assertEquals(
          0L,
          children
              .filter(path -> path.getFileName().toString().startsWith("book.sqlite.restore-"))
              .count());
    }
  }

  @Test
  void deleteRollbackArtifact_wrapsDeleteFailures() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath rollbackPath = fileSystem.path("\\books\\book.rollback.sqlite");
      rollbackPath.exists = true;
      rollbackPath.regularFile = true;
      rollbackPath.failDeleteIfExistsWith(new IOException("delete-boom"));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookMaintenanceFiles.deleteRollbackArtifact(rollbackPath));

      assertTrue(NullTestSupport.messageOf(exception).contains("rollback artifact"));
      assertEquals("delete-boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
    }
  }

  @Test
  void moveReplacing_fallsBackWhenAtomicMoveIsUnavailable() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath sourcePath = fileSystem.path("\\books\\staged.tmp");
      sourcePath.exists = true;
      sourcePath.regularFile = true;
      AclFixturePath targetPath = fileSystem.path("\\books\\book.sqlite");
      sourcePath.failMoveWith(
          new AtomicMoveNotSupportedException(
              sourcePath.toString(), targetPath.toString(), "atomic-unsupported"));

      invokeMoveReplacing(sourcePath, targetPath);

      assertFalse(sourcePath.exists);
      assertTrue(targetPath.exists);
      assertTrue(targetPath.regularFile);
    }
  }

  @Test
  void materialize_rejectsExistingKeyFiles() {
    Path keyFilePath = tempDirectory.resolve("existing.key");
    assertDoesNotThrowIo(() -> java.nio.file.Files.writeString(keyFilePath, "existing"));
    try (SqliteBookPassphrase bookPassphrase =
        SqliteBookPassphrase.fromUtf8Bytes(
            "existing-key", "secret".getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> SqliteBookKeyFileMaterializer.materialize(keyFilePath, bookPassphrase));
      assertTrue(NullTestSupport.messageOf(exception).contains("already exists"));
    }
  }

  @Test
  void materialize_deletesCreatedKeyFilesWhenWriteFails() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parentPath = fileSystem.path("\\keys");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE);
      AclFixturePath keyFilePath = fileSystem.path("\\keys\\backup.key");
      keyFilePath.failNewByteChannelAfter(1, new IOException("write-boom"));

      try (SqliteBookPassphrase bookPassphrase =
          SqliteBookPassphrase.fromUtf8Bytes(
              "materialize-failure", "secret".getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () -> SqliteBookKeyFileMaterializer.materialize(keyFilePath, bookPassphrase));

        assertTrue(NullTestSupport.messageOf(exception).contains("backup key file"));
        assertEquals("write-boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
        assertFalse(keyFilePath.exists);
      }
    }
  }

  @Test
  void materialize_leavesNoArtifactWhenSecureFileCreationFailsBeforeCreationCompletes() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parentPath = fileSystem.path("\\keys");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE);
      AclFixturePath keyFilePath = fileSystem.path("\\keys\\create-failure.key");
      keyFilePath.failNewByteChannelWith(new IOException("create-boom"));

      try (SqliteBookPassphrase bookPassphrase =
          SqliteBookPassphrase.fromUtf8Bytes(
              "materialize-create-failure",
              "secret".getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () -> SqliteBookKeyFileMaterializer.materialize(keyFilePath, bookPassphrase));

        assertTrue(NullTestSupport.messageOf(exception).contains("backup key file"));
        assertEquals("create-boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
        assertFalse(keyFilePath.exists);
      }
    }
  }

  @Test
  void canonicalSchemaManifest_rejectsSchemasWithoutObjects() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteCanonicalSchemaManifest.parseObjectNames("-- no schema objects here"));

    assertEquals(
        "SQLite canonical schema manifest found no schema objects.", exception.getMessage());
  }

  private static void invokeMoveReplacing(Path sourcePath, Path targetPath) {
    try {
      MOVE_REPLACING.invokeExact(sourcePath, targetPath);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError("Failed to invoke SQLite maintenance move helper.", throwable);
    }
  }

  private static MethodHandle maintenanceFilesHelper(String methodName) {
    try {
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(SqliteBookMaintenanceFiles.class, MethodHandles.lookup());
      return lookup.findStatic(
          SqliteBookMaintenanceFiles.class,
          methodName,
          MethodType.methodType(void.class, Path.class, Path.class));
    } catch (IllegalAccessException | NoSuchMethodException exception) {
      throw new LinkageError("Failed to bind SQLite maintenance helper: " + methodName, exception);
    }
  }

  private static void assertDoesNotThrowIo(IoAction action) {
    try {
      action.run();
    } catch (IOException exception) {
      throw new IllegalStateException("Unexpected I/O failure in test setup.", exception);
    }
  }

  /** Minimal checked-I/O action used by local test setup helpers. */
  @FunctionalInterface
  private interface IoAction {
    void run() throws IOException;
  }
}
