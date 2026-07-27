package dev.erst.fingrind.contract.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Locks the direct public response descriptor family to its exhaustive inventory and validation.
 */
class ResponseDescriptorTypeTest {
  @Test
  void descriptorTypes_returnsTheCompleteDirectResponseDescriptorInventory() {
    assertEquals(
        List.of(
            BookModelDescriptor.class,
            BookkeepingKernelDescriptor.class,
            FieldDescriptor.class,
            ErrorDescriptor.class,
            ResponseModelDescriptor.class,
            PlanExecutionDescriptor.class,
            PlanAttestationOutcomeDescriptor.class,
            RejectionDescriptor.class,
            AttestationDiagnosticDescriptors.DiagnosticDescriptor.class,
            AttestationDiagnosticDescriptors.AdmissionDiagnosticsDescriptor.class,
            AttestationDiagnosticDescriptors.VerificationDiagnosticsDescriptor.class,
            AuditDescriptor.class,
            AccountRegistryDescriptor.class,
            ReversalDescriptor.class,
            PreflightDescriptor.class,
            CurrencyDescriptor.class),
        ResponseDescriptorType.descriptorTypes());
  }

  @Test
  void attestationDiagnostics_remainInTheResponseDescriptorFamily() {
    assertEquals(
        List.of(
            AttestationDiagnosticDescriptors.DiagnosticDescriptor.class,
            AttestationDiagnosticDescriptors.AdmissionDiagnosticsDescriptor.class,
            AttestationDiagnosticDescriptors.VerificationDiagnosticsDescriptor.class),
        AttestationDiagnosticDescriptors.descriptorTypes());
  }

  @Test
  void descriptors_retainTheirValidationAndEmptyDetailDefaults() {
    RejectionDescriptor rejection =
        new RejectionDescriptor("code", FailureCategory.DOMAIN_SEMANTIC, "description");

    assertEquals(
        List.of(),
        new ErrorDescriptor("code", FailureCategory.PRECONDITION, 4, "description").detailFields());
    assertThrows(
        IllegalArgumentException.class,
        () -> new ErrorDescriptor("code", FailureCategory.PRECONDITION, -1, "description"));
    assertEquals(List.of(), rejection.detailFields());
    assertEquals(List.of(), rejection.detailRejections());
  }
}
