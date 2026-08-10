package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.compareCanonicalUnsigned64Decimals;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireCanonicalUnsigned64Decimal;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalCanonicalUnsigned64Decimal;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireSha256Hex;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Core error-detail JSON records emitted by the CLI transport layer. */
public interface CliErrorJsonModels {
  /** Sealed marker for machine-readable CLI failure detail payloads. */
  sealed interface ErrorDetails extends CliEnvelopeJsonModels.EnvelopeDetails
      permits InvalidJsonDetails,
          InvalidRequestDetails,
          StaleHeadDetails,
          CliMaintenanceErrorJsonModels.PublicationTransactionIncompleteDetails,
          CliMaintenanceErrorJsonModels.ArtifactPublicationOutcomeUncertainDetails,
          CliMaintenanceErrorJsonModels.ArtifactPublicationDurabilityUncertainDetails,
          CliMaintenanceErrorJsonModels.ProtectedBookPairPublicationUncertainDetails,
          CliMaintenanceErrorJsonModels.ProtectedBookPairPublicationEvidenceBlockedDetails,
          CliOpenBookErrorJsonModels.OpenBookPreparationArtifactsRetainedDetails,
          CliOpenBookErrorJsonModels.OpenBookCompletionUncertainDetails,
          AttestationReviewWindowDetails,
          UnsupportedBookFormatVersionDetails {}

  record InvalidJsonDetails(String parseMessage, int line, int column) implements ErrorDetails {
    public InvalidJsonDetails {
      parseMessage = CliJsonModelValidation.requireText(parseMessage, "parseMessage");
      if (line <= 0) {
        throw new IllegalArgumentException("line must be positive.");
      }
      if (column <= 0) {
        throw new IllegalArgumentException("column must be positive.");
      }
    }
  }

  record InvalidRequestDetails(List<String> violations) implements ErrorDetails {
    public InvalidRequestDetails {
      violations = copyList(violations, "violations");
      if (violations.isEmpty()) {
        throw new IllegalArgumentException("violations must not be empty.");
      }
    }
  }

  record StaleHeadDetails(String observedHead, String currentHead, String currentOrder)
      implements ErrorDetails {
    public StaleHeadDetails {
      observedHead = requireSha256Hex(observedHead, "observedHead");
      currentHead = requireSha256Hex(currentHead, "currentHead");
      currentOrder = requireCanonicalUnsigned64Decimal(currentOrder, "currentOrder");
    }
  }

  /** Exact review declaration and authenticated head that make its interval inadmissible. */
  record AttestationReviewWindowDetails(
      String credentialKeyId,
      String firstAffectedOrder,
      @JsonInclude(JsonInclude.Include.ALWAYS) @Nullable String lastAffectedOrder,
      String verifiedHeadOrder)
      implements ErrorDetails {
    public AttestationReviewWindowDetails {
      credentialKeyId = requireSha256Hex(credentialKeyId, "credentialKeyId");
      firstAffectedOrder =
          requireCanonicalUnsigned64Decimal(firstAffectedOrder, "firstAffectedOrder");
      lastAffectedOrder =
          requireOptionalCanonicalUnsigned64Decimal(lastAffectedOrder, "lastAffectedOrder");
      verifiedHeadOrder = requireCanonicalUnsigned64Decimal(verifiedHeadOrder, "verifiedHeadOrder");
      if (lastAffectedOrder != null
          && compareCanonicalUnsigned64Decimals(lastAffectedOrder, firstAffectedOrder) < 0) {
        throw new IllegalArgumentException(
            "lastAffectedOrder must not precede firstAffectedOrder.");
      }
      if (compareCanonicalUnsigned64Decimals(firstAffectedOrder, verifiedHeadOrder) <= 0
          && (lastAffectedOrder == null
              || compareCanonicalUnsigned64Decimals(lastAffectedOrder, verifiedHeadOrder) <= 0)) {
        throw new IllegalArgumentException(
            "A review-window error must identify an interval outside the verified head.");
      }
    }
  }

  /** Exact physical-book format facts for a deterministic non-current-format refusal. */
  record UnsupportedBookFormatVersionDetails(
      int detectedBookFormatVersion, int supportedBookFormatVersion) implements ErrorDetails {
    public UnsupportedBookFormatVersionDetails {
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
