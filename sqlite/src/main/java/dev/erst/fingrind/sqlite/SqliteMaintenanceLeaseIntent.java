package dev.erst.fingrind.sqlite;

/** Intent for acquiring one maintenance lease around an existing artifact or a managed target. */
enum SqliteMaintenanceLeaseIntent {
  /** Lease one artifact that must already exist as a regular file and is safe to inspect. */
  EXISTING_ARTIFACT(true),
  /**
   * Lease one target path that FinGrind may prepare and publish into.
   *
   * <p>A target can already be a caller-owned ordinary file. Its publication policy must classify
   * that occupancy before any operation attempts to identify or inspect it as a FinGrind artifact.
   */
  MANAGED_TARGET(false);

  private final boolean requiresNativeActivityCheck;

  SqliteMaintenanceLeaseIntent(boolean requiresNativeActivityCheck) {
    this.requiresNativeActivityCheck = requiresNativeActivityCheck;
  }

  boolean requiresNativeActivityCheck() {
    return requiresNativeActivityCheck;
  }
}
