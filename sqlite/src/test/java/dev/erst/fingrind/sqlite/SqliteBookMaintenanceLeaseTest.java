package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.ContractFailureException;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Behavioral tests for the exclusive protected-book maintenance lease seam. */
class SqliteBookMaintenanceLeaseTest extends SqliteNativeBridgeTestSupport {
  private static final MethodHandle ACQUIRE_LEASE_FILE =
      leaseHelper("acquireLeaseFile", MethodType.methodType(boolean.class, Path.class));
  private static final MethodHandle EXISTING_LEASE_IS_BUSY =
      leaseHelper("existingLeaseIsBusy", MethodType.methodType(boolean.class, Path.class));
  private static final MethodHandle RELEASE_LEASE_FILE_QUIETLY =
      leaseHelper("releaseLeaseFileQuietly", MethodType.methodType(void.class, Path.class));

  @Test
  void acquire_rejectsDuplicateOwnershipAndHeldLeaseCleanupDeletesTheLeaseFile() throws Exception {
    Path artifactPath = writeArtifact("held-lease.sqlite", "content");
    Path leasePath = leasePath(artifactPath);

    try (SqliteBookMaintenanceLease.HeldLease heldLease =
        assertInstanceOf(
            SqliteBookMaintenanceLease.HeldLease.class,
            SqliteBookMaintenanceLease.acquire(artifactPath))) {
      assertEquals(artifactPath.toAbsolutePath().normalize(), heldLease.artifactPath());
      assertDoesNotThrow(() -> SqliteBookMaintenanceLease.requireNoActiveLease(artifactPath));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class, () -> SqliteBookMaintenanceLease.acquire(artifactPath));
      assertTrue(
          NullTestSupport.messageOf(exception).contains("already owns"),
          () -> NullTestSupport.messageOf(exception));
      assertTrue(Files.exists(leasePath));
    }

    assertFalse(Files.exists(leasePath));
  }

  @Test
  void acquire_returnsBusyWhenTheLeaseFileBelongsToOneLiveProcess() throws Exception {
    Path artifactPath = writeArtifact("live-lease.sqlite", "content");
    Path leasePath = leasePath(artifactPath);
    Files.writeString(
        leasePath,
        SqliteProcessIdentity.current().leaseMetadataText(),
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE);
    SqliteBookFileSecurity.hardenOwnerOnlyFile(leasePath);

    SqliteBookMaintenanceLease.LeaseBusy leaseBusy =
        assertInstanceOf(
            SqliteBookMaintenanceLease.LeaseBusy.class,
            SqliteBookMaintenanceLease.acquire(artifactPath));
    assertEquals(artifactPath, leaseBusy.artifactPath());
    assertTrue(Files.exists(leasePath));
  }

  @Test
  void acquire_reclaimsStaleLeaseFilesAndDeletesThemOnClose() throws Exception {
    Path artifactPath = writeArtifact("stale-lease.sqlite", "content");
    Path leasePath = leasePath(artifactPath);
    Files.writeString(
        leasePath,
        "pid=99999999\nstartEpochMillis=-1\n",
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE);
    SqliteBookFileSecurity.hardenOwnerOnlyFile(leasePath);

    try (SqliteBookMaintenanceLease.HeldLease heldLease =
        assertInstanceOf(
            SqliteBookMaintenanceLease.HeldLease.class,
            SqliteBookMaintenanceLease.acquire(artifactPath))) {
      assertEquals(artifactPath, heldLease.artifactPath());
      assertTrue(Files.exists(leasePath));
    }

    assertFalse(Files.exists(leasePath));
  }

  @Test
  void heldLease_close_isIdempotent() throws Exception {
    Path artifactPath = writeArtifact("idempotent-close.sqlite", "content");
    Path leasePath = leasePath(artifactPath);
    try (SqliteBookMaintenanceLease.HeldLease heldLease =
        assertInstanceOf(
            SqliteBookMaintenanceLease.HeldLease.class,
            SqliteBookMaintenanceLease.acquire(artifactPath))) {

      heldLease.close();
      assertFalse(Files.exists(leasePath));
      assertDoesNotThrow(heldLease::close);
    }
  }

  @Test
  void acquire_returnsBusyWhenTheCurrentProcessHasOneOpenBookConnection() throws Exception {
    Path artifactPath = writeArtifact("busy-current-process.sqlite", "content");

    SqliteNativeBootstrap.recordOpeningConnection(artifactPath);
    try {
      SqliteBookMaintenanceLease.LeaseBusy leaseBusy =
          assertInstanceOf(
              SqliteBookMaintenanceLease.LeaseBusy.class,
              SqliteBookMaintenanceLease.acquire(artifactPath));
      assertEquals(artifactPath, leaseBusy.artifactPath());
    } finally {
      SqliteNativeBootstrap.recordConnectionClosed(artifactPath);
    }
  }

  @Test
  void acquire_returnsBusyWhenOneExternalProcessMarkerIsLive() throws Exception {
    Path artifactPath = writeArtifact("busy-external-marker.sqlite", "content");
    try (Process helperProcess = startHelperProcess("sleep", "30")) {
      Path markerPath = externalActivityMarkerPath(artifactPath, helperProcess);
      try {
        Files.writeString(
            markerPath,
            "pid=marker\nstartEpochMillis=-1\n",
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE);
        SqliteBookFileSecurity.hardenOwnerOnlyFile(markerPath);

        SqliteBookMaintenanceLease.LeaseBusy leaseBusy =
            assertInstanceOf(
                SqliteBookMaintenanceLease.LeaseBusy.class,
                SqliteBookMaintenanceLease.acquire(artifactPath));
        assertEquals(artifactPath, leaseBusy.artifactPath());
      } finally {
        Files.deleteIfExists(markerPath);
        helperProcess.destroyForcibly();
        helperProcess.waitFor();
      }
    }
  }

  @Test
  void activityMarkerIdentityParser_acceptsUnknownStartEpochTokens() {
    SqliteProcessIdentity identity =
        assertInstanceOf(
            SqliteProcessIdentity.class,
            SqliteProcessIdentity.fromActivityMarkerFileName(
                SqliteProcessIdentity.activityMarkerFileToken(
                    1234L, SqliteProcessIdentity.UNKNOWN_START_EPOCH_MILLIS)));
    assertEquals(
        SqliteProcessIdentity.activityMarkerFileToken(
            1234L, SqliteProcessIdentity.UNKNOWN_START_EPOCH_MILLIS),
        identity.activityMarkerFileToken());
  }

  @Test
  void requireNoActiveLease_rejectsLiveLeaseFilesAndDeletesStaleLeaseFiles() throws Exception {
    Path artifactPath = writeArtifact("require-no-active-lease.sqlite", "content");
    Path leasePath = leasePath(artifactPath);
    Files.writeString(
        leasePath,
        SqliteProcessIdentity.current().leaseMetadataText(),
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE);
    SqliteBookFileSecurity.hardenOwnerOnlyFile(leasePath);

    ContractFailureException liveLeaseFailure =
        assertThrows(
            ContractFailureException.class,
            () -> SqliteBookMaintenanceLease.requireNoActiveLease(artifactPath));
    assertTrue(
        NullTestSupport.messageOf(liveLeaseFailure)
            .contains("active FinGrind maintenance workflow"),
        () -> NullTestSupport.messageOf(liveLeaseFailure));
    assertTrue(Files.exists(leasePath));

    Files.deleteIfExists(leasePath);
    Files.writeString(
        leasePath,
        "pid=99999999\nstartEpochMillis=-1\n",
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE);
    SqliteBookFileSecurity.hardenOwnerOnlyFile(leasePath);

    assertDoesNotThrow(() -> SqliteBookMaintenanceLease.requireNoActiveLease(artifactPath));
    assertFalse(Files.exists(leasePath));
  }

  @Test
  void acquire_wrapsHardeningFailuresAndQuietlyToleratesLeaseCleanupFaults() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath bookPath = fileSystem.path("\\books\\book.sqlite");
      AclFixturePath leasePath = fileSystem.path("\\books\\book.sqlite.fingrind-maintenance.lock");
      leasePath.overrideAclView = throwingAclView("lease-harden-boom");
      leasePath.failDeleteIfExistsWith(new IOException("lease-delete-boom"));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class, () -> SqliteBookMaintenanceLease.acquire(bookPath));
      assertEquals("Failed to acquire one FinGrind maintenance lease.", exception.getMessage());
      assertEquals(
          "lease-harden-boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
    }
  }

  @Test
  void acquire_wrapsLeaseCreationIoFailuresAndRethrowsInvalidParentRuntimeFailures() {
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
      AclFixturePath leasePath = fileSystem.path("\\books\\book.sqlite.fingrind-maintenance.lock");
      leasePath.failNewByteChannelWith(new IOException("lease-boom"));

      IllegalStateException ioFailure =
          assertThrows(
              IllegalStateException.class, () -> SqliteBookMaintenanceLease.acquire(artifactPath));
      assertEquals("Failed to acquire one FinGrind maintenance lease.", ioFailure.getMessage());
      assertEquals("lease-boom", NullTestSupport.messageOf(NullTestSupport.causeOf(ioFailure)));
    }

    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parentPath = fileSystem.path("\\broken-parent");
      parentPath.exists = true;
      parentPath.regularFile = true;
      AclFixturePath artifactPath = fileSystem.path("\\broken-parent\\book.sqlite");

      IllegalArgumentException runtimeFailure =
          assertThrows(
              IllegalArgumentException.class,
              () -> SqliteBookMaintenanceLease.acquire(artifactPath));
      assertTrue(
          NullTestSupport.messageOf(runtimeFailure).contains("existing directory"),
          () -> NullTestSupport.messageOf(runtimeFailure));
    }
  }

  @Test
  void acquireLeaseFile_returnsFalseAfterTwoGhostCollisions() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath leasePath = fileSystem.path("\\books\\ghost.sqlite.fingrind-maintenance.lock");
      leasePath.failNewByteChannelWith(new FileAlreadyExistsException(leasePath.toString()));
      leasePath.failNewByteChannelWith(new FileAlreadyExistsException(leasePath.toString()));

      assertFalse(invokeAcquireLeaseFile(leasePath));
    }
  }

  @Test
  void existingLeaseIsBusy_returnsFalseWhenTheLeaseArtifactIsAbsent() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      assertFalse(
          invokeExistingLeaseIsBusy(
              fileSystem.path("\\books\\missing.sqlite.fingrind-maintenance.lock")));
    }
  }

  @Test
  void requireNoActiveLease_handlesMissingAndIncompleteLeaseArtifacts() throws Exception {
    Path missingLeaseArtifactPath = writeArtifact("missing-lease.sqlite", "content");
    assertDoesNotThrow(
        () -> SqliteBookMaintenanceLease.requireNoActiveLease(missingLeaseArtifactPath));

    Path artifactPath = writeArtifact("incomplete-lease.sqlite", "content");
    Path leasePath = leasePath(artifactPath);
    Files.writeString(
        leasePath, "corrupt-lease", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    Files.setLastModifiedTime(leasePath, FileTime.from(Instant.now()));
    ContractFailureException recentIncompleteFailure =
        assertThrows(
            ContractFailureException.class,
            () -> SqliteBookMaintenanceLease.requireNoActiveLease(artifactPath));
    assertTrue(
        NullTestSupport.messageOf(recentIncompleteFailure)
            .contains("active FinGrind maintenance workflow"),
        () -> NullTestSupport.messageOf(recentIncompleteFailure));

    Files.writeString(
        leasePath, "corrupt-lease", StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    Files.setLastModifiedTime(leasePath, FileTime.from(Instant.now().minusSeconds(10)));
    assertDoesNotThrow(() -> SqliteBookMaintenanceLease.requireNoActiveLease(artifactPath));
    assertFalse(Files.exists(leasePath));
  }

  @Test
  void releaseLeaseFileQuietly_swallowsDeleteFailures() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath leasePath =
          fileSystem.path("\\books\\cleanup.sqlite.fingrind-maintenance.lock");
      leasePath.exists = true;
      leasePath.regularFile = true;
      leasePath.failDeleteIfExistsWith(new IOException("delete-boom"));

      invokeReleaseLeaseFileQuietly(leasePath);
      assertTrue(leasePath.exists);
    }
  }

  @Test
  void requireNoActiveLease_wrapsLeaseInspectionReadFailures() {
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
      AclFixturePath leasePath = fileSystem.path("\\books\\book.sqlite.fingrind-maintenance.lock");
      leasePath.exists = true;
      leasePath.regularFile = true;
      leasePath.failNewByteChannelWith(new IOException("read-boom"));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookMaintenanceLease.requireNoActiveLease(artifactPath));
      assertEquals(
          "Failed to inspect or clear one FinGrind maintenance lease artifact.",
          exception.getMessage());
      assertEquals("read-boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
    }
  }

  @Test
  void acquire_reclaimsExpiredIncompleteLeaseArtifacts() throws Exception {
    Path artifactPath = writeArtifact("expired-incomplete-lease.sqlite", "content");
    Path leasePath = leasePath(artifactPath);
    Files.writeString(
        leasePath, "corrupt-lease", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    Files.setLastModifiedTime(leasePath, FileTime.from(Instant.now().minusSeconds(10)));

    try (SqliteBookMaintenanceLease.HeldLease heldLease =
        assertInstanceOf(
            SqliteBookMaintenanceLease.HeldLease.class,
            SqliteBookMaintenanceLease.acquire(artifactPath))) {
      assertEquals(artifactPath, heldLease.artifactPath());
    }

    assertFalse(Files.exists(leasePath));
  }

  private Path writeArtifact(String fileName, String contents) throws IOException {
    Path artifactPath = tempDirectory.resolve(fileName);
    Path parentDirectory = artifactPath.getParent();
    if (parentDirectory != null) {
      Files.createDirectories(parentDirectory);
      SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parentDirectory);
    }
    Files.writeString(artifactPath, contents);
    SqliteBookFileSecurity.hardenOwnerOnlyFile(artifactPath);
    return artifactPath.toAbsolutePath().normalize();
  }

  private static Path leasePath(Path artifactPath) {
    return artifactPath.resolveSibling(artifactPath.getFileName() + ".fingrind-maintenance.lock");
  }

  private static Path externalActivityMarkerPath(Path artifactPath, Process helperProcess) {
    return artifactPath.resolveSibling(
        artifactPath.getFileName()
            + ".fingrind-activity-"
            + SqliteProcessIdentity.activityMarkerFileToken(
                helperProcess.pid(), SqliteProcessIdentity.UNKNOWN_START_EPOCH_MILLIS)
            + ".marker");
  }

  private static MethodHandle leaseHelper(String methodName, MethodType methodType) {
    try {
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(SqliteBookMaintenanceLease.class, MethodHandles.lookup());
      return lookup.findStatic(SqliteBookMaintenanceLease.class, methodName, methodType);
    } catch (IllegalAccessException | NoSuchMethodException exception) {
      throw new LinkageError(
          "Failed to bind SQLite maintenance lease helper: " + methodName, exception);
    }
  }

  private static boolean invokeAcquireLeaseFile(Path leasePath) {
    try {
      return (boolean) ACQUIRE_LEASE_FILE.invokeExact(leasePath);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError(
          "Failed to invoke SQLite maintenance lease acquisition helper.", throwable);
    }
  }

  private static boolean invokeExistingLeaseIsBusy(Path leasePath) {
    try {
      return (boolean) EXISTING_LEASE_IS_BUSY.invokeExact(leasePath);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError(
          "Failed to invoke SQLite maintenance lease busy-state helper.", throwable);
    }
  }

  private static void invokeReleaseLeaseFileQuietly(Path leasePath) {
    try {
      RELEASE_LEASE_FILE_QUIETLY.invokeExact(leasePath);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError(
          "Failed to invoke SQLite maintenance lease cleanup helper.", throwable);
    }
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

  private Process startHelperProcess(String... command) throws IOException {
    return new ProcessBuilder(command).start();
  }
}
