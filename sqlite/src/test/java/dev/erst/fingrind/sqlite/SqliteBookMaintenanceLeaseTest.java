package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.ContractFailureException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Behavioral tests for two-layer protected-book maintenance leases. */
class SqliteBookMaintenanceLeaseTest extends SqliteNativeBridgeTestSupport {
  @Test
  void managedTargetRequiresItsAlreadyExistingPrivateParentAndNeverCreatesIt() {
    Path target = tempDirectory.resolve("managed").resolve("book.sqlite");
    Path parent = target.getParent();
    if (parent == null) {
      throw new AssertionError("Fixture target requires one parent.");
    }

    SqliteCallerPathContractException failure =
        assertThrows(
            SqliteCallerPathContractException.class,
            () ->
                SqliteBookMaintenanceLease.acquire(
                    target, SqliteMaintenanceLeaseIntent.MANAGED_TARGET));

    assertEquals(SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY, failure.pathFailure());
    assertFalse(Files.exists(parent));
  }

  @Test
  void sameThreadReferencesForDistinctSiblingTargetsShareOneDirectoryLeaseUntilBothClose()
      throws Exception {
    Path first = managedTarget("shared-domain/first.sqlite");
    Path second = managedTarget("shared-domain/second.key");
    Path parent = first.getParent();
    if (parent == null) {
      throw new AssertionError("Fixture target requires one parent.");
    }

    Path leasePath = SqliteMaintenanceLeaseArtifacts.controlFilePath(parent.toRealPath());
    try (SqliteHeldLease firstLease =
            assertInstanceOf(
                SqliteHeldLease.class,
                SqliteBookMaintenanceLease.acquire(
                    first, SqliteMaintenanceLeaseIntent.MANAGED_TARGET));
        SqliteHeldLease secondLease =
            assertInstanceOf(
                SqliteHeldLease.class,
                SqliteBookMaintenanceLease.acquire(
                    second, SqliteMaintenanceLeaseIntent.MANAGED_TARGET))) {
      firstLease.close();
      assertEquals(second, secondLease.artifactPath());
      assertTrue(Files.exists(leasePath));
    }
    assertTrue(Files.exists(leasePath));
    assertFalse(SqliteMaintenanceLeaseArtifacts.hasBlockingArtifact(parent.toRealPath()));
  }

  @Test
  void anotherThreadCannotAcquireASecondSiblingWhileThisThreadOwnsTheDirectoryDomain()
      throws Exception {
    Path first = managedTarget("thread-domain/first.sqlite");
    Path second = managedTarget("thread-domain/second.key");
    AtomicReference<SqliteProtectedBookLeaseAcquisition> concurrent = new AtomicReference<>();

    try (SqliteHeldLease ignored =
        assertInstanceOf(
            SqliteHeldLease.class,
            SqliteBookMaintenanceLease.acquire(
                first, SqliteMaintenanceLeaseIntent.MANAGED_TARGET))) {
      Thread thread =
          new Thread(
              () ->
                  concurrent.set(
                      SqliteBookMaintenanceLease.acquire(
                          second, SqliteMaintenanceLeaseIntent.MANAGED_TARGET)));
      thread.start();
      thread.join();
    }

    SqliteLeaseBusy busy = assertInstanceOf(SqliteLeaseBusy.class, concurrent.get());
    assertEquals(second.toAbsolutePath().normalize(), busy.artifactPath());
  }

  @Test
  void deterministicPairAcquisitionDeduplicatesOneSharedParentDomain() throws Exception {
    Path bookTarget = managedTarget("pair-domain/book.sqlite");
    Path secretTarget = managedTarget("pair-domain/book.key");
    Path parent = bookTarget.getParent();
    if (parent == null) {
      throw new AssertionError("Fixture target requires one parent.");
    }

    SqliteManagedTargetLeasesHeld held =
        assertInstanceOf(
            SqliteManagedTargetLeasesHeld.class,
            SqliteBookMaintenanceLease.acquireManagedTargetPair(bookTarget, secretTarget));
    Path leasePath = SqliteMaintenanceLeaseArtifacts.controlFilePath(parent.toRealPath());
    assertTrue(Files.exists(leasePath));

    held.bookTargetLease().close();
    assertTrue(Files.exists(leasePath));
    held.secretTargetLease().close();
    assertTrue(Files.exists(leasePath));
    assertFalse(SqliteMaintenanceLeaseArtifacts.hasBlockingArtifact(parent.toRealPath()));
  }

  @Test
  void pairCoordinatorReportsTheLaterExactBusyTargetAndReleasesEarlierOwnership() throws Exception {
    Path secretTarget = managedTarget("coordinator-busy/a-secret/book.key");
    Path bookTarget = managedTarget("coordinator-busy/z-book/book.sqlite");
    List<Path> attemptedTargets = new ArrayList<>();
    AtomicInteger releasedLeases = new AtomicInteger();

    SqliteManagedTargetLeasesBusy result =
        assertInstanceOf(
            SqliteManagedTargetLeasesBusy.class,
            SqliteBookMaintenanceLease.acquireManagedTargetPair(
                bookTarget,
                secretTarget,
                target -> {
                  attemptedTargets.add(target);
                  if (attemptedTargets.size() == 2) {
                    return new SqliteLeaseBusy(target);
                  }
                  return new SqliteHeldLease(target, releasedLeases::incrementAndGet);
                }));

    assertEquals(List.of(secretTarget, bookTarget), attemptedTargets);
    assertEquals(bookTarget, result.artifactPath());
    assertEquals(1, releasedLeases.get());
  }

  @Test
  void pairCoordinatorTransfersBothExactLeasesToTheirBookAndSecretOwners() throws Exception {
    Path bookTarget = managedTarget("coordinator-transfer/book.sqlite");
    Path secretTarget = managedTarget("coordinator-transfer/book.key");
    AtomicInteger releasedLeases = new AtomicInteger();

    SqliteManagedTargetLeasesHeld result =
        assertInstanceOf(
            SqliteManagedTargetLeasesHeld.class,
            SqliteBookMaintenanceLease.acquireManagedTargetPair(
                bookTarget,
                secretTarget,
                target -> new SqliteHeldLease(target, releasedLeases::incrementAndGet)));

    assertEquals(bookTarget, result.bookTargetLease().artifactPath());
    assertEquals(secretTarget, result.secretTargetLease().artifactPath());
    assertEquals(0, releasedLeases.get());

    result.bookTargetLease().close();
    result.secretTargetLease().close();
    assertEquals(2, releasedLeases.get());
  }

  @Test
  void pairCoordinator_refusesPreexistingNativeActivityBeforeInvokingItsAcquirer()
      throws Exception {
    Path bookTarget = writeArtifact("coordinator-preflight/book.sqlite", "book bytes");
    Path secretTarget = managedTarget("coordinator-preflight/book.key");
    AtomicInteger acquirerCalls = new AtomicInteger();
    SqliteNativeActivityRegistration activity =
        SqliteNativeRuntimeActivity.recordOpeningConnection(bookTarget, true);
    try {
      SqliteManagedTargetLeasesBusy result =
          assertInstanceOf(
              SqliteManagedTargetLeasesBusy.class,
              SqliteBookMaintenanceLease.acquireManagedTargetPair(
                  bookTarget,
                  secretTarget,
                  target -> {
                    acquirerCalls.incrementAndGet();
                    return new SqliteHeldLease(target, () -> {});
                  }));

      assertEquals(bookTarget, result.artifactPath());
      assertEquals(0, acquirerCalls.get());
    } finally {
      SqliteNativeRuntimeActivity.recordConnectionClosed(activity);
    }
  }

  @Test
  void pairCoordinator_releasesBothLeasesWhenActivityAppearsAfterAcquisition() throws Exception {
    Path bookTarget = writeArtifact("coordinator-post-acquisition/book.sqlite", "book bytes");
    Path secretTarget = managedTarget("coordinator-post-acquisition/book.key");
    AtomicInteger acquirerCalls = new AtomicInteger();
    AtomicInteger releasedLeases = new AtomicInteger();
    AtomicReference<SqliteNativeActivityRegistration> activity = new AtomicReference<>();
    try {
      SqliteManagedTargetLeasesBusy result =
          assertInstanceOf(
              SqliteManagedTargetLeasesBusy.class,
              SqliteBookMaintenanceLease.acquireManagedTargetPair(
                  bookTarget,
                  secretTarget,
                  target -> {
                    if (acquirerCalls.incrementAndGet() == 2) {
                      activity.set(
                          SqliteNativeRuntimeActivity.recordOpeningConnection(bookTarget, true));
                    }
                    return new SqliteHeldLease(target, releasedLeases::incrementAndGet);
                  }));

      assertEquals(bookTarget, result.artifactPath());
      assertEquals(2, acquirerCalls.get());
      assertEquals(2, releasedLeases.get());
    } finally {
      SqliteNativeRuntimeActivity.recordConnectionClosed(activity.get());
    }
  }

  @Test
  void sameParentBackupTargetsNeverAuthorizeAnActiveLiveSourceRetain() throws Exception {
    Path source = writeArtifact("same-parent-backup/z-live.sqlite", "live book bytes");
    Path backupTarget = managedTarget("same-parent-backup/a-backup.sqlite");
    Path backupKeyTarget = managedTarget("same-parent-backup/b-backup.key");

    SqliteManagedTargetLeasesHeld pair =
        assertInstanceOf(
            SqliteManagedTargetLeasesHeld.class,
            SqliteBookMaintenanceLease.acquireManagedTargetPair(backupTarget, backupKeyTarget));
    SqliteNativeActivityRegistration activityRegistration =
        SqliteNativeRuntimeActivity.recordOpeningConnection(source, true);
    try (SqliteHeldLease bookTargetLease = pair.bookTargetLease();
        SqliteHeldLease secretTargetLease = pair.secretTargetLease()) {
      assertEquals(backupTarget, bookTargetLease.artifactPath());
      assertEquals(backupKeyTarget, secretTargetLease.artifactPath());
      SqliteLeaseBusy busy =
          assertInstanceOf(
              SqliteLeaseBusy.class,
              SqliteBookMaintenanceLease.acquire(
                  source, SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT));
      assertEquals(source, busy.artifactPath());
    } finally {
      SqliteNativeRuntimeActivity.recordConnectionClosed(activityRegistration);
    }
  }

  @Test
  void activityOnEitherHardLinkAliasBlocksMaintenanceOnTheOtherAlias() throws Exception {
    Path original = writeArtifact("hard-link-original/book.sqlite", "book bytes");
    Path alias = managedTarget("hard-link-alias/book.sqlite");
    Files.createLink(alias, original);

    for (Path[] direction : new Path[][] {{original, alias}, {alias, original}}) {
      Path activeAlias = direction[0];
      Path maintenanceAlias = direction[1];
      SqliteNativeActivityRegistration activityRegistration =
          SqliteNativeRuntimeActivity.recordOpeningConnection(activeAlias, true);
      try {
        SqliteLeaseBusy busy =
            assertInstanceOf(
                SqliteLeaseBusy.class,
                SqliteBookMaintenanceLease.acquire(
                    maintenanceAlias, SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT));
        assertEquals(maintenanceAlias, busy.artifactPath());
      } finally {
        SqliteNativeRuntimeActivity.recordConnectionClosed(activityRegistration);
      }
    }
  }

  @Test
  void globalObjectLeaseBlocksASecondThreadFromEnteringThroughAHardLinkAlias() throws Exception {
    Path original = writeArtifact("hard-link-lease/original.sqlite", "book bytes");
    Path alias = managedTarget("hard-link-lease-alias/alias.sqlite");
    Files.createLink(alias, original);
    AtomicReference<SqliteProtectedBookLeaseAcquisition> concurrent = new AtomicReference<>();

    try (SqliteHeldLease ignored =
        assertInstanceOf(
            SqliteHeldLease.class,
            SqliteBookMaintenanceLease.acquire(
                original, SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT))) {
      Thread contender =
          new Thread(
              () ->
                  concurrent.set(
                      SqliteBookMaintenanceLease.acquire(
                          alias, SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT)));
      contender.start();
      contender.join();
    }

    assertEquals(alias, assertInstanceOf(SqliteLeaseBusy.class, concurrent.get()).artifactPath());
    try (SqliteHeldLease afterRelease =
        assertInstanceOf(
            SqliteHeldLease.class,
            SqliteBookMaintenanceLease.acquire(
                alias, SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT))) {
      assertEquals(alias, afterRelease.artifactPath());
    }
  }

  @Test
  void externallyHeldObjectExclusionReleasesTheDirectoryAdmissionBeforeReportingBusy()
      throws Exception {
    Path artifact = writeArtifact("object-exclusion-busy/book.sqlite", "book bytes");
    SqliteObjectCoordinationArtifacts.Domain domain =
        SqliteObjectCoordinationArtifacts.domainForExistingArtifact(artifact);

    try (SqliteLeaseHandle externallyHeldExclusion =
        java.util.Objects.requireNonNull(
            SqliteObjectCoordinationArtifacts.tryAcquireMaintenanceExclusion(domain),
            "direct object exclusion")) {
      SqliteLeaseBusy busy =
          assertInstanceOf(
              SqliteLeaseBusy.class,
              SqliteBookMaintenanceLease.acquire(
                  artifact, SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT));
      assertEquals(artifact, busy.artifactPath());
    }

    try (SqliteHeldLease reacquired =
        assertInstanceOf(
            SqliteHeldLease.class,
            SqliteBookMaintenanceLease.acquire(
                artifact, SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT))) {
      assertEquals(artifact, reacquired.artifactPath());
    }
  }

  @Test
  void refusedObjectExclusionReleasesTheDirectoryAdmissionBeforeReportingBusy()
      throws Exception {
    Path artifact = writeArtifact("injected-object-exclusion-busy/book.sqlite", "book bytes");

    SqliteLeaseBusy busy =
        assertInstanceOf(
            SqliteLeaseBusy.class,
            SqliteBookMaintenanceLease.acquireWithAdmittedScope(
                artifact,
                SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT,
                List.of(artifact),
                ignored -> null));
    assertEquals(artifact, busy.artifactPath());

    try (SqliteHeldLease reacquired =
        assertInstanceOf(
            SqliteHeldLease.class,
            SqliteBookMaintenanceLease.acquire(
                artifact, SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT))) {
      assertEquals(artifact, reacquired.artifactPath());
    }
  }

  @Test
  void objectExclusionRuntimeFailureReleasesTheDirectoryAdmission() throws Exception {
    Path artifact = writeArtifact("injected-object-exclusion-runtime/book.sqlite", "book bytes");
    IllegalStateException expected = new IllegalStateException("injected object exclusion failure");

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteBookMaintenanceLease.acquireWithAdmittedScope(
                    artifact,
                    SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT,
                    List.of(artifact),
                    ignored -> {
                      throw expected;
                    }));
    assertSame(expected, failure);

    try (SqliteHeldLease reacquired =
        assertInstanceOf(
            SqliteHeldLease.class,
            SqliteBookMaintenanceLease.acquire(
                artifact, SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT))) {
      assertEquals(artifact, reacquired.artifactPath());
    }
  }

  @Test
  void objectExclusionIoFailureIsPreservedAfterDirectoryAdmissionCleanup() throws Exception {
    Path artifact = writeArtifact("injected-object-exclusion-io/book.sqlite", "book bytes");
    java.io.IOException expected = new java.io.IOException("injected object exclusion I/O failure");

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteBookMaintenanceLease.acquireWithAdmittedScope(
                    artifact,
                    SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT,
                    List.of(artifact),
                    ignored -> {
                      throw expected;
                    }));
    assertSame(expected, failure.getCause());

    try (SqliteHeldLease reacquired =
        assertInstanceOf(
            SqliteHeldLease.class,
            SqliteBookMaintenanceLease.acquire(
                artifact, SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT))) {
      assertEquals(artifact, reacquired.artifactPath());
    }
  }

  @Test
  void nestedSameThreadExistingArtifactLeasesRetainOnePhysicalObjectExclusion() throws Exception {
    Path artifact = writeArtifact("nested-object-lease/book.sqlite", "book bytes");

    try (SqliteHeldLease first =
            assertInstanceOf(
                SqliteHeldLease.class,
                SqliteBookMaintenanceLease.acquire(
                    artifact, SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT));
        SqliteHeldLease second =
            assertInstanceOf(
                SqliteHeldLease.class,
                SqliteBookMaintenanceLease.acquire(
                    artifact, SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT))) {
      first.close();
      assertEquals(artifact, second.artifactPath());
      assertTrue(SqliteMaintenanceLeaseAuthority.hasBlockingActivity(artifact));
    }

    assertFalse(SqliteMaintenanceLeaseAuthority.hasBlockingActivity(artifact));
  }

  @Test
  void sameDirectoryAdmissionScopeMustContainItsExactArtifactAndNoOtherDomain() throws Exception {
    Path artifact = managedTarget("admission-scope/artifact.sqlite");
    Path sibling = managedTarget("admission-scope/sibling.key");
    Path otherDomain = managedTarget("admission-scope-other/other.key");

    IllegalArgumentException omittedArtifact =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                SqliteBookMaintenanceLease.acquireWithAdmittedScope(
                    artifact,
                    SqliteMaintenanceLeaseIntent.MANAGED_TARGET,
                    java.util.List.of(sibling)));
    assertTrue(
        java.util.Objects.requireNonNull(omittedArtifact.getMessage(), "omitted-scope message")
            .contains("omitted its acquired artifact"));

    IllegalArgumentException crossedDomain =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                SqliteBookMaintenanceLease.acquireWithAdmittedScope(
                    artifact,
                    SqliteMaintenanceLeaseIntent.MANAGED_TARGET,
                    java.util.List.of(artifact, otherDomain)));
    assertTrue(
        java.util.Objects.requireNonNull(crossedDomain.getMessage(), "cross-domain message")
            .contains("crossed directory domains"));
  }

  @Test
  void activeSameDirectorySiblingCannotBeAdmittedAfterTheFirstLease() throws Exception {
    Path first = writeArtifact("same-directory-activity/first.sqlite", "first");
    Path activeSibling = writeArtifact("same-directory-activity/sibling.sqlite", "sibling");

    try (SqliteHeldLease ignored =
        assertInstanceOf(
            SqliteHeldLease.class,
            SqliteBookMaintenanceLease.acquire(
                first, SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT))) {
      SqliteNativeActivityRegistration activity =
          SqliteNativeRuntimeActivity.recordOpeningConnection(activeSibling, false);
      try {
        assertEquals(
            activeSibling,
            assertInstanceOf(
                    SqliteLeaseBusy.class,
                    SqliteBookMaintenanceLease.acquire(
                        activeSibling, SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT))
                .artifactPath());
      } finally {
        SqliteNativeRuntimeActivity.recordConnectionClosed(activity);
      }
    }
  }

  @Test
  void nestedNativeConnectionRetainsTheCurrentThreadsExactObjectLeaseUntilItCloses()
      throws Exception {
    Path source = writeArtifact("nested-activity/book.sqlite", "book bytes");
    try (SqliteHeldLease sourceLease =
        assertInstanceOf(
            SqliteHeldLease.class,
            SqliteBookMaintenanceLease.acquire(
                source, SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT))) {
      SqliteNativeActivityRegistration activityRegistration =
          SqliteNativeRuntimeActivity.recordOpeningConnection(source, true);

      sourceLease.close();
      assertTrue(SqliteMaintenanceLeaseAuthority.hasBlockingActivity(source));

      SqliteNativeRuntimeActivity.recordConnectionClosed(activityRegistration);
    }
    assertFalse(SqliteMaintenanceLeaseAuthority.hasBlockingActivity(source));
  }

  @Test
  void pairAcquisitionReleasesTheFirstDomainWhenALaterAcquisitionThrows() throws Exception {
    Path secretTarget = managedTarget("late-pair-failure/a-secret/book.key");
    Path bookTarget = managedTarget("late-pair-failure/z-book/book.sqlite");
    Path secretLeasePath =
        SqliteMaintenanceLeaseArtifacts.controlFilePath(
            java.util.Objects.requireNonNull(secretTarget.getParent(), "secretTarget parent")
                .toRealPath());
    java.util.concurrent.atomic.AtomicInteger acquisitionCalls =
        new java.util.concurrent.atomic.AtomicInteger();

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteBookMaintenanceLease.acquireManagedTargetPair(
                    bookTarget,
                    secretTarget,
                    target -> {
                      if (acquisitionCalls.getAndIncrement() == 0) {
                        return SqliteBookMaintenanceLease.acquire(
                            target, SqliteMaintenanceLeaseIntent.MANAGED_TARGET);
                      }
                      throw new IllegalStateException("injected later pair-acquisition failure");
                    }));

    assertEquals("injected later pair-acquisition failure", failure.getMessage());
    assertTrue(Files.exists(secretLeasePath));
    assertFalse(
        SqliteMaintenanceLeaseArtifacts.hasBlockingArtifact(secretTarget.getParent().toRealPath()));
    try (SqliteHeldLease reacquired =
        assertInstanceOf(
            SqliteHeldLease.class,
            SqliteBookMaintenanceLease.acquire(
                secretTarget, SqliteMaintenanceLeaseIntent.MANAGED_TARGET))) {
      assertEquals(secretTarget.toAbsolutePath().normalize(), reacquired.artifactPath());
    }
  }

  @Test
  void retiredRawTargetLeaseResidueBlocksEverySiblingWithoutDeletion() throws Exception {
    Path artifact = writeArtifact("retired-lease/book.sqlite", "book bytes");
    Path sibling = artifact.resolveSibling("other.sqlite");
    Path retiredLease = artifact.resolveSibling("book.sqlite.fingrind-maintenance.lock");
    Files.writeString(retiredLease, "retired lease bytes");

    SqliteLeaseBusy busy =
        assertInstanceOf(
            SqliteLeaseBusy.class,
            SqliteBookMaintenanceLease.acquire(
                artifact, SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT));
    assertEquals(artifact, busy.artifactPath());
    assertTrue(Files.exists(retiredLease));
    assertThrows(
        ContractFailureException.class,
        () -> SqliteMaintenanceLeaseAuthority.requireNoActiveLease(sibling));
    assertTrue(Files.exists(retiredLease));
  }

  @Test
  void existingArtifactLeaseRetainsTheExistingArtifactContract() {
    Path missing = tempDirectory.resolve("missing-parent").resolve("book.sqlite");

    SqliteCallerPathContractException failure =
        assertThrows(
            SqliteCallerPathContractException.class,
            () ->
                SqliteBookMaintenanceLease.acquire(
                    missing, SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT));
    assertEquals(SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY, failure.pathFailure());
  }

  @Test
  void currentThreadMayReadAnArtifactInItsHeldDirectoryDomain() throws Exception {
    Path artifact = writeArtifact("owned-domain/book.sqlite", "book bytes");

    try (SqliteHeldLease ignored =
        assertInstanceOf(
            SqliteHeldLease.class,
            SqliteBookMaintenanceLease.acquire(
                artifact, SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT))) {
      assertDoesNotThrow(() -> SqliteMaintenanceLeaseAuthority.requireNoActiveLease(artifact));
    }
  }

  private Path managedTarget(String relativePath) throws java.io.IOException {
    Path target = tempDirectory.resolve(relativePath);
    Path parent = target.getParent();
    if (parent == null) {
      throw new AssertionError("Fixture target requires one parent.");
    }
    Files.createDirectories(parent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parent);
    return target;
  }

  private Path writeArtifact(String relativePath, String content) throws java.io.IOException {
    Path artifact = managedTarget(relativePath);
    Files.writeString(artifact, content);
    return artifact.toAbsolutePath().normalize();
  }
}
