package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.allFounderGrants;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.binding;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.credential;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.fiscalWorkflow;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.interimWorkflow;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Proves that each accepted registry mutation preserves reachable quorums. */
class AttestationRegistryCapacityTest {
  @Test
  void acceptsThePolicyMaximumWhenEveryEligiblePrincipalCanSign() {
    List<TestCredential> operators =
        java.util.stream.IntStream.range(0, AttestationAuthorizationLimits.MAXIMUM_QUORUM)
            .mapToObj(ignored -> credential())
            .toList();
    List<AttestationCredentialBinding> bindings =
        operators.stream().map(operator -> binding(0, operator)).toList();
    List<AttestationCapabilityGrant> grants =
        operators.stream()
            .flatMap(operator -> allFounderGrants(operator.principalId()).stream())
            .toList();

    assertDoesNotThrow(
        () ->
            AttestationRegistry.fromAcceptedHistory(
                bindings,
                List.of(),
                grants,
                List.of(
                    new AttestationPolicyRule(
                        BigInteger.ZERO,
                        AttestationCapability.POST,
                        AttestationAuthorizationLimits.MAXIMUM_QUORUM)),
                List.of()));
  }

  @Test
  void rejectsAnAcceptedHistoryWhoseSystemWorkflowQuorumWouldBeImpossible() {
    TestCredential firstOperator = credential();
    TestCredential secondOperator = credential();
    TestCredential system = credential();
    List<AttestationCapabilityGrant> grants = new ArrayList<>();
    grants.addAll(allFounderGrants(firstOperator.principalId()));
    grants.addAll(allFounderGrants(secondOperator.principalId()));
    grants.addAll(allFounderGrants(system.principalId()));
    assertFailure(
        AttestationAuthorizationFailure.POLICY_CAPACITY_INVALID,
        () ->
            AttestationRegistry.fromAcceptedHistory(
                List.of(
                    binding(0, firstOperator),
                    binding(0, secondOperator),
                    binding(0, system, AttestationCredentialPurpose.SYSTEM)),
                List.of(),
                grants,
                List.of(
                    new AttestationPolicyRule(
                        BigInteger.ONE, AttestationCapability.CLOSE_PERIOD, 2)),
                List.of(interimWorkflow(1, UUID.randomUUID(), true))));
  }

  @Test
  void acceptsReachableAcceptedHistoryCapacityAndAnEmptyPolicyHistory() {
    TestCredential firstOperator = credential();
    TestCredential secondOperator = credential();
    TestCredential firstSystem = credential();
    TestCredential secondSystem = credential();
    List<AttestationCapabilityGrant> grants = new ArrayList<>();
    for (TestCredential credential :
        List.of(firstOperator, secondOperator, firstSystem, secondSystem)) {
      grants.addAll(allFounderGrants(credential.principalId()));
    }
    assertDoesNotThrow(
        () ->
            AttestationRegistry.fromAcceptedHistory(
                List.of(
                    binding(0, firstOperator),
                    binding(0, secondOperator),
                    binding(0, firstSystem, AttestationCredentialPurpose.SYSTEM),
                    binding(0, secondSystem, AttestationCredentialPurpose.SYSTEM)),
                List.of(),
                grants,
                List.of(
                    new AttestationPolicyRule(
                        BigInteger.ONE, AttestationCapability.CLOSE_PERIOD, 2)),
                List.of(fiscalWorkflow(1, UUID.randomUUID(), true))));

    assertDoesNotThrow(
        () ->
            AttestationRegistry.fromAcceptedHistory(
                List.of(binding(0, firstOperator)),
                List.of(),
                List.of(
                    new AttestationCapabilityGrant(
                        BigInteger.ZERO,
                        firstOperator.principalId(),
                        AttestationCapability.CLOSE_PERIOD,
                        AttestationGrantState.GRANT)),
                List.of(
                    new AttestationPolicyRule(
                        BigInteger.ZERO, AttestationCapability.CLOSE_PERIOD, 1)),
                List.of()));

    AttestationRegistry noPolicy =
        AttestationRegistry.fromAcceptedHistory(
            List.of(binding(0, firstOperator)), List.of(), List.of(), List.of(), List.of());
    assertFailure(
        AttestationAuthorizationFailure.CAPABILITY_INVALID,
        () -> noPolicy.quorumAt(AttestationCapability.POST, BigInteger.ZERO));

    assertDoesNotThrow(
        () ->
            AttestationRegistry.fromAcceptedHistory(
                List.of(binding(0, firstOperator)),
                List.of(),
                List.of(
                    new AttestationCapabilityGrant(
                        BigInteger.ZERO,
                        firstOperator.principalId(),
                        AttestationCapability.POST,
                        AttestationGrantState.GRANT)),
                List.of(new AttestationPolicyRule(BigInteger.ZERO, AttestationCapability.POST, 1)),
                List.of()));
  }

  @Test
  void validatesOtherAcceptedHistoryCapacityCases() {
    TestCredential operator = credential();
    assertFailure(
        AttestationAuthorizationFailure.POLICY_CAPACITY_INVALID,
        () ->
            AttestationRegistry.fromAcceptedHistory(
                List.of(binding(0, operator)),
                List.of(),
                allFounderGrants(operator.principalId()),
                List.of(new AttestationPolicyRule(BigInteger.ZERO, AttestationCapability.POST, 2)),
                List.of()));

    TestCredential firstSystem = credential();
    TestCredential secondSystem = credential();
    assertFailure(
        AttestationAuthorizationFailure.POLICY_CAPACITY_INVALID,
        () ->
            AttestationRegistry.fromAcceptedHistory(
                List.of(
                    binding(0, firstSystem, AttestationCredentialPurpose.SYSTEM),
                    binding(0, secondSystem, AttestationCredentialPurpose.SYSTEM)),
                List.of(),
                List.of(
                    new AttestationCapabilityGrant(
                        BigInteger.ZERO,
                        firstSystem.principalId(),
                        AttestationCapability.POST,
                        AttestationGrantState.GRANT),
                    new AttestationCapabilityGrant(
                        BigInteger.ZERO,
                        secondSystem.principalId(),
                        AttestationCapability.POST,
                        AttestationGrantState.GRANT)),
                List.of(new AttestationPolicyRule(BigInteger.ZERO, AttestationCapability.POST, 1)),
                List.of()));

    assertDoesNotThrow(
        () ->
            AttestationRegistry.fromAcceptedHistory(
                List.of(
                    binding(0, firstSystem, AttestationCredentialPurpose.SYSTEM),
                    binding(0, secondSystem, AttestationCredentialPurpose.SYSTEM)),
                List.of(),
                List.of(
                    new AttestationCapabilityGrant(
                        BigInteger.ZERO,
                        firstSystem.principalId(),
                        AttestationCapability.ANCHOR,
                        AttestationGrantState.GRANT),
                    new AttestationCapabilityGrant(
                        BigInteger.ZERO,
                        secondSystem.principalId(),
                        AttestationCapability.ANCHOR,
                        AttestationGrantState.GRANT)),
                List.of(
                    new AttestationPolicyRule(BigInteger.ZERO, AttestationCapability.ANCHOR, 2)),
                List.of()));

    TestCredential initialSystem = credential();
    TestCredential replacementSystem = credential();
    UUID workflowId = UUID.randomUUID();
    assertFailure(
        AttestationAuthorizationFailure.POLICY_CAPACITY_INVALID,
        () ->
            AttestationRegistry.fromAcceptedHistory(
                List.of(
                    binding(0, operator),
                    binding(0, initialSystem, AttestationCredentialPurpose.SYSTEM),
                    binding(2, replacementSystem, AttestationCredentialPurpose.SYSTEM)),
                List.of(
                    new AttestationCredentialRevocation(
                        BigInteger.ONE, initialSystem.principalId(), initialSystem.keyId())),
                List.of(
                    new AttestationCapabilityGrant(
                        BigInteger.ZERO,
                        operator.principalId(),
                        AttestationCapability.CLOSE_PERIOD,
                        AttestationGrantState.GRANT),
                    new AttestationCapabilityGrant(
                        BigInteger.ZERO,
                        initialSystem.principalId(),
                        AttestationCapability.CLOSE_PERIOD,
                        AttestationGrantState.GRANT),
                    new AttestationCapabilityGrant(
                        BigInteger.TWO,
                        replacementSystem.principalId(),
                        AttestationCapability.CLOSE_PERIOD,
                        AttestationGrantState.GRANT)),
                List.of(
                    new AttestationPolicyRule(
                        BigInteger.ZERO, AttestationCapability.CLOSE_PERIOD, 1)),
                List.of(interimWorkflow(0, workflowId, true))));
  }
}
