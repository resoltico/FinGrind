package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.CashFlowSection;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowSectionKind;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Coverage for statement-report comparative and current surface policy branches. */
class CliStatementReportSurfacePolicyCoverageTest extends CliFixtureSupport {
  private static final LocalDate EFFECTIVE_DATE_FROM = LocalDate.parse("2026-04-01");
  private static final LocalDate EFFECTIVE_DATE_TO = LocalDate.parse("2026-04-30");
  private static final EffectiveDateRange COMPARATIVE_RANGE =
      EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30"));

  @Test
  void financialPositionComparativeSurface_coversReferenceDataAndEmptyStates() {
    assertFalse(CliStatementReportSurfacePolicy.hasComparative(emptyFinancialPositionReport()));
    assertFalse(CliStatementReportSurfacePolicy.hasComparativeData(emptyFinancialPositionReport()));
    assertTrue(CliStatementReportSurfacePolicy.hasComparative(financialPositionRefOnlyReport()));
    assertTrue(CliStatementReportSurfacePolicy.hasComparative(financialPositionDataOnlyReport()));
    assertTrue(
        CliStatementReportSurfacePolicy.hasComparativeData(financialPositionDataOnlyReport()));
  }

  @Test
  void incomeStatementComparativeSurface_coversSectionTotalsAndEmptyStates() {
    assertFalse(CliStatementReportSurfacePolicy.hasComparative(emptyIncomeStatementReport()));
    assertFalse(CliStatementReportSurfacePolicy.hasComparativeData(emptyIncomeStatementReport()));
    assertTrue(CliStatementReportSurfacePolicy.hasComparative(incomeStatementRefOnlyReport()));
    assertTrue(CliStatementReportSurfacePolicy.hasComparative(incomeStatementSectionOnlyReport()));
    assertTrue(
        CliStatementReportSurfacePolicy.hasComparativeData(incomeStatementSectionOnlyReport()));
    assertTrue(
        CliStatementReportSurfacePolicy.hasComparativeData(incomeStatementTotalsOnlyReport()));
  }

  @Test
  void changesInEquitySurface_coversComparativeAndCurrentInputs() {
    assertFalse(CliStatementReportSurfacePolicy.hasComparative(emptyChangesInEquityReport()));
    assertFalse(CliStatementReportSurfacePolicy.hasComparativeData(emptyChangesInEquityReport()));
    assertFalse(CliStatementReportSurfacePolicy.hasCurrent(emptyChangesInEquityReport()));
    assertTrue(CliStatementReportSurfacePolicy.hasComparative(changesInEquityRefOnlyReport()));
    assertTrue(
        CliStatementReportSurfacePolicy.hasComparativeData(changesInEquityWithComparativeRows()));
    assertTrue(
        CliStatementReportSurfacePolicy.hasComparativeData(
            changesInEquityWithComparativeOpeningTotals()));
    assertTrue(
        CliStatementReportSurfacePolicy.hasComparativeData(
            changesInEquityWithComparativeMovementTotals()));
    assertTrue(
        CliStatementReportSurfacePolicy.hasComparativeData(
            changesInEquityWithComparativeClosingTotals()));
    assertTrue(CliStatementReportSurfacePolicy.hasCurrent(changesInEquityWithRows()));
    assertTrue(CliStatementReportSurfacePolicy.hasCurrent(changesInEquityWithOpeningTotals()));
    assertTrue(CliStatementReportSurfacePolicy.hasCurrent(changesInEquityWithMovementTotals()));
    assertTrue(CliStatementReportSurfacePolicy.hasCurrent(changesInEquityWithClosingTotals()));
  }

  @Test
  void cashFlowSurface_coversComparativeAndCurrentInputs() {
    assertFalse(CliStatementReportSurfacePolicy.hasComparative(emptyCashFlowStatementReport()));
    assertFalse(CliStatementReportSurfacePolicy.hasComparativeData(emptyCashFlowStatementReport()));
    assertFalse(CliStatementReportSurfacePolicy.hasCurrent(emptyCashFlowStatementReport()));
    assertTrue(CliStatementReportSurfacePolicy.hasComparative(cashFlowRefOnlyReport()));
    assertTrue(
        CliStatementReportSurfacePolicy.hasComparativeData(cashFlowWithComparativeSections()));
    assertTrue(
        CliStatementReportSurfacePolicy.hasComparativeData(cashFlowWithComparativeOpeningTotals()));
    assertTrue(
        CliStatementReportSurfacePolicy.hasComparativeData(
            cashFlowWithComparativeMovementTotals()));
    assertTrue(
        CliStatementReportSurfacePolicy.hasComparativeData(cashFlowWithComparativeClosingTotals()));
    assertTrue(CliStatementReportSurfacePolicy.hasCurrent(cashFlowWithSections()));
    assertTrue(CliStatementReportSurfacePolicy.hasCurrent(cashFlowWithOpeningTotals()));
    assertTrue(CliStatementReportSurfacePolicy.hasCurrent(cashFlowWithMovementTotals()));
    assertTrue(CliStatementReportSurfacePolicy.hasCurrent(cashFlowWithClosingTotals()));
  }

  private static FinancialPositionReport emptyFinancialPositionReport() {
    return new FinancialPositionReport(
        bookIdentity(),
        Optional.empty(),
        Optional.empty(),
        EffectiveDateRange.unbounded(),
        allPostingKinds(),
        true,
        List.of(),
        List.of());
  }

  private static FinancialPositionReport financialPositionRefOnlyReport() {
    return new FinancialPositionReport(
        bookIdentity(),
        Optional.empty(),
        Optional.empty(),
        COMPARATIVE_RANGE,
        allPostingKinds(),
        true,
        List.of(),
        List.of());
  }

  private static FinancialPositionReport financialPositionDataOnlyReport() {
    return new FinancialPositionReport(
        bookIdentity(),
        Optional.empty(),
        Optional.empty(),
        EffectiveDateRange.unbounded(),
        allPostingKinds(),
        true,
        List.of(),
        List.of(new FinancialPositionSection(AccountType.ASSET, List.of(), balances())));
  }

  private static IncomeStatementReport emptyIncomeStatementReport() {
    return new IncomeStatementReport(
        bookIdentity(),
        EFFECTIVE_DATE_FROM,
        EFFECTIVE_DATE_TO,
        EffectiveDateRange.unbounded(),
        standardOnly(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static IncomeStatementReport incomeStatementRefOnlyReport() {
    return new IncomeStatementReport(
        bookIdentity(),
        EFFECTIVE_DATE_FROM,
        EFFECTIVE_DATE_TO,
        COMPARATIVE_RANGE,
        standardOnly(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static IncomeStatementReport incomeStatementSectionOnlyReport() {
    return new IncomeStatementReport(
        bookIdentity(),
        EFFECTIVE_DATE_FROM,
        EFFECTIVE_DATE_TO,
        EffectiveDateRange.unbounded(),
        standardOnly(),
        List.of(),
        List.of(),
        List.of(new IncomeStatementSection(AccountType.REVENUE, List.of(), balances())),
        List.of());
  }

  private static IncomeStatementReport incomeStatementTotalsOnlyReport() {
    return new IncomeStatementReport(
        bookIdentity(),
        EFFECTIVE_DATE_FROM,
        EFFECTIVE_DATE_TO,
        EffectiveDateRange.unbounded(),
        standardOnly(),
        List.of(),
        List.of(),
        List.of(),
        balances());
  }

  private static ChangesInEquityReport emptyChangesInEquityReport() {
    return new ChangesInEquityReport(
        bookIdentity(),
        EFFECTIVE_DATE_FROM,
        EFFECTIVE_DATE_TO,
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
  }

  private static ChangesInEquityReport changesInEquityRefOnlyReport() {
    return new ChangesInEquityReport(
        bookIdentity(),
        EFFECTIVE_DATE_FROM,
        EFFECTIVE_DATE_TO,
        COMPARATIVE_RANGE,
        allPostingKinds(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static ChangesInEquityReport changesInEquityWithRows() {
    return new ChangesInEquityReport(
        bookIdentity(),
        EFFECTIVE_DATE_FROM,
        EFFECTIVE_DATE_TO,
        EffectiveDateRange.unbounded(),
        allPostingKinds(),
        List.of(row()),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static ChangesInEquityReport changesInEquityWithOpeningTotals() {
    return new ChangesInEquityReport(
        bookIdentity(),
        EFFECTIVE_DATE_FROM,
        EFFECTIVE_DATE_TO,
        EffectiveDateRange.unbounded(),
        allPostingKinds(),
        List.of(),
        balances(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static ChangesInEquityReport changesInEquityWithMovementTotals() {
    return new ChangesInEquityReport(
        bookIdentity(),
        EFFECTIVE_DATE_FROM,
        EFFECTIVE_DATE_TO,
        EffectiveDateRange.unbounded(),
        allPostingKinds(),
        List.of(),
        List.of(),
        balances(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static ChangesInEquityReport changesInEquityWithClosingTotals() {
    return new ChangesInEquityReport(
        bookIdentity(),
        EFFECTIVE_DATE_FROM,
        EFFECTIVE_DATE_TO,
        EffectiveDateRange.unbounded(),
        allPostingKinds(),
        List.of(),
        List.of(),
        List.of(),
        balances(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static ChangesInEquityReport changesInEquityWithComparativeRows() {
    return new ChangesInEquityReport(
        bookIdentity(),
        EFFECTIVE_DATE_FROM,
        EFFECTIVE_DATE_TO,
        EffectiveDateRange.unbounded(),
        allPostingKinds(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(row()),
        List.of(),
        List.of(),
        List.of());
  }

  private static ChangesInEquityReport changesInEquityWithComparativeOpeningTotals() {
    return new ChangesInEquityReport(
        bookIdentity(),
        EFFECTIVE_DATE_FROM,
        EFFECTIVE_DATE_TO,
        EffectiveDateRange.unbounded(),
        allPostingKinds(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        balances(),
        List.of(),
        List.of());
  }

  private static ChangesInEquityReport changesInEquityWithComparativeMovementTotals() {
    return new ChangesInEquityReport(
        bookIdentity(),
        EFFECTIVE_DATE_FROM,
        EFFECTIVE_DATE_TO,
        EffectiveDateRange.unbounded(),
        allPostingKinds(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        balances(),
        List.of());
  }

  private static ChangesInEquityReport changesInEquityWithComparativeClosingTotals() {
    return new ChangesInEquityReport(
        bookIdentity(),
        EFFECTIVE_DATE_FROM,
        EFFECTIVE_DATE_TO,
        EffectiveDateRange.unbounded(),
        allPostingKinds(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        balances());
  }

  private static CashFlowStatementReport emptyCashFlowStatementReport() {
    return new CashFlowStatementReport(
        bookIdentity(),
        EFFECTIVE_DATE_FROM,
        EFFECTIVE_DATE_TO,
        EffectiveDateRange.unbounded(),
        standardOnly(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static CashFlowStatementReport cashFlowRefOnlyReport() {
    return new CashFlowStatementReport(
        bookIdentity(),
        EFFECTIVE_DATE_FROM,
        EFFECTIVE_DATE_TO,
        COMPARATIVE_RANGE,
        standardOnly(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static CashFlowStatementReport cashFlowWithSections() {
    return new CashFlowStatementReport(
        bookIdentity(),
        EFFECTIVE_DATE_FROM,
        EFFECTIVE_DATE_TO,
        EffectiveDateRange.unbounded(),
        standardOnly(),
        List.of(),
        List.of(new CashFlowSection(CashFlowSectionKind.OPERATING, List.of(), balances())),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static CashFlowStatementReport cashFlowWithOpeningTotals() {
    return new CashFlowStatementReport(
        bookIdentity(),
        EFFECTIVE_DATE_FROM,
        EFFECTIVE_DATE_TO,
        EffectiveDateRange.unbounded(),
        standardOnly(),
        balances(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static CashFlowStatementReport cashFlowWithMovementTotals() {
    return new CashFlowStatementReport(
        bookIdentity(),
        EFFECTIVE_DATE_FROM,
        EFFECTIVE_DATE_TO,
        EffectiveDateRange.unbounded(),
        standardOnly(),
        List.of(),
        List.of(),
        balances(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static CashFlowStatementReport cashFlowWithClosingTotals() {
    return new CashFlowStatementReport(
        bookIdentity(),
        EFFECTIVE_DATE_FROM,
        EFFECTIVE_DATE_TO,
        EffectiveDateRange.unbounded(),
        standardOnly(),
        List.of(),
        List.of(),
        List.of(),
        balances(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static CashFlowStatementReport cashFlowWithComparativeSections() {
    return new CashFlowStatementReport(
        bookIdentity(),
        EFFECTIVE_DATE_FROM,
        EFFECTIVE_DATE_TO,
        EffectiveDateRange.unbounded(),
        standardOnly(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(new CashFlowSection(CashFlowSectionKind.OPERATING, List.of(), balances())),
        List.of(),
        List.of());
  }

  private static CashFlowStatementReport cashFlowWithComparativeOpeningTotals() {
    return new CashFlowStatementReport(
        bookIdentity(),
        EFFECTIVE_DATE_FROM,
        EFFECTIVE_DATE_TO,
        EffectiveDateRange.unbounded(),
        standardOnly(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        balances(),
        List.of(),
        List.of(),
        List.of());
  }

  private static CashFlowStatementReport cashFlowWithComparativeMovementTotals() {
    return new CashFlowStatementReport(
        bookIdentity(),
        EFFECTIVE_DATE_FROM,
        EFFECTIVE_DATE_TO,
        EffectiveDateRange.unbounded(),
        standardOnly(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        balances(),
        List.of());
  }

  private static CashFlowStatementReport cashFlowWithComparativeClosingTotals() {
    return new CashFlowStatementReport(
        bookIdentity(),
        EFFECTIVE_DATE_FROM,
        EFFECTIVE_DATE_TO,
        EffectiveDateRange.unbounded(),
        standardOnly(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        balances());
  }

  private static List<CurrencyBalance> balances() {
    return List.of(eurDebitBalance());
  }

  private static ChangesInEquityRow row() {
    return new ChangesInEquityRow(
        "3200",
        "Retained Earnings",
        Optional.of(AccountType.EQUITY),
        Optional.of(FinancialPositionLineClassification.RESULT_HOLDING),
        StatementLineKind.DECLARED_ACCOUNT,
        eurDebitBalance(),
        eurDebitBalance(),
        eurDebitBalance());
  }
}
