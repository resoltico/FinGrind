package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.PostEntryAccountDistinctness.distinct;
import static dev.erst.fingrind.executor.PostEntryOperatingAccountExpectations.cash;
import static dev.erst.fingrind.executor.PostEntryOperatingAccountExpectations.expense;
import static dev.erst.fingrind.executor.PostEntryOperatingAccountExpectations.receivable;
import static dev.erst.fingrind.executor.PostEntryOperatingAccountExpectations.revenue;

import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeBookkeepingEntryVariants;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.List;
import java.util.Map;

/** Role-account admission for the realized-foreign-exchange typed write vocabulary. */
final class PostEntryRealizedForeignExchangeRoleAccountSemantics {
  private PostEntryRealizedForeignExchangeRoleAccountSemantics() {}

  static void validate(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      RealizedForeignExchangeBookkeepingEntryVariants entry,
      String selectorField,
      String selectorValue) {
    switch (entry) {
      case RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable receivable ->
          PostEntryRoleAccountValidationSupport.validate(
              violations,
              accounts,
              selectorField,
              selectorValue,
              List.of(
                  distinct(
                      receivable(receivable.receivableAccountCode(), "receivableAccountCode"),
                      revenue(receivable.revenueAccountCode(), "revenueAccountCode")),
                  distinct(
                      receivable(receivable.receivableAccountCode(), "receivableAccountCode"),
                      revenue(receivable.realizedGainAccountCode(), "realizedGainAccountCode")),
                  distinct(
                      receivable(receivable.receivableAccountCode(), "receivableAccountCode"),
                      expense(receivable.realizedLossAccountCode(), "realizedLossAccountCode"))),
              receivable(receivable.receivableAccountCode(), "receivableAccountCode"),
              revenue(receivable.revenueAccountCode(), "revenueAccountCode"),
              revenue(receivable.realizedGainAccountCode(), "realizedGainAccountCode"),
              expense(receivable.realizedLossAccountCode(), "realizedLossAccountCode"));
      case RealizedForeignExchangeBookkeepingEntryVariants.Settlement settlement -> {
        if (settlement.resolvedSettlement() != null) {
          PostEntryAccountExpectation gainOrLoss =
              settlement.resolvedSettlement().gain()
                  ? revenue(
                      settlement.resolvedSettlement().gainOrLossAccountCode(),
                      "realizedGainAccountCode")
                  : expense(
                      settlement.resolvedSettlement().gainOrLossAccountCode(),
                      "realizedLossAccountCode");
          PostEntryRoleAccountValidationSupport.validate(
              violations,
              accounts,
              selectorField,
              selectorValue,
              List.of(
                  distinct(cash(settlement.cashAccountCode(), "cashAccountCode"), gainOrLoss),
                  distinct(
                      cash(settlement.cashAccountCode(), "cashAccountCode"),
                      receivable(
                          settlement.resolvedSettlement().receivableAccountCode(),
                          "receivableAccountCode")),
                  distinct(
                      gainOrLoss,
                      receivable(
                          settlement.resolvedSettlement().receivableAccountCode(),
                          "receivableAccountCode"))),
              cash(settlement.cashAccountCode(), "cashAccountCode"),
              receivable(
                  settlement.resolvedSettlement().receivableAccountCode(), "receivableAccountCode"),
              gainOrLoss);
        }
      }
    }
  }
}
