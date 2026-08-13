package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.PublicationTransactionResult;
import java.nio.file.Path;
import java.util.Objects;

/** Reports a founder-key publication that can be inspected or recovered only by transaction ID. */
public final class AttestationFounderKeyPublicationTransactionException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final transient Path candidateArtifactPath;
  private final String serializedCandidateArtifactPath;
  private final PublicationTransactionResult transactionResult;

  /**
   * Captures the candidate final path and non-success transaction result without exposing a stage.
   */
  public AttestationFounderKeyPublicationTransactionException(
      Path candidateArtifactPath, PublicationTransactionResult transactionResult, Throwable cause) {
    super(
        "Founder-key publication transaction did not complete.",
        Objects.requireNonNull(cause, "cause"));
    this.candidateArtifactPath = canonicalPath(candidateArtifactPath);
    this.serializedCandidateArtifactPath = this.candidateArtifactPath.toString();
    this.transactionResult = Objects.requireNonNull(transactionResult, "transactionResult");
    if (transactionResult.successful()) {
      throw new IllegalArgumentException(
          "A founder-key publication failure cannot carry a successful transaction result.");
    }
  }

  /** Returns the candidate final artifact selected for the failed publication. */
  public Path candidateArtifactPath() {
    return candidateArtifactPath == null
        ? Path.of(serializedCandidateArtifactPath)
        : candidateArtifactPath;
  }

  /** Returns the ID-only transaction recovery handle and durable outcome. */
  public PublicationTransactionResult transactionResult() {
    return transactionResult;
  }

  private static Path canonicalPath(Path candidateArtifactPath) {
    return Objects.requireNonNull(candidateArtifactPath, "candidateArtifactPath")
        .toAbsolutePath()
        .normalize();
  }
}
