package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.Map;

/** Shared helpers for ledger-plan field and schema builders. */
final class MachineContractLedgerPlanFieldSupport {
  private MachineContractLedgerPlanFieldSupport() {}

  static String operation(OperationId operationId) {
    return MachineContractSchemaSupport.operation(operationId);
  }

  static Map<String, Object> acceptedSchema(MachineContractFieldSpec field) {
    return field.inputSchema();
  }
}
