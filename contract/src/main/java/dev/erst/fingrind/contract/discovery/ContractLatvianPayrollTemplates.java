package dev.erst.fingrind.contract.discovery;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.Map;

/** Latvian payroll request-template descriptors owned by the payroll context. */
public interface ContractLatvianPayrollTemplates {
  /** Top-level request scaffold facts for one executor-resolved Latvian monthly payroll run. */
  record MonthlyPayrollTemplateDescriptor(
      String payrollRunId,
      String employeeReference,
      String payrollMonth,
      boolean taxBookHeldAtEmployer,
      int dependantCount,
      String wageExpenseAccountCode,
      String employerSocialContributionExpenseAccountCode,
      String netWagesPayableAccountCode,
      String employeeSocialContributionPayableAccountCode,
      String employerSocialContributionPayableAccountCode,
      String personalIncomeTaxPayableAccountCode,
      MonetaryAmount grossWages) {
    public MonthlyPayrollTemplateDescriptor {
      payrollRunId = ContractDescriptorValidation.requireText(payrollRunId, "payrollRunId");
      employeeReference =
          ContractDescriptorValidation.requireText(employeeReference, "employeeReference");
      payrollMonth = ContractDescriptorValidation.requireText(payrollMonth, "payrollMonth");
      new dev.erst.fingrind.contract.payroll.LatvianPayrollWithholdingProfile(
          taxBookHeldAtEmployer, dependantCount);
      wageExpenseAccountCode =
          ContractDescriptorValidation.requireText(
              wageExpenseAccountCode, "wageExpenseAccountCode");
      employerSocialContributionExpenseAccountCode =
          ContractDescriptorValidation.requireText(
              employerSocialContributionExpenseAccountCode,
              "employerSocialContributionExpenseAccountCode");
      netWagesPayableAccountCode =
          ContractDescriptorValidation.requireText(
              netWagesPayableAccountCode, "netWagesPayableAccountCode");
      employeeSocialContributionPayableAccountCode =
          ContractDescriptorValidation.requireText(
              employeeSocialContributionPayableAccountCode,
              "employeeSocialContributionPayableAccountCode");
      employerSocialContributionPayableAccountCode =
          ContractDescriptorValidation.requireText(
              employerSocialContributionPayableAccountCode,
              "employerSocialContributionPayableAccountCode");
      personalIncomeTaxPayableAccountCode =
          ContractDescriptorValidation.requireText(
              personalIncomeTaxPayableAccountCode, "personalIncomeTaxPayableAccountCode");
      grossWages = ContractDescriptorValidation.requireValue(grossWages, "grossWages");
    }
  }

  /** Top-level request scaffold facts for one exact executor-resolved payroll settlement. */
  record PayrollSettlementTemplateDescriptor(@JsonIgnore String payrollRunId) {
    public PayrollSettlementTemplateDescriptor {
      payrollRunId = ContractDescriptorValidation.requireText(payrollRunId, "payrollRunId");
    }

    /** Emits the command-level request field without colliding with the monthly-run scaffold. */
    @JsonAnyGetter
    public Map<String, String> requestFields() {
      return Map.of("payrollRunId", payrollRunId);
    }
  }
}
