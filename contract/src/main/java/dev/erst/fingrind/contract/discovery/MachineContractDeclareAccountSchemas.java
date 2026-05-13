package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolDeclareAccountFields;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
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
                "accountType", AccountType.wireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                "accountRole", AccountRole.wireValues())),
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
            ProtocolDeclareAccountFields.ACCOUNT_TYPE,
            "Canonical chart-of-accounts classification for the declared account.",
            MachineContractSchemaSupport.enumStringSchema(
                "Canonical chart-of-accounts classification for the declared account.",
                AccountType.wireValues())),
        MachineContractFieldSpec.required(
            ProtocolDeclareAccountFields.ACCOUNT_ROLE,
            "Canonical doctrinal role for the declared account, including contra and retained-earnings handling.",
            MachineContractSchemaSupport.enumStringSchema(
                "Canonical doctrinal role for the declared account, including contra and retained-earnings handling.",
                AccountRole.wireValues())));
  }
}
