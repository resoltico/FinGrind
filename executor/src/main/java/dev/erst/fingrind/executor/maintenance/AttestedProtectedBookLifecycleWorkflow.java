package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.core.attestation.AttestationAdmissionRejectedException;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationLifecycleMutationProjection;
import dev.erst.fingrind.core.attestation.AttestationLifecycleState;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.core.attestation.AttestationRegistryMutation;
import dev.erst.fingrind.core.attestation.AttestationSigningSession;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.core.attestation.AttestationVerifier;
import dev.erst.fingrind.executor.AttestationCommitProjection;
import dev.erst.fingrind.executor.spi.AttestedProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.HeldLease;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.LeaseAcquisition;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.PreparedPairPublication;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Attested lifecycle owner for protected-book restore and rekey mutations. */
public final class AttestedProtectedBookLifecycleWorkflow {
  private static final AttestationOperationKind RESTORE_BOOK_OPERATION =
      AttestationOperationKind.RESTORE_BOOK;
  private static final AttestationOperationKind REKEY_BOOK_OPERATION =
      AttestationOperationKind.REKEY_BOOK;

  private final Clock clock;
  private final AttestedProtectedBookMaintenanceStore store;
  private final AttestedProtectedBookBackupWorkflow backupWorkflow;
  private final AttestedProtectedBookRegistryMutationWorkflow registryMutationWorkflow;

  /** Creates one lifecycle workflow over an attested protected-book storage implementation. */
  public AttestedProtectedBookLifecycleWorkflow(Clock clock, ProtectedBookMaintenanceStore store) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.store = AttestedProtectedBookMaintenanceStore.require(store);
    backupWorkflow = new AttestedProtectedBookBackupWorkflow(this.clock, this.store);
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
    Objects.requireNonNull(signingSession, "signingSession");
    Path bookPath = store.normalize(bookFilePath, "bookFilePath");
    Path newKeyPath = store.normalize(newBookKeyFilePath, "newBookKeyFilePath");
    Path artifactPath = store.normalize(backupArtifactPath, "backupFilePath");
    Path backupKeyPath = store.normalize(backupKeyFilePath, "backupKeyFilePath");
    if (bookPath.equals(artifactPath)) {
      return AttestedProtectedBookMaintenanceDecisions.rejectedRestore(
          new ProtectedBookMaintenanceRejection.BackupSourceMatchesLiveBook(
              bookPath, artifactPath));
    }
    List<Path> backupBlockingArtifacts = store.blockingArtifactsForBackupSource(artifactPath);
    if (!backupBlockingArtifacts.isEmpty()) {
      return AttestedProtectedBookMaintenanceDecisions.rejectedRestore(
          new ProtectedBookMaintenanceRejection.BackupSourceHasBlockingArtifacts(
              artifactPath, backupBlockingArtifacts));
    }
    List<Path> bookBlockingArtifacts = store.blockingArtifactsForBook(bookPath);
    if (!bookBlockingArtifacts.isEmpty()) {
      return AttestedProtectedBookMaintenanceDecisions.rejectedRestore(
          new ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts(
              bookPath, bookBlockingArtifacts));
    }
    LeaseAcquisition sourceLease;
    try {
      sourceLease =
          store.acquireExistingArtifactLease(
              artifactPath, ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE);
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return AttestedProtectedBookMaintenanceDecisions.rejectedRestore(exception.rejection());
    } catch (RuntimeException exception) {
      return AttestedProtectedBookMaintenanceDecisions.failure(
          artifactPath, "backupFilePath", "Failed to reserve the selected backup artifact.");
    }
    if (sourceLease instanceof ProtectedBookMaintenanceStore.LeaseBusy busy) {
      return AttestedProtectedBookMaintenanceDecisions.rejectedRestore(
          new ProtectedBookMaintenanceRejection.ArtifactBusy(
              ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE, busy.artifactPath()));
    }
    HeldLease heldSourceLease = (HeldLease) sourceLease;
    try {
      return MaintenanceResourceScope.closeAfter(
          heldSourceLease::close,
          () -> {
            PreparedPairPublication publication;
            try {
              publication =
                  store.preparePairPublication(
                      newKeyPath,
                      bookPath,
                      RestoredBookTargetPolicy.REQUIRE_ABSENT,
                      ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
                      ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET);
            } catch (ProtectedBookMaintenanceRejectionException exception) {
              return AttestedProtectedBookMaintenanceDecisions.rejectedRestore(
                  exception.rejection());
            } catch (RuntimeException exception) {
              return AttestedProtectedBookMaintenanceDecisions.failure(
                  bookPath, "bookFilePath", "Failed to prepare restored-book publication.");
            }
            return MaintenanceResourceScope.closeAfter(
                publication::close,
                () -> {
                  AttestedProtectedBookMaintenanceStore.VerifiedBackupArtifact artifact =
                      store.verifyBackupArtifact(artifactPath, backupKeyPath);
                  return MaintenanceResourceScope.closeAfter(
                      artifact::close,
                      () ->
                          stageAndPublishRestore(
                              bookPath, newKeyPath, publication, artifact, signingSession));
                });
          });
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return AttestedProtectedBookMaintenanceDecisions.rejectedRestore(exception.rejection());
    } catch (AttestationAdmissionRejectedException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      return AttestedProtectedBookMaintenanceDecisions.failure(
          bookPath, "bookFilePath", "Failed to restore the attested backup artifact.");
    }
  }

  /** Rekeys one book only after writing its exact signed rekey operation into the staged copy. */
  public MaintenanceDecision<ProtectedBookRekeyOutcome> rekeyBook(
      ProtectedBookAccess bookAccess,
      Path newBookKeyFilePath,
      AttestationSigningSession signingSession) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(signingSession, "signingSession");
    Path bookPath = store.normalize(bookAccess.bookFilePath(), "bookFilePath");
    Path newKeyPath = store.normalize(newBookKeyFilePath, "newBookKeyFilePath");
    PreparedPairPublication publication;
    try {
      publication =
          store.preparePairPublication(
              newKeyPath,
              bookPath,
              RestoredBookTargetPolicy.REPLACE_SELECTED,
              ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
              ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET);
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return AttestedProtectedBookMaintenanceDecisions.rejectedRekey(exception.rejection());
    } catch (RuntimeException exception) {
      return AttestedProtectedBookMaintenanceDecisions.failure(
          newKeyPath, "newBookKeyFilePath", "Failed to prepare rekey publication.");
    }
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
                AttestedProtectedBookMaintenanceDecisions.requireVerifiedBook(store, bookAccess);
            return MaintenanceResourceScope.closeAfter(
                liveBook::close,
                () -> {
                  List<AttestationEvidence> evidence = store.loadAttestationEvidence(liveBook);
                  AttestationVerifier.verifyBook(evidence);
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

  private MaintenanceDecision<ProtectedBookRestoreOutcome> stageAndPublishRestore(
      Path bookPath,
      Path newKeyPath,
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
                            AttestationBackupAcknowledgement acknowledgement =
                                new AttestationBackupAcknowledgement(
                                    artifact.verification().backupId(),
                                    artifact.verification().artifactDigest(),
                                    artifact.verification().sourceOrder(),
                                    artifact.verification().sourceOperationHead());
                            AttestationVerification appendVerification =
                                store.appendAttestedOperation(
                                    restoredBook,
                                    RESTORE_BOOK_OPERATION,
                                    clock.instant(),
                                    AttestationLifecycleMutationProjection.restoreBook(
                                        RESTORE_BOOK_OPERATION.wireToken(), acknowledgement),
                                    signingSession,
                                    null);
                            stagedRestore.commit();
                            return MaintenanceDecision.accepted(
                                new ProtectedBookRestoreOutcome.Restored(
                                    bookPath,
                                    newKeyPath,
                                    AttestationCommitProjection.fromVerifiedAppend(
                                        appendVerification)));
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
                  AttestationVerification appendVerification =
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
                  stagedRekey.commit();
                  return MaintenanceDecision.accepted(
                      new ProtectedBookRekeyOutcome.Rekeyed(
                          bookPath,
                          newKeyPath,
                          AttestationCommitProjection.fromVerifiedAppend(appendVerification)));
                }
              },
              ignored ->
                  AttestedProtectedBookMaintenanceDecisions.failure(
                      bookPath, "bookFilePath", "Failed to verify staged rekey book."));
    }
  }
}
