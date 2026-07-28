package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Acquires a book-and-secret target pair in deterministic directory order. */
final class SqliteManagedTargetLeaseCoordinator {
  private SqliteManagedTargetLeaseCoordinator() {}

  static SqliteManagedTargetLeasePair acquire(Path bookTargetPath, Path secretTargetPath) {
    try {
      List<Request> requests = requests(bookTargetPath, secretTargetPath);
      return acquire(
          requests,
          request ->
              SqliteBookMaintenanceLease.acquireWithAdmittedScope(
                  request.path(),
                  SqliteMaintenanceLeaseIntent.MANAGED_TARGET,
                  targetsInSameDirectory(requests, request)));
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to prepare one FinGrind protected-book maintenance directory domain.", exception);
    }
  }

  static SqliteManagedTargetLeasePair acquire(
      Path bookTargetPath,
      Path secretTargetPath,
      SqliteManagedTargetLeaseAcquirer targetLeaseAcquirer) {
    SqliteManagedTargetLeaseAcquirer checkedTargetLeaseAcquirer =
        Objects.requireNonNull(targetLeaseAcquirer, "targetLeaseAcquirer");
    try {
      return acquire(
          requests(bookTargetPath, secretTargetPath),
          request -> checkedTargetLeaseAcquirer.acquire(request.path()));
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to prepare one FinGrind protected-book maintenance directory domain.", exception);
    }
  }

  private static SqliteManagedTargetLeasePair acquire(
      List<Request> requests, RequestLeaseAcquirer targetLeaseAcquirer) {
    @org.jspecify.annotations.Nullable Path preflightBusyTarget = firstBusy(requests);
    if (preflightBusyTarget != null) {
      return new SqliteManagedTargetLeasesBusy(preflightBusyTarget);
    }
    @org.jspecify.annotations.Nullable SqliteOwnedHeldLease bookLease = null;
    @org.jspecify.annotations.Nullable SqliteOwnedHeldLease secretLease = null;
    try {
      for (Request request : requests) {
        SqliteProtectedBookLeaseAcquisition acquisition =
            targetLeaseAcquirer.acquire(request);
        if (acquisition instanceof SqliteLeaseBusy busy) {
          releasePair(secretLease, bookLease);
          return new SqliteManagedTargetLeasesBusy(busy.artifactPath());
        }
        if (request.bookTarget()) {
          bookLease = SqliteOwnedHeldLease.acquire(acquisition);
        } else {
          secretLease = SqliteOwnedHeldLease.acquire(acquisition);
        }
      }
      @org.jspecify.annotations.Nullable Path postAcquisitionBusyTarget = firstBusy(requests);
      if (postAcquisitionBusyTarget != null) {
        releasePair(secretLease, bookLease);
        return new SqliteManagedTargetLeasesBusy(postAcquisitionBusyTarget);
      }
      SqliteManagedTargetLeasesHeld acquired =
          new SqliteManagedTargetLeasesHeld(
              Objects.requireNonNull(bookLease, "bookLease").transfer(),
              Objects.requireNonNull(secretLease, "secretLease").transfer());
      bookLease = null;
      secretLease = null;
      return acquired;
    } finally {
      releasePair(secretLease, bookLease);
    }
  }

  private static List<Request> requests(Path bookTargetPath, Path secretTargetPath)
      throws IOException {
    Path checkedBookTarget = Objects.requireNonNull(bookTargetPath, "bookTargetPath");
    Path checkedSecretTarget = Objects.requireNonNull(secretTargetPath, "secretTargetPath");
    List<Request> requests =
        new ArrayList<>(
            List.of(
                new Request(
                    checkedBookTarget,
                    SqliteMaintenanceLeaseAuthority.canonicalManagedTargetDirectory(
                        checkedBookTarget),
                    true),
                new Request(
                    checkedSecretTarget,
                    SqliteMaintenanceLeaseAuthority.canonicalManagedTargetDirectory(
                        checkedSecretTarget),
                    false)));
    requests.sort(
        Comparator.comparing(
                (Request request) ->
                    SqliteProtectedBookPathIdentity.normalizedSpelling(request.directoryDomain()))
            .thenComparing(
                request -> SqliteProtectedBookPathIdentity.normalizedSpelling(request.path())));
    return List.copyOf(requests);
  }

  private static List<Path> targetsInSameDirectory(List<Request> requests, Request request) {
    Request checkedRequest = Objects.requireNonNull(request, "request");
    return requests.stream()
        .filter(
            candidate ->
                SqliteProtectedBookPathIdentity.sameNormalizedSpelling(
                    candidate.directoryDomain(), checkedRequest.directoryDomain()))
        .map(Request::path)
        .toList();
  }

  private static @org.jspecify.annotations.Nullable Path firstBusy(List<Request> requests) {
    for (Request request : requests) {
      if (SqliteMaintenanceLeaseAuthority.hasBlockingActivity(request.path())) {
        return request.path();
      }
    }
    return null;
  }

  private static void releasePair(
      @org.jspecify.annotations.Nullable SqliteOwnedHeldLease secretLease,
      @org.jspecify.annotations.Nullable SqliteOwnedHeldLease bookLease) {
    if (secretLease != null) {
      secretLease.release();
    }
    if (bookLease != null) {
      bookLease.release();
    }
  }

  private record Request(Path path, Path directoryDomain, boolean bookTarget) {
    private Request {
      Objects.requireNonNull(path, "path");
      Objects.requireNonNull(directoryDomain, "directoryDomain");
    }
  }

  /** Acquires one lease using an already validated request from this exact acquisition set. */
  @FunctionalInterface
  private interface RequestLeaseAcquirer {
    SqliteProtectedBookLeaseAcquisition acquire(Request request);
  }
}
