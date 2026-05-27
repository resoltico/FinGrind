package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.Objects;

/** Detached lease-file owner that survives until the workflow closes it. */
final class SqliteLeaseFileHandle {
  private final Path leasePath;

  SqliteLeaseFileHandle(Path leasePath) {
    this.leasePath = Objects.requireNonNull(leasePath, "leasePath");
  }

  void closeAndDelete() {
    SqliteBookMaintenanceLease.releaseLeaseFileQuietly(leasePath);
  }
}
