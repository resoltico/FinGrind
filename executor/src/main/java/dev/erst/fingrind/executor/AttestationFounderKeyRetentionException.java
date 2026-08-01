package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.runtime.OpenBookFailureDetails;
import java.util.List;
import java.util.Objects;

/**
 * Preserves founder-key artifacts created before an unsuccessful genesis preparation.
 *
 * <p>The cause remains the primary preparation failure. The listed artifacts are immutable
 * evidence: FinGrind never deletes, replaces, recreates, or reuses them, and a retry requires fresh
 * paths.
 */
public final class AttestationFounderKeyRetentionException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final List<OpenBookFailureDetails.RetainedOpenBookPreparationArtifact>
      retainedFounderKeyArtifacts;

  /** Records every founder-key artifact retained alongside the primary failure. */
  public AttestationFounderKeyRetentionException(
      List<OpenBookFailureDetails.RetainedOpenBookPreparationArtifact> retainedFounderKeyArtifacts,
      Throwable cause) {
    super(
        "FinGrind retained one or more attestation founder-key artifacts after genesis preparation"
            + " did not complete.",
        Objects.requireNonNull(cause, "cause"));
    this.retainedFounderKeyArtifacts =
        List.copyOf(
            Objects.requireNonNull(retainedFounderKeyArtifacts, "retainedFounderKeyArtifacts"));
    if (this.retainedFounderKeyArtifacts.isEmpty()) {
      throw new IllegalArgumentException(
          "A founder-key retention exception requires at least one retained artifact.");
    }
  }

  /** Returns the ordered public facts for the artifacts FinGrind retained. */
  public List<OpenBookFailureDetails.RetainedOpenBookPreparationArtifact>
      retainedFounderKeyArtifacts() {
    return retainedFounderKeyArtifacts;
  }
}
