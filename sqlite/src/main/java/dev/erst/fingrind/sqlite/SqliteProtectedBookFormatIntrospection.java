package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.protocol.BookCipher;
import dev.erst.fingrind.contract.protocol.ProtectedBookFormatContract;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.sqlite.internal.SqliteNativeCallAdapter;
import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Reads the active SQLite3MC protected-book format facts through the native bridge. */
final class SqliteProtectedBookFormatIntrospection {
  private static final int SQLITE_FCNTL_RESERVE_BYTES = 38;

  private SqliteProtectedBookFormatIntrospection() {}

  static void requireRuntimeDefaultCipherContract(SqliteNativeApi sqliteApi) {
    Objects.requireNonNull(sqliteApi, "sqliteApi");
    CipherSettings expected =
        CipherSettings.fromContract(ProtocolCatalog.runtime().protectedBookFormat());
    CipherSettings actual = runtimeDefaultCipherSettings(sqliteApi);
    if (!expected.equals(actual)) {
      throw new IllegalStateException(
          "Managed SQLite runtime default protected-book cipher settings drifted from the canonical contract. Expected "
              + expected
              + " but loaded "
              + actual
              + ".");
    }
  }

  static ProtectedBookFormatContract openedBookFormat(SqliteNativeDatabase database) {
    Objects.requireNonNull(database, "database");
    CipherSettings cipherSettings = openedBookCipherSettings(database);
    int pageSize = SqliteStatementQueries.querySingleInt(database, "pragma page_size");
    int reservedBytes = fileControlReserveBytes(database);
    return new ProtectedBookFormatContract(
        ProtocolCatalog.runtime().protectedBookFormat().applicationId(),
        ProtocolCatalog.runtime().protectedBookFormat().formatVersion(),
        cipherSettings.cipher(),
        cipherSettings.legacyMode(),
        pageSize,
        reservedBytes,
        cipherSettings.legacyPageSize(),
        cipherSettings.kdfIter(),
        cipherSettings.plaintextHeaderSize());
  }

  static CipherSettings runtimeDefaultCipherSettings(SqliteNativeApi sqliteApi) {
    return cipherSettings(null, sqliteApi);
  }

  private static CipherSettings openedBookCipherSettings(SqliteNativeDatabase database) {
    BookCipher cipher = configuredCipher(database);
    String cipherName = cipher.wireValue();
    return new CipherSettings(
        cipher,
        database.protectedBookRuntime().cipherParameter(cipherName, "legacy") != 0,
        database.protectedBookRuntime().cipherParameter(cipherName, "legacy_page_size"),
        database.protectedBookRuntime().cipherParameter(cipherName, "kdf_iter"),
        database.protectedBookRuntime().cipherParameter(cipherName, "plaintext_header_size"));
  }

  private static CipherSettings cipherSettings(
      @Nullable MemorySegment databaseHandle, SqliteNativeApi sqliteApi) {
    BookCipher cipher = configuredCipher(databaseHandle, sqliteApi);
    String cipherName = cipher.wireValue();
    return new CipherSettings(
        cipher,
        configCipher(databaseHandle, cipherName, "legacy", sqliteApi) != 0,
        configCipher(databaseHandle, cipherName, "legacy_page_size", sqliteApi),
        configCipher(databaseHandle, cipherName, "kdf_iter", sqliteApi),
        configCipher(databaseHandle, cipherName, "plaintext_header_size", sqliteApi));
  }

  private static BookCipher configuredCipher(
      @Nullable MemorySegment databaseHandle, SqliteNativeApi sqliteApi) {
    int cipherIndex = config(databaseHandle, "cipher", sqliteApi);
    MemorySegment cipherNamePointer =
        SqliteNativeInvocation.invoke(
            "Failed to read the SQLite3MC cipher name.",
            () ->
                SqliteNativeCallAdapter.adapt(
                        SqliteNativeCalls.IntToAddressCall.class, sqliteApi.sqlite3mcCipherName())
                    .invoke(cipherIndex));
    if (cipherNamePointer == null || cipherNamePointer.equals(MemorySegment.NULL)) {
      throw new IllegalStateException(
          "Managed SQLite runtime returned no cipher name for cipher index " + cipherIndex + ".");
    }
    return BookCipher.fromWireValue(
        SqliteNativeErrors.cString(cipherNamePointer, SqliteNativeBootstrap.strlen()));
  }

  private static BookCipher configuredCipher(SqliteNativeDatabase database) {
    int cipherIndex = database.protectedBookRuntime().runtimeParameter("cipher");
    return BookCipher.fromWireValue(database.protectedBookRuntime().cipherName(cipherIndex));
  }

  private static int config(
      @Nullable MemorySegment databaseHandle, String parameterName, SqliteNativeApi sqliteApi) {
    try (Arena arena = Arena.ofConfined()) {
      int value =
          SqliteNativeInvocation.invoke(
              "Failed to read one SQLite3MC runtime parameter.",
              () ->
                  SqliteNativeCallAdapter.adapt(
                          SqliteNativeCalls.AddressAddressIntToIntCall.class,
                          sqliteApi.sqlite3mcConfig())
                      .invoke(
                          nullableHandle(databaseHandle), arena.allocateFrom(parameterName), -1));
      if (value < 0) {
        throw new IllegalStateException(
            "Managed SQLite runtime did not expose protected-book parameter `"
                + parameterName
                + "`.");
      }
      return value;
    }
  }

  private static int configCipher(
      @Nullable MemorySegment databaseHandle,
      String cipherName,
      String parameterName,
      SqliteNativeApi sqliteApi) {
    try (Arena arena = Arena.ofConfined()) {
      int value =
          SqliteNativeInvocation.invoke(
              "Failed to read one SQLite3MC cipher parameter.",
              () ->
                  SqliteNativeCallAdapter.adapt(
                          SqliteNativeCalls.AddressAddressAddressIntToIntCall.class,
                          sqliteApi.sqlite3mcConfigCipher())
                      .invoke(
                          nullableHandle(databaseHandle),
                          arena.allocateFrom(cipherName),
                          arena.allocateFrom(parameterName),
                          -1));
      if (value < 0) {
        throw new IllegalStateException(
            "Managed SQLite runtime did not expose cipher parameter `"
                + parameterName
                + "` for `"
                + cipherName
                + "`.");
      }
      return value;
    }
  }

  private static int fileControlReserveBytes(SqliteNativeDatabase database) {
    return database.protectedBookRuntime().fileControlReserveBytes(SQLITE_FCNTL_RESERVE_BYTES);
  }

  private static MemorySegment nullableHandle(@Nullable MemorySegment databaseHandle) {
    return databaseHandle == null ? MemorySegment.NULL : databaseHandle;
  }

  record CipherSettings(
      BookCipher cipher,
      boolean legacyMode,
      int legacyPageSize,
      int kdfIter,
      int plaintextHeaderSize) {
    private static CipherSettings fromContract(ProtectedBookFormatContract contract) {
      return new CipherSettings(
          contract.cipher(),
          contract.legacyMode(),
          contract.legacyPageSize(),
          contract.kdfIter(),
          contract.plaintextHeaderSize());
    }
  }
}
