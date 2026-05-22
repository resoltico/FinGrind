package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.ClosePeriodCommand;
import dev.erst.fingrind.contract.bookkeeping.ClosePeriodResult;
import dev.erst.fingrind.contract.bookkeeping.ClosedPeriod;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import java.util.Objects;

/** Translates between the public published language and the local bookkeeping model. */
public final class BookkeepingPublishedLanguageTranslator {
  private BookkeepingPublishedLanguageTranslator() {}

  /** Translates one public declare-account request into the local bookkeeping model. */
  public static AccountDeclaration fromPublished(DeclareAccountCommand command) {
    Objects.requireNonNull(command, "command");
    return new AccountDeclaration(
        command.accountCode(),
        command.accountName(),
        command.accountType(),
        command.accountRole(),
        command.accountTaxonomy());
  }

  /** Translates one public close-period request into the local bookkeeping model. */
  public static dev.erst.fingrind.core.ReportingPeriod fromPublished(ClosePeriodCommand command) {
    Objects.requireNonNull(command, "command");
    return command.reportingPeriod();
  }

  /** Translates one public open-book request into the local identity model. */
  public static dev.erst.fingrind.core.BookIdentity fromPublished(OpenBookCommand command) {
    Objects.requireNonNull(command, "command");
    return command.bookIdentity();
  }

  /** Translates one published committed posting into the local bookkeeping model. */
  public static CommittedPosting fromPublished(PostingFact postingFact) {
    Objects.requireNonNull(postingFact, "postingFact");
    return new CommittedPosting(
        postingFact.postingId(),
        postingFact.journalEntry(),
        fromPublished(postingFact.postingLineage()),
        postingFact.postingKind(),
        postingFact.evidence(),
        postingFact.provenance());
  }

  /** Translates one published posting lineage into the bookkeeping model. */
  public static PostingLineageModel fromPublished(PostingLineage postingLineage) {
    Objects.requireNonNull(postingLineage, "postingLineage");
    return switch (postingLineage) {
      case PostingLineage.Direct _ -> PostingLineageModel.direct();
      case PostingLineage.Reversal reversal ->
          PostingLineageModel.reversal(reversal.reference(), reversal.reason());
    };
  }

  /** Translates one bookkeeping registered account into the public response model. */
  public static DeclaredAccount toPublished(RegisteredAccount account) {
    Objects.requireNonNull(account, "account");
    return new DeclaredAccount(
        account.accountCode(),
        account.accountName(),
        account.accountType(),
        account.accountRole(),
        account.accountTaxonomy(),
        account.active(),
        account.declaredAt());
  }

  /** Translates one bookkeeping committed posting into the public response model. */
  public static PostingFact toPublished(CommittedPosting posting) {
    Objects.requireNonNull(posting, "posting");
    return new PostingFact(
        posting.postingId(),
        posting.journalEntry(),
        toPublished(posting.postingLineage()),
        posting.postingKind(),
        posting.evidence(),
        posting.provenance());
  }

  /** Translates one bookkeeping opening outcome into the public response model. */
  public static OpenBookResult toPublished(BookOpeningOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome");
    return switch (outcome) {
      case BookOpeningOutcome.Opened opened ->
          new OpenBookResult.Opened(opened.initializedAt(), opened.bookIdentity());
      case BookOpeningOutcome.Rejected rejected ->
          new OpenBookResult.Rejected(toPublished(rejected.rejection()));
    };
  }

  /** Translates one bookkeeping account-declaration outcome into the public response model. */
  public static DeclareAccountResult toPublished(AccountDeclarationOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome");
    return switch (outcome) {
      case AccountDeclarationOutcome.Declared declared ->
          new DeclareAccountResult.Declared(toPublished(declared.account()));
      case AccountDeclarationOutcome.Rejected rejected ->
          new DeclareAccountResult.Rejected(toPublished(rejected.rejection()));
    };
  }

  /** Translates one bookkeeping period-close outcome into the public response model. */
  public static ClosePeriodResult toPublished(PeriodCloseOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome");
    return switch (outcome) {
      case PeriodCloseOutcome.Closed closed ->
          new ClosePeriodResult.Closed(toPublished(closed.closedPeriod()));
      case PeriodCloseOutcome.Rejected rejected ->
          new ClosePeriodResult.Rejected(toPublished(rejected.rejection()));
    };
  }

  /** Translates one bookkeeping administration rejection into the public rejection contract. */
  public static BookAdministrationRejection toPublished(
      BookkeepingAdministrationRejection rejection) {
    Objects.requireNonNull(rejection, "rejection");
    return switch (rejection) {
      case BookkeepingAdministrationRejection.BookAlreadyInitialized _ ->
          new BookAdministrationRejection.BookAlreadyInitialized();
      case BookkeepingAdministrationRejection.BookNotInitialized _ ->
          new BookAdministrationRejection.BookNotInitialized();
      case BookkeepingAdministrationRejection.BookContainsSchema _ ->
          new BookAdministrationRejection.BookContainsSchema();
      case BookkeepingAdministrationRejection.AccountTypeConflict conflict ->
          new BookAdministrationRejection.AccountTypeConflict(
              conflict.accountCode(),
              conflict.existingAccountType(),
              conflict.requestedAccountType());
      case BookkeepingAdministrationRejection.AccountRoleConflict conflict ->
          new BookAdministrationRejection.AccountRoleConflict(
              conflict.accountCode(),
              conflict.existingAccountRole(),
              conflict.requestedAccountRole());
      case BookkeepingAdministrationRejection.AccountTaxonomyConflict conflict ->
          new BookAdministrationRejection.AccountTaxonomyConflict(
              conflict.accountCode(),
              conflict.existingAccountTaxonomy(),
              conflict.requestedAccountTaxonomy());
      case BookkeepingAdministrationRejection.ParentAccountMissing conflict ->
          new BookAdministrationRejection.ParentAccountMissing(
              conflict.accountCode(), conflict.parentAccountCode());
      case BookkeepingAdministrationRejection.ParentAccountInactive conflict ->
          new BookAdministrationRejection.ParentAccountInactive(
              conflict.accountCode(), conflict.parentAccountCode());
      case BookkeepingAdministrationRejection.ParentAccountTypeConflict conflict ->
          new BookAdministrationRejection.ParentAccountTypeConflict(
              conflict.accountCode(),
              conflict.requestedAccountType(),
              conflict.parentAccountCode(),
              conflict.parentAccountType());
      case BookkeepingAdministrationRejection.ParentAccountRoleConflict conflict ->
          new BookAdministrationRejection.ParentAccountRoleConflict(
              conflict.accountCode(),
              conflict.requestedAccountRole(),
              conflict.parentAccountCode(),
              conflict.parentAccountRole());
      case BookkeepingAdministrationRejection.ParentAccountNotHeader conflict ->
          new BookAdministrationRejection.ParentAccountNotHeader(
              conflict.accountCode(),
              conflict.parentAccountCode(),
              conflict.parentAccountNodeKind());
      case BookkeepingAdministrationRejection.ParentAccountTaxonomyConflict conflict ->
          new BookAdministrationRejection.ParentAccountTaxonomyConflict(
              conflict.accountCode(),
              conflict.requestedAccountTaxonomy(),
              conflict.parentAccountCode(),
              conflict.parentAccountTaxonomy());
      case BookkeepingAdministrationRejection.AccountHierarchyCycle conflict ->
          new BookAdministrationRejection.AccountHierarchyCycle(
              conflict.accountCode(), conflict.parentAccountCode());
      case BookkeepingAdministrationRejection.ClosingEquityAccountCandidateMissing conflict ->
          new BookAdministrationRejection.ClosingEquityAccountCandidateMissing(
              conflict.requiredFinancialPositionLineClassification(),
              conflict.inactiveCandidateAccountCodes());
      case BookkeepingAdministrationRejection.ClosingEquityAccountCandidateAmbiguous conflict ->
          new BookAdministrationRejection.ClosingEquityAccountCandidateAmbiguous(
              conflict.requiredFinancialPositionLineClassification(),
              conflict.candidateAccountCodes());
      case BookkeepingAdministrationRejection.PeriodCloseMustStartAt conflict ->
          new BookAdministrationRejection.PeriodCloseMustStartAt(
              conflict.requiredEffectiveDateFrom());
      case BookkeepingAdministrationRejection.PeriodCloseFutureDate conflict ->
          new BookAdministrationRejection.PeriodCloseFutureDate(
              conflict.attemptedEffectiveDateTo());
      case BookkeepingAdministrationRejection.PeriodCloseCrossesFiscalYearBoundary conflict ->
          new BookAdministrationRejection.PeriodCloseCrossesFiscalYearBoundary(
              conflict.attemptedEffectiveDateFrom(),
              conflict.attemptedEffectiveDateTo(),
              conflict.fiscalYearStart());
    };
  }

  /** Translates one bookkeeping posting rejection into the public rejection contract. */
  public static PostingRejection toPublished(BookkeepingPostingRejection rejection) {
    Objects.requireNonNull(rejection, "rejection");
    return switch (rejection) {
      case BookkeepingPostingRejection.BookNotInitialized _ ->
          new PostingRejection.BookNotInitialized();
      case BookkeepingPostingRejection.AccountStateViolations violations ->
          new PostingRejection.AccountStateViolations(
              violations.violations().stream()
                  .map(BookkeepingPublishedLanguageTranslator::toPublished)
                  .toList());
      case BookkeepingPostingRejection.DuplicateIdempotencyKey _ ->
          new PostingRejection.DuplicateIdempotencyKey();
      case BookkeepingPostingRejection.BookFunctionalCurrencyMismatch currencyMismatch ->
          new PostingRejection.BookFunctionalCurrencyMismatch(
              currencyMismatch.functionalCurrency(), currencyMismatch.attemptedCurrency());
      case BookkeepingPostingRejection.ClosedPeriodViolation rejectionClosedPeriod ->
          new PostingRejection.ClosedPeriodViolation(
              rejectionClosedPeriod.closedThroughEffectiveDate(),
              rejectionClosedPeriod.attemptedEffectiveDate());
      case BookkeepingPostingRejection.OpeningBalanceWindowClosed rejectionWindowClosed ->
          new PostingRejection.OpeningBalanceWindowClosed(
              rejectionWindowClosed.firstBlockingPostingKind(),
              rejectionWindowClosed.firstBlockingEffectiveDate());
      case BookkeepingPostingRejection.OpeningBalanceTouchesNominalAccount rejectionNominal ->
          new PostingRejection.OpeningBalanceTouchesNominalAccount(
              rejectionNominal.accountCode(), rejectionNominal.accountType());
      case BookkeepingPostingRejection.ClosingEquityAccountReserved rejectionReserved ->
          new PostingRejection.ClosingEquityAccountReserved(rejectionReserved.accountCode());
      case BookkeepingPostingRejection.ReversalTargetNotFound rejectionTarget ->
          new PostingRejection.ReversalTargetNotFound(rejectionTarget.priorPostingId());
      case BookkeepingPostingRejection.ReversalAlreadyExists rejectionExists ->
          new PostingRejection.ReversalAlreadyExists(rejectionExists.priorPostingId());
      case BookkeepingPostingRejection.ReversalDoesNotNegateTarget rejectionMismatch ->
          new PostingRejection.ReversalDoesNotNegateTarget(rejectionMismatch.priorPostingId());
    };
  }

  private static PostingRejection.AccountStateViolation toPublished(
      BookkeepingPostingRejection.AccountStateViolation violation) {
    Objects.requireNonNull(violation, "violation");
    return switch (violation) {
      case BookkeepingPostingRejection.UnknownAccount unknownAccount ->
          new PostingRejection.UnknownAccount(unknownAccount.accountCode());
      case BookkeepingPostingRejection.InactiveAccount inactiveAccount ->
          new PostingRejection.InactiveAccount(inactiveAccount.accountCode());
      case BookkeepingPostingRejection.NonPostableAccount nonPostableAccount ->
          new PostingRejection.NonPostableAccount(
              nonPostableAccount.accountCode(), nonPostableAccount.accountNodeKind());
    };
  }

  /** Translates one bookkeeping lineage back into the public published language. */
  public static PostingLineage toPublished(PostingLineageModel postingLineage) {
    Objects.requireNonNull(postingLineage, "postingLineage");
    return switch (postingLineage) {
      case PostingLineageModel.Direct _ -> PostingLineage.direct();
      case PostingLineageModel.Reversal reversal ->
          PostingLineage.reversal(reversal.reference(), reversal.reason());
    };
  }

  /** Translates one durably recorded closed period into the public contract. */
  public static ClosedPeriod toPublished(
      dev.erst.fingrind.executor.bookkeeping.ClosedPeriod closedPeriod) {
    Objects.requireNonNull(closedPeriod, "closedPeriod");
    return new ClosedPeriod(
        closedPeriod.closeOrder(),
        closedPeriod.reportingPeriod(),
        closedPeriod.closingEquityAccountCode(),
        closedPeriod.closedTotals(),
        closedPeriod.closedAt(),
        closedPeriod.closingPostingIds());
  }
}
