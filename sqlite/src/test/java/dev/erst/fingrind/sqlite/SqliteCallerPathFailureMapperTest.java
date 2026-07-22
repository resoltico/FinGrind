package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenancePathFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Coverage for published path-failure mapping across book, key-file, and maintenance surfaces. */
class SqliteCallerPathFailureMapperTest {
  private static final Path REQUESTED_PATH = Path.of("books/entity.sqlite");

  @Test
  void invalidBookFilePath_mapsEveryCallerPathFailure() {
    assertBookFileMapping(
        SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY, "requires a parent directory");
    assertBookFileMapping(
        SqliteCallerPathFailure.PARENT_PATH_COLLISION, "already exists as a non-directory");
    assertBookFileMapping(
        SqliteCallerPathFailure.PARENT_OWNER_ACCESS_REQUIRED, "owner can traverse and write");
    assertBookFileMapping(
        SqliteCallerPathFailure.PARENT_OWNER_ONLY_REQUIRED,
        "requires an owner-only parent directory");
    assertBookFileMapping(
        SqliteCallerPathFailure.TARGET_MUST_BE_REGULAR_NON_SYMLINK_FILE,
        "a regular non-symlink file");
    assertBookFileMapping(
        SqliteCallerPathFailure.UNSUPPORTED_SECURE_FILESYSTEM,
        "supports POSIX owner-only permissions or Windows owner-only ACLs");
    assertBookFileMapping(
        SqliteCallerPathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED,
        "supports atomic no-replace secret publication");
  }

  @Test
  void invalidBookKeyFile_mapsEveryCallerPathFailure() {
    assertBookKeyFileMapping(
        SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY, "requires a parent directory");
    assertBookKeyFileMapping(
        SqliteCallerPathFailure.PARENT_PATH_COLLISION, "already exists as a non-directory");
    assertBookKeyFileMapping(
        SqliteCallerPathFailure.PARENT_OWNER_ACCESS_REQUIRED, "owner can traverse and write");
    assertBookKeyFileMapping(
        SqliteCallerPathFailure.PARENT_OWNER_ONLY_REQUIRED,
        "requires an owner-only parent directory");
    assertBookKeyFileMapping(
        SqliteCallerPathFailure.TARGET_MUST_BE_REGULAR_NON_SYMLINK_FILE,
        "a regular non-symlink file");
    assertBookKeyFileMapping(
        SqliteCallerPathFailure.UNSUPPORTED_SECURE_FILESYSTEM,
        "supports POSIX owner-only permissions or Windows owner-only ACLs");
    assertBookKeyFileMapping(
        SqliteCallerPathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED,
        "supports atomic no-replace secret publication");
  }

  @Test
  void maintenanceRejection_mapsEveryCallerPathFailureToTheLocalMaintenanceVocabulary() {
    assertMaintenanceMapping(
        SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY,
        ProtectedBookMaintenancePathFailure.MISSING_PARENT_DIRECTORY);
    assertMaintenanceMapping(
        SqliteCallerPathFailure.PARENT_PATH_COLLISION,
        ProtectedBookMaintenancePathFailure.PARENT_PATH_COLLISION);
    assertMaintenanceMapping(
        SqliteCallerPathFailure.PARENT_OWNER_ACCESS_REQUIRED,
        ProtectedBookMaintenancePathFailure.PARENT_OWNER_ACCESS_REQUIRED);
    assertMaintenanceMapping(
        SqliteCallerPathFailure.PARENT_OWNER_ONLY_REQUIRED,
        ProtectedBookMaintenancePathFailure.PARENT_OWNER_ONLY_REQUIRED);
    assertMaintenanceMapping(
        SqliteCallerPathFailure.TARGET_MUST_BE_REGULAR_NON_SYMLINK_FILE,
        ProtectedBookMaintenancePathFailure.TARGET_MUST_BE_REGULAR_NON_SYMLINK_FILE);
    assertMaintenanceMapping(
        SqliteCallerPathFailure.UNSUPPORTED_SECURE_FILESYSTEM,
        ProtectedBookMaintenancePathFailure.UNSUPPORTED_SECURE_FILESYSTEM);
    assertMaintenanceMapping(
        SqliteCallerPathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED,
        ProtectedBookMaintenancePathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED);
  }

  @Test
  void publicPathRepairHints_useFinalOperatorFacingArticles() {
    assertEquals(
        "Choose a regular non-symlink protected-book path beneath a private owner-only parent directory. If the parent directory already exists, tighten it first; otherwise target a missing private directory so FinGrind can create it securely, then rerun the command.",
        SqliteBookFileSecuritySupport.invalidBookFilePathHint());
    assertEquals(
        "Create a private owner-only parent directory yourself, tighten it if needed, then choose a regular non-symlink key file path beneath it and rerun the command.",
        SqliteBookKeyFileSecuritySupport.generalKeyFileHint());
  }

  private static void assertBookFileMapping(
      SqliteCallerPathFailure pathFailure, String expectedMessageFragment) {
    var failure = SqliteCallerPathFailureMapper.invalidBookFilePath(exceptionFor(pathFailure));
    assertEquals(ContractErrors.Descriptor.INVALID_BOOK_FILE_PATH, failure.descriptor());
    assertTrue(failure.message().contains(expectedMessageFragment), failure.message());
    assertPathIsTypedAndAbsentFromMessage(failure);
  }

  private static void assertBookKeyFileMapping(
      SqliteCallerPathFailure pathFailure, String expectedMessageFragment) {
    var failure = SqliteCallerPathFailureMapper.invalidBookKeyFile(exceptionFor(pathFailure));
    assertEquals(ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE, failure.descriptor());
    assertTrue(failure.message().contains(expectedMessageFragment), failure.message());
    assertPathIsTypedAndAbsentFromMessage(failure);
  }

  private static void assertPathIsTypedAndAbsentFromMessage(
      dev.erst.fingrind.contract.runtime.ContractFailure failure) {
    var paths = failure.paths();
    assertNotNull(paths);
    Path expected = REQUESTED_PATH.toAbsolutePath().normalize();
    assertEquals(expected, paths.path());
    assertEquals(java.util.List.of(), paths.relatedPaths());
    assertFalse(failure.message().contains(expected.toString()), failure.message());
  }

  private static void assertMaintenanceMapping(
      SqliteCallerPathFailure pathFailure,
      ProtectedBookMaintenancePathFailure expectedMaintenanceFailure) {
    ProtectedBookMaintenanceRejection.ArtifactPathInvalid rejection =
        SqliteCallerPathFailureMapper.maintenanceRejection(
            ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET, exceptionFor(pathFailure));
    assertEquals(ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET, rejection.artifactRole());
    assertEquals(REQUESTED_PATH, rejection.artifactPath());
    assertEquals(expectedMaintenanceFailure, rejection.pathFailure());
  }

  private static SqliteCallerPathContractException exceptionFor(
      SqliteCallerPathFailure pathFailure) {
    return new SqliteCallerPathContractException(REQUESTED_PATH, pathFailure, pathFailure.name());
  }
}
