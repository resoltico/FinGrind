package dev.erst.fingrind.executor.bookkeeping.read;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.executor.InMemoryBookSession;
import dev.erst.fingrind.executor.bookkeeping.AcceptedInterimResultTargetSelection;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.InterimResultTargetSelection;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Direct coverage for read-side result-holding helpers. */
class BookkeepingResultHoldingReadSupportTest {
  @Test
  void resolvesConfiguredResultHoldingSelectionAndRequiredClassification() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.openBook(Instant.parse("2026-04-07T10:15:30Z"), bookIdentity(), List.of());
      assertInstanceOf(
          AccountDeclarationOutcome.Declared.class,
          bookSession.declareAccount(
              new AccountCode("3200"),
              new AccountName("Result Holding"),
              AccountType.EQUITY,
              financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING),
              Instant.parse("2026-04-07T10:15:30Z")));

      InterimResultTargetSelection selection =
          BookkeepingResultHoldingReadSupport.resultHoldingSelection(bookIdentity(), bookSession);

      assertEquals(
          new AccountCode("3200"),
          assertInstanceOf(AcceptedInterimResultTargetSelection.class, selection)
              .account()
              .accountCode());
      assertEquals(
          FinancialPositionLineClassification.RESULT_HOLDING,
          BookkeepingResultHoldingReadSupport.requiredResultHoldingClassification(bookIdentity()));
    }
  }
}
