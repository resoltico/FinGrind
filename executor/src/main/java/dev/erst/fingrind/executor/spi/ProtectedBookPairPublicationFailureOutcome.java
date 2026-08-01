package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRecoveryRecordState;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRetention;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One retained-evidence classification shared by pair-admission and staged-commit boundaries.
 *
 * <p>Both boundaries observe the same immutable pair-recovery facts; duplicating their validation
 * would allow their classifications to diverge.
 */
public sealed interface ProtectedBookPairPublicationFailureOutcome
    permits ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired,
        ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked,
        ProtectedBookPairPublicationFailureOutcome.CompletionUncertain {

  /** A durable exact pair record proves that neither final-member primitive was invoked. */
  record PrepublicationRecoveryRequired(
      Path bookArtifactPath,
      Path secretArtifactPath,
      ProtectedBookPairPublicationRecoveryRecordState recoveryRecordState,
      ProtectedBookPairPublicationRetention pairPublicationRetention)
      implements ProtectedBookPairPublicationFailureOutcome,
          ProtectedBookPairPublicationAdmission,
          StagedPairPublicationCommitOutcome {
    public PrepublicationRecoveryRequired {
      bookArtifactPath = normalized(bookArtifactPath, "bookArtifactPath");
      secretArtifactPath = normalized(secretArtifactPath, "secretArtifactPath");
      Objects.requireNonNull(recoveryRecordState, "recoveryRecordState");
      ProtectedBookPairPublicationRetention checkedPairPublicationRetention =
          Objects.requireNonNull(pairPublicationRetention, "pairPublicationRetention");
      if (bookArtifactPath.equals(secretArtifactPath)) {
        throw new IllegalArgumentException("Protected-book pair members must be distinct.");
      }
      checkedPairPublicationRetention.requireBookPublication(bookArtifactPath);
      checkedPairPublicationRetention.requireGeneratedSecretPublication(secretArtifactPath);
    }
  }

  /** Retained evidence blocks safe recovery because both final-member facts are unestablished. */
  record EvidenceBlocked(
      Path bookArtifactPath,
      ProtectedBookPairPublicationMemberState bookArtifactState,
      Path secretArtifactPath,
      ProtectedBookPairPublicationMemberState secretArtifactState,
      @Nullable ProtectedBookPairPublicationRetention pairPublicationRetention)
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
      if (pairPublicationRetention != null) {
        throw new IllegalArgumentException(
            "Evidence-blocked pair publication cannot claim authoritative retained-stage evidence.");
      }
    }
  }

  /** A pair record or final member remains preserved because durable completion is unproved. */
  record CompletionUncertain(
      Path bookArtifactPath,
      ProtectedBookPairPublicationMemberState bookArtifactState,
      Path secretArtifactPath,
      ProtectedBookPairPublicationMemberState secretArtifactState,
      @Nullable ProtectedBookPairPublicationRetention pairPublicationRetention)
      implements ProtectedBookPairPublicationFailureOutcome,
          ProtectedBookPairPublicationAdmission,
          StagedPairPublicationCommitOutcome {
    public CompletionUncertain {
      bookArtifactPath = normalized(bookArtifactPath, "bookArtifactPath");
      secretArtifactPath = normalized(secretArtifactPath, "secretArtifactPath");
      Objects.requireNonNull(bookArtifactState, "bookArtifactState");
      Objects.requireNonNull(secretArtifactState, "secretArtifactState");
      if (bookArtifactState == ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED
          && secretArtifactState == ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED) {
        throw new IllegalArgumentException(
            "Pair-publication uncertainty requires at least one final-member publication fact.");
      }
      if (bookArtifactState == ProtectedBookPairPublicationMemberState.UNESTABLISHED
          || secretArtifactState == ProtectedBookPairPublicationMemberState.UNESTABLISHED) {
        throw new IllegalArgumentException(
            "Completion uncertainty cannot claim an unestablished member fact.");
      }
      requireRetention(pairPublicationRetention, bookArtifactPath, secretArtifactPath);
    }
  }

  private static Path normalized(Path path, String name) {
    return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
  }

  private static void requireRetention(
      @Nullable ProtectedBookPairPublicationRetention pairPublicationRetention,
      Path bookArtifactPath,
      Path secretArtifactPath) {
    if (pairPublicationRetention == null) {
      return;
    }
    pairPublicationRetention.requireBookPublication(bookArtifactPath);
    pairPublicationRetention.requireGeneratedSecretPublication(secretArtifactPath);
  }
}
