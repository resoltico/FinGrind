package dev.erst.fingrind.sqlite;

import java.util.List;
import java.util.Objects;

/** Reports a loaded SQLite runtime that is missing FinGrind's required hardening options. */
public final class UnsupportedSqliteCompileOptionsException extends IllegalStateException {
  private static final long serialVersionUID = 1L;

  private final String loadedSqliteVersion;
  private final String loadedSqlite3mcVersion;
  private final String loadedSourceId;
  private final String libraryMode;
  private final List<String> missingCompileOptions;
  private final List<String> forbiddenCompileOptions;

  UnsupportedSqliteCompileOptionsException(
      String loadedSqliteVersion,
      String loadedSqlite3mcVersion,
      String libraryMode,
      List<String> missingCompileOptions) {
    this(
        loadedSqliteVersion,
        loadedSqlite3mcVersion,
        SqliteRuntime.REQUIRED_SQLITE_SOURCE_ID,
        libraryMode,
        missingCompileOptions,
        List.of());
  }

  UnsupportedSqliteCompileOptionsException(
      String loadedSqliteVersion,
      String loadedSqlite3mcVersion,
      String loadedSourceId,
      String libraryMode,
      List<String> missingCompileOptions,
      List<String> forbiddenCompileOptions) {
    super(
        message(
            loadedSqliteVersion,
            loadedSqlite3mcVersion,
            loadedSourceId,
            libraryMode,
            missingCompileOptions,
            forbiddenCompileOptions));
    this.loadedSqliteVersion = requireText(loadedSqliteVersion, "loadedSqliteVersion");
    this.loadedSqlite3mcVersion = requireText(loadedSqlite3mcVersion, "loadedSqlite3mcVersion");
    this.loadedSourceId = requireText(loadedSourceId, "loadedSourceId");
    this.libraryMode = requireText(libraryMode, "libraryMode");
    this.missingCompileOptions =
        List.copyOf(Objects.requireNonNull(missingCompileOptions, "missingCompileOptions"));
    this.forbiddenCompileOptions =
        List.copyOf(Objects.requireNonNull(forbiddenCompileOptions, "forbiddenCompileOptions"));
    if (this.missingCompileOptions.isEmpty() && this.forbiddenCompileOptions.isEmpty()) {
      throw new IllegalArgumentException(
          "At least one missing or forbidden compile option must be reported.");
    }
  }

  public String loadedSqliteVersion() {
    return loadedSqliteVersion;
  }

  public String loadedSqlite3mcVersion() {
    return loadedSqlite3mcVersion;
  }

  public String loadedSourceId() {
    return loadedSourceId;
  }

  public String libraryMode() {
    return libraryMode;
  }

  public List<String> missingCompileOptions() {
    return missingCompileOptions;
  }

  public List<String> forbiddenCompileOptions() {
    return forbiddenCompileOptions;
  }

  private static String message(
      String loadedSqliteVersion,
      String loadedSqlite3mcVersion,
      String loadedSourceId,
      String libraryMode,
      List<String> missingCompileOptions,
      List<String> forbiddenCompileOptions) {
    List<String> missingOptions =
        List.copyOf(Objects.requireNonNull(missingCompileOptions, "missingCompileOptions"));
    List<String> forbiddenOptions =
        List.copyOf(Objects.requireNonNull(forbiddenCompileOptions, "forbiddenCompileOptions"));
    String detail =
        java.util.stream.Stream.of(
                missingOptions.isEmpty()
                    ? null
                    : "missing required compile options: " + String.join(", ", missingOptions),
                forbiddenOptions.isEmpty()
                    ? null
                    : "forbidden compile options were present: "
                        + String.join(", ", forbiddenOptions))
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.joining("; "));
    return "SQLite "
        + loadedSqliteVersion
        + " / SQLite3 Multiple Ciphers "
        + loadedSqlite3mcVersion
        + " / source id "
        + loadedSourceId
        + " in "
        + libraryMode
        + " violated the FinGrind compile-option contract: "
        + detail;
  }

  private static String requireText(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName);
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank.");
    }
    return value;
  }
}
