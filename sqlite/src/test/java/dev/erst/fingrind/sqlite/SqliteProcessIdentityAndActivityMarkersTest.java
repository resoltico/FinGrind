package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Focused tests for process identity and retained activity-control coordination. */
class SqliteProcessIdentityAndActivityMarkersTest extends SqliteNativeBridgeTestSupport {
  @Test
  void processIdentity_parsersEqualityAndLivenessCoverExpectedShapes() {
    SqliteProcessIdentity current = SqliteProcessIdentity.current();
    SqliteProcessIdentity currentFromLease =
        assertInstanceOf(
            SqliteProcessIdentity.class,
            SqliteProcessIdentity.fromLeaseMetadata(current.leaseMetadataText()));
    SqliteProcessIdentity currentFromLegacyMarker =
        assertInstanceOf(
            SqliteProcessIdentity.class,
            SqliteProcessIdentity.fromCoordinationToken(current.coordinationToken()));
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

    assertEquals(current, currentFromLease);
    assertEquals(current.hashCode(), currentFromLease.hashCode());
    assertEquals(current.coordinationToken(), currentFromLegacyMarker.coordinationToken());
    assertTrue(currentFromLease.isCurrentProcess());
    assertTrue(currentFromLegacyMarker.isCurrentProcess());
    assertTrue(currentFromLease.isLive());
    assertTrue(unknownStartCurrent.isLive());
    assertTrue(currentFromLease.isLiveWhenUnlocked());
    assertFalse(mismatchedStartCurrent.isLiveWhenUnlocked());
    assertFalse(mismatchedStartCurrent.isLive());
    assertFalse(missingProcess.isLive());
    assertNotEquals(mismatchedStartCurrent, currentFromLease);
    assertNotEquals("not-a-process-identity", currentFromLease);

    assertNull(SqliteProcessIdentity.fromLeaseMetadata("startEpochMillis=1\n"));
    assertNull(SqliteProcessIdentity.fromLeaseMetadata("pid=not-a-number\n"));
    assertNull(SqliteProcessIdentity.fromCoordinationToken("book.sqlite.marker"));
    assertNull(SqliteProcessIdentity.fromCoordinationToken("pid-1234"));
    assertNull(SqliteProcessIdentity.fromCoordinationToken("pid-NaN-start-1"));
    assertNull(SqliteProcessIdentity.fromCoordinationToken("pid-1-start-NaN"));
  }

  @Test
  void activityControlSlotIsReferenceCountedAndItsControlFileRemainsAfterRelease()
      throws Exception {
    Path bookPath = writeProtectedBookPath("reference-counted.sqlite");
    Path controlPath = activityControlPath(bookPath);

    assertFalse(SqliteBookActivityMarkers.hasExternalLiveMarker(bookPath));
    try (SqliteBookActivityMarkers.ActivityRegistration firstRegistration =
        SqliteBookActivityMarkers.acquireCurrentProcessActivity(bookPath)) {
      assertNotNull(firstRegistration.objectIdentity());
      try (SqliteBookActivityMarkers.ActivityRegistration secondRegistration =
          SqliteBookActivityMarkers.acquireCurrentProcessActivity(bookPath)) {
        assertNotNull(secondRegistration.objectIdentity());
        assertTrue(Files.isRegularFile(controlPath, LinkOption.NOFOLLOW_LINKS));
        assertTrue(SqliteBookActivityMarkers.hasExternalLiveMarker(bookPath));
      }
      assertTrue(SqliteBookActivityMarkers.hasExternalLiveMarker(bookPath));
    }

    assertFalse(SqliteBookActivityMarkers.hasExternalLiveMarker(bookPath));
    assertTrue(Files.isRegularFile(controlPath, LinkOption.NOFOLLOW_LINKS));
  }

  @Test
  void activityRegistrationCloseIsIdempotent() throws Exception {
    Path bookPath = writeProtectedBookPath("idempotent-close.sqlite");
    SqliteBookActivityMarkers.ActivityRegistration registration =
        SqliteBookActivityMarkers.acquireCurrentProcessActivity(bookPath);

    registration.close();
    registration.close();

    assertFalse(SqliteBookActivityMarkers.hasExternalLiveMarker(bookPath));
  }

  @Test
  void externalActivityQueryTreatsANonDirectoryParentAsInactiveWithoutProbingIt()
      throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath artifact = fileSystem.path("\\not-a-directory\\book.sqlite");
      AclFixturePath parent = assertInstanceOf(AclFixturePath.class, artifact.getParent());
      parent.exists = true;
      parent.regularFile = true;
      artifact.exists = true;
      artifact.regularFile = true;

      assertFalse(SqliteBookActivityMarkers.hasExternalLiveMarker(artifact));
    }
  }

  @Test
  void retiredActivityMarkerResidueFailsClosedWithoutDeletion() throws Exception {
    Path bookPath = writeProtectedBookPath("retired-marker.sqlite");
    Path retiredMarker =
        bookPath.resolveSibling(bookPath.getFileName() + ".fingrind-activity-retired.marker");
    Files.writeString(retiredMarker, "retired coordination state");

    assertTrue(SqliteBookActivityMarkers.hasExternalLiveMarker(bookPath));
    assertTrue(Files.exists(retiredMarker, LinkOption.NOFOLLOW_LINKS));
  }

  @Test
  void retiredActivityMarkerForOneBookDoesNotBlockAnUnrelatedSibling() throws Exception {
    Path firstBook = writeProtectedBookPath("marker-siblings/first.sqlite");
    Path secondBook = writeProtectedBookPath("marker-siblings/second.sqlite");
    Path firstBookMarker =
        firstBook.resolveSibling(firstBook.getFileName() + ".fingrind-activity-retired.marker");
    Files.writeString(firstBookMarker, "retired coordination state");

    assertTrue(SqliteBookActivityMarkers.hasExternalLiveMarker(firstBook));
    assertFalse(SqliteBookActivityMarkers.hasExternalLiveMarker(secondBook));
    assertTrue(Files.exists(firstBookMarker, LinkOption.NOFOLLOW_LINKS));
  }

  @Test
  void retiredV2ActivityControlResidueFailsClosedWithoutBeingInterpreted() throws Exception {
    Path bookPath = writeProtectedBookPath("retired-v2-control.sqlite");
    Path retiredControl = bookPath.resolveSibling(".fingrind-activity-v2-retired-identity.control");
    Files.writeString(retiredControl, "retired v2 activity control contents");

    assertTrue(SqliteBookActivityMarkers.hasExternalLiveMarker(bookPath));
    assertTrue(Files.exists(retiredControl, LinkOption.NOFOLLOW_LINKS));
  }

  @Test
  void malformedRetainedActivityControlFailsClosed() throws Exception {
    Path bookPath = writeProtectedBookPath("malformed-control.sqlite");
    Path controlPath = activityControlPath(bookPath);

    try (SqliteBookActivityMarkers.ActivityRegistration registration =
        SqliteBookActivityMarkers.acquireCurrentProcessActivity(bookPath)) {
      assertNotNull(registration.objectIdentity());
      assertTrue(SqliteBookActivityMarkers.hasExternalLiveMarker(bookPath));
    }
    Files.writeString(controlPath, "malformed control contents");

    assertTrue(SqliteBookActivityMarkers.hasExternalLiveMarker(bookPath));
    assertTrue(Files.isRegularFile(controlPath, LinkOption.NOFOLLOW_LINKS));
  }

  @Test
  void retiredRuntimeRootPropertyCannotSelectAnotherCoordinationNamespace() throws Exception {
    Path bookPath = writeProtectedBookPath("property-ignored.sqlite");
    Path trapRoot = tempDirectory.resolve("property-selected-root");
    String originalProperty = System.getProperty("fingrind.coordination.root");
    System.setProperty("fingrind.coordination.root", trapRoot.toString());
    try {
      Path controlPath = activityControlPath(bookPath);
      assertTrue(controlPath.startsWith(tempDirectory));
      assertFalse(controlPath.startsWith(trapRoot));
      assertFalse(Files.exists(trapRoot, LinkOption.NOFOLLOW_LINKS));
    } finally {
      if (originalProperty == null) {
        System.clearProperty("fingrind.coordination.root");
      } else {
        System.setProperty("fingrind.coordination.root", originalProperty);
      }
    }
  }

  @Test
  void symlinkedObjectCoordinationRootFailsClosed() throws Exception {
    Path bookPath = writeProtectedBookPath("symlink-root.sqlite");
    Path secureRoot = tempDirectory.resolve("real-coordination-root");
    Files.createDirectory(secureRoot);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(secureRoot);
    Path symlinkRoot = tempDirectory.resolve("coordination-root-link");
    try {
      Files.createSymbolicLink(symlinkRoot, secureRoot);
    } catch (UnsupportedOperationException | java.nio.file.FileSystemException unavailable) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "host filesystem cannot create symbolic links: " + unavailable);
      return;
    }

    try (AutoCloseable ignored = SqliteObjectCoordinationArtifacts.installTestRoot(symlinkRoot)) {
      IOException failure =
          assertThrows(
              IOException.class,
              () -> SqliteObjectCoordinationArtifacts.domainForExistingArtifact(bookPath));
      assertTrue(
          Objects.requireNonNull(failure.getMessage(), "failure message")
              .contains("private object-coordination root"));
    }
  }

  @Test
  void retiredOrUnrecognizedObjectCoordinationResidueCannotBeSilentlyAdopted() throws Exception {
    Path bookPath = writeProtectedBookPath("object-residue.sqlite");

    Path v4Root = tempDirectory.resolve("object-protocol-v4");
    Path retiredV3Root = tempDirectory.resolve("object-protocol-v3");
    Files.createDirectory(retiredV3Root);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(retiredV3Root);
    try (AutoCloseable ignored = SqliteObjectCoordinationArtifacts.installTestRoot(v4Root)) {
      IOException failure =
          assertThrows(
              IOException.class,
              () -> SqliteObjectCoordinationArtifacts.domainForExistingArtifact(bookPath));
      assertTrue(
          Objects.requireNonNull(failure.getMessage(), "retired namespace failure message")
              .contains("Retired FinGrind object-coordination namespace"));
    }
    assertTrue(Files.exists(retiredV3Root, LinkOption.NOFOLLOW_LINKS));

    Path residueRoot = tempDirectory.resolve("object-residue-v4");
    Files.createDirectory(residueRoot);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(residueRoot);
    Path retiredObject = residueRoot.resolve("object-v3-retained.control");
    Files.writeString(retiredObject, "retired v3 control");
    try (AutoCloseable ignored = SqliteObjectCoordinationArtifacts.installTestRoot(residueRoot)) {
      IOException failure =
          assertThrows(
              IOException.class,
              () -> SqliteObjectCoordinationArtifacts.domainForExistingArtifact(bookPath));
      assertTrue(
          Objects.requireNonNull(failure.getMessage(), "retired object failure message")
              .contains("Retired FinGrind object-coordination state"));
    }
    assertTrue(Files.exists(retiredObject, LinkOption.NOFOLLOW_LINKS));

    Path foreignRoot = tempDirectory.resolve("object-foreign-v4");
    Files.createDirectory(foreignRoot);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(foreignRoot);
    Path foreignState = foreignRoot.resolve("unrecognized.control");
    Files.writeString(foreignState, "foreign control");
    try (AutoCloseable ignored = SqliteObjectCoordinationArtifacts.installTestRoot(foreignRoot)) {
      IOException failure =
          assertThrows(
              IOException.class,
              () -> SqliteObjectCoordinationArtifacts.domainForExistingArtifact(bookPath));
      assertTrue(
          Objects.requireNonNull(failure.getMessage(), "foreign residue failure message")
              .contains("Unexpected state exists"));
    }
    assertTrue(Files.exists(foreignState, LinkOption.NOFOLLOW_LINKS));
  }

  @Test
  void objectControlAdmissionRejectsMalformedCurrentNamespaceNamesWithoutAdoptingThem()
      throws Exception {
    Path bookPath = writeProtectedBookPath("malformed-object-name.sqlite");

    for (String malformedName :
        java.util.List.of(
            "object-v4-no-control-suffix",
            "object-v4-too-short.control",
            "object-v4-" + "g".repeat(64) + ".control")) {
      Path root = tempDirectory.resolve("malformed-object-root-" + malformedName.hashCode());
      Files.createDirectory(root);
      SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(root);
      Path residue = root.resolve(malformedName);
      Files.writeString(residue, "malformed current namespace control");

      try (AutoCloseable ignored = SqliteObjectCoordinationArtifacts.installTestRoot(root)) {
        IOException failure =
            assertThrows(
                IOException.class,
                () -> SqliteObjectCoordinationArtifacts.domainForExistingArtifact(bookPath));
        assertTrue(
            Objects.requireNonNull(failure.getMessage(), "malformed name failure message")
                .contains("Unexpected state exists"));
      }
      assertTrue(Files.exists(residue, LinkOption.NOFOLLOW_LINKS));
    }
  }

  @Test
  void objectCoordinationRetainsOneExactDomainAndReleasesTestRootsInLifoOrder() throws Exception {
    Path bookPath = writeProtectedBookPath("domain-and-lifo.sqlite");
    Path outerRoot = tempDirectory.resolve("object-domain-outer");
    Path innerRoot = tempDirectory.resolve("object-domain-inner");
    AutoCloseable outer = SqliteObjectCoordinationArtifacts.installTestRoot(outerRoot);
    AutoCloseable inner = SqliteObjectCoordinationArtifacts.installTestRoot(innerRoot);

    try {
      SqliteObjectCoordinationArtifacts.Domain domain =
          SqliteObjectCoordinationArtifacts.domainForExistingArtifact(bookPath);
      byte[] suppliedMagic = domain.magic();
      suppliedMagic[0] ^= 1;
      assertFalse(Arrays.equals(suppliedMagic, domain.magic()));

      IllegalStateException outOfOrderClose =
          assertThrows(IllegalStateException.class, outer::close);
      assertTrue(
          Objects.requireNonNull(outOfOrderClose.getMessage(), "out-of-order close message")
              .contains("test root changed"));
    } finally {
      inner.close();
      outer.close();
    }
  }

  @Test
  void directObjectCoordinationLeasesAndActivitySlotsObserveOnePhysicalControl() throws Exception {
    Path bookPath = writeProtectedBookPath("direct-object-coordination.sqlite");
    try (AutoCloseable ignored =
        SqliteObjectCoordinationArtifacts.installTestRoot(
            tempDirectory.resolve("direct-object-coordination-root"))) {
      SqliteObjectCoordinationArtifacts.Domain domain =
          SqliteObjectCoordinationArtifacts.domainForExistingArtifact(bookPath);
      try (SqliteLeaseHandle maintenanceLease =
          Objects.requireNonNull(
              SqliteObjectCoordinationArtifacts.tryAcquireMaintenanceExclusion(bookPath),
              "maintenance lease")) {
        IOException activityBlockedByMaintenance =
            assertThrows(
                IOException.class,
                () -> SqliteObjectCoordinationArtifacts.acquireActivitySlot(domain));
        assertTrue(
            Objects.requireNonNull(activityBlockedByMaintenance.getMessage(), "busy slot message")
                .contains("No FinGrind object-coordination activity slot"));
      }
      try (SqliteObjectCoordinationArtifacts.ActivitySlot slot =
          SqliteObjectCoordinationArtifacts.acquireActivitySlot(domain)) {
        assertTrue(SqliteObjectCoordinationArtifacts.hasActiveSlot(bookPath));
      }
      assertFalse(SqliteObjectCoordinationArtifacts.hasActiveSlot(bookPath));
    }
  }

  @Test
  void objectCoordinationRootSelectionAndEmptyControlsRemainFailClosed() throws Exception {
    Path bookPath = writeProtectedBookPath("object-root-selection.sqlite");
    Path testRoot = tempDirectory.resolve("empty-object-coordination-root");
    try (AutoCloseable ignored = SqliteObjectCoordinationArtifacts.installTestRoot(testRoot)) {
      SqliteObjectCoordinationArtifacts.Domain domain =
          SqliteObjectCoordinationArtifacts.domainForExistingArtifact(bookPath);

      assertFalse(Files.exists(domain.controlPath(), LinkOption.NOFOLLOW_LINKS));
      assertFalse(SqliteObjectCoordinationArtifacts.hasActiveSlot(bookPath));

      Files.writeString(testRoot.resolve("object-v4-" + "a".repeat(64) + ".control"), "other object");
      assertEquals(
          domain.objectIdentity(),
          SqliteObjectCoordinationArtifacts.domainForExistingArtifact(bookPath).objectIdentity());
    }

    Path selectedRoot = tempDirectory.resolve("selected-windows-root");
    assertEquals(
        selectedRoot,
        SqliteObjectCoordinationArtifacts.createOrValidatePrivateRoot(
            tempDirectory.resolve("unused-root"), true, ignored -> selectedRoot));

    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath unsupportedRoot = fileSystem.path("\\unsupported\\coordination-root");
      IOException failure =
          assertThrows(
              IOException.class,
              () -> SqliteObjectCoordinationArtifacts.createOrValidatePrivatePosixRoot(unsupportedRoot));
      assertTrue(
          Objects.requireNonNull(failure.getMessage(), "unsupported-root message")
              .contains("requires POSIX owner-only root creation"));
    }
  }

  @Test
  void objectCoordinationRejectsEveryRetiredStateVariantAndMissingUserHome() throws Exception {
    Path bookPath = writeProtectedBookPath("object-retired-variants.sqlite");
    for (String retiredName :
        java.util.List.of(
            "object-v2-retained.control", ".fingrind-object-registry-v4.control")) {
      Path root = tempDirectory.resolve("retired-object-" + retiredName.hashCode());
      Files.createDirectory(root);
      SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(root);
      Files.writeString(root.resolve(retiredName), "retired object-coordination state");

      try (AutoCloseable ignored = SqliteObjectCoordinationArtifacts.installTestRoot(root)) {
        IOException failure =
            assertThrows(
                IOException.class,
                () -> SqliteObjectCoordinationArtifacts.domainForExistingArtifact(bookPath));
        assertTrue(
            Objects.requireNonNull(failure.getMessage(), "retired-state message")
                .contains("Retired FinGrind object-coordination state"));
      }
    }

    assertThrows(IOException.class, () -> SqliteObjectCoordinationArtifacts.userHomeRoot(null));
    assertThrows(IOException.class, () -> SqliteObjectCoordinationArtifacts.userHomeRoot(""));
  }

  private Path writeProtectedBookPath(String fileName) throws IOException {
    Path bookPath = tempDirectory.resolve(fileName).toAbsolutePath().normalize();
    Path parentPath = Objects.requireNonNull(bookPath.getParent(), "parentPath");
    Files.createDirectories(parentPath);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parentPath);
    SqliteBookFileSecurity.createNewOwnerOnlyBookFile(bookPath);
    Files.writeString(bookPath, "book");
    return bookPath;
  }

  private static Path activityControlPath(Path bookPath) throws IOException {
    return SqliteObjectCoordinationArtifacts.domainForExistingArtifact(
            Objects.requireNonNull(bookPath, "bookPath"))
        .controlPath();
  }
}
