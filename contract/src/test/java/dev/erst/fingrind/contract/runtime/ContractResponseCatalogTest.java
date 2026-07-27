package dev.erst.fingrind.contract.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.ProtocolEnvelopeStatus;
import dev.erst.fingrind.contract.workflow.LedgerPlanFailure;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

/** Locks every published failure code to one explicit category at its contract owner. */
class ContractResponseCatalogTest {
  @Test
  void everyPublishedFailureDescriptorHasOneCatalogCategory() {
    Map<String, FailureCategory> expected = new ConcurrentHashMap<>();
    ContractResponseCatalog.errorDescriptors()
        .forEach(descriptor -> expected.put(descriptor.code(), descriptor.category()));
    ContractResponseCatalog.rejectionDescriptors()
        .forEach(descriptor -> collect(expected, descriptor));

    assertEquals(expected, ContractResponseCatalog.categoriesByCode());
    expected.forEach(
        (code, category) ->
            assertEquals(category, ContractResponseCatalog.failureCategoryFor(code)));
  }

  @Test
  void categoriesKeepStructuralPreconditionsAndInternalFailuresDistinct() {
    assertEquals(
        FailureCategory.STRUCTURAL_INVALID,
        ContractResponseCatalog.failureCategoryFor("invalid-request"));
    assertEquals(
        FailureCategory.PRECONDITION,
        ContractResponseCatalog.failureCategoryFor("query-book-not-initialized"));
    assertEquals(
        FailureCategory.INTERNAL, ContractResponseCatalog.failureCategoryFor("internal-error"));
    assertEquals(
        FailureCategory.INTERNAL, ContractResponseCatalog.failureCategoryFor("internal-defect"));
    assertEquals(
        FailureCategory.DOMAIN_SEMANTIC,
        ContractResponseCatalog.failureCategoryFor("assertion-failed"));
    assertEquals(
        FailureCategory.DOMAIN_SEMANTIC,
        ContractResponseCatalog.failureCategoryFor("read-only-plan-mutation-forbidden"));
    assertEquals(
        FailureCategory.STRUCTURAL_INVALID,
        ContractResponseCatalog.failureCategoryFor("attestation-signature-invalid"));
    assertEquals(
        FailureCategory.STRUCTURAL_INVALID,
        ContractResponseCatalog.failureCategoryFor("receipt-artifact-invalid"));
  }

  @Test
  void invalidAttestationCredentialDescriptor_coversMissingAndInvalidSelections() {
    assertEquals(
        "Attested-book authorization refused because a required attestation credential selection is missing or invalid.",
        ContractErrors.Descriptor.INVALID_ATTESTATION_CREDENTIAL.description());
  }

  @Test
  void attestationReviewWindowDescriptorPublishesItsExactTypedDetailContract() {
    ErrorDescriptor descriptor =
        ContractResponseCatalog.errorDescriptorFor(
            ContractErrors.Descriptor.ATTESTATION_REVIEW_WINDOW_EXCEEDS_HEAD.code());

    assertEquals(FailureCategory.DOMAIN_SEMANTIC, descriptor.category());
    assertEquals(1, descriptor.exitCode());
    assertEquals(
        List.of("credentialKeyId", "firstAffectedOrder", "lastAffectedOrder", "verifiedHeadOrder"),
        descriptor.detailFields().stream().map(FieldDescriptor::name).toList());
    assertEquals(
        "Always-present nullable canonical unsigned-64 final order; null means through the verified head.",
        descriptor.detailFields().get(2).description());
  }

  @Test
  void unsupportedBookFormatDescriptorPublishesItsExactTypedDetailContract() {
    ErrorDescriptor descriptor =
        ContractResponseCatalog.errorDescriptorFor(
            ContractErrors.Descriptor.UNSUPPORTED_BOOK_FORMAT_VERSION.code());

    assertEquals(FailureCategory.PRECONDITION, descriptor.category());
    assertEquals(6, descriptor.exitCode());
    assertEquals(
        List.of("detectedBookFormatVersion", "supportedBookFormatVersion"),
        descriptor.detailFields().stream().map(FieldDescriptor::name).toList());
  }

  @Test
  void unknownFailureCodeIsRejectedInsteadOfReceivingADefaultCategory() {
    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () -> ContractResponseCatalog.failureCategoryFor("unpublished-failure"));

    assertEquals(
        "No published failure category exists for code: unpublished-failure", failure.getMessage());
  }

  @Test
  void errorDescriptorsAreRetrievedOnlyForPublishedErrorCodes() {
    assertEquals(
        "invalid-request", ContractResponseCatalog.errorDescriptorFor("invalid-request").code());

    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () -> ContractResponseCatalog.errorDescriptorFor("unknown-rejection-code"));

    assertEquals(
        "No published error descriptor exists for code: unknown-rejection-code",
        failure.getMessage());
  }

  @Test
  void planFailuresPublishTheirDeclaredStatusCategoryExitCodeAndDescription() {
    for (LedgerPlanFailure failure : LedgerPlanFailure.values()) {
      assertEquals(failure.category(), ContractResponseCatalog.failureCategoryFor(failure.code()));
      assertEquals(
          failure.envelopeStatus() == ProtocolEnvelopeStatus.ERROR,
          LedgerPlanFailure.errorDescriptors().stream()
              .map(ErrorDescriptor::code)
              .anyMatch(failure.code()::equals));
      assertEquals(
          failure.envelopeStatus() == ProtocolEnvelopeStatus.REJECTED,
          LedgerPlanFailure.rejectionDescriptors().stream()
              .map(RejectionDescriptor::code)
              .anyMatch(failure.code()::equals));
      assertEquals(
          failure == LedgerPlanFailure.ASSERTION_FAILED ? 3 : 2,
          failure.exitCode(),
          "Plan outcome exit behavior is a contract decision.");
      assertFalse(failure.description().isBlank());
    }

    assertEquals(ProtocolEnvelopeStatus.ERROR, LedgerPlanFailure.ASSERTION_FAILED.envelopeStatus());
    assertEquals(3, LedgerPlanFailure.ASSERTION_FAILED.exitCode());
  }

  @Test
  void duplicateCodesMayOnlyBeRegisteredWithTheSameCategory() {
    Map<String, FailureCategory> categories = new ConcurrentHashMap<>();
    ContractResponseCatalog.register(categories, "known-code", FailureCategory.PRECONDITION);
    ContractResponseCatalog.register(categories, "known-code", FailureCategory.PRECONDITION);

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                ContractResponseCatalog.register(
                    categories, "known-code", FailureCategory.INTERNAL));

    assertEquals(
        "Conflicting published failure categories for code: known-code", failure.getMessage());
  }

  private static void collect(
      Map<String, FailureCategory> categories, RejectionDescriptor descriptor) {
    FailureCategory prior = categories.put(descriptor.code(), descriptor.category());
    if (prior != null && prior != descriptor.category()) {
      throw new AssertionError("Conflicting published failure categories: " + descriptor.code());
    }
    descriptor.detailRejections().forEach(detail -> collect(categories, detail));
  }
}
