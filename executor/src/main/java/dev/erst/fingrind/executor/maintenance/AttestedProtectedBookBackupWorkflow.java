package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRetention;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.core.attestation.AttestationAdmissionRejectedException;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationSigningSession;
import dev.erst.fingrind.executor.spi.AttestedProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.HeldWorkflowScope;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.PreparedPairPublication;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowScopeAcquisition;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowScopeBusy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationAdmission;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationFailureOutcome;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationRecoveryRequest;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

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
    Path bookPath;
    Path backupPath;
    Path backupKeyPath;
    ProtectedBookAccess canonicalBookAccess;
    try {
      canonicalBookAccess =
          ProtectedBookAccess.canonicalizeExistingLiveBookAccess(store, bookAccess);
      bookPath = canonicalBookAccess.bookFilePath();
      backupPath =
          store.normalizeFinalTarget(
              backupFilePath, "backupFilePath", ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET);
      backupKeyPath =
          store.normalizeFinalTarget(
              backupBookKeyFilePath,
              "backupBookKeyFilePath",
              ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET);
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return AttestedProtectedBookMaintenanceDecisions.rejectedBackup(exception.rejection());
    }
    UUID checkedBackupId = Objects.requireNonNull(backupId, "backupId");
    try {
      WorkflowScopeAcquisition scopeAcquisition =
          store.acquireWorkflowScope(
              canonicalBookAccess.workflowSourceMembers(),
              backupPath,
              ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
              backupKeyPath,
              ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET);
      if (scopeAcquisition instanceof WorkflowScopeBusy busy) {
        return AttestedProtectedBookMaintenanceDecisions.rejectedBackup(
            new ProtectedBookMaintenanceRejection.ArtifactBusy(
                busy.artifactRole(), busy.artifactPath()));
      }
      try (HeldWorkflowScope workflowScope = (HeldWorkflowScope) scopeAcquisition) {
        ProtectedBookPairPublicationAdmission admission =
            workflowScope.admitPairPublication(
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                new ProtectedBookPairPublicationRecoveryRequest.Backup(bookPath, checkedBackupId));
        return switch (admission) {
          case ProtectedBookPairPublicationAdmission.Prepared prepared ->
              createBackupArtifact(
                  canonicalBookAccess,
                  bookPath,
                  backupPath,
                  backupKeyPath,
                  checkedBackupId,
                  signingSession,
                  prepared.publication());
          case ProtectedBookPairPublicationAdmission.Recovered recovered ->
              resumeRecoveredBackupAcknowledgement(
                  canonicalBookAccess,
                  bookPath,
                  backupPath,
                  backupKeyPath,
                  checkedBackupId,
                  signingSession,
                  recovered);
          case ProtectedBookPairPublicationAdmission.ExistingCompleteBackup existing -> {
            if (!backupPath.equals(existing.backupArtifactPath())
                || !backupKeyPath.equals(existing.backupKeyPath())) {
              yield AttestedProtectedBookMaintenanceDecisions.rejectedBackup(
                  new ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists(backupPath));
            }
            yield resumeBackupAcknowledgement(
                canonicalBookAccess,
                bookPath,
                backupPath,
                backupKeyPath,
                checkedBackupId,
                signingSession,
                ProtectedBookPairPublicationCompletion.ALREADY_PUBLISHED,
                null);
          }
          case ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired
                  prepublication ->
              throw AttestedProtectedBookMaintenanceDecisions.prepublicationRecoveryRequired(
                  dev.erst.fingrind.contract.protocol.OperationId.BACKUP_BOOK,
                  prepublication.bookArtifactPath(),
                  prepublication.secretArtifactPath(),
                  prepublication.recoveryRecordState(),
                  prepublication.pairPublicationRetention());
          case ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked blocked ->
              throw AttestedProtectedBookMaintenanceDecisions.pairPublicationEvidenceBlocked(
                  blocked.bookArtifactPath(),
                  blocked.bookArtifactState(),
                  blocked.secretArtifactPath(),
                  blocked.secretArtifactState(),
                  blocked.pairPublicationRetention());
          case ProtectedBookPairPublicationFailureOutcome.CompletionUncertain uncertain ->
              throw AttestedProtectedBookMaintenanceDecisions.pairPublicationUncertain(
                  dev.erst.fingrind.contract.protocol.OperationId.BACKUP_BOOK,
                  uncertain.bookArtifactPath(),
                  uncertain.bookArtifactState(),
                  uncertain.secretArtifactPath(),
                  uncertain.secretArtifactState(),
                  uncertain.pairPublicationRetention());
        };
      }
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return AttestedProtectedBookMaintenanceDecisions.rejectedBackup(exception.rejection());
    } catch (AttestationAdmissionRejectedException exception) {
      throw exception;
    } catch (ContractFailureException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      return AttestedProtectedBookMaintenanceDecisions.failure(
          backupPath, "backupFilePath", "Failed to recover an interrupted backup publication.");
    }
  }

  private MaintenanceDecision<ProtectedBookBackupOutcome> createBackupArtifact(
      ProtectedBookAccess canonicalBookAccess,
      Path bookPath,
      Path backupPath,
      Path backupKeyPath,
      UUID backupId,
      AttestationSigningSession signingSession,
      PreparedPairPublication publication) {
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
            ProtectedBookMaintenanceStore.VerifiedBook liveBook =
                AttestedProtectedBookMaintenanceDecisions.requireVerifiedBook(
                    store, canonicalBookAccess);
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
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return AttestedProtectedBookMaintenanceDecisions.rejectedBackup(exception.rejection());
    } catch (AttestationAdmissionRejectedException exception) {
      throw exception;
    } catch (ContractFailureException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      return AttestedProtectedBookMaintenanceDecisions.failure(
          backupPath, "backupFilePath", "Failed to create the attested backup artifact.");
    }
  }

  private MaintenanceDecision<ProtectedBookBackupOutcome> resumeBackupAcknowledgement(
      ProtectedBookAccess canonicalBookAccess,
      Path bookPath,
      Path backupPath,
      Path backupKeyPath,
      UUID backupId,
      AttestationSigningSession signingSession,
      ProtectedBookPairPublicationCompletion pairPublicationCompletion,
      @Nullable ProtectedBookPairPublicationRetention pairPublicationRetention) {
    List<Path> blocking = store.blockingArtifactsForBook(bookPath);
    if (!blocking.isEmpty()) {
      return AttestedProtectedBookMaintenanceDecisions.rejectedBackup(
          new ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts(bookPath, blocking));
    }
    try {
      ProtectedBookMaintenanceStore.VerifiedBook liveBook =
          AttestedProtectedBookMaintenanceDecisions.requireVerifiedBook(store, canonicalBookAccess);
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
                  List<AttestationEvidence> liveEvidence = store.loadAttestationEvidence(liveBook);
                  AttestedProtectedBookMaintenanceDecisions.requireVerifiedLiveEvidence(
                      liveEvidence, bookPath);
                  if (!AttestedProtectedBookBackupAcknowledgementWorkflow.artifactSourceIsLive(
                      artifact.verification(), liveEvidence)) {
                    return AttestedProtectedBookMaintenanceDecisions.rejectedBackup(
                        new ProtectedBookMaintenanceRejection.ArtifactVerificationFailed(
                            ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE,
                            backupPath,
                            ProtectedBookVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED));
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
                      AttestedProtectedBookBackupAcknowledgementWorkflow
                          .BackupAcknowledgementRequest.RESUMED,
                      pairPublicationCompletion,
                      pairPublicationRetention);
                });
          });
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return AttestedProtectedBookMaintenanceDecisions.rejectedBackup(exception.rejection());
    } catch (AttestationAdmissionRejectedException exception) {
      throw exception;
    } catch (ContractFailureException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      return AttestedProtectedBookMaintenanceDecisions.failure(
          backupPath, "backupFilePath", "Failed to resume the backup acknowledgement.");
    }
  }

  private MaintenanceDecision<ProtectedBookBackupOutcome> resumeRecoveredBackupAcknowledgement(
      ProtectedBookAccess canonicalBookAccess,
      Path bookPath,
      Path backupPath,
      Path backupKeyPath,
      UUID backupId,
      AttestationSigningSession signingSession,
      ProtectedBookPairPublicationAdmission.Recovered recovered) {
    if (!(recovered.binding()
            instanceof
            dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationBinding.Backup backupBinding)
        || !backupBinding.acknowledgement().backupId().equals(backupId)) {
      throw new IllegalStateException(
          "Backup pair recovery returned a mismatched operation binding.");
    }
    return resumeBackupAcknowledgement(
        canonicalBookAccess,
        bookPath,
        backupPath,
        backupKeyPath,
        backupId,
        signingSession,
        ProtectedBookPairPublicationCompletion.RECOVERED,
        recovered.retention());
  }
}
