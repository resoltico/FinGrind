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
import dev.erst.fingrind.core.attestation.AttestationAppendOutcome;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationLifecycleMutationProjection;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.core.attestation.AttestationStaleHeadException;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.core.attestation.AttestationVerifier;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Exercises durable maintenance-store runtime and backup-artifact verification failures. */
class SqliteMaintenanceStoreErrorPathTest extends SqliteArtifactPublicationTestSupport {
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
                          java.math.BigInteger.TWO,
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
          SqliteAttestedOperationAppender.retriesStaleHead(
              AttestationOperationKind.BACKUP_CREATED, acknowledgement));
      assertFalse(
          SqliteAttestedOperationAppender.retriesStaleHead(
              AttestationOperationKind.BACKUP_CREATED, null));
      assertFalse(
          SqliteAttestedOperationAppender.retriesStaleHead(
              AttestationOperationKind.REKEY_BOOK, acknowledgement));

      AttestationVerification backupVerification =
          assertInstanceOf(
                  AttestationAppendOutcome.Appended.class,
                  store.appendAttestedOperation(
                      verifiedBook,
                      AttestationOperationKind.BACKUP_CREATED,
                      Instant.parse("2026-07-21T12:00:00Z"),
                      AttestationLifecycleMutationProjection.backupBook(
                          AttestationOperationKind.BACKUP_CREATED.wireToken(), acknowledgement),
                      SqliteAttestationTestSupport.authorizer(),
                      acknowledgement))
              .verification();

      assertEquals(
          sourceVerification.headOrder().add(java.math.BigInteger.ONE),
          backupVerification.headOrder());
    }

    AttestationStaleHeadException staleHead =
        new AttestationStaleHeadException(new byte[32], new byte[32], java.math.BigInteger.ONE);
    AtomicInteger attempts = new AtomicInteger();
    assertEquals(
        "accepted",
        SqliteAttestedOperationAppender.retryStaleHead(
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
            AttestationStaleHeadException.class,
            () ->
                SqliteAttestedOperationAppender.retryStaleHead(
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
  void signedBackupArtifactWithAnUnusableSelectedKey_isClassifiedAsKeySourceFailure()
      throws Exception {
    Instant initializedAt = Instant.parse("2026-07-21T12:00:00Z");
    AttestationEvidence genesis =
        SqliteAttestationTestSupport.genesis(
            SqlitePostingFactFixtureSupport.bookIdentity(), initializedAt);
    AttestationVerification sourceVerification = AttestationVerifier.verifyBook(List.of(genesis));
    Path artifactPath = writeArtifact("signed-unusable-key.fgba", "");
    Files.write(
        artifactPath,
        SqliteAttestationTestSupport.signedBackupArtifact(
            "not an encrypted SQLite book".getBytes(java.nio.charset.StandardCharsets.UTF_8),
            sourceVerification,
            UUID.fromString("e1292d51-bccd-414f-8c08-6381f5dc26fb")));
    Path unusableKeyPath = writeArtifact("signed-unusable-key.key", "");

    ProtectedBookMaintenanceRejection.ArtifactVerificationFailed rejection =
        assertInstanceOf(
            ProtectedBookMaintenanceRejection.ArtifactVerificationFailed.class,
            assertThrows(
                    ProtectedBookMaintenanceRejectionException.class,
                    () -> maintenanceStore().verifyBackupArtifact(artifactPath, unusableKeyPath))
                .rejection());

    assertEquals(ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_SOURCE, rejection.artifactRole());
    assertEquals(unusableKeyPath.toAbsolutePath().normalize(), rejection.artifactPath());
    assertEquals(
        ProtectedBookVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED,
        rejection.verificationFailure());
  }

  @Test
  void backupArtifactVerifier_preservesUnexpectedKeyLoaderFailuresAsKeySourceEvidence()
      throws Exception {
    Path keyPath = writeArtifact("unexpected-key-loader.key", "not reached");
    IllegalStateException loaderFailure = new IllegalStateException("injected key loader failure");

    RuntimeException failure =
        assertThrows(
            RuntimeException.class,
            () ->
                SqliteBackupArtifactVerifier.loadBackupKey(
                    keyPath,
                    ignored -> {
                      throw loaderFailure;
                    }));

    assertEquals("Backup artifact key cannot be opened.", failure.getMessage());
    assertSame(loaderFailure, failure.getCause());
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
  void backupArtifactVerifierMapsEachInvalidSelectedSourceToItsOwnArtifactRole() throws Exception {
    Path artifactDirectory = Files.createDirectory(tempDirectory.resolve("artifact-directory"));
    Path keyDirectory = Files.createDirectory(tempDirectory.resolve("key-directory"));

    ProtectedBookMaintenanceRejection.ArtifactPathInvalid artifactRejection =
        assertInstanceOf(
            ProtectedBookMaintenanceRejection.ArtifactPathInvalid.class,
            assertThrows(
                    ProtectedBookMaintenanceRejectionException.class,
                    () ->
                        SqliteBackupArtifactVerifier.normalizeBackupArtifactPath(artifactDirectory))
                .rejection());
    assertEquals(
        ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE, artifactRejection.artifactRole());
    assertEquals(artifactDirectory.toAbsolutePath().normalize(), artifactRejection.artifactPath());

    ProtectedBookMaintenanceRejection.ArtifactPathInvalid keyRejection =
        assertInstanceOf(
            ProtectedBookMaintenanceRejection.ArtifactPathInvalid.class,
            assertThrows(
                    ProtectedBookMaintenanceRejectionException.class,
                    () -> SqliteBackupArtifactVerifier.normalizeBackupKeyPath(keyDirectory))
                .rejection());
    assertEquals(
        ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_SOURCE, keyRejection.artifactRole());
    assertEquals(keyDirectory.toAbsolutePath().normalize(), keyRejection.artifactPath());
  }

  @Test
  void backupArtifactVerification_translatesArtifactReadAndSnapshotWriteIoFailures() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath unreadableArtifact = fileSystem.path("\\backup.fgba");
      unreadableArtifact.exists = true;
      unreadableArtifact.regularFile = true;
      unreadableArtifact.posixPermissions =
          Set.of(
              java.nio.file.attribute.PosixFilePermission.OWNER_READ,
              java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
      AclFixturePath backupKey = fileSystem.path("\\backup.key");
      backupKey.exists = true;
      backupKey.regularFile = true;
      backupKey.posixPermissions =
          Set.of(
              java.nio.file.attribute.PosixFilePermission.OWNER_READ,
              java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
      IOException artifactReadCause = new IOException("simulated artifact read failure");
      unreadableArtifact.failNewByteChannelWith(artifactReadCause);

      IllegalStateException artifactReadFailure =
          assertThrows(
              IllegalStateException.class,
              () -> maintenanceStore().verifyBackupArtifact(unreadableArtifact, backupKey));
      assertEquals(
          "Failed to read the selected backup artifact.", artifactReadFailure.getMessage());
      assertSame(artifactReadCause, artifactReadFailure.getCause());

      AclFixturePath snapshotStage = fileSystem.path("\\snapshot.sqlite");
      snapshotStage.exists = true;
      snapshotStage.regularFile = true;
      snapshotStage.posixPermissions =
          Set.of(
              java.nio.file.attribute.PosixFilePermission.OWNER_READ,
              java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
      IOException snapshotWriteCause = new IOException("simulated snapshot write failure");
      snapshotStage.failNewByteChannelWith(snapshotWriteCause);

      IllegalStateException snapshotWriteFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteBackupArtifactVerifier.writeSnapshot(snapshotStage, new byte[] {1, 2, 3}));
      assertEquals(
          "Failed to stage the encrypted backup artifact snapshot.",
          snapshotWriteFailure.getMessage());
      assertSame(snapshotWriteCause, snapshotWriteFailure.getCause());

      AclFixturePath zeroProgressSnapshot = fileSystem.path("\\zero-progress-snapshot.sqlite");
      zeroProgressSnapshot.exists = true;
      zeroProgressSnapshot.regularFile = true;
      zeroProgressSnapshot.posixPermissions =
          Set.of(
              java.nio.file.attribute.PosixFilePermission.OWNER_READ,
              java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
      zeroProgressSnapshot.returnZeroProgressFromNextWrite();

      IllegalStateException zeroProgressFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteBackupArtifactVerifier.writeSnapshot(
                      zeroProgressSnapshot, new byte[] {1, 2, 3}));
      assertEquals(
          "Failed to stage the encrypted backup artifact snapshot.",
          zeroProgressFailure.getMessage());
      assertEquals(
          "Failed to write the complete encrypted backup artifact snapshot.",
          java.util.Objects.requireNonNull(zeroProgressFailure.getCause()).getMessage());
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

  @Test
  void workflowScopeFailureMapsTheExactBookTargetRole() {
    Path source = tempDirectory.resolve("workflow-role-source.sqlite");
    Path bookTarget = tempDirectory.resolve("workflow-role-target.sqlite");
    Path secretTarget = tempDirectory.resolve("workflow-role-target.key");
    SqliteProtectedBookMaintenanceStore store =
        new SqliteProtectedBookMaintenanceStore(
            KEY_FILE_RESOLVER,
            (sourceMembers,
                ignoredBookTarget,
                ignoredBookRole,
                ignoredSecretTarget,
                ignoredSecretRole) -> {
              throw new SqliteCallerPathContractException(
                  bookTarget,
                  SqliteCallerPathFailure.PARENT_OWNER_ONLY_REQUIRED,
                  "injected book-target scope failure");
            });

    ProtectedBookMaintenanceRejectionException failure =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                store.acquireWorkflowScope(
                    new ProtectedBookMaintenanceStore.WorkflowSourceMembers(
                        List.of(
                            new ProtectedBookMaintenanceStore.WorkflowSourceMember(
                                source, ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE))),
                    bookTarget,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                    secretTarget,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));

    assertEquals(
        ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
        assertInstanceOf(
                ProtectedBookMaintenanceRejection.ArtifactPathInvalid.class, failure.rejection())
            .artifactRole());
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

  @Test
  void verification_rejectsARegularBookReplacedByADirectoryAfterPassphraseResolution()
      throws Exception {
    Path bookPath = writeArtifact("replaced-live-book.sqlite", "temporary book bytes");
    SqliteProtectedBookMaintenanceStore store =
        new SqliteProtectedBookMaintenanceStore(
            (resolvedBookPath, passphraseSource, intent) -> {
              try {
                Files.delete(resolvedBookPath);
                Files.createDirectory(resolvedBookPath);
              } catch (IOException exception) {
                throw new java.io.UncheckedIOException(exception);
              }
              return ContractDecision.accepted(
                  SqliteBookPassphrase.fromUtf8Bytes(
                      "replaced regular book test",
                      TEST_BOOK_KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            });

    ProtectedBookMaintenanceRejection.ArtifactPathInvalid rejection =
        assertInstanceOf(
            ProtectedBookMaintenanceRejection.ArtifactPathInvalid.class,
            assertThrows(
                    ProtectedBookMaintenanceRejectionException.class,
                    () ->
                        store.verifyInitializedBook(
                            localAccess(bookAccess(bookPath)),
                            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK))
                .rejection());
    assertEquals(ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, rejection.artifactRole());
    assertEquals(bookPath, rejection.artifactPath());
  }
}
