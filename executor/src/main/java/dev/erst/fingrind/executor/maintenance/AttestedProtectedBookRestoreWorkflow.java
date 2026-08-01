package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRetention;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.core.attestation.AttestationAdmissionRejectedException;
import dev.erst.fingrind.core.attestation.AttestationAppendOutcome;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import dev.erst.fingrind.core.attestation.AttestationLifecycleMutationProjection;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.core.attestation.AttestationSigningSession;
import dev.erst.fingrind.executor.AttestationCommitProjection;
import dev.erst.fingrind.executor.spi.AttestedProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.HeldWorkflowScope;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.PreparedPairPublication;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowScopeAcquisition;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowScopeBusy;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowSourceMember;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowSourceMembers;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationAdmission;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationBinding;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationFailureOutcome;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationRecoveryRequest;
import dev.erst.fingrind.executor.spi.StagedPairPublicationCommitOutcome;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Restores one verified backup through its independently attested derived continuation. */
final class AttestedProtectedBookRestoreWorkflow {
  private static final AttestationOperationKind RESTORE_BOOK_OPERATION =
      AttestationOperationKind.RESTORE_BOOK;

  private final Clock clock;
  private final AttestedProtectedBookMaintenanceStore store;

  AttestedProtectedBookRestoreWorkflow(Clock clock, AttestedProtectedBookMaintenanceStore store) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.store = Objects.requireNonNull(store, "store");
  }

  MaintenanceDecision<ProtectedBookRestoreOutcome> restore(
      Path bookFilePath,
      Path newBookKeyFilePath,
      Path backupArtifactPath,
      Path backupKeyFilePath,
      AttestationSigningSession signingSession) {
    Objects.requireNonNull(signingSession, "signingSession");
    Path bookPath;
    Path newKeyPath;
    Path artifactPath;
    Path backupKeyPath;
    try {
      artifactPath =
          store.normalizeExistingSource(
              backupArtifactPath,
              "backupFilePath",
              ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE);
      backupKeyPath =
          store.normalizeExistingSource(
              backupKeyFilePath,
              "backupKeyFilePath",
              ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_SOURCE);
      bookPath =
          store.normalizeFinalTarget(
              bookFilePath, "bookFilePath", ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET);
      newKeyPath =
          store.normalizeFinalTarget(
              newBookKeyFilePath,
              "newBookKeyFilePath",
              ProtectedBookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET);
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return AttestedProtectedBookMaintenanceDecisions.rejectedRestore(exception.rejection());
    }
    try {
      WorkflowScopeAcquisition scopeAcquisition =
          store.acquireWorkflowScope(
              new WorkflowSourceMembers(
                  List.of(
                      new WorkflowSourceMember(
                          artifactPath, ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE),
                      new WorkflowSourceMember(
                          backupKeyPath, ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_SOURCE))),
              bookPath,
              ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET,
              newKeyPath,
              ProtectedBookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET);
      if (scopeAcquisition instanceof WorkflowScopeBusy busy) {
        return AttestedProtectedBookMaintenanceDecisions.rejectedRestore(
            new ProtectedBookMaintenanceRejection.ArtifactBusy(
                busy.artifactRole(), busy.artifactPath()));
      }
      try (HeldWorkflowScope workflowScope = (HeldWorkflowScope) scopeAcquisition) {
        return restoreWithinWorkflowScope(
            workflowScope, bookPath, newKeyPath, artifactPath, backupKeyPath, signingSession);
      }
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return AttestedProtectedBookMaintenanceDecisions.rejectedRestore(exception.rejection());
    } catch (AttestationAdmissionRejectedException exception) {
      throw exception;
    } catch (ContractFailureException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      return AttestedProtectedBookMaintenanceDecisions.failure(
          bookPath, "bookFilePath", "Failed to restore the attested backup artifact.");
    }
  }

  private Optional<MaintenanceDecision<ProtectedBookRestoreOutcome>> restoreInputRejectionFor(
      Path bookPath, Path artifactPath) {
    if (bookPath.equals(artifactPath)) {
      return Optional.of(
          AttestedProtectedBookMaintenanceDecisions.rejectedRestore(
              new ProtectedBookMaintenanceRejection.BackupSourceMatchesLiveBook(
                  bookPath, artifactPath)));
    }
    List<Path> backupBlockingArtifacts = store.blockingArtifactsForBackupSource(artifactPath);
    if (!backupBlockingArtifacts.isEmpty()) {
      return Optional.of(
          AttestedProtectedBookMaintenanceDecisions.rejectedRestore(
              new ProtectedBookMaintenanceRejection.BackupSourceHasBlockingArtifacts(
                  artifactPath, backupBlockingArtifacts)));
    }
    return Optional.empty();
  }

  private MaintenanceDecision<ProtectedBookRestoreOutcome> restoreWithinWorkflowScope(
      HeldWorkflowScope workflowScope,
      Path bookPath,
      Path newKeyPath,
      Path artifactPath,
      Path backupKeyPath,
      AttestationSigningSession signingSession) {
    Optional<MaintenanceDecision<ProtectedBookRestoreOutcome>> inputRejection =
        restoreInputRejectionFor(bookPath, artifactPath);
    if (inputRejection.isPresent()) {
      return inputRejection.orElseThrow();
    }
    AttestedProtectedBookMaintenanceStore.VerifiedBackupArtifact artifact =
        store.verifyBackupArtifact(artifactPath, backupKeyPath);
    return MaintenanceResourceScope.closeAfter(
        artifact::close,
        () -> {
          AttestationBackupAcknowledgement acknowledgement = acknowledgementFor(artifact);
          ProtectedBookPairPublicationAdmission admission =
              Objects.requireNonNull(workflowScope, "workflowScope")
                  .admitPairPublication(
                      RestoredBookTargetPolicy.REQUIRE_ABSENT,
                      new ProtectedBookPairPublicationRecoveryRequest.Restore(
                          artifactPath, backupKeyPath, acknowledgement));
          return switch (admission) {
            case ProtectedBookPairPublicationAdmission.Prepared prepared ->
                stagePreparedRestore(
                    bookPath,
                    newKeyPath,
                    artifactPath,
                    backupKeyPath,
                    acknowledgement,
                    prepared.publication(),
                    artifact,
                    signingSession);
            case ProtectedBookPairPublicationAdmission.Recovered recovered ->
                recoveredRestore(bookPath, newKeyPath, recovered.binding(), recovered.retention());
            case ProtectedBookPairPublicationAdmission.ExistingCompleteBackup _ ->
                throw new IllegalStateException(
                    "Restore admission cannot classify a backup-only external pair.");
            case ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired
                    prepublication ->
                throw AttestedProtectedBookMaintenanceDecisions.prepublicationRecoveryRequired(
                    OperationId.RESTORE_BOOK,
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
                    OperationId.RESTORE_BOOK,
                    uncertain.bookArtifactPath(),
                    uncertain.bookArtifactState(),
                    uncertain.secretArtifactPath(),
                    uncertain.secretArtifactState(),
                    uncertain.pairPublicationRetention());
          };
        });
  }

  private MaintenanceDecision<ProtectedBookRestoreOutcome> stagePreparedRestore(
      Path bookPath,
      Path newKeyPath,
      Path artifactPath,
      Path backupKeyPath,
      AttestationBackupAcknowledgement acknowledgement,
      PreparedPairPublication publication,
      AttestedProtectedBookMaintenanceStore.VerifiedBackupArtifact artifact,
      AttestationSigningSession signingSession) {
    return MaintenanceResourceScope.closeAfter(
        publication::close,
        () -> {
          List<Path> bookBlockingArtifacts = store.blockingArtifactsForBook(bookPath);
          if (!bookBlockingArtifacts.isEmpty()) {
            return AttestedProtectedBookMaintenanceDecisions.rejectedRestore(
                new ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts(
                    bookPath, bookBlockingArtifacts));
          }
          return stageAndPublishRestore(
              bookPath,
              newKeyPath,
              artifactPath,
              backupKeyPath,
              acknowledgement,
              publication,
              artifact,
              signingSession);
        });
  }

  private MaintenanceDecision<ProtectedBookRestoreOutcome> stageAndPublishRestore(
      Path bookPath,
      Path newKeyPath,
      Path artifactPath,
      Path backupKeyPath,
      AttestationBackupAcknowledgement acknowledgement,
      PreparedPairPublication publication,
      AttestedProtectedBookMaintenanceStore.VerifiedBackupArtifact artifact,
      AttestationSigningSession signingSession) {
    return store
        .stageRestoredBookPair(artifact.snapshotBook(), publication)
        .fold(
            staged -> {
              try (StagedRestoredBookPair stagedRestore = staged) {
                return stagedRestore
                    .verifyInitializedRestoredBook()
                    .fold(
                        verification -> {
                          if (verification
                              instanceof
                              ProtectedBookMaintenanceStore.VerificationFailure failure) {
                            return AttestedProtectedBookMaintenanceDecisions.rejectedRestore(
                                AttestedProtectedBookMaintenanceDecisions.verificationFailed(
                                    ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET, failure));
                          }
                          try (ProtectedBookMaintenanceStore.VerifiedBook restoredBook =
                              (ProtectedBookMaintenanceStore.VerifiedBook) verification) {
                            AttestationAppendOutcome appendOutcome =
                                store.appendAttestedOperation(
                                    restoredBook,
                                    RESTORE_BOOK_OPERATION,
                                    clock.instant(),
                                    AttestationLifecycleMutationProjection.restoreBook(
                                        RESTORE_BOOK_OPERATION.wireToken(), acknowledgement),
                                    signingSession,
                                    null);
                            AttestationCommit attestationCommit =
                                AttestationCommitProjection.fromVerifiedAppend(
                                    AttestedProtectedBookAppendOutcomes.requireNewAppend(
                                        appendOutcome, RESTORE_BOOK_OPERATION));
                            StagedPairPublicationCommitOutcome.Published published =
                                AttestedProtectedBookPairPublicationCommit.requirePublished(
                                    OperationId.RESTORE_BOOK,
                                    stagedRestore.commit(
                                        new ProtectedBookPairPublicationBinding.Restore(
                                            artifactPath,
                                            backupKeyPath,
                                            acknowledgement,
                                            attestationCommit)));
                            return MaintenanceDecision.accepted(
                                new ProtectedBookRestoreOutcome.Restored(
                                    bookPath,
                                    newKeyPath,
                                    attestationCommit,
                                    ProtectedBookPairPublicationCompletion.PUBLISHED,
                                    published.retention()));
                          }
                        },
                        ignored ->
                            AttestedProtectedBookMaintenanceDecisions.failure(
                                bookPath,
                                "bookFilePath",
                                "Failed to verify staged restored book."));
              }
            },
            failure ->
                AttestedProtectedBookMaintenanceDecisions.failure(
                    bookPath, "bookFilePath", failure.message()));
  }

  private static AttestationBackupAcknowledgement acknowledgementFor(
      AttestedProtectedBookMaintenanceStore.VerifiedBackupArtifact artifact) {
    return new AttestationBackupAcknowledgement(
        artifact.verification().backupId(),
        artifact.verification().artifactDigest(),
        artifact.verification().sourceOrder(),
        artifact.verification().sourceOperationHead());
  }

  private static MaintenanceDecision<ProtectedBookRestoreOutcome> recoveredRestore(
      Path bookPath,
      Path newKeyPath,
      ProtectedBookPairPublicationBinding binding,
      ProtectedBookPairPublicationRetention retention) {
    if (!(binding instanceof ProtectedBookPairPublicationBinding.Restore restore)) {
      throw new IllegalStateException("Restore recovery returned a non-restore pair binding.");
    }
    return MaintenanceDecision.accepted(
        new ProtectedBookRestoreOutcome.Restored(
            bookPath,
            newKeyPath,
            restore.attestationCommit(),
            ProtectedBookPairPublicationCompletion.RECOVERED,
            retention));
  }
}
