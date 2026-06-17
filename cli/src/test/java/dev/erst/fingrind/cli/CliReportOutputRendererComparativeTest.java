package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementRow;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Focused tests for comparative statement rendering and report-surface policy. */
class CliReportOutputRendererComparativeTest extends FinGrindCliTestSupport {
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
        CliReportOutputRenderer.renderTrialBalanceText(comparativeTrialBalance);
    String trialBalanceCsv = CliReportOutputRenderer.renderTrialBalanceCsv(comparativeTrialBalance);
    String incomeStatementText =
        CliReportOutputRenderer.renderIncomeStatementText(comparativeTotalsOnlyIncomeStatement);
    String changesInEquityText =
        CliReportOutputRenderer.renderChangesInEquityText(partialComparativeEquityReport);

    assertEquals(
        "EUR 6.00 DEBIT", CliQueryRowFormatAccess.displayBalance(comparativeRow.balance()));
    assertTrue(trialBalanceText.contains("Comparative Trial Balance"));
    assertTrue(trialBalanceCsv.contains("comparative"));
    assertTrue(incomeStatementText.contains("Comparative Income Statement"));
    assertTrue(incomeStatementText.contains("Comparative net income totals"));
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

    String rendered = CliReportOutputRenderer.renderChangesInEquityText(nonComparativeReport);

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

    String rendered = CliReportOutputRenderer.renderChangesInEquityText(report);

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

    String rendered = CliReportOutputRenderer.renderChangesInEquityText(report);

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

    String rendered = CliReportOutputRenderer.renderChangesInEquityText(report);

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
                    Optional.empty(),
                    Optional.of(FinancialPositionLineClassification.OTHER_EQUITY),
                    StatementLineKind.DECLARED_ACCOUNT,
                    zeroBalance,
                    zeroBalance,
                    zeroBalance)),
            List.of(),
            List.of(),
            List.of());

    String rendered = CliReportOutputRenderer.renderChangesInEquityText(report);

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

    String rendered = CliReportOutputRenderer.renderChangesInEquityText(report);

    assertTrue(rendered.contains("Changes In Equity"));
    assertTrue(rendered.contains("Opening totals"));
    assertFalse(rendered.contains("Comparative Changes In Equity"));
  }

  @Test
  void reportSurfacePolicy_detectsTrialBalanceAndCurrentEquityComparatives() {
    var cashAccount = declaredAccount("1000", "Cash", NormalBalance.DEBIT);
    TrialBalanceReport nonComparativeTrialBalance =
        trialBalanceReport(
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            List.of(new TrialBalanceRow(cashAccount, eurDebitBalance())),
            List.of());
    ChangesInEquityReport nonCurrentEquityReport =
        new ChangesInEquityReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    ChangesInEquityReport openingOnlyEquityReport =
        new ChangesInEquityReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            List.of(),
            List.of(
                CliResponseWriterTestSupport.currencyBalance(
                    "EUR", "4.00", "0.00", "4.00", BalanceSide.DEBIT)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    ChangesInEquityReport movementOnlyEquityReport =
        new ChangesInEquityReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            List.of(),
            List.of(),
            List.of(
                CliResponseWriterTestSupport.currencyBalance(
                    "EUR", "0.00", "3.00", "3.00", BalanceSide.CREDIT)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    ChangesInEquityReport closingOnlyEquityReport =
        new ChangesInEquityReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            List.of(),
            List.of(),
            List.of(),
            List.of(
                CliResponseWriterTestSupport.currencyBalance(
                    "EUR", "0.00", "8.00", "8.00", BalanceSide.CREDIT)),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    assertFalse(CliReportSurfacePolicy.hasComparative(nonComparativeTrialBalance));
    assertTrue(
        CliReportSurfacePolicy.hasComparative(
            trialBalanceReport(
                nonComparativeTrialBalance.bookIdentity(),
                Optional.of(LocalDate.parse("2026-04-30")),
                EffectiveDateRange.unbounded(),
                allPostingKinds(),
                nonComparativeTrialBalance.rows(),
                List.of(new TrialBalanceRow(cashAccount, eurDebitBalance())))));
    assertTrue(
        CliReportSurfacePolicy.hasComparative(
            new TrialBalanceReport(
                bookIdentity(),
                Optional.of(LocalDate.parse("2026-04-30")),
                Optional.of(LocalDate.parse("2026-04-30")),
                EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
                allPostingKinds(),
                List.of(),
                List.of(),
                true,
                List.of(),
                List.of(),
                true)));
    assertFalse(CliReportSurfacePolicy.hasCurrent(nonCurrentEquityReport));
    assertTrue(CliReportSurfacePolicy.hasCurrent(openingOnlyEquityReport));
    assertTrue(CliReportSurfacePolicy.hasCurrent(movementOnlyEquityReport));
    assertTrue(CliReportSurfacePolicy.hasCurrent(closingOnlyEquityReport));
  }

  @Test
  void reportSurfacePolicy_treatsRowsOrTotalsAsRenderableStatementSections() {
    CurrencyBalance balance = eurDebitBalance();

    assertFalse(
        CliReportSurfacePolicy.hasRenderableFinancialPositionSection(
            new FinancialPositionSection(AccountType.ASSET, List.of(), List.of())));
    assertTrue(
        CliReportSurfacePolicy.hasRenderableFinancialPositionSection(
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
                        balance)),
                List.of())));
    assertTrue(
        CliReportSurfacePolicy.hasRenderableFinancialPositionSection(
            new FinancialPositionSection(AccountType.ASSET, List.of(), List.of(balance))));

    assertFalse(
        CliReportSurfacePolicy.hasRenderableIncomeStatementSection(
            new IncomeStatementSection(AccountType.EXPENSE, List.of(), List.of())));
    assertTrue(
        CliReportSurfacePolicy.hasRenderableIncomeStatementSection(
            new IncomeStatementSection(
                AccountType.EXPENSE,
                List.of(
                    new IncomeStatementRow(
                        "5100",
                        "Software",
                        AccountType.EXPENSE,
                        Optional.of(AccountRole.ORDINARY),
                        ProfitAndLossLineClassification.OPERATING_EXPENSE,
                        StatementLineKind.DECLARED_ACCOUNT,
                        balance)),
                List.of())));
    assertTrue(
        CliReportSurfacePolicy.hasRenderableIncomeStatementSection(
            new IncomeStatementSection(AccountType.EXPENSE, List.of(), List.of(balance))));
  }

  @Test
  void reportSurfacePolicy_treatsRowsAndComparativeTotalsAsMeaningfulEquityContent() {
    CurrencyBalance balance = eurDebitBalance();
    ChangesInEquityRow row =
        new ChangesInEquityRow(
            "equity-1000",
            "Equity",
            Optional.of(AccountType.EQUITY),
            Optional.of(AccountRole.ORDINARY),
            Optional.of(FinancialPositionLineClassification.OTHER_EQUITY),
            StatementLineKind.DECLARED_ACCOUNT,
            balance,
            balance,
            balance);

    ChangesInEquityReport currentRowsOnlyReport =
        new ChangesInEquityReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            List.of(row),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    ChangesInEquityReport comparativeRowsOnlyReport =
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
            List.of(row),
            List.of(),
            List.of(),
            List.of());
    ChangesInEquityReport comparativeOpeningOnlyReport =
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
            List.of(balance),
            List.of(),
            List.of());
    ChangesInEquityReport comparativeMovementOnlyReport =
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
            List.of(balance),
            List.of());
    ChangesInEquityReport comparativeClosingOnlyReport =
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
            List.of(balance));

    assertTrue(CliReportSurfacePolicy.hasCurrent(currentRowsOnlyReport));
    assertTrue(CliReportSurfacePolicy.hasComparative(comparativeRowsOnlyReport));
    assertTrue(CliReportSurfacePolicy.hasComparative(comparativeOpeningOnlyReport));
    assertTrue(CliReportSurfacePolicy.hasComparative(comparativeMovementOnlyReport));
    assertTrue(CliReportSurfacePolicy.hasComparative(comparativeClosingOnlyReport));
  }

  @Test
  void reportSurfacePolicy_detectsComparativeDataWithoutReferenceAcrossStatementFamilies() {
    CurrencyBalance balance = eurDebitBalance();
    FinancialPositionSection comparativeAssetSection =
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
                    balance)),
            List.of());
    FinancialPositionReport financialPositionReport =
        new FinancialPositionReport(
            bookIdentity(),
            Optional.of(LocalDate.parse("2026-04-30")),
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            true,
            List.of(),
            List.of(comparativeAssetSection));
    IncomeStatementReport incomeStatementReport =
        new IncomeStatementReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            standardOnly(),
            List.of(),
            List.of(),
            List.of(),
            List.of(balance));
    ChangesInEquityReport changesInEquityReport =
        new ChangesInEquityReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(balance),
            List.of(),
            List.of());

    assertTrue(CliReportSurfacePolicy.hasComparative(financialPositionReport));
    assertTrue(CliReportSurfacePolicy.hasComparative(incomeStatementReport));
    assertTrue(CliReportSurfacePolicy.hasComparative(changesInEquityReport));
  }

  @Test
  void reportSurfacePolicy_ignoresEmptyComparativeIncomeSectionsUntilDataAppears() {
    IncomeStatementReport emptyComparativeSectionsReport =
        new IncomeStatementReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            standardOnly(),
            List.of(),
            List.of(),
            List.of(new IncomeStatementSection(AccountType.EXPENSE, List.of(), List.of())),
            List.of());
    IncomeStatementReport totalsBackedComparativeReport =
        new IncomeStatementReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            standardOnly(),
            List.of(),
            List.of(),
            List.of(new IncomeStatementSection(AccountType.EXPENSE, List.of(), List.of())),
            List.of(eurDebitBalance()));

    assertFalse(CliReportSurfacePolicy.hasComparativeData(emptyComparativeSectionsReport));
    assertTrue(CliReportSurfacePolicy.hasComparativeData(totalsBackedComparativeReport));
  }

  @Test
  void reportSurfacePolicy_treatsRenderableComparativeIncomeSectionAsComparativeData() {
    IncomeStatementReport sectionBackedComparativeReport =
        new IncomeStatementReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            standardOnly(),
            List.of(),
            List.of(),
            List.of(
                new IncomeStatementSection(
                    AccountType.EXPENSE,
                    List.of(
                        new IncomeStatementRow(
                            "5100",
                            "Software",
                            AccountType.EXPENSE,
                            Optional.of(AccountRole.ORDINARY),
                            ProfitAndLossLineClassification.OPERATING_EXPENSE,
                            StatementLineKind.DECLARED_ACCOUNT,
                            eurDebitBalance())),
                    List.of())),
            List.of());

    assertTrue(CliReportSurfacePolicy.hasComparativeData(sectionBackedComparativeReport));
  }
}
