package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.PrivateOutputFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exact caller-path mapping for every core private-output file admission category. */
class SqlitePrivateOutputFileFailuresTest {
  @TempDir Path tempDirectory;

  @Test
  void mapsEveryCorePrivateOutputFileFailureCategory() throws Exception {
    assertFailure(
        tempDirectory.resolve("missing/record"),
        PrivateOutputFile.ViolationKind.MISSING_PARENT,
        SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY);
    Path existingParent = Files.createDirectory(tempDirectory.resolve("existing"));
    assertFailure(
        existingParent.resolve("record"),
        PrivateOutputFile.ViolationKind.PARENT_OWNER_ONLY_REQUIRED,
        SqliteCallerPathFailure.PARENT_OWNER_ONLY_REQUIRED);
    assertFailure(
        existingParent.resolve("record"),
        PrivateOutputFile.ViolationKind.REGULAR_NON_SYMLINK_REQUIRED,
        SqliteCallerPathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE);
    assertFailure(
        existingParent.resolve("record"),
        PrivateOutputFile.ViolationKind.OWNER_ONLY_REQUIRED,
        SqliteCallerPathFailure.TARGET_OWNER_ONLY_REQUIRED);
    assertFailure(
        existingParent.resolve("record"),
        PrivateOutputFile.ViolationKind.ATOMIC_CREATION_UNSUPPORTED,
        SqliteCallerPathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED);
  }

  @Test
  void mapsARealParentPathCollisionFromTheCoreAdmissionBoundary() throws Exception {
    Path parentCollision = tempDirectory.resolve("parent-file");
    Files.writeString(parentCollision, "not a directory");

    SqliteCallerPathContractException failure =
        assertThrows(
            SqliteCallerPathContractException.class,
            () -> SqliteOwnedRegularFileAccess.openWrite(parentCollision.resolve("record")));

    assertEquals(SqliteCallerPathFailure.PARENT_PATH_COLLISION, failure.pathFailure());
  }

  @Test
  void mapsEveryExactCoreOwnerOnlyFileAdmissionFailure() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parent = fileSystem.path("\\artifacts");
      configureOwnerOnlyDirectory(parent);

      AclFixturePath nonRegularArtifact = fileSystem.path("\\artifacts\\directory-artifact");
      nonRegularArtifact.exists = true;
      nonRegularArtifact.regularFile = false;
      assertExactBoundaryFailure(
          nonRegularArtifact,
          SqliteCallerPathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE,
          SqliteOwnedRegularFileAccess::openWrite);

      AclFixturePath nonOwnerOnlyArtifact = fileSystem.path("\\artifacts\\shared-artifact");
      nonOwnerOnlyArtifact.exists = true;
      nonOwnerOnlyArtifact.regularFile = true;
      nonOwnerOnlyArtifact.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.GROUP_READ);
      assertExactBoundaryFailure(
          nonOwnerOnlyArtifact,
          SqliteCallerPathFailure.TARGET_OWNER_ONLY_REQUIRED,
          SqliteOwnedRegularFileAccess::openWrite);

      AclFixturePath unsupportedCreationArtifact = fileSystem.path("\\artifacts\\new-artifact");
      unsupportedCreationArtifact.failNewFileChannelWithUnsupportedOperation(
          new UnsupportedOperationException("injected atomic owner-only creation refusal"));
      assertExactBoundaryFailure(
          unsupportedCreationArtifact,
          SqliteCallerPathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED,
          SqliteOwnedRegularFileAccess::openNewWrite);
    }
  }

  private static void assertFailure(
      Path path,
      PrivateOutputFile.ViolationKind violationKind,
      SqliteCallerPathFailure expectedFailure) {
    assertEquals(
        expectedFailure,
        SqlitePrivateOutputFileFailures.map(
                path, violationKind, new IOException("controlled core admission failure"))
            .pathFailure());
  }

  private static void configureOwnerOnlyDirectory(AclFixturePath directory) {
    directory.exists = true;
    directory.regularFile = false;
    directory.posixPermissions =
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);
  }

  private static void assertExactBoundaryFailure(
      Path path, SqliteCallerPathFailure expectedFailure, ThrowingFileOpeningOperation operation) {
    SqliteCallerPathContractException exception =
        assertThrows(SqliteCallerPathContractException.class, () -> operation.open(path));

    assertEquals(expectedFailure, exception.pathFailure());
  }

  /** Opens one exact owner-only file while allowing the test to inject a core admission failure. */
  @FunctionalInterface
  private interface ThrowingFileOpeningOperation {
    PrivateOutputFile.OpenedFile open(Path path) throws IOException;
  }
}
