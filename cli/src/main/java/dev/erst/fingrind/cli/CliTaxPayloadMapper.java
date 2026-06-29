package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliTaxJsonModels;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.contract.tax.TaxObligationCodeSummary;
import dev.erst.fingrind.contract.tax.TaxObligationReport;
import dev.erst.fingrind.contract.tax.TaxRegistrationPage;
import dev.erst.fingrind.contract.tax.TaxSelection;

/** Maps tax-context contract results into CLI JSON payloads. */
final class CliTaxPayloadMapper {
  private CliTaxPayloadMapper() {}

  static CliTaxJsonModels.DeclaredTaxCodePayload taxCodePayload(TaxCodeDefinition taxCode) {
    return new CliTaxJsonModels.DeclaredTaxCodePayload(
        taxCode.taxCode().value(),
        taxCode.taxCodeName().value(),
        taxCode.rate().partsPerMillionOfWhole(),
        taxCode.inclusionMode().wireValue(),
        taxCode.applicationKind().wireValue());
  }

  static CliTaxJsonModels.DeclaredTaxRegistrationPayload taxRegistrationPayload(
      DeclaredTaxRegistration registration) {
    return new CliTaxJsonModels.DeclaredTaxRegistrationPayload(
        registration.taxRegistrationId().value(),
        registration.taxRegistrationName().value(),
        registration.jurisdiction().value(),
        registration.registrationNumber() == null
            ? null
            : registration.registrationNumber().value(),
        registration.payableAccountCode().value(),
        registration.recoverableAccountCode().value(),
        registration.obligationFrequency().wireValue(),
        registration.dueDaysAfterPeriodEnd(),
        registration.taxCodes().stream().map(CliTaxPayloadMapper::taxCodePayload).toList(),
        registration.declaredAt().toString());
  }

  static CliTaxJsonModels.TaxRegistrationMutationPayload taxRegistrationMutationPayload(
      String outcome, DeclaredTaxRegistration registration) {
    return new CliTaxJsonModels.TaxRegistrationMutationPayload(
        outcome, taxRegistrationPayload(registration));
  }

  static CliTaxJsonModels.TaxRegistrationListPayload taxRegistrationPagePayload(
      TaxRegistrationPage page) {
    return new CliTaxJsonModels.TaxRegistrationListPayload(
        CliBookQueryPayloadMapper.bookContextPayload(page.bookIdentity()),
        page.limit(),
        page.nextCursor().map(value -> value.wireValue()).orElse(null),
        page.registrations().stream().map(CliTaxPayloadMapper::taxRegistrationPayload).toList());
  }

  static CliTaxJsonModels.TaxObligationPayload taxObligationPayload(TaxObligationReport report) {
    return new CliTaxJsonModels.TaxObligationPayload(
        new CliTaxJsonModels.TaxObligationContextPayload(
            CliBookInspectionPayloadMapper.bookIdentityPayload(report.bookIdentity()),
            taxRegistrationPayload(report.registration()),
            report.reportingPeriod().effectiveDateFrom().toString(),
            report.reportingPeriod().effectiveDateTo().toString(),
            report.dueDate().toString()),
        report.codeSummaries().stream().map(CliTaxPayloadMapper::taxObligationCodeSummary).toList(),
        report.outputTax(),
        report.recoverableInputTax(),
        report.nonrecoverableInputTax(),
        report.netPayable(),
        report.netReceivable());
  }

  static CliTaxJsonModels.TaxSelectionPayload taxSelectionPayload(TaxSelection selection) {
    return new CliTaxJsonModels.TaxSelectionPayload(
        selection.taxRegistrationId().value(), selection.taxCode().value());
  }

  static CliTaxJsonModels.AppliedTaxPayload appliedTaxPayload(AppliedTax appliedTax) {
    return new CliTaxJsonModels.AppliedTaxPayload(
        appliedTax.taxRegistrationId().value(),
        appliedTax.taxCode().value(),
        appliedTax.taxCodeName().value(),
        appliedTax.rate().partsPerMillionOfWhole(),
        appliedTax.inclusionMode().wireValue(),
        appliedTax.applicationKind().wireValue(),
        appliedTax.taxableAmount(),
        appliedTax.taxAmount(),
        appliedTax.grossAmount(),
        appliedTax.taxAccountCode() == null ? null : appliedTax.taxAccountCode().value());
  }

  private static CliTaxJsonModels.TaxObligationCodeSummaryPayload taxObligationCodeSummary(
      TaxObligationCodeSummary summary) {
    return new CliTaxJsonModels.TaxObligationCodeSummaryPayload(
        summary.taxCode().value(),
        summary.taxCodeName().value(),
        summary.applicationKind().wireValue(),
        summary.postingCount(),
        summary.taxableAmount(),
        summary.taxAmount(),
        summary.grossAmount());
  }
}
