package dev.erst.fingrind.executor.maintenance;

/** In-book maintenance audit vocabulary for successful protected-book artifact workflows. */
public enum ProtectedBookMaintenanceAuditKind {
  BACKUP_CREATED,
  BACKUP_RESTORED,
  REKEY_ROLLBACK_RESTORED,
  REKEY_ROLLBACK_DELETED
}
