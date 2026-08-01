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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Exercises direct pair-recovery admission and member-level publication recovery. */
class SqliteProtectedBookPairPublicationRecoveryTest
    extends SqliteProtectedBookPairPublicationRecoveryTestSupport {
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
  void completedEarlierRekeyEvidenceDoesNotHideANewOccupiedGeneratedSecret() throws Exception {
    SqliteProtectedBookPairPublicationRecord earlierRekey = rekeyRecord("completed-rekey-history");
    Files.delete(earlierRekey.bookTargetPath);
    Files.createLink(earlierRekey.bookTargetPath, earlierRekey.bookStagePath);
    Files.createLink(earlierRekey.secretTargetPath, earlierRekey.secretStagePath);
    SqliteProtectedBookPairPublicationEvidenceLifecycle.confirmCompletedPublication(
        earlierRekey, (ignoredStep, ignoredParent) -> {});
    Path occupiedNewSecret =
        writeArtifact("completed-rekey-next/occupied-new.key", "caller-owned key artifact");

    ProtectedBookMaintenanceRejectionException refusal =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                recovery()
                    .reconcile(
                        earlierRekey.bookTargetPath,
                        occupiedNewSecret,
                        RestoredBookTargetPolicy.REPLACE_SELECTED,
                        new ProtectedBookPairPublicationRecoveryRequest.Rekey(
                            rekeyBinding(
                                    earlierRekey.bookTargetPath,
                                    earlierRekey.bookTargetPath.resolveSibling("current.key"))
                                .sourceIdentity()),
                        ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
                        ProtectedBookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET));

    assertEquals(
        occupiedNewSecret.toAbsolutePath().normalize(),
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
  void rekeyRecoveryRefusesAnAbsentTargetPolicyBeforeItCanReclassifyTheLiveBook() throws Exception {
    Path liveBook = writeArtifact("invalid-rekey-policy/live.sqlite", "selected live book");
    Path occupiedSecret = writeArtifact("invalid-rekey-policy/new.key", "occupied generated key");
    ProtectedBookPairPublicationRecoveryRequest.Rekey request =
        new ProtectedBookPairPublicationRecoveryRequest.Rekey(
            rekeyBinding(liveBook, liveBook.resolveSibling("live.key")).sourceIdentity());

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                recovery()
                    .reconcile(
                        liveBook,
                        occupiedSecret,
                        RestoredBookTargetPolicy.REQUIRE_ABSENT,
                        request,
                        ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
                        ProtectedBookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET));

    assertEquals(
        "A rekey pair publication must replace its selected live book target.",
        exception.getMessage());
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
  void existingCompleteBackupClassificationRequiresBothRegularMembersAndABackupRequest()
      throws Exception {
    Path book = writeArtifact("complete-classification/backup.sqlite", "backup bytes");
    Path secret = writeArtifact("complete-classification/backup.key", "backup key");

    assertInstanceOf(
        SqlitePairPublicationReconciliationExistingCompleteBackup.class,
        SqliteProtectedBookPairPublicationRecovery.existingCompleteBackupOrEvidenceBlocked(
            backupRequest(book), book, secret));
    assertInstanceOf(
        SqlitePairPublicationReconciliationEvidenceBlocked.class,
        SqliteProtectedBookPairPublicationRecovery.existingCompleteBackupOrEvidenceBlocked(
            restoreRequest(book), book, secret));

    Path directoryInsteadOfBook =
        Files.createDirectory(tempDirectory.resolve("complete-classification/dir"));
    assertInstanceOf(
        SqlitePairPublicationReconciliationEvidenceBlocked.class,
        SqliteProtectedBookPairPublicationRecovery.existingCompleteBackupOrEvidenceBlocked(
            backupRequest(directoryInsteadOfBook), directoryInsteadOfBook, secret));

    Path directoryInsteadOfSecret =
        Files.createDirectory(tempDirectory.resolve("complete-classification/secret-dir"));
    assertInstanceOf(
        SqlitePairPublicationReconciliationEvidenceBlocked.class,
        SqliteProtectedBookPairPublicationRecovery.existingCompleteBackupOrEvidenceBlocked(
            backupRequest(book), book, directoryInsteadOfSecret));
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
  void recoverySupport_blocksChangedStagesAndChangedReplaceTargetsAndRequiresBothOwnedStages()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord changedBookStage =
        retainedRecord("recovery-support-changed-book-stage");
    Files.writeString(changedBookStage.bookStagePath, "changed retained book stage");
    assertEquals(
        SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.BLOCKED,
        SqliteProtectedBookPairPublicationRecoverySupport.bookPlan(changedBookStage));

    SqliteProtectedBookPairPublicationRecord changedSecretStage =
        retainedRecord("recovery-support-changed-secret-stage");
    Files.writeString(changedSecretStage.secretStagePath, "changed retained secret stage");
    assertEquals(
        SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.BLOCKED,
        SqliteProtectedBookPairPublicationRecoverySupport.secretPlan(changedSecretStage));

    SqliteProtectedBookPairPublicationRecord changedReplaceTarget =
        rekeyRecord("recovery-support-changed-replace-target");
    assertEquals(
        SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
        SqliteProtectedBookPairPublicationRecoverySupport.bookPlan(changedReplaceTarget));
    Files.writeString(changedReplaceTarget.bookTargetPath, "changed selected live book");
    assertEquals(
        SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.BLOCKED,
        SqliteProtectedBookPairPublicationRecoverySupport.bookPlan(changedReplaceTarget));

    SqliteProtectedBookPairPublicationRecord onlyBookStageOwned =
        pairRecord("recovery-support-only-book-stage-owned", false);
    SqliteOwnedStagedArtifact bookStage =
        SqliteOwnedStagedArtifact.recordExisting(
            onlyBookStageOwned.bookTargetPath, onlyBookStageOwned.bookStagePath);
    try {
      assertFalse(
          SqliteProtectedBookPairPublicationRecoverySupport.hasOwnedStages(onlyBookStageOwned));
    } finally {
      bookStage.releaseRetained();
    }
  }

  @Test
  void rekeyAdmissionTreatsUnboundStageResidueAsEvidenceBlockedRatherThanASecretCollision()
      throws Exception {
    Path bookTarget = writeArtifact("rekey-unbound-stage/book.sqlite", "selected live book");
    Path secretTarget = writeArtifact("rekey-unbound-stage/book.key", "occupied generated secret");
    Path secretStage = writeArtifact("rekey-unbound-stage/.book.key.stage", "retained key stage");
    SqliteOwnedStagedArtifact.recordExisting(secretTarget, secretStage);

    assertInstanceOf(
        SqlitePairPublicationReconciliationEvidenceBlocked.class,
        recovery()
            .reconcile(
                bookTarget,
                secretTarget,
                RestoredBookTargetPolicy.REPLACE_SELECTED,
                new ProtectedBookPairPublicationRecoveryRequest.Rekey(
                    rekeyBinding(bookTarget, bookTarget.resolveSibling("source.key"))
                        .sourceIdentity()),
                ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
                ProtectedBookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET));
  }

  @Test
  void memberRecoveryRechecksStageAndRekeyTargetFactsAtTheFinalPublicationBoundary()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord changedBookStage =
        retainedRecord("member-book-stage-preflight");
    Files.writeString(changedBookStage.bookStagePath, "changed retained book stage");
    try (SqlitePublicationCapabilityWitness.Set witnesses = witnessesFor(changedBookStage)) {
      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.OUTCOME_UNCERTAIN,
          reconciler((ignoredStep, ignoredParent) -> {})
              .reconcileBook(
                  changedBookStage,
                  SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan
                      .PUBLISH_ELIGIBLE,
                  witnesses));
    }

    SqliteProtectedBookPairPublicationRecord changedSecretStage =
        retainedRecord("member-secret-stage-after-evidence");
    AtomicInteger evidenceForceCalls = new AtomicInteger();
    try (SqlitePublicationCapabilityWitness.Set witnesses = witnessesFor(changedSecretStage)) {
      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.OUTCOME_UNCERTAIN,
          reconciler(
                  (ignoredStep, ignoredParent) -> {},
                  evidencePath -> {
                    SqliteOwnedRegularFileAccess.forceFile(evidencePath);
                    if (evidenceForceCalls.getAndIncrement() == 0) {
                      Files.writeString(
                          changedSecretStage.secretStagePath, "changed retained secret stage");
                    }
                  })
              .reconcileSecret(
                  changedSecretStage,
                  SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan
                      .PUBLISH_ELIGIBLE,
                  witnesses));
    }
    assertTrue(evidenceForceCalls.get() > 0);

    SqliteProtectedBookPairPublicationRecord convergedRekey =
        rekeyRecord("member-rekey-converged-book");
    try (SqlitePublicationCapabilityWitness.Set witnesses =
        SqlitePairPublicationRecoveryCapabilities.acquire(
            convergedRekey,
            SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
            SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
            ProtectedBookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET)) {
      Files.writeString(
          convergedRekey.bookTargetPath, Files.readString(convergedRekey.bookStagePath));
      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.DURABLE,
          reconciler((ignoredStep, ignoredParent) -> {})
              .reconcileSecret(
                  convergedRekey,
                  SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan
                      .PUBLISH_ELIGIBLE,
                  witnesses));
    }

    assertTrue(Files.isSameFile(convergedRekey.secretTargetPath, convergedRekey.secretStagePath));
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
  void memberRecoveryAcceptsOnlyTheExpectedWinnerOfARaceForTheProtectedBook() throws Exception {
    SqliteProtectedBookPairPublicationRecord matched = retainedRecord("member-book-race-match");
    boolean[] matchedCollisionCreated = {false};
    SqlitePairPublicationMemberReconciler matchedReconciler =
        reconciler(
            (ignoredStep, ignoredParent) -> {
              if (!matchedCollisionCreated[0]) {
                Files.createLink(matched.bookTargetPath, matched.bookStagePath);
                matchedCollisionCreated[0] = true;
              }
            });

    try (SqlitePublicationCapabilityWitness.Set witnesses = witnessesFor(matched)) {
      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.DURABLE,
          matchedReconciler.reconcileBook(
              matched,
              SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
              witnesses));
    }
    assertTrue(Files.isSameFile(matched.bookTargetPath, matched.bookStagePath));

    SqliteProtectedBookPairPublicationRecord foreign = retainedRecord("member-book-race-foreign");
    boolean[] foreignCollisionCreated = {false};
    SqlitePairPublicationMemberReconciler foreignReconciler =
        reconciler(
            (ignoredStep, ignoredParent) -> {
              if (!foreignCollisionCreated[0]) {
                Files.writeString(foreign.bookTargetPath, "foreign protected book");
                foreignCollisionCreated[0] = true;
              }
            });

    try (SqlitePublicationCapabilityWitness.Set witnesses = witnessesFor(foreign)) {
      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.OUTCOME_UNCERTAIN,
          foreignReconciler.reconcileBook(
              foreign,
              SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
              witnesses));
    }
    assertFalse(Files.isSameFile(foreign.bookTargetPath, foreign.bookStagePath));
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
  void rekeyRecoveryRefusesBookReplacementWhenTheSelectedBookChangesAfterEvidenceForcing()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord record =
        rekeyRecord("member-rekey-book-changed-during-replacement");
    boolean[] changed = {false};
    SqlitePairPublicationMemberReconciler reconciler =
        reconciler(
            (ignoredStep, ignoredParent) -> {
              if (!changed[0]) {
                Files.writeString(record.bookTargetPath, "changed live book after recovery proof");
                changed[0] = true;
              }
            });

    try (SqlitePublicationCapabilityWitness.Set witnesses =
        SqlitePairPublicationRecoveryCapabilities.acquire(
            record,
            SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
            SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
            ProtectedBookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET)) {
      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.OUTCOME_UNCERTAIN,
          reconciler.reconcileBook(
              record,
              SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
              witnesses));
    }

    assertFalse(Files.isSameFile(record.bookTargetPath, record.bookStagePath));
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
  void rekeyRecoveryFailsClosedWhenAtomicReplacementCollidesOrItsResultIsImmediatelyLost()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord collision = rekeyRecord("member-rekey-move-collision");
    SqlitePairPublicationMemberReconciler collisionReconciler =
        reconciler(
            (ignoredStep, ignoredParent) -> {},
            (ignoredBridge, target) -> {
              throw new java.nio.file.FileAlreadyExistsException(target.toString());
            });

    try (SqlitePublicationCapabilityWitness.Set witnesses =
        SqlitePairPublicationRecoveryCapabilities.acquire(
            collision,
            SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
            SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
            ProtectedBookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET)) {
      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.OUTCOME_UNCERTAIN,
          collisionReconciler.reconcileBook(
              collision,
              SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
              witnesses));
    }

    SqliteProtectedBookPairPublicationRecord removed = rekeyRecord("member-rekey-move-removed");
    SqlitePairPublicationMemberReconciler removedReconciler =
        reconciler(
            (ignoredStep, ignoredParent) -> {},
            (replacementBridge, target) -> {
              SqliteProtectedBookPublicationSupport.moveReplacing(replacementBridge, target);
              Files.delete(target);
            });

    try (SqlitePublicationCapabilityWitness.Set witnesses =
        SqlitePairPublicationRecoveryCapabilities.acquire(
            removed,
            SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
            SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
            ProtectedBookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET)) {
      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.OUTCOME_UNCERTAIN,
          removedReconciler.reconcileBook(
              removed,
              SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
              witnesses));
    }

    assertFalse(Files.exists(removed.bookTargetPath));
    assertTrue(Files.exists(removed.bookStagePath));
  }

  @Test
  void memberRecoveryRejectsChangedStagesAndFailsClosedOnPostBoundaryReplacementIo()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord occupiedSecret =
        retainedRecord("member-secret-occupied");
    SqlitePairPublicationMemberReconciler reconciler =
        reconciler((ignoredStep, ignoredParent) -> {});
    try (SqlitePublicationCapabilityWitness.Set witnesses = witnessesFor(occupiedSecret)) {
      Files.writeString(occupiedSecret.secretTargetPath, "foreign generated secret");
      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.OUTCOME_UNCERTAIN,
          reconciler.reconcileSecret(
              occupiedSecret,
              SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
              witnesses));
    }

    SqliteProtectedBookPairPublicationRecord changedSecret = retainedRecord("member-secret-stage");
    try (SqlitePublicationCapabilityWitness.Set witnesses = witnessesFor(changedSecret)) {
      Files.writeString(changedSecret.secretStagePath, "changed retained secret");
      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.OUTCOME_UNCERTAIN,
          reconciler.reconcileSecret(
              changedSecret,
              SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
              witnesses));
    }

    SqliteProtectedBookPairPublicationRecord changedRekeyStage = rekeyRecord("member-rekey-stage");
    boolean[] stageChanged = {false};
    SqlitePairPublicationMemberReconciler stageChangedReconciler =
        reconciler(
            (ignoredStep, ignoredParent) -> {
              if (!stageChanged[0]) {
                Files.writeString(changedRekeyStage.bookStagePath, "changed rekeyed book");
                stageChanged[0] = true;
              }
            });
    try (SqlitePublicationCapabilityWitness.Set witnesses =
        SqlitePairPublicationRecoveryCapabilities.acquire(
            changedRekeyStage,
            SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
            SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
            ProtectedBookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET)) {
      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.OUTCOME_UNCERTAIN,
          stageChangedReconciler.reconcileBook(
              changedRekeyStage,
              SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
              witnesses));
    }

    SqliteProtectedBookPairPublicationRecord replacementIo = rekeyRecord("member-rekey-move-io");
    SqlitePairPublicationMemberReconciler replacementIoReconciler =
        reconciler(
            (ignoredStep, ignoredParent) -> {},
            (ignoredBridge, ignoredTarget) -> {
              throw new IOException("injected atomic replacement I/O failure");
            });
    try (SqlitePublicationCapabilityWitness.Set witnesses =
        SqlitePairPublicationRecoveryCapabilities.acquire(
            replacementIo,
            SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
            SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
            ProtectedBookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET)) {
      assertEquals(
          SqliteProtectedBookPairPublicationRecoverySupport.MemberReconciliation.OUTCOME_UNCERTAIN,
          replacementIoReconciler.reconcileBook(
              replacementIo,
              SqliteProtectedBookPairPublicationRecoverySupport.MemberRecoveryPlan.PUBLISH_ELIGIBLE,
              witnesses));
    }
  }
}
