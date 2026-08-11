package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import java.nio.file.Path;
import java.util.Objects;

/**
 * One retained-evidence classification shared by pair-admission and staged-commit boundaries.
 *
 * <p>Both boundaries observe the same immutable pair-recovery facts; duplicating their validation
 * would allow their classifications to diverge.
 */
public sealed interface ProtectedBookPairPublicationFailureOutcome
    permits ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked {

  /** Retained evidence blocks safe recovery because both final-member facts are unestablished. */
  record EvidenceBlocked(
      Path bookArtifactPath,
      ProtectedBookPairPublicationMemberState bookArtifactState,
      Path secretArtifactPath,
      ProtectedBookPairPublicationMemberState secretArtifactState)
      implements ProtectedBookPairPublicationFailureOutcome,
          ProtectedBookPairPublicationAdmission,
          StagedPairPublicationCommitOutcome {
    public EvidenceBlocked {
      bookArtifactPath = normalized(bookArtifactPath, "bookArtifactPath");
      secretArtifactPath = normalized(secretArtifactPath, "secretArtifactPath");
      Objects.requireNonNull(bookArtifactState, "bookArtifactState");
      Objects.requireNonNull(secretArtifactState, "secretArtifactState");
      if (bookArtifactState != ProtectedBookPairPublicationMemberState.UNESTABLISHED
          || secretArtifactState != ProtectedBookPairPublicationMemberState.UNESTABLISHED) {
        throw new IllegalArgumentException(
            "Evidence-blocked pair publication requires unestablished facts for both members.");
      }
    }
  }

  private static Path normalized(Path path, String name) {
    return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
  }
}
