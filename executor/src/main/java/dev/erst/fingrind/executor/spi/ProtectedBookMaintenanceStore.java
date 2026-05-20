package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.executor.maintenance.MaintenanceCompletion;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceAuditCompensationKind;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceAuditKind;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Narrow SPI for protected-book maintenance verification and staged filesystem work. */
public interface ProtectedBookMaintenanceStore {
  /** Returns one normalized absolute path for the supplied maintenance argument. */
  Path normalize(Path path, String argumentName);

  /** Lists every artifact that blocks one clean live-book maintenance workflow. */
  List<Path> blockingArtifactsForBook(Path normalizedBookPath);

  /** Lists every artifact that blocks one clean backup-source restore workflow. */
  List<Path> blockingArtifactsForBackupSource(Path normalizedBackupFilePath);

  /** Acquires one exclusive maintenance lease for one existing protected-book artifact path. */
  LeaseAcquisition acquireExistingArtifactLease(Path normalizedArtifactPath);

  /** Acquires one exclusive maintenance lease for one managed protected-book artifact path. */
  LeaseAcquisition acquireManagedArtifactLease(Path normalizedArtifactPath);

  /** Verifies that the supplied protected book opens as one initialized FinGrind book. */
  MaintenanceDecision<BookVerification> verifyInitializedBook(ProtectedBookAccess bookAccess);

  /** Stages one encrypted backup pair without publishing it to the final destination yet. */
  MaintenanceDecision<StagedBackupPair> stageBackupPair(
      ProtectedBookAccess sourceAccess,
      Path normalizedBackupFilePath,
      Path normalizedBackupBookKeyFilePath);

  /** Stages one reversible replacement of the selected live book path with one verified source. */
  StagedBookReplacement stageReplacement(
      Path normalizedSourceBookPath, Path normalizedTargetBookPath);

  /** Lists every sibling rollback artifact that belongs to the supplied live book path. */
  List<Path> staleRollbackArtifacts(Path normalizedBookPath);

  /** Returns whether the selected rollback artifact belongs to the supplied live book path. */
  boolean isRollbackArtifactForBook(Path normalizedBookPath, Path normalizedRollbackArtifactPath);

  /** Stages one reversible rollback-artifact deletion. */
  StagedRollbackArtifactDeletion stageRollbackArtifactDeletion(Path normalizedRollbackArtifactPath);

  /** Appends one durable maintenance audit event into the selected initialized protected book. */
  MaintenanceDecision<MaintenanceCompletion> appendMaintenanceAudit(
      ProtectedBookAccess bookAccess,
      Instant recordedAt,
      ProtectedBookMaintenanceAuditKind auditKind);

  /** Appends one compensating maintenance audit event after external publish compensation. */
  MaintenanceDecision<MaintenanceCompletion> appendMaintenanceAuditCompensation(
      ProtectedBookAccess bookAccess,
      Instant recordedAt,
      ProtectedBookMaintenanceAuditCompensationKind auditKind);

  /** Outcome of attempting to acquire one exclusive maintenance lease. */
  sealed interface LeaseAcquisition permits HeldLease, LeaseBusy {
    /** Absolute normalized artifact path guarded by this acquisition result. */
    Path artifactPath();
  }

  /** Held exclusive maintenance lease for one protected-book artifact path. */
  non-sealed interface HeldLease extends LeaseAcquisition, AutoCloseable {
    @Override
    void close();
  }

  /** Busy outcome when one protected-book artifact could not be leased exclusively. */
  record LeaseBusy(Path artifactPath) implements LeaseAcquisition {
    public LeaseBusy {
      Objects.requireNonNull(artifactPath, "artifactPath");
    }
  }

  /** Verification result for one protected-book artifact. */
  sealed interface BookVerification permits VerifiedBook, VerificationFailure {
    /** Absolute normalized artifact path. */
    Path artifactPath();
  }

  /** Successful verification for one initialized protected book. */
  record VerifiedBook(Path artifactPath) implements BookVerification {
    public VerifiedBook {
      Objects.requireNonNull(artifactPath, "artifactPath");
    }
  }

  /** Failed verification for one protected-book artifact. */
  record VerificationFailure(Path artifactPath, ProtectedBookVerificationFailure failure)
      implements BookVerification {
    public VerificationFailure {
      Objects.requireNonNull(artifactPath, "artifactPath");
      Objects.requireNonNull(failure, "failure");
    }
  }

  /** Staged encrypted backup pair that is either published atomically or discarded. */
  interface StagedBackupPair extends AutoCloseable {
    /** Verifies that the staged backup file already opens as one initialized protected book. */
    MaintenanceDecision<BookVerification> verifyInitializedBackup();

    /** Publishes the staged backup pair to its final destinations. */
    void commit();

    /** Discards the staged backup pair without publishing it. */
    void rollback();

    @Override
    void close();
  }

  /** Reversible staged replacement prepared for one restore-style workflow. */
  interface StagedBookReplacement extends AutoCloseable {
    /** Staged replacement path that can be verified before the live target is replaced. */
    Path stagedBookPath();

    /** Commits the staged replacement and discards the previous-target rollback copy. */
    void commit();

    /** Discards the staged replacement and restores any prior target snapshot if one exists. */
    void rollback();

    @Override
    void close();
  }

  /** Reversible staged deletion for one rollback artifact. */
  interface StagedRollbackArtifactDeletion extends AutoCloseable {
    /** Commits the staged rollback-artifact deletion. */
    void commit();

    /** Restores the rollback artifact under its original path. */
    void rollback();

    @Override
    void close();
  }
}
