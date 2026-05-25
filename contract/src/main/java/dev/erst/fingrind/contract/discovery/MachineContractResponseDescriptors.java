package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolFailureStatus;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import java.util.List;

/** Builds rejection and error response descriptors for the machine contract. */
final class MachineContractResponseDescriptors {
  private MachineContractResponseDescriptors() {}

  static ContractResponse.ResponseModelDescriptor responseModel() {
    return new ContractResponse.ResponseModelDescriptor(
        ProtocolCatalog.successStatuses(),
        List.of(
            new ContractResponse.FieldDescriptor("status", "Literal success status."),
            new ContractResponse.FieldDescriptor(
                "payload", "Operation-specific success payload object."),
            new ContractResponse.FieldDescriptor(
                "artifacts",
                "Optional generated artifact metadata array for commands that exported files during the successful run.")),
        ProtocolCatalog.rejectionStatuses(),
        ProtocolFailureStatus.ERROR,
        rejectionDescriptors(),
        ContractErrors.descriptors(),
        List.of(
            new ContractResponse.FieldDescriptor("status", "Literal rejection status."),
            new ContractResponse.FieldDescriptor("code", "Stable machine rejection code."),
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
            new ContractResponse.FieldDescriptor("code", "Stable machine error code."),
            new ContractResponse.FieldDescriptor(
                "message", "Plain-language explanation of the error."),
            new ContractResponse.FieldDescriptor(
                "hint", "Optional operator hint for repairing the invocation."),
            new ContractResponse.FieldDescriptor(
                "argument", "Optional argument name associated with the failure."),
            new ContractResponse.FieldDescriptor(
                "details", "Optional structured error-specific detail object.")));
  }

  private static List<ContractResponse.RejectionDescriptor> rejectionDescriptors() {
    return java.util.stream.Stream.concat(
            java.util.stream.Stream.concat(
                BookAdministrationRejection.descriptors().stream(),
                java.util.stream.Stream.concat(
                    BookMaintenanceRejection.descriptors().stream(),
                    BookQueryRejection.descriptors().stream())),
            PostingRejection.descriptors().stream())
        .toList();
  }
}
