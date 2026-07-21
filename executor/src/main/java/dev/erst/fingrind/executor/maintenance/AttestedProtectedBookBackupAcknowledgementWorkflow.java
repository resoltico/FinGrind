package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgementAdmission;
import dev.erst.fingrind.core.attestation.AttestationBackupArtifact;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.core.attestation.AttestationLifecycleMutationProjection;
import dev.erst.fingrind.core.attestation.AttestationSigningSession;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.core.attestation.AttestationVerifier;
import dev.erst.fingrind.executor.spi.AttestedProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.PreparedPairPublication;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Owns durable backup publication and its exactly-once attestation acknowledgement. */
final class AttestedProtectedBookBackupAcknowledgementWorkflow {
  private static final String BACKUP_CREATED_OPERATION =
      AttestationOperationKind.BACKUP_CREATED.wireToken();

  private final Clock clock;
  private final AttestedProtectedBookMaintenanceStore store;

  AttestedProtectedBookBackupAcknowledgementWorkflow(
      Clock clock, AttestedProtectedBookMaintenanceStore store) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.store = Objects.requireNonNull(store, "store");
  }

  MaintenanceDecision<ProtectedBookBackupOutcome> stagePublishAndAcknowledgeBackup(
      ProtectedBookMaintenanceStore.VerifiedBook liveBook,
      Path bookPath,
      Path backupPath,
      Path backupKeyPath,
      PreparedPairPublication publication,
      UUID backupId,
      AttestationSigningSession signingSession) {
    return store
        .stageBackupPair(liveBook, publication)
        .fold(
            staged -> {
              try (StagedBackupPair stagedBackup = staged) {
                return stagedBackup
                    .verifyInitializedBackup()
                    .fold(
                        verification -> {
                          if (verification
                              instanceof
                              ProtectedBookMaintenanceStore.VerificationFailure failure) {
                            return AttestedProtectedBookMaintenanceDecisions.rejectedBackup(
                                AttestedProtectedBookMaintenanceDecisions.verificationFailed(
                                    ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE, failure));
                          }
                          try (ProtectedBookMaintenanceStore.VerifiedBook snapshotBook =
                              (ProtectedBookMaintenanceStore.VerifiedBook) verification) {
                            List<AttestationEvidence> evidence =
                                store.loadAttestationEvidence(snapshotBook);
                            AttestationVerification source =
                                AttestationVerifier.verifyBook(evidence);
                            byte[] artifact =
                                signingSession.createBackupArtifact(
                                    stagedBackup.snapshot(),
                                    source.bookId(),
                                    Objects.requireNonNull(backupId, "backupId"),
                                    source.headOrder(),
                                    source.operationHead());
                            var verifiedArtifact =
                                AttestationBackupArtifact.verify(artifact, ignored -> evidence);
                            AttestationBackupAcknowledgement acknowledgement =
                                new AttestationBackupAcknowledgement(
                                    verifiedArtifact.backupId(),
                                    verifiedArtifact.artifactDigest(),
                                    verifiedArtifact.sourceOrder(),
                                    verifiedArtifact.sourceOperationHead());
                            stagedBackup.sealArtifact(artifact);
                            stagedBackup.commit();
                            return acknowledgeBackup(
                                liveBook,
                                bookPath,
                                backupPath,
                                backupKeyPath,
                                acknowledgement,
                                signingSession,
                                false);
                          }
                        },
                        ignored ->
                            AttestedProtectedBookMaintenanceDecisions.failure(
                                backupPath,
                                "backupFilePath",
                                "Failed to verify staged backup snapshot."));
              }
            },
            failure ->
                AttestedProtectedBookMaintenanceDecisions.failure(
                    backupPath, "backupFilePath", failure.message()));
  }

  MaintenanceDecision<ProtectedBookBackupOutcome> acknowledgeBackup(
      ProtectedBookMaintenanceStore.VerifiedBook liveBook,
      Path bookPath,
      Path backupPath,
      Path backupKeyPath,
      AttestationBackupAcknowledgement acknowledgement,
      AttestationSigningSession signingSession,
      boolean acknowledgementResumed) {
    AttestationBackupAcknowledgementAdmission admission =
        AttestationBackupAcknowledgementAdmission.evaluate(
            store.loadAttestationEvidence(liveBook), acknowledgement);
    return switch (admission) {
      case CONFLICT ->
          AttestedProtectedBookMaintenanceDecisions.rejectedBackup(
              new ProtectedBookMaintenanceRejection.BackupAcknowledgementConflict(
                  acknowledgement.backupId()));
      case IDENTICAL_REPLAY ->
          MaintenanceDecision.accepted(
              new ProtectedBookBackupOutcome.BackedUp(
                  bookPath,
                  backupPath,
                  backupKeyPath,
                  acknowledgement.backupId(),
                  acknowledgementResumed));
      case APPEND -> {
        try {
          store.appendAttestedOperation(
              liveBook,
              BACKUP_CREATED_OPERATION,
              clock.instant(),
              AttestationLifecycleMutationProjection.backupBook(
                  BACKUP_CREATED_OPERATION, acknowledgement),
              signingSession,
              acknowledgement);
          yield MaintenanceDecision.accepted(
              new ProtectedBookBackupOutcome.BackedUp(
                  bookPath,
                  backupPath,
                  backupKeyPath,
                  acknowledgement.backupId(),
                  acknowledgementResumed));
        } catch (BackupAcknowledgementConflictException exception) {
          yield AttestedProtectedBookMaintenanceDecisions.rejectedBackup(
              new ProtectedBookMaintenanceRejection.BackupAcknowledgementConflict(
                  acknowledgement.backupId()));
        } catch (RuntimeException exception) {
          yield MaintenanceDecision.accepted(
              new ProtectedBookBackupOutcome.AcknowledgementPending(
                  bookPath, backupPath, backupKeyPath, acknowledgement.backupId()));
        }
      }
    };
  }

  static boolean artifactSourceIsLive(
      dev.erst.fingrind.core.attestation.AttestationBackupArtifactVerification artifact,
      List<AttestationEvidence> liveEvidence) {
    List<AttestationEvidence> checkedEvidence = List.copyOf(liveEvidence);
    int sourceIndex;
    try {
      sourceIndex = artifact.sourceOrder().intValueExact();
    } catch (ArithmeticException exception) {
      return false;
    }
    if (sourceIndex < 0 || sourceIndex >= checkedEvidence.size()) {
      return false;
    }
    AttestationVerification sourceVerification =
        AttestationVerifier.verifyBook(checkedEvidence.subList(0, sourceIndex + 1));
    return sourceVerification.bookId().equals(artifact.bookId())
        && sourceVerification.headOrder().equals(artifact.sourceOrder())
        && Arrays.equals(sourceVerification.operationHead(), artifact.sourceOperationHead());
  }
}
