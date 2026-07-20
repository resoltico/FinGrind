package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.binding;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.credential;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.fiscalWorkflow;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.interimWorkflow;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.rollover;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.security.KeyPair;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Proves that invalid cross-fact registry histories have no representable fold. */
class AttestationRegistryValidatorTest {
  @Test
  void rejectsInvalidCrossFactRegistryHistories() {
    TestCredential first = credential();
    TestCredential second = credential();
    TestCredential replacement = credential();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationRegistry.of(
                List.of(binding(0, first), binding(1, first)),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationRegistry.of(
                List.of(),
                List.of(
                    new AttestationCredentialRevocation(
                        BigInteger.ONE, first.principalId(), first.keyId())),
                List.of(),
                List.of(),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationRegistry.of(
                List.of(binding(0, first)),
                List.of(
                    new AttestationCredentialRevocation(
                        BigInteger.ONE, UUID.randomUUID(), first.keyId())),
                List.of(),
                List.of(),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationRegistry.of(
                List.of(binding(1, first)),
                List.of(
                    new AttestationCredentialRevocation(
                        BigInteger.ONE, first.principalId(), first.keyId())),
                List.of(),
                List.of(),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationRegistry.of(
                List.of(binding(0, first)),
                List.of(
                    new AttestationCredentialRevocation(
                        BigInteger.ONE, first.principalId(), first.keyId()),
                    new AttestationCredentialRevocation(
                        BigInteger.TWO, first.principalId(), first.keyId())),
                List.of(),
                List.of(),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationRegistry.of(
                List.of(binding(0, first), rollover(1, replacement, second.keyId())),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
    KeyPair futurePredecessorPair = AttestationEd25519.generateKeyPair();
    TestCredential futurePredecessor =
        new TestCredential(
            first.principalId(),
            futurePredecessorPair,
            AttestationEd25519.keyId(futurePredecessorPair.getPublic()));
    KeyPair samePrincipalReplacementPair = AttestationEd25519.generateKeyPair();
    TestCredential samePrincipalReplacement =
        new TestCredential(
            first.principalId(),
            samePrincipalReplacementPair,
            AttestationEd25519.keyId(samePrincipalReplacementPair.getPublic()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationRegistry.of(
                List.of(
                    rollover(1, samePrincipalReplacement, futurePredecessor.keyId()),
                    binding(2, futurePredecessor)),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationRegistry.of(
                List.of(binding(0, first), rollover(1, replacement, first.keyId())),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationRegistry.of(
                List.of(binding(0, first), rollover(2, samePrincipalReplacement, first.keyId())),
                List.of(
                    new AttestationCredentialRevocation(
                        BigInteger.ONE, first.principalId(), first.keyId())),
                List.of(),
                List.of(),
                List.of()));
    AttestationCapabilityGrant duplicateGrant =
        new AttestationCapabilityGrant(
            BigInteger.ZERO,
            first.principalId(),
            AttestationCapability.POST,
            AttestationGrantState.GRANT);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationRegistry.of(
                List.of(binding(0, first)),
                List.of(),
                List.of(duplicateGrant, duplicateGrant),
                List.of(),
                List.of()));
    AttestationPolicyRule duplicateRule =
        new AttestationPolicyRule(BigInteger.ZERO, AttestationCapability.POST, 1);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationRegistry.of(
                List.of(binding(0, first)),
                List.of(),
                List.of(),
                List.of(duplicateRule, duplicateRule),
                List.of()));
    AttestationSystemWorkflowPolicy duplicateWorkflow = interimWorkflow(0, UUID.randomUUID(), true);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationRegistry.of(
                List.of(binding(0, first)),
                List.of(),
                List.of(),
                List.of(),
                List.of(duplicateWorkflow, duplicateWorkflow)));
  }

  @Test
  void validatesWorkflowPolicyLifecycleAndActiveKindUniqueness() {
    UUID originalWorkflowId = UUID.randomUUID();
    UUID replacementWorkflowId = UUID.randomUUID();
    assertDoesNotThrow(
        () ->
            AttestationRegistry.of(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                    interimWorkflow(0, originalWorkflowId, true),
                    interimWorkflow(1, originalWorkflowId, false),
                    interimWorkflow(1, replacementWorkflowId, true))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationRegistry.of(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                    interimWorkflow(0, originalWorkflowId, true),
                    interimWorkflow(1, replacementWorkflowId, true))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationRegistry.of(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                    interimWorkflow(0, originalWorkflowId, true),
                    interimWorkflow(1, originalWorkflowId, true))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationRegistry.of(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(interimWorkflow(0, originalWorkflowId, false))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationRegistry.of(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                    interimWorkflow(0, originalWorkflowId, true),
                    fiscalWorkflow(1, originalWorkflowId, false))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationRegistry.of(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                    interimWorkflow(0, originalWorkflowId, true),
                    interimWorkflow(1, originalWorkflowId, false),
                    interimWorkflow(2, originalWorkflowId, true))));
  }

  @Test
  void validatesWorkflowPolicyShapeAndConfigurationIdentity() {
    UUID workflowId = UUID.randomUUID();
    AttestationSystemWorkflowPolicy interim = interimWorkflow(0, workflowId, true);
    AttestationSystemWorkflowPolicy fiscal = fiscalWorkflow(0, workflowId, true);
    assertTrue(interim.hasSameConfiguration(interimWorkflow(1, workflowId, false)));
    assertFalse(interim.hasSameConfiguration(fiscal));
    assertFalse(
        fiscal.hasSameConfiguration(
            new AttestationSystemWorkflowPolicy(
                BigInteger.ZERO,
                workflowId,
                AttestationSystemWorkflowKind.FISCAL_YEAR_CLOSE,
                "4000",
                "3100",
                "3200",
                true)));
    assertFalse(
        fiscal.hasSameConfiguration(
            new AttestationSystemWorkflowPolicy(
                BigInteger.ZERO,
                workflowId,
                AttestationSystemWorkflowKind.FISCAL_YEAR_CLOSE,
                "3000",
                "4100",
                "3200",
                true)));
    assertFalse(
        fiscal.hasSameConfiguration(
            new AttestationSystemWorkflowPolicy(
                BigInteger.ZERO,
                workflowId,
                AttestationSystemWorkflowKind.FISCAL_YEAR_CLOSE,
                "3000",
                "3100",
                "4200",
                true)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationSystemWorkflowPolicy(
                BigInteger.ZERO,
                workflowId,
                AttestationSystemWorkflowKind.INTERIM_RESULT_SWEEP,
                "3000",
                "3100",
                null,
                true));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationSystemWorkflowPolicy(
                BigInteger.ZERO,
                workflowId,
                AttestationSystemWorkflowKind.INTERIM_RESULT_SWEEP,
                "3000",
                null,
                "3200",
                true));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationSystemWorkflowPolicy(
                BigInteger.ZERO,
                workflowId,
                AttestationSystemWorkflowKind.INTERIM_RESULT_SWEEP,
                " ",
                null,
                null,
                true));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationSystemWorkflowPolicy(
                BigInteger.ZERO,
                workflowId,
                AttestationSystemWorkflowKind.FISCAL_YEAR_CLOSE,
                "3000",
                null,
                "3200",
                true));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationSystemWorkflowPolicy(
                BigInteger.ZERO,
                workflowId,
                AttestationSystemWorkflowKind.FISCAL_YEAR_CLOSE,
                "3000",
                " ",
                "3200",
                true));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationSystemWorkflowPolicy(
                BigInteger.ZERO,
                workflowId,
                AttestationSystemWorkflowKind.FISCAL_YEAR_CLOSE,
                "3000",
                "3100",
                null,
                true));
  }
}
