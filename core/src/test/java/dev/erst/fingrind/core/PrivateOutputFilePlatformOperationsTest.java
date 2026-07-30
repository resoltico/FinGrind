package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises live POSIX behavior of the production owner-only file platform boundary. */
class PrivateOutputFilePlatformOperationsTest {
  private static final Set<PosixFilePermission> OWNER_ONLY_DIRECTORY =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE);
  private static final Set<PosixFilePermission> OWNER_READ_WRITE =
      Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

  @TempDir Path temporaryDirectory;

  @Test
  void reportsTheLiveFilesystemCapabilitiesAndOperatingSystemFact() {
    PrivateOutputFilePlatformOperations operations = new PrivateOutputFilePlatformOperations();
    Set<String> views = temporaryDirectory.getFileSystem().supportedFileAttributeViews();

    assertEquals(views.contains("posix"), operations.supportsPosix(temporaryDirectory));
    assertEquals(views.contains("acl"), operations.supportsAcl(temporaryDirectory));
    assertEquals(
        PrivateOutputFile.isWindows(System.getProperty("os.name", "")), operations.isWindows());
  }

  @Test
  void delegatesWindowsFileOperationsToTheSelectedNativeTransportBoundary() throws IOException {
    Path retainedChannelPath = temporaryDirectory.resolve("retained-native-handle-test.fg");
    Path creationPath = temporaryDirectory.resolve("created-through-native-boundary.fg");
    Path openingPath = temporaryDirectory.resolve("opened-through-native-boundary.fg");
    try (PrivateOutputFile.OpenedFile expected =
        PrivateOutputFile.wrap(
            FileChannel.open(
                retainedChannelPath,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE))) {
      PrivateOutputFilePlatformOperations operations =
          new PrivateOutputFilePlatformOperations(
              PrivateOutputFilePlatformOperationsTest::unexpectedPosixCreation,
              PrivateOutputFilePlatformOperationsTest::unexpectedPosixOpening,
              file -> {
                assertEquals(creationPath, file);
                return expected;
              },
              (file, access) -> {
                assertEquals(openingPath, file);
                assertEquals(PrivateOutputFile.Access.READ_WRITE, access);
                return expected;
              });

      assertSame(expected, operations.createNewWindows(creationPath));
      assertSame(
          expected,
          operations.openExistingWindows(openingPath, PrivateOutputFile.Access.READ_WRITE));
    }
  }

  @Test
  void createsAndReopensAnExactPosixOwnerOnlyFileForBothAccessModes() throws IOException {
    assumePosix();
    PrivateOutputFilePlatformOperations operations = new PrivateOutputFilePlatformOperations();
    Path parent = privateParent();
    Path artifact = parent.resolve("artifact.fg");

    assertDoesNotThrow(() -> operations.requireSecureParent(artifact));
    try (PrivateOutputFile.OpenedFile created = operations.createNewPosix(artifact)) {
      assertTrue(created.created());
      assertEquals(1, created.write(ByteBuffer.wrap(new byte[] {1})));
    }

    assertEquals(OWNER_READ_WRITE, Files.getPosixFilePermissions(artifact));
    try (PrivateOutputFile.OpenedFile readOnly =
        operations.openExistingPosix(artifact, PrivateOutputFile.Access.READ_ONLY)) {
      assertFalse(readOnly.created());
      assertEquals(1, readOnly.read(ByteBuffer.allocate(1)));
    }
    try (PrivateOutputFile.OpenedFile readWrite =
        operations.openExistingPosix(artifact, PrivateOutputFile.Access.READ_WRITE)) {
      assertFalse(readWrite.created());
    }
  }

  @Test
  void rejectsDirectoryAndInsufficientOwnerPermissionsBeforeOpening() throws IOException {
    assumePosix();
    PrivateOutputFilePlatformOperations operations = new PrivateOutputFilePlatformOperations();
    Path parent = privateParent();
    Path directory = parent.resolve("not-a-file");
    Files.createDirectory(directory);
    Path ownerReadableOnly = parent.resolve("owner-readable-only.fg");
    Files.createFile(ownerReadableOnly);
    Files.setPosixFilePermissions(ownerReadableOnly, Set.of(PosixFilePermission.OWNER_READ));
    Path ownerWriteOnly = parent.resolve("owner-write-only.fg");
    Files.createFile(ownerWriteOnly);
    Files.setPosixFilePermissions(ownerWriteOnly, Set.of(PosixFilePermission.OWNER_WRITE));

    PrivateOutputFile.OwnerOnlyFileViolation directoryFailure =
        assertThrows(
            PrivateOutputFile.OwnerOnlyFileViolation.class,
            () -> operations.openExistingPosix(directory, PrivateOutputFile.Access.READ_ONLY));
    PrivateOutputFile.OwnerOnlyFileViolation readWriteFailure =
        assertThrows(
            PrivateOutputFile.OwnerOnlyFileViolation.class,
            () ->
                operations.openExistingPosix(
                    ownerReadableOnly, PrivateOutputFile.Access.READ_WRITE));
    PrivateOutputFile.OwnerOnlyFileViolation readFailure =
        assertThrows(
            PrivateOutputFile.OwnerOnlyFileViolation.class,
            () -> operations.openExistingPosix(ownerWriteOnly, PrivateOutputFile.Access.READ_ONLY));

    assertEquals(
        PrivateOutputFile.ViolationKind.REGULAR_NON_SYMLINK_REQUIRED, directoryFailure.kind());
    assertEquals(PrivateOutputFile.ViolationKind.OWNER_ONLY_REQUIRED, readWriteFailure.kind());
    assertEquals(PrivateOutputFile.ViolationKind.OWNER_ONLY_REQUIRED, readFailure.kind());
  }

  @Test
  void mapsDeterministicPosixChannelRejectionsToTheOwnerOnlyVocabulary() throws IOException {
    assumePosix();
    Path parent = privateParent();
    Path artifact = parent.resolve("unsupported-channel.fg");
    Files.createFile(artifact);
    Files.setPosixFilePermissions(artifact, OWNER_READ_WRITE);
    UnsupportedOperationException creationRejection =
        new UnsupportedOperationException("simulated unsupported creation primitive");
    IllegalArgumentException openingRejection =
        new IllegalArgumentException("simulated unsupported nofollow opening primitive");
    PrivateOutputFilePlatformOperations operations =
        new PrivateOutputFilePlatformOperations(
            ignored -> {
              throw creationRejection;
            },
            (ignored, access) -> {
              throw openingRejection;
            },
            PrivateOutputFilePlatformOperationsTest::unexpectedWindowsCreation,
            PrivateOutputFilePlatformOperationsTest::unexpectedWindowsOpening);

    PrivateOutputFile.OwnerOnlyFileViolation creationFailure =
        assertThrows(
            PrivateOutputFile.OwnerOnlyFileViolation.class,
            () -> operations.createNewPosix(parent.resolve("new-unsupported-channel.fg")));
    PrivateOutputFile.OwnerOnlyFileViolation openingFailure =
        assertThrows(
            PrivateOutputFile.OwnerOnlyFileViolation.class,
            () -> operations.openExistingPosix(artifact, PrivateOutputFile.Access.READ_ONLY));

    assertEquals(
        PrivateOutputFile.ViolationKind.ATOMIC_CREATION_UNSUPPORTED, creationFailure.kind());
    assertSame(creationRejection, creationFailure.getCause());
    assertEquals(
        PrivateOutputFile.ViolationKind.ATOMIC_CREATION_UNSUPPORTED, openingFailure.kind());
    assertSame(openingRejection, openingFailure.getCause());
  }

  @Test
  void publicPhysicalIdentityUsesTheRetainedPosixArtifactIdentity() throws IOException {
    assumePosix();
    Path artifact = privateParent().resolve("identity.fg");

    try (PrivateOutputFile.OpenedFile ignored = PrivateOutputFile.createNew(artifact)) {
      // Creating through the public capability establishes the owner-only artifact admission.
    }

    assertTrue(PrivateOutputFile.physicalObjectIdentity(artifact).startsWith("posix-v1:dev="));
  }

  @Test
  void posixIdentityRequiresBothNumericFilesystemIdentityComponents() throws IOException {
    assertEquals(
        "posix-v1:dev=7:ino=11",
        PrivateOutputFilePosixOpenedFile.physicalObjectIdentity(
            Map.<String, Object>of("dev", 7L, "ino", 11L)));
    assertThrows(
        IOException.class,
        () ->
            PrivateOutputFilePosixOpenedFile.physicalObjectIdentity(
                Map.<String, Object>of("dev", "not-a-number", "ino", 11L)));
    assertThrows(
        IOException.class,
        () ->
            PrivateOutputFilePosixOpenedFile.physicalObjectIdentity(
                Map.<String, Object>of("dev", 7L, "ino", "not-a-number")));
    assertNull(PrivateOutputFilePosixOpenedFile.heldLock(null));
  }

  @Test
  void testOnlyWrappedChannelCannotClaimAPhysicalArtifactIdentity() throws IOException {
    Path artifact = temporaryDirectory.resolve("test-only-channel.fg");

    try (PrivateOutputFile.OpenedFile opened =
        PrivateOutputFile.wrap(
            FileChannel.open(
                artifact,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE))) {
      assertThrows(IOException.class, opened::physicalObjectIdentity);
    }
  }

  private Path privateParent() throws IOException {
    Path parent = temporaryDirectory.toRealPath();
    Files.setPosixFilePermissions(parent, OWNER_ONLY_DIRECTORY);
    return parent;
  }

  private static FileChannel unexpectedPosixCreation(Path ignored) {
    throw new AssertionError("The Windows delegation proof must not create a POSIX file.");
  }

  private static FileChannel unexpectedPosixOpening(Path ignored, PrivateOutputFile.Access access) {
    throw new AssertionError(
        "The Windows delegation proof must not open a POSIX file with access " + access + ".");
  }

  private static PrivateOutputFile.OpenedFile unexpectedWindowsCreation(Path ignored) {
    throw new AssertionError("The POSIX rejection proof must not create a Windows file.");
  }

  private static PrivateOutputFile.OpenedFile unexpectedWindowsOpening(
      Path ignored, PrivateOutputFile.Access access) {
    throw new AssertionError(
        "The POSIX rejection proof must not open a Windows file with access " + access + ".");
  }

  private void assumePosix() {
    assumeTrue(
        temporaryDirectory.getFileSystem().supportedFileAttributeViews().contains("posix"),
        "POSIX permissions are unavailable on this filesystem.");
  }
}
