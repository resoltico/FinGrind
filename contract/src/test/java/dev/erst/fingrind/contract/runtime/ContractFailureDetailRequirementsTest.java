package dev.erst.fingrind.contract.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Proves every descriptor that represents structured facts refuses an absent detail payload. */
class ContractFailureDetailRequirementsTest {
  @Test
  void descriptorsThatRequireFacts_rejectMissingDetailsWithTheirCanonicalMessage() {
    assertMissingDetails(
        ContractErrors.Descriptor.ARTIFACT_PUBLICATION_OUTCOME_UNCERTAIN,
        "artifact-publication-outcome-uncertain failures require outcome details.");
    assertMissingDetails(
        ContractErrors.Descriptor.ARTIFACT_PUBLICATION_DURABILITY_UNCERTAIN,
        "artifact-publication-durability-uncertain failures require publication details.");
    assertMissingDetails(
        ContractErrors.Descriptor.UNSUPPORTED_BOOK_FORMAT_VERSION,
        "unsupported-book-format-version failures require format-version details.");
    assertMissingDetails(
        ContractErrors.Descriptor.OPEN_BOOK_PREPARATION_ARTIFACTS_RETAINED,
        "open-book-preparation-artifacts-retained failures require retained-artifact details.");
    assertMissingDetails(
        ContractErrors.Descriptor.OPEN_BOOK_COMPLETION_UNCERTAIN,
        "open-book-completion-uncertain failures require completion details.");
  }

  private static void assertMissingDetails(
      ContractErrors.Descriptor descriptor, String requiredMessage) {
    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () -> new ContractFailure(descriptor, "message", null, null, null, null, null));

    assertEquals(requiredMessage, failure.getMessage());
  }
}
