package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.RejectionNarrative;
import dev.erst.fingrind.contract.tax.TaxDeclarationRejection;
import dev.erst.fingrind.contract.workflow.LedgerPlanFailure;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingQueryRejection;
import java.util.List;
import java.util.Objects;

/** Maps local workflow rejections into stable ledger-plan failure payloads. */
final class LedgerPlanWorkflowFailureMapper {
  private LedgerPlanWorkflowFailureMapper() {}

  static BookWorkflowFailure administrationFailure(BookkeepingAdministrationRejection rejection) {
    Objects.requireNonNull(rejection, "rejection");
    BookAdministrationRejection published =
        LedgerPlanAdministrationFailureSupport.toPublished(rejection);
    String message =
        published instanceof BookAdministrationRejection.BookNotInitialized
            ? missingBookMessage()
            : RejectionNarrative.message(published);
    return new BookWorkflowFailure(
        BookAdministrationRejection.wireCode(published),
        message,
        LedgerPlanAdministrationFailureSupport.facts(published));
  }

  static BookWorkflowFailure queryFailure(BookkeepingQueryRejection rejection) {
    Objects.requireNonNull(rejection, "rejection");
    return switch (rejection) {
      case BookkeepingQueryRejection.BookNotInitialized _ ->
          new BookWorkflowFailure(
              BookkeepingQueryRejection.bookNotInitializedCode(), missingBookMessage(), List.of());
      case BookkeepingQueryRejection.UnknownAccount unknownAccount ->
          new BookWorkflowFailure(
              BookkeepingQueryRejection.wireCode(unknownAccount),
              "Account '%s' is not declared in this book."
                  .formatted(unknownAccount.accountCode().value()),
              List.of(BookWorkflowFact.text("accountCode", unknownAccount.accountCode().value())));
      case BookkeepingQueryRejection.PostingNotFound postingNotFound ->
          new BookWorkflowFailure(
              BookkeepingQueryRejection.wireCode(postingNotFound),
              "Posting '%s' does not exist in this book."
                  .formatted(postingNotFound.postingId().value()),
              List.of(BookWorkflowFact.text("postingId", postingNotFound.postingId().value())));
    };
  }

  static BookWorkflowFailure postingFailure(BookkeepingPostingRejection rejection) {
    Objects.requireNonNull(rejection, "rejection");
    PostingRejection publishedRejection =
        dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator.toPublished(
            rejection);
    return postingFailure(publishedRejection);
  }

  static BookWorkflowFailure postingFailure(PostingRejection publishedRejection) {
    Objects.requireNonNull(publishedRejection, "publishedRejection");
    return new BookWorkflowFailure(
        PostingRejection.wireCode(publishedRejection),
        RejectionNarrative.message(publishedRejection),
        LedgerPlanWorkflowPostingFailureFacts.postingRejectionFacts(publishedRejection));
  }

  static BookWorkflowFailure taxDeclarationFailure(TaxDeclarationRejection rejection) {
    Objects.requireNonNull(rejection, "rejection");
    return switch (rejection) {
      case TaxDeclarationRejection.BookNotInitialized _ ->
          new BookWorkflowFailure(
              TaxDeclarationRejection.wireCode(rejection), missingBookMessage(), List.of());
      case TaxDeclarationRejection.DefinitionViolations violations ->
          new BookWorkflowFailure(
              TaxDeclarationRejection.wireCode(rejection),
              "The tax registration definition contains one or more invalid facts.",
              List.of(BookWorkflowFact.count("violationCount", violations.violations().size())));
    };
  }

  static BookWorkflowFailure ensureBookIdentityConflict(
      BookIdentity existingBookIdentity, BookIdentity requestedBookIdentity) {
    Objects.requireNonNull(existingBookIdentity, "existingBookIdentity");
    Objects.requireNonNull(requestedBookIdentity, "requestedBookIdentity");
    return new BookWorkflowFailure(
        LedgerPlanFailure.ENSURE_BOOK_IDENTITY_CONFLICT.code(),
        "The selected book is already initialized with a different book identity.",
        List.of(
            BookWorkflowFact.text("existingEntityName", existingBookIdentity.entityName().value()),
            BookWorkflowFact.text(
                "existingFunctionalCurrency", existingBookIdentity.functionalCurrency().code()),
            BookWorkflowFact.text(
                "existingFiscalYearStart", existingBookIdentity.fiscalYearStart().wireValue()),
            BookWorkflowFact.text(
                "existingBookStartEffectiveDate",
                existingBookIdentity.bookStartEffectiveDate().toString()),
            BookWorkflowFact.text(
                "existingBookTemplateId",
                existingBookIdentity.bookDoctrine().bookTemplateId().wireValue()),
            BookWorkflowFact.text(
                "requestedEntityName", requestedBookIdentity.entityName().value()),
            BookWorkflowFact.text(
                "requestedFunctionalCurrency", requestedBookIdentity.functionalCurrency().code()),
            BookWorkflowFact.text(
                "requestedFiscalYearStart", requestedBookIdentity.fiscalYearStart().wireValue()),
            BookWorkflowFact.text(
                "requestedBookStartEffectiveDate",
                requestedBookIdentity.bookStartEffectiveDate().toString()),
            BookWorkflowFact.text(
                "requestedBookTemplateId",
                requestedBookIdentity.bookDoctrine().bookTemplateId().wireValue())));
  }

  private static String missingBookMessage() {
    return "The selected book does not exist or has not been initialized with an ensure-book step.";
  }
}
