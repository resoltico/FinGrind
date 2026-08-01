package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRetention;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.core.attestation.AttestationAdmissionRejectedException;
import dev.erst.fingrind.core.attestation.AttestationAppendOutcome;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationLifecycleMutationProjection;
import dev.erst.fingrind.core.attestation.AttestationLifecycleState;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.core.attestation.AttestationRegistryMutation;
import dev.erst.fingrind.core.attestation.AttestationSigningSession;
import dev.erst.fingrind.executor.AttestationCommitProjection;
import dev.erst.fingrind.executor.spi.AttestedProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.HeldWorkflowScope;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.PreparedPairPublication;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowScopeAcquisition;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowScopeBusy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationAdmission;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationBinding;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationFailureOutcome;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationRecoveryRequest;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationSourceIdentity;
import dev.erst.fingrind.executor.spi.StagedPairPublicationCommitOutcome;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Attested lifecycle owner for protected-book restore and rekey mutations. */
public final class AttestedProtectedBookLifecycleWorkflow {
  private static final AttestationOperationKind REKEY_BOOK_OPERATION =
      AttestationOperationKind.REKEY_BOOK;

  private final Clock clock;
  private final AttestedProtectedBookMaintenanceStore store;
  private final AttestedProtectedBookBackupWorkflow backupWorkflow;
  private final AttestedProtectedBookRestoreWorkflow restoreWorkflow;
  private final AttestedProtectedBookRegistryMutationWorkflow registryMutationWorkflow;

  /** Creates one lifecycle workflow over an attested protected-book storage implementation. */
  public AttestedProtectedBookLifecycleWorkflow(Clock clock, ProtectedBookMaintenanceStore store) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.store = AttestedProtectedBookMaintenanceStore.require(store);
    backupWorkflow = new AttestedProtectedBookBackupWorkflow(this.clock, this.store);
    restoreWorkflow = new AttestedProtectedBookRestoreWorkflow(this.clock, this.store);
    registryMutationWorkflow =
        new AttestedProtectedBookRegistryMutationWorkflow(this.clock, this.store);
  }

  /** Stages, verifies, seals, publishes, and acknowledges one manifest-attested backup artifact. */
  public MaintenanceDecision<ProtectedBookBackupOutcome> backupBook(
      ProtectedBookAccess bookAccess,
      Path backupFilePath,
      Path backupBookKeyFilePath,
      UUID backupId,
      AttestationSigningSession signingSession) {
    return backupWorkflow.backupBook(
        bookAccess, backupFilePath, backupBookKeyFilePath, backupId, signingSession);
  }

  /** Restores a manifest-attested artifact as one independently signed derived continuation. */
  public MaintenanceDecision<ProtectedBookRestoreOutcome> restoreBook(
      Path bookFilePath,
      Path newBookKeyFilePath,
      Path backupArtifactPath,
      Path backupKeyFilePath,
      AttestationSigningSession signingSession) {
    return restoreWorkflow.restore(
        bookFilePath, newBookKeyFilePath, backupArtifactPath, backupKeyFilePath, signingSession);
  }

  /** Rekeys one book only after writing its exact signed rekey operation into the staged copy. */
  public MaintenanceDecision<ProtectedBookRekeyOutcome> rekeyBook(
      ProtectedBookAccess bookAccess,
      Path newBookKeyFilePath,
      AttestationSigningSession signingSession) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(signingSession, "signingSession");
    Path bookPath;
    Path newKeyPath;
    ProtectedBookAccess canonicalBookAccess;
    try {
      canonicalBookAccess =
          ProtectedBookAccess.canonicalizeExistingLiveBookAccess(store, bookAccess);
      bookPath = canonicalBookAccess.bookFilePath();
      newKeyPath =
          store.normalizeFinalTarget(
              newBookKeyFilePath,
              "newBookKeyFilePath",
              ProtectedBookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET);
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return AttestedProtectedBookMaintenanceDecisions.rejectedRekey(exception.rejection());
    }
    ProtectedBookPairPublicationSourceIdentity sourceIdentity =
        ProtectedBookPairPublicationSourceIdentity.from(canonicalBookAccess);
    try {
      WorkflowScopeAcquisition scopeAcquisition =
          store.acquireWorkflowScope(
              canonicalBookAccess.workflowSourceMembers(),
              bookPath,
              ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
              newKeyPath,
              ProtectedBookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET);
      if (scopeAcquisition instanceof WorkflowScopeBusy busy) {
        return AttestedProtectedBookMaintenanceDecisions.rejectedRekey(
            new ProtectedBookMaintenanceRejection.ArtifactBusy(
                busy.artifactRole(), busy.artifactPath()));
      }
      try (HeldWorkflowScope workflowScope = (HeldWorkflowScope) scopeAcquisition) {
        ProtectedBookPairPublicationAdmission admission =
            workflowScope.admitPairPublication(
                RestoredBookTargetPolicy.REPLACE_SELECTED,
                new ProtectedBookPairPublicationRecoveryRequest.Rekey(sourceIdentity));
        return decideRekeyAdmission(
            admission, canonicalBookAccess, bookPath, newKeyPath, sourceIdentity, signingSession);
      }
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return AttestedProtectedBookMaintenanceDecisions.rejectedRekey(exception.rejection());
    } catch (AttestationAdmissionRejectedException exception) {
      throw exception;
    } catch (ContractFailureException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      return AttestedProtectedBookMaintenanceDecisions.failure(
          newKeyPath, "newBookKeyFilePath", "Failed to prepare rekey publication.");
    }
  }

  private MaintenanceDecision<ProtectedBookRekeyOutcome> decideRekeyAdmission(
      ProtectedBookPairPublicationAdmission admission,
      ProtectedBookAccess canonicalBookAccess,
      Path bookPath,
      Path newKeyPath,
      ProtectedBookPairPublicationSourceIdentity sourceIdentity,
      AttestationSigningSession signingSession) {
    return switch (admission) {
      case ProtectedBookPairPublicationAdmission.Prepared prepared ->
          rekeyPrepared(
              canonicalBookAccess,
              bookPath,
              newKeyPath,
              sourceIdentity,
              signingSession,
              prepared.publication());
      case ProtectedBookPairPublicationAdmission.Recovered recovered ->
          recoveredRekey(bookPath, newKeyPath, recovered.binding(), recovered.retention());
      case ProtectedBookPairPublicationAdmission.ExistingCompleteBackup _ ->
          throw new IllegalStateException("Rekey admission cannot classify a backup-only pair.");
      case ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired
              prepublication ->
          throw AttestedProtectedBookMaintenanceDecisions.prepublicationRecoveryRequired(
              OperationId.REKEY_BOOK,
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
              OperationId.REKEY_BOOK,
              uncertain.bookArtifactPath(),
              uncertain.bookArtifactState(),
              uncertain.secretArtifactPath(),
              uncertain.secretArtifactState(),
              uncertain.pairPublicationRetention());
    };
  }

  private MaintenanceDecision<ProtectedBookRekeyOutcome> rekeyPrepared(
      ProtectedBookAccess canonicalBookAccess,
      Path bookPath,
      Path newKeyPath,
      ProtectedBookPairPublicationSourceIdentity sourceIdentity,
      AttestationSigningSession signingSession,
      PreparedPairPublication publication) {
    try {
      return MaintenanceResourceScope.closeAfter(
          publication::close,
          () -> {
            List<Path> blocking = store.blockingArtifactsForBook(bookPath);
            if (!blocking.isEmpty()) {
              return AttestedProtectedBookMaintenanceDecisions.rejectedRekey(
                  new ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts(
                      bookPath, blocking));
            }
            ProtectedBookMaintenanceStore.VerifiedBook liveBook =
                AttestedProtectedBookMaintenanceDecisions.requireVerifiedBook(
                    store, canonicalBookAccess);
            return MaintenanceResourceScope.closeAfter(
                liveBook::close,
                () -> {
                  List<AttestationEvidence> evidence = store.loadAttestationEvidence(liveBook);
                  var sourceVerification =
                      AttestedProtectedBookMaintenanceDecisions.requireVerifiedLiveEvidence(
                          evidence, bookPath);
                  AttestationCommit sourceCommit =
                      new AttestationCommit(
                          sourceVerification.headOrder(),
                          HexFormat.of().formatHex(sourceVerification.operationHead()));
                  return store
                      .stageRestoredBookPair(liveBook, publication)
                      .fold(
                          staged ->
                              rekeyAndPublish(
                                  bookPath,
                                  newKeyPath,
                                  staged,
                                  evidence,
                                  sourceIdentity,
                                  sourceCommit,
                                  signingSession),
                          failure ->
                              AttestedProtectedBookMaintenanceDecisions.failure(
                                  newKeyPath, "newBookKeyFilePath", failure.message()));
                });
          });
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return AttestedProtectedBookMaintenanceDecisions.rejectedRekey(exception.rejection());
    } catch (AttestationAdmissionRejectedException exception) {
      throw exception;
    } catch (ContractFailureException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      return AttestedProtectedBookMaintenanceDecisions.failure(
          bookPath, "bookFilePath", "Failed to stage the attested rekey operation.");
    }
  }

  /** Appends one exact credential-registry or authorization-policy mutation to the live book. */
  public MaintenanceDecision<ProtectedBookRegistryMutationOutcome> mutateRegistry(
      ProtectedBookAccess bookAccess,
      AttestationRegistryMutation mutation,
      AttestationSigningSession signingSession) {
    return registryMutationWorkflow.mutate(bookAccess, mutation, signingSession);
  }

  private MaintenanceDecision<ProtectedBookRekeyOutcome> rekeyAndPublish(
      Path bookPath,
      Path newKeyPath,
      StagedRestoredBookPair staged,
      List<AttestationEvidence> sourceEvidence,
      ProtectedBookPairPublicationSourceIdentity sourceIdentity,
      AttestationCommit sourceCommit,
      AttestationSigningSession signingSession) {
    try (StagedRestoredBookPair stagedRekey = staged) {
      return stagedRekey
          .verifyInitializedRestoredBook()
          .fold(
              verification -> {
                if (verification
                    instanceof ProtectedBookMaintenanceStore.VerificationFailure failure) {
                  return AttestedProtectedBookMaintenanceDecisions.rejectedRekey(
                      AttestedProtectedBookMaintenanceDecisions.verificationFailed(
                          ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET, failure));
                }
                try (ProtectedBookMaintenanceStore.VerifiedBook rekeyedBook =
                    (ProtectedBookMaintenanceStore.VerifiedBook) verification) {
                  Instant recordedAt = clock.instant();
                  AttestationAppendOutcome appendOutcome =
                      store.appendAttestedOperation(
                          rekeyedBook,
                          REKEY_BOOK_OPERATION,
                          recordedAt,
                          AttestationLifecycleMutationProjection.rekeyBook(
                              REKEY_BOOK_OPERATION.wireToken(),
                              AttestationLifecycleState.nextKeyEpoch(sourceEvidence),
                              recordedAt,
                              java.util.Optional.empty()),
                          signingSession,
                          null);
                  AttestationCommit attestationCommit =
                      AttestationCommitProjection.fromVerifiedAppend(
                          AttestedProtectedBookAppendOutcomes.requireNewAppend(
                              appendOutcome, REKEY_BOOK_OPERATION));
                  StagedPairPublicationCommitOutcome.Published published =
                      AttestedProtectedBookPairPublicationCommit.requirePublished(
                          OperationId.REKEY_BOOK,
                          stagedRekey.commit(
                              new ProtectedBookPairPublicationBinding.Rekey(
                                  sourceIdentity, sourceCommit, attestationCommit)));
                  return MaintenanceDecision.accepted(
                      new ProtectedBookRekeyOutcome.Rekeyed(
                          bookPath,
                          newKeyPath,
                          attestationCommit,
                          ProtectedBookPairPublicationCompletion.PUBLISHED,
                          published.retention()));
                }
              },
              ignored ->
                  AttestedProtectedBookMaintenanceDecisions.failure(
                      bookPath, "bookFilePath", "Failed to verify staged rekey book."));
    }
  }

  private static MaintenanceDecision<ProtectedBookRekeyOutcome> recoveredRekey(
      Path bookPath,
      Path newKeyPath,
      ProtectedBookPairPublicationBinding binding,
      ProtectedBookPairPublicationRetention retention) {
    if (!(binding instanceof ProtectedBookPairPublicationBinding.Rekey rekey)) {
      throw new IllegalStateException("Rekey recovery returned a non-rekey pair binding.");
    }
    return MaintenanceDecision.accepted(
        new ProtectedBookRekeyOutcome.Rekeyed(
            bookPath,
            newKeyPath,
            rekey.attestationCommit(),
            ProtectedBookPairPublicationCompletion.RECOVERED,
            retention));
  }
}
