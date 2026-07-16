package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.payroll.LatvianPayrollEmployeeReference;
import dev.erst.fingrind.contract.payroll.LatvianPayrollMonth;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.core.PostingId;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable payroll calculation and complete settlement lineage for one retained payroll run. */
public record LatvianPayrollRegisterRow(
    LatvianPayrollRunId payrollRunId,
    LatvianPayrollEmployeeReference employeeReference,
    LatvianPayrollMonth payrollMonth,
    PostingId originPostingId,
    LocalDate effectiveDate,
    Optional<PostingId> reversalPostingId,
    MonetaryAmount grossWages,
    MonetaryAmount employeeSocialContribution,
    MonetaryAmount employerSocialContribution,
    MonetaryAmount nonTaxableMinimum,
    MonetaryAmount personalIncomeTax,
    MonetaryAmount netWages,
    MonetaryAmount totalEmployerCost,
    MonetaryAmount stateRemittance,
    List<LatvianPayrollSettlementStatus> settlements) {
  /** Validates one internally coherent retained payroll register row. */
  public LatvianPayrollRegisterRow {
    Objects.requireNonNull(payrollRunId, "payrollRunId");
    Objects.requireNonNull(employeeReference, "employeeReference");
    Objects.requireNonNull(payrollMonth, "payrollMonth");
    Objects.requireNonNull(originPostingId, "originPostingId");
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    Objects.requireNonNull(reversalPostingId, "reversalPostingId");
    Objects.requireNonNull(grossWages, "grossWages");
    Objects.requireNonNull(employeeSocialContribution, "employeeSocialContribution");
    Objects.requireNonNull(employerSocialContribution, "employerSocialContribution");
    Objects.requireNonNull(nonTaxableMinimum, "nonTaxableMinimum");
    Objects.requireNonNull(personalIncomeTax, "personalIncomeTax");
    Objects.requireNonNull(netWages, "netWages");
    Objects.requireNonNull(totalEmployerCost, "totalEmployerCost");
    Objects.requireNonNull(stateRemittance, "stateRemittance");
    settlements = ContractDescriptorValidation.copyList(settlements, "settlements");
    String currencyCode = grossWages.currencyCode();
    if (List.of(
            employeeSocialContribution,
            employerSocialContribution,
            nonTaxableMinimum,
            personalIncomeTax,
            netWages,
            totalEmployerCost,
            stateRemittance)
        .stream()
        .anyMatch(amount -> !currencyCode.equals(amount.currencyCode()))) {
      throw new IllegalArgumentException(
          "Latvian payroll register amounts must share one currency.");
    }
  }

  /** Returns whether the run itself remains active, independent of any settlement state. */
  public boolean active() {
    return reversalPostingId.isEmpty();
  }
}
