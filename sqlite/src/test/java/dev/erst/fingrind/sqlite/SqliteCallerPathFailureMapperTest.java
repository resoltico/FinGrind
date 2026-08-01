package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenancePathFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Coverage for published path-failure mapping across book, key-file, and maintenance surfaces. */
class SqliteCallerPathFailureMapperTest {
  private static final Path REQUESTED_PATH = Path.of("books/entity.sqlite");
  private static final Map<SqliteCallerPathFailure, String> MESSAGE_SUFFIXES =
      Map.ofEntries(
          Map.entry(
              SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY,
              " path requires a parent directory."),
          Map.entry(
              SqliteCallerPathFailure.PARENT_PATH_COLLISION,
              " path cannot use a parent path that already exists as a non-directory entry or symlink."),
          Map.entry(
              SqliteCallerPathFailure.PARENT_OWNER_ACCESS_REQUIRED,
              " path requires a parent directory that the owner can traverse and write."),
          Map.entry(
              SqliteCallerPathFailure.PARENT_OWNER_ONLY_REQUIRED,
              " path requires an owner-only parent directory."),
          Map.entry(
              SqliteCallerPathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE,
              " path must resolve to a regular non-symlink file."),
          Map.entry(
              SqliteCallerPathFailure.TARGET_OWNER_ONLY_REQUIRED,
              " path must already use owner-only permissions."),
          Map.entry(
              SqliteCallerPathFailure.TARGET_IDENTITY_UNESTABLISHED,
              " path could not establish a distinct final-target identity."),
          Map.entry(
              SqliteCallerPathFailure.SOURCE_ARTIFACT_IDENTITY_DUPLICATED,
              " path duplicates another selected source artifact's physical identity."),
          Map.entry(
              SqliteCallerPathFailure.SOURCE_ARTIFACT_IDENTITY_CHANGED,
              " source changed after its physical identity was admitted for this maintenance workflow."),
          Map.entry(
              SqliteCallerPathFailure.UNSUPPORTED_SECURE_FILESYSTEM,
              " path must live on a filesystem that supports POSIX owner-only permissions or Windows owner-only ACLs."),
          Map.entry(
              SqliteCallerPathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED,
              " path must live on a filesystem that supports atomically creating owner-only FinGrind protocol files."),
          Map.entry(
              SqliteCallerPathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED,
              " path must live on a filesystem that supports atomic no-replace secret publication."),
          Map.entry(
              SqliteCallerPathFailure.ATOMIC_BOOK_PUBLICATION_UNSUPPORTED,
              " path must live on a filesystem that supports atomic no-replace protected-book publication."),
          Map.entry(
              SqliteCallerPathFailure.ATOMIC_BOOK_REPLACEMENT_UNSUPPORTED,
              " path must live on a filesystem that supports atomic protected-book replacement."));

  @Test
  void invalidBookFilePath_mapsEveryCallerPathFailure() {
    for (SqliteCallerPathFailure pathFailure : SqliteCallerPathFailure.values()) {
      assertBookFileMapping(pathFailure, expectedBookFileMessage(pathFailure));
    }
  }

  @Test
  void invalidBookKeyFile_mapsEveryCallerPathFailure() {
    for (SqliteCallerPathFailure pathFailure : SqliteCallerPathFailure.values()) {
      assertBookKeyFileMapping(pathFailure, expectedBookKeyFileMessage(pathFailure));
    }
  }

  @Test
  void maintenanceRejection_mapsEveryCallerPathFailureToTheLocalMaintenanceVocabulary() {
    for (SqliteCallerPathFailure pathFailure : SqliteCallerPathFailure.values()) {
      assertMaintenanceMapping(
          pathFailure, ProtectedBookMaintenancePathFailure.valueOf(pathFailure.name()));
    }
  }

  @Test
  void publicPathRepairHints_useFinalOperatorFacingArticles() {
    assertEquals(
        "Choose a regular non-symlink protected-book path beneath a private owner-only parent directory. If the parent directory already exists, tighten it first; otherwise target a missing private directory so FinGrind can create it securely, then rerun the command.",
        SqliteBookFileSecuritySupport.invalidBookFilePathHint());
    assertEquals(
        "Create a private owner-only parent directory yourself, then choose a regular non-symlink key file path beneath it and rerun the command.",
        SqliteBookKeyFileSecuritySupport.generalKeyFileHint());
  }

  private static void assertBookFileMapping(
      SqliteCallerPathFailure pathFailure, String expectedMessage) {
    var failure = SqliteCallerPathFailureMapper.invalidBookFilePath(exceptionFor(pathFailure));
    assertEquals(ContractErrors.Descriptor.INVALID_BOOK_FILE_PATH, failure.descriptor());
    assertEquals(expectedMessage, failure.message());
    assertPathIsTypedAndAbsentFromMessage(failure);
  }

  private static void assertBookKeyFileMapping(
      SqliteCallerPathFailure pathFailure, String expectedMessage) {
    var failure = SqliteCallerPathFailureMapper.invalidBookKeyFile(exceptionFor(pathFailure));
    assertEquals(ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE, failure.descriptor());
    assertEquals(expectedMessage, failure.message());
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

  private static String expectedBookFileMessage(SqliteCallerPathFailure pathFailure) {
    return expectedMessage("protected-book", pathFailure);
  }

  private static String expectedBookKeyFileMessage(SqliteCallerPathFailure pathFailure) {
    return expectedMessage("book key file", pathFailure);
  }

  private static String expectedMessage(String artifactKind, SqliteCallerPathFailure pathFailure) {
    return "The FinGrind "
        + artifactKind
        + Objects.requireNonNull(MESSAGE_SUFFIXES.get(pathFailure), "message suffix");
  }
}
