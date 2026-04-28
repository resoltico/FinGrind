package dev.erst.fingrind.sqlite;

/** Signals that a loaded SQLite3 Multiple Ciphers library is outside FinGrind's audited pin. */
final class UnsupportedSqliteMultipleCiphersVersionException extends IllegalStateException {
  private static final long serialVersionUID = 1L;

  private final String loadedVersion;
  private final String requiredVersion;
  private final String libraryMode;
  private final String loadedSqliteVersion;
  private final String loadedSourceId;

  UnsupportedSqliteMultipleCiphersVersionException(
      String loadedVersion, String requiredVersion, String libraryMode) {
    this(
        loadedVersion,
        requiredVersion,
        libraryMode,
        SqliteRuntime.REQUIRED_MINIMUM_SQLITE_VERSION,
        SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID);
  }

  UnsupportedSqliteMultipleCiphersVersionException(
      String loadedVersion,
      String requiredVersion,
      String libraryMode,
      String loadedSqliteVersion,
      String loadedSourceId) {
    super(
        "FinGrind requires SQLite3 Multiple Ciphers "
            + requiredVersion
            + " but loaded "
            + loadedVersion
            + " in "
            + libraryMode
            + " mode.");
    this.loadedVersion = loadedVersion;
    this.requiredVersion = requiredVersion;
    this.libraryMode = libraryMode;
    this.loadedSqliteVersion = loadedSqliteVersion;
    this.loadedSourceId = loadedSourceId;
  }

  String loadedVersion() {
    return loadedVersion;
  }

  String requiredVersion() {
    return requiredVersion;
  }

  String libraryMode() {
    return libraryMode;
  }

  String loadedSqliteVersion() {
    return loadedSqliteVersion;
  }

  String loadedSourceId() {
    return loadedSourceId;
  }
}
