package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryReportResult;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementResult;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies the common success-or-rejection grammar exposed by book-query reports. */
class BookQueryReportResultTest {
  @Test
  void rejectedReportsExposeOneCommonOutcomeShape() {
    BookQueryRejection rejection = new BookQueryRejection.BookNotInitialized();
    List<BookQueryReportResult<?>> results =
        List.of(
            new AccountBalanceResult.Rejected(rejection),
            new TrialBalanceResult.Rejected(rejection),
            new AccountLedgerResult.Rejected(rejection),
            new PeriodSummaryResult.Rejected(rejection),
            new FinancialPositionResult.Rejected(rejection),
            new IncomeStatementResult.Rejected(rejection),
            new CashFlowStatementResult.Rejected(rejection),
            new ChangesInEquityResult.Rejected(rejection),
            new InventoryValuationResult.Rejected(rejection));

    results.forEach(
        result -> {
          assertNull(result.reported());
          assertSame(rejection, result.rejection());
        });
  }
}
