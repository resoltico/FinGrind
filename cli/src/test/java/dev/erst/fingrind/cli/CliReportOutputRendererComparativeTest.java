package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.StatementLineKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Focused tests for comparative statement rendering and report-surface policy. */
class CliReportOutputRendererComparativeTest extends CliWorkflowFixtureSupport {
  @Test
  void reportRenderers_andBalanceFormatter_renderComparativeBranches() {
    var cashAccount = declaredAccount("1000", "Cash", NormalBalance.DEBIT);
    CurrencyBalance eurDebitBalance = eurDebitBalance();
    TrialBalanceRow currentRow = new TrialBalanceRow(cashAccount, eurDebitBalance);
    TrialBalanceRow comparativeRow =
        new TrialBalanceRow(
            cashAccount,
            CliResponseWriterTestSupport.currencyBalance(
                "EUR", "7.00", "1.00", "6.00", BalanceSide.DEBIT));
    TrialBalanceReport comparativeTrialBalance =
        trialBalanceReport(
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.of(null, LocalDate.parse("2025-04-30")),
            allPostingKinds(),
            List.of(currentRow),
            List.of(comparativeRow));
    IncomeStatementReport comparativeTotalsOnlyIncomeStatement =
        new IncomeStatementReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            standardOnly(),
            List.of(),
            List.of(),
            List.of(),
            List.of(eurDebitBalance));
    ChangesInEquityReport partialComparativeEquityReport =
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
            List.of(eurDebitBalance),
            List.of());

    String trialBalanceText =
        CliQueryOutputRenderer.renderTrialBalanceText(comparativeTrialBalance);
    String trialBalanceCsv = CliQueryOutputRenderer.renderTrialBalanceCsv(comparativeTrialBalance);
    String incomeStatementText =
        CliQueryOutputRenderer.renderIncomeStatementText(comparativeTotalsOnlyIncomeStatement);
    String changesInEquityText =
        CliQueryOutputRenderer.renderChangesInEquityText(partialComparativeEquityReport);

    assertEquals(
        "EUR 6.00 DEBIT", CliQueryRowFormatAccess.displayBalance(comparativeRow.balance()));
    assertTrue(trialBalanceText.contains("Comparative Trial Balance"));
    assertTrue(trialBalanceCsv.contains("comparative"));
    assertTrue(incomeStatementText.contains("Comparative Income Statement"));
    assertTrue(incomeStatementText.contains("Comparative Net Income Totals"));
    assertTrue(changesInEquityText.contains("Comparative Changes In Equity"));
    assertTrue(changesInEquityText.contains("Comparative movement totals"));
  }

  @Test
  void renderChangesInEquityText_keepsComparativeSectionWhenComparativeReferenceIsBounded() {
    ChangesInEquityReport nonComparativeReport =
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

    String rendered = CliQueryOutputRenderer.renderChangesInEquityText(nonComparativeReport);

    assertTrue(rendered.contains("Changes In Equity"));
    assertTrue(rendered.contains("Outcome"));
    assertTrue(rendered.contains("No equity lines matched the selected scope."));
    assertTrue(rendered.contains("Comparative Changes In Equity"));
    assertTrue(rendered.contains("Comparative reference"));
  }

  @Test
  void renderChangesInEquityText_rendersComparativeSectionWhenOnlyClosingTotalsExist() {
    CurrencyBalance comparativeClosing =
        CliResponseWriterTestSupport.currencyBalance(
            "EUR", "0.00", "8.00", "8.00", BalanceSide.CREDIT);
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
            List.of(comparativeClosing));

    String rendered = CliQueryOutputRenderer.renderChangesInEquityText(report);

    assertTrue(rendered.contains("Comparative Changes In Equity"));
    assertTrue(rendered.contains("Comparative closing totals"));
  }

  @Test
  void renderChangesInEquityText_rendersComparativeSectionWhenOnlyOpeningTotalsExist() {
    CurrencyBalance comparativeOpening =
        CliResponseWriterTestSupport.currencyBalance(
            "EUR", "4.00", "0.00", "4.00", BalanceSide.DEBIT);
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
            List.of(comparativeOpening),
            List.of(),
            List.of());

    String rendered = CliQueryOutputRenderer.renderChangesInEquityText(report);

    assertTrue(rendered.contains("Comparative Changes In Equity"));
    assertTrue(rendered.contains("Comparative opening totals"));
  }

  @Test
  void renderChangesInEquityText_rendersComparativeSectionWhenOnlyMovementTotalsExist() {
    CurrencyBalance comparativeMovement =
        CliResponseWriterTestSupport.currencyBalance(
            "EUR", "0.00", "5.00", "5.00", BalanceSide.CREDIT);
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
            List.of(comparativeMovement),
            List.of());

    String rendered = CliQueryOutputRenderer.renderChangesInEquityText(report);

    assertTrue(rendered.contains("Comparative Changes In Equity"));
    assertTrue(rendered.contains("Comparative movement totals"));
  }

  @Test
  void renderChangesInEquityText_rendersComparativeRowsWithoutSyntheticTotalsBlock() {
    CurrencyBalance zeroBalance =
        CliResponseWriterTestSupport.currencyBalance(
            "EUR", "0.00", "0.00", "0.00", BalanceSide.ZERO);
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
            List.of(
                new dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow(
                    "equity-rollforward",
                    "Equity rollforward",
                    Optional.of(AccountType.EQUITY),
                    Optional.of(FinancialPositionLineClassification.OTHER_EQUITY),
                    StatementLineKind.DECLARED_ACCOUNT,
                    zeroBalance,
                    zeroBalance,
                    zeroBalance)),
            List.of(),
            List.of(),
            List.of());

    String rendered = CliQueryOutputRenderer.renderChangesInEquityText(report);

    assertTrue(rendered.contains("Comparative Changes In Equity"));
    assertTrue(rendered.contains("Equity rollforward"));
    assertFalse(rendered.contains("Comparative opening totals"));
    assertFalse(rendered.contains("Comparative movement totals"));
    assertFalse(rendered.contains("Comparative closing totals"));
  }

  @Test
  void renderChangesInEquityText_rendersHeaderSummaryWhenOnlyCurrentTotalsExist() {
    CurrencyBalance openingBalance =
        CliResponseWriterTestSupport.currencyBalance(
            "EUR", "4.00", "0.00", "4.00", BalanceSide.DEBIT);
    ChangesInEquityReport report =
        new ChangesInEquityReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            List.of(),
            List.of(openingBalance),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    String rendered = CliQueryOutputRenderer.renderChangesInEquityText(report);

    assertTrue(rendered.contains("Changes In Equity"));
    assertTrue(rendered.contains("Opening totals"));
    assertFalse(rendered.contains("Comparative Changes In Equity"));
  }

  @Test
  void cashFlowTextRenderer_omitsOutcomeWhenTotalsExistWithoutRenderableSections() {
    CurrencyBalance balance = eurDebitBalance();
    CashFlowStatementReport totalsOnlyReport =
        new CashFlowStatementReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            standardOnly(),
            List.of(),
            List.of(),
            List.of(balance),
            List.of(),
            List.of(),
            List.of(),
            List.of(balance),
            List.of());

    String rendered = CliQueryOutputRenderer.renderCashFlowStatementText(totalsOnlyReport);

    assertTrue(rendered.contains("Cash Receipts And Payments"));
    assertTrue(rendered.contains("Comparative Cash Receipts And Payments"));
    assertFalse(rendered.contains("Outcome"));
    assertFalse(rendered.contains("Sections with data"));
    assertFalse(rendered.contains("Empty sections"));
  }

  @Test
  void cashFlowTextRenderer_reportsComparativeNoMatchesWhenOnlyReferenceExists() {
    CashFlowStatementReport comparativeReferenceOnlyReport =
        new CashFlowStatementReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            standardOnly(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    String rendered =
        CliQueryOutputRenderer.renderCashFlowStatementText(comparativeReferenceOnlyReport);

    assertTrue(rendered.contains("Comparative reference"));
    assertTrue(rendered.contains("2025-04-01"));
    assertTrue(rendered.contains("2025-04-30"));
    assertTrue(rendered.contains("Outcome"));
    assertTrue(rendered.contains("No cash-flow lines matched the selected scope."));
  }
}
