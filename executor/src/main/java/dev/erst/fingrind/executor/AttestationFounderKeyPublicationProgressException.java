package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.PublicationTransactionArtifact;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Reports an incomplete genesis preparation after one or more founder keys completed publication.
 *
 * <p>The completed artifacts remain recoverable by their transaction IDs; callers must not infer or
 * expose any private staging pathname.
 */
public final class AttestationFounderKeyPublicationProgressException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final List<PublicationTransactionArtifact> publishedFounderKeyArtifacts;
  private final @Nullable AttestationFounderKeyPublicationTransactionException
      incompletePublication;

  /** Captures completed founder-key publications and an optional later incomplete publication. */
  public AttestationFounderKeyPublicationProgressException(
      List<PublicationTransactionArtifact> publishedFounderKeyArtifacts,
      @Nullable AttestationFounderKeyPublicationTransactionException incompletePublication,
      RuntimeException cause) {
    super(
        "Genesis preparation did not complete after founder-key publication progress was recorded.",
        Objects.requireNonNull(cause, "cause"));
    this.publishedFounderKeyArtifacts =
        List.copyOf(
            Objects.requireNonNull(publishedFounderKeyArtifacts, "publishedFounderKeyArtifacts"));
    if (this.publishedFounderKeyArtifacts.isEmpty()) {
      throw new IllegalArgumentException(
          "Founder-key publication progress requires at least one completed publication.");
    }
    this.incompletePublication = incompletePublication;
  }

  /**
   * Returns every completed founder-key publication recorded before genesis preparation stopped.
   */
  public List<PublicationTransactionArtifact> publishedFounderKeyArtifacts() {
    return publishedFounderKeyArtifacts;
  }

  /**
   * Returns a later incomplete founder-key publication, when one caused the preparation failure.
   */
  public @Nullable AttestationFounderKeyPublicationTransactionException incompletePublication() {
    return incompletePublication;
  }
}
