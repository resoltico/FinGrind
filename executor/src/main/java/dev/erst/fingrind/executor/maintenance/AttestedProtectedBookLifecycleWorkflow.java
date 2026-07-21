package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailurePaths;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import dev.erst.fingrind.core.attestation.AttestationBackupArtifact;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationLifecycleMutationProjection;
import dev.erst.fingrind.core.attestation.AttestationLifecycleState;
import dev.erst.fingrind.core.attestation.AttestationSigningSession;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.core.attestation.AttestationVerifier;
import dev.erst.fingrind.executor.spi.AttestedProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.PreparedPairPublication;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Attested lifecycle owner for every protected-book mutation and every external backup artifact.
 *
 * <p>The workflow deliberately owns no private key material. Its supplied signing session can sign
 * only exact evidence produced after the relevant SQLite transaction has observed its current head.
 */
public final class AttestedProtectedBookLifecycleWorkflow {
  private final Clock clock;
  private final AttestedProtectedBookMaintenanceStore store;

  /** Creates one lifecycle workflow over an attested protected-book storage implementation. */
  public AttestedProtectedBookLifecycleWorkflow(Clock clock, ProtectedBookMaintenanceStore store) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.store = AttestedProtectedBookMaintenanceStore.require(store);
  }

  /** Stages, verifies, seals, publishes, and acknowledges one manifest-attested backup artifact. */
  public MaintenanceDecision<ProtectedBookBackupOutcome> backupBook(
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
      return rejectedBackup(exception.rejection());
    } catch (RuntimeException exception) {
      return failure(
          backupPath, "backupFilePath", "Failed to prepare backup artifact publication.");
    }
    try (publication) {
      List<Path> blocking = store.blockingArtifactsForBook(bookPath);
      if (!blocking.isEmpty()) {
        return rejectedBackup(
            new ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts(bookPath, blocking));
      }
      ProtectedBookMaintenanceStore.LeaseAcquisition lease =
          store.acquireManagedArtifactLease(
              bookPath, ProtectedBookMaintenanceArtifactRole.LIVE_BOOK);
      if (lease instanceof ProtectedBookMaintenanceStore.LeaseBusy busy) {
        return rejectedBackup(
            new ProtectedBookMaintenanceRejection.ArtifactBusy(
                ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, busy.artifactPath()));
      }
      try (ProtectedBookMaintenanceStore.HeldLease ignoredLease =
              (ProtectedBookMaintenanceStore.HeldLease) lease;
          ProtectedBookMaintenanceStore.VerifiedBook liveBook = requireVerifiedBook(bookAccess)) {
        return stagePublishAndAcknowledgeBackup(
            liveBook, bookPath, backupPath, backupKeyPath, publication, backupId, signingSession);
      }
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return rejectedBackup(exception.rejection());
    } catch (RuntimeException exception) {
      return failure(
          backupPath, "backupFilePath", "Failed to create the attested backup artifact.");
    }
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
      return rejectedRestore(
          new ProtectedBookMaintenanceRejection.BackupSourceMatchesLiveBook(
              bookPath, artifactPath));
    }
    if (!store.blockingArtifactsForBook(bookPath).isEmpty()) {
      return rejectedRestore(
          new ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts(
              bookPath, store.blockingArtifactsForBook(bookPath)));
    }
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
      return rejectedRestore(exception.rejection());
    } catch (RuntimeException exception) {
      return failure(bookPath, "bookFilePath", "Failed to prepare restored-book publication.");
    }
    try (publication) {
      ProtectedBookMaintenanceStore.LeaseAcquisition lease =
          store.acquireManagedArtifactLease(
              bookPath, ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET);
      if (lease instanceof ProtectedBookMaintenanceStore.LeaseBusy busy) {
        return rejectedRestore(
            new ProtectedBookMaintenanceRejection.ArtifactBusy(
                ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, busy.artifactPath()));
      }
      try (ProtectedBookMaintenanceStore.HeldLease ignoredLease =
              (ProtectedBookMaintenanceStore.HeldLease) lease;
          AttestedProtectedBookMaintenanceStore.VerifiedBackupArtifact artifact =
              store.verifyBackupArtifact(artifactPath, backupKeyPath)) {
        return stageAndPublishRestore(bookPath, newKeyPath, publication, artifact, signingSession);
      }
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return rejectedRestore(exception.rejection());
    } catch (RuntimeException exception) {
      return failure(bookPath, "bookFilePath", "Failed to restore the attested backup artifact.");
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
      return rejectedRekey(exception.rejection());
    } catch (RuntimeException exception) {
      return failure(newKeyPath, "newBookKeyFilePath", "Failed to prepare rekey publication.");
    }
    try (publication) {
      List<Path> blocking = store.blockingArtifactsForBook(bookPath);
      if (!blocking.isEmpty()) {
        return rejectedRekey(
            new ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts(bookPath, blocking));
      }
      ProtectedBookMaintenanceStore.LeaseAcquisition lease =
          store.acquireManagedArtifactLease(
              bookPath, ProtectedBookMaintenanceArtifactRole.LIVE_BOOK);
      if (lease instanceof ProtectedBookMaintenanceStore.LeaseBusy busy) {
        return rejectedRekey(
            new ProtectedBookMaintenanceRejection.ArtifactBusy(
                ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, busy.artifactPath()));
      }
      try (ProtectedBookMaintenanceStore.HeldLease ignoredLease =
              (ProtectedBookMaintenanceStore.HeldLease) lease;
          ProtectedBookMaintenanceStore.VerifiedBook liveBook = requireVerifiedBook(bookAccess)) {
        List<AttestationEvidence> evidence = store.loadAttestationEvidence(liveBook);
        AttestationVerifier.verifyBook(evidence);
        return store
            .stageRestoredBookPair(liveBook, publication)
            .fold(
                staged -> rekeyAndPublish(bookPath, newKeyPath, staged, evidence, signingSession),
                failure -> failure(newKeyPath, "newBookKeyFilePath", failure.message()));
      }
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return rejectedRekey(exception.rejection());
    } catch (RuntimeException exception) {
      return failure(bookPath, "bookFilePath", "Failed to stage the attested rekey operation.");
    }
  }

  private MaintenanceDecision<ProtectedBookBackupOutcome> stagePublishAndAcknowledgeBackup(
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
                            return rejectedBackup(
                                verificationFailed(
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
                            try {
                              store.appendAttestedOperation(
                                  liveBook,
                                  "backup-created",
                                  clock.instant(),
                                  AttestationLifecycleMutationProjection.backupCreated(
                                      acknowledgement),
                                  signingSession,
                                  acknowledgement);
                            } catch (BackupAcknowledgementConflictException exception) {
                              return rejectedBackup(
                                  new ProtectedBookMaintenanceRejection
                                      .BackupAcknowledgementConflict(acknowledgement.backupId()));
                            }
                            return MaintenanceDecision.accepted(
                                new ProtectedBookBackupOutcome.BackedUp(
                                    bookPath, backupPath, backupKeyPath));
                          }
                        },
                        ignored ->
                            failure(
                                backupPath,
                                "backupFilePath",
                                "Failed to verify staged backup snapshot."));
              }
            },
            failure -> failure(backupPath, "backupFilePath", failure.message()));
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
                            return rejectedRestore(
                                verificationFailed(
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
                            store.appendAttestedOperation(
                                restoredBook,
                                "restore-book",
                                clock.instant(),
                                AttestationLifecycleMutationProjection.restoreBook(acknowledgement),
                                signingSession,
                                null);
                            stagedRestore.commit();
                            return MaintenanceDecision.accepted(
                                new ProtectedBookRestoreOutcome.Restored(bookPath, newKeyPath));
                          }
                        },
                        ignored ->
                            failure(
                                bookPath,
                                "bookFilePath",
                                "Failed to verify staged restored book."));
              }
            },
            failure -> failure(bookPath, "bookFilePath", failure.message()));
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
                  return rejectedRekey(
                      verificationFailed(
                          ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET, failure));
                }
                try (ProtectedBookMaintenanceStore.VerifiedBook rekeyedBook =
                    (ProtectedBookMaintenanceStore.VerifiedBook) verification) {
                  Instant recordedAt = clock.instant();
                  store.appendAttestedOperation(
                      rekeyedBook,
                      "rekey-book",
                      recordedAt,
                      AttestationLifecycleMutationProjection.rekeyBook(
                          AttestationLifecycleState.nextKeyEpoch(sourceEvidence),
                          recordedAt,
                          java.util.Optional.empty()),
                      signingSession,
                      null);
                  stagedRekey.commit();
                  return MaintenanceDecision.accepted(
                      new ProtectedBookRekeyOutcome.Rekeyed(bookPath, newKeyPath));
                }
              },
              ignored -> failure(bookPath, "bookFilePath", "Failed to verify staged rekey book."));
    }
  }

  private ProtectedBookMaintenanceStore.VerifiedBook requireVerifiedBook(
      ProtectedBookAccess access) {
    ProtectedBookMaintenanceStore.BookVerification verification =
        store
            .verifyInitializedBook(access, ProtectedBookMaintenanceArtifactRole.LIVE_BOOK)
            .fold(
                value -> value,
                failure -> {
                  throw new IllegalStateException(failure.message());
                });
    if (verification instanceof ProtectedBookMaintenanceStore.VerifiedBook verifiedBook) {
      return verifiedBook;
    }
    ProtectedBookMaintenanceStore.VerificationFailure failure =
        (ProtectedBookMaintenanceStore.VerificationFailure) verification;
    throw new ProtectedBookMaintenanceRejectionException(
        verificationFailed(ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, failure));
  }

  private static ProtectedBookMaintenanceRejection.ArtifactVerificationFailed verificationFailed(
      ProtectedBookMaintenanceArtifactRole role,
      ProtectedBookMaintenanceStore.VerificationFailure failure) {
    return new ProtectedBookMaintenanceRejection.ArtifactVerificationFailed(
        role, failure.artifactPath(), failure.failure());
  }

  private static MaintenanceDecision<ProtectedBookBackupOutcome> rejectedBackup(
      ProtectedBookMaintenanceRejection rejection) {
    return MaintenanceDecision.accepted(new ProtectedBookBackupOutcome.Rejected(rejection));
  }

  private static MaintenanceDecision<ProtectedBookRestoreOutcome> rejectedRestore(
      ProtectedBookMaintenanceRejection rejection) {
    return MaintenanceDecision.accepted(new ProtectedBookRestoreOutcome.Rejected(rejection));
  }

  private static MaintenanceDecision<ProtectedBookRekeyOutcome> rejectedRekey(
      ProtectedBookMaintenanceRejection rejection) {
    return MaintenanceDecision.accepted(new ProtectedBookRekeyOutcome.Rejected(rejection));
  }

  private static <T> MaintenanceDecision<T> failure(Path path, String argument, String message) {
    return MaintenanceDecision.failed(
        new MaintenanceFailure(
            ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE,
            message,
            "Inspect the selected path and retry after resolving the underlying storage condition.",
            argument,
            ContractFailurePaths.primary(path)));
  }
}
