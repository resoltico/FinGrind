package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.allFounderGrants;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.binding;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.credential;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.defaultRules;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Covers verified-registry inspection projection and its public value boundary. */
class AttestationRegistryInspectionTest {
  @Test
  void projectsCredentialsCapabilitiesAndTheLatestWorkflowPolicyAtTheVerifiedHead() {
    TestCredential founder = credential();
    TestCredential revoked = credential();
    UUID workflowId = UUID.randomUUID();
    AttestationRegistry registry =
        AttestationRegistry.fromVerifierFacts(
            List.of(binding(0, founder), binding(0, revoked)),
            List.of(
                new AttestationCredentialRetirement(
                    BigInteger.ONE,
                    revoked.principalId(),
                    revoked.keyId(),
                    AttestationCredentialRetirementState.REVOKED)),
            java.util.stream.Stream.concat(
                    allFounderGrants(founder.principalId()).stream(),
                    allFounderGrants(revoked.principalId()).stream())
                .toList(),
            defaultRules(2),
            List.of(
                AttestationAuthorizationTestSupport.interimWorkflow(0, workflowId, true),
                AttestationAuthorizationTestSupport.interimWorkflow(1, workflowId, false)));

    AttestationRegistryInspection inspection =
        registry.inspection(UUID.randomUUID(), BigInteger.ONE, "a".repeat(64));

    assertEquals(2, inspection.credentials().size());
    assertEquals(
        1,
        inspection.credentials().stream().filter(entry -> "revoked".equals(entry.state())).count());
    assertEquals(AttestationCapability.values().length, inspection.capabilityPolicies().size());
    assertEquals(
        2 * AttestationCapability.values().length, inspection.principalCapabilities().size());
    assertEquals(1, inspection.systemWorkflowPolicies().size());
    assertFalse(inspection.systemWorkflowPolicies().getFirst().active());
  }

  @Test
  void projectsRolloverPredecessorsAtTheVerifiedHead() {
    TestCredential founder = credential();
    TestCredential replacementRaw = credential();
    TestCredential replacement =
        new TestCredential(founder.principalId(), replacementRaw.pair(), replacementRaw.keyId());
    AttestationRegistry registry =
        AttestationRegistry.fromVerifierFacts(
            List.of(
                binding(0, founder),
                AttestationAuthorizationTestSupport.rollover(1, replacement, founder.keyId())),
            List.of(
                new AttestationCredentialRetirement(
                    BigInteger.ONE,
                    founder.principalId(),
                    founder.keyId(),
                    AttestationCredentialRetirementState.SUPERSEDED)),
            allFounderGrants(founder.principalId()),
            defaultRules(1),
            List.of());

    AttestationRegistryInspection inspection =
        registry.inspection(UUID.randomUUID(), BigInteger.ONE, "b".repeat(64));

    assertEquals(
        founder.keyId().hex(),
        inspection.credentials().stream()
            .filter(entry -> entry.keyId().equals(replacement.keyId().hex()))
            .findFirst()
            .orElseThrow()
            .predecessorKeyId());
    assertEquals(
        "superseded",
        inspection.credentials().stream()
            .filter(entry -> entry.keyId().equals(founder.keyId().hex()))
            .findFirst()
            .orElseThrow()
            .state());
  }

  @Test
  void rejectsMalformedInspectionValuesAtEveryPublicReadModelBoundary() {
    UUID principalId = UUID.randomUUID();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationRegistryInspection(
                principalId,
                BigInteger.valueOf(-1),
                "a".repeat(64),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationRegistryInspection(
                principalId,
                BigInteger.ONE.shiftLeft(Long.SIZE),
                "a".repeat(64),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
    new AttestationRegistryInspection.Credential(
        principalId,
        "key",
        "spki",
        "operator",
        "rollover",
        BigInteger.ZERO,
        "predecessor",
        "active");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationRegistryInspection(
                principalId,
                BigInteger.ZERO,
                "A".repeat(64),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationRegistryInspection.Credential(
                principalId, "key", "spki", "operator", "enroll", BigInteger.ZERO, " ", "active"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationRegistryInspection.CapabilityPolicy("post", 0, 0, 0, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationRegistryInspection.CapabilityPolicy("post", 1, -1, 0, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationRegistryInspection.CapabilityPolicy("post", 1, 0, -1, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationRegistryInspection.CapabilityPolicy("post", 1, 0, 0, -1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationRegistryInspection.PrincipalCapability(principalId, " ", true));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationRegistryInspection.SystemWorkflowPolicy(
                principalId, "interim-result-sweep", "3000", " ", null, true, BigInteger.ZERO));
  }
}
