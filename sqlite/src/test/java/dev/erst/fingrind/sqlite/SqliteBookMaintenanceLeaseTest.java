package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.ContractFailureException;
import java.nio.file.Files;
import java.nio.file.Path;
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
