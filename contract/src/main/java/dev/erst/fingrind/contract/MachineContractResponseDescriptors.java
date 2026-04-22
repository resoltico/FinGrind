package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolStatuses;
import java.util.List;

/** Builds rejection and error response descriptors for the machine contract. */
final class MachineContractResponseDescriptors {
  private MachineContractResponseDescriptors() {}

  static ContractResponse.ResponseModelDescriptor responseModel() {
    return new ContractResponse.ResponseModelDescriptor(
        ProtocolCatalog.successStatuses(),
        ProtocolCatalog.rejectionStatuses(),
        ProtocolStatuses.ERROR,
        rejectionDescriptors(),
        ContractErrors.descriptors(),
        List.of(
            new ContractResponse.FieldDescriptor("status", "Literal rejection status."),
            new ContractResponse.FieldDescriptor("code", "Stable machine rejection code."),
            new ContractResponse.FieldDescriptor(
                "message", "Human-readable explanation of the rejection."),
            new ContractResponse.FieldDescriptor(
                "details", "Optional structured rejection-specific detail object.")),
        List.of(
            new ContractResponse.FieldDescriptor("status", "Literal rejection status."),
            new ContractResponse.FieldDescriptor("code", "Stable machine rejection code."),
            new ContractResponse.FieldDescriptor(
                "message", "Human-readable explanation of the rejection."),
            new ContractResponse.FieldDescriptor(
                "idempotencyKey", "The caller-supplied idempotency key from the rejected request."),
            new ContractResponse.FieldDescriptor(
                "details", "Optional structured rejection-specific detail object.")),
        List.of(
            new ContractResponse.FieldDescriptor(
                "status", "Literal runtime or invalid-request error status."),
            new ContractResponse.FieldDescriptor("code", "Stable machine error code."),
            new ContractResponse.FieldDescriptor(
                "message", "Human-readable explanation of the error."),
            new ContractResponse.FieldDescriptor(
                "hint", "Optional operator hint for repairing the invocation."),
            new ContractResponse.FieldDescriptor(
                "argument", "Optional argument name associated with the failure.")));
  }

  private static List<ContractResponse.RejectionDescriptor> rejectionDescriptors() {
    return java.util.stream.Stream.concat(
            java.util.stream.Stream.concat(
                BookAdministrationRejection.descriptors().stream(),
                BookQueryRejection.descriptors().stream()),
            PostingRejection.descriptors().stream())
        .toList();
  }
}
