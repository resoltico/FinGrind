package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterResult;
import org.junit.jupiter.api.Test;

/** Preserves the deterministic query-rejection exit contract for every lifecycle register. */
class CliOwnedLifecycleReportExitCodesTest {
  @Test
  void exitCodeFor_lifecycleRegisterRejections_isTwo() {
    assertEquals(
        2,
        CliReportExitCodes.exitCodeFor(
            new FixedAssetRegisterResult.Rejected(new BookQueryRejection.BookNotInitialized())));
    assertEquals(
        2,
        CliReportExitCodes.exitCodeFor(
            new FinancingRegisterResult.Rejected(new BookQueryRejection.BookNotInitialized())));
    assertEquals(
        2,
        CliReportExitCodes.exitCodeFor(
            new RealizedForeignExchangeRegisterResult.Rejected(
                new BookQueryRejection.BookNotInitialized())));
  }
}
