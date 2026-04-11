package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for the SQLite FFM binding layer. */
class SqliteNativeLibraryTest {
  @TempDir Path tempDirectory;

  @Test
  void sqliteRuntimeProbe_reportsManagedSupportedVersion() {
    SqliteRuntime.Probe runtimeProbe = SqliteRuntime.probe();

    assertEquals("sqlite-ffm", SqliteRuntime.STORAGE_DRIVER);
    assertEquals("sqlite", SqliteRuntime.STORAGE_ENGINE);
    assertEquals("FINGRIND_SQLITE_LIBRARY", SqliteRuntime.LIBRARY_OVERRIDE_ENVIRONMENT_VARIABLE);
    assertEquals("3.53.0", SqliteRuntime.REQUIRED_MINIMUM_SQLITE_VERSION);
    assertEquals("managed", runtimeProbe.librarySource());
    assertEquals("3.53.0", runtimeProbe.requiredMinimumSqliteVersion());
    assertEquals(SqliteRuntime.Status.READY, runtimeProbe.status());
    assertEquals("3.53.0", runtimeProbe.loadedSqliteVersion());
    assertFalse(SqliteRuntime.sqliteVersion().isBlank());
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
            SqliteRuntime::failureDetail);

    assertEquals("system", runtimeProbe.librarySource());
    assertEquals("3.53.0", runtimeProbe.requiredMinimumSqliteVersion());
    assertEquals(SqliteRuntime.Status.INCOMPATIBLE, runtimeProbe.status());
    assertEquals("3.51.0", runtimeProbe.loadedSqliteVersion());
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
            SqliteRuntime::failureDetail);

    assertEquals("managed", runtimeProbe.librarySource());
    assertEquals(SqliteRuntime.Status.UNAVAILABLE, runtimeProbe.status());
    assertEquals("sqlite runtime unavailable", runtimeProbe.issue());
    assertNull(runtimeProbe.loadedSqliteVersion());
  }

  @Test
  void runtimeProbeAndStatusExposeStableValueSemantics() {
    SqliteRuntime.Probe runtimeProbe =
        new SqliteRuntime.Probe("managed", "3.53.0", SqliteRuntime.Status.READY, "3.53.0", null);

    assertEquals("managed", runtimeProbe.librarySource());
    assertEquals("3.53.0", runtimeProbe.requiredMinimumSqliteVersion());
    assertEquals(SqliteRuntime.Status.READY, runtimeProbe.status());
    assertEquals("3.53.0", runtimeProbe.loadedSqliteVersion());
    assertNull(runtimeProbe.issue());
    assertEquals(
        runtimeProbe,
        new SqliteRuntime.Probe("managed", "3.53.0", SqliteRuntime.Status.READY, "3.53.0", null));
    assertEquals(
        runtimeProbe.hashCode(),
        new SqliteRuntime.Probe("managed", "3.53.0", SqliteRuntime.Status.READY, "3.53.0", null)
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
        () -> new SqliteRuntime.Probe("managed", "3.53.0", null, null, "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SqliteRuntime.Probe("managed", "3.53.0", SqliteRuntime.Status.READY, null, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                "managed", "3.53.0", SqliteRuntime.Status.READY, "3.53.0", "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                "managed", "3.53.0", SqliteRuntime.Status.UNAVAILABLE, "3.53.0", "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                "managed", "3.53.0", SqliteRuntime.Status.UNAVAILABLE, null, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                "managed", "3.53.0", SqliteRuntime.Status.INCOMPATIBLE, null, "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                "managed", "3.53.0", SqliteRuntime.Status.INCOMPATIBLE, "3.51.0", null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(" ", "3.53.0", SqliteRuntime.Status.UNAVAILABLE, null, "boom"));
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

    SqliteNativeDatabase database = SqliteNativeLibrary.open(bookPath);
    try (Arena arena = Arena.ofConfined()) {
      MethodHandle versionHandle =
          MethodHandles.constant(MemorySegment.class, arena.allocateFrom("3.53.0"));

      assertFalse(SqliteNativeLibrary.errorMessage(database.handle()).isBlank());
      assertEquals("3.53.0", SqliteNativeLibrary.sqliteVersion(versionHandle));
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
  void open_nullPathMapsToBridgeFailure() {
    assertThrows(IllegalStateException.class, () -> SqliteNativeLibrary.open(null));
  }

  @Test
  void openExecutePrepareAndClose_roundTripThroughSystemLibrary() throws Exception {
    Path bookPath = tempDirectory.resolve("native-round-trip.sqlite");

    SqliteNativeDatabase database = SqliteNativeLibrary.open(bookPath);
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
        assertThrows(SqliteNativeException.class, () -> SqliteNativeLibrary.open(directoryPath));

    assertTrue(exception.resultName().contains("SQLITE_CANTOPEN"));
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
              constantMethodHandle(14, MemorySegment.class));

      InvocationTargetException exception =
          assertThrows(
              InvocationTargetException.class,
              () -> method.invoke(null, databaseHandle, 14, sqliteApi));

      assertTrue(exception.getCause() instanceof SqliteNativeException);
      SqliteNativeException sqliteException = (SqliteNativeException) exception.getCause();
      assertEquals(14, sqliteException.resultCode());
      assertEquals("SQLITE_CANTOPEN", sqliteException.resultName());
      assertEquals("boom", sqliteException.getMessage());
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
              constantMethodHandle(14, MemorySegment.class));

      InvocationTargetException exception =
          assertThrows(
              InvocationTargetException.class,
              () -> method.invoke(null, databaseHandle, 14, sqliteApi));

      assertTrue(exception.getCause() instanceof SqliteNativeException);
      assertEquals("boom", exception.getCause().getMessage());
    }
  }

  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  private static Object sqliteApi(
      MethodHandle closeHandle, MethodHandle errorMessageHandle, MethodHandle extendedErrcodeHandle)
      throws ReflectiveOperationException {
    Class<?> sqliteApiClass =
        Class.forName("dev.erst.fingrind.sqlite.SqliteNativeLibrary$SqliteApi");
    var constructor =
        sqliteApiClass.getDeclaredConstructor(
            Arena.class,
            MethodHandle.class,
            MethodHandle.class,
            MethodHandle.class,
            MethodHandle.class,
            MethodHandle.class,
            MethodHandle.class,
            MethodHandle.class,
            MethodHandle.class,
            MethodHandle.class,
            MethodHandle.class,
            MethodHandle.class,
            MethodHandle.class,
            MethodHandle.class,
            MethodHandle.class,
            MethodHandle.class,
            MethodHandle.class,
            MethodHandle.class,
            String.class);
    constructor.setAccessible(true);

    return constructor.newInstance(
        Arena.ofShared(),
        constantMethodHandle(
            0, MemorySegment.class, MemorySegment.class, int.class, MemorySegment.class),
        closeHandle,
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
        extendedErrcodeHandle,
        "3.53.0");
  }

  private static MethodHandle constantMethodHandle(Object value, Class<?>... parameterTypes) {
    MethodHandle constantHandle = MethodHandles.constant(constantType(value), value);
    return MethodHandles.dropArguments(constantHandle, 0, parameterTypes);
  }

  private static MethodHandle voidMethodHandle(Class<?>... parameterTypes) {
    return MethodHandles.dropArguments(
        MethodHandles.empty(java.lang.invoke.MethodType.methodType(void.class)), 0, parameterTypes);
  }

  private static Class<?> constantType(Object value) {
    return switch (value) {
      case Integer _ -> int.class;
      case MemorySegment _ -> MemorySegment.class;
      default -> value.getClass();
    };
  }
}
