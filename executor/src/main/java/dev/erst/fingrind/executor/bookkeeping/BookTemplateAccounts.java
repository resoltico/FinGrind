package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookTemplateId;
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
      case OWNER_MANAGED_SERVICE_CASH -> ownerManagedServiceCash();
    };
  }

  private static List<AccountDeclaration> ownerManagedServiceCash() {
    return List.of(
        account(
            "cash",
            "Cash",
            AccountType.ASSET,
            AccountRole.ORDINARY,
            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
            Optional.empty()),
        account(
            "owner-capital",
            "Owner Capital",
            AccountType.EQUITY,
            AccountRole.ORDINARY,
            Optional.of(FinancialPositionLineClassification.EQUITY_CONTRIBUTION),
            Optional.empty()),
        account(
            "owner-draws",
            "Owner Draws",
            AccountType.EQUITY,
            AccountRole.ORDINARY,
            Optional.of(FinancialPositionLineClassification.EQUITY_WITHDRAWAL),
            Optional.empty()),
        account(
            "result-holding",
            "Result Holding",
            AccountType.EQUITY,
            AccountRole.ORDINARY,
            Optional.of(FinancialPositionLineClassification.RESULT_HOLDING),
            Optional.empty()),
        account(
            "service-revenue",
            "Service Revenue",
            AccountType.REVENUE,
            AccountRole.ORDINARY,
            Optional.empty(),
            Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE)),
        account(
            "operating-expense",
            "Operating Expense",
            AccountType.EXPENSE,
            AccountRole.ORDINARY,
            Optional.empty(),
            Optional.of(ProfitAndLossLineClassification.OPERATING_EXPENSE)));
  }

  private static AccountDeclaration account(
      String code,
      String name,
      AccountType accountType,
      AccountRole accountRole,
      Optional<FinancialPositionLineClassification> financialPositionLineClassification,
      Optional<ProfitAndLossLineClassification> profitAndLossLineClassification) {
    return new AccountDeclaration(
        new AccountCode(code),
        new AccountName(name),
        accountType,
        accountRole,
        new AccountTaxonomy(
            AccountNodeKind.POSTABLE,
            Optional.empty(),
            financialPositionLineClassification,
            profitAndLossLineClassification));
  }
}
