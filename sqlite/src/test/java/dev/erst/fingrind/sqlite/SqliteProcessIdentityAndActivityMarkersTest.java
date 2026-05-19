package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Focused tests for process-identity and same-directory activity-marker coordination. */
class SqliteProcessIdentityAndActivityMarkersTest extends SqliteNativeBridgeTestSupport {
  @Test
  void processIdentity_parsersEqualityAndLivenessCoverExpectedShapes() {
    SqliteProcessIdentity current = SqliteProcessIdentity.current();
    SqliteProcessIdentity currentFromLease =
        assertInstanceOf(
            SqliteProcessIdentity.class,
            SqliteProcessIdentity.fromLeaseMetadata(current.leaseMetadataText()));
    SqliteProcessIdentity currentFromMarker =
        assertInstanceOf(
            SqliteProcessIdentity.class,
            SqliteProcessIdentity.fromActivityMarkerFileName(current.activityMarkerFileToken()));
    SqliteProcessIdentity unknownStartCurrent =
        assertInstanceOf(
            SqliteProcessIdentity.class,
            SqliteProcessIdentity.fromLeaseMetadata("pid=" + ProcessHandle.current().pid() + "\n"));
    SqliteProcessIdentity mismatchedStartCurrent =
        assertInstanceOf(
            SqliteProcessIdentity.class,
            SqliteProcessIdentity.fromLeaseMetadata(
                "pid=" + ProcessHandle.current().pid() + "\nstartEpochMillis=0\n"));
    SqliteProcessIdentity missingProcess =
        assertInstanceOf(
            SqliteProcessIdentity.class,
            SqliteProcessIdentity.fromLeaseMetadata("pid=999999999\nstartEpochMillis=0\n"));
    SqliteProcessIdentity differentCurrent =
        assertInstanceOf(
            SqliteProcessIdentity.class,
            SqliteProcessIdentity.fromLeaseMetadata(
                "pid=" + ProcessHandle.current().pid() + "\nstartEpochMillis=0\n"));

    assertEquals(current, currentFromLease);
    assertEquals(current.hashCode(), currentFromLease.hashCode());
    assertEquals(current.activityMarkerFileToken(), currentFromMarker.activityMarkerFileToken());
    assertTrue(currentFromLease.isCurrentProcess());
    assertTrue(currentFromMarker.isCurrentProcess());
    assertTrue(currentFromLease.isLive());
    assertTrue(unknownStartCurrent.isLive());
    assertFalse(mismatchedStartCurrent.isLive());
    assertFalse(missingProcess.isLive());
    Object differentType = "not-a-process-identity";
    boolean equalsDifferentType = currentFromLease.equals(differentType);
    assertNotEquals(differentCurrent, currentFromLease);
    assertFalse(equalsDifferentType);

    assertNull(SqliteProcessIdentity.fromLeaseMetadata("startEpochMillis=1\n"));
    assertNull(SqliteProcessIdentity.fromLeaseMetadata("pid=not-a-number\n"));
    assertNull(SqliteProcessIdentity.fromActivityMarkerFileName("book.sqlite.marker"));
    assertNull(SqliteProcessIdentity.fromActivityMarkerFileName("pid-1234"));
    assertNull(SqliteProcessIdentity.fromActivityMarkerFileName("pid-NaN-start-1"));
    assertNull(SqliteProcessIdentity.fromActivityMarkerFileName("pid-1-start-NaN"));
  }

  @Test
  void createCurrentProcessMarker_handlesFileAlreadyExistsAndOtherIoFailures() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE);

      AclFixturePath bookPath = fileSystem.path("\\books\\book.sqlite");
      AclFixturePath markerPath =
          fileSystem.path(
              "\\books\\book.sqlite.fingrind-activity-"
                  + SqliteProcessIdentity.current().activityMarkerFileToken()
                  + ".marker");
      markerPath.failNewByteChannelWith(new FileAlreadyExistsException(markerPath.toString()));

      assertDoesNotThrow(() -> SqliteBookActivityMarkers.createCurrentProcessMarker(bookPath));

      AclFixturePath failingBookPath = fileSystem.path("\\books\\broken.sqlite");
      AclFixturePath failingMarkerPath =
          fileSystem.path(
              "\\books\\broken.sqlite.fingrind-activity-"
                  + SqliteProcessIdentity.current().activityMarkerFileToken()
                  + ".marker");
      failingMarkerPath.failNewByteChannelWith(new IOException("marker-boom"));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookActivityMarkers.createCurrentProcessMarker(failingBookPath));
      assertEquals(
          "Failed to publish one FinGrind SQLite book activity marker.", exception.getMessage());
      assertEquals("marker-boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
    }
  }

  @Test
  void createCurrentProcessMarker_wrapsHardeningFailureAfterOneMarkerCollision() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath bookPath = fileSystem.path("\\books\\book.sqlite");
      AclFixturePath markerPath =
          fileSystem.path(
              "\\books\\book.sqlite.fingrind-activity-"
                  + SqliteProcessIdentity.current().activityMarkerFileToken()
                  + ".marker");
      markerPath.exists = true;
      markerPath.regularFile = true;
      markerPath.preserveExistingEntryOnDeleteIfExists();
      markerPath.overrideAclView = throwingAclView("marker-harden-boom");

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookActivityMarkers.createCurrentProcessMarker(bookPath));
      assertEquals(
          "Failed to publish one FinGrind SQLite book activity marker.", exception.getMessage());
      assertEquals(
          "marker-harden-boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
    }
  }

  @Test
  void deleteCurrentProcessMarker_swallowsDeleteFailures() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath bookPath = fileSystem.path("\\books\\book.sqlite");
      AclFixturePath markerPath =
          fileSystem.path(
              "\\books\\book.sqlite.fingrind-activity-"
                  + SqliteProcessIdentity.current().activityMarkerFileToken()
                  + ".marker");
      markerPath.exists = true;
      markerPath.regularFile = true;
      markerPath.failDeleteIfExistsWith(new IOException("delete-boom"));

      assertDoesNotThrow(() -> SqliteBookActivityMarkers.deleteCurrentProcessMarker(bookPath));
      assertTrue(markerPath.exists);
    }
  }

  @Test
  void hasExternalLiveMarker_wrapsDirectoryFailuresAndRejectsParentlessPaths() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.failNewDirectoryStreamWith(new IOException("scan-boom"));
      AclFixturePath bookPath = fileSystem.path("\\books\\book.sqlite");
      bookPath.exists = true;
      bookPath.regularFile = true;

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookActivityMarkers.hasExternalLiveMarker(bookPath));
      assertEquals(
          "Failed to inspect or clear one FinGrind SQLite book activity marker.",
          exception.getMessage());
      assertEquals("scan-boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
    }

    IllegalArgumentException parentlessPath =
        assertThrows(
            IllegalArgumentException.class,
            () -> SqliteBookActivityMarkers.hasExternalLiveMarker(Path.of("book.sqlite")));
    assertTrue(
        Objects.requireNonNull(parentlessPath.getMessage())
            .contains("beneath one parent directory"));
  }

  @Test
  void hasExternalLiveMarker_wrapsDirectoryCloseFailuresAfterOneSuccessfulScan() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.failDirectoryStreamCloseWith(new IOException("close-boom"));
      AclFixturePath bookPath = fileSystem.path("\\books\\book.sqlite");
      bookPath.exists = true;
      bookPath.regularFile = true;

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookActivityMarkers.hasExternalLiveMarker(bookPath));
      assertEquals(
          "Failed to inspect or clear one FinGrind SQLite book activity marker.",
          exception.getMessage());
      assertEquals("close-boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
    }
  }

  @Test
  void hasExternalLiveMarker_wrapsInvalidMarkerDeletionFailures() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      parentPath.exists = true;
      parentPath.regularFile = false;
      AclFixturePath bookPath = fileSystem.path("\\books\\book.sqlite");
      bookPath.exists = true;
      bookPath.regularFile = true;
      AclFixturePath invalidMarkerPath =
          fileSystem.path("\\books\\book.sqlite.fingrind-activity-invalid-token.marker");
      invalidMarkerPath.exists = true;
      invalidMarkerPath.regularFile = true;
      invalidMarkerPath.failDeleteIfExistsWith(new IOException("delete-boom"));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookActivityMarkers.hasExternalLiveMarker(bookPath));
      assertEquals(
          "Failed to inspect or clear one FinGrind SQLite book activity marker.",
          exception.getMessage());
      assertEquals("delete-boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
    }
  }

  @Test
  void hasExternalLiveMarker_returnsFalseWhenTheParentPathIsNotOneDirectory() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      parentPath.exists = true;
      parentPath.regularFile = true;
      AclFixturePath bookPath = fileSystem.path("\\books\\book.sqlite");
      bookPath.exists = true;
      bookPath.regularFile = true;

      assertFalse(SqliteBookActivityMarkers.hasExternalLiveMarker(bookPath));
    }
  }

  @Test
  void hasExternalLiveMarker_returnsFalseWhenNoMarkerFilesExist() throws Exception {
    Path bookPath = writeProtectedBookPath("no-markers.sqlite");
    assertFalse(SqliteBookActivityMarkers.hasExternalLiveMarker(bookPath));
  }

  @Test
  void hasExternalLiveMarker_returnsFalseAfterProcessingOneStaleExternalMarker() throws Exception {
    Path bookPath = writeProtectedBookPath("one-stale-marker.sqlite");
    Path staleSibling =
        markerPath(
            bookPath,
            SqliteProcessIdentity.activityMarkerFileToken(
                999_999_999L, SqliteProcessIdentity.UNKNOWN_START_EPOCH_MILLIS));
    Files.writeString(staleSibling, "stale");

    assertFalse(SqliteBookActivityMarkers.hasExternalLiveMarker(bookPath));
    assertFalse(Files.exists(staleSibling));
  }

  @Test
  void hasExternalLiveMarker_ignoresCurrentInvalidStaleAndIrrelevantMarkers() throws Exception {
    Path bookPath = writeProtectedBookPath("marker-scan.sqlite");
    Path parentPath = Objects.requireNonNull(bookPath.getParent(), "parentPath");
    Path nonRegularSibling =
        parentPath.resolve(bookPath.getFileName() + ".fingrind-activity-directory.marker");
    Files.createDirectories(nonRegularSibling);
    Path unrelatedSibling = parentPath.resolve("other.txt");
    Files.writeString(unrelatedSibling, "unrelated");
    Path invalidSibling = markerPath(bookPath, "invalid-token");
    Files.writeString(invalidSibling, "invalid");
    Path currentSibling =
        markerPath(bookPath, SqliteProcessIdentity.current().activityMarkerFileToken());
    Files.writeString(currentSibling, "current");
    Path wrongSuffixSibling =
        parentPath.resolve(
            bookPath.getFileName()
                + ".fingrind-activity-"
                + SqliteProcessIdentity.current().activityMarkerFileToken()
                + ".tmp");
    Files.writeString(wrongSuffixSibling, "wrong-suffix");
    Path staleSibling =
        markerPath(
            bookPath,
            SqliteProcessIdentity.activityMarkerFileToken(
                999_999_999L, SqliteProcessIdentity.UNKNOWN_START_EPOCH_MILLIS));
    Files.writeString(staleSibling, "stale");

    assertFalse(SqliteBookActivityMarkers.hasExternalLiveMarker(bookPath));
    assertTrue(Files.isDirectory(nonRegularSibling));
    assertTrue(Files.exists(unrelatedSibling));
    assertFalse(Files.exists(invalidSibling));
    assertTrue(Files.exists(currentSibling));
    assertTrue(Files.exists(wrongSuffixSibling));
    assertFalse(Files.exists(staleSibling));
  }

  @Test
  void hasExternalLiveMarker_returnsTrueForOneLiveExternalProcessMarker() throws Exception {
    Path bookPath = writeProtectedBookPath("live-external-marker.sqlite");
    try (Process helperProcess = startHelperProcess("sleep", "30")) {
      Path liveMarkerPath =
          markerPath(
              bookPath,
              SqliteProcessIdentity.activityMarkerFileToken(
                  helperProcess.pid(), SqliteProcessIdentity.UNKNOWN_START_EPOCH_MILLIS));
      try {
        Files.writeString(
            liveMarkerPath,
            "pid=helper\nstartEpochMillis=-1\n",
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE);

        assertTrue(SqliteBookActivityMarkers.hasExternalLiveMarker(bookPath));
      } finally {
        Files.deleteIfExists(liveMarkerPath);
        helperProcess.destroyForcibly();
        helperProcess.waitFor();
      }
    }
  }

  private Path writeProtectedBookPath(String fileName) throws IOException {
    Path bookPath = tempDirectory.resolve(fileName).toAbsolutePath().normalize();
    Path parentPath = Objects.requireNonNull(bookPath.getParent(), "parentPath");
    Files.createDirectories(parentPath);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parentPath);
    Files.writeString(bookPath, "book");
    SqliteBookFileSecurity.hardenOwnerOnlyFile(bookPath);
    return bookPath;
  }

  private static Path markerPath(Path bookPath, String markerToken) {
    return bookPath.resolveSibling(
        bookPath.getFileName() + ".fingrind-activity-" + markerToken + ".marker");
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
