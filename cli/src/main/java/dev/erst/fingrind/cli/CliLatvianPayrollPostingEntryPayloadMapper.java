package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliPostingEntryPayload;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.ResolvedLatvianPayrollSettlement;
import dev.erst.fingrind.contract.payroll.LatvianMonthlyPayrollCalculation;
import org.jspecify.annotations.Nullable;

/** Maps executor-owned Latvian payroll calculation facts to the public posting payload. */
final class CliLatvianPayrollPostingEntryPayloadMapper {
  private CliLatvianPayrollPostingEntryPayloadMapper() {}

  static CliPostingEntryPayload.@Nullable ResolvedLatvianMonthlyPayrollCalculationPayload
      resolvedCalculationPayload(@Nullable LatvianMonthlyPayrollCalculation calculation) {
    if (calculation == null) {
      return null;
    }
    return new CliPostingEntryPayload.ResolvedLatvianMonthlyPayrollCalculationPayload(
        MonetaryAmount.of(calculation.employeeSocialContribution()),
        MonetaryAmount.of(calculation.employerSocialContribution()),
        MonetaryAmount.of(calculation.monthlyNonTaxableMinimum()),
        MonetaryAmount.of(calculation.personalIncomeTax()),
        MonetaryAmount.of(calculation.netWages()));
  }

  static CliPostingEntryPayload.@Nullable ResolvedLatvianPayrollSettlementPayload
      resolvedSettlementPayload(@Nullable ResolvedLatvianPayrollSettlement settlement) {
    if (settlement == null) {
      return null;
    }
    return new CliPostingEntryPayload.ResolvedLatvianPayrollSettlementPayload(
        settlement.netWagesPayableAccountCode().value(),
        settlement.employeeSocialContributionPayableAccountCode().value(),
        settlement.employerSocialContributionPayableAccountCode().value(),
        settlement.personalIncomeTaxPayableAccountCode().value(),
        MonetaryAmount.of(settlement.netWages()),
        MonetaryAmount.of(settlement.employeeSocialContribution()),
        MonetaryAmount.of(settlement.employerSocialContribution()),
        MonetaryAmount.of(settlement.personalIncomeTax()));
  }
}
