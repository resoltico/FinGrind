package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** One close-once reference to a held exact-artifact maintenance lease. */
final class SqliteHeldLease
    implements SqliteProtectedBookLeaseAcquisition, ProtectedBookMaintenanceStore.HeldLease {
  private final Path artifactPath;
  private final @Nullable String lockedPhysicalObjectIdentity;
  private final Runnable releaseAction;
  private boolean closed;

  SqliteHeldLease(Path artifactPath, Runnable releaseAction) {
    this(artifactPath, null, releaseAction);
  }

  SqliteHeldLease(
      Path artifactPath, @Nullable String lockedPhysicalObjectIdentity, Runnable releaseAction) {
    this.artifactPath = Objects.requireNonNull(artifactPath, "artifactPath");
    this.lockedPhysicalObjectIdentity = lockedPhysicalObjectIdentity;
    this.releaseAction = Objects.requireNonNull(releaseAction, "releaseAction");
  }

  @Override
  public Path artifactPath() {
    return artifactPath;
  }

  /** Returns the exact global object identity whose maintenance exclusion this lease retained. */
  @Nullable String lockedPhysicalObjectIdentity() {
    return lockedPhysicalObjectIdentity;
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    releaseAction.run();
  }
}
