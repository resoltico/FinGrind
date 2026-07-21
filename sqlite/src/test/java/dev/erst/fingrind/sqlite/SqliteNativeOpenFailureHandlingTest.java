package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
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
  void closedDatabasesRejectFurtherNativeAccess() {
    Path bookPath = tempDirectory.resolve("closed-native.sqlite").toAbsolutePath().normalize();
    int activeConnectionsBeforeOpen = SqliteNativeRuntimeActivity.activeConnectionCount();
    SqliteNativeRuntimeActivity.recordOpeningConnection(bookPath, false);
    try (Arena arena = Arena.ofConfined();
        SqliteNativeDatabase database =
            new SqliteNativeDatabase(
                arena.allocate(1), bookPath, false, buildSqliteApi(defaultSqliteApiArguments()))) {
      database.close();

      IllegalStateException exception = assertThrows(IllegalStateException.class, database::handle);

      assertEquals("SQLite native database handle is already closed.", exception.getMessage());
      assertDoesNotThrow(database::close);
    }
    assertEquals(activeConnectionsBeforeOpen, SqliteNativeRuntimeActivity.activeConnectionCount());
  }

  @Test
  void close_defaultPublishesActivityMarkerOverload_closesOneOpenedHandle() throws Exception {
    AtomicInteger closeCalls = new AtomicInteger();
    Path bookPath = tempDirectory.resolve("native-close-overload.sqlite");
    int activeConnectionsBeforeOpen = SqliteNativeRuntimeActivity.activeConnectionCount();
    SqliteNativeRuntimeActivity.recordOpeningConnection(bookPath);
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
        () -> SqliteNativeConnections.close(MemorySegment.ofAddress(1L), bookPath, sqliteApi));
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
                      tempDirectory
                          .resolve("configure-opened-error.sqlite")
                          .toAbsolutePath()
                          .normalize(),
                      fakeDatabaseHandle,
                      passphrase,
                      sqliteApi,
                      arena));
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
                      tempDirectory
                          .resolve("configure-opened-close-failure.sqlite")
                          .toAbsolutePath()
                          .normalize(),
                      fakeDatabaseHandle,
                      passphrase,
                      sqliteApi,
                      arena));
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
  void hardenOpenedDatabase_addsSuppressedCloseFailureWhenHardeningFails() throws Exception {
    AtomicInteger closeCalls = new AtomicInteger();
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"));
        Arena arena = Arena.ofConfined();
        SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "configure-and-harden-close-failure", TEST_BOOK_KEY.toCharArray())) {
      AclFixturePath bookPath = fileSystem.path("\\books\\book.sqlite");
      SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath);
      bookPath.exists = true;
      bookPath.regularFile = true;
      bookPath.overrideAclView = throwingAclView("book-harden-boom");
      MemorySegment fakeDatabaseHandle = arena.allocate(1);
      Object[] sqliteApiArguments = defaultSqliteApiArguments();
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
      SqliteNativeApi sqliteApi = buildSqliteApi(sqliteApiArguments);

      SqliteStorageFailureException exception =
          assertThrows(
              SqliteStorageFailureException.class,
              () ->
                  hardenOpenedDatabase(
                      bookPath.toAbsolutePath().normalize(),
                      SqliteNativeKeyConfiguration.configureOpenedDatabase(
                          bookPath.toAbsolutePath().normalize(),
                          fakeDatabaseHandle,
                          passphrase,
                          sqliteApi,
                          arena),
                      SqliteNativeOpenMode.READ_WRITE_CREATE));

      assertEquals(
          "Failed to enforce the FinGrind SQLite book file permissions.", exception.getMessage());
      assertEquals("book-harden-boom", Objects.requireNonNull(exception.getCause()).getMessage());
      assertEquals(1, closeCalls.get());
      assertEquals(1, exception.getSuppressed().length);
      assertEquals(
          "Failed to close the SQLite native library bridge.",
          exception.getSuppressed()[0].getMessage());
    }
  }

  @Test
  void hardenOpenedDatabase_closesOpenedDatabaseWhenHardeningFailsWithoutCloseFault()
      throws Exception {
    AtomicInteger closeCalls = new AtomicInteger();
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"));
        Arena arena = Arena.ofConfined();
        SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "configure-and-harden-close-success", TEST_BOOK_KEY.toCharArray())) {
      AclFixturePath bookPath = fileSystem.path("\\books\\book.sqlite");
      SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath);
      bookPath.exists = true;
      bookPath.regularFile = true;
      bookPath.overrideAclView = throwingAclView("book-harden-boom");
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
      SqliteNativeApi sqliteApi = buildSqliteApi(sqliteApiArguments);

      SqliteStorageFailureException exception =
          assertThrows(
              SqliteStorageFailureException.class,
              () ->
                  hardenOpenedDatabase(
                      bookPath.toAbsolutePath().normalize(),
                      SqliteNativeKeyConfiguration.configureOpenedDatabase(
                          bookPath.toAbsolutePath().normalize(),
                          fakeDatabaseHandle,
                          passphrase,
                          sqliteApi,
                          arena),
                      SqliteNativeOpenMode.READ_WRITE_CREATE));

      assertEquals(
          "Failed to enforce the FinGrind SQLite book file permissions.", exception.getMessage());
      assertEquals("book-harden-boom", Objects.requireNonNull(exception.getCause()).getMessage());
      assertEquals(1, closeCalls.get());
      assertEquals(
          1,
          java.util.Arrays.stream(exception.getSuppressed())
              .filter(
                  suppressed ->
                      "Failed to close the SQLite native library bridge."
                          .equals(suppressed.getMessage()))
              .count());
    }
  }

  @Test
  void hardenOpenedDatabase_defersStageHardening() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"));
        Arena arena = Arena.ofConfined()) {
      AclFixturePath stagedPath = fileSystem.path("\\books\\staged-backup.sqlite");
      SqliteBookFileSecurity.ensureSecureParentDirectory(stagedPath);
      stagedPath.exists = true;
      stagedPath.regularFile = true;
      stagedPath.overrideAclView = throwingAclView("stage-harden-must-be-deferred");
      try (SqliteNativeDatabase openedDatabase = new FixtureNativeDatabase(arena.allocate(1))) {
        assertEquals(
            SqliteNativeOpenMode.READ_WRITE_EXISTING.flags(),
            SqliteNativeOpenMode.READ_WRITE_EXISTING_STAGE.flags());
        assertSame(
            openedDatabase,
            hardenOpenedDatabase(
                stagedPath.toAbsolutePath().normalize(),
                openedDatabase,
                SqliteNativeOpenMode.READ_WRITE_EXISTING_STAGE));
      }
    }
  }

  @Test
  void open_closesConfiguredDatabaseWhenHardeningFailsWithoutCloseFault() throws Exception {
    AtomicInteger closeCalls = new AtomicInteger();
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"));
        Arena arena = Arena.ofConfined();
        SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "open hardening cleanup", TEST_BOOK_KEY.toCharArray())) {
      AclFixturePath bookPath = fileSystem.path("\\books\\book.sqlite");
      SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath);
      bookPath.exists = true;
      bookPath.regularFile = true;
      bookPath.overrideAclView = throwingAclView("book-harden-boom");
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
      SqliteNativeApi sqliteApi = buildSqliteApi(sqliteApiArguments);

      SqliteStorageFailureException exception =
          assertThrows(
              SqliteStorageFailureException.class,
              () ->
                  SqliteNativeConnections.open(
                      bookPath, passphrase, SqliteNativeOpenMode.READ_WRITE_CREATE, sqliteApi));

      assertEquals(
          "Failed to enforce the FinGrind SQLite book file permissions.", exception.getMessage());
      assertEquals("book-harden-boom", Objects.requireNonNull(exception.getCause()).getMessage());
      assertEquals(1, closeCalls.get());
      assertEquals(0, exception.getSuppressed().length);
    }
  }

  @Test
  void open_addsSuppressedCloseFailureWhenHardeningFails() throws Exception {
    AtomicInteger closeCalls = new AtomicInteger();
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"));
        Arena arena = Arena.ofConfined();
        SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "open hardening cleanup failure", TEST_BOOK_KEY.toCharArray())) {
      AclFixturePath bookPath = fileSystem.path("\\books\\book.sqlite");
      SqliteBookFileSecurity.ensureSecureParentDirectory(bookPath);
      bookPath.exists = true;
      bookPath.regularFile = true;
      bookPath.overrideAclView = throwingAclView("book-harden-boom");
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
                      "recordCloseCallThenThrow",
                      java.lang.invoke.MethodType.methodType(
                          int.class, AtomicInteger.class, MemorySegment.class)),
              0,
              closeCalls);
      SqliteNativeApi sqliteApi = buildSqliteApi(sqliteApiArguments);

      SqliteStorageFailureException exception =
          assertThrows(
              SqliteStorageFailureException.class,
              () ->
                  SqliteNativeConnections.open(
                      bookPath, passphrase, SqliteNativeOpenMode.READ_WRITE_CREATE, sqliteApi));

      assertEquals(
          "Failed to enforce the FinGrind SQLite book file permissions.", exception.getMessage());
      assertEquals("book-harden-boom", Objects.requireNonNull(exception.getCause()).getMessage());
      assertEquals(1, closeCalls.get());
      assertEquals(1, exception.getSuppressed().length);
      assertEquals(
          "Failed to close the SQLite native library bridge.",
          exception.getSuppressed()[0].getMessage());
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

  private static SqliteNativeDatabase hardenOpenedDatabase(
      Path normalizedBookPath, SqliteNativeDatabase openedDatabase, SqliteNativeOpenMode openMode) {
    try {
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(SqliteNativeConnections.class, MethodHandles.lookup());
      return (SqliteNativeDatabase)
          lookup
              .findStatic(
                  SqliteNativeConnections.class,
                  "hardenOpenedDatabase",
                  MethodType.methodType(
                      SqliteNativeDatabase.class,
                      Path.class,
                      SqliteNativeDatabase.class,
                      SqliteNativeOpenMode.class))
              .invokeExact(normalizedBookPath, openedDatabase, openMode);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError(
          "Failed to invoke SQLite native open-and-harden helper for tests.", throwable);
    }
  }

  /** Test double that does not own a real native connection handle. */
  private static final class FixtureNativeDatabase extends SqliteNativeDatabase {
    private FixtureNativeDatabase(MemorySegment databaseHandle) {
      super(databaseHandle);
    }

    @Override
    public void close() {}
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
      public void setAcl(List<AclEntry> acl) throws java.io.IOException {
        throw new java.io.IOException(message);
      }

      @Override
      public UserPrincipal getOwner() throws java.io.IOException {
        throw new java.io.IOException(message);
      }

      @Override
      public void setOwner(UserPrincipal ownerPrincipal) {
        throw new UnsupportedOperationException();
      }
    };
  }
}
