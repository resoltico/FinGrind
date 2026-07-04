package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.CashFlowSection;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowSectionKind;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Covers residual CLI renderer and surface-policy branches left after the main report sweep. */
class CliSurfacePolicyBranchCoverageTest extends CliFixtureSupport {
  @Test
  void accountBalanceRenderers_coverDirectTextAndCsvDispatchBranches() {
    AccountBalanceSnapshot populatedSnapshot = sampleAccountBalanceSnapshot();
    AccountBalanceSnapshot emptySnapshot =
        new AccountBalanceSnapshot(
            populatedSnapshot.bookIdentity(),
            populatedSnapshot.account(),
            populatedSnapshot.effectiveDateFrom(),
            populatedSnapshot.effectiveDateTo(),
            populatedSnapshot.postingCoverage(),
            List.of());

    String populatedText = CliAccountBalanceOutputRenderer.renderText(populatedSnapshot);
    String populatedCsv = CliAccountBalanceOutputRenderer.renderCsv(populatedSnapshot);
    String emptyText = CliAccountBalanceOutputRenderer.renderText(emptySnapshot);
    String emptyCsv = CliReportCsvRenderer.renderAccountBalance(emptySnapshot);

    assertTrue(populatedText.contains("Account Balance"));
    assertTrue(populatedText.contains("Cash [1000]"), populatedText);
    assertTrue(populatedText.contains("Debit total"), populatedText);
    assertEquals(populatedCsv, CliReportCsvRenderer.renderAccountBalance(populatedSnapshot));
    assertTrue(populatedCsv.contains("balance"), populatedCsv);
    assertTrue(emptyText.contains("No balances matched the selected scope."), emptyText);
    assertTrue(emptyCsv.contains("scope-empty"), emptyCsv);
  }

  @Test
  void trialBalanceAndStatementPolicies_coverResidualComparativeBranches() {
    assertTrue(CliTrialBalanceSurfacePolicy.hasComparative(trialBalanceWithComparativeDataOnly()));
    assertTrue(
        CliTrialBalanceSurfacePolicy.hasComparativeData(trialBalanceWithComparativeDataOnly()));
    assertFalse(CliTrialBalanceSurfacePolicy.hasCurrent(trialBalanceWithComparativeDataOnly()));
    assertTrue(CliTrialBalanceSurfacePolicy.hasCurrent(trialBalanceWithCurrentTotalsOnly()));

    assertFalse(
        CliStatementSectionSurfacePolicy.hasRenderableFinancialPositionSection(
            new FinancialPositionSection(AccountType.ASSET, List.of(), List.of())));
    assertTrue(
        CliStatementSectionSurfacePolicy.hasRenderableFinancialPositionSection(
            new FinancialPositionSection(
                AccountType.ASSET,
                List.of(sampleFinancialPositionReport().sections().getFirst().rows().getFirst()),
                List.of())));
    assertTrue(
        CliStatementSectionSurfacePolicy.hasRenderableFinancialPositionSection(
            new FinancialPositionSection(
                AccountType.ASSET, List.of(), List.of(eurDebitBalance()))));
    assertFalse(
        CliStatementSectionSurfacePolicy.hasRenderableIncomeStatementSection(
            new IncomeStatementSection(AccountType.REVENUE, List.of(), List.of())));
    assertTrue(
        CliStatementSectionSurfacePolicy.hasRenderableIncomeStatementSection(
            new IncomeStatementSection(
                AccountType.REVENUE,
                List.of(sampleIncomeStatementReport().sections().getFirst().rows().getFirst()),
                List.of())));
    assertTrue(
        CliStatementSectionSurfacePolicy.hasRenderableIncomeStatementSection(
            new IncomeStatementSection(
                AccountType.REVENUE, List.of(), List.of(eurDebitBalance()))));
    assertFalse(
        CliStatementSectionSurfacePolicy.hasRenderableCashFlowSection(
            new CashFlowSection(CashFlowSectionKind.OPERATING, List.of(), List.of())));
    assertTrue(
        CliStatementSectionSurfacePolicy.hasRenderableCashFlowSection(
            new CashFlowSection(
                CashFlowSectionKind.OPERATING, List.of(), List.of(eurDebitBalance()))));

    assertTrue(CliStatementReportSurfacePolicy.hasComparative(changesInEquityWithDataOnly()));
    assertTrue(CliStatementReportSurfacePolicy.hasComparative(cashFlowWithDataOnly()));
  }

  private static TrialBalanceReport trialBalanceWithComparativeDataOnly() {
    return new TrialBalanceReport(
        bookIdentity(),
        Optional.of(LocalDate.parse("2026-04-30")),
        Optional.of(LocalDate.parse("2026-04-30")),
        EffectiveDateRange.unbounded(),
        allPostingKinds(),
        List.of(),
        List.of(),
        true,
        List.of(
            new TrialBalanceRow(
                declaredAccount("1000", "Cash", dev.erst.fingrind.core.NormalBalance.DEBIT),
                eurDebitBalance())),
        List.of(),
        true);
  }

  private static TrialBalanceReport trialBalanceWithCurrentTotalsOnly() {
    CurrencyBalance balance = eurDebitBalance();
    return new TrialBalanceReport(
        bookIdentity(),
        Optional.of(LocalDate.parse("2026-04-30")),
        Optional.of(LocalDate.parse("2026-04-30")),
        EffectiveDateRange.unbounded(),
        allPostingKinds(),
        List.of(),
        List.of(balance),
        true,
        List.of(),
        List.of(),
        true);
  }

  private static ChangesInEquityReport changesInEquityWithDataOnly() {
    ChangesInEquityReport sample = sampleChangesInEquityReport();
    return new ChangesInEquityReport(
        sample.bookIdentity(),
        sample.effectiveDateFrom(),
        sample.effectiveDateTo(),
        EffectiveDateRange.unbounded(),
        sample.postingCoverage(),
        sample.rows(),
        sample.openingTotals(),
        sample.movementTotals(),
        sample.closingTotals(),
        sample.comparativeRows(),
        sample.comparativeOpeningTotals(),
        sample.comparativeMovementTotals(),
        sample.comparativeClosingTotals());
  }

  private static CashFlowStatementReport cashFlowWithDataOnly() {
    CashFlowStatementReport sample = sampleCashFlowStatementReport();
    return new CashFlowStatementReport(
        sample.bookIdentity(),
        sample.effectiveDateFrom(),
        sample.effectiveDateTo(),
        EffectiveDateRange.unbounded(),
        sample.postingCoverage(),
        sample.openingCashTotals(),
        sample.sections(),
        sample.movementTotals(),
        sample.closingCashTotals(),
        sample.comparativeOpeningCashTotals(),
        sample.comparativeSections(),
        sample.comparativeMovementTotals(),
        sample.comparativeClosingCashTotals());
  }
}
