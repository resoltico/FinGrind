package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.core.PublicationTransactionResult;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Structured facts owned by one deterministic contract failure. */
public sealed interface ContractFailureDetails
    permits ContractFailureDetails.ArtifactPublicationOutcomeUncertain,
        ContractFailureDetails.ArtifactPublicationDurabilityUncertain,
        ContractFailureDetails.PublicationTransactionIncomplete,
        ContractFailureDetails.ProtectedBookPairPublicationEvidenceBlocked,
        OpenBookFailureDetails.OpenBookPreparationArtifactsRetained,
        OpenBookFailureDetails.OpenBookPublicationProgress,
        OpenBookFailureDetails.OpenBookCompletionUncertain,
        ContractFailureDetails.UnsupportedBookFormatVersion {
  /** A no-replace-link attempt did not establish whether its candidate final name exists. */
  record ArtifactPublicationOutcomeUncertain(
      Path candidateArtifactPath, @Nullable ArtifactPublicationRetention retainedStage)
      implements ContractFailureDetails {
    /** Retains the indeterminate candidate and any stage created before the failed link. */
    public ArtifactPublicationOutcomeUncertain {
      candidateArtifactPath =
          Objects.requireNonNull(candidateArtifactPath, "candidateArtifactPath")
              .toAbsolutePath()
              .normalize();
      if (retainedStage != null
          && candidateArtifactPath.equals(retainedStage.retainedStagePath())) {
        throw new IllegalArgumentException(
            "An indeterminate artifact candidate and its retained stage must be distinct.");
      }
    }
  }

  /**
   * A final no-clobber artifact name was linked, but its parent-directory durability could not be
   * confirmed.
   *
   * <p>The stage used to create the final link is retained immutable evidence; only the final-link
   * durability fact is uncertain.
   */
  record ArtifactPublicationDurabilityUncertain(ArtifactPublicationResult publication)
      implements ContractFailureDetails {
    /** Retains the published artifact and its immutable retained-stage fact. */
    public ArtifactPublicationDurabilityUncertain {
      Objects.requireNonNull(publication, "publication");
    }
  }

  /** One failed publication whose canonical transaction identifier is the sole recovery handle. */
  record PublicationTransactionIncomplete(
      Path candidateArtifactPath, PublicationTransactionResult transactionResult)
      implements ContractFailureDetails {
    /** Normalizes the final candidate and rejects any result that falsely claims completion. */
    public PublicationTransactionIncomplete {
      candidateArtifactPath =
          Objects.requireNonNull(candidateArtifactPath, "candidateArtifactPath")
              .toAbsolutePath()
              .normalize();
      Objects.requireNonNull(transactionResult, "transactionResult");
      if (transactionResult.successful()) {
        throw new IllegalArgumentException(
            "An incomplete publication failure cannot carry a successful transaction result.");
      }
    }
  }

  /**
   * Legacy pair evidence prevents a safe publication decision but cannot establish a recoverable
   * final-member outcome.
   */
  record ProtectedBookPairPublicationEvidenceBlocked(PairPublication pairPublication)
      implements ContractFailureDetails {
    public ProtectedBookPairPublicationEvidenceBlocked {
      Objects.requireNonNull(pairPublication, "pairPublication");
      if (!pairPublication.hasOnlyUnestablishedMembers()) {
        throw new IllegalArgumentException(
            "Evidence-blocked pair publication requires unestablished facts for both members.");
      }
    }
  }

  /**
   * Both canonical final members and their independently established public states.
   *
   * <p>Private stage names and recovery material are deliberately absent. Current transactions are
   * recovered only by their journal identifier; old sidecars are fail-closed evidence.
   */
  record PairPublication(
      PairPublicationMember bookTarget, PairPublicationMember generatedSecretTarget) {
    /** Rejects ambiguous pairs that would let one path stand in for both owned members. */
    public PairPublication {
      Objects.requireNonNull(bookTarget, "bookTarget");
      Objects.requireNonNull(generatedSecretTarget, "generatedSecretTarget");
      if (bookTarget.path().equals(generatedSecretTarget.path())) {
        throw new IllegalArgumentException(
            "Protected-book pair publication members must name distinct final paths.");
      }
    }

    boolean hasOnlyUnestablishedMembers() {
      return bookTarget.state() == ProtectedBookPairPublicationMemberState.UNESTABLISHED
          && generatedSecretTarget.state() == ProtectedBookPairPublicationMemberState.UNESTABLISHED;
    }
  }

  /** One canonical final pair member and the strongest publication fact FinGrind established. */
  record PairPublicationMember(Path path, ProtectedBookPairPublicationMemberState state) {
    /** Normalizes the exposed final target without claiming that it remains materialized. */
    public PairPublicationMember {
      path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
      Objects.requireNonNull(state, "state");
    }
  }

  /** Exact versions that make an authenticated FinGrind book non-current for this binary. */
  record UnsupportedBookFormatVersion(int detectedBookFormatVersion, int supportedBookFormatVersion)
      implements ContractFailureDetails {
    /** Validates one non-current protected-book format boundary. */
    public UnsupportedBookFormatVersion {
      if (detectedBookFormatVersion < 0) {
        throw new IllegalArgumentException("detectedBookFormatVersion must be non-negative.");
      }
      if (supportedBookFormatVersion < 1) {
        throw new IllegalArgumentException("supportedBookFormatVersion must be positive.");
      }
      if (detectedBookFormatVersion == supportedBookFormatVersion) {
        throw new IllegalArgumentException(
            "Unsupported-book-format details require distinct detected and supported versions.");
      }
    }
  }
}
