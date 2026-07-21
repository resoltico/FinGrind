package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.runtime.ContractErrors;
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
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.BackupArtifactPairState;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.LeaseBusy;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.VerificationFailure;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies deterministic refusals and storage failures around attested lifecycle transitions. */
class AttestedProtectedBookWorkflowFailureTest {
  private static final Instant RECORDED_AT = Instant.parse("2026-07-21T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(RECORDED_AT, ZoneOffset.UTC);
  private static final UUID BACKUP_ID = UUID.fromString("018f0000-0000-7000-8000-000000000001");

  @TempDir Path temporaryDirectory;

  @Test
  void classifiesEveryBackupDestinationAndLiveBookAdmissionAlternative() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    ProtectedBookAccess access = access(credential);

    assertBackupRejection(
        store(credential, BackupArtifactPairState.KEY_ONLY),
        access,
        credential,
        ProtectedBookMaintenanceRejection.SecretTargetOccupied.class);

    AttestationMaintenanceTestSupport.Store blocked =
        store(credential, BackupArtifactPairState.ABSENT);
    blocked.setLiveBlockingArtifacts(List.of(bookPath().resolveSibling("book.sqlite-wal")));
    assertBackupRejection(
        blocked,
        access,
        credential,
        ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts.class);

    AttestationMaintenanceTestSupport.Store busy =
        store(credential, BackupArtifactPairState.ABSENT);
    busy.setManagedLease(new LeaseBusy(bookPath()));
    assertBackupRejection(
        busy, access, credential, ProtectedBookMaintenanceRejection.ArtifactBusy.class);

    AttestationMaintenanceTestSupport.Store invalidLive =
        store(credential, BackupArtifactPairState.ABSENT);
    invalidLive.setLiveVerification(
        MaintenanceDecision.accepted(
            new VerificationFailure(bookPath(), ProtectedBookVerificationFailure.MISSING)));
    assertBackupRejection(
        invalidLive,
        access,
        credential,
        ProtectedBookMaintenanceRejection.ArtifactVerificationFailed.class);
  }

  @Test
  void separatesBackupPublicationRejectionsAndStorageFailures() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    ProtectedBookAccess access = access(credential);

    AttestationMaintenanceTestSupport.Store rejectedPublication =
        store(credential, BackupArtifactPairState.ABSENT);
    rejectedPublication.setPrepareFailure(
        new ProtectedBookMaintenanceRejectionException(
            new ProtectedBookMaintenanceRejection.SecretTargetOccupied(backupKeyPath())));
    assertBackupRejection(
        rejectedPublication,
        access,
        credential,
        ProtectedBookMaintenanceRejection.SecretTargetOccupied.class);

    AttestationMaintenanceTestSupport.Store failedPublication =
        store(credential, BackupArtifactPairState.ABSENT);
    failedPublication.setPrepareFailure(new IllegalStateException("staging unavailable"));
    assertInstanceOf(
        MaintenanceDecision.Failed.class, backup(failedPublication, access, credential));

    AttestationMaintenanceTestSupport.Store invalidSnapshot =
        store(credential, BackupArtifactPairState.ABSENT);
    invalidSnapshot.setStagedBackupVerification(
        MaintenanceDecision.accepted(
            new VerificationFailure(backupPath(), ProtectedBookVerificationFailure.MISSING)));
    assertBackupRejection(
        invalidSnapshot,
        access,
        credential,
        ProtectedBookMaintenanceRejection.ArtifactVerificationFailed.class);

    AttestationMaintenanceTestSupport.Store failedStaging =
        store(credential, BackupArtifactPairState.ABSENT);
    failedStaging.setStagedBackup(MaintenanceDecision.failed(storageFailure()));
    assertInstanceOf(MaintenanceDecision.Failed.class, backup(failedStaging, access, credential));

    AttestationMaintenanceTestSupport.Store failedStagedVerification =
        store(credential, BackupArtifactPairState.ABSENT);
    failedStagedVerification.setStagedBackupVerification(
        MaintenanceDecision.failed(storageFailure()));
    assertInstanceOf(
        MaintenanceDecision.Failed.class, backup(failedStagedVerification, access, credential));

    AttestationMaintenanceTestSupport.Store malformedSnapshot =
        store(credential, BackupArtifactPairState.ABSENT);
    malformedSnapshot.setSnapshotEvidence(List.of());
    assertInstanceOf(
        MaintenanceDecision.Failed.class, backup(malformedSnapshot, access, credential));

    AttestationMaintenanceTestSupport.Store failedStageExecution =
        store(credential, BackupArtifactPairState.ABSENT);
    failedStageExecution.setStagedBackupFailure(
        new IllegalStateException("backup staging unavailable"));
    assertInstanceOf(
        MaintenanceDecision.Failed.class, backup(failedStageExecution, access, credential));
  }

  @Test
  void protectsAcknowledgementResumeFromMismatchedIdentifiersAndSourceChains() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    ProtectedBookAccess access = access(credential);

    AttestationMaintenanceTestSupport.Store changedIdentifier =
        store(credential, BackupArtifactPairState.ABSENT);
    assertInstanceOf(
        ProtectedBookBackupOutcome.BackedUp.class,
        accepted(backup(changedIdentifier, access, credential)));
    assertBackupRejection(
        changedIdentifier,
        access,
        credential,
        UUID.fromString("018f0000-0000-7000-8000-000000000002"),
        ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists.class);

    AttestationMaintenanceTestSupport.Store changedSource =
        store(credential, BackupArtifactPairState.ABSENT);
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

    AttestationMaintenanceTestSupport.Store missingSource =
        store(credential, BackupArtifactPairState.ABSENT);
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

    AttestationMaintenanceTestSupport.Store malformedResumedSource =
        store(credential, BackupArtifactPairState.ABSENT);
    assertInstanceOf(
        ProtectedBookBackupOutcome.BackedUp.class,
        accepted(backup(malformedResumedSource, access, credential)));
    malformedResumedSource.setLiveEvidence(
        List.of(
            new dev.erst.fingrind.core.attestation.AttestationEvidence(
                new byte[0], new byte[0], new byte[0])));
    assertInstanceOf(
        MaintenanceDecision.Failed.class, backup(malformedResumedSource, access, credential));

    AttestationMaintenanceTestSupport.Store conflict =
        store(credential, BackupArtifactPairState.ABSENT);
    conflict.setAppendFailure(new BackupAcknowledgementConflictException(BACKUP_ID));
    assertBackupRejection(
        conflict,
        access,
        credential,
        ProtectedBookMaintenanceRejection.BackupAcknowledgementConflict.class);

    AttestationMaintenanceTestSupport.Store admissionConflict =
        store(credential, BackupArtifactPairState.ABSENT);
    assertInstanceOf(
        ProtectedBookBackupOutcome.BackedUp.class,
        accepted(backup(admissionConflict, access, credential)));
    admissionConflict.setBackupPairState(BackupArtifactPairState.ABSENT);
    admissionConflict.setSnapshotEvidence(admissionConflict.liveEvidence());
    assertBackupRejection(
        admissionConflict,
        access,
        credential,
        ProtectedBookMaintenanceRejection.BackupAcknowledgementConflict.class);
  }

  @Test
  void classifiesEveryResumePreconditionBeforeOpeningTheBackupArtifact() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    ProtectedBookAccess access = access(credential);

    AttestationMaintenanceTestSupport.Store blocked =
        store(credential, BackupArtifactPairState.COMPLETE);
    blocked.setLiveBlockingArtifacts(List.of(bookPath().resolveSibling("book.sqlite-wal")));
    assertBackupRejection(
        blocked,
        access,
        credential,
        ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts.class);

    AttestationMaintenanceTestSupport.Store busy =
        store(credential, BackupArtifactPairState.COMPLETE);
    busy.setManagedLease(new LeaseBusy(bookPath()));
    assertBackupRejection(
        busy, access, credential, ProtectedBookMaintenanceRejection.ArtifactBusy.class);

    AttestationMaintenanceTestSupport.Store invalidLive =
        store(credential, BackupArtifactPairState.COMPLETE);
    invalidLive.setLiveVerification(
        MaintenanceDecision.accepted(
            new VerificationFailure(bookPath(), ProtectedBookVerificationFailure.MISSING)));
    assertBackupRejection(
        invalidLive,
        access,
        credential,
        ProtectedBookMaintenanceRejection.ArtifactVerificationFailed.class);

    AttestationMaintenanceTestSupport.Store unreadableArtifact =
        store(credential, BackupArtifactPairState.COMPLETE);
    assertInstanceOf(
        MaintenanceDecision.Failed.class, backup(unreadableArtifact, access, credential));

    AttestationMaintenanceTestSupport.Store failedLiveVerification =
        store(credential, BackupArtifactPairState.ABSENT);
    failedLiveVerification.setLiveVerification(MaintenanceDecision.failed(storageFailure()));
    assertInstanceOf(
        MaintenanceDecision.Failed.class, backup(failedLiveVerification, access, credential));
  }

  @Test
  void classifiesRestoreAndRekeyStagingVerificationFailures() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    ProtectedBookAccess access = access(credential);

    AttestationMaintenanceTestSupport.Store restoreStore =
        store(credential, BackupArtifactPairState.ABSENT);
    assertInstanceOf(
        ProtectedBookBackupOutcome.BackedUp.class,
        accepted(backup(restoreStore, access, credential)));
    restoreStore.setStagedRestoreVerification(
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

    AttestationMaintenanceTestSupport.Store rekeyStore =
        store(credential, BackupArtifactPairState.ABSENT);
    rekeyStore.setStagedRestoreVerification(
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
        store(credential, BackupArtifactPairState.ABSENT);
    rejectedRestorePublication.setPrepareFailure(
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
        store(credential, BackupArtifactPairState.ABSENT);
    failedRestorePublication.setPrepareFailure(
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

    AttestationMaintenanceTestSupport.Store failedRestoreStaging =
        store(credential, BackupArtifactPairState.ABSENT);
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

    AttestationMaintenanceTestSupport.Store failedRekeyPublication =
        store(credential, BackupArtifactPairState.ABSENT);
    failedRekeyPublication.setPrepareFailure(
        new IllegalStateException("rekey staging unavailable"));
    try (var session = credential.openSession()) {
      assertInstanceOf(
          MaintenanceDecision.Failed.class,
          workflow(failedRekeyPublication).rekeyBook(access, rekeyPath(), session));
    }

    AttestationMaintenanceTestSupport.Store failedRekeyStaging =
        store(credential, BackupArtifactPairState.ABSENT);
    failedRekeyStaging.setStagedRestore(MaintenanceDecision.failed(storageFailure()));
    try (var session = credential.openSession()) {
      assertInstanceOf(
          MaintenanceDecision.Failed.class,
          workflow(failedRekeyStaging).rekeyBook(access, rekeyPath(), session));
    }
  }

  @Test
  void classifiesRestoreAndRekeyLeaseAndVerificationBoundaryFailures() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    ProtectedBookAccess access = access(credential);

    AttestationMaintenanceTestSupport.Store blockedRestore =
        store(credential, BackupArtifactPairState.ABSENT);
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

    AttestationMaintenanceTestSupport.Store rejectedLease =
        store(credential, BackupArtifactPairState.ABSENT);
    rejectedLease.setExistingLeaseFailure(
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

    AttestationMaintenanceTestSupport.Store failedLease =
        store(credential, BackupArtifactPairState.ABSENT);
    failedLease.setExistingLeaseFailure(new IllegalStateException("lease service unavailable"));
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
        store(credential, BackupArtifactPairState.ABSENT);
    failedArtifactVerification.setBackupArtifactVerificationFailure(
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
        store(credential, BackupArtifactPairState.ABSENT);
    rejectedArtifactVerification.setBackupArtifactVerificationFailure(
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

    AttestationMaintenanceTestSupport.Store restoreAppendRejection =
        store(credential, BackupArtifactPairState.ABSENT);
    assertInstanceOf(
        ProtectedBookBackupOutcome.BackedUp.class,
        accepted(backup(restoreAppendRejection, access, credential)));
    restoreAppendRejection.setAppendFailure(
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

    AttestationMaintenanceTestSupport.Store rekeyAppendRejection =
        store(credential, BackupArtifactPairState.ABSENT);
    rekeyAppendRejection.setAppendFailure(
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

    AttestationMaintenanceTestSupport.Store resumeArtifactRejection =
        store(credential, BackupArtifactPairState.ABSENT);
    assertInstanceOf(
        ProtectedBookBackupOutcome.BackedUp.class,
        accepted(backup(resumeArtifactRejection, access, credential)));
    resumeArtifactRejection.setBackupArtifactVerificationFailure(
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

    AttestationMaintenanceTestSupport.Store rejectedRekeyPublication =
        store(credential, BackupArtifactPairState.ABSENT);
    rejectedRekeyPublication.setPrepareFailure(
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

    AttestationMaintenanceTestSupport.Store rejectedLiveVerification =
        store(credential, BackupArtifactPairState.ABSENT);
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

    AttestationMaintenanceTestSupport.Store failedRestoreVerification =
        store(credential, BackupArtifactPairState.ABSENT);
    assertInstanceOf(
        ProtectedBookBackupOutcome.BackedUp.class,
        accepted(backup(failedRestoreVerification, access, credential)));
    failedRestoreVerification.setStagedRestoreVerification(
        MaintenanceDecision.failed(storageFailure()));
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

    AttestationMaintenanceTestSupport.Store failedRekeyVerification =
        store(credential, BackupArtifactPairState.ABSENT);
    failedRekeyVerification.setStagedRestoreVerification(
        MaintenanceDecision.failed(storageFailure()));
    try (var session = credential.openSession()) {
      assertInstanceOf(
          MaintenanceDecision.Failed.class,
          workflow(failedRekeyVerification).rekeyBook(access, rekeyPath(), session));
    }

    AttestationMaintenanceTestSupport.Store malformedLiveEvidence =
        store(credential, BackupArtifactPairState.ABSENT);
    malformedLiveEvidence.setLiveEvidence(List.of());
    try (var session = credential.openSession()) {
      assertInstanceOf(
          MaintenanceDecision.Failed.class,
          workflow(malformedLiveEvidence).rekeyBook(access, rekeyPath(), session));
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
      AttestationMaintenanceTestSupport.CredentialFixture credential,
      BackupArtifactPairState backupPairState) {
    AttestationMaintenanceTestSupport.Store store =
        new AttestationMaintenanceTestSupport.Store(
            bookPath(),
            List.of(AttestationMaintenanceTestSupport.genesis(credential, RECORDED_AT)));
    store.setBackupPairState(backupPairState);
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
