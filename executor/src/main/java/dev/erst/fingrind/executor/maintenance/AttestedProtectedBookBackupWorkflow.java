package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.core.attestation.AttestationAdmissionRejectedException;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationSigningSession;
import dev.erst.fingrind.executor.spi.AttestedProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.PreparedPairPublication;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Owns backup destination admission and protected-book snapshot staging. */
final class AttestedProtectedBookBackupWorkflow {
  private final AttestedProtectedBookMaintenanceStore store;
  private final AttestedProtectedBookBackupAcknowledgementWorkflow acknowledgementWorkflow;

  AttestedProtectedBookBackupWorkflow(Clock clock, AttestedProtectedBookMaintenanceStore store) {
    this.store = Objects.requireNonNull(store, "store");
    acknowledgementWorkflow =
        new AttestedProtectedBookBackupAcknowledgementWorkflow(clock, this.store);
  }

  MaintenanceDecision<ProtectedBookBackupOutcome> backupBook(
      ProtectedBookAccess bookAccess,
      Path backupFilePath,
      Path backupBookKeyFilePath,
      UUID backupId,
      AttestationSigningSession signingSession) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(signingSession, "signingSession");
    Path bookPath = store.normalize(bookAccess.bookFilePath(), "bookFilePath");
    Path backupPath = store.normalize(backupFilePath, "backupFilePath");
    Path backupKeyPath = store.normalize(backupBookKeyFilePath, "backupBookKeyFilePath");
    UUID checkedBackupId = Objects.requireNonNull(backupId, "backupId");
    try {
      store.recoverInterruptedBackupPublication(backupPath, backupKeyPath);
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return AttestedProtectedBookMaintenanceDecisions.rejectedBackup(exception.rejection());
    } catch (RuntimeException exception) {
      return AttestedProtectedBookMaintenanceDecisions.failure(
          backupPath, "backupFilePath", "Failed to recover an interrupted backup publication.");
    }
    return switch (store.backupArtifactPairState(backupPath, backupKeyPath)) {
      case ABSENT ->
          createBackupArtifact(
              bookAccess, bookPath, backupPath, backupKeyPath, checkedBackupId, signingSession);
      case ARTIFACT_ONLY ->
          AttestedProtectedBookMaintenanceDecisions.rejectedBackup(
              new ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists(backupPath));
      case KEY_ONLY ->
          AttestedProtectedBookMaintenanceDecisions.rejectedBackup(
              new ProtectedBookMaintenanceRejection.SecretTargetOccupied(backupKeyPath));
      case COMPLETE ->
          resumeBackupAcknowledgement(
              bookAccess, bookPath, backupPath, backupKeyPath, checkedBackupId, signingSession);
    };
  }

  private MaintenanceDecision<ProtectedBookBackupOutcome> createBackupArtifact(
      ProtectedBookAccess bookAccess,
      Path bookPath,
      Path backupPath,
      Path backupKeyPath,
      UUID backupId,
      AttestationSigningSession signingSession) {
    PreparedPairPublication publication;
    try {
      publication =
          store.preparePairPublication(
              backupKeyPath,
              backupPath,
              RestoredBookTargetPolicy.REQUIRE_ABSENT,
              ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
              ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET);
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return AttestedProtectedBookMaintenanceDecisions.rejectedBackup(exception.rejection());
    } catch (RuntimeException exception) {
      return AttestedProtectedBookMaintenanceDecisions.failure(
          backupPath, "backupFilePath", "Failed to prepare backup artifact publication.");
    }
    try {
      return MaintenanceResourceScope.closeAfter(
          publication::close,
          () -> {
            List<Path> blocking = store.blockingArtifactsForBook(bookPath);
            if (!blocking.isEmpty()) {
              return AttestedProtectedBookMaintenanceDecisions.rejectedBackup(
                  new ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts(
                      bookPath, blocking));
            }
            ProtectedBookMaintenanceStore.LeaseAcquisition lease =
                store.acquireManagedArtifactLease(
                    bookPath, ProtectedBookMaintenanceArtifactRole.LIVE_BOOK);
            if (lease instanceof ProtectedBookMaintenanceStore.LeaseBusy busy) {
              return AttestedProtectedBookMaintenanceDecisions.rejectedBackup(
                  new ProtectedBookMaintenanceRejection.ArtifactBusy(
                      ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, busy.artifactPath()));
            }
            ProtectedBookMaintenanceStore.HeldLease heldLease =
                (ProtectedBookMaintenanceStore.HeldLease) lease;
            return MaintenanceResourceScope.closeAfter(
                heldLease::close,
                () -> {
                  ProtectedBookMaintenanceStore.VerifiedBook liveBook =
                      AttestedProtectedBookMaintenanceDecisions.requireVerifiedBook(
                          store, bookAccess);
                  return MaintenanceResourceScope.closeAfter(
                      liveBook::close,
                      () ->
                          acknowledgementWorkflow.stagePublishAndAcknowledgeBackup(
                              liveBook,
                              bookPath,
                              backupPath,
                              backupKeyPath,
                              publication,
                              backupId,
                              signingSession));
                });
          });
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return AttestedProtectedBookMaintenanceDecisions.rejectedBackup(exception.rejection());
    } catch (AttestationAdmissionRejectedException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      return AttestedProtectedBookMaintenanceDecisions.failure(
          backupPath, "backupFilePath", "Failed to create the attested backup artifact.");
    }
  }

  private MaintenanceDecision<ProtectedBookBackupOutcome> resumeBackupAcknowledgement(
      ProtectedBookAccess bookAccess,
      Path bookPath,
      Path backupPath,
      Path backupKeyPath,
      UUID backupId,
      AttestationSigningSession signingSession) {
    List<Path> blocking = store.blockingArtifactsForBook(bookPath);
    if (!blocking.isEmpty()) {
      return AttestedProtectedBookMaintenanceDecisions.rejectedBackup(
          new ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts(bookPath, blocking));
    }
    ProtectedBookMaintenanceStore.LeaseAcquisition lease =
        store.acquireManagedArtifactLease(bookPath, ProtectedBookMaintenanceArtifactRole.LIVE_BOOK);
    if (lease instanceof ProtectedBookMaintenanceStore.LeaseBusy busy) {
      return AttestedProtectedBookMaintenanceDecisions.rejectedBackup(
          new ProtectedBookMaintenanceRejection.ArtifactBusy(
              ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, busy.artifactPath()));
    }
    ProtectedBookMaintenanceStore.HeldLease heldLease =
        (ProtectedBookMaintenanceStore.HeldLease) lease;
    try {
      return MaintenanceResourceScope.closeAfter(
          heldLease::close,
          () -> {
            ProtectedBookMaintenanceStore.VerifiedBook liveBook =
                AttestedProtectedBookMaintenanceDecisions.requireVerifiedBook(store, bookAccess);
            return MaintenanceResourceScope.closeAfter(
                liveBook::close,
                () -> {
                  AttestedProtectedBookMaintenanceStore.VerifiedBackupArtifact artifact =
                      store.verifyBackupArtifact(backupPath, backupKeyPath);
                  return MaintenanceResourceScope.closeAfter(
                      artifact::close,
                      () -> {
                        if (!artifact.verification().backupId().equals(backupId)) {
                          return AttestedProtectedBookMaintenanceDecisions.rejectedBackup(
                              new ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists(
                                  backupPath));
                        }
                        List<AttestationEvidence> liveEvidence =
                            store.loadAttestationEvidence(liveBook);
                        if (!AttestedProtectedBookBackupAcknowledgementWorkflow
                            .artifactSourceIsLive(artifact.verification(), liveEvidence)) {
                          return AttestedProtectedBookMaintenanceDecisions.rejectedBackup(
                              new ProtectedBookMaintenanceRejection.ArtifactVerificationFailed(
                                  ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE,
                                  backupPath,
                                  ProtectedBookVerificationFailure
                                      .PROTECTED_BOOK_VERIFICATION_FAILED));
                        }
                        AttestationBackupAcknowledgement acknowledgement =
                            new AttestationBackupAcknowledgement(
                                artifact.verification().backupId(),
                                artifact.verification().artifactDigest(),
                                artifact.verification().sourceOrder(),
                                artifact.verification().sourceOperationHead());
                        return acknowledgementWorkflow.acknowledgeBackup(
                            liveBook,
                            bookPath,
                            backupPath,
                            backupKeyPath,
                            acknowledgement,
                            signingSession,
                            true);
                      });
                });
          });
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return AttestedProtectedBookMaintenanceDecisions.rejectedBackup(exception.rejection());
    } catch (AttestationAdmissionRejectedException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      return AttestedProtectedBookMaintenanceDecisions.failure(
          backupPath, "backupFilePath", "Failed to resume the backup acknowledgement.");
    }
  }
}
