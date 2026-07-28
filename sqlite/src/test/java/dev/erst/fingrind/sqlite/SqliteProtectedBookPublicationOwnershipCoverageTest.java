package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.HeldLease;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.PreparedPairPublication;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationFailureOutcome;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Direct branch coverage for the ownership handles behind protected-book pair publication. */
class SqliteProtectedBookPublicationOwnershipCoverageTest
    extends SqliteArtifactPublicationTestSupport {

  @Test
  void reservationPublishesOwnedStagesAndRejectsExternalOrMissingClaims() throws Exception {
    Path publishedTarget = absentTarget("reservation/published.key");
    try (SqliteOwnedDestinationReservation reservation =
        SqliteOwnedDestinationReservation.reserve(publishedTarget)) {
      assertEquals(publishedTarget, reservation.finalPath());
      SqliteOwnedStagedArtifact staged =
          writeStage(publishedTarget, ".stage-", ".tmp", "published");
      reservation.publishRetainingStage(staged, Files::createLink);
      assertEquals("published", Files.readString(publishedTarget));
      staged.releaseRetained();
    }

    Path occupiedTarget = absentTarget("reservation/occupied.key");
    try (SqliteOwnedDestinationReservation reservation =
        SqliteOwnedDestinationReservation.reserve(occupiedTarget)) {
      SqliteOwnedStagedArtifact staged = writeStage(occupiedTarget, ".stage-", ".tmp", "staged");
      Files.writeString(occupiedTarget, "external");
      assertThrows(
          FileAlreadyExistsException.class,
          () -> reservation.publishRetainingStage(staged, Files::createLink));
      staged.releaseRetained();
    }
    assertEquals("external", Files.readString(occupiedTarget));

    Path missingTarget = absentTarget("reservation/missing.key");
    try (SqliteOwnedDestinationReservation reservation =
        SqliteOwnedDestinationReservation.reserve(missingTarget)) {
      SqliteOwnedStagedArtifact staged = writeStage(missingTarget, ".stage-", ".tmp", "staged");
      assertThrows(
          IOException.class,
          () ->
              reservation.publishRetainingStage(
                  staged,
                  (ignoredFinalPath, ignoredStagedPath) -> {
                    throw new IOException("simulated publication I/O failure");
                  }));
      staged.releaseRetained();
    }
  }

  @Test
  void reservationReleasesCurrentClaimsAndRejectsUseAfterClose() throws Exception {
    Path target = absentTarget("reservation/close.key");
    try (SqliteOwnedDestinationReservation reservation =
        SqliteOwnedDestinationReservation.reserve(target)) {
      reservation.close();
      reservation.close();

      assertFalse(Files.exists(target));
      assertThrows(IllegalStateException.class, () -> reservation.createStage(".stage-", ".tmp"));
    }

    Files.writeString(target, "already-present");
    assertThrows(
        FileAlreadyExistsException.class, () -> SqliteOwnedDestinationReservation.reserve(target));
  }

  @Test
  void pairPublishersTranslateReservationRacesToGeneratedSecretRejections() throws Exception {
    Path backupKeyTarget = absentTarget("pair-publisher/backup.key");
    try (SqliteOwnedDestinationReservation backupKeyReservation =
        SqliteOwnedDestinationReservation.reserve(backupKeyTarget)) {
      SqliteOwnedStagedArtifact backupKeyStage =
          writeStage(backupKeyTarget, ".stage-", ".tmp", "backup-key");
      Files.writeString(backupKeyTarget, "external");
      SqliteBackupPairPublication backupPublication =
          new SqliteBackupPairPublication(
              Files::createLink,
              Files::createLink,
              null,
              backupKeyReservation,
              witnessesForNoReplace(backupKeyTarget));

      assertThrows(
          SqliteGeneratedSecretTargetOccupiedException.class,
          () ->
              backupPublication.publishKey(
                  backupKeyStage, backupKeyTarget, backupKeyTarget, () -> {}, () -> {}));
      backupPublication.closeReservations();
      backupKeyStage.releaseRetained();
    }

    Path restoredKeyTarget = absentTarget("pair-publisher/restored.key");
    try (SqliteOwnedDestinationReservation restoredKeyReservation =
        SqliteOwnedDestinationReservation.reserve(restoredKeyTarget)) {
      SqliteOwnedStagedArtifact restoredKeyStage =
          writeStage(restoredKeyTarget, ".stage-", ".tmp", "restored-key");
      Files.writeString(restoredKeyTarget, "external");
      SqliteRestoredBookPairPublication restoredPublication =
          new SqliteRestoredBookPairPublication(
              absentTarget("pair-publisher/restored.sqlite"),
              restoredKeyTarget,
              RestoredBookTargetPolicy.REQUIRE_ABSENT,
              SqliteRestoredBookPairPublication.defaultOperators(),
              null,
              restoredKeyReservation,
              witnessesForNoReplace(restoredKeyTarget));

      assertThrows(
          SqliteGeneratedSecretTargetOccupiedException.class,
          () -> restoredPublication.publishSecret(restoredKeyStage, () -> {}, () -> {}));
      restoredPublication.closeReservations();
      restoredKeyStage.releaseRetained();
    }
  }

  @Test
  void pairPublishersReleaseTheirWitnessesWhenNoDestinationReservationWasNeeded()
      throws Exception {
    Path backupTarget = absentTarget("pair-publisher/unreserved-backup.key");
    SqliteBackupPairPublication backupPublication =
        new SqliteBackupPairPublication(
            Files::createLink,
            Files::createLink,
            null,
            null,
            witnessesForNoReplace(backupTarget));
    backupPublication.closeReservations();

    Path restoredBookTarget = absentTarget("pair-publisher/unreserved-restored.sqlite");
    Path restoredSecretTarget = absentTarget("pair-publisher/unreserved-restored.key");
    SqliteRestoredBookPairPublication restoredPublication =
        new SqliteRestoredBookPairPublication(
            restoredBookTarget,
            restoredSecretTarget,
            RestoredBookTargetPolicy.REPLACE_SELECTED,
            SqliteRestoredBookPairPublication.defaultOperators(),
            null,
            null,
            witnessesForNoReplace(restoredSecretTarget));
    restoredPublication.closeReservations();
  }

  @Test
  void preparedPublicationCreatesUnreservedBookStagesAndReleasesTransferredResources()
      throws Exception {
    Path bookTarget = absentTarget("prepared/book.sqlite");
    Path secretTarget = absentTarget("prepared/book.key");
    CountingLease bookLease = new CountingLease(bookTarget);
    CountingLease secretLease = new CountingLease(secretTarget);
    try (SqliteOwnedDestinationReservation secretReservation =
            SqliteOwnedDestinationReservation.reserve(secretTarget);
        CountingLease ignoredBookLease = bookLease;
        CountingLease ignoredSecretLease = secretLease;
        SqlitePreparedPairPublication prepared =
            new SqlitePreparedPairPublication(
                bookTarget,
                secretTarget,
                RestoredBookTargetPolicy.REPLACE_SELECTED,
                null,
                secretReservation,
                bookLease,
                secretLease,
                witnessesForPair(
                    bookTarget,
                    SqlitePublicationCapabilityWitness.PrimitiveKind.ATOMIC_REPLACE,
                    secretTarget))) {
      SqliteOwnedStagedArtifact bookStage = prepared.createBookStage(".stage-", ".tmp");
      bookStage.releaseRetained();
    }

    assertEquals(1, bookLease.closeCount().get());
    assertEquals(1, secretLease.closeCount().get());
    assertFalse(Files.exists(secretTarget));
  }

  @Test
  void preparationResourceOwnershipRejectsDuplicateCaptures() {
    CountingLease lease = new CountingLease(tempDirectory.resolve("resources/book.sqlite"));
    try (CountingLease ignoredLease = lease;
        SqlitePairPublicationPreparationResources resources =
            new SqlitePairPublicationPreparationResources()) {
      resources.holdBookTargetLease(lease);

      assertThrows(
          IllegalStateException.class,
          () ->
              resources.holdBookTargetLease(
                  new CountingLease(tempDirectory.resolve("other.sqlite"))));
    }
    assertEquals(1, lease.closeCount().get());
  }

  @Test
  void pairPreparationMapsReservationAndArtifactRoleFailuresDeterministically() throws Exception {
    Path occupiedBookTarget = absentTarget("preparation/occupied.sqlite");
    Files.writeString(occupiedBookTarget, "occupied");
    ProtectedBookMaintenanceRejectionException occupiedBackup =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                SqliteProtectedBookPairPublicationPreparation.reserveAbsentBookTarget(
                    occupiedBookTarget, ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET));
    assertInstanceOf(
        ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists.class,
        occupiedBackup.rejection());
    assertInstanceOf(
        ProtectedBookMaintenanceRejection.BookDestinationOccupied.class,
        SqliteProtectedBookPairPublicationPreparation.occupiedBookTargetRejection(
            ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET, occupiedBookTarget));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqliteProtectedBookPairPublicationPreparation.occupiedBookTargetRejection(
                ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE, occupiedBookTarget));

    SqliteCallerPathContractException callerPathFailure =
        new SqliteCallerPathContractException(
            occupiedBookTarget,
            SqliteCallerPathFailure.PARENT_PATH_COLLISION,
            "test caller path rejection");
    ProtectedBookMaintenanceRejectionException mapped =
        SqliteProtectedBookPairPublicationPreparation.secretTargetPathRejection(
            ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET, callerPathFailure);
    assertEquals(
        ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
        assertInstanceOf(
                ProtectedBookMaintenanceRejection.ArtifactPathInvalid.class, mapped.rejection())
            .artifactRole());

    assertInstanceOf(
        ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists.class,
        assertThrows(
                ProtectedBookMaintenanceRejectionException.class,
                () ->
                    SqliteProtectedBookPairPublicationPreparation.reserveAbsentBookTarget(
                        occupiedBookTarget,
                        ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                        ignored -> {
                          throw new FileAlreadyExistsException(occupiedBookTarget.toString());
                        }))
            .rejection());
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteProtectedBookPairPublicationPreparation.reserveAbsentBookTarget(
                absentTarget("preparation/book-io.sqlite"),
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ignored -> {
                  throw new IOException("book reservation I/O failure");
                }));
    assertThrows(
        SqliteGeneratedSecretTargetOccupiedException.class,
        () ->
            SqliteProtectedBookPairPublicationPreparation.reserveAbsentSecretTarget(
                absentTarget("preparation/secret-occupied.key"),
                ignored -> {
                  throw new FileAlreadyExistsException("secret reservation already exists");
                }));
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteProtectedBookPairPublicationPreparation.reserveAbsentSecretTarget(
                absentTarget("preparation/secret-io.key"),
                ignored -> {
                  throw new IOException("secret reservation I/O failure");
                }));
    assertThrows(
        ProtectedBookMaintenanceRejectionException.class,
        () ->
            SqliteProtectedBookPairPublicationPreparation.prepareGeneratedSecretTarget(
                occupiedBookTarget,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                ignored -> {
                  throw callerPathFailure;
                }));
    ProtectedBookMaintenanceRejection.ArtifactPathInvalid bookPathRejection =
        assertInstanceOf(
            ProtectedBookMaintenanceRejection.ArtifactPathInvalid.class,
            assertThrows(
                    ProtectedBookMaintenanceRejectionException.class,
                    () ->
                        SqliteProtectedBookPairPublicationPreparation.reserveAbsentBookTarget(
                            absentTarget("preparation/book-caller-path.sqlite"),
                            ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                            ignored -> {
                              throw callerPathFailure;
                            }))
                .rejection());
    assertEquals(
        ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET, bookPathRejection.artifactRole());
  }

  @Test
  void pairPreparationFacadesRetainTheExactTargetAndReservationContracts() throws Exception {
    Path generatedSecret = absentTarget("preparation-facade/generated.key");
    AtomicInteger preparationCalls = new AtomicInteger();
    SqliteProtectedBookPairPublicationPreparation.prepareGeneratedSecretTarget(
        generatedSecret,
        targetPath -> {
          assertEquals(generatedSecret, targetPath);
          preparationCalls.incrementAndGet();
        });
    SqliteProtectedBookPairPublicationPreparation.prepareGeneratedSecretTarget(
        generatedSecret,
        ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
        targetPath -> {
          assertEquals(generatedSecret, targetPath);
          preparationCalls.incrementAndGet();
        });
    assertEquals(2, preparationCalls.get());

    Path directBookTarget = absentTarget("preparation-facade/direct-book.sqlite");
    try (SqliteOwnedDestinationReservation reservation =
        SqliteProtectedBookPairPublicationPreparation.reserveAbsentBookTarget(
            directBookTarget, ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET)) {
      assertEquals(directBookTarget, reservation.finalPath());
    }

    Path delegatedBookTarget = absentTarget("preparation-facade/delegated-book.sqlite");
    try (SqliteOwnedDestinationReservation reservation =
        SqliteProtectedBookPairPublicationPreparation.reserveAbsentBookTarget(
            delegatedBookTarget,
            ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
            SqliteOwnedDestinationReservation::reserve)) {
      assertEquals(delegatedBookTarget, reservation.finalPath());
    }

    Path directSecretTarget = absentTarget("preparation-facade/direct-secret.key");
    try (SqliteOwnedDestinationReservation reservation =
        SqliteProtectedBookPairPublicationPreparation.reserveAbsentSecretTarget(
            directSecretTarget)) {
      assertEquals(directSecretTarget, reservation.finalPath());
    }

    Path delegatedSecretTarget = absentTarget("preparation-facade/delegated-secret.key");
    try (SqliteOwnedDestinationReservation reservation =
        SqliteProtectedBookPairPublicationPreparation.reserveAbsentSecretTarget(
            delegatedSecretTarget, SqliteOwnedDestinationReservation::reserve)) {
      assertEquals(delegatedSecretTarget, reservation.finalPath());
    }
  }

  @Test
  void maintenanceStoreRejectsForeignPreparedHandlesAndRecognizesOnlyRegularBookPairs()
      throws Exception {
    Path regularBook = absentTarget("pair-shape/book.sqlite");
    Path regularSecret = absentTarget("pair-shape/book.key");
    assertFalse(SqliteProtectedBookMaintenanceStore.hasRegularBookPair(regularBook, regularSecret));
    Files.writeString(regularBook, "book");
    assertFalse(SqliteProtectedBookMaintenanceStore.hasRegularBookPair(regularBook, regularSecret));
    Files.writeString(regularSecret, "secret");
    assertTrue(SqliteProtectedBookMaintenanceStore.hasRegularBookPair(regularBook, regularSecret));

    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path sourceBookPath = tempDirectory.resolve("pair-shape").resolve("source.sqlite");
    BookAccess sourceAccess = bookAccess(sourceBookPath);
    initializeBook(sourceAccess);
    try (PreparedPairPublication foreignPreparedPair =
            new PreparedPairPublication() {
              @Override
              public Path bookTargetPath() {
                return regularBook;
              }

              @Override
              public Path secretTargetPath() {
                return regularSecret;
              }

              @Override
              public RestoredBookTargetPolicy bookTargetPolicy() {
                return RestoredBookTargetPolicy.REQUIRE_ABSENT;
              }

              @Override
              public void close() {}
            };
        var verifiedSource = verifiedBook(store, sourceAccess)) {
      assertThrows(
          IllegalArgumentException.class,
          () -> store.stageBackupPair(verifiedSource, foreignPreparedPair));
    }
  }

  @Test
  void ownedStagesAndReservationsRetainAbsentOrFinishedArtifactsWithoutTouchingExternalFiles()
      throws Exception {
    Path target = absentTarget("reservation/missing-stage.key");
    try (SqliteOwnedDestinationReservation reservation =
        SqliteOwnedDestinationReservation.reserve(target)) {
      Path reservationStage = SqliteOwnedStageRecord.findFor(target).getFirst().stagedPath();
      Files.delete(reservationStage);
      reservation.close();
      assertFalse(Files.exists(target));
    }

    Path discardedTarget = absentTarget("reservation/discarded-stage.key");
    SqliteOwnedStagedArtifact discardedStage =
        writeStage(discardedTarget, ".stage-", ".tmp", "discarded");
    SqliteOwnedStagedArtifact.releaseAllRetained(null, discardedStage);
    assertTrue(Files.exists(discardedStage.stagedPath()));
  }

  @Test
  void reservationReleaseRetainsItsOwnershipEvidenceWithoutMutatingTheParentDirectory()
      throws Exception {
    Path target = absentTarget("reservation/release-permission.key");
    Path parent = java.util.Objects.requireNonNull(target.getParent(), "reservation parent");
    Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(parent);
    Path reservationStage;
    try (SqliteOwnedDestinationReservation reservation =
        SqliteOwnedDestinationReservation.reserve(target)) {
      reservationStage = SqliteOwnedStageRecord.findFor(target).getFirst().stagedPath();
      try {
        Files.setPosixFilePermissions(
            parent, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
        assertDoesNotThrow(reservation::close);
      } finally {
        Files.setPosixFilePermissions(parent, originalPermissions);
      }
    }
    assertTrue(Files.exists(reservationStage));
  }

  @Test
  void bytePassphraseFactoriesConstructRetainedStagePairs() throws Exception {
    Path backupTarget = absentTarget("factory/backup.sqlite");
    Path backupKeyTarget = absentTarget("factory/backup.key");
    try (SqliteStagedBackupPair backupPair =
        SqliteStagedBackupPairFactory.create(
            SqliteOwnedStagedArtifact.create(backupTarget, ".backup-", ".sqlite"),
            backupTarget,
            SqliteOwnedStagedArtifact.create(backupKeyTarget, ".backup-key-", ".tmp"),
            backupKeyTarget,
            TEST_BOOK_KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            VERIFICATION_SUPPORT,
            Files::createLink,
            Files::createLink)) {
      backupPair.close();
      backupPair.retainUnpublishedArtifacts();
    }

    Path restoredTarget = absentTarget("factory/restored.sqlite");
    Path restoredKeyTarget = absentTarget("factory/restored.key");
    try (SqliteStagedRestoredBookPair restoredPair =
        SqliteStagedRestoredBookPairFactory.create(
            new SqliteStagedProtectedBookPairArtifacts(
                SqliteOwnedStagedArtifact.create(restoredTarget, ".restore-", ".tmp"),
                restoredTarget,
                SqliteOwnedStagedArtifact.create(restoredKeyTarget, ".restore-key-", ".tmp"),
                restoredKeyTarget),
            RestoredBookTargetPolicy.REQUIRE_ABSENT,
            TEST_BOOK_KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            VERIFICATION_SUPPORT,
            SqliteRestoredBookPairPublication.defaultOperators())) {
      restoredPair.close();
    }
    assertFalse(Files.exists(backupTarget));
    assertFalse(Files.exists(backupKeyTarget));
    assertFalse(Files.exists(restoredTarget));
    assertFalse(Files.exists(restoredKeyTarget));
  }

  @Test
  void stagedBackupPublicationPreservesAnExternalBookThatWinsTheFinalPathRace() throws Exception {
    Path backupTarget = absentTarget("backup-race/backup.sqlite");
    Path backupKeyTarget = absentTarget("backup-race/backup.key");
    SqliteOwnedStagedArtifact stagedBackup =
        writeStage(backupTarget, ".backup-", ".sqlite", "staged backup");
    SqliteOwnedStagedArtifact stagedBackupKey =
        writeStage(backupKeyTarget, ".backup-key-", ".tmp", "staged backup key");
    try (SqliteStagedBackupPair stagedBackupPair =
        SqliteStagedBackupPairFactory.create(
            stagedBackup,
            backupTarget,
            stagedBackupKey,
            backupKeyTarget,
            TEST_BOOK_KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            VERIFICATION_SUPPORT,
            Files::createLink,
            Files::createLink)) {
      sealBackupForPublication(stagedBackupPair);
      Files.writeString(backupTarget, "external backup");

      assertInstanceOf(
          ProtectedBookPairPublicationFailureOutcome.CompletionUncertain.class,
          stagedBackupPair.commit(backupBinding(backupTarget)));
      assertEquals("external backup", Files.readString(backupTarget));
    }
  }

  private Path absentTarget(String relativePath) throws IOException {
    Path target = tempDirectory.resolve(relativePath);
    Path parent = target.getParent();
    if (parent == null) {
      throw new AssertionError("Expected one target parent directory.");
    }
    Files.createDirectories(parent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parent);
    return target;
  }

  private static SqliteOwnedStagedArtifact writeStage(
      Path finalPath, String infix, String suffix, String content) throws IOException {
    SqliteOwnedStagedArtifact stage = SqliteOwnedStagedArtifact.create(finalPath, infix, suffix);
    Files.writeString(stage.stagedPath(), content);
    return stage;
  }

  private static SqlitePublicationCapabilityWitness.Set witnessesForNoReplace(Path targetPath)
      throws IOException {
    return SqlitePublicationCapabilityWitness.acquire(
        java.util.List.of(SqlitePublicationCapabilityWitness.Requirement.noReplace(targetPath)),
        Files::createLink,
        SqliteProtectedBookPublicationSupport::moveReplacing);
  }

  private static SqlitePublicationCapabilityWitness.Set witnessesForPair(
      Path bookTargetPath,
      SqlitePublicationCapabilityWitness.PrimitiveKind bookPrimitiveKind,
      Path secretTargetPath)
      throws IOException {
    return SqlitePublicationCapabilityWitness.acquirePair(
        bookTargetPath,
        bookPrimitiveKind,
        secretTargetPath,
        Files::createLink,
        SqliteProtectedBookPublicationSupport::moveReplacing);
  }

  private record CountingLease(Path artifactPath, AtomicInteger closeCount) implements HeldLease {
    private CountingLease(Path artifactPath) {
      this(artifactPath, new AtomicInteger());
    }

    @Override
    public void close() {
      closeCount.compareAndSet(0, 1);
    }
  }
}
