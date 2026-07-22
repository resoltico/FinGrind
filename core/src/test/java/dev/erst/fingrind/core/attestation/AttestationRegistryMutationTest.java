package dev.erst.fingrind.core.attestation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Covers the closed public registry-mutation model and its canonical preimage projections. */
class AttestationRegistryMutationTest {
  private static final UUID PRINCIPAL_ID = UUID.fromString("01234567-89ab-4cde-8fab-0123456789ab");
  private static final UUID WORKFLOW_ID = UUID.fromString("11111111-2222-4333-8444-555555555555");
  private static final AttestationPublicCredential CREDENTIAL = credential((byte) 0x01);
  private static final AttestationPublicCredential REPLACEMENT_CREDENTIAL = credential((byte) 0x02);

  @Test
  void projectsEveryClosedRegistryMutationToCanonicalNonemptyPreimages() {
    AttestationRegistryMutation.EnrollKey enrollment =
        new AttestationRegistryMutation.EnrollKey(
            PRINCIPAL_ID, CREDENTIAL, AttestationCredentialPurpose.OPERATOR);
    AttestationRegistryMutation.RolloverKey rollover =
        new AttestationRegistryMutation.RolloverKey(
            PRINCIPAL_ID, REPLACEMENT_CREDENTIAL, AttestationCredentialPurpose.SYSTEM, CREDENTIAL);
    AttestationRegistryMutation.RevokeKey revocation =
        new AttestationRegistryMutation.RevokeKey(
            PRINCIPAL_ID, CREDENTIAL, Optional.of("hardware retired"));
    AttestationRegistryMutation.AlterPolicy policy =
        new AttestationRegistryMutation.AlterPolicy(
            List.of(
                new AttestationRegistryMutation.PolicyRule(AttestationCapability.CLOSE_PERIOD, 1)),
            List.of(
                new AttestationRegistryMutation.CapabilityGrant(
                    PRINCIPAL_ID, AttestationCapability.CLOSE_PERIOD, AttestationGrantState.GRANT)),
            List.of(
                new AttestationRegistryMutation.SystemWorkflowPolicy(
                    WORKFLOW_ID,
                    AttestationSystemWorkflowKind.INTERIM_RESULT_SWEEP,
                    "3000",
                    null,
                    null,
                    true),
                new AttestationRegistryMutation.SystemWorkflowPolicy(
                    UUID.fromString("66666666-7777-4888-8999-aaaaaaaaaaaa"),
                    AttestationSystemWorkflowKind.FISCAL_YEAR_CLOSE,
                    "3000",
                    "3100",
                    "3200",
                    false)));

    assertEquals(AttestationOperationKind.ENROLL_KEY, enrollment.operationKind());
    assertEquals(AttestationOperationKind.ROLLOVER_KEY, rollover.operationKind());
    assertEquals(AttestationOperationKind.REVOKE_KEY, revocation.operationKind());
    assertEquals(AttestationOperationKind.ALTER_POLICY, policy.operationKind());
    assertNotEquals(CREDENTIAL.keyId()[0], REPLACEMENT_CREDENTIAL.keyId()[0]);

    assertCanonicalPreimages(enrollment);
    assertCanonicalPreimages(rollover);
    assertCanonicalPreimages(revocation);
    assertCanonicalPreimages(policy);
  }

  @Test
  void acceptsAnAbsentRevocationReasonAndEveryPolicyBoundary() {
    AttestationRegistryMutation.RevokeKey revocation =
        new AttestationRegistryMutation.RevokeKey(PRINCIPAL_ID, CREDENTIAL, Optional.empty());
    AttestationRegistryMutation.PolicyRule minimum =
        new AttestationRegistryMutation.PolicyRule(AttestationCapability.POST, 1);
    AttestationRegistryMutation.PolicyRule maximum =
        new AttestationRegistryMutation.PolicyRule(AttestationCapability.POST, 64);

    assertTrue(revocation.reason().isEmpty());
    assertEquals(1, minimum.quorum());
    assertEquals(64, maximum.quorum());
    assertCanonicalPreimages(revocation);
  }

  @Test
  void refusesAmbiguousOrInvalidRegistryMutationsBeforeSigning() {
    AttestationRegistryMutation.PolicyRule rule =
        new AttestationRegistryMutation.PolicyRule(AttestationCapability.CLOSE_PERIOD, 1);
    AttestationRegistryMutation.CapabilityGrant grant =
        new AttestationRegistryMutation.CapabilityGrant(
            PRINCIPAL_ID, AttestationCapability.CLOSE_PERIOD, AttestationGrantState.GRANT);
    AttestationRegistryMutation.SystemWorkflowPolicy workflow =
        new AttestationRegistryMutation.SystemWorkflowPolicy(
            WORKFLOW_ID,
            AttestationSystemWorkflowKind.INTERIM_RESULT_SWEEP,
            "3000",
            null,
            null,
            true);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationRegistryMutation.RolloverKey(
                PRINCIPAL_ID, CREDENTIAL, AttestationCredentialPurpose.OPERATOR, CREDENTIAL));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationRegistryMutation.RevokeKey(PRINCIPAL_ID, CREDENTIAL, Optional.of(" ")));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationRegistryMutation.AlterPolicy(List.of(), List.of(), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationRegistryMutation.AlterPolicy(List.of(rule, rule), List.of(), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationRegistryMutation.AlterPolicy(
                List.of(), List.of(grant, grant), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationRegistryMutation.AlterPolicy(
                List.of(), List.of(), List.of(workflow, workflow)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationRegistryMutation.PolicyRule(AttestationCapability.POST, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationRegistryMutation.PolicyRule(AttestationCapability.POST, 65));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationRegistryMutation.SystemWorkflowPolicy(
                WORKFLOW_ID,
                AttestationSystemWorkflowKind.FISCAL_YEAR_CLOSE,
                "3000",
                null,
                "3200",
                true));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationRegistryMutation.SystemWorkflowPolicy(
                WORKFLOW_ID,
                AttestationSystemWorkflowKind.FISCAL_YEAR_CLOSE,
                "3000",
                "3100",
                " ",
                true));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationRegistryMutation.SystemWorkflowPolicy(
                WORKFLOW_ID,
                AttestationSystemWorkflowKind.INTERIM_RESULT_SWEEP,
                "3000",
                "3100",
                null,
                true));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationRegistryMutation.SystemWorkflowPolicy(
                WORKFLOW_ID,
                AttestationSystemWorkflowKind.INTERIM_RESULT_SWEEP,
                "3000",
                null,
                "3200",
                true));
  }

  private static void assertCanonicalPreimages(AttestationRegistryMutation mutation) {
    AttestationOperationPreimages preimages = mutation.preimages();
    assertNotEquals(0, preimages.request().length);
    assertNotEquals(0, preimages.effect().length);
    assertEquals(
        preimages.request().length,
        AttestationPreimage.decode(
                preimages.request(), AttestationAuthorizationFailure.PREIMAGE_INVALID)
            .encoded()
            .length);
    assertEquals(
        preimages.effect().length,
        AttestationPreimage.decode(
                preimages.effect(), AttestationAuthorizationFailure.PREIMAGE_INVALID)
            .encoded()
            .length);
  }

  private static AttestationPublicCredential credential(byte finalByte) {
    byte[] spki =
        java.util.HexFormat.of()
            .parseHex(
                "302a300506032b657003210003a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8");
    spki[spki.length - 1] = finalByte;
    return new AttestationPublicCredential(spki);
  }
}
