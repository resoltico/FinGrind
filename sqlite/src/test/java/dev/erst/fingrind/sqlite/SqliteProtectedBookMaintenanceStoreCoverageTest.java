package dev.erst.fingrind.sqlite;

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
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceAuditCompensationKind;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceAuditKind;
import dev.erst.fingrind.executor.maintenance.ProtectedBookPassphraseSource;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import dev.erst.fingrind.executor.spi.StagedBookReplacement;
import dev.erst.fingrind.executor.spi.StagedRollbackArtifactDeletion;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Focused coverage tests for staged SQLite protected-book maintenance storage. */
class SqliteProtectedBookMaintenanceStoreCoverageTest extends SqliteNativeBridgeTestSupport {
  private static final SqliteProtectedBookVerificationSupport VERIFICATION_SUPPORT =
      new SqliteProtectedBookVerificationSupport();
  private static final SqlitePassphraseResolver KEY_FILE_RESOLVER =
      (resolvedBookPath, passphraseSource, intent) ->
          switch (passphraseSource) {
            case BookAccess.PassphraseSource.KeyFile keyFile ->
                SqliteBookKeyFile.loadDecision(keyFile.bookKeyFilePath());
            case BookAccess.PassphraseSource.StandardInput _ ->
                throw new AssertionError("This coverage suite uses key-file-backed access only.");
            case BookAccess.PassphraseSource.InteractivePrompt _ ->
                throw new AssertionError("This coverage suite uses key-file-backed access only.");
          };

  @Test
  void verifyInitializedBook_mapsMissingBlankAndWrongKeyFailures() throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();

    Path missingBookPath = tempDirectory.resolve("missing.sqlite");
    assertVerificationFailure(
        acceptedValue(store.verifyInitializedBook(localAccess(bookAccess(missingBookPath)))),
        missingBookPath,
        ProtectedBookVerificationFailure.MISSING);

    Path blankBookPath = tempDirectory.resolve("blank.sqlite");
    SqliteStoreFixtureSupport.createEmptySqliteFile(blankBookPath);
    assertVerificationFailure(
        acceptedValue(store.verifyInitializedBook(localAccess(bookAccess(blankBookPath)))),
        blankBookPath,
        ProtectedBookVerificationFailure.BLANK_SQLITE);

    Path initializedBookPath = tempDirectory.resolve("initialized.sqlite");
    BookAccess initializedBookAccess = bookAccess(initializedBookPath);
    initializeBook(initializedBookAccess);
    BookAccess wrongKeyAccess = bookAccess(initializedBookPath, "wrong-secret");
    assertVerificationFailure(
        acceptedValue(store.verifyInitializedBook(localAccess(wrongKeyAccess))),
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
                localAccess(bookAccess(foreignBookPath, SqliteStoreFixtureSupport.TEST_BOOK_KEY)))),
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
        acceptedValue(store.verifyInitializedBook(localAccess(unsupportedAccess))),
        unsupportedBookPath,
        ProtectedBookVerificationFailure.UNSUPPORTED_FORMAT_VERSION);

    Path incompleteBookPath = tempDirectory.resolve("incomplete.sqlite");
    SqliteStoreFixtureSupport.createSchemaOnlyBook(incompleteBookPath);
    assertVerificationFailure(
        acceptedValue(
            store.verifyInitializedBook(
                localAccess(
                    bookAccess(incompleteBookPath, SqliteStoreFixtureSupport.TEST_BOOK_KEY)))),
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

    try (StagedBackupPair stagedBackupPair =
        acceptedValue(
            store.stageBackupPair(
                localAccess(sourceAccess), backupFilePath, backupBookKeyFilePath))) {
      assertFalse(Files.exists(backupFilePath));
      assertFalse(Files.exists(backupBookKeyFilePath));
      assertInstanceOf(
          ProtectedBookMaintenanceStore.VerifiedBook.class,
          acceptedValue(stagedBackupPair.verifyInitializedBackup()));
      stagedBackupPair.commit();
    }

    assertTrue(Files.exists(backupFilePath));
    assertTrue(Files.exists(backupBookKeyFilePath));
    assertInstanceOf(
        ProtectedBookMaintenanceStore.VerifiedBook.class,
        acceptedValue(
            store.verifyInitializedBook(
                new ProtectedBookAccess(
                    backupFilePath,
                    new ProtectedBookPassphraseSource.KeyFile(backupBookKeyFilePath)))));
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

    MaintenanceCompletion completion =
        acceptedValue(
            store.appendMaintenanceAudit(
                localAccess(bookAccess),
                Instant.parse("2026-05-19T11:45:00Z"),
                ProtectedBookMaintenanceAuditKind.BACKUP_CREATED));

    assertEquals(MaintenanceCompletion.DONE, completion);
    assertEquals(1, auditEventCount(bookAccess, "BACKUP_CREATED"));
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
        failingStore.verifyInitializedBook(localAccess(access)),
        ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE);
    assertFailedDescriptor(
        failingStore.stageBackupPair(
            localAccess(access),
            tempDirectory.resolve("resolver-failure").resolve("backup.sqlite"),
            tempDirectory.resolve("resolver-failure").resolve("backup.key")),
        ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE);
    assertFailedDescriptor(
        failingStore.appendMaintenanceAudit(
            localAccess(access),
            Instant.parse("2026-05-19T12:10:00Z"),
            ProtectedBookMaintenanceAuditKind.BACKUP_CREATED),
        ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE);
    assertFailedDescriptor(
        failingStore.appendMaintenanceAuditCompensation(
            localAccess(access),
            Instant.parse("2026-05-19T12:11:00Z"),
            ProtectedBookMaintenanceAuditCompensationKind.BACKUP_CREATED),
        ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE);

    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path busyExistingBookPath =
        writeArtifact("busy-existing.sqlite", "content").toAbsolutePath().normalize();
    SqliteNativeRuntimeActivity.recordOpeningConnection(busyExistingBookPath);
    try {
      ProtectedBookMaintenanceStore.LeaseBusy existingBusy =
          assertInstanceOf(
              ProtectedBookMaintenanceStore.LeaseBusy.class,
              store.acquireExistingArtifactLease(busyExistingBookPath));
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
            busyManagedPath.getFileName().toString() + ".fingrind-maintenance.lock");
    Files.writeString(busyManagedLeasePath, SqliteProcessIdentity.current().leaseMetadataText());
    SqliteBookFileSecurity.hardenOwnerOnlyFile(busyManagedLeasePath);
    ProtectedBookMaintenanceStore.LeaseBusy managedBusy =
        assertInstanceOf(
            ProtectedBookMaintenanceStore.LeaseBusy.class,
            store.acquireManagedArtifactLease(busyManagedPath));
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
  void stagedBackupPair_and_replacement_coverGuardAndFailurePaths() throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path sourceBookPath = tempDirectory.resolve("guarded-stage").resolve("source.sqlite");
    BookAccess sourceAccess = bookAccess(sourceBookPath);
    initializeBook(sourceAccess);

    Path closedBackupFilePath = tempDirectory.resolve("guarded-stage").resolve("closed.sqlite");
    Path closedBackupKeyFilePath = tempDirectory.resolve("guarded-stage").resolve("closed.key");
    try (StagedBackupPair stagedBackupPair =
        acceptedValue(
            store.stageBackupPair(
                localAccess(sourceAccess), closedBackupFilePath, closedBackupKeyFilePath))) {
      stagedBackupPair.close();
    }
    assertFalse(Files.exists(closedBackupFilePath));
    assertFalse(Files.exists(closedBackupKeyFilePath));

    Path backupDirectoryTarget = tempDirectory.resolve("guarded-stage").resolve("backup-directory");
    Files.createDirectories(backupDirectoryTarget);
    Files.writeString(backupDirectoryTarget.resolve("child.txt"), "child");
    try (StagedBackupPair stagedBackupPair =
        acceptedValue(
            store.stageBackupPair(
                localAccess(sourceAccess),
                backupDirectoryTarget,
                tempDirectory.resolve("guarded-stage").resolve("backup-directory.key")))) {
      IllegalStateException publishFailure =
          assertThrows(IllegalStateException.class, stagedBackupPair::commit);
      assertTrue(
          NullTestSupport.messageOf(publishFailure)
              .contains("Failed to publish the staged FinGrind backup pair."));
      stagedBackupPair.rollback();
      stagedBackupPair.close();
    }

    Path publishedBackupFilePath =
        tempDirectory.resolve("guarded-stage").resolve("published.sqlite");
    Path failingKeyDirectory = tempDirectory.resolve("guarded-stage").resolve("key-directory");
    Files.createDirectories(failingKeyDirectory);
    Files.writeString(failingKeyDirectory.resolve("child.txt"), "child");
    try (StagedBackupPair stagedBackupPair =
        acceptedValue(
            store.stageBackupPair(
                localAccess(sourceAccess), publishedBackupFilePath, failingKeyDirectory))) {
      IllegalStateException publishFailure =
          assertThrows(IllegalStateException.class, stagedBackupPair::commit);
      assertTrue(
          NullTestSupport.messageOf(publishFailure)
              .contains("Failed to publish the staged FinGrind backup pair."));
      assertFalse(Files.exists(publishedBackupFilePath));
      stagedBackupPair.commit();
      stagedBackupPair.rollback();
      stagedBackupPair.close();
    }

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

    IllegalStateException invalidRollbackArtifact =
        assertThrows(
            IllegalStateException.class,
            () -> store.stageRollbackArtifactDeletion(tempDirectory.toAbsolutePath().normalize()));
    assertTrue(
        NullTestSupport.messageOf(invalidRollbackArtifact).contains("regular non-symlink file"));
  }

  @Test
  void maintenanceHelpers_coverBlockingArtifactsLeasesRollbackDiscoveryAndAuditRetraction()
      throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path bookPath = tempDirectory.resolve("helpers").resolve("book.sqlite");
    BookAccess access = bookAccess(bookPath);
    initializeBook(access);

    Path walPath = bookPath.resolveSibling(bookPath.getFileName().toString() + "-wal");
    Path rollbackArtifactPath =
        bookPath.resolveSibling(bookPath.getFileName().toString() + ".rekey-rollback-test.sqlite");
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
                store.acquireManagedArtifactLease(
                    tempDirectory.resolve("managed").resolve("book.sqlite")));
        ProtectedBookMaintenanceStore.HeldLease existingLease =
            acceptedLease(
                store.acquireExistingArtifactLease(bookPath.toAbsolutePath().normalize()))) {
      assertTrue(
          Files.exists(
              managedLease
                  .artifactPath()
                  .resolveSibling(
                      managedLease.artifactPath().getFileName() + ".fingrind-maintenance.lock")));
      assertEquals(bookPath.toAbsolutePath().normalize(), existingLease.artifactPath());
    }

    acceptedValue(
        store.appendMaintenanceAudit(
            localAccess(access),
            Instant.parse("2026-05-19T12:00:00Z"),
            ProtectedBookMaintenanceAuditKind.BACKUP_CREATED));
    acceptedValue(
        store.appendMaintenanceAudit(
            localAccess(access),
            Instant.parse("2026-05-19T12:05:00Z"),
            ProtectedBookMaintenanceAuditKind.BACKUP_CREATED));
    assertEquals(2, auditEventCount(access, "BACKUP_CREATED"));
    assertEquals(
        MaintenanceCompletion.DONE,
        acceptedValue(
            store.appendMaintenanceAuditCompensation(
                localAccess(access),
                Instant.parse("2026-05-19T12:05:00Z"),
                ProtectedBookMaintenanceAuditCompensationKind.BACKUP_CREATED)));
    assertEquals(2, auditEventCount(access, "BACKUP_CREATED"));
    assertEquals(1, auditEventCount(access, "BACKUP_CREATED_COMPENSATED"));
    assertEquals(
        MaintenanceCompletion.DONE,
        acceptedValue(
            store.appendMaintenanceAuditCompensation(
                localAccess(access),
                Instant.parse("2026-05-19T12:06:00Z"),
                ProtectedBookMaintenanceAuditCompensationKind.REKEY_ROLLBACK_DELETED)));
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
      MaintenanceDecision<?> verificationDecision =
          VERIFICATION_SUPPORT.verifyResolvedBook(verifiedBookPath, wrongPassphrase);
      ProtectedBookMaintenanceStore.BookVerification verification =
          acceptedValue(
              (MaintenanceDecision<ProtectedBookMaintenanceStore.BookVerification>)
                  verificationDecision);
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
                SqliteProtectedBookStagingSupport.createStagedSibling(
                    stagedParentAsFile.resolve("book.sqlite"), ".backup-", ".sqlite"));
    assertTrue(
        NullTestSupport.messageOf(stagedSiblingFailure)
            .contains("Failed to create one staged maintenance artifact"));

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
                  SqliteProtectedBookStagingSupport.ensureSecureBackupFileParentDirectory(
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
              () -> SqliteProtectedBookStagingSupport.hardenBookArtifacts(bookPath));
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
    assertThrows(
        RuntimeException.class,
        () -> store.stageBackupPair(localAccess(bogusSourceAccess), backupFilePath, backupKeyPath));
    try (var children =
        Files.list(java.util.Objects.requireNonNull(backupFilePath.getParent(), "backup parent"))) {
      assertFalse(children.anyMatch(path -> path.getFileName().toString().contains(".backup-")));
    }

    Path auditFailureBookPath = tempDirectory.resolve("audit-failure").resolve("book.sqlite");
    BookAccess auditFailureAccess = bookAccess(auditFailureBookPath);
    initializeBook(auditFailureAccess);
    withOpenDatabase(
        auditFailureAccess, database -> database.executeStatement("drop table audit_event"));

    assertThrows(
        SqliteNativeException.class,
        () ->
            store.appendMaintenanceAudit(
                localAccess(auditFailureAccess),
                Instant.parse("2026-05-19T12:30:00Z"),
                ProtectedBookMaintenanceAuditKind.BACKUP_RESTORED));
    assertThrows(
        SqliteNativeException.class,
        () ->
            store.appendMaintenanceAuditCompensation(
                localAccess(auditFailureAccess),
                Instant.parse("2026-05-19T12:31:00Z"),
                ProtectedBookMaintenanceAuditCompensationKind.REKEY_ROLLBACK_DELETED));
    withOpenDatabase(
        auditFailureAccess,
        database ->
            database.executeStatement("create table if not exists maintenance_probe (id integer)"));
  }

  @Test
  void stagedBackupPairRollback_removesPublishedBackupArtifacts() throws Throwable {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path sourceBookPath = tempDirectory.resolve("nested-rollback").resolve("source.sqlite");
    BookAccess sourceAccess = bookAccess(sourceBookPath);
    initializeBook(sourceAccess);
    Path finalBackupFilePath = tempDirectory.resolve("nested-rollback").resolve("backup.sqlite");
    Path finalBackupKeyFilePath = tempDirectory.resolve("nested-rollback").resolve("backup.key");

    try (StagedBackupPair stagedBackupPair =
        acceptedValue(
            store.stageBackupPair(
                localAccess(sourceAccess), finalBackupFilePath, finalBackupKeyFilePath))) {
      Files.writeString(finalBackupFilePath, "published-backup");
      Files.writeString(finalBackupKeyFilePath, "published-key");
      setPrivateField(stagedBackupPair, "backupPassphrase", null);
      setPrivateField(stagedBackupPair, "backupFilePublished", true);
      setPrivateField(stagedBackupPair, "backupKeyFilePublished", true);
      stagedBackupPair.rollback();
      assertFalse(Files.exists(finalBackupFilePath));
      assertFalse(Files.exists(finalBackupKeyFilePath));
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
  void moveReplacing_fallsBackWhenAtomicMoveIsUnsupported() throws Throwable {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath sourcePath = fileSystem.path("\\move\\source.sqlite");
      sourcePath.exists = true;
      sourcePath.regularFile = true;
      sourcePath.failMoveWith(
          new AtomicMoveNotSupportedException(
              sourcePath.toString(), "\\move\\target.sqlite", "atomic-move-unsupported"));
      AclFixturePath targetPath = fileSystem.path("\\move\\target.sqlite");
      SqliteProtectedBookStagingSupport.moveReplacing(sourcePath, targetPath);
      assertFalse(sourcePath.exists);
      assertTrue(targetPath.exists);
    }
  }

  private static ProtectedBookMaintenanceStore.HeldLease acceptedLease(
      ProtectedBookMaintenanceStore.LeaseAcquisition acquisition) {
    return switch (acquisition) {
      case ProtectedBookMaintenanceStore.HeldLease heldLease -> heldLease;
      case ProtectedBookMaintenanceStore.LeaseBusy leaseBusy ->
          throw new AssertionError(
              "Expected one acquired lease but got busy: " + leaseBusy.artifactPath());
    };
  }

  private SqliteProtectedBookMaintenanceStore maintenanceStore() {
    return new SqliteProtectedBookMaintenanceStore(KEY_FILE_RESOLVER);
  }

  private static ProtectedBookAccess localAccess(BookAccess bookAccess) {
    return ProtectedBookAccess.fromPublished(bookAccess);
  }

  private void initializeBook(BookAccess bookAccess) {
    try {
      Path parentDirectory = bookAccess.bookFilePath().getParent();
      if (parentDirectory != null) {
        Files.createDirectories(parentDirectory);
        SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parentDirectory);
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to create the test book directory.", exception);
    }
    withOpenDatabase(
        bookAccess,
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          SqliteStoreFixtureSupport.insertCanonicalInitializedBookMetadata(database);
          SqliteAuditEventWriter.insertAuditEvent(
              database, BookAuditEvent.bookOpened(Instant.parse("2026-05-19T09:00:00Z")));
        });
  }

  private Path writeArtifact(String fileName, String content) throws IOException {
    Path artifactPath = tempDirectory.resolve(fileName);
    Path parent = artifactPath.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
      SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parent);
    }
    Files.writeString(artifactPath, content);
    return artifactPath;
  }

  private int auditEventCount(BookAccess bookAccess, String eventKind) {
    final int[] count = new int[1];
    withOpenDatabase(
        bookAccess,
        database -> {
          try (SqliteNativeStatement statement =
              SqliteNativeStatements.prepare(
                  database,
                  "select count(*) from audit_event where event_kind = '" + eventKind + "'")) {
            assertEquals(SqliteNativeResultCode.code("ROW"), statement.step());
            count[0] = statement.columnInt(0);
            assertEquals(SqliteNativeResultCode.code("DONE"), statement.step());
          }
        });
    return count[0];
  }

  private static Path maintenanceJournalPath(Path bookPath) {
    return bookPath.resolveSibling(bookPath.getFileName().toString() + ".maintenance-log.jsonl");
  }

  private static <T> T acceptedValue(MaintenanceDecision<T> decision) {
    return switch (decision) {
      case MaintenanceDecision.Accepted<T>(T value) -> value;
      case MaintenanceDecision.Failed<T>(MaintenanceFailure failure) ->
          throw new AssertionError("Expected accepted maintenance decision but got " + failure);
    };
  }

  private static void assertFailedDescriptor(
      MaintenanceDecision<?> decision, ContractErrors.Descriptor expectedDescriptor) {
    MaintenanceFailure failure =
        switch (decision) {
          case MaintenanceDecision.Accepted<?> accepted ->
              throw new AssertionError("Expected failed maintenance decision but got " + accepted);
          case MaintenanceDecision.Failed<?> failed -> failed.failure();
        };
    assertEquals(expectedDescriptor, failure.descriptor());
  }

  private static void assertVerificationFailure(
      ProtectedBookMaintenanceStore.BookVerification verification,
      Path expectedArtifactPath,
      ProtectedBookVerificationFailure expectedFailure) {
    ProtectedBookMaintenanceStore.VerificationFailure failure =
        assertInstanceOf(ProtectedBookMaintenanceStore.VerificationFailure.class, verification);
    assertEquals(expectedArtifactPath.toAbsolutePath().normalize(), failure.artifactPath());
    assertEquals(expectedFailure, failure.failure());
  }

  private static StagedBookReplacement newStagedReplacement(
      Path stagedBookPath, Path targetBookPath, @Nullable Path previousTargetBackupPath) {
    return new SqliteStagedBookReplacement(
        stagedBookPath, targetBookPath, previousTargetBackupPath);
  }

  private static void setPrivateField(Object target, String fieldName, @Nullable Object value) {
    try {
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(target.getClass(), MethodHandles.lookup());
      switch (fieldName) {
        case "backupPassphrase" -> {
          VarHandle field =
              lookup.findVarHandle(target.getClass(), fieldName, SqliteBookPassphrase.class);
          field.set(target, value);
        }
        case "backupFilePublished", "backupKeyFilePublished" -> {
          VarHandle field = lookup.findVarHandle(target.getClass(), fieldName, boolean.class);
          field.set(
              target,
              ((Boolean) java.util.Objects.requireNonNull(value, fieldName)).booleanValue());
        }
        default ->
            throw new IllegalArgumentException("Unsupported private test field: " + fieldName);
      }
    } catch (IllegalAccessException | NoSuchFieldException exception) {
      throw new LinkageError(
          "Failed to set one private field on the maintenance test fixture: " + fieldName + ".",
          exception);
    }
  }

  private static AclEntry ownerDirectoryAccessEntry(UserPrincipal owner) {
    return AclEntry.newBuilder()
        .setType(AclEntryType.ALLOW)
        .setPrincipal(owner)
        .setPermissions(
            Set.of(
                AclEntryPermission.LIST_DIRECTORY,
                AclEntryPermission.ADD_FILE,
                AclEntryPermission.EXECUTE))
        .build();
  }

  /** ACL view test double that throws while hardening attempts to rewrite ACL entries. */
  private static final class ThrowingAclFileAttributeView implements AclFileAttributeView {
    private final UserPrincipal owner;
    private final String message;

    private ThrowingAclFileAttributeView(UserPrincipal owner, String message) {
      this.owner = java.util.Objects.requireNonNull(owner, "owner");
      this.message = java.util.Objects.requireNonNull(message, "message");
    }

    @Override
    public String name() {
      return "acl";
    }

    @Override
    public List<AclEntry> getAcl() {
      return List.of();
    }

    @Override
    public void setAcl(List<AclEntry> acl) throws IOException {
      throw new IOException(message);
    }

    @Override
    public UserPrincipal getOwner() {
      return owner;
    }

    @Override
    public void setOwner(UserPrincipal owner) {}
  }
}
