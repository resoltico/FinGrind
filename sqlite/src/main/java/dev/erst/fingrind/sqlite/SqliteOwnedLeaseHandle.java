package dev.erst.fingrind.sqlite;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Explicitly owns one coordination lock handle until its lease state retains or releases it. */
final class SqliteOwnedLeaseHandle {
  private @Nullable SqliteLeaseHandle handle;

  private SqliteOwnedLeaseHandle(SqliteLeaseHandle handle) {
    this.handle = Objects.requireNonNull(handle, "handle");
  }

  static @Nullable SqliteOwnedLeaseHandle acquire(@Nullable SqliteLeaseHandle handle) {
    return handle == null ? null : new SqliteOwnedLeaseHandle(handle);
  }

  SqliteLeaseHandle transfer() {
    SqliteLeaseHandle transferred = Objects.requireNonNull(handle, "owned handle");
    handle = null;
    return transferred;
  }

  void release() {
    if (handle != null) {
      handle.close();
      handle = null;
    }
  }
}
