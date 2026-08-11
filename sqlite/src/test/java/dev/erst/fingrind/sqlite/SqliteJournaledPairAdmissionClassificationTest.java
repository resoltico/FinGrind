package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationRecoveryRequest;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Proves clean-target classification and retired-residue detection cannot confer recovery
 * authority.
 */
class SqliteJournaledPairAdmissionClassificationTest extends SqliteNativeBridgeTestSupport {
  @Test
  void classifiesOnlyAnAbsentPairOrACompleteBackupAsReplayable() throws Exception {
    Path book = tempDirectory.resolve("backup.sqlite");
    Path secret = tempDirectory.resolve("backup.key");

    assertInstanceOf(
        SqlitePairPublicationReconciliationAbsent.class,
        classify(book, secret, RestoredBookTargetPolicy.REQUIRE_ABSENT));

    Files.writeString(book, "backup");
    Files.writeString(secret, "key");
    SqlitePairPublicationReconciliationExistingCompleteBackup complete =
        assertInstanceOf(
            SqlitePairPublicationReconciliationExistingCompleteBackup.class,
            classify(book, secret, RestoredBookTargetPolicy.REQUIRE_ABSENT));
    assertEquals(book, complete.backupArtifactPath());
    assertEquals(secret, complete.backupKeyPath());
  }

  @Test
  void rejectsUnboundOccupancyInsteadOfTreatingItAsRecoverableEvidence() throws Exception {
    Path occupiedBook = tempDirectory.resolve("occupied-book.sqlite");
    Files.writeString(occupiedBook, "occupied");
    ProtectedBookMaintenanceRejectionException bookFailure =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                classify(
                    occupiedBook,
                    tempDirectory.resolve("occupied-book.key"),
                    RestoredBookTargetPolicy.REQUIRE_ABSENT));
    assertInstanceOf(
        ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists.class,
        bookFailure.rejection());

    Path occupiedSecret = tempDirectory.resolve("occupied-secret.key");
    Files.writeString(occupiedSecret, "occupied");
    ProtectedBookMaintenanceRejectionException secretFailure =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                classify(
                    tempDirectory.resolve("occupied-secret.sqlite"),
                    occupiedSecret,
                    RestoredBookTargetPolicy.REQUIRE_ABSENT));
    assertInstanceOf(
        ProtectedBookMaintenanceRejection.SecretTargetOccupied.class, secretFailure.rejection());

    Path directoryBook = Files.createDirectory(tempDirectory.resolve("directory-book"));
    Path directorySecret = Files.createDirectory(tempDirectory.resolve("directory-secret"));
    SqlitePairPublicationReconciliationEvidenceBlocked blocked =
        assertInstanceOf(
            SqlitePairPublicationReconciliationEvidenceBlocked.class,
            classify(directoryBook, directorySecret, RestoredBookTargetPolicy.REQUIRE_ABSENT));
    assertEquals(directoryBook, blocked.bookArtifactPath());
    assertEquals(directorySecret, blocked.secretArtifactPath());
  }

  @Test
  void allowsOnlyTheSelectedReplaceTargetToPreexist() throws Exception {
    Path book = tempDirectory.resolve("replace.sqlite");
    Files.writeString(book, "old-book");

    assertInstanceOf(
        SqlitePairPublicationReconciliationAbsent.class,
        classify(
            book, tempDirectory.resolve("replace.key"), RestoredBookTargetPolicy.REPLACE_SELECTED));

    Path secret = tempDirectory.resolve("replace-secret.key");
    Files.writeString(secret, "old-secret");
    ProtectedBookMaintenanceRejectionException failure =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () -> classify(book, secret, RestoredBookTargetPolicy.REPLACE_SELECTED));
    assertInstanceOf(
        ProtectedBookMaintenanceRejection.SecretTargetOccupied.class, failure.rejection());
  }

  @Test
  void retiredResidueBlocksWithoutParsingOrDeletingIt() throws Exception {
    Path book = tempDirectory.resolve("residue.sqlite");
    Path secret = tempDirectory.resolve("residue.key");
    assertFalse(SqliteProtectedBookPairPublicationEvidenceScanner.hasLegacyResidue(book, secret));

    SqliteOwnedStageRecord.recordExisting(book, tempDirectory.resolve("valid-unbound.stage"));
    assertTrue(SqliteProtectedBookPairPublicationEvidenceScanner.hasLegacyResidue(book, secret));

    Path malformedOwnerRecord =
        book.resolveSibling(".fingrind-maintenance-stage-" + UUID.randomUUID() + ".owner");
    try (var channel = SqliteOwnedRegularFileAccess.openNewWrite(malformedOwnerRecord)) {
      channel.write(ByteBuffer.wrap("malformed owner record".getBytes(StandardCharsets.UTF_8)));
    }
    assertTrue(SqliteProtectedBookPairPublicationEvidenceScanner.hasLegacyResidue(book, secret));

    Path sidecar =
        Files.writeString(
            tempDirectory.resolve(".fingrind-protected-book-pair-untrusted-sidecar"),
            "not a recovery record");
    assertTrue(SqliteProtectedBookPairPublicationEvidenceScanner.hasLegacyResidue(book, secret));
    assertEquals("not a recovery record", Files.readString(sidecar));

    Files.delete(sidecar);
    SqliteOwnedStagedArtifact legacyStage =
        SqliteOwnedStagedArtifact.create(book, ".legacy-stage-", ".sqlite");
    try {
      assertTrue(SqliteProtectedBookPairPublicationEvidenceScanner.hasLegacyResidue(book, secret));
    } finally {
      legacyStage.releaseRetained();
    }
  }

  @Test
  void fullPairsWithoutBackupAuthorityAreEvidenceBlockedRatherThanReplayed() throws Exception {
    Path book = Files.writeString(tempDirectory.resolve("restore-book.sqlite"), "backup");
    Path secret = Files.writeString(tempDirectory.resolve("restore-book.key"), "key");
    ProtectedBookPairPublicationRecoveryRequest.Restore restore =
        new ProtectedBookPairPublicationRecoveryRequest.Restore(
            book,
            secret,
            new AttestationBackupAcknowledgement(
                new UUID(7L, 8L), new byte[32], BigInteger.ZERO, new byte[32]));

    assertInstanceOf(
        SqlitePairPublicationReconciliationEvidenceBlocked.class,
        SqliteJournaledPairAdmissionClassification.classifyCleanTargets(
            book, secret, RestoredBookTargetPolicy.REQUIRE_ABSENT, restore));
    assertInstanceOf(
        dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked
            .class,
        SqliteJournaledPairAdmissionClassification.evidenceBlocked(book, secret));
  }

  @Test
  void aNonregularSecretPreventsAnExistingBackupPairFromBeingReplayed() throws Exception {
    Path book = Files.writeString(tempDirectory.resolve("regular-backup.sqlite"), "backup");
    Path secret = Files.createDirectory(tempDirectory.resolve("nonregular-backup.key"));

    assertInstanceOf(
        SqlitePairPublicationReconciliationEvidenceBlocked.class,
        classify(book, secret, RestoredBookTargetPolicy.REQUIRE_ABSENT));
  }

  @Test
  void classifiesEachSingleTargetOccupancyByItsOnlySafePolicy() throws Exception {
    Path restoreBook = Files.writeString(tempDirectory.resolve("restore.sqlite"), "book");
    Path restoreSecret = restoreBook.resolveSibling("restore.key");
    ProtectedBookPairPublicationRecoveryRequest.Restore restore =
        new ProtectedBookPairPublicationRecoveryRequest.Restore(
            restoreBook,
            restoreSecret,
            new AttestationBackupAcknowledgement(
                new UUID(7L, 18L), new byte[32], BigInteger.ZERO, new byte[32]));
    ProtectedBookMaintenanceRejectionException restoreFailure =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                SqliteJournaledPairAdmissionClassification.classifyCleanTargets(
                    restoreBook, restoreSecret, RestoredBookTargetPolicy.REQUIRE_ABSENT, restore));
    assertInstanceOf(
        ProtectedBookMaintenanceRejection.BookDestinationOccupied.class,
        restoreFailure.rejection());

    Path rekeyBook = Files.writeString(tempDirectory.resolve("rekey.sqlite"), "book");
    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteJournaledPairAdmissionClassification.classifyCleanTargets(
                rekeyBook,
                rekeyBook.resolveSibling("rekey.key"),
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                ProtectedBookPairPublicationRecoveryRequest.Rekey.INSTANCE));
  }

  private static SqlitePairPublicationReconciliation classify(
      Path book, Path secret, RestoredBookTargetPolicy policy) {
    return SqliteJournaledPairAdmissionClassification.classifyCleanTargets(
        book, secret, policy, backupRequest(book));
  }

  private static ProtectedBookPairPublicationRecoveryRequest.Backup backupRequest(Path source) {
    return new ProtectedBookPairPublicationRecoveryRequest.Backup(source, new UUID(5L, 6L));
  }
}
