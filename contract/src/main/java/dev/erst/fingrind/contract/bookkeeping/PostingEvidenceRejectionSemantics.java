package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.EconomicEventClass;
import dev.erst.fingrind.core.EvidenceClass;
import dev.erst.fingrind.core.SourceDocumentType;
import java.util.List;
import java.util.Objects;

/** Canonical owner for evidence-driven posting entry-semantics rejection details. */
final class PostingEvidenceRejectionSemantics {
  private PostingEvidenceRejectionSemantics() {}

  /** Returns one entry-semantics violation using the canonical entryKind selector field. */
  static PostingRejection.EntrySemanticsViolation sourceDocumentTypeNotAccepted(
      String selectorValue, SourceDocumentType sourceDocumentType, List<String> acceptedTypes) {
    return sourceDocumentTypeNotAccepted(
        "entryKind", selectorValue, sourceDocumentType, acceptedTypes);
  }

  /**
   * Returns one entry-semantics violation for evidence whose source-document type is not admitted.
   */
  static PostingRejection.EntrySemanticsViolation sourceDocumentTypeNotAccepted(
      String selectorField,
      String selectorValue,
      SourceDocumentType sourceDocumentType,
      List<String> acceptedTypes) {
    String requiredSelectorField =
        PostingRejectionSemanticsSupport.requireSelectorField(selectorField);
    String requiredSelectorValue =
        PostingRejectionSemanticsSupport.requireSelectorValue(selectorValue);
    Objects.requireNonNull(sourceDocumentType, "sourceDocumentType");
    List<String> acceptedTypeValues =
        List.copyOf(Objects.requireNonNull(acceptedTypes, "acceptedTypes"));
    return new PostingRejection.EntrySemanticsViolation(
        "source-document-type-not-accepted",
        "evidence.sourceDocuments[].sourceDocumentType",
        "%s '%s' does not accept evidence.sourceDocuments[].sourceDocumentType '%s'. Accepted values: %s."
            .formatted(
                requiredSelectorField,
                requiredSelectorValue,
                sourceDocumentType.value(),
                String.join(", ", acceptedTypeValues)));
  }

  /** Returns one entry-semantics violation using the canonical selector field. */
  static PostingRejection.EntrySemanticsViolation evidenceClassConflict(
      String selectorValue, EvidenceClass evidenceClass, EconomicEventClass eventClass) {
    return evidenceClassConflict("entryKind", selectorValue, evidenceClass, eventClass);
  }

  /** Returns one evidence-class conflict between retained evidence and the resolved event class. */
  static PostingRejection.EntrySemanticsViolation evidenceClassConflict(
      String selectorField,
      String selectorValue,
      EvidenceClass evidenceClass,
      EconomicEventClass eventClass) {
    String requiredSelectorField =
        PostingRejectionSemanticsSupport.requireSelectorField(selectorField);
    String requiredSelectorValue =
        PostingRejectionSemanticsSupport.requireSelectorValue(selectorValue);
    Objects.requireNonNull(evidenceClass, "evidenceClass");
    Objects.requireNonNull(eventClass, "eventClass");
    return new PostingRejection.EntrySemanticsViolation(
        "evidence-class-conflict",
        "evidence.sourceDocuments[].sourceDocumentType",
        "%s '%s' resolves to eventClass '%s', but the evidence resolves to evidenceClass '%s'."
            .formatted(
                requiredSelectorField,
                requiredSelectorValue,
                eventClass.wireValue(),
                evidenceClass.wireValue()));
  }
}
