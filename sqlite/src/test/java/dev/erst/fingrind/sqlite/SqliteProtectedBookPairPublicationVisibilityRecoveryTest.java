package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRecoveryRecordState;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationAdmission;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationFailureOutcome;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Exercises visibility, collision, and bounded evidence-repair recovery outcomes. */
class SqliteProtectedBookPairPublicationVisibilityRecoveryTest
    extends SqliteProtectedBookPairPublicationRecoveryTestSupport {
  @Test
  void admissionMapperPreservesEveryRecoveredOutcomeAndTheExactPathRole() throws Exception {
    SqliteProtectedBookPairPublicationRecord record = retainedRecord("admission-mapper");
    SqlitePairPublicationReconciliationCompletionUncertain uncertain =
        SqliteProtectedBookPairPublicationRecovery.completionUncertain(
            record,
            ProtectedBookPairPublicationMemberState.PUBLISHED_DURABLE,
            ProtectedBookPairPublicationMemberState.PUBLISHED_DURABLE);
    var retention =
        Objects.requireNonNull(uncertain.pairPublicationRetention(), "retained publication stages");

    assertInstanceOf(
        ProtectedBookPairPublicationAdmission.Recovered.class,
        SqlitePairPublicationAdmissionMapper.fromRecoveredReconciliation(
            new SqlitePairPublicationReconciliationRecovered(record.binding, retention)));
    assertInstanceOf(
        ProtectedBookPairPublicationAdmission.ExistingCompleteBackup.class,
        SqlitePairPublicationAdmissionMapper.fromRecoveredReconciliation(
            new SqlitePairPublicationReconciliationExistingCompleteBackup(
                record.bookTargetPath, record.secretTargetPath)));
    assertInstanceOf(
        ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired.class,
        SqlitePairPublicationAdmissionMapper.fromRecoveredReconciliation(
            SqliteProtectedBookPairPublicationRecovery.prepublicationRecoveryRequired(
                record, ProtectedBookPairPublicationRecoveryRecordState.DURABLY_RETAINED)));
    assertInstanceOf(
        ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked.class,
        SqlitePairPublicationAdmissionMapper.fromRecoveredReconciliation(
            SqliteProtectedBookPairPublicationRecovery.evidenceBlocked(record)));
    assertInstanceOf(
        ProtectedBookPairPublicationFailureOutcome.CompletionUncertain.class,
        SqlitePairPublicationAdmissionMapper.fromRecoveredReconciliation(uncertain));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqlitePairPublicationAdmissionMapper.fromRecoveredReconciliation(
                SqlitePairPublicationReconciliationAbsent.INSTANCE));

    SqliteCallerPathContractException secretFailure =
        new SqliteCallerPathContractException(
            record.secretTargetPath,
            SqliteCallerPathFailure.PARENT_PATH_COLLISION,
            "secret target parent changed");
    SqliteCallerPathContractException bookFailure =
        new SqliteCallerPathContractException(
            record.bookTargetPath,
            SqliteCallerPathFailure.PARENT_PATH_COLLISION,
            "book target parent changed");
    assertEquals(
        ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
        SqlitePairPublicationAdmissionMapper.roleForRecoveryPathFailure(
            secretFailure,
            record.secretTargetPath,
            ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
            ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));
    assertEquals(
        ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
        SqlitePairPublicationAdmissionMapper.roleForRecoveryPathFailure(
            bookFailure,
            record.secretTargetPath,
            ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
            ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));
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

    SqliteProtectedBookPairPublicationRecord bookForceFailure =
        retainedRecord("workflow-visible-book-force-failure");
    Files.createLink(bookForceFailure.bookTargetPath, bookForceFailure.bookStagePath);
    Files.createLink(bookForceFailure.secretTargetPath, bookForceFailure.secretStagePath);

    SqlitePairPublicationReconciliationCompletionUncertain bookUncertain =
        assertInstanceOf(
            SqlitePairPublicationReconciliationCompletionUncertain.class,
            recoveryWorkflow(
                    true,
                    (step, ignoredParent) -> {
                      if (step
                          == SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
                              .BOOK_PUBLICATION) {
                        throw new IOException("injected visible-book durability failure");
                      }
                    })
                .recover(
                    bookForceFailure,
                    RestoredBookTargetPolicy.REQUIRE_ABSENT,
                    backupRequest(bookForceFailure.bookTargetPath),
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
  void evidenceReservationStopsAfterItsBoundedClaimCollisionRetries() {
    AtomicInteger claimCollisions = new AtomicInteger();

    IOException failure =
        assertThrows(
            IOException.class,
            () ->
                reserveEvidence(
                    "reservation-claim-collision",
                    false,
                    (evidencePath, temporaryPath) -> {
                      claimCollisions.incrementAndGet();
                      throw new java.nio.file.FileAlreadyExistsException(evidencePath.toString());
                    }));

    assertEquals(8, claimCollisions.get());
    assertTrue(
        Objects.requireNonNull(failure.getMessage(), "claim collision message")
            .contains("Unable to reserve durable protected-book pair recovery evidence"));
  }

  @Test
  void evidenceReservationFailsClosedWhenItsIntentCollidesAfterTheClaimIsDurable() {
    SqliteProtectedBookPairPublicationRecord.RecoveryRecordDurabilityUnconfirmedException failure =
        assertThrows(
            SqliteProtectedBookPairPublicationRecord.RecoveryRecordDurabilityUnconfirmedException
                .class,
            () ->
                reserveEvidence(
                    "reservation-intent-collision",
                    false,
                    (evidencePath, temporaryPath) -> {
                      if (evidencePath.getFileName().toString().contains("pair-intent-")) {
                        throw new java.nio.file.FileAlreadyExistsException(evidencePath.toString());
                      }
                      Files.createLink(evidencePath, temporaryPath);
                    }));

    assertTrue(
        Objects.requireNonNull(
                Objects.requireNonNull(failure.getCause(), "intent collision cause").getMessage(),
                "intent collision message")
            .contains("claim was durable but recovery intent collided"));
  }

  @Test
  void evidenceReservationFailsClosedWhenOnlyOneMirroredCopyWasPromoted() {
    AtomicInteger promotedCopies = new AtomicInteger();

    SqliteProtectedBookPairPublicationRecord.RecoveryRecordDurabilityUnconfirmedException failure =
        assertThrows(
            SqliteProtectedBookPairPublicationRecord.RecoveryRecordDurabilityUnconfirmedException
                .class,
            () ->
                reserveEvidence(
                    "reservation-partial-collision",
                    true,
                    (evidencePath, temporaryPath) -> {
                      if (promotedCopies.getAndIncrement() == 1) {
                        throw new java.nio.file.FileAlreadyExistsException(evidencePath.toString());
                      }
                      Files.createLink(evidencePath, temporaryPath);
                    }));

    assertInstanceOf(java.nio.file.FileAlreadyExistsException.class, failure.getCause());
    assertEquals(2, promotedCopies.get());
  }

  @Test
  void evidenceReservationPreservesAnIoFailureAfterOneMirroredCopyWasPromoted() {
    AtomicInteger promotedCopies = new AtomicInteger();

    SqliteProtectedBookPairPublicationRecord.RecoveryRecordDurabilityUnconfirmedException failure =
        assertThrows(
            SqliteProtectedBookPairPublicationRecord.RecoveryRecordDurabilityUnconfirmedException
                .class,
            () ->
                reserveEvidence(
                    "reservation-partial-io-failure",
                    true,
                    (evidencePath, temporaryPath) -> {
                      if (promotedCopies.getAndIncrement() == 1) {
                        throw new IOException("injected mirrored-copy I/O failure");
                      }
                      Files.createLink(evidencePath, temporaryPath);
                    }));

    assertEquals(
        "injected mirrored-copy I/O failure",
        Objects.requireNonNull(
            Objects.requireNonNull(failure.getCause(), "partial I/O failure cause").getMessage(),
            "partial I/O failure message"));
    assertEquals(2, promotedCopies.get());
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
}
