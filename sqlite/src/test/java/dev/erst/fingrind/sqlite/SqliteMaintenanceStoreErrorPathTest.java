package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.core.attestation.AttestationLifecycleMutationProjection;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Exercises real maintenance-store error handling at the durable attestation boundary. */
class SqliteMaintenanceStoreErrorPathTest extends SqliteArtifactPublicationTestSupport {
  @Test
  void backupArtifactPairState_distinguishesEveryRecoverableFilesystemState() throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path artifactPath = tempDirectory.resolve("backup.fgba");
    Path keyPath = tempDirectory.resolve("backup.key");

    assertEquals(
        ProtectedBookMaintenanceStore.BackupArtifactPairState.ABSENT,
        store.backupArtifactPairState(artifactPath, keyPath));
    Files.writeString(artifactPath, "artifact");
    assertEquals(
        ProtectedBookMaintenanceStore.BackupArtifactPairState.ARTIFACT_ONLY,
        store.backupArtifactPairState(artifactPath, keyPath));
    Files.delete(artifactPath);
    Files.writeString(keyPath, "key");
    assertEquals(
        ProtectedBookMaintenanceStore.BackupArtifactPairState.KEY_ONLY,
        store.backupArtifactPairState(artifactPath, keyPath));
    Files.writeString(artifactPath, "artifact");
    assertEquals(
        ProtectedBookMaintenanceStore.BackupArtifactPairState.COMPLETE,
        store.backupArtifactPairState(artifactPath, keyPath));
  }

  @Test
  void resolverFailureAndRejectedAttestedAppend_preserveTheDurableBookState() throws Exception {
    Path unresolvedBookPath = writeArtifact("resolver-failure.sqlite", "not a SQLite book");
    BookAccess unresolvedAccess = bookAccess(unresolvedBookPath);
    SqliteProtectedBookMaintenanceStore resolverFailingStore =
        new SqliteProtectedBookMaintenanceStore(
            (bookPath, passphraseSource, intent) ->
                ContractDecision.rejected(
                    ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE.failure(
                        "The selected key cannot be opened.", null, null)));
    MaintenanceFailure resolutionFailure =
        failedValue(
            resolverFailingStore.verifyInitializedBook(
                localAccess(unresolvedAccess), ProtectedBookMaintenanceArtifactRole.LIVE_BOOK));
    assertEquals(ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE, resolutionFailure.descriptor());

    Path bookPath = tempDirectory.resolve("append-rejection.sqlite");
    BookAccess bookAccess = bookAccess(bookPath);
    initializeBook(bookAccess);
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    try (ProtectedBookMaintenanceStore.VerifiedBook verifiedBook =
        verifiedBook(store, bookAccess)) {
      int operationCount = store.loadAttestationEvidence(verifiedBook).size();
      IllegalStateException rejection = new IllegalStateException("custody rejected authorization");
      assertSame(
          rejection,
          assertThrows(
              IllegalStateException.class,
              () ->
                  store.appendAttestedOperation(
                      verifiedBook,
                      "rekey-book",
                      Instant.parse("2026-07-21T12:00:00Z"),
                      AttestationLifecycleMutationProjection.rekeyBook(
                          "rekey-book",
                          java.math.BigInteger.ONE,
                          Instant.parse("2026-07-21T12:00:00Z"),
                          Optional.empty()),
                      ignored -> {
                        throw rejection;
                      },
                      null)));
      assertEquals(operationCount, store.loadAttestationEvidence(verifiedBook).size());
    }
  }

  @Test
  void malformedBackupArtifact_isClassifiedAsVerificationFailureWithoutOpeningTheLiveBook()
      throws Exception {
    Path artifactPath = writeArtifact("malformed.fgba", "not a FinGrind backup artifact");
    Path keyPath = writeArtifact("malformed.key", "not a usable key");
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();

    ProtectedBookMaintenanceRejectionException exception =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () -> store.verifyBackupArtifact(artifactPath, keyPath));
    ProtectedBookMaintenanceRejection.ArtifactVerificationFailed rejection =
        assertInstanceOf(
            ProtectedBookMaintenanceRejection.ArtifactVerificationFailed.class,
            exception.rejection());
    assertEquals(ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE, rejection.artifactRole());
    assertEquals(artifactPath.toAbsolutePath().normalize(), rejection.artifactPath());
    assertEquals(
        ProtectedBookVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED,
        rejection.verificationFailure());
  }

  @Test
  void backupArtifactVerification_rejectsADirectorySourceBeforeAnySnapshotIsOpened()
      throws Exception {
    Path artifactDirectory = Files.createDirectory(tempDirectory.resolve("backup-directory"));
    Path keyPath = writeArtifact("backup-directory.key", "not a usable key");
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();

    ProtectedBookMaintenanceRejection.ArtifactPathInvalid rejection =
        assertInstanceOf(
            ProtectedBookMaintenanceRejection.ArtifactPathInvalid.class,
            assertThrows(
                    ProtectedBookMaintenanceRejectionException.class,
                    () -> store.verifyBackupArtifact(artifactDirectory, keyPath))
                .rejection());

    assertEquals(ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE, rejection.artifactRole());
    assertEquals(artifactDirectory.toAbsolutePath().normalize(), rejection.artifactPath());
  }
}
