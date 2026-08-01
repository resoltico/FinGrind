package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
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
  void tokenConstructionFailureRollsBackBothNativeActivityCounters() throws Exception {
    Path bookPath = writeBook("token-construction-failure/book.sqlite");
    int processCountBefore = SqliteNativeRuntimeActivity.activeConnectionCount();

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteNativeConnectionActivityRegistry.recordOpeningConnection(
                    bookPath,
                    false,
                    (diagnosticBookPath, objectIdentity, activityRegistration) -> {
                      throw new IllegalStateException("injected token construction failure");
                    }));

    assertEquals("injected token construction failure", failure.getMessage());
    assertEquals(processCountBefore, SqliteNativeRuntimeActivity.activeConnectionCount());
    assertEquals(0, SqliteNativeRuntimeActivity.activeConnectionCount(bookPath));
  }

  @Test
  void tokenConstructionFailureRetainsAnAlreadyOpenedObjectsActivityCount() throws Exception {
    Path bookPath = writeBook("retained-object-count/book.sqlite");
    int processCountBefore = SqliteNativeRuntimeActivity.activeConnectionCount();
    SqliteNativeActivityRegistration opened =
        SqliteNativeRuntimeActivity.recordOpeningConnection(bookPath, false);

    try {
      assertThrows(
          IllegalStateException.class,
          () ->
              SqliteNativeConnectionActivityRegistry.recordOpeningConnection(
                  bookPath,
                  false,
                  (diagnosticBookPath, objectIdentity, activityRegistration) -> {
                    throw new IllegalStateException("injected token construction failure");
                  }));

      assertEquals(processCountBefore + 1, SqliteNativeRuntimeActivity.activeConnectionCount());
      assertEquals(1, SqliteNativeRuntimeActivity.activeConnectionCount(bookPath));
    } finally {
      SqliteNativeRuntimeActivity.recordConnectionClosed(opened);
    }

    assertEquals(processCountBefore, SqliteNativeRuntimeActivity.activeConnectionCount());
    assertEquals(0, SqliteNativeRuntimeActivity.activeConnectionCount(bookPath));
  }

  @Test
  void tokenConstructionFailureAlsoReleasesItsAcquiredActivityMarker() throws Exception {
    Path bookPath = writeBook("marker-token-construction-failure/book.sqlite");
    int processCountBefore = SqliteNativeRuntimeActivity.activeConnectionCount();

    assertThrows(
        IllegalStateException.class,
        () ->
            SqliteNativeConnectionActivityRegistry.recordOpeningConnection(
                bookPath,
                true,
                (diagnosticBookPath, objectIdentity, activityRegistration) -> {
                  throw new IllegalStateException("injected marker token construction failure");
                }));

    assertEquals(processCountBefore, SqliteNativeRuntimeActivity.activeConnectionCount());
    assertEquals(0, SqliteNativeRuntimeActivity.activeConnectionCount(bookPath));
    assertFalse(SqliteNativeRuntimeActivity.hasExternalActiveConnections(bookPath));
  }

  @Test
  void markerTokenMustRetainTheExactBorrowedRegistrationBeforeItCanBecomeActive() throws Exception {
    Path bookPath = writeBook("marker-token-mismatch/book.sqlite");
    int processCountBefore = SqliteNativeRuntimeActivity.activeConnectionCount();
    AtomicReference<SqliteNativeActivityRegistration> issuedRegistration = new AtomicReference<>();

    IllegalStateException mismatch =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteNativeConnectionActivityRegistry.recordOpeningConnection(
                    bookPath,
                    true,
                    (diagnosticBookPath, objectIdentity, ignoredBorrowedMarker) -> {
                      SqliteNativeActivityRegistration tokenWithoutBorrowedMarker =
                          new SqliteNativeActivityRegistration(
                              diagnosticBookPath, objectIdentity, null);
                      issuedRegistration.set(tokenWithoutBorrowedMarker);
                      return tokenWithoutBorrowedMarker;
                    }));

    assertEquals(
        "The issued SQLite native-connection registration did not retain its borrowed activity marker.",
        mismatch.getMessage());
    assertEquals(processCountBefore, SqliteNativeRuntimeActivity.activeConnectionCount());
    assertEquals(0, SqliteNativeRuntimeActivity.activeConnectionCount(bookPath));
    assertFalse(SqliteNativeRuntimeActivity.hasExternalActiveConnections(bookPath));
    IllegalStateException removedRegistration =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteNativeRuntimeActivity.recordConnectionClosed(
                    Objects.requireNonNull(issuedRegistration.get(), "issued registration")));
    assertTrue(
        Objects.requireNonNull(removedRegistration.getMessage(), "removed-registration message")
            .contains("was not issued"));
  }

  @Test
  void registryRejectsAnAlreadyIssuedTokenWithoutConsumingItsActivityOrCounters() throws Exception {
    Path bookPath = writeBook("duplicate-issued-token/book.sqlite");
    int processCountBefore = SqliteNativeRuntimeActivity.activeConnectionCount();
    SqliteNativeActivityRegistration first =
        SqliteNativeRuntimeActivity.recordOpeningConnection(bookPath, true);

    try {
      IllegalStateException duplicateIssuedToken =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteNativeConnectionActivityRegistry.recordOpeningConnection(
                      bookPath,
                      true,
                      (diagnosticBookPath, objectIdentity, borrowedMarker) -> first));

      assertEquals(
          "The SQLite native-connection registry rejected a newly issued registration.",
          duplicateIssuedToken.getMessage());
      assertEquals(processCountBefore + 1, SqliteNativeRuntimeActivity.activeConnectionCount());
      assertEquals(1, SqliteNativeRuntimeActivity.activeConnectionCount(bookPath));
      assertTrue(SqliteNativeRuntimeActivity.hasExternalActiveConnections(bookPath));
    } finally {
      SqliteNativeRuntimeActivity.recordConnectionClosed(first);
    }

    assertEquals(processCountBefore, SqliteNativeRuntimeActivity.activeConnectionCount());
    assertFalse(SqliteNativeRuntimeActivity.hasExternalActiveConnections(bookPath));
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
            .contains("was not issued"));
    assertEquals(activeConnectionsBeforeClose, SqliteNativeRuntimeActivity.activeConnectionCount());
  }

  @Test
  void anUnissuedRegistrationCannotConsumeOneOpenedConnectionsActivityCount() throws Exception {
    Path bookPath = writeBook("forged-registration/book.sqlite");
    int activeConnectionsBeforeOpen = SqliteNativeRuntimeActivity.activeConnectionCount();
    SqliteNativeActivityRegistration opened =
        SqliteNativeRuntimeActivity.recordOpeningConnection(bookPath, false);
    SqliteNativeActivityRegistration forged =
        new SqliteNativeActivityRegistration(
            bookPath, SqliteObjectCoordinationArtifacts.physicalIdentity(bookPath), null);

    try {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteNativeRuntimeActivity.recordConnectionClosed(forged));

      assertTrue(
          Objects.requireNonNull(exception.getMessage(), "exception message")
              .contains("was not issued"));
      assertEquals(
          activeConnectionsBeforeOpen + 1, SqliteNativeRuntimeActivity.activeConnectionCount());
      assertEquals(1, SqliteNativeRuntimeActivity.activeConnectionCount(bookPath));
    } finally {
      SqliteNativeRuntimeActivity.recordConnectionClosed(opened);
    }

    assertEquals(activeConnectionsBeforeOpen, SqliteNativeRuntimeActivity.activeConnectionCount());
    assertEquals(0, SqliteNativeRuntimeActivity.activeConnectionCount(bookPath));
  }

  @Test
  void closingOneRegistrationTwiceCannotConsumeAnotherConnectionsActivityCount() throws Exception {
    Path bookPath = writeBook("duplicate-close/book.sqlite");
    int activeConnectionsBeforeOpen = SqliteNativeRuntimeActivity.activeConnectionCount();
    SqliteNativeActivityRegistration first =
        SqliteNativeRuntimeActivity.recordOpeningConnection(bookPath, false);
    SqliteNativeActivityRegistration second =
        SqliteNativeRuntimeActivity.recordOpeningConnection(bookPath, false);

    SqliteNativeRuntimeActivity.recordConnectionClosed(first);
    try {
      IllegalStateException duplicateClose =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteNativeRuntimeActivity.recordConnectionClosed(first));
      assertTrue(
          Objects.requireNonNull(duplicateClose.getMessage(), "duplicate-close message")
              .contains("already closed"));
      assertEquals(
          activeConnectionsBeforeOpen + 1, SqliteNativeRuntimeActivity.activeConnectionCount());
      assertEquals(1, SqliteNativeRuntimeActivity.activeConnectionCount(bookPath));
    } finally {
      SqliteNativeRuntimeActivity.recordConnectionClosed(second);
    }
    assertEquals(activeConnectionsBeforeOpen, SqliteNativeRuntimeActivity.activeConnectionCount());
    assertEquals(0, SqliteNativeRuntimeActivity.activeConnectionCount(bookPath));
  }

  @Test
  void nonPosixArtifactsCannotEnterOrQueryNativeActivityAccounting() throws Exception {
    Path archive = tempDirectory.resolve("activity-without-posix.zip");
    try (FileSystem zipFileSystem =
        FileSystems.newFileSystem(URI.create("jar:" + archive.toUri()), Map.of("create", "true"))) {
      Path artifact = Files.writeString(zipFileSystem.getPath("/book.sqlite"), "book");
      int activeConnectionsBefore = SqliteNativeRuntimeActivity.activeConnectionCount();

      SqliteCallerPathContractException openFailure =
          assertThrows(
              SqliteCallerPathContractException.class,
              () -> SqliteNativeRuntimeActivity.recordOpeningConnection(artifact, false));
      assertEquals(SqliteCallerPathFailure.PARENT_OWNER_ONLY_REQUIRED, openFailure.pathFailure());
      SqliteCallerPathContractException queryFailure =
          assertThrows(
              SqliteCallerPathContractException.class,
              () -> SqliteNativeRuntimeActivity.activeConnectionCount(artifact));
      assertEquals(SqliteCallerPathFailure.PARENT_OWNER_ONLY_REQUIRED, queryFailure.pathFailure());
      assertEquals(activeConnectionsBefore, SqliteNativeRuntimeActivity.activeConnectionCount());
    }
  }

  @Test
  void physicalIdentityIoFailuresBecomeStableNativeActivityFailuresWithoutMutatingCounters()
      throws Exception {
    Path bookPath = writeBook("identity-failure/book.sqlite");
    int activeConnectionsBefore = SqliteNativeRuntimeActivity.activeConnectionCount();
    IOException openingIdentityFailure = new IOException("opening identity failure");

    IllegalStateException openingFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteNativeConnectionActivityRegistry.recordOpeningConnection(
                    bookPath,
                    false,
                    SqliteNativeActivityRegistration::new,
                    ignored -> {
                      throw openingIdentityFailure;
                    }));

    assertSame(openingIdentityFailure, openingFailure.getCause());
    assertEquals(activeConnectionsBefore, SqliteNativeRuntimeActivity.activeConnectionCount());

    IOException queryIdentityFailure = new IOException("query identity failure");
    IllegalStateException queryFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteNativeConnectionActivityRegistry.activeConnectionCount(
                    bookPath,
                    ignored -> {
                      throw queryIdentityFailure;
                    }));

    assertSame(queryIdentityFailure, queryFailure.getCause());
    assertEquals(activeConnectionsBefore, SqliteNativeRuntimeActivity.activeConnectionCount());
  }

  @Test
  void explicitFactoryRegistersTheResolvedPhysicalIdentityAndReleasesItOnClose() throws Exception {
    Path bookPath = writeBook("explicit-factory/book.sqlite");
    Path expectedDiagnosticPath = bookPath.toAbsolutePath().normalize();
    String expectedObjectIdentity = "explicit-fixture-object-identity";
    int activeConnectionsBeforeOpen = SqliteNativeRuntimeActivity.activeConnectionCount();
    AtomicReference<Path> factoryDiagnosticPath = new AtomicReference<>();
    AtomicReference<String> factoryObjectIdentity = new AtomicReference<>();

    SqliteNativeActivityRegistration registration =
        SqliteNativeConnectionActivityRegistry.recordOpeningConnection(
            bookPath,
            false,
            (diagnosticBookPath, objectIdentity, activityRegistration) -> {
              factoryDiagnosticPath.set(diagnosticBookPath);
              factoryObjectIdentity.set(objectIdentity);
              return new SqliteNativeActivityRegistration(
                  diagnosticBookPath, objectIdentity, activityRegistration);
            },
            ignored -> expectedObjectIdentity);

    try {
      assertEquals(expectedDiagnosticPath, factoryDiagnosticPath.get());
      assertEquals(expectedObjectIdentity, factoryObjectIdentity.get());
      assertEquals(
          activeConnectionsBeforeOpen + 1, SqliteNativeRuntimeActivity.activeConnectionCount());
      assertEquals(
          1,
          SqliteNativeConnectionActivityRegistry.activeConnectionCount(
              bookPath, ignored -> expectedObjectIdentity));
    } finally {
      SqliteNativeRuntimeActivity.recordConnectionClosed(registration);
    }

    assertEquals(activeConnectionsBeforeOpen, SqliteNativeRuntimeActivity.activeConnectionCount());
    assertEquals(
        0,
        SqliteNativeConnectionActivityRegistry.activeConnectionCount(
            bookPath, ignored -> expectedObjectIdentity));
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
