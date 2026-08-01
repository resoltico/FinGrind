package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.FinancingArrangementId;
import dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetId;
import dev.erst.fingrind.contract.bookkeeping.ForeignCurrencyObligationId;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.ResolvedFinancingApplication;
import dev.erst.fingrind.contract.bookkeeping.ResolvedFixedAssetDepreciation;
import dev.erst.fingrind.contract.bookkeeping.ResolvedFixedAssetDisposal;
import dev.erst.fingrind.contract.bookkeeping.ResolvedLatvianPayrollSettlement;
import dev.erst.fingrind.contract.bookkeeping.ResolvedRealizedForeignExchangeSettlement;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.contract.fx.QuotedExchangeRate;
import dev.erst.fingrind.contract.payroll.LatvianMonthlyPayroll2026;
import dev.erst.fingrind.contract.payroll.LatvianMonthlyPayrollCalculation;
import dev.erst.fingrind.contract.payroll.LatvianPayrollEmployeeReference;
import dev.erst.fingrind.contract.payroll.LatvianPayrollMonth;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.contract.payroll.LatvianPayrollSettlementKind;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.Money;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Verifies that every executor-owned payroll resolution is a journal-admission prerequisite. */
class PostEntryDeferredResolutionReadinessTest {
  private static final LatvianPayrollMonth PAYROLL_MONTH =
      new LatvianPayrollMonth(YearMonth.of(2026, 7));
  private static final LocalDate EFFECTIVE_DATE = PAYROLL_MONTH.value().atEndOfMonth();
  private static final LatvianPayrollRunId PAYROLL_RUN_ID =
      new LatvianPayrollRunId("payroll-2026-07-employee-1");
  private static final AccountCode CASH = new AccountCode("1000");
  private static final FixedAssetId FIXED_ASSET_ID = new FixedAssetId("office-desk");
  private static final FinancingArrangementId FINANCING_ID =
      new FinancingArrangementId("working-capital-loan");
  private static final ForeignCurrencyObligationId OBLIGATION_ID =
      new ForeignCurrencyObligationId("usd-client-invoice");
  private static final LatvianMonthlyPayrollCalculation CALCULATION =
      LatvianMonthlyPayroll2026.calculate(
          PAYROLL_MONTH,
          Money.parse("EUR", "2000.00"),
          dev.erst.fingrind.contract.payroll.LatvianPayrollWithholdingProfile
              .taxBookWithNoDependantsFor2026());

  @Test
  void readiness_requiresAndRecognizesEveryLatvianPayrollResolution() {
    assertEquals(
        Optional.of(false), PostEntryDeferredResolutionReadiness.readiness(monthlyPayroll(null)));
    assertEquals(
        Optional.of(true),
        PostEntryDeferredResolutionReadiness.readiness(monthlyPayroll(CALCULATION)));
    assertEquals(
        Optional.of(false), PostEntryDeferredResolutionReadiness.readiness(netWages(null)));
    assertEquals(
        Optional.of(true),
        PostEntryDeferredResolutionReadiness.readiness(
            netWages(resolved(LatvianPayrollSettlementKind.NET_WAGES))));
    assertEquals(
        Optional.of(false), PostEntryDeferredResolutionReadiness.readiness(stateRemittance(null)));
    assertEquals(
        Optional.of(true),
        PostEntryDeferredResolutionReadiness.readiness(
            stateRemittance(resolved(LatvianPayrollSettlementKind.STATE_REMITTANCE))));
  }

  @Test
  void readiness_requiresAndRecognizesEveryFixedAssetResolution() {
    assertEquals(
        Optional.of(false),
        PostEntryDeferredResolutionReadiness.readiness(
            new FixedAssetBookkeepingEntryVariants.Depreciation(
                EFFECTIVE_DATE, FIXED_ASSET_ID, null)));
    assertEquals(
        Optional.of(true),
        PostEntryDeferredResolutionReadiness.readiness(
            new FixedAssetBookkeepingEntryVariants.Depreciation(
                EFFECTIVE_DATE,
                FIXED_ASSET_ID,
                new ResolvedFixedAssetDepreciation(
                    new AccountCode("5000"), new AccountCode("1601"), amount("100")))));
    assertEquals(
        Optional.of(false),
        PostEntryDeferredResolutionReadiness.readiness(
            new FixedAssetBookkeepingEntryVariants.Disposal(
                EFFECTIVE_DATE, FIXED_ASSET_ID, CASH, amount("900"), null)));
    assertEquals(
        Optional.of(true),
        PostEntryDeferredResolutionReadiness.readiness(
            new FixedAssetBookkeepingEntryVariants.Disposal(
                EFFECTIVE_DATE,
                FIXED_ASSET_ID,
                CASH,
                amount("900"),
                new ResolvedFixedAssetDisposal(
                    new AccountCode("1600"),
                    new AccountCode("1601"),
                    new AccountCode("4100"),
                    amount("1000"),
                    amount("100"),
                    amount("900"),
                    amount("0"),
                    true))));
  }

  @Test
  void readiness_requiresAndRecognizesEveryFinancingResolution() {
    ResolvedFinancingApplication resolved =
        new ResolvedFinancingApplication(new AccountCode("2000"), new AccountCode("2001"));
    assertReadiness(
        new FinancingBookkeepingEntryVariants.PrincipalRepayment(
            EFFECTIVE_DATE, FINANCING_ID, CASH, amount("100"), null),
        new FinancingBookkeepingEntryVariants.PrincipalRepayment(
            EFFECTIVE_DATE, FINANCING_ID, CASH, amount("100"), resolved));
    assertReadiness(
        new FinancingBookkeepingEntryVariants.InterestAccrual(
            EFFECTIVE_DATE, FINANCING_ID, new AccountCode("5000"), amount("100"), null),
        new FinancingBookkeepingEntryVariants.InterestAccrual(
            EFFECTIVE_DATE, FINANCING_ID, new AccountCode("5000"), amount("100"), resolved));
    assertReadiness(
        new FinancingBookkeepingEntryVariants.InterestPayment(
            EFFECTIVE_DATE, FINANCING_ID, CASH, amount("100"), null),
        new FinancingBookkeepingEntryVariants.InterestPayment(
            EFFECTIVE_DATE, FINANCING_ID, CASH, amount("100"), resolved));
  }

  @Test
  void readiness_requiresAndRecognizesRealizedForeignExchangeSettlementResolution() {
    assertReadiness(
        new RealizedForeignExchangeBookkeepingEntryVariants.Settlement(
            EFFECTIVE_DATE, OBLIGATION_ID, CASH, usdAtEuro("10000", "9200"), null),
        new RealizedForeignExchangeBookkeepingEntryVariants.Settlement(
            EFFECTIVE_DATE,
            OBLIGATION_ID,
            CASH,
            usdAtEuro("10000", "9200"),
            new ResolvedRealizedForeignExchangeSettlement(
                new AccountCode("1100"),
                new AccountCode("4100"),
                amount("9000"),
                amount("200"),
                true)));
  }

  private static LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll monthlyPayroll(
      @Nullable LatvianMonthlyPayrollCalculation resolvedCalculation) {
    return new LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll(
        EFFECTIVE_DATE,
        PAYROLL_RUN_ID,
        new LatvianPayrollEmployeeReference("employee-1"),
        PAYROLL_MONTH,
        dev.erst.fingrind.contract.payroll.LatvianPayrollWithholdingProfile
            .taxBookWithNoDependantsFor2026(),
        new AccountCode("5000"),
        new AccountCode("5010"),
        new AccountCode("2200"),
        new AccountCode("2210"),
        new AccountCode("2220"),
        new AccountCode("2230"),
        MonetaryAmount.of(CALCULATION.grossWages()),
        resolvedCalculation);
  }

  private static LatvianPayrollBookkeepingEntryVariants.NetWageSettlement netWages(
      @Nullable ResolvedLatvianPayrollSettlement resolvedSettlement) {
    return new LatvianPayrollBookkeepingEntryVariants.NetWageSettlement(
        EFFECTIVE_DATE, PAYROLL_RUN_ID, CASH, resolvedSettlement);
  }

  private static LatvianPayrollBookkeepingEntryVariants.StateRemittance stateRemittance(
      @Nullable ResolvedLatvianPayrollSettlement resolvedSettlement) {
    return new LatvianPayrollBookkeepingEntryVariants.StateRemittance(
        EFFECTIVE_DATE, PAYROLL_RUN_ID, CASH, resolvedSettlement);
  }

  private static ResolvedLatvianPayrollSettlement resolved(
      LatvianPayrollSettlementKind settlementKind) {
    return new ResolvedLatvianPayrollSettlement(
        settlementKind,
        PAYROLL_RUN_ID,
        CASH,
        new AccountCode("2200"),
        new AccountCode("2210"),
        new AccountCode("2220"),
        new AccountCode("2230"),
        CALCULATION.netWages(),
        CALCULATION.employeeSocialContribution(),
        CALCULATION.employerSocialContribution(),
        CALCULATION.personalIncomeTax());
  }

  private static void assertReadiness(
      dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry unresolved,
      dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry resolved) {
    assertEquals(Optional.of(false), PostEntryDeferredResolutionReadiness.readiness(unresolved));
    assertEquals(Optional.of(true), PostEntryDeferredResolutionReadiness.readiness(resolved));
  }

  private static MonetaryAmount amount(String minorUnits) {
    return new MonetaryAmount("EUR", minorUnits);
  }

  private static ForeignExchangeDetails usdAtEuro(String usdMinorUnits, String euroMinorUnits) {
    MonetaryAmount transactionAmount = new MonetaryAmount("USD", usdMinorUnits);
    MonetaryAmount functionalAmount = new MonetaryAmount("EUR", euroMinorUnits);
    return new ForeignExchangeDetails(
        transactionAmount,
        functionalAmount,
        new QuotedExchangeRate(transactionAmount, functionalAmount, EFFECTIVE_DATE, "test quote"),
        ForeignExchangeTreatmentKind.SPOT_TRANSACTION);
  }
}
