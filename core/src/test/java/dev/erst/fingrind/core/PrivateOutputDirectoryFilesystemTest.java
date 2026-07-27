package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
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
  void creation_createsEveryNestedMissingComponentAsOwnerOnly() throws IOException {
    assumePosix();
    Path canonicalTemporaryDirectory = temporaryDirectory.toRealPath();
    Files.setPosixFilePermissions(canonicalTemporaryDirectory, OWNER_ONLY_DIRECTORY);
    Path first = canonicalTemporaryDirectory.resolve("nested");
    Path planned = first.resolve("private");

    PrivateOutputDirectory.createNewPosixOwnerOnlyDirectories(planned);

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
                PrivateOutputDirectory.createNewPosixOwnerOnlyDirectories(
                    planned,
                    (directory, attributes) -> {
                      Files.createDirectory(directory, attributes);
                      throw new java.nio.file.FileAlreadyExistsException(directory.toString());
                    }));

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
              () -> PrivateOutputDirectory.createNewPosixOwnerOnlyDirectories(planned));

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
            () -> PrivateOutputDirectory.createNewPosixOwnerOnlyDirectories(existing));

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
                PrivateOutputDirectory.createNewPosixOwnerOnlyDirectories(
                    canonicalTemporaryDirectory.resolve("private"),
                    (directory, attributes) -> {
                      throw creationFailure;
                    }));

    assertSame(creationFailure, exception.getCause());
  }

  private void assumePosix() {
    assumeTrue(
        temporaryDirectory.getFileSystem().supportedFileAttributeViews().contains("posix"),
        "POSIX permissions are unavailable on this filesystem.");
  }
}
