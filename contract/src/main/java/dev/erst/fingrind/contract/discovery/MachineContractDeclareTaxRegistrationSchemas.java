package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolTaxRegistrationFields;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxJurisdiction;
import dev.erst.fingrind.contract.tax.TaxObligationFrequency;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationName;
import dev.erst.fingrind.contract.tax.TaxRegistrationNumber;
import dev.erst.fingrind.core.AccountCode;
import java.util.List;
import java.util.Map;

/** Builds executable JSON Schema documents for declare-tax-registration request shapes. */
final class MachineContractDeclareTaxRegistrationSchemas {
  private MachineContractDeclareTaxRegistrationSchemas() {}

  static Map<String, Object> declareTaxRegistrationSchema() {
    return MachineContractSchemaSupport.rootObjectSchema(
        "Canonical "
            + MachineContractSchemaSupport.operation(OperationId.DECLARE_TAX_REGISTRATION)
            + " request JSON document.",
        topLevelFields());
  }

  static Map<String, Object> declareTaxRegistrationSchemaWithoutDialect() {
    return MachineContractSchemaSupport.stripDialect(declareTaxRegistrationSchema());
  }

  static ContractRequestShapes.DeclareTaxRegistrationRequestShapeDescriptor descriptor() {
    return new ContractRequestShapes.DeclareTaxRegistrationRequestShapeDescriptor(
        MachineContractSchemaSupport.requestFieldDescriptors(topLevelFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(taxCodeFields()),
        List.of(
            new ContractRequestShapes.EnumVocabularyDescriptor(
                ProtocolTaxRegistrationFields.OBLIGATION_FREQUENCY,
                TaxObligationFrequency.wireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                ProtocolTaxRegistrationFields.TaxCode.INCLUSION_MODE,
                TaxInclusionMode.wireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                ProtocolTaxRegistrationFields.TaxCode.APPLICATION_KIND,
                TaxApplicationKind.wireValues())),
        declareTaxRegistrationSchema());
  }

  private static List<MachineContractFieldSpec> topLevelFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolTaxRegistrationFields.TAX_REGISTRATION_ID,
            "Stable tax-registration identifier owned inside this book.",
            MachineContractScalarSchemas.tokenStringSchema(
                "Stable tax-registration identifier owned inside this book.",
                TaxRegistrationId.pattern(),
                TaxRegistrationId.maxLength())),
        MachineContractFieldSpec.required(
            ProtocolTaxRegistrationFields.TAX_REGISTRATION_NAME,
            "Human-facing display name for this declared tax registration.",
            MachineContractSchemaSupport.orderedMap(
                "type",
                "string",
                "description",
                "Human-facing display name for this declared tax registration.",
                "pattern",
                "^(?=\\S).*(?<=\\S)$",
                "maxLength",
                TaxRegistrationName.maxLength())),
        MachineContractFieldSpec.required(
            ProtocolTaxRegistrationFields.JURISDICTION,
            "Operator-managed jurisdiction token or label that owns this registration.",
            MachineContractSchemaSupport.orderedMap(
                "type",
                "string",
                "description",
                "Operator-managed jurisdiction token or label that owns this registration.",
                "pattern",
                "^(?=\\S).*(?<=\\S)$",
                "maxLength",
                TaxJurisdiction.maxLength())),
        MachineContractFieldSpec.optional(
            ProtocolTaxRegistrationFields.REGISTRATION_NUMBER,
            "Optional operator-managed registration number inside the selected jurisdiction.",
            MachineContractSchemaSupport.orderedMap(
                "type",
                "string",
                "description",
                "Optional operator-managed registration number inside the selected jurisdiction.",
                "pattern",
                "^(?=\\S).*(?<=\\S)$",
                "maxLength",
                TaxRegistrationNumber.maxLength())),
        MachineContractFieldSpec.required(
            ProtocolTaxRegistrationFields.PAYABLE_ACCOUNT_CODE,
            "Declared liability account that accumulates net payable tax.",
            MachineContractScalarSchemas.tokenStringSchema(
                "Declared liability account that accumulates net payable tax.",
                AccountCode.pattern(),
                AccountCode.maxLength())),
        MachineContractFieldSpec.required(
            ProtocolTaxRegistrationFields.RECOVERABLE_ACCOUNT_CODE,
            "Declared asset account that accumulates recoverable input tax.",
            MachineContractScalarSchemas.tokenStringSchema(
                "Declared asset account that accumulates recoverable input tax.",
                AccountCode.pattern(),
                AccountCode.maxLength())),
        MachineContractFieldSpec.required(
            ProtocolTaxRegistrationFields.OBLIGATION_FREQUENCY,
            "Filing cadence that governs valid obligation windows for this registration.",
            MachineContractScalarSchemas.enumStringSchema(
                "Filing cadence that governs valid obligation windows for this registration.",
                TaxObligationFrequency.wireValues())),
        MachineContractFieldSpec.required(
            ProtocolTaxRegistrationFields.DUE_DAYS_AFTER_PERIOD_END,
            "Whole-number due-day offset counted after each filing period ends.",
            MachineContractSchemaSupport.orderedMap(
                "type",
                "integer",
                "description",
                "Whole-number due-day offset counted after each filing period ends.",
                "minimum",
                0,
                "maximum",
                366)),
        MachineContractFieldSpec.required(
            ProtocolTaxRegistrationFields.TAX_CODES,
            "Non-empty array of declared tax codes owned by this registration.",
            MachineContractSchemaSupport.arraySchema(
                "Non-empty array of declared tax codes owned by this registration.",
                taxCodeSchema(),
                1)));
  }

  private static List<MachineContractFieldSpec> taxCodeFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolTaxRegistrationFields.TaxCode.TAX_CODE,
            "Stable tax-code token unique inside one tax registration.",
            MachineContractScalarSchemas.tokenStringSchema(
                "Stable tax-code token unique inside one tax registration.",
                TaxCode.pattern(),
                TaxCode.maxLength())),
        MachineContractFieldSpec.required(
            ProtocolTaxRegistrationFields.TaxCode.TAX_CODE_NAME,
            "Human-facing display name for the declared tax code.",
            MachineContractScalarSchemas.nonBlankStringSchema(
                "Human-facing display name for the declared tax code.")),
        MachineContractFieldSpec.required(
            ProtocolTaxRegistrationFields.TaxCode.RATE_PARTS_PER_MILLION,
            "Exact tax rate carried as parts per million of one whole amount.",
            MachineContractSchemaSupport.orderedMap(
                "type",
                "integer",
                "description",
                "Exact tax rate carried as parts per million of one whole amount.",
                "minimum",
                0,
                "maximum",
                TaxRate.WHOLE)),
        MachineContractFieldSpec.required(
            ProtocolTaxRegistrationFields.TaxCode.INCLUSION_MODE,
            "Declares whether caller-authored entry amounts exclude or include tax.",
            MachineContractScalarSchemas.enumStringSchema(
                "Declares whether caller-authored entry amounts exclude or include tax.",
                TaxInclusionMode.wireValues())),
        MachineContractFieldSpec.required(
            ProtocolTaxRegistrationFields.TaxCode.APPLICATION_KIND,
            "Declares how this tax code participates in sale or expense recognition.",
            MachineContractScalarSchemas.enumStringSchema(
                "Declares how this tax code participates in sale or expense recognition.",
                TaxApplicationKind.wireValues())));
  }

  private static Map<String, Object> taxCodeSchema() {
    return MachineContractSchemaSupport.objectSchema(
        "Declared tax-code definition nested inside one tax registration.", taxCodeFields());
  }
}
