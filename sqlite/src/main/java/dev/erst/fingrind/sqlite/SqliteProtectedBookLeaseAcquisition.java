package dev.erst.fingrind.sqlite;

import java.nio.file.Path;

/** Lease-acquisition result for one protected-book artifact path. */
sealed interface SqliteProtectedBookLeaseAcquisition permits SqliteHeldLease, SqliteLeaseBusy {
  /** Returns the normalized artifact path that this acquisition outcome belongs to. */
  Path artifactPath();
}
