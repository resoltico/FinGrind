package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolDeclareAccountFields;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.NormalBalance;
import java.util.List;
import java.util.Map;

/** Builds executable JSON Schema documents for declare-account request shapes. */
final class MachineContractDeclareAccountSchemas {
  private MachineContractDeclareAccountSchemas() {}

  static Map<String, Object> declareAccountSchema() {
    return MachineContractSchemaSupport.rootObjectSchema(
        "Canonical "
            + MachineContractSchemaSupport.operation(OperationId.DECLARE_ACCOUNT)
            + " request JSON document.",
        topLevelFields());
  }

  static Map<String, Object> declareAccountSchemaWithoutDialect() {
    return MachineContractSchemaSupport.stripDialect(declareAccountSchema());
  }

  static ContractRequestShapes.DeclareAccountRequestShapeDescriptor descriptor() {
    return new ContractRequestShapes.DeclareAccountRequestShapeDescriptor(
        MachineContractSchemaSupport.requestFieldDescriptors(topLevelFields()),
        List.of(
            new ContractRequestShapes.EnumVocabularyDescriptor(
                "normalBalance", NormalBalance.wireValues())),
        declareAccountSchema());
  }

  private static List<MachineContractFieldSpec> topLevelFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolDeclareAccountFields.ACCOUNT_CODE,
            "Book-local account code used by journal lines. FinGrind accepts ASCII letters or digits followed by ASCII letters, digits, '.', '_', ':', '/', or '-'.",
            MachineContractSchemaSupport.tokenStringSchema(
                "Book-local account code used by journal lines.",
                AccountCode.pattern(),
                AccountCode.maxLength())),
        MachineContractFieldSpec.required(
            ProtocolDeclareAccountFields.ACCOUNT_NAME,
            "Non-blank display name for the declared account.",
            MachineContractSchemaSupport.nonBlankStringSchema(
                "Non-blank display name for the declared account.")),
        MachineContractFieldSpec.required(
            ProtocolDeclareAccountFields.NORMAL_BALANCE,
            "Side of the journal equation that increases the account.",
            MachineContractSchemaSupport.enumStringSchema(
                "Side of the journal equation that increases the account.",
                NormalBalance.wireValues())));
  }
}
