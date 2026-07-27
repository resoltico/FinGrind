package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireSha256Hex;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireValue;

import java.util.List;

/** Attestation-verification rejection details emitted by the CLI transport layer. */
public interface CliAttestationRejectionJsonModels {
  /**
   * Immutable verified-chain evidence returned when strict clean-attestation verification rejects.
   */
  record AttestationReviewRequiredDetails(
      String bookId,
      CliAttestationJsonModels.AttestationHeadPayload verifiedAttestationHead,
      String previousHead,
      List<CliAttestationJsonModels.AttestationReviewFindingPayload> reviewFindings)
      implements CliRejectionJsonModels.RejectionDetails {
    public AttestationReviewRequiredDetails {
      bookId = requireText(bookId, "bookId");
      verifiedAttestationHead = requireValue(verifiedAttestationHead, "verifiedAttestationHead");
      previousHead = requireSha256Hex(previousHead, "previousHead");
      reviewFindings =
          CliAttestationJsonModels.requireReviewFindingsWithinVerifiedHead(
              reviewFindings, verifiedAttestationHead);
      if (reviewFindings.isEmpty()) {
        throw new IllegalArgumentException(
            "reviewFindings must not be empty for attestation-review-required.");
      }
    }
  }
}
