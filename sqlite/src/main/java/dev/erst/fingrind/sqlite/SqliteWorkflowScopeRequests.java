package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowSourceMember;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowSourceMembers;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Builds and validates the immutable source-first member requests for one workflow scope. */
final class SqliteWorkflowScopeRequests {
  private SqliteWorkflowScopeRequests() {}

  static List<Request> create(
      WorkflowSourceMembers sourceMembers,
      Path bookTarget,
      ProtectedBookMaintenanceArtifactRole bookTargetRole,
      Path secretTarget,
      ProtectedBookMaintenanceArtifactRole secretTargetRole)
      throws IOException {
    List<Request> requests = new ArrayList<>();
    for (WorkflowSourceMember sourceMember : sourceMembers.members()) {
      requests.add(
          createRequest(
              sourceMember.artifactPath(),
              SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT,
              Member.SOURCE,
              sourceMember.artifactRole()));
    }
    requests.add(
        createRequest(
            bookTarget,
            SqliteMaintenanceLeaseIntent.MANAGED_TARGET,
            Member.BOOK_TARGET,
            bookTargetRole));
    requests.add(
        createRequest(
            secretTarget,
            SqliteMaintenanceLeaseIntent.MANAGED_TARGET,
            Member.SECRET_TARGET,
            secretTargetRole));
    requests.sort(
        Comparator.comparing(
                (Request request) ->
                    SqliteProtectedBookPathIdentity.normalizedSpelling(request.directoryDomain()))
            .thenComparing(
                request ->
                    SqliteProtectedBookPathIdentity.normalizedSpelling(request.artifactPath()))
            .thenComparing(Request::member)
            .thenComparing(Request::artifactRole));
    return List.copyOf(requests);
  }

  static List<Request> forMember(List<Request> requests, Member member) {
    return requests.stream().filter(request -> request.member() == member).toList();
  }

  static List<Request> exceptMember(List<Request> requests, Member member) {
    return requests.stream().filter(request -> request.member() != member).toList();
  }

  /** Narrows declared workflow members to the two final-target positions. */
  static List<TargetRequest> targetRequests(List<Request> requests) {
    return requests.stream()
        .filter(request -> request.member() != Member.SOURCE)
        .map(TargetRequest::from)
        .toList();
  }

  static List<Path> artifactsForDirectory(List<Request> requests, Path directoryDomain) {
    return requests.stream()
        .filter(
            request ->
                SqliteProtectedBookPathIdentity.sameNormalizedSpelling(
                    request.directoryDomain(), directoryDomain))
        .map(Request::artifactPath)
        .toList();
  }

  static void requireDistinctPhysicalSources(WorkflowSourceMembers sourceMembers)
      throws IOException {
    Set<String> identities = new HashSet<>();
    for (WorkflowSourceMember sourceMember : sourceMembers.members()) {
      String identity =
          SqliteObjectCoordinationArtifacts.physicalIdentity(sourceMember.artifactPath());
      if (!identities.add(identity)) {
        throw duplicatedSource(sourceMember);
      }
    }
  }

  static void requireSourcesStillMatchLockedIdentities(
      WorkflowSourceMembers sourceMembers, java.util.Map<String, SqliteOwnedHeldLease> sourceLeases)
      throws IOException {
    for (WorkflowSourceMember sourceMember : sourceMembers.members()) {
      String spelling =
          SqliteProtectedBookPathIdentity.normalizedSpelling(sourceMember.artifactPath());
      String lockedIdentity =
          Objects.requireNonNull(
              Objects.requireNonNull(sourceLeases.get(spelling), "source lease")
                  .borrowedLease()
                  .lockedPhysicalObjectIdentity(),
              "existing source leases must retain their exact physical object identity");
      String currentIdentity =
          SqliteObjectCoordinationArtifacts.physicalIdentity(sourceMember.artifactPath());
      if (!lockedIdentity.equals(currentIdentity)) {
        throw new SqliteCallerPathContractException(
            sourceMember.artifactPath(),
            SqliteCallerPathFailure.SOURCE_ARTIFACT_IDENTITY_CHANGED,
            "The selected protected-book maintenance source no longer resolves to the physical artifact whose maintenance exclusion was acquired: "
                + sourceMember.artifactPath()
                + ".");
      }
    }
  }

  private static Request createRequest(
      Path artifactPath,
      SqliteMaintenanceLeaseIntent leaseIntent,
      Member member,
      ProtectedBookMaintenanceArtifactRole artifactRole)
      throws IOException {
    Path checkedArtifactPath = Objects.requireNonNull(artifactPath, "artifactPath");
    SqliteMaintenanceLeaseAuthority.validateArtifactForLeaseIntent(
        checkedArtifactPath, leaseIntent);
    return new Request(
        checkedArtifactPath,
        SqliteMaintenanceLeaseAuthority.canonicalDirectoryDomain(checkedArtifactPath),
        Objects.requireNonNull(leaseIntent, "leaseIntent"),
        Objects.requireNonNull(member, "member"),
        Objects.requireNonNull(artifactRole, "artifactRole"));
  }

  private static SqliteCallerPathContractException duplicatedSource(WorkflowSourceMember source) {
    return new SqliteCallerPathContractException(
        source.artifactPath(),
        SqliteCallerPathFailure.SOURCE_ARTIFACT_IDENTITY_DUPLICATED,
        "The selected protected-book maintenance source resolves to the same physical artifact as an earlier source role: "
            + source.artifactPath()
            + ".");
  }

  /** Declared workflow positions distinguish sources from final target admissions. */
  enum Member {
    SOURCE,
    BOOK_TARGET,
    SECRET_TARGET
  }

  /** One normalized artifact and its exact lease intent within a workflow scope. */
  record Request(
      Path artifactPath,
      Path directoryDomain,
      SqliteMaintenanceLeaseIntent leaseIntent,
      Member member,
      ProtectedBookMaintenanceArtifactRole artifactRole) {}

  /** A request whose role is statically narrowed to one of the two final pair members. */
  record TargetRequest(Request request, Target target) {
    static TargetRequest from(Request request) {
      Request checkedRequest = Objects.requireNonNull(request, "request");
      return switch (checkedRequest.member()) {
        case BOOK_TARGET -> new TargetRequest(checkedRequest, Target.BOOK);
        case SECRET_TARGET -> new TargetRequest(checkedRequest, Target.SECRET);
        case SOURCE ->
            throw new IllegalArgumentException(
                "Source workflow requests cannot enter final-target acquisition.");
      };
    }
  }

  /** The two final pair roles after source-first acquisition has completed. */
  enum Target {
    BOOK,
    SECRET
  }
}
