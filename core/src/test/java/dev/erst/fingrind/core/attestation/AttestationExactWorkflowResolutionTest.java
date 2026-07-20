package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.allFounderGrants;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.binding;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.credential;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.defaultRules;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.fiscalWorkflow;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.interimWorkflow;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Proves that autonomous authorization resolves the exact workflow instance at its order. */
class AttestationExactWorkflowResolutionTest {
  @Test
  void rejectsFutureInactiveAndDifferentKindPoliciesForTheExactWorkflowId() {
    TestCredential operator = credential();
    UUID workflowId = UUID.randomUUID();

    assertFalse(
        registry(operator, List.of(interimWorkflow(2, workflowId, true)))
            .hasActiveSystemWorkflow(
                workflowId, AttestationSystemWorkflowKind.INTERIM_RESULT_SWEEP, BigInteger.ONE));
    assertFalse(
        registry(
                operator,
                List.of(
                    interimWorkflow(0, workflowId, true), interimWorkflow(1, workflowId, false)))
            .hasActiveSystemWorkflow(
                workflowId, AttestationSystemWorkflowKind.INTERIM_RESULT_SWEEP, BigInteger.ONE));
    assertFalse(
        registry(operator, List.of(fiscalWorkflow(0, workflowId, true)))
            .hasActiveSystemWorkflow(
                workflowId, AttestationSystemWorkflowKind.INTERIM_RESULT_SWEEP, BigInteger.ONE));
  }

  private static AttestationRegistry registry(
      TestCredential operator, List<AttestationSystemWorkflowPolicy> workflowPolicies) {
    return AttestationRegistry.fromVerifierFacts(
        List.of(binding(0, operator)),
        List.of(),
        allFounderGrants(operator.principalId()),
        defaultRules(1),
        workflowPolicies);
  }
}
