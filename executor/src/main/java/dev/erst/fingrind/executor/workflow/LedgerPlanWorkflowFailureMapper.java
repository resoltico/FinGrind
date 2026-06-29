package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.RejectionNarrative;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingQueryRejection;
import java.util.ArrayList;
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
        postingRejectionFacts(publishedRejection));
  }

  static BookWorkflowFailure ensureBookIdentityConflict(
      BookIdentity existingBookIdentity, BookIdentity requestedBookIdentity) {
    Objects.requireNonNull(existingBookIdentity, "existingBookIdentity");
    Objects.requireNonNull(requestedBookIdentity, "requestedBookIdentity");
    return new BookWorkflowFailure(
        "ensure-book-identity-conflict",
        "The selected book is already initialized with a different book identity.",
        List.of(
            BookWorkflowFact.text("existingEntityName", existingBookIdentity.entityName().value()),
            BookWorkflowFact.text(
                "existingFunctionalCurrency", existingBookIdentity.functionalCurrency().code()),
            BookWorkflowFact.text(
                "existingFiscalYearStart", existingBookIdentity.fiscalYearStart().wireValue()),
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
                "requestedBookTemplateId",
                requestedBookIdentity.bookDoctrine().bookTemplateId().wireValue())));
  }

  private static List<BookWorkflowFact> postingRejectionFacts(PostingRejection rejection) {
    return switch (Objects.requireNonNull(rejection, "rejection")) {
      case PostingRejection.BookNotInitialized _ -> List.of();
      case PostingRejection.AccountStateViolations violations -> accountStateFacts(violations);
      case PostingRejection.EntrySemanticsViolations violations -> entrySemanticsFacts(violations);
      case PostingRejection.IdempotencyKeyConflict _ -> List.of();
      case PostingRejection.BookFunctionalCurrencyMismatch mismatch ->
          List.of(
              BookWorkflowFact.text("functionalCurrency", mismatch.functionalCurrency().code()),
              BookWorkflowFact.text("attemptedCurrency", mismatch.attemptedCurrency().code()));
      case PostingRejection.SweptInterimResultViolation sweptInterimResultViolation ->
          List.of(
              BookWorkflowFact.text(
                  "transferredThroughEffectiveDate",
                  sweptInterimResultViolation.transferredThroughEffectiveDate().toString()),
              BookWorkflowFact.text(
                  "attemptedEffectiveDate",
                  sweptInterimResultViolation.attemptedEffectiveDate().toString()));
      case PostingRejection.OpeningPositionWindowClosed openingBalanceWindowClosed ->
          List.of(
              BookWorkflowFact.text(
                  "firstBlockingPostingKind",
                  openingBalanceWindowClosed.firstBlockingPostingKind().wireValue()),
              BookWorkflowFact.text(
                  "firstBlockingEffectiveDate",
                  openingBalanceWindowClosed.firstBlockingEffectiveDate().toString()));
      case PostingRejection.OpeningPositionTouchesNominalAccount openingBalanceNominal ->
          List.of(
              BookWorkflowFact.text("accountCode", openingBalanceNominal.accountCode().value()),
              BookWorkflowFact.text(
                  "accountType", openingBalanceNominal.accountType().wireValue()));
      case PostingRejection.ReservedResultClassification reservedClassification ->
          List.of(
              BookWorkflowFact.text("accountCode", reservedClassification.accountCode().value()),
              BookWorkflowFact.text(
                  "financialPositionLineClassification",
                  reservedClassification.financialPositionLineClassification().wireValue()));
      case PostingRejection.ReversalTargetNotFound reversalTargetNotFound ->
          priorPostingFacts(reversalTargetNotFound.priorPostingId());
      case PostingRejection.ReversalAlreadyExists reversalAlreadyExists ->
          priorPostingFacts(reversalAlreadyExists.priorPostingId());
      case PostingRejection.ReversalDoesNotNegateTarget reversalDoesNotNegateTarget ->
          priorPostingFacts(reversalDoesNotNegateTarget.priorPostingId());
    };
  }

  private static List<BookWorkflowFact> accountStateFacts(
      PostingRejection.AccountStateViolations violations) {
    List<BookWorkflowFact> facts = new ArrayList<>();
    facts.add(BookWorkflowFact.count("violationCount", violations.violations().size()));
    for (PostingRejection.AccountStateViolation violation : violations.violations()) {
      switch (violation) {
        case PostingRejection.UnknownAccount unknownAccount ->
            facts.add(
                BookWorkflowFact.group(
                    "violation",
                    List.of(
                        BookWorkflowFact.text("code", "unknown-account"),
                        BookWorkflowFact.text(
                            "accountCode", unknownAccount.accountCode().value()))));
        case PostingRejection.InactiveAccount inactiveAccount ->
            facts.add(
                BookWorkflowFact.group(
                    "violation",
                    List.of(
                        BookWorkflowFact.text("code", "inactive-account"),
                        BookWorkflowFact.text(
                            "accountCode", inactiveAccount.accountCode().value()))));
        case PostingRejection.NonPostableAccount nonPostableAccount ->
            facts.add(
                BookWorkflowFact.group(
                    "violation",
                    List.of(
                        BookWorkflowFact.text("code", "non-postable-account"),
                        BookWorkflowFact.text(
                            "accountCode", nonPostableAccount.accountCode().value()),
                        BookWorkflowFact.text(
                            "accountNodeKind", nonPostableAccount.accountNodeKind().wireValue()))));
      }
    }
    return List.copyOf(facts);
  }

  private static List<BookWorkflowFact> entrySemanticsFacts(
      PostingRejection.EntrySemanticsViolations violations) {
    List<BookWorkflowFact> facts = new ArrayList<>();
    facts.add(BookWorkflowFact.count("violationCount", violations.violations().size()));
    for (PostingRejection.EntrySemanticsViolation violation : violations.violations()) {
      facts.add(entrySemanticsViolationFact(violation));
    }
    return List.copyOf(facts);
  }

  private static BookWorkflowFact entrySemanticsViolationFact(
      PostingRejection.EntrySemanticsViolation violation) {
    List<BookWorkflowFact> detailFacts = new ArrayList<>();
    detailFacts.add(BookWorkflowFact.text("code", violation.code()));
    if (violation.field() != null) {
      detailFacts.add(BookWorkflowFact.text("field", violation.field()));
    }
    detailFacts.add(BookWorkflowFact.text("message", violation.message()));
    return BookWorkflowFact.group("violation", detailFacts);
  }

  private static List<BookWorkflowFact> priorPostingFacts(PostingId priorPostingId) {
    return List.of(BookWorkflowFact.text("priorPostingId", priorPostingId.value()));
  }

  private static String missingBookMessage() {
    return "The selected book does not exist or has not been initialized with an ensure-book step.";
  }
}
