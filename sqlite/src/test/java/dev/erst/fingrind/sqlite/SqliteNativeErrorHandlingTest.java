package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Tests for the SQLite FFM binding layer. */
class SqliteNativeErrorHandlingTest extends SqliteNativeBridgeTestSupport {
  @Test
  void errorMessage_returnsGenericTextForNullHandle() {
    try (Arena arena = Arena.ofConfined()) {
      MethodHandle errorHandle =
          MethodHandles.dropArguments(
              MethodHandles.constant(MemorySegment.class, arena.allocateFrom("boom")),
              0,
              MemorySegment.class);
      assertEquals("SQLite native failure.", SqliteNativeErrors.errorMessage(null, errorHandle));
      assertEquals(
          "SQLite native failure.",
          SqliteNativeErrors.errorMessage(MemorySegment.NULL, errorHandle));
    }
    assertEquals("SQLite native failure.", SqliteNativeErrors.errorMessage(null));
    assertEquals("SQLite native failure.", SqliteNativeErrors.errorMessage(MemorySegment.NULL));
    assertEquals(
        "SQLite native failure.",
        SqliteNativeErrors.scriptErrorMessage(MemorySegment.NULL, MemorySegment.NULL));
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
      assertEquals("boom", SqliteNativeErrors.errorMessage(fakeHandle, errorHandle));
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
                        MethodHandles.constant(MemorySegment.class, arena.allocateFrom("3.53.3"));
                    MethodHandle sqlite3mcVersionHandle =
                        MethodHandles.constant(
                            MemorySegment.class,
                            arena.allocateFrom("SQLite3 Multiple Ciphers 2.3.6"));
                    assertFalse(database.diagnostics().errorMessage().isBlank());
                    assertFalse(SqliteNativeErrors.errorMessage(database.handle()).isBlank());
                    assertEquals(
                        "3.53.3", SqliteNativeRuntimeMetadata.sqliteVersion(versionHandle));
                    assertEquals(
                        "2.3.6",
                        SqliteNativeRuntimeMetadata.sqlite3MultipleCiphersVersion(
                            sqlite3mcVersionHandle));
                    assertEquals(
                        SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
                        SqliteNativeRuntimeMetadata.sqliteSourceId(
                            MethodHandles.constant(
                                MemorySegment.class,
                                arena.allocateFrom(SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID))));
                    assertDoesNotThrow(
                        () ->
                            SqliteNativeErrors.freeSqliteBuffer(
                                NullTestSupport.nullOf(MemorySegment.class),
                                MethodHandles.dropArguments(
                                    MethodHandles.empty(
                                        java.lang.invoke.MethodType.methodType(void.class)),
                                    0,
                                    MemorySegment.class)));
                    assertDoesNotThrow(
                        () ->
                            SqliteNativeErrors.freeSqliteBuffer(
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
    assertFalse(SqliteNativeErrors.errorString(14).isBlank());
  }

  @Test
  void wrapperDelegates_supportSuccessfulFacadeCalls() {
    try (Arena arena = Arena.ofConfined()) {
      MethodHandle errorMessageHandle =
          constantMethodHandle(arena.allocateFrom("boom"), MemorySegment.class);
      MethodHandle errorStrlenHandle = constantMethodHandle(4L, MemorySegment.class);
      MethodHandle sqliteVersionHandle = constantMethodHandle(arena.allocateFrom("3.53.3"));
      MethodHandle sqliteVersionStrlenHandle = constantMethodHandle(6L, MemorySegment.class);
      MethodHandle sqlite3mcVersionHandle =
          constantMethodHandle(arena.allocateFrom("SQLite3 Multiple Ciphers 2.3.6"));
      MethodHandle sqlite3mcVersionStrlenHandle =
          constantMethodHandle(
              (long) "SQLite3 Multiple Ciphers 2.3.6".length(), MemorySegment.class);
      assertEquals(
          "boom",
          SqliteNativeErrors.errorMessage(
              MemorySegment.ofAddress(1L), errorMessageHandle, errorStrlenHandle));
      assertEquals(
          "3.53.3",
          SqliteNativeRuntimeMetadata.sqliteVersion(
              sqliteVersionHandle, sqliteVersionStrlenHandle));
      assertEquals(
          "2.3.6",
          SqliteNativeRuntimeMetadata.sqlite3MultipleCiphersVersion(
              sqlite3mcVersionHandle, sqlite3mcVersionStrlenHandle));
      assertEquals(
          SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
          SqliteNativeRuntimeMetadata.sqliteSourceId(
              constantMethodHandle(arena.allocateFrom(SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID)),
              constantMethodHandle(
                  (long) SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID.length(), MemorySegment.class)));
      assertEquals(
          "3.53.3",
          SqliteNativeCompatibilityPolicy.requireSupportedVersion("3.53.3", "managed-only"));
      assertEquals(
          "2.3.6",
          SqliteNativeCompatibilityPolicy.requireSupportedSqlite3mcVersion(
              "2.3.6", "managed-only"));
      assertDoesNotThrow(
          () ->
              SqliteNativeCompatibilityPolicy.requireSupportedCompileOptions(
                  compileOptionPresenceHandle(), "3.53.3", "2.3.6", "managed-only"));
      assertEquals("ok", SqliteNativeBootstrap.initialize(() -> "ok"));
    }
  }

  @Test
  void compileOptionUsed_wrapsUnexpectedThrowableFromNativeInvocation() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteNativeCompatibilityPolicy.compileOptionUsed(
                    throwingMethodHandle(
                        new IllegalStateException("boom"), int.class, MemorySegment.class),
                    "SECURE_DELETE"));
    assertEquals("Failed to read the SQLite compile option: SECURE_DELETE", exception.getMessage());
    assertEquals("boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
  }

  @Test
  void compileOptionUsed_reportsEnabledCompileOption() {
    assertTrue(
        SqliteNativeCompatibilityPolicy.compileOptionUsed(
            constantMethodHandle(1, MemorySegment.class), "SECURE_DELETE"));
  }

  @Test
  void compileOptionUsed_rethrowsErrorsFromNativeInvocation() {
    AssertionError error =
        assertThrows(
            AssertionError.class,
            () ->
                SqliteNativeCompatibilityPolicy.compileOptionUsed(
                    throwingMethodHandle(
                        new AssertionError("boom"), int.class, MemorySegment.class),
                    "SECURE_DELETE"));
    assertEquals("boom", error.getMessage());
  }

  @Test
  void errorString_returnsResultNameWhenPointerIsJavaNull() {
    MethodHandle nullErrorStringHandle =
        MethodHandles.dropArguments(
            MethodHandles.constant(MemorySegment.class, null), 0, int.class);
    assertEquals(
        "SQLITE_CANTOPEN",
        SqliteNativeErrors.errorString(
            14, nullErrorStringHandle, constantMethodHandle(0L, MemorySegment.class)));
  }

  @Test
  void errorString_returnsResultNameWhenPointerIsNullSegment() {
    assertEquals(
        "SQLITE_CANTOPEN",
        SqliteNativeErrors.errorString(
            14,
            constantMethodHandle(MemorySegment.NULL, int.class),
            constantMethodHandle(0L, MemorySegment.class)));
  }

  @Test
  void errorString_returnsResultNameWhenPointerIsBlank() {
    try (Arena arena = Arena.ofConfined()) {
      assertEquals(
          "SQLITE_CANTOPEN",
          SqliteNativeErrors.errorString(
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
                SqliteNativeErrors.errorString(
                    14, throwingErrorStringHandle, constantMethodHandle(0L, MemorySegment.class)));
    assertEquals("Failed to read the SQLite error string.", exception.getMessage());
    assertTrue(exception.getCause() instanceof IllegalStateException);
    assertEquals("boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
  }

  @Test
  void scriptErrorMessage_resultCodeOverload_prefersExecBufferAndFallsBackToErrorString() {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment boom = arena.allocateFrom("boom");
      MethodHandle errorStringHandle = constantMethodHandle(boom, int.class);
      MethodHandle strlenHandle = constantMethodHandle(4L, MemorySegment.class);
      assertEquals(
          "boom", SqliteNativeErrors.scriptErrorMessage(14, boom, errorStringHandle, strlenHandle));
      assertEquals(
          "boom",
          SqliteNativeErrors.scriptErrorMessage(
              14, MemorySegment.NULL, errorStringHandle, strlenHandle));
      assertEquals(
          "boom", SqliteNativeErrors.scriptErrorMessage(14, null, errorStringHandle, strlenHandle));
    }
  }

  @Test
  void sqlite3MultipleCiphersVersion_wrapsUnexpectedLookupFailure() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteNativeRuntimeMetadata.sqlite3MultipleCiphersVersion(
                    throwingMethodHandle(new IllegalStateException("boom"), MemorySegment.class)));
    assertTrue(
        NullTestSupport.messageOf(exception)
            .contains("Failed to read the SQLite3 Multiple Ciphers library version."));
  }

  @Test
  void sqlite3MultipleCiphersVersion_rethrowsErrorsFromLookupFailure() {
    AssertionError error =
        assertThrows(
            AssertionError.class,
            () ->
                SqliteNativeRuntimeMetadata.sqlite3MultipleCiphersVersion(
                    throwingMethodHandle(new AssertionError("boom"), MemorySegment.class)));
    assertEquals("boom", error.getMessage());
  }

  @Test
  void shutdownQuietly_reportsRuntimeFailuresFromNativeShutdown() {
    List<String> cleanupReports = new ArrayList<>();
    assertDoesNotThrow(
        () ->
            SqliteNativeBootstrap.shutdownQuietly(
                throwingMethodHandle(new IllegalStateException("boom"), int.class),
                (action, exception) ->
                    cleanupReports.add(action + "|" + exception.getClass().getSimpleName())));
    assertEquals(
        List.of("shutting down the process-scoped SQLite runtime|IllegalStateException"),
        cleanupReports);
  }

  @Test
  void shutdownQuietly_invokesSuccessfulNativeShutdownWithoutReportingFailures() throws Exception {
    AtomicInteger shutdownCalls = new AtomicInteger();
    List<String> cleanupReports = new ArrayList<>();
    MethodHandle shutdownHandle =
        MethodHandles.insertArguments(
            MethodHandles.lookup()
                .findStatic(
                    SqliteNativeBridgeTestSupport.class,
                    "recordShutdownCall",
                    MethodType.methodType(int.class, AtomicInteger.class)),
            0,
            shutdownCalls);

    assertDoesNotThrow(
        () ->
            SqliteNativeBootstrap.shutdownQuietly(
                shutdownHandle,
                (action, exception) ->
                    cleanupReports.add(action + "|" + exception.getClass().getSimpleName())));
    assertEquals(1, shutdownCalls.get());
    assertTrue(cleanupReports.isEmpty());
    assertDoesNotThrow(() -> SqliteNativeBootstrap.shutdownQuietly(shutdownHandle));
    assertEquals(2, shutdownCalls.get());
  }

  @Test
  void shutdownQuietly_oneArgOverloadSwallowsRuntimeFailuresFromNativeShutdown() {
    assertDoesNotThrow(
        () ->
            SqliteNativeBootstrap.shutdownQuietly(
                throwingMethodHandle(new IllegalStateException("boom"), int.class)));
  }

  @Test
  void shutdownQuietly_rethrowsErrorsFromNativeShutdown() {
    AssertionError error =
        assertThrows(
            AssertionError.class,
            () ->
                SqliteNativeBootstrap.shutdownQuietly(
                    throwingMethodHandle(new AssertionError("boom"), int.class)));
    assertEquals("boom", error.getMessage());
  }

  @Test
  void downcall_throwsForMissingSymbol() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteNativeApiBindings.downcall(
                    Linker.nativeLinker().defaultLookup(),
                    "sqlite3_missing_symbol_for_test",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT)));
    assertTrue(NullTestSupport.messageOf(exception).contains("Missing SQLite symbol"));
  }

  @Test
  void loadApi_reraisesLookupFailureForConfiguredMissingLibrary() {
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () ->
                SqliteNativeApiLoader.loadApi(
                    new SqliteLibraryTarget(
                        "managed",
                        dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance
                            .SOURCE_CHECKOUT_MANAGED,
                        tempDirectory.resolve("missing/libsqlite3.dylib").toString())));
    assertNotNull(exception.getMessage());
  }

  private static MethodHandle compileOptionPresenceHandle() {
    try {
      return MethodHandles.lookup()
          .findStatic(
              SqliteNativeErrorHandlingTest.class,
              "compileOptionPresence",
              java.lang.invoke.MethodType.methodType(int.class, MemorySegment.class));
    } catch (NoSuchMethodException | IllegalAccessException exception) {
      throw new IllegalStateException("Failed to build the compile-option test handle.", exception);
    }
  }

  @SuppressWarnings("unused")
  private static int compileOptionPresence(MemorySegment compileOptionName) {
    return "USE_URI".equals(compileOptionName.getString(0)) ? 0 : 1;
  }
}
