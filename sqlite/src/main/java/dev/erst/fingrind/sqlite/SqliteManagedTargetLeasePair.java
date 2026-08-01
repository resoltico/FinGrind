package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.Objects;

/** Complete-or-busy result for deterministic protected-book target-lease acquisition. */
sealed interface SqliteManagedTargetLeasePair
    permits SqliteManagedTargetLeasesHeld, SqliteManagedTargetLeasesBusy {}

/** Both final-target directory references are held. */
record SqliteManagedTargetLeasesHeld(
    SqliteHeldLease bookTargetLease, SqliteHeldLease secretTargetLease)
    implements SqliteManagedTargetLeasePair {
  SqliteManagedTargetLeasesHeld {
    Objects.requireNonNull(bookTargetLease, "bookTargetLease");
    Objects.requireNonNull(secretTargetLease, "secretTargetLease");
  }
}

/** One target was already active or held by a conflicting workflow. */
record SqliteManagedTargetLeasesBusy(Path artifactPath) implements SqliteManagedTargetLeasePair {
  SqliteManagedTargetLeasesBusy {
    Objects.requireNonNull(artifactPath, "artifactPath");
  }
}

/** Acquires one managed final-target reference under one already-admitted directory scope. */
@FunctionalInterface
interface SqliteManagedTargetLeaseAcquirer {
  /** Acquires the supplied normalized target or returns a busy result. */
  SqliteProtectedBookLeaseAcquisition acquire(Path normalizedTargetPath);
}
