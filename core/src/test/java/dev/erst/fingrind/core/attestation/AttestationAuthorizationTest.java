package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.PAYLOAD;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.POSITION;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.allFounderGrants;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.authorize;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.binding;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.credential;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.defaultRules;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.fiscalWorkflow;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.founder;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.interimWorkflow;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.manifestContext;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.operationContext;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.orderedEntries;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.postRule;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.receiptContext;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.registry;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.signedEnvelope;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Proves positional registry folding and the shared envelope failure precedence. */
class AttestationAuthorizationTest {
  @Test
  void acceptsExactlyTheEligibleOrderedOperatorQuorumAtItsResolvingPosition() {
    TestCredential first = credential();
    TestCredential second = credential();
    AttestationRegistry registry =
        registry(List.of(first, second), List.of(), List.of(postRule(0, 2)));

    assertDoesNotThrow(
        () ->
            AttestationAuthorization.requireAuthorized(
                registry, operationContext(), signedEnvelope(first, second)));
  }

  @Test
  void derivesCapabilityAndHistoricalPositionFromThePayloadBoundAuthorizationContext() {
    TestCredential signer = credential();
    AttestationAuthorizationContext context =
        operationContext(AttestationOperationKind.REKEY_BOOK, AttestationSourceChannel.CLI);
    AttestationRegistry registry =
        AttestationRegistry.fromVerifierFacts(
            List.of(binding(0, signer)),
            List.of(),
            List.of(
                new AttestationCapabilityGrant(
                    BigInteger.ZERO,
                    signer.principalId(),
                    AttestationCapability.REKEY,
                    AttestationGrantState.GRANT)),
            List.of(new AttestationPolicyRule(BigInteger.ZERO, AttestationCapability.REKEY, 1)),
            List.of());

    assertEquals(POSITION, context.resolvingOrder());
    assertEquals(AttestationCapability.REKEY, context.capability());
    assertDoesNotThrow(
        () ->
            AttestationAuthorization.requireAuthorized(
                registry, context, signedEnvelope(context, signer)));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationAuthorization.requireAuthorized(registry, context, signedEnvelope(signer)));
  }

  @Test
  void rejectsGenesisAsAnOperationAuthorizationContext() {
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            operationContext(
                BigInteger.ZERO,
                AttestationOperationKind.POST_ENTRY,
                AttestationSourceChannel.CLI));
  }

  @Test
  void resolvesTheLatestPolicyAndGrantAtOrBeforeThePosition() {
    TestCredential first = credential();
    TestCredential second = credential();
    List<AttestationCapabilityGrant> grants = new ArrayList<>();
    grants.addAll(allFounderGrants(first.principalId()));
    grants.addAll(allFounderGrants(second.principalId()));
    grants.add(
        new AttestationCapabilityGrant(
            BigInteger.ONE,
            second.principalId(),
            AttestationCapability.POST,
            AttestationGrantState.REVOKE));
    AttestationRegistry registry =
        AttestationRegistry.fromVerifierFacts(
            List.of(binding(0, first), binding(0, second)),
            List.of(),
            grants,
            List.of(postRule(0, 1), postRule(1, 2)),
            List.of());

    assertFailure(
        AttestationAuthorizationFailure.CAPABILITY_INVALID,
        () ->
            AttestationAuthorization.requireAuthorized(
                registry, operationContext(), signedEnvelope(first, second)));
  }

  @Test
  void keepsNewBindingsAndRevocationsPositional() {
    TestCredential founder = credential();
    TestCredential later = credential();
    AttestationRegistry futureBinding =
        AttestationRegistry.fromVerifierFacts(
            List.of(binding(0, founder), binding(2, later)),
            List.of(),
            allFounderGrants(founder.principalId()),
            defaultRules(1),
            List.of());
    assertFailure(
        AttestationAuthorizationFailure.KEY_NOT_ENROLLED,
        () ->
            AttestationAuthorization.requireAuthorized(
                futureBinding, operationContext(), signedEnvelope(later)));

    AttestationRegistry revoked =
        AttestationRegistry.fromVerifierFacts(
            List.of(binding(0, founder)),
            List.of(
                new AttestationCredentialRevocation(
                    BigInteger.ONE, founder.principalId(), founder.keyId())),
            allFounderGrants(founder.principalId()),
            defaultRules(1),
            List.of());
    assertFailure(
        AttestationAuthorizationFailure.KEY_REVOKED,
        () ->
            AttestationAuthorization.requireAuthorized(
                revoked, operationContext(), signedEnvelope(founder)));
  }

  @Test
  void rejectsEverySharedEnvelopeStructuralFailureBeforeSignatureVerification() {
    TestCredential first = credential();
    TestCredential second = credential();
    AttestationRegistry quorumTwo =
        registry(List.of(first, second), List.of(), List.of(postRule(0, 2)));
    List<AttestationSignatureEntry> ordered = orderedEntries(first, second);

    assertFailure(
        AttestationAuthorizationFailure.QUORUM_BELOW,
        () ->
            authorize(
                quorumTwo,
                new AttestationAuthorizationEnvelope(PAYLOAD, List.of(ordered.getFirst()))));
    assertFailure(
        AttestationAuthorizationFailure.QUORUM_EXCESS,
        () ->
            authorize(
                AttestationRegistry.genesis(List.of(founder(first))),
                signedEnvelope(first, second)));

    AttestationSignatureEntry firstEntry = ordered.getFirst();
    AttestationSignatureEntry secondEntry = ordered.getLast();
    assertFailure(
        AttestationAuthorizationFailure.DUPLICATE_PRINCIPAL,
        () ->
            authorize(
                quorumTwo,
                new AttestationAuthorizationEnvelope(
                    PAYLOAD,
                    List.of(
                        firstEntry,
                        new AttestationSignatureEntry(
                            firstEntry.principalId(),
                            secondEntry.keyId(),
                            secondEntry.signature())))));
    assertFailure(
        AttestationAuthorizationFailure.DUPLICATE_KEY,
        () ->
            authorize(
                quorumTwo,
                new AttestationAuthorizationEnvelope(
                    PAYLOAD,
                    List.of(
                        firstEntry,
                        new AttestationSignatureEntry(
                            secondEntry.principalId(),
                            firstEntry.keyId(),
                            secondEntry.signature())))));
    assertFailure(
        AttestationAuthorizationFailure.ENVELOPE_ORDER_INVALID,
        () ->
            authorize(
                quorumTwo,
                new AttestationAuthorizationEnvelope(PAYLOAD, List.of(secondEntry, firstEntry))));
  }

  @Test
  void distinguishesPrincipalBindingAlgorithmSignatureCapabilityAndPurposeFailures() {
    TestCredential first = credential();
    TestCredential second = credential();
    AttestationRegistry quorumTwo =
        registry(List.of(first, second), List.of(), List.of(postRule(0, 2)));
    List<AttestationSignatureEntry> entries = orderedEntries(first, second);

    assertFailure(
        AttestationAuthorizationFailure.KEY_PRINCIPAL_MISMATCH,
        () ->
            authorize(
                quorumTwo,
                new AttestationAuthorizationEnvelope(
                    PAYLOAD,
                    List.of(
                        new AttestationSignatureEntry(
                            UUID.randomUUID(),
                            entries.getFirst().keyId(),
                            entries.getFirst().signature()),
                        entries.getLast()))));
    byte[] tampered = entries.getFirst().signature();
    tampered[0] ^= 1;
    assertFailure(
        AttestationAuthorizationFailure.SIGNATURE_INVALID,
        () ->
            authorize(
                quorumTwo,
                new AttestationAuthorizationEnvelope(
                    PAYLOAD,
                    List.of(
                        new AttestationSignatureEntry(
                            entries.getFirst().principalId(), entries.getFirst().keyId(), tampered),
                        entries.getLast()))));

    AttestationRegistry noGrant =
        AttestationRegistry.fromVerifierFacts(
            List.of(binding(0, first), binding(0, second)),
            List.of(),
            allFounderGrants(first.principalId()),
            List.of(postRule(0, 2)),
            List.of());
    assertFailure(
        AttestationAuthorizationFailure.CAPABILITY_INVALID,
        () -> authorize(noGrant, signedEnvelope(first, second)));

    TestCredential system = credential();
    AttestationRegistry systemRegistry =
        AttestationRegistry.fromVerifierFacts(
            List.of(binding(0, system, AttestationCredentialPurpose.SYSTEM)),
            List.of(),
            allFounderGrants(system.principalId()),
            defaultRules(1),
            List.of());
    assertFailure(
        AttestationAuthorizationFailure.CREDENTIAL_PURPOSE_INVALID,
        () -> authorize(systemRegistry, signedEnvelope(system)));

    UUID x25519Principal = UUID.randomUUID();
    byte[] x25519Spki =
        java.util.HexFormat.of()
            .parseHex(
                "302a300506032b656e032100000102030405060708090a0b0c0d0e0f101112131415161718191a1f");
    AttestationHash x25519KeyId = AttestationHash.sha256(x25519Spki);
    AttestationRegistry x25519Registry =
        AttestationRegistry.fromVerifierFacts(
            List.of(
                new AttestationCredentialBinding(
                    BigInteger.ZERO,
                    x25519Principal,
                    x25519KeyId,
                    AttestationCredentialBinding.BindingAction.ENROLL,
                    AttestationSpki.of(x25519Spki),
                    AttestationCredentialPurpose.OPERATOR,
                    null)),
            List.of(),
            allFounderGrants(x25519Principal),
            defaultRules(1),
            List.of());
    assertFalse(x25519Registry.isEligible(x25519Principal, AttestationCapability.POST, POSITION));
    assertEquals(0, x25519Registry.eligiblePrincipalCount(AttestationCapability.POST, POSITION));
    assertFailure(
        AttestationAuthorizationFailure.KEY_ALGORITHM_INVALID,
        () ->
            authorize(
                x25519Registry,
                new AttestationAuthorizationEnvelope(
                    PAYLOAD,
                    List.of(
                        new AttestationSignatureEntry(
                            x25519Principal, x25519KeyId, new byte[64])))));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationRegistry.fromAcceptedHistory(
                List.of(
                    new AttestationCredentialBinding(
                        BigInteger.ZERO,
                        x25519Principal,
                        x25519KeyId,
                        AttestationCredentialBinding.BindingAction.ENROLL,
                        AttestationSpki.of(x25519Spki),
                        AttestationCredentialPurpose.OPERATOR,
                        null)),
                List.of(),
                allFounderGrants(x25519Principal),
                defaultRules(1),
                List.of()));
    AttestationRegistry revokedX25519Registry =
        AttestationRegistry.fromVerifierFacts(
            List.of(
                new AttestationCredentialBinding(
                    BigInteger.ZERO,
                    x25519Principal,
                    x25519KeyId,
                    AttestationCredentialBinding.BindingAction.ENROLL,
                    AttestationSpki.of(x25519Spki),
                    AttestationCredentialPurpose.OPERATOR,
                    null)),
            List.of(
                new AttestationCredentialRevocation(BigInteger.ONE, x25519Principal, x25519KeyId)),
            allFounderGrants(x25519Principal),
            defaultRules(1),
            List.of());
    assertFailure(
        AttestationAuthorizationFailure.KEY_ALGORITHM_INVALID,
        () ->
            authorize(
                revokedX25519Registry,
                new AttestationAuthorizationEnvelope(
                    PAYLOAD,
                    List.of(
                        new AttestationSignatureEntry(
                            x25519Principal, x25519KeyId, new byte[64])))));
  }

  @Test
  void requiresAllDistinctGenesisFoundersToSign() {
    TestCredential first = credential();
    TestCredential second = credential();

    assertDoesNotThrow(
        () ->
            AttestationAuthorization.requireGenesis(
                List.of(founder(first), founder(second)), signedEnvelope(first, second)));
    assertFailure(
        AttestationAuthorizationFailure.GENESIS_INVALID,
        () ->
            AttestationAuthorization.requireGenesis(
                List.of(founder(first), founder(second)), signedEnvelope(first)));
  }

  @Test
  void allowsManifestAndReceiptQuorumsWithoutAnOperationSourceChannelPurpose() {
    TestCredential system = credential();
    AttestationRegistry registry =
        AttestationRegistry.fromVerifierFacts(
            List.of(binding(0, system, AttestationCredentialPurpose.SYSTEM)),
            List.of(),
            allFounderGrants(system.principalId()),
            defaultRules(1),
            List.of());

    assertDoesNotThrow(
        () ->
            AttestationAuthorization.requireAuthorized(
                registry, manifestContext(), signedEnvelope(manifestContext(), system)));
    assertDoesNotThrow(
        () ->
            AttestationAuthorization.requireAuthorized(
                registry, receiptContext(), signedEnvelope(receiptContext(), system)));
  }

  @Test
  void permitsSystemSourceOnlyForAutonomousCloseKinds() {
    for (AttestationOperationKind operationKind : AttestationOperationKind.values()) {
      if (operationKind == AttestationOperationKind.INTERIM_RESULT_SWEEP
          || operationKind == AttestationOperationKind.FISCAL_YEAR_CLOSE) {
        assertDoesNotThrow(() -> operationContext(operationKind, AttestationSourceChannel.SYSTEM));
      } else {
        assertFailure(
            AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
            () -> operationContext(operationKind, AttestationSourceChannel.SYSTEM));
      }
    }
  }

  @Test
  void requiresTheMatchingActiveWorkflowForASystemClose() {
    TestCredential operator = credential();
    TestCredential system = credential();
    List<AttestationCapabilityGrant> grants = new ArrayList<>();
    grants.addAll(allFounderGrants(operator.principalId()));
    grants.addAll(allFounderGrants(system.principalId()));
    List<AttestationCredentialBinding> bindings =
        List.of(binding(0, operator), binding(0, system, AttestationCredentialPurpose.SYSTEM));
    List<AttestationPolicyRule> rules =
        List.of(new AttestationPolicyRule(BigInteger.ZERO, AttestationCapability.CLOSE_PERIOD, 1));
    AttestationAuthorizationContext systemInterimContext =
        operationContext(
            AttestationOperationKind.INTERIM_RESULT_SWEEP, AttestationSourceChannel.SYSTEM);

    AttestationRegistry noWorkflow =
        AttestationRegistry.fromVerifierFacts(bindings, List.of(), grants, rules, List.of());
    assertFalse(
        noWorkflow.hasActiveSystemWorkflow(
            AttestationSystemWorkflowKind.INTERIM_RESULT_SWEEP, POSITION));
    assertFailure(
        AttestationAuthorizationFailure.SYSTEM_DERIVATION_INVALID,
        () ->
            AttestationAuthorization.requireAuthorized(
                noWorkflow, systemInterimContext, signedEnvelope(systemInterimContext, system)));

    AttestationRegistry wrongWorkflow =
        AttestationRegistry.fromVerifierFacts(
            bindings,
            List.of(),
            grants,
            rules,
            List.of(fiscalWorkflow(0, UUID.randomUUID(), true)));
    assertFalse(
        wrongWorkflow.hasActiveSystemWorkflow(
            AttestationSystemWorkflowKind.INTERIM_RESULT_SWEEP, POSITION));
    assertFailure(
        AttestationAuthorizationFailure.SYSTEM_DERIVATION_INVALID,
        () ->
            AttestationAuthorization.requireAuthorized(
                wrongWorkflow, systemInterimContext, signedEnvelope(systemInterimContext, system)));

    AttestationRegistry matchingWorkflow =
        AttestationRegistry.fromVerifierFacts(
            bindings,
            List.of(),
            grants,
            rules,
            List.of(interimWorkflow(0, UUID.randomUUID(), true)));
    assertTrue(
        matchingWorkflow.hasActiveSystemWorkflow(
            AttestationSystemWorkflowKind.INTERIM_RESULT_SWEEP, POSITION));
    assertDoesNotThrow(
        () ->
            AttestationAuthorization.requireAuthorized(
                matchingWorkflow,
                systemInterimContext,
                signedEnvelope(systemInterimContext, system)));
    assertDoesNotThrow(
        () ->
            AttestationAuthorization.requireAuthorized(
                noWorkflow,
                operationContext(
                    AttestationOperationKind.INTERIM_RESULT_SWEEP, AttestationSourceChannel.CLI),
                signedEnvelope(
                    operationContext(
                        AttestationOperationKind.INTERIM_RESULT_SWEEP,
                        AttestationSourceChannel.CLI),
                    operator)));

    UUID retiredWorkflowId = UUID.randomUUID();
    AttestationRegistry retiredWorkflow =
        AttestationRegistry.fromVerifierFacts(
            bindings,
            List.of(),
            grants,
            rules,
            List.of(
                interimWorkflow(0, retiredWorkflowId, true),
                interimWorkflow(1, retiredWorkflowId, false)));
    assertFalse(
        retiredWorkflow.hasActiveSystemWorkflow(
            AttestationSystemWorkflowKind.INTERIM_RESULT_SWEEP, POSITION));

    AttestationRegistry futureWorkflow =
        AttestationRegistry.fromVerifierFacts(
            bindings,
            List.of(),
            grants,
            rules,
            List.of(interimWorkflow(2, UUID.randomUUID(), true)));
    assertFalse(
        futureWorkflow.hasActiveSystemWorkflow(
            AttestationSystemWorkflowKind.INTERIM_RESULT_SWEEP, POSITION));
  }

  @Test
  void resolvesHistoricalCredentialsGrantsAndPolicyFactsWithoutFutureLeakage() {
    TestCredential first = credential();
    TestCredential second = credential();
    TestCredential future = credential();
    AttestationRegistry registry =
        AttestationRegistry.fromVerifierFacts(
            List.of(binding(0, first), binding(0, second), binding(2, future)),
            List.of(
                new AttestationCredentialRevocation(
                    BigInteger.ONE, second.principalId(), second.keyId()),
                new AttestationCredentialRevocation(
                    BigInteger.valueOf(3), first.principalId(), first.keyId())),
            List.of(
                new AttestationCapabilityGrant(
                    BigInteger.ZERO,
                    first.principalId(),
                    AttestationCapability.POST,
                    AttestationGrantState.GRANT),
                new AttestationCapabilityGrant(
                    BigInteger.valueOf(2),
                    first.principalId(),
                    AttestationCapability.POST,
                    AttestationGrantState.REVOKE),
                new AttestationCapabilityGrant(
                    BigInteger.valueOf(2),
                    future.principalId(),
                    AttestationCapability.POST,
                    AttestationGrantState.GRANT)),
            List.of(
                new AttestationPolicyRule(BigInteger.valueOf(2), AttestationCapability.POST, 1)),
            List.of());

    assertFalse(registry.credentialAt(AttestationHash.sha256(new byte[] {6}), POSITION).active());
    assertFalse(registry.credentialAt(future.keyId(), POSITION).active());
    assertTrue(registry.credentialAt(first.keyId(), POSITION).active());
    assertFalse(registry.credentialAt(second.keyId(), POSITION).active());
    assertTrue(registry.isEligible(first.principalId(), AttestationCapability.POST, POSITION));
    assertFalse(registry.isEligible(second.principalId(), AttestationCapability.POST, POSITION));
    assertFalse(registry.isEligible(UUID.randomUUID(), AttestationCapability.POST, POSITION));
    assertEquals(1, registry.eligiblePrincipalCount(AttestationCapability.POST, POSITION));
    assertFailure(
        AttestationAuthorizationFailure.CAPABILITY_INVALID,
        () -> registry.quorumAt(AttestationCapability.POST, POSITION));
    assertEquals(1, registry.quorumAt(AttestationCapability.POST, BigInteger.valueOf(2)));
    assertFalse(
        registry.isEligible(first.principalId(), AttestationCapability.POST, BigInteger.TWO));
    assertTrue(
        registry.isEligible(future.principalId(), AttestationCapability.POST, BigInteger.TWO));
  }

  @Test
  void rejectsAnOtherwiseValidSignatureFromAnIneligiblePrincipal() {
    TestCredential eligible = credential();
    TestCredential ineligible = credential();
    AttestationRegistry registry =
        AttestationRegistry.fromVerifierFacts(
            List.of(binding(0, eligible), binding(0, ineligible)),
            List.of(),
            List.of(
                new AttestationCapabilityGrant(
                    BigInteger.ZERO,
                    eligible.principalId(),
                    AttestationCapability.POST,
                    AttestationGrantState.GRANT)),
            List.of(new AttestationPolicyRule(BigInteger.ZERO, AttestationCapability.POST, 1)),
            List.of());
    assertFailure(
        AttestationAuthorizationFailure.CAPABILITY_INVALID,
        () -> authorize(registry, signedEnvelope(ineligible)));
  }

  @Test
  void admitsRolloverOnlyFromAnActiveCredentialOfTheSamePrincipal() {
    TestCredential original = credential();
    TestCredential unrelated = credential();
    KeyPair replacementPair = AttestationEd25519.generateKeyPair();
    TestCredential replacement =
        new TestCredential(
            original.principalId(),
            replacementPair,
            AttestationEd25519.keyId(replacementPair.getPublic()));
    AttestationCredentialBinding rollover =
        new AttestationCredentialBinding(
            BigInteger.ONE,
            replacement.principalId(),
            replacement.keyId(),
            AttestationCredentialBinding.BindingAction.ROLLOVER,
            AttestationSpki.of(replacement.pair().getPublic().getEncoded()),
            AttestationCredentialPurpose.OPERATOR,
            original.keyId());
    AttestationRegistry registry =
        AttestationRegistry.fromVerifierFacts(
            List.of(binding(0, original), binding(0, unrelated), rollover),
            List.of(
                new AttestationCredentialRevocation(
                    BigInteger.ONE, unrelated.principalId(), unrelated.keyId()),
                new AttestationCredentialRevocation(
                    BigInteger.valueOf(3), original.principalId(), original.keyId())),
            allFounderGrants(original.principalId()),
            defaultRules(1),
            List.of());

    assertDoesNotThrow(() -> authorize(registry, signedEnvelope(replacement)));
  }

  @Test
  void rejectsUnknownOperationKinds() {
    assertFailure(
        AttestationAuthorizationFailure.UNKNOWN_OPERATION_KIND,
        () -> AttestationOperationKind.forWireToken("not-an-operation"));
  }

  @Test
  void preservesSpkiIdentityAndRejectsInvalidCredentialShapes() {
    TestCredential credential = credential();
    AttestationSpki spki = AttestationSpki.of(credential.pair().getPublic().getEncoded());
    assertEquals(spki, AttestationSpki.of(spki.bytes()));
    assertEquals(spki.hashCode(), AttestationSpki.of(spki.bytes()).hashCode());
    assertNotEquals(spki, "spki");
    byte[] copiedSpki = spki.bytes();
    copiedSpki[0] ^= 1;
    assertNotEquals(spki, AttestationSpki.of(copiedSpki));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationCredentialBinding(
                BigInteger.ZERO,
                credential.principalId(),
                AttestationHash.sha256(new byte[] {9}),
                AttestationCredentialBinding.BindingAction.ENROLL,
                spki,
                AttestationCredentialPurpose.OPERATOR,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationCredentialBinding(
                BigInteger.ZERO,
                credential.principalId(),
                credential.keyId(),
                AttestationCredentialBinding.BindingAction.ENROLL,
                spki,
                AttestationCredentialPurpose.OPERATOR,
                AttestationHash.sha256(new byte[] {8})));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationCredentialBinding(
                BigInteger.ZERO,
                credential.principalId(),
                credential.keyId(),
                AttestationCredentialBinding.BindingAction.ROLLOVER,
                spki,
                AttestationCredentialPurpose.OPERATOR,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationCredentialBinding(
                BigInteger.ZERO,
                credential.principalId(),
                credential.keyId(),
                AttestationCredentialBinding.BindingAction.ROLLOVER,
                spki,
                AttestationCredentialPurpose.OPERATOR,
                credential.keyId()));
  }

  @Test
  void mapsTheClosedCapabilityCatalogAndRejectsInvalidGenesisInputs() {
    for (AttestationOperationKind operationKind : AttestationOperationKind.values()) {
      assertEquals(operationKind.capability(), AttestationCapability.forOperation(operationKind));
      assertEquals(operationKind, AttestationOperationKind.forWireToken(operationKind.wireToken()));
    }
    assertEquals(AttestationCapability.POST, AttestationOperationKind.POST_ENTRY.capability());
    assertEquals(
        AttestationCapability.APPROVE,
        AttestationOperationKind.ATTACH_POSTING_APPROVAL.capability());
    assertEquals(
        AttestationCapability.CLOSE_PERIOD,
        AttestationOperationKind.FISCAL_YEAR_CLOSE.capability());
    assertEquals(
        AttestationCapability.BACKUP, AttestationOperationKind.BACKUP_CREATED.capability());
    assertEquals(AttestationCapability.RESTORE, AttestationOperationKind.RESTORE_BOOK.capability());
    assertEquals(AttestationCapability.REKEY, AttestationOperationKind.REKEY_BOOK.capability());
    assertEquals(
        AttestationCapability.ENROLL_KEY, AttestationOperationKind.ROLLOVER_KEY.capability());
    assertEquals(
        AttestationCapability.REVOKE_KEY, AttestationOperationKind.REVOKE_KEY.capability());
    assertEquals(
        AttestationCapability.ALTER_POLICY, AttestationOperationKind.ALTER_POLICY.capability());
    for (AttestationCapability capability : AttestationCapability.values()) {
      assertFalse(capability.token().isBlank());
      assertTrue(capability.genesisQuorum(2) >= 1);
    }
    assertThrows(IllegalArgumentException.class, () -> AttestationCapability.POST.genesisQuorum(0));
    assertThrows(IllegalArgumentException.class, () -> AttestationCapability.POST.genesisQuorum(6));
  }

  @Test
  void rejectsInvalidGenesisAndFounderDeclarationsAsOneTypedFailure() {
    TestCredential first = credential();
    TestCredential second = credential();
    assertFailure(
        AttestationAuthorizationFailure.GENESIS_INVALID,
        () -> AttestationAuthorization.requireGenesis(List.of(), signedEnvelope()));
    assertFailure(
        AttestationAuthorizationFailure.GENESIS_INVALID,
        () ->
            AttestationAuthorization.requireGenesis(
                List.of(founder(first), founder(first)), signedEnvelope(first, first)));
    assertFailure(
        AttestationAuthorizationFailure.GENESIS_INVALID,
        () ->
            AttestationAuthorization.requireGenesis(
                List.of(founder(first)), new AttestationAuthorizationEnvelope(PAYLOAD, List.of())));
    assertFailure(
        AttestationAuthorizationFailure.GENESIS_INVALID,
        () ->
            AttestationAuthorization.requireGenesis(
                List.of(founder(first)), signedEnvelope(second)));
    List<AttestationSignatureEntry> firstEntry = orderedEntries(first);
    assertFailure(
        AttestationAuthorizationFailure.GENESIS_INVALID,
        () ->
            AttestationAuthorization.requireGenesis(
                List.of(founder(first)),
                new AttestationAuthorizationEnvelope(
                    PAYLOAD,
                    List.of(
                        new AttestationSignatureEntry(
                            UUID.randomUUID(),
                            firstEntry.getFirst().keyId(),
                            firstEntry.getFirst().signature())))));
    List<AttestationSignatureEntry> ordered = orderedEntries(first, second);
    assertFailure(
        AttestationAuthorizationFailure.GENESIS_INVALID,
        () ->
            AttestationAuthorization.requireGenesis(
                List.of(founder(first), founder(second)),
                new AttestationAuthorizationEnvelope(
                    PAYLOAD, List.of(ordered.getFirst(), ordered.getFirst()))));
    assertFailure(
        AttestationAuthorizationFailure.GENESIS_INVALID,
        () ->
            AttestationAuthorization.requireGenesis(
                List.of(founder(first), founder(second)),
                new AttestationAuthorizationEnvelope(
                    PAYLOAD,
                    List.of(
                        ordered.getFirst(),
                        new AttestationSignatureEntry(
                            ordered.getLast().principalId(),
                            ordered.getFirst().keyId(),
                            ordered.getLast().signature())))));
    assertFailure(
        AttestationAuthorizationFailure.GENESIS_INVALID,
        () ->
            AttestationAuthorization.requireGenesis(
                List.of(founder(first), founder(second)),
                new AttestationAuthorizationEnvelope(
                    PAYLOAD, List.of(ordered.getLast(), ordered.getFirst()))));
    byte[] tampered = ordered.getFirst().signature();
    tampered[0] ^= 1;
    assertFailure(
        AttestationAuthorizationFailure.GENESIS_INVALID,
        () ->
            AttestationAuthorization.requireGenesis(
                List.of(founder(first), founder(second)),
                new AttestationAuthorizationEnvelope(
                    PAYLOAD,
                    List.of(
                        new AttestationSignatureEntry(
                            ordered.getFirst().principalId(), ordered.getFirst().keyId(), tampered),
                        ordered.getLast()))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationFounder(
                first.principalId(),
                AttestationHash.sha256(new byte[] {7}),
                AttestationSpki.of(first.pair().getPublic().getEncoded())));
    byte[] invalidSpki = new byte[] {1};
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationFounder(
                UUID.randomUUID(),
                AttestationHash.sha256(invalidSpki),
                AttestationSpki.of(invalidSpki)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationPolicyRule(BigInteger.ZERO, AttestationCapability.POST, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationPolicyRule(BigInteger.ZERO, AttestationCapability.POST, 65));
    assertThrows(IllegalArgumentException.class, () -> AttestationRegistry.genesis(List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> AttestationRegistry.genesis(List.of(founder(first), founder(first))));
    AttestationFounder reusedKeyForOtherPrincipal =
        new AttestationFounder(
            UUID.randomUUID(),
            first.keyId(),
            AttestationSpki.of(first.pair().getPublic().getEncoded()));
    assertThrows(
        IllegalArgumentException.class,
        () -> AttestationRegistry.genesis(List.of(founder(first), reusedKeyForOtherPrincipal)));
    assertFailure(
        AttestationAuthorizationFailure.GENESIS_INVALID,
        () ->
            AttestationAuthorization.requireGenesis(
                List.of(founder(first), reusedKeyForOtherPrincipal), signedEnvelope(first, first)));
    List<AttestationFounder> tooManyFounders =
        List.of(
            founder(credential()),
            founder(credential()),
            founder(credential()),
            founder(credential()),
            founder(credential()),
            founder(credential()));
    assertThrows(
        IllegalArgumentException.class, () -> AttestationRegistry.genesis(tooManyFounders));
    assertFailure(
        AttestationAuthorizationFailure.GENESIS_INVALID,
        () -> AttestationAuthorization.requireGenesis(tooManyFounders, signedEnvelope()));
    assertFalse(AttestationEd25519.verifies(new byte[] {1}, PAYLOAD, new byte[64]));
  }
}
