package dev.erst.fingrind.cli.json;

import org.jspecify.annotations.Nullable;

/** Artifact-publication and protected-book maintenance error details emitted by the CLI. */
public interface CliMaintenanceErrorJsonModels {
  /** Closed detail family that carries publication or protected-book maintenance recovery facts. */
  sealed interface MaintenanceErrorDetails extends CliErrorJsonModels.ErrorDetails
      permits PublicationTransactionIncompleteDetails,
          ArtifactPublicationOutcomeUncertainDetails,
          ArtifactPublicationDurabilityUncertainDetails,
          ProtectedBookPairPublicationEvidenceBlockedDetails {}

  /** A final artifact whose publication requires ID-only transaction recovery or inspection. */
  record PublicationTransactionIncompleteDetails(
      String candidateArtifact, CliEnvelopeJsonModels.PublicationTransaction publicationTransaction)
      implements MaintenanceErrorDetails {
    public PublicationTransactionIncompleteDetails {
      candidateArtifact =
          CliJsonModelValidation.requireText(candidateArtifact, "candidateArtifact");
      java.util.Objects.requireNonNull(publicationTransaction, "publicationTransaction");
    }
  }

  /** An indeterminate no-replace-link candidate and any stage retained before that attempt. */
  record ArtifactPublicationOutcomeUncertainDetails(
      String candidateArtifact, @Nullable String retainedStage) implements MaintenanceErrorDetails {
    public ArtifactPublicationOutcomeUncertainDetails {
      candidateArtifact =
          CliJsonModelValidation.requireText(candidateArtifact, "candidateArtifact");
      retainedStage = CliJsonModelValidation.requireOptionalText(retainedStage, "retainedStage");
      if (candidateArtifact.equals(retainedStage)) {
        throw new IllegalArgumentException(
            "candidateArtifact and retainedStage must identify distinct artifacts.");
      }
    }
  }

  /** A published artifact whose final-link durability requires inspection. */
  record ArtifactPublicationDurabilityUncertainDetails(PublishedArtifact publishedArtifact)
      implements MaintenanceErrorDetails {
    public ArtifactPublicationDurabilityUncertainDetails {
      java.util.Objects.requireNonNull(publishedArtifact, "publishedArtifact");
    }
  }

  /** Pair evidence that blocks a safe final-member publication classification. */
  record ProtectedBookPairPublicationEvidenceBlockedDetails(
      EvidenceBlockedPairPublication pairPublication) implements MaintenanceErrorDetails {
    public ProtectedBookPairPublicationEvidenceBlockedDetails {
      java.util.Objects.requireNonNull(pairPublication, "pairPublication");
    }
  }

  /**
   * Final pair targets whose legacy evidence is blocked without exposing private recovery state.
   */
  record EvidenceBlockedPairPublication(
      PairPublicationMember bookTarget, PairPublicationMember generatedSecretTarget) {
    public EvidenceBlockedPairPublication {
      java.util.Objects.requireNonNull(bookTarget, "bookTarget");
      java.util.Objects.requireNonNull(generatedSecretTarget, "generatedSecretTarget");
      if (bookTarget.path().equals(generatedSecretTarget.path())) {
        throw new IllegalArgumentException(
            "Evidence-blocked pair-publication details require distinct final member paths.");
      }
      if (bookTarget.state() != PairPublicationMemberStatePayload.UNESTABLISHED
          || generatedSecretTarget.state() != PairPublicationMemberStatePayload.UNESTABLISHED) {
        throw new IllegalArgumentException(
            "Evidence-blocked pair-publication details require both members to be unestablished.");
      }
    }
  }

  /** One canonical final member and its strongest established pair-publication fact. */
  record PairPublicationMember(String path, PairPublicationMemberStatePayload state) {
    public PairPublicationMember {
      path = CliJsonModelValidation.requireText(path, "path");
      java.util.Objects.requireNonNull(state, "state");
    }
  }

  /** Closed JSON wire vocabulary for one final protected-book pair-member publication state. */
  enum PairPublicationMemberStatePayload implements dev.erst.fingrind.core.WireValue {
    UNESTABLISHED("unestablished"),
    NOT_ATTEMPTED("not-attempted"),
    OUTCOME_UNCERTAIN("outcome-uncertain"),
    PUBLISHED_DURABILITY_UNCONFIRMED("published-durability-unconfirmed"),
    PUBLISHED_DURABLE("published-durable");

    private final String wireValue;

    PairPublicationMemberStatePayload(String wireValue) {
      this.wireValue = wireValue;
    }

    @Override
    @com.fasterxml.jackson.annotation.JsonValue
    public String wireValue() {
      return wireValue;
    }

    /** Maps the public contract state without exposing Java enum identifiers on the wire. */
    public static PairPublicationMemberStatePayload from(
        dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState state) {
      return switch (java.util.Objects.requireNonNull(state, "state")) {
        case UNESTABLISHED -> UNESTABLISHED;
        case NOT_ATTEMPTED -> NOT_ATTEMPTED;
        case OUTCOME_UNCERTAIN -> OUTCOME_UNCERTAIN;
        case PUBLISHED_DURABILITY_UNCONFIRMED -> PUBLISHED_DURABILITY_UNCONFIRMED;
        case PUBLISHED_DURABLE -> PUBLISHED_DURABLE;
      };
    }
  }

  /** One final artifact path and its mandatory retained private stage. */
  record PublishedArtifact(String path, String retainedStage) {
    public PublishedArtifact {
      path = CliJsonModelValidation.requireText(path, "path");
      retainedStage = CliJsonModelValidation.requireText(retainedStage, "retainedStage");
      if (path.equals(retainedStage)) {
        throw new IllegalArgumentException(
            "path and retainedStage must identify distinct artifacts.");
      }
    }
  }
}
