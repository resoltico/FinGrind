package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenancePathFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowSourceMember;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowSourceMembers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Exercises real maintenance-store error handling at the durable attestation boundary. */
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
    Path artifactPath = tempDirectory.resolve("signed-unusable-key.fgba");
    Files.write(
        artifactPath,
        SqliteAttestationTestSupport.signedBackupArtifact(
            "not an encrypted SQLite book".getBytes(java.nio.charset.StandardCharsets.UTF_8),
            sourceVerification,
            UUID.fromString("e1292d51-bccd-414f-8c08-6381f5dc26fb")));
    Path unusableKeyPath = writeArtifact("signed-unusable-key.key", "not a usable key");

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
      AclFixturePath backupKey = fileSystem.path("\\backup.key");
      backupKey.exists = true;
      backupKey.regularFile = true;
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
  void maintenanceNormalization_resolvesAnExistingLeafToItsCanonicalFilesystemSpelling()
      throws Exception {
    Path canonicalLeaf = writeArtifact("Canonical-book.sqlite", "canonical spelling");
    Path alternateSpelling = canonicalLeaf.resolveSibling("canonical-book.sqlite");
    assumeTrue(
        Files.exists(alternateSpelling, java.nio.file.LinkOption.NOFOLLOW_LINKS)
            && Files.isSameFile(canonicalLeaf, alternateSpelling),
        "The fixture filesystem is case-sensitive.");

    assertEquals(
        canonicalLeaf.toRealPath(java.nio.file.LinkOption.NOFOLLOW_LINKS),
        SqliteBookMaintenanceFiles.normalizeExistingSource(alternateSpelling, "bookFilePath"));
  }

  @Test
  void maintenanceNormalization_rejectsAnIntermediateSymbolicLinkBeforeCanonicalization()
      throws Exception {
    Path physicalRoot = Files.createDirectory(tempDirectory.resolve("maintenance-physical-root"));
    Path realParent = Files.createDirectory(physicalRoot.resolve("real-parent"));
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(physicalRoot);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(realParent);
    Path intermediateAlias = tempDirectory.resolve("maintenance-intermediate-alias");
    createDirectorySymlinkOrSkip(intermediateAlias, physicalRoot);
    Path selectedPath = intermediateAlias.resolve(realParent.getFileName()).resolve("book.sqlite");

    SqliteCallerPathContractException exception =
        assertThrows(
            SqliteCallerPathContractException.class,
            () ->
                SqliteBookMaintenanceFiles.normalizeOptionalArtifact(selectedPath, "bookFilePath"));

    assertEquals(SqliteCallerPathFailure.PARENT_PATH_COLLISION, exception.pathFailure());
    assertFalse(Files.exists(realParent.resolve("book.sqlite")));
  }

  @Test
  void maintenanceNormalization_doesNotLetDotDotHideAnIntermediateSymbolicLink() throws Exception {
    Path physicalRoot = Files.createDirectory(tempDirectory.resolve("dotdot-physical-root"));
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(physicalRoot);
    Path intermediateAlias = tempDirectory.resolve("dotdot-intermediate-alias");
    createDirectorySymlinkOrSkip(intermediateAlias, physicalRoot);
    Path selectedPath = intermediateAlias.resolve("..").resolve("book.sqlite");

    SqliteCallerPathContractException exception =
        assertThrows(
            SqliteCallerPathContractException.class,
            () ->
                SqliteBookMaintenanceFiles.normalizeOptionalArtifact(selectedPath, "bookFilePath"));

    assertEquals(SqliteCallerPathFailure.PARENT_PATH_COLLISION, exception.pathFailure());
  }

  @Test
  void maintenanceTargetNormalization_createsOnlyMissingOwnerOnlyOutputParents() throws Exception {
    Path backupTarget =
        tempDirectory.resolve("fresh-backup-parent").resolve("nested").resolve("backup.sqlite");
    Path backupKeyTarget =
        tempDirectory.resolve("fresh-key-parent").resolve("nested").resolve("backup.key");
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();

    Path normalizedBackup =
        store.normalizeFinalTarget(
            backupTarget, "backupFilePath", ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET);
    Path normalizedBackupKey =
        store.normalizeFinalTarget(
            backupKeyTarget,
            "backupKeyFilePath",
            ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET);

    assertEquals(
        Objects.requireNonNull(backupTarget.getParent(), "backupTarget parent")
            .toRealPath()
            .resolve(backupTarget.getFileName()),
        normalizedBackup);
    assertEquals(
        Objects.requireNonNull(backupKeyTarget.getParent(), "backupKeyTarget parent")
            .toRealPath()
            .resolve(backupKeyTarget.getFileName()),
        normalizedBackupKey);
    assertCreatedOwnerOnlyDirectory(backupTarget.getParent());
    assertCreatedOwnerOnlyDirectory(backupKeyTarget.getParent());
    assertFalse(Files.exists(backupTarget, java.nio.file.LinkOption.NOFOLLOW_LINKS));
    assertFalse(Files.exists(backupKeyTarget, java.nio.file.LinkOption.NOFOLLOW_LINKS));
  }

  @Test
  void maintenanceSourceNormalization_rejectsAnAbsentLeafWithAnExistingPrivateParent()
      throws Exception {
    Path sourceParent = Files.createDirectory(tempDirectory.resolve("missing-source"));
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(sourceParent);
    Path missingSource = sourceParent.resolve("backup.sqlite");
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();

    ProtectedBookMaintenanceRejection.ArtifactPathInvalid rejection =
        assertInstanceOf(
            ProtectedBookMaintenanceRejection.ArtifactPathInvalid.class,
            assertThrows(
                    ProtectedBookMaintenanceRejectionException.class,
                    () ->
                        store.normalizeExistingSource(
                            missingSource,
                            "backupFilePath",
                            ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE))
                .rejection());

    assertEquals(ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE, rejection.artifactRole());
    assertEquals(missingSource.toAbsolutePath(), rejection.artifactPath());
    assertEquals(
        ProtectedBookMaintenancePathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE,
        rejection.pathFailure());
    assertFalse(Files.exists(missingSource, java.nio.file.LinkOption.NOFOLLOW_LINKS));
  }

  @Test
  void optionalLiveBookInspectionAndRequiredLifecycleSourceUseDistinctLeafPolicies()
      throws Exception {
    Path liveParent = Files.createDirectory(tempDirectory.resolve("optional-live-book"));
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(liveParent);
    Path missingBook = liveParent.resolve("missing.sqlite");
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();

    assertEquals(
        liveParent.toRealPath().resolve(missingBook.getFileName()),
        store.normalizeOptionalInspectionArtifact(
            missingBook, "bookFilePath", ProtectedBookMaintenanceArtifactRole.LIVE_BOOK));

    ProtectedBookMaintenanceRejection.ArtifactPathInvalid rejection =
        assertInstanceOf(
            ProtectedBookMaintenanceRejection.ArtifactPathInvalid.class,
            assertThrows(
                    ProtectedBookMaintenanceRejectionException.class,
                    () ->
                        store.normalizeExistingSource(
                            missingBook,
                            "bookFilePath",
                            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK))
                .rejection());

    assertEquals(ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, rejection.artifactRole());
    assertEquals(
        ProtectedBookMaintenancePathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE,
        rejection.pathFailure());
  }

  @Test
  void maintenanceNormalizationBoundariesRejectRoleCrossing() throws Exception {
    Path source = writeArtifact("boundary-source.sqlite", "maintenance source");
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();

    assertThrows(
        IllegalArgumentException.class,
        () ->
            store.normalizeOptionalInspectionArtifact(
                source, "backupFilePath", ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            store.normalizeFinalTarget(
                source, "backupFilePath", ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            store.normalizeExistingSource(
                source, "backupFilePath", ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET));
  }

  @Test
  void workflowScopeRejectsPairIdentityBeforeCreatingItsRetainedLeaseControlFile()
      throws Exception {
    Path source = writeArtifact("identity-source.sqlite", "maintenance source");
    Path sharedTarget = tempDirectory.resolve("identity-targets").resolve("same.sqlite");
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path normalizedSource =
        store.normalizeExistingSource(
            source, "backupFilePath", ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE);
    Path normalizedTarget =
        store.normalizeFinalTarget(
            sharedTarget, "bookTargetPath", ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET);
    Path canonicalParent =
        Objects.requireNonNull(normalizedTarget.getParent(), "normalizedTarget parent")
            .toRealPath();
    Path controlFile = SqliteMaintenanceLeaseArtifacts.controlFilePath(canonicalParent);

    ProtectedBookMaintenanceRejection.PairTargetsConflict rejection =
        assertInstanceOf(
            ProtectedBookMaintenanceRejection.PairTargetsConflict.class,
            assertThrows(
                    ProtectedBookMaintenanceRejectionException.class,
                    () ->
                        store.acquireWorkflowScope(
                            new WorkflowSourceMembers(
                                List.of(
                                    new WorkflowSourceMember(
                                        normalizedSource,
                                        ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE))),
                            normalizedTarget,
                            ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                            normalizedTarget,
                            ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET))
                .rejection());

    assertEquals(normalizedTarget, rejection.bookTargetPath());
    assertEquals(normalizedTarget, rejection.generatedSecretTargetPath());
    assertFalse(Files.exists(controlFile, java.nio.file.LinkOption.NOFOLLOW_LINKS));
  }

  @Test
  void workflowScopeRejectsTheLaterSourceRoleBeforePairTargetAdmissionWhenSourcesAlias()
      throws Exception {
    Path firstSource = writeArtifact("source-identity/live.sqlite", "maintenance source");
    Path aliasParent = tempDirectory.resolve("source-identity-alias");
    Files.createDirectories(aliasParent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(aliasParent);
    Path laterSource = aliasParent.resolve("live-key-alias.key");
    Files.createLink(laterSource, firstSource);
    Path bookTarget = tempDirectory.resolve("source-identity-targets/backup.fgba");
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path normalizedFirst =
        store.normalizeExistingSource(
            firstSource, "bookFilePath", ProtectedBookMaintenanceArtifactRole.LIVE_BOOK);
    Path normalizedLater =
        store.normalizeExistingSource(
            laterSource,
            "bookKeyFilePath",
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK_KEY_SOURCE);
    Path normalizedBookTarget =
        store.normalizeFinalTarget(
            bookTarget, "backupFilePath", ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET);
    Path canonicalTargetParent =
        Objects.requireNonNull(normalizedBookTarget.getParent(), "normalizedBookTarget parent")
            .toRealPath();
    Path directoryControl = SqliteMaintenanceLeaseArtifacts.controlFilePath(canonicalTargetParent);

    ProtectedBookMaintenanceRejection.ArtifactPathInvalid rejection =
        assertInstanceOf(
            ProtectedBookMaintenanceRejection.ArtifactPathInvalid.class,
            assertThrows(
                    ProtectedBookMaintenanceRejectionException.class,
                    () ->
                        store.acquireWorkflowScope(
                            new WorkflowSourceMembers(
                                List.of(
                                    new WorkflowSourceMember(
                                        normalizedFirst,
                                        ProtectedBookMaintenanceArtifactRole.LIVE_BOOK),
                                    new WorkflowSourceMember(
                                        normalizedLater,
                                        ProtectedBookMaintenanceArtifactRole
                                            .LIVE_BOOK_KEY_SOURCE))),
                            normalizedBookTarget,
                            ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                            normalizedBookTarget,
                            ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET))
                .rejection());

    assertEquals(
        ProtectedBookMaintenanceArtifactRole.LIVE_BOOK_KEY_SOURCE, rejection.artifactRole());
    assertEquals(normalizedLater, rejection.artifactPath());
    assertEquals(
        ProtectedBookMaintenancePathFailure.SOURCE_ARTIFACT_IDENTITY_DUPLICATED,
        rejection.pathFailure());
    assertFalse(Files.exists(directoryControl, java.nio.file.LinkOption.NOFOLLOW_LINKS));
  }

  @Test
  void lockedSourceRevalidationRejectsAPhysicalSourceSubstitution() throws Exception {
    Path source =
        writeArtifact("source-identity-changed/live.sqlite", "original maintenance source");
    Path replacement =
        writeArtifact(
            "source-identity-changed/replacement.sqlite", "replacement maintenance source");
    Path normalizedSource =
        maintenanceStore()
            .normalizeExistingSource(
                source, "bookFilePath", ProtectedBookMaintenanceArtifactRole.LIVE_BOOK);
    String lockedIdentity = SqliteObjectCoordinationArtifacts.physicalIdentity(normalizedSource);
    SqliteOwnedHeldLease heldSourceLease =
        SqliteOwnedHeldLease.acquire(
            new SqliteHeldLease(normalizedSource, lockedIdentity, () -> {}));
    try {
      Files.move(
          replacement,
          normalizedSource,
          java.nio.file.StandardCopyOption.ATOMIC_MOVE,
          java.nio.file.StandardCopyOption.REPLACE_EXISTING);

      SqliteCallerPathContractException rejection =
          assertThrows(
              SqliteCallerPathContractException.class,
              () ->
                  SqliteWorkflowScopeRequests.requireSourcesStillMatchLockedIdentities(
                      new WorkflowSourceMembers(
                          List.of(
                              new WorkflowSourceMember(
                                  normalizedSource,
                                  ProtectedBookMaintenanceArtifactRole.LIVE_BOOK))),
                      Map.of(
                          SqliteProtectedBookPathIdentity.normalizedSpelling(normalizedSource),
                          heldSourceLease)));

      assertEquals(normalizedSource, rejection.requestedPath());
      assertEquals(
          SqliteCallerPathFailure.SOURCE_ARTIFACT_IDENTITY_CHANGED, rejection.pathFailure());
    } finally {
      heldSourceLease.release();
    }
  }

  @Test
  void maintenanceNormalization_rejectsAnExistingNonRegularLeafBeforeItCanBecomeAnIdentity()
      throws Exception {
    Path directoryLeaf = Files.createDirectory(tempDirectory.resolve("nonregular-book.sqlite"));

    SqliteCallerPathContractException exception =
        assertThrows(
            SqliteCallerPathContractException.class,
            () ->
                SqliteBookMaintenanceFiles.normalizeExistingSource(directoryLeaf, "bookFilePath"));

    assertEquals(
        SqliteCallerPathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE, exception.pathFailure());
  }

  private static void createDirectorySymlinkOrSkip(Path link, Path target) throws IOException {
    try {
      Files.createSymbolicLink(link, target);
    } catch (UnsupportedOperationException | java.nio.file.FileSystemException unavailable) {
      assumeTrue(false, "host filesystem cannot create symbolic links: " + unavailable);
    }
  }

  private static void assertCreatedOwnerOnlyDirectory(Path directory) throws IOException {
    assertTrue(Files.isDirectory(directory, java.nio.file.LinkOption.NOFOLLOW_LINKS));
    if (directory.getFileSystem().supportedFileAttributeViews().contains("posix")) {
      assertEquals(
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE),
          Files.getPosixFilePermissions(directory, java.nio.file.LinkOption.NOFOLLOW_LINKS));
    }
  }
}
