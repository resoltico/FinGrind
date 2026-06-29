package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.contract.protocol.SourceDocumentTypePolicyMode;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingEntrySemanticsViolationFactory;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Application-boundary semantic validation for the published post-entry command surface. */
final class PostEntrySemanticsPolicy {
  private static final PostEntrySemanticsPolicy CURRENT_KERNEL = new PostEntrySemanticsPolicy();
  private static final RequestSurfaceFacts REQUEST_SURFACE =
      ProtocolCatalog.domain().requestSurface();

  private PostEntrySemanticsPolicy() {}

  static PostEntrySemanticsPolicy currentKernel() {
    return CURRENT_KERNEL;
  }

  Optional<BookkeepingPostingRejection> rejectionFor(
      PostEntryCommand command, PostingValidationStore book) {
    Objects.requireNonNull(command, "command");
    Objects.requireNonNull(book, "book");
    List<BookkeepingPostingRejection.EntrySemanticsViolation> violations = new ArrayList<>();
    PostEntrySemanticContext semanticContext =
        PostEntrySemanticContext.from(command.entry(), REQUEST_SURFACE);
    Map<AccountCode, RegisteredAccount> accounts =
        book.findAccounts(semanticContext.referencedAccounts());
    PostEntryRoleAccountSemantics.validate(
        violations,
        accounts,
        command.entry(),
        semanticContext.selectorField(),
        semanticContext.selectorValue());
    requireSourceDocumentTypes(
        violations,
        semanticContext.selectorField(),
        semanticContext.selectorValue(),
        semanticContext.sourceDocumentTypes(),
        command.evidence().sourceDocuments());
    TaxEntrySemantics.validate(
        violations,
        book,
        command.entry(),
        semanticContext.selectorField(),
        semanticContext.selectorValue());
    if (violations.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new BookkeepingPostingRejection.EntrySemanticsViolations(violations));
  }

  private static void requireSourceDocumentTypes(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      String selectorField,
      String selectorValue,
      RequestSurfaceFacts.SourceDocumentTypeFacts sourceDocumentTypeFacts,
      List<SourceDocumentReference> sourceDocuments) {
    if (sourceDocumentTypeFacts.mode() != SourceDocumentTypePolicyMode.ENUMERATED) {
      return;
    }
    List<String> acceptedTypes = sourceDocumentTypeFacts.acceptedValues();
    for (SourceDocumentReference sourceDocument : sourceDocuments) {
      SourceDocumentType sourceDocumentType = sourceDocument.sourceDocumentType();
      if (acceptedTypes.contains(sourceDocumentType.value())) {
        continue;
      }
      violations.add(
          BookkeepingEntrySemanticsViolationFactory.sourceDocumentTypeNotAccepted(
              selectorField, selectorValue, sourceDocumentType, acceptedTypes));
    }
  }
}
