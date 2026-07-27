package dev.erst.fingrind.sqlite;

import java.util.Objects;

/** Explicitly owns one acquired lease until the owner transfers or releases it exactly once. */
final class SqliteOwnedHeldLease {
  private @org.jspecify.annotations.Nullable SqliteHeldLease lease;

  private SqliteOwnedHeldLease(SqliteHeldLease lease) {
    this.lease = Objects.requireNonNull(lease, "lease");
  }

  static SqliteOwnedHeldLease acquire(SqliteProtectedBookLeaseAcquisition acquisition) {
    return new SqliteOwnedHeldLease(
        (SqliteHeldLease) Objects.requireNonNull(acquisition, "acquisition"));
  }

  SqliteHeldLease borrowedLease() {
    return Objects.requireNonNull(lease, "owned lease");
  }

  SqliteHeldLease transfer() {
    SqliteHeldLease transferred = Objects.requireNonNull(lease, "owned lease");
    lease = null;
    return transferred;
  }

  void release() {
    if (lease != null) {
      lease.close();
      lease = null;
    }
  }
}
