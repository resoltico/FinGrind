package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.BookAccess;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
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
    assertEquals("FINGRIND_SQLITE_LIBRARY", SqliteRuntime.LIBRARY_ENVIRONMENT_VARIABLE);
    assertEquals("fingrind.bundle.home", SqliteRuntime.BUNDLE_HOME_SYSTEM_PROPERTY);
    assertEquals(
        java.util.List.of("THREADSAFE=1", "OMIT_LOAD_EXTENSION", "TEMP_STORE=3", "SECURE_DELETE"),
        SqliteRuntime.REQUIRED_SQLITE_COMPILE_OPTIONS);
    assertEquals("3.53.0", SqliteRuntime.REQUIRED_MINIMUM_SQLITE_VERSION);
    assertEquals("2.3.3", SqliteRuntime.REQUIRED_SQLITE3MC_VERSION);
    assertEquals("managed-only", runtimeProbe.libraryMode());
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
            () -> "managed-only",
            () -> {
              throw new UnsupportedSqliteVersionException("3.51.0", "3.53.0", "managed-only");
            },
            () -> "2.3.3",
            SqliteRuntime::failureDetail);

    assertEquals("managed-only", runtimeProbe.libraryMode());
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
            () -> "managed-only",
            () -> {
              throw new IllegalStateException("sqlite runtime unavailable");
            },
            () -> "2.3.3",
            SqliteRuntime::failureDetail);

    assertEquals("managed-only", runtimeProbe.libraryMode());
    assertEquals(SqliteRuntime.Status.UNAVAILABLE, runtimeProbe.status());
    assertEquals("sqlite runtime unavailable", runtimeProbe.issue());
    assertNull(runtimeProbe.loadedSqliteVersion());
    assertNull(runtimeProbe.loadedSqlite3mcVersion());
  }

  @Test
  void probe_reportsIncompatibleSqlite3mcRuntimeWithoutThrowing() {
    SqliteRuntime.Probe runtimeProbe =
        SqliteRuntime.probe(
            () -> "managed-only",
            () -> "3.53.0",
            () -> {
              throw new UnsupportedSqliteMultipleCiphersVersionException(
                  "2.3.2", "2.3.3", "managed-only");
            },
            SqliteRuntime::failureDetail);

    assertEquals("managed-only", runtimeProbe.libraryMode());
    assertEquals(SqliteRuntime.Status.INCOMPATIBLE, runtimeProbe.status());
    assertEquals("3.53.0", runtimeProbe.loadedSqliteVersion());
    assertEquals("2.3.2", runtimeProbe.loadedSqlite3mcVersion());
    assertTrue(runtimeProbe.issue().contains("requires SQLite3 Multiple Ciphers 2.3.3"));
  }

  @Test
  void probe_reportsIncompatibleCompileOptionsRuntimeWithoutThrowing() {
    SqliteRuntime.Probe runtimeProbe =
        SqliteRuntime.probe(
            () -> "managed-only",
            () -> {
              throw new UnsupportedSqliteCompileOptionsException(
                  "3.53.0", "2.3.3", "managed-only", List.of("SECURE_DELETE"));
            },
            () -> {
              throw new AssertionError("sqlite3mc version lookup should not run");
            },
            SqliteRuntime::failureDetail);

    assertEquals("managed-only", runtimeProbe.libraryMode());
    assertEquals(SqliteRuntime.Status.INCOMPATIBLE, runtimeProbe.status());
    assertEquals("3.53.0", runtimeProbe.loadedSqliteVersion());
    assertEquals("2.3.3", runtimeProbe.loadedSqlite3mcVersion());
    assertTrue(runtimeProbe.issue().contains("missing required compile options"));
  }

  @Test
  void runtimeProbeAndStatusExposeStableValueSemantics() {
    SqliteRuntime.Probe runtimeProbe =
        new SqliteRuntime.Probe(
            "managed-only", "3.53.0", "2.3.3", SqliteRuntime.Status.READY, "3.53.0", "2.3.3", null);

    assertEquals("managed-only", runtimeProbe.libraryMode());
    assertEquals("3.53.0", runtimeProbe.requiredMinimumSqliteVersion());
    assertEquals("2.3.3", runtimeProbe.requiredSqlite3mcVersion());
    assertEquals(SqliteRuntime.Status.READY, runtimeProbe.status());
    assertEquals("3.53.0", runtimeProbe.loadedSqliteVersion());
    assertEquals("2.3.3", runtimeProbe.loadedSqlite3mcVersion());
    assertNull(runtimeProbe.issue());
    assertEquals(
        runtimeProbe,
        new SqliteRuntime.Probe(
            "managed-only",
            "3.53.0",
            "2.3.3",
            SqliteRuntime.Status.READY,
            "3.53.0",
            "2.3.3",
            null));
    assertEquals(
        runtimeProbe.hashCode(),
        new SqliteRuntime.Probe(
                "managed-only",
                "3.53.0",
                "2.3.3",
                SqliteRuntime.Status.READY,
                "3.53.0",
                "2.3.3",
                null)
            .hashCode());
    assertTrue(runtimeProbe.toString().contains("managed-only"));
    assertEquals("ready", SqliteRuntime.Status.READY.wireValue());
    assertEquals("unavailable", SqliteRuntime.Status.UNAVAILABLE.wireValue());
    assertEquals("incompatible", SqliteRuntime.Status.INCOMPATIBLE.wireValue());
  }

  @Test
  void runtimeProbe_rejectsInvalidStatusSpecificShapes() {
    assertThrows(
        NullPointerException.class,
        () -> new SqliteRuntime.Probe("managed-only", "3.53.0", "2.3.3", null, null, null, "boom"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                "managed-only",
                "3.53.0",
                "2.3.3",
                SqliteRuntime.Status.READY,
                null,
                "2.3.3",
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                "managed-only",
                "3.53.0",
                "2.3.3",
                SqliteRuntime.Status.READY,
                "3.53.0",
                null,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                "managed-only",
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
                "managed-only",
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
                "managed-only",
                "3.53.0",
                "2.3.3",
                SqliteRuntime.Status.UNAVAILABLE,
                null,
                null,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntime.Probe(
                "managed-only",
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
                "managed-only",
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
                "managed-only",
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
  void configuredLibraryTarget_requiresManagedLibraryPath() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> SqliteNativeLibrary.configuredLibraryTarget(null));

    assertTrue(exception.getMessage().contains("bundle launcher"));
    assertTrue(exception.getMessage().contains("FINGRIND_SQLITE_LIBRARY"));
  }

  @Test
  void configuredLibraryTarget_requiresManagedPathAndNormalizesIt() {
    SqliteNativeLibrary.SqliteLibraryTarget libraryTarget =
        SqliteNativeLibrary.configuredLibraryTarget("./build/../sqlite/libsqlite3.so.0");

    assertEquals("managed-only", libraryTarget.mode());
    assertTrue(
        Path.of(libraryTarget.lookupTarget()).endsWith(Path.of("sqlite", "libsqlite3.so.0")));
    assertEquals("managed-only", SqliteNativeLibrary.configuredLibraryMode());
    assertTrue(libraryTarget.toString().contains("managed-only"));
    assertEquals(
        libraryTarget,
        SqliteNativeLibrary.configuredLibraryTarget("./build/../sqlite/libsqlite3.so.0"));
    assertEquals(
        libraryTarget.hashCode(),
        SqliteNativeLibrary.configuredLibraryTarget("./build/../sqlite/libsqlite3.so.0")
            .hashCode());
    assertThrows(
        IllegalStateException.class, () -> SqliteNativeLibrary.configuredLibraryTarget("   "));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SqliteNativeLibrary.SqliteLibraryTarget(" ", "x"));
  }

  @Test
  void configuredLibraryTarget_prefersExplicitEnvironmentLibraryOverBundleHome() {
    SqliteNativeLibrary.SqliteLibraryTarget libraryTarget =
        SqliteNativeLibrary.configuredLibraryTarget(
            "./build/../sqlite/libsqlite3.so.0", tempDirectory.toString());

    assertEquals("managed-only", libraryTarget.mode());
    assertTrue(
        Path.of(libraryTarget.lookupTarget()).endsWith(Path.of("sqlite", "libsqlite3.so.0")));
  }

  @Test
  void configuredLibraryTarget_resolvesBundledLibraryWhenBundleHomeIsPresent() throws IOException {
    Path bundleHomePath = tempDirectory.resolve("fingrind-0.14.0-test");
    Path bundledLibraryPath =
        bundleHomePath.resolve("lib").resolve("native").resolve(expectedNativeLibraryFileName());
    Files.createDirectories(bundledLibraryPath.getParent());
    Files.writeString(bundledLibraryPath, "sqlite3mc", StandardCharsets.UTF_8);

    SqliteNativeLibrary.SqliteLibraryTarget libraryTarget =
        SqliteNativeLibrary.configuredLibraryTarget(null, bundleHomePath.toString());

    assertEquals("managed-only", libraryTarget.mode());
    assertEquals(
        bundledLibraryPath.toAbsolutePath().normalize().toString(), libraryTarget.lookupTarget());
  }

  @Test
  void configuredLibraryTarget_rejectsIncompleteBundleHome() throws IOException {
    Path bundleHomePath = tempDirectory.resolve("fingrind-0.14.0-test");
    Files.createDirectories(bundleHomePath);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteNativeLibrary.configuredLibraryTarget(null, bundleHomePath.toString()));

    assertTrue(exception.getMessage().contains("bundle home"));
    assertTrue(exception.getMessage().contains("FINGRIND_SQLITE_LIBRARY"));
  }

  @Test
  void configuredLibraryTarget_rejectsMissingOrBlankInputsAcrossBundleResolutionModes() {
    IllegalStateException missingEverywhere =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteNativeLibrary.configuredLibraryTarget(null, null));
    IllegalStateException blankConfiguredPath =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteNativeLibrary.configuredLibraryTarget("   ", null));
    IllegalStateException blankBundleHome =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteNativeLibrary.configuredLibraryTarget(null, "   "));

    assertTrue(missingEverywhere.getMessage().contains("bundle launcher"));
    assertTrue(blankConfiguredPath.getMessage().contains("FINGRIND_SQLITE_LIBRARY"));
    assertTrue(blankBundleHome.getMessage().contains("bundle launcher"));
  }

  @Test
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  void supportedNativeLibraryFileName_supportsMacOsLinuxWindowsAndRejectsUnsupportedHosts()
      throws Exception {
    Method method = SqliteNativeLibrary.class.getDeclaredMethod("supportedNativeLibraryFileName");
    method.setAccessible(true);

    String originalOsName = System.getProperty("os.name");
    try {
      System.setProperty("os.name", "Mac OS X");
      assertEquals("libsqlite3.dylib", method.invoke(null));

      System.setProperty("os.name", "Linux");
      assertEquals("libsqlite3.so.0", method.invoke(null));

      System.setProperty("os.name", "Windows 11");
      assertEquals("sqlite3.dll", method.invoke(null));

      System.setProperty("os.name", "FreeBSD");
      InvocationTargetException exception =
          assertThrows(InvocationTargetException.class, () -> method.invoke(null));

      assertTrue(exception.getCause() instanceof IllegalStateException);
      assertTrue(exception.getCause().getMessage().contains("macOS, Linux, and Windows only"));
      assertTrue(exception.getCause().getMessage().contains("FreeBSD"));
    } finally {
      restoreSystemProperty("os.name", originalOsName);
    }
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
            () -> SqliteNativeLibrary.requireSupportedVersion("3.51.0", "managed-only"));

    assertEquals("3.51.0", exception.loadedVersion());
    assertEquals("3.53.0", exception.requiredMinimumVersion());
    assertEquals("managed-only", exception.libraryMode());
  }

  @Test
  void requireSupportedSqlite3mcVersion_rejectsUnexpectedRuntime() {
    UnsupportedSqliteMultipleCiphersVersionException exception =
        assertThrows(
            UnsupportedSqliteMultipleCiphersVersionException.class,
            () -> SqliteNativeLibrary.requireSupportedSqlite3mcVersion("2.3.2", "managed-only"));

    assertEquals("2.3.2", exception.loadedVersion());
    assertEquals("2.3.3", exception.requiredVersion());
    assertEquals("managed-only", exception.libraryMode());
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

    assertDoesNotThrow(
        () ->
            withOpenDatabase(
                bookAccess(bookPath),
                database -> {
                  try (Arena arena = Arena.ofConfined()) {
                    MethodHandle versionHandle =
                        MethodHandles.constant(MemorySegment.class, arena.allocateFrom("3.53.0"));
                    MethodHandle sqlite3mcVersionHandle =
                        MethodHandles.constant(
                            MemorySegment.class,
                            arena.allocateFrom("SQLite3 Multiple Ciphers 2.3.3"));

                    assertFalse(SqliteNativeLibrary.errorMessage(database.handle()).isBlank());
                    assertEquals("3.53.0", SqliteNativeLibrary.sqliteVersion(versionHandle));
                    assertEquals(
                        "2.3.3",
                        SqliteNativeLibrary.sqlite3MultipleCiphersVersion(sqlite3mcVersionHandle));
                    assertDoesNotThrow(
                        () ->
                            SqliteNativeLibrary.freeSqliteBuffer(
                                null,
                                MethodHandles.dropArguments(
                                    MethodHandles.empty(
                                        java.lang.invoke.MethodType.methodType(void.class)),
                                    0,
                                    MemorySegment.class)));
                    assertDoesNotThrow(
                        () ->
                            SqliteNativeLibrary.freeSqliteBuffer(
                                MemorySegment.NULL,
                                MethodHandles.dropArguments(
                                    MethodHandles.empty(
                                        java.lang.invoke.MethodType.methodType(void.class)),
                                    0,
                                    MemorySegment.class)));
                  }
                }));
  }

  @Test
  void errorString_convenienceOverload_readsConfiguredApi() {
    assertFalse(SqliteNativeLibrary.errorString(14).isBlank());
  }

  @Test
  void requireSupportedCompileOptions_rejectsMissingHardeningOptions() {
    UnsupportedSqliteCompileOptionsException exception =
        assertThrows(
            UnsupportedSqliteCompileOptionsException.class,
            () ->
                SqliteNativeLibrary.requireSupportedCompileOptions(
                    constantMethodHandle(0, MemorySegment.class),
                    "3.53.0",
                    "2.3.3",
                    "managed-only"));

    assertEquals("3.53.0", exception.loadedSqliteVersion());
    assertEquals("2.3.3", exception.loadedSqlite3mcVersion());
    assertEquals("managed-only", exception.libraryMode());
    assertEquals(SqliteRuntime.REQUIRED_SQLITE_COMPILE_OPTIONS, exception.missingCompileOptions());
  }

  @Test
  void compileOptionUsed_wrapsUnexpectedThrowableFromNativeInvocation() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteNativeLibrary.compileOptionUsed(
                    throwingMethodHandle(
                        new IllegalStateException("boom"), int.class, MemorySegment.class),
                    "SECURE_DELETE"));

    assertEquals("Failed to read the SQLite compile option: SECURE_DELETE", exception.getMessage());
    assertEquals("boom", exception.getCause().getMessage());
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
  void open_rejectsNonKeyFileAccessSelection() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                SqliteNativeLibrary.open(
                    new BookAccess(
                        tempDirectory.resolve("stdin-access.sqlite"),
                        BookAccess.PassphraseSource.StandardInput.INSTANCE)));

    assertEquals(
        "SQLite same-package file-backed open requires a --book-key-file access selection.",
        exception.getMessage());
  }

  @Test
  void openExecutePrepareAndClose_roundTripThroughSystemLibrary() throws Exception {
    Path bookPath = tempDirectory.resolve("native-round-trip.sqlite");

    assertDoesNotThrow(
        () ->
            withOpenDatabase(
                bookAccess(bookPath),
                database -> {
                  database.executeStatement(
                      "create table sample (id integer not null, note text null)");

                  try (SqliteNativeStatement insert =
                      SqliteNativeLibrary.prepare(
                          database, "insert into sample (id, note) values (?, ?)")) {
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
                }));
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

    withOpenDatabase(
        bookAccess(bookPath, TEST_BOOK_KEY),
        database -> database.executeStatement("create table sample (id integer not null)"));

    SqliteNativeException exception =
        assertThrows(
            SqliteNativeException.class,
            () -> SqliteNativeLibrary.open(bookAccess(bookPath, "different-book-key")));

    assertTrue(exception.resultName().contains("SQLITE_NOTADB"));
  }

  @Test
  void openOverloadAndRekey_rotateBookPassphrase() throws Exception {
    Path bookPath = tempDirectory.resolve("rekey-native.sqlite");

    try (SqliteBookPassphrase initialPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "initial native passphrase", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database = SqliteNativeLibrary.open(bookPath, initialPassphrase)) {
      database.executeStatement("create table sample (id integer primary key, note text not null)");
      database.executeStatement("insert into sample (id, note) values (1, 'ok')");

      try (SqliteBookPassphrase replacementPassphrase =
          SqliteBookPassphrase.fromCharacters(
              "replacement native passphrase", "rotated-key".toCharArray())) {
        SqliteNativeLibrary.rekey(database, replacementPassphrase);
      }
    }

    try (SqliteBookPassphrase replacementPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "replacement native passphrase", "rotated-key".toCharArray());
        SqliteNativeDatabase reopened = SqliteNativeLibrary.open(bookPath, replacementPassphrase)) {
      try (SqliteNativeStatement statement =
          SqliteNativeLibrary.prepare(reopened, "select count(*) from sample")) {
        assertEquals(SqliteNativeLibrary.SQLITE_ROW, statement.step());
        assertEquals(1, statement.columnInt(0));
      }
    }

    try (SqliteBookPassphrase oldPassphrase =
        SqliteBookPassphrase.fromCharacters(
            "stale native passphrase", TEST_BOOK_KEY.toCharArray())) {
      SqliteNativeException exception =
          assertThrows(
              SqliteNativeException.class, () -> SqliteNativeLibrary.open(bookPath, oldPassphrase));

      assertEquals("SQLITE_NOTADB", exception.resultName());
    }
  }

  @Test
  void rekey_rejectsNullArguments() throws Exception {
    Path bookPath = tempDirectory.resolve("rekey-nulls.sqlite");

    assertThrows(NullPointerException.class, () -> SqliteNativeLibrary.rekey(null, null));
    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "rekey null passphrase", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database = SqliteNativeLibrary.open(bookPath, passphrase)) {
      assertThrows(NullPointerException.class, () -> SqliteNativeLibrary.rekey(database, null));
    }
  }

  @Test
  void rekey_rethrowsSqliteNativeExceptionFromNativeFailure() throws Exception {
    Path bookPath = tempDirectory.resolve("rekey-native-failure.sqlite");

    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "native failure passphrase", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database = SqliteNativeLibrary.open(bookPath, passphrase);
        AutoCloseable ignored =
            SqliteNativeLibrary.overrideSqlite3RekeyHandleForTesting(
                constantMethodHandle(14, MemorySegment.class, MemorySegment.class, int.class))) {
      try (SqliteBookPassphrase replacementPassphrase =
          SqliteBookPassphrase.fromCharacters(
              "native failure replacement", "rotated-key".toCharArray())) {
        SqliteNativeException exception =
            assertThrows(
                SqliteNativeException.class,
                () -> SqliteNativeLibrary.rekey(database, replacementPassphrase));

        assertEquals("SQLITE_CANTOPEN", exception.resultName());
      }
    }
  }

  @Test
  void rekey_wrapsUnexpectedThrowableFromNativeInvocation() throws Exception {
    Path bookPath = tempDirectory.resolve("rekey-throwable.sqlite");

    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "throwable passphrase", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database = SqliteNativeLibrary.open(bookPath, passphrase);
        AutoCloseable ignored =
            SqliteNativeLibrary.overrideSqlite3RekeyHandleForTesting(
                throwingMethodHandle(
                    new IllegalStateException("boom"),
                    int.class,
                    MemorySegment.class,
                    MemorySegment.class,
                    int.class))) {
      try (SqliteBookPassphrase replacementPassphrase =
          SqliteBookPassphrase.fromCharacters(
              "throwable replacement", "rotated-key".toCharArray())) {
        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () -> SqliteNativeLibrary.rekey(database, replacementPassphrase));

        assertTrue(
            exception
                .getMessage()
                .contains(
                    "Failed to rekey the FinGrind SQLite book with passphrase material from"));
        assertEquals("boom", exception.getCause().getMessage());
      }
    }
  }

  @Test
  void open_propagatesBookKeyReadFailureBeforeNativeBridgeOpen() {
    Path bookPath = tempDirectory.resolve("missing-key.sqlite");
    Path missingKeyPath = tempDirectory.resolve("missing.key");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteNativeLibrary.open(
                    new BookAccess(
                        bookPath, new BookAccess.PassphraseSource.KeyFile(missingKeyPath))));

    assertTrue(exception.getMessage().contains("Failed to read the FinGrind book key file"));
  }

  @Test
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  void applyKey_wrapsUnexpectedThrowableFromNativeInvocation() throws Exception {
    Method method =
        SqliteNativeLibrary.class.getDeclaredMethod(
            "applyKey",
            MemorySegment.class,
            SqliteBookPassphrase.class,
            Class.forName("dev.erst.fingrind.sqlite.SqliteNativeLibrary$SqliteApi"),
            Arena.class);
    method.setAccessible(true);

    Path keyFile = tempDirectory.resolve("apply-key.key");
    writeSecureKeyFile(keyFile, TEST_BOOK_KEY);

    try (SqliteBookPassphrase keyMaterial = SqliteBookKeyFile.load(keyFile);
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
              .contains("Failed to apply the FinGrind SQLite book passphrase from"));
    }
  }

  @Test
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  void applyKey_rethrowsSqliteNativeException() throws Exception {
    Method method =
        SqliteNativeLibrary.class.getDeclaredMethod(
            "applyKey",
            MemorySegment.class,
            SqliteBookPassphrase.class,
            Class.forName("dev.erst.fingrind.sqlite.SqliteNativeLibrary$SqliteApi"),
            Arena.class);
    method.setAccessible(true);

    Path keyFile = tempDirectory.resolve("apply-key-native-failure.key");
    writeSecureKeyFile(keyFile, TEST_BOOK_KEY);

    try (SqliteBookPassphrase keyMaterial = SqliteBookKeyFile.load(keyFile);
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
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  void open_wrapsUnexpectedThrowableFromOpenInvocation() throws Exception {
    Method method =
        SqliteNativeLibrary.class.getDeclaredMethod(
            "open",
            Path.class,
            SqliteBookPassphrase.class,
            SqliteNativeLibrary.OpenMode.class,
            Class.forName("dev.erst.fingrind.sqlite.SqliteNativeLibrary$SqliteApi"));
    method.setAccessible(true);

    Object[] sqliteApiArguments = defaultSqliteApiArguments();
    sqliteApiArguments[1] =
        throwingMethodHandle(
            new IllegalStateException("boom"),
            int.class,
            MemorySegment.class,
            MemorySegment.class,
            int.class,
            MemorySegment.class);
    Object sqliteApi = buildSqliteApi(sqliteApiArguments);

    try (SqliteBookPassphrase passphrase =
        SqliteBookPassphrase.fromCharacters("native open throwable", TEST_BOOK_KEY.toCharArray())) {
      InvocationTargetException exception =
          assertThrows(
              InvocationTargetException.class,
              () ->
                  method.invoke(
                      null,
                      tempDirectory.resolve("open-throwable.sqlite"),
                      passphrase,
                      SqliteNativeLibrary.OpenMode.READ_WRITE_CREATE,
                      sqliteApi));

      assertTrue(exception.getCause() instanceof IllegalStateException);
      assertEquals(
          "Failed to open the SQLite native library bridge.", exception.getCause().getMessage());
      assertEquals("boom", exception.getCause().getCause().getMessage());
    }
  }

  @Test
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  void open_closesNativeHandleWhenKeyValidationFails() throws Exception {
    Method method =
        SqliteNativeLibrary.class.getDeclaredMethod(
            "open",
            Path.class,
            SqliteBookPassphrase.class,
            SqliteNativeLibrary.OpenMode.class,
            Class.forName("dev.erst.fingrind.sqlite.SqliteNativeLibrary$SqliteApi"));
    method.setAccessible(true);
    AtomicInteger closeCalls = new AtomicInteger();

    try (Arena arena = Arena.ofConfined();
        SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "native open validation failure", TEST_BOOK_KEY.toCharArray())) {
      MemorySegment fakeDatabaseHandle = arena.allocate(1);
      Object[] sqliteApiArguments = defaultSqliteApiArguments();
      sqliteApiArguments[1] =
          MethodHandles.insertArguments(
              MethodHandles.lookup()
                  .findStatic(
                      SqliteNativeLibraryTest.class,
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
      sqliteApiArguments[2] =
          MethodHandles.insertArguments(
              MethodHandles.lookup()
                  .findStatic(
                      SqliteNativeLibraryTest.class,
                      "recordCloseCall",
                      java.lang.invoke.MethodType.methodType(
                          int.class, AtomicInteger.class, MemorySegment.class)),
              0,
              closeCalls);
      sqliteApiArguments[8] =
          constantMethodHandle(
              26,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class,
              MemorySegment.class);
      sqliteApiArguments[20] =
          constantMethodHandle(arena.allocateFrom("file is not a database"), int.class);
      Object sqliteApi = buildSqliteApi(sqliteApiArguments);

      InvocationTargetException exception =
          assertThrows(
              InvocationTargetException.class,
              () ->
                  method.invoke(
                      null,
                      tempDirectory.resolve("open-validation-failure.sqlite"),
                      passphrase,
                      SqliteNativeLibrary.OpenMode.READ_WRITE_CREATE,
                      sqliteApi));

      assertTrue(exception.getCause() instanceof SqliteNativeException);
      assertEquals("SQLITE_NOTADB", ((SqliteNativeException) exception.getCause()).resultName());
      assertEquals(1, closeCalls.get());
    }
  }

  @Test
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  void open_closesNativeHandleWhenConfigurationThrowsUnexpectedly() throws Exception {
    Method method =
        SqliteNativeLibrary.class.getDeclaredMethod(
            "open",
            Path.class,
            SqliteBookPassphrase.class,
            SqliteNativeLibrary.OpenMode.class,
            Class.forName("dev.erst.fingrind.sqlite.SqliteNativeLibrary$SqliteApi"));
    method.setAccessible(true);
    AtomicInteger closeCalls = new AtomicInteger();

    try (Arena arena = Arena.ofConfined();
        SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "native open configuration failure", TEST_BOOK_KEY.toCharArray())) {
      MemorySegment fakeDatabaseHandle = arena.allocate(1);
      Object[] sqliteApiArguments = defaultSqliteApiArguments();
      sqliteApiArguments[1] =
          MethodHandles.insertArguments(
              MethodHandles.lookup()
                  .findStatic(
                      SqliteNativeLibraryTest.class,
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
      sqliteApiArguments[2] =
          MethodHandles.insertArguments(
              MethodHandles.lookup()
                  .findStatic(
                      SqliteNativeLibraryTest.class,
                      "recordCloseCall",
                      java.lang.invoke.MethodType.methodType(
                          int.class, AtomicInteger.class, MemorySegment.class)),
              0,
              closeCalls);
      sqliteApiArguments[6] =
          throwingMethodHandle(
              new IllegalStateException("busy-timeout boom"),
              int.class,
              MemorySegment.class,
              int.class);
      Object sqliteApi = buildSqliteApi(sqliteApiArguments);

      InvocationTargetException exception =
          assertThrows(
              InvocationTargetException.class,
              () ->
                  method.invoke(
                      null,
                      tempDirectory.resolve("open-configuration-failure.sqlite"),
                      passphrase,
                      SqliteNativeLibrary.OpenMode.READ_WRITE_CREATE,
                      sqliteApi));

      assertTrue(exception.getCause() instanceof IllegalStateException);
      assertEquals(
          "Failed to open the SQLite native library bridge.", exception.getCause().getMessage());
      assertEquals("busy-timeout boom", exception.getCause().getCause().getMessage());
      assertEquals(1, closeCalls.get());
    }
  }

  @Test
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  void open_preservesNativeOpenFailureWhenCleanupCloseThrows() throws Exception {
    Method method =
        SqliteNativeLibrary.class.getDeclaredMethod(
            "open",
            Path.class,
            SqliteBookPassphrase.class,
            SqliteNativeLibrary.OpenMode.class,
            Class.forName("dev.erst.fingrind.sqlite.SqliteNativeLibrary$SqliteApi"));
    method.setAccessible(true);
    AtomicInteger closeCalls = new AtomicInteger();

    try (Arena arena = Arena.ofConfined();
        SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "native open cleanup failure", TEST_BOOK_KEY.toCharArray())) {
      MemorySegment fakeDatabaseHandle = arena.allocate(1);
      Object[] sqliteApiArguments = defaultSqliteApiArguments();
      sqliteApiArguments[1] =
          MethodHandles.insertArguments(
              MethodHandles.lookup()
                  .findStatic(
                      SqliteNativeLibraryTest.class,
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
      sqliteApiArguments[2] =
          MethodHandles.insertArguments(
              MethodHandles.lookup()
                  .findStatic(
                      SqliteNativeLibraryTest.class,
                      "recordCloseCallThenThrow",
                      java.lang.invoke.MethodType.methodType(
                          int.class, AtomicInteger.class, MemorySegment.class)),
              0,
              closeCalls);
      sqliteApiArguments[20] = constantMethodHandle(arena.allocateFrom("open boom"), int.class);
      Object sqliteApi = buildSqliteApi(sqliteApiArguments);

      InvocationTargetException exception =
          assertThrows(
              InvocationTargetException.class,
              () ->
                  method.invoke(
                      null,
                      tempDirectory.resolve("open-native-failure.sqlite"),
                      passphrase,
                      SqliteNativeLibrary.OpenMode.READ_WRITE_CREATE,
                      sqliteApi));

      assertTrue(exception.getCause() instanceof SqliteNativeException);
      assertEquals("SQLITE_CANTOPEN: open boom", exception.getCause().getMessage());
      assertEquals(1, closeCalls.get());
    }
  }

  @Test
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  void open_preservesNativeOpenFailureWhenNoHandleIsReturned() throws Exception {
    Method method =
        SqliteNativeLibrary.class.getDeclaredMethod(
            "open",
            Path.class,
            SqliteBookPassphrase.class,
            SqliteNativeLibrary.OpenMode.class,
            Class.forName("dev.erst.fingrind.sqlite.SqliteNativeLibrary$SqliteApi"));
    method.setAccessible(true);
    AtomicInteger closeCalls = new AtomicInteger();

    try (Arena arena = Arena.ofConfined();
        SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "native open null handle failure", TEST_BOOK_KEY.toCharArray())) {
      Object[] sqliteApiArguments = defaultSqliteApiArguments();
      sqliteApiArguments[1] =
          constantMethodHandle(
              14, MemorySegment.class, MemorySegment.class, int.class, MemorySegment.class);
      sqliteApiArguments[2] =
          MethodHandles.insertArguments(
              MethodHandles.lookup()
                  .findStatic(
                      SqliteNativeLibraryTest.class,
                      "recordCloseCall",
                      java.lang.invoke.MethodType.methodType(
                          int.class, AtomicInteger.class, MemorySegment.class)),
              0,
              closeCalls);
      sqliteApiArguments[20] = constantMethodHandle(arena.allocateFrom("open boom"), int.class);
      Object sqliteApi = buildSqliteApi(sqliteApiArguments);

      InvocationTargetException exception =
          assertThrows(
              InvocationTargetException.class,
              () ->
                  method.invoke(
                      null,
                      tempDirectory.resolve("open-no-handle-failure.sqlite"),
                      passphrase,
                      SqliteNativeLibrary.OpenMode.READ_WRITE_CREATE,
                      sqliteApi));

      assertTrue(exception.getCause() instanceof SqliteNativeException);
      assertEquals("SQLITE_CANTOPEN: open boom", exception.getCause().getMessage());
      assertEquals(0, closeCalls.get());
    }
  }

  @Test
  void close_rethrowsSqliteNativeExceptionFromNativeFailure() throws Exception {
    Path bookPath = tempDirectory.resolve("close-native-failure.sqlite");
    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "close native failure", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database = SqliteNativeLibrary.open(bookPath, passphrase)) {
      try (AutoCloseable ignored =
          SqliteNativeLibrary.overrideSqlite3CloseV2HandleForTesting(
              constantMethodHandle(14, MemorySegment.class))) {
        SqliteNativeException exception =
            assertThrows(SqliteNativeException.class, database::close);

        assertEquals("SQLITE_CANTOPEN", exception.resultName());
      }
    }
  }

  @Test
  void close_keepsActiveConnectionCountUntilSuccessfulRetry() throws Exception {
    Path bookPath = tempDirectory.resolve("close-active-count.sqlite");
    int initialActiveConnections = SqliteNativeLibrary.activeConnectionCount();

    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters("close active count", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database = SqliteNativeLibrary.open(bookPath, passphrase)) {
      assertEquals(initialActiveConnections + 1, SqliteNativeLibrary.activeConnectionCount());

      try (AutoCloseable ignored =
          SqliteNativeLibrary.overrideSqlite3CloseV2HandleForTesting(
              constantMethodHandle(14, MemorySegment.class))) {
        assertThrows(SqliteNativeException.class, database::close);
        assertEquals(initialActiveConnections + 1, SqliteNativeLibrary.activeConnectionCount());
      }

      assertEquals(initialActiveConnections + 1, SqliteNativeLibrary.activeConnectionCount());
    }

    assertEquals(initialActiveConnections, SqliteNativeLibrary.activeConnectionCount());
  }

  @Test
  void close_wrapsUnexpectedThrowableFromNativeInvocation() throws Exception {
    Path bookPath = tempDirectory.resolve("close-throwable.sqlite");
    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters("close throwable", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database = SqliteNativeLibrary.open(bookPath, passphrase)) {
      try (AutoCloseable ignored =
          SqliteNativeLibrary.overrideSqlite3CloseV2HandleForTesting(
              throwingMethodHandle(
                  new IllegalStateException("boom"), int.class, MemorySegment.class))) {
        IllegalStateException exception =
            assertThrows(IllegalStateException.class, database::close);

        assertEquals("Failed to close the SQLite native library bridge.", exception.getMessage());
        assertEquals("boom", exception.getCause().getMessage());
      }
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
  void shutdownIfQuiescent_runsShutdownOnlyWhenNoConnectionsRemain() throws Throwable {
    AtomicInteger shutdownCalls = new AtomicInteger();
    MethodHandle shutdownHandle =
        MethodHandles.insertArguments(
            MethodHandles.lookup()
                .findStatic(
                    SqliteNativeLibraryTest.class,
                    "recordShutdownCall",
                    java.lang.invoke.MethodType.methodType(int.class, AtomicInteger.class)),
            0,
            shutdownCalls);

    SqliteNativeLibrary.shutdownIfQuiescent(shutdownHandle, 1);
    assertEquals(0, shutdownCalls.get());

    SqliteNativeLibrary.shutdownIfQuiescent(shutdownHandle, 0);
    assertEquals(1, shutdownCalls.get());
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
            "requireOpenConfigurationSuccess", int.class, sqliteApiClass);
    method.setAccessible(true);

    try (Arena arena = Arena.ofConfined()) {
      Object sqliteApi =
          sqliteApi(
              constantMethodHandle(0, MemorySegment.class),
              constantMethodHandle(arena.allocateFrom("boom"), MemorySegment.class),
              constantMethodHandle(arena.allocateFrom("boom"), int.class),
              constantMethodHandle(14, MemorySegment.class));

      InvocationTargetException exception =
          assertThrows(InvocationTargetException.class, () -> method.invoke(null, 14, sqliteApi));

      assertTrue(exception.getCause() instanceof SqliteNativeException);
      SqliteNativeException sqliteException = (SqliteNativeException) exception.getCause();
      assertEquals(14, sqliteException.resultCode());
      assertEquals("SQLITE_CANTOPEN", sqliteException.resultName());
      assertEquals("SQLITE_CANTOPEN: boom", sqliteException.getMessage());
    }
  }

  @Test
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  void requireOpenConfigurationSuccess_preservesNativeFailureMessage() throws Exception {
    Class<?> sqliteApiClass =
        Class.forName("dev.erst.fingrind.sqlite.SqliteNativeLibrary$SqliteApi");
    Method method =
        SqliteNativeLibrary.class.getDeclaredMethod(
            "requireOpenConfigurationSuccess", int.class, sqliteApiClass);
    method.setAccessible(true);

    try (Arena arena = Arena.ofConfined()) {
      Object sqliteApi =
          sqliteApi(
              constantMethodHandle(0, MemorySegment.class),
              constantMethodHandle(arena.allocateFrom("boom"), MemorySegment.class),
              constantMethodHandle(arena.allocateFrom("boom"), int.class),
              constantMethodHandle(14, MemorySegment.class));

      InvocationTargetException exception =
          assertThrows(InvocationTargetException.class, () -> method.invoke(null, 14, sqliteApi));

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
            "requireOpenConfigurationSuccess", int.class, sqliteApiClass);
    method.setAccessible(true);

    try (Arena arena = Arena.ofConfined()) {
      Object sqliteApi =
          sqliteApi(
              constantMethodHandle(0, MemorySegment.class),
              constantMethodHandle(arena.allocateFrom("unused"), MemorySegment.class),
              constantMethodHandle(arena.allocateFrom(""), int.class),
              constantMethodHandle(14, MemorySegment.class));

      InvocationTargetException exception =
          assertThrows(InvocationTargetException.class, () -> method.invoke(null, 14, sqliteApi));

      assertTrue(exception.getCause() instanceof SqliteNativeException);
      assertEquals("SQLITE_CANTOPEN", exception.getCause().getMessage());
    }
  }

  private static Object sqliteApi(
      MethodHandle keyHandle,
      MethodHandle closeHandle,
      MethodHandle errorMessageHandle,
      MethodHandle errorStringHandle,
      MethodHandle extendedErrcodeHandle)
      throws ReflectiveOperationException {
    Object[] sqliteApiArguments = defaultSqliteApiArguments();
    sqliteApiArguments[2] = closeHandle;
    sqliteApiArguments[3] = keyHandle;
    sqliteApiArguments[19] = errorMessageHandle;
    sqliteApiArguments[20] = errorStringHandle;
    sqliteApiArguments[21] = extendedErrcodeHandle;
    return buildSqliteApi(sqliteApiArguments);
  }

  private static Object sqliteApi(
      MethodHandle closeHandle,
      MethodHandle errorMessageHandle,
      MethodHandle errorStringHandle,
      MethodHandle extendedErrcodeHandle)
      throws ReflectiveOperationException {
    Object[] sqliteApiArguments = defaultSqliteApiArguments();
    sqliteApiArguments[2] = closeHandle;
    sqliteApiArguments[19] = errorMessageHandle;
    sqliteApiArguments[20] = errorStringHandle;
    sqliteApiArguments[21] = extendedErrcodeHandle;
    return buildSqliteApi(sqliteApiArguments);
  }

  private static Object[] defaultSqliteApiArguments() {
    return new Object[] {
      Arena.ofShared(),
      constantMethodHandle(
          0, MemorySegment.class, MemorySegment.class, int.class, MemorySegment.class),
      constantMethodHandle(0, MemorySegment.class),
      constantMethodHandle(0, MemorySegment.class, MemorySegment.class, int.class),
      constantMethodHandle(0, MemorySegment.class, MemorySegment.class, int.class),
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
          0,
          MemorySegment.class,
          MemorySegment.class,
          int.class,
          MemorySegment.class,
          MemorySegment.class),
      constantMethodHandle(0, MemorySegment.class, int.class),
      constantMethodHandle(0, MemorySegment.class, int.class, int.class),
      constantMethodHandle(
          0, MemorySegment.class, int.class, MemorySegment.class, int.class, MemorySegment.class),
      constantMethodHandle(0, MemorySegment.class),
      constantMethodHandle(0, MemorySegment.class),
      constantMethodHandle(MemorySegment.NULL, MemorySegment.class, int.class),
      constantMethodHandle(0, MemorySegment.class, int.class),
      constantMethodHandle(0, MemorySegment.class, int.class),
      constantMethodHandle(MemorySegment.NULL, MemorySegment.class),
      constantMethodHandle(MemorySegment.NULL, int.class),
      constantMethodHandle(0, MemorySegment.class),
      "3.53.0",
      "2.3.3"
    };
  }

  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  private static Object buildSqliteApi(Object[] sqliteApiArguments)
      throws ReflectiveOperationException {
    Class<?> sqliteApiClass =
        Class.forName("dev.erst.fingrind.sqlite.SqliteNativeLibrary$SqliteApi");
    var constructor = sqliteApiClass.getDeclaredConstructors()[0];
    constructor.setAccessible(true);
    return constructor.newInstance(sqliteApiArguments);
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
      writeSecureKeyFile(keyPath, keyText);
      return new BookAccess(bookPath, new BookAccess.PassphraseSource.KeyFile(keyPath));
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

  private static String expectedNativeLibraryFileName() {
    String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (operatingSystem.contains("mac")) {
      return "libsqlite3.dylib";
    }
    if (operatingSystem.contains("linux")) {
      return "libsqlite3.so.0";
    }
    if (operatingSystem.contains("windows")) {
      return "sqlite3.dll";
    }
    throw new IllegalStateException(
        "Unsupported test operating system: " + System.getProperty("os.name"));
  }

  private static void restoreSystemProperty(String key, String value) {
    if (value == null) {
      System.clearProperty(key);
      return;
    }
    System.setProperty(key, value);
  }

  @SuppressWarnings("unused")
  private static int recordShutdownCall(AtomicInteger shutdownCalls) {
    return shutdownCalls.incrementAndGet();
  }

  @SuppressWarnings("unused")
  private static int recordCloseCall(AtomicInteger closeCalls, MemorySegment databaseHandle) {
    return closeCalls.incrementAndGet() == 1 && !databaseHandle.equals(MemorySegment.NULL) ? 0 : 14;
  }

  @SuppressWarnings("unused")
  private static int recordCloseCallThenThrow(
      AtomicInteger closeCalls, MemorySegment databaseHandle) {
    closeCalls.incrementAndGet();
    throw new IllegalStateException(
        "close boom for " + (databaseHandle.equals(MemorySegment.NULL) ? "null" : "handle"));
  }

  @SuppressWarnings("unused")
  private static int openWithDatabaseHandle(
      MemorySegment openedHandle,
      MemorySegment filename,
      MemorySegment databasePointer,
      int flags,
      MemorySegment vfs) {
    databasePointer.set(ValueLayout.ADDRESS, 0, openedHandle);
    return 0;
  }

  @SuppressWarnings("unused")
  private static int failOpenWithDatabaseHandle(
      MemorySegment openedHandle,
      MemorySegment filename,
      MemorySegment databasePointer,
      int flags,
      MemorySegment vfs) {
    databasePointer.set(ValueLayout.ADDRESS, 0, openedHandle);
    return 14;
  }

  private static void writeSecureKeyFile(Path keyPath, String keyText) throws IOException {
    if (Files.notExists(keyPath)) {
      SqliteBookKeyFileGenerator.generate(keyPath);
    } else {
      SqliteBookKeyFileSecurity.requireSecureKeyFile(keyPath);
    }
    Files.writeString(keyPath, keyText, StandardCharsets.UTF_8);
  }

  private static void withOpenDatabase(BookAccess bookAccess, SqliteDatabaseAction action)
      throws SqliteNativeException {
    try (SqliteNativeDatabase database = SqliteNativeLibrary.open(bookAccess)) {
      action.run(database);
    }
  }

  /** Performs one checked action against a temporary native SQLite handle. */
  @FunctionalInterface
  private interface SqliteDatabaseAction {
    void run(SqliteNativeDatabase database) throws SqliteNativeException;
  }
}
