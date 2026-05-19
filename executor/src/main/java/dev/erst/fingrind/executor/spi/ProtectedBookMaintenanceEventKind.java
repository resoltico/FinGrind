package dev.erst.fingrind.executor.spi;

import java.util.Objects;

/** Stable SPI event kinds for durable protected-book maintenance journal records. */
public enum ProtectedBookMaintenanceEventKind {
  BACKUP_CREATED("backup-created"),
  BACKUP_RESTORED("backup-restored"),
  REKEY_ROLLBACK_INSPECTED("rekey-rollback-inspected"),
  REKEY_ROLLBACK_RESTORED("rekey-rollback-restored"),
  REKEY_ROLLBACK_DELETED("rekey-rollback-deleted");

  private final String wireValue;

  ProtectedBookMaintenanceEventKind(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  /** Stable journal wire value for one maintenance event kind. */
  public String wireValue() {
    return wireValue;
  }
}
