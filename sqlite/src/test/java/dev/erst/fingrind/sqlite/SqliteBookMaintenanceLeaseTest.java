package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.ContractFailureException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Behavioral tests for the exclusive protected-book maintenance lease seam. */
class SqliteBookMaintenanceLeaseTest extends SqliteNativeBridgeTestSupport {
  @Test
  void acquireManagedTarget_createsTheParentDirectoryAndDeletesTheLeaseFileOnClose()
      throws Exception {
    Path artifactPath = tempDirectory.resolve("managed").resolve("book.sqlite");
    Path leasePath = leasePath(artifactPath);
    Path managedParent =
        java.util.Objects.requireNonNull(artifactPath.getParent(), "artifactPath parent");

    try (SqliteBookMaintenanceLease.HeldLease heldLease =
        assertInstanceOf(
            SqliteBookMaintenanceLease.HeldLease.class,
            SqliteBookMaintenanceLease.acquire(
                artifactPath, SqliteBookMaintenanceLease.LeaseIntent.MANAGED_TARGET))) {
      assertTrue(Files.isDirectory(managedParent, LinkOption.NOFOLLOW_LINKS));
      assertEquals(artifactPath.toAbsolutePath().normalize(), heldLease.artifactPath());
      assertTrue(Files.exists(leasePath));
    }

    assertFalse(Files.exists(leasePath));
  }

  @Test
  void acquireExistingArtifact_requiresOneExistingParentDirectory() {
    Path artifactPath = tempDirectory.resolve("missing-parent").resolve("book.sqlite");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteBookMaintenanceLease.acquire(
                    artifactPath, SqliteBookMaintenanceLease.LeaseIntent.EXISTING_ARTIFACT));

    assertTrue(
        NullTestSupport.messageOf(exception)
            .contains("requires one existing artifact parent directory"),
        () -> NullTestSupport.messageOf(exception));
  }

  @Test
  void acquireExistingArtifact_requiresOneExistingRegularArtifactFile() throws Exception {
    Path artifactPath = tempDirectory.resolve("existing-parent").resolve("book.sqlite");
    Files.createDirectories(artifactPath.getParent());

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteBookMaintenanceLease.acquire(
                    artifactPath, SqliteBookMaintenanceLease.LeaseIntent.EXISTING_ARTIFACT));

    assertTrue(
        NullTestSupport.messageOf(exception).contains("existing regular artifact file"),
        () -> NullTestSupport.messageOf(exception));
  }

  @Test
  void acquireExistingArtifact_rejectsDuplicateOwnershipInsideTheSameThread() throws Exception {
    Path artifactPath = writeArtifact("duplicate.sqlite", "content");
    Path leasePath = leasePath(artifactPath);

    try (SqliteBookMaintenanceLease.HeldLease ignored =
        assertInstanceOf(
            SqliteBookMaintenanceLease.HeldLease.class,
            SqliteBookMaintenanceLease.acquire(
                artifactPath, SqliteBookMaintenanceLease.LeaseIntent.EXISTING_ARTIFACT))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteBookMaintenanceLease.acquire(
                      artifactPath, SqliteBookMaintenanceLease.LeaseIntent.EXISTING_ARTIFACT));
      assertTrue(
          NullTestSupport.messageOf(exception).contains("already owns"),
          () -> NullTestSupport.messageOf(exception));
      assertTrue(Files.exists(leasePath));
    }

    assertFalse(Files.exists(leasePath));
  }

  @Test
  void acquire_returnsBusyWhenTheCurrentProcessHasOneOpenBookConnection() throws Exception {
    Path artifactPath = writeArtifact("busy.sqlite", "content");

    SqliteNativeBootstrap.recordOpeningConnection(artifactPath);
    try {
      SqliteBookMaintenanceLease.LeaseBusy leaseBusy =
          assertInstanceOf(
              SqliteBookMaintenanceLease.LeaseBusy.class,
              SqliteBookMaintenanceLease.acquire(
                  artifactPath, SqliteBookMaintenanceLease.LeaseIntent.EXISTING_ARTIFACT));
      assertEquals(artifactPath.toAbsolutePath().normalize(), leaseBusy.artifactPath());
    } finally {
      SqliteNativeBootstrap.recordConnectionClosed(artifactPath);
    }
  }

  @Test
  void acquire_returnsBusyWhenOneExternalActivityMarkerLooksLive() throws Exception {
    Path artifactPath = writeArtifact("external-marker-busy.sqlite", "content");
    Path markerPath =
        artifactPath.resolveSibling(
            artifactPath.getFileName()
                + ".fingrind-activity-"
                + SqliteProcessIdentity.activityMarkerFileToken(
                    ProcessHandle.current().pid(), SqliteProcessIdentity.UNKNOWN_START_EPOCH_MILLIS)
                + ".marker");
    Files.writeString(
        markerPath,
        "pid="
            + ProcessHandle.current().pid()
            + "\nstartEpochMillis="
            + SqliteProcessIdentity.UNKNOWN_START_EPOCH_MILLIS
            + "\n",
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE);
    SqliteBookFileSecurity.hardenOwnerOnlyFile(markerPath);

    SqliteBookMaintenanceLease.LeaseBusy leaseBusy =
        assertInstanceOf(
            SqliteBookMaintenanceLease.LeaseBusy.class,
            SqliteBookMaintenanceLease.acquire(
                artifactPath, SqliteBookMaintenanceLease.LeaseIntent.EXISTING_ARTIFACT));
    assertEquals(artifactPath.toAbsolutePath().normalize(), leaseBusy.artifactPath());
  }

  @Test
  void requireNoActiveLease_clearsOneStaleLeaseFile() throws Exception {
    Path artifactPath = writeArtifact("stale.sqlite", "content");
    Path leasePath = leasePath(artifactPath);
    writeLeaseMetadata(leasePath, "pid=99999999\nstartEpochMillis=-1\n");

    assertDoesNotThrow(() -> SqliteBookMaintenanceLease.requireNoActiveLease(artifactPath));
    assertFalse(Files.exists(leasePath));
  }

  @Test
  void requireNoActiveLease_rejectsOneLiveLeaseFile() throws Exception {
    Path artifactPath = writeArtifact("live.sqlite", "content");
    Path leasePath = leasePath(artifactPath);
    writeLeaseMetadata(leasePath, SqliteProcessIdentity.current().leaseMetadataText());

    ContractFailureException failure =
        assertThrows(
            ContractFailureException.class,
            () -> SqliteBookMaintenanceLease.requireNoActiveLease(artifactPath));
    assertTrue(
        NullTestSupport.messageOf(failure).contains("active FinGrind maintenance workflow"),
        () -> NullTestSupport.messageOf(failure));
    assertTrue(Files.exists(leasePath));
  }

  @Test
  void requireNoActiveLease_returnsWhenTheCurrentThreadAlreadyOwnsTheLease() throws Exception {
    Path artifactPath = writeArtifact("owned.sqlite", "content");
    Path leasePath = leasePath(artifactPath);

    try (SqliteBookMaintenanceLease.HeldLease heldLease =
        assertInstanceOf(
            SqliteBookMaintenanceLease.HeldLease.class,
            SqliteBookMaintenanceLease.acquire(
                artifactPath, SqliteBookMaintenanceLease.LeaseIntent.EXISTING_ARTIFACT))) {
      assertDoesNotThrow(() -> SqliteBookMaintenanceLease.requireNoActiveLease(artifactPath));
      assertTrue(Files.exists(leasePath));
      heldLease.close();
      heldLease.close();
    }

    assertFalse(Files.exists(leasePath));
  }

  @Test
  void acquire_returnsBusyForOneUnlockedLiveLeaseFileButReclaimsOneOldMalformedLease()
      throws Exception {
    Path liveArtifactPath = writeArtifact("unlocked-live.sqlite", "content");
    Path liveLeasePath = leasePath(liveArtifactPath);
    writeLeaseMetadata(liveLeasePath, SqliteProcessIdentity.current().leaseMetadataText());

    SqliteBookMaintenanceLease.LeaseBusy liveBusy =
        assertInstanceOf(
            SqliteBookMaintenanceLease.LeaseBusy.class,
            SqliteBookMaintenanceLease.acquire(
                liveArtifactPath, SqliteBookMaintenanceLease.LeaseIntent.EXISTING_ARTIFACT));
    assertEquals(liveArtifactPath, liveBusy.artifactPath());
    assertTrue(Files.exists(liveLeasePath));

    Path malformedArtifactPath = writeArtifact("malformed.sqlite", "content");
    Path malformedLeasePath = leasePath(malformedArtifactPath);
    writeLeaseMetadata(malformedLeasePath, "not-one-fingrind-lease-file\n");
    Files.setLastModifiedTime(
        malformedLeasePath, FileTime.from(Instant.now().minus(Duration.ofMinutes(10))));

    try (SqliteBookMaintenanceLease.HeldLease heldLease =
        assertInstanceOf(
            SqliteBookMaintenanceLease.HeldLease.class,
            SqliteBookMaintenanceLease.acquire(
                malformedArtifactPath, SqliteBookMaintenanceLease.LeaseIntent.EXISTING_ARTIFACT))) {
      assertEquals(malformedArtifactPath, heldLease.artifactPath());
      assertTrue(Files.exists(leasePath(malformedArtifactPath)));
    }
    assertFalse(Files.exists(leasePath(malformedArtifactPath)));
  }

  @Test
  void requireNoActiveLease_rejectsOneFreshMalformedLeaseFileUntilItsGraceWindowExpires()
      throws Exception {
    Path artifactPath = writeArtifact("fresh-malformed.sqlite", "content");
    Path leasePath = leasePath(artifactPath);
    writeLeaseMetadata(leasePath, "garbage\n");
    Files.setLastModifiedTime(leasePath, FileTime.from(Instant.now()));

    ContractFailureException failure =
        assertThrows(
            ContractFailureException.class,
            () -> SqliteBookMaintenanceLease.requireNoActiveLease(artifactPath));
    assertTrue(
        NullTestSupport.messageOf(failure).contains("active FinGrind maintenance workflow"),
        () -> NullTestSupport.messageOf(failure));
    assertTrue(Files.exists(leasePath));
  }

  @Test
  void requireNoActiveLease_wrapsDeleteFailuresWhileClearingOneStaleLease() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      parentPath.exists = true;
      parentPath.regularFile = false;
      AclFixturePath artifactPath = fileSystem.path("\\books\\book.sqlite");
      artifactPath.exists = true;
      artifactPath.regularFile = true;
      AclFixturePath leasePath = fileSystem.path("\\books\\book.sqlite.fingrind-maintenance.lock");
      leasePath.exists = true;
      leasePath.regularFile = true;
      leasePath.failDeleteIfExistsWith(new IOException("delete-boom"));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookMaintenanceLease.requireNoActiveLease(artifactPath));

      assertTrue(
          NullTestSupport.messageOf(exception)
              .contains("Failed to inspect or clear one FinGrind maintenance lease artifact."));
      assertEquals("delete-boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
    }
  }

  @Test
  void acquire_wrapsLeaseFileOpenAndWriteFailures() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      parentPath.exists = true;
      parentPath.regularFile = false;
      AclFixturePath artifactPath = fileSystem.path("\\books\\book.sqlite");
      artifactPath.exists = true;
      artifactPath.regularFile = true;
      AclFixturePath leasePath = fileSystem.path("\\books\\book.sqlite.fingrind-maintenance.lock");
      leasePath.failNewByteChannelWith(new IOException("lease-open-boom"));

      IllegalStateException openFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteBookMaintenanceLease.acquire(
                      artifactPath, SqliteBookMaintenanceLease.LeaseIntent.EXISTING_ARTIFACT));
      assertTrue(NullTestSupport.messageOf(openFailure).contains("Failed to acquire"));
    }

    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      parentPath.exists = true;
      parentPath.regularFile = false;
      AclFixturePath artifactPath = fileSystem.path("\\books\\book.sqlite");
      artifactPath.exists = true;
      artifactPath.regularFile = true;
      AclFixturePath leasePath = fileSystem.path("\\books\\book.sqlite.fingrind-maintenance.lock");
      leasePath.failWriteWith(new IOException("lease-write-boom"));

      IllegalStateException writeFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteBookMaintenanceLease.acquire(
                      artifactPath, SqliteBookMaintenanceLease.LeaseIntent.EXISTING_ARTIFACT));
      assertTrue(NullTestSupport.messageOf(writeFailure).contains("Failed to acquire"));
    }
  }

  @Test
  void acquire_reclaimsOneStaleLeaseFileWithoutAdvisoryFileLocks() throws Exception {
    Path artifactPath = writeArtifact("reclaimable.sqlite", "content");
    Path leasePath = leasePath(artifactPath);
    writeLeaseMetadata(leasePath, "pid=99999999\nstartEpochMillis=-1\n");

    try (SqliteBookMaintenanceLease.HeldLease heldLease =
        assertInstanceOf(
            SqliteBookMaintenanceLease.HeldLease.class,
            SqliteBookMaintenanceLease.acquire(
                artifactPath, SqliteBookMaintenanceLease.LeaseIntent.EXISTING_ARTIFACT))) {
      assertEquals(artifactPath, heldLease.artifactPath());
      assertTrue(Files.exists(leasePath));
    }

    assertFalse(Files.exists(leasePath));
  }

  @Test
  void acquire_reclaimsOneLeaseFileThatDisappearsDuringInspection() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE);
      AclFixturePath artifactPath = fileSystem.path("\\books\\book.sqlite");
      artifactPath.exists = true;
      artifactPath.regularFile = true;
      AclFixturePath leasePath = fileSystem.path("\\books\\book.sqlite.fingrind-maintenance.lock");
      leasePath.exists = true;
      leasePath.regularFile = true;
      leasePath.failNewByteChannelAfter(1, new NoSuchFileException(leasePath.toString()));

      try (SqliteBookMaintenanceLease.HeldLease heldLease =
          assertInstanceOf(
              SqliteBookMaintenanceLease.HeldLease.class,
              SqliteBookMaintenanceLease.acquire(
                  artifactPath, SqliteBookMaintenanceLease.LeaseIntent.EXISTING_ARTIFACT))) {
        assertEquals(artifactPath, heldLease.artifactPath());
        assertTrue(leasePath.exists);
      }

      assertFalse(leasePath.exists);
    }
  }

  @Test
  void acquire_returnsBusyWhenOneStaleLeaseCannotBeReclaimedAfterEveryRetry() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      parentPath.exists = true;
      parentPath.regularFile = false;
      AclFixturePath artifactPath = fileSystem.path("\\books\\book.sqlite");
      artifactPath.exists = true;
      artifactPath.regularFile = true;
      AclFixturePath leasePath = fileSystem.path("\\books\\book.sqlite.fingrind-maintenance.lock");
      leasePath.exists = true;
      leasePath.regularFile = true;
      leasePath.preserveExistingEntryOnDeleteIfExists();

      SqliteBookMaintenanceLease.LeaseBusy leaseBusy =
          assertInstanceOf(
              SqliteBookMaintenanceLease.LeaseBusy.class,
              SqliteBookMaintenanceLease.acquire(
                  artifactPath, SqliteBookMaintenanceLease.LeaseIntent.EXISTING_ARTIFACT));

      assertEquals(artifactPath, leaseBusy.artifactPath());
      assertTrue(leasePath.exists);
    }
  }

  @Test
  void acquire_wrapsLeaseHardeningFailureAndPreservesItOverCleanupFailure() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.aclView = secureDirectoryAcl(fileSystem.owner);
      AclFixturePath artifactPath = fileSystem.path("\\books\\book.sqlite");
      artifactPath.exists = true;
      artifactPath.regularFile = true;
      AclFixturePath leasePath = fileSystem.path("\\books\\book.sqlite.fingrind-maintenance.lock");
      leasePath.overrideAclView = throwingAclView("lease-harden-boom");
      leasePath.failDeleteIfExistsWith(new IOException("lease-cleanup-boom"));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteBookMaintenanceLease.acquire(
                      artifactPath, SqliteBookMaintenanceLease.LeaseIntent.EXISTING_ARTIFACT));

      assertTrue(NullTestSupport.messageOf(exception).contains("Failed to acquire"));
      assertEquals(
          "lease-harden-boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
      assertTrue(leasePath.exists);
    }
  }

  private Path writeArtifact(String fileName, String content) throws IOException {
    Path artifactPath = tempDirectory.resolve(fileName);
    Path parent = artifactPath.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
      SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parent);
    }
    Files.writeString(artifactPath, content);
    return artifactPath.toAbsolutePath().normalize();
  }

  private static void writeLeaseMetadata(Path leasePath, String metadata) throws IOException {
    Files.writeString(leasePath, metadata, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    SqliteBookFileSecurity.hardenOwnerOnlyFile(leasePath);
  }

  private static Path leasePath(Path artifactPath) {
    Path normalized = artifactPath.toAbsolutePath().normalize();
    return normalized.resolveSibling(
        normalized.getFileName().toString() + ".fingrind-maintenance.lock");
  }

  private static AclFileAttributeView throwingAclView(String message) {
    return new AclFileAttributeView() {
      @Override
      public String name() {
        return "acl";
      }

      @Override
      public List<AclEntry> getAcl() {
        return List.of();
      }

      @Override
      public void setAcl(List<AclEntry> acl) throws IOException {
        throw new IOException(message);
      }

      @Override
      public UserPrincipal getOwner() throws IOException {
        throw new IOException(message);
      }

      @Override
      public void setOwner(UserPrincipal ownerPrincipal) {
        throw new UnsupportedOperationException();
      }
    };
  }

  private static AclFixtureView secureDirectoryAcl(UserPrincipal owner) {
    AclFixtureView view = new AclFixtureView(owner);
    view.setAcl(
        List.of(
            AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(
                    Set.of(
                        AclEntryPermission.LIST_DIRECTORY,
                        AclEntryPermission.ADD_FILE,
                        AclEntryPermission.EXECUTE))
                .build()));
    return view;
  }
}
