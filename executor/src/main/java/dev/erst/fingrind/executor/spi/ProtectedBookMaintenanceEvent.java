package dev.erst.fingrind.executor.spi;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** One durable SPI audit fact for a successful protected-book maintenance workflow. */
public record ProtectedBookMaintenanceEvent(
    Instant recordedAt,
    ProtectedBookMaintenanceEventKind kind,
    Path bookFilePath,
    @Nullable Path backupFilePath,
    @Nullable Path backupBookKeyFilePath,
    List<Path> rollbackArtifactPaths,
    @Nullable Path rollbackArtifactPath) {
  public ProtectedBookMaintenanceEvent {
    Objects.requireNonNull(recordedAt, "recordedAt");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(bookFilePath, "bookFilePath");
    rollbackArtifactPaths =
        List.copyOf(Objects.requireNonNull(rollbackArtifactPaths, "rollbackArtifactPaths"));
  }

  /** Returns one durable event for successful encrypted-backup creation. */
  public static ProtectedBookMaintenanceEvent backupCreated(
      Instant recordedAt, Path bookFilePath, Path backupFilePath, Path backupBookKeyFilePath) {
    return new ProtectedBookMaintenanceEvent(
        recordedAt,
        ProtectedBookMaintenanceEventKind.BACKUP_CREATED,
        bookFilePath,
        backupFilePath,
        backupBookKeyFilePath,
        List.of(),
        null);
  }

  /** Returns one durable event for successful encrypted-backup restore. */
  public static ProtectedBookMaintenanceEvent backupRestored(
      Instant recordedAt, Path bookFilePath, Path backupFilePath, Path backupBookKeyFilePath) {
    return new ProtectedBookMaintenanceEvent(
        recordedAt,
        ProtectedBookMaintenanceEventKind.BACKUP_RESTORED,
        bookFilePath,
        backupFilePath,
        backupBookKeyFilePath,
        List.of(),
        null);
  }

  /** Returns one durable event for rollback-artifact inspection over one selected live book. */
  public static ProtectedBookMaintenanceEvent rollbackArtifactsInspected(
      Instant recordedAt, Path bookFilePath, List<Path> rollbackArtifactPaths) {
    return new ProtectedBookMaintenanceEvent(
        recordedAt,
        ProtectedBookMaintenanceEventKind.REKEY_ROLLBACK_INSPECTED,
        bookFilePath,
        null,
        null,
        rollbackArtifactPaths,
        null);
  }

  /** Returns one durable event for successful rollback-artifact restore over one live book. */
  public static ProtectedBookMaintenanceEvent rollbackArtifactRestored(
      Instant recordedAt, Path bookFilePath, Path rollbackArtifactPath) {
    return new ProtectedBookMaintenanceEvent(
        recordedAt,
        ProtectedBookMaintenanceEventKind.REKEY_ROLLBACK_RESTORED,
        bookFilePath,
        null,
        null,
        List.of(rollbackArtifactPath),
        rollbackArtifactPath);
  }

  /** Returns one durable event for successful rollback-artifact deletion beside one live book. */
  public static ProtectedBookMaintenanceEvent rollbackArtifactDeleted(
      Instant recordedAt, Path bookFilePath, Path rollbackArtifactPath) {
    return new ProtectedBookMaintenanceEvent(
        recordedAt,
        ProtectedBookMaintenanceEventKind.REKEY_ROLLBACK_DELETED,
        bookFilePath,
        null,
        null,
        List.of(rollbackArtifactPath),
        rollbackArtifactPath);
  }
}
