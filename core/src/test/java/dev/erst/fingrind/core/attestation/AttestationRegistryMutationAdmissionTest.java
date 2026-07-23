package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.allFounderGrants;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.binding;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.credential;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.defaultRules;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.rollover;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.founder;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Covers exact target admission before the registry mutation is signed or appended. */
class AttestationRegistryMutationAdmissionTest {
  @Test
  void admitsEveryMutationFamilyWhenItsTargetIsValidAtTheAuthenticatedHead() {
    TestCredential founder = credential();
    TestCredential enrollment = credential();
    TestCredential replacement = credential();
    AttestationRegistry registry = AttestationRegistry.genesis(List.of(founder(founder)));

    assertDoesNotThrow(
        () ->
            registry.requireMutationAdmissible(
                new AttestationRegistryMutation.EnrollKey(
                    enrollment.principalId(),
                    publicCredential(enrollment),
                    AttestationCredentialPurpose.OPERATOR),
                BigInteger.ZERO));
    assertDoesNotThrow(
        () ->
            registry.requireMutationAdmissible(
                new AttestationRegistryMutation.RolloverKey(
                    founder.principalId(),
                    publicCredential(replacement),
                    AttestationCredentialPurpose.OPERATOR,
                    publicCredential(founder)),
                BigInteger.ZERO));
    assertDoesNotThrow(
        () ->
            registry.requireMutationAdmissible(
                new AttestationRegistryMutation.RevokeKey(
                    founder.principalId(), publicCredential(founder), Optional.empty()),
                BigInteger.ZERO));
    assertDoesNotThrow(
        () ->
            registry.requireMutationAdmissible(
                new AttestationRegistryMutation.AlterPolicy(
                    List.of(
                        new AttestationRegistryMutation.PolicyRule(AttestationCapability.POST, 1)),
                    List.of(),
                    List.of()),
                BigInteger.ZERO));
  }

  @Test
  void reportsEachNonRevokedMutationTargetRefusalExactly() {
    TestCredential founder = credential();
    TestCredential replacement = credential();
    AttestationRegistry registry = AttestationRegistry.genesis(List.of(founder(founder)));

    assertFailure(
        AttestationAuthorizationFailure.DUPLICATE_PRINCIPAL,
        () ->
            registry.requireMutationAdmissible(
                new AttestationRegistryMutation.EnrollKey(
                    founder.principalId(),
                    publicCredential(replacement),
                    AttestationCredentialPurpose.OPERATOR),
                BigInteger.ZERO));
    assertFailure(
        AttestationAuthorizationFailure.DUPLICATE_KEY,
        () ->
            registry.requireMutationAdmissible(
                new AttestationRegistryMutation.EnrollKey(
                    UUID.randomUUID(),
                    publicCredential(founder),
                    AttestationCredentialPurpose.OPERATOR),
                BigInteger.ZERO));
    assertFailure(
        AttestationAuthorizationFailure.KEY_NOT_ENROLLED,
        () ->
            registry.requireMutationAdmissible(
                new AttestationRegistryMutation.RevokeKey(
                    UUID.randomUUID(), publicCredential(replacement), Optional.empty()),
                BigInteger.ZERO));
    assertFailure(
        AttestationAuthorizationFailure.KEY_PRINCIPAL_MISMATCH,
        () ->
            registry.requireMutationAdmissible(
                new AttestationRegistryMutation.RevokeKey(
                    UUID.randomUUID(), publicCredential(founder), Optional.empty()),
                BigInteger.ZERO));
  }

  @Test
  void rejectsRevocationOfAnAlreadyRevokedCredential() {
    TestCredential founder = credential();
    AttestationRegistry registry =
        AttestationRegistry.fromVerifierFacts(
            List.of(binding(0, founder)),
            List.of(
                new AttestationCredentialRetirement(
                    BigInteger.ONE,
                    founder.principalId(),
                    founder.keyId(),
                    AttestationCredentialRetirementState.REVOKED)),
            allFounderGrants(founder.principalId()),
            defaultRules(1),
            List.of());

    assertFailure(
        AttestationAuthorizationFailure.KEY_REVOKED,
        () ->
            registry.requireMutationAdmissible(
                new AttestationRegistryMutation.RevokeKey(
                    founder.principalId(), publicCredential(founder), Optional.empty()),
                BigInteger.ONE));
  }

  @Test
  void rejectsMutationOfAnAlreadySupersededCredential() {
    TestCredential predecessor = credential();
    TestCredential rawReplacement = credential();
    TestCredential replacement =
        new TestCredential(
            predecessor.principalId(), rawReplacement.pair(), rawReplacement.keyId());
    AttestationRegistry registry =
        AttestationRegistry.fromVerifierFacts(
            List.of(binding(0, predecessor), rollover(1, replacement, predecessor.keyId())),
            List.of(
                new AttestationCredentialRetirement(
                    BigInteger.ONE,
                    predecessor.principalId(),
                    predecessor.keyId(),
                    AttestationCredentialRetirementState.SUPERSEDED)),
            allFounderGrants(predecessor.principalId()),
            defaultRules(1),
            List.of());

    assertFailure(
        AttestationAuthorizationFailure.KEY_SUPERSEDED,
        () ->
            registry.requireMutationAdmissible(
                new AttestationRegistryMutation.RevokeKey(
                    predecessor.principalId(), publicCredential(predecessor), Optional.empty()),
                BigInteger.ONE));
  }

  @Test
  void doesNotTreatAFutureBindingAsPresentAtAnEarlierHead() {
    TestCredential future = credential();
    AttestationRegistry registry =
        AttestationRegistry.fromVerifierFacts(
            List.of(binding(1, future)),
            List.of(),
            allFounderGrants(future.principalId()),
            defaultRules(1),
            List.of());

    assertDoesNotThrow(
        () ->
            registry.requireMutationAdmissible(
                new AttestationRegistryMutation.EnrollKey(
                    future.principalId(),
                    publicCredential(future),
                    AttestationCredentialPurpose.OPERATOR),
                BigInteger.ZERO));
  }

  private static AttestationPublicCredential publicCredential(TestCredential credential) {
    return new AttestationPublicCredential(credential.pair().getPublic().getEncoded());
  }
}
