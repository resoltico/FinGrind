package dev.erst.fingrind.executor.maintenance;

/** Local artifact roles for verification-driven protected-book maintenance refusals. */
public enum ProtectedBookMaintenanceArtifactRole {
  LIVE_BOOK,
  LIVE_BOOK_KEY_SOURCE,
  BACKUP_SOURCE,
  BACKUP_KEY_SOURCE,
  BACKUP_TARGET,
  BACKUP_KEY_TARGET,
  RESTORED_TARGET,
  NEW_BOOK_KEY_TARGET
}
