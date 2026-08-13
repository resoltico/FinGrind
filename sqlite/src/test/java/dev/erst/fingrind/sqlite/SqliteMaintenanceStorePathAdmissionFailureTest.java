package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.maintenance.ProtectedPublicationPathFailure;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowSourceMember;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowSourceMembers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Exercises maintenance path normalization, workflow admission, and lease failure boundaries. */
class SqliteMaintenanceStorePathAdmissionFailureTest extends SqliteArtifactPublicationTestSupport {
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
        ProtectedPublicationPathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE,
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
        ProtectedPublicationPathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE,
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
  void maintenanceArtifactStore_reportsSidecarsAndMapsDirectLeasePathFailures() throws Exception {
    Path bookPath = writeArtifact("forwarded-blocking-artifacts.sqlite", "book");
    Path backupPath = writeArtifact("forwarded-backup-artifacts.fgba", "backup");
    Path bookJournal = bookPath.resolveSibling(bookPath.getFileName() + "-journal");
    Path bookWal = bookPath.resolveSibling(bookPath.getFileName() + "-wal");
    Path backupShm = backupPath.resolveSibling(backupPath.getFileName() + "-shm");
    Files.writeString(bookJournal, "journal");
    Files.writeString(bookWal, "wal");
    Files.writeString(backupShm, "shm");
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();

    assertEquals(List.of(bookJournal, bookWal), store.blockingArtifactsForBook(bookPath));
    assertEquals(List.of(backupShm), store.blockingArtifactsForBackupSource(backupPath));

    Path missingTarget = tempDirectory.resolve("missing-lease-parent").resolve("book.sqlite");
    ProtectedBookMaintenanceRejection.ArtifactPathInvalid rejection =
        assertInstanceOf(
            ProtectedBookMaintenanceRejection.ArtifactPathInvalid.class,
            assertThrows(
                    ProtectedBookMaintenanceRejectionException.class,
                    () ->
                        store.acquireManagedArtifactLease(
                            missingTarget, ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET))
                .rejection());
    assertEquals(ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET, rejection.artifactRole());
    assertEquals(missingTarget.toAbsolutePath(), rejection.artifactPath());
  }

  @Test
  void maintenanceFinalTargetNormalizationPreservesParentMetadataIoFailures() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      SqliteProtectedBookMaintenanceStore store = maintenanceStore();
      AclFixturePath bookTarget = fileSystem.path("\\book-target\\backup.sqlite");
      AclFixturePath bookParent = assertInstanceOf(AclFixturePath.class, bookTarget.getParent());
      bookParent.exists = true;
      bookParent.overrideAclView = failingMaintenanceAclView();

      IllegalStateException bookFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  store.normalizeFinalTarget(
                      bookTarget,
                      "backupFilePath",
                      ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET));
      assertEquals(
          "Failed to prepare the protected-book maintenance target parent"
              + " \\book-target\\backup.sqlite.",
          bookFailure.getMessage());
      assertInstanceOf(IOException.class, bookFailure.getCause());

      AclFixturePath keyTarget = fileSystem.path("\\key-target\\backup.key");
      AclFixturePath keyParent = assertInstanceOf(AclFixturePath.class, keyTarget.getParent());
      keyParent.exists = true;
      keyParent.overrideAclView = failingMaintenanceAclView();

      IllegalStateException keyFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  store.normalizeFinalTarget(
                      keyTarget,
                      "backupKeyFilePath",
                      ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));
      assertEquals(
          "Failed to prepare the protected-book maintenance target parent"
              + " \\key-target\\backup.key.",
          keyFailure.getMessage());
      assertInstanceOf(IOException.class, keyFailure.getCause());
    }
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
        ProtectedPublicationPathFailure.SOURCE_ARTIFACT_IDENTITY_DUPLICATED,
        rejection.pathFailure());
    assertFalse(Files.exists(directoryControl, java.nio.file.LinkOption.NOFOLLOW_LINKS));
  }

  @Test
  void workflowScopePreservesAnUnexpectedFilesystemIdentityFailure() {
    IOException acquisitionFailure = new IOException("injected identity inspection failure");
    SqliteProtectedBookMaintenanceStore store =
        new SqliteProtectedBookMaintenanceStore(
            KEY_FILE_RESOLVER,
            (ignoredSources,
                ignoredBookTarget,
                ignoredBookRole,
                ignoredSecretTarget,
                ignoredSecretRole) -> {
              throw acquisitionFailure;
            });
    Path source = tempDirectory.resolve("identity-failure-source.sqlite");
    Path bookTarget = tempDirectory.resolve("identity-failure-target.sqlite");
    Path secretTarget = tempDirectory.resolve("identity-failure-target.key");

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                store.acquireWorkflowScope(
                    new WorkflowSourceMembers(
                        List.of(
                            new WorkflowSourceMember(
                                source, ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE))),
                    bookTarget,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                    secretTarget,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));

    assertEquals(
        "Failed to establish distinct physical identities for the protected-book maintenance"
            + " sources.",
        failure.getMessage());
    assertSame(acquisitionFailure, failure.getCause());
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

  @Test
  void maintenanceStoreMapsFinalTargetLeafRejectionAndReturnsHeldDirectLeases() throws Exception {
    Path existingSource = writeArtifact("direct-lease-source.sqlite", "maintenance source");
    Path managedTarget = existingSource.resolveSibling("direct-lease-target.sqlite");
    Path liveBookTarget = existingSource.resolveSibling("direct-live-book-target.sqlite");
    Path directoryTarget = Files.createDirectory(existingSource.resolveSibling("directory-target"));
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();

    assertEquals(
        liveBookTarget,
        store.normalizeFinalTarget(
            liveBookTarget, "liveBookPath", ProtectedBookMaintenanceArtifactRole.LIVE_BOOK));

    ProtectedBookMaintenanceRejectionException finalTargetFailure =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                store.normalizeFinalTarget(
                    directoryTarget,
                    "restoredBookPath",
                    ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET));
    ProtectedBookMaintenanceRejection.ArtifactPathInvalid rejection =
        assertInstanceOf(
            ProtectedBookMaintenanceRejection.ArtifactPathInvalid.class,
            finalTargetFailure.rejection());
    assertEquals(ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET, rejection.artifactRole());

    try (ProtectedBookMaintenanceStore.HeldLease existingLease =
        assertInstanceOf(
            ProtectedBookMaintenanceStore.HeldLease.class,
            store.acquireExistingArtifactLease(
                existingSource, ProtectedBookMaintenanceArtifactRole.LIVE_BOOK))) {
      assertEquals(existingSource, existingLease.artifactPath());
    }
    try (ProtectedBookMaintenanceStore.HeldLease managedLease =
        assertInstanceOf(
            ProtectedBookMaintenanceStore.HeldLease.class,
            store.acquireManagedArtifactLease(
                managedTarget, ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET))) {
      assertEquals(managedTarget, managedLease.artifactPath());
    }
  }

  @Test
  void managedArtifactLeaseReportsBusyWhenAnotherThreadAlreadyHoldsItsDirectory() throws Exception {
    Path target = tempDirectory.resolve("contended-managed-target.sqlite");
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();

    try (ProtectedBookMaintenanceStore.HeldLease ignored =
        assertInstanceOf(
            ProtectedBookMaintenanceStore.HeldLease.class,
            store.acquireManagedArtifactLease(
                target, ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET))) {
      AtomicReference<ProtectedBookMaintenanceStore.LeaseAcquisition> acquisition =
          new AtomicReference<>();
      AtomicReference<Throwable> failure = new AtomicReference<>();
      Thread contender =
          Thread.ofPlatform()
              .start(
                  () -> {
                    try {
                      acquisition.set(
                          store.acquireManagedArtifactLease(
                              target, ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET));
                    } catch (Throwable thrown) {
                      failure.set(thrown);
                    }
                  });
      contender.join(10_000L);

      assertFalse(contender.isAlive(), "contended lease acquisition must not block");
      assertNull(failure.get());
      ProtectedBookMaintenanceStore.LeaseBusy busy =
          assertInstanceOf(ProtectedBookMaintenanceStore.LeaseBusy.class, acquisition.get());
      assertEquals(target, busy.artifactPath());
    }
  }

  private static void createDirectorySymlinkOrSkip(Path link, Path target) throws IOException {
    try {
      Files.createSymbolicLink(link, target);
    } catch (UnsupportedOperationException | java.nio.file.FileSystemException unavailable) {
      assumeTrue(false, "host filesystem cannot create symbolic links: " + unavailable);
    }
  }

  private static AclFileAttributeView failingMaintenanceAclView() {
    return new AclFileAttributeView() {
      @Override
      public String name() {
        return "acl";
      }

      @Override
      public List<AclEntry> getAcl() throws IOException {
        throw new IOException("injected maintenance ACL metadata failure");
      }

      @Override
      public void setAcl(List<AclEntry> acl) throws IOException {
        throw new IOException("injected maintenance ACL metadata failure");
      }

      @Override
      public UserPrincipal getOwner() throws IOException {
        throw new IOException("injected maintenance ACL metadata failure");
      }

      @Override
      public void setOwner(UserPrincipal owner) throws IOException {
        throw new IOException("injected maintenance ACL metadata failure");
      }
    };
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
