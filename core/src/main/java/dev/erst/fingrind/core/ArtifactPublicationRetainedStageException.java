package dev.erst.fingrind.core;

import java.io.IOException;
import java.util.Objects;

/**
 * Preserves a primary stage-creation failure together with the stage that must never be mutated.
 */
public final class ArtifactPublicationRetainedStageException extends IOException {
  private static final long serialVersionUID = 1L;

  private final transient ArtifactPublicationRetention retainedStage;
  private final ArtifactPublicationExceptionDetails.SerializedRetention serializedRetainedStage;

  /** Records the exact retained stage without replacing the primary publication failure. */
  public ArtifactPublicationRetainedStageException(
      ArtifactPublicationRetention retainedStage, Throwable cause) {
    super(
        "FinGrind retained a private artifact stage after publication did not complete.",
        Objects.requireNonNull(cause, "cause"));
    this.retainedStage = Objects.requireNonNull(retainedStage, "retainedStage");
    this.serializedRetainedStage =
        Objects.requireNonNull(
            ArtifactPublicationExceptionDetails.capture(this.retainedStage),
            "serializedRetainedStage");
  }

  /** Returns the stage created before the primary failure. */
  public ArtifactPublicationRetention retainedStage() {
    return retainedStage == null
        ? Objects.requireNonNull(
            ArtifactPublicationExceptionDetails.restore(serializedRetainedStage),
            "serializedRetainedStage")
        : retainedStage;
  }
}
