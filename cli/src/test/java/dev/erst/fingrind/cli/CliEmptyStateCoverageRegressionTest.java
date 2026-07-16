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
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Locks public empty-state and comparative rendering branches introduced by recent refactors. */
class CliEmptyStateCoverageRegressionTest extends CliFixtureSupport {
  @Test
  void renderAccountBalanceCsv_emitsItsTypedHeaderWithoutSyntheticRecords() {
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

    String csv = CliQueryOutputRenderer.renderAccountBalanceCsv(snapshot);
    assertTrue(csv.startsWith("family,accountCode,accountName"));
    assertEquals(1, csv.lines().count());
  }

  @Test
  void renderAccountPageCsv_emitsExplicitEmptyRow() {
    String csv =
        CliAccountPageOutputRenderer.renderCsv(accountPage(List.of(), 50, Optional.empty()));

    assertEquals(CliCsvExportFamilies.ACCOUNTS, csvValue(csv, 1, "exportFamily"));
    assertEquals("scope-empty", csvValue(csv, 1, "relationKind"));
    assertEquals(CliCsvExportFamilies.ACCOUNTS, csvValue(csv, 1, "recordKind"));
    assertEquals("No accounts matched the selected scope.", csvValue(csv, 1, "message"));
  }

  @Test
  void renderPostingRegisterCsv_emitsExplicitEmptyRow() {
    PostingPage page = postingPage(List.of(), 50, Optional.empty());

    String csv = CliPostingOutputRenderer.renderPostingRegisterCsv(page);

    assertEquals(CliCsvExportFamilies.POSTINGS, csvValue(csv, 1, "exportFamily"));
    assertEquals(CliCsvExportFamilies.POSTINGS, csvValue(csv, 1, "recordKind"));
    assertEquals("posting-page:scope-empty", csvValue(csv, 1, "rowId"));
    assertEquals("No postings matched the selected scope.", csvValue(csv, 1, "message"));
  }

  @Test
  void renderPeriodSummaryTextAndCsv_keepHumanEmptyGuidanceOutOfTheTypedTable() {
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

    String text = CliQueryOutputRenderer.renderPeriodSummaryText(report);
    String csv = CliQueryOutputRenderer.renderPeriodSummaryCsv(report);

    assertTrue(text.contains("No currency totals matched the selected scope."));
    assertTrue(text.contains("No account activity matched the selected scope."));
    assertTrue(csv.startsWith("family,recordScope,accountCode"));
    assertEquals(1, csv.lines().count());
  }

  @Test
  void renderTrialBalanceTextKeepsEmptyGuidanceWhileCsvStaysAHeaderOnlyTable() {
    TrialBalanceReport report =
        new TrialBalanceReport(
            bookIdentity(),
            Optional.of(LocalDate.parse("2026-04-30")),
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.of(null, LocalDate.parse("2025-04-30")),
            allPostingKinds(),
            List.of(),
            List.of(),
            true,
            List.of(),
            List.of(CurrencyBalance.ofTotals(money("EUR", "0.00"), money("EUR", "0.00"))),
            true);

    String text = CliQueryOutputRenderer.renderTrialBalanceText(report);
    String csv = CliQueryOutputRenderer.renderTrialBalanceCsv(report);

    assertTrue(text.contains("Outcome"));
    assertTrue(text.contains("No account balances matched the selected scope."));
    assertTrue(text.contains("Comparative Trial Balance"));
    assertTrue(csv.startsWith("family,reportPeriod,accountCode"));
    assertEquals(1, csv.lines().count());
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
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            allPostingKinds(),
            List.of(row),
            List.of(),
            true,
            List.of(row),
            List.of(),
            true);

    String text = CliQueryOutputRenderer.renderTrialBalanceText(report);

    assertFalse(text.contains("Current totals"));
    assertTrue(text.contains("Comparative Trial Balance"));
    assertFalse(text.contains("Comparative totals"));
    assertTrue(countOccurrences(text, "Cash") >= 2);
  }

  @Test
  void renderTrialBalanceText_rendersComparativeAccountsWhenComparativeRowsExist() {
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
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            allPostingKinds(),
            List.of(row),
            List.of(CurrencyBalance.ofTotals(money("EUR", "10.00"), money("EUR", "0.00"))),
            true,
            List.of(row),
            List.of(CurrencyBalance.ofTotals(money("EUR", "10.00"), money("EUR", "0.00"))),
            true);

    String text = CliQueryOutputRenderer.renderTrialBalanceText(report);

    assertTrue(text.contains("Comparative Trial Balance"));
    assertTrue(text.contains("Cash"));
    assertFalse(text.contains("No account balances matched the selected scope."));
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
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            allPostingKinds(),
            true,
            List.of(assetSection),
            List.of(assetSection, liabilitySection));

    String text = CliQueryOutputRenderer.renderFinancialPositionText(report);

    assertTrue(text.contains("Comparative Financial Position"));
    assertTrue(text.contains("Sections with data"));
    assertTrue(text.contains("Assets"));
    assertTrue(text.contains("Empty sections"));
    assertTrue(text.contains("Liabilities"));
  }

  @Test
  void renderFinancialPositionCsv_isHeaderOnlyWhenNoStatementRowsExist() {
    FinancialPositionReport report =
        new FinancialPositionReport(
            bookIdentity(),
            Optional.of(LocalDate.parse("2026-04-30")),
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            allPostingKinds(),
            true,
            List.of(),
            List.of());

    String csv = CliQueryOutputRenderer.renderFinancialPositionCsv(report);
    assertTrue(csv.startsWith("family,reportPeriod,sectionKind,lineCode"));
    assertEquals(1, csv.lines().count());
  }

  @Test
  void renderFinancialPositionCsv_omitsComparativeRowsWhenNoReferenceOrComparativeDataExist() {
    CurrencyBalance assetBalance =
        CurrencyBalance.ofTotals(money("EUR", "10.00"), money("EUR", "0.00"));
    FinancialPositionReport report =
        new FinancialPositionReport(
            bookIdentity(),
            Optional.of(LocalDate.parse("2026-04-30")),
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            true,
            List.of(
                new FinancialPositionSection(
                    AccountType.ASSET,
                    List.of(
                        new FinancialPositionRow(
                            "1000",
                            "Cash",
                            AccountType.ASSET,
                            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                            StatementLineKind.DECLARED_ACCOUNT,
                            assetBalance)),
                    List.of(assetBalance))),
            List.of());

    String csv = CliQueryOutputRenderer.renderFinancialPositionCsv(report);

    assertTrue(csv.contains("current"));
    assertFalse(csv.contains("comparative"));
  }

  @Test
  void renderIncomeStatementCsv_isHeaderOnlyWhenNoStatementRowsExist() {
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

    String csv = CliQueryOutputRenderer.renderIncomeStatementCsv(report);

    assertTrue(csv.startsWith("family,reportPeriod,sectionKind,lineCode"));
    assertEquals(1, csv.lines().count());
  }

  @Test
  void renderIncomeStatementTextAndCsv_omitComparativeSectionsWhenNoReferenceOrDataExist() {
    IncomeStatementSection expenseSection =
        new IncomeStatementSection(
            AccountType.EXPENSE,
            List.of(
                new dev.erst.fingrind.contract.bookkeeping.IncomeStatementRow(
                    "5100",
                    "Software",
                    AccountType.EXPENSE,
                    ProfitAndLossLineClassification.OPERATING_EXPENSE,
                    StatementLineKind.DECLARED_ACCOUNT,
                    CurrencyBalance.ofTotals(money("EUR", "12.00"), money("EUR", "0.00")))),
            List.of(CurrencyBalance.ofTotals(money("EUR", "12.00"), money("EUR", "0.00"))));
    IncomeStatementReport report =
        new IncomeStatementReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            standardOnly(),
            List.of(expenseSection),
            List.of(CurrencyBalance.ofTotals(money("EUR", "12.00"), money("EUR", "0.00"))),
            List.of(),
            List.of());

    String text = CliQueryOutputRenderer.renderIncomeStatementText(report);
    String csv = CliQueryOutputRenderer.renderIncomeStatementCsv(report);

    assertFalse(text.contains("Comparative Income Statement"));
    assertTrue(csv.contains("current"));
    assertFalse(csv.contains("comparative"));
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

    String text = CliQueryOutputRenderer.renderChangesInEquityText(report);

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

  private static String csvValue(String csv, int rowIndex, String headerName) {
    List<String> lines = csv.lines().toList();
    List<String> headers = CliCsvFormat.parseRow(lines.getFirst());
    List<String> row = CliCsvFormat.parseRow(lines.get(rowIndex));
    return row.get(headers.indexOf(headerName));
  }
}
