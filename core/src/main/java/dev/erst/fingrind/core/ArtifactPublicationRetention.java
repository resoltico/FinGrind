package dev.erst.fingrind.core;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Names the private stage deliberately retained by a no-clobber artifact publication attempt.
 *
 * <p>FinGrind never deletes, replaces, or reuses this pathname after it has been created. The
 * retained stage is evidence of the exact bytes used for the final hard-link publication and makes
 * a later retry choose a fresh destination rather than mutating an ambiguous prior attempt.
 */
public record ArtifactPublicationRetention(Path retainedStagePath) {
  /** Normalizes the retained artifact name without asserting that it remains materialized. */
  public ArtifactPublicationRetention {
    retainedStagePath =
        Objects.requireNonNull(retainedStagePath, "retainedStagePath").toAbsolutePath().normalize();
    if (retainedStagePath.getFileName() == null || retainedStagePath.getParent() == null) {
      throw new IllegalArgumentException(
          "retainedStagePath must name an artifact in a parent directory.");
    }
  }
}
