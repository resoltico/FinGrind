package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Direct physical-identity and conservative absent-leaf admission coverage. */
class SqliteProtectedBookPairPublicationTargetsTest extends SqliteNativeBridgeTestSupport {

  @Test
  void exactRawLeafEqualityIsAConflictWithoutCreatingAnArtifact() throws Exception {
    Path target = tempDirectory.resolve("exact.sqlite");

    assertTrue(SqlitePairTargetIdentity.sameFinalTargetIdentity(target, target));

    assertFalse(Files.exists(target, LinkOption.NOFOLLOW_LINKS));
    assertDirectoryEmpty(tempDirectory);
  }

  @Test
  void existingHardLinkAcrossDistinctParentsIsOnePhysicalTarget() throws Exception {
    Path bookParent = Files.createDirectory(tempDirectory.resolve("book"));
    Path secretParent = Files.createDirectory(tempDirectory.resolve("secret"));
    Path bookTarget = Files.writeString(bookParent.resolve("book.sqlite"), "book");
    Path secretTarget = secretParent.resolve("book.key");
    try {
      Files.createLink(secretTarget, bookTarget);
    } catch (UnsupportedOperationException | FileSystemException unsupported) {
      Assumptions.assumeTrue(
          false, "The test filesystem does not support hard links: " + unsupported.getMessage());
      return;
    }

    assertTrue(SqlitePairTargetIdentity.sameFinalTargetIdentity(bookTarget, secretTarget));
  }

  @Test
  void distinctExistingTargetsRemainDistinctRegardlessOfLeafGrammar() throws Exception {
    Path bookTarget = Files.writeString(tempDirectory.resolve("Book.sqlite"), "book");
    Path secretTarget = Files.writeString(tempDirectory.resolve("book.key"), "secret");

    assertFalse(SqlitePairTargetIdentity.sameFinalTargetIdentity(bookTarget, secretTarget));
  }

  @Test
  void nonportableAbsentCaseSpellingsAreTypedAndLeaveNoArtifact() throws Exception {
    Path bookTarget = tempDirectory.resolve("Book.sqlite");
    Path secretTarget = tempDirectory.resolve("book.sqlite");

    SqliteCallerPathContractException exception =
        assertThrows(
            SqliteCallerPathContractException.class,
            () -> SqlitePairTargetIdentity.sameFinalTargetIdentity(bookTarget, secretTarget));

    assertEquals(
        SqliteCallerPathFailure.PAIR_TARGET_LEAF_PORTABILITY_REQUIRED, exception.pathFailure());
    assertEquals(bookTarget, exception.requestedPath());
    assertFalse(Files.exists(bookTarget, LinkOption.NOFOLLOW_LINKS));
    assertFalse(Files.exists(secretTarget, LinkOption.NOFOLLOW_LINKS));
    assertDirectoryEmpty(tempDirectory);
  }

  @Test
  void nonportableAbsentNormalizationSpellingsAreTypedAndLeaveNoArtifact() throws Exception {
    Path bookTarget = tempDirectory.resolve("caf\u00e9.sqlite");
    Path secretTarget = tempDirectory.resolve(Normalizer.normalize("caf\u00e9.sqlite", Form.NFD));

    SqliteCallerPathContractException exception =
        assertThrows(
            SqliteCallerPathContractException.class,
            () -> SqlitePairTargetIdentity.sameFinalTargetIdentity(bookTarget, secretTarget));

    assertEquals(
        SqliteCallerPathFailure.PAIR_TARGET_LEAF_PORTABILITY_REQUIRED, exception.pathFailure());
    assertEquals(bookTarget, exception.requestedPath());
    assertFalse(Files.exists(bookTarget, LinkOption.NOFOLLOW_LINKS));
    assertFalse(Files.exists(secretTarget, LinkOption.NOFOLLOW_LINKS));
    assertDirectoryEmpty(tempDirectory);
  }

  @Test
  void nonportableWindowsDeviceLeafIsTypedAndLeavesNoArtifact() throws Exception {
    Path bookTarget = tempDirectory.resolve("book.sqlite");
    Path secretTarget = tempDirectory.resolve("con.key");

    SqliteCallerPathContractException exception =
        assertThrows(
            SqliteCallerPathContractException.class,
            () -> SqlitePairTargetIdentity.sameFinalTargetIdentity(bookTarget, secretTarget));

    assertEquals(
        SqliteCallerPathFailure.PAIR_TARGET_LEAF_PORTABILITY_REQUIRED, exception.pathFailure());
    assertEquals(secretTarget, exception.requestedPath());
    assertFalse(Files.exists(bookTarget, LinkOption.NOFOLLOW_LINKS));
    assertFalse(Files.exists(secretTarget, LinkOption.NOFOLLOW_LINKS));
    assertDirectoryEmpty(tempDirectory);
  }

  @Test
  void portableDistinctAbsentLeavesAreAdmittedWithoutCreatingAnArtifact() throws Exception {
    Path bookTarget = tempDirectory.resolve("book.sqlite");
    Path secretTarget = tempDirectory.resolve("book.key");

    assertFalse(SqlitePairTargetIdentity.sameFinalTargetIdentity(bookTarget, secretTarget));

    assertFalse(Files.exists(bookTarget, LinkOption.NOFOLLOW_LINKS));
    assertFalse(Files.exists(secretTarget, LinkOption.NOFOLLOW_LINKS));
    assertDirectoryEmpty(tempDirectory);
  }

  @Test
  void targetIdentityFailsClosedWhenItsProviderCannotReadOrCompareOneTarget() throws Exception {
    try (AclFixtureFileSystem fileSystem =
        AclFixtureFileSystem.withViews(java.util.Set.of("posix"))) {
      AclFixturePath unreadableBookTarget = fileSystem.path("\\targets\\unreadable.sqlite");
      Path unreadableSecretTarget = fileSystem.path("\\targets\\unreadable.key");
      unreadableBookTarget.failReadAttributesWith(new IOException("injected unreadable target"));

      assertTargetIdentityUnestablished(
          unreadableBookTarget,
          assertThrows(
              SqliteCallerPathContractException.class,
              () ->
                  SqlitePairTargetIdentity.sameFinalTargetIdentity(
                      unreadableBookTarget, unreadableSecretTarget)));

      AclFixturePath existingBookTarget = fileSystem.path("\\targets\\existing.sqlite");
      existingBookTarget.exists = true;
      existingBookTarget.regularFile = true;
      AclFixturePath existingSecretTarget = fileSystem.path("\\targets\\existing.key");
      existingSecretTarget.exists = true;
      existingSecretTarget.regularFile = true;
      existingBookTarget.failSameFileWith(new IOException("injected target identity failure"));

      assertTargetIdentityUnestablished(
          existingBookTarget,
          assertThrows(
              SqliteCallerPathContractException.class,
              () ->
                  SqlitePairTargetIdentity.sameFinalTargetIdentity(
                      existingBookTarget, existingSecretTarget)));

      AclFixturePath bookParent = fileSystem.path("\\book-parent");
      bookParent.exists = true;
      bookParent.regularFile = false;
      AclFixturePath secretParent = fileSystem.path("\\secret-parent");
      secretParent.exists = true;
      secretParent.regularFile = false;
      bookParent.failSameFileWith(new IOException("injected parent identity failure"));
      Path absentBookTarget = bookParent.resolve("book.sqlite");
      Path absentSecretTarget = secretParent.resolve("book.key");

      assertTargetIdentityUnestablished(
          absentBookTarget,
          assertThrows(
              SqliteCallerPathContractException.class,
              () ->
                  SqlitePairTargetIdentity.sameFinalTargetIdentity(
                      absentBookTarget, absentSecretTarget)));
    }
  }

  @Test
  void distinctPhysicalParentsDoNotNeedThePortableSharedParentLeafGrammar() throws Exception {
    Path bookParent = Files.createDirectory(tempDirectory.resolve("book"));
    Path secretParent = Files.createDirectory(tempDirectory.resolve("secret"));
    Path bookTarget = bookParent.resolve("caf\u00e9.sqlite");
    Path secretTarget = secretParent.resolve(Normalizer.normalize("caf\u00e9.key", Form.NFD));

    assertFalse(SqlitePairTargetIdentity.sameFinalTargetIdentity(bookTarget, secretTarget));

    assertDirectoryEmpty(bookParent);
    assertDirectoryEmpty(secretParent);
  }

  @Test
  void generatedSecretPreparation_preservesTypedPathRejectionsAndUnexpectedIoFailures() {
    Path targetPath = tempDirectory.resolve("target.key");
    SqliteCallerPathContractException pathFailure =
        new SqliteCallerPathContractException(
            targetPath,
            SqliteCallerPathFailure.TARGET_OWNER_ONLY_REQUIRED,
            "target must be owner-only");

    ProtectedBookMaintenanceRejectionException rejection =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                SqliteProtectedBookPairPublicationTargets.prepareGeneratedSecretTarget(
                    targetPath,
                    ProtectedBookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET,
                    ignored -> {
                      throw pathFailure;
                    }));
    ProtectedBookMaintenanceRejection.ArtifactPathInvalid typedRejection =
        assertInstanceOf(
            ProtectedBookMaintenanceRejection.ArtifactPathInvalid.class, rejection.rejection());
    assertEquals(
        ProtectedBookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET, typedRejection.artifactRole());
    assertEquals(
        SqliteCallerPathFailure.TARGET_OWNER_ONLY_REQUIRED.maintenanceFailure(),
        typedRejection.pathFailure());

    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteProtectedBookPairPublicationTargets.prepareGeneratedSecretTarget(
                targetPath,
                ignored -> {
                  throw new IOException("injected preparation failure");
                }));
  }

  @Test
  void reservations_translateOccupiedAndIoOutcomesByArtifactRole() throws Exception {
    Path bookTarget = tempDirectory.resolve("backup.sqlite");
    Path secretTarget = tempDirectory.resolve("backup.key");

    ProtectedBookMaintenanceRejectionException bookCollision =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                SqliteProtectedBookPairPublicationTargets.reserveAbsentBookTarget(
                    bookTarget,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                    ignored -> {
                      throw new FileAlreadyExistsException(bookTarget.toString());
                    }));
    assertEquals(
        bookTarget,
        assertInstanceOf(
                ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists.class,
                bookCollision.rejection())
            .backupFilePath());
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteProtectedBookPairPublicationTargets.reserveAbsentBookTarget(
                bookTarget,
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ignored -> {
                  throw new IOException("injected reservation failure");
                }));

    assertEquals(
        secretTarget,
        assertThrows(
                SqliteGeneratedSecretTargetOccupiedException.class,
                () ->
                    SqliteProtectedBookPairPublicationTargets.reserveAbsentSecretTarget(
                        secretTarget,
                        ignored -> {
                          throw new FileAlreadyExistsException(secretTarget.toString());
                        }))
            .targetPath());
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteProtectedBookPairPublicationTargets.reserveAbsentSecretTarget(
                secretTarget,
                ignored -> {
                  throw new IOException("injected secret reservation failure");
                }));
  }

  @Test
  void preparationTranslatesAnAlreadyOccupiedGeneratedSecretBeforeRetainingResources()
      throws Exception {
    Path secretTarget = Files.writeString(tempDirectory.resolve("occupied-preparation.key"), "key");
    Path bookTarget = tempDirectory.resolve("occupied-preparation.sqlite");

    try (SqlitePairPublicationPreparationResources resources =
        new SqlitePairPublicationPreparationResources()) {
      ProtectedBookMaintenanceRejectionException rejection =
          assertThrows(
              ProtectedBookMaintenanceRejectionException.class,
              () ->
                  SqliteProtectedBookPairPublicationTargets.prepareWithHeldLeases(
                      resources,
                      secretTarget,
                      bookTarget,
                      dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore
                          .RestoredBookTargetPolicy.REQUIRE_ABSENT,
                      ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                      ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));

      assertEquals(
          secretTarget,
          assertInstanceOf(
                  ProtectedBookMaintenanceRejection.SecretTargetOccupied.class,
                  rejection.rejection())
              .secretTargetPath());
    }
  }

  @Test
  void occupiedBookTargetRejection_acceptsOnlyBookArtifactRoles() {
    Path targetPath = tempDirectory.resolve("target.sqlite");

    assertInstanceOf(
        ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists.class,
        SqliteProtectedBookPairPublicationTargets.occupiedBookTargetRejection(
            ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET, targetPath));
    assertInstanceOf(
        ProtectedBookMaintenanceRejection.BookDestinationOccupied.class,
        SqliteProtectedBookPairPublicationTargets.occupiedBookTargetRejection(
            ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET, targetPath));
    assertInstanceOf(
        ProtectedBookMaintenanceRejection.BookDestinationOccupied.class,
        SqliteProtectedBookPairPublicationTargets.occupiedBookTargetRejection(
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, targetPath));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqliteProtectedBookPairPublicationTargets.occupiedBookTargetRejection(
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET, targetPath));
  }

  @Test
  void prepublicationAdmissionRejectsConflictsAndMapsEachInvalidTargetToItsArtifactRole()
      throws Exception {
    Path bookTarget = tempDirectory.resolve("backup.sqlite");
    Path secretTarget = tempDirectory.resolve("backup.key");

    SqliteProtectedBookPairPublicationTargets.requirePrepublicationPairTargetAdmission(
        bookTarget,
        secretTarget,
        ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
        ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET);

    ProtectedBookMaintenanceRejectionException conflict =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                SqliteProtectedBookPairPublicationTargets.requirePrepublicationPairTargetAdmission(
                    bookTarget,
                    bookTarget,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));
    assertInstanceOf(
        ProtectedBookMaintenanceRejection.PairTargetsConflict.class, conflict.rejection());

    Path nonDirectoryParent = Files.writeString(tempDirectory.resolve("not-a-directory"), "data");
    Path invalidBookTarget = nonDirectoryParent.resolve("backup.sqlite");
    Path invalidSecretTarget = nonDirectoryParent.resolve("backup.key");
    assertArtifactPathFailure(
        assertThrows(
            RuntimeException.class,
            () ->
                SqliteProtectedBookPairPublicationTargets.requireRecoveryTargetSecurity(
                    invalidBookTarget,
                    secretTarget,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET)),
        ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
        SqliteCallerPathFailure.PARENT_PATH_COLLISION);
    assertArtifactPathFailure(
        assertThrows(
            RuntimeException.class,
            () ->
                SqliteProtectedBookPairPublicationTargets.requirePrepublicationPairTargetAdmission(
                    bookTarget,
                    invalidSecretTarget,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET)),
        ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
        SqliteCallerPathFailure.PARENT_PATH_COLLISION);
    assertArtifactPathFailure(
        assertThrows(
            RuntimeException.class,
            () ->
                SqliteProtectedBookPairPublicationTargets.requirePrepublicationPairTargetAdmission(
                    tempDirectory.resolve("Book.sqlite"),
                    tempDirectory.resolve("book.sqlite"),
                    ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET)),
        ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
        SqliteCallerPathFailure.PAIR_TARGET_LEAF_PORTABILITY_REQUIRED);
    assertArtifactPathFailure(
        assertThrows(
            RuntimeException.class,
            () ->
                SqliteProtectedBookPairPublicationTargets.requirePrepublicationPairTargetAdmission(
                    tempDirectory.resolve("book.sqlite"),
                    tempDirectory.resolve("Book.sqlite"),
                    ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET)),
        ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
        SqliteCallerPathFailure.PAIR_TARGET_LEAF_PORTABILITY_REQUIRED);
  }

  @Test
  void capabilityAcquisitionFailuresPreserveTheExactArtifactAndPrimitiveRejection()
      throws Exception {
    Path bookTarget = tempDirectory.resolve("backup.sqlite");
    Path secretTarget = tempDirectory.resolve("backup.key");

    assertArtifactPathFailure(
        SqliteProtectedBookPairPublicationTargets.capabilityAcquisitionFailure(
            unsupportedWitnessFailure(
                SqlitePublicationCapabilityWitness.Requirement.noReplace(bookTarget)),
            bookTarget,
            secretTarget,
            dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy
                .REQUIRE_ABSENT,
            ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
            ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET),
        ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
        SqliteCallerPathFailure.ATOMIC_BOOK_PUBLICATION_UNSUPPORTED);
    assertArtifactPathFailure(
        SqliteProtectedBookPairPublicationTargets.capabilityAcquisitionFailure(
            unsupportedWitnessFailure(
                SqlitePublicationCapabilityWitness.Requirement.atomicReplace(bookTarget)),
            bookTarget,
            secretTarget,
            dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy
                .REPLACE_SELECTED,
            ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET,
            ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET),
        ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET,
        SqliteCallerPathFailure.ATOMIC_BOOK_REPLACEMENT_UNSUPPORTED);
    assertArtifactPathFailure(
        SqliteProtectedBookPairPublicationTargets.capabilityAcquisitionFailure(
            unsupportedWitnessFailure(
                SqlitePublicationCapabilityWitness.Requirement.noReplace(secretTarget)),
            bookTarget,
            secretTarget,
            dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy
                .REQUIRE_ABSENT,
            ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
            ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET),
        ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
        SqliteCallerPathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED);

    SqlitePublicationCapabilityWitness.AcquisitionFailure unexpectedFailure =
        unexpectedWitnessFailure(
            SqlitePublicationCapabilityWitness.Requirement.noReplace(bookTarget));
    IllegalStateException genericFailure =
        assertInstanceOf(
            IllegalStateException.class,
            SqliteProtectedBookPairPublicationTargets.capabilityAcquisitionFailure(
                unexpectedFailure,
                bookTarget,
                secretTarget,
                dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore
                    .RestoredBookTargetPolicy.REQUIRE_ABSENT,
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));
    assertEquals(unexpectedFailure, genericFailure.getCause());

    SqlitePublicationCapabilityWitness.AcquisitionFailure unadmittedFailure =
        unexpectedWitnessFailure(
            SqlitePublicationCapabilityWitness.Requirement.noReplace(
                tempDirectory.resolve("unadmitted.key")));
    IllegalStateException unadmittedTargetFailure =
        assertInstanceOf(
            IllegalStateException.class,
            SqliteProtectedBookPairPublicationTargets.capabilityAcquisitionFailure(
                unadmittedFailure,
                bookTarget,
                secretTarget,
                dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore
                    .RestoredBookTargetPolicy.REQUIRE_ABSENT,
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));
    assertEquals(unadmittedFailure, unadmittedTargetFailure.getCause());
  }

  private static void assertArtifactPathFailure(
      RuntimeException failure,
      ProtectedBookMaintenanceArtifactRole expectedRole,
      SqliteCallerPathFailure expectedFailure) {
    ProtectedBookMaintenanceRejectionException rejection =
        assertInstanceOf(ProtectedBookMaintenanceRejectionException.class, failure);
    ProtectedBookMaintenanceRejection.ArtifactPathInvalid typedRejection =
        assertInstanceOf(
            ProtectedBookMaintenanceRejection.ArtifactPathInvalid.class, rejection.rejection());
    assertEquals(expectedRole, typedRejection.artifactRole());
    assertEquals(expectedFailure.maintenanceFailure(), typedRejection.pathFailure());
  }

  private static void assertTargetIdentityUnestablished(
      Path expectedTargetPath, SqliteCallerPathContractException exception) {
    assertEquals(expectedTargetPath, exception.requestedPath());
    assertEquals(SqliteCallerPathFailure.TARGET_IDENTITY_UNESTABLISHED, exception.pathFailure());
  }

  private static SqlitePublicationCapabilityWitness.AcquisitionFailure unsupportedWitnessFailure(
      SqlitePublicationCapabilityWitness.Requirement requirement) {
    return assertThrows(
        SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
        () ->
            SqlitePublicationCapabilityWitness.acquire(
                List.of(requirement),
                (ignoredFinalPath, ignoredStagedPath) -> {
                  throw new UnsupportedOperationException("injected unsupported primitive");
                },
                (ignoredSourcePath, ignoredTargetPath) -> {
                  throw new UnsupportedOperationException("injected unsupported primitive");
                }));
  }

  private static SqlitePublicationCapabilityWitness.AcquisitionFailure unexpectedWitnessFailure(
      SqlitePublicationCapabilityWitness.Requirement requirement) {
    return assertThrows(
        SqlitePublicationCapabilityWitness.AcquisitionFailure.class,
        () ->
            SqlitePublicationCapabilityWitness.acquire(
                List.of(requirement),
                (ignoredFinalPath, ignoredStagedPath) -> {
                  throw new IOException("injected unexpected primitive failure");
                },
                (ignoredSourcePath, ignoredTargetPath) -> {
                  throw new IOException("injected unexpected primitive failure");
                }));
  }

  private static void assertDirectoryEmpty(Path directory) throws java.io.IOException {
    try (var children = Files.list(directory)) {
      assertFalse(children.findAny().isPresent(), () -> "Unexpected residue in " + directory);
    }
  }
}
