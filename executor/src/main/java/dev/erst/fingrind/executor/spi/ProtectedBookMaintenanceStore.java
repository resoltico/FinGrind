package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.executor.maintenance.MaintenanceCompletion;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
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

  /**
   * Reserves all absent final targets needed by one pair publication before its source is
   * inspected.
   */
  PreparedPairPublication preparePairPublication(
      Path normalizedSecretTargetPath,
      Path normalizedBookTargetPath,
      RestoredBookTargetPolicy bookTargetPolicy,
      ProtectedBookMaintenanceArtifactRole bookArtifactRole,
      ProtectedBookMaintenanceArtifactRole secretArtifactRole);

  /** Lists every artifact that blocks one clean live-book maintenance workflow. */
  List<Path> blockingArtifactsForBook(Path normalizedBookPath);

  /** Lists every artifact that blocks one clean backup-source restore workflow. */
  List<Path> blockingArtifactsForBackupSource(Path normalizedBackupFilePath);

  /** Reports whether a requested external backup artifact and its companion key already exist. */
  BackupArtifactPairState backupArtifactPairState(
      Path normalizedBackupArtifactPath, Path normalizedBackupKeyFilePath);

  /** Acquires one exclusive maintenance lease for one existing protected-book artifact path. */
  LeaseAcquisition acquireExistingArtifactLease(
      Path normalizedArtifactPath, ProtectedBookMaintenanceArtifactRole artifactRole);

  /** Acquires one exclusive maintenance lease for one managed protected-book artifact path. */
  LeaseAcquisition acquireManagedArtifactLease(
      Path normalizedArtifactPath, ProtectedBookMaintenanceArtifactRole artifactRole);

  /** Verifies that the supplied protected book opens as one initialized FinGrind book. */
  MaintenanceDecision<BookVerification> verifyInitializedBook(
      ProtectedBookAccess bookAccess, ProtectedBookMaintenanceArtifactRole artifactRole);

  /**
   * Stages one encrypted backup pair from one already verified protected book without publishing it
   * to the final destination yet.
   */
  MaintenanceDecision<StagedBackupPair> stageBackupPair(
      VerifiedBook sourceBook, PreparedPairPublication preparedPairPublication);

  /**
   * Stages one restored live-book pair by re-encrypting the verified backup under one new
   * destination key file before publication.
   */
  MaintenanceDecision<StagedRestoredBookPair> stageRestoredBookPair(
      VerifiedBook sourceBook, PreparedPairPublication preparedPairPublication);

  /** Publication authority for one restored-book target path. */
  enum RestoredBookTargetPolicy {
    /** Publish only when the final book path remains absent through the final atomic operation. */
    REQUIRE_ABSENT,

    /** Atomically replace the book path that the caller explicitly selected for replacement. */
    REPLACE_SELECTED
  }

  /** Existing-state classification for one externally published backup artifact pair. */
  enum BackupArtifactPairState {
    /** Neither final artifact exists, so a new no-clobber publication may be prepared. */
    ABSENT,
    /** Only the public backup artifact exists; a new publication must refuse. */
    ARTIFACT_ONLY,
    /** Only the companion backup key exists; a new publication must refuse. */
    KEY_ONLY,
    /** Both final artifacts exist and may be considered for exact-tuple acknowledgement resume. */
    COMPLETE
  }

  /** Holds the reservations that make one staged pair safe to publish after source verification. */
  interface PreparedPairPublication extends AutoCloseable {
    /** Absolute normalized final protected-book artifact path. */
    Path bookTargetPath();

    /** Absolute normalized final generated-secret path. */
    Path secretTargetPath();

    /** Final-book publication authority selected by the caller. */
    RestoredBookTargetPolicy bookTargetPolicy();

    @Override
    void close();
  }

  /**
   * Verifies that the supplied replicated book path opens with the same secret material as the
   * already verified source book.
   */
  MaintenanceDecision<BookVerification> verifyInitializedReplica(
      Path normalizedReplicaBookPath, VerifiedBook sourceBook);

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
      VerifiedBook verifiedBook, Instant recordedAt, ProtectedBookMaintenanceAuditKind auditKind);

  /** Appends one compensating maintenance audit event after external publish compensation. */
  MaintenanceDecision<MaintenanceCompletion> appendMaintenanceAuditCompensation(
      VerifiedBook verifiedBook,
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

  /** Successful verification handle for one initialized protected book. */
  non-sealed interface VerifiedBook extends BookVerification, AutoCloseable {
    @Override
    void close();
  }

  /** Failed verification for one protected-book artifact. */
  record VerificationFailure(Path artifactPath, ProtectedBookVerificationFailure failure)
      implements BookVerification {
    public VerificationFailure {
      Objects.requireNonNull(artifactPath, "artifactPath");
      Objects.requireNonNull(failure, "failure");
    }
  }
}
