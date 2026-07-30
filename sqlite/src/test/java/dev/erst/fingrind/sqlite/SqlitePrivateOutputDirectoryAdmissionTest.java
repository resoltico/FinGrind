package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.core.PrivateOutputDirectory;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Verifies user-visible classification of private-output-directory admission failures. */
class SqlitePrivateOutputDirectoryAdmissionTest {
  @Test
  void ownerOnlyAdmissionMapsUnsupportedFilesystemFailuresOnlyForNewDirectoryCreation() {
    UnsupportedOperationException unsupported =
        new UnsupportedOperationException("injected owner-only creation refusal");

    assertEquals(
        SqliteCallerPathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED,
        SqlitePrivateOutputDirectoryAdmission.failureFor(
            PrivateOutputDirectory.Violation.Kind.OWNER_ONLY_REQUIRED, unsupported, true));
    assertEquals(
        SqliteCallerPathFailure.PARENT_OWNER_ONLY_REQUIRED,
        SqlitePrivateOutputDirectoryAdmission.failureFor(
            PrivateOutputDirectory.Violation.Kind.OWNER_ONLY_REQUIRED, unsupported, false));
    assertEquals(
        SqliteCallerPathFailure.PARENT_OWNER_ONLY_REQUIRED,
        SqlitePrivateOutputDirectoryAdmission.failureFor(
            PrivateOutputDirectory.Violation.Kind.OWNER_ONLY_REQUIRED,
            new IllegalStateException("existing parent is not owner-only"),
            true));
  }

  @Test
  void pathCollisionsAlwaysRemainPathCollisions() {
    assertEquals(
        SqliteCallerPathFailure.PARENT_PATH_COLLISION,
        SqlitePrivateOutputDirectoryAdmission.failureFor(
            PrivateOutputDirectory.Violation.Kind.PATH_COLLISION,
            new UnsupportedOperationException("not a creation outcome"),
            true));
  }

  @Test
  void atomicDirectoryCreationRefusalUsesTheClosedCallerPathFailure() {
    SqliteCallerPathContractException exception =
        SqlitePrivateOutputDirectoryAdmission.atomicOwnerOnlyDirectoryCreationUnsupported(
            Path.of("books/entity.sqlite"));

    assertEquals(
        SqliteCallerPathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED,
        exception.pathFailure());
  }
}
