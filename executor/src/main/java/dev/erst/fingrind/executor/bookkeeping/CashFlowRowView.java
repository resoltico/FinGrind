package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Local bookkeeping line inside one cash-flow statement section. */
public record CashFlowRowView(
    String lineCode,
    String lineName,
    AccountType lineType,
    Optional<FinancialPositionLineClassification> financialPositionLineClassification,
    Optional<ProfitAndLossLineClassification> profitAndLossLineClassification,
    StatementLineKind lineKind,
    CurrencyBalance movement) {
  public CashFlowRowView {
    Objects.requireNonNull(lineCode, "lineCode");
    Objects.requireNonNull(lineName, "lineName");
    Objects.requireNonNull(lineType, "lineType");
    Objects.requireNonNull(
        financialPositionLineClassification, "financialPositionLineClassification");
    Objects.requireNonNull(profitAndLossLineClassification, "profitAndLossLineClassification");
    Objects.requireNonNull(lineKind, "lineKind");
    Objects.requireNonNull(movement, "movement");
    validateClassifications(
        lineType,
        financialPositionLineClassification.orElse(null),
        profitAndLossLineClassification.orElse(null));
  }

  private static void validateClassifications(
      AccountType lineType,
      @Nullable FinancialPositionLineClassification financialPositionLineClassification,
      @Nullable ProfitAndLossLineClassification profitAndLossLineClassification) {
    if (lineType == AccountType.REVENUE || lineType == AccountType.EXPENSE) {
      validateNominalClassifications(
          financialPositionLineClassification, profitAndLossLineClassification);
      return;
    }
    validateBalanceSheetClassifications(
        financialPositionLineClassification, profitAndLossLineClassification);
  }

  private static void validateBalanceSheetClassifications(
      @Nullable FinancialPositionLineClassification financialPositionLineClassification,
      @Nullable ProfitAndLossLineClassification profitAndLossLineClassification) {
    if (financialPositionLineClassification == null) {
      throw new IllegalArgumentException(
          "financialPositionLineClassification is required for balance-sheet cash-flow rows.");
    }
    if (profitAndLossLineClassification != null) {
      throw new IllegalArgumentException(
          "profitAndLossLineClassification must be absent for balance-sheet cash-flow rows.");
    }
  }

  private static void validateNominalClassifications(
      @Nullable FinancialPositionLineClassification financialPositionLineClassification,
      @Nullable ProfitAndLossLineClassification profitAndLossLineClassification) {
    if (financialPositionLineClassification != null) {
      throw new IllegalArgumentException(
          "financialPositionLineClassification must be absent for nominal cash-flow rows.");
    }
    if (profitAndLossLineClassification == null) {
      throw new IllegalArgumentException(
          "profitAndLossLineClassification is required for nominal cash-flow rows.");
    }
  }
}
