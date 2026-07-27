package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.discovery.DescriptorNamespaceSupport;
import java.util.List;

/** Sealed inventory root for the public machine-readable response descriptor types. */
public sealed interface ResponseDescriptorType
    permits BookModelDescriptor,
        BookkeepingKernelDescriptor,
        FieldDescriptor,
        ErrorDescriptor,
        ResponseModelDescriptor,
        PlanExecutionDescriptor,
        PlanAttestationOutcomeDescriptor,
        RejectionDescriptor,
        AttestationDiagnosticDescriptors.DiagnosticDescriptor,
        AttestationDiagnosticDescriptors.AdmissionDiagnosticsDescriptor,
        AttestationDiagnosticDescriptors.VerificationDiagnosticsDescriptor,
        AuditDescriptor,
        AccountRegistryDescriptor,
        ReversalDescriptor,
        PreflightDescriptor,
        CurrencyDescriptor {

  /** Returns the descriptor record types owned by the response descriptor family. */
  static List<Class<?>> descriptorTypes() {
    return DescriptorNamespaceSupport.descriptorTypes(ResponseDescriptorType.class);
  }
}
