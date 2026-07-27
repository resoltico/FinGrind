package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowSourceMember;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowSourceMembers;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationAdmission;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationBinding;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationFailureOutcome;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationRecoveryRequest;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationSourceIdentity;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Shared fixture support for the durable, no-clobber publication primitives. */
abstract class SqliteArtifactPublicationTestSupport extends SqliteNativeBridgeTestSupport {
  protected static final SqliteProtectedBookVerificationSupport VERIFICATION_SUPPORT =
      new SqliteProtectedBookVerificationSupport();
  protected static final SqlitePassphraseResolver KEY_FILE_RESOLVER =
      (resolvedBookPath, passphraseSource, intent) ->
          switch (passphraseSource) {
            case BookAccess.PassphraseSource.KeyFile keyFile ->
                SqliteBookKeyFile.loadDecision(keyFile.bookKeyFilePath());
            case BookAccess.PassphraseSource.StandardInput _ ->
                throw new AssertionError("This fixture requires a key-file passphrase source.");
            case BookAccess.PassphraseSource.InteractivePrompt _ ->
                throw new AssertionError("This fixture requires a key-file passphrase source.");
          };

  protected SqliteProtectedBookMaintenanceStore maintenanceStore() {
    return new SqliteProtectedBookMaintenanceStore(KEY_FILE_RESOLVER);
  }

  protected static ProtectedBookAccess localAccess(BookAccess bookAccess) {
    return ProtectedBookAccess.fromPublished(bookAccess);
  }

  protected static ProtectedBookMaintenanceStore.VerifiedBook verifiedBook(
      SqliteProtectedBookMaintenanceStore store, BookAccess bookAccess) {
    return switch (acceptedValue(
        store.verifyInitializedBook(
            localAccess(bookAccess), ProtectedBookMaintenanceArtifactRole.LIVE_BOOK))) {
      case ProtectedBookMaintenanceStore.VerifiedBook verifiedBook -> verifiedBook;
      case ProtectedBookMaintenanceStore.VerificationFailure verificationFailure ->
          throw new AssertionError(
              "Expected a verified book but got " + verificationFailure.failure());
    };
  }

  protected final ProtectedBookMaintenanceStore.PreparedPairPublication prepareBackupPair(
      SqliteProtectedBookMaintenanceStore store, Path backupFilePath, Path backupKeyFilePath) {
    return prepared(admitBackupPair(store, backupFilePath, backupKeyFilePath));
  }

  /** Stages through the concrete prepared handle while the fixture retains its source scope. */
  protected final MaintenanceDecision<StagedBackupPair> stageBackupPairForFixture(
      ProtectedBookMaintenanceStore.VerifiedBook sourceBook,
      ProtectedBookMaintenanceStore.PreparedPairPublication preparedPairPublication) {
    SqliteVerifiedBook verifiedSourceBook = assertInstanceOf(SqliteVerifiedBook.class, sourceBook);
    return SqliteProtectedBookBackupStaging.stageResolvedPair(
        verifiedSourceBook.artifactPath(),
        fixturePreparedPublication(preparedPairPublication),
        verifiedSourceBook.passphraseCopy(),
        VERIFICATION_SUPPORT);
  }

  /** Produces non-secret immutable evidence for a staged backup-pair test publication. */
  protected static ProtectedBookPairPublicationBinding.Backup backupBinding(Path sourceBookPath) {
    return new ProtectedBookPairPublicationBinding.Backup(
        sourceBookPath,
        new AttestationBackupAcknowledgement(
            new UUID(0L, 1L), new byte[32], BigInteger.ZERO, new byte[32]));
  }

  /** Seals a staged backup with a non-empty envelope suffix so it can reach final publication. */
  protected static void sealBackupForPublication(StagedBackupPair stagedBackupPair) {
    StagedBackupPair checkedPair =
        java.util.Objects.requireNonNull(stagedBackupPair, "stagedBackupPair");
    byte[] snapshot = checkedPair.snapshot();
    checkedPair.sealArtifact(Arrays.copyOf(snapshot, snapshot.length + 1));
  }

  /** Produces non-secret immutable evidence for a staged restore-pair test publication. */
  protected static ProtectedBookPairPublicationBinding.Restore restoreBinding(
      Path backupArtifactPath, Path backupKeyPath) {
    return new ProtectedBookPairPublicationBinding.Restore(
        backupArtifactPath,
        backupKeyPath,
        new AttestationBackupAcknowledgement(
            new UUID(0L, 2L), new byte[32], BigInteger.ZERO, new byte[32]),
        testAttestationCommit());
  }

  /** Produces non-secret immutable evidence for a staged rekey-pair test publication. */
  protected static ProtectedBookPairPublicationBinding.Rekey rekeyBinding(
      Path sourceBookPath, Path sourceKeyPath) {
    return new ProtectedBookPairPublicationBinding.Rekey(
        new ProtectedBookPairPublicationSourceIdentity(
            sourceBookPath,
            ProtectedBookPairPublicationSourceIdentity.Kind.KEY_FILE,
            sourceKeyPath),
        testAttestationCommit(),
        new AttestationCommit(BigInteger.ONE, "1".repeat(64)));
  }

  private static AttestationCommit testAttestationCommit() {
    return new AttestationCommit(BigInteger.ZERO, "0".repeat(64));
  }

  protected final ProtectedBookPairPublicationAdmission admitBackupPair(
      SqliteProtectedBookMaintenanceStore store, Path backupFilePath, Path backupKeyFilePath) {
    return admitPairPublication(
        store,
        backupFilePath,
        backupKeyFilePath,
        RestoredBookTargetPolicy.REQUIRE_ABSENT,
        new ProtectedBookPairPublicationRecoveryRequest.Backup(
            backupFilePath.resolveSibling("source.sqlite"), new UUID(0L, 0L)),
        ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
        ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET);
  }

  protected final ProtectedBookMaintenanceStore.PreparedPairPublication prepareRestoredBookPair(
      SqliteProtectedBookMaintenanceStore store,
      Path restoredBookPath,
      Path restoredBookKeyPath,
      RestoredBookTargetPolicy targetPolicy) {
    return prepared(
        admitRestoredBookPair(store, restoredBookPath, restoredBookKeyPath, targetPolicy));
  }

  /** Stages through the concrete prepared handle while the fixture retains its source scope. */
  protected final MaintenanceDecision<dev.erst.fingrind.executor.spi.StagedRestoredBookPair>
      stageRestoredBookPairForFixture(
          ProtectedBookMaintenanceStore.VerifiedBook sourceBook,
          ProtectedBookMaintenanceStore.PreparedPairPublication preparedPairPublication) {
    SqliteVerifiedBook verifiedSourceBook = assertInstanceOf(SqliteVerifiedBook.class, sourceBook);
    return SqliteProtectedBookRestoreStaging.stageResolvedPair(
        verifiedSourceBook.artifactPath(),
        fixturePreparedPublication(preparedPairPublication),
        verifiedSourceBook.passphraseCopy(),
        VERIFICATION_SUPPORT);
  }

  protected final ProtectedBookPairPublicationAdmission admitRestoredBookPair(
      SqliteProtectedBookMaintenanceStore store,
      Path restoredBookPath,
      Path restoredBookKeyPath,
      RestoredBookTargetPolicy targetPolicy) {
    return admitPairPublication(
        store,
        restoredBookPath,
        restoredBookKeyPath,
        targetPolicy,
        new ProtectedBookPairPublicationRecoveryRequest.Restore(
            restoredBookPath.resolveSibling("source-backup.sqlite"),
            restoredBookPath.resolveSibling("source-backup.key"),
            new AttestationBackupAcknowledgement(
                new UUID(0L, 0L), new byte[32], BigInteger.ZERO, new byte[32])),
        ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET,
        ProtectedBookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET);
  }

  /**
   * Exercises target admission only through the same complete scope that production workflows use,
   * while keeping an isolated test-owned source anchor out of target assertions.
   */
  protected final ProtectedBookPairPublicationAdmission admitPairPublication(
      SqliteProtectedBookMaintenanceStore store,
      Path bookTargetPath,
      Path secretTargetPath,
      RestoredBookTargetPolicy bookTargetPolicy,
      ProtectedBookPairPublicationRecoveryRequest request,
      ProtectedBookMaintenanceArtifactRole bookTargetArtifactRole,
      ProtectedBookMaintenanceArtifactRole secretTargetArtifactRole) {
    Path sourceAnchor;
    try {
      sourceAnchor =
          tempDirectory
              .resolve(".workflow-sources")
              .resolve(UUID.randomUUID().toString())
              .resolve("source.sqlite");
      Path sourceParent =
          Objects.requireNonNull(sourceAnchor.getParent(), "workflow source anchor parent");
      Files.createDirectories(sourceParent);
      SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(sourceParent);
      SqliteBookFileSecurity.createNewOwnerOnlyBookFile(sourceAnchor);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Could not create an isolated workflow-scope test source.", exception);
    }
    return admitPairPublicationFromSourceAnchor(
        store,
        bookTargetPath,
        secretTargetPath,
        bookTargetPolicy,
        request,
        bookTargetArtifactRole,
        secretTargetArtifactRole,
        sourceAnchor);
  }

  private static ProtectedBookPairPublicationAdmission admitPairPublicationFromSourceAnchor(
      SqliteProtectedBookMaintenanceStore store,
      Path bookTargetPath,
      Path secretTargetPath,
      RestoredBookTargetPolicy bookTargetPolicy,
      ProtectedBookPairPublicationRecoveryRequest request,
      ProtectedBookMaintenanceArtifactRole bookTargetArtifactRole,
      ProtectedBookMaintenanceArtifactRole secretTargetArtifactRole,
      Path sourceAnchor) {
    ProtectedBookMaintenanceStore.WorkflowScopeAcquisition scopeAcquisition;
    try {
      scopeAcquisition =
          store.acquireWorkflowScope(
              new WorkflowSourceMembers(
                  List.of(
                      new WorkflowSourceMember(
                          sourceAnchor, ProtectedBookMaintenanceArtifactRole.LIVE_BOOK))),
              bookTargetPath,
              bookTargetArtifactRole,
              secretTargetPath,
              secretTargetArtifactRole);
    } catch (RuntimeException | Error failure) {
      deleteTestSourceAnchorPreservingFailure(sourceAnchor, failure);
      throw failure;
    }
    if (scopeAcquisition instanceof ProtectedBookMaintenanceStore.WorkflowScopeBusy busy) {
      deleteTestSourceAnchor(sourceAnchor);
      throw new dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException(
          new dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection.ArtifactBusy(
              busy.artifactRole(), busy.artifactPath()));
    }
    ProtectedBookMaintenanceStore.HeldWorkflowScope scope =
        (ProtectedBookMaintenanceStore.HeldWorkflowScope) scopeAcquisition;
    ProtectedBookPairPublicationAdmission admission;
    try {
      admission = scope.admitPairPublication(bookTargetPolicy, request);
      if (admission instanceof ProtectedBookPairPublicationAdmission.Prepared prepared) {
        return new ProtectedBookPairPublicationAdmission.Prepared(
            new ScopeBoundPreparedPublication(prepared.publication(), scope, sourceAnchor));
      }
    } catch (RuntimeException | Error failure) {
      closeScopePreservingFailure(scope, failure);
      deleteTestSourceAnchorPreservingFailure(sourceAnchor, failure);
      throw failure;
    }
    try {
      scope.close();
    } catch (RuntimeException | Error failure) {
      deleteTestSourceAnchorPreservingFailure(sourceAnchor, failure);
      throw failure;
    }
    deleteTestSourceAnchor(sourceAnchor);
    return admission;
  }

  private static void closeScopePreservingFailure(
      ProtectedBookMaintenanceStore.HeldWorkflowScope scope, Throwable failure) {
    try {
      scope.close();
    } catch (RuntimeException | Error cleanupFailure) {
      failure.addSuppressed(cleanupFailure);
    }
  }

  private static ProtectedBookMaintenanceStore.PreparedPairPublication prepared(
      ProtectedBookPairPublicationAdmission admission) {
    return switch (admission) {
      case ProtectedBookPairPublicationAdmission.Prepared prepared -> prepared.publication();
      case ProtectedBookPairPublicationAdmission.Recovered _ ->
          throw new AssertionError("Fixture unexpectedly recovered a protected-book pair.");
      case ProtectedBookPairPublicationAdmission.ExistingCompleteBackup _ ->
          throw new AssertionError("Fixture unexpectedly found an existing backup pair.");
      case ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired
              prepublication ->
          throw new AssertionError(
              "Fixture unexpectedly required prepublication recovery: " + prepublication);
      case ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked blocked ->
          throw new AssertionError("Fixture encountered blocked pair evidence: " + blocked);
      case ProtectedBookPairPublicationFailureOutcome.CompletionUncertain uncertain ->
          throw new AssertionError("Fixture encountered uncertain pair evidence: " + uncertain);
    };
  }

  protected static SqlitePreparedPairPublication fixturePreparedPublication(
      ProtectedBookMaintenanceStore.PreparedPairPublication preparedPublication) {
    if (preparedPublication instanceof ScopeBoundPreparedPublication scopeBound) {
      return assertInstanceOf(SqlitePreparedPairPublication.class, scopeBound.delegate());
    }
    return assertInstanceOf(SqlitePreparedPairPublication.class, preparedPublication);
  }

  private record ScopeBoundPreparedPublication(
      ProtectedBookMaintenanceStore.PreparedPairPublication delegate,
      ProtectedBookMaintenanceStore.HeldWorkflowScope scope,
      Path sourceAnchor)
      implements ProtectedBookMaintenanceStore.PreparedPairPublication {
    private ScopeBoundPreparedPublication {
      Objects.requireNonNull(delegate, "delegate");
      Objects.requireNonNull(scope, "scope");
      Objects.requireNonNull(sourceAnchor, "sourceAnchor");
    }

    @Override
    public Path bookTargetPath() {
      return delegate.bookTargetPath();
    }

    @Override
    public Path secretTargetPath() {
      return delegate.secretTargetPath();
    }

    @Override
    public RestoredBookTargetPolicy bookTargetPolicy() {
      return delegate.bookTargetPolicy();
    }

    @Override
    public void close() {
      try {
        closePublicationAndScope(scope, delegate);
      } finally {
        deleteTestSourceAnchor(sourceAnchor);
      }
    }
  }

  private static void closePublicationAndScope(
      ProtectedBookMaintenanceStore.HeldWorkflowScope scope,
      ProtectedBookMaintenanceStore.PreparedPairPublication publication) {
    try (scope;
        publication) {
      // Resources close in publication-then-scope order, matching the production lifetime.
    }
  }

  private static void deleteTestSourceAnchor(Path sourceAnchor) {
    try {
      Files.deleteIfExists(Objects.requireNonNull(sourceAnchor, "sourceAnchor"));
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Could not remove one test-owned workflow source anchor.", exception);
    }
  }

  private static void deleteTestSourceAnchorPreservingFailure(
      Path sourceAnchor, Throwable failure) {
    try {
      deleteTestSourceAnchor(sourceAnchor);
    } catch (RuntimeException cleanupFailure) {
      Objects.requireNonNull(failure, "failure").addSuppressed(cleanupFailure);
    }
  }

  protected void initializeBook(BookAccess bookAccess) {
    Instant initializedAt = Instant.parse("2026-05-19T09:00:00Z");
    try (SqlitePostingFactStore store = SqliteStoreFixtureSupport.openStore(bookAccess)) {
      BookOpeningOutcome outcome =
          store.openAttestedBook(
              initializedAt,
              SqlitePostingFactFixtureSupport.bookIdentity(),
              List.of(),
              SqliteAttestationTestSupport.genesis(
                  SqlitePostingFactFixtureSupport.bookIdentity(), initializedAt));
      if (!(outcome instanceof BookOpeningOutcome.Opened)) {
        throw new AssertionError("Could not create an attested fixture book: " + outcome);
      }
    }
  }

  protected Path writeArtifact(String fileName, String content) throws IOException {
    Path artifactPath = tempDirectory.resolve(fileName);
    Path parent = artifactPath.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
      SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parent);
    }
    Files.writeString(artifactPath, content);
    return artifactPath;
  }

  protected static <T> T acceptedValue(MaintenanceDecision<T> decision) {
    return switch (decision) {
      case MaintenanceDecision.Accepted<T>(T value) -> value;
      case MaintenanceDecision.Failed<T>(MaintenanceFailure failure) ->
          throw new AssertionError("Expected accepted maintenance decision but got " + failure);
    };
  }

  protected static MaintenanceFailure failedValue(MaintenanceDecision<?> decision) {
    return switch (decision) {
      case MaintenanceDecision.Accepted<?> accepted ->
          throw new AssertionError("Expected failed maintenance decision but got " + accepted);
      case MaintenanceDecision.Failed<?> failed -> failed.failure();
    };
  }

  protected static void assertVerificationFailure(
      ProtectedBookMaintenanceStore.BookVerification verification,
      Path expectedArtifactPath,
      ProtectedBookVerificationFailure expectedFailure) {
    ProtectedBookMaintenanceStore.VerificationFailure failure =
        assertInstanceOf(ProtectedBookMaintenanceStore.VerificationFailure.class, verification);
    assertEquals(expectedArtifactPath.toAbsolutePath().normalize(), failure.artifactPath());
    assertEquals(expectedFailure, failure.failure());
  }

  protected static SqliteStagedRestoredBookPair newStagedRestoredBookPair(
      Path stagedBookPath,
      Path finalBookPath,
      Path stagedBookKeyFilePath,
      Path finalBookKeyFilePath,
      SqliteBookPassphrase restoredPassphrase) {
    return SqliteStagedRestoredBookPairFactory.create(
        new SqliteStagedProtectedBookPairArtifacts(
            SqliteOwnedStagedArtifact.recordExisting(finalBookPath, stagedBookPath),
            finalBookPath,
            SqliteOwnedStagedArtifact.recordExisting(finalBookKeyFilePath, stagedBookKeyFilePath),
            finalBookKeyFilePath),
        RestoredBookTargetPolicy.REPLACE_SELECTED,
        restoredPassphrase,
        VERIFICATION_SUPPORT);
  }
}
