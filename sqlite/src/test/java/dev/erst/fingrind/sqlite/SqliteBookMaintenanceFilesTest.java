package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Direct contracts for nofollow lexical admission of protected-book maintenance paths. */
class SqliteBookMaintenanceFilesTest extends SqliteNativeBridgeTestSupport {
  @Test
  void existingSourceAdmissionDistinguishesMissingParentsFromParentCollisionsAndMissingLeaves()
      throws Exception {
    Path filesystemRoot = java.util.Objects.requireNonNull(tempDirectory.getRoot(), "filesystem root");
    assertEquals(
        SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY,
        assertThrows(
                SqliteCallerPathContractException.class,
                () ->
                    SqliteBookMaintenanceFiles.normalizeExistingSource(
                        filesystemRoot, "bookFilePath"))
            .pathFailure());

    Path missingParentSource = tempDirectory.resolve("missing-parent").resolve("book.sqlite");
    assertEquals(
        SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY,
        assertThrows(
                SqliteCallerPathContractException.class,
                () ->
                    SqliteBookMaintenanceFiles.normalizeExistingSource(
                        missingParentSource, "bookFilePath"))
            .pathFailure());

    Path parentCollision = tempDirectory.resolve("parent-collision");
    Files.writeString(parentCollision, "not a directory");
    assertEquals(
        SqliteCallerPathFailure.PARENT_PATH_COLLISION,
        assertThrows(
                SqliteCallerPathContractException.class,
                () ->
                    SqliteBookMaintenanceFiles.normalizeExistingSource(
                        parentCollision.resolve("book.sqlite"), "bookFilePath"))
            .pathFailure());

    Path privateParent = privateParent("existing-source");
    Path missingLeaf = privateParent.resolve("missing.sqlite");
    assertEquals(
        SqliteCallerPathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE,
        assertThrows(
                SqliteCallerPathContractException.class,
                () ->
                    SqliteBookMaintenanceFiles.normalizeExistingSource(missingLeaf, "bookFilePath"))
            .pathFailure());
  }

  @Test
  void optionalArtifactAdmissionRejectsDirectoriesAndSymlinksButPreservesAnAbsentLeaf()
      throws Exception {
    Path privateParent = privateParent("optional-artifact");
    Path absentTarget = privateParent.resolve("absent.sqlite");
    assertEquals(
        privateParent.toRealPath().resolve(absentTarget.getFileName()),
        SqliteBookMaintenanceFiles.normalizeOptionalArtifact(absentTarget, "bookFilePath"));

    Path directoryTarget = Files.createDirectory(privateParent.resolve("directory.sqlite"));
    assertEquals(
        SqliteCallerPathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE,
        assertThrows(
                SqliteCallerPathContractException.class,
                () ->
                    SqliteBookMaintenanceFiles.normalizeOptionalArtifact(
                        directoryTarget, "bookFilePath"))
            .pathFailure());

    Path regularTarget = privateParent.resolve("regular.sqlite");
    Files.writeString(regularTarget, "book");
    Path symlinkTarget = privateParent.resolve("symlink.sqlite");
    Files.createSymbolicLink(symlinkTarget, regularTarget.getFileName());
    assertEquals(
        SqliteCallerPathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE,
        assertThrows(
                SqliteCallerPathContractException.class,
                () ->
                    SqliteBookMaintenanceFiles.normalizeOptionalArtifact(
                        symlinkTarget, "bookFilePath"))
            .pathFailure());
    assertTrue(Files.isSymbolicLink(symlinkTarget));
    assertTrue(Files.isRegularFile(regularTarget, LinkOption.NOFOLLOW_LINKS));
  }

  @Test
  void optionalArtifactAdmissionMapsCanonicalParentResolutionFailureToTheCallerPath() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parent = fileSystem.path("\\private");
      parent.exists = true;
      parent.regularFile = false;
      parent.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE);
      AclFixturePath target = fileSystem.path("\\private\\book.sqlite");
      target.exists = true;
      target.regularFile = true;
      target.failToRealPathWith(new java.io.IOException("injected canonical target failure"));

      SqliteCallerPathContractException exception =
          assertThrows(
              SqliteCallerPathContractException.class,
              () -> SqliteBookMaintenanceFiles.normalizeOptionalArtifact(target, "bookFilePath"));

      assertEquals(SqliteCallerPathFailure.PARENT_PATH_COLLISION, exception.pathFailure());
    }
  }

  private Path privateParent(String name) throws java.io.IOException {
    Path parent = tempDirectory.resolve(name);
    Files.createDirectories(parent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parent);
    return parent;
  }
}
