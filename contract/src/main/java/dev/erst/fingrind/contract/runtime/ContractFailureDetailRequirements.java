package dev.erst.fingrind.contract.runtime;

import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Owns the closed mapping between descriptors that require facts and their fact vocabulary. */
final class ContractFailureDetailRequirements {
  private static final Map<ContractErrors.Descriptor, DetailRequirement> REQUIREMENTS =
      Map.of(
          ContractErrors.Descriptor.ARTIFACT_PUBLICATION_OUTCOME_UNCERTAIN,
          new DetailRequirement(
              ContractFailureDetails.ArtifactPublicationOutcomeUncertain.class,
              "artifact-publication-outcome-uncertain failures require outcome details."),
          ContractErrors.Descriptor.ARTIFACT_PUBLICATION_DURABILITY_UNCERTAIN,
          new DetailRequirement(
              ContractFailureDetails.ArtifactPublicationDurabilityUncertain.class,
              "artifact-publication-durability-uncertain failures require publication details."),
          ContractErrors.Descriptor.PUBLICATION_TRANSACTION_INCOMPLETE,
          new DetailRequirement(
              ContractFailureDetails.PublicationTransactionIncomplete.class,
              "publication-transaction-incomplete failures require transaction details."),
          ContractErrors.Descriptor.PROTECTED_BOOK_PAIR_PUBLICATION_UNCERTAIN,
          new DetailRequirement(
              ContractFailureDetails.ProtectedBookPairPublicationUncertain.class,
              "protected-book-pair-publication-uncertain failures require pair-publication details."),
          ContractErrors.Descriptor.PROTECTED_BOOK_PAIR_PUBLICATION_EVIDENCE_BLOCKED,
          new DetailRequirement(
              ContractFailureDetails.ProtectedBookPairPublicationEvidenceBlocked.class,
              "protected-book-pair-publication-evidence-blocked failures require evidence details."),
          ContractErrors.Descriptor.UNSUPPORTED_BOOK_FORMAT_VERSION,
          new DetailRequirement(
              ContractFailureDetails.UnsupportedBookFormatVersion.class,
              "unsupported-book-format-version failures require format-version details."),
          ContractErrors.Descriptor.OPEN_BOOK_PREPARATION_ARTIFACTS_RETAINED,
          new DetailRequirement(
              OpenBookFailureDetails.OpenBookPreparationArtifactsRetained.class,
              "open-book-preparation-artifacts-retained failures require retained-artifact details."),
          ContractErrors.Descriptor.OPEN_BOOK_PUBLICATION_PROGRESS,
          new DetailRequirement(
              OpenBookFailureDetails.OpenBookPublicationProgress.class,
              "open-book-publication-progress failures require publication-progress details."),
          ContractErrors.Descriptor.OPEN_BOOK_COMPLETION_UNCERTAIN,
          new DetailRequirement(
              OpenBookFailureDetails.OpenBookCompletionUncertain.class,
              "open-book-completion-uncertain failures require completion details."));

  private ContractFailureDetailRequirements() {}

  static @Nullable ContractFailureDetails requireCompatible(
      ContractErrors.Descriptor descriptor, @Nullable ContractFailureDetails details) {
    @Nullable DetailRequirement requirement = REQUIREMENTS.get(descriptor);
    if (requirement == null) {
      requireNoDetails(descriptor, details);
      return null;
    }
    requirement.require(details);
    return details;
  }

  private static void requireNoDetails(
      ContractErrors.Descriptor descriptor, @Nullable ContractFailureDetails details) {
    if (details != null) {
      throw new IllegalArgumentException(
          "Contract failure details are not valid for " + descriptor.code() + ".");
    }
  }

  /** Identifies the sole structured-fact subtype valid for one descriptor. */
  private record DetailRequirement(
      Class<? extends ContractFailureDetails> detailsType, String mismatchMessage) {
    DetailRequirement {
      Objects.requireNonNull(detailsType, "detailsType");
      Objects.requireNonNull(mismatchMessage, "mismatchMessage");
    }

    void require(@Nullable ContractFailureDetails details) {
      if (!detailsType.isInstance(details)) {
        throw new IllegalArgumentException(mismatchMessage);
      }
    }
  }
}
