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
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Proves owner-only publication admission and creation against real filesystem semantics. */
class PrivateOutputDirectoryFilesystemTest {
  private static final Set<PosixFilePermission> OWNER_ONLY_DIRECTORY =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE);

  @TempDir Path temporaryDirectory;

  @Test
  void defaultAdmissionUsesTheLiveNioTopologyForAnOwnerOnlyDirectory() throws IOException {
    assumePosix();
    Path canonicalTemporaryDirectory = temporaryDirectory.toRealPath();
    Files.setPosixFilePermissions(canonicalTemporaryDirectory, OWNER_ONLY_DIRECTORY);

    assertDoesNotThrow(
        () -> PrivateOutputDirectory.requireExistingOwnerOnly(canonicalTemporaryDirectory));
  }

  @Test
  void defaultAdmissionRejectsASymlinkAliasButAcceptsItsPhysicalOwnerOnlyDirectory()
      throws IOException {
    assumePosix();
    Path canonicalTemporaryDirectory = temporaryDirectory.toRealPath();
    Files.setPosixFilePermissions(canonicalTemporaryDirectory, OWNER_ONLY_DIRECTORY);
    Path physicalDirectory = canonicalTemporaryDirectory.resolve("physical-private");
    Files.createDirectory(
        physicalDirectory, PosixFilePermissions.asFileAttribute(OWNER_ONLY_DIRECTORY));
    Path alias = canonicalTemporaryDirectory.resolve("private-alias");
    Files.createSymbolicLink(alias, physicalDirectory.getFileName());

    PrivateOutputDirectory.Violation exception =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () -> PrivateOutputDirectory.requireExistingOwnerOnly(alias));

    assertEquals(PrivateOutputDirectory.Violation.Kind.PATH_COLLISION, exception.kind());
    assertDoesNotThrow(() -> PrivateOutputDirectory.requireExistingOwnerOnly(physicalDirectory));
  }

  @Test
  void creation_createsEveryNestedMissingComponentAsOwnerOnly() throws IOException {
    assumePosix();
    Path canonicalTemporaryDirectory = temporaryDirectory.toRealPath();
    Files.setPosixFilePermissions(canonicalTemporaryDirectory, OWNER_ONLY_DIRECTORY);
    Path first = canonicalTemporaryDirectory.resolve("nested");
    Path planned = first.resolve("private");

    PrivateOutputDirectory.createNewOwnerOnlyDirectories(planned);

    assertEquals(OWNER_ONLY_DIRECTORY, Files.getPosixFilePermissions(first));
    assertEquals(OWNER_ONLY_DIRECTORY, Files.getPosixFilePermissions(planned));
    assertDoesNotThrow(() -> PrivateOutputDirectory.requireExistingOwnerOnly(planned));
  }

  @Test
  void creation_rejectsARacedInComponentWithoutCreatingFurtherDescendants() throws IOException {
    assumePosix();
    Path canonicalTemporaryDirectory = temporaryDirectory.toRealPath();
    Files.setPosixFilePermissions(canonicalTemporaryDirectory, OWNER_ONLY_DIRECTORY);
    Path racedIn = canonicalTemporaryDirectory.resolve("raced-in");
    Path planned = racedIn.resolve("private");

    PrivateOutputDirectory.Violation exception =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () ->
                PrivateOutputDirectoryCreation.createNewOwnerOnlyDirectories(
                    planned, failingAfterPosixCreationOperations()));

    assertEquals(PrivateOutputDirectory.Violation.Kind.PATH_COLLISION, exception.kind());
    assertTrue(Files.isDirectory(racedIn));
    assertFalse(Files.exists(planned));
  }

  @Test
  void creation_rejectsNonPosixFilesystemsBeforeCreatingAnyDirectory() throws IOException {
    Path archive = temporaryDirectory.resolve("non-posix-output.zip");
    try (FileSystem fileSystem =
        FileSystems.newFileSystem(URI.create("jar:" + archive.toUri()), Map.of("create", "true"))) {
      Path planned = fileSystem.getPath("/private");

      PrivateOutputDirectory.Violation exception =
          assertThrows(
              PrivateOutputDirectory.Violation.class,
              () -> PrivateOutputDirectory.createNewOwnerOnlyDirectories(planned));

      assertEquals(PrivateOutputDirectory.Violation.Kind.OWNER_ONLY_REQUIRED, exception.kind());
      assertFalse(Files.exists(planned));
    }
  }

  @Test
  void creationAncestryUsesTheLiveFilesystemForAMissingPrivateDescendant() throws IOException {
    assumePosix();
    Path canonicalTemporaryDirectory = temporaryDirectory.toRealPath();
    Files.setPosixFilePermissions(canonicalTemporaryDirectory, OWNER_ONLY_DIRECTORY);

    assertDoesNotThrow(
        () ->
            PrivateOutputDirectory.requireCreationAncestry(
                canonicalTemporaryDirectory.resolve("planned-private")));
  }

  @Test
  void creationRejectsAnExistingOutputDirectoryRatherThanAdoptingIt() throws IOException {
    assumePosix();
    Path canonicalTemporaryDirectory = temporaryDirectory.toRealPath();
    Files.setPosixFilePermissions(canonicalTemporaryDirectory, OWNER_ONLY_DIRECTORY);
    Path existing = canonicalTemporaryDirectory.resolve("existing-private");
    Files.createDirectory(existing);
    Files.setPosixFilePermissions(existing, OWNER_ONLY_DIRECTORY);

    PrivateOutputDirectory.Violation exception =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () -> PrivateOutputDirectory.createNewOwnerOnlyDirectories(existing));

    assertEquals(PrivateOutputDirectory.Violation.Kind.PATH_COLLISION, exception.kind());
  }

  @Test
  void creationWrapsAnUnclassifiedAtomicDirectoryCreationFailure() throws IOException {
    assumePosix();
    Path canonicalTemporaryDirectory = temporaryDirectory.toRealPath();
    Files.setPosixFilePermissions(canonicalTemporaryDirectory, OWNER_ONLY_DIRECTORY);
    IOException creationFailure = new IOException("simulated directory creation failure");

    PrivateOutputDirectory.Violation exception =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () ->
                PrivateOutputDirectoryCreation.createNewOwnerOnlyDirectories(
                    canonicalTemporaryDirectory.resolve("private"),
                    failingPosixCreationOperations(creationFailure)));

    assertSame(creationFailure, exception.getCause());
  }

  @Test
  void creationSelection_prefersPosixEvenWhenWindowsAclCapabilitiesAreAlsoPresent()
      throws PrivateOutputDirectory.Violation {
    assertEquals(
        PrivateOutputDirectoryCreation.CreationMode.POSIX,
        PrivateOutputDirectoryCreation.selectCreationMode(
            temporaryDirectory, platformSelector(true, true, true)));
  }

  @Test
  void creationSelection_usesWindowsOnlyForAclWithoutPosix()
      throws PrivateOutputDirectory.Violation {
    assertEquals(
        PrivateOutputDirectoryCreation.CreationMode.WINDOWS,
        PrivateOutputDirectoryCreation.selectCreationMode(
            temporaryDirectory, platformSelector(false, true, true)));
  }

  @Test
  void creationSelection_rejectsAclWhenTheHostIsNotWindows() {
    PrivateOutputDirectory.Violation exception =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () ->
                PrivateOutputDirectoryCreation.selectCreationMode(
                    temporaryDirectory, platformSelector(false, true, false)));

    assertEquals(PrivateOutputDirectory.Violation.Kind.OWNER_ONLY_REQUIRED, exception.kind());
    assertTrue(exception.getCause() instanceof UnsupportedOperationException);
  }

  @Test
  void freshChildAllocation_retriesOneObservedCollisionAndReturnsTheNextPrivateDirectory()
      throws IOException {
    assumePosix();
    Path canonicalTemporaryDirectory = temporaryDirectory.toRealPath();
    Files.setPosixFilePermissions(canonicalTemporaryDirectory, OWNER_ONLY_DIRECTORY);
    Path collision = canonicalTemporaryDirectory.resolve(".fresh-child-collision");
    Files.createDirectory(collision, PosixFilePermissions.asFileAttribute(OWNER_ONLY_DIRECTORY));
    Deque<String> tokens = new ArrayDeque<>(List.of("collision", "allocated"));

    Path allocated =
        PrivateOutputDirectoryCreation.createNewOwnerOnlyChild(
            canonicalTemporaryDirectory,
            ".fresh-child-",
            tokens::removeFirst,
            PrivateOutputDirectory::createNewOwnerOnlyDirectories,
            PrivateOutputDirectory.filesystemAccess());

    assertEquals(canonicalTemporaryDirectory.resolve(".fresh-child-allocated"), allocated);
    assertDoesNotThrow(() -> PrivateOutputDirectory.requireExistingOwnerOnly(allocated));
  }

  @Test
  void freshChildAllocation_doesNotMaskANonCollisionCreationFailure() throws IOException {
    assumePosix();
    Path canonicalTemporaryDirectory = temporaryDirectory.toRealPath();
    Files.setPosixFilePermissions(canonicalTemporaryDirectory, OWNER_ONLY_DIRECTORY);
    PrivateOutputDirectory.Violation failure =
        new PrivateOutputDirectory.Violation(
            PrivateOutputDirectory.Violation.Kind.OWNER_ONLY_REQUIRED,
            "simulated creation refusal");

    PrivateOutputDirectory.Violation thrown =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () ->
                PrivateOutputDirectoryCreation.createNewOwnerOnlyChild(
                    canonicalTemporaryDirectory,
                    ".fresh-child-",
                    () -> "refused",
                    candidate -> {
                      throw failure;
                    },
                    PrivateOutputDirectory.filesystemAccess()));

    assertSame(failure, thrown);
  }

  @Test
  void freshChildAllocation_rejectsAnEmptyGeneratedTokenBeforeItCanReuseOneName()
      throws IOException {
    assumePosix();
    Path canonicalTemporaryDirectory = temporaryDirectory.toRealPath();
    Files.setPosixFilePermissions(canonicalTemporaryDirectory, OWNER_ONLY_DIRECTORY);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            PrivateOutputDirectoryCreation.createNewOwnerOnlyChild(
                canonicalTemporaryDirectory,
                ".fresh-child-",
                () -> "",
                PrivateOutputDirectory::createNewOwnerOnlyDirectories,
                PrivateOutputDirectory.filesystemAccess()));
  }

  @Test
  void privateOutputFile_createsReadsLocksAndReopensTheExactOwnerOnlyArtifact() throws IOException {
    assumePosix();
    Path parent = temporaryDirectory.toRealPath();
    Files.setPosixFilePermissions(parent, OWNER_ONLY_DIRECTORY);
    Path artifact = parent.resolve("private-artifact.fg");

    try (PrivateOutputFile.OpenedFile opened = PrivateOutputFile.createNew(artifact)) {
      assertTrue(opened.created());
      assertEquals(3, opened.write(ByteBuffer.wrap(new byte[] {1, 2, 3})));
      opened.force();
      assertEquals(3L, opened.size());
      opened.position(0L);
      ByteBuffer read = ByteBuffer.allocate(3);
      assertEquals(3, opened.read(read));
      assertEquals(ByteBuffer.wrap(new byte[] {1, 2, 3}), read.flip());
      assertTrue(opened.physicalObjectIdentity().startsWith("posix-v1:dev="));
      try (PrivateOutputFile.HeldLock ignored = opened.tryExclusiveLock(0L, 1L)) {
        assertTrue(opened.isOpen());
        assertNull(opened.tryExclusiveLock(0L, 1L));
      }
    }

    assertEquals(
        Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
        Files.getPosixFilePermissions(artifact));
    PrivateOutputFile.requireExistingOwnerOnly(artifact, PrivateOutputFile.Access.READ_ONLY);
    try (PrivateOutputFile.OpenedFile reopened =
        PrivateOutputFile.openExisting(artifact, PrivateOutputFile.Access.READ_ONLY)) {
      assertFalse(reopened.created());
      ByteBuffer read = ByteBuffer.allocate(4);
      assertEquals(3, reopened.read(read));
      assertEquals(-1, reopened.read(read));
    }
  }

  @Test
  void privateOutputFile_openOrCreateAdmitsExistingFilesAndRejectsRelaxedPermissions()
      throws IOException {
    assumePosix();
    Path parent = temporaryDirectory.toRealPath();
    Files.setPosixFilePermissions(parent, OWNER_ONLY_DIRECTORY);
    Path artifact = parent.resolve("existing-artifact.fg");

    try (PrivateOutputFile.OpenedFile created = PrivateOutputFile.openOrCreate(artifact)) {
      assertTrue(created.created());
      created.truncate(0L);
    }
    try (PrivateOutputFile.OpenedFile reopened = PrivateOutputFile.openOrCreate(artifact)) {
      assertFalse(reopened.created());
    }

    Files.setPosixFilePermissions(
        artifact,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ));
    PrivateOutputFile.OwnerOnlyFileViolation failure =
        assertThrows(
            PrivateOutputFile.OwnerOnlyFileViolation.class,
            () -> PrivateOutputFile.openExisting(artifact, PrivateOutputFile.Access.READ_WRITE));

    assertEquals(PrivateOutputFile.ViolationKind.OWNER_ONLY_REQUIRED, failure.kind());
  }

  private PrivateOutputDirectoryCreation.Operations failingAfterPosixCreationOperations() {
    return new PosixCreationOperations() {
      @Override
      public void createPosixDirectory(
          Path directory, java.nio.file.attribute.FileAttribute<?>... attributes)
          throws IOException {
        Files.createDirectory(directory, attributes);
        throw new java.nio.file.FileAlreadyExistsException(directory.toString());
      }
    };
  }

  private PrivateOutputDirectoryCreation.Operations failingPosixCreationOperations(
      IOException failure) {
    return new PosixCreationOperations() {
      @Override
      public void createPosixDirectory(
          Path directory, java.nio.file.attribute.FileAttribute<?>... attributes)
          throws IOException {
        throw failure;
      }
    };
  }

  private static PrivateOutputDirectoryCreation.PlatformSelector platformSelector(
      boolean supportsPosix, boolean supportsAcl, boolean windows) {
    return new PrivateOutputDirectoryCreation.PlatformSelector() {
      @Override
      public boolean supportsPosix(Path path) {
        return supportsPosix;
      }

      @Override
      public boolean supportsAcl(Path path) {
        return supportsAcl;
      }

      @Override
      public boolean isWindows() {
        return windows;
      }
    };
  }

  /** Test-only POSIX creation boundary that rejects accidental Windows-path selection. */
  private abstract static class PosixCreationOperations
      implements PrivateOutputDirectoryCreation.Operations {
    @Override
    public boolean supportsPosix(Path path) {
      return true;
    }

    @Override
    public boolean supportsAcl(Path path) {
      return false;
    }

    @Override
    public boolean isWindows() {
      return false;
    }

    @Override
    public PrivateOutputDirectory.FilesystemAccess filesystemAccess() {
      return PrivateOutputDirectory.filesystemAccess();
    }

    @Override
    public void createWindowsDirectory(Path directory) {
      throw new AssertionError("POSIX creation must not select the Windows directory path.");
    }
  }

  private void assumePosix() {
    assumeTrue(
        temporaryDirectory.getFileSystem().supportedFileAttributeViews().contains("posix"),
        "POSIX permissions are unavailable on this filesystem.");
  }
}
