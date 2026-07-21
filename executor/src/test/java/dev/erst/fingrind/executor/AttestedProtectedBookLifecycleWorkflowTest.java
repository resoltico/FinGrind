package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.attestation.AttestationVerifier;
import dev.erst.fingrind.executor.maintenance.AttestedProtectedBookLifecycleWorkflow;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookBackupOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRekeyOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRestoreOutcome;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.BackupArtifactPairState;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.LeaseBusy;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the signed state transitions that own protected-book maintenance semantics. */
class AttestedProtectedBookLifecycleWorkflowTest {
  private static final Instant RECORDED_AT = Instant.parse("2026-07-21T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(RECORDED_AT, ZoneOffset.UTC);
  private static final UUID BACKUP_ID = UUID.fromString("018f0000-0000-7000-8000-000000000001");

  @TempDir Path temporaryDirectory;

  @Test
  void backsUpResumesRestoresAndRekeysWithVerifiableDerivedChains() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookPath = temporaryDirectory.resolve("live/book.sqlite");
    AttestationMaintenanceTestSupport.Store store =
        new AttestationMaintenanceTestSupport.Store(
            bookPath, List.of(AttestationMaintenanceTestSupport.genesis(credential, RECORDED_AT)));
    AttestedProtectedBookLifecycleWorkflow workflow =
        new AttestedProtectedBookLifecycleWorkflow(CLOCK, store);
    ProtectedBookAccess access =
        ProtectedBookAccess.fromPublished(
            AttestationMaintenanceTestSupport.bookAccess(bookPath, credential));
    Path backupPath = temporaryDirectory.resolve("retained/book.fgba");
    Path backupKeyPath = temporaryDirectory.resolve("retained/book.key");

    ProtectedBookBackupOutcome.BackedUp backedUp;
    try (var session = credential.openSession()) {
      backedUp =
          assertInstanceOf(
              ProtectedBookBackupOutcome.BackedUp.class,
              accepted(workflow.backupBook(access, backupPath, backupKeyPath, BACKUP_ID, session)));
    }
    assertFalse(backedUp.acknowledgementResumed());
    assertEquals(
        1, AttestationVerifier.verifyBook(store.liveEvidence()).headOrder().intValueExact());

    ProtectedBookBackupOutcome.BackedUp resumed;
    try (var session = credential.openSession()) {
      resumed =
          assertInstanceOf(
              ProtectedBookBackupOutcome.BackedUp.class,
              accepted(workflow.backupBook(access, backupPath, backupKeyPath, BACKUP_ID, session)));
    }
    assertTrue(resumed.acknowledgementResumed());
    assertEquals(2, store.liveEvidence().size());

    Path restoredBookPath = temporaryDirectory.resolve("restored/book.sqlite");
    Path restoredKeyPath = temporaryDirectory.resolve("restored/book.key");
    try (var session = credential.openSession()) {
      assertInstanceOf(
          ProtectedBookRestoreOutcome.Restored.class,
          accepted(
              workflow.restoreBook(
                  restoredBookPath, restoredKeyPath, backupPath, backupKeyPath, session)));
    }
    assertEquals(
        1, AttestationVerifier.verifyBook(store.restoredEvidence()).headOrder().intValueExact());

    Path rekeyedKeyPath = temporaryDirectory.resolve("rekeyed/book.key");
    try (var session = credential.openSession()) {
      assertInstanceOf(
          ProtectedBookRekeyOutcome.Rekeyed.class,
          accepted(workflow.rekeyBook(access, rekeyedKeyPath, session)));
    }
    assertEquals(
        2, AttestationVerifier.verifyBook(store.restoredEvidence()).headOrder().intValueExact());
  }

  @Test
  void reportsDeterministicRefusalsAndPreservesARecoverableAcknowledgementPendingState()
      throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookPath = temporaryDirectory.resolve("live/book.sqlite");
    ProtectedBookAccess access =
        ProtectedBookAccess.fromPublished(
            AttestationMaintenanceTestSupport.bookAccess(bookPath, credential));
    Path backupPath = temporaryDirectory.resolve("retained/book.fgba");
    Path backupKeyPath = temporaryDirectory.resolve("retained/book.key");

    AttestationMaintenanceTestSupport.Store occupiedStore = store(bookPath, credential);
    occupiedStore.setBackupPairState(BackupArtifactPairState.ARTIFACT_ONLY);
    try (var session = credential.openSession()) {
      ProtectedBookBackupOutcome.Rejected rejected =
          assertInstanceOf(
              ProtectedBookBackupOutcome.Rejected.class,
              accepted(
                  new AttestedProtectedBookLifecycleWorkflow(CLOCK, occupiedStore)
                      .backupBook(access, backupPath, backupKeyPath, BACKUP_ID, session)));
      assertInstanceOf(
          ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists.class,
          rejected.rejection());
    }

    AttestationMaintenanceTestSupport.Store pendingStore = store(bookPath, credential);
    pendingStore.setAppendFailure(
        new IllegalStateException("simulated acknowledgment interruption"));
    try (var session = credential.openSession()) {
      assertInstanceOf(
          ProtectedBookBackupOutcome.AcknowledgementPending.class,
          accepted(
              new AttestedProtectedBookLifecycleWorkflow(CLOCK, pendingStore)
                  .backupBook(access, backupPath, backupKeyPath, BACKUP_ID, session)));
    }

    AttestationMaintenanceTestSupport.Store restoreStore = store(bookPath, credential);
    try (var session = credential.openSession()) {
      ProtectedBookRestoreOutcome.Rejected rejected =
          assertInstanceOf(
              ProtectedBookRestoreOutcome.Rejected.class,
              accepted(
                  new AttestedProtectedBookLifecycleWorkflow(CLOCK, restoreStore)
                      .restoreBook(bookPath, backupKeyPath, bookPath, backupKeyPath, session)));
      assertInstanceOf(
          ProtectedBookMaintenanceRejection.BackupSourceMatchesLiveBook.class,
          rejected.rejection());
    }

    AttestationMaintenanceTestSupport.Store sourceBlockingStore = store(bookPath, credential);
    sourceBlockingStore.setBackupBlockingArtifacts(
        List.of(backupPath.resolveSibling("book.fgba-wal")));
    try (var session = credential.openSession()) {
      ProtectedBookRestoreOutcome.Rejected rejected =
          assertInstanceOf(
              ProtectedBookRestoreOutcome.Rejected.class,
              accepted(
                  new AttestedProtectedBookLifecycleWorkflow(CLOCK, sourceBlockingStore)
                      .restoreBook(
                          temporaryDirectory.resolve("restored/book.sqlite"),
                          temporaryDirectory.resolve("restored/book.key"),
                          backupPath,
                          backupKeyPath,
                          session)));
      assertInstanceOf(
          ProtectedBookMaintenanceRejection.BackupSourceHasBlockingArtifacts.class,
          rejected.rejection());
    }

    AttestationMaintenanceTestSupport.Store busySourceStore = store(bookPath, credential);
    busySourceStore.setExistingLease(new LeaseBusy(backupPath));
    try (var session = credential.openSession()) {
      ProtectedBookRestoreOutcome.Rejected rejected =
          assertInstanceOf(
              ProtectedBookRestoreOutcome.Rejected.class,
              accepted(
                  new AttestedProtectedBookLifecycleWorkflow(CLOCK, busySourceStore)
                      .restoreBook(
                          temporaryDirectory.resolve("restored/book.sqlite"),
                          temporaryDirectory.resolve("restored/book.key"),
                          backupPath,
                          backupKeyPath,
                          session)));
      assertInstanceOf(ProtectedBookMaintenanceRejection.ArtifactBusy.class, rejected.rejection());
    }

    AttestationMaintenanceTestSupport.Store rekeyStore = store(bookPath, credential);
    rekeyStore.setLiveBlockingArtifacts(List.of(bookPath.resolveSibling("book.sqlite-wal")));
    try (var session = credential.openSession()) {
      ProtectedBookRekeyOutcome.Rejected rejected =
          assertInstanceOf(
              ProtectedBookRekeyOutcome.Rejected.class,
              accepted(
                  new AttestedProtectedBookLifecycleWorkflow(CLOCK, rekeyStore)
                      .rekeyBook(access, temporaryDirectory.resolve("rekeyed/book.key"), session)));
      assertInstanceOf(
          ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts.class, rejected.rejection());
    }
  }

  private AttestationMaintenanceTestSupport.CredentialFixture credential() throws IOException {
    return AttestationMaintenanceTestSupport.createCredential(temporaryDirectory);
  }

  private AttestationMaintenanceTestSupport.Store store(
      Path bookPath, AttestationMaintenanceTestSupport.CredentialFixture credential) {
    return new AttestationMaintenanceTestSupport.Store(
        bookPath, List.of(AttestationMaintenanceTestSupport.genesis(credential, RECORDED_AT)));
  }

  private static <T> T accepted(MaintenanceDecision<T> decision) {
    return decision.fold(
        value -> value,
        failure -> {
          throw new AssertionError(failure.message());
        });
  }
}
