package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Narrow SPI for protected-book maintenance verification and staged filesystem work. */
public interface ProtectedBookMaintenanceStore {
  /**
   * Resolves one optional live-book inspection artifact beneath an existing private canonical
   * parent directory.
   *
   * <p>This boundary exists only for inspection, where a missing live-book leaf is observable
   * state. Lifecycle mutations must use {@link #normalizeExistingSource(Path, String,
   * ProtectedBookMaintenanceArtifactRole)} for sources and {@link #normalizeFinalTarget(Path,
   * String, ProtectedBookMaintenanceArtifactRole)} for outputs.
   */
  Path normalizeOptionalInspectionArtifact(
      Path path, String argumentName, ProtectedBookMaintenanceArtifactRole artifactRole);

  /**
   * Resolves one selected final target beneath its private canonical parent directory.
   *
   * <p>The returned path is the only spelling that a maintenance workflow may use for leases,
   * stages, recovery evidence, and final publication. A permitted missing final-target parent may
   * be admitted here; this boundary never admits a lifecycle source.
   */
  Path normalizeFinalTarget(
      Path path, String argumentName, ProtectedBookMaintenanceArtifactRole artifactRole);

  /**
   * Resolves a maintenance source that must already be one regular non-symlink artifact.
   *
   * <p>Lifecycle mutations use this boundary before they can prepare caller-selected output
   * parents. Inspection paths use {@link #normalizeOptionalInspectionArtifact(Path, String,
   * ProtectedBookMaintenanceArtifactRole)} instead because a missing live book is an observable
   * inspection state rather than a malformed mutation source.
   */
  Path normalizeExistingSource(
      Path path, String argumentName, ProtectedBookMaintenanceArtifactRole artifactRole);

  /**
   * Acquires the complete immutable maintenance scope before a workflow reads its source or touches
   * either final target.
   *
   * <p>The scope owns every selected file-backed source artifact lease and predeclares the exact
   * book and secret target members. Its implementation must reject two source roles that resolve to
   * one physical object, acquire parent domains in one deterministic total order, check every
   * member for native activity both before and after acquisition, and reject any later attempt to
   * widen the scope with a sibling artifact.
   */
  WorkflowScopeAcquisition acquireWorkflowScope(
      WorkflowSourceMembers normalizedSourceMembers,
      Path normalizedBookTargetPath,
      ProtectedBookMaintenanceArtifactRole bookTargetArtifactRole,
      Path normalizedSecretTargetPath,
      ProtectedBookMaintenanceArtifactRole secretTargetArtifactRole);

  /**
   * Immutable exact source-member set for one protected-book maintenance workflow.
   *
   * <p>Each member has already crossed the existing-source normalization boundary. The set is
   * deliberately nonempty and rejects duplicate normalized path spellings: acquiring the same
   * source twice would make a busy projection ambiguous and would obscure the authority that the
   * workflow actually holds.
   */
  public record WorkflowSourceMembers(List<WorkflowSourceMember> members) {
    public WorkflowSourceMembers {
      members = List.copyOf(Objects.requireNonNull(members, "members"));
      if (members.isEmpty()) {
        throw new IllegalArgumentException(
            "One FinGrind maintenance workflow scope requires at least one source member.");
      }
      java.util.Set<String> normalizedPathSpellings = new java.util.HashSet<>();
      for (WorkflowSourceMember member : members) {
        WorkflowSourceMember checkedMember = Objects.requireNonNull(member, "source member");
        if (!normalizedPathSpellings.add(checkedMember.normalizedPathSpelling())) {
          throw new IllegalArgumentException(
              "One FinGrind maintenance workflow source member was declared more than once: "
                  + checkedMember.artifactPath()
                  + ".");
        }
      }
    }

    /** The primary workflow source, retained for scope-level diagnostics. */
    public WorkflowSourceMember primaryMember() {
      return members.getFirst();
    }
  }

  /** Exact role-tagged source artifact selected for one maintenance workflow. */
  public record WorkflowSourceMember(
      Path artifactPath, ProtectedBookMaintenanceArtifactRole artifactRole) {
    public WorkflowSourceMember {
      artifactPath =
          Objects.requireNonNull(artifactPath, "artifactPath").toAbsolutePath().normalize();
      ProtectedBookMaintenanceArtifactRole checkedArtifactRole =
          Objects.requireNonNull(artifactRole, "artifactRole");
      if (!isSourceArtifactRole(checkedArtifactRole)) {
        throw new IllegalArgumentException(
            "One FinGrind maintenance workflow source member requires a source artifact role.");
      }
    }

    private String normalizedPathSpelling() {
      return artifactPath.toString();
    }
  }

  /** Lists every artifact that blocks one clean live-book maintenance workflow. */
  List<Path> blockingArtifactsForBook(Path normalizedBookPath);

  /** Lists every artifact that blocks one clean backup-source restore workflow. */
  List<Path> blockingArtifactsForBackupSource(Path normalizedBackupFilePath);

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

  /** Outcome of acquiring one complete source-and-pair maintenance scope. */
  public sealed interface WorkflowScopeAcquisition permits HeldWorkflowScope, WorkflowScopeBusy {
    /** Absolute normalized artifact path that prevented acquisition. */
    Path artifactPath();
  }

  /**
   * Complete maintenance scope whose source leases remain held for the workflow lifetime.
   *
   * <p>On a prepared admission, the scope transfers only the exact target leases into the returned
   * {@link PreparedPairPublication}; it retains every source lease until closed.
   */
  public non-sealed interface HeldWorkflowScope extends WorkflowScopeAcquisition, AutoCloseable {
    /**
     * Reconciles durable pair evidence and, only when absent, transfers the exact target leases
     * into one prepared publication.
     */
    ProtectedBookPairPublicationAdmission admitPairPublication(
        RestoredBookTargetPolicy bookTargetPolicy,
        ProtectedBookPairPublicationRecoveryRequest request);

    @Override
    void close();
  }

  /** Busy outcome when one declared role-tagged workflow member could not be leased exclusively. */
  public record WorkflowScopeBusy(
      Path artifactPath, ProtectedBookMaintenanceArtifactRole artifactRole)
      implements WorkflowScopeAcquisition {
    public WorkflowScopeBusy {
      Objects.requireNonNull(artifactPath, "artifactPath");
      Objects.requireNonNull(artifactRole, "artifactRole");
    }
  }

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

  private static boolean isSourceArtifactRole(ProtectedBookMaintenanceArtifactRole artifactRole) {
    return switch (Objects.requireNonNull(artifactRole, "artifactRole")) {
      case LIVE_BOOK, LIVE_BOOK_KEY_SOURCE, BACKUP_SOURCE, BACKUP_KEY_SOURCE -> true;
      case BACKUP_TARGET, BACKUP_KEY_TARGET, RESTORED_TARGET, NEW_BOOK_KEY_TARGET -> false;
    };
  }
}
