package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Explicitly owns one retained coordination control lock until it is transferred or released. */
final class SqliteOwnedLockedControlFile {
  private SqliteCoordinationControlFiles.@Nullable LockedControlFile controlFile;

  private SqliteOwnedLockedControlFile(
      SqliteCoordinationControlFiles.LockedControlFile controlFile) {
    this.controlFile = Objects.requireNonNull(controlFile, "controlFile");
  }

  static @Nullable SqliteOwnedLockedControlFile acquire(
      SqliteCoordinationControlFiles.@Nullable LockedControlFile controlFile) {
    return controlFile == null ? null : new SqliteOwnedLockedControlFile(controlFile);
  }

  SqliteCoordinationControlFiles.LockedControlFile transfer() {
    SqliteCoordinationControlFiles.LockedControlFile transferred =
        Objects.requireNonNull(controlFile, "owned controlFile");
    controlFile = null;
    return transferred;
  }

  void release() throws IOException {
    if (controlFile != null) {
      controlFile.close();
      controlFile = null;
    }
  }
}
