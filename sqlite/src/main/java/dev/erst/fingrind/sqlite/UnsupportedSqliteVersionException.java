package dev.erst.fingrind.sqlite;

/** Signals that a loaded SQLite library is below FinGrind's supported minimum version. */
final class UnsupportedSqliteVersionException extends IllegalStateException {
  private static final long serialVersionUID = 1L;

  private final String loadedVersion;
  private final String requiredMinimumVersion;
  private final String libraryMode;

  UnsupportedSqliteVersionException(
      String loadedVersion, String requiredMinimumVersion, String libraryMode) {
    super(
        "FinGrind requires SQLite "
            + requiredMinimumVersion
            + " or newer but loaded "
            + loadedVersion
            + " in "
            + libraryMode
            + " mode.");
    this.loadedVersion = loadedVersion;
    this.requiredMinimumVersion = requiredMinimumVersion;
    this.libraryMode = libraryMode;
  }

  String loadedVersion() {
    return loadedVersion;
  }

  String requiredMinimumVersion() {
    return requiredMinimumVersion;
  }

  String libraryMode() {
    return libraryMode;
  }
}
