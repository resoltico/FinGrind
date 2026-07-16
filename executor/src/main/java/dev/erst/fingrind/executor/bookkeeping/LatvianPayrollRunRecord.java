package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.payroll.LatvianMonthlyPayrollCalculation;
import dev.erst.fingrind.contract.payroll.LatvianPayrollEmployeeReference;
import dev.erst.fingrind.contract.payroll.LatvianPayrollMonth;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Durable, immutable Latvian monthly-payroll run and any compensating reversal. */
public record LatvianPayrollRunRecord(
    LatvianPayrollRunId payrollRunId,
    LatvianPayrollEmployeeReference employeeReference,
    LatvianPayrollMonth payrollMonth,
    LocalDate effectiveDate,
    AccountCode wageExpenseAccountCode,
    AccountCode employerSocialContributionExpenseAccountCode,
    AccountCode netWagesPayableAccountCode,
    AccountCode employeeSocialContributionPayableAccountCode,
    AccountCode employerSocialContributionPayableAccountCode,
    AccountCode personalIncomeTaxPayableAccountCode,
    LatvianMonthlyPayrollCalculation calculation,
    PostingId originPostingId,
    Optional<PostingId> reversalPostingId) {
  /** Validates retained payroll facts and the optional one-to-one compensating reversal. */
  public LatvianPayrollRunRecord {
    Objects.requireNonNull(payrollRunId, "payrollRunId");
    Objects.requireNonNull(employeeReference, "employeeReference");
    Objects.requireNonNull(payrollMonth, "payrollMonth");
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    Objects.requireNonNull(wageExpenseAccountCode, "wageExpenseAccountCode");
    Objects.requireNonNull(
        employerSocialContributionExpenseAccountCode,
        "employerSocialContributionExpenseAccountCode");
    Objects.requireNonNull(netWagesPayableAccountCode, "netWagesPayableAccountCode");
    Objects.requireNonNull(
        employeeSocialContributionPayableAccountCode,
        "employeeSocialContributionPayableAccountCode");
    Objects.requireNonNull(
        employerSocialContributionPayableAccountCode,
        "employerSocialContributionPayableAccountCode");
    Objects.requireNonNull(
        personalIncomeTaxPayableAccountCode, "personalIncomeTaxPayableAccountCode");
    Objects.requireNonNull(calculation, "calculation");
    Objects.requireNonNull(originPostingId, "originPostingId");
    reversalPostingId =
        Optional.ofNullable(
            Objects.requireNonNull(reversalPostingId, "reversalPostingId").orElse(null));
    if (!effectiveDate.equals(payrollMonth.value().atEndOfMonth())) {
      throw new IllegalArgumentException(
          "Latvian payroll effectiveDate must be the final day of its payroll month.");
    }
  }

  /** Returns whether the payroll accrual remains the active run for its employee-month. */
  public boolean active() {
    return reversalPostingId.isEmpty();
  }
}
