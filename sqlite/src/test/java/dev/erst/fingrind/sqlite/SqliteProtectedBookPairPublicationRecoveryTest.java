package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRecoveryRecordState;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationRecoveryRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Direct behavioural coverage for recovery admission with no retained pair record. */
class SqliteProtectedBookPairPublicationRecoveryTest extends SqliteArtifactPublicationTestSupport {
  @Test
  void absentPairTargetsRemainAvailableForBackupAndRestore() throws Exception {
    Path backupBook = absentTarget("absent/backup.sqlite");
    Path backupKey = absentTarget("absent/backup.key");

    assertEquals(
        SqlitePairPublicationReconciliationAbsent.INSTANCE,
        recovery()
            .reconcile(
                backupBook,
                backupKey,
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                backupRequest(backupBook),
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));

    Path restoredBook = absentTarget("absent/restored.sqlite");
    Path restoredKey = absentTarget("absent/restored.key");

    assertEquals(
        SqlitePairPublicationReconciliationAbsent.INSTANCE,
        recovery()
            .reconcile(
                restoredBook,
                restoredKey,
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                restoreRequest(restoredBook),
                ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET,
                ProtectedBookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET));
  }

  @Test
  void rekeyCanReplaceOnlyItsSelectedLiveBookButNeverAnOccupiedGeneratedSecret() throws Exception {
    Path liveBook = writeArtifact("rekey/live.sqlite", "selected live book");
    Path generatedSecret = absentTarget("rekey/new.key");
    ProtectedBookPairPublicationRecoveryRequest.Rekey request =
        new ProtectedBookPairPublicationRecoveryRequest.Rekey(
            rekeyBinding(liveBook, liveBook.resolveSibling("live.key")).sourceIdentity());

    assertEquals(
        SqlitePairPublicationReconciliationAbsent.INSTANCE,
        recovery()
            .reconcile(
                liveBook,
                generatedSecret,
                RestoredBookTargetPolicy.REPLACE_SELECTED,
                request,
                ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
                ProtectedBookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET));

    Files.writeString(generatedSecret, "caller-owned key artifact");

    ProtectedBookMaintenanceRejectionException refusal =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                recovery()
                    .reconcile(
                        liveBook,
                        generatedSecret,
                        RestoredBookTargetPolicy.REPLACE_SELECTED,
                        request,
                        ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
                        ProtectedBookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET));

    assertEquals(
        generatedSecret.toAbsolutePath().normalize(),
        assertInstanceOf(
                ProtectedBookMaintenanceRejection.SecretTargetOccupied.class, refusal.rejection())
            .secretTargetPath());
  }

  @Test
  void occupiedBookTargetUsesTheCallerOperationSpecificRejection() throws Exception {
    Path backupBook = writeArtifact("occupied/backup.sqlite", "existing backup");
    Path backupKey = absentTarget("occupied/backup.key");

    ProtectedBookMaintenanceRejectionException backupRefusal =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                recovery()
                    .reconcile(
                        backupBook,
                        backupKey,
                        RestoredBookTargetPolicy.REQUIRE_ABSENT,
                        backupRequest(backupBook),
                        ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                        ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));
    assertEquals(
        backupBook.toAbsolutePath().normalize(),
        assertInstanceOf(
                ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists.class,
                backupRefusal.rejection())
            .backupFilePath());

    Path restoredBook = writeArtifact("occupied/restored.sqlite", "existing restored book");
    Path restoredKey = absentTarget("occupied/restored.key");

    ProtectedBookMaintenanceRejectionException restoreRefusal =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                recovery()
                    .reconcile(
                        restoredBook,
                        restoredKey,
                        RestoredBookTargetPolicy.REQUIRE_ABSENT,
                        restoreRequest(restoredBook),
                        ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET,
                        ProtectedBookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET));
    assertEquals(
        restoredBook.toAbsolutePath().normalize(),
        assertInstanceOf(
                ProtectedBookMaintenanceRejection.BookDestinationOccupied.class,
                restoreRefusal.rejection())
            .bookFilePath());
  }

  @Test
  void unboundGeneratedSecretIsRefusedUnlessItsStageEvidenceRequiresFailClosedReview()
      throws Exception {
    Path occupiedSecret = writeArtifact("secret/occupied.key", "caller-owned key artifact");
    Path absentBook = absentTarget("secret/book.sqlite");

    ProtectedBookMaintenanceRejectionException refusal =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                recovery()
                    .reconcile(
                        absentBook,
                        occupiedSecret,
                        RestoredBookTargetPolicy.REQUIRE_ABSENT,
                        backupRequest(absentBook),
                        ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                        ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));
    assertEquals(
        occupiedSecret.toAbsolutePath().normalize(),
        assertInstanceOf(
                ProtectedBookMaintenanceRejection.SecretTargetOccupied.class, refusal.rejection())
            .secretTargetPath());

    Path stagedSecret = writeArtifact("secret/.staged.key", "retained generated secret");
    SqliteOwnedStagedArtifact.recordExisting(occupiedSecret, stagedSecret);

    SqlitePairPublicationReconciliationEvidenceBlocked blocked =
        assertInstanceOf(
            SqlitePairPublicationReconciliationEvidenceBlocked.class,
            recovery()
                .reconcile(
                    absentBook,
                    occupiedSecret,
                    RestoredBookTargetPolicy.REQUIRE_ABSENT,
                    backupRequest(absentBook),
                    ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));
    assertEquals(absentBook.toAbsolutePath().normalize(), blocked.bookArtifactPath());
    assertEquals(occupiedSecret.toAbsolutePath().normalize(), blocked.secretArtifactPath());
  }

  @Test
  void existingPairRequiresOwnedRecoveryEvidenceBeforeBackupCanBeRecognized() throws Exception {
    Path backupBook = writeArtifact("complete/backup.sqlite", "backup bytes");
    Path backupKey = writeArtifact("complete/backup.key", "backup key");

    assertThrows(
        ProtectedBookMaintenanceRejectionException.class,
        () ->
            recovery()
                .reconcile(
                    backupBook,
                    backupKey,
                    RestoredBookTargetPolicy.REQUIRE_ABSENT,
                    backupRequest(backupBook),
                    ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));

    Path backupStage = writeArtifact("complete/.backup.stage", "retained backup stage");
    SqliteOwnedStagedArtifact.recordExisting(backupBook, backupStage);

    SqlitePairPublicationReconciliationExistingCompleteBackup existingBackup =
        assertInstanceOf(
            SqlitePairPublicationReconciliationExistingCompleteBackup.class,
            recovery()
                .reconcile(
                    backupBook,
                    backupKey,
                    RestoredBookTargetPolicy.REQUIRE_ABSENT,
                    backupRequest(backupBook),
                    ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));
    assertEquals(backupBook.toAbsolutePath().normalize(), existingBackup.backupArtifactPath());
    assertEquals(backupKey.toAbsolutePath().normalize(), existingBackup.backupKeyPath());

    Path restoredBook = writeArtifact("complete/restored.sqlite", "restored bytes");
    Path restoredKey = writeArtifact("complete/restored.key", "restored key");
    Path restoredStage = writeArtifact("complete/.restored.stage", "retained restored stage");
    SqliteOwnedStagedArtifact.recordExisting(restoredBook, restoredStage);

    assertInstanceOf(
        SqlitePairPublicationReconciliationEvidenceBlocked.class,
        recovery()
            .reconcile(
                restoredBook,
                restoredKey,
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                restoreRequest(restoredBook),
                ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET,
                ProtectedBookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET));
  }

  @Test
  void retainedPairRecordCarriesItsStagesIntoUncertainAndPrepublicationOutcomes() throws Exception {
    SqliteProtectedBookPairPublicationRecord record = retainedRecord("retained-outcomes");

    SqlitePairPublicationReconciliationCompletionUncertain derivedUncertainty =
        assertInstanceOf(
            SqlitePairPublicationReconciliationCompletionUncertain.class,
            SqliteProtectedBookPairPublicationRecovery.completionUncertain(record));
    assertEquals(
        ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN,
        derivedUncertainty.bookArtifactState());
    assertEquals(
        ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN,
        derivedUncertainty.secretArtifactState());
    var derivedRetention =
        Objects.requireNonNull(
            derivedUncertainty.pairPublicationRetention(), "derived uncertainty retention");
    assertEquals(
        record.bookStagePath, derivedRetention.bookPublication().retention().retainedStagePath());
    assertEquals(
        record.secretStagePath,
        derivedRetention.generatedSecretPublication().retention().retainedStagePath());

    SqlitePairPublicationReconciliationCompletionUncertain explicitUncertainty =
        SqliteProtectedBookPairPublicationRecovery.completionUncertain(
            record,
            ProtectedBookPairPublicationMemberState.PUBLISHED_DURABLE,
            ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN);
    assertEquals(
        ProtectedBookPairPublicationMemberState.PUBLISHED_DURABLE,
        explicitUncertainty.bookArtifactState());
    assertEquals(
        ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN,
        explicitUncertainty.secretArtifactState());

    SqlitePairPublicationReconciliationPrepublicationRecoveryRequired prepublication =
        SqliteProtectedBookPairPublicationRecovery.prepublicationRecoveryRequired(
            record, ProtectedBookPairPublicationRecoveryRecordState.DURABLY_RETAINED);
    assertEquals(
        ProtectedBookPairPublicationRecoveryRecordState.DURABLY_RETAINED,
        prepublication.recoveryRecordState());
    assertEquals(record.bookTargetPath, prepublication.bookArtifactPath());
    assertEquals(record.secretTargetPath, prepublication.secretArtifactPath());
  }

  @Test
  void retainedStagesPublishBothAbsentMembersOnlyAfterRecoveryEvidenceIsForced() throws Exception {
    SqliteProtectedBookPairPublicationRecord record = retainedRecord("member-publication");
    SqlitePairPublicationMemberReconciler reconciler =
        reconciler((ignoredStep, ignoredParent) -> {});

    try (SqlitePublicationCapabilityWitness.Set witnesses = witnessesFor(record)) {
      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.DURABLE,
          reconciler.reconcileSecret(
              record,
              SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
              witnesses));
      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.DURABLE,
          reconciler.reconcileBook(
              record,
              SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
              witnesses));
    }

    assertTrue(Files.isSameFile(record.secretTargetPath, record.secretStagePath));
    assertTrue(Files.isSameFile(record.bookTargetPath, record.bookStagePath));
  }

  @Test
  void memberRecoveryDoesNotPublishWhenItsEvidenceForceCannotBeConfirmed() throws Exception {
    SqliteProtectedBookPairPublicationRecord record = retainedRecord("member-force-failure");
    SqlitePairPublicationMemberReconciler reconciler =
        reconciler(
            (ignoredStep, ignoredParent) -> {
              throw new IOException("injected recovery-evidence force failure");
            });

    try (SqlitePublicationCapabilityWitness.Set witnesses = witnessesFor(record)) {
      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.OUTCOME_UNCERTAIN,
          reconciler.reconcileSecret(
              record,
              SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
              witnesses));
      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.OUTCOME_UNCERTAIN,
          reconciler.reconcileBook(
              record,
              SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
              witnesses));
    }

    assertFalse(Files.exists(record.secretTargetPath));
    assertFalse(Files.exists(record.bookTargetPath));
  }

  @Test
  void memberRecoveryRefusesUnownedStagesEvenWhenTheirBytesMatchTheRecord() throws Exception {
    SqliteProtectedBookPairPublicationRecord record = pairRecord("member-unowned", false);
    SqlitePairPublicationMemberReconciler reconciler =
        reconciler((ignoredStep, ignoredParent) -> {});

    try (SqlitePublicationCapabilityWitness.Set witnesses = witnessesFor(record)) {
      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.OUTCOME_UNCERTAIN,
          reconciler.reconcileSecret(
              record,
              SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
              witnesses));
      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.OUTCOME_UNCERTAIN,
          reconciler.reconcileBook(
              record,
              SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
              witnesses));
    }

    assertFalse(Files.exists(record.secretTargetPath));
    assertFalse(Files.exists(record.bookTargetPath));
  }

  private static SqliteProtectedBookPairPublicationRecovery recovery() {
    return new SqliteProtectedBookPairPublicationRecovery(
        (ignoredBook, ignoredSecret, ignoredBinding) -> true,
        (ignoredStep, ignoredParent) -> {},
        ignoredRecord -> {});
  }

  private static ProtectedBookPairPublicationRecoveryRequest.Backup backupRequest(Path bookPath) {
    return new ProtectedBookPairPublicationRecoveryRequest.Backup(
        bookPath.resolveSibling("source.sqlite"), new UUID(0L, 1L));
  }

  private static ProtectedBookPairPublicationRecoveryRequest.Restore restoreRequest(Path bookPath) {
    return new ProtectedBookPairPublicationRecoveryRequest.Restore(
        bookPath.resolveSibling("source-backup.sqlite"),
        bookPath.resolveSibling("source-backup.key"),
        backupBinding(bookPath).acknowledgement());
  }

  private static SqlitePairPublicationMemberReconciler reconciler(
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer) {
    return new SqlitePairPublicationMemberReconciler(directoryForcer, ignoredRecord -> {});
  }

  private static SqlitePublicationCapabilityWitness.Set witnessesFor(
      SqliteProtectedBookPairPublicationRecord record) throws IOException {
    return SqlitePublicationCapabilityWitness.acquirePair(
        record.bookTargetPath,
        SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK,
        record.secretTargetPath,
        Files::createLink,
        SqliteProtectedBookPublicationSupport::moveReplacing);
  }

  private SqliteProtectedBookPairPublicationRecord retainedRecord(String directoryName)
      throws IOException {
    return pairRecord(directoryName, true);
  }

  private SqliteProtectedBookPairPublicationRecord pairRecord(
      String directoryName, boolean recordStageOwnership) throws IOException {
    Path bookTarget = absentTarget(directoryName + "/book.sqlite");
    Path secretTarget = absentTarget(directoryName + "/book.key");
    Path bookStage = writeArtifact(directoryName + "/.book.stage", "retained book stage");
    Path secretStage = writeArtifact(directoryName + "/.secret.stage", "retained secret stage");
    if (recordStageOwnership) {
      SqliteOwnedStagedArtifact.recordExisting(bookTarget, bookStage);
      SqliteOwnedStagedArtifact.recordExisting(secretTarget, secretStage);
    }
    return SqliteProtectedBookPairPublicationRecord.create(
        bookTarget,
        secretTarget,
        bookStage,
        secretStage,
        RestoredBookTargetPolicy.REQUIRE_ABSENT,
        backupBinding(bookTarget.resolveSibling("source.sqlite")),
        (ignoredStep, ignoredParent) -> {});
  }

  private Path absentTarget(String relativePath) throws IOException {
    Path target = tempDirectory.resolve(relativePath);
    Path parent = target.getParent();
    if (parent == null) {
      throw new AssertionError("Fixture target requires one parent.");
    }
    Files.createDirectories(parent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parent);
    return target;
  }
}
