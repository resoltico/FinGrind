package dev.erst.fingrind.sqlite;

/** Signals that a loaded SQLite source build is outside FinGrind's audited provenance pin. */
final class UnsupportedSqliteSourceIdException extends IllegalStateException {
  private static final long serialVersionUID = 1L;

  private final String loadedSourceId;
  private final String requiredSourceId;
  private final String libraryMode;
  private final String loadedSqliteVersion;
  private final String loadedSqlite3mcVersion;

  UnsupportedSqliteSourceIdException(
      String loadedSourceId,
      String requiredSourceId,
      String libraryMode,
      String loadedSqliteVersion,
      String loadedSqlite3mcVersion) {
    super(
        "FinGrind requires SQLite source id "
            + requiredSourceId
            + " but loaded "
            + loadedSourceId
            + " in "
            + libraryMode
            + " mode.");
    this.loadedSourceId = loadedSourceId;
    this.requiredSourceId = requiredSourceId;
    this.libraryMode = libraryMode;
    this.loadedSqliteVersion = loadedSqliteVersion;
    this.loadedSqlite3mcVersion = loadedSqlite3mcVersion;
  }

  String loadedSourceId() {
    return loadedSourceId;
  }

  String requiredSourceId() {
    return requiredSourceId;
  }

  String libraryMode() {
    return libraryMode;
  }

  String loadedSqliteVersion() {
    return loadedSqliteVersion;
  }

  String loadedSqlite3mcVersion() {
    return loadedSqlite3mcVersion;
  }
}
