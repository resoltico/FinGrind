package dev.erst.fingrind.contract.reporting;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.ReportingPeriod;
import java.util.List;
import java.util.Objects;

/** Public statement of comprehensive income. */
public record ComprehensiveIncomeReport(
    BookIdentity bookIdentity,
    ReportingPeriod reportingPeriod,
    List<ComprehensiveIncomeRow> profitOrLossRows,
    List<ComprehensiveIncomeRow> otherComprehensiveIncomeRows,
    Money totalProfitOrLoss,
    Money totalOtherComprehensiveIncome,
    Money totalComprehensiveIncome) {
  /** Defensively copies one comprehensive-income report. */
  public ComprehensiveIncomeReport {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    profitOrLossRows = List.copyOf(Objects.requireNonNull(profitOrLossRows, "profitOrLossRows"));
    otherComprehensiveIncomeRows =
        List.copyOf(
            Objects.requireNonNull(otherComprehensiveIncomeRows, "otherComprehensiveIncomeRows"));
    Objects.requireNonNull(totalProfitOrLoss, "totalProfitOrLoss");
    Objects.requireNonNull(totalOtherComprehensiveIncome, "totalOtherComprehensiveIncome");
    Objects.requireNonNull(totalComprehensiveIncome, "totalComprehensiveIncome");
  }
}
