package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.executor.bookkeeping.BookAuditEvent;
import dev.erst.fingrind.executor.bookkeeping.BookAuditEventKind;
import dev.erst.fingrind.executor.maintenance.MaintenanceCompletion;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceAuditCompensationKind;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceAuditKind;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenancePathFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import dev.erst.fingrind.executor.spi.StagedBookReplacement;
import dev.erst.fingrind.executor.spi.StagedRollbackArtifactDeletion;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Focused coverage tests for staged SQLite protected-book maintenance storage. */
class SqliteProtectedBookMaintenanceStoreCoverageTest
    extends SqliteProtectedBookMaintenanceStoreCoverageTestSupport {

  @Test
  void verifyInitializedBook_mapsMissingBlankAndWrongKeyFailures() throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();

    Path missingBookPath = tempDirectory.resolve("missing.sqlite");
    assertVerificationFailure(
        acceptedValue(
            store.verifyInitializedBook(
                localAccess(bookAccess(missingBookPath)),
                ProtectedBookMaintenanceArtifactRole.LIVE_BOOK)),
        missingBookPath,
        ProtectedBookVerificationFailure.MISSING);

    Path blankBookPath = tempDirectory.resolve("blank.sqlite");
    SqliteStoreFixtureSupport.createEmptySqliteFile(blankBookPath);
    assertVerificationFailure(
        acceptedValue(
            store.verifyInitializedBook(
                localAccess(bookAccess(blankBookPath)),
                ProtectedBookMaintenanceArtifactRole.LIVE_BOOK)),
        blankBookPath,
        ProtectedBookVerificationFailure.BLANK_SQLITE);

    Path initializedBookPath = tempDirectory.resolve("initialized.sqlite");
    BookAccess initializedBookAccess = bookAccess(initializedBookPath);
    initializeBook(initializedBookAccess);
    BookAccess wrongKeyAccess = bookAccess(initializedBookPath, "wrong-secret");
    assertVerificationFailure(
        acceptedValue(
            store.verifyInitializedBook(
                localAccess(wrongKeyAccess), ProtectedBookMaintenanceArtifactRole.LIVE_BOOK)),
        initializedBookPath,
        ProtectedBookVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED);
  }

  @Test
  void verifyInitializedBook_mapsForeignUnsupportedAndIncompleteFailures() throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();

    Path foreignBookPath = tempDirectory.resolve("foreign.sqlite");
    SqliteStoreFixtureSupport.createPostingFactOnlyBook(foreignBookPath);
    assertVerificationFailure(
        acceptedValue(
            store.verifyInitializedBook(
                localAccess(bookAccess(foreignBookPath, SqliteStoreFixtureSupport.TEST_BOOK_KEY)),
                ProtectedBookMaintenanceArtifactRole.LIVE_BOOK)),
        foreignBookPath,
        ProtectedBookVerificationFailure.FOREIGN_SQLITE);

    Path unsupportedBookPath = tempDirectory.resolve("unsupported.sqlite");
    BookAccess unsupportedAccess =
        bookAccess(unsupportedBookPath, SqliteStoreFixtureSupport.TEST_BOOK_KEY);
    SqliteStoreFixtureSupport.initializeBookOnDisk(unsupportedBookPath);
    SqliteStoreFixtureSupport.withStandaloneDatabase(
        unsupportedAccess,
        database ->
            database.executeStatement(
                "pragma user_version = " + (SqliteBookContract.FORMAT_VERSION + 1)));
    assertVerificationFailure(
        acceptedValue(
            store.verifyInitializedBook(
                localAccess(unsupportedAccess), ProtectedBookMaintenanceArtifactRole.LIVE_BOOK)),
        unsupportedBookPath,
        ProtectedBookVerificationFailure.UNSUPPORTED_FORMAT_VERSION);

    Path incompleteBookPath = tempDirectory.resolve("incomplete.sqlite");
    SqliteStoreFixtureSupport.createSchemaOnlyBook(incompleteBookPath);
    assertVerificationFailure(
        acceptedValue(
            store.verifyInitializedBook(
                localAccess(
                    bookAccess(incompleteBookPath, SqliteStoreFixtureSupport.TEST_BOOK_KEY)),
                ProtectedBookMaintenanceArtifactRole.LIVE_BOOK)),
        incompleteBookPath,
        ProtectedBookVerificationFailure.INCOMPLETE_FINGRIND);
  }

  @Test
  void stageBackupPair_exposesOneVerifiableStagedArtifactBeforeCommitAndPublishesOneFinalPair() {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path sourceBookPath = tempDirectory.resolve("books").resolve("source.sqlite");
    BookAccess sourceAccess = bookAccess(sourceBookPath);
    initializeBook(sourceAccess);
    Path backupFilePath = tempDirectory.resolve("backup").resolve("source.sqlite");
    Path backupBookKeyFilePath = tempDirectory.resolve("backup").resolve("source.key");

    try (ProtectedBookMaintenanceStore.VerifiedBook verifiedSourceBook =
            verifiedBook(store, sourceAccess);
        ProtectedBookMaintenanceStore.PreparedPairPublication preparedPairPublication =
            prepareBackupPair(store, backupFilePath, backupBookKeyFilePath);
        StagedBackupPair stagedBackupPair =
            acceptedValue(store.stageBackupPair(verifiedSourceBook, preparedPairPublication))) {
      assertFalse(Files.exists(backupFilePath));
      assertFalse(Files.exists(backupBookKeyFilePath));
      assertInstanceOf(
          ProtectedBookMaintenanceStore.VerifiedBook.class,
          acceptedValue(stagedBackupPair.verifyInitializedBackup()));
      stagedBackupPair.commit();
      stagedBackupPair.commit();
    }

    assertTrue(Files.exists(backupFilePath));
    assertTrue(Files.exists(backupBookKeyFilePath));
    try (ProtectedBookMaintenanceStore.VerifiedBook ignored =
        verifiedBook(
            store,
            new BookAccess(
                backupFilePath, new BookAccess.PassphraseSource.KeyFile(backupBookKeyFilePath)))) {
      assertEquals(backupFilePath.toAbsolutePath().normalize(), ignored.artifactPath());
    }
  }

  @Test
  void stageBackupPair_preservesAnUnownedFilenameShapedSibling() throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path sourceBookPath = tempDirectory.resolve("safety-source").resolve("source.sqlite");
    BookAccess sourceAccess = bookAccess(sourceBookPath);
    initializeBook(sourceAccess);
    Path backupFilePath = tempDirectory.resolve("safety-backup").resolve("backup.sqlite");
    Path backupParent =
        java.util.Objects.requireNonNull(backupFilePath.getParent(), "backup parent");
    Files.createDirectories(backupParent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(backupParent);
    Path backupKeyFilePath = backupParent.resolve("backup.key");
    Path unownedSibling = backupParent.resolve("backup.sqlite.backup-key-unowned.tmp");
    Files.writeString(unownedSibling, "unowned-secret");
    byte[] unownedSiblingBefore = Files.readAllBytes(unownedSibling);

    try (ProtectedBookMaintenanceStore.VerifiedBook verifiedSourceBook =
            verifiedBook(store, sourceAccess);
        ProtectedBookMaintenanceStore.PreparedPairPublication preparedPairPublication =
            prepareBackupPair(store, backupFilePath, backupKeyFilePath);
        StagedBackupPair stagedBackupPair =
            acceptedValue(store.stageBackupPair(verifiedSourceBook, preparedPairPublication))) {
      assertInstanceOf(
          ProtectedBookMaintenanceStore.VerifiedBook.class,
          acceptedValue(stagedBackupPair.verifyInitializedBackup()));
      assertArrayEquals(unownedSiblingBefore, Files.readAllBytes(unownedSibling));
    }

    assertArrayEquals(unownedSiblingBefore, Files.readAllBytes(unownedSibling));
  }

  @Test
  void stageReplacement_rollsBackCleanlyAndCommitsOneFinalSwap() throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();

    Path sourcePath = writeArtifact("replacement-source.sqlite", "replacement");
    Path targetPath = writeArtifact("replacement-target.sqlite", "previous");
    try (StagedBookReplacement stagedReplacement = store.stageReplacement(sourcePath, targetPath)) {
      assertEquals("previous", Files.readString(targetPath));
      assertEquals("replacement", Files.readString(stagedReplacement.stagedBookPath()));
      stagedReplacement.rollback();
    }
    assertEquals("previous", Files.readString(targetPath));

    try (StagedBookReplacement stagedReplacement = store.stageReplacement(sourcePath, targetPath)) {
      stagedReplacement.commit();
    }
    assertEquals("replacement", Files.readString(targetPath));
  }

  @Test
  void stageRollbackArtifactDeletion_rollsBackAndCommitsAsSeparateOutcomes() throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path rollbackArtifactPath = writeArtifact("book.sqlite.rekey-rollback-1.sqlite", "rollback");

    try (StagedRollbackArtifactDeletion stagedDeletion =
        store.stageRollbackArtifactDeletion(rollbackArtifactPath)) {
      assertTrue(Files.exists(rollbackArtifactPath));
      stagedDeletion.rollback();
    }
    assertTrue(Files.exists(rollbackArtifactPath));

    try (StagedRollbackArtifactDeletion stagedDeletion =
        store.stageRollbackArtifactDeletion(rollbackArtifactPath)) {
      stagedDeletion.commit();
    }
    assertFalse(Files.exists(rollbackArtifactPath));
  }

  @Test
  void appendMaintenanceAudit_writesOneEncryptedAuditEventWithoutOneAdjacentPlaintextJournal() {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path bookPath = tempDirectory.resolve("audit").resolve("book.sqlite");
    BookAccess bookAccess = bookAccess(bookPath);
    initializeBook(bookAccess);

    MaintenanceCompletion completion;
    try (ProtectedBookMaintenanceStore.VerifiedBook verifiedBook =
        verifiedBook(store, bookAccess)) {
      completion =
          acceptedValue(
              store.appendMaintenanceAudit(
                  verifiedBook,
                  Instant.parse("2026-05-19T11:45:00Z"),
                  ProtectedBookMaintenanceAuditKind.BACKUP_RESTORED));
    }

    assertEquals(MaintenanceCompletion.DONE, completion);
    assertEquals(1, auditEventCount(bookAccess, "BACKUP_RESTORED"));
    assertEquals(
        BookAuditEventKind.BOOK_REKEYED,
        SqliteProtectedBookMaintenanceAuditSupport.maintenanceAuditEvent(
                ProtectedBookMaintenanceAuditKind.BOOK_REKEYED,
                Instant.parse("2026-05-19T11:45:00Z"))
            .kind());
    assertFalse(Files.exists(maintenanceJournalPath(bookPath)));
  }

  @Test
  void resolverFailuresBusyLeasesAndExceptionalRollbackScans_surfaceAsMaintenanceFailures()
      throws Exception {
    Path bookPath = tempDirectory.resolve("resolver-failure").resolve("book.sqlite");
    BookAccess access = bookAccess(bookPath);
    initializeBook(access);
    ContractFailure resolverFailure =
        ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE.failure(
            "resolver-boom", "resolver-hint", "bookFilePath");
    SqliteProtectedBookMaintenanceStore failingStore =
        new SqliteProtectedBookMaintenanceStore(
            (resolvedBookPath, passphraseSource, intent) ->
                ContractDecision.rejected(resolverFailure));

    assertFailedDescriptor(
        failingStore.verifyInitializedBook(
            localAccess(access), ProtectedBookMaintenanceArtifactRole.LIVE_BOOK),
        ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE);

    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path busyExistingBookPath =
        writeArtifact("busy-existing.sqlite", "content").toAbsolutePath().normalize();
    SqliteNativeRuntimeActivity.recordOpeningConnection(busyExistingBookPath);
    try {
      ProtectedBookMaintenanceStore.LeaseBusy existingBusy =
          assertInstanceOf(
              ProtectedBookMaintenanceStore.LeaseBusy.class,
              acquireLiveArtifactLease(store, busyExistingBookPath));
      assertEquals(busyExistingBookPath, existingBusy.artifactPath());
    } finally {
      SqliteNativeRuntimeActivity.recordConnectionClosed(busyExistingBookPath);
    }

    Path busyManagedPath =
        tempDirectory.resolve("busy-managed").resolve("book.sqlite").toAbsolutePath().normalize();
    Path busyManagedParent = busyManagedPath.getParent();
    if (busyManagedParent == null) {
      throw new AssertionError("Expected one managed target parent directory.");
    }
    Files.createDirectories(busyManagedParent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(busyManagedParent);
    Path busyManagedLeasePath =
        busyManagedPath.resolveSibling(
            busyManagedPath.getFileName().toString()
                + ".fingrind-maintenance-"
                + SqliteProcessIdentity.current().coordinationToken()
                + ".lock");
    Files.createDirectory(busyManagedLeasePath);
    SqliteBookFileSecurity.hardenDirectory(busyManagedLeasePath);
    ProtectedBookMaintenanceStore.LeaseBusy managedBusy =
        assertInstanceOf(
            ProtectedBookMaintenanceStore.LeaseBusy.class,
            acquireRestoredTargetLease(store, busyManagedPath));
    assertEquals(busyManagedPath, managedBusy.artifactPath());

    try (AclFixtureFileSystem fileSystem =
        AclFixtureFileSystem.withViews(java.util.Set.of("basic"))) {
      AclFixturePath rollbackParent = fileSystem.path("\\rollback-parent");
      rollbackParent.exists = true;
      rollbackParent.regularFile = false;
      rollbackParent.failNewDirectoryStreamWith(new IOException("rollback-scan-boom"));
      AclFixturePath rollbackBookPath = fileSystem.path("\\rollback-parent\\book.sqlite");
      rollbackBookPath.exists = true;
      rollbackBookPath.regularFile = true;

      IllegalStateException rollbackScanFailure =
          assertThrows(
              IllegalStateException.class, () -> store.staleRollbackArtifacts(rollbackBookPath));
      assertTrue(
          NullTestSupport.messageOf(rollbackScanFailure)
              .contains("Failed to inspect FinGrind SQLite rollback artifacts"));
    }
  }

  @Test
  void leaseVerificationAndReplacement_translateCallerPathContractRejections() throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();

    Path existingDirectory = tempDirectory.resolve("lease-directory");
    Files.createDirectories(existingDirectory);
    ProtectedBookMaintenanceRejection.ArtifactPathInvalid existingLeaseRejection =
        assertArtifactPathInvalid(
            assertThrows(
                    ProtectedBookMaintenanceRejectionException.class,
                    () ->
                        store.acquireExistingArtifactLease(
                            existingDirectory.toAbsolutePath().normalize(),
                            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK))
                .rejection());
    assertEquals(
        ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, existingLeaseRejection.artifactRole());
    assertEquals(
        ProtectedBookMaintenancePathFailure.TARGET_MUST_BE_REGULAR_NON_SYMLINK_FILE,
        existingLeaseRejection.pathFailure());

    Path managedParentBlocker = tempDirectory.resolve("managed-parent-blocker");
    Files.writeString(managedParentBlocker, "not-a-directory");
    Path invalidManagedTarget = managedParentBlocker.resolve("book.sqlite");
    ProtectedBookMaintenanceRejection.ArtifactPathInvalid managedLeaseRejection =
        assertArtifactPathInvalid(
            assertThrows(
                    ProtectedBookMaintenanceRejectionException.class,
                    () ->
                        store.acquireManagedArtifactLease(
                            invalidManagedTarget,
                            ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET))
                .rejection());
    assertEquals(
        ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET, managedLeaseRejection.artifactRole());
    assertEquals(
        ProtectedBookMaintenancePathFailure.PARENT_PATH_COLLISION,
        managedLeaseRejection.pathFailure());

    Path sourceBookPath = writeArtifact("replacement-source-valid.sqlite", "replacement");
    Path replacementParentBlocker = tempDirectory.resolve("replacement-parent-blocker");
    Files.writeString(replacementParentBlocker, "not-a-directory");
    ProtectedBookMaintenanceRejection.ArtifactPathInvalid replacementRejection =
        assertArtifactPathInvalid(
            assertThrows(
                    ProtectedBookMaintenanceRejectionException.class,
                    () ->
                        store.stageReplacement(
                            sourceBookPath, replacementParentBlocker.resolve("target.sqlite")))
                .rejection());
    assertEquals(
        ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET, replacementRejection.artifactRole());
    assertEquals(
        ProtectedBookMaintenancePathFailure.PARENT_PATH_COLLISION,
        replacementRejection.pathFailure());

    Path directoryBookPath = tempDirectory.resolve("book-directory");
    Files.createDirectories(directoryBookPath);
    Path directoryBookKeyPath =
        tempDirectory.resolve("book-keys").resolve(directoryBookPath.getFileName() + ".key");
    writeSecureKeyFile(directoryBookKeyPath, TEST_BOOK_KEY);
    ProtectedBookMaintenanceRejection.ArtifactPathInvalid verificationRejection =
        assertArtifactPathInvalid(
            assertThrows(
                    ProtectedBookMaintenanceRejectionException.class,
                    () ->
                        store.verifyInitializedBook(
                            localAccess(bookAccess(directoryBookPath)),
                            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK))
                .rejection());
    assertEquals(
        ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, verificationRejection.artifactRole());
    assertEquals(
        ProtectedBookMaintenancePathFailure.TARGET_MUST_BE_REGULAR_NON_SYMLINK_FILE,
        verificationRejection.pathFailure());
  }

  @Test
  void stagedBackupPair_and_replacement_coverGuardAndFailurePaths() throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path sourceBookPath = tempDirectory.resolve("guarded-stage").resolve("source.sqlite");
    BookAccess sourceAccess = bookAccess(sourceBookPath);
    initializeBook(sourceAccess);

    Path closedBackupFilePath = tempDirectory.resolve("guarded-stage").resolve("closed.sqlite");
    Path closedBackupKeyFilePath = tempDirectory.resolve("guarded-stage").resolve("closed.key");
    try (ProtectedBookMaintenanceStore.VerifiedBook verifiedSourceBook =
            verifiedBook(store, sourceAccess);
        ProtectedBookMaintenanceStore.PreparedPairPublication preparedPairPublication =
            prepareBackupPair(store, closedBackupFilePath, closedBackupKeyFilePath);
        StagedBackupPair stagedBackupPair =
            acceptedValue(store.stageBackupPair(verifiedSourceBook, preparedPairPublication))) {
      stagedBackupPair.close();
    }
    assertFalse(Files.exists(closedBackupFilePath));
    assertFalse(Files.exists(closedBackupKeyFilePath));

    Path backupDirectoryTarget = tempDirectory.resolve("guarded-stage").resolve("backup-directory");
    Files.createDirectories(backupDirectoryTarget);
    Files.writeString(backupDirectoryTarget.resolve("child.txt"), "child");
    ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists rejected =
        assertInstanceOf(
            ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists.class,
            assertThrows(
                    ProtectedBookMaintenanceRejectionException.class,
                    () ->
                        prepareBackupPair(
                            store,
                            backupDirectoryTarget,
                            tempDirectory.resolve("guarded-stage").resolve("backup-directory.key")))
                .rejection());
    assertEquals(backupDirectoryTarget, rejected.backupFilePath());

    Path publishedBackupFilePath =
        tempDirectory.resolve("guarded-stage").resolve("published.sqlite");
    Path failingKeyDirectory = tempDirectory.resolve("guarded-stage").resolve("key-directory");
    Files.createDirectories(failingKeyDirectory);
    Files.writeString(failingKeyDirectory.resolve("child.txt"), "child");
    assertThrows(
        dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException.class,
        () -> prepareBackupPair(store, publishedBackupFilePath, failingKeyDirectory));
    assertFalse(Files.exists(publishedBackupFilePath));

    Path missingTargetPath = tempDirectory.resolve("guarded-stage").resolve("new-target.sqlite");
    try (StagedBookReplacement stagedReplacement =
        store.stageReplacement(sourceBookPath, missingTargetPath)) {
      assertFalse(Files.exists(missingTargetPath));
      stagedReplacement.close();
    }
    assertFalse(Files.exists(missingTargetPath));

    try (StagedBookReplacement stagedReplacement =
        store.stageReplacement(sourceBookPath, missingTargetPath)) {
      stagedReplacement.commit();
      stagedReplacement.commit();
      stagedReplacement.rollback();
      stagedReplacement.close();
    }
    assertTrue(Files.exists(missingTargetPath));

    IllegalStateException stageReplacementFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                store.stageReplacement(
                    tempDirectory.resolve("guarded-stage").resolve("missing-source.sqlite"),
                    tempDirectory.resolve("guarded-stage").resolve("failed-target.sqlite")));
    assertTrue(
        NullTestSupport.messageOf(stageReplacementFailure)
            .contains("Failed to stage the FinGrind SQLite book replacement"));
  }

  @Test
  void stagedRollbackDeletion_and_directArtifactValidation_coverNoopGuards() throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path rollbackArtifactPath = writeArtifact("guarded-rollback.sqlite", "rollback");

    try (StagedRollbackArtifactDeletion stagedDeletion =
        store.stageRollbackArtifactDeletion(rollbackArtifactPath)) {
      stagedDeletion.close();
    }
    assertTrue(Files.exists(rollbackArtifactPath));

    try (StagedRollbackArtifactDeletion stagedDeletion =
        store.stageRollbackArtifactDeletion(rollbackArtifactPath)) {
      stagedDeletion.commit();
      stagedDeletion.commit();
      stagedDeletion.rollback();
      stagedDeletion.close();
    }
    assertFalse(Files.exists(rollbackArtifactPath));

    ProtectedBookMaintenanceRejectionException invalidRollbackArtifact =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () -> store.stageRollbackArtifactDeletion(tempDirectory.toAbsolutePath().normalize()));
    ProtectedBookMaintenanceRejection.ArtifactPathInvalid rejection =
        assertInstanceOf(
            ProtectedBookMaintenanceRejection.ArtifactPathInvalid.class,
            invalidRollbackArtifact.rejection());
    assertEquals(ProtectedBookMaintenanceArtifactRole.ROLLBACK_ARTIFACT, rejection.artifactRole());
    assertEquals(
        tempDirectory.toAbsolutePath().normalize(),
        rejection.artifactPath().toAbsolutePath().normalize());
    assertEquals(
        ProtectedBookMaintenancePathFailure.TARGET_MUST_BE_REGULAR_NON_SYMLINK_FILE,
        rejection.pathFailure());
  }

  @Test
  void maintenanceHelpers_coverBlockingArtifactsLeasesRollbackDiscoveryAndAuditRetraction()
      throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path bookPath = tempDirectory.resolve("helpers").resolve("book.sqlite");
    BookAccess access = bookAccess(bookPath);
    initializeBook(access);
    try (ProtectedBookMaintenanceStore.VerifiedBook verifiedBook = verifiedBook(store, access)) {
      Path walPath = bookPath.resolveSibling(bookPath.getFileName().toString() + "-wal");
      Path rollbackArtifactPath =
          bookPath.resolveSibling(
              bookPath.getFileName().toString() + ".rekey-rollback-test.sqlite");
      Files.writeString(walPath, "wal");
      Files.writeString(rollbackArtifactPath, "rollback");

      assertEquals(
          List.of(
              walPath.toAbsolutePath().normalize(),
              rollbackArtifactPath.toAbsolutePath().normalize()),
          store.blockingArtifactsForBook(bookPath.toAbsolutePath().normalize()));
      assertEquals(
          List.of(
              walPath.toAbsolutePath().normalize(),
              rollbackArtifactPath.toAbsolutePath().normalize()),
          store.blockingArtifactsForBackupSource(bookPath.toAbsolutePath().normalize()));
      assertEquals(
          List.of(rollbackArtifactPath.toAbsolutePath().normalize()),
          store.staleRollbackArtifacts(bookPath.toAbsolutePath().normalize()));
      assertTrue(
          store.isRollbackArtifactForBook(
              bookPath.toAbsolutePath().normalize(),
              rollbackArtifactPath.toAbsolutePath().normalize()));

      try (ProtectedBookMaintenanceStore.HeldLease managedLease =
              acceptedLease(
                  acquireRestoredTargetLease(
                      store, tempDirectory.resolve("managed").resolve("book.sqlite")));
          ProtectedBookMaintenanceStore.HeldLease existingLease =
              acceptedLease(
                  acquireLiveArtifactLease(store, bookPath.toAbsolutePath().normalize()))) {
        assertTrue(
            Files.exists(
                managedLease
                    .artifactPath()
                    .resolveSibling(
                        managedLease.artifactPath().getFileName().toString()
                            + ".fingrind-maintenance-"
                            + SqliteProcessIdentity.current().coordinationToken()
                            + ".lock")));
        assertEquals(bookPath.toAbsolutePath().normalize(), existingLease.artifactPath());
      }

      acceptedValue(
          store.appendMaintenanceAudit(
              verifiedBook,
              Instant.parse("2026-05-19T12:00:00Z"),
              ProtectedBookMaintenanceAuditKind.BACKUP_RESTORED));
      acceptedValue(
          store.appendMaintenanceAudit(
              verifiedBook,
              Instant.parse("2026-05-19T12:05:00Z"),
              ProtectedBookMaintenanceAuditKind.BACKUP_RESTORED));
      assertEquals(2, auditEventCount(access, "BACKUP_RESTORED"));
      assertEquals(
          MaintenanceCompletion.DONE,
          acceptedValue(
              store.appendMaintenanceAuditCompensation(
                  verifiedBook,
                  Instant.parse("2026-05-19T12:05:00Z"),
                  ProtectedBookMaintenanceAuditCompensationKind.REKEY_ROLLBACK_DELETED)));
      assertEquals(2, auditEventCount(access, "BACKUP_RESTORED"));
    }
    assertEquals(1, auditEventCount(access, "REKEY_ROLLBACK_DELETED_COMPENSATED"));
  }

  @Test
  void maintenanceStore_privateFailureAndDispatchPaths_areCovered() throws Throwable {
    Path verifiedBookPath = tempDirectory.resolve("private-verify").resolve("book.sqlite");
    BookAccess verifiedBookAccess = bookAccess(verifiedBookPath);
    initializeBook(verifiedBookAccess);
    BookAccess wrongKeyAccess = bookAccess(verifiedBookPath, "private-verify-wrong-secret");
    try (SqliteBookPassphrase wrongPassphrase =
        SqliteStoreFixtureSupport.loadPassphrase(wrongKeyAccess)) {
      ProtectedBookMaintenanceStore.BookVerification verification =
          VERIFICATION_SUPPORT.verifyResolvedBook(verifiedBookPath, wrongPassphrase);
      assertVerificationFailure(
          verification,
          verifiedBookPath,
          ProtectedBookVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED);
    }

    ProtectedBookMaintenanceStore.BookVerification missingVerification =
        VERIFICATION_SUPPORT.mapInspection(
            verifiedBookPath,
            new BookLifecycleInspection.Missing(SqliteBookContract.FORMAT_VERSION));
    assertVerificationFailure(
        missingVerification, verifiedBookPath, ProtectedBookVerificationFailure.MISSING);

    assertEquals(
        ProtectedBookVerificationFailure.MISSING,
        VERIFICATION_SUPPORT.mapInspectionFailure(BookLifecycleInspection.Status.MISSING));
    IllegalArgumentException initializedStatusFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                VERIFICATION_SUPPORT.mapInspectionFailure(
                    BookLifecycleInspection.Status.INITIALIZED));
    assertTrue(NullTestSupport.messageOf(initializedStatusFailure).contains("INITIALIZED"));
    IllegalStateException nonVerificationFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteProtectedBookVerificationSupport.protectedBookVerificationFailure(
                    ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE.failure(
                        "non-verification", "hint", null)));
    assertTrue(
        NullTestSupport.messageOf(nonVerificationFailure)
            .contains("non-verification contract failure"));

    BookAuditEvent restoredBackupAudit =
        SqliteProtectedBookMaintenanceAuditSupport.maintenanceAuditEvent(
            ProtectedBookMaintenanceAuditKind.BACKUP_RESTORED,
            Instant.parse("2026-05-19T12:20:00Z"));
    assertEquals(BookAuditEventKind.BACKUP_RESTORED, restoredBackupAudit.kind());
    BookAuditEvent restoredRollbackAudit =
        SqliteProtectedBookMaintenanceAuditSupport.maintenanceAuditEvent(
            ProtectedBookMaintenanceAuditKind.REKEY_ROLLBACK_RESTORED,
            Instant.parse("2026-05-19T12:21:00Z"));
    assertEquals(BookAuditEventKind.REKEY_ROLLBACK_RESTORED, restoredRollbackAudit.kind());

    Path stagedParentAsFile = writeArtifact("staged-parent-as-file", "parent");
    IllegalStateException stagedSiblingFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteOwnedStagedArtifact.create(
                    stagedParentAsFile.resolve("book.sqlite"), ".backup-", ".sqlite"));
    assertTrue(
        NullTestSupport.messageOf(stagedSiblingFailure)
            .contains("Failed to create one owned maintenance stage"));

    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath missingParentArtifactPath = fileSystem.path("\\acl-parent\\book.sqlite");
      AclFixturePath missingParent = fileSystem.path("\\acl-parent");
      missingParent.overrideAclView =
          new ThrowingAclFileAttributeView(
              java.util.Objects.requireNonNull(missingParent.aclView, "missingParent.aclView")
                  .getOwner(),
              "parent-acl-boom");
      IllegalStateException parentHardeningFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteProtectedBookStagingFiles.ensureSecureBackupFileParentDirectory(
                      missingParentArtifactPath));
      assertTrue(
          NullTestSupport.messageOf(parentHardeningFailure)
              .contains("Failed to secure the parent directory"));
    }

    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath missingParentArtifactPath = fileSystem.path("\\acl-key-parent\\book.key");
      AclFixturePath missingParent = fileSystem.path("\\acl-key-parent");
      missingParent.overrideAclView =
          new ThrowingAclFileAttributeView(
              java.util.Objects.requireNonNull(missingParent.aclView, "missingParent.aclView")
                  .getOwner(),
              "key-parent-acl-boom");
      IllegalStateException parentHardeningFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteProtectedBookStagingFiles.ensureSecureBackupKeyFileParentDirectory(
                      missingParentArtifactPath));
      assertTrue(
          NullTestSupport.messageOf(parentHardeningFailure)
              .contains("Failed to secure the parent directory"));
    }

    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath parentPath = fileSystem.path("\\acl-books");
      parentPath.exists = true;
      parentPath.regularFile = false;
      java.util.Objects.requireNonNull(parentPath.aclView, "parentPath.aclView")
          .setAcl(
              List.of(
                  ownerDirectoryAccessEntry(
                      java.util.Objects.requireNonNull(parentPath.aclView, "parentPath.aclView")
                          .getOwner())));
      AclFixturePath bookPath = fileSystem.path("\\acl-books\\book.sqlite");
      bookPath.exists = true;
      bookPath.regularFile = true;
      bookPath.overrideAclView =
          new ThrowingAclFileAttributeView(
              java.util.Objects.requireNonNull(bookPath.aclView, "bookPath.aclView").getOwner(),
              "artifact-acl-boom");
      IllegalStateException hardeningFailure =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteProtectedBookStagingFiles.hardenBookArtifacts(bookPath));
      assertTrue(
          NullTestSupport.messageOf(hardeningFailure)
              .contains("Failed to harden the FinGrind protected-book artifacts"));
    }
  }

  @Test
  void maintenanceStore_publicFailurePaths_cleanupStagedArtifactsAndRollbackAuditTransactions()
      throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();

    Path bogusSourcePath = writeArtifact("bogus-source.sqlite", "not-a-sqlite-book");
    BookAccess bogusSourceAccess = bookAccess(bogusSourcePath);
    Path backupFilePath = tempDirectory.resolve("failed-stage").resolve("backup.sqlite");
    Path backupKeyPath = tempDirectory.resolve("failed-stage").resolve("backup.key");
    try (SqliteBookPassphrase bogusPassphrase =
            SqliteStoreFixtureSupport.loadPassphrase(bogusSourceAccess);
        SqliteVerifiedBook bogusVerifiedBook =
            new SqliteVerifiedBook(
                bogusSourcePath.toAbsolutePath().normalize(), bogusPassphrase.copy());
        ProtectedBookMaintenanceStore.PreparedPairPublication preparedPairPublication =
            prepareBackupPair(store, backupFilePath, backupKeyPath)) {
      var failure = failedValue(store.stageBackupPair(bogusVerifiedBook, preparedPairPublication));
      assertEquals(ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE, failure.descriptor());
      assertEquals(
          SqliteProtectedBookStagingSupport.StagingCheckpoint.BACKUP_SOURCE_OPEN.failureMessage(),
          failure.message());
      assertEquals("backupFilePath", failure.argument());
      assertEquals(
          backupFilePath.toAbsolutePath().normalize(),
          java.util.Objects.requireNonNull(failure.paths(), "failure paths").path());
    }
    try (var children =
        Files.list(java.util.Objects.requireNonNull(backupFilePath.getParent(), "backup parent"))) {
      assertFalse(children.anyMatch(path -> path.getFileName().toString().contains(".backup-")));
    }

    Path auditFailureBookPath = tempDirectory.resolve("audit-failure").resolve("book.sqlite");
    BookAccess auditFailureAccess = bookAccess(auditFailureBookPath);
    initializeBook(auditFailureAccess);
    try (ProtectedBookMaintenanceStore.VerifiedBook verifiedBook =
        verifiedBook(store, auditFailureAccess)) {
      withOpenDatabase(
          auditFailureAccess, database -> database.executeStatement("drop table audit_event"));
      assertThrows(
          SqliteNativeException.class,
          () ->
              store.appendMaintenanceAudit(
                  verifiedBook,
                  Instant.parse("2026-05-19T12:30:00Z"),
                  ProtectedBookMaintenanceAuditKind.BACKUP_RESTORED));
      assertThrows(
          SqliteNativeException.class,
          () ->
              store.appendMaintenanceAuditCompensation(
                  verifiedBook,
                  Instant.parse("2026-05-19T12:31:00Z"),
                  ProtectedBookMaintenanceAuditCompensationKind.REKEY_ROLLBACK_DELETED));
    }
    withOpenDatabase(
        auditFailureAccess,
        database ->
            database.executeStatement("create table if not exists maintenance_probe (id integer)"));
  }

  @Test
  void stagedBackupPairRollback_preservesUnownedTargets() throws Throwable {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path sourceBookPath = tempDirectory.resolve("nested-rollback").resolve("source.sqlite");
    BookAccess sourceAccess = bookAccess(sourceBookPath);
    initializeBook(sourceAccess);
    Path finalBackupFilePath = tempDirectory.resolve("nested-rollback").resolve("backup.sqlite");
    Path finalBackupKeyFilePath = tempDirectory.resolve("nested-rollback").resolve("backup.key");

    try (ProtectedBookMaintenanceStore.VerifiedBook verifiedSourceBook =
            verifiedBook(store, sourceAccess);
        ProtectedBookMaintenanceStore.PreparedPairPublication preparedPairPublication =
            prepareBackupPair(store, finalBackupFilePath, finalBackupKeyFilePath);
        StagedBackupPair stagedBackupPair =
            acceptedValue(store.stageBackupPair(verifiedSourceBook, preparedPairPublication))) {
      Files.writeString(finalBackupFilePath, "published-backup");
      Files.writeString(finalBackupKeyFilePath, "published-key");
      setPrivateField(stagedBackupPair, "backupPassphrase", null);
      setPrivateField(stagedBackupPair, "backupFilePublished", true);
      setPrivateField(stagedBackupPair, "backupKeyFilePublished", true);
      stagedBackupPair.rollback();
      assertEquals("published-backup", Files.readString(finalBackupFilePath));
      assertEquals("published-key", Files.readString(finalBackupKeyFilePath));
    }
  }

  @Test
  void stagedReplacement_commitFailurePaths_areCoveredOnRealFilesystemArtifacts()
      throws IOException {
    Path stagedBookPath = tempDirectory.resolve("replace-success").resolve("missing-staged.sqlite");
    Path targetBookPath = writeArtifact("replace-success/target.sqlite", "live-target");
    Path previousTargetBackupPath =
        tempDirectory.resolve("replace-success").resolve("target.previous.sqlite");
    try (StagedBookReplacement stagedReplacement =
        newStagedReplacement(stagedBookPath, targetBookPath, previousTargetBackupPath)) {
      IllegalStateException commitFailure =
          assertThrows(IllegalStateException.class, stagedReplacement::commit);
      assertTrue(
          NullTestSupport.messageOf(commitFailure)
              .contains("Failed to commit the staged FinGrind SQLite replacement"));
      assertTrue(Files.exists(targetBookPath));
      assertFalse(Files.exists(previousTargetBackupPath));
    }

    Path firstMoveFailureStagedBookPath =
        tempDirectory.resolve("replace-first-move-failure").resolve("staged.sqlite");
    Files.createDirectories(
        java.util.Objects.requireNonNull(
            firstMoveFailureStagedBookPath.getParent(), "firstMoveFailureStagedBookPath parent"));
    Files.writeString(firstMoveFailureStagedBookPath, "replacement");
    Path firstMoveFailureTargetBookPath =
        writeArtifact("replace-first-move-failure/target.sqlite", "live-target");
    Path firstMoveFailurePreviousTargetBackupPath =
        tempDirectory.resolve("replace-first-move-failure").resolve("target.previous.sqlite");
    Files.createDirectories(firstMoveFailurePreviousTargetBackupPath);
    Files.writeString(firstMoveFailurePreviousTargetBackupPath.resolve("child.txt"), "occupied");
    try (StagedBookReplacement firstMoveFailureReplacement =
        newStagedReplacement(
            firstMoveFailureStagedBookPath,
            firstMoveFailureTargetBookPath,
            firstMoveFailurePreviousTargetBackupPath)) {
      IllegalStateException firstMoveFailure =
          assertThrows(IllegalStateException.class, firstMoveFailureReplacement::commit);
      assertTrue(
          NullTestSupport.messageOf(firstMoveFailure)
              .contains("Failed to commit the staged FinGrind SQLite replacement"));
      assertTrue(Files.exists(firstMoveFailureTargetBookPath));
    }

    Path missingPreviousStagedBookPath =
        tempDirectory.resolve("replace-missing-previous").resolve("missing-staged.sqlite");
    Path missingPreviousTargetBookPath =
        tempDirectory.resolve("replace-missing-previous").resolve("target.sqlite");
    Path missingPreviousBackupPath =
        tempDirectory.resolve("replace-missing-previous").resolve("target.previous.sqlite");
    try (StagedBookReplacement missingPreviousReplacement =
        newStagedReplacement(
            missingPreviousStagedBookPath,
            missingPreviousTargetBookPath,
            missingPreviousBackupPath)) {
      IllegalStateException missingPreviousFailure =
          assertThrows(IllegalStateException.class, missingPreviousReplacement::commit);
      assertTrue(
          NullTestSupport.messageOf(missingPreviousFailure)
              .contains("Failed to commit the staged FinGrind SQLite replacement"));
      assertFalse(Files.exists(missingPreviousTargetBookPath));
      assertFalse(Files.exists(missingPreviousBackupPath));
    }

    Path nullPreviousStagedBookPath =
        tempDirectory.resolve("replace-null-previous").resolve("missing-staged.sqlite");
    Path nullPreviousTargetBookPath =
        tempDirectory.resolve("replace-null-previous").resolve("target.sqlite");
    try (StagedBookReplacement nullPreviousReplacement =
        newStagedReplacement(nullPreviousStagedBookPath, nullPreviousTargetBookPath, null)) {
      IllegalStateException nullPreviousFailure =
          assertThrows(IllegalStateException.class, nullPreviousReplacement::commit);
      assertTrue(
          NullTestSupport.messageOf(nullPreviousFailure)
              .contains("Failed to commit the staged FinGrind SQLite replacement"));
      assertFalse(Files.exists(nullPreviousTargetBookPath));
    }
  }

  @Test
  void stagedReplacement_commitFailurePaths_areCoveredOnFixtureArtifacts() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath firstMoveFailingStagedBookPath =
          fileSystem.path("\\replace-first\\staged.sqlite");
      firstMoveFailingStagedBookPath.exists = true;
      firstMoveFailingStagedBookPath.regularFile = true;
      AclFixturePath firstMoveFailingTargetBookPath =
          fileSystem.path("\\replace-first\\target.sqlite");
      firstMoveFailingTargetBookPath.exists = true;
      firstMoveFailingTargetBookPath.regularFile = true;
      firstMoveFailingTargetBookPath.failMoveWith(new IOException("first-move-boom"));
      AclFixturePath firstMoveFailingPreviousBackupPath =
          fileSystem.path("\\replace-first\\target.previous.sqlite");
      firstMoveFailingPreviousBackupPath.exists = false;
      firstMoveFailingPreviousBackupPath.regularFile = true;
      try (StagedBookReplacement firstMoveFailingReplacement =
          newStagedReplacement(
              firstMoveFailingStagedBookPath,
              firstMoveFailingTargetBookPath,
              firstMoveFailingPreviousBackupPath)) {
        IllegalStateException firstMoveCommitFailure =
            assertThrows(IllegalStateException.class, firstMoveFailingReplacement::commit);
        assertTrue(
            NullTestSupport.messageOf(firstMoveCommitFailure)
                .contains("Failed to commit the staged FinGrind SQLite replacement"));
        assertTrue(firstMoveFailingTargetBookPath.exists);
        assertFalse(firstMoveFailingPreviousBackupPath.exists);
      }
    }

    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath failingStagedBookPath = fileSystem.path("\\replace\\staged.sqlite");
      failingStagedBookPath.exists = true;
      failingStagedBookPath.regularFile = true;
      failingStagedBookPath.failMoveWith(new IOException("publish-boom"));

      AclFixturePath failingTargetBookPath = fileSystem.path("\\replace\\target.sqlite");
      failingTargetBookPath.exists = true;
      failingTargetBookPath.regularFile = true;

      AclFixturePath failingPreviousTargetBackupPath =
          fileSystem.path("\\replace\\target.previous.sqlite");
      failingPreviousTargetBackupPath.exists = false;
      failingPreviousTargetBackupPath.regularFile = true;
      failingPreviousTargetBackupPath.failMoveWith(new IOException("restore-boom"));

      try (StagedBookReplacement failingStagedReplacement =
          newStagedReplacement(
              failingStagedBookPath, failingTargetBookPath, failingPreviousTargetBackupPath)) {
        IllegalStateException failingCommitFailure =
            assertThrows(IllegalStateException.class, failingStagedReplacement::commit);
        assertTrue(
            NullTestSupport.messageOf(failingCommitFailure)
                .contains("Failed to commit the staged FinGrind SQLite replacement"));
        assertFalse(failingTargetBookPath.exists);
        assertTrue(failingPreviousTargetBackupPath.exists);
      }
    }
  }

  @Test
  void moveReplacing_refusesWhenAtomicMoveIsUnsupported() throws Throwable {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath sourcePath = fileSystem.path("\\move\\source.sqlite");
      sourcePath.exists = true;
      sourcePath.regularFile = true;
      sourcePath.failMoveWith(
          new AtomicMoveNotSupportedException(
              sourcePath.toString(), "\\move\\target.sqlite", "atomic-move-unsupported"));
      AclFixturePath targetPath = fileSystem.path("\\move\\target.sqlite");
      assertThrows(
          AtomicMoveNotSupportedException.class,
          () -> SqliteProtectedBookPublicationSupport.moveReplacing(sourcePath, targetPath));
      assertTrue(sourcePath.exists);
      assertFalse(targetPath.exists);
    }
  }

  private static ProtectedBookMaintenanceRejection.ArtifactPathInvalid assertArtifactPathInvalid(
      ProtectedBookMaintenanceRejection rejection) {
    return assertInstanceOf(ProtectedBookMaintenanceRejection.ArtifactPathInvalid.class, rejection);
  }
}
