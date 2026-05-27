package dev.erst.fingrind.sqlite;

import java.util.Objects;

/** Freshly created lease file owned by the current workflow. */
record SqliteCreatedLease(SqliteLeaseFileHandle leaseFileHandle) implements SqliteLeaseCreation {
  SqliteCreatedLease {
    Objects.requireNonNull(leaseFileHandle, "leaseFileHandle");
  }
}
