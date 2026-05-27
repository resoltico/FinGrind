package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Focused tests for managed-library verification snapshots. */
class SqliteManagedLibrarySnapshotTest extends SqliteManagedLibraryIdentityTestSupport {
  @Test
  void verifiedSnapshot_copiesManagedLibraryIntoPrivateVerifiedPath() throws Exception {
    Path libraryPath = copyHostManagedLibrary();
    writeSiblingChecksum(libraryPath);
    writeTrustedChecksum(libraryPath);

    SqliteVerifiedLibrarySnapshot snapshot =
        SqliteManagedLibraryIdentity.verifiedSnapshot(
            new SqliteLibraryTarget(
                "managed-only",
                SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                libraryPath.toString()));

    assertTrue(snapshot.snapshotLibraryPath().startsWith(snapshot.snapshotDirectory()));
    assertTrue(snapshot.snapshotChecksumPath().startsWith(snapshot.snapshotDirectory()));
    assertTrue(Files.isRegularFile(snapshot.snapshotLibraryPath()));
    assertTrue(Files.isRegularFile(snapshot.snapshotChecksumPath()));
    assertArrayEquals(
        Files.readAllBytes(libraryPath), Files.readAllBytes(snapshot.snapshotLibraryPath()));
    assertEquals(
        Files.readString(
            SqliteManagedLibraryIdentity.checksumPath(libraryPath), StandardCharsets.UTF_8),
        Files.readString(snapshot.snapshotChecksumPath(), StandardCharsets.UTF_8));
    assertEquals(
        Files.readString(
            SqliteManagedLibraryIdentity.trustedChecksumPath(libraryPath), StandardCharsets.UTF_8),
        Files.readString(
            Objects.requireNonNull(snapshot.snapshotTrustedChecksumPath()),
            StandardCharsets.UTF_8));
    assertEquals(
        snapshot.snapshotLibraryPath().toString(), snapshot.runtimeTarget().lookupTarget());

    snapshot.deleteQuietly();
  }

  @Test
  void verifiedSnapshot_rejectsUnusableSnapshotTempRoot() throws Exception {
    Path libraryPath = writeLibrary("libsqlite3.so.0", "sqlite3mc");
    writeSiblingChecksum(libraryPath);
    String originalTempRoot = System.getProperty("java.io.tmpdir");
    String missingTempRoot = tempDirectory.resolve("missing-temp-root").toString();
    System.setProperty("java.io.tmpdir", missingTempRoot);
    try {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteVerifiedLibrarySnapshot.copyOf(
                      new SqliteLibraryTarget(
                          "managed-only",
                          SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                          libraryPath.toString()),
                      libraryPath,
                      SqliteManagedLibraryIdentity.checksumPath(libraryPath),
                      null));

      assertTrue(
          Objects.requireNonNull(exception.getMessage())
              .contains(
                  "Failed to create a private managed SQLite verification snapshot directory."));
    } finally {
      System.setProperty("java.io.tmpdir", originalTempRoot);
    }
  }

  @Test
  void createPrivateSnapshotDirectory_supportsNonPosixFallbackCreation() throws Exception {
    Path snapshotDirectory =
        SqliteManagedLibraryIdentity.createPrivateSnapshotDirectory(tempDirectory, false);

    assertTrue(snapshotDirectory.startsWith(tempDirectory));
    assertTrue(Files.isDirectory(snapshotDirectory));
    Files.delete(snapshotDirectory);
  }

  @Test
  void createPrivateSnapshotDirectory_supportsPosixOwnerOnlyCreation() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath tempRoot = fileSystem.path("\\tmp");
      tempRoot.exists = true;
      tempRoot.regularFile = false;

      Path snapshotDirectory =
          SqliteManagedLibraryIdentity.createPrivateSnapshotDirectory(tempRoot, true);

      assertTrue(Files.isDirectory(snapshotDirectory));
      assertEquals(
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE),
          ((AclFixturePath) snapshotDirectory).posixPermissions);
    }
  }

  @Test
  void verifiedSnapshot_recordRejectsSnapshotPathsOutsideSnapshotDirectory() throws Exception {
    Path snapshotDirectory = Files.createDirectory(tempDirectory.resolve("snapshot"));
    Path outsideLibraryPath = tempDirectory.resolve("outside-library.dylib");
    Path outsideChecksumPath = tempDirectory.resolve("outside-library.dylib.sha256");
    Path validLibraryPath = snapshotDirectory.resolve("library.dylib");
    Path validChecksumPath = snapshotDirectory.resolve("library.dylib.sha256");
    Files.writeString(outsideLibraryPath, "library", StandardCharsets.UTF_8);
    Files.writeString(outsideChecksumPath, "checksum", StandardCharsets.UTF_8);
    Files.writeString(validLibraryPath, "library", StandardCharsets.UTF_8);
    Files.writeString(validChecksumPath, "checksum", StandardCharsets.UTF_8);
    SqliteLibraryTarget sourceTarget =
        new SqliteLibraryTarget(
            "managed-only",
            SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
            validLibraryPath.toString());

    IllegalArgumentException outsideLibrary =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new SqliteVerifiedLibrarySnapshot(
                    sourceTarget, snapshotDirectory, outsideLibraryPath, validChecksumPath, null));
    IllegalArgumentException outsideChecksum =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new SqliteVerifiedLibrarySnapshot(
                    sourceTarget, snapshotDirectory, validLibraryPath, outsideChecksumPath, null));
    IllegalArgumentException outsideTrustedChecksum =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new SqliteVerifiedLibrarySnapshot(
                    sourceTarget,
                    snapshotDirectory,
                    validLibraryPath,
                    validChecksumPath,
                    outsideChecksumPath));

    assertEquals(
        "snapshotLibraryPath must live inside snapshotDirectory.", outsideLibrary.getMessage());
    assertEquals(
        "snapshotChecksumPath must live inside snapshotDirectory.", outsideChecksum.getMessage());
    assertEquals(
        "snapshotTrustedChecksumPath must live inside snapshotDirectory.",
        outsideTrustedChecksum.getMessage());
  }

  @Test
  void verifiedSnapshot_copyOfCleansUpAfterCopyFailures() throws Exception {
    Path libraryPath = writeLibrary("libsqlite3.so.0", "sqlite3mc");
    Path missingChecksumPath = tempDirectory.resolve("missing.sha256");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteVerifiedLibrarySnapshot.copyOf(
                    new SqliteLibraryTarget(
                        "managed-only",
                        SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                        libraryPath.toString()),
                    libraryPath,
                    missingChecksumPath,
                    null));

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("Failed to create the private managed SQLite verification snapshot"));
  }

  @Test
  void verifiedSnapshot_copyOf_supportsSnapshotsWithoutTrustedChecksumSidecars() throws Exception {
    Path libraryPath = writeLibrary("untrusted-snapshot.dylib", "sqlite3mc");
    writeSiblingChecksum(libraryPath);

    SqliteVerifiedLibrarySnapshot snapshot =
        SqliteVerifiedLibrarySnapshot.copyOf(
            new SqliteLibraryTarget(
                "managed-only",
                SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                libraryPath.toString()),
            libraryPath,
            SqliteManagedLibraryIdentity.checksumPath(libraryPath),
            null);

    assertNull(snapshot.snapshotTrustedChecksumPath());
    assertEquals(
        snapshot.snapshotLibraryPath().toString(), snapshot.runtimeTarget().lookupTarget());
    snapshot.deleteQuietly();
    assertFalse(Files.exists(snapshot.snapshotLibraryPath()));
    assertFalse(Files.exists(snapshot.snapshotChecksumPath()));
  }

  @Test
  void verifiedSnapshot_copyOfCleansUpTrustedChecksumArtifactsAfterTrustedCopyFailures()
      throws Exception {
    Path libraryPath = writeLibrary(hostManagedLibraryFileName(), "sqlite3mc");
    Path checksumPath = SqliteManagedLibraryIdentity.checksumPath(libraryPath);
    Path missingTrustedChecksumPath = tempDirectory.resolve("missing.trusted.sha256");
    writeSiblingChecksum(libraryPath);
    String originalTempRoot = System.getProperty("java.io.tmpdir");
    System.setProperty("java.io.tmpdir", tempDirectory.toString());
    try {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteVerifiedLibrarySnapshot.copyOf(
                      new SqliteLibraryTarget(
                          "managed-only",
                          SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                          libraryPath.toString()),
                      libraryPath,
                      checksumPath,
                      missingTrustedChecksumPath));

      assertTrue(
          Objects.requireNonNull(exception.getMessage())
              .contains("Failed to create the private managed SQLite verification snapshot"));
      try (var snapshotPaths = Files.list(tempDirectory)) {
        assertTrue(
            snapshotPaths.noneMatch(
                path -> path.getFileName().toString().startsWith("fingrind-managed-sqlite-")));
      }
    } finally {
      System.setProperty("java.io.tmpdir", originalTempRoot);
    }
  }

  @Test
  void verifiedSnapshot_copyOfWithInjectedSnapshotDirectory_cleansUpAfterChecksumCopyFailures() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath sourceLibraryPath = fileSystem.path("\\source\\sqlite3.dll");
      sourceLibraryPath.exists = true;
      sourceLibraryPath.regularFile = true;
      AclFixturePath sourceChecksumPath = fileSystem.path("\\source\\sqlite3.dll.sha256");
      AclFixturePath snapshotDirectory = fileSystem.path("\\snapshots");
      snapshotDirectory.exists = true;
      snapshotDirectory.regularFile = false;

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteVerifiedLibrarySnapshot.copyOf(
                      new SqliteLibraryTarget(
                          "managed-only",
                          SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                          sourceLibraryPath.toString()),
                      sourceLibraryPath,
                      sourceChecksumPath,
                      null,
                      () -> snapshotDirectory));

      assertTrue(
          Objects.requireNonNull(exception.getMessage())
              .contains("Failed to create the private managed SQLite verification snapshot"));
      assertFalse(snapshotDirectory.exists);
      assertFalse(fileSystem.path("\\snapshots\\sqlite3.dll").exists);
      assertFalse(fileSystem.path("\\snapshots\\sqlite3.dll.sha256").exists);
    }
  }

  @Test
  void verifiedSnapshot_copyOfWithInjectedSnapshotDirectory_cleansUpTrustedArtifactsOnFailure() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath sourceLibraryPath = fileSystem.path("\\source\\sqlite3.dll");
      sourceLibraryPath.exists = true;
      sourceLibraryPath.regularFile = true;
      AclFixturePath sourceChecksumPath = fileSystem.path("\\source\\sqlite3.dll.sha256");
      sourceChecksumPath.exists = true;
      sourceChecksumPath.regularFile = true;
      AclFixturePath sourceTrustedChecksumPath =
          fileSystem.path("\\source\\sqlite3.dll.trusted.sha256");
      AclFixturePath snapshotDirectory = fileSystem.path("\\snapshots");
      snapshotDirectory.exists = true;
      snapshotDirectory.regularFile = false;

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteVerifiedLibrarySnapshot.copyOf(
                      new SqliteLibraryTarget(
                          "managed-only",
                          SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                          sourceLibraryPath.toString()),
                      sourceLibraryPath,
                      sourceChecksumPath,
                      sourceTrustedChecksumPath,
                      () -> snapshotDirectory));

      assertTrue(
          Objects.requireNonNull(exception.getMessage())
              .contains("Failed to create the private managed SQLite verification snapshot"));
      assertFalse(snapshotDirectory.exists);
      assertFalse(fileSystem.path("\\snapshots\\sqlite3.dll").exists);
      assertFalse(fileSystem.path("\\snapshots\\sqlite3.dll.sha256").exists);
      assertFalse(fileSystem.path("\\snapshots\\sqlite3.dll.trusted.sha256").exists);
    }
  }

  @Test
  void verifiedSnapshot_deleteQuietly_ignoresDirectoryDeleteFailures() throws Exception {
    Path snapshotDirectory = Files.createDirectory(tempDirectory.resolve("snapshot-cleanup"));
    Path snapshotLibraryPath = snapshotDirectory.resolve("library.dylib");
    Path snapshotChecksumPath = snapshotDirectory.resolve("library.dylib.sha256");
    Path snapshotTrustedChecksumPath = snapshotDirectory.resolve("library.dylib.trusted.sha256");
    Path sentinel = snapshotDirectory.resolve("sentinel.txt");
    Files.writeString(snapshotLibraryPath, "library", StandardCharsets.UTF_8);
    Files.writeString(snapshotChecksumPath, "checksum", StandardCharsets.UTF_8);
    Files.writeString(snapshotTrustedChecksumPath, "trusted", StandardCharsets.UTF_8);
    Files.writeString(sentinel, "keep", StandardCharsets.UTF_8);
    SqliteVerifiedLibrarySnapshot snapshot =
        new SqliteVerifiedLibrarySnapshot(
            new SqliteLibraryTarget(
                "managed-only",
                SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                snapshotLibraryPath.toString()),
            snapshotDirectory,
            snapshotLibraryPath,
            snapshotChecksumPath,
            snapshotTrustedChecksumPath);

    assertDoesNotThrow(snapshot::deleteQuietly);
    assertTrue(Files.exists(snapshotDirectory));
    assertTrue(Files.exists(sentinel));
    assertTrue(Files.notExists(snapshotLibraryPath));
    assertTrue(Files.notExists(snapshotChecksumPath));
    assertTrue(Files.notExists(snapshotTrustedChecksumPath));
  }
}
