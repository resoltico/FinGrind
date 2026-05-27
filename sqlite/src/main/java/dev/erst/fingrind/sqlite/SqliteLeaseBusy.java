package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.Objects;

/** Busy outcome when one exclusive maintenance lease could not be acquired. */
record SqliteLeaseBusy(Path artifactPath) implements SqliteProtectedBookLeaseAcquisition {
  SqliteLeaseBusy {
    Objects.requireNonNull(artifactPath, "artifactPath");
  }
}
