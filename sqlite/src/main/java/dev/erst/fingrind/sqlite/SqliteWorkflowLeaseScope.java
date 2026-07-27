package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Holds one immutable source-and-pair lease scope until a maintenance workflow completes.
 *
 * <p>The source references remain owned by this scope. Target admission references are only
 * placeholders that keep their canonical parent-directory reservations held until pair admission
 * has retained the exact target references that transfer into a prepared publication. Existing
 * source references also retain their global physical-object exclusions through this scope.
 */
final class SqliteWorkflowLeaseScope implements AutoCloseable {
  private final Path primarySourceArtifactPath;
  private final List<SqliteHeldLease> sourceLeases;
  private @Nullable SqliteTargetAdmissionLeases targetAdmissionLeases;
  private boolean closed;

  SqliteWorkflowLeaseScope(
      Path primarySourceArtifactPath,
      List<SqliteHeldLease> sourceLeases,
      SqliteHeldLease bookTargetAdmissionLease,
      SqliteHeldLease secretTargetAdmissionLease) {
    this.primarySourceArtifactPath =
        Objects.requireNonNull(primarySourceArtifactPath, "primarySourceArtifactPath");
    this.sourceLeases = List.copyOf(Objects.requireNonNull(sourceLeases, "sourceLeases"));
    if (this.sourceLeases.isEmpty()) {
      throw new IllegalArgumentException(
          "One FinGrind maintenance workflow scope requires at least one source lease.");
    }
    this.targetAdmissionLeases =
        new SqliteTargetAdmissionLeases(bookTargetAdmissionLease, secretTargetAdmissionLease);
  }

  Path sourceArtifactPath() {
    return primarySourceArtifactPath;
  }

  /**
   * Transfers the temporary exact target references to pair admission.
   *
   * <p>After this handoff, pair admission either transfers them into a prepared publication or
   * closes them before returning a non-prepared outcome. The workflow scope never reacquires the
   * pair: doing so would make a rekey contend with its own live-book source lease.
   */
  SqliteTargetAdmissionLeases takeTargetAdmissionLeases() {
    if (closed) {
      throw new IllegalStateException("The FinGrind maintenance workflow scope is already closed.");
    }
    SqliteTargetAdmissionLeases leases = targetAdmissionLeases;
    if (leases == null) {
      throw new IllegalStateException(
          "The FinGrind maintenance workflow scope has already transferred its target admissions.");
    }
    targetAdmissionLeases = null;
    return leases;
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    try {
      releaseTargetAdmissionLeases();
    } finally {
      closeSourceLeases();
    }
  }

  private void releaseTargetAdmissionLeases() {
    if (targetAdmissionLeases != null) {
      try {
        targetAdmissionLeases.close();
      } finally {
        targetAdmissionLeases = null;
      }
    }
  }

  private void closeSourceLeases() {
    @Nullable RuntimeException failure = null;
    for (int index = sourceLeases.size() - 1; index >= 0; index--) {
      try {
        sourceLeases.get(index).close();
      } catch (RuntimeException closeFailure) {
        if (failure == null) {
          failure = closeFailure;
        } else {
          failure.addSuppressed(closeFailure);
        }
      }
    }
    if (failure != null) {
      throw failure;
    }
  }
}
