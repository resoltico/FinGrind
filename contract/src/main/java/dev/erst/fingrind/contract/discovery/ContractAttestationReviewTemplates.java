package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.List;

/** Executable scaffold for the non-persisted compromise-review declaration file. */
public final class ContractAttestationReviewTemplates {
  public static final String EXAMPLE_CREDENTIAL_KEY_ID =
      "8f0e9c3c96c8188db78dc9de35290a86f8d3a5c0b9e9d1d2a0e3fd48c6b7a901";

  private ContractAttestationReviewTemplates() {}

  static AttestationReviewFileTemplateDescriptor reviewFileTemplate() {
    return new AttestationReviewFileTemplateDescriptor(
        List.of(new CompromiseReviewTemplateDescriptor(EXAMPLE_CREDENTIAL_KEY_ID, "41", "57")));
  }

  /** One complete review-file document accepted by verify-book and attestation-review. */
  public record AttestationReviewFileTemplateDescriptor(
      List<CompromiseReviewTemplateDescriptor> compromiseReviews)
      implements TemplateDescriptorType {
    public AttestationReviewFileTemplateDescriptor {
      compromiseReviews =
          ContractDescriptorValidation.copyList(compromiseReviews, "compromiseReviews");
      if (compromiseReviews.isEmpty()) {
        throw new IllegalArgumentException("compromiseReviews must not be empty.");
      }
    }
  }

  /** One bounded inclusive credential-compromise interval with wire-safe string order values. */
  public record CompromiseReviewTemplateDescriptor(
      String credentialKeyId, String firstAffectedOrder, String lastAffectedOrder)
      implements TemplateDescriptorType {
    public CompromiseReviewTemplateDescriptor {
      credentialKeyId =
          ContractDescriptorValidation.requireText(credentialKeyId, "credentialKeyId");
      firstAffectedOrder =
          ContractDescriptorValidation.requireText(firstAffectedOrder, "firstAffectedOrder");
      lastAffectedOrder =
          ContractDescriptorValidation.requireText(lastAffectedOrder, "lastAffectedOrder");
    }
  }
}
