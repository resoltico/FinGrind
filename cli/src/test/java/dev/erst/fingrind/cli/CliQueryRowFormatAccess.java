package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import java.util.List;

/** Test-only accessors for split query row, balance, and posting display helpers. */
final class CliQueryRowFormatAccess {
  private CliQueryRowFormatAccess() {}

  static List<String> postingRegisterTextRow(PostingFact postingFact) {
    return CliPostingFactFormatter.postingRegisterTextRow(postingFact);
  }

  static List<String> balanceTextRow(CurrencyBalance balance) {
    return CliBalanceOutputFormatter.balanceTextRow(balance);
  }

  static List<String> balanceCsvRow(CurrencyBalance balance) {
    return CliBalanceOutputFormatter.balanceCsvRow(balance);
  }

  static List<String> trialBalanceCsvRow(TrialBalanceRow row) {
    return CliPostingFactFormatter.trialBalanceCsvRow(row);
  }

  static List<String> accountLedgerTextRow(DeclaredAccount account, AccountLedgerEntry entry) {
    return CliPostingFactFormatter.accountLedgerTextRow(account, entry);
  }

  static List<String> periodActivityCsvRow(PeriodAccountActivityRow row) {
    return CliPostingFactFormatter.periodActivityCsvRow(row);
  }

  static String counterpartAccounts(DeclaredAccount account, PostingFact postingFact) {
    return CliPostingFactFormatter.counterpartAccounts(account, postingFact);
  }

  static String postingSourceDocumentsText(PostingFact postingFact) {
    return CliPostingFactFormatter.postingSourceDocumentsText(postingFact);
  }

  static String postingSourceDocumentIdsText(PostingFact postingFact) {
    return CliPostingFactFormatter.postingSourceDocumentIdsText(postingFact);
  }

  static String postingApprovalsText(PostingFact postingFact) {
    return CliPostingFactFormatter.postingApprovalsText(postingFact);
  }

  static String joinedBalances(List<CurrencyBalance> balances) {
    return CliBalanceOutputFormatter.joinedBalances(balances);
  }

  static String displayBalance(CurrencyBalance balance) {
    return CliBalanceOutputFormatter.displayBalance(balance);
  }

  static String displayBalanceText(CurrencyBalance balance) {
    return CliBalanceOutputFormatter.displayBalanceText(balance);
  }

  static String displayBalanceSideLabel(BalanceSide balanceSide) {
    return CliBalanceOutputFormatter.displayBalanceSideLabel(balanceSide);
  }

  static String displayBalanceStateLabel(boolean balanced) {
    return CliBalanceOutputFormatter.displayBalanceStateLabel(balanced);
  }

  static String postingCurrency(PostingFact postingFact) {
    return CliPostingLabels.postingCurrency(postingFact);
  }

  static String postingDebitTotal(PostingFact postingFact) {
    return CliPostingLabels.postingDebitTotal(postingFact);
  }

  static String postingCreditTotal(PostingFact postingFact) {
    return CliPostingLabels.postingCreditTotal(postingFact);
  }
}
