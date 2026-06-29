package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonFieldAccess.optionalText;
import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredInt;
import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredText;
import static dev.erst.fingrind.cli.CliJsonScalarParsers.parseWireValue;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.rejectUnexpectedFields;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.requireObjectNode;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.requiredArray;

import dev.erst.fingrind.contract.protocol.ProtocolBookRequestFieldSets;
import dev.erst.fingrind.contract.protocol.ProtocolTaxRegistrationFields;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxJurisdiction;
import dev.erst.fingrind.contract.tax.TaxObligationFrequency;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationName;
import dev.erst.fingrind.contract.tax.TaxRegistrationNumber;
import dev.erst.fingrind.core.AccountCode;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Parses declare-tax-registration request payloads into command objects. */
final class CliDeclareTaxRegistrationRequestParser {
  private CliDeclareTaxRegistrationRequestParser() {}

  static DeclareTaxRegistrationCommand readDeclareTaxRegistrationCommand(ObjectNode rootNode) {
    CliWrappedRequestShapeGuards.rejectWrappedTopLevelPayload(
        rootNode,
        "declareTaxRegistration",
        ProtocolBookRequestFieldSets.declareTaxRegistrationFields(),
        "Declare-tax-registration request fields must be top-level for direct request files; remove the declareTaxRegistration wrapper.");
    rejectUnexpectedFields(
        rootNode, null, ProtocolBookRequestFieldSets.declareTaxRegistrationFields());
    return new DeclareTaxRegistrationCommand(
        new TaxRegistrationId(
            requiredText(rootNode, ProtocolTaxRegistrationFields.TAX_REGISTRATION_ID)),
        new TaxRegistrationName(
            requiredText(rootNode, ProtocolTaxRegistrationFields.TAX_REGISTRATION_NAME)),
        new TaxJurisdiction(requiredText(rootNode, ProtocolTaxRegistrationFields.JURISDICTION)),
        optionalText(rootNode, ProtocolTaxRegistrationFields.REGISTRATION_NUMBER)
            .map(TaxRegistrationNumber::new)
            .orElse(null),
        new AccountCode(requiredText(rootNode, ProtocolTaxRegistrationFields.PAYABLE_ACCOUNT_CODE)),
        new AccountCode(
            requiredText(rootNode, ProtocolTaxRegistrationFields.RECOVERABLE_ACCOUNT_CODE)),
        parseWireValue(
            requiredText(rootNode, ProtocolTaxRegistrationFields.OBLIGATION_FREQUENCY),
            ProtocolTaxRegistrationFields.OBLIGATION_FREQUENCY,
            TaxObligationFrequency.wireValues(),
            TaxObligationFrequency::fromWireValue),
        requiredInt(rootNode, ProtocolTaxRegistrationFields.DUE_DAYS_AFTER_PERIOD_END),
        readTaxCodes(requiredArray(rootNode, ProtocolTaxRegistrationFields.TAX_CODES)));
  }

  private static List<TaxCodeDefinition> readTaxCodes(JsonNode taxCodesNode) {
    List<TaxCodeDefinition> taxCodes = new ArrayList<>();
    int index = 0;
    for (JsonNode taxCodeNode : taxCodesNode) {
      ObjectNode taxCodeObject = requireObjectNode(taxCodeNode, "taxCodes[%d]".formatted(index));
      rejectUnexpectedFields(
          taxCodeObject,
          "taxCodes[%d]".formatted(index),
          java.util.Set.copyOf(ProtocolTaxRegistrationFields.taxCodeFields()));
      taxCodes.add(
          new TaxCodeDefinition(
              new TaxCode(
                  requiredText(taxCodeObject, ProtocolTaxRegistrationFields.TaxCode.TAX_CODE)),
              new TaxCodeName(
                  requiredText(taxCodeObject, ProtocolTaxRegistrationFields.TaxCode.TAX_CODE_NAME)),
              new TaxRate(
                  requiredInt(
                      taxCodeObject, ProtocolTaxRegistrationFields.TaxCode.RATE_PARTS_PER_MILLION)),
              parseWireValue(
                  requiredText(taxCodeObject, ProtocolTaxRegistrationFields.TaxCode.INCLUSION_MODE),
                  ProtocolTaxRegistrationFields.TaxCode.INCLUSION_MODE,
                  TaxInclusionMode.wireValues(),
                  TaxInclusionMode::fromWireValue),
              parseWireValue(
                  requiredText(
                      taxCodeObject, ProtocolTaxRegistrationFields.TaxCode.APPLICATION_KIND),
                  ProtocolTaxRegistrationFields.TaxCode.APPLICATION_KIND,
                  TaxApplicationKind.wireValues(),
                  TaxApplicationKind::fromWireValue)));
      index++;
    }
    return List.copyOf(taxCodes);
  }
}
