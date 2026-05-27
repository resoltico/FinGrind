package dev.erst.fingrind.sqlite;

/** Intent for acquiring one maintenance lease around an existing artifact or a managed target. */
enum SqliteMaintenanceLeaseIntent {
  /** Lease one artifact that must already exist as a regular file. */
  EXISTING_ARTIFACT,
  /** Lease one target path that FinGrind may prepare and publish into. */
  MANAGED_TARGET
}
