package dev.erst.fingrind.contract.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.AttestationDiagnosticDescriptors.AdmissionContext;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers defensive diagnostic-catalog boundaries that have no ordinary command-path trigger. */
class AttestationVerificationDiagnosticCatalogTest {
  @Test
  void ordinaryAdmissionVocabulary_rejectsAnyDriftFromCoreAuthorizationFailures() {
    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                AttestationVerificationDiagnosticCatalog.requireAuthorizationVocabulary(
                    List.of(AttestationVerificationFailure.MANIFEST_INVALID)));

    assertEquals(
        "Ordinary attestation admission must match the core authorization failure vocabulary.",
        failure.getMessage());
  }

  @Test
  void admissionContextUsesItsStableWireText_andVerificationRejectsNonVerifierOperations() {
    assertEquals("ordinary-live-admission", AdmissionContext.ORDINARY_LIVE_ADMISSION.toString());

    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                AttestationVerificationFailure.PREIMAGE_INVALID.verificationDiagnostic(
                    OperationId.DECLARE_ACCOUNT));

    assertEquals(
        "Unsupported attestation verification diagnostic surface: declare-account",
        failure.getMessage());
  }
}
