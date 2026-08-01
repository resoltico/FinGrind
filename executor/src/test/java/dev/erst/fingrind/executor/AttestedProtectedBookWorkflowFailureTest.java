package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureDetails;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.core.attestation.AttestationAdmissionRejectedException;
import dev.erst.fingrind.core.attestation.AttestationAuthorizationFailure;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import dev.erst.fingrind.core.attestation.AttestationLifecycleMutationProjection;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.core.attestation.AttestationVerifier;
import dev.erst.fingrind.executor.maintenance.AttestedProtectedBookLifecycleWorkflow;
import dev.erst.fingrind.executor.maintenance.BackupAcknowledgementConflictException;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookBackupOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRekeyOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRestoreOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.LeaseBusy;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.VerificationFailure;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.VerifiedBook;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationFailureOutcome;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies deterministic refusals and storage failures around attested lifecycle transitions. */
class AttestedProtectedBookWorkflowFailureTest {
  private static final Instant RECORDED_AT = Instant.parse("2026-07-21T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(RECORDED_AT, ZoneOffset.UTC);
  private static final UUID BACKUP_ID = UUID.fromString("018f0000-0000-7000-8000-000000000001");

  @TempDir Path temporaryDirectory;

  @BeforeEach
  void canonicalizeTemporaryDirectory() throws IOException {
    temporaryDirectory = temporaryDirectory.toRealPath();
  }

  @Test
  void rethrowsNewBackupManifestAuthorizationRefusalsBeforePublishingTheArtifact()
      throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    AttestationMaintenanceTestSupport.CredentialFixture mismatchedCredential =
        new AttestationMaintenanceTestSupport.CredentialFixture(
            new AttestationCredentialSource(
                dev.erst.fingrind.core.attestation.AttestationCustodian.FILE_PKCS8,
                UUID.fromString("10213243-5465-7687-98a9-babcbddcee00"),
                credential.source().encryptedKeyFilePath(),
                credential.source().passphraseFilePath()));
    ProtectedBookAccess access = access(credential);
    AttestationMaintenanceTestSupport.Store store = store(credential);

    AttestationAdmissionRejectedException rejected =
        assertThrows(
            AttestationAdmissionRejectedException.class,
            () -> {
              try (var session = mismatchedCredential.openSession()) {
                workflow(store)
                    .backupBook(access, backupPath(), backupKeyPath(), BACKUP_ID, session);
              }
            });

    assertEquals(AttestationAuthorizationFailure.KEY_PRINCIPAL_MISMATCH, rejected.failure());
    assertInstanceOf(
        ProtectedBookBackupOutcome.BackedUp.class, accepted(backup(store, access, credential)));
  }

  @Test
  void classifiesEveryBackupDestinationAndLiveBookAdmissionAlternative() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    ProtectedBookAccess access = access(credential);

    AttestationMaintenanceTestSupport.Store occupiedSecret = store(credential);
    occupiedSecret.setPairAdmissionFailure(
        new ProtectedBookMaintenanceRejectionException(
            new ProtectedBookMaintenanceRejection.SecretTargetOccupied(backupKeyPath())));
    assertBackupRejection(
        occupiedSecret,
        access,
        credential,
        ProtectedBookMaintenanceRejection.SecretTargetOccupied.class);

    AttestationMaintenanceTestSupport.Store blocked = store(credential);
    blocked.setLiveBlockingArtifacts(List.of(bookPath().resolveSibling("book.sqlite-wal")));
    assertBackupRejection(
        blocked,
        access,
        credential,
        ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts.class);

    AttestationMaintenanceTestSupport.Store busy = store(credential);
    busy.setManagedLease(new LeaseBusy(bookPath()));
    assertBackupRejection(
        busy, access, credential, ProtectedBookMaintenanceRejection.ArtifactBusy.class);

    AttestationMaintenanceTestSupport.Store invalidLive = store(credential);
    invalidLive.setLiveVerification(
        MaintenanceDecision.accepted(
            new VerificationFailure(bookPath(), ProtectedBookVerificationFailure.MISSING)));
    assertBackupRejection(
        invalidLive,
        access,
        credential,
        ProtectedBookMaintenanceRejection.ArtifactVerificationFailed.class);

    AttestationMaintenanceTestSupport.Store invalidSource = store(credential);
    ProtectedBookMaintenanceRejection.ArtifactPathInvalid sourceRejection =
        new ProtectedBookMaintenanceRejection.ArtifactPathInvalid(
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
            bookPath(),
            dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenancePathFailure
                .ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE);
    invalidSource.rejectExistingSourceNormalization(
        bookPath(), ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, sourceRejection);
    assertBackupRejection(
        invalidSource,
        access,
        credential,
        ProtectedBookMaintenanceRejection.ArtifactPathInvalid.class);
  }

  @Test
  void preservesCompletionUncertainBackupPairInsteadOfStartingAnotherPublication()
      throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    ProtectedBookAccess access = access(credential);
    AttestationMaintenanceTestSupport.Store interrupted = store(credential);
    interrupted.setInjectedPairAdmission(
        new ProtectedBookPairPublicationFailureOutcome.CompletionUncertain(
            backupPath(),
            ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN,
            backupKeyPath(),
            ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED,
            null));

    ContractFailureException failure =
        assertThrows(ContractFailureException.class, () -> backup(interrupted, access, credential));
    assertEquals(
        ContractErrors.Descriptor.PROTECTED_BOOK_PAIR_PUBLICATION_UNCERTAIN,
        failure.failure().descriptor());
    ContractFailureDetails.ProtectedBookPairPublicationUncertain details =
        assertInstanceOf(
            ContractFailureDetails.ProtectedBookPairPublicationUncertain.class,
            failure.failure().details());
    assertEquals(OperationId.BACKUP_BOOK, details.operation());
    assertEquals(backupPath(), details.pairPublication().bookTarget().path());
    assertEquals(
        ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN,
        details.pairPublication().bookTarget().state());
    assertEquals(backupKeyPath(), details.pairPublication().generatedSecretTarget().path());
    assertEquals(
        ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED,
        details.pairPublication().generatedSecretTarget().state());
    assertNull(details.pairPublication().recoveryRecordState());
  }

  @Test
  void separatesBackupPublicationRejectionsAndStorageFailures() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    ProtectedBookAccess access = access(credential);

    AttestationMaintenanceTestSupport.Store rejectedPublication = store(credential);
    rejectedPublication.setPairAdmissionFailure(
        new ProtectedBookMaintenanceRejectionException(
            new ProtectedBookMaintenanceRejection.SecretTargetOccupied(backupKeyPath())));
    assertBackupRejection(
        rejectedPublication,
        access,
        credential,
        ProtectedBookMaintenanceRejection.SecretTargetOccupied.class);

    AttestationMaintenanceTestSupport.Store failedPublication = store(credential);
    failedPublication.setPairAdmissionFailure(new IllegalStateException("staging unavailable"));
    assertInstanceOf(
        MaintenanceDecision.Failed.class, backup(failedPublication, access, credential));

    AttestationMaintenanceTestSupport.Store invalidSnapshot = store(credential);
    invalidSnapshot.setStagedBackupVerification(
        MaintenanceDecision.accepted(
            new VerificationFailure(backupPath(), ProtectedBookVerificationFailure.MISSING)));
    assertBackupRejection(
        invalidSnapshot,
        access,
        credential,
        ProtectedBookMaintenanceRejection.ArtifactVerificationFailed.class);

    AttestationMaintenanceTestSupport.Store failedStaging = store(credential);
    failedStaging.setStagedBackup(MaintenanceDecision.failed(storageFailure()));
    assertInstanceOf(MaintenanceDecision.Failed.class, backup(failedStaging, access, credential));

    AttestationMaintenanceTestSupport.Store failedStagedVerification = store(credential);
    failedStagedVerification.setStagedBackupVerification(
        MaintenanceDecision.failed(storageFailure()));
    assertInstanceOf(
        MaintenanceDecision.Failed.class, backup(failedStagedVerification, access, credential));

    AttestationMaintenanceTestSupport.Store malformedSnapshot = store(credential);
    malformedSnapshot.setSnapshotEvidence(List.of());
    ProtectedBookBackupOutcome.Rejected malformedSnapshotRejected =
        assertInstanceOf(
            ProtectedBookBackupOutcome.Rejected.class,
            accepted(backup(malformedSnapshot, access, credential)));
    assertHistoricalLiveVerificationFailure(malformedSnapshotRejected.rejection(), bookPath());

    AttestationMaintenanceTestSupport.Store failedStageExecution = store(credential);
    failedStageExecution
        .overrides()
        .stagedBackupFailure(new IllegalStateException("backup staging unavailable"));
    assertInstanceOf(
        MaintenanceDecision.Failed.class, backup(failedStageExecution, access, credential));
  }

  @Test
  void protectsAcknowledgementResumeFromMismatchedIdentifiersAndSourceChains() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    ProtectedBookAccess access = access(credential);

    AttestationMaintenanceTestSupport.Store changedIdentifier = store(credential);
    assertInstanceOf(
        ProtectedBookBackupOutcome.BackedUp.class,
        accepted(backup(changedIdentifier, access, credential)));
    assertBackupRejection(
        changedIdentifier,
        access,
        credential,
        UUID.fromString("018f0000-0000-7000-8000-000000000002"),
        ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists.class);

    AttestationMaintenanceTestSupport.Store changedSource = store(credential);
    assertInstanceOf(
        ProtectedBookBackupOutcome.BackedUp.class,
        accepted(backup(changedSource, access, credential)));
    changedSource.setLiveEvidence(
        List.of(AttestationMaintenanceTestSupport.genesis(credential, RECORDED_AT)));
    assertBackupRejection(
        changedSource,
        access,
        credential,
        BACKUP_ID,
        ProtectedBookMaintenanceRejection.ArtifactVerificationFailed.class);

    AttestationMaintenanceTestSupport.Store missingSource = store(credential);
    assertInstanceOf(
        ProtectedBookBackupOutcome.BackedUp.class,
        accepted(backup(missingSource, access, credential)));
    missingSource.setLiveEvidence(List.of());
    assertBackupRejection(
        missingSource,
        access,
        credential,
        BACKUP_ID,
        ProtectedBookMaintenanceRejection.ArtifactVerificationFailed.class);

    AttestationMaintenanceTestSupport.Store malformedResumedSource = store(credential);
    assertInstanceOf(
        ProtectedBookBackupOutcome.BackedUp.class,
        accepted(backup(malformedResumedSource, access, credential)));
    malformedResumedSource.setLiveEvidence(
        List.of(
            new dev.erst.fingrind.core.attestation.AttestationEvidence(
                new byte[0], new byte[0], new byte[0])));
    ProtectedBookBackupOutcome.Rejected malformedResumedSourceRejected =
        assertInstanceOf(
            ProtectedBookBackupOutcome.Rejected.class,
            accepted(backup(malformedResumedSource, access, credential)));
    assertHistoricalLiveVerificationFailure(malformedResumedSourceRejected.rejection(), bookPath());

    AttestationMaintenanceTestSupport.Store conflict = store(credential);
    conflict.overrides().appendFailure(new BackupAcknowledgementConflictException(BACKUP_ID));
    assertBackupRejection(
        conflict,
        access,
        credential,
        ProtectedBookMaintenanceRejection.BackupAcknowledgementConflict.class);

    AttestationMaintenanceTestSupport.Store conflictingAcknowledgement = store(credential);
    var genesisVerification =
        AttestationVerifier.verifyBook(conflictingAcknowledgement.liveEvidence());
    AttestationBackupAcknowledgement retainedAcknowledgement =
        new AttestationBackupAcknowledgement(
            BACKUP_ID,
            new byte[32],
            genesisVerification.headOrder(),
            genesisVerification.operationHead());
    try (var session = credential.openSession()) {
      try (VerifiedBook liveBook =
          (VerifiedBook)
              accepted(
                  conflictingAcknowledgement.verifyInitializedBook(
                      access, ProtectedBookMaintenanceArtifactRole.LIVE_BOOK))) {
        conflictingAcknowledgement.appendAttestedOperation(
            liveBook,
            AttestationOperationKind.BACKUP_CREATED,
            CLOCK.instant(),
            AttestationLifecycleMutationProjection.backupBook(
                AttestationOperationKind.BACKUP_CREATED.wireToken(), retainedAcknowledgement),
            session,
            retainedAcknowledgement);
      }
      ProtectedBookBackupOutcome.Rejected rejected =
          assertInstanceOf(
              ProtectedBookBackupOutcome.Rejected.class,
              accepted(
                  workflow(conflictingAcknowledgement)
                      .backupBook(access, backupPath(), backupKeyPath(), BACKUP_ID, session)));
      assertInstanceOf(
          ProtectedBookMaintenanceRejection.BackupAcknowledgementConflict.class,
          rejected.rejection());
    }
  }

  @Test
  void classifiesEveryResumePreconditionBeforeOpeningTheBackupArtifact() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    ProtectedBookAccess access = access(credential);

    AttestationMaintenanceTestSupport.Store blocked = backedUpStore(credential, access);
    blocked.setLiveBlockingArtifacts(List.of(bookPath().resolveSibling("book.sqlite-wal")));
    assertBackupRejection(
        blocked,
        access,
        credential,
        ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts.class);

    AttestationMaintenanceTestSupport.Store busy = backedUpStore(credential, access);
    busy.setManagedLease(new LeaseBusy(bookPath()));
    assertBackupRejection(
        busy, access, credential, ProtectedBookMaintenanceRejection.ArtifactBusy.class);

    AttestationMaintenanceTestSupport.Store invalidLive = backedUpStore(credential, access);
    invalidLive.setLiveVerification(
        MaintenanceDecision.accepted(
            new VerificationFailure(bookPath(), ProtectedBookVerificationFailure.MISSING)));
    assertBackupRejection(
        invalidLive,
        access,
        credential,
        ProtectedBookMaintenanceRejection.ArtifactVerificationFailed.class);

    AttestationMaintenanceTestSupport.Store unreadableArtifact = backedUpStore(credential, access);
    unreadableArtifact
        .overrides()
        .backupArtifactVerificationFailure(
            new IllegalStateException("artifact verification unavailable"));
    assertInstanceOf(
        MaintenanceDecision.Failed.class, backup(unreadableArtifact, access, credential));

    AttestationMaintenanceTestSupport.Store failedLiveVerification = store(credential);
    failedLiveVerification.setLiveVerification(MaintenanceDecision.failed(storageFailure()));
    ContractFailureException failure =
        assertThrows(
            ContractFailureException.class,
            () -> backup(failedLiveVerification, access, credential));
    assertEquals(ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE, failure.failure().descriptor());
    assertEquals("simulated storage failure", failure.getMessage());
  }

  @Test
  void classifiesRestoreAndRekeyStagingVerificationFailures() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    ProtectedBookAccess access = access(credential);

    AttestationMaintenanceTestSupport.Store restoreStore = store(credential);
    assertInstanceOf(
        ProtectedBookBackupOutcome.BackedUp.class,
        accepted(backup(restoreStore, access, credential)));
    restoreStore
        .overrides()
        .stagedRestoreVerification(
            MaintenanceDecision.accepted(
                new VerificationFailure(bookPath(), ProtectedBookVerificationFailure.MISSING)));
    try (var session = credential.openSession()) {
      ProtectedBookRestoreOutcome.Rejected rejected =
          assertInstanceOf(
              ProtectedBookRestoreOutcome.Rejected.class,
              accepted(
                  workflow(restoreStore)
                      .restoreBook(
                          temporaryDirectory.resolve("restored/book.sqlite"),
                          temporaryDirectory.resolve("restored/book.key"),
                          backupPath(),
                          backupKeyPath(),
                          session)));
      assertInstanceOf(
          ProtectedBookMaintenanceRejection.ArtifactVerificationFailed.class, rejected.rejection());
    }

    AttestationMaintenanceTestSupport.Store rekeyStore = store(credential);
    rekeyStore
        .overrides()
        .stagedRestoreVerification(
            MaintenanceDecision.accepted(
                new VerificationFailure(bookPath(), ProtectedBookVerificationFailure.MISSING)));
    try (var session = credential.openSession()) {
      ProtectedBookRekeyOutcome.Rejected rejected =
          assertInstanceOf(
              ProtectedBookRekeyOutcome.Rejected.class,
              accepted(workflow(rekeyStore).rekeyBook(access, rekeyPath(), session)));
      assertInstanceOf(
          ProtectedBookMaintenanceRejection.ArtifactVerificationFailed.class, rejected.rejection());
    }
  }

  @Test
  void mapsRestoreAndRekeyPublicationAndStagingFailuresToLocalFailures() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    ProtectedBookAccess access = access(credential);

    AttestationMaintenanceTestSupport.Store rejectedRestorePublication =
        backedUpStore(credential, access);
    rejectedRestorePublication.setPairAdmissionFailure(
        new ProtectedBookMaintenanceRejectionException(
            new ProtectedBookMaintenanceRejection.BookDestinationOccupied(bookPath())));
    try (var session = credential.openSession()) {
      ProtectedBookRestoreOutcome.Rejected rejected =
          assertInstanceOf(
              ProtectedBookRestoreOutcome.Rejected.class,
              accepted(
                  workflow(rejectedRestorePublication)
                      .restoreBook(
                          temporaryDirectory.resolve("restored/book.sqlite"),
                          temporaryDirectory.resolve("restored/book.key"),
                          backupPath(),
                          backupKeyPath(),
                          session)));
      assertInstanceOf(
          ProtectedBookMaintenanceRejection.BookDestinationOccupied.class, rejected.rejection());
    }

    AttestationMaintenanceTestSupport.Store failedRestorePublication =
        backedUpStore(credential, access);
    failedRestorePublication.setPairAdmissionFailure(
        new IllegalStateException("restore staging unavailable"));
    try (var session = credential.openSession()) {
      assertInstanceOf(
          MaintenanceDecision.Failed.class,
          workflow(failedRestorePublication)
              .restoreBook(
                  temporaryDirectory.resolve("restored/book.sqlite"),
                  temporaryDirectory.resolve("restored/book.key"),
                  backupPath(),
                  backupKeyPath(),
                  session));
    }

    AttestationMaintenanceTestSupport.Store failedRestoreStaging = store(credential);
    assertInstanceOf(
        ProtectedBookBackupOutcome.BackedUp.class,
        accepted(backup(failedRestoreStaging, access, credential)));
    failedRestoreStaging.setStagedRestore(MaintenanceDecision.failed(storageFailure()));
    try (var session = credential.openSession()) {
      assertInstanceOf(
          MaintenanceDecision.Failed.class,
          workflow(failedRestoreStaging)
              .restoreBook(
                  temporaryDirectory.resolve("restored/book.sqlite"),
                  temporaryDirectory.resolve("restored/book.key"),
                  backupPath(),
                  backupKeyPath(),
                  session));
    }

    AttestationMaintenanceTestSupport.Store failedRekeyPublication = store(credential);
    failedRekeyPublication.setPairAdmissionFailure(
        new IllegalStateException("rekey staging unavailable"));
    try (var session = credential.openSession()) {
      assertInstanceOf(
          MaintenanceDecision.Failed.class,
          workflow(failedRekeyPublication).rekeyBook(access, rekeyPath(), session));
    }

    AttestationMaintenanceTestSupport.Store failedRekeyStaging = store(credential);
    failedRekeyStaging.setStagedRestore(MaintenanceDecision.failed(storageFailure()));
    try (var session = credential.openSession()) {
      assertInstanceOf(
          MaintenanceDecision.Failed.class,
          workflow(failedRekeyStaging).rekeyBook(access, rekeyPath(), session));
    }
  }

  @Test
  void classifiesRestoreLeaseAndArtifactVerificationBoundaryFailures() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    ProtectedBookAccess access = access(credential);

    AttestationMaintenanceTestSupport.Store blockedRestore = backedUpStore(credential, access);
    blockedRestore.setLiveBlockingArtifacts(List.of(bookPath().resolveSibling("book.sqlite-wal")));
    try (var session = credential.openSession()) {
      ProtectedBookRestoreOutcome.Rejected rejected =
          assertInstanceOf(
              ProtectedBookRestoreOutcome.Rejected.class,
              accepted(
                  workflow(blockedRestore)
                      .restoreBook(
                          temporaryDirectory.resolve("restored/book.sqlite"),
                          temporaryDirectory.resolve("restored/book.key"),
                          backupPath(),
                          backupKeyPath(),
                          session)));
      assertInstanceOf(
          ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts.class, rejected.rejection());
    }

    AttestationMaintenanceTestSupport.Store rejectedLease = store(credential);
    rejectedLease
        .overrides()
        .workflowScopeAcquisitionFailure(
            new ProtectedBookMaintenanceRejectionException(
                new ProtectedBookMaintenanceRejection.ArtifactBusy(
                    ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE, backupPath())));
    try (var session = credential.openSession()) {
      ProtectedBookRestoreOutcome.Rejected rejected =
          assertInstanceOf(
              ProtectedBookRestoreOutcome.Rejected.class,
              accepted(
                  workflow(rejectedLease)
                      .restoreBook(
                          temporaryDirectory.resolve("restored/book.sqlite"),
                          temporaryDirectory.resolve("restored/book.key"),
                          backupPath(),
                          backupKeyPath(),
                          session)));
      assertInstanceOf(ProtectedBookMaintenanceRejection.ArtifactBusy.class, rejected.rejection());
    }

    AttestationMaintenanceTestSupport.Store failedLease = store(credential);
    failedLease
        .overrides()
        .workflowScopeAcquisitionFailure(new IllegalStateException("lease service unavailable"));
    try (var session = credential.openSession()) {
      assertInstanceOf(
          MaintenanceDecision.Failed.class,
          workflow(failedLease)
              .restoreBook(
                  temporaryDirectory.resolve("restored/book.sqlite"),
                  temporaryDirectory.resolve("restored/book.key"),
                  backupPath(),
                  backupKeyPath(),
                  session));
    }

    AttestationMaintenanceTestSupport.Store failedArtifactVerification =
        backedUpStore(credential, access);
    failedArtifactVerification
        .overrides()
        .backupArtifactVerificationFailure(
            new IllegalStateException("artifact verification unavailable"));
    try (var session = credential.openSession()) {
      assertInstanceOf(
          MaintenanceDecision.Failed.class,
          workflow(failedArtifactVerification)
              .restoreBook(
                  temporaryDirectory.resolve("restored/book.sqlite"),
                  temporaryDirectory.resolve("restored/book.key"),
                  backupPath(),
                  backupKeyPath(),
                  session));
    }

    AttestationMaintenanceTestSupport.Store rejectedArtifactVerification =
        backedUpStore(credential, access);
    rejectedArtifactVerification
        .overrides()
        .backupArtifactVerificationFailure(
            new ProtectedBookMaintenanceRejectionException(
                new ProtectedBookMaintenanceRejection.ArtifactVerificationFailed(
                    ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE,
                    backupPath(),
                    ProtectedBookVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED)));
    try (var session = credential.openSession()) {
      ProtectedBookRestoreOutcome.Rejected rejected =
          assertInstanceOf(
              ProtectedBookRestoreOutcome.Rejected.class,
              accepted(
                  workflow(rejectedArtifactVerification)
                      .restoreBook(
                          temporaryDirectory.resolve("restored/book.sqlite"),
                          temporaryDirectory.resolve("restored/book.key"),
                          backupPath(),
                          backupKeyPath(),
                          session)));
      assertInstanceOf(
          ProtectedBookMaintenanceRejection.ArtifactVerificationFailed.class, rejected.rejection());
    }
  }

  @Test
  void classifiesRestoreAndRekeyAppendPublicationAndLiveVerificationRejections()
      throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    ProtectedBookAccess access = access(credential);

    AttestationMaintenanceTestSupport.Store restoreAppendRejection = store(credential);
    assertInstanceOf(
        ProtectedBookBackupOutcome.BackedUp.class,
        accepted(backup(restoreAppendRejection, access, credential)));
    restoreAppendRejection
        .overrides()
        .appendFailure(
            new ProtectedBookMaintenanceRejectionException(
                new ProtectedBookMaintenanceRejection.ArtifactBusy(
                    ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, bookPath())));
    try (var session = credential.openSession()) {
      ProtectedBookRestoreOutcome.Rejected rejected =
          assertInstanceOf(
              ProtectedBookRestoreOutcome.Rejected.class,
              accepted(
                  workflow(restoreAppendRejection)
                      .restoreBook(
                          temporaryDirectory.resolve("restore-append-rejection/book.sqlite"),
                          temporaryDirectory.resolve("restore-append-rejection/book.key"),
                          backupPath(),
                          backupKeyPath(),
                          session)));
      assertInstanceOf(ProtectedBookMaintenanceRejection.ArtifactBusy.class, rejected.rejection());
    }

    AttestationMaintenanceTestSupport.Store rekeyAppendRejection = store(credential);
    rekeyAppendRejection
        .overrides()
        .appendFailure(
            new ProtectedBookMaintenanceRejectionException(
                new ProtectedBookMaintenanceRejection.ArtifactBusy(
                    ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, bookPath())));
    try (var session = credential.openSession()) {
      ProtectedBookRekeyOutcome.Rejected rejected =
          assertInstanceOf(
              ProtectedBookRekeyOutcome.Rejected.class,
              accepted(workflow(rekeyAppendRejection).rekeyBook(access, rekeyPath(), session)));
      assertInstanceOf(ProtectedBookMaintenanceRejection.ArtifactBusy.class, rejected.rejection());
    }

    AttestationMaintenanceTestSupport.Store resumeArtifactRejection = store(credential);
    assertInstanceOf(
        ProtectedBookBackupOutcome.BackedUp.class,
        accepted(backup(resumeArtifactRejection, access, credential)));
    resumeArtifactRejection
        .overrides()
        .backupArtifactVerificationFailure(
            new ProtectedBookMaintenanceRejectionException(
                new ProtectedBookMaintenanceRejection.ArtifactVerificationFailed(
                    ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE,
                    backupPath(),
                    ProtectedBookVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED)));
    assertBackupRejection(
        resumeArtifactRejection,
        access,
        credential,
        ProtectedBookMaintenanceRejection.ArtifactVerificationFailed.class);

    AttestationMaintenanceTestSupport.Store rejectedRekeyPublication = store(credential);
    rejectedRekeyPublication.setPairAdmissionFailure(
        new ProtectedBookMaintenanceRejectionException(
            new ProtectedBookMaintenanceRejection.SecretTargetOccupied(rekeyPath())));
    try (var session = credential.openSession()) {
      ProtectedBookRekeyOutcome.Rejected rejected =
          assertInstanceOf(
              ProtectedBookRekeyOutcome.Rejected.class,
              accepted(workflow(rejectedRekeyPublication).rekeyBook(access, rekeyPath(), session)));
      assertInstanceOf(
          ProtectedBookMaintenanceRejection.SecretTargetOccupied.class, rejected.rejection());
    }

    AttestationMaintenanceTestSupport.Store rejectedLiveVerification = store(credential);
    rejectedLiveVerification.setLiveVerification(
        MaintenanceDecision.accepted(
            new VerificationFailure(bookPath(), ProtectedBookVerificationFailure.MISSING)));
    try (var session = credential.openSession()) {
      ProtectedBookRekeyOutcome.Rejected rejected =
          assertInstanceOf(
              ProtectedBookRekeyOutcome.Rejected.class,
              accepted(workflow(rejectedLiveVerification).rekeyBook(access, rekeyPath(), session)));
      assertInstanceOf(
          ProtectedBookMaintenanceRejection.ArtifactVerificationFailed.class, rejected.rejection());
    }
  }

  @Test
  void classifiesStagedRestoreAndRekeyVerificationAndMalformedLiveEvidence() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    ProtectedBookAccess access = access(credential);

    AttestationMaintenanceTestSupport.Store failedRestoreVerification = store(credential);
    assertInstanceOf(
        ProtectedBookBackupOutcome.BackedUp.class,
        accepted(backup(failedRestoreVerification, access, credential)));
    failedRestoreVerification
        .overrides()
        .stagedRestoreVerification(MaintenanceDecision.failed(storageFailure()));
    try (var session = credential.openSession()) {
      assertInstanceOf(
          MaintenanceDecision.Failed.class,
          workflow(failedRestoreVerification)
              .restoreBook(
                  temporaryDirectory.resolve("restored/book.sqlite"),
                  temporaryDirectory.resolve("restored/book.key"),
                  backupPath(),
                  backupKeyPath(),
                  session));
    }

    AttestationMaintenanceTestSupport.Store failedRekeyVerification = store(credential);
    failedRekeyVerification
        .overrides()
        .stagedRestoreVerification(MaintenanceDecision.failed(storageFailure()));
    try (var session = credential.openSession()) {
      assertInstanceOf(
          MaintenanceDecision.Failed.class,
          workflow(failedRekeyVerification).rekeyBook(access, rekeyPath(), session));
    }

    AttestationMaintenanceTestSupport.Store malformedLiveEvidence = store(credential);
    malformedLiveEvidence.setLiveEvidence(List.of());
    try (var session = credential.openSession()) {
      ProtectedBookRekeyOutcome.Rejected rejected =
          assertInstanceOf(
              ProtectedBookRekeyOutcome.Rejected.class,
              accepted(workflow(malformedLiveEvidence).rekeyBook(access, rekeyPath(), session)));
      ProtectedBookMaintenanceRejection.ArtifactVerificationFailed failure =
          assertInstanceOf(
              ProtectedBookMaintenanceRejection.ArtifactVerificationFailed.class,
              rejected.rejection());
      assertEquals(ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, failure.artifactRole());
      assertEquals(bookPath(), failure.artifactPath());
      assertEquals(
          ProtectedBookVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED,
          failure.verificationFailure());
    }
  }

  private void assertBackupRejection(
      AttestationMaintenanceTestSupport.Store store,
      ProtectedBookAccess access,
      AttestationMaintenanceTestSupport.CredentialFixture credential,
      Class<? extends ProtectedBookMaintenanceRejection> rejectionType)
      throws IOException {
    assertBackupRejection(store, access, credential, BACKUP_ID, rejectionType);
  }

  private void assertBackupRejection(
      AttestationMaintenanceTestSupport.Store store,
      ProtectedBookAccess access,
      AttestationMaintenanceTestSupport.CredentialFixture credential,
      UUID backupId,
      Class<? extends ProtectedBookMaintenanceRejection> rejectionType)
      throws IOException {
    ProtectedBookBackupOutcome.Rejected rejected =
        assertInstanceOf(
            ProtectedBookBackupOutcome.Rejected.class,
            accepted(backup(store, access, credential, backupId)));
    assertInstanceOf(rejectionType, rejected.rejection());
  }

  private static void assertHistoricalLiveVerificationFailure(
      ProtectedBookMaintenanceRejection rejection, Path bookPath) {
    ProtectedBookMaintenanceRejection.ArtifactVerificationFailed failure =
        assertInstanceOf(
            ProtectedBookMaintenanceRejection.ArtifactVerificationFailed.class, rejection);
    assertEquals(ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, failure.artifactRole());
    assertEquals(bookPath, failure.artifactPath());
    assertEquals(
        ProtectedBookVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED,
        failure.verificationFailure());
  }

  private MaintenanceDecision<ProtectedBookBackupOutcome> backup(
      AttestationMaintenanceTestSupport.Store store,
      ProtectedBookAccess access,
      AttestationMaintenanceTestSupport.CredentialFixture credential)
      throws IOException {
    return backup(store, access, credential, BACKUP_ID);
  }

  private MaintenanceDecision<ProtectedBookBackupOutcome> backup(
      AttestationMaintenanceTestSupport.Store store,
      ProtectedBookAccess access,
      AttestationMaintenanceTestSupport.CredentialFixture credential,
      UUID backupId)
      throws IOException {
    try (var session = credential.openSession()) {
      return workflow(store).backupBook(access, backupPath(), backupKeyPath(), backupId, session);
    }
  }

  private AttestedProtectedBookLifecycleWorkflow workflow(
      AttestationMaintenanceTestSupport.Store store) {
    return new AttestedProtectedBookLifecycleWorkflow(CLOCK, store);
  }

  private AttestationMaintenanceTestSupport.Store store(
      AttestationMaintenanceTestSupport.CredentialFixture credential) {
    return new AttestationMaintenanceTestSupport.Store(
        bookPath(), List.of(AttestationMaintenanceTestSupport.genesis(credential, RECORDED_AT)));
  }

  private AttestationMaintenanceTestSupport.Store backedUpStore(
      AttestationMaintenanceTestSupport.CredentialFixture credential, ProtectedBookAccess access)
      throws IOException {
    AttestationMaintenanceTestSupport.Store store = store(credential);
    assertInstanceOf(
        ProtectedBookBackupOutcome.BackedUp.class, accepted(backup(store, access, credential)));
    return store;
  }

  private ProtectedBookAccess access(
      AttestationMaintenanceTestSupport.CredentialFixture credential) {
    return ProtectedBookAccess.fromPublished(
        AttestationMaintenanceTestSupport.bookAccess(bookPath(), credential));
  }

  private AttestationMaintenanceTestSupport.CredentialFixture credential() throws IOException {
    return AttestationMaintenanceTestSupport.createCredential(temporaryDirectory);
  }

  private Path bookPath() {
    return temporaryDirectory.resolve("live/book.sqlite");
  }

  private Path backupPath() {
    return temporaryDirectory.resolve("retained/book.fgba");
  }

  private Path backupKeyPath() {
    return temporaryDirectory.resolve("retained/book.key");
  }

  private Path rekeyPath() {
    return temporaryDirectory.resolve("rekeyed/book.key");
  }

  private static MaintenanceFailure storageFailure() {
    return new MaintenanceFailure(
        ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE,
        "simulated storage failure",
        null,
        null,
        null,
        null);
  }

  private static <T> T accepted(MaintenanceDecision<T> decision) {
    return decision.fold(
        value -> value,
        failure -> {
          throw new AssertionError(failure.message());
        });
  }
}
