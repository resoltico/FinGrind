package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.core.EffectiveDateRange;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Locks the empty-ledger CSV summary contract to the new rectangular row model. */
class CliReportOutputRendererCoverageTest extends CliFixtureSupport {
  @Test
  void renderAccountLedgerCsv_emitsRectangularSummaryRowsWhenLedgerHasNoEntries() {
    String csv = CliReportOutputRenderer.renderAccountLedgerCsv(sampleAccountLedgerReport());
    java.util.List<String> lines = csv.lines().toList();

    assertEquals(2, lines.size());
    assertTrue(lines.get(0).startsWith("rowKind,accountCode,accountName,accountType"));
    assertTrue(
        lines
            .get(1)
            .contains(
                "summary,1000,Cash,ASSET,ORDINARY,DEBIT,true,2026-04-01,2026-04-30,EUR,0.00,0.00,0.00,ZERO,10.00,0.00,10.00,DEBIT"));
  }

  @Test
  void renderAccountLedgerCsv_fallsBackToBookCurrencyWhenNoBalanceBucketsCarryCurrency() {
    AccountLedgerReport emptyCurrencyReport =
        new AccountLedgerReport(
            bookIdentity(),
            declaredAccount("1000", "Cash", dev.erst.fingrind.core.NormalBalance.DEBIT),
            EffectiveDateRange.of(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
            allPostingKinds(),
            List.of(),
            List.of(),
            List.of());

    String csv = CliReportOutputRenderer.renderAccountLedgerCsv(emptyCurrencyReport);
    java.util.List<String> lines = csv.lines().toList();

    assertEquals(2, lines.size());
    assertTrue(lines.get(1).contains(",EUR,0.00,0.00,0.00,ZERO,0.00,0.00,0.00,ZERO,"));
  }
}
