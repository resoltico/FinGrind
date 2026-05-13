package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingLookupOutcome;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingReadOutcome;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingReadService;
import java.util.Optional;

/** Assertion evaluation support for first-class ledger-plan assert steps. */
public final class LedgerPlanAssertionEvaluator {
  private LedgerPlanAssertionEvaluator() {}

  /** Evaluates one local workflow assertion against the local bookkeeping read service. */
  public static LedgerPlanStepOutcome evaluate(
      BookkeepingReadService bookkeepingReadService, BookWorkflowAssertion assertion) {
    return switch (assertion) {
      case BookWorkflowAssertion.AccountDeclared accountDeclared ->
          assertAccountDeclared(bookkeepingReadService, accountDeclared);
      case BookWorkflowAssertion.AccountActive accountActive ->
          assertAccountActive(bookkeepingReadService, accountActive);
      case BookWorkflowAssertion.PostingExists postingExists ->
          assertPostingExists(bookkeepingReadService, postingExists);
      case BookWorkflowAssertion.AccountBalanceEquals balanceEquals ->
          assertAccountBalance(bookkeepingReadService, balanceEquals);
    };
  }

  private static LedgerPlanStepOutcome assertAccountDeclared(
      BookkeepingReadService bookkeepingReadService,
      BookWorkflowAssertion.AccountDeclared assertion) {
    return switch (bookkeepingReadService.findAccount(assertion.accountCode())) {
      case BookkeepingLookupOutcome.Found<RegisteredAccount> _ ->
          LedgerPlanOutcomeMapper.stepSucceeded(
              BookWorkflowFact.text("accountCode", assertion.accountCode().value()));
      case BookkeepingLookupOutcome.Missing<RegisteredAccount> _ ->
          LedgerPlanOutcomeMapper.assertionFailure(
              "Account is not declared.",
              BookWorkflowFact.text("accountCode", assertion.accountCode().value()));
      case BookkeepingLookupOutcome.Rejected<RegisteredAccount> rejected ->
          LedgerPlanOutcomeMapper.queryRejection(rejected.rejection());
    };
  }

  private static LedgerPlanStepOutcome assertAccountActive(
      BookkeepingReadService bookkeepingReadService,
      BookWorkflowAssertion.AccountActive assertion) {
    return switch (bookkeepingReadService.findAccount(assertion.accountCode())) {
      case BookkeepingLookupOutcome.Found<RegisteredAccount> found ->
          found.value().active()
              ? LedgerPlanOutcomeMapper.stepSucceeded(
                  BookWorkflowFact.text("accountCode", assertion.accountCode().value()))
              : LedgerPlanOutcomeMapper.assertionFailure(
                  "Account is not active.",
                  BookWorkflowFact.text("accountCode", assertion.accountCode().value()));
      case BookkeepingLookupOutcome.Missing<RegisteredAccount> _ ->
          LedgerPlanOutcomeMapper.assertionFailure(
              "Account is not declared.",
              BookWorkflowFact.text("accountCode", assertion.accountCode().value()));
      case BookkeepingLookupOutcome.Rejected<RegisteredAccount> rejected ->
          LedgerPlanOutcomeMapper.queryRejection(rejected.rejection());
    };
  }

  private static LedgerPlanStepOutcome assertPostingExists(
      BookkeepingReadService bookkeepingReadService,
      BookWorkflowAssertion.PostingExists assertion) {
    return switch (bookkeepingReadService.findPosting(assertion.postingId())) {
      case BookkeepingLookupOutcome.Found<dev.erst.fingrind.executor.bookkeeping.CommittedPosting>
              _ ->
          LedgerPlanOutcomeMapper.stepSucceeded(
              BookWorkflowFact.text("postingId", assertion.postingId().value()));
      case BookkeepingLookupOutcome.Missing<dev.erst.fingrind.executor.bookkeeping.CommittedPosting>
              _ ->
          LedgerPlanOutcomeMapper.assertionFailure(
              "Posting does not exist.",
              BookWorkflowFact.text("postingId", assertion.postingId().value()));
      case BookkeepingLookupOutcome.Rejected<
                  dev.erst.fingrind.executor.bookkeeping.CommittedPosting>
              rejected ->
          LedgerPlanOutcomeMapper.queryRejection(rejected.rejection());
    };
  }

  private static LedgerPlanStepOutcome assertAccountBalance(
      BookkeepingReadService bookkeepingReadService,
      BookWorkflowAssertion.AccountBalanceEquals assertion) {
    return switch (bookkeepingReadService.accountBalance(assertion.query())) {
      case BookkeepingReadOutcome.Reported<AccountBalanceView> reported ->
          assertAccountBalance(assertion, reported.value());
      case BookkeepingReadOutcome.Rejected<AccountBalanceView> rejected ->
          LedgerPlanOutcomeMapper.queryRejection(rejected.rejection());
    };
  }

  private static LedgerPlanStepOutcome assertAccountBalance(
      BookWorkflowAssertion.AccountBalanceEquals assertion, AccountBalanceView view) {
    Optional<CurrencyBalance> matchingBalance =
        view.balances().stream()
            .filter(
                balance ->
                    balance.netAmount().currencyUnit().equals(assertion.netAmount().currencyUnit()))
            .findFirst();
    if (matchingBalance.isEmpty()) {
      return LedgerPlanOutcomeMapper.assertionFailure(
          "Expected currency balance bucket does not exist.",
          BookWorkflowFact.text("accountCode", assertion.accountCode().value()),
          BookWorkflowFact.money("expectedNetAmount", MonetaryAmount.of(assertion.netAmount())));
    }
    CurrencyBalance balance = matchingBalance.orElseThrow();
    boolean matchesAmount = balance.netAmount().equals(assertion.netAmount());
    boolean matchesSide = balance.balanceSide() == assertion.balanceSide();
    if (matchesAmount && matchesSide) {
      return LedgerPlanOutcomeMapper.balanceFacts(view);
    }
    return LedgerPlanOutcomeMapper.assertionFailure(
        "Account balance does not match expected value.",
        BookWorkflowFact.text("accountCode", assertion.accountCode().value()),
        BookWorkflowFact.money("expectedNetAmount", MonetaryAmount.of(assertion.netAmount())),
        BookWorkflowFact.money("actualNetAmount", MonetaryAmount.of(balance.netAmount())),
        BookWorkflowFact.text("expectedBalanceSide", assertion.balanceSide().wireValue()),
        BookWorkflowFact.text("actualBalanceSide", balance.balanceSide().wireValue()));
  }
}
