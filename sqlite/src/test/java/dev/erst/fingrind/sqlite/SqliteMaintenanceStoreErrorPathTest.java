package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationLifecycleMutationProjection;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.core.attestation.AttestationVerifier;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
                      AttestationOperationKind.REKEY_BOOK,
                      Instant.parse("2026-07-21T12:00:00Z"),
                      AttestationLifecycleMutationProjection.rekeyBook(
                          AttestationOperationKind.REKEY_BOOK.wireToken(),
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
  void backupAcknowledgementAdmissionRetriesOnlyItsStaleHeadPrecondition() throws Exception {
    Path bookPath = tempDirectory.resolve("backup-acknowledgement.sqlite");
    BookAccess bookAccess = bookAccess(bookPath);
    initializeBook(bookAccess);
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    try (ProtectedBookMaintenanceStore.VerifiedBook verifiedBook =
        verifiedBook(store, bookAccess)) {
      AttestationVerification sourceVerification =
          AttestationVerifier.verifyBook(store.loadAttestationEvidence(verifiedBook));
      AttestationBackupAcknowledgement acknowledgement =
          new AttestationBackupAcknowledgement(
              UUID.fromString("c4de4521-4296-4f93-873c-17b8c625f44d"),
              new byte[32],
              sourceVerification.headOrder(),
              sourceVerification.operationHead());
      assertTrue(
          SqliteProtectedBookMaintenanceStore.retriesStaleHead(
              AttestationOperationKind.BACKUP_CREATED, acknowledgement));
      assertFalse(
          SqliteProtectedBookMaintenanceStore.retriesStaleHead(
              AttestationOperationKind.BACKUP_CREATED, null));
      assertFalse(
          SqliteProtectedBookMaintenanceStore.retriesStaleHead(
              AttestationOperationKind.REKEY_BOOK, acknowledgement));

      AttestationVerification backupVerification =
          store.appendAttestedOperation(
              verifiedBook,
              AttestationOperationKind.BACKUP_CREATED,
              Instant.parse("2026-07-21T12:00:00Z"),
              AttestationLifecycleMutationProjection.backupBook(
                  AttestationOperationKind.BACKUP_CREATED.wireToken(), acknowledgement),
              SqliteAttestationTestSupport.authorizer(),
              acknowledgement);

      assertEquals(
          sourceVerification.headOrder().add(java.math.BigInteger.ONE),
          backupVerification.headOrder());
    }

    SqliteAttestationStaleHeadException staleHead =
        new SqliteAttestationStaleHeadException(
            new byte[32], new byte[32], java.math.BigInteger.ONE);
    AtomicInteger attempts = new AtomicInteger();
    assertEquals(
        "accepted",
        SqliteProtectedBookMaintenanceStore.retryStaleHead(
            true,
            () -> {
              if (attempts.getAndIncrement() == 0) {
                throw staleHead;
              }
              return "accepted";
            }));
    assertEquals(2, attempts.get());
    assertSame(
        staleHead,
        assertThrows(
            SqliteAttestationStaleHeadException.class,
            () ->
                SqliteProtectedBookMaintenanceStore.retryStaleHead(
                    false,
                    () -> {
                      throw staleHead;
                    })));
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
  void signedBackupArtifactWithANonBookSnapshot_isClassifiedAsVerificationFailure()
      throws Exception {
    Instant initializedAt = Instant.parse("2026-07-21T12:00:00Z");
    AttestationEvidence genesis =
        SqliteAttestationTestSupport.genesis(
            SqlitePostingFactFixtureSupport.bookIdentity(), initializedAt);
    AttestationVerification sourceVerification = AttestationVerifier.verifyBook(List.of(genesis));
    Path artifactPath = tempDirectory.resolve("signed-non-book-snapshot.fgba");
    Files.write(
        artifactPath,
        SqliteAttestationTestSupport.signedBackupArtifact(
            "not an encrypted SQLite book".getBytes(java.nio.charset.StandardCharsets.UTF_8),
            sourceVerification,
            UUID.fromString("75624522-2c32-42fe-bc59-815d2f5b062e")));
    BookAccess backupAccess = bookAccess(tempDirectory.resolve("signed-non-book-snapshot.sqlite"));
    Path keyPath =
        ((BookAccess.PassphraseSource.KeyFile) backupAccess.passphraseSource()).bookKeyFilePath();

    ProtectedBookMaintenanceRejection.ArtifactVerificationFailed rejection =
        assertInstanceOf(
            ProtectedBookMaintenanceRejection.ArtifactVerificationFailed.class,
            assertThrows(
                    ProtectedBookMaintenanceRejectionException.class,
                    () -> maintenanceStore().verifyBackupArtifact(artifactPath, keyPath))
                .rejection());

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

  @Test
  void backupArtifactVerification_translatesArtifactReadAndSnapshotWriteIoFailures() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath unreadableArtifact = fileSystem.path("\\backup.fgba");
      unreadableArtifact.exists = true;
      unreadableArtifact.regularFile = true;
      IOException artifactReadCause = new IOException("simulated artifact read failure");
      unreadableArtifact.failNewByteChannelWith(artifactReadCause);

      IllegalStateException artifactReadFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  maintenanceStore()
                      .verifyBackupArtifact(unreadableArtifact, fileSystem.path("\\backup.key")));
      assertEquals(
          "Failed to read the selected backup artifact.", artifactReadFailure.getMessage());
      assertSame(artifactReadCause, artifactReadFailure.getCause());

      AclFixturePath snapshotStage = fileSystem.path("\\snapshot.sqlite");
      snapshotStage.exists = true;
      snapshotStage.regularFile = true;
      IOException snapshotWriteCause = new IOException("simulated snapshot write failure");
      snapshotStage.failNewByteChannelWith(snapshotWriteCause);

      IllegalStateException snapshotWriteFailure =
          assertThrows(
              IllegalStateException.class,
              () -> writeSnapshot(snapshotStage, new byte[] {1, 2, 3}));
      assertEquals(
          "Failed to stage the encrypted backup artifact snapshot.",
          snapshotWriteFailure.getMessage());
      assertSame(snapshotWriteCause, snapshotWriteFailure.getCause());
    }
  }

  @Test
  void protectedBookMaintenanceStore_rejectsForeignVerifiedBookHandles() {
    Path artifactPath = tempDirectory.resolve("foreign-verified-book.sqlite");
    try (ProtectedBookMaintenanceStore.VerifiedBook foreignHandle =
        new ProtectedBookMaintenanceStore.VerifiedBook() {
          @Override
          public Path artifactPath() {
            return artifactPath;
          }

          @Override
          public void close() {}
        }) {

      assertEquals(
          "The SQLite maintenance store requires one verified SQLite book handle.",
          assertThrows(
                  IllegalArgumentException.class,
                  () ->
                      SqliteProtectedBookMaintenanceArtifactStore.requireVerifiedBook(
                          foreignHandle))
              .getMessage());
    }
  }

  private static void writeSnapshot(Path snapshotPath, byte[] snapshot) {
    try {
      MethodHandle writeSnapshot =
          MethodHandles.privateLookupIn(SqliteBackupArtifactVerifier.class, MethodHandles.lookup())
              .findStatic(
                  SqliteBackupArtifactVerifier.class,
                  "writeSnapshot",
                  MethodType.methodType(void.class, Path.class, byte[].class));
      writeSnapshot.invoke(snapshotPath, snapshot);
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new AssertionError("Failed to invoke encrypted backup snapshot staging.", throwable);
    }
  }

  @Test
  void verification_reportsAMissingBookBeforeThePassphraseSourceIsResolved() {
    Path missingBookPath = tempDirectory.resolve("resolved-missing-book.sqlite");
    SqliteProtectedBookMaintenanceStore store =
        new SqliteProtectedBookMaintenanceStore(
            (bookPath, passphraseSource, intent) ->
                ContractDecision.accepted(
                    SqliteBookPassphrase.fromUtf8Bytes(
                        "resolved missing book test",
                        TEST_BOOK_KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8))));

    assertVerificationFailure(
        acceptedValue(
            store.verifyInitializedBook(
                localAccess(bookAccess(missingBookPath)),
                ProtectedBookMaintenanceArtifactRole.LIVE_BOOK)),
        missingBookPath,
        ProtectedBookVerificationFailure.MISSING);
  }

  @Test
  void verification_reportsAMissingBookWhenItDisappearsAfterPassphraseResolution() {
    Path bookPath = tempDirectory.resolve("disappeared-after-resolution.sqlite");
    BookAccess bookAccess = bookAccess(bookPath);
    initializeBook(bookAccess);
    SqliteProtectedBookMaintenanceStore store =
        new SqliteProtectedBookMaintenanceStore(
            (resolvedBookPath, passphraseSource, intent) -> {
              try {
                Files.delete(resolvedBookPath);
              } catch (java.io.IOException exception) {
                throw new java.io.UncheckedIOException(exception);
              }
              return ContractDecision.accepted(
                  SqliteBookPassphrase.fromUtf8Bytes(
                      "disappeared book test",
                      TEST_BOOK_KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            });

    assertVerificationFailure(
        acceptedValue(
            store.verifyInitializedBook(
                localAccess(bookAccess), ProtectedBookMaintenanceArtifactRole.LIVE_BOOK)),
        bookPath,
        ProtectedBookVerificationFailure.MISSING);
  }

  @Test
  void verification_rejectsAnInvalidLiveBookPathAfterThePassphraseSourceHasResolved()
      throws Exception {
    Path bookDirectory = Files.createDirectory(tempDirectory.resolve("live-book-directory"));
    SqliteProtectedBookMaintenanceStore store =
        new SqliteProtectedBookMaintenanceStore(
            (bookPath, passphraseSource, intent) ->
                ContractDecision.accepted(
                    SqliteBookPassphrase.fromUtf8Bytes(
                        "resolved directory book test",
                        TEST_BOOK_KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8))));

    ProtectedBookMaintenanceRejection.ArtifactPathInvalid rejection =
        assertInstanceOf(
            ProtectedBookMaintenanceRejection.ArtifactPathInvalid.class,
            assertThrows(
                    ProtectedBookMaintenanceRejectionException.class,
                    () ->
                        store.verifyInitializedBook(
                            localAccess(bookAccess(bookDirectory)),
                            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK))
                .rejection());
    assertEquals(ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, rejection.artifactRole());
    assertEquals(bookDirectory.toAbsolutePath().normalize(), rejection.artifactPath());
  }
}
