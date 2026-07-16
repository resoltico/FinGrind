package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ProtectedBookMaintenanceTestSupport.FIXED_CLOCK;
import static dev.erst.fingrind.executor.ProtectedBookMaintenanceTestSupport.access;
import static dev.erst.fingrind.executor.ProtectedBookMaintenanceTestSupport.hint;
import static dev.erst.fingrind.executor.ProtectedBookMaintenanceTestSupport.internalError;
import static dev.erst.fingrind.executor.ProtectedBookMaintenanceTestSupport.path;
import static dev.erst.fingrind.executor.ProtectedBookMaintenanceTestSupport.service;
import static dev.erst.fingrind.executor.ProtectedBookMaintenanceTestSupport.touch;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceAuditKind;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Executable contract for generated-secret rekeying at the published maintenance boundary. */
class ProtectedBookMaintenanceRekeyServiceTest {
  @TempDir Path tempDirectory;

  @Test
  void rekeyBook_reencryptsOneVerifiedBookAndPublishesTheFreshKey() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path newBookKey = path(tempDirectory, "book-new.book-key");

    RekeyBookResult.Rekeyed rekeyed =
        assertInstanceOf(
            RekeyBookResult.Rekeyed.class,
            service(store).rekeyBook(access(book), newBookKey).requireAccepted());

    assertEquals(book, rekeyed.bookFilePath());
    assertEquals(1, store.recordedAudits.size());
    RecordedAudit audit = store.recordedAudits.getFirst();
    assertEquals(store.normalized(book), audit.bookPath());
    assertEquals(FIXED_CLOCK.instant(), audit.recordedAt());
    assertEquals(ProtectedBookMaintenanceAuditKind.BOOK_REKEYED, audit.auditKind());
    assertTrue(store.staging.restoredPairCommitted);
    assertTrue(store.staging.restoredPairClosed);
  }

  @Test
  void rekeyBook_refusesAnOccupiedGeneratedSecretTargetBeforeVerification() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path newBookKey = path(tempDirectory, "book-new.book-key");
    touch(newBookKey);

    RekeyBookResult.Rejected rejected =
        assertInstanceOf(
            RekeyBookResult.Rejected.class,
            service(store).rekeyBook(access(book), newBookKey).requireAccepted());

    BookMaintenanceRejection.SecretTargetOccupied occupied =
        assertInstanceOf(BookMaintenanceRejection.SecretTargetOccupied.class, rejected.rejection());
    assertEquals(hint(newBookKey), occupied.secretTargetPath());
    assertTrue(store.recordedAudits.isEmpty());
  }

  @Test
  void rekeyBook_rejectsAClosedCopyViolationAndBusyLiveBook() {
    Path book = path(tempDirectory, "book.sqlite");
    Path newBookKey = path(tempDirectory, "book-new.book-key");
    Path wal = path(tempDirectory, "book.sqlite-wal");
    FakeMaintenanceStore blockingStore = new FakeMaintenanceStore();
    blockingStore.bookBlockingArtifacts.put(
        blockingStore.normalized(book), List.of(blockingStore.normalized(wal)));

    RekeyBookResult.Rejected blocked =
        assertInstanceOf(
            RekeyBookResult.Rejected.class,
            service(blockingStore).rekeyBook(access(book), newBookKey).requireAccepted());

    assertInstanceOf(BookMaintenanceRejection.BookHasBlockingArtifacts.class, blocked.rejection());

    FakeMaintenanceStore busyStore = new FakeMaintenanceStore();
    busyStore.busyManagedArtifacts.add(busyStore.normalized(book));

    RekeyBookResult.Rejected busy =
        assertInstanceOf(
            RekeyBookResult.Rejected.class,
            service(busyStore).rekeyBook(access(book), newBookKey).requireAccepted());

    BookMaintenanceRejection.ArtifactBusy artifactBusy =
        assertInstanceOf(BookMaintenanceRejection.ArtifactBusy.class, busy.rejection());
    assertEquals(BookMaintenanceArtifactRole.LIVE_BOOK, artifactBusy.artifactRole());
  }

  @Test
  void rekeyBook_projectsInvalidLiveAndStagedBookVerification() {
    Path book = path(tempDirectory, "book.sqlite");
    Path newBookKey = path(tempDirectory, "book-new.book-key");
    FakeMaintenanceStore invalidLiveStore = new FakeMaintenanceStore();
    invalidLiveStore.verifications.put(
        invalidLiveStore.normalized(book),
        MaintenanceDecision.accepted(
            new ProtectedBookMaintenanceStore.VerificationFailure(
                invalidLiveStore.normalized(book),
                ProtectedBookVerificationFailure.FOREIGN_SQLITE)));

    RekeyBookResult.Rejected invalidLive =
        assertInstanceOf(
            RekeyBookResult.Rejected.class,
            service(invalidLiveStore).rekeyBook(access(book), newBookKey).requireAccepted());

    BookMaintenanceRejection.ArtifactVerificationFailed liveFailure =
        assertInstanceOf(
            BookMaintenanceRejection.ArtifactVerificationFailed.class, invalidLive.rejection());
    assertEquals(BookMaintenanceArtifactRole.LIVE_BOOK, liveFailure.artifactRole());
    assertEquals(
        BookMaintenanceVerificationFailure.FOREIGN_SQLITE, liveFailure.verificationFailure());

    FakeMaintenanceStore invalidStagedStore = new FakeMaintenanceStore();
    Path staged = path(tempDirectory, "staged-rekey.sqlite");
    invalidStagedStore.stagedReplacementPath = staged;
    invalidStagedStore.staging.restoredPairVerification =
        MaintenanceDecision.accepted(
            new ProtectedBookMaintenanceStore.VerificationFailure(
                invalidStagedStore.normalized(staged),
                ProtectedBookVerificationFailure.UNSUPPORTED_FORMAT_VERSION));

    RekeyBookResult.Rejected invalidStaged =
        assertInstanceOf(
            RekeyBookResult.Rejected.class,
            service(invalidStagedStore).rekeyBook(access(book), newBookKey).requireAccepted());

    BookMaintenanceRejection.ArtifactVerificationFailed stagedFailure =
        assertInstanceOf(
            BookMaintenanceRejection.ArtifactVerificationFailed.class, invalidStaged.rejection());
    assertEquals(BookMaintenanceArtifactRole.RESTORED_TARGET, stagedFailure.artifactRole());
    assertEquals(
        BookMaintenanceVerificationFailure.UNSUPPORTED_FORMAT_VERSION,
        stagedFailure.verificationFailure());
    assertTrue(invalidStagedStore.staging.restoredPairClosed);
    assertFalse(invalidStagedStore.staging.restoredPairCommitted);
  }

  @Test
  void rekeyBook_propagatesStoreFailuresAtEveryResolutionBoundary() {
    Path book = path(tempDirectory, "book.sqlite");
    Path newBookKey = path(tempDirectory, "book-new.book-key");
    ContractFailure failure = internalError("newBookKeyFilePath");

    FakeMaintenanceStore stagingFailureStore = new FakeMaintenanceStore();
    stagingFailureStore.staging.restoredPairFailure =
        MaintenanceDecision.failed(MaintenanceFailure.fromContractFailure(failure));
    assertEquals(
        failure,
        service(stagingFailureStore).rekeyBook(access(book), newBookKey).requireRejected());

    FakeMaintenanceStore verificationFailureStore = new FakeMaintenanceStore();
    verificationFailureStore.staging.restoredPairVerification =
        MaintenanceDecision.failed(MaintenanceFailure.fromContractFailure(failure));
    assertEquals(
        failure,
        service(verificationFailureStore).rekeyBook(access(book), newBookKey).requireRejected());

    FakeMaintenanceStore auditFailureStore = new FakeMaintenanceStore();
    auditFailureStore.appendAuditFailure =
        MaintenanceDecision.failed(MaintenanceFailure.fromContractFailure(failure));
    assertEquals(
        failure, service(auditFailureStore).rekeyBook(access(book), newBookKey).requireRejected());
  }

  @Test
  void rekeyBook_projectsStoreRejectionsRaisedAfterLiveBookVerification() {
    Path book = path(tempDirectory, "book.sqlite");
    Path newBookKey = path(tempDirectory, "book-new.book-key");
    FakeMaintenanceStore stageRejectionStore = new FakeMaintenanceStore();
    stageRejectionStore.stageReplacementRejection =
        new ProtectedBookMaintenanceRejection.SecretTargetOccupied(
            stageRejectionStore.normalized(newBookKey));

    RekeyBookResult.Rejected stageRejected =
        assertInstanceOf(
            RekeyBookResult.Rejected.class,
            service(stageRejectionStore).rekeyBook(access(book), newBookKey).requireAccepted());

    assertInstanceOf(
        BookMaintenanceRejection.SecretTargetOccupied.class, stageRejected.rejection());

    FakeMaintenanceStore commitRejectionStore = new FakeMaintenanceStore();
    commitRejectionStore.staging.restoredPairCommitRejection =
        new ProtectedBookMaintenanceRejection.ArtifactBusy(
            dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole
                .RESTORED_TARGET,
            commitRejectionStore.normalized(book));

    RekeyBookResult.Rejected commitRejected =
        assertInstanceOf(
            RekeyBookResult.Rejected.class,
            service(commitRejectionStore).rekeyBook(access(book), newBookKey).requireAccepted());

    assertInstanceOf(BookMaintenanceRejection.ArtifactBusy.class, commitRejected.rejection());

    FakeMaintenanceStore leaseRejectionStore = new FakeMaintenanceStore();
    leaseRejectionStore.managedLeaseRejection =
        new ProtectedBookMaintenanceRejection.SecretTargetOccupied(
            leaseRejectionStore.normalized(newBookKey));

    RekeyBookResult.Rejected leaseRejected =
        assertInstanceOf(
            RekeyBookResult.Rejected.class,
            service(leaseRejectionStore).rekeyBook(access(book), newBookKey).requireAccepted());

    assertInstanceOf(
        BookMaintenanceRejection.SecretTargetOccupied.class, leaseRejected.rejection());
  }

  @Test
  void rekeyBook_projectsOneRuntimeFailureWhilePublishingTheStagedPair() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path newBookKey = path(tempDirectory, "book-new.book-key");
    store.staging.restoredPairCommitRuntimeFailure =
        new IllegalStateException("rekey pair publication failed");

    ContractFailure failure = service(store).rekeyBook(access(book), newBookKey).requireRejected();

    assertEquals(ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE, failure.descriptor());
    assertTrue(store.staging.restoredPairClosed);
  }
}
