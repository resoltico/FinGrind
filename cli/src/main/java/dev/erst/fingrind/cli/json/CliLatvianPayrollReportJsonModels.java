package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Semantic machine payloads for the durable Latvian payroll register. */
public interface CliLatvianPayrollReportJsonModels {
  record LatvianPayrollRegisterPayload(
      String family,
      CliAdministrationJsonModels.BookIdentityPayload bookIdentity,
      CliReportJsonModels.LatvianPayrollRegisterResolvedQuery resolvedQuery,
      String generatedAt,
      List<LatvianPayrollRegisterRowPayload> rows)
      implements CliReportJsonModels.ReportPayload {
    public LatvianPayrollRegisterPayload {
      family = requireText(family, "family");
      Objects.requireNonNull(bookIdentity, "bookIdentity");
      Objects.requireNonNull(resolvedQuery, "resolvedQuery");
      generatedAt = requireText(generatedAt, "generatedAt");
      rows = copyList(rows, "rows");
    }
  }

  record LatvianPayrollRegisterRowPayload(
      String payrollRunId,
      String employeeReference,
      String payrollMonth,
      String originPostingId,
      String effectiveDate,
      String runStatus,
      @Nullable String runReversalPostingId,
      CliReportValueJsonModels.MoneyPayload grossWages,
      CliReportValueJsonModels.MoneyPayload employeeSocialContribution,
      CliReportValueJsonModels.MoneyPayload employerSocialContribution,
      CliReportValueJsonModels.MoneyPayload nonTaxableMinimum,
      CliReportValueJsonModels.MoneyPayload personalIncomeTax,
      CliReportValueJsonModels.MoneyPayload netWages,
      CliReportValueJsonModels.MoneyPayload totalEmployerCost,
      CliReportValueJsonModels.MoneyPayload stateRemittance,
      List<LatvianPayrollSettlementStatusPayload> settlements) {
    public LatvianPayrollRegisterRowPayload {
      payrollRunId = requireText(payrollRunId, "payrollRunId");
      employeeReference = requireText(employeeReference, "employeeReference");
      payrollMonth = requireText(payrollMonth, "payrollMonth");
      originPostingId = requireText(originPostingId, "originPostingId");
      effectiveDate = requireText(effectiveDate, "effectiveDate");
      runStatus = requireText(runStatus, "runStatus");
      runReversalPostingId = requireOptionalText(runReversalPostingId, "runReversalPostingId");
      Objects.requireNonNull(grossWages, "grossWages");
      Objects.requireNonNull(employeeSocialContribution, "employeeSocialContribution");
      Objects.requireNonNull(employerSocialContribution, "employerSocialContribution");
      Objects.requireNonNull(nonTaxableMinimum, "nonTaxableMinimum");
      Objects.requireNonNull(personalIncomeTax, "personalIncomeTax");
      Objects.requireNonNull(netWages, "netWages");
      Objects.requireNonNull(totalEmployerCost, "totalEmployerCost");
      Objects.requireNonNull(stateRemittance, "stateRemittance");
      settlements = copyList(settlements, "settlements");
    }
  }

  record LatvianPayrollSettlementStatusPayload(
      String settlementKind,
      String postingId,
      String effectiveDate,
      String status,
      @Nullable String reversalPostingId) {
    public LatvianPayrollSettlementStatusPayload {
      settlementKind = requireText(settlementKind, "settlementKind");
      postingId = requireText(postingId, "postingId");
      effectiveDate = requireText(effectiveDate, "effectiveDate");
      status = requireText(status, "status");
      reversalPostingId = requireOptionalText(reversalPostingId, "reversalPostingId");
    }
  }
}
