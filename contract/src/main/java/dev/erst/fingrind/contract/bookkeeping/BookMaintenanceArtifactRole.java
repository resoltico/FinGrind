package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.WireValue;
import java.util.List;

/** Stable public artifact roles for verification-driven maintenance refusals. */
public enum BookMaintenanceArtifactRole implements WireValue {
  LIVE_BOOK,
  BACKUP_SOURCE,
  BACKUP_TARGET,
  BACKUP_KEY_TARGET,
  RESTORED_TARGET;

  @Override
  public String wireValue() {
    return switch (this) {
      case LIVE_BOOK -> "live-book";
      case BACKUP_SOURCE -> "backup-source";
      case BACKUP_TARGET -> "backup-target";
      case BACKUP_KEY_TARGET -> "backup-key-target";
      case RESTORED_TARGET -> "restored-target";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(BookMaintenanceArtifactRole.class);
  }
}
