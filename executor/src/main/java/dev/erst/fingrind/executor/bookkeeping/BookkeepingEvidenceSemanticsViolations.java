package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.PostingRejectionSemantics;
import dev.erst.fingrind.core.EconomicEventClass;
import dev.erst.fingrind.core.EvidenceClass;
import dev.erst.fingrind.core.SourceDocumentType;
import java.util.List;

/** Executor-local evidence-semantics violations derived from canonical contract rejections. */
public final class BookkeepingEvidenceSemanticsViolations {
  private BookkeepingEvidenceSemanticsViolations() {}

  /** Creates one source-document-type violation for one explicit selector pair. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation sourceDocumentTypeNotAccepted(
      String selectorField,
      String selectorValue,
      SourceDocumentType sourceDocumentType,
      List<String> acceptedTypes) {
    BookkeepingEntrySemanticsViolationSupport.requireCanonicalSelectorField(selectorField);
    return BookkeepingEntrySemanticsViolationSupport.toLocal(
        PostingRejectionSemantics.sourceDocumentTypeNotAccepted(
            BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue),
            sourceDocumentType,
            acceptedTypes));
  }

  /** Creates one evidence-class conflict violation against one resolved journal class. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation evidenceClassConflict(
      String selectorField,
      String selectorValue,
      EvidenceClass evidenceClass,
      EconomicEventClass eventClass) {
    BookkeepingEntrySemanticsViolationSupport.requireCanonicalSelectorField(selectorField);
    return BookkeepingEntrySemanticsViolationSupport.toLocal(
        PostingRejectionSemantics.evidenceClassConflict(
            BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue),
            evidenceClass,
            eventClass));
  }
}
