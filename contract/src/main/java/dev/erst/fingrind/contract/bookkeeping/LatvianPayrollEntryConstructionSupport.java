package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.payroll.LatvianMonthlyPayrollCalculation;
import dev.erst.fingrind.contract.payroll.LatvianPayrollEmployeeReference;
import dev.erst.fingrind.contract.payroll.LatvianPayrollMonth;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.contract.payroll.LatvianPayrollWithholdingProfile;
import dev.erst.fingrind.core.AccountCode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Constructor normalization for public Latvian monthly-payroll entries. */
final class LatvianPayrollEntryConstructionSupport {
  record MonthlyPayrollState(
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
      @Nullable LatvianMonthlyPayrollCalculation resolvedCalculation) {}

  record MonthlyPayrollInput(
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
      @Nullable LatvianMonthlyPayrollCalculation resolvedCalculation) {}

  record SettlementState(
      LocalDate effectiveDate,
      LatvianPayrollRunId payrollRunId,
      AccountCode cashAccountCode,
      @Nullable ResolvedLatvianPayrollSettlement resolvedSettlement) {}

  private LatvianPayrollEntryConstructionSupport() {}

  static MonthlyPayrollState monthlyPayroll(MonthlyPayrollInput input) {
    Objects.requireNonNull(input, "input");
    LocalDate requiredEffectiveDate =
        BookkeepingEntryScalarValidationSupport.requireEffectiveDate(input.effectiveDate());
    LatvianPayrollMonth requiredPayrollMonth =
        Objects.requireNonNull(input.payrollMonth(), "payrollMonth");
    LatvianPayrollWithholdingProfile requiredWithholdingProfile =
        Objects.requireNonNull(input.withholdingProfile(), "withholdingProfile");
    if (!requiredEffectiveDate.equals(requiredPayrollMonth.value().atEndOfMonth())) {
      throw new IllegalArgumentException("effectiveDate must equal the final day of payrollMonth.");
    }
    AccountCode requiredWageExpenseAccountCode =
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            input.wageExpenseAccountCode(), "wageExpenseAccountCode");
    AccountCode requiredEmployerSocialContributionExpenseAccountCode =
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            input.employerSocialContributionExpenseAccountCode(),
            "employerSocialContributionExpenseAccountCode");
    AccountCode requiredNetWagesPayableAccountCode =
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            input.netWagesPayableAccountCode(), "netWagesPayableAccountCode");
    AccountCode requiredEmployeeSocialContributionPayableAccountCode =
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            input.employeeSocialContributionPayableAccountCode(),
            "employeeSocialContributionPayableAccountCode");
    AccountCode requiredEmployerSocialContributionPayableAccountCode =
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            input.employerSocialContributionPayableAccountCode(),
            "employerSocialContributionPayableAccountCode");
    AccountCode requiredPersonalIncomeTaxPayableAccountCode =
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            input.personalIncomeTaxPayableAccountCode(), "personalIncomeTaxPayableAccountCode");
    requireDistinctAccounts(
        requiredWageExpenseAccountCode,
        requiredEmployerSocialContributionExpenseAccountCode,
        requiredNetWagesPayableAccountCode,
        requiredEmployeeSocialContributionPayableAccountCode,
        requiredEmployerSocialContributionPayableAccountCode,
        requiredPersonalIncomeTaxPayableAccountCode);
    MonetaryAmount requiredGrossWages =
        BookkeepingEntryScalarValidationSupport.requirePositiveAmount(
            input.grossWages(), "grossWages");
    if (input.resolvedCalculation() != null
        && !input.resolvedCalculation().grossWages().equals(requiredGrossWages.toMoney())) {
      throw new IllegalArgumentException(
          "resolvedCalculation.grossWages must equal the caller-authored grossWages.");
    }
    if (input.resolvedCalculation() != null
        && !input.resolvedCalculation().withholdingProfile().equals(requiredWithholdingProfile)) {
      throw new IllegalArgumentException(
          "resolvedCalculation.withholdingProfile must equal the caller-authored withholdingProfile.");
    }
    return new MonthlyPayrollState(
        requiredEffectiveDate,
        Objects.requireNonNull(input.payrollRunId(), "payrollRunId"),
        Objects.requireNonNull(input.employeeReference(), "employeeReference"),
        requiredPayrollMonth,
        requiredWithholdingProfile,
        requiredWageExpenseAccountCode,
        requiredEmployerSocialContributionExpenseAccountCode,
        requiredNetWagesPayableAccountCode,
        requiredEmployeeSocialContributionPayableAccountCode,
        requiredEmployerSocialContributionPayableAccountCode,
        requiredPersonalIncomeTaxPayableAccountCode,
        requiredGrossWages,
        input.resolvedCalculation());
  }

  static SettlementState netWageSettlement(
      LocalDate effectiveDate,
      LatvianPayrollRunId payrollRunId,
      AccountCode cashAccountCode,
      @Nullable ResolvedLatvianPayrollSettlement resolvedSettlement) {
    return settlement(
        effectiveDate,
        payrollRunId,
        cashAccountCode,
        resolvedSettlement,
        dev.erst.fingrind.contract.payroll.LatvianPayrollSettlementKind.NET_WAGES);
  }

  static SettlementState stateRemittance(
      LocalDate effectiveDate,
      LatvianPayrollRunId payrollRunId,
      AccountCode cashAccountCode,
      @Nullable ResolvedLatvianPayrollSettlement resolvedSettlement) {
    return settlement(
        effectiveDate,
        payrollRunId,
        cashAccountCode,
        resolvedSettlement,
        dev.erst.fingrind.contract.payroll.LatvianPayrollSettlementKind.STATE_REMITTANCE);
  }

  private static SettlementState settlement(
      LocalDate effectiveDate,
      LatvianPayrollRunId payrollRunId,
      AccountCode cashAccountCode,
      @Nullable ResolvedLatvianPayrollSettlement resolvedSettlement,
      dev.erst.fingrind.contract.payroll.LatvianPayrollSettlementKind expectedKind) {
    LocalDate requiredEffectiveDate =
        BookkeepingEntryScalarValidationSupport.requireEffectiveDate(effectiveDate);
    LatvianPayrollRunId requiredPayrollRunId = Objects.requireNonNull(payrollRunId, "payrollRunId");
    AccountCode requiredCashAccountCode =
        BookkeepingEntryScalarValidationSupport.requireAccountCode(
            cashAccountCode, "cashAccountCode");
    if (resolvedSettlement != null) {
      if (resolvedSettlement.settlementKind() != expectedKind) {
        throw new IllegalArgumentException(
            "resolvedSettlement.settlementKind must be " + expectedKind.wireValue() + ".");
      }
      if (!requiredPayrollRunId.equals(resolvedSettlement.payrollRunId())) {
        throw new IllegalArgumentException(
            "resolvedSettlement.payrollRunId must equal the caller-authored payrollRunId.");
      }
      if (!requiredCashAccountCode.equals(resolvedSettlement.cashAccountCode())) {
        throw new IllegalArgumentException(
            "resolvedSettlement.cashAccountCode must equal the caller-authored cashAccountCode.");
      }
    }
    return new SettlementState(
        requiredEffectiveDate, requiredPayrollRunId, requiredCashAccountCode, resolvedSettlement);
  }

  private static void requireDistinctAccounts(AccountCode... accountCodes) {
    Set<AccountCode> distinct = new HashSet<>(List.of(accountCodes));
    if (distinct.size() != accountCodes.length) {
      throw new IllegalArgumentException("Latvian monthly payroll account codes must be distinct.");
    }
  }
}
