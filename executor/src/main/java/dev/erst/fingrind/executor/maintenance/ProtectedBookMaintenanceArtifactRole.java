package dev.erst.fingrind.executor.maintenance;

/** Local artifact roles for verification-driven protected-book maintenance refusals. */
public enum ProtectedBookMaintenanceArtifactRole {
  LIVE_BOOK,
  BACKUP_SOURCE,
  ROLLBACK_ARTIFACT,
  RESTORED_TARGET
}
