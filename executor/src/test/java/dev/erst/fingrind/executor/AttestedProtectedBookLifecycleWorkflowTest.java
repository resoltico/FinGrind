package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BackupAcknowledgementState;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.core.attestation.AttestationAdmissionRejectedException;
import dev.erst.fingrind.core.attestation.AttestationAuthorizationFailure;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationVerifier;
import dev.erst.fingrind.executor.maintenance.AttestedProtectedBookLifecycleWorkflow;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookBackupOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenancePathFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRekeyOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRestoreOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.LeaseBusy;
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

/** Exercises the signed state transitions that own protected-book maintenance semantics. */
class AttestedProtectedBookLifecycleWorkflowTest {
  private static final Instant RECORDED_AT = Instant.parse("2026-07-21T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(RECORDED_AT, ZoneOffset.UTC);
  private static final UUID BACKUP_ID = UUID.fromString("018f0000-0000-7000-8000-000000000001");

  @TempDir Path temporaryDirectory;

  @BeforeEach
  void canonicalizeTemporaryDirectory() throws IOException {
    temporaryDirectory = temporaryDirectory.toRealPath();
  }

  @Test
  void backupVerificationUsesTheCanonicalBookAccessReturnedByPathAdmission() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path callerAliasPath = temporaryDirectory.resolve("alias/live.sqlite");
    Path canonicalBookPath = temporaryDirectory.resolve("real/live.sqlite");
    AttestationMaintenanceTestSupport.Store store =
        new AttestationMaintenanceTestSupport.Store(
            canonicalBookPath,
            List.of(AttestationMaintenanceTestSupport.genesis(credential, RECORDED_AT)));
    store.canonicalize(callerAliasPath, canonicalBookPath);
    AttestedProtectedBookLifecycleWorkflow workflow =
        new AttestedProtectedBookLifecycleWorkflow(CLOCK, store);
    ProtectedBookAccess access =
        ProtectedBookAccess.fromPublished(
            AttestationMaintenanceTestSupport.bookAccess(callerAliasPath, credential));

    try (var session = credential.openSession()) {
      assertInstanceOf(
          ProtectedBookBackupOutcome.BackedUp.class,
          accepted(
              workflow.backupBook(
                  access,
                  temporaryDirectory.resolve("retained/book.fgba"),
                  temporaryDirectory.resolve("retained/book.key"),
                  BACKUP_ID,
                  session)));
    }

    assertEquals(canonicalBookPath.toAbsolutePath().normalize(), store.verifiedBookPath());
  }

  @Test
  void restoreRejectsExistingSourceAdmissionBeforeNormalizingOutputTargets() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookPath = temporaryDirectory.resolve("live/book.sqlite");
    Path backupPath = temporaryDirectory.resolve("retained/book.fgba");
    Path backupKeyPath = temporaryDirectory.resolve("retained/book.key");
    AttestationMaintenanceTestSupport.Store store =
        new AttestationMaintenanceTestSupport.Store(
            bookPath, List.of(AttestationMaintenanceTestSupport.genesis(credential, RECORDED_AT)));
    ProtectedBookMaintenanceRejection.ArtifactPathInvalid sourceRejection =
        new ProtectedBookMaintenanceRejection.ArtifactPathInvalid(
            ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_SOURCE,
            backupKeyPath.toAbsolutePath().normalize(),
            ProtectedBookMaintenancePathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE);
    store.rejectExistingSourceNormalization(
        backupKeyPath, ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_SOURCE, sourceRejection);
    AttestedProtectedBookLifecycleWorkflow workflow =
        new AttestedProtectedBookLifecycleWorkflow(CLOCK, store);

    try (var session = credential.openSession()) {
      ProtectedBookRestoreOutcome.Rejected rejected =
          assertInstanceOf(
              ProtectedBookRestoreOutcome.Rejected.class,
              accepted(
                  workflow.restoreBook(
                      temporaryDirectory.resolve("restored/book.sqlite"),
                      temporaryDirectory.resolve("restored/book.key"),
                      backupPath,
                      backupKeyPath,
                      session)));
      assertEquals(sourceRejection, rejected.rejection());
    }

    assertEquals(
        List.of(
            ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE,
            ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_SOURCE),
        store.normalizationRequests().stream()
            .map(
                AttestationMaintenanceTestSupport.MaintenanceStore.NormalizationRequest
                    ::artifactRole)
            .toList());
    assertEquals(
        List.of(
            AttestationMaintenanceTestSupport.MaintenanceStore.NormalizationBoundary
                .EXISTING_SOURCE,
            AttestationMaintenanceTestSupport.MaintenanceStore.NormalizationBoundary
                .EXISTING_SOURCE),
        store.normalizationRequests().stream()
            .map(
                AttestationMaintenanceTestSupport.MaintenanceStore.NormalizationRequest
                    ::normalizationBoundary)
            .toList());
  }

  @Test
  void rekeyRejectsInvalidLiveSourcesAndBusyPublicationScopes() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookPath = temporaryDirectory.resolve("live/book.sqlite");
    ProtectedBookAccess access =
        ProtectedBookAccess.fromPublished(
            AttestationMaintenanceTestSupport.bookAccess(bookPath, credential));
    Path rekeyPath = temporaryDirectory.resolve("rekeyed/book.key");
    ProtectedBookMaintenanceRejection.ArtifactPathInvalid sourceRejection =
        new ProtectedBookMaintenanceRejection.ArtifactPathInvalid(
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
            bookPath,
            ProtectedBookMaintenancePathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE);

    AttestationMaintenanceTestSupport.Store invalidSource = store(bookPath, credential);
    invalidSource.rejectExistingSourceNormalization(
        bookPath, ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, sourceRejection);
    try (var session = credential.openSession()) {
      ProtectedBookRekeyOutcome.Rejected rejected =
          assertInstanceOf(
              ProtectedBookRekeyOutcome.Rejected.class,
              accepted(
                  new AttestedProtectedBookLifecycleWorkflow(CLOCK, invalidSource)
                      .rekeyBook(access, rekeyPath, session)));
      assertEquals(sourceRejection, rejected.rejection());
    }

    AttestationMaintenanceTestSupport.Store busy = store(bookPath, credential);
    busy.setManagedLease(new LeaseBusy(bookPath));
    try (var session = credential.openSession()) {
      ProtectedBookRekeyOutcome.Rejected rejected =
          assertInstanceOf(
              ProtectedBookRekeyOutcome.Rejected.class,
              accepted(
                  new AttestedProtectedBookLifecycleWorkflow(CLOCK, busy)
                      .rekeyBook(access, rekeyPath, session)));
      assertInstanceOf(ProtectedBookMaintenanceRejection.ArtifactBusy.class, rejected.rejection());
    }
  }

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
    var snapshotVerification = AttestationVerifier.verifyBook(store.liveEvidence());

    ProtectedBookBackupOutcome.BackedUp backedUp;
    try (var session = credential.openSession()) {
      backedUp =
          assertInstanceOf(
              ProtectedBookBackupOutcome.BackedUp.class,
              accepted(workflow.backupBook(access, backupPath, backupKeyPath, BACKUP_ID, session)));
    }
    assertEquals(BackupAcknowledgementState.ACKNOWLEDGED, backedUp.acknowledgementState());
    assertNotNull(backedUp.attestationCommit());
    assertEquals(
        1, AttestationVerifier.verifyBook(store.liveEvidence()).headOrder().intValueExact());

    ProtectedBookBackupOutcome.BackedUp resumed;
    try (var session = credential.openSession()) {
      resumed =
          assertInstanceOf(
              ProtectedBookBackupOutcome.BackedUp.class,
              accepted(workflow.backupBook(access, backupPath, backupKeyPath, BACKUP_ID, session)));
    }
    assertEquals(BackupAcknowledgementState.RESUMED, resumed.acknowledgementState());
    assertNull(resumed.attestationCommit());
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
    var restoredVerification = AttestationVerifier.verifyBook(store.restoredEvidence());
    assertEquals(1, restoredVerification.headOrder().intValueExact());
    assertEquals(snapshotVerification.bookId(), restoredVerification.bookId());
    assertArrayEquals(snapshotVerification.operationHead(), restoredVerification.previousHead());

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
  void reportsAlreadyPresentWithoutReusingTheConcurrentChainHead() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookPath = temporaryDirectory.resolve("live/book.sqlite");
    AttestationMaintenanceTestSupport.Store store =
        new AttestationMaintenanceTestSupport.Store(
            bookPath, List.of(AttestationMaintenanceTestSupport.genesis(credential, RECORDED_AT)));
    store.simulateConcurrentExactAppend();
    AttestedProtectedBookLifecycleWorkflow workflow =
        new AttestedProtectedBookLifecycleWorkflow(CLOCK, store);
    ProtectedBookAccess access =
        ProtectedBookAccess.fromPublished(
            AttestationMaintenanceTestSupport.bookAccess(bookPath, credential));

    ProtectedBookBackupOutcome.BackedUp backedUp;
    try (var session = credential.openSession()) {
      backedUp =
          assertInstanceOf(
              ProtectedBookBackupOutcome.BackedUp.class,
              accepted(
                  workflow.backupBook(
                      access,
                      temporaryDirectory.resolve("retained/book.fgba"),
                      temporaryDirectory.resolve("retained/book.key"),
                      BACKUP_ID,
                      session)));
    }

    assertEquals(BackupAcknowledgementState.ALREADY_PRESENT, backedUp.acknowledgementState());
    assertNull(backedUp.attestationCommit());
    assertEquals(
        1, AttestationVerifier.verifyBook(store.liveEvidence()).headOrder().intValueExact());
  }

  @Test
  void rejectsAnExactBackupReplayWhenLaterLiveHistoryIsMalformedAsHistoricalVerificationFailure()
      throws IOException {
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

    try (var session = credential.openSession()) {
      accepted(workflow.backupBook(access, backupPath, backupKeyPath, BACKUP_ID, session));
    }
    List<AttestationEvidence> acknowledgedEvidence = store.liveEvidence();
    store.setLiveEvidence(
        List.of(
            acknowledgedEvidence.getFirst(),
            acknowledgedEvidence.get(1),
            acknowledgedEvidence.get(1)));

    try (var session = credential.openSession()) {
      ProtectedBookBackupOutcome.Rejected rejected =
          assertInstanceOf(
              ProtectedBookBackupOutcome.Rejected.class,
              accepted(workflow.backupBook(access, backupPath, backupKeyPath, BACKUP_ID, session)));
      ProtectedBookMaintenanceRejection.ArtifactVerificationFailed failure =
          assertInstanceOf(
              ProtectedBookMaintenanceRejection.ArtifactVerificationFailed.class,
              rejected.rejection());
      assertEquals(ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, failure.artifactRole());
      assertEquals(bookPath, failure.artifactPath());
      assertEquals(
          ProtectedBookVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED,
          failure.verificationFailure());
    }
  }

  @Test
  void rejectsFreshBackupWhenTheStagedSnapshotCarriesMalformedLiveHistory() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookPath = temporaryDirectory.resolve("live/book.sqlite");
    AttestationMaintenanceTestSupport.Store store = store(bookPath, credential);
    store.setSnapshotEvidence(List.of());
    AttestedProtectedBookLifecycleWorkflow workflow =
        new AttestedProtectedBookLifecycleWorkflow(CLOCK, store);
    ProtectedBookAccess access =
        ProtectedBookAccess.fromPublished(
            AttestationMaintenanceTestSupport.bookAccess(bookPath, credential));
    Path backupPath = temporaryDirectory.resolve("retained/book.fgba");
    Path backupKeyPath = temporaryDirectory.resolve("retained/book.key");

    try (var session = credential.openSession()) {
      ProtectedBookBackupOutcome.Rejected rejected =
          assertInstanceOf(
              ProtectedBookBackupOutcome.Rejected.class,
              accepted(workflow.backupBook(access, backupPath, backupKeyPath, BACKUP_ID, session)));
      assertHistoricalLiveVerificationFailure(rejected.rejection(), bookPath);
    }
  }

  @Test
  void rejectsResumeWhenTheArtifactSourcePrefixOfLiveHistoryIsMalformed() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookPath = temporaryDirectory.resolve("live/book.sqlite");
    AttestationMaintenanceTestSupport.Store store = store(bookPath, credential);
    AttestedProtectedBookLifecycleWorkflow workflow =
        new AttestedProtectedBookLifecycleWorkflow(CLOCK, store);
    ProtectedBookAccess access =
        ProtectedBookAccess.fromPublished(
            AttestationMaintenanceTestSupport.bookAccess(bookPath, credential));
    Path backupPath = temporaryDirectory.resolve("retained/book.fgba");
    Path backupKeyPath = temporaryDirectory.resolve("retained/book.key");

    try (var session = credential.openSession()) {
      accepted(workflow.backupBook(access, backupPath, backupKeyPath, BACKUP_ID, session));
    }
    store.setLiveEvidence(List.of(store.liveEvidence().get(1)));

    try (var session = credential.openSession()) {
      ProtectedBookBackupOutcome.Rejected rejected =
          assertInstanceOf(
              ProtectedBookBackupOutcome.Rejected.class,
              accepted(workflow.backupBook(access, backupPath, backupKeyPath, BACKUP_ID, session)));
      assertHistoricalLiveVerificationFailure(rejected.rejection(), bookPath);
    }
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
    occupiedStore.setInjectedPairAdmission(
        new ProtectedBookPairPublicationFailureOutcome.CompletionUncertain(
            backupPath,
            ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN,
            backupKeyPath,
            ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED,
            null));
    try (var session = credential.openSession()) {
      ContractFailureException failure =
          assertThrows(
              ContractFailureException.class,
              () ->
                  new AttestedProtectedBookLifecycleWorkflow(CLOCK, occupiedStore)
                      .backupBook(access, backupPath, backupKeyPath, BACKUP_ID, session));
      assertEquals("protected-book-pair-publication-uncertain", failure.failure().code());
    }

    AttestationMaintenanceTestSupport.Store pendingStore = store(bookPath, credential);
    pendingStore
        .overrides()
        .appendFailure(new IllegalStateException("simulated acknowledgment interruption"));
    try (var session = credential.openSession()) {
      assertInstanceOf(
          ProtectedBookBackupOutcome.AcknowledgementPending.class,
          accepted(
              new AttestedProtectedBookLifecycleWorkflow(CLOCK, pendingStore)
                  .backupBook(access, backupPath, backupKeyPath, BACKUP_ID, session)));
    }

    AttestationMaintenanceTestSupport.Store authorizationRejectedStore =
        store(bookPath, credential);
    authorizationRejectedStore
        .overrides()
        .appendFailure(
            AttestationAdmissionRejectedException.from(
                AttestationAuthorizationFailure.QUORUM_BELOW));
    try (var session = credential.openSession()) {
      ProtectedBookBackupOutcome.AcknowledgementAuthorizationRejected rejected =
          assertInstanceOf(
              ProtectedBookBackupOutcome.AcknowledgementAuthorizationRejected.class,
              accepted(
                  new AttestedProtectedBookLifecycleWorkflow(CLOCK, authorizationRejectedStore)
                      .backupBook(access, backupPath, backupKeyPath, BACKUP_ID, session)));
      assertEquals(AttestationAuthorizationFailure.QUORUM_BELOW, rejected.failure());
      assertEquals(
          ProtectedBookPairPublicationCompletion.PUBLISHED, rejected.pairPublicationCompletion());
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
    sourceBlockingStore
        .overrides()
        .backupBlockingArtifacts(List.of(backupPath.resolveSibling("book.fgba-wal")));
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

  @Test
  void rethrowsAdmissionRefusalsAtEveryUnpublishedLifecycleMutationBoundary() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    Path bookPath = temporaryDirectory.resolve("live/book.sqlite");
    ProtectedBookAccess access =
        ProtectedBookAccess.fromPublished(
            AttestationMaintenanceTestSupport.bookAccess(bookPath, credential));
    Path backupPath = temporaryDirectory.resolve("retained/book.fgba");
    Path backupKeyPath = temporaryDirectory.resolve("retained/book.key");

    AttestationMaintenanceTestSupport.Store createStore = store(bookPath, credential);
    createStore
        .overrides()
        .stagedBackupFailure(
            AttestationAdmissionRejectedException.from(
                AttestationAuthorizationFailure.KEY_REVOKED));
    try (var session = credential.openSession()) {
      AttestationAdmissionRejectedException rejected =
          assertThrows(
              AttestationAdmissionRejectedException.class,
              () ->
                  new AttestedProtectedBookLifecycleWorkflow(CLOCK, createStore)
                      .backupBook(access, backupPath, backupKeyPath, BACKUP_ID, session));
      assertEquals(AttestationAuthorizationFailure.KEY_REVOKED, rejected.failure());
    }

    AttestationMaintenanceTestSupport.Store resumeStore = store(bookPath, credential);
    AttestedProtectedBookLifecycleWorkflow resumeWorkflow =
        new AttestedProtectedBookLifecycleWorkflow(CLOCK, resumeStore);
    try (var session = credential.openSession()) {
      accepted(resumeWorkflow.backupBook(access, backupPath, backupKeyPath, BACKUP_ID, session));
    }
    resumeStore
        .overrides()
        .backupArtifactVerificationFailure(
            AttestationAdmissionRejectedException.from(
                AttestationAuthorizationFailure.CREDENTIAL_PURPOSE_INVALID));
    try (var session = credential.openSession()) {
      AttestationAdmissionRejectedException rejected =
          assertThrows(
              AttestationAdmissionRejectedException.class,
              () ->
                  resumeWorkflow.backupBook(access, backupPath, backupKeyPath, BACKUP_ID, session));
      assertEquals(AttestationAuthorizationFailure.CREDENTIAL_PURPOSE_INVALID, rejected.failure());
    }

    AttestationMaintenanceTestSupport.Store restoreStore = store(bookPath, credential);
    AttestedProtectedBookLifecycleWorkflow restoreWorkflow =
        new AttestedProtectedBookLifecycleWorkflow(CLOCK, restoreStore);
    try (var session = credential.openSession()) {
      accepted(restoreWorkflow.backupBook(access, backupPath, backupKeyPath, BACKUP_ID, session));
    }
    restoreStore
        .overrides()
        .appendFailure(
            AttestationAdmissionRejectedException.from(
                AttestationAuthorizationFailure.CAPABILITY_INVALID));
    try (var session = credential.openSession()) {
      AttestationAdmissionRejectedException rejected =
          assertThrows(
              AttestationAdmissionRejectedException.class,
              () ->
                  restoreWorkflow.restoreBook(
                      temporaryDirectory.resolve("restored/book.sqlite"),
                      temporaryDirectory.resolve("restored/book.key"),
                      backupPath,
                      backupKeyPath,
                      session));
      assertEquals(AttestationAuthorizationFailure.CAPABILITY_INVALID, rejected.failure());
    }

    AttestationMaintenanceTestSupport.Store rekeyStore = store(bookPath, credential);
    rekeyStore
        .overrides()
        .appendFailure(
            AttestationAdmissionRejectedException.from(
                AttestationAuthorizationFailure.QUORUM_BELOW));
    try (var session = credential.openSession()) {
      AttestationAdmissionRejectedException rejected =
          assertThrows(
              AttestationAdmissionRejectedException.class,
              () ->
                  new AttestedProtectedBookLifecycleWorkflow(CLOCK, rekeyStore)
                      .rekeyBook(access, temporaryDirectory.resolve("rekeyed/book.key"), session));
      assertEquals(AttestationAuthorizationFailure.QUORUM_BELOW, rejected.failure());
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

  private static <T> T accepted(MaintenanceDecision<T> decision) {
    return decision.fold(
        value -> value,
        failure -> {
          throw new AssertionError(failure.message());
        });
  }
}
