package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.Objects;

/** Detached owner of one coordination lease artifact that survives until the workflow closes it. */
final class SqliteLeaseHandle {
  private final Path leasePath;

  SqliteLeaseHandle(Path leasePath) {
    this.leasePath = Objects.requireNonNull(leasePath, "leasePath");
  }

  void closeAndDelete() {
    SqliteBookMaintenanceLease.releaseLeaseArtifactQuietly(leasePath);
  }
}
