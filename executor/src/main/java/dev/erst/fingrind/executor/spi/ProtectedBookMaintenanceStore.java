package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Narrow SPI for protected-book maintenance verification and closed-copy filesystem work. */
public interface ProtectedBookMaintenanceStore {
  /** Returns one normalized absolute path for the supplied maintenance argument. */
  Path normalize(Path path, String argumentName);

  /** Lists every artifact that blocks one clean live-book maintenance workflow. */
  List<Path> blockingArtifactsForBook(Path normalizedBookPath);

  /** Lists every artifact that blocks one clean backup-source restore workflow. */
  List<Path> blockingArtifactsForBackupSource(Path normalizedBackupFilePath);

  /** Acquires one exclusive maintenance lease for the selected protected-book artifact path. */
  LeaseAcquisition acquireExclusiveLease(Path normalizedArtifactPath);

  /** Verifies that the supplied protected book opens as one initialized FinGrind book. */
  ContractDecision<BookVerification> verifyInitializedBook(BookAccess bookAccess);

  /** Writes one backup key file and one encrypted backup copy from the verified source book. */
  ContractDecision<Path> publishBackupPair(
      BookAccess sourceAccess, Path normalizedBackupFilePath, Path normalizedBackupBookKeyFilePath);

  /**
   * Prepares one reversible replacement of the selected live book path with one verified source.
   */
  PreparedBookReplacement prepareReplacement(
      Path normalizedSourceBookPath, Path normalizedTargetBookPath);

  /** Lists every sibling rollback artifact that belongs to the supplied live book path. */
  List<Path> staleRollbackArtifacts(Path normalizedBookPath);

  /** Returns whether the selected rollback artifact belongs to the supplied live book path. */
  boolean isRollbackArtifactForBook(Path normalizedBookPath, Path normalizedRollbackArtifactPath);

  /** Deletes one selected rollback artifact. */
  void deleteRollbackArtifact(Path normalizedRollbackArtifactPath);

  /** Appends one durable protected-book maintenance event beside the selected live book path. */
  void recordMaintenanceEvent(ProtectedBookMaintenanceEvent maintenanceEvent);

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
  record VerificationFailure(Path artifactPath, ProtectedBookMaintenanceVerificationFailure failure)
      implements BookVerification {
    public VerificationFailure {
      Objects.requireNonNull(artifactPath, "artifactPath");
      Objects.requireNonNull(failure, "failure");
    }
  }

  /** Reversible filesystem replacement prepared for one restore-style workflow. */
  interface PreparedBookReplacement extends AutoCloseable {
    /** Final live target path of this prepared replacement. */
    Path targetBookPath();

    /** Commits the prepared replacement and discards the previous-target rollback copy. */
    void commit();

    /** Restores the previous target contents and discards the staged replacement. */
    void rollback();

    @Override
    void close();
  }
}
