package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Canonical starter chart declarations for built-in book templates. */
public final class BookTemplateAccounts {
  private BookTemplateAccounts() {}

  /** Returns the canonical starter chart for the supplied built-in template. */
  public static List<AccountDeclaration> declarations(BookTemplateId bookTemplateId) {
    Objects.requireNonNull(bookTemplateId, "bookTemplateId");
    return switch (bookTemplateId) {
      case OWNER_MANAGED_SERVICE -> ownerManagedService();
    };
  }

  private static List<AccountDeclaration> ownerManagedService() {
    return List.of(
        account(
            "cash",
            "Cash",
            AccountType.ASSET,
            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
            Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT),
            Optional.empty()),
        account(
            "owner-capital",
            "Owner Capital",
            AccountType.EQUITY,
            Optional.of(FinancialPositionLineClassification.EQUITY_CONTRIBUTION),
            Optional.empty(),
            Optional.empty()),
        account(
            "owner-draws",
            "Owner Draws",
            AccountType.EQUITY,
            Optional.of(FinancialPositionLineClassification.EQUITY_WITHDRAWAL),
            Optional.empty(),
            Optional.empty()),
        account(
            "result-holding",
            "Result Holding",
            AccountType.EQUITY,
            Optional.of(FinancialPositionLineClassification.RESULT_HOLDING),
            Optional.empty(),
            Optional.empty()),
        account(
            "retained-accumulated",
            "Retained Accumulated",
            AccountType.EQUITY,
            Optional.of(FinancialPositionLineClassification.RETAINED_ACCUMULATED),
            Optional.empty(),
            Optional.empty()),
        account(
            "service-revenue",
            "Service Revenue",
            AccountType.REVENUE,
            Optional.empty(),
            Optional.empty(),
            Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE)),
        account(
            "operating-expense",
            "Operating Expense",
            AccountType.EXPENSE,
            Optional.empty(),
            Optional.empty(),
            Optional.of(ProfitAndLossLineClassification.OPERATING_EXPENSE)));
  }

  private static AccountDeclaration account(
      String code,
      String name,
      AccountType accountType,
      Optional<FinancialPositionLineClassification> financialPositionLineClassification,
      Optional<CashFlowAssetClassification> cashFlowAssetClassification,
      Optional<ProfitAndLossLineClassification> profitAndLossLineClassification) {
    return new AccountDeclaration(
        new AccountCode(code),
        new AccountName(name),
        accountType,
        new AccountTaxonomy(
            AccountNodeKind.POSTABLE,
            Optional.empty(),
            financialPositionLineClassification,
            profitAndLossLineClassification,
            cashFlowAssetClassification));
  }
}
