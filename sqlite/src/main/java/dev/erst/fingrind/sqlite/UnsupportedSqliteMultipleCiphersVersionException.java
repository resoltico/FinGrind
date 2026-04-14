package dev.erst.fingrind.sqlite;

/** Signals that a loaded SQLite3 Multiple Ciphers library is outside FinGrind's audited pin. */
final class UnsupportedSqliteMultipleCiphersVersionException extends IllegalStateException {
  private static final long serialVersionUID = 1L;

  private final String loadedVersion;
  private final String requiredVersion;
  private final String librarySource;

  UnsupportedSqliteMultipleCiphersVersionException(
      String loadedVersion, String requiredVersion, String librarySource) {
    super(
        "FinGrind requires SQLite3 Multiple Ciphers "
            + requiredVersion
            + " but loaded "
            + loadedVersion
            + " from the "
            + librarySource
            + " library.");
    this.loadedVersion = loadedVersion;
    this.requiredVersion = requiredVersion;
    this.librarySource = librarySource;
  }

  String loadedVersion() {
    return loadedVersion;
  }

  String requiredVersion() {
    return requiredVersion;
  }

  String librarySource() {
    return librarySource;
  }
}
