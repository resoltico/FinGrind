package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import java.util.Optional;

/** Constructs a postable built-in account declaration from its reporting classifications. */
final class BookTemplateSeedAccountFactory {
  private BookTemplateSeedAccountFactory() {}

  static AccountDeclaration postable(
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
