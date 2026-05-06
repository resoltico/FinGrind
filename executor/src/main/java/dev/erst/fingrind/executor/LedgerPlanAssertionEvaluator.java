package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.LedgerFact;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.workflow.BookWorkflowAssertion;
import java.util.Optional;

/** Assertion evaluation support for first-class ledger-plan assert steps. */
final class LedgerPlanAssertionEvaluator {
  private LedgerPlanAssertionEvaluator() {}

  static LedgerPlanStepOutcome evaluate(
      BookReadService bookReadService, BookWorkflowAssertion assertion) {
    return switch (assertion) {
      case BookWorkflowAssertion.AccountDeclared accountDeclared ->
          assertAccountDeclared(bookReadService, accountDeclared);
      case BookWorkflowAssertion.AccountActive accountActive ->
          assertAccountActive(bookReadService, accountActive);
      case BookWorkflowAssertion.PostingExists postingExists ->
          assertPostingExists(bookReadService, postingExists);
      case BookWorkflowAssertion.AccountBalanceEquals balanceEquals ->
          assertAccountBalance(bookReadService, balanceEquals);
    };
  }

  private static LedgerPlanStepOutcome assertAccountDeclared(
      BookReadService bookReadService, BookWorkflowAssertion.AccountDeclared assertion) {
    boolean present = bookReadService.findAccount(assertion.accountCode()).isPresent();
    return present
        ? LedgerPlanOutcomeMapper.stepSucceeded(
            LedgerFact.text("accountCode", assertion.accountCode().value()))
        : LedgerPlanOutcomeMapper.assertionFailure(
            "Account is not declared.",
            LedgerFact.text("accountCode", assertion.accountCode().value()));
  }

  private static LedgerPlanStepOutcome assertAccountActive(
      BookReadService bookReadService, BookWorkflowAssertion.AccountActive assertion) {
    Optional<RegisteredAccount> account = bookReadService.findAccount(assertion.accountCode());
    if (account.isEmpty()) {
      return LedgerPlanOutcomeMapper.assertionFailure(
          "Account is not declared.",
          LedgerFact.text("accountCode", assertion.accountCode().value()));
    }
    return account.orElseThrow().active()
        ? LedgerPlanOutcomeMapper.stepSucceeded(
            LedgerFact.text("accountCode", assertion.accountCode().value()))
        : LedgerPlanOutcomeMapper.assertionFailure(
            "Account is not active.",
            LedgerFact.text("accountCode", assertion.accountCode().value()));
  }

  private static LedgerPlanStepOutcome assertPostingExists(
      BookReadService bookReadService, BookWorkflowAssertion.PostingExists assertion) {
    boolean present = bookReadService.findPosting(assertion.postingId()).isPresent();
    return present
        ? LedgerPlanOutcomeMapper.stepSucceeded(
            LedgerFact.text("postingId", assertion.postingId().value()))
        : LedgerPlanOutcomeMapper.assertionFailure(
            "Posting does not exist.", LedgerFact.text("postingId", assertion.postingId().value()));
  }

  private static LedgerPlanStepOutcome assertAccountBalance(
      BookReadService bookReadService, BookWorkflowAssertion.AccountBalanceEquals assertion) {
    return switch (bookReadService.accountBalanceOutcome(assertion.query())) {
      case BookReadOutcome.Reported<AccountBalanceView> reported ->
          assertAccountBalance(assertion, reported.value());
      case BookReadOutcome.Rejected<AccountBalanceView> rejected ->
          LedgerPlanOutcomeMapper.queryRejection(rejected.rejection());
    };
  }

  private static LedgerPlanStepOutcome assertAccountBalance(
      BookWorkflowAssertion.AccountBalanceEquals assertion, AccountBalanceView view) {
    Optional<CurrencyBalance> matchingBalance =
        view.balances().stream()
            .filter(
                balance ->
                    balance.netAmount().currencyCode().equals(assertion.netAmount().currencyCode()))
            .findFirst();
    if (matchingBalance.isEmpty()) {
      return LedgerPlanOutcomeMapper.assertionFailure(
          "Expected currency balance bucket does not exist.",
          LedgerFact.text("accountCode", assertion.accountCode().value()),
          LedgerFact.text("currencyCode", assertion.netAmount().currencyCode().value()));
    }
    CurrencyBalance balance = matchingBalance.orElseThrow();
    boolean matchesAmount = balance.netAmount().equals(assertion.netAmount());
    boolean matchesSide = balance.balanceSide() == assertion.balanceSide();
    if (matchesAmount && matchesSide) {
      return LedgerPlanOutcomeMapper.balanceFacts(view);
    }
    return LedgerPlanOutcomeMapper.assertionFailure(
        "Account balance does not match expected value.",
        LedgerFact.text("accountCode", assertion.accountCode().value()),
        LedgerFact.text("currencyCode", assertion.netAmount().currencyCode().value()),
        LedgerFact.text("expectedNetAmount", assertion.netAmount().amount().toPlainString()),
        LedgerFact.text("actualNetAmount", balance.netAmount().amount().toPlainString()),
        LedgerFact.text("expectedBalanceSide", assertion.balanceSide().wireValue()),
        LedgerFact.text("actualBalanceSide", balance.balanceSide().wireValue()));
  }
}
