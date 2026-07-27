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
import java.util.Map;
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

  @Test
  void memberRecoveryAcceptsOnlyTheExpectedWinnerOfARaceForTheGeneratedSecret() throws Exception {
    SqliteProtectedBookPairPublicationRecord matched = retainedRecord("member-secret-race-match");
    boolean[] matchedCollisionCreated = {false};
    SqlitePairPublicationMemberReconciler matchedReconciler =
        reconciler(
            (ignoredStep, ignoredParent) -> {
              if (!matchedCollisionCreated[0]) {
                Files.createLink(matched.secretTargetPath, matched.secretStagePath);
                matchedCollisionCreated[0] = true;
              }
            });

    try (SqlitePublicationCapabilityWitness.Set witnesses = witnessesFor(matched)) {
      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.DURABLE,
          matchedReconciler.reconcileSecret(
              matched,
              SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
              witnesses));
    }
    assertTrue(Files.isSameFile(matched.secretTargetPath, matched.secretStagePath));

    SqliteProtectedBookPairPublicationRecord foreign = retainedRecord("member-secret-race-foreign");
    boolean[] foreignCollisionCreated = {false};
    SqlitePairPublicationMemberReconciler foreignReconciler =
        reconciler(
            (ignoredStep, ignoredParent) -> {
              if (!foreignCollisionCreated[0]) {
                Files.writeString(foreign.secretTargetPath, "foreign generated secret");
                foreignCollisionCreated[0] = true;
              }
            });

    try (SqlitePublicationCapabilityWitness.Set witnesses = witnessesFor(foreign)) {
      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.OUTCOME_UNCERTAIN,
          foreignReconciler.reconcileSecret(
              foreign,
              SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
              witnesses));
    }
    assertFalse(Files.isSameFile(foreign.secretTargetPath, foreign.secretStagePath));
  }

  @Test
  void memberRecoveryFailsClosedWhenAStagedBookOrItsFinalTargetChangesAfterPlanning()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord targetChanged =
        retainedRecord("member-book-target-changed");
    SqlitePairPublicationMemberReconciler reconciler =
        reconciler((ignoredStep, ignoredParent) -> {});

    try (SqlitePublicationCapabilityWitness.Set witnesses = witnessesFor(targetChanged)) {
      Files.writeString(targetChanged.bookTargetPath, "foreign book target");
      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.OUTCOME_UNCERTAIN,
          reconciler.reconcileBook(
              targetChanged,
              SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
              witnesses));
    }

    SqliteProtectedBookPairPublicationRecord stageChanged =
        retainedRecord("member-book-stage-changed");
    boolean[] stageMutationApplied = {false};
    SqlitePairPublicationMemberReconciler stageChangedReconciler =
        reconciler(
            (ignoredStep, ignoredParent) -> {
              if (!stageMutationApplied[0]) {
                Files.writeString(stageChanged.bookStagePath, "changed staged book");
                stageMutationApplied[0] = true;
              }
            });

    try (SqlitePublicationCapabilityWitness.Set witnesses = witnessesFor(stageChanged)) {
      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.OUTCOME_UNCERTAIN,
          stageChangedReconciler.reconcileBook(
              stageChanged,
              SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
              witnesses));
    }
    assertFalse(Files.exists(stageChanged.bookTargetPath));
  }

  @Test
  void rekeyRecoveryRefusesToPublishItsGeneratedSecretAfterTheLiveBookChanges() throws Exception {
    SqliteProtectedBookPairPublicationRecord record = rekeyRecord("member-rekey-book-changed");
    SqlitePairPublicationMemberReconciler reconciler =
        reconciler((ignoredStep, ignoredParent) -> {});

    try (SqlitePublicationCapabilityWitness.Set witnesses =
        SqlitePairPublicationRecoveryCapabilities.acquire(
            record,
            SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
            SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
            ProtectedBookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET)) {
      Files.writeString(record.bookTargetPath, "changed live book");
      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.OUTCOME_UNCERTAIN,
          reconciler.reconcileSecret(
              record,
              SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
              witnesses));
    }
    assertFalse(Files.exists(record.secretTargetPath));
  }

  @Test
  void matchedAndBlockedMemberPlansRespectTheirBoundedRecoveryActions() throws Exception {
    SqliteProtectedBookPairPublicationRecord matched = retainedRecord("member-matched");
    Files.createLink(matched.bookTargetPath, matched.bookStagePath);
    Files.createLink(matched.secretTargetPath, matched.secretStagePath);
    SqlitePairPublicationMemberReconciler reconciler =
        reconciler((ignoredStep, ignoredParent) -> {});

    try (SqlitePublicationCapabilityWitness.Set witnesses = witnessesFor(matched)) {
      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.DURABLE,
          reconciler.reconcileSecret(
              matched,
              SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan
                  .MATCHED_OR_FORCEABLE,
              witnesses));
      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.DURABLE,
          reconciler.reconcileBook(
              matched,
              SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan
                  .MATCHED_OR_FORCEABLE,
              witnesses));
      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.OUTCOME_UNCERTAIN,
          reconciler.reconcileSecret(
              matched,
              SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.BLOCKED,
              witnesses));
      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.OUTCOME_UNCERTAIN,
          reconciler.reconcileBook(
              matched,
              SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.BLOCKED,
              witnesses));
    }
  }

  @Test
  void rekeyRecoveryPublishesTheGeneratedSecretBeforeAtomicallyReplacingTheSelectedBook()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord record = rekeyRecord("member-rekey");
    SqlitePairPublicationMemberReconciler reconciler =
        reconciler((ignoredStep, ignoredParent) -> {});

    try (SqlitePublicationCapabilityWitness.Set witnesses =
        SqlitePairPublicationRecoveryCapabilities.acquire(
            record,
            SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
            SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
            ProtectedBookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET)) {
      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.DURABLE,
          reconciler.reconcileSecret(
              record,
              SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
              witnesses));
      assertTrue(Files.isSameFile(record.secretTargetPath, record.secretStagePath));

      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.DURABLE,
          reconciler.reconcileBook(
              record,
              SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
              witnesses));
    }

    assertTrue(Files.isSameFile(record.bookTargetPath, record.bookStagePath));
  }

  @Test
  void exactRecoveryWorkflowPublishesOneOwnedStagedBackupPair() throws Exception {
    SqliteProtectedBookPairPublicationRecord record = retainedRecord("workflow-recovery");

    SqlitePairPublicationReconciliationRecovered recovered =
        assertInstanceOf(
            SqlitePairPublicationReconciliationRecovered.class,
            recoveryWorkflow(true)
                .recover(
                    record,
                    RestoredBookTargetPolicy.REQUIRE_ABSENT,
                    backupRequest(record.bookTargetPath),
                    ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                    false));

    assertTrue(recovered.binding().matches(backupRequest(record.bookTargetPath)));
    assertTrue(Files.isSameFile(record.bookTargetPath, record.bookStagePath));
    assertTrue(Files.isSameFile(record.secretTargetPath, record.secretStagePath));
  }

  @Test
  void workflowTreatsCompletedBackupAsIdempotentAndRetainedPrepublicationAsNotPublished()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord completed = retainedRecord("workflow-completed");
    Files.createLink(completed.bookTargetPath, completed.bookStagePath);
    Files.createLink(completed.secretTargetPath, completed.secretStagePath);
    SqliteProtectedBookPairPublicationEvidenceLifecycle.confirmCompletedPublication(
        completed, (ignoredStep, ignoredParent) -> {});

    assertInstanceOf(
        SqlitePairPublicationReconciliationExistingCompleteBackup.class,
        recoveryWorkflow(true)
            .recover(
                completed,
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                backupRequest(completed.bookTargetPath),
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                false));
    assertEquals(
        SqlitePairPublicationReconciliationAbsent.INSTANCE,
        recoveryWorkflow(true)
            .recover(
                completed,
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                restoreRequest(completed.bookTargetPath),
                ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET,
                ProtectedBookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET,
                false));

    SqliteProtectedBookPairPublicationRecord prepublication =
        retainedRecord("workflow-prepublication");
    SqliteProtectedBookPairPublicationEvidenceLifecycle.retainPrepublication(
        prepublication, (ignoredStep, ignoredParent) -> {});
    assertEquals(
        SqlitePairPublicationReconciliationAbsent.INSTANCE,
        recoveryWorkflow(true)
            .recover(
                prepublication,
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                backupRequest(prepublication.bookTargetPath),
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                false));
  }

  @Test
  void workflowRefusesMismatchedRecoveryBeforeItPublishesEitherMember() throws Exception {
    SqliteProtectedBookPairPublicationRecord record = retainedRecord("workflow-mismatch");
    ProtectedBookPairPublicationRecoveryRequest.Backup mismatchedRequest =
        new ProtectedBookPairPublicationRecoveryRequest.Backup(
            record.bookTargetPath.resolveSibling("source.sqlite"), new UUID(0L, 2L));

    ProtectedBookMaintenanceRejectionException refusal =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                recoveryWorkflow(true)
                    .recover(
                        record,
                        RestoredBookTargetPolicy.REQUIRE_ABSENT,
                        mismatchedRequest,
                        ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                        ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                        false));

    assertInstanceOf(ProtectedBookMaintenanceRejection.RecoveryPending.class, refusal.rejection());
    assertFalse(Files.exists(record.bookTargetPath));
    assertFalse(Files.exists(record.secretTargetPath));
  }

  @Test
  void workflowRejectsIncompleteEvidenceForAMismatchedRequestBeforeAnyRecoveryRepair()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord record =
        retainedRecord("workflow-incomplete-mismatch");
    ProtectedBookPairPublicationRecoveryRequest.Backup mismatchedRequest =
        new ProtectedBookPairPublicationRecoveryRequest.Backup(
            record.bookTargetPath.resolveSibling("other-source.sqlite"), new UUID(0L, 9L));

    ProtectedBookMaintenanceRejectionException refusal =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                recoveryWorkflow(true)
                    .recover(
                        record,
                        RestoredBookTargetPolicy.REQUIRE_ABSENT,
                        mismatchedRequest,
                        ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                        ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                        true));

    assertInstanceOf(ProtectedBookMaintenanceRejection.RecoveryPending.class, refusal.rejection());
    assertFalse(Files.exists(record.bookTargetPath));
    assertFalse(Files.exists(record.secretTargetPath));
  }

  @Test
  void workflowRepairsMissingMirroredEvidenceOnlyWhileBothExactTargetLeasesAreHeld()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord record = retainedRecord("workflow-evidence-repair");
    Path missingIntent =
        record.evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.INTENT).getFirst();
    Files.delete(missingIntent);

    SqliteManagedTargetLeasesHeld leases =
        assertInstanceOf(
            SqliteManagedTargetLeasesHeld.class,
            SqliteBookMaintenanceLease.acquireManagedTargetPair(
                record.bookTargetPath, record.secretTargetPath));
    try (SqliteHeldLease ignoredBookLease = leases.bookTargetLease();
        SqliteHeldLease ignoredSecretLease = leases.secretTargetLease()) {
      assertInstanceOf(
          SqlitePairPublicationReconciliationRecovered.class,
          recoveryWorkflow(true)
              .recover(
                  record,
                  RestoredBookTargetPolicy.REQUIRE_ABSENT,
                  backupRequest(record.bookTargetPath),
                  ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                  ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                  true));
    }

    assertTrue(
        SqliteProtectedBookPairPublicationEvidenceLifecycle.hasComplete(
            record, SqliteProtectedBookPairPublicationEvidenceKind.INTENT));
    assertTrue(Files.isSameFile(record.bookTargetPath, record.bookStagePath));
    assertTrue(Files.isSameFile(record.secretTargetPath, record.secretStagePath));
  }

  @Test
  void evidenceRepairRestoresObservedTerminalCopiesAndRetainsTheDurabilityFailure()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord record =
        retainedCrossParentRecord("terminal-evidence-repair");
    Files.createLink(record.bookTargetPath, record.bookStagePath);
    Files.createLink(record.secretTargetPath, record.secretStagePath);
    SqliteProtectedBookPairPublicationEvidenceLifecycle.retainPrepublication(
        record, (ignoredStep, ignoredParent) -> {});
    SqliteProtectedBookPairPublicationEvidenceLifecycle.confirmCompletedPublication(
        record, (ignoredStep, ignoredParent) -> {});
    Files.delete(
        record.evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.RETAINED).getLast());
    Files.delete(
        record.evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.COMPLETED).getLast());

    SqliteManagedTargetLeasesHeld leases =
        assertInstanceOf(
            SqliteManagedTargetLeasesHeld.class,
            SqliteBookMaintenanceLease.acquireManagedTargetPair(
                record.bookTargetPath, record.secretTargetPath));
    try (SqliteHeldLease ignoredBookLease = leases.bookTargetLease();
        SqliteHeldLease ignoredSecretLease = leases.secretTargetLease()) {
      SqlitePairPublicationEvidenceRecovery.repairIncompleteEvidence(
          record, (ignoredStep, ignoredParent) -> {});
    }

    assertTrue(
        SqlitePairPublicationEvidenceStatus.hasComplete(
            record, SqliteProtectedBookPairPublicationEvidenceKind.RETAINED));
    assertTrue(
        SqlitePairPublicationEvidenceStatus.hasComplete(
            record, SqliteProtectedBookPairPublicationEvidenceKind.COMPLETED));
    Path claimPath =
        record.evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.CLAIM).getFirst();
    SqliteProtectedBookPairPublicationRecord.RecoveryRecordDurabilityUnconfirmedException
        durabilityFailure =
            assertThrows(
                SqliteProtectedBookPairPublicationRecord
                    .RecoveryRecordDurabilityUnconfirmedException.class,
                () ->
                    SqlitePairPublicationEvidenceRecovery.forceCopy(
                        record,
                        SqliteProtectedBookPairPublicationEvidenceKind.CLAIM,
                        claimPath,
                        (ignoredStep, ignoredParent) -> {
                          throw new IOException("directory force failed");
                        }));
    assertEquals(
        "directory force failed",
        Objects.requireNonNull(durabilityFailure.getCause(), "durability failure cause")
            .getMessage());
  }

  @Test
  void completionEvidenceRejectsChangedMembersAndMetadataAboveItsBound() throws Exception {
    SqliteProtectedBookPairPublicationRecord changedMember = retainedRecord("completion-change");
    Files.createLink(changedMember.bookTargetPath, changedMember.bookStagePath);
    Files.createLink(changedMember.secretTargetPath, changedMember.secretStagePath);
    Files.delete(changedMember.bookTargetPath);
    Files.writeString(changedMember.bookTargetPath, "changed final member");

    IOException changedFinalMember =
        assertThrows(
            IOException.class,
            () ->
                SqliteProtectedBookPairPublicationEvidenceLifecycle.confirmCompletedPublication(
                    changedMember, (ignoredStep, ignoredParent) -> {}));
    assertTrue(
        Objects.requireNonNull(changedFinalMember.getMessage(), "changed-member message")
            .contains("final members changed"));

    String oversizedSegment =
        "x".repeat(SqliteSecureRegularFileAccess.MAXIMUM_RECOVERY_METADATA_BYTES);
    SqliteProtectedBookPairPublicationRecord oversizedRecord =
        new SqliteProtectedBookPairPublicationRecord(
            new SqliteProtectedBookPairPublicationRecord.Components(
                UUID.randomUUID(),
                new SqliteProtectedBookPairPublicationRecord.PairPaths(
                    Path.of("/synthetic-book", "book.sqlite"),
                    Path.of("/synthetic-secret", "book.key"),
                    Path.of("/synthetic-book", oversizedSegment),
                    Path.of("/synthetic-secret", "key.stage")),
                new SqliteProtectedBookPairPublicationRecord.PairDigests(
                    new byte[32], new byte[32], null),
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                backupBinding(Path.of("/synthetic-source"))));

    IOException oversizedMetadata =
        assertThrows(
            IOException.class,
            () ->
                SqlitePairPublicationEvidenceRecovery.writeNew(
                    tempDirectory.resolve("oversized-evidence"),
                    oversizedRecord,
                    SqliteProtectedBookPairPublicationEvidenceKind.CLAIM));
    assertTrue(
        Objects.requireNonNull(oversizedMetadata.getMessage(), "oversized-metadata message")
            .contains("exceeds its supported size"));
  }

  @Test
  void workflowBlocksPartialPrepublicationVisibilityInsteadOfReinterpretingItAsNewWork()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord record =
        retainedRecord("workflow-partial-prepublication");
    SqliteProtectedBookPairPublicationEvidenceLifecycle.retainPrepublication(
        record, (ignoredStep, ignoredParent) -> {});
    Files.createLink(record.bookTargetPath, record.bookStagePath);

    SqlitePairPublicationReconciliationEvidenceBlocked blocked =
        assertInstanceOf(
            SqlitePairPublicationReconciliationEvidenceBlocked.class,
            recoveryWorkflow(true)
                .recover(
                    record,
                    RestoredBookTargetPolicy.REQUIRE_ABSENT,
                    backupRequest(record.bookTargetPath),
                    ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                    false));

    assertEquals(record.bookTargetPath, blocked.bookArtifactPath());
    assertEquals(record.secretTargetPath, blocked.secretArtifactPath());
  }

  @Test
  void workflowRecoversVisibleExactPairByForcingItsDirectoriesAndRecheckingItsBinding()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord record = retainedRecord("workflow-visible-pair");
    Files.createLink(record.bookTargetPath, record.bookStagePath);
    Files.createLink(record.secretTargetPath, record.secretStagePath);

    SqlitePairPublicationReconciliationRecovered recovered =
        assertInstanceOf(
            SqlitePairPublicationReconciliationRecovered.class,
            recoveryWorkflow(true)
                .recover(
                    record,
                    RestoredBookTargetPolicy.REQUIRE_ABSENT,
                    backupRequest(record.bookTargetPath),
                    ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                    false));

    assertTrue(recovered.binding().matches(backupRequest(record.bookTargetPath)));
  }

  @Test
  void workflowKeepsAVisiblePairUncertainWhenItsRecoveryDurabilityCannotBeConfirmed()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord record =
        retainedRecord("workflow-visible-force-failure");
    Files.createLink(record.bookTargetPath, record.bookStagePath);
    Files.createLink(record.secretTargetPath, record.secretStagePath);

    SqlitePairPublicationReconciliationCompletionUncertain uncertain =
        assertInstanceOf(
            SqlitePairPublicationReconciliationCompletionUncertain.class,
            recoveryWorkflow(
                    true,
                    (ignoredStep, ignoredParent) -> {
                      throw new IOException("injected visible-pair force failure");
                    })
                .recover(
                    record,
                    RestoredBookTargetPolicy.REQUIRE_ABSENT,
                    backupRequest(record.bookTargetPath),
                    ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                    false));

    assertEquals(
        ProtectedBookPairPublicationMemberState.PUBLISHED_DURABILITY_UNCONFIRMED,
        uncertain.bookArtifactState());
    assertEquals(
        ProtectedBookPairPublicationMemberState.PUBLISHED_DURABILITY_UNCONFIRMED,
        uncertain.secretArtifactState());
  }

  @Test
  void workflowKeepsAMismatchedButFullyVisiblePairUncertainRatherThanReusingIt() throws Exception {
    SqliteProtectedBookPairPublicationRecord record = retainedRecord("workflow-visible-mismatch");
    Files.createLink(record.bookTargetPath, record.bookStagePath);
    Files.createLink(record.secretTargetPath, record.secretStagePath);

    SqlitePairPublicationReconciliationCompletionUncertain uncertain =
        assertInstanceOf(
            SqlitePairPublicationReconciliationCompletionUncertain.class,
            recoveryWorkflow(true)
                .recover(
                    record,
                    RestoredBookTargetPolicy.REPLACE_SELECTED,
                    backupRequest(record.bookTargetPath),
                    ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                    false));

    assertEquals(
        ProtectedBookPairPublicationMemberState.PUBLISHED_DURABILITY_UNCONFIRMED,
        uncertain.bookArtifactState());
    assertEquals(
        ProtectedBookPairPublicationMemberState.PUBLISHED_DURABILITY_UNCONFIRMED,
        uncertain.secretArtifactState());
  }

  @Test
  void workflowKeepsVisibleOrStagedPairsUncertainWhenTheirStageOwnershipIsMissing()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord visible =
        pairRecord("workflow-visible-unowned", false);
    Files.createLink(visible.bookTargetPath, visible.bookStagePath);
    Files.createLink(visible.secretTargetPath, visible.secretStagePath);

    assertInstanceOf(
        SqlitePairPublicationReconciliationCompletionUncertain.class,
        recoveryWorkflow(true)
            .recover(
                visible,
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                backupRequest(visible.bookTargetPath),
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                false));

    SqliteProtectedBookPairPublicationRecord staged = pairRecord("workflow-staged-unowned", false);
    assertInstanceOf(
        SqlitePairPublicationReconciliationCompletionUncertain.class,
        recoveryWorkflow(true)
            .recover(
                staged,
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                backupRequest(staged.bookTargetPath),
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                false));
  }

  @Test
  void workflowKeepsAPlanBlockedByForeignBookBytesUncertainWithoutPublishingTheSecret()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord record = retainedRecord("workflow-blocked-plan");
    Files.writeString(record.bookTargetPath, "foreign book bytes");

    SqlitePairPublicationReconciliationCompletionUncertain uncertain =
        assertInstanceOf(
            SqlitePairPublicationReconciliationCompletionUncertain.class,
            recoveryWorkflow(true)
                .recover(
                    record,
                    RestoredBookTargetPolicy.REQUIRE_ABSENT,
                    backupRequest(record.bookTargetPath),
                    ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                    false));

    assertEquals(
        ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN, uncertain.bookArtifactState());
    assertEquals(
        ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN, uncertain.secretArtifactState());
    assertFalse(Files.exists(record.secretTargetPath));
  }

  @Test
  void workflowRetainsAnUncertainOutcomeWhenSecretOrBookPublicationCannotBeForced()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord secretFailure =
        retainedRecord("workflow-secret-force-failure");
    SqlitePairPublicationReconciliationCompletionUncertain secretUncertain =
        assertInstanceOf(
            SqlitePairPublicationReconciliationCompletionUncertain.class,
            recoveryWorkflow(
                    true,
                    (step, ignoredParent) -> {
                      if (step
                          == SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
                              .GENERATED_SECRET_PUBLICATION) {
                        throw new IOException("injected secret publication force failure");
                      }
                    })
                .recover(
                    secretFailure,
                    RestoredBookTargetPolicy.REQUIRE_ABSENT,
                    backupRequest(secretFailure.bookTargetPath),
                    ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                    false));
    assertEquals(
        ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN,
        secretUncertain.bookArtifactState());
    assertEquals(
        ProtectedBookPairPublicationMemberState.PUBLISHED_DURABILITY_UNCONFIRMED,
        secretUncertain.secretArtifactState());

    SqliteProtectedBookPairPublicationRecord bookFailure =
        retainedRecord("workflow-book-force-failure");
    SqlitePairPublicationReconciliationCompletionUncertain bookUncertain =
        assertInstanceOf(
            SqlitePairPublicationReconciliationCompletionUncertain.class,
            recoveryWorkflow(
                    true,
                    (step, ignoredParent) -> {
                      if (step
                          == SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
                              .BOOK_PUBLICATION) {
                        throw new IOException("injected book publication force failure");
                      }
                    })
                .recover(
                    bookFailure,
                    RestoredBookTargetPolicy.REQUIRE_ABSENT,
                    backupRequest(bookFailure.bookTargetPath),
                    ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                    false));
    assertEquals(
        ProtectedBookPairPublicationMemberState.PUBLISHED_DURABILITY_UNCONFIRMED,
        bookUncertain.bookArtifactState());
    assertEquals(
        ProtectedBookPairPublicationMemberState.PUBLISHED_DURABLE,
        bookUncertain.secretArtifactState());
  }

  @Test
  void evidenceClassifierDistinguishesExactIncompleteAndUnsafeRecoveryEvidence() throws Exception {
    SqliteProtectedBookPairPublicationRecord exact = retainedRecord("classifier-exact");

    assertInstanceOf(
        SqlitePairPublicationEvidenceExact.class,
        SqlitePairPublicationEvidenceClassifier.classify(
            exact.bookTargetPath, exact.secretTargetPath, Map.of(exact.pairId, exact)));

    SqliteProtectedBookPairPublicationRecord duplicate =
        SqliteProtectedBookPairPublicationRecord.create(
            exact.bookTargetPath,
            exact.secretTargetPath,
            writeArtifact("classifier-exact/.other-book.stage", "other retained book stage"),
            writeArtifact("classifier-exact/.other-secret.stage", "other retained secret stage"),
            RestoredBookTargetPolicy.REQUIRE_ABSENT,
            backupBinding(exact.bookTargetPath.resolveSibling("source.sqlite")),
            (ignoredStep, ignoredParent) -> {});
    assertEquals(
        SqlitePairPublicationEvidenceUnsafe.INSTANCE,
        SqlitePairPublicationEvidenceClassifier.classify(
            exact.bookTargetPath,
            exact.secretTargetPath,
            Map.of(exact.pairId, exact, duplicate.pairId, duplicate)));

    SqliteProtectedBookPairPublicationEvidenceLifecycle.retainPrepublication(
        exact, (ignoredStep, ignoredParent) -> {});
    assertEquals(
        SqlitePairPublicationEvidenceAbsent.INSTANCE,
        SqlitePairPublicationEvidenceClassifier.classify(
            exact.bookTargetPath, exact.secretTargetPath, Map.of(exact.pairId, exact)));

    Files.writeString(
        exact.evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.RETAINED).getFirst(),
        "changed retained evidence");
    assertInstanceOf(
        SqlitePairPublicationEvidenceExactIncomplete.class,
        SqlitePairPublicationEvidenceClassifier.classify(
            exact.bookTargetPath, exact.secretTargetPath, Map.of(exact.pairId, exact)));
    assertInstanceOf(
        SqlitePairPublicationEvidenceOtherPending.class,
        SqlitePairPublicationEvidenceClassifier.classify(
            exact.bookTargetPath.resolveSibling("other-book.sqlite"),
            exact.secretTargetPath.resolveSibling("other-book.key"),
            Map.of(exact.pairId, exact)));
  }

  @Test
  void evidenceClassifierTreatsDurablyCompletedBackupAsAuthoritativeAndIncompleteClaimsAsUnsafe()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord completed = retainedRecord("classifier-completed");
    Files.createLink(completed.bookTargetPath, completed.bookStagePath);
    Files.createLink(completed.secretTargetPath, completed.secretStagePath);
    SqliteProtectedBookPairPublicationEvidenceLifecycle.confirmCompletedPublication(
        completed, (ignoredStep, ignoredParent) -> {});

    assertInstanceOf(
        SqlitePairPublicationEvidenceExact.class,
        SqlitePairPublicationEvidenceClassifier.classify(
            completed.bookTargetPath,
            completed.secretTargetPath.resolveSibling("unrelated.key"),
            Map.of(completed.pairId, completed)));

    SqliteProtectedBookPairPublicationRecord partialClaim = retainedRecord("classifier-claim");
    Files.writeString(
        partialClaim.evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.CLAIM).getFirst(),
        "changed claim evidence");
    for (Path evidencePath :
        partialClaim.evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.INTENT)) {
      Files.delete(evidencePath);
    }
    for (Path evidencePath :
        partialClaim.evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.RECOVERY)) {
      Files.delete(evidencePath);
    }

    assertEquals(
        SqlitePairPublicationEvidenceUnsafe.INSTANCE,
        SqlitePairPublicationEvidenceClassifier.classify(
            partialClaim.bookTargetPath,
            partialClaim.secretTargetPath,
            Map.of(partialClaim.pairId, partialClaim)));
  }

  @Test
  void evidenceClassifierIgnoresInertClaimResidueAndCompletedNonBackupPublications()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord claimOnly = retainedRecord("classifier-claim-only");
    deleteEvidence(claimOnly, SqliteProtectedBookPairPublicationEvidenceKind.INTENT);
    deleteEvidence(claimOnly, SqliteProtectedBookPairPublicationEvidenceKind.RECOVERY);

    assertEquals(
        SqlitePairPublicationEvidenceAbsent.INSTANCE,
        SqlitePairPublicationEvidenceClassifier.classify(
            claimOnly.bookTargetPath,
            claimOnly.secretTargetPath,
            Map.of(claimOnly.pairId, claimOnly)));

    SqliteProtectedBookPairPublicationRecord completedRekey =
        rekeyRecord("classifier-completed-rekey");
    Files.delete(completedRekey.bookTargetPath);
    Files.createLink(completedRekey.bookTargetPath, completedRekey.bookStagePath);
    Files.createLink(completedRekey.secretTargetPath, completedRekey.secretStagePath);
    SqliteProtectedBookPairPublicationEvidenceLifecycle.confirmCompletedPublication(
        completedRekey, (ignoredStep, ignoredParent) -> {});

    assertEquals(
        SqlitePairPublicationEvidenceAbsent.INSTANCE,
        SqlitePairPublicationEvidenceClassifier.classify(
            completedRekey.bookTargetPath,
            completedRekey.secretTargetPath,
            Map.of(completedRekey.pairId, completedRekey)));

    SqliteProtectedBookPairPublicationRecord completedBackup =
        retainedRecord("classifier-completed-unrelated-backup");
    Files.createLink(completedBackup.bookTargetPath, completedBackup.bookStagePath);
    Files.createLink(completedBackup.secretTargetPath, completedBackup.secretStagePath);
    SqliteProtectedBookPairPublicationEvidenceLifecycle.confirmCompletedPublication(
        completedBackup, (ignoredStep, ignoredParent) -> {});

    assertEquals(
        SqlitePairPublicationEvidenceAbsent.INSTANCE,
        SqlitePairPublicationEvidenceClassifier.classify(
            completedBackup.bookTargetPath.resolveSibling("unrelated.sqlite"),
            completedBackup.secretTargetPath,
            Map.of(completedBackup.pairId, completedBackup)));
  }

  @Test
  void evidenceClassifierDistinguishesPendingIncompleteAndCompletionUncertainEvidence()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord pending =
        pairRecord("classifier-pending-other-target", false);

    assertInstanceOf(
        SqlitePairPublicationEvidenceOtherPending.class,
        SqlitePairPublicationEvidenceClassifier.classify(
            pending.bookTargetPath.resolveSibling("other-book.sqlite"),
            pending.secretTargetPath.resolveSibling("other-book.key"),
            Map.of(pending.pairId, pending)));

    SqliteProtectedBookPairPublicationRecord incompleteClaim =
        retainedRecord("classifier-incomplete-claim-terminal");
    Files.writeString(
        incompleteClaim
            .evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.CLAIM)
            .getFirst(),
        "changed claim evidence");

    assertEquals(
        SqlitePairPublicationEvidenceUnsafe.INSTANCE,
        SqlitePairPublicationEvidenceClassifier.classify(
            incompleteClaim.bookTargetPath,
            incompleteClaim.secretTargetPath,
            Map.of(incompleteClaim.pairId, incompleteClaim)));

    SqliteProtectedBookPairPublicationRecord incompleteCompletion =
        retainedRecord("classifier-incomplete-completion");
    SqliteProtectedBookPairPublicationEvidenceLifecycle.retainPrepublication(
        incompleteCompletion, (ignoredStep, ignoredParent) -> {});
    Files.createLink(incompleteCompletion.bookTargetPath, incompleteCompletion.bookStagePath);
    Files.createLink(incompleteCompletion.secretTargetPath, incompleteCompletion.secretStagePath);
    SqliteProtectedBookPairPublicationEvidenceLifecycle.confirmCompletedPublication(
        incompleteCompletion, (ignoredStep, ignoredParent) -> {});
    Files.writeString(
        incompleteCompletion
            .evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.COMPLETED)
            .getFirst(),
        "changed completed evidence");
    Files.delete(incompleteCompletion.secretTargetPath);

    assertInstanceOf(
        SqlitePairPublicationEvidenceExactIncomplete.class,
        SqlitePairPublicationEvidenceClassifier.classify(
            incompleteCompletion.bookTargetPath,
            incompleteCompletion.secretTargetPath,
            Map.of(incompleteCompletion.pairId, incompleteCompletion)));

    SqliteProtectedBookPairPublicationRecord partialCompleted =
        retainedRecord("classifier-partial-completed");
    SqliteProtectedBookPairPublicationEvidenceLifecycle.retainPrepublication(
        partialCompleted, (ignoredStep, ignoredParent) -> {});
    Files.createLink(partialCompleted.bookTargetPath, partialCompleted.bookStagePath);

    assertInstanceOf(
        SqlitePairPublicationEvidenceExact.class,
        SqlitePairPublicationEvidenceClassifier.classify(
            partialCompleted.bookTargetPath,
            partialCompleted.secretTargetPath,
            Map.of(partialCompleted.pairId, partialCompleted)));

    SqliteProtectedBookPairPublicationRecord claimOnlyWithVisibleBook =
        retainedRecord("classifier-claim-only-visible-book");
    deleteEvidence(claimOnlyWithVisibleBook, SqliteProtectedBookPairPublicationEvidenceKind.INTENT);
    deleteEvidence(
        claimOnlyWithVisibleBook, SqliteProtectedBookPairPublicationEvidenceKind.RECOVERY);
    Files.createLink(
        claimOnlyWithVisibleBook.bookTargetPath, claimOnlyWithVisibleBook.bookStagePath);

    assertEquals(
        SqlitePairPublicationEvidenceUnsafe.INSTANCE,
        SqlitePairPublicationEvidenceClassifier.classify(
            claimOnlyWithVisibleBook.bookTargetPath,
            claimOnlyWithVisibleBook.secretTargetPath,
            Map.of(claimOnlyWithVisibleBook.pairId, claimOnlyWithVisibleBook)));

    SqliteProtectedBookPairPublicationRecord incompleteIntent =
        retainedRecord("classifier-incomplete-intent-terminal");
    Files.writeString(
        incompleteIntent
            .evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.INTENT)
            .getFirst(),
        "changed intent evidence");

    assertEquals(
        SqlitePairPublicationEvidenceUnsafe.INSTANCE,
        SqlitePairPublicationEvidenceClassifier.classify(
            incompleteIntent.bookTargetPath,
            incompleteIntent.secretTargetPath,
            Map.of(incompleteIntent.pairId, incompleteIntent)));
  }

  @Test
  void evidenceStatusRejectsAParseableEnvelopeWithTheWrongKindAndPinsEachDurabilityBarrier()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord record = retainedRecord("evidence-status");
    Path claimPath =
        record.evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.CLAIM).getFirst();
    Path intentPath =
        record.evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.INTENT).getFirst();

    assertTrue(
        SqlitePairPublicationEvidenceStatus.hasComplete(
            record, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM));
    Files.writeString(claimPath, Files.readString(intentPath));
    assertFalse(
        SqlitePairPublicationEvidenceStatus.hasComplete(
            record, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM));
    IOException wrongKind =
        assertThrows(
            IOException.class,
            () ->
                SqlitePairPublicationEvidenceStatus.requireExact(
                    record, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM, claimPath));
    assertTrue(
        Objects.requireNonNull(wrongKind.getMessage(), "wrong-kind evidence message")
            .contains("evidence changed"));

    SqliteProtectedBookPairPublicationRecord malformed =
        retainedRecord("evidence-status-malformed");
    Path malformedClaim =
        malformed.evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.CLAIM).getFirst();
    Files.writeString(malformedClaim, "not protected-book recovery evidence");
    assertTrue(
        SqlitePairPublicationEvidenceStatus.hasObserved(
            malformed, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM));
    assertFalse(
        SqlitePairPublicationEvidenceStatus.hasComplete(
            malformed, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM));
    assertThrows(
        IOException.class,
        () ->
            SqlitePairPublicationEvidenceStatus.requireComplete(
                malformed, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM));

    SqliteProtectedBookPairPublicationRecord mismatched =
        retainedRecord("evidence-status-mismatch");
    SqliteProtectedBookPairPublicationRecord differentRecord =
        retainedRecord("evidence-status-different-record");
    Path mismatchedClaim =
        mismatched.evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.CLAIM).getFirst();
    Path differentClaim =
        differentRecord
            .evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.CLAIM)
            .getFirst();
    Files.writeString(mismatchedClaim, Files.readString(differentClaim));
    assertFalse(
        SqlitePairPublicationEvidenceStatus.hasComplete(
            mismatched, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM));
    assertThrows(
        IOException.class,
        () ->
            SqlitePairPublicationEvidenceStatus.requireExact(
                mismatched, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM, mismatchedClaim));

    SqliteProtectedBookPairPublicationRecord samePathDifferentRecord =
        retainedRecord("evidence-status-same-path-different-record");
    Path samePathDifferentClaim =
        samePathDifferentRecord
            .evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.CLAIM)
            .getFirst();
    SqliteProtectedBookPairPublicationRecord alteredImmutableRecord =
        withChangedBookDigest(samePathDifferentRecord);
    Files.writeString(
        samePathDifferentClaim,
        SqliteProtectedBookPairPublicationEvidenceCodec.encoded(
            alteredImmutableRecord, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM));
    assertFalse(
        SqlitePairPublicationEvidenceStatus.hasComplete(
            samePathDifferentRecord, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM));
    assertThrows(
        IOException.class,
        () ->
            SqlitePairPublicationEvidenceStatus.requireExact(
                samePathDifferentRecord,
                SqliteProtectedBookPairPublicationEvidenceKind.CLAIM,
                samePathDifferentClaim));

    assertEquals(
        SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep.PAIR_STAGE_CLAIM,
        SqlitePairPublicationEvidenceStatus.durabilityStep(
            SqliteProtectedBookPairPublicationEvidenceKind.CLAIM));
    assertEquals(
        SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep.RECOVERY_INTENT,
        SqlitePairPublicationEvidenceStatus.durabilityStep(
            SqliteProtectedBookPairPublicationEvidenceKind.INTENT));
    assertEquals(
        SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep.RECOVERY_RECORD,
        SqlitePairPublicationEvidenceStatus.durabilityStep(
            SqliteProtectedBookPairPublicationEvidenceKind.RECOVERY));
    assertEquals(
        SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
            .PREPUBLICATION_RETENTION,
        SqlitePairPublicationEvidenceStatus.durabilityStep(
            SqliteProtectedBookPairPublicationEvidenceKind.RETAINED));
    assertEquals(
        SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
            .RECOVERY_TERMINAL_RETENTION,
        SqlitePairPublicationEvidenceStatus.durabilityStep(
            SqliteProtectedBookPairPublicationEvidenceKind.COMPLETED));
  }

  @Test
  void evidenceStateDistinguishesClaimOnlyAndIncompleteRetainedOrCompletedEvidence()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord claimOnly = retainedRecord("evidence-state-claim");
    assertFalse(SqlitePairPublicationEvidenceState.isCompleteClaimOnly(claimOnly));
    assertFalse(SqlitePairPublicationEvidenceState.hasNoAuthorizationEvidence(claimOnly));

    deleteEvidence(claimOnly, SqliteProtectedBookPairPublicationEvidenceKind.INTENT);
    assertFalse(SqlitePairPublicationEvidenceState.isCompleteClaimOnly(claimOnly));
    assertFalse(SqlitePairPublicationEvidenceState.hasNoAuthorizationEvidence(claimOnly));

    deleteEvidence(claimOnly, SqliteProtectedBookPairPublicationEvidenceKind.RECOVERY);
    assertTrue(SqlitePairPublicationEvidenceState.isCompleteClaimOnly(claimOnly));
    assertTrue(SqlitePairPublicationEvidenceState.hasNoAuthorizationEvidence(claimOnly));

    SqliteProtectedBookPairPublicationRecord retainedClaim =
        retainedRecord("evidence-state-retained-claim");
    SqliteProtectedBookPairPublicationEvidenceLifecycle.retainPrepublication(
        retainedClaim, (ignoredStep, ignoredParent) -> {});
    assertFalse(SqlitePairPublicationEvidenceState.isCompleteClaimOnly(retainedClaim));

    SqliteProtectedBookPairPublicationRecord incompleteRetained =
        retainedRecord("evidence-state-retained");
    SqliteProtectedBookPairPublicationEvidenceLifecycle.retainPrepublication(
        incompleteRetained, (ignoredStep, ignoredParent) -> {});
    Files.writeString(
        incompleteRetained
            .evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.RETAINED)
            .getFirst(),
        "changed retained evidence");
    assertTrue(SqlitePairPublicationEvidenceState.retainedEvidenceIsIncomplete(incompleteRetained));

    SqliteProtectedBookPairPublicationRecord incompleteCompleted =
        retainedRecord("evidence-state-completed");
    Files.createLink(incompleteCompleted.bookTargetPath, incompleteCompleted.bookStagePath);
    Files.createLink(incompleteCompleted.secretTargetPath, incompleteCompleted.secretStagePath);
    SqliteProtectedBookPairPublicationEvidenceLifecycle.confirmCompletedPublication(
        incompleteCompleted, (ignoredStep, ignoredParent) -> {});
    Files.writeString(
        incompleteCompleted
            .evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.COMPLETED)
            .getFirst(),
        "changed completed evidence");
    assertTrue(
        SqlitePairPublicationEvidenceState.completionEvidenceIsIncomplete(incompleteCompleted));
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

  private static SqlitePairPublicationRecoveryWorkflow recoveryWorkflow(
      boolean verifiesRecoveredPair) {
    return recoveryWorkflow(verifiesRecoveredPair, (ignoredStep, ignoredParent) -> {});
  }

  private static SqlitePairPublicationRecoveryWorkflow recoveryWorkflow(
      boolean verifiesRecoveredPair,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer) {
    return new SqlitePairPublicationRecoveryWorkflow(
        (ignoredBook, ignoredSecret, ignoredBinding) -> verifiesRecoveredPair,
        directoryForcer,
        ignoredRecord -> {});
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

  private SqliteProtectedBookPairPublicationRecord retainedCrossParentRecord(String directoryName)
      throws IOException {
    Path bookTarget = absentTarget(directoryName + "/book/book.sqlite");
    Path secretTarget = absentTarget(directoryName + "/secret/book.key");
    Path bookStage = writeArtifact(directoryName + "/book/.book.stage", "retained book stage");
    Path secretStage =
        writeArtifact(directoryName + "/secret/.secret.stage", "retained secret stage");
    SqliteOwnedStagedArtifact.recordExisting(bookTarget, bookStage);
    SqliteOwnedStagedArtifact.recordExisting(secretTarget, secretStage);
    return SqliteProtectedBookPairPublicationRecord.create(
        bookTarget,
        secretTarget,
        bookStage,
        secretStage,
        RestoredBookTargetPolicy.REQUIRE_ABSENT,
        backupBinding(bookTarget.resolveSibling("source.sqlite")),
        (ignoredStep, ignoredParent) -> {});
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

  private static SqliteProtectedBookPairPublicationRecord withChangedBookDigest(
      SqliteProtectedBookPairPublicationRecord original) {
    byte[] changedBookDigest = original.bookDigest.clone();
    changedBookDigest[0] ^= 1;
    return new SqliteProtectedBookPairPublicationRecord(
        new SqliteProtectedBookPairPublicationRecord.Components(
            original.pairId,
            new SqliteProtectedBookPairPublicationRecord.PairPaths(
                original.bookTargetPath,
                original.secretTargetPath,
                original.bookStagePath,
                original.secretStagePath),
            new SqliteProtectedBookPairPublicationRecord.PairDigests(
                changedBookDigest, original.secretDigest, original.replaceTargetDigest),
            original.bookTargetPolicy,
            original.binding));
  }

  private SqliteProtectedBookPairPublicationRecord rekeyRecord(String directoryName)
      throws IOException {
    Path bookTarget = writeArtifact(directoryName + "/book.sqlite", "selected source book");
    Path secretTarget = absentTarget(directoryName + "/book.key");
    Path bookStage = writeArtifact(directoryName + "/.book.stage", "rekeyed book");
    Path secretStage = writeArtifact(directoryName + "/.secret.stage", "generated key");
    SqliteOwnedStagedArtifact.recordExisting(bookTarget, bookStage);
    SqliteOwnedStagedArtifact.recordExisting(secretTarget, secretStage);
    return SqliteProtectedBookPairPublicationRecord.create(
        bookTarget,
        secretTarget,
        bookStage,
        secretStage,
        RestoredBookTargetPolicy.REPLACE_SELECTED,
        rekeyBinding(bookTarget, bookTarget.resolveSibling("source.book-key")),
        (ignoredStep, ignoredParent) -> {});
  }

  private static void deleteEvidence(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPairPublicationEvidenceKind evidenceKind)
      throws IOException {
    for (Path evidencePath : record.evidencePaths(evidenceKind)) {
      Files.delete(evidencePath);
    }
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
