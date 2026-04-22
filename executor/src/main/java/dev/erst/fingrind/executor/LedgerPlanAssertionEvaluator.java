package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.AccountBalanceResult;
import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.CurrencyBalance;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.LedgerAssertion;
import dev.erst.fingrind.contract.LedgerFact;
import java.util.Optional;

/** Assertion evaluation support for first-class ledger-plan assert steps. */
final class LedgerPlanAssertionEvaluator {
  private LedgerPlanAssertionEvaluator() {}

  static LedgerPlanStepOutcome evaluate(
      BookReadService bookReadService, LedgerAssertion assertion) {
    return switch (assertion) {
      case LedgerAssertion.AccountDeclared accountDeclared ->
          assertAccountDeclared(bookReadService, accountDeclared);
      case LedgerAssertion.AccountActive accountActive ->
          assertAccountActive(bookReadService, accountActive);
      case LedgerAssertion.PostingExists postingExists ->
          assertPostingExists(bookReadService, postingExists);
      case LedgerAssertion.AccountBalanceEquals balanceEquals ->
          assertAccountBalance(bookReadService, balanceEquals);
    };
  }

  private static LedgerPlanStepOutcome assertAccountDeclared(
      BookReadService bookReadService, LedgerAssertion.AccountDeclared assertion) {
    boolean present = bookReadService.findAccount(assertion.accountCode()).isPresent();
    return present
        ? LedgerPlanOutcomeMapper.stepSucceeded(
            LedgerFact.text("accountCode", assertion.accountCode().value()))
        : LedgerPlanOutcomeMapper.assertionFailure(
            "Account is not declared.",
            LedgerFact.text("accountCode", assertion.accountCode().value()));
  }

  private static LedgerPlanStepOutcome assertAccountActive(
      BookReadService bookReadService, LedgerAssertion.AccountActive assertion) {
    Optional<DeclaredAccount> account = bookReadService.findAccount(assertion.accountCode());
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
      BookReadService bookReadService, LedgerAssertion.PostingExists assertion) {
    boolean present = bookReadService.findPosting(assertion.postingId()).isPresent();
    return present
        ? LedgerPlanOutcomeMapper.stepSucceeded(
            LedgerFact.text("postingId", assertion.postingId().value()))
        : LedgerPlanOutcomeMapper.assertionFailure(
            "Posting does not exist.", LedgerFact.text("postingId", assertion.postingId().value()));
  }

  private static LedgerPlanStepOutcome assertAccountBalance(
      BookReadService bookReadService, LedgerAssertion.AccountBalanceEquals assertion) {
    return switch (bookReadService.accountBalance(assertion.query())) {
      case AccountBalanceResult.Reported reported ->
          assertAccountBalance(assertion, reported.snapshot());
      case AccountBalanceResult.Rejected rejected ->
          LedgerPlanOutcomeMapper.queryRejection(rejected.rejection());
    };
  }

  private static LedgerPlanStepOutcome assertAccountBalance(
      LedgerAssertion.AccountBalanceEquals assertion, AccountBalanceSnapshot snapshot) {
    Optional<CurrencyBalance> matchingBalance =
        snapshot.balances().stream()
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
      return LedgerPlanOutcomeMapper.balanceFacts(snapshot);
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
