package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliPlanStepDataJsonModels;
import java.util.List;

/** Renders administration facts embedded in full execute-plan journal output. */
final class CliPlanAdministrativeTextRenderer {
  private CliPlanAdministrativeTextRenderer() {}

  static String renderStepData(
      CliPlanStepDataJsonModels.LedgerAdministrativeStepDataPayload dataPayload) {
    return switch (dataPayload) {
      case CliPlanStepDataJsonModels.AccountDeclarationStepDataPayload accountDeclaration ->
          CliPlanBookkeepingTextRenderer.renderDeclaredAccount(
              accountDeclaration.outcome(), accountDeclaration.account());
      case CliPlanStepDataJsonModels.TaxRegistrationDeclarationStepDataPayload taxRegistration ->
          renderTaxRegistration(taxRegistration);
    };
  }

  private static String renderTaxRegistration(
      CliPlanStepDataJsonModels.TaxRegistrationDeclarationStepDataPayload taxRegistration) {
    var registration = taxRegistration.taxRegistration();
    return CliReportRenderSupport.joinSections(
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Outcome", CliTextDisplay.wireLabel(taxRegistration.outcome())),
                List.of("Tax registration id", registration.taxRegistrationId()),
                List.of("Name", registration.taxRegistrationName()),
                List.of("Jurisdiction", registration.jurisdiction()),
                List.of(
                    "Registration number",
                    registration.registrationNumber() == null
                        ? "(none)"
                        : registration.registrationNumber()),
                List.of("Payable account", registration.payableAccountCode()),
                List.of("Recoverable account", registration.recoverableAccountCode()),
                List.of(
                    "Obligation frequency",
                    CliTextDisplay.wireLabel(registration.obligationFrequency())),
                List.of(
                    "Due days after period end",
                    Integer.toString(registration.dueDaysAfterPeriodEnd())),
                List.of("Declared at", registration.declaredAt()))),
        CliReportRenderSupport.section(
            "Tax codes",
            CliTextFormat.renderAdaptiveTable(
                CliReportRenderSupport.TEXT_TABLE_WIDTH,
                List.of("Tax code", "Name", "Rate", "Inclusion", "Application"),
                registration.taxCodes().stream()
                    .map(
                        taxCode ->
                            List.of(
                                taxCode.taxCode(),
                                taxCode.taxCodeName(),
                                taxCode.ratePartsPerMillion() + " ppm",
                                CliTextDisplay.wireLabel(taxCode.inclusionMode()),
                                CliTextDisplay.wireLabel(taxCode.applicationKind())))
                    .toList())));
  }
}
