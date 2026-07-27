package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Direct contracts for nofollow lexical admission of protected-book maintenance paths. */
class SqliteBookMaintenanceFilesTest extends SqliteNativeBridgeTestSupport {
  @Test
  void existingSourceAdmissionDistinguishesMissingParentsFromParentCollisionsAndMissingLeaves()
      throws Exception {
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

  private Path privateParent(String name) throws java.io.IOException {
    Path parent = tempDirectory.resolve(name);
    Files.createDirectories(parent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parent);
    return parent;
  }
}
