package dev.erst.fingrind.sqlite;

import java.util.List;
import java.util.Objects;

/** Reports a loaded SQLite runtime that is missing FinGrind's required hardening options. */
public final class UnsupportedSqliteCompileOptionsException extends IllegalStateException {
  private static final long serialVersionUID = 1L;

  private final String loadedSqliteVersion;
  private final String loadedSqlite3mcVersion;
  private final String libraryMode;
  private final List<String> missingCompileOptions;

  UnsupportedSqliteCompileOptionsException(
      String loadedSqliteVersion,
      String loadedSqlite3mcVersion,
      String libraryMode,
      List<String> missingCompileOptions) {
    super(message(loadedSqliteVersion, loadedSqlite3mcVersion, libraryMode, missingCompileOptions));
    this.loadedSqliteVersion = requireText(loadedSqliteVersion, "loadedSqliteVersion");
    this.loadedSqlite3mcVersion = requireText(loadedSqlite3mcVersion, "loadedSqlite3mcVersion");
    this.libraryMode = requireText(libraryMode, "libraryMode");
    this.missingCompileOptions =
        List.copyOf(Objects.requireNonNull(missingCompileOptions, "missingCompileOptions"));
    if (this.missingCompileOptions.isEmpty()) {
      throw new IllegalArgumentException("missingCompileOptions must not be empty.");
    }
  }

  public String loadedSqliteVersion() {
    return loadedSqliteVersion;
  }

  public String loadedSqlite3mcVersion() {
    return loadedSqlite3mcVersion;
  }

  public String libraryMode() {
    return libraryMode;
  }

  public List<String> missingCompileOptions() {
    return missingCompileOptions;
  }

  private static String message(
      String loadedSqliteVersion,
      String loadedSqlite3mcVersion,
      String libraryMode,
      List<String> missingCompileOptions) {
    return "SQLite "
        + loadedSqliteVersion
        + " / SQLite3 Multiple Ciphers "
        + loadedSqlite3mcVersion
        + " in "
        + libraryMode
        + " is missing required compile options: "
        + String.join(", ", missingCompileOptions);
  }

  private static String requireText(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName);
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank.");
    }
    return value;
  }
}
