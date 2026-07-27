package dev.erst.fingrind.core;

import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Reports an indeterminate no-replace-link attempt whose candidate final artifact must be inspected
 * before any retry.
 *
 * <p>The link primitive threw without proving whether its namespace mutation reached storage. A
 * private stage may not have been created ({@code null}) or may remain as retained evidence.
 */
public final class ArtifactPublicationOutcomeUncertainException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final transient Path candidateArtifactPath;
  private final String serializedCandidateArtifactPath;
  private final transient @Nullable ArtifactPublicationRetention retainedStage;
  private final ArtifactPublicationExceptionDetails.@Nullable SerializedRetention
      serializedRetainedStage;

  /** Retains the candidate final path and any stage created before the indeterminate link. */
  public ArtifactPublicationOutcomeUncertainException(
      Path candidateArtifactPath,
      @Nullable ArtifactPublicationRetention retainedStage,
      Throwable cause) {
    super(
        "FinGrind could not establish the outcome of a no-replace artifact publication attempt.",
        Objects.requireNonNull(cause, "cause"));
    this.candidateArtifactPath =
        Objects.requireNonNull(candidateArtifactPath, "candidateArtifactPath")
            .toAbsolutePath()
            .normalize();
    @Nullable ArtifactPublicationRetention checkedRetainedStage = retainedStage;
    if (checkedRetainedStage != null
        && this.candidateArtifactPath.equals(checkedRetainedStage.retainedStagePath())) {
      throw new IllegalArgumentException(
          "An indeterminate publication candidate and its retained stage must name distinct"
              + " canonical paths.");
    }
    this.serializedCandidateArtifactPath =
        ArtifactPublicationExceptionDetails.pathText(
            this.candidateArtifactPath, "candidateArtifactPath");
    this.retainedStage = checkedRetainedStage;
    this.serializedRetainedStage =
        ArtifactPublicationExceptionDetails.capture(checkedRetainedStage);
  }

  /** Returns the canonical final candidate whose existence was not established. */
  public Path candidateArtifactPath() {
    return candidateArtifactPath == null
        ? ArtifactPublicationExceptionDetails.path(serializedCandidateArtifactPath)
        : candidateArtifactPath;
  }

  /** Returns the private stage retained before the indeterminate link, when one was created. */
  public @Nullable ArtifactPublicationRetention retainedStage() {
    return retainedStage == null
        ? ArtifactPublicationExceptionDetails.restore(serializedRetainedStage)
        : retainedStage;
  }
}
