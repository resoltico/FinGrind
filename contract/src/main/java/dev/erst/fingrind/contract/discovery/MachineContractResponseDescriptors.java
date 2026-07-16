package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.contract.runtime.ContractResponseCatalog;
import java.util.List;

/** Builds rejection and error response descriptors for the machine contract. */
final class MachineContractResponseDescriptors {
  private MachineContractResponseDescriptors() {}

  static ContractResponse.ResponseModelDescriptor responseModel() {
    return new ContractResponse.ResponseModelDescriptor(
        ProtocolCatalog.envelopes().successStatus(),
        List.of(
            new ContractResponse.FieldDescriptor("status", "Literal success status."),
            new ContractResponse.FieldDescriptor(
                "payload", "Operation-specific success payload object."),
            new ContractResponse.FieldDescriptor(
                "artifacts",
                "Optional artifact metadata array that owns every successful artifact path published beside the primary payload.")),
        ProtocolCatalog.envelopes().rejectionStatus(),
        ProtocolCatalog.envelopes().errorStatus(),
        ContractResponseCatalog.rejectionDescriptors(),
        ContractResponseCatalog.errorDescriptors(),
        List.of(
            new ContractResponse.FieldDescriptor("status", "Literal rejection status."),
            liftedPlanOutcomePayloadField(),
            new ContractResponse.FieldDescriptor("code", "Stable machine rejection code."),
            new ContractResponse.FieldDescriptor(
                "category", "Explicit transport category for the published rejection code."),
            new ContractResponse.FieldDescriptor(
                "message", "Plain-language explanation of the rejection."),
            new ContractResponse.FieldDescriptor(
                "hint", "Optional operator hint for repairing or rerunning the request."),
            new ContractResponse.FieldDescriptor(
                "details", "Optional structured rejection-specific detail object.")),
        List.of(
            new ContractResponse.FieldDescriptor("status", "Literal rejection status."),
            new ContractResponse.FieldDescriptor("code", "Stable machine rejection code."),
            new ContractResponse.FieldDescriptor(
                "category", "Explicit transport category for the published rejection code."),
            new ContractResponse.FieldDescriptor(
                "message", "Plain-language explanation of the rejection."),
            new ContractResponse.FieldDescriptor(
                "hint", "Optional operator hint for repairing or rerunning the request."),
            new ContractResponse.FieldDescriptor(
                "idempotencyKey", "The caller-supplied idempotency key from the rejected request."),
            new ContractResponse.FieldDescriptor(
                "details", "Optional structured rejection-specific detail object.")),
        List.of(
            new ContractResponse.FieldDescriptor(
                "status", "Literal runtime or invalid-request error status."),
            liftedPlanOutcomePayloadField(),
            new ContractResponse.FieldDescriptor("code", "Stable machine error code."),
            new ContractResponse.FieldDescriptor(
                "category", "Explicit transport category for the published error code."),
            new ContractResponse.FieldDescriptor(
                "message", "Plain-language explanation of the error."),
            new ContractResponse.FieldDescriptor(
                "hint", "Optional operator hint for repairing the invocation."),
            new ContractResponse.FieldDescriptor(
                "argument", "Optional argument name associated with the failure."),
            new ContractResponse.FieldDescriptor(
                "details", "Optional structured error-specific detail object.")));
  }

  private static ContractResponse.FieldDescriptor liftedPlanOutcomePayloadField() {
    return new ContractResponse.FieldDescriptor(
        "payload",
        "Optional "
            + ProtocolCatalog.operationName(OperationId.EXECUTE_PLAN)
            + " outcome payload object published when a rejected or assertion-failed plan result still owns the primary operation body beside lifted top-level diagnostics.");
  }
}
