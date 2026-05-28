package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.StatementLineKind;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Locks public empty-state and comparative rendering branches introduced by recent refactors. */
class CliEmptyStateCoverageRegressionTest extends CliFixtureSupport {
  @Test
  void renderAccountBalanceCsv_emitsExplicitEmptyRow() {
    AccountBalanceSnapshot snapshot =
        new AccountBalanceSnapshot(
            bookIdentity(),
            declaredAccount(
                "1000",
                "Cash",
                AccountType.ASSET,
                NormalBalance.DEBIT,
                true,
                Instant.parse("2026-04-07T12:00:00Z")),
            Optional.of(LocalDate.parse("2026-04-01")),
            Optional.of(LocalDate.parse("2026-04-30")),
            allPostingKinds(),
            List.of());

    String csv = CliAccountBalanceOutputRenderer.renderCsv(snapshot);
    List<String> row = CliCsvFormat.parseRow(csv.lines().toList().get(1));

    assertEquals(CliCsvEmptyKinds.SCOPE_EMPTY, row.get(0));
    assertEquals("1000", row.get(1));
    assertEquals("Cash", row.get(2));
    assertEquals("ASSET", row.get(3));
    assertEquals("No balances matched the selected scope.", row.get(13));
  }

  @Test
  void renderAccountPageCsv_emitsExplicitEmptyRow() {
    String csv =
        CliAccountPageOutputRenderer.renderCsv(accountPage(List.of(), 50, Optional.empty()));
    List<String> row = CliCsvFormat.parseRow(csv.lines().toList().get(1));

    assertEquals(CliCsvEmptyKinds.SCOPE_EMPTY, row.get(0));
    assertEquals("No accounts matched the selected scope.", row.get(11));
  }

  @Test
  void renderPostingRegisterCsv_emitsExplicitEmptyRow() {
    PostingPage page = postingPage(List.of(), 50, Optional.empty());

    String csv = CliPostingOutputRenderer.renderPostingRegisterCsv(page);
    List<String> row = CliCsvFormat.parseRow(csv.lines().toList().get(1));

    assertEquals(CliCsvEmptyKinds.SCOPE_EMPTY, row.get(0));
    assertEquals("No postings matched the selected scope.", row.get(16));
  }

  @Test
  void renderPeriodSummaryTextAndCsv_emitExplicitEmptyCurrencyAndAccountSections() {
    PeriodSummaryReport report =
        new PeriodSummaryReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            allPostingKinds(),
            0,
            0,
            0,
            List.of(),
            List.of());

    String text = CliPeriodSummaryReportRenderer.renderText(report);
    String csv = CliPeriodSummaryReportRenderer.renderCsv(report);

    assertTrue(text.contains("No currency totals matched the selected scope."));
    assertTrue(text.contains("No account activity matched the selected scope."));
    assertTrue(
        csv.contains(
            "section-empty,currency,,,,,,,No currency totals matched the selected scope."));
    assertTrue(
        csv.contains(
            "section-empty,account,,,,,,,No account activity matched the selected scope."));
  }

  @Test
  void renderTrialBalanceTextAndCsv_coverEmptyCurrentAndComparativeRegisterBranches() {
    TrialBalanceReport report =
        new TrialBalanceReport(
            bookIdentity(),
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.of(null, LocalDate.parse("2025-04-30")),
            allPostingKinds(),
            List.of(),
            List.of(),
            true,
            List.of(),
            List.of(CurrencyBalance.ofTotals(money("EUR", "0.00"), money("EUR", "0.00"))),
            true);

    String text = CliTrialBalanceReportRenderer.renderText(report);
    String csv = CliTrialBalanceReportRenderer.renderCsv(report);

    assertTrue(text.contains("Outcome"));
    assertTrue(text.contains("No account balances matched the selected scope."));
    assertTrue(text.contains("Comparative Trial Balance"));
    assertTrue(csv.contains("current,report-empty,2026-04-30"));
    assertTrue(csv.contains("comparative,total,2025-04-30,true"));
    assertTrue(csv.contains("comparative,report-empty,2025-04-30"));
  }

  @Test
  void renderTrialBalanceText_rendersNoBalanceTablesWhenRowsDrivePresenceAlone() {
    TrialBalanceRow row =
        new TrialBalanceRow(
            declaredAccount(
                "1000",
                "Cash",
                AccountType.ASSET,
                NormalBalance.DEBIT,
                true,
                Instant.parse("2026-04-07T12:00:00Z")),
            CurrencyBalance.ofTotals(money("EUR", "10.00"), money("EUR", "0.00")));
    TrialBalanceReport report =
        new TrialBalanceReport(
            bookIdentity(),
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            allPostingKinds(),
            List.of(row),
            List.of(),
            true,
            List.of(row),
            List.of(),
            true);

    String text = CliTrialBalanceReportRenderer.renderText(report);

    assertTrue(text.contains("Current totals"));
    assertTrue(text.contains("Comparative Trial Balance"));
    assertTrue(countOccurrences(text, "No balances matched the selected scope.") >= 2);
  }

  @Test
  void reportSurfacePolicy_marksEmptyTrialBalanceAsEmpty() {
    TrialBalanceReport report =
        new TrialBalanceReport(
            bookIdentity(),
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            List.of(),
            List.of(),
            true,
            List.of(),
            List.of(),
            true);

    assertFalse(CliReportSurfacePolicy.hasCurrent(report));
    assertFalse(CliReportSurfacePolicy.hasComparative(report));
  }

  @Test
  void reportSurfacePolicy_marksTotalsOnlyTrialBalanceAsCurrent() {
    TrialBalanceReport report =
        new TrialBalanceReport(
            bookIdentity(),
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            List.of(),
            List.of(CurrencyBalance.ofTotals(money("EUR", "0.00"), money("EUR", "0.00"))),
            true,
            List.of(),
            List.of(),
            true);

    assertTrue(CliReportSurfacePolicy.hasCurrent(report));
  }

  @Test
  void renderFinancialPositionText_listsComparativeEmptySectionsAlongsideRenderableSections() {
    CurrencyBalance assetBalance =
        CurrencyBalance.ofTotals(money("EUR", "10.00"), money("EUR", "0.00"));
    FinancialPositionSection assetSection =
        new FinancialPositionSection(
            AccountType.ASSET,
            List.of(
                new FinancialPositionRow(
                    "1000",
                    "Cash",
                    AccountType.ASSET,
                    Optional.of(AccountRole.ORDINARY),
                    Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                    StatementLineKind.DECLARED_ACCOUNT,
                    assetBalance)),
            List.of(assetBalance));
    FinancialPositionSection liabilitySection =
        new FinancialPositionSection(AccountType.LIABILITY, List.of(), List.of());
    FinancialPositionReport report =
        new FinancialPositionReport(
            bookIdentity(),
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            allPostingKinds(),
            List.of(assetSection),
            List.of(assetSection, liabilitySection));

    String text = CliFinancialPositionReportRenderer.renderText(report);

    assertTrue(text.contains("Comparative Financial Position"));
    assertTrue(text.contains("Sections with data"));
    assertTrue(text.contains("Assets"));
    assertTrue(text.contains("Empty sections"));
    assertTrue(text.contains("Liabilities"));
  }

  @Test
  void renderFinancialPositionCsv_emitsGlobalEmptyRowsWhenNoSectionsExist() {
    FinancialPositionReport report =
        new FinancialPositionReport(
            bookIdentity(),
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            allPostingKinds(),
            List.of(),
            List.of());

    String csv = CliFinancialPositionReportRenderer.renderCsv(report);
    List<String> currentRow = CliCsvFormat.parseRow(csv.lines().toList().get(1));
    List<String> comparativeRow = CliCsvFormat.parseRow(csv.lines().toList().get(2));

    assertEquals("current", currentRow.get(0));
    assertEquals(CliCsvEmptyKinds.REPORT_EMPTY, currentRow.get(1));
    assertEquals("2026-04-30", currentRow.get(2));
    assertEquals("No financial position lines matched the selected scope.", currentRow.get(15));
    assertEquals("comparative", comparativeRow.get(0));
    assertEquals(CliCsvEmptyKinds.REPORT_EMPTY, comparativeRow.get(1));
    assertEquals("2025-04-30", comparativeRow.get(2));
    assertEquals("No financial position lines matched the selected scope.", comparativeRow.get(15));
  }

  @Test
  void renderIncomeStatementCsv_emitsGlobalEmptyRowsWhenSectionsAndTotalsAreAbsent() {
    IncomeStatementReport report =
        new IncomeStatementReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            standardOnly(),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    String csv = CliIncomeStatementReportRenderer.renderCsv(report);

    assertTrue(
        csv.contains(
            "current,report-empty,2026-04-01,2026-04-30,,,,,,,,EUR,,,,,No income statement lines matched the selected scope."));
    assertTrue(
        csv.contains(
            "comparative,report-empty,2025-04-01,2025-04-30,,,,,,,,EUR,,,,,No income statement lines matched the selected scope."));
  }

  @Test
  void renderChangesInEquityText_emitsCurrentOutcomeWhenNoRowsOrTotalsExist() {
    ChangesInEquityReport report =
        new ChangesInEquityReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            allPostingKinds(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    String text = CliChangesInEquityReportRenderer.renderText(report);

    assertTrue(text.contains("Outcome"));
    assertTrue(text.contains("No equity lines matched the selected scope."));
  }

  private static int countOccurrences(String text, String token) {
    int count = 0;
    int index = 0;
    while (true) {
      int foundIndex = text.indexOf(token, index);
      if (foundIndex < 0) {
        return count;
      }
      count++;
      index = foundIndex + token.length();
    }
  }
}
