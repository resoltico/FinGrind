package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowSourceMember;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowSourceMembers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Regression coverage for immutable source-and-pair maintenance lease scopes. */
class SqliteMaintenanceWorkflowScopeTest extends SqliteNativeBridgeTestSupport {
  @Test
  void backupScopeAdmitsSameParentSourceAndPairWithoutGrantingSiblingAuthority() throws Exception {
    Path source = writeArtifact("backup-same-parent/live.sqlite", "source");
    Path backupTarget = managedTarget("backup-same-parent/backup.fgba");
    Path backupKeyTarget = managedTarget("backup-same-parent/backup.key");
    Path unrelatedSibling = managedTarget("backup-same-parent/unrelated.sqlite");

    try (SqliteWorkflowLeaseScope ignored = heldScope(source, backupTarget, backupKeyTarget)) {
      assertTrue(SqliteMaintenanceLeaseAuthority.currentThreadOwnsArtifactLease(source));
      assertTrue(SqliteMaintenanceLeaseAuthority.currentThreadOwnsArtifactLease(backupTarget));
      assertTrue(SqliteMaintenanceLeaseAuthority.currentThreadOwnsArtifactLease(backupKeyTarget));
      assertFalse(SqliteMaintenanceLeaseAuthority.currentThreadOwnsArtifactLease(unrelatedSibling));

      SqliteLeaseBusy busy =
          assertInstanceOf(
              SqliteLeaseBusy.class,
              SqliteBookMaintenanceLease.acquire(
                  unrelatedSibling, SqliteMaintenanceLeaseIntent.MANAGED_TARGET));
      assertEquals(unrelatedSibling, busy.artifactPath());
    }
  }

  @Test
  void restoreScopeAdmitsSameParentBackupSourceAndPair() throws Exception {
    Path backupSource = writeArtifact("restore-same-parent/backup.fgba", "backup source");
    Path restoredTarget = managedTarget("restore-same-parent/restored.sqlite");
    Path restoredKeyTarget = managedTarget("restore-same-parent/restored.key");

    try (SqliteWorkflowLeaseScope ignored =
        heldScope(backupSource, restoredTarget, restoredKeyTarget)) {
      assertTrue(SqliteMaintenanceLeaseAuthority.currentThreadOwnsArtifactLease(backupSource));
      assertTrue(SqliteMaintenanceLeaseAuthority.currentThreadOwnsArtifactLease(restoredTarget));
      assertTrue(SqliteMaintenanceLeaseAuthority.currentThreadOwnsArtifactLease(restoredKeyTarget));
    }
  }

  @Test
  void rekeyScopeRetainsTheLiveBookAfterTransferringItsDuplicateTargetAdmission() throws Exception {
    Path liveBook = writeArtifact("rekey-same-path/live.sqlite", "live source");
    Path replacementKeyTarget = managedTarget("rekey-same-path/replacement.key");

    try (SqliteWorkflowLeaseScope scope = heldScope(liveBook, liveBook, replacementKeyTarget)) {
      assertTrue(SqliteMaintenanceLeaseAuthority.currentThreadOwnsArtifactLease(liveBook));
      assertTrue(
          SqliteMaintenanceLeaseAuthority.currentThreadOwnsArtifactLease(replacementKeyTarget));

      try (SqliteTargetAdmissionLeases targetAdmissionLeases = scope.takeTargetAdmissionLeases();
          SqlitePairPublicationPreparationResources resources =
              new SqlitePairPublicationPreparationResources()) {
        targetAdmissionLeases.transferTo(resources);
      }

      assertTrue(
          SqliteMaintenanceLeaseAuthority.currentThreadOwnsArtifactLease(liveBook),
          "The source lease must outlive the duplicate rekey target admission reference.");
      assertFalse(
          SqliteMaintenanceLeaseAuthority.currentThreadOwnsArtifactLease(replacementKeyTarget));
    }

    assertFalse(SqliteMaintenanceLeaseAuthority.currentThreadOwnsArtifactLease(liveBook));
  }

  @Test
  void scopeReleasesEarlierCrossParentDomainsWhenLaterMemberIsBusy() throws Exception {
    Path source = writeArtifact("cross-parent/a-source/source.sqlite", "source");
    Path bookTarget = managedTarget("cross-parent/m-book/book.sqlite");
    Path secretTarget = managedTarget("cross-parent/z-secret/book.key");
    CountDownLatch secretLeaseHeld = new CountDownLatch(1);
    CountDownLatch releaseSecretLease = new CountDownLatch(1);
    AtomicReference<Throwable> holderFailure = new AtomicReference<>();
    Thread holder =
        new Thread(
            () -> {
              try (SqliteHeldLease ignored =
                  assertInstanceOf(
                      SqliteHeldLease.class,
                      SqliteBookMaintenanceLease.acquire(
                          secretTarget, SqliteMaintenanceLeaseIntent.MANAGED_TARGET))) {
                secretLeaseHeld.countDown();
                if (!awaitRelease(releaseSecretLease)) {
                  holderFailure.set(
                      new AssertionError("Timed out while holding the later workflow member."));
                  return;
                }
              } catch (RuntimeException | AssertionError failure) {
                holderFailure.set(failure);
                secretLeaseHeld.countDown();
              }
            });
    holder.start();
    assertTrue(secretLeaseHeld.await(10, TimeUnit.SECONDS));
    try {
      assertTrue(holderFailure.get() == null, () -> "holder failed: " + holderFailure.get());
      SqliteWorkflowScopeAcquisition acquisition =
          SqliteBookMaintenanceLease.acquireWorkflowScope(
              sourceMembers(source),
              bookTarget,
              ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
              secretTarget,
              ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET);
      SqliteWorkflowScopeBusy busy = assertInstanceOf(SqliteWorkflowScopeBusy.class, acquisition);
      assertEquals(secretTarget, busy.artifactPath());

      try (SqliteHeldLease reacquiredSource =
              assertInstanceOf(
                  SqliteHeldLease.class,
                  SqliteBookMaintenanceLease.acquire(
                      source, SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT));
          SqliteHeldLease reacquiredBookTarget =
              assertInstanceOf(
                  SqliteHeldLease.class,
                  SqliteBookMaintenanceLease.acquire(
                      bookTarget, SqliteMaintenanceLeaseIntent.MANAGED_TARGET))) {
        assertEquals(source, reacquiredSource.artifactPath());
        assertEquals(bookTarget, reacquiredBookTarget.artifactPath());
      }
    } finally {
      releaseSecretLease.countDown();
      holder.join(10_000L);
    }
    assertTrue(holderFailure.get() == null, () -> "holder failed: " + holderFailure.get());
  }

  @Test
  void scopeReportsABusySourceBeforeItCanAcquireAnyPairTarget() throws Exception {
    Path source = writeArtifact("busy-source/source.sqlite", "source");
    Path bookTarget = managedTarget("busy-source-book/book.sqlite");
    Path secretTarget = managedTarget("busy-source-secret/book.key");
    CountDownLatch sourceLeaseHeld = new CountDownLatch(1);
    CountDownLatch releaseSourceLease = new CountDownLatch(1);
    AtomicReference<Throwable> holderFailure = new AtomicReference<>();
    Thread holder =
        new Thread(
            () -> {
              try (SqliteHeldLease ignored =
                  assertInstanceOf(
                      SqliteHeldLease.class,
                      SqliteBookMaintenanceLease.acquire(
                          source, SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT))) {
                sourceLeaseHeld.countDown();
                if (!awaitRelease(releaseSourceLease)) {
                  holderFailure.set(new AssertionError("Timed out while holding the source."));
                }
              } catch (RuntimeException | AssertionError failure) {
                holderFailure.set(failure);
                sourceLeaseHeld.countDown();
              }
            });
    holder.start();
    assertTrue(sourceLeaseHeld.await(10, TimeUnit.SECONDS));
    try {
      assertTrue(holderFailure.get() == null, () -> "holder failed: " + holderFailure.get());
      SqliteWorkflowScopeBusy busy =
          assertInstanceOf(
              SqliteWorkflowScopeBusy.class,
              SqliteBookMaintenanceLease.acquireWorkflowScope(
                  sourceMembers(source),
                  bookTarget,
                  ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                  secretTarget,
                  ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));
      assertEquals(source, busy.artifactPath());
      assertEquals(ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE, busy.artifactRole());

      try (SqliteHeldLease acquiredBookTarget =
              assertInstanceOf(
                  SqliteHeldLease.class,
                  SqliteBookMaintenanceLease.acquire(
                      bookTarget, SqliteMaintenanceLeaseIntent.MANAGED_TARGET));
          SqliteHeldLease acquiredSecretTarget =
              assertInstanceOf(
                  SqliteHeldLease.class,
                  SqliteBookMaintenanceLease.acquire(
                      secretTarget, SqliteMaintenanceLeaseIntent.MANAGED_TARGET))) {
        assertEquals(bookTarget, acquiredBookTarget.artifactPath());
        assertEquals(secretTarget, acquiredSecretTarget.artifactPath());
      }
    } finally {
      releaseSourceLease.countDown();
      holder.join(10_000L);
    }
    assertTrue(holderFailure.get() == null, () -> "holder failed: " + holderFailure.get());
  }

  @Test
  void scopeReleasesAnEarlierSourceWhenALaterSourceBecomesBusyAfterPreflight() throws Exception {
    Path firstSource = writeArtifact("acquisition-race/a-source.sqlite", "first source");
    Path laterSource = writeArtifact("acquisition-race/b-source.key", "later source");
    Path bookTarget = managedTarget("acquisition-race/book.sqlite");
    Path secretTarget = managedTarget("acquisition-race/book.key");
    AtomicBoolean releasedFirstSource = new AtomicBoolean();
    String firstSourceIdentity = SqliteObjectCoordinationArtifacts.physicalIdentity(firstSource);

    SqliteWorkflowScopeBusy busy =
        assertInstanceOf(
            SqliteWorkflowScopeBusy.class,
            SqliteMaintenanceWorkflowScopeAcquirer.acquire(
                sourceMembers(firstSource, laterSource),
                bookTarget,
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                secretTarget,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                (requests, request) -> {
                  if (request.artifactPath().equals(firstSource)) {
                    return heldSource(firstSource, firstSourceIdentity, releasedFirstSource);
                  }
                  if (request.artifactPath().equals(laterSource)) {
                    return new SqliteLeaseBusy(laterSource);
                  }
                  throw new AssertionError("Pair target must not be acquired after a busy source.");
                }));

    assertEquals(laterSource, busy.artifactPath());
    assertEquals(ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_SOURCE, busy.artifactRole());
    assertTrue(releasedFirstSource.get());
  }

  @Test
  void scopeReleasesEveryHeldMemberWhenALaterTargetBecomesBusy() throws Exception {
    Path source = writeArtifact("target-busy/source.sqlite", "source");
    Path bookTarget = managedTarget("target-busy/a-book.sqlite");
    Path secretTarget = managedTarget("target-busy/z-secret.key");
    AtomicBoolean releasedSource = new AtomicBoolean();
    AtomicBoolean releasedBookTarget = new AtomicBoolean();
    String sourceIdentity = SqliteObjectCoordinationArtifacts.physicalIdentity(source);

    SqliteWorkflowScopeBusy busy =
        assertInstanceOf(
            SqliteWorkflowScopeBusy.class,
            SqliteMaintenanceWorkflowScopeAcquirer.acquire(
                sourceMembers(source),
                bookTarget,
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                secretTarget,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                (requests, request) -> {
                  if (request.artifactPath().equals(source)) {
                    return heldSource(source, sourceIdentity, releasedSource);
                  }
                  if (request.artifactPath().equals(bookTarget)) {
                    return new SqliteHeldLease(bookTarget, () -> releasedBookTarget.set(true));
                  }
                  if (request.artifactPath().equals(secretTarget)) {
                    return new SqliteLeaseBusy(secretTarget);
                  }
                  throw new AssertionError("Unexpected workflow request: " + request);
                }));

    assertEquals(secretTarget, busy.artifactPath());
    assertEquals(ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET, busy.artifactRole());
    assertTrue(releasedSource.get());
    assertTrue(releasedBookTarget.get());
  }

  @Test
  void scopePreservesAReleaseFailureWhenTargetAcquisitionFails() throws Exception {
    Path source = writeArtifact("release-failure/source.sqlite", "source");
    Path bookTarget = managedTarget("release-failure/book.sqlite");
    Path secretTarget = managedTarget("release-failure/book.key");
    IllegalStateException primaryFailure =
        new IllegalStateException("injected target acquisition failure");
    IllegalStateException releaseFailure =
        new IllegalStateException("injected source release failure");
    String sourceIdentity = SqliteObjectCoordinationArtifacts.physicalIdentity(source);

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteMaintenanceWorkflowScopeAcquirer.acquire(
                    sourceMembers(source),
                    bookTarget,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                    secretTarget,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                    (requests, request) -> {
                      if (request.artifactPath().equals(source)) {
                        return new SqliteHeldLease(
                            source,
                            sourceIdentity,
                            () -> {
                              throw releaseFailure;
                            });
                      }
                      throw primaryFailure;
                    }));

    assertSame(primaryFailure, failure);
    assertEquals(1, failure.getSuppressed().length);
    assertSame(releaseFailure, failure.getSuppressed()[0]);
  }

  @Test
  void finalTargetRequestsRejectASourceBeforeFinalTargetAcquisitionCanBegin() {
    Path source = tempDirectory.resolve("typed-target-request/source.sqlite").toAbsolutePath();
    SqliteWorkflowScopeRequests.Request sourceRequest =
        new SqliteWorkflowScopeRequests.Request(
            source,
            java.util.Objects.requireNonNull(source.getParent(), "source parent"),
            SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT,
            SqliteWorkflowScopeRequests.Member.SOURCE,
            ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> SqliteWorkflowScopeRequests.TargetRequest.from(sourceRequest));

    assertTrue(
        java.util.Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("Source workflow requests"));
  }

  @Test
  void scopeRejectsAnAcquirerThatReportsABusyArtifactOutsideItsExactRequest() throws Exception {
    Path source = writeArtifact("unadmitted-busy/source.sqlite", "source");
    Path bookTarget = managedTarget("unadmitted-busy/book.sqlite");
    Path secretTarget = managedTarget("unadmitted-busy/book.key");
    Path unrelatedArtifact = managedTarget("unadmitted-busy/unrelated.sqlite");
    AtomicBoolean releasedSource = new AtomicBoolean();
    String sourceIdentity = SqliteObjectCoordinationArtifacts.physicalIdentity(source);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteMaintenanceWorkflowScopeAcquirer.acquire(
                    sourceMembers(source),
                    bookTarget,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                    secretTarget,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                    (requests, request) -> {
                      if (request.artifactPath().equals(source)) {
                        return heldSource(source, sourceIdentity, releasedSource);
                      }
                      return new SqliteLeaseBusy(unrelatedArtifact);
                    }));

    assertTrue(
        java.util.Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("unadmitted artifact"));
    assertTrue(releasedSource.get());
  }

  @Test
  void scopeRejectsNativeActivityOnEachDeclaredMemberBeforeAcquiringAnything() throws Exception {
    Path source = writeArtifact("activity-members/source.sqlite", "source");
    Path bookTarget = managedTarget("activity-members/book.sqlite");
    Path secretTarget = managedTarget("activity-members/book.key");
    Files.writeString(bookTarget, "book target");
    Files.writeString(secretTarget, "secret target");

    for (Path activeMember : java.util.List.of(source, bookTarget, secretTarget)) {
      SqliteNativeActivityRegistration activityRegistration =
          SqliteNativeRuntimeActivity.recordOpeningConnection(activeMember, false);
      try {
        SqliteWorkflowScopeAcquisition acquisition =
            SqliteBookMaintenanceLease.acquireWorkflowScope(
                sourceMembers(source),
                bookTarget,
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                secretTarget,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET);
        SqliteWorkflowScopeBusy busy = assertInstanceOf(SqliteWorkflowScopeBusy.class, acquisition);
        assertEquals(activeMember, busy.artifactPath());
      } finally {
        SqliteNativeRuntimeActivity.recordConnectionClosed(activityRegistration);
      }
    }
  }

  @Test
  void scopeAuthorizesOnlyPrivateStagesBoundToAnExactDeclaredMember() throws Exception {
    Path source = writeArtifact("derived-stage-authority/source.sqlite", "source");
    Path bookTarget = managedTarget("derived-stage-authority/book.sqlite");
    Path secretTarget = managedTarget("derived-stage-authority/book.key");
    Path unrelatedSibling = managedTarget("derived-stage-authority/unrelated.sqlite");
    SqliteOwnedStagedArtifact ownedBookStage =
        SqliteOwnedStagedArtifact.create(bookTarget, ".owned-stage-", ".sqlite");
    SqliteOwnedStagedArtifact unrelatedStage =
        SqliteOwnedStagedArtifact.create(unrelatedSibling, ".unrelated-stage-", ".sqlite");

    try (SqliteWorkflowLeaseScope ignored = heldScope(source, bookTarget, secretTarget)) {
      assertDoesNotThrow(
          () -> SqliteMaintenanceLeaseAuthority.requireNoActiveLease(ownedBookStage.stagedPath()));
      assertThrows(
          ContractFailureException.class,
          () -> SqliteMaintenanceLeaseAuthority.requireNoActiveLease(unrelatedStage.stagedPath()));
      assertThrows(
          ContractFailureException.class,
          () ->
              SqliteMaintenanceLeaseAuthority.requireNoActiveLease(
                  unrelatedSibling.resolveSibling("arbitrary-stage.sqlite")));
      SqliteOwnedStageRecord.recordExisting(bookTarget, ownedBookStage.stagedPath());
      assertThrows(
          ContractFailureException.class,
          () -> SqliteMaintenanceLeaseAuthority.requireNoActiveLease(ownedBookStage.stagedPath()));
    } finally {
      ownedBookStage.releaseRetained();
      unrelatedStage.releaseRetained();
    }
  }

  private SqliteWorkflowLeaseScope heldScope(Path source, Path bookTarget, Path secretTarget)
      throws java.io.IOException {
    SqliteWorkflowScopeAcquisition acquisition =
        SqliteBookMaintenanceLease.acquireWorkflowScope(
            sourceMembers(source),
            bookTarget,
            ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
            secretTarget,
            ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET);
    return assertInstanceOf(SqliteWorkflowScopeHeld.class, acquisition).scope();
  }

  private static boolean awaitRelease(CountDownLatch releaseSecretLease) {
    try {
      return releaseSecretLease.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Interrupted while holding the later workflow member.", exception);
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
    return target.toAbsolutePath().normalize();
  }

  private static WorkflowSourceMembers sourceMembers(Path source) {
    return sourceMembers(source, null);
  }

  private static WorkflowSourceMembers sourceMembers(
      Path firstSource, @org.jspecify.annotations.Nullable Path secondSource) {
    java.util.List<WorkflowSourceMember> members =
        new java.util.ArrayList<>(
            java.util.List.of(
                new WorkflowSourceMember(
                    firstSource, ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE)));
    if (secondSource != null) {
      members.add(
          new WorkflowSourceMember(
              secondSource, ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_SOURCE));
    }
    return new WorkflowSourceMembers(members);
  }

  private static SqliteHeldLease heldSource(
      Path source, String sourceIdentity, AtomicBoolean released) {
    return new SqliteHeldLease(source, sourceIdentity, () -> released.set(true));
  }

  private Path writeArtifact(String relativePath, String content) throws java.io.IOException {
    Path artifact = managedTarget(relativePath);
    Files.writeString(artifact, content);
    return artifact;
  }
}
