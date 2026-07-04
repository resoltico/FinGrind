package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Exhaustive label coverage for public account and statement vocabulary helpers. */
class CliAccountStatementLabelsCoverageTest {
  @Test
  void displayFinancialPositionLabels_coverEveryDeclaredClassification() {
    for (FinancialPositionLineClassification classification :
        FinancialPositionLineClassification.values()) {
      assertFalse(
          CliAccountStatementLabels.displayFinancialPositionLineClassification(classification)
              .isBlank(),
          classification::wireValue);
      assertFalse(
          CliAccountStatementLabels.displayFinancialPositionLineClassification(
                  Optional.of(classification))
              .isBlank(),
          classification::wireValue);
    }
  }

  @Test
  void displayProfitAndLossLabels_coverEveryDeclaredClassification() {
    for (ProfitAndLossLineClassification classification :
        ProfitAndLossLineClassification.values()) {
      assertFalse(
          CliAccountStatementLabels.displayProfitAndLossLineClassification(classification)
              .isBlank(),
          classification::wireValue);
    }
  }
}
