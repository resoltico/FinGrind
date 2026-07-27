package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowSourceMembers;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Acquires source-first maintenance workflow scopes without granting target authority early. */
final class SqliteMaintenanceWorkflowScopeAcquirer {
  private SqliteMaintenanceWorkflowScopeAcquirer() {}

  static SqliteWorkflowScopeAcquisition acquire(
      WorkflowSourceMembers normalizedSourceMembers,
      Path normalizedBookTargetPath,
      ProtectedBookMaintenanceArtifactRole bookTargetArtifactRole,
      Path normalizedSecretTargetPath,
      ProtectedBookMaintenanceArtifactRole secretTargetArtifactRole)
      throws IOException {
    return acquire(
        normalizedSourceMembers,
        normalizedBookTargetPath,
        bookTargetArtifactRole,
        normalizedSecretTargetPath,
        secretTargetArtifactRole,
        SqliteMaintenanceWorkflowScopeAcquirer::acquireRequest);
  }

  /**
   * Acquires one workflow scope through an exact-request acquisition boundary.
   *
   * <p>The boundary preserves the production ordering while making lease races and release-failure
   * semantics deterministic to prove. An implementation must return an outcome only for the
   * supplied request; returning an unrelated busy artifact is rejected by this class.
   */
  static SqliteWorkflowScopeAcquisition acquire(
      WorkflowSourceMembers normalizedSourceMembers,
      Path normalizedBookTargetPath,
      ProtectedBookMaintenanceArtifactRole bookTargetArtifactRole,
      Path normalizedSecretTargetPath,
      ProtectedBookMaintenanceArtifactRole secretTargetArtifactRole,
      LeaseAcquirer leaseAcquirer)
      throws IOException {
    WorkflowSourceMembers sourceMembers =
        Objects.requireNonNull(normalizedSourceMembers, "normalizedSourceMembers");
    Path bookTarget = Objects.requireNonNull(normalizedBookTargetPath, "normalizedBookTargetPath");
    ProtectedBookMaintenanceArtifactRole bookTargetRole =
        Objects.requireNonNull(bookTargetArtifactRole, "bookTargetArtifactRole");
    Path secretTarget =
        Objects.requireNonNull(normalizedSecretTargetPath, "normalizedSecretTargetPath");
    ProtectedBookMaintenanceArtifactRole secretTargetRole =
        Objects.requireNonNull(secretTargetArtifactRole, "secretTargetArtifactRole");
    LeaseAcquirer checkedLeaseAcquirer = Objects.requireNonNull(leaseAcquirer, "leaseAcquirer");
    List<SqliteWorkflowScopeRequests.Request> requests =
        SqliteWorkflowScopeRequests.create(
            sourceMembers, bookTarget, bookTargetRole, secretTarget, secretTargetRole);
    List<SqliteWorkflowScopeRequests.Request> sourceRequests =
        SqliteWorkflowScopeRequests.forMember(requests, SqliteWorkflowScopeRequests.Member.SOURCE);
    List<SqliteWorkflowScopeRequests.TargetRequest> targetRequests =
        SqliteWorkflowScopeRequests.targetRequests(requests);
    SqliteWorkflowScopeRequests.requireDistinctPhysicalSources(sourceMembers);
    SqliteWorkflowScopeRequests.@org.jspecify.annotations.Nullable Request preflightBusy =
        firstBusy(requests);
    if (preflightBusy != null) {
      return busy(preflightBusy);
    }

    List<SqliteOwnedHeldLease> sourceLeases = new ArrayList<>();
    Map<String, SqliteOwnedHeldLease> sourceLeasesBySpelling = new ConcurrentHashMap<>();
    @org.jspecify.annotations.Nullable SqliteOwnedHeldLease bookTargetLease = null;
    @org.jspecify.annotations.Nullable SqliteOwnedHeldLease secretTargetLease = null;
    try {
      SqliteWorkflowScopeAcquisition sourceResult =
          acquireSources(
              requests, sourceRequests, sourceLeases, sourceLeasesBySpelling, checkedLeaseAcquirer);
      if (sourceResult != null) {
        return sourceResult;
      }
      SqliteWorkflowScopeRequests.requireSourcesStillMatchLockedIdentities(
          sourceMembers, sourceLeasesBySpelling);
      SqliteProtectedBookPairPublicationTargets.requirePrepublicationPairTargetAdmission(
          bookTarget, secretTarget, bookTargetRole, secretTargetRole);
      TargetLeasePair targetLeases = acquireTargets(requests, targetRequests, checkedLeaseAcquirer);
      if (targetLeases.busyMember() != null) {
        closeWorkflowLeases(secretTargetLease, bookTargetLease, sourceLeases);
        return busy(targetLeases.busyMember());
      }
      bookTargetLease = targetLeases.bookTargetLease();
      secretTargetLease = targetLeases.secretTargetLease();
      return new SqliteWorkflowScopeHeld(
          new SqliteWorkflowLeaseScope(
              sourceMembers.primaryMember().artifactPath(),
              transferSources(sourceLeases),
              Objects.requireNonNull(bookTargetLease, "bookTargetLease").transfer(),
              Objects.requireNonNull(secretTargetLease, "secretTargetLease").transfer()));
    } catch (IOException | RuntimeException | Error failure) {
      closeWorkflowLeasesPreservingFailure(
          secretTargetLease, bookTargetLease, sourceLeases, failure);
      throw failure;
    }
  }

  private static @org.jspecify.annotations.Nullable SqliteWorkflowScopeAcquisition acquireSources(
      List<SqliteWorkflowScopeRequests.Request> requests,
      List<SqliteWorkflowScopeRequests.Request> sourceRequests,
      List<SqliteOwnedHeldLease> sourceLeases,
      Map<String, SqliteOwnedHeldLease> sourceLeasesBySpelling,
      LeaseAcquirer leaseAcquirer) {
    for (SqliteWorkflowScopeRequests.Request request : sourceRequests) {
      SqliteProtectedBookLeaseAcquisition acquisition = leaseAcquirer.acquire(requests, request);
      if (acquisition instanceof SqliteLeaseBusy busy) {
        closeSourceLeases(sourceLeases);
        return new SqliteWorkflowScopeBusy(busy.artifactPath(), request.artifactRole());
      }
      sourceLeases.add(SqliteOwnedHeldLease.acquire(acquisition));
      sourceLeasesBySpelling.put(
          SqliteProtectedBookPathIdentity.normalizedSpelling(request.artifactPath()),
          sourceLeases.getLast());
    }
    return null;
  }

  private static TargetLeasePair acquireTargets(
      List<SqliteWorkflowScopeRequests.Request> requests,
      List<SqliteWorkflowScopeRequests.TargetRequest> targetRequests,
      LeaseAcquirer leaseAcquirer) {
    @org.jspecify.annotations.Nullable SqliteOwnedHeldLease bookTargetLease = null;
    @org.jspecify.annotations.Nullable SqliteOwnedHeldLease secretTargetLease = null;
    try {
      for (SqliteWorkflowScopeRequests.TargetRequest targetRequest : targetRequests) {
        SqliteWorkflowScopeRequests.Request request = targetRequest.request();
        SqliteProtectedBookLeaseAcquisition acquisition = leaseAcquirer.acquire(requests, request);
        if (acquisition instanceof SqliteLeaseBusy busy) {
          closePairLeases(secretTargetLease, bookTargetLease);
          return new TargetLeasePair(null, null, requestForBusy(busy, request));
        }
        switch (targetRequest.target()) {
          case BOOK -> bookTargetLease = SqliteOwnedHeldLease.acquire(acquisition);
          case SECRET -> secretTargetLease = SqliteOwnedHeldLease.acquire(acquisition);
        }
      }
      TargetLeasePair acquired =
          new TargetLeasePair(
              Objects.requireNonNull(bookTargetLease, "bookTargetLease"),
              Objects.requireNonNull(secretTargetLease, "secretTargetLease"),
              null);
      bookTargetLease = null;
      secretTargetLease = null;
      return acquired;
    } finally {
      closePairLeases(secretTargetLease, bookTargetLease);
    }
  }

  private static SqliteProtectedBookLeaseAcquisition acquireRequest(
      List<SqliteWorkflowScopeRequests.Request> requests,
      SqliteWorkflowScopeRequests.Request request) {
    return SqliteBookMaintenanceLease.acquireWithAdmittedScope(
        request.artifactPath(),
        request.leaseIntent(),
        SqliteWorkflowScopeRequests.artifactsForDirectory(requests, request.directoryDomain()));
  }

  /** Acquires exactly one declared workflow member under its immutable admission scope. */
  @FunctionalInterface
  interface LeaseAcquirer {
    SqliteProtectedBookLeaseAcquisition acquire(
        List<SqliteWorkflowScopeRequests.Request> requests,
        SqliteWorkflowScopeRequests.Request request);
  }

  private static SqliteWorkflowScopeBusy busy(SqliteWorkflowScopeRequests.Request request) {
    return new SqliteWorkflowScopeBusy(request.artifactPath(), request.artifactRole());
  }

  private static SqliteWorkflowScopeRequests.Request requestForBusy(
      SqliteLeaseBusy busy, SqliteWorkflowScopeRequests.Request request) {
    if (SqliteProtectedBookPathIdentity.sameNormalizedSpelling(
        busy.artifactPath(), request.artifactPath())) {
      return request;
    }
    throw new IllegalStateException(
        "A workflow lease acquisition reported an unadmitted artifact.");
  }

  private static SqliteWorkflowScopeRequests.@org.jspecify.annotations.Nullable Request firstBusy(
      List<SqliteWorkflowScopeRequests.Request> requests) {
    for (SqliteWorkflowScopeRequests.Request request : requests) {
      if (SqliteMaintenanceLeaseAuthority.hasBlockingActivity(request.artifactPath())) {
        return request;
      }
    }
    return null;
  }

  private static void closeWorkflowLeases(
      @org.jspecify.annotations.Nullable SqliteOwnedHeldLease secretTargetLease,
      @org.jspecify.annotations.Nullable SqliteOwnedHeldLease bookTargetLease,
      List<SqliteOwnedHeldLease> sourceLeases) {
    try {
      close(secretTargetLease);
    } finally {
      try {
        close(bookTargetLease);
      } finally {
        closeSourceLeases(sourceLeases);
      }
    }
  }

  private static void closeWorkflowLeasesPreservingFailure(
      @org.jspecify.annotations.Nullable SqliteOwnedHeldLease secretTargetLease,
      @org.jspecify.annotations.Nullable SqliteOwnedHeldLease bookTargetLease,
      List<SqliteOwnedHeldLease> sourceLeases,
      Throwable failure) {
    closePreservingFailure(secretTargetLease, failure);
    closePreservingFailure(bookTargetLease, failure);
    for (int index = sourceLeases.size() - 1; index >= 0; index--) {
      closePreservingFailure(sourceLeases.get(index), failure);
    }
  }

  private static void closeSourceLeases(List<SqliteOwnedHeldLease> sourceLeases) {
    for (int index = sourceLeases.size() - 1; index >= 0; index--) {
      sourceLeases.get(index).release();
    }
  }

  private static void close(@org.jspecify.annotations.Nullable SqliteOwnedHeldLease lease) {
    if (lease != null) {
      lease.release();
    }
  }

  private static void closePairLeases(
      @org.jspecify.annotations.Nullable SqliteOwnedHeldLease secretLease,
      @org.jspecify.annotations.Nullable SqliteOwnedHeldLease bookLease) {
    try {
      close(secretLease);
    } finally {
      close(bookLease);
    }
  }

  private static void closePreservingFailure(
      @org.jspecify.annotations.Nullable SqliteOwnedHeldLease lease, Throwable failure) {
    if (lease != null) {
      try {
        lease.release();
      } catch (RuntimeException | Error closeFailure) {
        failure.addSuppressed(closeFailure);
      }
    }
  }

  private record TargetLeasePair(
      @org.jspecify.annotations.Nullable SqliteOwnedHeldLease bookTargetLease,
      @org.jspecify.annotations.Nullable SqliteOwnedHeldLease secretTargetLease,
      SqliteWorkflowScopeRequests.@org.jspecify.annotations.Nullable Request busyMember) {}

  private static List<SqliteHeldLease> transferSources(List<SqliteOwnedHeldLease> sourceLeases) {
    return sourceLeases.stream().map(SqliteOwnedHeldLease::transfer).toList();
  }
}
