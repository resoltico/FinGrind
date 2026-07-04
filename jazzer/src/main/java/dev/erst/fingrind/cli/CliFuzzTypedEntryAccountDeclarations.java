package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.SettlementAdjunct;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Typed-entry synthetic account declarations shared by Jazzer workflow fixtures. */
final class CliFuzzTypedEntryAccountDeclarations {
  private CliFuzzTypedEntryAccountDeclarations() {}

  static List<DeclareAccountCommand> declare(BookkeepingEntry entry) {
    if (entry instanceof BookkeepingEntry.SaleSettled
        || entry instanceof BookkeepingEntry.SaleOnCredit
        || entry instanceof BookkeepingEntry.ExpenseSettled
        || entry instanceof BookkeepingEntry.ExpenseOnCredit) {
      return salesAndExpenseDeclarations(entry);
    }
    if (entry instanceof BookkeepingEntry.Receipt
        || entry instanceof BookkeepingEntry.Payment
        || entry instanceof BookkeepingEntry.OwnerContribution
        || entry instanceof BookkeepingEntry.OwnerWithdrawal) {
      return settlementAndEquityDeclarations(entry);
    }
    throw new IllegalArgumentException(
        "typed entry declarations do not support: " + entry.getClass().getSimpleName());
  }

  static List<DeclareAccountCommand> salesAndExpenseDeclarations(BookkeepingEntry entry) {
    return switch (entry) {
      case BookkeepingEntry.SaleSettled sale ->
          declarations(
              List.of(
                  seed(
                      sale.cashAccountCode(),
                      AccountType.ASSET,
                      CliFuzzSyntheticAccountDoctrine.financialPositionTaxonomy(
                          FinancialPositionLineClassification.CURRENT_ASSET)),
                  seed(
                      sale.revenueAccountCode(),
                      AccountType.REVENUE,
                      CliFuzzSyntheticAccountDoctrine.profitAndLossTaxonomy(
                          ProfitAndLossLineClassification.OPERATING_REVENUE))),
              taxSeed(
                  sale.appliedTax(),
                  AccountType.LIABILITY,
                  CliFuzzSyntheticAccountDoctrine.financialPositionTaxonomy(
                      FinancialPositionLineClassification.CURRENT_LIABILITY)));
      case BookkeepingEntry.SaleOnCredit sale ->
          declarations(
              List.of(
                  seed(
                      sale.receivableAccountCode(),
                      AccountType.ASSET,
                      CliFuzzSyntheticAccountDoctrine.financialPositionTaxonomy(
                          FinancialPositionLineClassification.TRADE_RECEIVABLE)),
                  seed(
                      sale.revenueAccountCode(),
                      AccountType.REVENUE,
                      CliFuzzSyntheticAccountDoctrine.profitAndLossTaxonomy(
                          ProfitAndLossLineClassification.OPERATING_REVENUE))),
              taxSeed(
                  sale.appliedTax(),
                  AccountType.LIABILITY,
                  CliFuzzSyntheticAccountDoctrine.financialPositionTaxonomy(
                      FinancialPositionLineClassification.CURRENT_LIABILITY)));
      case BookkeepingEntry.ExpenseSettled expense ->
          declarations(
              List.of(
                  seed(
                      expense.expenseAccountCode(),
                      AccountType.EXPENSE,
                      CliFuzzSyntheticAccountDoctrine.profitAndLossTaxonomy(
                          ProfitAndLossLineClassification.OPERATING_EXPENSE)),
                  seed(
                      expense.cashAccountCode(),
                      AccountType.ASSET,
                      CliFuzzSyntheticAccountDoctrine.financialPositionTaxonomy(
                          FinancialPositionLineClassification.CURRENT_ASSET))),
              taxSeed(
                  expense.appliedTax(),
                  AccountType.ASSET,
                  CliFuzzSyntheticAccountDoctrine.financialPositionTaxonomy(
                      FinancialPositionLineClassification.CURRENT_ASSET)));
      case BookkeepingEntry.ExpenseOnCredit expense ->
          declarations(
              List.of(
                  seed(
                      expense.expenseAccountCode(),
                      AccountType.EXPENSE,
                      CliFuzzSyntheticAccountDoctrine.profitAndLossTaxonomy(
                          ProfitAndLossLineClassification.OPERATING_EXPENSE)),
                  seed(
                      expense.payableAccountCode(),
                      AccountType.LIABILITY,
                      CliFuzzSyntheticAccountDoctrine.financialPositionTaxonomy(
                          FinancialPositionLineClassification.TRADE_PAYABLE))),
              taxSeed(
                  expense.appliedTax(),
                  AccountType.ASSET,
                  CliFuzzSyntheticAccountDoctrine.financialPositionTaxonomy(
                      FinancialPositionLineClassification.CURRENT_ASSET)));
      default ->
          throw new IllegalArgumentException(
              "sales-and-expenses declarations do not support: "
                  + entry.getClass().getSimpleName());
    };
  }

  static List<DeclareAccountCommand> settlementAndEquityDeclarations(BookkeepingEntry entry) {
    return switch (entry) {
      case BookkeepingEntry.Receipt receipt ->
          declarations(
              List.of(
                  seed(
                      receipt.cashAccountCode(),
                      AccountType.ASSET,
                      CliFuzzSyntheticAccountDoctrine.financialPositionTaxonomy(
                          FinancialPositionLineClassification.CURRENT_ASSET)),
                  seed(
                      receipt.receivableAccountCode(),
                      AccountType.ASSET,
                      CliFuzzSyntheticAccountDoctrine.financialPositionTaxonomy(
                          FinancialPositionLineClassification.TRADE_RECEIVABLE))),
              settlementSeed(receipt.settlementAdjunct()));
      case BookkeepingEntry.Payment payment ->
          declarations(
              List.of(
                  seed(
                      payment.payableAccountCode(),
                      AccountType.LIABILITY,
                      CliFuzzSyntheticAccountDoctrine.financialPositionTaxonomy(
                          FinancialPositionLineClassification.TRADE_PAYABLE)),
                  seed(
                      payment.cashAccountCode(),
                      AccountType.ASSET,
                      CliFuzzSyntheticAccountDoctrine.financialPositionTaxonomy(
                          FinancialPositionLineClassification.CURRENT_ASSET))),
              settlementSeed(payment.settlementAdjunct()));
      case BookkeepingEntry.OwnerContribution ownerContribution ->
          declarations(
              List.of(
                  seed(
                      ownerContribution.cashAccountCode(),
                      AccountType.ASSET,
                      CliFuzzSyntheticAccountDoctrine.financialPositionTaxonomy(
                          FinancialPositionLineClassification.CURRENT_ASSET)),
                  seed(
                      ownerContribution.equityAccountCode(),
                      AccountType.EQUITY,
                      CliFuzzSyntheticAccountDoctrine.financialPositionTaxonomy(
                          FinancialPositionLineClassification.EQUITY_CONTRIBUTION))),
              null);
      case BookkeepingEntry.OwnerWithdrawal ownerWithdrawal ->
          declarations(
              List.of(
                  seed(
                      ownerWithdrawal.equityAccountCode(),
                      AccountType.EQUITY,
                      CliFuzzSyntheticAccountDoctrine.financialPositionTaxonomy(
                          FinancialPositionLineClassification.EQUITY_WITHDRAWAL)),
                  seed(
                      ownerWithdrawal.cashAccountCode(),
                      AccountType.ASSET,
                      CliFuzzSyntheticAccountDoctrine.financialPositionTaxonomy(
                          FinancialPositionLineClassification.CURRENT_ASSET))),
              null);
      default ->
          throw new IllegalArgumentException(
              "settlement-and-equity declarations do not support: "
                  + entry.getClass().getSimpleName());
    };
  }

  private static List<DeclareAccountCommand> declarations(
      List<AccountDeclarationSeed> requiredSeeds, @Nullable AccountDeclarationSeed optionalSeed) {
    List<AccountDeclarationSeed> seeds =
        optionalSeed == null
            ? requiredSeeds
            : java.util.stream.Stream.concat(
                    requiredSeeds.stream(), java.util.stream.Stream.of(optionalSeed))
                .toList();
    return seeds.stream().map(CliFuzzTypedEntryAccountDeclarations::declare).toList();
  }

  private static DeclareAccountCommand declare(AccountDeclarationSeed seed) {
    return new DeclareAccountCommand(
        seed.accountCode(),
        new AccountName("Synthetic " + seed.accountCode().value()),
        seed.accountType(),
        seed.accountTaxonomy());
  }

  private static AccountDeclarationSeed seed(
      AccountCode accountCode, AccountType accountType, AccountTaxonomy accountTaxonomy) {
    return new AccountDeclarationSeed(accountCode, accountType, accountTaxonomy);
  }

  private static @Nullable AccountDeclarationSeed taxSeed(
      @Nullable AppliedTax appliedTax, AccountType accountType, AccountTaxonomy accountTaxonomy) {
    if (appliedTax == null || appliedTax.taxAccountCode() == null) {
      return null;
    }
    return seed(appliedTax.taxAccountCode(), accountType, accountTaxonomy);
  }

  private static @Nullable AccountDeclarationSeed settlementSeed(
      @Nullable SettlementAdjunct settlementAdjunct) {
    if (settlementAdjunct == null) {
      return null;
    }
    return seed(
        settlementAdjunct.accountCode(),
        AccountType.EXPENSE,
        CliFuzzSyntheticAccountDoctrine.profitAndLossTaxonomy(
            ProfitAndLossLineClassification.SETTLEMENT_FEE));
  }

  private record AccountDeclarationSeed(
      AccountCode accountCode, AccountType accountType, AccountTaxonomy accountTaxonomy) {}
}
