package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Focused regression coverage for physical-object native connection activity accounting. */
class SqliteNativeConnectionActivityRegistryTest extends SqliteNativeBridgeTestSupport {
  @Test
  void absentPathHasNoNativeActivityAndDoesNotRequireAPhysicalIdentity() {
    Path absentBookPath = tempDirectory.resolve("absent/book.sqlite");

    assertEquals(0, SqliteNativeRuntimeActivity.activeConnectionCount(absentBookPath));
    assertFalse(
        Files.exists(
            tempDirectory.resolve("object-coordination-v4"),
            java.nio.file.LinkOption.NOFOLLOW_LINKS));
  }

  @Test
  void hardLinkAliasesShareOneProcessLocalActivityIdentity() throws Exception {
    Path original = writeBook("original/book.sqlite");
    Path alias = tempDirectory.resolve("alias/book.sqlite");
    Path aliasParent = Objects.requireNonNull(alias.getParent(), "alias parent");
    Files.createDirectories(aliasParent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(aliasParent);
    Files.createLink(alias, original);
    String physicalIdentity = SqliteObjectCoordinationArtifacts.physicalIdentity(original);

    assertEquals(physicalIdentity, SqliteObjectCoordinationArtifacts.physicalIdentity(alias));
    assertEquals(physicalIdentity, SqliteObjectCoordinationArtifacts.physicalIdentity(original));
    int activeConnectionsBeforeOpen = SqliteNativeRuntimeActivity.activeConnectionCount();

    SqliteNativeActivityRegistration registration =
        SqliteNativeRuntimeActivity.recordOpeningConnection(original, false);
    try {
      assertEquals(
          activeConnectionsBeforeOpen + 1, SqliteNativeRuntimeActivity.activeConnectionCount());
      assertEquals(1, SqliteNativeRuntimeActivity.activeConnectionCount(original));
      assertEquals(1, SqliteNativeRuntimeActivity.activeConnectionCount(alias));
    } finally {
      SqliteNativeRuntimeActivity.recordConnectionClosed(registration);
    }
    assertEquals(activeConnectionsBeforeOpen, SqliteNativeRuntimeActivity.activeConnectionCount());
    assertEquals(0, SqliteNativeRuntimeActivity.activeConnectionCount(alias));
  }

  @Test
  void closingAfterTheOriginalPathIsRenamedReleasesTheOriginalObjectRegistration()
      throws Exception {
    Path original = writeBook("renamed/book.sqlite");
    Path renamed = original.resolveSibling("renamed.sqlite");
    int activeConnectionsBeforeOpen = SqliteNativeRuntimeActivity.activeConnectionCount();
    SqliteNativeActivityRegistration registration =
        SqliteNativeRuntimeActivity.recordOpeningConnection(original, false);

    Files.move(original, renamed);
    SqliteNativeRuntimeActivity.recordConnectionClosed(registration);

    assertEquals(activeConnectionsBeforeOpen, SqliteNativeRuntimeActivity.activeConnectionCount());
    assertEquals(0, SqliteNativeRuntimeActivity.activeConnectionCount(renamed));
  }

  @Test
  void markerPublicationFailureLeavesNoProcessLocalActivity() throws Exception {
    Path bookPath = writeBook("retired-marker/book.sqlite");
    Path retiredControl = bookPath.resolveSibling(".fingrind-activity-v2-retired-identity.control");
    Files.writeString(retiredControl, "retired v2 activity control");
    int activeConnectionsBeforeOpen = SqliteNativeRuntimeActivity.activeConnectionCount();

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteNativeRuntimeActivity.recordOpeningConnection(bookPath, true));

    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("activity control-file slot"));
    assertEquals(activeConnectionsBeforeOpen, SqliteNativeRuntimeActivity.activeConnectionCount());
  }

  @Test
  void nestedMarkerRegistrationsShareOnePhysicalSlotUntilTheLastClose() throws Exception {
    Path bookPath = writeBook("nested-marker/book.sqlite");
    int activeConnectionsBeforeOpen = SqliteNativeRuntimeActivity.activeConnectionCount();
    SqliteNativeActivityRegistration first =
        SqliteNativeRuntimeActivity.recordOpeningConnection(bookPath, true);
    SqliteNativeActivityRegistration second =
        SqliteNativeRuntimeActivity.recordOpeningConnection(bookPath, true);
    try {
      assertTrue(first.publishesActivityMarker());
      assertTrue(second.publishesActivityMarker());
      assertEquals(
          activeConnectionsBeforeOpen + 2, SqliteNativeRuntimeActivity.activeConnectionCount());
      assertEquals(2, SqliteNativeRuntimeActivity.activeConnectionCount(bookPath));
      assertTrue(SqliteNativeRuntimeActivity.hasExternalActiveConnections(bookPath));

      SqliteNativeRuntimeActivity.recordConnectionClosed(first);
      assertEquals(
          activeConnectionsBeforeOpen + 1, SqliteNativeRuntimeActivity.activeConnectionCount());
      assertEquals(1, SqliteNativeRuntimeActivity.activeConnectionCount(bookPath));
      assertTrue(SqliteNativeRuntimeActivity.hasExternalActiveConnections(bookPath));
    } finally {
      SqliteNativeRuntimeActivity.recordConnectionClosed(second);
    }

    assertEquals(activeConnectionsBeforeOpen, SqliteNativeRuntimeActivity.activeConnectionCount());
    assertEquals(0, SqliteNativeRuntimeActivity.activeConnectionCount(bookPath));
    assertFalse(SqliteNativeRuntimeActivity.hasExternalActiveConnections(bookPath));
    SqliteNativeRuntimeActivity.recordConnectionClosed(null);
  }

  @Test
  void closeRejectsOneRegistrationThatWasNeverOpened() throws Exception {
    Path bookPath = writeBook("missing-registration/book.sqlite");
    int activeConnectionsBeforeClose = SqliteNativeRuntimeActivity.activeConnectionCount();
    SqliteNativeActivityRegistration unregistered =
        new SqliteNativeActivityRegistration(
            bookPath, SqliteObjectCoordinationArtifacts.physicalIdentity(bookPath), null);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteNativeRuntimeActivity.recordConnectionClosed(unregistered));

    assertTrue(
        Objects.requireNonNull(exception.getMessage(), "exception message")
            .contains("registry missing the physical book identity"));
    assertEquals(activeConnectionsBeforeClose, SqliteNativeRuntimeActivity.activeConnectionCount());
  }

  private Path writeBook(String relativePath) throws java.io.IOException {
    Path bookPath = tempDirectory.resolve(relativePath).toAbsolutePath().normalize();
    Path parent = bookPath.getParent();
    if (parent == null) {
      throw new AssertionError("Book fixture requires one parent directory.");
    }
    Files.createDirectories(parent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parent);
    SqliteBookFileSecurity.createNewOwnerOnlyBookFile(bookPath);
    Files.writeString(bookPath, "book");
    return bookPath;
  }
}
