package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.LedgerFact;
import dev.erst.fingrind.contract.LedgerStep;
import dev.erst.fingrind.contract.LedgerStepFailure;
import dev.erst.fingrind.contract.LedgerStepStatus;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.contract.RejectionNarrative;
import java.util.List;

/** Shared fact and failure mapping for ledger-plan execution steps. */
final class LedgerPlanOutcomeMapper {
  private LedgerPlanOutcomeMapper() {}

  static LedgerPlanStepOutcome balanceFacts(AccountBalanceSnapshot snapshot) {
    return stepSucceeded(LedgerPlanFactMapper.balanceFacts(snapshot));
  }

  static List<LedgerFact> postingFacts(PostingFact postingFact) {
    return LedgerPlanFactMapper.postingFacts(postingFact);
  }

  static LedgerPlanStepOutcome administrationRejection(
      dev.erst.fingrind.contract.BookAdministrationRejection rejection) {
    return stepRejected(
        dev.erst.fingrind.contract.BookAdministrationRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        RejectionNarrative.facts(rejection));
  }

  static LedgerPlanStepOutcome queryRejection(
      dev.erst.fingrind.contract.BookQueryRejection rejection) {
    return stepRejected(
        dev.erst.fingrind.contract.BookQueryRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        RejectionNarrative.facts(rejection));
  }

  static LedgerPlanStepOutcome postingRejection(PostingRejection rejection) {
    return stepRejected(
        PostingRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        RejectionNarrative.facts(rejection));
  }

  static LedgerPlanStepOutcome assertionFailure(String message, LedgerFact... facts) {
    return new LedgerPlanStepOutcome.AssertionFailed(
        new LedgerStepFailure(
            LedgerStepStatus.ASSERTION_FAILED.wireValue(), message, List.of(facts)));
  }

  static String missingBookCode(LedgerStep firstStep) {
    return switch (firstStep.kind()) {
      case OPEN_BOOK, DECLARE_ACCOUNT ->
          dev.erst.fingrind.contract.BookAdministrationRejection.bookNotInitializedCode();
      case PREFLIGHT_ENTRY, POST_ENTRY -> PostingRejection.bookNotInitializedCode();
      case INSPECT_BOOK, LIST_ACCOUNTS, GET_POSTING, LIST_POSTINGS, ACCOUNT_BALANCE, ASSERT ->
          dev.erst.fingrind.contract.BookQueryRejection.bookNotInitializedCode();
    };
  }

  static LedgerPlanStepOutcome stepSucceeded(LedgerFact... facts) {
    return new LedgerPlanStepOutcome.Succeeded(List.of(facts));
  }

  static LedgerPlanStepOutcome stepSucceeded(List<LedgerFact> facts) {
    return new LedgerPlanStepOutcome.Succeeded(facts);
  }

  static LedgerPlanStepOutcome stepRejected(String code, String message, List<LedgerFact> facts) {
    return new LedgerPlanStepOutcome.Rejected(new LedgerStepFailure(code, message, facts));
  }
}
