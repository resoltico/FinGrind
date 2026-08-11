package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Covers journal-local access facts and fail-closed target identity infrastructure. */
class SqliteJournaledInfrastructureInvariantTest {
  @Test
  void stageAccessIsThreadLocalSingleTargetAndRemovesItsEmptyMap() {
    Path firstStage = Path.of("private", "first-stage");
    Path secondStage = Path.of("private", "second-stage");
    Path firstTarget = Path.of("private", "first.sqlite");
    Path secondTarget = Path.of("private", "second.key");

    assertNull(SqliteJournaledStageAccess.finalTargetForCurrentThread(firstStage));
    SqliteJournaledStageAccess.retain(firstStage, firstTarget);
    SqliteJournaledStageAccess.retain(firstStage, firstTarget);
    SqliteJournaledStageAccess.retain(secondStage, secondTarget);
    assertEquals(
        firstTarget.toAbsolutePath().normalize(),
        SqliteJournaledStageAccess.finalTargetForCurrentThread(firstStage));
    assertThrows(
        IllegalStateException.class,
        () -> SqliteJournaledStageAccess.retain(firstStage, secondTarget));
    SqliteJournaledStageAccess.release(firstStage);
    assertEquals(
        secondTarget.toAbsolutePath().normalize(),
        SqliteJournaledStageAccess.finalTargetForCurrentThread(secondStage));
    SqliteJournaledStageAccess.release(secondStage);
    assertNull(SqliteJournaledStageAccess.finalTargetForCurrentThread(secondStage));
  }

  @Test
  void stageAccessRejectsPathsThatDoNotNameAFile() {
    assertThrows(
        IllegalArgumentException.class,
        () -> SqliteJournaledStageAccess.retain(Path.of("/"), Path.of("private", "target")));
    assertThrows(
        IllegalArgumentException.class,
        () -> SqliteJournaledStageAccess.finalTargetForCurrentThread(Path.of("/")));
  }

  @Test
  void identityFailuresAreMappedToTheAffectedPairMember() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parent = privateParent(fileSystem, "\\targets");
      AclFixturePath book = fileSystem.path("\\targets\\book.sqlite");
      AclFixturePath secret = fileSystem.path("\\targets\\book.key");
      book.failReadAttributesWith(new IOException("book identity failure"));

      SqliteCallerPathContractException failure =
          assertThrows(
              SqliteCallerPathContractException.class,
              () -> SqlitePairTargetIdentity.sameFinalTargetIdentity(book, secret));
      assertEquals(SqliteCallerPathFailure.TARGET_IDENTITY_UNESTABLISHED, failure.pathFailure());
      ProtectedBookMaintenanceRejectionException mapped =
          assertThrows(
              ProtectedBookMaintenanceRejectionException.class,
              () ->
                  SqliteProtectedBookPairTargetSecurity.requirePrepublicationPairTargetAdmission(
                      book,
                      secret,
                      ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                      ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));
      assertEquals(parent, book.getParent());
      assertEquals(
          ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
          assertInstanceOf(
                  ProtectedBookMaintenanceRejection.ArtifactPathInvalid.class, mapped.rejection())
              .artifactRole());

      AclFixturePath secretIdentityFailure = fileSystem.path("\\targets\\secret.key");
      secretIdentityFailure.failReadAttributesWith(new IOException("secret identity failure"));
      ProtectedBookMaintenanceRejectionException secretMapped =
          assertThrows(
              ProtectedBookMaintenanceRejectionException.class,
              () ->
                  SqliteProtectedBookPairTargetSecurity.requirePrepublicationPairTargetAdmission(
                      fileSystem.path("\\targets\\second-book.sqlite"),
                      secretIdentityFailure,
                      ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                      ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));
      assertEquals(
          ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
          assertInstanceOf(
                  ProtectedBookMaintenanceRejection.ArtifactPathInvalid.class,
                  secretMapped.rejection())
              .artifactRole());
    }
  }

  @Test
  void identityFailsClosedWhenExistingLeavesOrDistinctParentsCannotBeCompared() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath sameParent = privateParent(fileSystem, "\\same");
      AclFixturePath first = fileSystem.path("\\same\\book.sqlite");
      AclFixturePath second = fileSystem.path("\\same\\book.key");
      first.exists = true;
      first.regularFile = true;
      second.exists = true;
      second.regularFile = true;
      first.failSameFileWith(new IOException("existing identity failure"));
      assertThrows(
          SqliteCallerPathContractException.class,
          () -> SqlitePairTargetIdentity.sameFinalTargetIdentity(first, second));
      assertEquals(sameParent, first.getParent());

      AclFixturePath firstParent = privateParent(fileSystem, "\\first");
      AclFixturePath secondParent = privateParent(fileSystem, "\\second");
      firstParent.failSameFileWith(new IOException("parent identity failure"));
      assertThrows(
          SqliteCallerPathContractException.class,
          () ->
              SqlitePairTargetIdentity.sameFinalTargetIdentity(
                  fileSystem.path("\\first\\book.sqlite"), fileSystem.path("\\second\\book.key")));
      assertEquals(secondParent, fileSystem.path("\\second"));
    }
  }

  @Test
  void occupiedSecretExceptionKeepsTheExactTargetAndCause() {
    Path target = Path.of("private", "occupied.key");
    IOException cause = new IOException("collision");
    SqliteGeneratedSecretTargetOccupiedException failure =
        new SqliteGeneratedSecretTargetOccupiedException(target, cause);

    assertEquals(target, failure.targetPath());
    assertSame(cause, failure.getCause());
  }

  @Test
  void evidenceScanIgnoresNondirectoryParentsAndFailsClosedWhenEnumerationFails() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath nondirectoryParent = privateParent(fileSystem, "\\not-a-directory");
      nondirectoryParent.regularFile = true;
      assertFalse(
          SqliteProtectedBookPairPublicationEvidenceScanner.hasLegacyResidue(
              fileSystem.path("\\not-a-directory\\book.sqlite"),
              fileSystem.path("\\not-a-directory\\book.key")));

      AclFixturePath unreadableParent = privateParent(fileSystem, "\\unreadable");
      unreadableParent.failNewDirectoryStreamAfterSuccessfulCallsWith(
          3, new IOException("retired evidence enumeration failure"));
      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteProtectedBookPairPublicationEvidenceScanner.hasLegacyResidue(
                      fileSystem.path("\\unreadable\\book.sqlite"),
                      fileSystem.path("\\unreadable\\book.key")));
      assertInstanceOf(IOException.class, failure.getCause());
    }
  }

  @Test
  void keyTargetSecurityRefusesAnExistingParentThatIsNotADirectory() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parent = privateParent(fileSystem, "\\not-a-directory-parent");
      parent.regularFile = true;
      SqliteCallerPathContractException failure =
          assertThrows(
              SqliteCallerPathContractException.class,
              () ->
                  SqliteBookKeyFileSecurity.requireExistingSecureParentDirectory(
                      fileSystem.path("\\not-a-directory-parent\\book.key")));
      assertEquals(SqliteCallerPathFailure.PARENT_PATH_COLLISION, failure.pathFailure());
    }
  }

  private static AclFixturePath privateParent(AclFixtureFileSystem fileSystem, String path) {
    AclFixturePath parent = fileSystem.path(path);
    parent.exists = true;
    parent.regularFile = false;
    parent.posixPermissions =
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);
    return parent;
  }
}
