package dev.erst.fingrind.contract.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.ProtocolEnvelopeStatus;
import dev.erst.fingrind.contract.workflow.LedgerPlanFailure;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

/** Locks every published failure code to one explicit category at its contract owner. */
class ContractResponseCatalogTest {
  @Test
  void everyPublishedFailureDescriptorHasOneCatalogCategory() {
    Map<String, ContractResponse.FailureCategory> expected = new ConcurrentHashMap<>();
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
        ContractResponse.FailureCategory.STRUCTURAL_INVALID,
        ContractResponseCatalog.failureCategoryFor("invalid-request"));
    assertEquals(
        ContractResponse.FailureCategory.PRECONDITION,
        ContractResponseCatalog.failureCategoryFor("query-book-not-initialized"));
    assertEquals(
        ContractResponse.FailureCategory.INTERNAL,
        ContractResponseCatalog.failureCategoryFor("internal-error"));
    assertEquals(
        ContractResponse.FailureCategory.INTERNAL,
        ContractResponseCatalog.failureCategoryFor("internal-defect"));
    assertEquals(
        ContractResponse.FailureCategory.DOMAIN_SEMANTIC,
        ContractResponseCatalog.failureCategoryFor("assertion-failed"));
    assertEquals(
        ContractResponse.FailureCategory.STRUCTURAL_INVALID,
        ContractResponseCatalog.failureCategoryFor("attestation-signature-invalid"));
    assertEquals(
        ContractResponse.FailureCategory.STRUCTURAL_INVALID,
        ContractResponseCatalog.failureCategoryFor("receipt-artifact-invalid"));
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
              .map(ContractResponse.ErrorDescriptor::code)
              .anyMatch(failure.code()::equals));
      assertEquals(
          failure.envelopeStatus() == ProtocolEnvelopeStatus.REJECTED,
          LedgerPlanFailure.rejectionDescriptors().stream()
              .map(ContractResponse.RejectionDescriptor::code)
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
    Map<String, ContractResponse.FailureCategory> categories = new ConcurrentHashMap<>();
    ContractResponseCatalog.register(
        categories, "known-code", ContractResponse.FailureCategory.PRECONDITION);
    ContractResponseCatalog.register(
        categories, "known-code", ContractResponse.FailureCategory.PRECONDITION);

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                ContractResponseCatalog.register(
                    categories, "known-code", ContractResponse.FailureCategory.INTERNAL));

    assertEquals(
        "Conflicting published failure categories for code: known-code", failure.getMessage());
  }

  private static void collect(
      Map<String, ContractResponse.FailureCategory> categories,
      ContractResponse.RejectionDescriptor descriptor) {
    ContractResponse.FailureCategory prior =
        categories.put(descriptor.code(), descriptor.category());
    if (prior != null && prior != descriptor.category()) {
      throw new AssertionError("Conflicting published failure categories: " + descriptor.code());
    }
    descriptor.detailRejections().forEach(detail -> collect(categories, detail));
  }
}
