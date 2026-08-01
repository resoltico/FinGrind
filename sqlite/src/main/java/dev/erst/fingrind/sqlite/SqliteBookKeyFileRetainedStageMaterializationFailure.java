package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.ArtifactPublicationRetention;
import java.nio.file.Path;
import java.util.Objects;

/** Marks a stage-write failure whose exact retained path must reach the deterministic boundary. */
final class SqliteBookKeyFileRetainedStageMaterializationFailure extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final Path retainedStagePath;

  SqliteBookKeyFileRetainedStageMaterializationFailure(
      ArtifactPublicationRetention retention, Throwable cause) {
    super(
        "FinGrind retained a private book-key stage after it could not be materialized.",
        Objects.requireNonNull(cause, "cause"));
    retainedStagePath = Objects.requireNonNull(retention, "retention").retainedStagePath();
  }

  ArtifactPublicationRetention retention() {
    return new ArtifactPublicationRetention(retainedStagePath);
  }
}
