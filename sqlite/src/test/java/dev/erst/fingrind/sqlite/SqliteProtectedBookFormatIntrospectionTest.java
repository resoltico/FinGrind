package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.BookCipher;
import dev.erst.fingrind.contract.protocol.ProtectedBookFormatContract;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Deterministic coverage tests for protected-book format introspection and drift detection. */
class SqliteProtectedBookFormatIntrospectionTest extends SqliteNativeBridgeTestSupport {
  private static final Map<String, Integer> CANONICAL_RUNTIME_PARAMETERS = Map.of("cipher", 1);
  private static final Map<String, Integer> CANONICAL_CIPHER_PARAMETERS =
      Map.of(
          "legacy", 0,
          "legacy_page_size", 4096,
          "kdf_iter", 64007,
          "plaintext_header_size", 0);

  @Test
  void requireRuntimeDefaultCipherContract_rejectsDriftedLegacyMode() throws Throwable {
    try (Arena arena = Arena.ofConfined()) {
      SqliteNativeApi sqliteApi =
          sqliteApiWithFormatHandles(
              configHandle(CANONICAL_RUNTIME_PARAMETERS),
              configCipherHandle(
                  Map.of(
                      "legacy", 1,
                      "legacy_page_size", 4096,
                      "kdf_iter", 64007,
                      "plaintext_header_size", 0)),
              constantMethodHandle(arena.allocateFrom("chacha20"), int.class),
              null);
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteProtectedBookFormatIntrospection.requireRuntimeDefaultCipherContract(
                      sqliteApi));
      assertTrue(messageText(exception).contains("drifted from the canonical contract"));
    }
  }

  @Test
  void runtimeDefaultCipherSettings_rejectsNullCipherNamePointer() throws Throwable {
    SqliteNativeApi sqliteApi =
        sqliteApiWithFormatHandles(
            configHandle(CANONICAL_RUNTIME_PARAMETERS),
            configCipherHandle(CANONICAL_CIPHER_PARAMETERS),
            nullMemorySegmentHandle(int.class),
            null);
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteProtectedBookFormatIntrospection.runtimeDefaultCipherSettings(sqliteApi));
    assertTrue(messageText(exception).contains("returned no cipher name"));
  }

  @Test
  void runtimeDefaultCipherSettings_rejectsNullMemorySegmentCipherNamePointer() throws Throwable {
    SqliteNativeApi sqliteApi =
        sqliteApiWithFormatHandles(
            configHandle(CANONICAL_RUNTIME_PARAMETERS),
            configCipherHandle(CANONICAL_CIPHER_PARAMETERS),
            constantMethodHandle(MemorySegment.NULL, int.class),
            null);
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteProtectedBookFormatIntrospection.runtimeDefaultCipherSettings(sqliteApi));
    assertTrue(messageText(exception).contains("returned no cipher name"));
  }

  @Test
  void runtimeDefaultCipherSettings_rejectsMissingRuntimeParameter() throws Throwable {
    try (Arena arena = Arena.ofConfined()) {
      SqliteNativeApi sqliteApi =
          sqliteApiWithFormatHandles(
              configHandle(Map.of()),
              configCipherHandle(CANONICAL_CIPHER_PARAMETERS),
              constantMethodHandle(arena.allocateFrom("chacha20"), int.class),
              null);
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteProtectedBookFormatIntrospection.runtimeDefaultCipherSettings(sqliteApi));
      assertTrue(
          messageText(exception).contains("did not expose protected-book parameter `cipher`"));
    }
  }

  @Test
  void runtimeDefaultCipherSettings_rejectsMissingCipherParameter() throws Throwable {
    try (Arena arena = Arena.ofConfined()) {
      SqliteNativeApi sqliteApi =
          sqliteApiWithFormatHandles(
              configHandle(CANONICAL_RUNTIME_PARAMETERS),
              configCipherHandle(Map.of()),
              constantMethodHandle(arena.allocateFrom("chacha20"), int.class),
              null);
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteProtectedBookFormatIntrospection.runtimeDefaultCipherSettings(sqliteApi));
      assertTrue(messageText(exception).contains("did not expose cipher parameter `legacy`"));
    }
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void openedBookFormat_rethrowsFileControlFailures() throws Throwable {
    Path bookPath = tempDirectory.resolve("file-control-failure.sqlite");
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "protected-book-format-file-control", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database = SqliteNativeConnections.open(bookPath, bookPassphrase)) {
      SqliteNativeApi failingApi =
          sqliteApiWithFormatHandles(
              database.sqliteApi(),
              null,
              null,
              null,
              fileControlHandle(SqliteNativeResultCodes.CANTOPEN, 0));
      // This wrapper aliases the live handle owned by `database`; closing it here would
      // double-close.
      SqliteNativeDatabase probingDatabase =
          new SqliteNativeDatabase(database.handle(), failingApi);
      SqliteNativeException exception =
          assertThrows(
              SqliteNativeException.class,
              () -> SqliteProtectedBookFormatIntrospection.openedBookFormat(probingDatabase));
      assertEquals(SqliteNativeResultCodes.CANTOPEN, exception.resultCode());
      assertEquals("SQLITE_CANTOPEN", exception.resultName());
    }
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void protectedBookRuntime_rejectsMissingRuntimeParameter() throws Throwable {
    Path bookPath = tempDirectory.resolve("runtime-parameter-missing.sqlite");
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "protected-book-runtime-parameter", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database = SqliteNativeConnections.open(bookPath, bookPassphrase)) {
      SqliteNativeApi probingApi =
          sqliteApiWithFormatHandles(
              database.sqliteApi(), configHandle(Map.of()), null, null, null);
      SqliteNativeDatabase probingDatabase =
          new SqliteNativeDatabase(database.handle(), probingApi);
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> probingDatabase.protectedBookRuntime().runtimeParameter("cipher"));
      assertTrue(
          messageText(exception).contains("did not expose protected-book parameter `cipher`"));
    }
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void protectedBookRuntime_rejectsMissingCipherParameter() throws Throwable {
    Path bookPath = tempDirectory.resolve("cipher-parameter-missing.sqlite");
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "protected-book-cipher-parameter", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database = SqliteNativeConnections.open(bookPath, bookPassphrase)) {
      SqliteNativeApi probingApi =
          sqliteApiWithFormatHandles(
              database.sqliteApi(), null, configCipherHandle(Map.of()), null, null);
      SqliteNativeDatabase probingDatabase =
          new SqliteNativeDatabase(database.handle(), probingApi);
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> probingDatabase.protectedBookRuntime().cipherParameter("chacha20", "legacy"));
      assertTrue(messageText(exception).contains("did not expose cipher parameter `legacy`"));
    }
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void protectedBookRuntime_rejectsNullCipherNamePointer() throws Throwable {
    Path bookPath = tempDirectory.resolve("cipher-name-null-pointer.sqlite");
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "protected-book-cipher-name-null", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database = SqliteNativeConnections.open(bookPath, bookPassphrase)) {
      SqliteNativeApi probingApi =
          sqliteApiWithFormatHandles(
              database.sqliteApi(), null, null, nullMemorySegmentHandle(int.class), null);
      SqliteNativeDatabase probingDatabase =
          new SqliteNativeDatabase(database.handle(), probingApi);
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> probingDatabase.protectedBookRuntime().cipherName(1));
      assertTrue(messageText(exception).contains("returned no cipher name"));
    }
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void protectedBookRuntime_rejectsNullMemorySegmentCipherNamePointer() throws Throwable {
    Path bookPath = tempDirectory.resolve("cipher-name-null-segment.sqlite");
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "protected-book-cipher-name-null-segment", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database = SqliteNativeConnections.open(bookPath, bookPassphrase)) {
      SqliteNativeApi probingApi =
          sqliteApiWithFormatHandles(
              database.sqliteApi(),
              null,
              null,
              constantMethodHandle(MemorySegment.NULL, int.class),
              null);
      SqliteNativeDatabase probingDatabase =
          new SqliteNativeDatabase(database.handle(), probingApi);
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> probingDatabase.protectedBookRuntime().cipherName(1));
      assertTrue(messageText(exception).contains("returned no cipher name"));
    }
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void openedBookFormat_readsLegacyEnabledCipherSettings() throws Throwable {
    Path bookPath = tempDirectory.resolve("legacy-enabled-cipher.sqlite");
    try (Arena arena = Arena.ofConfined();
        SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "protected-book-legacy-enabled", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database = SqliteNativeConnections.open(bookPath, bookPassphrase)) {
      SqliteNativeApi probingApi =
          sqliteApiWithFormatHandles(
              database.sqliteApi(),
              configHandle(CANONICAL_RUNTIME_PARAMETERS),
              configCipherHandle(
                  Map.of(
                      "legacy", 1,
                      "legacy_page_size", 1024,
                      "kdf_iter", 777,
                      "plaintext_header_size", 16)),
              constantMethodHandle(arena.allocateFrom("chacha20"), int.class),
              fileControlHandle(SqliteNativeResultCodes.OK, 32));
      SqliteNativeDatabase probingDatabase =
          new SqliteNativeDatabase(database.handle(), probingApi);
      ProtectedBookFormatContract contract =
          SqliteProtectedBookFormatIntrospection.openedBookFormat(probingDatabase);
      assertTrue(contract.legacyMode());
      assertEquals(1024, contract.legacyPageSize());
      assertEquals(777, contract.kdfIter());
      assertEquals(16, contract.plaintextHeaderSize());
      assertEquals(32, contract.reservedBytes());
    }
  }

  @Test
  void configuredCipher_acceptsNonNullDatabaseHandlesInFormatConfigLookups() throws Throwable {
    MethodHandles.Lookup lookup =
        MethodHandles.privateLookupIn(
            SqliteProtectedBookFormatIntrospection.class, MethodHandles.lookup());
    MethodHandle configuredCipherHandle =
        lookup.findStatic(
            SqliteProtectedBookFormatIntrospection.class,
            "configuredCipher",
            MethodType.methodType(BookCipher.class, MemorySegment.class, SqliteNativeApi.class));
    try (Arena arena = Arena.ofConfined()) {
      SqliteNativeApi sqliteApi =
          sqliteApiWithFormatHandles(
              configHandle(CANONICAL_RUNTIME_PARAMETERS),
              configCipherHandle(CANONICAL_CIPHER_PARAMETERS),
              constantMethodHandle(arena.allocateFrom("chacha20"), int.class),
              null);
      BookCipher cipher =
          (BookCipher) configuredCipherHandle.invokeExact(arena.allocate(1), sqliteApi);
      assertEquals(BookCipher.CHACHA20, cipher);
    }
  }

  private static SqliteNativeApi sqliteApiWithFormatHandles(
      @Nullable MethodHandle sqlite3mcConfig,
      @Nullable MethodHandle sqlite3mcConfigCipher,
      @Nullable MethodHandle sqlite3mcCipherName,
      @Nullable MethodHandle sqlite3FileControl) {
    return sqliteApiWithFormatHandles(
        baseTestApi(),
        sqlite3mcConfig,
        sqlite3mcConfigCipher,
        sqlite3mcCipherName,
        sqlite3FileControl);
  }

  private static SqliteNativeApi sqliteApiWithFormatHandles(
      SqliteNativeApi base,
      @Nullable MethodHandle sqlite3mcConfig,
      @Nullable MethodHandle sqlite3mcConfigCipher,
      @Nullable MethodHandle sqlite3mcCipherName,
      @Nullable MethodHandle sqlite3FileControl) {
    return SqliteNativeApiTestSupport.withFormatCalls(
        base, sqlite3mcConfig, sqlite3mcConfigCipher, sqlite3mcCipherName, sqlite3FileControl);
  }

  private static SqliteNativeApi baseTestApi() {
    return buildSqliteApi(defaultSqliteApiArguments());
  }

  private static MethodHandle configHandle(Map<String, Integer> values)
      throws ReflectiveOperationException {
    return MethodHandles.insertArguments(
        MethodHandles.lookup()
            .findStatic(
                SqliteProtectedBookFormatIntrospectionTest.class,
                "configValue",
                MethodType.methodType(
                    int.class, Map.class, MemorySegment.class, MemorySegment.class, int.class)),
        0,
        values);
  }

  private static MethodHandle configCipherHandle(Map<String, Integer> values)
      throws ReflectiveOperationException {
    return MethodHandles.insertArguments(
        MethodHandles.lookup()
            .findStatic(
                SqliteProtectedBookFormatIntrospectionTest.class,
                "configCipherValue",
                MethodType.methodType(
                    int.class,
                    Map.class,
                    MemorySegment.class,
                    MemorySegment.class,
                    MemorySegment.class,
                    int.class)),
        0,
        values);
  }

  private static MethodHandle fileControlHandle(int resultCode, int reserveBytes)
      throws ReflectiveOperationException {
    return MethodHandles.insertArguments(
        MethodHandles.lookup()
            .findStatic(
                SqliteProtectedBookFormatIntrospectionTest.class,
                "fileControlResult",
                MethodType.methodType(
                    int.class,
                    int.class,
                    int.class,
                    MemorySegment.class,
                    MemorySegment.class,
                    int.class,
                    MemorySegment.class)),
        0,
        resultCode,
        reserveBytes);
  }

  private static MethodHandle nullMemorySegmentHandle(Class<?>... parameterTypes) {
    MethodHandle constantHandle = MethodHandles.constant(MemorySegment.class, null);
    return MethodHandles.dropArguments(constantHandle, 0, parameterTypes);
  }

  @SuppressWarnings({"unused", "UnusedVariable"})
  private static int configValue(
      Map<String, Integer> values,
      MemorySegment databaseHandle,
      MemorySegment parameterName,
      int current) {
    return values.getOrDefault(cString(parameterName), -1);
  }

  @SuppressWarnings({"unused", "UnusedVariable"})
  private static int configCipherValue(
      Map<String, Integer> values,
      MemorySegment databaseHandle,
      MemorySegment cipherName,
      MemorySegment parameterName,
      int current) {
    return values.getOrDefault(cString(parameterName), -1);
  }

  @SuppressWarnings({"unused", "UnusedVariable"})
  private static int fileControlResult(
      int resultCode,
      int reserveBytes,
      MemorySegment databaseHandle,
      MemorySegment fileName,
      int operation,
      MemorySegment resultPointer) {
    if (resultCode == SqliteNativeResultCodes.OK) {
      resultPointer.set(ValueLayout.JAVA_INT, 0L, reserveBytes);
    }
    return resultCode;
  }

  private static String cString(MemorySegment text) {
    return SqliteNativeErrors.cString(text, SqliteNativeBootstrap.strlen());
  }

  private static String messageText(Throwable throwable) {
    return String.valueOf(throwable.getMessage());
  }
}
