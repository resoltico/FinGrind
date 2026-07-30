package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Exercises workflow recovery outcomes after an owned protected-book pair is admitted. */
class SqliteProtectedBookPairPublicationWorkflowRecoveryTest
    extends SqliteProtectedBookPairPublicationRecoveryTestSupport {
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
  void recoveryWorkflowReleasesCapabilitiesWhenPostreconciliationVerificationFails()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord record = retainedRecord("workflow-verifier-fault");
    AtomicInteger verificationCalls = new AtomicInteger();
    AssertionError injected = new AssertionError("simulated verifier fault");

    AssertionError observed =
        assertThrows(
            AssertionError.class,
            () ->
                recoveryWorkflow(
                        (ignoredBook, ignoredSecret, ignoredBinding) ->
                            verificationCalls.getAndIncrement() == 0
                                || throwVerifierFault(injected))
                    .recover(
                        record,
                        RestoredBookTargetPolicy.REQUIRE_ABSENT,
                        backupRequest(record.bookTargetPath),
                        ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                        ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                        false));

    assertEquals(injected, observed);
    assertEquals(2, verificationCalls.get());
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
  void workflowRejectsIncompleteEvidenceWhenTheRequestedTargetPolicyChanged() throws Exception {
    SqliteProtectedBookPairPublicationRecord record =
        retainedRecord("workflow-incomplete-policy-mismatch");

    ProtectedBookMaintenanceRejectionException refusal =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                recoveryWorkflow(true)
                    .recover(
                        record,
                        RestoredBookTargetPolicy.REPLACE_SELECTED,
                        backupRequest(record.bookTargetPath),
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
  void workflowBlocksIncompleteEvidenceWhenItsDurabilityRepairCannotBeConfirmed() throws Exception {
    SqliteProtectedBookPairPublicationRecord record =
        retainedRecord("workflow-evidence-repair-force-failure");
    Files.delete(
        record.evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.INTENT).getFirst());

    SqliteManagedTargetLeasesHeld leases =
        assertInstanceOf(
            SqliteManagedTargetLeasesHeld.class,
            SqliteBookMaintenanceLease.acquireManagedTargetPair(
                record.bookTargetPath, record.secretTargetPath));
    try (SqliteHeldLease ignoredBookLease = leases.bookTargetLease();
        SqliteHeldLease ignoredSecretLease = leases.secretTargetLease()) {
      assertInstanceOf(
          SqlitePairPublicationReconciliationEvidenceBlocked.class,
          recoveryWorkflow(
                  true,
                  (ignoredStep, ignoredParent) -> {
                    throw new IOException("injected incomplete-evidence durability failure");
                  })
              .recover(
                  record,
                  RestoredBookTargetPolicy.REQUIRE_ABSENT,
                  backupRequest(record.bookTargetPath),
                  ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                  ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                  true));
    }

    assertFalse(Files.exists(record.bookTargetPath));
    assertFalse(Files.exists(record.secretTargetPath));
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

    SqliteProtectedBookPairPublicationRecord changedSecret =
        retainedRecord("completion-secret-change");
    Files.createLink(changedSecret.bookTargetPath, changedSecret.bookStagePath);
    Files.createLink(changedSecret.secretTargetPath, changedSecret.secretStagePath);
    Files.delete(changedSecret.secretTargetPath);
    Files.writeString(changedSecret.secretTargetPath, "changed final secret");

    IOException changedFinalSecret =
        assertThrows(
            IOException.class,
            () ->
                SqliteProtectedBookPairPublicationEvidenceLifecycle.confirmCompletedPublication(
                    changedSecret, (ignoredStep, ignoredParent) -> {}));
    assertTrue(
        Objects.requireNonNull(changedFinalSecret.getMessage(), "changed-secret message")
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
  void workflowDistinguishesAdditionalPartialVisibilityAndRecoveryProofBoundaries()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord onlySecretVisible =
        retainedRecord("workflow-only-secret-visible");
    Files.createLink(onlySecretVisible.secretTargetPath, onlySecretVisible.secretStagePath);
    assertInstanceOf(
        SqlitePairPublicationReconciliationRecovered.class,
        recoveryWorkflow(true)
            .recover(
                onlySecretVisible,
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                backupRequest(onlySecretVisible.bookTargetPath),
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                false));

    SqliteProtectedBookPairPublicationRecord prepublicationOnlySecret =
        retainedRecord("workflow-prepublication-only-secret");
    SqliteProtectedBookPairPublicationEvidenceLifecycle.retainPrepublication(
        prepublicationOnlySecret, (ignoredStep, ignoredParent) -> {});
    Files.createLink(
        prepublicationOnlySecret.secretTargetPath, prepublicationOnlySecret.secretStagePath);
    assertInstanceOf(
        SqlitePairPublicationReconciliationEvidenceBlocked.class,
        recoveryWorkflow(true)
            .recover(
                prepublicationOnlySecret,
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                backupRequest(prepublicationOnlySecret.bookTargetPath),
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                false));

    SqliteProtectedBookPairPublicationRecord proofFailure =
        retainedRecord("workflow-proof-failure");
    assertInstanceOf(
        SqlitePairPublicationReconciliationCompletionUncertain.class,
        recoveryWorkflow(false)
            .recover(
                proofFailure,
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                backupRequest(proofFailure.bookTargetPath),
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                false));

    SqliteProtectedBookPairPublicationRecord secretBlocked =
        retainedRecord("workflow-secret-blocked");
    Files.writeString(secretBlocked.secretTargetPath, "foreign generated secret");
    assertInstanceOf(
        SqlitePairPublicationReconciliationCompletionUncertain.class,
        recoveryWorkflow(true)
            .recover(
                secretBlocked,
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                backupRequest(secretBlocked.bookTargetPath),
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                false));
  }

  @Test
  void workflowDistinguishesBookOnlyVisibilityAndFullyVisibleProofFailure() throws Exception {
    SqliteProtectedBookPairPublicationRecord bookOnlyVisible =
        retainedRecord("workflow-only-book-visible");
    Files.createLink(bookOnlyVisible.bookTargetPath, bookOnlyVisible.bookStagePath);

    assertInstanceOf(
        SqlitePairPublicationReconciliationRecovered.class,
        recoveryWorkflow(true)
            .recover(
                bookOnlyVisible,
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                backupRequest(bookOnlyVisible.bookTargetPath),
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                false));

    SqliteProtectedBookPairPublicationRecord fullyVisible =
        retainedRecord("workflow-fully-visible-proof-failure");
    Files.createLink(fullyVisible.bookTargetPath, fullyVisible.bookStagePath);
    Files.createLink(fullyVisible.secretTargetPath, fullyVisible.secretStagePath);

    assertInstanceOf(
        SqlitePairPublicationReconciliationCompletionUncertain.class,
        recoveryWorkflow(false)
            .recover(
                fullyVisible,
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                backupRequest(fullyVisible.bookTargetPath),
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                false));
  }

  @Test
  void workflowLeavesIncompleteEvidenceBlockedWhenTheStagesAreNoLongerOwned() throws Exception {
    SqliteProtectedBookPairPublicationRecord unowned =
        pairRecord("workflow-incomplete-unowned", false);

    assertInstanceOf(
        SqlitePairPublicationReconciliationEvidenceBlocked.class,
        recoveryWorkflow(true)
            .recover(
                unowned,
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                backupRequest(unowned.bookTargetPath),
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                true));
  }

  @Test
  void workflowDoesNotTreatCompletedNonBackupEvidenceAsAnExistingBackup() throws Exception {
    SqliteProtectedBookPairPublicationRecord restoreRecord =
        restoreRecord("workflow-completed-restore");
    Files.createLink(restoreRecord.bookTargetPath, restoreRecord.bookStagePath);
    Files.createLink(restoreRecord.secretTargetPath, restoreRecord.secretStagePath);
    SqliteProtectedBookPairPublicationEvidenceLifecycle.confirmCompletedPublication(
        restoreRecord, (ignoredStep, ignoredParent) -> {});

    assertEquals(
        SqlitePairPublicationReconciliationAbsent.INSTANCE,
        recoveryWorkflow(true)
            .recover(
                restoreRecord,
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                backupRequest(restoreRecord.bookTargetPath),
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                false));
  }

  @Test
  void workflowRetainsAnUncertainOutcomeWhenThePostPublicationProofChanges() throws Exception {
    SqliteProtectedBookPairPublicationRecord record =
        retainedRecord("workflow-post-publication-proof");
    AtomicInteger verificationCalls = new AtomicInteger();

    SqlitePairPublicationReconciliationCompletionUncertain uncertain =
        assertInstanceOf(
            SqlitePairPublicationReconciliationCompletionUncertain.class,
            recoveryWorkflow(
                    (ignoredBook, ignoredSecret, ignoredBinding) ->
                        verificationCalls.incrementAndGet() == 1)
                .recover(
                    record,
                    RestoredBookTargetPolicy.REQUIRE_ABSENT,
                    backupRequest(record.bookTargetPath),
                    ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                    false));

    assertEquals(2, verificationCalls.get());
    assertEquals(
        ProtectedBookPairPublicationMemberState.PUBLISHED_DURABLE, uncertain.bookArtifactState());
    assertEquals(
        ProtectedBookPairPublicationMemberState.PUBLISHED_DURABLE, uncertain.secretArtifactState());
  }

  @Test
  void workflowChecksBothVisibleMembersBeforeRejectingAMismatchedRequest() throws Exception {
    SqliteProtectedBookPairPublicationRecord record = retainedRecord("workflow-mismatch-partial");
    Files.createLink(record.bookTargetPath, record.bookStagePath);
    ProtectedBookPairPublicationRecoveryRequest.Backup mismatchedRequest =
        new ProtectedBookPairPublicationRecoveryRequest.Backup(
            record.bookTargetPath.resolveSibling("other-source.sqlite"), new UUID(0L, 24L));

    ProtectedBookMaintenanceRejectionException rejection =
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

    assertInstanceOf(
        ProtectedBookMaintenanceRejection.RecoveryPending.class, rejection.rejection());
  }
}
