package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.core.attestation.AttestationAdmissionRejectedException;
import dev.erst.fingrind.core.attestation.AttestationAppendOutcome;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationLifecycleMutationProjection;
import dev.erst.fingrind.core.attestation.AttestationLifecycleState;
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
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationAdmission;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationFailureOutcome;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationRecoveryRequest;
import dev.erst.fingrind.executor.spi.StagedPairPublicationCommitOutcome;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Owns rekey admission, staging, attestation, and final protected-book pair publication. */
final class AttestedProtectedBookRekeyWorkflow {
  private static final AttestationOperationKind REKEY_BOOK_OPERATION =
      AttestationOperationKind.REKEY_BOOK;

  private final Clock clock;
  private final AttestedProtectedBookMaintenanceStore store;
  private final AttestedProtectedBookPairPublicationRecovery publicationRecovery;

  AttestedProtectedBookRekeyWorkflow(
      Clock clock,
      AttestedProtectedBookMaintenanceStore store,
      AttestedProtectedBookPairPublicationRecovery publicationRecovery) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.store = Objects.requireNonNull(store, "store");
    this.publicationRecovery = Objects.requireNonNull(publicationRecovery, "publicationRecovery");
  }

  MaintenanceDecision<ProtectedBookRekeyOutcome> rekey(
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
                ProtectedBookPairPublicationRecoveryRequest.Rekey.INSTANCE);
        return decideAdmission(
            admission, canonicalBookAccess, bookPath, newKeyPath, signingSession);
      }
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return AttestedProtectedBookRekeyAdmissionRejection.resolve(
          store, canonicalBookAccess, bookPath, exception.rejection());
    } catch (AttestationAdmissionRejectedException exception) {
      throw exception;
    } catch (ContractFailureException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      return AttestedProtectedBookMaintenanceDecisions.failure(
          newKeyPath, "newBookKeyFilePath", "Failed to prepare rekey publication.");
    }
  }

  private MaintenanceDecision<ProtectedBookRekeyOutcome> decideAdmission(
      ProtectedBookPairPublicationAdmission admission,
      ProtectedBookAccess canonicalBookAccess,
      Path bookPath,
      Path newKeyPath,
      AttestationSigningSession signingSession) {
    return switch (admission) {
      case ProtectedBookPairPublicationAdmission.Prepared prepared ->
          rekeyPrepared(
              canonicalBookAccess, bookPath, newKeyPath, signingSession, prepared.publication());
      case ProtectedBookPairPublicationAdmission.Recovered recovered ->
          publicationRecovery.recoverRekey(bookPath, newKeyPath, recovered.publication());
      case ProtectedBookPairPublicationAdmission.ExistingCompleteBackup _ ->
          throw new IllegalStateException("Rekey admission cannot classify a backup-only pair.");
      case ProtectedBookPairPublicationAdmission.PublicationTransactionIncomplete incomplete ->
          throw AttestedProtectedBookPairPublicationCommit.incompleteAdmission(
              OperationId.REKEY_BOOK, incomplete);
      case ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked blocked ->
          throw AttestedProtectedBookMaintenanceDecisions.pairPublicationEvidenceBlocked(
              blocked.bookArtifactPath(),
              blocked.bookArtifactState(),
              blocked.secretArtifactPath(),
              blocked.secretArtifactState());
    };
  }

  private MaintenanceDecision<ProtectedBookRekeyOutcome> rekeyPrepared(
      ProtectedBookAccess canonicalBookAccess,
      Path bookPath,
      Path newKeyPath,
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
                  AttestedProtectedBookMaintenanceDecisions.requireVerifiedLiveEvidence(
                      evidence, bookPath);
                  return store
                      .stageRestoredBookPair(liveBook, publication)
                      .fold(
                          staged ->
                              rekeyAndPublish(
                                  bookPath, newKeyPath, staged, evidence, signingSession),
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

  private MaintenanceDecision<ProtectedBookRekeyOutcome> rekeyAndPublish(
      Path bookPath,
      Path newKeyPath,
      StagedRestoredBookPair staged,
      List<AttestationEvidence> sourceEvidence,
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
                          OperationId.REKEY_BOOK, stagedRekey.commit());
                  return MaintenanceDecision.accepted(
                      new ProtectedBookRekeyOutcome.Rekeyed(
                          bookPath,
                          newKeyPath,
                          attestationCommit,
                          dev.erst.fingrind.contract.bookkeeping
                              .ProtectedBookPairPublicationCompletion.PUBLISHED,
                          published.requirePublication()));
                }
              },
              ignored ->
                  AttestedProtectedBookMaintenanceDecisions.failure(
                      bookPath, "bookFilePath", "Failed to verify staged rekey book."));
    }
  }
}
