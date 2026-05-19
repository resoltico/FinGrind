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
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceEvent;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceVerificationFailure;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Focused coverage tests for SQLite protected-book maintenance verification and replacement. */
class SqliteProtectedBookMaintenanceStoreCoverageTest extends SqliteNativeBridgeTestSupport {
  private static final MethodHandle MAP_INSPECTION = bindMapInspection();
  private static final MethodHandle MAP_INSPECTION_FAILURE = bindMapInspectionFailure();
  private static final MethodHandle VERIFY_RESOLVED_BOOK_FAILURE_MAPPER =
      bindVerifyResolvedBookFailureMapper();
  private static final MethodHandle PREPARED_REPLACEMENT_MOVE_REPLACING =
      bindPreparedReplacementMoveReplacing();
  private static final MethodHandle PREPARED_REPLACEMENT_CONSTRUCTOR =
      bindPreparedReplacementConstructor();
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
  void verifyInitializedBook_mapsLifecycleFailuresAndWrongKeyFailure() throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();

    Path blankBookPath = tempDirectory.resolve("blank.sqlite");
    SqliteStoreFixtureSupport.createEmptySqliteFile(blankBookPath);
    assertVerificationFailure(
        store.verifyInitializedBook(bookAccess(blankBookPath)).requireAccepted(),
        blankBookPath,
        ProtectedBookMaintenanceVerificationFailure.BLANK_SQLITE);

    Path foreignBookPath = tempDirectory.resolve("foreign.sqlite");
    assertVerificationFailure(
        mapInspection(
            store,
            foreignBookPath,
            new BookLifecycleInspection.Existing(
                BookLifecycleInspection.Status.FOREIGN_SQLITE,
                0,
                0,
                SqliteBookContract.FORMAT_VERSION)),
        foreignBookPath,
        ProtectedBookMaintenanceVerificationFailure.FOREIGN_SQLITE);

    Path unsupportedBookPath = tempDirectory.resolve("unsupported.sqlite");
    assertVerificationFailure(
        mapInspection(
            store,
            unsupportedBookPath,
            new BookLifecycleInspection.Existing(
                BookLifecycleInspection.Status.UNSUPPORTED_FORMAT_VERSION,
                SqliteBookContract.APPLICATION_ID,
                SqliteBookContract.FORMAT_VERSION + 1,
                SqliteBookContract.FORMAT_VERSION)),
        unsupportedBookPath,
        ProtectedBookMaintenanceVerificationFailure.UNSUPPORTED_FORMAT_VERSION);

    Path incompleteBookPath = tempDirectory.resolve("incomplete.sqlite");
    assertVerificationFailure(
        mapInspection(
            store,
            incompleteBookPath,
            new BookLifecycleInspection.Existing(
                BookLifecycleInspection.Status.INCOMPLETE_FINGRIND,
                SqliteBookContract.APPLICATION_ID,
                SqliteBookContract.FORMAT_VERSION,
                SqliteBookContract.FORMAT_VERSION)),
        incompleteBookPath,
        ProtectedBookMaintenanceVerificationFailure.INCOMPLETE_FINGRIND);

    Path initializedBookPath = tempDirectory.resolve("initialized.sqlite");
    SqliteStoreFixtureSupport.initializeBookOnDisk(initializedBookPath);
    assertVerificationFailure(
        store
            .verifyInitializedBook(bookAccess(initializedBookPath, "wrong-secret"))
            .requireAccepted(),
        initializedBookPath,
        ProtectedBookMaintenanceVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED);
  }

  @Test
  void inspectionMapping_coversMissingAndInitializedGuardBranches() {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path missingBookPath = tempDirectory.resolve("mapped-missing.sqlite");

    assertVerificationFailure(
        mapInspection(
            store,
            missingBookPath,
            new BookLifecycleInspection.Missing(SqliteBookContract.FORMAT_VERSION)),
        missingBookPath,
        ProtectedBookMaintenanceVerificationFailure.MISSING);
    assertEquals(
        ProtectedBookMaintenanceVerificationFailure.MISSING,
        mapInspectionFailure(store, BookLifecycleInspection.Status.MISSING));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> mapInspectionFailure(store, BookLifecycleInspection.Status.INITIALIZED));
    assertTrue(
        NullTestSupport.messageOf(exception).contains("not one rejection inspection status"),
        () -> NullTestSupport.messageOf(exception));
  }

  @Test
  void verifyInitializedBook_rejectsDirectoryPathsBeforeOpening() throws IOException {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path directoryPath = tempDirectory.resolve("directory-book.sqlite");
    Files.createDirectories(directoryPath);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(directoryPath);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> store.verifyInitializedBook(bookAccess(directoryPath)));

    assertTrue(
        NullTestSupport.messageOf(exception)
            .contains("must resolve to one regular non-symlink file"),
        () -> NullTestSupport.messageOf(exception));
  }

  @Test
  void verifyResolvedBook_leavesNonVerificationContractFailuresRejected() {
    Path bookPath = tempDirectory.resolve("mapper.sqlite");
    ContractFailure failure =
        ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.failure(
            "resolver mismatch", null, null);

    ContractDecision<?> decision = mapVerifyResolvedBookFailure(bookPath, failure);
    switch (decision) {
      case ContractDecision.Accepted<?> accepted ->
          throw new AssertionError(
              "Expected non-verification failures to stay rejected: " + accepted);
      case ContractDecision.Rejected<?> rejected -> assertEquals(failure, rejected.failure());
    }
  }

  @Test
  void prepareReplacement_commit_keepsTheReplacementContents() throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path sourcePath = writeArtifact("commit-source.sqlite", "replacement");
    Path targetPath = writeArtifact("commit-target.sqlite", "previous");

    try (ProtectedBookMaintenanceStore.PreparedBookReplacement replacement =
        store.prepareReplacement(sourcePath, targetPath)) {
      assertEquals(targetPath, replacement.targetBookPath());
      assertEquals("replacement", Files.readString(targetPath));
      replacement.commit();
    }

    assertEquals("replacement", Files.readString(targetPath));
  }

  @Test
  void prepareReplacement_createWrapsSourceCopyFailures() {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path missingSourcePath = tempDirectory.resolve("missing-source.sqlite");
    Path targetPath = tempDirectory.resolve("wrapped-failure.sqlite");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> store.prepareReplacement(missingSourcePath, targetPath));
    assertTrue(
        NullTestSupport.messageOf(exception).contains("Failed to replace the FinGrind SQLite book"),
        () -> NullTestSupport.messageOf(exception));
  }

  @Test
  void prepareReplacement_rollback_restoresPreviousTargetContents() throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path sourcePath = writeArtifact("rollback-source.sqlite", "replacement");
    Path targetPath = writeArtifact("rollback-target.sqlite", "previous");

    try (ProtectedBookMaintenanceStore.PreparedBookReplacement replacement =
        store.prepareReplacement(sourcePath, targetPath)) {
      assertEquals("replacement", Files.readString(targetPath));
      replacement.rollback();
    }

    assertEquals("previous", Files.readString(targetPath));
  }

  @Test
  void prepareReplacement_rollbackAfterCommit_isNoOp() throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path sourcePath = writeArtifact("rollback-after-commit-source.sqlite", "replacement");
    Path targetPath = writeArtifact("rollback-after-commit-target.sqlite", "previous");

    try (ProtectedBookMaintenanceStore.PreparedBookReplacement replacement =
        store.prepareReplacement(sourcePath, targetPath)) {
      replacement.commit();
      replacement.rollback();
    }

    assertEquals("replacement", Files.readString(targetPath));
  }

  @Test
  void prepareReplacement_closeWithoutCommit_restoresPreviousTargetContents() throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path sourcePath = writeArtifact("close-source.sqlite", "replacement");
    Path targetPath = writeArtifact("close-target.sqlite", "previous");

    try (ProtectedBookMaintenanceStore.PreparedBookReplacement ignored =
        store.prepareReplacement(sourcePath, targetPath)) {
      assertEquals("replacement", Files.readString(targetPath));
    }

    assertEquals("previous", Files.readString(targetPath));
  }

  @Test
  void prepareReplacement_closeWithoutCommit_removesCreatedTargetWhenNoneExisted()
      throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path sourcePath = writeArtifact("new-target-source.sqlite", "replacement");
    Path targetPath = tempDirectory.resolve("new-target.sqlite");

    try (ProtectedBookMaintenanceStore.PreparedBookReplacement ignored =
        store.prepareReplacement(sourcePath, targetPath)) {
      assertEquals("replacement", Files.readString(targetPath));
    }

    assertFalse(Files.exists(targetPath));
  }

  @Test
  void prepareReplacement_restoresPreviousTargetWhenStageMoveFails() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      fileSystem.onPathCreated(
          path -> {
            if (path.toString().contains(".restore-")) {
              path.failMoveWith(new IOException("stage-move-boom"));
            }
          });
      AclFixturePath parentPath = ownerOnlyPosixDirectory(fileSystem.path("\\books"));
      AclFixturePath sourcePath = ownerOnlyPosixFile(fileSystem.path("\\books\\source.sqlite"));
      AclFixturePath targetPath = ownerOnlyPosixFile(fileSystem.path("\\books\\target.sqlite"));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> maintenanceStore().prepareReplacement(sourcePath, targetPath));

      assertEquals(
          "stage-move-boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
      assertTrue(parentPath.exists);
      assertTrue(targetPath.exists);
      assertTrue(targetPath.regularFile);
    }
  }

  @Test
  void prepareReplacement_wrapsCleanupFailureWhenPreviousTargetRestoreFails() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      fileSystem.onPathCreated(
          path -> {
            if (path.toString().contains(".restore-")) {
              path.failMoveWith(new IOException("stage-move-boom"));
            }
            if (path.toString().contains(".previous-")) {
              path.failMoveWith(new IOException("restore-move-boom"));
            }
          });
      ownerOnlyPosixDirectory(fileSystem.path("\\books"));
      AclFixturePath sourcePath = ownerOnlyPosixFile(fileSystem.path("\\books\\source.sqlite"));
      AclFixturePath targetPath = ownerOnlyPosixFile(fileSystem.path("\\books\\target.sqlite"));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> maintenanceStore().prepareReplacement(sourcePath, targetPath));

      assertEquals(
          "stage-move-boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
      assertFalse(targetPath.exists);
    }
  }

  @Test
  void prepareReplacement_wrapsTargetHardeningFailureAfterReplacementMove() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      ownerOnlyAclDirectory(fileSystem.path("\\books"));
      AclFixturePath sourcePath = ownerOnlyAclFile(fileSystem.path("\\books\\source.sqlite"));
      AclFixturePath targetPath = ownerOnlyAclFile(fileSystem.path("\\books\\target.sqlite"));
      AclFixturePath walPath = ownerOnlyAclFile(fileSystem.path("\\books\\target.sqlite-wal"));
      walPath.overrideAclView = throwingAclView("target-harden-boom");

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> maintenanceStore().prepareReplacement(sourcePath, targetPath));

      String exceptionMessage = NullTestSupport.messageOf(exception);
      Throwable cause = exception.getCause();
      String causeMessage = cause == null ? "" : NullTestSupport.messageOf(cause);
      assertTrue(
          exceptionMessage.contains("target-harden-boom")
              || causeMessage.contains("target-harden-boom"),
          () -> exceptionMessage + " / cause=" + causeMessage);
      assertTrue(targetPath.exists);
    }
  }

  @Test
  void preparedReplacementRollback_skipsMissingPreviousBackupAndWrapsRestoreFailures() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      ownerOnlyPosixDirectory(fileSystem.path("\\books"));

      AclFixturePath missingTargetPath =
          ownerOnlyPosixFile(fileSystem.path("\\books\\missing.sqlite"));
      AclFixturePath missingStagedPath =
          ownerOnlyPosixFile(fileSystem.path("\\books\\missing-stage.tmp"));
      AclFixturePath absentPreviousBackupPath = fileSystem.path("\\books\\absent-previous.sqlite");
      newPreparedReplacement(missingTargetPath, missingStagedPath, absentPreviousBackupPath)
          .rollback();
      assertFalse(missingTargetPath.exists);
      assertFalse(missingStagedPath.exists);

      AclFixturePath failingTargetPath =
          ownerOnlyPosixFile(fileSystem.path("\\books\\failing.sqlite"));
      AclFixturePath failingStagedPath =
          ownerOnlyPosixFile(fileSystem.path("\\books\\failing-stage.tmp"));
      AclFixturePath failingPreviousBackupPath =
          ownerOnlyPosixFile(fileSystem.path("\\books\\failing-previous.sqlite"));
      failingPreviousBackupPath.failMoveWith(new IOException("restore-backup-boom"));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  newPreparedReplacement(
                          failingTargetPath, failingStagedPath, failingPreviousBackupPath)
                      .rollback());

      assertEquals(
          "restore-backup-boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
    }
  }

  @Test
  void preparedReplacementMoveReplacing_fallsBackWhenAtomicMoveIsUnavailable() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath sourcePath = fileSystem.path("\\books\\staged.tmp");
      sourcePath.exists = true;
      sourcePath.regularFile = true;
      AclFixturePath targetPath = fileSystem.path("\\books\\book.sqlite");
      sourcePath.failMoveWith(
          new AtomicMoveNotSupportedException(
              sourcePath.toString(), targetPath.toString(), "atomic-unsupported"));

      invokePreparedReplacementMoveReplacing(sourcePath, targetPath);

      assertFalse(sourcePath.exists);
      assertTrue(targetPath.exists);
      assertTrue(targetPath.regularFile);
    }
  }

  @Test
  void recordMaintenanceEvent_appendsOneJsonLineWithMultipleRollbackArtifacts() throws Exception {
    Path bookPath = tempDirectory.resolve("journal-book.sqlite");
    ProtectedBookMaintenanceEvent event =
        ProtectedBookMaintenanceEvent.rollbackArtifactsInspected(
            Instant.parse("2026-05-18T12:00:00Z"),
            bookPath,
            List.of(
                tempDirectory.resolve("rollback-a.sqlite"),
                tempDirectory.resolve("rollback-b.sqlite")));

    maintenanceStore().recordMaintenanceEvent(event);

    String journalLine =
        Files.readString(SqliteProtectedBookMaintenanceJournal.journalPath(bookPath));
    assertTrue(journalLine.contains("\"eventKind\":\"rekey-rollback-inspected\""), journalLine);
    assertTrue(journalLine.contains("\"rollbackArtifacts\""), journalLine);
    assertTrue(journalLine.contains("<redacted>/rollback-a.sqlite"), journalLine);
    assertTrue(journalLine.contains("<redacted>/rollback-b.sqlite"), journalLine);
  }

  @Test
  void recordMaintenanceEvent_wrapsAppendIoFailures() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE);
      AclFixturePath bookPath = fileSystem.path("\\books\\append-failure.sqlite");
      AclFixturePath journalPath =
          fileSystem.path("\\books\\append-failure.sqlite.maintenance-log.jsonl");
      journalPath.failNewByteChannelWith(new IOException("append-boom"));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  maintenanceStore()
                      .recordMaintenanceEvent(
                          ProtectedBookMaintenanceEvent.backupCreated(
                              Instant.parse("2026-05-18T12:06:00Z"),
                              bookPath,
                              fileSystem.path("\\books\\backup.sqlite"),
                              fileSystem.path("\\books\\backup.key"))));

      assertEquals("append-boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
    }
  }

  @Test
  void recordMaintenanceEvent_rejectsNonRegularJournalPath() throws Exception {
    Path bookPath = tempDirectory.resolve("journal-reject-book.sqlite");
    Path journalPath = SqliteProtectedBookMaintenanceJournal.journalPath(bookPath);
    Files.createDirectories(journalPath);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(journalPath);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                maintenanceStore()
                    .recordMaintenanceEvent(
                        ProtectedBookMaintenanceEvent.backupCreated(
                            Instant.parse("2026-05-18T12:05:00Z"),
                            bookPath,
                            tempDirectory.resolve("backup.sqlite"),
                            tempDirectory.resolve("backup.key"))));

    assertTrue(
        NullTestSupport.messageOf(exception).contains("maintenance journal"),
        () -> NullTestSupport.messageOf(exception));
  }

  @Test
  void replaceBook_successfullyReplacesExistingTargetContents() throws Exception {
    Path sourcePath = writeArtifact("replace-source.sqlite", "replacement");
    Path targetPath = writeArtifact("replace-target.sqlite", "previous");

    SqliteBookMaintenanceFiles.replaceBook(sourcePath, targetPath);

    assertEquals("replacement", Files.readString(targetPath));
  }

  @Test
  void hardenOwnerOnlyFile_acceptsMissingFilesUnderSecureParents() throws Exception {
    Path parentDirectory = tempDirectory.resolve("secure-parent");
    Files.createDirectories(parentDirectory);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parentDirectory);
    Path missingFilePath = parentDirectory.resolve("missing.sqlite");

    SqliteBookFileSecurity.hardenOwnerOnlyFile(missingFilePath);

    assertFalse(Files.exists(missingFilePath));
  }

  @Test
  void hardenOwnerOnlyFile_acceptsMissingFilesUnderMissingSecureParents() throws Exception {
    Path missingFilePath = tempDirectory.resolve("missing-parent").resolve("missing.sqlite");

    SqliteBookFileSecurity.hardenOwnerOnlyFile(missingFilePath);

    assertFalse(Files.exists(missingFilePath));
  }

  private SqliteProtectedBookMaintenanceStore maintenanceStore() {
    return new SqliteProtectedBookMaintenanceStore(KEY_FILE_RESOLVER);
  }

  private static ProtectedBookMaintenanceStore.BookVerification mapInspection(
      SqliteProtectedBookMaintenanceStore store,
      Path artifactPath,
      BookLifecycleInspection inspection) {
    try {
      return (ProtectedBookMaintenanceStore.BookVerification)
          MAP_INSPECTION.invoke(store, artifactPath, inspection);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError(
          "Failed to invoke protected-book maintenance inspection mapping.", throwable);
    }
  }

  private static ProtectedBookMaintenanceVerificationFailure mapInspectionFailure(
      SqliteProtectedBookMaintenanceStore store, BookLifecycleInspection.Status status) {
    try {
      return (ProtectedBookMaintenanceVerificationFailure)
          MAP_INSPECTION_FAILURE.invoke(store, status);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError(
          "Failed to invoke protected-book maintenance rejection mapping.", throwable);
    }
  }

  private static ContractDecision<?> mapVerifyResolvedBookFailure(
      Path bookPath, ContractFailure failure) {
    try {
      return (ContractDecision<?>) VERIFY_RESOLVED_BOOK_FAILURE_MAPPER.invoke(bookPath, failure);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError(
          "Failed to invoke protected-book verification failure mapper.", throwable);
    }
  }

  private static void assertVerificationFailure(
      ProtectedBookMaintenanceStore.BookVerification verification,
      Path expectedArtifactPath,
      ProtectedBookMaintenanceVerificationFailure expectedFailure) {
    ProtectedBookMaintenanceStore.VerificationFailure failure =
        assertInstanceOf(ProtectedBookMaintenanceStore.VerificationFailure.class, verification);
    assertEquals(expectedArtifactPath.toAbsolutePath().normalize(), failure.artifactPath());
    assertEquals(expectedFailure, failure.failure());
  }

  private Path writeArtifact(String fileName, String contents) throws IOException {
    Path artifactPath = tempDirectory.resolve(fileName);
    Path parentDirectory = artifactPath.getParent();
    if (parentDirectory != null) {
      Files.createDirectories(parentDirectory);
      SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parentDirectory);
    }
    Files.writeString(artifactPath, contents);
    SqliteBookFileSecurity.hardenOwnerOnlyFile(artifactPath);
    return artifactPath;
  }

  private static AclFixturePath ownerOnlyPosixDirectory(AclFixturePath path) {
    path.exists = true;
    path.regularFile = false;
    path.posixPermissions =
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);
    return path;
  }

  private static AclFixturePath ownerOnlyPosixFile(AclFixturePath path) {
    path.exists = true;
    path.regularFile = true;
    path.posixPermissions = Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    return path;
  }

  private static AclFixturePath ownerOnlyAclDirectory(AclFixturePath path) {
    path.exists = true;
    path.regularFile = false;
    Objects.requireNonNull(path.aclView, "path aclView")
        .setAcl(
            List.of(
                AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(path.getFileSystem().owner)
                    .setPermissions(
                        Set.of(
                            AclEntryPermission.LIST_DIRECTORY,
                            AclEntryPermission.ADD_FILE,
                            AclEntryPermission.EXECUTE))
                    .build()));
    return path;
  }

  private static AclFixturePath ownerOnlyAclFile(AclFixturePath path) {
    path.exists = true;
    path.regularFile = true;
    Objects.requireNonNull(path.aclView, "path aclView")
        .setAcl(
            List.of(
                AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(path.getFileSystem().owner)
                    .setPermissions(
                        Set.of(
                            AclEntryPermission.READ_DATA,
                            AclEntryPermission.WRITE_DATA,
                            AclEntryPermission.READ_ATTRIBUTES,
                            AclEntryPermission.WRITE_ATTRIBUTES,
                            AclEntryPermission.READ_ACL,
                            AclEntryPermission.SYNCHRONIZE))
                    .build()));
    return path;
  }

  private static ProtectedBookMaintenanceStore.PreparedBookReplacement newPreparedReplacement(
      Path targetBookPath, Path stagedReplacementPath, Path previousTargetBackupPath) {
    try {
      return (ProtectedBookMaintenanceStore.PreparedBookReplacement)
          PREPARED_REPLACEMENT_CONSTRUCTOR.invoke(
              targetBookPath, stagedReplacementPath, previousTargetBackupPath);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError("Failed to construct one prepared replacement fixture.", throwable);
    }
  }

  private static void invokePreparedReplacementMoveReplacing(Path sourcePath, Path targetPath) {
    try {
      PREPARED_REPLACEMENT_MOVE_REPLACING.invokeExact(sourcePath, targetPath);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError("Failed to invoke prepared-replacement move helper.", throwable);
    }
  }

  private static MethodHandle bindMapInspection() {
    try {
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(
              SqliteProtectedBookMaintenanceStore.class, MethodHandles.lookup());
      return lookup.findVirtual(
          SqliteProtectedBookMaintenanceStore.class,
          "mapInspection",
          MethodType.methodType(
              ProtectedBookMaintenanceStore.BookVerification.class,
              Path.class,
              BookLifecycleInspection.class));
    } catch (IllegalAccessException | NoSuchMethodException exception) {
      throw new LinkageError(
          "Failed to bind protected-book maintenance inspection mapper.", exception);
    }
  }

  private static MethodHandle bindMapInspectionFailure() {
    try {
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(
              SqliteProtectedBookMaintenanceStore.class, MethodHandles.lookup());
      return lookup.findVirtual(
          SqliteProtectedBookMaintenanceStore.class,
          "mapInspectionFailure",
          MethodType.methodType(
              ProtectedBookMaintenanceVerificationFailure.class,
              BookLifecycleInspection.Status.class));
    } catch (IllegalAccessException | NoSuchMethodException exception) {
      throw new LinkageError(
          "Failed to bind protected-book maintenance rejection mapper.", exception);
    }
  }

  private static MethodHandle bindVerifyResolvedBookFailureMapper() {
    try {
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(
              SqliteProtectedBookMaintenanceStore.class, MethodHandles.lookup());
      return lookup.findStatic(
          SqliteProtectedBookMaintenanceStore.class,
          "lambda$verifyResolvedBook$1",
          MethodType.methodType(ContractDecision.class, Path.class, ContractFailure.class));
    } catch (IllegalAccessException | NoSuchMethodException exception) {
      throw new LinkageError(
          "Failed to bind protected-book verification failure mapper.", exception);
    }
  }

  private static MethodHandle bindPreparedReplacementMoveReplacing() {
    try {
      Class<?> preparedReplacementClass =
          Class.forName(
              "dev.erst.fingrind.sqlite.SqliteProtectedBookMaintenanceStore$PreparedReplacement");
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(preparedReplacementClass, MethodHandles.lookup());
      return lookup.findStatic(
          preparedReplacementClass,
          "moveReplacing",
          MethodType.methodType(void.class, Path.class, Path.class));
    } catch (ClassNotFoundException | IllegalAccessException | NoSuchMethodException exception) {
      throw new LinkageError("Failed to bind prepared-replacement move helper.", exception);
    }
  }

  private static MethodHandle bindPreparedReplacementConstructor() {
    try {
      Class<?> preparedReplacementClass =
          Class.forName(
              "dev.erst.fingrind.sqlite.SqliteProtectedBookMaintenanceStore$PreparedReplacement");
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(preparedReplacementClass, MethodHandles.lookup());
      return lookup.findConstructor(
          preparedReplacementClass,
          MethodType.methodType(void.class, Path.class, Path.class, Path.class));
    } catch (ClassNotFoundException | IllegalAccessException | NoSuchMethodException exception) {
      throw new LinkageError("Failed to bind prepared-replacement constructor.", exception);
    }
  }

  private static AclFileAttributeView throwingAclView(String message) {
    return new AclFileAttributeView() {
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
      public UserPrincipal getOwner() throws IOException {
        throw new IOException(message);
      }

      @Override
      public void setOwner(UserPrincipal ownerPrincipal) {
        throw new UnsupportedOperationException();
      }
    };
  }
}
