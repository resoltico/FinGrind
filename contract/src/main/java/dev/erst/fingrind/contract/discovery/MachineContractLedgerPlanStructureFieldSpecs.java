package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import dev.erst.fingrind.contract.protocol.ProtocolLedgerPlanFields;

/** Plan-level and step-identity field specifications for executable ledger plans. */
final class MachineContractLedgerPlanStructureFieldSpecs {
  private MachineContractLedgerPlanStructureFieldSpecs() {}

  static MachineContractFieldSpec planIdField() {
    String description = "Caller-supplied plan identifier.";
    return MachineContractFieldSpec.required(
        ProtocolLedgerPlanFields.Plan.PLAN_ID,
        description,
        MachineContractScalarSchemas.nonBlankStringSchema(description));
  }

  static MachineContractFieldSpec stepsField() {
    String description = "Ordered non-empty array of executable ledger-plan steps.";
    return MachineContractFieldSpec.required(
        ProtocolLedgerPlanFields.Plan.STEPS,
        description,
        MachineContractSchemaSupport.arraySchema(
            description,
            MachineContractLedgerPlanVariantSchemas.stepSchema(),
            1,
            ProtocolInteractionLimits.LEDGER_PLAN_STEP_MAX));
  }

  static MachineContractFieldSpec stepIdField() {
    String description = "Caller-supplied step identifier unique inside the plan.";
    return MachineContractFieldSpec.required(
        ProtocolLedgerPlanFields.Step.STEP_ID,
        description,
        MachineContractScalarSchemas.nonBlankStringSchema(description));
  }

  static MachineContractFieldSpec genericStepKindField() {
    String description = "Canonical ledger-plan step kind for this step.";
    return MachineContractFieldSpec.required(
        ProtocolLedgerPlanFields.Step.KIND,
        description,
        MachineContractScalarSchemas.enumStringSchema(description, LedgerStepKind.wireValues()));
  }

  static MachineContractFieldSpec requiredConstStepKindField(LedgerStepKind kind) {
    String description = "Canonical ledger-plan step kind for this step.";
    return MachineContractFieldSpec.required(
        ProtocolLedgerPlanFields.Step.KIND,
        description,
        MachineContractScalarSchemas.constSchema(kind.wireValue(), description));
  }
}
