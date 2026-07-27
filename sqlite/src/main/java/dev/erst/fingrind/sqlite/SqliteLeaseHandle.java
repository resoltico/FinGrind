package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Holds one retained coordination control-file lock until its workflow releases the handle. */
final class SqliteLeaseHandle implements AutoCloseable {
  private final Path controlPath;
  private final SqliteCoordinationControlFiles.LockedControlFile control;
  private boolean closed;

  SqliteLeaseHandle(Path controlPath, SqliteCoordinationControlFiles.LockedControlFile control) {
    this.controlPath = Objects.requireNonNull(controlPath, "controlPath");
    this.control = Objects.requireNonNull(control, "control");
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    try {
      control.close();
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to release the FinGrind maintenance control-file lock at " + controlPath + ".",
          exception);
    }
  }
}
