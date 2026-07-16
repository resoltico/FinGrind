package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliTaxJsonModels;
import dev.erst.fingrind.contract.workflow.LedgerFact;
import java.util.List;

/** Maps workflow-owned tax-registration facts into the public tax payload. */
final class CliLedgerTaxRegistrationPayloadMapper {
  private CliLedgerTaxRegistrationPayloadMapper() {}

  static CliTaxJsonModels.DeclaredTaxRegistrationPayload taxRegistrationPayload(
      List<LedgerFact> facts) {
    return new CliTaxJsonModels.DeclaredTaxRegistrationPayload(
        CliLedgerFactAccess.requiredTextFact(facts, "taxRegistrationId"),
        CliLedgerFactAccess.requiredTextFact(facts, "taxRegistrationName"),
        CliLedgerFactAccess.requiredTextFact(facts, "jurisdiction"),
        CliLedgerFactAccess.optionalTextFact(facts, "registrationNumber"),
        CliLedgerFactAccess.requiredTextFact(facts, "payableAccountCode"),
        CliLedgerFactAccess.requiredTextFact(facts, "recoverableAccountCode"),
        CliLedgerFactAccess.requiredTextFact(facts, "obligationFrequency"),
        CliLedgerFactAccess.requiredCountFact(facts, "dueDaysAfterPeriodEnd"),
        CliLedgerFactAccess.groupedFacts(facts, "taxCode").stream()
            .map(CliLedgerTaxRegistrationPayloadMapper::taxCodePayload)
            .toList(),
        CliLedgerFactAccess.requiredTextFact(facts, "declaredAt"));
  }

  private static CliTaxJsonModels.DeclaredTaxCodePayload taxCodePayload(List<LedgerFact> facts) {
    return new CliTaxJsonModels.DeclaredTaxCodePayload(
        CliLedgerFactAccess.requiredTextFact(facts, "taxCode"),
        CliLedgerFactAccess.requiredTextFact(facts, "taxCodeName"),
        CliLedgerFactAccess.requiredCountFact(facts, "ratePartsPerMillion"),
        CliLedgerFactAccess.requiredTextFact(facts, "inclusionMode"),
        CliLedgerFactAccess.requiredTextFact(facts, "applicationKind"));
  }
}
