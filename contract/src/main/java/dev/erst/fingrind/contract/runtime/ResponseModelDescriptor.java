package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.ProtocolEnvelopeStatus;
import java.util.List;

/** Descriptor for the stable response-model contract. */
public record ResponseModelDescriptor(
    ProtocolEnvelopeStatus successStatus,
    List<FieldDescriptor> successFields,
    ProtocolEnvelopeStatus rejectionStatus,
    ProtocolEnvelopeStatus errorStatus,
    List<RejectionDescriptor> rejections,
    List<AttestationDiagnosticDescriptors.AdmissionDiagnosticsDescriptor>
        attestationAdmissionDiagnostics,
    List<AttestationDiagnosticDescriptors.VerificationDiagnosticsDescriptor>
        attestationVerificationDiagnostics,
    List<ErrorDescriptor> errorDescriptors,
    List<FieldDescriptor> rejectionFields,
    List<FieldDescriptor> postEntryRejectionFields,
    List<FieldDescriptor> errorFields)
    implements ResponseDescriptorType {
  /** Validates one response-model descriptor payload. */
  public ResponseModelDescriptor {
    successStatus = ContractDescriptorValidation.requireValue(successStatus, "successStatus");
    successFields = ContractDescriptorValidation.copyList(successFields, "successFields");
    rejectionStatus = ContractDescriptorValidation.requireValue(rejectionStatus, "rejectionStatus");
    errorStatus = ContractDescriptorValidation.requireValue(errorStatus, "errorStatus");
    rejections = ContractDescriptorValidation.copyList(rejections, "rejections");
    attestationAdmissionDiagnostics =
        ContractDescriptorValidation.copyList(
            attestationAdmissionDiagnostics, "attestationAdmissionDiagnostics");
    attestationVerificationDiagnostics =
        ContractDescriptorValidation.copyList(
            attestationVerificationDiagnostics, "attestationVerificationDiagnostics");
    errorDescriptors = ContractDescriptorValidation.copyList(errorDescriptors, "errorDescriptors");
    rejectionFields = ContractDescriptorValidation.copyList(rejectionFields, "rejectionFields");
    postEntryRejectionFields =
        ContractDescriptorValidation.copyList(postEntryRejectionFields, "postEntryRejectionFields");
    errorFields = ContractDescriptorValidation.copyList(errorFields, "errorFields");
  }
}
