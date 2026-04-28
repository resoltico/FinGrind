package dev.erst.fingrind.sqlite;

/** Signals that a loaded SQLite library is below FinGrind's supported minimum version. */
final class UnsupportedSqliteVersionException extends IllegalStateException {
  private static final long serialVersionUID = 1L;

  private final String loadedVersion;
  private final String loadedSqlite3mcVersion;
  private final String loadedSourceId;
  private final String requiredMinimumVersion;
  private final String libraryMode;

  UnsupportedSqliteVersionException(
      String loadedVersion, String requiredMinimumVersion, String libraryMode) {
    this(
        loadedVersion,
        requiredMinimumVersion,
        libraryMode,
        SqliteRuntime.REQUIRED_SQLITE3MC_VERSION,
        SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID);
  }

  UnsupportedSqliteVersionException(
      String loadedVersion,
      String requiredMinimumVersion,
      String libraryMode,
      String loadedSqlite3mcVersion,
      String loadedSourceId) {
    super(
        "FinGrind requires SQLite "
            + requiredMinimumVersion
            + " or newer but loaded "
            + loadedVersion
            + " in "
            + libraryMode
            + " mode.");
    this.loadedVersion = loadedVersion;
    this.loadedSqlite3mcVersion = loadedSqlite3mcVersion;
    this.loadedSourceId = loadedSourceId;
    this.requiredMinimumVersion = requiredMinimumVersion;
    this.libraryMode = libraryMode;
  }

  String loadedVersion() {
    return loadedVersion;
  }

  String requiredMinimumVersion() {
    return requiredMinimumVersion;
  }

  String loadedSqlite3mcVersion() {
    return loadedSqlite3mcVersion;
  }

  String loadedSourceId() {
    return loadedSourceId;
  }

  String libraryMode() {
    return libraryMode;
  }
}
