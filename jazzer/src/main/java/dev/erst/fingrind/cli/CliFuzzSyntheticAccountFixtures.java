package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Synthetic account declarations shared by Jazzer workflow fixtures. */
public final class CliFuzzSyntheticAccountFixtures {
  private CliFuzzSyntheticAccountFixtures() {}

  /** Returns deterministic declare-account commands for every distinct posting account. */
  public static List<DeclareAccountCommand> declarePostingAccountCommands(
      PostEntryCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return switch (command.entry()) {
      case BookkeepingEntry.CashRevenue event ->
          List.of(
              syntheticDeclareAccountCommand(
                  event.cashAccountCode(),
                  AccountType.ASSET,
                  AccountRole.ORDINARY,
                  syntheticAccountTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET)),
              syntheticDeclareAccountCommand(
                  event.revenueAccountCode(),
                  AccountType.REVENUE,
                  AccountRole.ORDINARY,
                  syntheticAccountTaxonomy(ProfitAndLossLineClassification.OPERATING_REVENUE)));
      case BookkeepingEntry.CashExpense event ->
          List.of(
              syntheticDeclareAccountCommand(
                  event.expenseAccountCode(),
                  AccountType.EXPENSE,
                  AccountRole.ORDINARY,
                  syntheticAccountTaxonomy(ProfitAndLossLineClassification.OPERATING_EXPENSE)),
              syntheticDeclareAccountCommand(
                  event.cashAccountCode(),
                  AccountType.ASSET,
                  AccountRole.ORDINARY,
                  syntheticAccountTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET)));
      case BookkeepingEntry.EquityContribution event ->
          List.of(
              syntheticDeclareAccountCommand(
                  event.cashAccountCode(),
                  AccountType.ASSET,
                  AccountRole.ORDINARY,
                  syntheticAccountTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET)),
              syntheticDeclareAccountCommand(
                  event.equityAccountCode(),
                  AccountType.EQUITY,
                  AccountRole.ORDINARY,
                  syntheticAccountTaxonomy(
                      FinancialPositionLineClassification.EQUITY_CONTRIBUTION)));
      case BookkeepingEntry.EquityWithdrawal event ->
          List.of(
              syntheticDeclareAccountCommand(
                  event.equityAccountCode(),
                  AccountType.EQUITY,
                  AccountRole.ORDINARY,
                  syntheticAccountTaxonomy(FinancialPositionLineClassification.EQUITY_WITHDRAWAL)),
              syntheticDeclareAccountCommand(
                  event.cashAccountCode(),
                  AccountType.ASSET,
                  AccountRole.ORDINARY,
                  syntheticAccountTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET)));
      case BookkeepingEntry.OpeningBalanceAdjustment _ ->
          distinctJournalLineAccountDeclarations(command);
      case BookkeepingEntry.CorrectionAdjustment _ ->
          distinctJournalLineAccountDeclarations(command);
      case BookkeepingEntry.ReversalAdjustment _ -> distinctJournalLineAccountDeclarations(command);
    };
  }

  /** Returns the first journal-line account code for lifecycle assertions. */
  public static AccountCode firstAccountCode(PostEntryCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return CliFuzzFixtures.journalEntry(command).lines().getFirst().accountCode();
  }

  private static List<DeclareAccountCommand> distinctJournalLineAccountDeclarations(
      PostEntryCommand command) {
    return CliFuzzFixtures.journalEntry(command).lines().stream()
        .map(line -> line.accountCode())
        .distinct()
        .map(CliFuzzSyntheticAccountFixtures::syntheticDeclareAccountCommand)
        .toList();
  }

  private static AccountName syntheticAccountName(AccountCode accountCode) {
    return new AccountName("Synthetic " + accountCode.value());
  }

  private static DeclareAccountCommand syntheticDeclareAccountCommand(AccountCode accountCode) {
    AccountType accountType = syntheticAccountType(accountCode);
    AccountRole accountRole = syntheticAccountRole(accountCode);
    return syntheticDeclareAccountCommand(
        accountCode, accountType, accountRole, syntheticAccountTaxonomy(accountType));
  }

  private static DeclareAccountCommand syntheticDeclareAccountCommand(
      AccountCode accountCode,
      AccountType accountType,
      AccountRole accountRole,
      AccountTaxonomy accountTaxonomy) {
    return new DeclareAccountCommand(
        accountCode, syntheticAccountName(accountCode), accountType, accountRole, accountTaxonomy);
  }

  private static AccountRole syntheticAccountRole(AccountCode accountCode) {
    int bucket = Math.floorMod(accountCode.value().hashCode(), 4);
    if (bucket == 0) {
      return AccountRole.CONTRA;
    }
    return AccountRole.ORDINARY;
  }

  private static AccountType syntheticAccountType(AccountCode accountCode) {
    String normalized = Objects.requireNonNull(accountCode, "accountCode").value().strip();
    if (Character.isDigit(normalized.charAt(0))) {
      return switch (normalized.charAt(0)) {
        case '1' -> AccountType.ASSET;
        case '2' -> AccountType.LIABILITY;
        case '3' -> AccountType.EQUITY;
        case '4' -> AccountType.REVENUE;
        case '5', '6', '7', '8', '9' -> AccountType.EXPENSE;
        default -> hashedAccountType(normalized);
      };
    }
    return hashedAccountType(normalized);
  }

  private static AccountType hashedAccountType(String normalizedAccountCode) {
    return switch (Math.floorMod(normalizedAccountCode.hashCode(), 5)) {
      case 0 -> AccountType.ASSET;
      case 1 -> AccountType.LIABILITY;
      case 2 -> AccountType.EQUITY;
      case 3 -> AccountType.REVENUE;
      default -> AccountType.EXPENSE;
    };
  }

  private static AccountTaxonomy syntheticAccountTaxonomy(AccountType accountType) {
    return switch (accountType) {
      case ASSET -> syntheticAccountTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET);
      case LIABILITY ->
          syntheticAccountTaxonomy(FinancialPositionLineClassification.CURRENT_LIABILITY);
      case EQUITY -> syntheticAccountTaxonomy(FinancialPositionLineClassification.OTHER_EQUITY);
      case REVENUE -> syntheticAccountTaxonomy(ProfitAndLossLineClassification.OPERATING_REVENUE);
      case EXPENSE -> syntheticAccountTaxonomy(ProfitAndLossLineClassification.OPERATING_EXPENSE);
    };
  }

  private static AccountTaxonomy syntheticAccountTaxonomy(
      FinancialPositionLineClassification classification) {
    return new AccountTaxonomy(
        AccountNodeKind.POSTABLE,
        Optional.empty(),
        Optional.of(Objects.requireNonNull(classification, "classification")),
        Optional.empty());
  }

  private static AccountTaxonomy syntheticAccountTaxonomy(
      ProfitAndLossLineClassification classification) {
    return new AccountTaxonomy(
        AccountNodeKind.POSTABLE,
        Optional.empty(),
        Optional.empty(),
        Optional.of(Objects.requireNonNull(classification, "classification")));
  }
}
