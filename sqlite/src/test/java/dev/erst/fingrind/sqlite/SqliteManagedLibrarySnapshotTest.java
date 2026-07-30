package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import dev.erst.fingrind.core.PrivateOutputDirectory;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Focused tests for managed-library verification snapshots. */
class SqliteManagedLibrarySnapshotTest extends SqliteManagedLibraryIdentityTestSupport {
  @Test
  void verifiedSnapshot_copiesManagedLibraryIntoRetainedPrivateVerifiedPath() throws Exception {
    Path libraryPath = copyHostManagedLibrary();
    writeSiblingChecksum(libraryPath);

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
        snapshot.snapshotLibraryPath().toString(), snapshot.runtimeTarget().lookupTarget());
    assertTrue(Files.isDirectory(snapshot.snapshotDirectory()));
  }

  @Test
  void verifiedSnapshot_rejectsUnusableSnapshotTempRoot() throws Exception {
    Path libraryPath = writeLibrary("libsqlite3.so.0", "sqlite3mc");
    writeSiblingChecksum(libraryPath);
    String originalTempRoot = System.getProperty("java.io.tmpdir");
    String missingTempRoot = tempDirectory.resolve("missing-temp-root").toString();
    System.setProperty("java.io.tmpdir", missingTempRoot);
    try {
      ManagedSqliteRuntimeUnavailableException exception =
          assertThrows(
              ManagedSqliteRuntimeUnavailableException.class,
              () ->
                  SqliteVerifiedLibrarySnapshot.copyOf(
                      new SqliteLibraryTarget(
                          "managed-only",
                          SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                          libraryPath.toString()),
                      libraryPath,
                      SqliteManagedLibraryIdentity.checksumPath(libraryPath)));

      assertTrue(
          Objects.requireNonNull(exception.getMessage())
              .contains(
                  "could not resolve the managed SQLite verification snapshot temporary directory"));
    } finally {
      System.setProperty("java.io.tmpdir", originalTempRoot);
    }
  }

  @Test
  void createPrivateSnapshotDirectory_reportsAnAllocationFailureWithoutCreatingAnyPath() {
    ManagedSqliteRuntimeUnavailableException exception =
        assertThrows(
            ManagedSqliteRuntimeUnavailableException.class,
            () ->
                SqliteManagedLibraryIdentity.createPrivateSnapshotDirectory(
                    tempDirectory,
                    (parentDirectory, namePrefix) -> {
                      throw new IOException("injected private-directory refusal");
                    }));

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("could not create a private managed SQLite verification snapshot directory"));
  }

  @Test
  void createPrivateSnapshotDirectory_supportsPosixOwnerOnlyCreation() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath tempRoot = fileSystem.path("\\tmp");
      tempRoot.exists = true;
      tempRoot.regularFile = false;

      Path snapshotDirectory =
          SqliteManagedLibraryIdentity.createPrivateSnapshotDirectory(
              tempRoot, SqliteManagedLibrarySnapshotTest::createPosixSnapshotDirectory);

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
  void createPrivateSnapshotDirectory_reportsItsSelectedTempRootWhenPrivateCreationFails()
      throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath tempRoot = fileSystem.path("\\snapshot-temp");
      tempRoot.exists = true;
      tempRoot.regularFile = false;
      fileSystem.onPathCreated(
          path ->
              path.failCreateDirectoryWith(new IOException("injected snapshot directory refusal")));

      ManagedSqliteRuntimeUnavailableException failure =
          assertThrows(
              ManagedSqliteRuntimeUnavailableException.class,
              () ->
                  SqliteManagedLibrarySnapshotSecurity.createPrivateSnapshotDirectory(
                      tempRoot, SqliteManagedLibrarySnapshotTest::createPosixSnapshotDirectory));

      assertTrue(
          Objects.requireNonNull(failure.getMessage())
              .contains(
                  "could not create a private managed SQLite verification snapshot directory"));
      assertInstanceOf(IOException.class, failure.getCause());
    }
  }

  @Test
  void createPrivateSnapshotDirectory_refusesAnUnsupportedPrivateDirectoryCreationPrimitive()
      throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath tempRoot = fileSystem.path("\\unsupported-snapshot-temp");
      tempRoot.exists = true;
      tempRoot.regularFile = false;
      fileSystem.onPathCreated(
          path ->
              path.failCreateDirectoryWithUnsupportedOperation(
                  new UnsupportedOperationException("injected unsupported private directory")));

      ManagedSqliteRuntimeUnavailableException failure =
          assertThrows(
              ManagedSqliteRuntimeUnavailableException.class,
              () ->
                  SqliteManagedLibrarySnapshotSecurity.createPrivateSnapshotDirectory(
                      tempRoot, SqliteManagedLibrarySnapshotTest::createPosixSnapshotDirectory));

      assertTrue(
          Objects.requireNonNull(failure.getMessage())
              .contains(
                  "could not create a private managed SQLite verification snapshot directory"));
      assertInstanceOf(UnsupportedOperationException.class, failure.getCause());
    }
  }

  @Test
  void openNewPrivateSnapshotChannel_reportsTheFilesystemProbeFailure() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parent = fileSystem.path("\\unreadable-snapshot-parent");
      parent.exists = true;
      parent.regularFile = false;
      Path snapshotPath = fileSystem.path("\\unreadable-snapshot-parent\\sqlite3.dll");
      IOException probeFailure = new IOException("injected filesystem probe failure");
      fileSystem.failFileStoreWith(probeFailure);

      ManagedSqliteRuntimeUnavailableException failure =
          assertThrows(
              ManagedSqliteRuntimeUnavailableException.class,
              () ->
                  SqliteManagedLibrarySnapshotSecurity.openNewPrivateSnapshotChannel(snapshotPath));

      assertTrue(
          Objects.requireNonNull(failure.getMessage())
              .contains(
                  "could not atomically create a private managed SQLite verification snapshot"));
      assertInstanceOf(IOException.class, failure.getCause());
    }
  }

  @Test
  void createPrivateSnapshotDirectory_reportsARejectedAllocationOnAnAclOnlyFixture() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath tempRoot = fileSystem.path("\\tmp");
      tempRoot.exists = true;
      tempRoot.regularFile = false;

      ManagedSqliteRuntimeUnavailableException exception =
          assertThrows(
              ManagedSqliteRuntimeUnavailableException.class,
              () ->
                  SqliteManagedLibraryIdentity.createPrivateSnapshotDirectory(
                      tempRoot,
                      (parentDirectory, namePrefix) -> {
                        throw new IOException("injected ACL-only fixture refusal");
                      }));

      assertTrue(
          Objects.requireNonNull(exception.getMessage())
              .contains(
                  "could not create a private managed SQLite verification snapshot directory"));
      assertEquals(2, fileSystem.registeredPaths().size());
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
                    sourceTarget,
                    snapshotDirectory,
                    outsideLibraryPath,
                    validChecksumPath,
                    "0".repeat(64)));
    IllegalArgumentException outsideChecksum =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new SqliteVerifiedLibrarySnapshot(
                    sourceTarget,
                    snapshotDirectory,
                    validLibraryPath,
                    outsideChecksumPath,
                    "0".repeat(64)));

    assertEquals(
        "snapshotLibraryPath must live inside snapshotDirectory.", outsideLibrary.getMessage());
    assertEquals(
        "snapshotChecksumPath must live inside snapshotDirectory.", outsideChecksum.getMessage());
  }

  @Test
  void verifiedSnapshot_recordRejectsAnInvalidVerifiedDigest() throws Exception {
    Path snapshotDirectory =
        Files.createDirectory(tempDirectory.resolve("invalid-digest-snapshot"));
    Path libraryPath = snapshotDirectory.resolve("library.dylib");
    Path checksumPath = snapshotDirectory.resolve("library.dylib.sha256");
    Files.writeString(libraryPath, "library", StandardCharsets.UTF_8);
    Files.writeString(checksumPath, "checksum", StandardCharsets.UTF_8);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new SqliteVerifiedLibrarySnapshot(
                    new SqliteLibraryTarget(
                        "managed-only",
                        SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                        libraryPath.toString()),
                    snapshotDirectory,
                    libraryPath,
                    checksumPath,
                    "not-a-digest"));

    assertEquals(
        "snapshotLibrarySha256 must be one lowercase 64-character SHA-256 digest.",
        exception.getMessage());
  }

  @Test
  void verifiedSnapshot_copyOfRetainsAnIncompleteOwnerOnlyAttemptAfterCopyFailure()
      throws Exception {
    Path libraryPath = writeLibrary("libsqlite3.so.0", "sqlite3mc");
    Path missingChecksumPath = tempDirectory.resolve("missing.sha256");
    Path snapshotDirectory =
        SqliteManagedLibraryIdentity.createPrivateSnapshotDirectory(
            tempDirectory, PrivateOutputDirectory::createNewOwnerOnlyChild);

    ManagedSqliteRuntimeUnavailableException exception =
        assertThrows(
            ManagedSqliteRuntimeUnavailableException.class,
            () ->
                SqliteVerifiedLibrarySnapshot.copyOf(
                    new SqliteLibraryTarget(
                        "managed-only",
                        SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                        libraryPath.toString()),
                    libraryPath,
                    missingChecksumPath,
                    () -> snapshotDirectory));

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("could not create a retained private managed SQLite verification snapshot"));
    assertTrue(Files.isDirectory(snapshotDirectory));
    assertEquals(0L, Files.size(snapshotDirectory.resolve(libraryPath.getFileName())));
    assertFalse(Files.exists(snapshotDirectory.resolve(missingChecksumPath.getFileName())));
  }

  @Test
  void verifiedSnapshot_copyOfRejectsAnInjectedNonPrivateSnapshotDirectoryBeforeCopying()
      throws Exception {
    Assumptions.assumeTrue(
        tempDirectory.getFileSystem().supportedFileAttributeViews().contains("posix"),
        "host filesystem lacks POSIX permissions");
    Path libraryPath = writeLibrary("insecure-snapshot.dylib", "sqlite3mc");
    writeSiblingChecksum(libraryPath);
    Path insecureSnapshotDirectory =
        Files.createDirectory(tempDirectory.resolve("insecure-snapshot"));
    Files.setPosixFilePermissions(
        insecureSnapshotDirectory,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_EXECUTE));

    ManagedSqliteRuntimeUnavailableException exception =
        assertThrows(
            ManagedSqliteRuntimeUnavailableException.class,
            () ->
                SqliteVerifiedLibrarySnapshot.copyOf(
                    new SqliteLibraryTarget(
                        "managed-only",
                        SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                        libraryPath.toString()),
                    libraryPath,
                    SqliteManagedLibraryIdentity.checksumPath(libraryPath),
                    () -> insecureSnapshotDirectory));

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains(
                "could not establish an exact private managed SQLite verification snapshot directory"));
    assertFalse(Files.exists(insecureSnapshotDirectory.resolve(libraryPath.getFileName())));
  }

  @Test
  void verifiedSnapshot_copyOfRetainsOnlyLibraryAndChecksumArtifacts() throws Exception {
    Path libraryPath = writeLibrary("snapshot.dylib", "sqlite3mc");
    writeSiblingChecksum(libraryPath);

    SqliteVerifiedLibrarySnapshot snapshot =
        SqliteVerifiedLibrarySnapshot.copyOf(
            new SqliteLibraryTarget(
                "managed-only",
                SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                libraryPath.toString()),
            libraryPath,
            SqliteManagedLibraryIdentity.checksumPath(libraryPath));

    assertEquals(
        snapshot.snapshotLibraryPath().toString(), snapshot.runtimeTarget().lookupTarget());
    assertTrue(Files.isRegularFile(snapshot.snapshotLibraryPath()));
    assertTrue(Files.isRegularFile(snapshot.snapshotChecksumPath()));
    assertTrue(Files.isDirectory(snapshot.snapshotDirectory()));
  }

  @Test
  void verifiedSnapshot_rejectsAReplacementAtTheImmediatePreLoadDigestCheck() throws Exception {
    Path libraryPath = writeLibrary("preload.dylib", "trusted managed sqlite");
    writeSiblingChecksum(libraryPath);
    SqliteVerifiedLibrarySnapshot snapshot =
        SqliteVerifiedLibrarySnapshot.copyOf(
            new SqliteLibraryTarget(
                "managed-only",
                SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                libraryPath.toString()),
            libraryPath,
            SqliteManagedLibraryIdentity.checksumPath(libraryPath));

    snapshot.requireCurrentBytesMatchVerifiedDigestBeforePathLoad();
    Files.writeString(snapshot.snapshotLibraryPath(), "replacement", StandardCharsets.UTF_8);

    ManagedSqliteRuntimeUnavailableException exception =
        assertThrows(
            ManagedSqliteRuntimeUnavailableException.class,
            snapshot::requireCurrentBytesMatchVerifiedDigestBeforePathLoad);

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("changed after verification and before native loading"));
  }

  @Test
  void verifiedSnapshot_reportsAnUnreadableSnapshotAtTheImmediatePreLoadDigestCheck()
      throws Exception {
    Path libraryPath = writeLibrary("preload-missing.dylib", "trusted managed sqlite");
    writeSiblingChecksum(libraryPath);
    SqliteVerifiedLibrarySnapshot snapshot =
        SqliteVerifiedLibrarySnapshot.copyOf(
            new SqliteLibraryTarget(
                "managed-only",
                SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                libraryPath.toString()),
            libraryPath,
            SqliteManagedLibraryIdentity.checksumPath(libraryPath));
    Files.delete(snapshot.snapshotLibraryPath());

    ManagedSqliteRuntimeUnavailableException exception =
        assertThrows(
            ManagedSqliteRuntimeUnavailableException.class,
            snapshot::requireCurrentBytesMatchVerifiedDigestBeforePathLoad);

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("could not revalidate the retained managed SQLite snapshot"));
  }

  @Test
  void verifiedSnapshot_copyOfWithInjectedSnapshotDirectory_retainsIncompleteAttempt() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath sourceLibraryPath = fileSystem.path("\\source\\sqlite3.dll");
      sourceLibraryPath.exists = true;
      sourceLibraryPath.regularFile = true;
      AclFixturePath sourceChecksumPath = fileSystem.path("\\source\\sqlite3.dll.sha256");
      AclFixturePath snapshotDirectory = fileSystem.path("\\snapshots");
      snapshotDirectory.exists = true;
      snapshotDirectory.regularFile = false;
      snapshotDirectory.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE);

      ManagedSqliteRuntimeUnavailableException exception =
          assertThrows(
              ManagedSqliteRuntimeUnavailableException.class,
              () ->
                  SqliteVerifiedLibrarySnapshot.copyOf(
                      new SqliteLibraryTarget(
                          "managed-only",
                          SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                          sourceLibraryPath.toString()),
                      sourceLibraryPath,
                      sourceChecksumPath,
                      () -> snapshotDirectory));

      assertTrue(
          Objects.requireNonNull(exception.getMessage())
              .contains(
                  "could not create a retained private managed SQLite verification snapshot"));
      assertTrue(snapshotDirectory.exists);
      assertTrue(fileSystem.path("\\snapshots\\sqlite3.dll").exists);
      assertFalse(fileSystem.path("\\snapshots\\sqlite3.dll.sha256").exists);
      assertEquals(
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE),
          snapshotDirectory.posixPermissions);
      assertEquals(
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
          fileSystem.path("\\snapshots\\sqlite3.dll").posixPermissions);
    }
  }

  @Test
  void verifiedSnapshot_copyOfRefusesOccupiedSnapshotNamesWithoutReplacingOrRemovingThem()
      throws Exception {
    Path libraryPath = writeLibrary("source.dylib", "trusted source library");
    writeSiblingChecksum(libraryPath);
    Path checksumPath = SqliteManagedLibraryIdentity.checksumPath(libraryPath);
    Path snapshotDirectory =
        SqliteManagedLibraryIdentity.createPrivateSnapshotDirectory(
            tempDirectory, PrivateOutputDirectory::createNewOwnerOnlyChild);
    Path snapshotLibraryPath = snapshotDirectory.resolve(libraryPath.getFileName());
    Path snapshotChecksumPath = snapshotDirectory.resolve(checksumPath.getFileName());
    Files.writeString(snapshotLibraryPath, "external replacement library", StandardCharsets.UTF_8);
    Files.writeString(
        snapshotChecksumPath, "external replacement checksum", StandardCharsets.UTF_8);

    ManagedSqliteRuntimeUnavailableException exception =
        assertThrows(
            ManagedSqliteRuntimeUnavailableException.class,
            () ->
                SqliteVerifiedLibrarySnapshot.copyOf(
                    new SqliteLibraryTarget(
                        "managed-only",
                        SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                        libraryPath.toString()),
                    libraryPath,
                    checksumPath,
                    () -> snapshotDirectory));

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("could not create a retained private managed SQLite verification snapshot"));
    assertTrue(Files.exists(snapshotDirectory));
    assertEquals("external replacement library", Files.readString(snapshotLibraryPath));
    assertEquals("external replacement checksum", Files.readString(snapshotChecksumPath));
  }

  @Test
  void verifiedSnapshot_copyOfRefusesAclOnlyDestinationsBeforeCreatingSnapshotFiles() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath sourceLibraryPath = fileSystem.path("\\source\\sqlite3.dll");
      sourceLibraryPath.exists = true;
      sourceLibraryPath.regularFile = true;
      sourceLibraryPath.replaceContent("sqlite3mc".getBytes(StandardCharsets.UTF_8));
      AclFixturePath sourceChecksumPath = fileSystem.path("\\source\\sqlite3.dll.sha256");
      sourceChecksumPath.exists = true;
      sourceChecksumPath.regularFile = true;
      sourceChecksumPath.replaceContent(
          "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa  sqlite3.dll\\n"
              .getBytes(StandardCharsets.UTF_8));
      AclFixturePath snapshotDirectory = fileSystem.path("\\snapshots");
      snapshotDirectory.exists = true;
      snapshotDirectory.regularFile = false;
      Objects.requireNonNull(snapshotDirectory.aclView)
          .setAcl(
              java.util.List.of(
                  AclEntry.newBuilder()
                      .setType(AclEntryType.ALLOW)
                      .setPrincipal(fileSystem.owner)
                      .setPermissions(
                          AclEntryPermission.LIST_DIRECTORY,
                          AclEntryPermission.ADD_FILE,
                          AclEntryPermission.EXECUTE)
                      .build()));
      AclFixturePath snapshotLibraryPath = fileSystem.path("\\snapshots\\sqlite3.dll");
      AclFixturePath snapshotChecksumPath = fileSystem.path("\\snapshots\\sqlite3.dll.sha256");

      ManagedSqliteRuntimeUnavailableException exception =
          assertThrows(
              ManagedSqliteRuntimeUnavailableException.class,
              () ->
                  SqliteVerifiedLibrarySnapshot.copyOf(
                      new SqliteLibraryTarget(
                          "managed-only",
                          SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED,
                          sourceLibraryPath.toString()),
                      sourceLibraryPath,
                      sourceChecksumPath,
                      () -> snapshotDirectory));

      assertTrue(
          Objects.requireNonNull(exception.getMessage())
              .contains(
                  "could not atomically create a private managed SQLite verification snapshot"));
      assertInstanceOf(
          dev.erst.fingrind.core.PrivateOutputFile.OwnerOnlyFileViolation.class,
          exception.getCause());
      assertFalse(snapshotLibraryPath.exists);
      assertFalse(snapshotChecksumPath.exists);
    }
  }

  @Test
  void openSourceReadChannel_explainsWhenTheFilesystemCannotHonorNoFollow() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath sourcePath = fixtureRegularFile(fileSystem, "\\source\\sqlite3.dll", "sqlite");
      sourcePath.failNewFileChannelWithUnsupportedOperation(
          new UnsupportedOperationException("NOFOLLOW_LINKS is unavailable"));

      ManagedSqliteRuntimeUnavailableException exception =
          assertThrows(
              ManagedSqliteRuntimeUnavailableException.class,
              () -> SqliteManagedLibrarySnapshotSecurity.openSourceReadChannel(sourcePath));

      assertTrue(
          Objects.requireNonNull(exception.getMessage())
              .contains(
                  "cannot open a managed SQLite snapshot source without nofollow protection"));
    }
  }

  @Test
  void openNewPrivateSnapshotChannel_refusesAFileSystemWithoutAtomicPosixCreation() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parent = fileSystem.path("\\snapshots");
      parent.exists = true;
      parent.regularFile = false;
      AclFixturePath snapshotPath = fileSystem.path("\\snapshots\\sqlite3.dll");
      snapshotPath.failNewFileChannelWithUnsupportedOperation(
          new UnsupportedOperationException("POSIX create attributes are unavailable"));

      ManagedSqliteRuntimeUnavailableException exception =
          assertThrows(
              ManagedSqliteRuntimeUnavailableException.class,
              () ->
                  SqliteManagedLibrarySnapshotSecurity.openNewPrivateSnapshotChannel(snapshotPath));

      assertTrue(
          Objects.requireNonNull(exception.getMessage())
              .contains(
                  "could not atomically create a private managed SQLite verification snapshot"));
      assertInstanceOf(
          dev.erst.fingrind.core.PrivateOutputFile.OwnerOnlyFileViolation.class,
          exception.getCause());
      assertFalse(snapshotPath.exists);
    }
  }

  @Test
  void copyForceAndVerifyExact_refusesAClaimedNewDestinationThatAlreadyContainsBytes()
      throws Exception {
    Path sourcePath = writeLibrary("source.dylib", "trusted source");
    Path destinationPath = writeLibrary("destination.dylib", "unexpected existing content");

    try (FileChannel source = FileChannel.open(sourcePath);
        FileChannel destination = FileChannel.open(destinationPath)) {
      IOException exception =
          assertThrows(
              IOException.class,
              () ->
                  SqliteManagedLibrarySnapshotSecurity.copyForceAndVerifyExact(
                      source, SqliteTestPrivateOutputFile.wrap(destination)));

      assertEquals(
          "A newly created managed SQLite snapshot file was not empty.", exception.getMessage());
    }
  }

  @Test
  void copyForceAndVerifyExact_refusesAChangedSourceBeforeExactChannelValidation()
      throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath sourcePath = fixtureRegularFile(fileSystem, "\\source\\sqlite3.dll", "sqlite");
      AclFixturePath destinationPath =
          fixtureRegularFile(fileSystem, "\\snapshot\\sqlite3.dll", "");

      try (FileChannel source = new ChangingSourceFixtureFileChannel(sourcePath);
          FileChannel destination =
              new AclFixtureFileChannel(
                  destinationPath, new AclFixtureSeekableByteChannel(destinationPath))) {
        IOException exception =
            assertThrows(
                IOException.class,
                () ->
                    SqliteManagedLibrarySnapshotSecurity.copyForceAndVerifyExact(
                        source, SqliteTestPrivateOutputFile.wrap(destination)));

        assertEquals(
            "Managed SQLite snapshot bytes changed before exact-channel validation.",
            exception.getMessage());
      }
    }
  }

  @Test
  void readUtf8LinesFromExactChannel_refusesOversizedChecksumsBeforeAllocatingMemory()
      throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath checksumPath =
          fixtureRegularFile(fileSystem, "\\snapshot\\sqlite3.dll.sha256", "x".repeat(65_537));

      try (FileChannel checksum =
          new AclFixtureFileChannel(
              checksumPath, new AclFixtureSeekableByteChannel(checksumPath))) {
        IOException exception =
            assertThrows(
                IOException.class,
                () ->
                    SqliteManagedLibrarySnapshotSecurity.readUtf8LinesFromExactChannel(
                        SqliteTestPrivateOutputFile.wrap(checksum)));

        assertEquals(
            "Managed SQLite snapshot checksum exceeds its maximum size.", exception.getMessage());
      }
    }
  }

  @Test
  void readUtf8LinesFromExactChannel_refusesAChecksumThatEndsBeforeItsAdvertisedSize()
      throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath checksumPath =
          fixtureRegularFile(fileSystem, "\\snapshot\\sqlite3.dll.sha256", "checksum");

      try (FileChannel checksum = new UnexpectedEndFixtureFileChannel(checksumPath)) {
        IOException exception =
            assertThrows(
                IOException.class,
                () ->
                    SqliteManagedLibrarySnapshotSecurity.readUtf8LinesFromExactChannel(
                        SqliteTestPrivateOutputFile.wrap(checksum)));

        assertEquals(
            "Managed SQLite snapshot checksum ended unexpectedly.", exception.getMessage());
      }
    }
  }

  @Test
  void copyForceAndVerifyExactRejectsSourceReadZeroProgress() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath sourcePath = fixtureRegularFile(fileSystem, "\\source\\sqlite3.dll", "sqlite");
      AclFixturePath destinationPath =
          fixtureRegularFile(fileSystem, "\\snapshot\\sqlite3.dll", "");

      try (FileChannel source = new ZeroReadFixtureFileChannel(sourcePath);
          FileChannel destination =
              new AclFixtureFileChannel(
                  destinationPath, new AclFixtureSeekableByteChannel(destinationPath))) {
        IOException exception =
            assertThrows(
                IOException.class,
                () ->
                    SqliteManagedLibrarySnapshotSecurity.copyForceAndVerifyExact(
                        source, SqliteTestPrivateOutputFile.wrap(destination)));

        assertEquals(
            "Managed SQLite snapshot source did not make read progress.", exception.getMessage());
      }
    }
  }

  @Test
  void copyForceAndVerifyExactRejectsDestinationWriteZeroProgress() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath sourcePath = fixtureRegularFile(fileSystem, "\\source\\sqlite3.dll", "sqlite");
      AclFixturePath destinationPath =
          fixtureRegularFile(fileSystem, "\\snapshot\\sqlite3.dll", "");

      try (FileChannel source =
              new AclFixtureFileChannel(sourcePath, new AclFixtureSeekableByteChannel(sourcePath));
          FileChannel destination = new ZeroWriteFixtureFileChannel(destinationPath)) {
        IOException exception =
            assertThrows(
                IOException.class,
                () ->
                    SqliteManagedLibrarySnapshotSecurity.copyForceAndVerifyExact(
                        source, SqliteTestPrivateOutputFile.wrap(destination)));

        assertEquals(
            "Managed SQLite snapshot destination did not make write progress.",
            exception.getMessage());
      }
    }
  }

  @Test
  void copyForceAndVerifyExactRejectsDestinationDigestReadZeroProgress() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath sourcePath = fixtureRegularFile(fileSystem, "\\source\\sqlite3.dll", "sqlite");
      AclFixturePath destinationPath =
          fixtureRegularFile(fileSystem, "\\snapshot\\sqlite3.dll", "");

      try (FileChannel source =
              new AclFixtureFileChannel(sourcePath, new AclFixtureSeekableByteChannel(sourcePath));
          FileChannel destination = new ZeroReadFixtureFileChannel(destinationPath)) {
        IOException exception =
            assertThrows(
                IOException.class,
                () ->
                    SqliteManagedLibrarySnapshotSecurity.copyForceAndVerifyExact(
                        source, SqliteTestPrivateOutputFile.wrap(destination)));

        assertEquals(
            "Cryptographic digest input did not make read progress.", exception.getMessage());
      }
    }
  }

  @Test
  void readUtf8LinesFromExactChannelRejectsZeroProgress() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath checksumPath =
          fixtureRegularFile(fileSystem, "\\snapshot\\sqlite3.dll.sha256", "checksum");

      try (FileChannel checksum = new ZeroReadFixtureFileChannel(checksumPath)) {
        IOException exception =
            assertThrows(
                IOException.class,
                () ->
                    SqliteManagedLibrarySnapshotSecurity.readUtf8LinesFromExactChannel(
                        SqliteTestPrivateOutputFile.wrap(checksum)));

        assertEquals(
            "Managed SQLite snapshot checksum did not make read progress.", exception.getMessage());
      }
    }
  }

  private static AclFixturePath fixtureRegularFile(
      AclFixtureFileSystem fileSystem, String path, String contents) {
    AclFixturePath fixture = fileSystem.path(path);
    fixture.exists = true;
    fixture.regularFile = true;
    fixture.replaceContent(contents.getBytes(StandardCharsets.UTF_8));
    return fixture;
  }

  private static Path createPosixSnapshotDirectory(Path parentDirectory, String namePrefix)
      throws IOException {
    return Files.createTempDirectory(
        parentDirectory,
        namePrefix,
        PosixFilePermissions.asFileAttribute(
            Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE)));
  }

  /** File channel that refuses every read request without making checksum progress. */
  private static final class ZeroReadFixtureFileChannel extends AclFixtureFileChannel {
    private ZeroReadFixtureFileChannel(AclFixturePath path) {
      super(path, new AclFixtureSeekableByteChannel(path));
    }

    @Override
    public int read(ByteBuffer destination) {
      return 0;
    }
  }

  /** Source channel whose bytes change after copy but before its descriptor-bound digest. */
  private static final class ChangingSourceFixtureFileChannel extends AclFixtureFileChannel {
    private final AclFixturePath path;
    private int resetCount;

    private ChangingSourceFixtureFileChannel(AclFixturePath path) {
      super(path, new AclFixtureSeekableByteChannel(path));
      this.path = path;
    }

    @Override
    public FileChannel position(long newPosition) throws IOException {
      FileChannel positioned = super.position(newPosition);
      if (newPosition == 0L) {
        int completedResets = resetCount;
        resetCount++;
        if (completedResets == 1) {
          path.replaceContent("tamper".getBytes(StandardCharsets.UTF_8));
        }
      }
      return positioned;
    }
  }

  /** Checksum channel that advertises bytes but reaches EOF before yielding any. */
  private static final class UnexpectedEndFixtureFileChannel extends AclFixtureFileChannel {
    private UnexpectedEndFixtureFileChannel(AclFixturePath path) {
      super(path, new AclFixtureSeekableByteChannel(path));
    }

    @Override
    public int read(ByteBuffer destination) {
      return -1;
    }
  }

  /** File channel that refuses every write request without making snapshot-copy progress. */
  private static final class ZeroWriteFixtureFileChannel extends AclFixtureFileChannel {
    private ZeroWriteFixtureFileChannel(AclFixturePath path) {
      super(path, new AclFixtureSeekableByteChannel(path));
    }

    @Override
    public int write(ByteBuffer source) {
      return 0;
    }
  }
}
