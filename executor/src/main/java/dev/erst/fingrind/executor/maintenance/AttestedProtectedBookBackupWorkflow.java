package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublication;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion;
import dev.erst.fingrind.contract.protocol.OperationId;
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
    BackupRequest request;
    try {
      request = prepareBackupRequest(bookAccess, backupFilePath, backupBookKeyFilePath, backupId);
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return AttestedProtectedBookMaintenanceDecisions.rejectedBackup(exception.rejection());
    }
    return backupWithinWorkflowScope(request, signingSession);
  }

  private BackupRequest prepareBackupRequest(
      ProtectedBookAccess bookAccess,
      Path backupFilePath,
      Path backupBookKeyFilePath,
      UUID backupId) {
    ProtectedBookAccess canonicalBookAccess =
        ProtectedBookAccess.canonicalizeExistingLiveBookAccess(store, bookAccess);
    Path backupPath =
        store.normalizeFinalTarget(
            backupFilePath, "backupFilePath", ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET);
    Path backupKeyPath =
        store.normalizeFinalTarget(
            backupBookKeyFilePath,
            "backupBookKeyFilePath",
            ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET);
    return new BackupRequest(
        canonicalBookAccess,
        canonicalBookAccess.bookFilePath(),
        backupPath,
        backupKeyPath,
        Objects.requireNonNull(backupId, "backupId"));
  }

  private MaintenanceDecision<ProtectedBookBackupOutcome> backupWithinWorkflowScope(
      BackupRequest request, AttestationSigningSession signingSession) {
    try {
      WorkflowScopeAcquisition scopeAcquisition =
          store.acquireWorkflowScope(
              request.canonicalBookAccess().workflowSourceMembers(),
              request.backupPath(),
              ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
              request.backupKeyPath(),
              ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET);
      if (scopeAcquisition instanceof WorkflowScopeBusy busy) {
        return AttestedProtectedBookMaintenanceDecisions.rejectedBackup(
            new ProtectedBookMaintenanceRejection.ArtifactBusy(
                busy.artifactRole(), busy.artifactPath()));
      }
      try (HeldWorkflowScope workflowScope = (HeldWorkflowScope) scopeAcquisition) {
        return resolvePairPublicationAdmission(
            request,
            signingSession,
            workflowScope.admitPairPublication(
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                new ProtectedBookPairPublicationRecoveryRequest.Backup(
                    request.bookPath(), request.backupId())));
      }
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return AttestedProtectedBookMaintenanceDecisions.rejectedBackup(exception.rejection());
    } catch (AttestationAdmissionRejectedException exception) {
      throw exception;
    } catch (ContractFailureException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      return AttestedProtectedBookMaintenanceDecisions.failure(
          request.backupPath(),
          "backupFilePath",
          "Failed to recover an interrupted backup publication.");
    }
  }

  private MaintenanceDecision<ProtectedBookBackupOutcome> resolvePairPublicationAdmission(
      BackupRequest request,
      AttestationSigningSession signingSession,
      ProtectedBookPairPublicationAdmission admission) {
    return switch (admission) {
      case ProtectedBookPairPublicationAdmission.Prepared prepared ->
          createBackupArtifact(
              request.canonicalBookAccess(),
              request.bookPath(),
              request.backupPath(),
              request.backupKeyPath(),
              request.backupId(),
              signingSession,
              prepared.publication());
      case ProtectedBookPairPublicationAdmission.Recovered recovered ->
          resumeBackupAcknowledgement(
              request.canonicalBookAccess(),
              request.bookPath(),
              request.backupPath(),
              request.backupKeyPath(),
              request.backupId(),
              signingSession,
              ProtectedBookPairPublicationCompletion.RECOVERED,
              recovered.publication());
      case ProtectedBookPairPublicationAdmission.ExistingCompleteBackup existing ->
          resolveExistingCompleteBackup(request, signingSession, existing);
      case ProtectedBookPairPublicationAdmission.PublicationTransactionIncomplete incomplete ->
          throw AttestedProtectedBookPairPublicationCommit.incompleteAdmission(
              OperationId.BACKUP_BOOK, incomplete);
      case ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked blocked ->
          throw AttestedProtectedBookMaintenanceDecisions.pairPublicationEvidenceBlocked(
              blocked.bookArtifactPath(),
              blocked.bookArtifactState(),
              blocked.secretArtifactPath(),
              blocked.secretArtifactState());
    };
  }

  private MaintenanceDecision<ProtectedBookBackupOutcome> resolveExistingCompleteBackup(
      BackupRequest request,
      AttestationSigningSession signingSession,
      ProtectedBookPairPublicationAdmission.ExistingCompleteBackup existing) {
    if (!request.backupPath().equals(existing.backupArtifactPath())
        || !request.backupKeyPath().equals(existing.backupKeyPath())) {
      return AttestedProtectedBookMaintenanceDecisions.rejectedBackup(
          new ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists(
              request.backupPath()));
    }
    return resumeBackupAcknowledgement(
        request.canonicalBookAccess(),
        request.bookPath(),
        request.backupPath(),
        request.backupKeyPath(),
        request.backupId(),
        signingSession,
        ProtectedBookPairPublicationCompletion.ALREADY_PUBLISHED,
        null);
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
      @Nullable ProtectedBookPairPublication pairPublication) {
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
                      pairPublication);
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

  /** Holds one normalized backup request while its publication scope is acquired and resolved. */
  private record BackupRequest(
      ProtectedBookAccess canonicalBookAccess,
      Path bookPath,
      Path backupPath,
      Path backupKeyPath,
      UUID backupId) {}
}
