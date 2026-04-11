package dev.erst.fingrind.sqlite;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/** Minimal Java FFM binding layer over the configured SQLite C library. */
final class SqliteNativeLibrary {
  static final int SQLITE_OK = 0;
  static final int SQLITE_ROW = 100;
  static final int SQLITE_DONE = 101;

  static final int SQLITE_OPEN_READWRITE = 0x00000002;
  static final int SQLITE_OPEN_CREATE = 0x00000004;

  static final int SQLITE_CONSTRAINT_UNIQUE = 2067;
  static final int SQLITE_CONSTRAINT_PRIMARYKEY = 1555;
  static final int SQLITE_CONSTRAINT_DATATYPE = 3091;

  private static final int SQLITE_BUSY_TIMEOUT_MILLIS = 5_000;

  private static final Linker LINKER = Linker.nativeLinker();
  private static final SymbolLookup DEFAULT_LOOKUP = LINKER.defaultLookup();
  private static final MemorySegment SQLITE_TRANSIENT = MemorySegment.ofAddress(-1L);
  private static final MethodHandle STRLEN =
      downcall(
          DEFAULT_LOOKUP,
          "strlen",
          FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

  private SqliteNativeLibrary() {}

  static SqliteNativeDatabase open(Path bookPath) throws SqliteNativeException {
    SqliteApi sqliteApi = api();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment databasePointer = arena.allocate(ValueLayout.ADDRESS);
      MemorySegment filename = arena.allocateFrom(bookPath.toString());
      int resultCode =
          (int)
              sqliteApi.sqlite3OpenV2.invokeExact(
                  filename,
                  databasePointer,
                  SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE,
                  MemorySegment.NULL);
      MemorySegment databaseHandle = databasePointer.get(ValueLayout.ADDRESS, 0);
      if (resultCode != SQLITE_OK) {
        SqliteNativeException exception = failure(databaseHandle, resultCode, sqliteApi);
        closeQuietly(databaseHandle, sqliteApi);
        throw exception;
      }
      int timeoutResult =
          (int)
              sqliteApi.sqlite3BusyTimeout.invokeExact(databaseHandle, SQLITE_BUSY_TIMEOUT_MILLIS);
      requireOpenConfigurationSuccess(databaseHandle, timeoutResult, sqliteApi);
      int extendedCodeResult =
          (int) sqliteApi.sqlite3ExtendedResultCodes.invokeExact(databaseHandle, 1);
      requireOpenConfigurationSuccess(databaseHandle, extendedCodeResult, sqliteApi);
      return new SqliteNativeDatabase(databaseHandle);
    } catch (SqliteNativeException exception) {
      throw exception;
    } catch (Throwable throwable) {
      throw new IllegalStateException(
          "Failed to open the SQLite native library bridge.", throwable);
    }
  }

  static void close(MemorySegment databaseHandle) throws SqliteNativeException {
    SqliteApi sqliteApi = api();
    try {
      int resultCode = (int) sqliteApi.sqlite3CloseV2.invokeExact(databaseHandle);
      if (resultCode != SQLITE_OK) {
        throw failure(databaseHandle, resultCode, sqliteApi);
      }
    } catch (SqliteNativeException exception) {
      throw exception;
    } catch (Throwable throwable) {
      throw new IllegalStateException(
          "Failed to close the SQLite native library bridge.", throwable);
    }
  }

  static SqliteNativeStatement prepare(SqliteNativeDatabase database, String sql)
      throws SqliteNativeException {
    return new SqliteNativeStatement(database, sql);
  }

  static void executeScript(MemorySegment databaseHandle, MemorySegment sqlPointer)
      throws SqliteNativeException {
    SqliteApi sqliteApi = api();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment errorPointer = arena.allocate(ValueLayout.ADDRESS);
      int resultCode =
          (int)
              sqliteApi.sqlite3Exec.invokeExact(
                  databaseHandle, sqlPointer, MemorySegment.NULL, MemorySegment.NULL, errorPointer);
      MemorySegment execErrorPointer = errorPointer.get(ValueLayout.ADDRESS, 0);
      try {
        if (resultCode != SQLITE_OK) {
          throw new SqliteNativeException(
              resultCode,
              scriptErrorMessage(
                  databaseHandle, execErrorPointer, sqliteApi.sqlite3Errmsg, STRLEN));
        }
      } finally {
        freeSqliteBuffer(execErrorPointer, sqliteApi.sqlite3Free);
      }
    } catch (SqliteNativeException exception) {
      throw exception;
    } catch (Throwable throwable) {
      throw new IllegalStateException("Failed to execute a SQLite script.", throwable);
    }
  }

  static int prepareStatement(
      MemorySegment databaseHandle, MemorySegment sql, MemorySegment statementPointer)
      throws SqliteNativeException {
    SqliteApi sqliteApi = api();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment tailPointer = arena.allocate(ValueLayout.ADDRESS);
      int resultCode =
          (int)
              sqliteApi.sqlite3PrepareV2.invokeExact(
                  databaseHandle, sql, -1, statementPointer, tailPointer);
      if (resultCode != SQLITE_OK) {
        throw failure(databaseHandle, resultCode, sqliteApi);
      }
      return resultCode;
    } catch (SqliteNativeException exception) {
      throw exception;
    } catch (Throwable throwable) {
      throw new IllegalStateException("Failed to prepare a SQLite statement.", throwable);
    }
  }

  static void bindNull(MemorySegment statementHandle, int parameterIndex)
      throws SqliteNativeException {
    SqliteApi sqliteApi = api();
    try {
      int resultCode = (int) sqliteApi.sqlite3BindNull.invokeExact(statementHandle, parameterIndex);
      if (resultCode != SQLITE_OK) {
        throw new SqliteNativeException(resultCode, "Failed to bind a SQLite null parameter.");
      }
    } catch (SqliteNativeException exception) {
      throw exception;
    } catch (Throwable throwable) {
      throw new IllegalStateException("Failed to bind a SQLite null parameter.", throwable);
    }
  }

  static void bindInt(MemorySegment statementHandle, int parameterIndex, int value)
      throws SqliteNativeException {
    SqliteApi sqliteApi = api();
    try {
      int resultCode =
          (int) sqliteApi.sqlite3BindInt.invokeExact(statementHandle, parameterIndex, value);
      if (resultCode != SQLITE_OK) {
        throw new SqliteNativeException(resultCode, "Failed to bind a SQLite integer parameter.");
      }
    } catch (SqliteNativeException exception) {
      throw exception;
    } catch (Throwable throwable) {
      throw new IllegalStateException("Failed to bind a SQLite integer parameter.", throwable);
    }
  }

  static void bindText(
      MemorySegment statementHandle, int parameterIndex, MemorySegment textPointer, int byteLength)
      throws SqliteNativeException {
    SqliteApi sqliteApi = api();
    try {
      int resultCode =
          (int)
              sqliteApi.sqlite3BindText.invokeExact(
                  statementHandle, parameterIndex, textPointer, byteLength, SQLITE_TRANSIENT);
      if (resultCode != SQLITE_OK) {
        throw new SqliteNativeException(resultCode, "Failed to bind a SQLite text parameter.");
      }
    } catch (SqliteNativeException exception) {
      throw exception;
    } catch (Throwable throwable) {
      throw new IllegalStateException("Failed to bind a SQLite text parameter.", throwable);
    }
  }

  static int step(MemorySegment databaseHandle, MemorySegment statementHandle)
      throws SqliteNativeException {
    SqliteApi sqliteApi = api();
    try {
      int resultCode = (int) sqliteApi.sqlite3Step.invokeExact(statementHandle);
      if (resultCode == SQLITE_ROW || resultCode == SQLITE_DONE) {
        return resultCode;
      }
      throw failure(databaseHandle, resultCode, sqliteApi);
    } catch (SqliteNativeException exception) {
      throw exception;
    } catch (Throwable throwable) {
      throw new IllegalStateException("Failed to step a SQLite statement.", throwable);
    }
  }

  static void finalizeStatement(MemorySegment statementHandle) {
    SqliteApi sqliteApi = api();
    try {
      int ignored = (int) sqliteApi.sqlite3Finalize.invokeExact(statementHandle);
    } catch (Throwable throwable) {
      throw new IllegalStateException("Failed to finalize a SQLite statement.", throwable);
    }
  }

  static String columnText(MemorySegment statementHandle, int columnIndex) {
    SqliteApi sqliteApi = api();
    try {
      MemorySegment textPointer =
          (MemorySegment) sqliteApi.sqlite3ColumnText.invokeExact(statementHandle, columnIndex);
      if (textPointer.equals(MemorySegment.NULL)) {
        return null;
      }
      int byteLength = (int) sqliteApi.sqlite3ColumnBytes.invokeExact(statementHandle, columnIndex);
      return textPointer.reinterpret(byteLength + 1L).getString(0);
    } catch (Throwable throwable) {
      throw new IllegalStateException("Failed to read a SQLite text column.", throwable);
    }
  }

  static int columnInt(MemorySegment statementHandle, int columnIndex) {
    SqliteApi sqliteApi = api();
    try {
      return (int) sqliteApi.sqlite3ColumnInt.invokeExact(statementHandle, columnIndex);
    } catch (Throwable throwable) {
      throw new IllegalStateException("Failed to read a SQLite integer column.", throwable);
    }
  }

  static int extendedErrorCode(MemorySegment databaseHandle) {
    SqliteApi sqliteApi = api();
    try {
      return (int) sqliteApi.sqlite3ExtendedErrcode.invokeExact(databaseHandle);
    } catch (Throwable throwable) {
      throw new IllegalStateException("Failed to read the SQLite extended error code.", throwable);
    }
  }

  static String errorMessage(MemorySegment databaseHandle) {
    if (databaseHandle == null || databaseHandle.equals(MemorySegment.NULL)) {
      return "SQLite native failure.";
    }
    SqliteApi sqliteApi = api();
    return errorMessage(databaseHandle, sqliteApi.sqlite3Errmsg, STRLEN);
  }

  static String errorMessage(MemorySegment databaseHandle, MethodHandle errorMessageHandle) {
    return errorMessage(databaseHandle, errorMessageHandle, STRLEN);
  }

  static String errorMessage(
      MemorySegment databaseHandle, MethodHandle errorMessageHandle, MethodHandle strlenHandle) {
    try {
      if (databaseHandle == null) {
        return "SQLite native failure.";
      }
      if (databaseHandle.equals(MemorySegment.NULL)) {
        return "SQLite native failure.";
      }
      MemorySegment errorMessagePointer =
          (MemorySegment) errorMessageHandle.invokeExact(databaseHandle);
      if (errorMessagePointer.equals(MemorySegment.NULL)) {
        return "SQLite native failure.";
      }
      return cString(errorMessagePointer, strlenHandle);
    } catch (Throwable throwable) {
      throw new IllegalStateException("Failed to read the SQLite error message.", throwable);
    }
  }

  static String scriptErrorMessage(MemorySegment databaseHandle, MemorySegment execErrorPointer) {
    SqliteApi sqliteApi = api();
    return scriptErrorMessage(databaseHandle, execErrorPointer, sqliteApi.sqlite3Errmsg, STRLEN);
  }

  static String scriptErrorMessage(
      MemorySegment databaseHandle,
      MemorySegment execErrorPointer,
      MethodHandle errorMessageHandle,
      MethodHandle strlenHandle) {
    if (execErrorPointer != null && !execErrorPointer.equals(MemorySegment.NULL)) {
      return cString(execErrorPointer, strlenHandle);
    }
    return errorMessage(databaseHandle, errorMessageHandle, strlenHandle);
  }

  static String sqliteVersion() {
    return api().loadedVersion;
  }

  static String sqliteVersion(MethodHandle libraryVersionHandle) {
    return sqliteVersion(libraryVersionHandle, STRLEN);
  }

  static String sqliteVersion(MethodHandle libraryVersionHandle, MethodHandle strlenHandle) {
    try {
      MemorySegment versionPointer = (MemorySegment) libraryVersionHandle.invokeExact();
      return cString(versionPointer, strlenHandle);
    } catch (Throwable throwable) {
      throw new IllegalStateException("Failed to read the SQLite library version.", throwable);
    }
  }

  static String configuredLibrarySource() {
    return configuredLibrarySource(
        System.getenv(SqliteRuntime.LIBRARY_OVERRIDE_ENVIRONMENT_VARIABLE));
  }

  static String configuredLibrarySource(String configuredLibraryPath) {
    return configuredLibraryPath == null ? "system" : "managed";
  }

  static int compareVersions(String leftVersion, String rightVersion) {
    int[] leftParts = parseVersionParts(leftVersion);
    int[] rightParts = parseVersionParts(rightVersion);
    int parts = Math.max(leftParts.length, rightParts.length);
    for (int index = 0; index < parts; index++) {
      int left = index < leftParts.length ? leftParts[index] : 0;
      int right = index < rightParts.length ? rightParts[index] : 0;
      if (left != right) {
        return Integer.compare(left, right);
      }
    }
    return 0;
  }

  static String requireSupportedVersion(String loadedVersion, String librarySource) {
    Objects.requireNonNull(loadedVersion, "loadedVersion");
    Objects.requireNonNull(librarySource, "librarySource");
    if (compareVersions(loadedVersion, SqliteRuntime.REQUIRED_MINIMUM_SQLITE_VERSION) < 0) {
      throw new UnsupportedSqliteVersionException(
          loadedVersion, SqliteRuntime.REQUIRED_MINIMUM_SQLITE_VERSION, librarySource);
    }
    return loadedVersion;
  }

  static String resultName(int resultCode) {
    return switch (resultCode) {
      case SQLITE_OK -> "SQLITE_OK";
      case SQLITE_ROW -> "SQLITE_ROW";
      case SQLITE_DONE -> "SQLITE_DONE";
      case SQLITE_CONSTRAINT_UNIQUE -> "SQLITE_CONSTRAINT_UNIQUE";
      case SQLITE_CONSTRAINT_PRIMARYKEY -> "SQLITE_CONSTRAINT_PRIMARYKEY";
      case SQLITE_CONSTRAINT_DATATYPE -> "SQLITE_CONSTRAINT_DATATYPE";
      case 787 -> "SQLITE_CONSTRAINT_FOREIGNKEY";
      case 14 -> "SQLITE_CANTOPEN";
      case 526 -> "SQLITE_CANTOPEN_ISDIR";
      case 26 -> "SQLITE_NOTADB";
      default -> "SQLITE_" + resultCode;
    };
  }

  private static SqliteApi api() {
    return SqliteApiHolder.INSTANCE;
  }

  private static SqliteApi loadApi() {
    return loadApi(
        configuredLibraryTarget(
            System.getenv(SqliteRuntime.LIBRARY_OVERRIDE_ENVIRONMENT_VARIABLE),
            System.getProperty("os.name", "")));
  }

  private static SqliteApi loadApi(SqliteLibraryTarget libraryTarget) {
    // The downcall handles and looked-up SQLite symbols must stay valid for the whole JVM lifetime
    // once published into SQLITE_API, so this shared arena is intentionally process-scoped.
    Arena libraryArena = Arena.ofShared();
    try {
      SymbolLookup lookup = SymbolLookup.libraryLookup(libraryTarget.lookupTarget(), libraryArena);
      MethodHandle sqlite3Libversion =
          downcall(lookup, "sqlite3_libversion", FunctionDescriptor.of(ValueLayout.ADDRESS));
      String loadedVersion =
          requireSupportedVersion(sqliteVersion(sqlite3Libversion, STRLEN), libraryTarget.source());
      return new SqliteApi(
          libraryArena,
          downcall(
              lookup,
              "sqlite3_open_v2",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS)),
          downcall(
              lookup,
              "sqlite3_close_v2",
              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)),
          downcall(
              lookup,
              "sqlite3_busy_timeout",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)),
          downcall(
              lookup,
              "sqlite3_extended_result_codes",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)),
          downcall(
              lookup,
              "sqlite3_exec",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS)),
          downcall(lookup, "sqlite3_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)),
          downcall(
              lookup,
              "sqlite3_prepare_v2",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS)),
          downcall(
              lookup,
              "sqlite3_bind_null",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)),
          downcall(
              lookup,
              "sqlite3_bind_int",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_INT,
                  ValueLayout.JAVA_INT)),
          downcall(
              lookup,
              "sqlite3_bind_text",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS)),
          downcall(
              lookup,
              "sqlite3_step",
              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)),
          downcall(
              lookup,
              "sqlite3_finalize",
              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)),
          downcall(
              lookup,
              "sqlite3_column_text",
              FunctionDescriptor.of(
                  ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)),
          downcall(
              lookup,
              "sqlite3_column_bytes",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)),
          downcall(
              lookup,
              "sqlite3_column_int",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)),
          downcall(
              lookup,
              "sqlite3_errmsg",
              FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)),
          downcall(
              lookup,
              "sqlite3_extended_errcode",
              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)),
          loadedVersion);
    } catch (RuntimeException | Error exception) {
      libraryArena.close();
      throw exception;
    }
  }

  private static MethodHandle downcall(
      SymbolLookup lookup, String symbolName, FunctionDescriptor functionDescriptor) {
    MemorySegment symbol =
        lookup
            .find(symbolName)
            .orElseThrow(() -> new IllegalStateException("Missing SQLite symbol: " + symbolName));
    return LINKER.downcallHandle(symbol, functionDescriptor);
  }

  static SqliteLibraryTarget configuredLibraryTarget(
      String configuredLibraryPath, String operatingSystemName) {
    if (configuredLibraryPath != null) {
      return new SqliteLibraryTarget(
          "managed", normalizeConfiguredLibraryPath(configuredLibraryPath));
    }
    return new SqliteLibraryTarget("system", sqliteLibraryNameFor(operatingSystemName));
  }

  private static String normalizeConfiguredLibraryPath(String configuredLibraryPath) {
    Objects.requireNonNull(configuredLibraryPath, "configuredLibraryPath");
    String normalizedPath = configuredLibraryPath.trim();
    if (normalizedPath.isEmpty()) {
      throw new IllegalStateException(
          SqliteRuntime.LIBRARY_OVERRIDE_ENVIRONMENT_VARIABLE + " must not be blank.");
    }
    return Path.of(normalizedPath).toAbsolutePath().normalize().toString();
  }

  static String sqliteLibraryNameFor(String operatingSystemName) {
    String operatingSystem = operatingSystemName.toLowerCase(Locale.ROOT);
    if (operatingSystem.contains("mac")) {
      return "/usr/lib/libsqlite3.dylib";
    }
    if (operatingSystem.contains("linux")) {
      return "libsqlite3.so.0";
    }
    throw new IllegalStateException(
        "Unsupported operating system for the FinGrind SQLite runtime: " + operatingSystem);
  }

  private static SqliteNativeException failure(
      MemorySegment databaseHandle, int resultCode, SqliteApi sqliteApi) {
    return new SqliteNativeException(
        resultCode, errorMessage(databaseHandle, sqliteApi.sqlite3Errmsg, STRLEN));
  }

  private static String cString(MemorySegment cStringPointer, MethodHandle strlenHandle) {
    try {
      // The caller guarantees one stable, null-terminated UTF-8 pointer for the duration of this
      // read. SQLite-owned strings and arena-backed test strings both satisfy that contract here.
      long byteLength = (long) strlenHandle.invokeExact(cStringPointer);
      return cStringPointer.reinterpret(byteLength + 1L).getString(0);
    } catch (Throwable throwable) {
      throw new IllegalStateException("Failed to read a native C string.", throwable);
    }
  }

  static void freeSqliteBuffer(MemorySegment pointer, MethodHandle freeHandle) {
    if (pointer == null || pointer.equals(MemorySegment.NULL)) {
      return;
    }
    try {
      freeHandle.invokeExact(pointer);
    } catch (Throwable throwable) {
      throw new IllegalStateException("Failed to free a SQLite-owned native buffer.", throwable);
    }
  }

  static void requireOpenConfigurationSuccess(
      MemorySegment databaseHandle, int resultCode, SqliteApi sqliteApi)
      throws SqliteNativeException {
    if (resultCode != SQLITE_OK) {
      closeQuietly(databaseHandle, sqliteApi);
      throw failure(databaseHandle, resultCode, sqliteApi);
    }
  }

  private static void closeQuietly(MemorySegment databaseHandle, SqliteApi sqliteApi) {
    try {
      int ignored = (int) sqliteApi.sqlite3CloseV2.invokeExact(databaseHandle);
    } catch (Throwable ignored) {
      // Preserve the original open failure.
    }
  }

  private static int[] parseVersionParts(String version) {
    Objects.requireNonNull(version, "version");
    String[] parts = version.split("\\.", -1);
    int[] parsedParts = new int[parts.length];
    for (int index = 0; index < parts.length; index++) {
      try {
        parsedParts[index] = Integer.parseInt(parts[index]);
      } catch (NumberFormatException exception) {
        throw new IllegalStateException("Unsupported SQLite version string: " + version, exception);
      }
    }
    return parsedParts;
  }

  record SqliteLibraryTarget(String source, String lookupTarget) {
    SqliteLibraryTarget {
      source = requireText(source, "source");
      lookupTarget = requireText(lookupTarget, "lookupTarget");
    }

    private static String requireText(String value, String fieldName) {
      Objects.requireNonNull(value, fieldName);
      if (value.isBlank()) {
        throw new IllegalArgumentException(fieldName + " must not be blank.");
      }
      return value;
    }
  }

  /** Lazily publishes the process-scoped SQLite native API bundle on first use. */
  private static final class SqliteApiHolder {
    private static final SqliteApi INSTANCE = loadApi();

    private SqliteApiHolder() {}
  }

  /** Loaded SQLite symbol handles and process-scoped resources for one JVM lifetime. */
  private static final class SqliteApi {
    @SuppressWarnings("unused")
    private final Arena libraryArena;

    private final MethodHandle sqlite3OpenV2;
    private final MethodHandle sqlite3CloseV2;
    private final MethodHandle sqlite3BusyTimeout;
    private final MethodHandle sqlite3ExtendedResultCodes;
    private final MethodHandle sqlite3Exec;
    private final MethodHandle sqlite3Free;
    private final MethodHandle sqlite3PrepareV2;
    private final MethodHandle sqlite3BindNull;
    private final MethodHandle sqlite3BindInt;
    private final MethodHandle sqlite3BindText;
    private final MethodHandle sqlite3Step;
    private final MethodHandle sqlite3Finalize;
    private final MethodHandle sqlite3ColumnText;
    private final MethodHandle sqlite3ColumnBytes;
    private final MethodHandle sqlite3ColumnInt;
    private final MethodHandle sqlite3Errmsg;
    private final MethodHandle sqlite3ExtendedErrcode;
    private final String loadedVersion;

    private SqliteApi(
        Arena libraryArena,
        MethodHandle sqlite3OpenV2,
        MethodHandle sqlite3CloseV2,
        MethodHandle sqlite3BusyTimeout,
        MethodHandle sqlite3ExtendedResultCodes,
        MethodHandle sqlite3Exec,
        MethodHandle sqlite3Free,
        MethodHandle sqlite3PrepareV2,
        MethodHandle sqlite3BindNull,
        MethodHandle sqlite3BindInt,
        MethodHandle sqlite3BindText,
        MethodHandle sqlite3Step,
        MethodHandle sqlite3Finalize,
        MethodHandle sqlite3ColumnText,
        MethodHandle sqlite3ColumnBytes,
        MethodHandle sqlite3ColumnInt,
        MethodHandle sqlite3Errmsg,
        MethodHandle sqlite3ExtendedErrcode,
        String loadedVersion) {
      this.libraryArena = Objects.requireNonNull(libraryArena, "libraryArena");
      this.sqlite3OpenV2 = Objects.requireNonNull(sqlite3OpenV2, "sqlite3OpenV2");
      this.sqlite3CloseV2 = Objects.requireNonNull(sqlite3CloseV2, "sqlite3CloseV2");
      this.sqlite3BusyTimeout = Objects.requireNonNull(sqlite3BusyTimeout, "sqlite3BusyTimeout");
      this.sqlite3ExtendedResultCodes =
          Objects.requireNonNull(sqlite3ExtendedResultCodes, "sqlite3ExtendedResultCodes");
      this.sqlite3Exec = Objects.requireNonNull(sqlite3Exec, "sqlite3Exec");
      this.sqlite3Free = Objects.requireNonNull(sqlite3Free, "sqlite3Free");
      this.sqlite3PrepareV2 = Objects.requireNonNull(sqlite3PrepareV2, "sqlite3PrepareV2");
      this.sqlite3BindNull = Objects.requireNonNull(sqlite3BindNull, "sqlite3BindNull");
      this.sqlite3BindInt = Objects.requireNonNull(sqlite3BindInt, "sqlite3BindInt");
      this.sqlite3BindText = Objects.requireNonNull(sqlite3BindText, "sqlite3BindText");
      this.sqlite3Step = Objects.requireNonNull(sqlite3Step, "sqlite3Step");
      this.sqlite3Finalize = Objects.requireNonNull(sqlite3Finalize, "sqlite3Finalize");
      this.sqlite3ColumnText = Objects.requireNonNull(sqlite3ColumnText, "sqlite3ColumnText");
      this.sqlite3ColumnBytes = Objects.requireNonNull(sqlite3ColumnBytes, "sqlite3ColumnBytes");
      this.sqlite3ColumnInt = Objects.requireNonNull(sqlite3ColumnInt, "sqlite3ColumnInt");
      this.sqlite3Errmsg = Objects.requireNonNull(sqlite3Errmsg, "sqlite3Errmsg");
      this.sqlite3ExtendedErrcode =
          Objects.requireNonNull(sqlite3ExtendedErrcode, "sqlite3ExtendedErrcode");
      this.loadedVersion = Objects.requireNonNull(loadedVersion, "loadedVersion");
    }
  }
}
