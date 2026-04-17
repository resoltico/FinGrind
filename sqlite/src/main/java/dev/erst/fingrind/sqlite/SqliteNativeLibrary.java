package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.BookAccess;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** Minimal Java FFM binding layer over the configured SQLite C library. */
final class SqliteNativeLibrary {
  static final int SQLITE_OK = 0;
  static final int SQLITE_ROW = 100;
  static final int SQLITE_DONE = 101;

  static final int SQLITE_OPEN_READONLY = 0x00000001;
  static final int SQLITE_OPEN_READWRITE = 0x00000002;
  static final int SQLITE_OPEN_CREATE = 0x00000004;

  static final int SQLITE_CONSTRAINT_UNIQUE = 2067;
  static final int SQLITE_CONSTRAINT_PRIMARYKEY = 1555;
  static final int SQLITE_CONSTRAINT_DATATYPE = 3091;

  private static final int SQLITE_BUSY_TIMEOUT_MILLIS = 5_000;

  private static final Linker LINKER = Linker.nativeLinker();
  private static final SymbolLookup DEFAULT_LOOKUP = LINKER.defaultLookup();
  private static final MemorySegment SQLITE_TRANSIENT = MemorySegment.ofAddress(-1L);
  private static final AtomicInteger ACTIVE_CONNECTIONS = new AtomicInteger();
  private static final AtomicReference<MethodHandle> SQLITE3_OPEN_V2_OVERRIDE =
      new AtomicReference<>();
  private static final AtomicReference<MethodHandle> SQLITE3_CLOSE_V2_OVERRIDE =
      new AtomicReference<>();
  private static final AtomicReference<MethodHandle> SQLITE3_REKEY_OVERRIDE =
      new AtomicReference<>();
  private static final MethodHandle STRLEN =
      downcall(
          DEFAULT_LOOKUP,
          "strlen",
          FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
  private static final String KEY_VALIDATION_QUERY = "SELECT count(*) FROM sqlite_master;";

  private SqliteNativeLibrary() {}

  static SqliteNativeDatabase open(BookAccess bookAccess) throws SqliteNativeException {
    Objects.requireNonNull(bookAccess, "bookAccess");
    if (!(bookAccess.passphraseSource() instanceof BookAccess.PassphraseSource.KeyFile keyFile)) {
      throw new IllegalArgumentException(
          "SQLite same-package file-backed open requires a --book-key-file access selection.");
    }
    return open(
        bookAccess.bookFilePath(),
        SqliteBookKeyFile.load(keyFile.bookKeyFilePath()),
        OpenMode.READ_WRITE_CREATE);
  }

  static SqliteNativeDatabase open(Path bookPath, SqliteBookPassphrase bookPassphrase)
      throws SqliteNativeException {
    return open(bookPath, bookPassphrase, OpenMode.READ_WRITE_CREATE);
  }

  static SqliteNativeDatabase open(
      Path bookPath, SqliteBookPassphrase bookPassphrase, OpenMode openMode)
      throws SqliteNativeException {
    return open(bookPath, bookPassphrase, openMode, api());
  }

  static SqliteNativeDatabase open(
      Path bookPath, SqliteBookPassphrase bookPassphrase, OpenMode openMode, SqliteApi sqliteApi)
      throws SqliteNativeException {
    Objects.requireNonNull(bookPath, "bookPath");
    Objects.requireNonNull(bookPassphrase, "bookPassphrase");
    Objects.requireNonNull(openMode, "openMode");
    Objects.requireNonNull(sqliteApi, "sqliteApi");
    Path normalizedBookPath = bookPath.toAbsolutePath().normalize();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment databasePointer = arena.allocate(ValueLayout.ADDRESS);
      MemorySegment filename = arena.allocateFrom(normalizedBookPath.toString());
      int resultCode =
          (int)
              effectiveSqlite3OpenV2(sqliteApi)
                  .invokeExact(filename, databasePointer, openMode.flags(), MemorySegment.NULL);
      MemorySegment databaseHandle = databasePointer.get(ValueLayout.ADDRESS, 0);
      if (resultCode != SQLITE_OK) {
        SqliteNativeException exception = failure(resultCode, sqliteApi);
        closeQuietly(databaseHandle, sqliteApi);
        throw exception;
      }
      applyKey(databaseHandle, bookPassphrase, sqliteApi, arena);
      int timeoutResult =
          (int)
              sqliteApi.sqlite3BusyTimeout.invokeExact(databaseHandle, SQLITE_BUSY_TIMEOUT_MILLIS);
      requireOpenConfigurationSuccess(databaseHandle, timeoutResult, sqliteApi);
      int extendedCodeResult =
          (int) sqliteApi.sqlite3ExtendedResultCodes.invokeExact(databaseHandle, 1);
      requireOpenConfigurationSuccess(databaseHandle, extendedCodeResult, sqliteApi);
      validateConfiguredKey(databaseHandle);
      ACTIVE_CONNECTIONS.incrementAndGet();
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
      int resultCode = (int) effectiveSqlite3CloseV2(sqliteApi).invokeExact(databaseHandle);
      if (resultCode != SQLITE_OK) {
        throw failure(resultCode, sqliteApi);
      }
      ACTIVE_CONNECTIONS.decrementAndGet();
    } catch (SqliteNativeException exception) {
      throw exception;
    } catch (Throwable throwable) {
      throw new IllegalStateException(
          "Failed to close the SQLite native library bridge.", throwable);
    }
  }

  static void rekey(SqliteNativeDatabase database, SqliteBookPassphrase bookPassphrase)
      throws SqliteNativeException {
    Objects.requireNonNull(database, "database");
    Objects.requireNonNull(bookPassphrase, "bookPassphrase");
    SqliteApi sqliteApi = api();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment keyPointer = bookPassphrase.copyToCString(arena);
      int resultCode =
          (int)
              effectiveSqlite3Rekey(sqliteApi)
                  .invokeExact(database.handle(), keyPointer, bookPassphrase.byteLength());
      if (resultCode != SQLITE_OK) {
        throw failure(resultCode, sqliteApi);
      }
      validateConfiguredKey(database.handle());
    } catch (SqliteNativeException exception) {
      throw exception;
    } catch (Throwable throwable) {
      throw new IllegalStateException(
          "Failed to rekey the FinGrind SQLite book with passphrase material from "
              + bookPassphrase.sourceDescription()
              + ".",
          throwable);
    }
  }

  /** Overrides the native `sqlite3_rekey` handle for one test scope and restores it on close. */
  static AutoCloseable overrideSqlite3RekeyHandleForTesting(MethodHandle sqlite3RekeyHandle) {
    Objects.requireNonNull(sqlite3RekeyHandle, "sqlite3RekeyHandle");
    MethodHandle previousHandle = SQLITE3_REKEY_OVERRIDE.getAndSet(sqlite3RekeyHandle);
    return () -> SQLITE3_REKEY_OVERRIDE.set(previousHandle);
  }

  /** Overrides the native `sqlite3_open_v2` handle for one test scope and restores it on close. */
  static AutoCloseable overrideSqlite3OpenV2HandleForTesting(MethodHandle sqlite3OpenV2Handle) {
    Objects.requireNonNull(sqlite3OpenV2Handle, "sqlite3OpenV2Handle");
    MethodHandle previousHandle = SQLITE3_OPEN_V2_OVERRIDE.getAndSet(sqlite3OpenV2Handle);
    return () -> SQLITE3_OPEN_V2_OVERRIDE.set(previousHandle);
  }

  /** Overrides the native `sqlite3_close_v2` handle for one test scope and restores it on close. */
  static AutoCloseable overrideSqlite3CloseV2HandleForTesting(MethodHandle sqlite3CloseV2Handle) {
    Objects.requireNonNull(sqlite3CloseV2Handle, "sqlite3CloseV2Handle");
    MethodHandle previousHandle = SQLITE3_CLOSE_V2_OVERRIDE.getAndSet(sqlite3CloseV2Handle);
    return () -> SQLITE3_CLOSE_V2_OVERRIDE.set(previousHandle);
  }

  private static MethodHandle effectiveSqlite3OpenV2(SqliteApi sqliteApi) {
    return Objects.requireNonNullElseGet(
        SQLITE3_OPEN_V2_OVERRIDE.get(), () -> sqliteApi.sqlite3OpenV2);
  }

  private static MethodHandle effectiveSqlite3CloseV2(SqliteApi sqliteApi) {
    return Objects.requireNonNullElseGet(
        SQLITE3_CLOSE_V2_OVERRIDE.get(), () -> sqliteApi.sqlite3CloseV2);
  }

  private static MethodHandle effectiveSqlite3Rekey(SqliteApi sqliteApi) {
    return Objects.requireNonNullElseGet(
        SQLITE3_REKEY_OVERRIDE.get(), () -> sqliteApi.sqlite3Rekey);
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
              scriptErrorMessage(resultCode, execErrorPointer, sqliteApi.sqlite3Errstr, STRLEN));
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
        throw failure(resultCode, sqliteApi);
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
      throw failure(resultCode, sqliteApi);
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

  static @Nullable String columnText(MemorySegment statementHandle, int columnIndex) {
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

  static String errorMessage(@Nullable MemorySegment databaseHandle) {
    if (databaseHandle == null || databaseHandle.equals(MemorySegment.NULL)) {
      return "SQLite native failure.";
    }
    SqliteApi sqliteApi = api();
    return errorMessage(databaseHandle, sqliteApi.sqlite3Errmsg, STRLEN);
  }

  static String errorMessage(
      @Nullable MemorySegment databaseHandle, MethodHandle errorMessageHandle) {
    return errorMessage(databaseHandle, errorMessageHandle, STRLEN);
  }

  static String errorMessage(
      @Nullable MemorySegment databaseHandle,
      MethodHandle errorMessageHandle,
      MethodHandle strlenHandle) {
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

  static String errorString(int resultCode) {
    SqliteApi sqliteApi = api();
    return errorString(resultCode, sqliteApi.sqlite3Errstr, STRLEN);
  }

  static String errorString(
      int resultCode, MethodHandle errorStringHandle, MethodHandle strlenHandle) {
    try {
      MemorySegment errorStringPointer = (MemorySegment) errorStringHandle.invokeExact(resultCode);
      if (errorStringPointer == null || errorStringPointer.equals(MemorySegment.NULL)) {
        return resultName(resultCode);
      }
      String errorString = cString(errorStringPointer, strlenHandle);
      return errorString.isBlank() ? resultName(resultCode) : errorString;
    } catch (Throwable throwable) {
      throw new IllegalStateException("Failed to read the SQLite error string.", throwable);
    }
  }

  static String scriptErrorMessage(
      int resultCode,
      @Nullable MemorySegment execErrorPointer,
      MethodHandle errorStringHandle,
      MethodHandle strlenHandle) {
    if (execErrorPointer != null && !execErrorPointer.equals(MemorySegment.NULL)) {
      return cString(execErrorPointer, strlenHandle);
    }
    return errorString(resultCode, errorStringHandle, strlenHandle);
  }

  static String scriptErrorMessage(
      MemorySegment databaseHandle,
      @Nullable MemorySegment execErrorPointer,
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

  static String sqlite3MultipleCiphersVersion() {
    return api().loadedSqlite3mcVersion;
  }

  static String sqlite3MultipleCiphersVersion(MethodHandle versionHandle) {
    return sqlite3MultipleCiphersVersion(versionHandle, STRLEN);
  }

  static String sqlite3MultipleCiphersVersion(
      MethodHandle versionHandle, MethodHandle strlenHandle) {
    try {
      MemorySegment versionPointer = (MemorySegment) versionHandle.invokeExact();
      String loadedVersion = cString(versionPointer, strlenHandle);
      return loadedVersion.replace("SQLite3 Multiple Ciphers ", "").trim();
    } catch (Throwable throwable) {
      throw new IllegalStateException(
          "Failed to read the SQLite3 Multiple Ciphers library version.", throwable);
    }
  }

  static String configuredLibraryMode() {
    return SqliteRuntime.LIBRARY_MODE;
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

  static String requireSupportedVersion(String loadedVersion, String libraryMode) {
    Objects.requireNonNull(loadedVersion, "loadedVersion");
    Objects.requireNonNull(libraryMode, "libraryMode");
    if (compareVersions(loadedVersion, SqliteRuntime.REQUIRED_MINIMUM_SQLITE_VERSION) < 0) {
      throw new UnsupportedSqliteVersionException(
          loadedVersion, SqliteRuntime.REQUIRED_MINIMUM_SQLITE_VERSION, libraryMode);
    }
    return loadedVersion;
  }

  static String requireSupportedSqlite3mcVersion(String loadedVersion, String libraryMode) {
    Objects.requireNonNull(loadedVersion, "loadedVersion");
    Objects.requireNonNull(libraryMode, "libraryMode");
    if (!SqliteRuntime.REQUIRED_SQLITE3MC_VERSION.equals(loadedVersion)) {
      throw new UnsupportedSqliteMultipleCiphersVersionException(
          loadedVersion, SqliteRuntime.REQUIRED_SQLITE3MC_VERSION, libraryMode);
    }
    return loadedVersion;
  }

  static void requireSupportedCompileOptions(
      MethodHandle compileOptionUsedHandle,
      String loadedSqliteVersion,
      String loadedSqlite3mcVersion,
      String libraryMode) {
    Objects.requireNonNull(compileOptionUsedHandle, "compileOptionUsedHandle");
    Objects.requireNonNull(loadedSqliteVersion, "loadedSqliteVersion");
    Objects.requireNonNull(loadedSqlite3mcVersion, "loadedSqlite3mcVersion");
    Objects.requireNonNull(libraryMode, "libraryMode");
    var missingCompileOptions =
        SqliteRuntime.REQUIRED_SQLITE_COMPILE_OPTIONS.stream()
            .filter(option -> !compileOptionUsed(compileOptionUsedHandle, option))
            .toList();
    if (!missingCompileOptions.isEmpty()) {
      throw new UnsupportedSqliteCompileOptionsException(
          loadedSqliteVersion, loadedSqlite3mcVersion, libraryMode, missingCompileOptions);
    }
  }

  static boolean compileOptionUsed(MethodHandle compileOptionUsedHandle, String compileOption) {
    Objects.requireNonNull(compileOptionUsedHandle, "compileOptionUsedHandle");
    Objects.requireNonNull(compileOption, "compileOption");
    try (Arena arena = Arena.ofConfined()) {
      return (int) compileOptionUsedHandle.invokeExact(arena.allocateFrom(compileOption)) != 0;
    } catch (Throwable throwable) {
      throw new IllegalStateException(
          "Failed to read the SQLite compile option: " + compileOption, throwable);
    }
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
    return initialize(() -> SqliteApiHolder.INSTANCE);
  }

  static <T> T initialize(Supplier<T> initializer) {
    Objects.requireNonNull(initializer, "initializer");
    try {
      return initializer.get();
    } catch (ExceptionInInitializerError error) {
      throw nativeInitializationFailure(error);
    }
  }

  static IllegalStateException nativeInitializationFailure(ExceptionInInitializerError error) {
    Objects.requireNonNull(error, "error");
    Throwable cause = error.getCause();
    if (cause instanceof IllegalStateException illegalStateException) {
      return illegalStateException;
    }
    Throwable reportedCause = cause == null ? error : cause;
    return new IllegalStateException(
        Objects.requireNonNullElse(
            reportedCause.getMessage(), "Failed to initialize SQLite native library."),
        reportedCause);
  }

  private static SqliteApi loadApi() {
    return loadApi(
        configuredLibraryTarget(
            System.getenv(SqliteRuntime.LIBRARY_ENVIRONMENT_VARIABLE),
            System.getProperty(SqliteRuntime.BUNDLE_HOME_SYSTEM_PROPERTY)));
  }

  private static SqliteApi loadApi(SqliteLibraryTarget libraryTarget) {
    // The downcall handles and looked-up SQLite symbols must stay valid for the whole JVM lifetime
    // once published into SQLITE_API, so this shared arena is intentionally process-scoped.
    Arena libraryArena = Arena.ofShared();
    try {
      SymbolLookup lookup = SymbolLookup.libraryLookup(libraryTarget.lookupTarget(), libraryArena);
      MethodHandle sqlite3Libversion =
          downcall(lookup, "sqlite3_libversion", FunctionDescriptor.of(ValueLayout.ADDRESS));
      MethodHandle sqlite3mcVersion =
          downcall(lookup, "sqlite3mc_version", FunctionDescriptor.of(ValueLayout.ADDRESS));
      MethodHandle sqlite3CompileoptionUsed =
          downcall(
              lookup,
              "sqlite3_compileoption_used",
              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
      String loadedVersion =
          requireSupportedVersion(sqliteVersion(sqlite3Libversion, STRLEN), libraryTarget.mode());
      String loadedSqlite3mcVersion =
          requireSupportedSqlite3mcVersion(
              sqlite3MultipleCiphersVersion(sqlite3mcVersion, STRLEN), libraryTarget.mode());
      requireSupportedCompileOptions(
          sqlite3CompileoptionUsed, loadedVersion, loadedSqlite3mcVersion, libraryTarget.mode());
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
              "sqlite3_key",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_INT)),
          downcall(
              lookup,
              "sqlite3_rekey",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_INT)),
          downcall(lookup, "sqlite3_shutdown", FunctionDescriptor.of(ValueLayout.JAVA_INT)),
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
              "sqlite3_errstr",
              FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT)),
          downcall(
              lookup,
              "sqlite3_extended_errcode",
              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)),
          loadedVersion,
          loadedSqlite3mcVersion);
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

  static SqliteLibraryTarget configuredLibraryTarget(@Nullable String configuredLibraryPath) {
    return new SqliteLibraryTarget(
        SqliteRuntime.LIBRARY_MODE, normalizeConfiguredLibraryPath(configuredLibraryPath));
  }

  static SqliteLibraryTarget configuredLibraryTarget(
      @Nullable String configuredLibraryPath, @Nullable String bundleHomePath) {
    String normalizedConfiguredPath = normalizeNullableConfiguredLibraryPath(configuredLibraryPath);
    if (normalizedConfiguredPath != null) {
      return new SqliteLibraryTarget(SqliteRuntime.LIBRARY_MODE, normalizedConfiguredPath);
    }
    String normalizedBundleHomePath = normalizeNullablePath(bundleHomePath);
    if (normalizedBundleHomePath != null) {
      return bundledLibraryTarget(normalizedBundleHomePath);
    }
    throw missingLibraryTargetFailure();
  }

  private static String normalizeConfiguredLibraryPath(@Nullable String configuredLibraryPath) {
    if (configuredLibraryPath == null) {
      throw missingLibraryTargetFailure();
    }
    String normalizedPath = configuredLibraryPath.trim();
    if (normalizedPath.isEmpty()) {
      throw new IllegalStateException(
          SqliteRuntime.LIBRARY_ENVIRONMENT_VARIABLE + " must not be blank.");
    }
    return Path.of(normalizedPath).toAbsolutePath().normalize().toString();
  }

  private static @Nullable String normalizeNullableConfiguredLibraryPath(
      @Nullable String configuredLibraryPath) {
    if (configuredLibraryPath == null) {
      return null;
    }
    String normalizedPath = configuredLibraryPath.trim();
    if (normalizedPath.isEmpty()) {
      return null;
    }
    return Path.of(normalizedPath).toAbsolutePath().normalize().toString();
  }

  private static @Nullable String normalizeNullablePath(@Nullable String path) {
    if (path == null) {
      return null;
    }
    String normalizedPath = path.trim();
    if (normalizedPath.isEmpty()) {
      return null;
    }
    return Path.of(normalizedPath).toAbsolutePath().normalize().toString();
  }

  private static SqliteLibraryTarget bundledLibraryTarget(String normalizedBundleHomePath) {
    Path bundleLibraryPath =
        Path.of(normalizedBundleHomePath)
            .resolve("lib")
            .resolve("native")
            .resolve(supportedNativeLibraryFileName());
    if (!Files.isRegularFile(bundleLibraryPath)) {
      throw new IllegalStateException(
          "FinGrind bundle home at "
              + normalizedBundleHomePath
              + " does not contain the managed SQLite library at "
              + bundleLibraryPath
              + ". Use the published FinGrind bundle launcher as extracted (bin/fingrind on macOS/Linux or bin\\fingrind.cmd on Windows), or for a local source checkout set "
              + SqliteRuntime.LIBRARY_ENVIRONMENT_VARIABLE
              + " to the managed SQLite 3.53.0 / SQLite3 Multiple Ciphers 2.3.3 shared library produced by ./gradlew prepareManagedSqlite.");
    }
    return new SqliteLibraryTarget(SqliteRuntime.LIBRARY_MODE, bundleLibraryPath.toString());
  }

  private static IllegalStateException missingLibraryTargetFailure() {
    return new IllegalStateException(
        "FinGrind could not locate the managed SQLite runtime. Run the published FinGrind bundle launcher (bin/fingrind on macOS/Linux or bin\\fingrind.cmd on Windows), or for a local source checkout set "
            + SqliteRuntime.LIBRARY_ENVIRONMENT_VARIABLE
            + " to the managed SQLite 3.53.0 / SQLite3 Multiple Ciphers 2.3.3 shared library produced by ./gradlew prepareManagedSqlite.");
  }

  private static String supportedNativeLibraryFileName() {
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
        "FinGrind bundles currently support managed SQLite on macOS, Linux, and Windows only. Detected: "
            + System.getProperty("os.name"));
  }

  private static SqliteNativeException failure(int resultCode, SqliteApi sqliteApi) {
    String resultName = resultName(resultCode);
    String errorString = errorString(resultCode, sqliteApi.sqlite3Errstr, STRLEN);
    String message = errorString.equals(resultName) ? resultName : resultName + ": " + errorString;
    return new SqliteNativeException(resultCode, message);
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
      throw failure(resultCode, sqliteApi);
    }
  }

  private static void closeQuietly(MemorySegment databaseHandle, SqliteApi sqliteApi) {
    try {
      int ignored = (int) sqliteApi.sqlite3CloseV2.invokeExact(databaseHandle);
    } catch (Throwable ignored) {
      // Preserve the original open failure.
    }
  }

  private static void applyKey(
      MemorySegment databaseHandle,
      SqliteBookPassphrase bookPassphrase,
      SqliteApi sqliteApi,
      Arena arena)
      throws SqliteNativeException {
    try {
      MemorySegment keyPointer = bookPassphrase.copyToCString(arena);
      int resultCode =
          (int)
              sqliteApi.sqlite3Key.invokeExact(
                  databaseHandle, keyPointer, bookPassphrase.byteLength());
      requireOpenConfigurationSuccess(databaseHandle, resultCode, sqliteApi);
    } catch (SqliteNativeException exception) {
      throw exception;
    } catch (Throwable throwable) {
      throw new IllegalStateException(
          "Failed to apply the FinGrind SQLite book passphrase from "
              + bookPassphrase.sourceDescription()
              + ".",
          throwable);
    }
  }

  private static void validateConfiguredKey(MemorySegment databaseHandle)
      throws SqliteNativeException {
    try (Arena arena = Arena.ofConfined()) {
      executeScript(databaseHandle, arena.allocateFrom(KEY_VALIDATION_QUERY));
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

  record SqliteLibraryTarget(String mode, String lookupTarget) {
    SqliteLibraryTarget {
      mode = requireText(mode, "mode");
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

  /** Native SQLite open policies that FinGrind maps from its command-level access intents. */
  enum OpenMode {
    READ_ONLY(SQLITE_OPEN_READONLY),
    READ_WRITE_EXISTING(SQLITE_OPEN_READWRITE),
    READ_WRITE_CREATE(SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE);

    private final int flags;

    OpenMode(int flags) {
      this.flags = flags;
    }

    int flags() {
      return flags;
    }
  }

  static int activeConnectionCount() {
    return ACTIVE_CONNECTIONS.get();
  }

  /** Lazily publishes the process-scoped SQLite native API bundle on first use. */
  @SuppressWarnings("PMD.DoNotUseThreads")
  private static final class SqliteApiHolder {
    private static final SqliteApi INSTANCE = loadApi();

    private SqliteApiHolder() {}

    static {
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(SqliteNativeLibrary::shutdownIfQuiescent, "fingrind-sqlite3mc-shutdown"));
    }
  }

  /** Loaded SQLite symbol handles and process-scoped resources for one JVM lifetime. */
  private static final class SqliteApi {
    @SuppressWarnings("unused")
    private final Arena libraryArena;

    private final MethodHandle sqlite3OpenV2;
    private final MethodHandle sqlite3CloseV2;
    private final MethodHandle sqlite3Key;
    private final MethodHandle sqlite3Rekey;
    private final MethodHandle sqlite3Shutdown;
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
    private final MethodHandle sqlite3Errstr;
    private final MethodHandle sqlite3ExtendedErrcode;
    private final String loadedVersion;
    private final String loadedSqlite3mcVersion;

    private SqliteApi(
        Arena libraryArena,
        MethodHandle sqlite3OpenV2,
        MethodHandle sqlite3CloseV2,
        MethodHandle sqlite3Key,
        MethodHandle sqlite3Rekey,
        MethodHandle sqlite3Shutdown,
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
        MethodHandle sqlite3Errstr,
        MethodHandle sqlite3ExtendedErrcode,
        String loadedVersion,
        String loadedSqlite3mcVersion) {
      this.libraryArena = Objects.requireNonNull(libraryArena, "libraryArena");
      this.sqlite3OpenV2 = Objects.requireNonNull(sqlite3OpenV2, "sqlite3OpenV2");
      this.sqlite3CloseV2 = Objects.requireNonNull(sqlite3CloseV2, "sqlite3CloseV2");
      this.sqlite3Key = Objects.requireNonNull(sqlite3Key, "sqlite3Key");
      this.sqlite3Rekey = Objects.requireNonNull(sqlite3Rekey, "sqlite3Rekey");
      this.sqlite3Shutdown = Objects.requireNonNull(sqlite3Shutdown, "sqlite3Shutdown");
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
      this.sqlite3Errstr = Objects.requireNonNull(sqlite3Errstr, "sqlite3Errstr");
      this.sqlite3ExtendedErrcode =
          Objects.requireNonNull(sqlite3ExtendedErrcode, "sqlite3ExtendedErrcode");
      this.loadedVersion = Objects.requireNonNull(loadedVersion, "loadedVersion");
      this.loadedSqlite3mcVersion =
          Objects.requireNonNull(loadedSqlite3mcVersion, "loadedSqlite3mcVersion");
    }
  }

  private static void shutdownIfQuiescent() {
    shutdownIfQuiescent(SqliteApiHolder.INSTANCE.sqlite3Shutdown, activeConnectionCount());
  }

  static void shutdownIfQuiescent(MethodHandle sqlite3Shutdown, int activeConnections) {
    Objects.requireNonNull(sqlite3Shutdown, "sqlite3Shutdown");
    if (activeConnections == 0) {
      shutdownQuietly(sqlite3Shutdown);
    }
  }

  static void shutdownQuietly(MethodHandle sqlite3Shutdown) {
    Objects.requireNonNull(sqlite3Shutdown, "sqlite3Shutdown");
    try {
      int ignored = (int) sqlite3Shutdown.invokeExact();
    } catch (Throwable ignored) {
      // Process exit owns final cleanup; best-effort shutdown is sufficient here.
    }
  }
}
