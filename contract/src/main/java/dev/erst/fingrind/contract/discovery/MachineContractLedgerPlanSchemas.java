package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.runtime.ContractResponse.PlanExecutionDescriptor;
import java.util.Arrays;
import java.util.Map;

/** Builds executable JSON Schema documents for ledger-plan request shapes. */
final class MachineContractLedgerPlanSchemas {
  private MachineContractLedgerPlanSchemas() {}

  static Map<String, Object> ledgerPlanSchema() {
    return MachineContractSchemaSupport.rootObjectSchema(
        "Canonical ledger-plan request JSON document.",
        MachineContractLedgerPlanFieldSets.topLevelFields());
  }

  static ContractRequestShapes.LedgerPlanRequestShapeDescriptor descriptor() {
    PlanExecutionDescriptor execution = MachineContractDomainDescriptors.planExecution();
    return new ContractRequestShapes.LedgerPlanRequestShapeDescriptor(
        MachineContractSchemaSupport.requestFieldDescriptors(
            MachineContractLedgerPlanFieldSets.topLevelFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(
            MachineContractLedgerPlanFieldSets.stepFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(
            MachineContractLedgerPlanFieldSets.queryFields()),
        MachineContractSchemaSupport.requestFieldDescriptors(
            MachineContractLedgerPlanFieldSets.assertionFields()),
        MachineContractLedgerPlanRoles.administrationStepKinds(),
        MachineContractLedgerPlanRoles.queryStepKinds(),
        MachineContractLedgerPlanRoles.writeStepKinds(),
        MachineContractLedgerPlanRoles.assertStepKind(),
        Arrays.stream(LedgerAssertionKind.values()).toList(),
        execution,
        ledgerPlanSchema());
  }
}
