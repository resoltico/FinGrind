package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.application.BookAccess;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for the SQLite FFM binding layer. */
class SqliteNativeLibraryTest {
  private static final String TEST_BOOK_KEY = "native-library-test-book-key";

  @TempDir Path tempDirectory;

  @Test
  void sqliteRuntimeProbe_reportsManagedSupportedVersion() {
    SqliteRuntime.Probe runtimeProbe = SqliteRuntime.probe();

    assertEquals("sqlite-ffm-sqlite3mc", SqliteRuntime.STORAGE_DRIVER);
    assertEquals("sqlite", SqliteRuntime.STORAGE_ENGINE);
    assertEquals("required", SqliteRuntime.BOOK_PROTECTION_MODE);
    assertEquals("chacha20", SqliteRuntime.DEFAULT_BOOK_CIPHER);
    assertEquals("FINGRIND_SQLITE_LIBRARY", SqliteRuntime.LIBRARY_OVERRIDE_ENVIRONMENT_VARIABLE);
    assertEquals("3.53.0", SqliteRuntime.REQUIRED_MINIMUM_SQLITE_VERSION);
    assertEquals("2.3.3", SqliteRuntime.REQUIRED_SQLITE3MC_VERSION);
    assertEquals("managed", runtimeProbe.librarySource());
    assertEquals("3.53.0", runtimeProbe.requiredMinimumSqliteVersion());
    assertEquals("2.3.3", runtimeProbe.requiredSqlite3mcVersion());
    assertEquals(SqliteRuntime.Status.READY, runtimeProbe.status());
    assertEquals("3.53.0", runtimeProbe.loadedSqliteVersion());
    assertEquals("2.3.3", runtimeProbe.loadedSqlite3mcVersion());
    assertFalse(SqliteRuntime.sqliteVersion().isBlank());
    assertEquals("2.3.3", SqliteRuntime.sqlite3MultipleCiphersVersion());
  }

  @Test
  void failureDetail_prefersMessageAndFallsBackToType() {
    assertEquals("boom", SqliteRuntime.failureDetail(new IllegalStateException("boom")));
    assertEquals("RuntimeException", SqliteRuntime.failureDetail(new RuntimeException()));
  }

  @Test
  void probe_reportsIncompatibleRuntimeWithoutThrowing() {
    SqliteRuntime.Probe runtimeProbe =
        SqliteRuntime.probe(
            () -> "system",
            () -> {
              throw new UnsupportedSqliteVersionException("3.51.0", "3.53.0", "system");
            },
            () -> "2.3.3",
            SqliteRuntime::failureDetail);

    assertEquals("system", runtimeProbe.librarySource());
    assertEquals("3.53.0", runtimeProbe.requiredMinimumSqliteVersion());
    assertEquals("2.3.3", runtimeProbe.requiredSqlite3mcVersion());
    assertEquals(SqliteRuntime.Status.INCOMPATIBLE, runtimeProbe.status());
    assertEquals("3.51.0", runtimeProbe.loadedSqliteVersion());
    assertEquals("2.3.3", runtimeProbe.loadedSqlite3mcVersion());
    assertTrue(runtimeProbe.issue().contains("requires SQLite 3.53.0 or newer"));
  }

  @Test
  void probe_reportsUnavailableRuntimeWithoutThrowing() {
    SqliteRuntime.Probe runtimeProbe =
        SqliteRuntime.probe(
            () -> "managed",
            () -> {
              throw new IllegalStateException("sqlite runtime unavailable");
            },
            () -> "2.3.3",
            SqliteRuntime::failureDetail);

    assertEquals("managed", runtimeProbe.librarySource());
    assertEquals(SqliteRuntime.Status.UNAVAILABLE, runtimeProbe.status());
    assertEquals("sqlite runtime unavailable", runtimeProbe.issue());
    assertNull(runtimeProbe.loadedSqliteVersion());
    assertNull(runtimeProbe.loadedSqlite3mcVersion());
  }

  @Test
  void probe_reportsIncompatibleSqlite3mcRuntimeWithoutThrowing() {
    SqliteRuntime.Probe runtimeProbe =
        SqliteRuntime.probe(
            () -> "system",
            () -> "3.53.0",
            () -> {
              throw new UnsupportedSqliteMultipleCiphersVersionException(
                  "2.3.2", "2.3.3", "system");
            },
            SqliteRuntime::failureDetail);

    assertEquals("system", runtimeProbe.librarySource());
    assertEquals(SqliteRuntime.Status.INCOMPATIBLE, runtimeProbe.status());
    assertEquals("3.53.0", runtimeProbe.loadedSqliteVersion());
    assertEquals("2.3.2", runtimeProbe.loadedSqlite3mcVersion());
    assertTrue(runtimeProbe.issue().contains("requires SQLite3 Multiple Ciphers 2.3.3"));
  }

  @Test
  void runtimeProbeAndStatusExposeStableValueSemantics() {
    SqliteRuntime.Probe runtimeProbe =
        new SqliteRuntime.Probe(
            "managed", "3.53.0", "2.3.3", SqliteRuntime.Status.READY, "3.53.0", "2.3.3", null);

    assertEquals("managed", runtimeProbe.librarySource());
    assertEquals("3.53.0", runtimeProbe.requiredMinimumSqliteVersion());
    assertEquals("2.3.3", runtimeProbe.requiredSqlite3mcVersion());
    assertEquals(SqliteRuntime.Status.READY, runtimeProbe.status());
    assertEquals("3.53.0", runtimeProbe.loadedSqliteVersion());
    assertEquals("2.3.3", runtimeProbe.loadedSqlite3mcVersion());
    assertNull(runtimeProbe.issue());
    assertEquals(
        runtimeProbe,
        new SqliteRuntime.Probe(
            "managed", "3.53.0", "2.3.3", SqliteRuntime.Status.READY, "3.53.0", "2.3.3", null));
    assertEquals(
        runtimeProbe.hashCode(),
        new SqliteRuntime.Probe(
                "managed", "3.53.0", "2.3.3", SqliteRuntime.Status.READY, "3.53.0", "2.3.3", null)
            .hashCode());
    assertTrue(runtimeProbe.toString().contains("managed"));
    assertEquals("ready", SqliteRuntime.Status.READY.wireValue());
    assertEquals("unavailable", SqliteRuntime.Status.UNAVAILABLE.wireValue());
    assertEquals("incompatible", SqliteRuntime.Status.INCOMPATIBLE.wireValue());
  }

  @Test
  void runtimeProbe_rejectsInvalidStatusSpecificShapes() {
    assertThrows(
        NullPointerException.class,
        () -> new SqliteRuntime.Probe("managed", "3.53.0", "2.3.3", null, null, null, "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                "managed", "3.53.0", "2.3.3", SqliteRuntime.Status.READY, null, "2.3.3", null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                "managed", "3.53.0", "2.3.3", SqliteRuntime.Status.READY, "3.53.0", null, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                "managed",
                "3.53.0",
                "2.3.3",
                SqliteRuntime.Status.READY,
                "3.53.0",
                "2.3.3",
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                "managed",
                "3.53.0",
                "2.3.3",
                SqliteRuntime.Status.UNAVAILABLE,
                "3.53.0",
                null,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                "managed",
                "3.53.0",
                "2.3.3",
                SqliteRuntime.Status.UNAVAILABLE,
                null,
                "2.3.3",
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                "managed", "3.53.0", "2.3.3", SqliteRuntime.Status.UNAVAILABLE, null, null, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                "managed",
                "3.53.0",
                "2.3.3",
                SqliteRuntime.Status.INCOMPATIBLE,
                null,
                "2.3.3",
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                "managed",
                "3.53.0",
                "2.3.3",
                SqliteRuntime.Status.INCOMPATIBLE,
                "3.53.0",
                null,
                "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                "managed",
                "3.53.0",
                "2.3.3",
                SqliteRuntime.Status.INCOMPATIBLE,
                "3.51.0",
                "2.3.3",
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                " ", "3.53.0", "2.3.3", SqliteRuntime.Status.UNAVAILABLE, null, null, "boom"));
  }

  @Test
  void resultName_mapsKnownAndUnknownCodes() {
    assertEquals("SQLITE_OK", SqliteNativeLibrary.resultName(0));
    assertEquals("SQLITE_ROW", SqliteNativeLibrary.resultName(100));
    assertEquals("SQLITE_DONE", SqliteNativeLibrary.resultName(101));
    assertEquals("SQLITE_CONSTRAINT_UNIQUE", SqliteNativeLibrary.resultName(2067));
    assertEquals("SQLITE_CONSTRAINT_PRIMARYKEY", SqliteNativeLibrary.resultName(1555));
    assertEquals("SQLITE_CONSTRAINT_DATATYPE", SqliteNativeLibrary.resultName(3091));
    assertEquals("SQLITE_CONSTRAINT_FOREIGNKEY", SqliteNativeLibrary.resultName(787));
    assertEquals("SQLITE_CANTOPEN", SqliteNativeLibrary.resultName(14));
    assertEquals("SQLITE_CANTOPEN_ISDIR", SqliteNativeLibrary.resultName(526));
    assertEquals("SQLITE_NOTADB", SqliteNativeLibrary.resultName(26));
    assertEquals("SQLITE_999999", SqliteNativeLibrary.resultName(999999));
  }

  @Test
  void sqliteLibraryNameFor_mapsSupportedAndUnsupportedOperatingSystems() {
    assertEquals("/usr/lib/libsqlite3.dylib", SqliteNativeLibrary.sqliteLibraryNameFor("Mac OS X"));
    assertEquals("libsqlite3.so.0", SqliteNativeLibrary.sqliteLibraryNameFor("Linux"));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteNativeLibrary.sqliteLibraryNameFor("Windows 11"));

    assertTrue(exception.getMessage().contains("Unsupported operating system"));
  }

  @Test
  void configuredLibraryTarget_prefersManagedOverrideAndNormalizesPath() {
    SqliteNativeLibrary.SqliteLibraryTarget libraryTarget =
        SqliteNativeLibrary.configuredLibraryTarget("./build/../sqlite/libsqlite3.so.0", "Linux");
    SqliteNativeLibrary.SqliteLibraryTarget systemLibraryTarget =
        SqliteNativeLibrary.configuredLibraryTarget(null, "Linux");

    assertEquals("managed", libraryTarget.source());
    assertTrue(libraryTarget.lookupTarget().endsWith("/sqlite/libsqlite3.so.0"));
    assertEquals("managed", SqliteNativeLibrary.configuredLibrarySource("/tmp/libsqlite3.so.0"));
    assertEquals("system", SqliteNativeLibrary.configuredLibrarySource(null));
    assertEquals("system", systemLibraryTarget.source());
    assertEquals("libsqlite3.so.0", systemLibraryTarget.lookupTarget());
    assertTrue(libraryTarget.toString().contains("managed"));
    assertEquals(
        libraryTarget,
        SqliteNativeLibrary.configuredLibraryTarget("./build/../sqlite/libsqlite3.so.0", "Linux"));
    assertEquals(
        libraryTarget.hashCode(),
        SqliteNativeLibrary.configuredLibraryTarget("./build/../sqlite/libsqlite3.so.0", "Linux")
            .hashCode());
    assertThrows(
        IllegalStateException.class,
        () -> SqliteNativeLibrary.configuredLibraryTarget("   ", "Linux"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SqliteNativeLibrary.SqliteLibraryTarget(" ", "x"));
  }

  @Test
  void requireSupportedVersion_rejectsOlderRuntimeAndCompareVersionsOrdersDottedNumbers() {
    assertTrue(SqliteNativeLibrary.compareVersions("3.53.0", "3.52.9") > 0);
    assertEquals(0, SqliteNativeLibrary.compareVersions("3.53", "3.53.0"));
    assertEquals(0, SqliteNativeLibrary.compareVersions("3.53.0", "3.53"));
    assertThrows(
        IllegalStateException.class,
        () -> SqliteNativeLibrary.compareVersions("3.bad.0", "3.53.0"));

    UnsupportedSqliteVersionException exception =
        assertThrows(
            UnsupportedSqliteVersionException.class,
            () -> SqliteNativeLibrary.requireSupportedVersion("3.51.0", "system"));

    assertEquals("3.51.0", exception.loadedVersion());
    assertEquals("3.53.0", exception.requiredMinimumVersion());
    assertEquals("system", exception.librarySource());
  }

  @Test
  void requireSupportedSqlite3mcVersion_rejectsUnexpectedRuntime() {
    UnsupportedSqliteMultipleCiphersVersionException exception =
        assertThrows(
            UnsupportedSqliteMultipleCiphersVersionException.class,
            () -> SqliteNativeLibrary.requireSupportedSqlite3mcVersion("2.3.2", "system"));

    assertEquals("2.3.2", exception.loadedVersion());
    assertEquals("2.3.3", exception.requiredVersion());
    assertEquals("system", exception.librarySource());
  }

  @Test
  void nativeInitializationFailure_unwrapsIllegalStateCause() {
    UnsupportedSqliteVersionException cause =
        new UnsupportedSqliteVersionException("3.51.0", "3.53.0", "system");

    IllegalStateException exception =
        SqliteNativeLibrary.nativeInitializationFailure(new ExceptionInInitializerError(cause));

    assertEquals(cause, exception);
  }

  @Test
  void nativeInitializationFailure_wrapsUnexpectedCause() {
    RuntimeException cause = new RuntimeException("boom");

    IllegalStateException exception =
        SqliteNativeLibrary.nativeInitializationFailure(new ExceptionInInitializerError(cause));

    assertEquals("boom", exception.getMessage());
    assertEquals(cause, exception.getCause());
  }

  @Test
  void nativeInitializationFailure_wrapsInitializerErrorWhenCauseIsMissing() {
    ExceptionInInitializerError error = new ExceptionInInitializerError();

    IllegalStateException exception = SqliteNativeLibrary.nativeInitializationFailure(error);

    assertEquals("Failed to initialize SQLite native library.", exception.getMessage());
    assertEquals(error, exception.getCause());
  }

  @Test
  void initialize_wrapsInitializerErrorsIntoStableIllegalStateExceptions() {
    RuntimeException cause = new RuntimeException("boom");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteNativeLibrary.initialize(
                    () -> {
                      throw new ExceptionInInitializerError(cause);
                    }));

    assertEquals("boom", exception.getMessage());
    assertEquals(cause, exception.getCause());
  }

  @Test
  void errorMessage_returnsGenericTextForNullHandle() {
    try (Arena arena = Arena.ofConfined()) {
      MethodHandle errorHandle =
          MethodHandles.dropArguments(
              MethodHandles.constant(MemorySegment.class, arena.allocateFrom("boom")),
              0,
              MemorySegment.class);

      assertEquals("SQLite native failure.", SqliteNativeLibrary.errorMessage(null, errorHandle));
      assertEquals(
          "SQLite native failure.",
          SqliteNativeLibrary.errorMessage(MemorySegment.NULL, errorHandle));
    }
    assertEquals("SQLite native failure.", SqliteNativeLibrary.errorMessage(null));
    assertEquals("SQLite native failure.", SqliteNativeLibrary.errorMessage(MemorySegment.NULL));
    assertEquals(
        "SQLite native failure.",
        SqliteNativeLibrary.scriptErrorMessage(MemorySegment.NULL, MemorySegment.NULL));
  }

  @Test
  void errorMessage_readsMessageForNonNullHandle() {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment fakeHandle = arena.allocate(1);
      MethodHandle errorHandle =
          MethodHandles.dropArguments(
              MethodHandles.constant(MemorySegment.class, arena.allocateFrom("boom")),
              0,
              MemorySegment.class);

      assertEquals("boom", SqliteNativeLibrary.errorMessage(fakeHandle, errorHandle));
    }
  }

  @Test
  void errorMessage_andSqliteVersion_coverDefaultConvenienceOverloads() throws Exception {
    Path bookPath = tempDirectory.resolve("error-message.sqlite");

    SqliteNativeDatabase database = SqliteNativeLibrary.open(bookAccess(bookPath));
    try (Arena arena = Arena.ofConfined()) {
      MethodHandle versionHandle =
          MethodHandles.constant(MemorySegment.class, arena.allocateFrom("3.53.0"));
      MethodHandle sqlite3mcVersionHandle =
          MethodHandles.constant(
              MemorySegment.class, arena.allocateFrom("SQLite3 Multiple Ciphers 2.3.3"));

      assertFalse(SqliteNativeLibrary.errorMessage(database.handle()).isBlank());
      assertEquals("3.53.0", SqliteNativeLibrary.sqliteVersion(versionHandle));
      assertEquals(
          "2.3.3", SqliteNativeLibrary.sqlite3MultipleCiphersVersion(sqlite3mcVersionHandle));
      assertDoesNotThrow(
          () ->
              SqliteNativeLibrary.freeSqliteBuffer(
                  null,
                  MethodHandles.dropArguments(
                      MethodHandles.empty(java.lang.invoke.MethodType.methodType(void.class)),
                      0,
                      MemorySegment.class)));
      assertDoesNotThrow(
          () ->
              SqliteNativeLibrary.freeSqliteBuffer(
                  MemorySegment.NULL,
                  MethodHandles.dropArguments(
                      MethodHandles.empty(java.lang.invoke.MethodType.methodType(void.class)),
                      0,
                      MemorySegment.class)));
    } finally {
      database.close();
    }
  }

  @Test
  void errorString_convenienceOverload_readsConfiguredApi() {
    assertFalse(SqliteNativeLibrary.errorString(14).isBlank());
  }

  @Test
  void errorString_returnsResultNameWhenPointerIsJavaNull() {
    MethodHandle nullErrorStringHandle =
        MethodHandles.dropArguments(
            MethodHandles.constant(MemorySegment.class, null), 0, int.class);

    assertEquals(
        "SQLITE_CANTOPEN",
        SqliteNativeLibrary.errorString(
            14, nullErrorStringHandle, constantMethodHandle(0L, MemorySegment.class)));
  }

  @Test
  void errorString_returnsResultNameWhenPointerIsNullSegment() {
    assertEquals(
        "SQLITE_CANTOPEN",
        SqliteNativeLibrary.errorString(
            14,
            constantMethodHandle(MemorySegment.NULL, int.class),
            constantMethodHandle(0L, MemorySegment.class)));
  }

  @Test
  void errorString_returnsResultNameWhenPointerIsBlank() {
    try (Arena arena = Arena.ofConfined()) {
      assertEquals(
          "SQLITE_CANTOPEN",
          SqliteNativeLibrary.errorString(
              14,
              constantMethodHandle(arena.allocateFrom(""), int.class),
              constantMethodHandle(0L, MemorySegment.class)));
    }
  }

  @Test
  void errorString_wrapsThrowableFromErrorStringHandle() {
    MethodHandle throwingErrorStringHandle =
        MethodHandles.dropArguments(
            MethodHandles.throwException(MemorySegment.class, IllegalStateException.class)
                .bindTo(new IllegalStateException("boom")),
            0,
            int.class);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteNativeLibrary.errorString(
                    14, throwingErrorStringHandle, constantMethodHandle(0L, MemorySegment.class)));

    assertEquals("Failed to read the SQLite error string.", exception.getMessage());
    assertTrue(exception.getCause() instanceof IllegalStateException);
    assertEquals("boom", exception.getCause().getMessage());
  }

  @Test
  void scriptErrorMessage_resultCodeOverload_prefersExecBufferAndFallsBackToErrorString() {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment boom = arena.allocateFrom("boom");
      MethodHandle errorStringHandle = constantMethodHandle(boom, int.class);
      MethodHandle strlenHandle = constantMethodHandle(4L, MemorySegment.class);

      assertEquals(
          "boom",
          SqliteNativeLibrary.scriptErrorMessage(14, boom, errorStringHandle, strlenHandle));
      assertEquals(
          "boom",
          SqliteNativeLibrary.scriptErrorMessage(
              14, MemorySegment.NULL, errorStringHandle, strlenHandle));
      assertEquals(
          "boom",
          SqliteNativeLibrary.scriptErrorMessage(14, null, errorStringHandle, strlenHandle));
    }
  }

  @Test
  void open_rejectsNullBookAccess() {
    assertThrows(NullPointerException.class, () -> SqliteNativeLibrary.open(null));
  }

  @Test
  void openExecutePrepareAndClose_roundTripThroughSystemLibrary() throws Exception {
    Path bookPath = tempDirectory.resolve("native-round-trip.sqlite");

    SqliteNativeDatabase database = SqliteNativeLibrary.open(bookAccess(bookPath));
    try {
      database.executeStatement("create table sample (id integer not null, note text null)");

      try (SqliteNativeStatement insert =
          SqliteNativeLibrary.prepare(database, "insert into sample (id, note) values (?, ?)")) {
        insert.bindInt(1, 7);
        insert.bindText(2, null);
        assertEquals(SqliteNativeLibrary.SQLITE_DONE, insert.step());
      }

      try (SqliteNativeStatement select =
          SqliteNativeLibrary.prepare(database, "select id, note from sample")) {
        assertEquals(SqliteNativeLibrary.SQLITE_ROW, select.step());
        assertEquals(7, select.columnInt(0));
        assertNull(select.columnText(1));
        assertEquals(SqliteNativeLibrary.SQLITE_DONE, select.step());
      }
    } finally {
      database.close();
    }
  }

  @Test
  void utf8ByteLength_usesNativeSegmentSizeWithoutNullTerminator() {
    String value = "Riga € 漢字";

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment valuePointer = arena.allocateFrom(value);

      assertEquals(
          value.getBytes(StandardCharsets.UTF_8).length,
          SqliteNativeStatement.utf8ByteLength(valuePointer));
      assertEquals(value.getBytes(StandardCharsets.UTF_8).length + 1L, valuePointer.byteSize());
    }
  }

  @Test
  void open_throwsForDirectoryTarget() throws Exception {
    Path directoryPath = tempDirectory.resolve("not-a-book");
    java.nio.file.Files.createDirectories(directoryPath);

    SqliteNativeException exception =
        assertThrows(
            SqliteNativeException.class, () -> SqliteNativeLibrary.open(bookAccess(directoryPath)));

    assertTrue(exception.resultName().contains("SQLITE_CANTOPEN"));
  }

  @Test
  void open_rejectsWrongBookKey() throws Exception {
    Path bookPath = tempDirectory.resolve("wrong-key.sqlite");

    SqliteNativeDatabase database = SqliteNativeLibrary.open(bookAccess(bookPath, TEST_BOOK_KEY));
    try {
      database.executeStatement("create table sample (id integer not null)");
    } finally {
      database.close();
    }

    SqliteNativeException exception =
        assertThrows(
            SqliteNativeException.class,
            () -> SqliteNativeLibrary.open(bookAccess(bookPath, "different-book-key")));

    assertTrue(exception.resultName().contains("SQLITE_NOTADB"));
  }

  @Test
  void open_wrapsBookKeyReadFailureInStableBridgeFailure() {
    Path bookPath = tempDirectory.resolve("missing-key.sqlite");
    Path missingKeyPath = tempDirectory.resolve("missing.key");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteNativeLibrary.open(new BookAccess(bookPath, missingKeyPath)));

    assertTrue(exception.getMessage().contains("Failed to open the SQLite native library bridge."));
    assertTrue(
        exception.getCause().getMessage().contains("Failed to read the FinGrind book key file"));
  }

  @Test
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  void applyKey_wrapsUnexpectedThrowableFromNativeInvocation() throws Exception {
    Method method =
        SqliteNativeLibrary.class.getDeclaredMethod(
            "applyKey",
            MemorySegment.class,
            SqliteBookKeyFile.KeyMaterial.class,
            Class.forName("dev.erst.fingrind.sqlite.SqliteNativeLibrary$SqliteApi"),
            Arena.class);
    method.setAccessible(true);

    Path keyFile = tempDirectory.resolve("apply-key.key");
    Files.writeString(keyFile, TEST_BOOK_KEY, StandardCharsets.UTF_8);

    try (SqliteBookKeyFile.KeyMaterial keyMaterial = SqliteBookKeyFile.load(keyFile);
        Arena arena = Arena.ofConfined()) {
      Object sqliteApi =
          sqliteApi(
              throwingMethodHandle(
                  new IllegalStateException("boom"),
                  int.class,
                  MemorySegment.class,
                  MemorySegment.class,
                  int.class),
              constantMethodHandle(0, MemorySegment.class),
              constantMethodHandle(MemorySegment.NULL, MemorySegment.class),
              constantMethodHandle(MemorySegment.NULL, int.class),
              constantMethodHandle(0, MemorySegment.class));

      InvocationTargetException exception =
          assertThrows(
              InvocationTargetException.class,
              () -> method.invoke(null, MemorySegment.NULL, keyMaterial, sqliteApi, arena));

      assertTrue(exception.getCause() instanceof IllegalStateException);
      assertTrue(
          exception
              .getCause()
              .getMessage()
              .contains("Failed to apply the FinGrind SQLite book key from"));
    }
  }

  @Test
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  void applyKey_rethrowsSqliteNativeException() throws Exception {
    Method method =
        SqliteNativeLibrary.class.getDeclaredMethod(
            "applyKey",
            MemorySegment.class,
            SqliteBookKeyFile.KeyMaterial.class,
            Class.forName("dev.erst.fingrind.sqlite.SqliteNativeLibrary$SqliteApi"),
            Arena.class);
    method.setAccessible(true);

    Path keyFile = tempDirectory.resolve("apply-key-native-failure.key");
    Files.writeString(keyFile, TEST_BOOK_KEY, StandardCharsets.UTF_8);

    try (SqliteBookKeyFile.KeyMaterial keyMaterial = SqliteBookKeyFile.load(keyFile);
        Arena arena = Arena.ofConfined()) {
      Object sqliteApi =
          sqliteApi(
              constantMethodHandle(14, MemorySegment.class, MemorySegment.class, int.class),
              constantMethodHandle(0, MemorySegment.class),
              constantMethodHandle(arena.allocateFrom("boom"), MemorySegment.class),
              constantMethodHandle(arena.allocateFrom("boom"), int.class),
              constantMethodHandle(14, MemorySegment.class));

      InvocationTargetException exception =
          assertThrows(
              InvocationTargetException.class,
              () -> method.invoke(null, MemorySegment.NULL, keyMaterial, sqliteApi, arena));

      assertTrue(exception.getCause() instanceof SqliteNativeException);
      assertEquals("SQLITE_CANTOPEN: boom", exception.getCause().getMessage());
    }
  }

  @Test
  void sqlite3MultipleCiphersVersion_wrapsUnexpectedLookupFailure() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteNativeLibrary.sqlite3MultipleCiphersVersion(
                    throwingMethodHandle(new IllegalStateException("boom"), MemorySegment.class)));

    assertTrue(
        exception
            .getMessage()
            .contains("Failed to read the SQLite3 Multiple Ciphers library version."));
  }

  @Test
  void shutdownQuietly_ignoresThrowablesFromNativeShutdown() {
    assertDoesNotThrow(
        () ->
            SqliteNativeLibrary.shutdownQuietly(
                throwingMethodHandle(new IllegalStateException("boom"), int.class)));
  }

  @Test
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  void downcall_throwsForMissingSymbol() throws Exception {
    Method method =
        SqliteNativeLibrary.class.getDeclaredMethod(
            "downcall", SymbolLookup.class, String.class, FunctionDescriptor.class);
    method.setAccessible(true);

    InvocationTargetException exception =
        assertThrows(
            InvocationTargetException.class,
            () ->
                method.invoke(
                    null,
                    Linker.nativeLinker().defaultLookup(),
                    "sqlite3_missing_symbol_for_test",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT)));

    assertTrue(exception.getCause() instanceof IllegalStateException);
    assertTrue(exception.getCause().getMessage().contains("Missing SQLite symbol"));
  }

  @Test
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  void loadApi_reraisesLookupFailureForConfiguredMissingLibrary() throws Exception {
    Method method =
        SqliteNativeLibrary.class.getDeclaredMethod(
            "loadApi", SqliteNativeLibrary.SqliteLibraryTarget.class);
    method.setAccessible(true);

    InvocationTargetException exception =
        assertThrows(
            InvocationTargetException.class,
            () ->
                method.invoke(
                    null,
                    new SqliteNativeLibrary.SqliteLibraryTarget(
                        "managed", tempDirectory.resolve("missing/libsqlite3.dylib").toString())));

    assertTrue(exception.getCause() instanceof RuntimeException);
  }

  @Test
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  void requireOpenConfigurationSuccess_throwsSqliteFailureForNonOkResult() throws Exception {
    Class<?> sqliteApiClass =
        Class.forName("dev.erst.fingrind.sqlite.SqliteNativeLibrary$SqliteApi");
    Method method =
        SqliteNativeLibrary.class.getDeclaredMethod(
            "requireOpenConfigurationSuccess", MemorySegment.class, int.class, sqliteApiClass);
    method.setAccessible(true);

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment databaseHandle = arena.allocate(1);
      Object sqliteApi =
          sqliteApi(
              constantMethodHandle(0, MemorySegment.class),
              constantMethodHandle(arena.allocateFrom("boom"), MemorySegment.class),
              constantMethodHandle(arena.allocateFrom("boom"), int.class),
              constantMethodHandle(14, MemorySegment.class));

      InvocationTargetException exception =
          assertThrows(
              InvocationTargetException.class,
              () -> method.invoke(null, databaseHandle, 14, sqliteApi));

      assertTrue(exception.getCause() instanceof SqliteNativeException);
      SqliteNativeException sqliteException = (SqliteNativeException) exception.getCause();
      assertEquals(14, sqliteException.resultCode());
      assertEquals("SQLITE_CANTOPEN", sqliteException.resultName());
      assertEquals("SQLITE_CANTOPEN: boom", sqliteException.getMessage());
    }
  }

  @Test
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  void requireOpenConfigurationSuccess_preservesFailureWhenCloseAlsoThrows() throws Exception {
    Class<?> sqliteApiClass =
        Class.forName("dev.erst.fingrind.sqlite.SqliteNativeLibrary$SqliteApi");
    Method method =
        SqliteNativeLibrary.class.getDeclaredMethod(
            "requireOpenConfigurationSuccess", MemorySegment.class, int.class, sqliteApiClass);
    method.setAccessible(true);

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment databaseHandle = arena.allocate(1);
      MethodHandle throwingCloseHandle =
          MethodHandles.throwException(int.class, IllegalStateException.class)
              .bindTo(new IllegalStateException("close boom"));
      Object sqliteApi =
          sqliteApi(
              MethodHandles.dropArguments(throwingCloseHandle, 0, MemorySegment.class),
              constantMethodHandle(arena.allocateFrom("boom"), MemorySegment.class),
              constantMethodHandle(arena.allocateFrom("boom"), int.class),
              constantMethodHandle(14, MemorySegment.class));

      InvocationTargetException exception =
          assertThrows(
              InvocationTargetException.class,
              () -> method.invoke(null, databaseHandle, 14, sqliteApi));

      assertTrue(exception.getCause() instanceof SqliteNativeException);
      assertEquals("SQLITE_CANTOPEN: boom", exception.getCause().getMessage());
    }
  }

  @Test
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  void requireOpenConfigurationSuccess_usesResultNameWhenErrorStringIsBlank() throws Exception {
    Class<?> sqliteApiClass =
        Class.forName("dev.erst.fingrind.sqlite.SqliteNativeLibrary$SqliteApi");
    Method method =
        SqliteNativeLibrary.class.getDeclaredMethod(
            "requireOpenConfigurationSuccess", MemorySegment.class, int.class, sqliteApiClass);
    method.setAccessible(true);

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment databaseHandle = arena.allocate(1);
      Object sqliteApi =
          sqliteApi(
              constantMethodHandle(0, MemorySegment.class),
              constantMethodHandle(arena.allocateFrom("unused"), MemorySegment.class),
              constantMethodHandle(arena.allocateFrom(""), int.class),
              constantMethodHandle(14, MemorySegment.class));

      InvocationTargetException exception =
          assertThrows(
              InvocationTargetException.class,
              () -> method.invoke(null, databaseHandle, 14, sqliteApi));

      assertTrue(exception.getCause() instanceof SqliteNativeException);
      assertEquals("SQLITE_CANTOPEN", exception.getCause().getMessage());
    }
  }

  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  private static Object sqliteApi(
      MethodHandle keyHandle,
      MethodHandle closeHandle,
      MethodHandle errorMessageHandle,
      MethodHandle errorStringHandle,
      MethodHandle extendedErrcodeHandle)
      throws ReflectiveOperationException {
    Class<?> sqliteApiClass =
        Class.forName("dev.erst.fingrind.sqlite.SqliteNativeLibrary$SqliteApi");
    var constructor = sqliteApiClass.getDeclaredConstructors()[0];
    constructor.setAccessible(true);

    return constructor.newInstance(
        Arena.ofShared(),
        constantMethodHandle(
            0, MemorySegment.class, MemorySegment.class, int.class, MemorySegment.class),
        closeHandle,
        keyHandle,
        constantMethodHandle(0),
        constantMethodHandle(0, MemorySegment.class, int.class),
        constantMethodHandle(0, MemorySegment.class, int.class),
        constantMethodHandle(
            0,
            MemorySegment.class,
            MemorySegment.class,
            MemorySegment.class,
            MemorySegment.class,
            MemorySegment.class),
        voidMethodHandle(MemorySegment.class),
        constantMethodHandle(
            0, MemorySegment.class, MemorySegment.class, int.class, MemorySegment.class),
        constantMethodHandle(0, MemorySegment.class, int.class),
        constantMethodHandle(0, MemorySegment.class, int.class, int.class),
        constantMethodHandle(
            0, MemorySegment.class, int.class, MemorySegment.class, int.class, MemorySegment.class),
        constantMethodHandle(0, MemorySegment.class),
        constantMethodHandle(0, MemorySegment.class),
        constantMethodHandle(MemorySegment.NULL, MemorySegment.class, int.class),
        constantMethodHandle(0, MemorySegment.class, int.class),
        constantMethodHandle(0, MemorySegment.class, int.class),
        errorMessageHandle,
        errorStringHandle,
        extendedErrcodeHandle,
        "3.53.0",
        "2.3.3");
  }

  private static Object sqliteApi(
      MethodHandle closeHandle,
      MethodHandle errorMessageHandle,
      MethodHandle errorStringHandle,
      MethodHandle extendedErrcodeHandle)
      throws ReflectiveOperationException {
    return sqliteApi(
        constantMethodHandle(0, MemorySegment.class, MemorySegment.class, int.class),
        closeHandle,
        errorMessageHandle,
        errorStringHandle,
        extendedErrcodeHandle);
  }

  private BookAccess bookAccess(Path bookPath) {
    return bookAccess(bookPath, TEST_BOOK_KEY);
  }

  private BookAccess bookAccess(Path bookPath, String keyText) {
    try {
      Path keyDirectory = tempDirectory.resolve("book-keys");
      Files.createDirectories(keyDirectory);
      Path keyPath = keyDirectory.resolve(bookPath.getFileName() + ".key");
      if (keyPath.getParent() != null) {
        Files.createDirectories(keyPath.getParent());
      }
      Files.writeString(keyPath, keyText, StandardCharsets.UTF_8);
      return new BookAccess(bookPath, keyPath);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static MethodHandle constantMethodHandle(Object value, Class<?>... parameterTypes) {
    MethodHandle constantHandle = MethodHandles.constant(constantType(value), value);
    return MethodHandles.dropArguments(constantHandle, 0, parameterTypes);
  }

  private static MethodHandle throwingMethodHandle(
      Throwable throwable, Class<?> returnType, Class<?>... parameterTypes) {
    MethodHandle throwingHandle = MethodHandles.throwException(returnType, Throwable.class);
    return MethodHandles.dropArguments(
        MethodHandles.insertArguments(throwingHandle, 0, throwable), 0, parameterTypes);
  }

  private static MethodHandle voidMethodHandle(Class<?>... parameterTypes) {
    return MethodHandles.dropArguments(
        MethodHandles.empty(java.lang.invoke.MethodType.methodType(void.class)), 0, parameterTypes);
  }

  private static Class<?> constantType(Object value) {
    return switch (value) {
      case Integer _ -> int.class;
      case Long _ -> long.class;
      case MemorySegment _ -> MemorySegment.class;
      default -> value.getClass();
    };
  }
}
