package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/** Held maintenance lease that blocks concurrent destructive workflows on one artifact path. */
final class SqliteHeldLease
    implements SqliteProtectedBookLeaseAcquisition, ProtectedBookMaintenanceStore.HeldLease {
  private final Path artifactPath;
  private final SqliteLeaseFileHandle leaseFileHandle;
  private final ThreadLocal<Set<Path>> ownedArtifactPaths;
  private boolean closed;

  SqliteHeldLease(
      Path artifactPath,
      SqliteLeaseFileHandle leaseFileHandle,
      ThreadLocal<Set<Path>> ownedArtifactPaths) {
    this.artifactPath = Objects.requireNonNull(artifactPath, "artifactPath");
    this.leaseFileHandle = Objects.requireNonNull(leaseFileHandle, "leaseFileHandle");
    this.ownedArtifactPaths = Objects.requireNonNull(ownedArtifactPaths, "ownedArtifactPaths");
  }

  @Override
  public Path artifactPath() {
    return artifactPath;
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    ownedArtifactPaths.get().remove(artifactPath);
    leaseFileHandle.closeAndDelete();
  }
}
