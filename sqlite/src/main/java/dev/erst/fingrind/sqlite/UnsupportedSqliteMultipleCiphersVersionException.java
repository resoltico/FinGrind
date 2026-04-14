package dev.erst.fingrind.sqlite;

/** Signals that a loaded SQLite3 Multiple Ciphers library is outside FinGrind's audited pin. */
final class UnsupportedSqliteMultipleCiphersVersionException extends IllegalStateException {
  private static final long serialVersionUID = 1L;

  private final String loadedVersion;
  private final String requiredVersion;
  private final String libraryMode;

  UnsupportedSqliteMultipleCiphersVersionException(
      String loadedVersion, String requiredVersion, String libraryMode) {
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
}
