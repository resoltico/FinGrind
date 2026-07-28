package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Tests for the SQLite FFM binding layer. */
class SqliteNativeOpenFailureHandlingTest extends SqliteNativeBridgeTestSupport {
  @Test
  void closedDatabasesRejectFurtherNativeAccess() throws Exception {
    Path bookPath = createActivityBook("closed-native.sqlite");
    int activeConnectionsBeforeOpen = SqliteNativeRuntimeActivity.activeConnectionCount();
    SqliteNativeActivityRegistration activityRegistration =
        SqliteNativeRuntimeActivity.recordOpeningConnection(bookPath, false);
    try (Arena arena = Arena.ofConfined();
        SqliteNativeDatabase database =
            new SqliteNativeDatabase(
                arena.allocate(1),
                activityRegistration,
                buildSqliteApi(defaultSqliteApiArguments()))) {
      database.close();

      IllegalStateException exception = assertThrows(IllegalStateException.class, database::handle);

      assertEquals("SQLite native database handle is already closed.", exception.getMessage());
      assertDoesNotThrow(database::close);
    }
    assertEquals(activeConnectionsBeforeOpen, SqliteNativeRuntimeActivity.activeConnectionCount());
  }

  @Test
  void close_releasesTheExactActivityRegistrationForOneOpenedHandle() throws Exception {
    AtomicInteger closeCalls = new AtomicInteger();
    Path bookPath = createActivityBook("native-close-registration.sqlite");
    int activeConnectionsBeforeOpen = SqliteNativeRuntimeActivity.activeConnectionCount();
    SqliteNativeActivityRegistration activityRegistration =
        SqliteNativeRuntimeActivity.recordOpeningConnection(bookPath, true);
    assertEquals(
        activeConnectionsBeforeOpen + 1, SqliteNativeRuntimeActivity.activeConnectionCount());
    Object[] sqliteApiArguments = defaultSqliteApiArguments();
    sqliteApiArguments[SQLITE_API_ARGUMENT_CLOSE_V2] =
        MethodHandles.insertArguments(
            MethodHandles.lookup()
                .findStatic(
                    SqliteNativeBridgeTestSupport.class,
                    "recordCloseCall",
                    MethodType.methodType(int.class, AtomicInteger.class, MemorySegment.class)),
            0,
            closeCalls);
    SqliteNativeApi sqliteApi = buildSqliteApi(sqliteApiArguments);
    assertDoesNotThrow(
        () ->
            SqliteNativeConnections.close(
                MemorySegment.ofAddress(1L), activityRegistration, sqliteApi));
    assertEquals(1, closeCalls.get());
    assertEquals(activeConnectionsBeforeOpen, SqliteNativeRuntimeActivity.activeConnectionCount());
  }

  @Test
  void open_wrapsUnexpectedThrowableFromOpenInvocation() throws Exception {
    Object[] sqliteApiArguments = defaultSqliteApiArguments();
    sqliteApiArguments[1] =
        throwingMethodHandle(
            new IllegalStateException("boom"),
            int.class,
            MemorySegment.class,
            MemorySegment.class,
            int.class,
            MemorySegment.class);
    SqliteNativeApi sqliteApi = buildSqliteApi(sqliteApiArguments);
    try (SqliteBookPassphrase passphrase =
        SqliteBookPassphrase.fromCharacters("native open throwable", TEST_BOOK_KEY.toCharArray())) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteNativeConnections.open(
                      tempDirectory.resolve("open-throwable.sqlite"),
                      passphrase,
                      SqliteNativeOpenMode.READ_WRITE_CREATE,
                      sqliteApi));
      assertEquals("Failed to open the SQLite native library bridge.", exception.getMessage());
      assertEquals("boom", Objects.requireNonNull(exception.getCause()).getMessage());
    }
  }

  @Test
  void open_closesNativeHandleWhenKeyValidationFails() throws Exception {
    AtomicInteger closeCalls = new AtomicInteger();
    try (Arena arena = Arena.ofConfined();
        SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "native open validation failure", TEST_BOOK_KEY.toCharArray())) {
      MemorySegment fakeDatabaseHandle = arena.allocate(1);
      Object[] sqliteApiArguments = defaultSqliteApiArguments();
      sqliteApiArguments[SQLITE_API_ARGUMENT_OPEN_V2] =
          MethodHandles.insertArguments(
              MethodHandles.lookup()
                  .findStatic(
                      SqliteNativeBridgeTestSupport.class,
                      "openWithDatabaseHandle",
                      java.lang.invoke.MethodType.methodType(
                          int.class,
                          MemorySegment.class,
                          MemorySegment.class,
                          MemorySegment.class,
                          int.class,
                          MemorySegment.class)),
              0,
              fakeDatabaseHandle);
      sqliteApiArguments[SQLITE_API_ARGUMENT_CLOSE_V2] =
          MethodHandles.insertArguments(
              MethodHandles.lookup()
                  .findStatic(
                      SqliteNativeBridgeTestSupport.class,
                      "recordCloseCall",
                      java.lang.invoke.MethodType.methodType(
                          int.class, AtomicInteger.class, MemorySegment.class)),
              0,
              closeCalls);
      sqliteApiArguments[SQLITE_API_ARGUMENT_EXEC] =
          constantMethodHandle(
              26,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class);
      sqliteApiArguments[SQLITE_API_ARGUMENT_ERRSTR] =
          constantMethodHandle(arena.allocateFrom("file is not a database"), int.class);
      SqliteNativeApi sqliteApi = buildSqliteApi(sqliteApiArguments);
      SqliteNativeException exception =
          assertThrows(
              SqliteNativeException.class,
              () ->
                  SqliteNativeConnections.open(
                      tempDirectory.resolve("open-validation-failure.sqlite"),
                      passphrase,
                      SqliteNativeOpenMode.READ_WRITE_CREATE,
                      sqliteApi));
      assertEquals("SQLITE_NOTADB", exception.resultName());
      assertEquals(1, closeCalls.get());
    }
  }

  @Test
  void configureOpenedDatabase_rethrowsErrorsAndClosesNativeHandle() throws Exception {
    AtomicInteger closeCalls = new AtomicInteger();
    try (Arena arena = Arena.ofConfined();
        SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "configure-opened-error", TEST_BOOK_KEY.toCharArray())) {
      MemorySegment fakeDatabaseHandle = arena.allocate(1);
      Object[] sqliteApiArguments = defaultSqliteApiArguments();
      sqliteApiArguments[SQLITE_API_ARGUMENT_CLOSE_V2] =
          MethodHandles.insertArguments(
              MethodHandles.lookup()
                  .findStatic(
                      SqliteNativeBridgeTestSupport.class,
                      "recordCloseCall",
                      java.lang.invoke.MethodType.methodType(
                          int.class, AtomicInteger.class, MemorySegment.class)),
              0,
              closeCalls);
      sqliteApiArguments[SQLITE_API_ARGUMENT_BUSY_TIMEOUT] =
          throwingMethodHandle(
              new AssertionError("boom"), int.class, MemorySegment.class, int.class);
      SqliteNativeApi sqliteApi = buildSqliteApi(sqliteApiArguments);
      AssertionError exception =
          assertThrows(
              AssertionError.class,
              () ->
                  SqliteNativeKeyConfiguration.configureOpenedDatabase(
                      fakeDatabaseHandle, passphrase, null, sqliteApi, arena));
      assertEquals("boom", exception.getMessage());
      assertEquals(1, closeCalls.get());
    }
  }

  @Test
  void configureOpenedDatabase_addsSuppressedCloseFailureWhenCleanupCloseReturnsNonOk()
      throws Exception {
    try (Arena arena = Arena.ofConfined();
        SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "configure-opened-close-failure", TEST_BOOK_KEY.toCharArray())) {
      MemorySegment fakeDatabaseHandle = arena.allocate(1);
      Object[] sqliteApiArguments = defaultSqliteApiArguments();
      sqliteApiArguments[SQLITE_API_ARGUMENT_CLOSE_V2] =
          constantMethodHandle(14, MemorySegment.class);
      sqliteApiArguments[SQLITE_API_ARGUMENT_BUSY_TIMEOUT] =
          throwingMethodHandle(
              new IllegalStateException("busy-timeout boom"),
              int.class,
              MemorySegment.class,
              int.class);
      SqliteNativeApi sqliteApi = buildSqliteApi(sqliteApiArguments);
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteNativeKeyConfiguration.configureOpenedDatabase(
                      fakeDatabaseHandle, passphrase, null, sqliteApi, arena));
      assertEquals("Failed to open the SQLite native library bridge.", exception.getMessage());
      assertEquals("busy-timeout boom", Objects.requireNonNull(exception.getCause()).getMessage());
      assertEquals(1, exception.getSuppressed().length);
    }
  }

  @Test
  void configureOpenedDatabase_cleanupCloseSkipsNullHandle() throws Throwable {
    RuntimeException primaryFailure = new RuntimeException("primary failure");

    invokeKeyConfigurationSuppressCloseFailure(
        MemorySegment.NULL, SqliteNativeBootstrap.api(), primaryFailure);

    assertEquals(0, primaryFailure.getSuppressed().length);
  }

  @Test
  void configureOpenedDatabase_addsSuppressedCloseFailureWhenCleanupCloseThrows() throws Throwable {
    SqliteNativeApi sqliteApi =
        SqliteNativeApiTestSupport.withCloseV2(
            SqliteNativeBootstrap.api(),
            throwingMethodHandle(
                new IllegalStateException("close boom"), int.class, MemorySegment.class));
    RuntimeException primaryFailure = new RuntimeException("primary failure");

    invokeKeyConfigurationSuppressCloseFailure(
        MemorySegment.ofAddress(1L), sqliteApi, primaryFailure);

    assertEquals(1, primaryFailure.getSuppressed().length);
    assertEquals("close boom", primaryFailure.getSuppressed()[0].getMessage());
  }

  @Test
  void prepareExistingLiveBook_validatesOwnerOnlyAclWithoutRepairingThePathname() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      parentPath.exists = true;
      parentPath.regularFile = false;
      ownerOnlyDirectoryAcl(fileSystem, parentPath);
      AclFixturePath bookPath = fileSystem.path("\\books\\book.sqlite");
      bookPath.exists = true;
      bookPath.regularFile = true;
      bookPath.overrideAclView = ownerOnlyBookAclWithForbiddenRepair(fileSystem);

      assertEquals(
          SqliteNativeOpenMode.READ_WRITE_EXISTING.flags(),
          SqliteNativeConnections.prepareBookPathForNativeOpen(
              bookPath.toAbsolutePath().normalize(), SqliteNativeOpenMode.READ_WRITE_EXISTING));
    }
  }

  @Test
  void prepareBookPathPreservesParentBookAndCreationIoFailures() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.overrideAclView = failingAclView();
      AclFixturePath bookPath = fileSystem.path("\\books\\book.sqlite");

      SqliteStorageFailureException parentFailure =
          assertThrows(
              SqliteStorageFailureException.class,
              () ->
                  SqliteNativeConnections.prepareBookPathForNativeOpen(
                      bookPath.toAbsolutePath().normalize(),
                      SqliteNativeOpenMode.READ_WRITE_EXISTING));
      assertEquals(
          "parent metadata failure",
          Objects.requireNonNull(parentFailure.getCause(), "parent failure cause").getMessage());
    }

    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      parentPath.exists = true;
      parentPath.regularFile = false;
      ownerOnlyDirectoryAcl(fileSystem, parentPath);
      AclFixturePath bookPath = fileSystem.path("\\books\\book.sqlite");
      bookPath.exists = true;
      bookPath.regularFile = true;
      bookPath.overrideAclView = failingAclView();

      SqliteStorageFailureException bookFailure =
          assertThrows(
              SqliteStorageFailureException.class,
              () ->
                  SqliteNativeConnections.prepareBookPathForNativeOpen(
                      bookPath.toAbsolutePath().normalize(),
                      SqliteNativeOpenMode.READ_WRITE_EXISTING_STAGE));
      assertEquals(
          "parent metadata failure",
          Objects.requireNonNull(bookFailure.getCause(), "book failure cause").getMessage());
    }

    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.posixPermissions =
          Set.of(
              java.nio.file.attribute.PosixFilePermission.OWNER_READ,
              java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
              java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE);
      AclFixturePath createPath = fileSystem.path("\\books\\create.sqlite");
      createPath.failNewByteChannelWith(new IOException("create failure"));
      AclFixturePath exclusivePath = fileSystem.path("\\books\\exclusive.sqlite");
      exclusivePath.failNewByteChannelWith(new IOException("exclusive creation failure"));

      SqliteStorageFailureException createFailure =
          assertThrows(
              SqliteStorageFailureException.class,
              () ->
                  SqliteNativeConnections.prepareBookPathForNativeOpen(
                      createPath.toAbsolutePath().normalize(), SqliteNativeOpenMode.READ_WRITE_CREATE));
      assertEquals(
          "create failure",
          Objects.requireNonNull(createFailure.getCause(), "create failure cause").getMessage());
      SqliteStorageFailureException exclusiveFailure =
          assertThrows(
              SqliteStorageFailureException.class,
              () ->
                  SqliteNativeConnections.prepareBookPathForNativeOpen(
                      exclusivePath.toAbsolutePath().normalize(),
                      SqliteNativeOpenMode.READ_WRITE_CREATE_EXCLUSIVE));
      assertEquals(
          "exclusive creation failure",
          Objects.requireNonNull(exclusiveFailure.getCause(), "exclusive failure cause").getMessage());
    }
  }

  @Test
  void prepareNewLiveBook_claimsAnExactPrivateFileAndThenUsesExistingOpenFlags() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(
        tempDirectory.getFileSystem().supportedFileAttributeViews().contains("posix"));
    Path privateParent = tempDirectory.resolve("private-live-book-parent");
    java.nio.file.Files.createDirectory(
        privateParent,
        java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
            Set.of(
                java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE)));
    Path bookPath = privateParent.resolve("atomic-live-book.sqlite");

    assertEquals(
        SqliteNativeOpenMode.READ_WRITE_EXISTING.flags(),
        SqliteNativeConnections.prepareBookPathForNativeOpen(
            bookPath, SqliteNativeOpenMode.READ_WRITE_CREATE_EXCLUSIVE));
    assertEquals(
        Set.of(
            java.nio.file.attribute.PosixFilePermission.OWNER_READ,
            java.nio.file.attribute.PosixFilePermission.OWNER_WRITE),
        java.nio.file.Files.getPosixFilePermissions(bookPath));

    SqliteNewBookDestinationOccupiedException collision =
        assertThrows(
            SqliteNewBookDestinationOccupiedException.class,
            () ->
                SqliteNativeConnections.prepareBookPathForNativeOpen(
                    bookPath, SqliteNativeOpenMode.READ_WRITE_CREATE_EXCLUSIVE));
    assertEquals(bookPath, collision.targetPath());
  }

  @Test
  void prepareNewLiveBook_refusesAclOnlyCreationInsteadOfRepairingAnAcl() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      parentPath.exists = true;
      parentPath.regularFile = false;
      ownerOnlyDirectoryAcl(fileSystem, parentPath);
      AclFixturePath bookPath = fileSystem.path("\\books\\new-book.sqlite");

      SqliteCallerPathContractException failure =
          assertThrows(
              SqliteCallerPathContractException.class,
              () ->
                  SqliteNativeConnections.prepareBookPathForNativeOpen(
                      bookPath.toAbsolutePath().normalize(),
                      SqliteNativeOpenMode.READ_WRITE_CREATE));

      assertEquals(
          SqliteCallerPathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED,
          failure.pathFailure());
      assertFalse(bookPath.exists);
    }
  }

  @Test
  void open_closesNativeHandleWhenConfigurationThrowsUnexpectedly() throws Exception {
    AtomicInteger closeCalls = new AtomicInteger();
    try (Arena arena = Arena.ofConfined();
        SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "native open configuration failure", TEST_BOOK_KEY.toCharArray())) {
      MemorySegment fakeDatabaseHandle = arena.allocate(1);
      Object[] sqliteApiArguments = defaultSqliteApiArguments();
      sqliteApiArguments[SQLITE_API_ARGUMENT_OPEN_V2] =
          MethodHandles.insertArguments(
              MethodHandles.lookup()
                  .findStatic(
                      SqliteNativeBridgeTestSupport.class,
                      "openWithDatabaseHandle",
                      java.lang.invoke.MethodType.methodType(
                          int.class,
                          MemorySegment.class,
                          MemorySegment.class,
                          MemorySegment.class,
                          int.class,
                          MemorySegment.class)),
              0,
              fakeDatabaseHandle);
      sqliteApiArguments[SQLITE_API_ARGUMENT_CLOSE_V2] =
          MethodHandles.insertArguments(
              MethodHandles.lookup()
                  .findStatic(
                      SqliteNativeBridgeTestSupport.class,
                      "recordCloseCall",
                      java.lang.invoke.MethodType.methodType(
                          int.class, AtomicInteger.class, MemorySegment.class)),
              0,
              closeCalls);
      sqliteApiArguments[SQLITE_API_ARGUMENT_BUSY_TIMEOUT] =
          throwingMethodHandle(
              new IllegalStateException("busy-timeout boom"),
              int.class,
              MemorySegment.class,
              int.class);
      SqliteNativeApi sqliteApi = buildSqliteApi(sqliteApiArguments);
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteNativeConnections.open(
                      tempDirectory.resolve("open-configuration-failure.sqlite"),
                      passphrase,
                      SqliteNativeOpenMode.READ_WRITE_CREATE,
                      sqliteApi));
      assertEquals("Failed to open the SQLite native library bridge.", exception.getMessage());
      assertEquals("busy-timeout boom", Objects.requireNonNull(exception.getCause()).getMessage());
      assertEquals(1, closeCalls.get());
    }
  }

  @Test
  void open_preservesNativeOpenFailureWhenCleanupCloseThrows() throws Exception {
    AtomicInteger closeCalls = new AtomicInteger();
    try (Arena arena = Arena.ofConfined();
        SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "native open cleanup failure", TEST_BOOK_KEY.toCharArray())) {
      MemorySegment fakeDatabaseHandle = arena.allocate(1);
      Object[] sqliteApiArguments = defaultSqliteApiArguments();
      sqliteApiArguments[SQLITE_API_ARGUMENT_OPEN_V2] =
          MethodHandles.insertArguments(
              MethodHandles.lookup()
                  .findStatic(
                      SqliteNativeBridgeTestSupport.class,
                      "failOpenWithDatabaseHandle",
                      java.lang.invoke.MethodType.methodType(
                          int.class,
                          MemorySegment.class,
                          MemorySegment.class,
                          MemorySegment.class,
                          int.class,
                          MemorySegment.class)),
              0,
              fakeDatabaseHandle);
      sqliteApiArguments[SQLITE_API_ARGUMENT_CLOSE_V2] =
          MethodHandles.insertArguments(
              MethodHandles.lookup()
                  .findStatic(
                      SqliteNativeBridgeTestSupport.class,
                      "recordCloseCallThenThrow",
                      java.lang.invoke.MethodType.methodType(
                          int.class, AtomicInteger.class, MemorySegment.class)),
              0,
              closeCalls);
      sqliteApiArguments[SQLITE_API_ARGUMENT_ERRSTR] =
          constantMethodHandle(arena.allocateFrom("open boom"), int.class);
      SqliteNativeApi sqliteApi = buildSqliteApi(sqliteApiArguments);
      SqliteNativeException exception =
          assertThrows(
              SqliteNativeException.class,
              () ->
                  SqliteNativeConnections.open(
                      tempDirectory.resolve("open-native-failure.sqlite"),
                      passphrase,
                      SqliteNativeOpenMode.READ_WRITE_CREATE,
                      sqliteApi));
      assertEquals("SQLITE_CANTOPEN: open boom", exception.getMessage());
      assertEquals(1, closeCalls.get());
    }
  }

  @Test
  void open_preservesNativeOpenFailureWhenCleanupCloseSucceeds() throws Exception {
    AtomicInteger closeCalls = new AtomicInteger();
    int activeConnectionsBeforeOpen = SqliteNativeRuntimeActivity.activeConnectionCount();
    try (Arena arena = Arena.ofConfined();
        SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "native open cleanup close success", TEST_BOOK_KEY.toCharArray())) {
      MemorySegment fakeDatabaseHandle = arena.allocate(1);
      Object[] sqliteApiArguments = defaultSqliteApiArguments();
      sqliteApiArguments[SQLITE_API_ARGUMENT_OPEN_V2] =
          MethodHandles.insertArguments(
              MethodHandles.lookup()
                  .findStatic(
                      SqliteNativeBridgeTestSupport.class,
                      "failOpenWithDatabaseHandle",
                      java.lang.invoke.MethodType.methodType(
                          int.class,
                          MemorySegment.class,
                          MemorySegment.class,
                          MemorySegment.class,
                          int.class,
                          MemorySegment.class)),
              0,
              fakeDatabaseHandle);
      sqliteApiArguments[SQLITE_API_ARGUMENT_CLOSE_V2] =
          MethodHandles.insertArguments(
              MethodHandles.lookup()
                  .findStatic(
                      SqliteNativeBridgeTestSupport.class,
                      "recordCloseCall",
                      java.lang.invoke.MethodType.methodType(
                          int.class, AtomicInteger.class, MemorySegment.class)),
              0,
              closeCalls);
      sqliteApiArguments[SQLITE_API_ARGUMENT_ERRSTR] =
          constantMethodHandle(arena.allocateFrom("open boom"), int.class);
      SqliteNativeApi sqliteApi = buildSqliteApi(sqliteApiArguments);
      SqliteNativeException exception =
          assertThrows(
              SqliteNativeException.class,
              () ->
                  SqliteNativeConnections.open(
                      tempDirectory.resolve("open-native-failure-cleanup-close-success.sqlite"),
                      passphrase,
                      SqliteNativeOpenMode.READ_WRITE_CREATE,
                      sqliteApi));
      assertEquals("SQLITE_CANTOPEN: open boom", exception.getMessage());
      assertEquals(1, closeCalls.get());
      assertEquals(0, exception.getSuppressed().length);
      assertEquals(
          activeConnectionsBeforeOpen, SqliteNativeRuntimeActivity.activeConnectionCount());
    }
  }

  @Test
  void open_cleanupCloseClosesReturnedHandleWhenCloseSucceeds() throws Throwable {
    AtomicInteger closeCalls = new AtomicInteger();
    SqliteNativeApi sqliteApi =
        SqliteNativeApiTestSupport.withCloseV2(
            SqliteNativeBootstrap.api(),
            MethodHandles.insertArguments(
                MethodHandles.lookup()
                    .findStatic(
                        SqliteNativeBridgeTestSupport.class,
                        "recordCloseCall",
                        MethodType.methodType(int.class, AtomicInteger.class, MemorySegment.class)),
                0,
                closeCalls));
    RuntimeException primaryFailure = new RuntimeException("primary failure");

    invokeConnectionSuppressCloseFailure(MemorySegment.ofAddress(1L), sqliteApi, primaryFailure);

    assertEquals(1, closeCalls.get());
    assertEquals(0, primaryFailure.getSuppressed().length);
  }

  @Test
  void open_addsSuppressedCleanupCloseFailureWhenCleanupCloseThrows() throws Throwable {
    AtomicInteger closeCalls = new AtomicInteger();
    SqliteNativeApi sqliteApi =
        SqliteNativeApiTestSupport.withCloseV2(
            SqliteNativeBootstrap.api(),
            MethodHandles.insertArguments(
                MethodHandles.lookup()
                    .findStatic(
                        SqliteNativeBridgeTestSupport.class,
                        "recordCloseCallThenThrow",
                        MethodType.methodType(int.class, AtomicInteger.class, MemorySegment.class)),
                0,
                closeCalls));
    RuntimeException primaryFailure = new RuntimeException("primary failure");

    invokeConnectionSuppressCloseFailure(MemorySegment.ofAddress(1L), sqliteApi, primaryFailure);

    assertEquals(1, closeCalls.get());
    assertEquals(1, primaryFailure.getSuppressed().length);
    assertEquals(
        "Failed to close the SQLite native library bridge.",
        primaryFailure.getSuppressed()[0].getMessage());
    assertEquals(
        "close boom for handle",
        Objects.requireNonNull(primaryFailure.getSuppressed()[0].getCause()).getMessage());
  }

  @Test
  void open_preservesNativeOpenFailureWhenNoHandleIsReturned() throws Exception {
    AtomicInteger closeCalls = new AtomicInteger();
    try (Arena arena = Arena.ofConfined();
        SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "native open null handle failure", TEST_BOOK_KEY.toCharArray())) {
      Object[] sqliteApiArguments = defaultSqliteApiArguments();
      sqliteApiArguments[SQLITE_API_ARGUMENT_OPEN_V2] =
          constantMethodHandle(
              14, MemorySegment.class, MemorySegment.class, int.class, MemorySegment.class);
      sqliteApiArguments[SQLITE_API_ARGUMENT_CLOSE_V2] =
          MethodHandles.insertArguments(
              MethodHandles.lookup()
                  .findStatic(
                      SqliteNativeBridgeTestSupport.class,
                      "recordCloseCall",
                      java.lang.invoke.MethodType.methodType(
                          int.class, AtomicInteger.class, MemorySegment.class)),
              0,
              closeCalls);
      sqliteApiArguments[SQLITE_API_ARGUMENT_ERRSTR] =
          constantMethodHandle(arena.allocateFrom("open boom"), int.class);
      SqliteNativeApi sqliteApi = buildSqliteApi(sqliteApiArguments);
      SqliteNativeException exception =
          assertThrows(
              SqliteNativeException.class,
              () ->
                  SqliteNativeConnections.open(
                      tempDirectory.resolve("open-no-handle-failure.sqlite"),
                      passphrase,
                      SqliteNativeOpenMode.READ_WRITE_CREATE,
                      sqliteApi));
      assertEquals("SQLITE_CANTOPEN: open boom", exception.getMessage());
      assertEquals(0, closeCalls.get());
    }
  }

  @Test
  void requireOpenConfigurationSuccess_throwsSqliteFailureForNonOkResult() throws Exception {
    try (Arena arena = Arena.ofConfined()) {
      SqliteNativeApi sqliteApi =
          sqliteApi(
              constantMethodHandle(0, MemorySegment.class),
              constantMethodHandle(arena.allocateFrom("boom"), MemorySegment.class),
              constantMethodHandle(arena.allocateFrom("boom"), int.class),
              constantMethodHandle(14, MemorySegment.class));
      SqliteNativeException sqliteException =
          assertThrows(
              SqliteNativeException.class,
              () -> SqliteNativeKeyConfiguration.requireOpenConfigurationSuccess(14, sqliteApi));
      assertEquals(14, sqliteException.resultCode());
      assertEquals("SQLITE_CANTOPEN", sqliteException.resultName());
      assertEquals("SQLITE_CANTOPEN: boom", sqliteException.getMessage());
    }
  }

  @Test
  void requireOpenConfigurationSuccess_preservesNativeFailureMessage() throws Exception {
    try (Arena arena = Arena.ofConfined()) {
      SqliteNativeApi sqliteApi =
          sqliteApi(
              constantMethodHandle(0, MemorySegment.class),
              constantMethodHandle(arena.allocateFrom("boom"), MemorySegment.class),
              constantMethodHandle(arena.allocateFrom("boom"), int.class),
              constantMethodHandle(14, MemorySegment.class));
      SqliteNativeException exception =
          assertThrows(
              SqliteNativeException.class,
              () -> SqliteNativeKeyConfiguration.requireOpenConfigurationSuccess(14, sqliteApi));
      assertEquals("SQLITE_CANTOPEN: boom", exception.getMessage());
    }
  }

  @Test
  void requireOpenConfigurationSuccess_usesResultNameWhenErrorStringIsBlank() throws Exception {
    try (Arena arena = Arena.ofConfined()) {
      SqliteNativeApi sqliteApi =
          sqliteApi(
              constantMethodHandle(0, MemorySegment.class),
              constantMethodHandle(arena.allocateFrom("unused"), MemorySegment.class),
              constantMethodHandle(arena.allocateFrom(""), int.class),
              constantMethodHandle(14, MemorySegment.class));
      SqliteNativeException exception =
          assertThrows(
              SqliteNativeException.class,
              () -> SqliteNativeKeyConfiguration.requireOpenConfigurationSuccess(14, sqliteApi));
      assertEquals("SQLITE_CANTOPEN", exception.getMessage());
    }
  }

  @Test
  void requireOpenConfigurationSuccess_acceptsOkResult() {
    assertDoesNotThrow(
        () ->
            SqliteNativeKeyConfiguration.requireOpenConfigurationSuccess(
                SqliteNativeResultCode.code("OK"), SqliteNativeBootstrap.api()));
  }

  private Path createActivityBook(String fileName) throws java.io.IOException {
    Path bookPath = tempDirectory.resolve(fileName).toAbsolutePath().normalize();
    SqliteBookFileSecurity.createNewOwnerOnlyBookFile(bookPath);
    java.nio.file.Files.writeString(bookPath, "book");
    return bookPath;
  }

  private static void invokeConnectionSuppressCloseFailure(
      MemorySegment databaseHandle, SqliteNativeApi sqliteApi, Throwable primaryFailure)
      throws Throwable {
    MethodHandles.Lookup lookup =
        MethodHandles.privateLookupIn(SqliteNativeConnections.class, MethodHandles.lookup());
    lookup
        .findStatic(
            SqliteNativeConnections.class,
            "suppressCloseFailure",
            MethodType.methodType(
                void.class, MemorySegment.class, SqliteNativeApi.class, Throwable.class))
        .invokeExact(databaseHandle, sqliteApi, primaryFailure);
  }

  private static void invokeKeyConfigurationSuppressCloseFailure(
      MemorySegment databaseHandle, SqliteNativeApi sqliteApi, Throwable primaryFailure)
      throws Throwable {
    MethodHandles.Lookup lookup =
        MethodHandles.privateLookupIn(SqliteNativeKeyConfiguration.class, MethodHandles.lookup());
    lookup
        .findStatic(
            SqliteNativeKeyConfiguration.class,
            "suppressCloseFailure",
            MethodType.methodType(
                void.class, MemorySegment.class, SqliteNativeApi.class, Throwable.class))
        .invokeExact(databaseHandle, sqliteApi, primaryFailure);
  }

  private static void ownerOnlyDirectoryAcl(
      AclFixtureFileSystem fileSystem, AclFixturePath parentPath) {
    Objects.requireNonNull(parentPath.aclView)
        .setAcl(
            List.of(
                AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(fileSystem.owner)
                    .setPermissions(
                        AclEntryPermission.LIST_DIRECTORY,
                        AclEntryPermission.ADD_FILE,
                        AclEntryPermission.EXECUTE)
                    .build()));
  }

  private static AclFileAttributeView failingAclView() {
    return new AclFileAttributeView() {
      @Override
      public String name() {
        return "acl";
      }

      @Override
      public List<AclEntry> getAcl() throws IOException {
        throw new IOException("parent metadata failure");
      }

      @Override
      public void setAcl(List<AclEntry> acl) throws IOException {
        throw new IOException("parent metadata failure");
      }

      @Override
      public UserPrincipal getOwner() throws IOException {
        throw new IOException("parent metadata failure");
      }

      @Override
      public void setOwner(UserPrincipal owner) throws IOException {
        throw new IOException("parent metadata failure");
      }
    };
  }

  private static AclFileAttributeView ownerOnlyBookAclWithForbiddenRepair(
      AclFixtureFileSystem fileSystem) {
    List<AclEntry> ownerOnlyAcl =
        List.of(
            AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(fileSystem.owner)
                .setPermissions(
                    AclEntryPermission.READ_DATA,
                    AclEntryPermission.WRITE_DATA,
                    AclEntryPermission.APPEND_DATA,
                    AclEntryPermission.READ_NAMED_ATTRS,
                    AclEntryPermission.WRITE_NAMED_ATTRS,
                    AclEntryPermission.READ_ATTRIBUTES,
                    AclEntryPermission.WRITE_ATTRIBUTES,
                    AclEntryPermission.DELETE,
                    AclEntryPermission.READ_ACL,
                    AclEntryPermission.SYNCHRONIZE)
                .build());
    return new AclFileAttributeView() {
      @Override
      public String name() {
        return "acl";
      }

      @Override
      public List<AclEntry> getAcl() {
        return ownerOnlyAcl;
      }

      @Override
      public void setAcl(List<AclEntry> acl) throws java.io.IOException {
        throw new java.io.IOException("pathname ACL repair is forbidden");
      }

      @Override
      public UserPrincipal getOwner() {
        return fileSystem.owner;
      }

      @Override
      public void setOwner(UserPrincipal ownerPrincipal) {
        throw new UnsupportedOperationException();
      }
    };
  }
}
