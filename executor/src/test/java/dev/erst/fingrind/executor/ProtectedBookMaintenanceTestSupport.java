package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.runtime.PublicPathHint;
import dev.erst.fingrind.executor.maintenance.MaintenanceCompletion;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceAuditCompensationKind;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceAuditKind;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import dev.erst.fingrind.executor.spi.StagedBookReplacement;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import dev.erst.fingrind.executor.spi.StagedRollbackArtifactDeletion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/** Shared protected-book maintenance fixtures for executor service tests. */
final class ProtectedBookMaintenanceTestSupport {
  static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-05-19T10:15:30Z"), ZoneOffset.UTC);

  private ProtectedBookMaintenanceTestSupport() {}

  static ProtectedBookMaintenanceService service(FakeMaintenanceStore store) {
    return new ProtectedBookMaintenanceService(FIXED_CLOCK, store);
  }

  static BookAccess access(Path bookFilePath) {
    return new BookAccess(
        bookFilePath,
        new BookAccess.PassphraseSource.KeyFile(
            bookFilePath.resolveSibling("book-passphrase.key").toAbsolutePath().normalize()));
  }

  static Path path(Path tempDirectory, String relativePath) {
    return tempDirectory.resolve(relativePath).toAbsolutePath().normalize();
  }

  static PublicPathHint hint(Path path) {
    return PublicPathHint.fromPath(path);
  }

  static ContractFailure internalError(String argument) {
    return new ContractFailure(
        ContractErrors.Descriptor.INTERNAL_ERROR, "maintenance failure", "retry later", argument);
  }

  static void touch(Path path) {
    try {
      if (path.getParent() != null) {
        Files.createDirectories(path.getParent());
      }
      if (!Files.exists(path)) {
        Files.createFile(path);
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to create test file: " + path, exception);
    }
  }
}

record RecordedAudit(Path bookPath, Instant recordedAt, Enum<?> auditKind) {
  RecordedAudit {
    Objects.requireNonNull(bookPath, "bookPath");
    Objects.requireNonNull(recordedAt, "recordedAt");
    Objects.requireNonNull(auditKind, "auditKind");
  }
}

/** Deterministic protected-book maintenance store fixture for service-path tests. */
final class FakeMaintenanceStore implements ProtectedBookMaintenanceStore {
  final Map<Path, List<Path>> rollbackArtifacts = new ConcurrentHashMap<>();
  final Map<Path, List<Path>> bookBlockingArtifacts = new ConcurrentHashMap<>();
  final Map<Path, List<Path>> backupBlockingArtifacts = new ConcurrentHashMap<>();
  final Map<Path, MaintenanceDecision<BookVerification>> verifications = new ConcurrentHashMap<>();
  final Set<Path> busyManagedArtifacts = ConcurrentHashMap.newKeySet();
  final Set<Path> busyExistingArtifacts = ConcurrentHashMap.newKeySet();
  final List<RecordedAudit> recordedAudits = new ArrayList<>();
  final List<RecordedAudit> compensatedAudits = new ArrayList<>();
  @Nullable MaintenanceDecision<StagedBackupPair> stageBackupFailure;
  @Nullable MaintenanceDecision<MaintenanceCompletion> appendAuditFailure;
  @Nullable MaintenanceDecision<MaintenanceCompletion> compensateAuditFailure;
  @Nullable ProtectedBookMaintenanceRejection verifyInitializedBookRejection;
  @Nullable ProtectedBookMaintenanceRejection existingLeaseRejection;
  @Nullable ProtectedBookMaintenanceRejection managedLeaseRejection;
  @Nullable ProtectedBookMaintenanceRejection stageReplacementRejection;
  @Nullable ProtectedBookMaintenanceRejection stageRollbackDeletionRejection;
  @Nullable Path stagedReplacementPath;
  boolean failBackupPairCommit;
  boolean failRollbackDeletionCommit;
  boolean lastStagedBackupPairCommitted;
  boolean lastStagedBackupPairClosed;
  boolean lastRollbackDeletionCommitted;
  boolean lastRollbackDeletionClosed;

  Path normalized(Path path) {
    return path.toAbsolutePath().normalize();
  }

  @Override
  public Path normalize(Path path, String argumentName) {
    Objects.requireNonNull(path, argumentName);
    return normalized(path);
  }

  @Override
  public List<Path> blockingArtifactsForBook(Path normalizedBookPath) {
    return bookBlockingArtifacts.getOrDefault(normalizedBookPath, List.of());
  }

  @Override
  public List<Path> blockingArtifactsForBackupSource(Path normalizedBackupFilePath) {
    return backupBlockingArtifacts.getOrDefault(normalizedBackupFilePath, List.of());
  }

  @Override
  public LeaseAcquisition acquireExistingArtifactLease(
      Path normalizedArtifactPath, ProtectedBookMaintenanceArtifactRole artifactRole) {
    if (existingLeaseRejection != null) {
      throw new ProtectedBookMaintenanceRejectionException(existingLeaseRejection);
    }
    if (busyExistingArtifacts.contains(normalizedArtifactPath)) {
      return new LeaseBusy(normalizedArtifactPath);
    }
    return new FakeLease(normalizedArtifactPath);
  }

  @Override
  public LeaseAcquisition acquireManagedArtifactLease(
      Path normalizedArtifactPath, ProtectedBookMaintenanceArtifactRole artifactRole) {
    if (managedLeaseRejection != null) {
      throw new ProtectedBookMaintenanceRejectionException(managedLeaseRejection);
    }
    if (busyManagedArtifacts.contains(normalizedArtifactPath)) {
      return new LeaseBusy(normalizedArtifactPath);
    }
    return new FakeLease(normalizedArtifactPath);
  }

  @Override
  public MaintenanceDecision<BookVerification> verifyInitializedBook(
      ProtectedBookAccess bookAccess, ProtectedBookMaintenanceArtifactRole artifactRole) {
    if (verifyInitializedBookRejection != null) {
      throw new ProtectedBookMaintenanceRejectionException(verifyInitializedBookRejection);
    }
    return verifications.getOrDefault(
        normalized(bookAccess.bookFilePath()),
        MaintenanceDecision.accepted(new FakeVerifiedBook(normalized(bookAccess.bookFilePath()))));
  }

  @Override
  public MaintenanceDecision<StagedBackupPair> stageBackupPair(
      VerifiedBook sourceBook,
      Path normalizedBackupFilePath,
      Path normalizedBackupBookKeyFilePath) {
    if (stageBackupFailure != null) {
      return stageBackupFailure;
    }
    BookVerification stagedBackupVerification =
        switch (verifications.getOrDefault(
            normalized(normalizedBackupFilePath),
            MaintenanceDecision.accepted(
                new FakeVerifiedBook(normalized(normalizedBackupFilePath))))) {
          case MaintenanceDecision.Accepted<BookVerification>(BookVerification verification) ->
              verification;
          case MaintenanceDecision.Failed<BookVerification>(MaintenanceFailure failure) ->
              throw new AssertionError(
                  "Expected accepted staged-backup verification but got " + failure);
        };
    return MaintenanceDecision.accepted(new FakeStagedBackupPair(stagedBackupVerification));
  }

  @Override
  public MaintenanceDecision<BookVerification> verifyInitializedReplica(
      Path normalizedReplicaBookPath, VerifiedBook sourceBook) {
    Objects.requireNonNull(sourceBook, "sourceBook");
    return verifications.getOrDefault(
        normalized(normalizedReplicaBookPath),
        MaintenanceDecision.accepted(new FakeVerifiedBook(normalized(normalizedReplicaBookPath))));
  }

  @Override
  public MaintenanceDecision<StagedRestoredBookPair> stageRestoredBookPair(
      VerifiedBook sourceBook, Path normalizedBookFilePath, Path normalizedBookKeyFilePath) {
    Objects.requireNonNull(sourceBook, "sourceBook");
    Objects.requireNonNull(normalizedBookFilePath, "normalizedBookFilePath");
    Objects.requireNonNull(normalizedBookKeyFilePath, "normalizedBookKeyFilePath");
    if (stageReplacementRejection != null) {
      throw new ProtectedBookMaintenanceRejectionException(stageReplacementRejection);
    }
    Path stagedBookPath =
        stagedReplacementPath != null
            ? normalized(stagedReplacementPath)
            : normalized(normalizedBookFilePath);
    stagedReplacementPath = null;
    BookVerification stagedRestoredVerification =
        switch (verifications.getOrDefault(
            stagedBookPath, MaintenanceDecision.accepted(new FakeVerifiedBook(stagedBookPath)))) {
          case MaintenanceDecision.Accepted<BookVerification>(BookVerification verification) ->
              verification;
          case MaintenanceDecision.Failed<BookVerification>(MaintenanceFailure failure) ->
              throw new AssertionError(
                  "Expected accepted staged-restore verification but got " + failure);
        };
    return MaintenanceDecision.accepted(new FakeStagedRestoredBookPair(stagedRestoredVerification));
  }

  @Override
  public StagedBookReplacement stageReplacement(
      Path normalizedSourceBookPath, Path normalizedTargetBookPath) {
    if (stageReplacementRejection != null) {
      throw new ProtectedBookMaintenanceRejectionException(stageReplacementRejection);
    }
    Path stagedBookPath =
        stagedReplacementPath != null
            ? normalized(stagedReplacementPath)
            : normalized(normalizedSourceBookPath);
    stagedReplacementPath = null;
    return new FakeStagedBookReplacement(stagedBookPath);
  }

  @Override
  public List<Path> staleRollbackArtifacts(Path normalizedBookPath) {
    return rollbackArtifacts.getOrDefault(normalizedBookPath, List.of());
  }

  @Override
  public boolean isRollbackArtifactForBook(
      Path normalizedBookPath, Path normalizedRollbackArtifactPath) {
    return staleRollbackArtifacts(normalizedBookPath).contains(normalizedRollbackArtifactPath);
  }

  @Override
  public StagedRollbackArtifactDeletion stageRollbackArtifactDeletion(
      Path normalizedRollbackArtifactPath) {
    if (stageRollbackDeletionRejection != null) {
      throw new ProtectedBookMaintenanceRejectionException(stageRollbackDeletionRejection);
    }
    return new FakeStagedRollbackArtifactDeletion();
  }

  @Override
  public MaintenanceDecision<MaintenanceCompletion> appendMaintenanceAudit(
      VerifiedBook verifiedBook, Instant recordedAt, ProtectedBookMaintenanceAuditKind auditKind) {
    if (appendAuditFailure != null) {
      return appendAuditFailure;
    }
    recordedAudits.add(
        new RecordedAudit(normalized(verifiedBook.artifactPath()), recordedAt, auditKind));
    return MaintenanceDecision.accepted(MaintenanceCompletion.DONE);
  }

  @Override
  public MaintenanceDecision<MaintenanceCompletion> appendMaintenanceAuditCompensation(
      VerifiedBook verifiedBook,
      Instant recordedAt,
      ProtectedBookMaintenanceAuditCompensationKind auditKind) {
    if (compensateAuditFailure != null) {
      return compensateAuditFailure;
    }
    compensatedAudits.add(
        new RecordedAudit(normalized(verifiedBook.artifactPath()), recordedAt, auditKind));
    return MaintenanceDecision.accepted(MaintenanceCompletion.DONE);
  }

  /** Deterministic verified-book fixture that carries one normalized artifact path only. */
  private static final class FakeVerifiedBook implements VerifiedBook {
    private final Path artifactPath;

    private FakeVerifiedBook(Path artifactPath) {
      this.artifactPath = artifactPath;
    }

    @Override
    public Path artifactPath() {
      return artifactPath;
    }

    @Override
    public void close() {}
  }

  /** Deterministic held-lease fixture for one normalized artifact path. */
  private static final class FakeLease implements HeldLease {
    private final Path artifactPath;

    private FakeLease(Path artifactPath) {
      this.artifactPath = artifactPath;
    }

    @Override
    public Path artifactPath() {
      return artifactPath;
    }

    @Override
    public void close() {}
  }

  /** Deterministic staged-backup fixture that can publish or fail on demand. */
  private final class FakeStagedBackupPair implements StagedBackupPair {
    private final BookVerification backupVerification;

    private FakeStagedBackupPair(BookVerification backupVerification) {
      this.backupVerification = backupVerification;
    }

    @Override
    public MaintenanceDecision<BookVerification> verifyInitializedBackup() {
      return MaintenanceDecision.accepted(backupVerification);
    }

    @Override
    public void commit() {
      if (failBackupPairCommit) {
        throw new IllegalStateException("backup pair publish failed");
      }
      lastStagedBackupPairCommitted = true;
    }

    @Override
    public void rollback() {}

    @Override
    public void close() {
      lastStagedBackupPairClosed = true;
    }
  }

  /** Deterministic staged-replacement fixture for restore-style maintenance flows. */
  private static final class FakeStagedBookReplacement implements StagedBookReplacement {
    private final Path stagedBookPath;

    private FakeStagedBookReplacement(Path stagedBookPath) {
      this.stagedBookPath = stagedBookPath;
    }

    @Override
    public Path stagedBookPath() {
      return stagedBookPath;
    }

    @Override
    public void commit() {}

    @Override
    public void rollback() {}

    @Override
    public void close() {}
  }

  /** Deterministic staged-restored-book fixture that can verify and publish on demand. */
  private static final class FakeStagedRestoredBookPair implements StagedRestoredBookPair {
    private final BookVerification restoredVerification;

    private FakeStagedRestoredBookPair(BookVerification restoredVerification) {
      this.restoredVerification = restoredVerification;
    }

    @Override
    public MaintenanceDecision<BookVerification> verifyInitializedRestoredBook() {
      return MaintenanceDecision.accepted(restoredVerification);
    }

    @Override
    public void commit() {}

    @Override
    public void rollback() {}

    @Override
    public void close() {}
  }

  /** Deterministic staged rollback-artifact deletion fixture. */
  private final class FakeStagedRollbackArtifactDeletion implements StagedRollbackArtifactDeletion {
    @Override
    public void commit() {
      if (failRollbackDeletionCommit) {
        throw new IllegalStateException("rollback deletion failed");
      }
      lastRollbackDeletionCommitted = true;
    }

    @Override
    public void rollback() {}

    @Override
    public void close() {
      lastRollbackDeletionClosed = true;
    }
  }
}
