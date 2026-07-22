package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.core.attestation.AttestationAdmissionRejectedException;
import dev.erst.fingrind.core.attestation.AttestationAuthorizationException;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgementAdmission;
import dev.erst.fingrind.core.attestation.AttestationBackupArtifact;
import dev.erst.fingrind.core.attestation.AttestationBackupArtifactVerification;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationLifecycleMutationProjection;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.core.attestation.AttestationSigningSession;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.core.attestation.AttestationVerifier;
import dev.erst.fingrind.executor.spi.AttestedProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.PreparedPairPublication;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Owns durable backup publication and its exactly-once attestation acknowledgement. */
final class AttestedProtectedBookBackupAcknowledgementWorkflow {
  private static final AttestationOperationKind BACKUP_CREATED_OPERATION =
      AttestationOperationKind.BACKUP_CREATED;

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
                            AttestationBackupArtifactVerification verifiedArtifact =
                                verifyNewBackupArtifact(artifact, evidence);
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

  /**
   * Verifies the manifest signed during this invocation and preserves an authorization refusal as a
   * live admission result rather than an operational backup failure.
   */
  private static AttestationBackupArtifactVerification verifyNewBackupArtifact(
      byte[] artifact, List<AttestationEvidence> sourceEvidence) {
    try {
      return AttestationBackupArtifact.verify(artifact, ignored -> sourceEvidence);
    } catch (AttestationAuthorizationException exception) {
      throw AttestationAdmissionRejectedException.from(exception);
    }
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
                  BACKUP_CREATED_OPERATION.wireToken(), acknowledgement),
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
        } catch (AttestationAdmissionRejectedException exception) {
          yield MaintenanceDecision.accepted(
              new ProtectedBookBackupOutcome.AcknowledgementAuthorizationRejected(
                  bookPath,
                  backupPath,
                  backupKeyPath,
                  acknowledgement.backupId(),
                  exception.failure()));
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
    if (!sourceOrderIsAddressable(artifact.sourceOrder(), checkedEvidence.size())) {
      return false;
    }
    int sourceIndex = artifact.sourceOrder().intValue();
    AttestationVerification sourceVerification =
        AttestationVerifier.verifyBook(checkedEvidence.subList(0, sourceIndex + 1));
    return sourceVerificationMatchesArtifact(
        sourceVerification,
        artifact.bookId(),
        artifact.sourceOrder(),
        artifact.sourceOperationHead());
  }

  /** Returns whether an authenticated unsigned order can address one entry in the live chain. */
  static boolean sourceOrderIsAddressable(BigInteger sourceOrder, int evidenceSize) {
    return Objects.requireNonNull(sourceOrder, "sourceOrder")
            .compareTo(BigInteger.valueOf(evidenceSize))
        < 0;
  }

  /** Compares the reconstructed source state with the immutable backup-manifest binding. */
  static boolean sourceVerificationMatchesArtifact(
      AttestationVerification sourceVerification,
      UUID bookId,
      BigInteger sourceOrder,
      byte[] sourceOperationHead) {
    AttestationVerification checkedVerification =
        Objects.requireNonNull(sourceVerification, "sourceVerification");
    return checkedVerification.bookId().equals(Objects.requireNonNull(bookId, "bookId"))
        && checkedVerification
            .headOrder()
            .equals(Objects.requireNonNull(sourceOrder, "sourceOrder"))
        && Arrays.equals(
            checkedVerification.operationHead(),
            Objects.requireNonNull(sourceOperationHead, "sourceOperationHead"));
  }
}
