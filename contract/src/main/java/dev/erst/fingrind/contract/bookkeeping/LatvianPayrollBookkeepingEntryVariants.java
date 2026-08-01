package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.payroll.LatvianMonthlyPayrollCalculation;
import dev.erst.fingrind.contract.payroll.LatvianPayrollEmployeeReference;
import dev.erst.fingrind.contract.payroll.LatvianPayrollMonth;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.contract.payroll.LatvianPayrollWithholdingProfile;
import dev.erst.fingrind.core.AccountCode;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Typed write variants owned by the Latvian monthly-payroll context. */
public sealed interface LatvianPayrollBookkeepingEntryVariants extends TypedBookkeepingEntry
    permits LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll,
        LatvianPayrollBookkeepingEntryVariants.NetWageSettlement,
        LatvianPayrollBookkeepingEntryVariants.StateRemittance {
  /** Records one executor-resolved Latvian monthly payroll accrual for one opaque employee. */
  record MonthlyPayroll(
      LocalDate effectiveDate,
      LatvianPayrollRunId payrollRunId,
      LatvianPayrollEmployeeReference employeeReference,
      LatvianPayrollMonth payrollMonth,
      LatvianPayrollWithholdingProfile withholdingProfile,
      AccountCode wageExpenseAccountCode,
      AccountCode employerSocialContributionExpenseAccountCode,
      AccountCode netWagesPayableAccountCode,
      AccountCode employeeSocialContributionPayableAccountCode,
      AccountCode employerSocialContributionPayableAccountCode,
      AccountCode personalIncomeTaxPayableAccountCode,
      MonetaryAmount grossWages,
      @Nullable LatvianMonthlyPayrollCalculation resolvedCalculation)
      implements LatvianPayrollBookkeepingEntryVariants {
    /** Normalizes public payroll-run facts and checks the resolved-calculation precondition. */
    public MonthlyPayroll {
      var state =
          LatvianPayrollEntryConstructionSupport.monthlyPayroll(
              new LatvianPayrollEntryConstructionSupport.MonthlyPayrollInput(
                  effectiveDate,
                  payrollRunId,
                  employeeReference,
                  payrollMonth,
                  withholdingProfile,
                  wageExpenseAccountCode,
                  employerSocialContributionExpenseAccountCode,
                  netWagesPayableAccountCode,
                  employeeSocialContributionPayableAccountCode,
                  employerSocialContributionPayableAccountCode,
                  personalIncomeTaxPayableAccountCode,
                  grossWages,
                  resolvedCalculation));
      effectiveDate = state.effectiveDate();
      payrollRunId = state.payrollRunId();
      employeeReference = state.employeeReference();
      payrollMonth = state.payrollMonth();
      withholdingProfile = state.withholdingProfile();
      wageExpenseAccountCode = state.wageExpenseAccountCode();
      employerSocialContributionExpenseAccountCode =
          state.employerSocialContributionExpenseAccountCode();
      netWagesPayableAccountCode = state.netWagesPayableAccountCode();
      employeeSocialContributionPayableAccountCode =
          state.employeeSocialContributionPayableAccountCode();
      employerSocialContributionPayableAccountCode =
          state.employerSocialContributionPayableAccountCode();
      personalIncomeTaxPayableAccountCode = state.personalIncomeTaxPayableAccountCode();
      grossWages = state.grossWages();
      resolvedCalculation = state.resolvedCalculation();
    }
  }

  /** Settles the exact net-wage obligation of one immutable payroll run. */
  record NetWageSettlement(
      LocalDate effectiveDate,
      LatvianPayrollRunId payrollRunId,
      AccountCode cashAccountCode,
      @Nullable ResolvedLatvianPayrollSettlement resolvedSettlement)
      implements LatvianPayrollBookkeepingEntryVariants {
    /** Normalizes caller facts and reserves journal construction to executor resolution. */
    public NetWageSettlement {
      var state =
          LatvianPayrollEntryConstructionSupport.netWageSettlement(
              effectiveDate, payrollRunId, cashAccountCode, resolvedSettlement);
      effectiveDate = state.effectiveDate();
      payrollRunId = state.payrollRunId();
      cashAccountCode = state.cashAccountCode();
      resolvedSettlement = state.resolvedSettlement();
    }
  }

  /** Settles the exact social-insurance and personal-income-tax obligation of one payroll run. */
  record StateRemittance(
      LocalDate effectiveDate,
      LatvianPayrollRunId payrollRunId,
      AccountCode cashAccountCode,
      @Nullable ResolvedLatvianPayrollSettlement resolvedSettlement)
      implements LatvianPayrollBookkeepingEntryVariants {
    /** Normalizes caller facts and reserves journal construction to executor resolution. */
    public StateRemittance {
      var state =
          LatvianPayrollEntryConstructionSupport.stateRemittance(
              effectiveDate, payrollRunId, cashAccountCode, resolvedSettlement);
      effectiveDate = state.effectiveDate();
      payrollRunId = state.payrollRunId();
      cashAccountCode = state.cashAccountCode();
      resolvedSettlement = state.resolvedSettlement();
    }
  }
}
