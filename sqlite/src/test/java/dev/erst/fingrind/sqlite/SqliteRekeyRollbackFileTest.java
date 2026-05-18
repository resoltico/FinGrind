package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for rollback-copy lifecycle behavior around SQLite rekey attempts. */
class SqliteRekeyRollbackFileTest {
  private static final MethodHandle PUBLIC_PATH_HINT = rollbackFileHelper("publicPathHint");

  @TempDir Path tempDirectory;

  @BeforeEach
  void hardenTempDirectory() {
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(tempDirectory);
  }

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
    assertTrue(NullTestSupport.messageOf(missingBook).contains("rekey rollback copy"));
    IllegalArgumentException rootPath =
        assertThrows(
            IllegalArgumentException.class,
            () -> SqliteRekeyRollbackFile.create(tempDirectory.getRoot()));
    assertTrue(NullTestSupport.messageOf(rootPath).contains("parent directory"));
  }

  @Test
  void restoreAndDeleteQuietly_preserveBestEffortFailureSemantics() throws java.io.IOException {
    Path bookPath = tempDirectory.resolve("book.sqlite");
    Files.writeString(bookPath, "original");
    SqliteRekeyRollbackFile rollbackFile = SqliteRekeyRollbackFile.create(bookPath);
    SqliteStorageFailureException restoreFailure =
        assertThrows(
            SqliteStorageFailureException.class, () -> rollbackFile.restore(tempDirectory));
    assertTrue(NullTestSupport.messageOf(restoreFailure).contains("rekey rollback copy"));
    Path nonEmptyDirectory = tempDirectory.resolve("rollback-dir");
    Files.createDirectories(nonEmptyDirectory);
    Files.writeString(nonEmptyDirectory.resolve("sentinel.txt"), "keep");
    SqliteRekeyRollbackFile directoryRollback = new SqliteRekeyRollbackFile(nonEmptyDirectory);
    assertDoesNotThrow(directoryRollback::deleteQuietly);
    assertTrue(Files.exists(nonEmptyDirectory));
  }

  @Test
  void findAndReportStaleRollbackArtifacts_matchOnlySameBookArtifacts() throws Exception {
    Path bookPath = tempDirectory.resolve("acme.sqlite");
    Files.writeString(bookPath, "book");
    Path siblingArtifact = tempDirectory.resolve("acme.sqlite.rekey-rollback-001.sqlite");
    Path laterArtifact = tempDirectory.resolve("acme.sqlite.rekey-rollback-002.sqlite");
    Path wrongSuffixArtifact = tempDirectory.resolve("acme.sqlite.rekey-rollback-003.tmp");
    Path otherBookArtifact = tempDirectory.resolve("other.sqlite.rekey-rollback-001.sqlite");
    Path unrelatedFile = tempDirectory.resolve("acme.sqlite.backup");
    Files.writeString(siblingArtifact, "ciphertext");
    Files.writeString(laterArtifact, "ciphertext");
    Files.writeString(wrongSuffixArtifact, "ciphertext");
    Files.writeString(otherBookArtifact, "other ciphertext");
    Files.writeString(unrelatedFile, "ignore");
    assertIterableEquals(
        List.of(
            siblingArtifact.toAbsolutePath().normalize(),
            laterArtifact.toAbsolutePath().normalize()),
        SqliteRekeyRollbackFile.findStaleRollbackArtifacts(bookPath));
    AtomicReference<List<Path>> reportedArtifacts = new AtomicReference<>();
    AtomicReference<Path> reportedBookPath = new AtomicReference<>();
    AtomicReference<java.io.IOException> scanFailure = new AtomicReference<>();
    SqliteRekeyRollbackFile.reportStaleRollbackArtifacts(
        bookPath,
        (normalizedBookPath, rollbackArtifacts) -> {
          reportedBookPath.set(normalizedBookPath);
          reportedArtifacts.set(rollbackArtifacts);
        },
        (normalizedBookPath, exception) -> scanFailure.set(exception));
    assertEquals(bookPath, reportedBookPath.get());
    assertIterableEquals(
        List.of(
            siblingArtifact.toAbsolutePath().normalize(),
            laterArtifact.toAbsolutePath().normalize()),
        reportedArtifacts.get());
    assertNull(scanFailure.get());
    assertDoesNotThrow(() -> SqliteRekeyRollbackFile.reportStaleRollbackArtifacts(bookPath));
  }

  @Test
  void findStaleRollbackArtifacts_returnsEmptyWhenParentIsNotDirectory() throws Exception {
    Path parentFile = tempDirectory.resolve("not-a-directory");
    Files.writeString(parentFile, "file");
    assertEquals(
        List.of(),
        SqliteRekeyRollbackFile.findStaleRollbackArtifacts(parentFile.resolve("book.sqlite")));
  }

  @Test
  void isRollbackArtifactForBook_requiresSameParentDirectoryAndCanonicalName() throws Exception {
    Path bookPath = tempDirectory.resolve("acme.sqlite");
    Files.writeString(bookPath, "book");
    Path validArtifact = tempDirectory.resolve("acme.sqlite.rekey-rollback-001.sqlite");
    Path wrongParent =
        tempDirectory.resolve("nested").resolve("acme.sqlite.rekey-rollback-001.sqlite");
    Path wrongSuffix = tempDirectory.resolve("acme.sqlite.rekey-rollback-001.tmp");
    Files.createDirectories(wrongParent.getParent());

    assertTrue(SqliteRekeyRollbackFile.isRollbackArtifactForBook(bookPath, validArtifact));
    assertFalse(SqliteRekeyRollbackFile.isRollbackArtifactForBook(bookPath, wrongParent));
    assertFalse(SqliteRekeyRollbackFile.isRollbackArtifactForBook(bookPath, wrongSuffix));
  }

  @Test
  void publicPathHint_redactsRootPathsWithoutAFileName() {
    assertEquals("<redacted>", publicPathHint(tempDirectory.getRoot()));
  }

  @Test
  void reportStaleRollbackArtifacts_skipsReporterWhenNoArtifactsExist() throws Exception {
    Path bookPath = tempDirectory.resolve("empty.sqlite");
    Files.writeString(bookPath, "book");
    AtomicReference<Path> reportedBookPath = new AtomicReference<>();
    AtomicReference<List<Path>> reportedArtifacts = new AtomicReference<>();
    AtomicReference<java.io.IOException> scanFailure = new AtomicReference<>();
    SqliteRekeyRollbackFile.reportStaleRollbackArtifacts(
        bookPath,
        (normalizedBookPath, rollbackArtifacts) -> {
          reportedBookPath.set(normalizedBookPath);
          reportedArtifacts.set(rollbackArtifacts);
        },
        (normalizedBookPath, exception) -> scanFailure.set(exception));
    assertNull(reportedBookPath.get());
    assertNull(reportedArtifacts.get());
    assertNull(scanFailure.get());
  }

  @Test
  void reportStaleRollbackArtifacts_reportsDirectoryScanFailuresWithoutHostSpecificPermissions() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.failNewDirectoryStreamWith(new java.io.IOException("scan-boom"));
      AclFixturePath bookPath = fileSystem.path("\\books\\acme.sqlite");
      bookPath.exists = true;
      bookPath.regularFile = true;

      AtomicReference<Path> reportedBookPath = new AtomicReference<>();
      AtomicReference<java.io.IOException> reportedFailure = new AtomicReference<>();
      assertDoesNotThrow(
          () ->
              SqliteRekeyRollbackFile.reportStaleRollbackArtifacts(
                  bookPath,
                  (normalizedBookPath, rollbackArtifacts) -> {
                    throw new AssertionError(
                        "unexpected stale artifact report for " + normalizedBookPath);
                  },
                  (normalizedBookPath, exception) -> {
                    reportedBookPath.set(normalizedBookPath);
                    reportedFailure.set(exception);
                  }));

      assertEquals(bookPath, reportedBookPath.get());
      assertEquals("scan-boom", NullTestSupport.messageOf(reportedFailure.get()));
    }
  }

  @Test
  void reportStaleRollbackArtifacts_logsDirectoryScanFailuresWithoutThrowing() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.failNewDirectoryStreamWith(new java.io.IOException("scan-boom"));
      AclFixturePath bookPath = fileSystem.path("\\books\\acme.sqlite");
      bookPath.exists = true;
      bookPath.regularFile = true;

      assertDoesNotThrow(() -> SqliteRekeyRollbackFile.reportStaleRollbackArtifacts(bookPath));
    }
  }

  @Test
  void reportStaleRollbackArtifacts_logsAndSuppressesDirectoryScanFailures() throws Exception {
    Path lockedDirectory = tempDirectory.resolve("locked");
    Files.createDirectories(lockedDirectory);
    Path bookPath = lockedDirectory.resolve("acme.sqlite");
    Files.writeString(bookPath, "book");
    Assumptions.assumeTrue(Files.getFileStore(lockedDirectory).supportsFileAttributeView("posix"));
    Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(lockedDirectory);
    Files.setPosixFilePermissions(
        lockedDirectory,
        Set.of(PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
    try {
      AtomicReference<Path> reportedBookPath = new AtomicReference<>();
      AtomicReference<java.io.IOException> reportedFailure = new AtomicReference<>();
      assertDoesNotThrow(
          () ->
              SqliteRekeyRollbackFile.reportStaleRollbackArtifacts(
                  bookPath,
                  (normalizedBookPath, rollbackArtifacts) -> {
                    throw new AssertionError(
                        "unexpected stale artifact report for " + normalizedBookPath);
                  },
                  (normalizedBookPath, exception) -> {
                    reportedBookPath.set(normalizedBookPath);
                    reportedFailure.set(exception);
                  }));
      assertEquals(bookPath, reportedBookPath.get());
      assertNotNull(reportedFailure.get());
      assertDoesNotThrow(() -> SqliteRekeyRollbackFile.reportStaleRollbackArtifacts(bookPath));
    } finally {
      Files.setPosixFilePermissions(lockedDirectory, originalPermissions);
    }
  }

  private static String publicPathHint(Path path) {
    try {
      return (String) PUBLIC_PATH_HINT.invokeExact(path);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError("Failed to invoke SQLite rollback-file helper.", throwable);
    }
  }

  private static MethodHandle rollbackFileHelper(String methodName) {
    try {
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(SqliteRekeyRollbackFile.class, MethodHandles.lookup());
      return lookup.findStatic(
          SqliteRekeyRollbackFile.class,
          methodName,
          MethodType.methodType(String.class, Path.class));
    } catch (IllegalAccessException | NoSuchMethodException exception) {
      throw new LinkageError(
          "Failed to bind SQLite rollback-file helper: " + methodName, exception);
    }
  }
}
