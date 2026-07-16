package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.Objects;

/** Internal exception that marks one deterministic caller path-contract violation. */
final class SqliteCallerPathContractException extends IllegalArgumentException {
  private static final long serialVersionUID = 1L;

  private final Path requestedPath;
  private final SqliteCallerPathFailure pathFailure;

  SqliteCallerPathContractException(
      Path requestedPath, SqliteCallerPathFailure pathFailure, String message) {
    super(Objects.requireNonNull(message, "message"));
    this.requestedPath = Objects.requireNonNull(requestedPath, "requestedPath");
    this.pathFailure = Objects.requireNonNull(pathFailure, "pathFailure");
  }

  SqliteCallerPathContractException(
      Path requestedPath, SqliteCallerPathFailure pathFailure, String message, Throwable cause) {
    super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
    this.requestedPath = Objects.requireNonNull(requestedPath, "requestedPath");
    this.pathFailure = Objects.requireNonNull(pathFailure, "pathFailure");
  }

  Path requestedPath() {
    return requestedPath;
  }

  SqliteCallerPathFailure pathFailure() {
    return pathFailure;
  }
}
