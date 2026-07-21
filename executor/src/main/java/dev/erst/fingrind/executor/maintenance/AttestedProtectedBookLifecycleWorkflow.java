package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailurePaths;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgementAdmission;
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
import java.util.Arrays;
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
  private static final String BACKUP_BOOK_OPERATION = OperationId.BACKUP_BOOK.wireName();
  private static final String RESTORE_BOOK_OPERATION = OperationId.RESTORE_BOOK.wireName();
  private static final String REKEY_BOOK_OPERATION = OperationId.REKEY_BOOK.wireName();
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
    UUID checkedBackupId = Objects.requireNonNull(backupId, "backupId");
    return switch (store.backupArtifactPairState(backupPath, backupKeyPath)) {
      case ABSENT ->
          createBackupArtifact(
              bookAccess, bookPath, backupPath, backupKeyPath, checkedBackupId, signingSession);
      case ARTIFACT_ONLY ->
          rejectedBackup(
              new ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists(backupPath));
      case KEY_ONLY ->
          rejectedBackup(new ProtectedBookMaintenanceRejection.SecretTargetOccupied(backupKeyPath));
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

  private MaintenanceDecision<ProtectedBookBackupOutcome> resumeBackupAcknowledgement(
      ProtectedBookAccess bookAccess,
      Path bookPath,
      Path backupPath,
      Path backupKeyPath,
      UUID backupId,
      AttestationSigningSession signingSession) {
    List<Path> blocking = store.blockingArtifactsForBook(bookPath);
    if (!blocking.isEmpty()) {
      return rejectedBackup(
          new ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts(bookPath, blocking));
    }
    ProtectedBookMaintenanceStore.LeaseAcquisition lease =
        store.acquireManagedArtifactLease(bookPath, ProtectedBookMaintenanceArtifactRole.LIVE_BOOK);
    if (lease instanceof ProtectedBookMaintenanceStore.LeaseBusy busy) {
      return rejectedBackup(
          new ProtectedBookMaintenanceRejection.ArtifactBusy(
              ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, busy.artifactPath()));
    }
    try (ProtectedBookMaintenanceStore.HeldLease ignoredLease =
            (ProtectedBookMaintenanceStore.HeldLease) lease;
        ProtectedBookMaintenanceStore.VerifiedBook liveBook = requireVerifiedBook(bookAccess);
        AttestedProtectedBookMaintenanceStore.VerifiedBackupArtifact artifact =
            store.verifyBackupArtifact(backupPath, backupKeyPath)) {
      if (!artifact.verification().backupId().equals(backupId)) {
        return rejectedBackup(
            new ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists(backupPath));
      }
      List<AttestationEvidence> liveEvidence = store.loadAttestationEvidence(liveBook);
      if (!artifactSourceIsLive(artifact.verification(), liveEvidence)) {
        return rejectedBackup(
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
      return acknowledgeBackup(
          liveBook, bookPath, backupPath, backupKeyPath, acknowledgement, signingSession, true);
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      return rejectedBackup(exception.rejection());
    } catch (RuntimeException exception) {
      return failure(backupPath, "backupFilePath", "Failed to resume the backup acknowledgement.");
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
                                RESTORE_BOOK_OPERATION,
                                clock.instant(),
                                AttestationLifecycleMutationProjection.restoreBook(
                                    RESTORE_BOOK_OPERATION, acknowledgement),
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

  private MaintenanceDecision<ProtectedBookBackupOutcome> acknowledgeBackup(
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
          rejectedBackup(
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
              BACKUP_BOOK_OPERATION,
              clock.instant(),
              AttestationLifecycleMutationProjection.backupBook(
                  BACKUP_BOOK_OPERATION, acknowledgement),
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
          yield rejectedBackup(
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

  private static boolean artifactSourceIsLive(
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
                      REKEY_BOOK_OPERATION,
                      recordedAt,
                      AttestationLifecycleMutationProjection.rekeyBook(
                          REKEY_BOOK_OPERATION,
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
