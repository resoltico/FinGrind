package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolDeclareAccountFields;
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
        MachineContractSchemaSupport.orderedMap(
            ProtocolDeclareAccountFields.ACCOUNT_CODE,
            MachineContractSchemaSupport.nonBlankStringSchema("Book-local account code."),
            ProtocolDeclareAccountFields.ACCOUNT_NAME,
            MachineContractSchemaSupport.nonBlankStringSchema("Non-blank display name."),
            ProtocolDeclareAccountFields.NORMAL_BALANCE,
            MachineContractSchemaSupport.enumStringSchema(
                "Normal-balance side that increases this account.", NormalBalance.wireValues())),
        List.of(
            ProtocolDeclareAccountFields.ACCOUNT_CODE,
            ProtocolDeclareAccountFields.ACCOUNT_NAME,
            ProtocolDeclareAccountFields.NORMAL_BALANCE));
  }

  static Map<String, Object> declareAccountSchemaWithoutDialect() {
    return MachineContractSchemaSupport.stripDialect(declareAccountSchema());
  }
}
