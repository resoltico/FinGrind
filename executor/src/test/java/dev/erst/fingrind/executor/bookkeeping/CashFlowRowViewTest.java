package dev.erst.fingrind.executor.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct coverage for cash-flow row taxonomy validation across balance-sheet and nominal rows. */
class CashFlowRowViewTest {
  private static final CurrencyBalance MOVEMENT =
      CurrencyBalance.ofTotals(Money.parse("EUR", "1.00"), Money.parse("EUR", "0.00"));

  @Test
  void constructor_acceptsValidatedLiabilityRows() {
    CashFlowRowView row =
        new CashFlowRowView(
            "2000",
            "Accounts Payable",
            AccountType.LIABILITY,
            Optional.of(FinancialPositionLineClassification.CURRENT_LIABILITY),
            Optional.empty(),
            StatementLineKind.DECLARED_ACCOUNT,
            MOVEMENT);

    assertEquals(AccountType.LIABILITY, row.lineType());
  }

  @Test
  void constructor_rejectsBalanceSheetRowsWithoutFinancialPositionClassification() {
    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CashFlowRowView(
                    "1000",
                    "Cash",
                    AccountType.ASSET,
                    Optional.empty(),
                    Optional.empty(),
                    StatementLineKind.DECLARED_ACCOUNT,
                    MOVEMENT));

    assertEquals(
        "financialPositionLineClassification is required for balance-sheet cash-flow rows.",
        failure.getMessage());
  }

  @Test
  void constructor_rejectsBalanceSheetRowsWithProfitAndLossClassification() {
    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CashFlowRowView(
                    "1000",
                    "Cash",
                    AccountType.ASSET,
                    Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                    Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE),
                    StatementLineKind.DECLARED_ACCOUNT,
                    MOVEMENT));

    assertEquals(
        "profitAndLossLineClassification must be absent for balance-sheet cash-flow rows.",
        failure.getMessage());
  }

  @Test
  void constructor_rejectsNominalRowsWithFinancialPositionClassification() {
    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CashFlowRowView(
                    "4000",
                    "Sales",
                    AccountType.REVENUE,
                    Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                    Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE),
                    StatementLineKind.DECLARED_ACCOUNT,
                    MOVEMENT));

    assertEquals(
        "financialPositionLineClassification must be absent for nominal cash-flow rows.",
        failure.getMessage());
  }

  @Test
  void constructor_rejectsNominalRowsWithoutProfitAndLossClassification() {
    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CashFlowRowView(
                    "5000",
                    "Supplies",
                    AccountType.EXPENSE,
                    Optional.empty(),
                    Optional.empty(),
                    StatementLineKind.DECLARED_ACCOUNT,
                    MOVEMENT));

    assertEquals(
        "profitAndLossLineClassification is required for nominal cash-flow rows.",
        failure.getMessage());
  }
}
