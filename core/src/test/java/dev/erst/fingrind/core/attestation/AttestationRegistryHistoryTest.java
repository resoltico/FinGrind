package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.credential;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.replaceFirstRecord;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.withField;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Requires the post-operation registry fold to retain a reachable authorization quorum. */
class AttestationRegistryHistoryTest {
  @Test
  void rejectsAnAcceptedPolicyMutationThatMakesPostQuorumUnreachable() {
    TestCredential founder = credential();
    AttestationRegistryHistory history =
        AttestationRegistryHistory.genesis(
            List.of(
                new AttestationFounder(
                    founder.principalId(),
                    founder.keyId(),
                    AttestationSpki.of(founder.pair().getPublic().getEncoded()))));

    history.accept(
        AttestationOperationKind.ALTER_POLICY,
        BigInteger.ONE,
        policyRequest(AttestationCapability.POST, 2),
        policyEffect(AttestationCapability.POST, 2));

    assertFailure(
        AttestationAuthorizationFailure.POLICY_CAPACITY_INVALID, history::requireAcceptedState);
  }

  @Test
  void decodesEveryRegistryFactFamilyFromItsMatchingRequestProjection() {
    TestCredential credential = credential();
    TestCredential predecessor = credential();
    UUID workflowId = UUID.fromString("11000000-0000-0000-0000-000000000001");
    UUID fiscalWorkflowId = UUID.fromString("11000000-0000-0000-0000-000000000002");

    AttestationRegistryEffectDecoder.DecodedFacts binding =
        decode(
            AttestationOperationKind.ENROLL_KEY,
            bindingRequest(credential, "enroll", "operator", null),
            bindingEffect(credential, "enroll", "operator", null));
    assertEquals(1, binding.bindings().size());

    AttestationRegistryEffectDecoder.DecodedFacts rollover =
        decode(
            AttestationOperationKind.ROLLOVER_KEY,
            bindingRequest(credential, "rollover", "system", predecessor.keyId()),
            bindingEffect(credential, "rollover", "system", predecessor.keyId()));
    assertEquals(
        AttestationCredentialBinding.BindingAction.ROLLOVER,
        rollover.bindings().getFirst().action());

    AttestationRegistryEffectDecoder.DecodedFacts revocation =
        decode(
            AttestationOperationKind.REVOKE_KEY,
            revocationRequest(credential),
            revocationEffect(credential));
    assertEquals(1, revocation.revocations().size());

    AttestationRegistryEffectDecoder.DecodedFacts policy =
        decode(
            AttestationOperationKind.ALTER_POLICY,
            AttestationPreimage.of(
                List.of(
                    policyRequest(AttestationCapability.ALTER_POLICY, 1).records().getFirst(),
                    grantRequest(credential, AttestationCapability.POST, "grant"),
                    workflowRequest(workflowId, "interim-result-sweep", null, null),
                    workflowRequest(fiscalWorkflowId, "fiscal-year-close", "3100", "3200"))),
            AttestationPreimage.of(
                List.of(
                    policyEffect(AttestationCapability.ALTER_POLICY, 1).records().getFirst(),
                    grantEffect(credential, AttestationCapability.POST, "grant"),
                    workflowEffect(workflowId, "interim-result-sweep", null, null),
                    workflowEffect(fiscalWorkflowId, "fiscal-year-close", "3100", "3200"))));
    assertEquals(1, policy.policyRules().size());
    assertEquals(1, policy.grants().size());
    assertEquals(2, policy.workflowPolicies().size());
  }

  @Test
  void rejectsUnownedAndUnprojectedRegistryEffectsBeforeTheyCanChangeHistory() {
    TestCredential first = credential();
    TestCredential second = credential();

    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            decode(
                AttestationOperationKind.ENROLL_KEY,
                AttestationPreimage.of(List.of()),
                AttestationPreimage.of(List.of())));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            decode(
                AttestationOperationKind.POST_ENTRY,
                bindingRequest(first, "enroll", "operator", null),
                bindingEffect(first, "enroll", "operator", null)));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            decode(
                AttestationOperationKind.ENROLL_KEY,
                bindingRequest(first, "enroll", "operator", null),
                bindingEffect(second, "enroll", "operator", null)));
  }

  @Test
  void rejectsEveryWrongRegistryEffectOwnershipAndProjectionShape() {
    TestCredential credential = credential();
    AttestationPreimage empty = AttestationPreimage.of(List.of());

    assertEquals(
        List.of(), decode(AttestationOperationKind.POST_ENTRY, empty, empty).policyRules());
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () -> decode(AttestationOperationKind.REVOKE_KEY, empty, empty));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            decode(
                AttestationOperationKind.ROLLOVER_KEY,
                bindingRequest(credential, "enroll", "operator", null),
                bindingEffect(credential, "enroll", "operator", null)));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () -> decode(AttestationOperationKind.ALTER_POLICY, empty, revocationEffect(credential)));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () -> decode(AttestationOperationKind.ALTER_POLICY, empty, empty));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () -> decode(AttestationOperationKind.POST_ENTRY, empty, revocationEffect(credential)));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            decode(
                AttestationOperationKind.ENROLL_KEY,
                empty,
                bindingEffect(credential, "enroll", "operator", null)));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            decode(
                AttestationOperationKind.ENROLL_KEY,
                empty,
                effects(
                    bindingEffect(credential, "enroll", "operator", null),
                    revocationEffect(credential))));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            decode(
                AttestationOperationKind.ENROLL_KEY,
                empty,
                effects(
                    bindingEffect(credential, "enroll", "operator", null),
                    policyEffect(AttestationCapability.POST, 1))));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            decode(
                AttestationOperationKind.REVOKE_KEY,
                empty,
                effects(
                    revocationEffect(credential),
                    bindingEffect(credential, "enroll", "operator", null))));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            decode(
                AttestationOperationKind.REVOKE_KEY,
                empty,
                effects(
                    revocationEffect(credential), policyEffect(AttestationCapability.POST, 1))));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            decode(
                AttestationOperationKind.ALTER_POLICY,
                empty,
                bindingEffect(credential, "enroll", "operator", null)));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            decode(
                AttestationOperationKind.POST_ENTRY,
                empty,
                policyEffect(AttestationCapability.POST, 1)));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            decode(
                AttestationOperationKind.POST_ENTRY,
                empty,
                AttestationPreimage.of(
                    List.of(
                        workflowEffect(
                            UUID.fromString("11000000-0000-0000-0000-000000000003"),
                            "interim-result-sweep",
                            null,
                            null)))));
  }

  @Test
  void rejectsUnknownRegistryWireValuesAtTheDedicatedFactDecoderBoundary() {
    TestCredential credential = credential();
    UUID workflowId = UUID.fromString("11000000-0000-0000-0000-000000000004");

    assertDecoderFailure(bindingEffect(credential, "other", "operator", null));
    assertDecoderFailure(bindingEffect(credential, "enroll", "other", null));
    assertDecoderFailure(
        AttestationPreimage.of(
            List.of(grantEffect(credential, AttestationCapability.POST, "other"))));
    assertDecoderFailure(
        AttestationPreimage.of(
            List.of(
                new AttestationPreimage.Fact(
                    0x0003,
                    List.of(
                        present(AttestationNumericFieldValue.mutation(0)),
                        present(AttestationBinaryFieldValue.uuid(credential.principalId())),
                        present(AttestationTextFieldValue.token("other")),
                        present(AttestationTextFieldValue.token("grant")))))));
    assertDecoderFailure(
        AttestationPreimage.of(List.of(workflowEffect(workflowId, "other", null, null))));
    assertEquals(
        AttestationGrantState.REVOKE,
        AttestationRegistryEffectFactDecoder.decode(
                BigInteger.ONE,
                AttestationRegistryEffectSets.from(
                    AttestationPreimage.of(
                        List.of(grantEffect(credential, AttestationCapability.POST, "revoke")))))
            .grants()
            .getFirst()
            .state());
  }

  @Test
  void classifiesAConflictingAcceptedRegistryFactAsAnUntrustedProfileFailure() {
    TestCredential founder = credential();
    AttestationRegistryHistory history =
        AttestationRegistryHistory.genesis(
            List.of(
                new AttestationFounder(
                    founder.principalId(),
                    founder.keyId(),
                    AttestationSpki.of(founder.pair().getPublic().getEncoded()))));

    history.accept(
        AttestationOperationKind.ALTER_POLICY,
        BigInteger.ZERO,
        policyRequest(AttestationCapability.POST, 1),
        policyEffect(AttestationCapability.POST, 1));

    assertFailure(AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID, history::registry);
  }

  @Test
  void rejectsMalformedRolloverEnrollmentAndRevocationFacts() {
    TestCredential a = credential();
    TestCredential b = credential();
    TestCredential rawA2 = credential();
    TestCredential a2 = new TestCredential(a.principalId(), rawA2.pair(), rawA2.keyId());
    TestCredential c = credential();
    AttestationRegistryHistory history = history(a, b);
    AttestationHash wrongKeyId = AttestationHash.sha256(new byte[] {9});
    AttestationPreimage n21Request =
        replaceFirstRecord(
            bindingRequest(a2, "rollover", "operator", a.keyId()),
            0x0180,
            record -> withField(record, 1, present(AttestationBinaryFieldValue.hash(wrongKeyId))));
    AttestationPreimage n21Effect =
        replaceFirstRecord(
            bindingEffect(a2, "rollover", "operator", a.keyId()),
            0x0002,
            record -> withField(record, 2, present(AttestationBinaryFieldValue.hash(wrongKeyId))));

    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            history.accept(
                AttestationOperationKind.ROLLOVER_KEY, BigInteger.ONE, n21Request, n21Effect));

    AttestationPreimage n22Request = bindingRequest(a2, "rollover", "operator", null);
    AttestationPreimage n22Effect = bindingEffect(a2, "rollover", "operator", null);
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            history(a, b)
                .accept(
                    AttestationOperationKind.ROLLOVER_KEY, BigInteger.ONE, n22Request, n22Effect));

    AttestationPreimage n23Request = bindingRequest(a2, "rollover", "operator", b.keyId());
    AttestationPreimage n23Effect = bindingEffect(a2, "rollover", "operator", b.keyId());
    AttestationRegistryHistory n23History = history(a, b);
    n23History.accept(AttestationOperationKind.ROLLOVER_KEY, BigInteger.ONE, n23Request, n23Effect);
    assertFailure(AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID, n23History::registry);

    TestCredential cWithBKey = new TestCredential(c.principalId(), b.pair(), b.keyId());
    AttestationPreimage n24Request = bindingRequest(cWithBKey, "enroll", "operator", null);
    AttestationPreimage n24Effect = bindingEffect(cWithBKey, "enroll", "operator", null);
    AttestationRegistryHistory n24History = history(a, b);
    n24History.accept(AttestationOperationKind.ENROLL_KEY, BigInteger.ONE, n24Request, n24Effect);
    assertFailure(AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID, n24History::registry);

    AttestationPreimage n25Request = bindingRequest(c, "enroll", "operator", a.keyId());
    AttestationPreimage n25Effect = bindingEffect(c, "enroll", "operator", a.keyId());
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            history(a, b)
                .accept(
                    AttestationOperationKind.ENROLL_KEY, BigInteger.ONE, n25Request, n25Effect));

    AttestationPreimage n26Request = bindingRequest(a2, "rollover", "operator", a2.keyId());
    AttestationPreimage n26Effect = bindingEffect(a2, "rollover", "operator", a2.keyId());
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            history(a, b)
                .accept(
                    AttestationOperationKind.ROLLOVER_KEY, BigInteger.ONE, n26Request, n26Effect));

    AttestationPreimage n27Request = revocationRequest(c);
    AttestationPreimage n27Effect = revocationEffect(c);
    AttestationRegistryHistory n27History = history(a, b);
    n27History.accept(AttestationOperationKind.REVOKE_KEY, BigInteger.ONE, n27Request, n27Effect);
    assertFailure(AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID, n27History::registry);
  }

  @Test
  void rejectsAnAcceptedSystemClosePolicyWithoutSystemPurposeCapacity() {
    TestCredential a = credential();
    TestCredential b = credential();
    TestCredential c = credential();
    AttestationRegistryHistory history = history(a, b);
    AttestationPreimage enrollmentRequest = bindingRequest(c, "enroll", "system", null);
    AttestationPreimage enrollmentEffect = bindingEffect(c, "enroll", "system", null);
    history.accept(
        AttestationOperationKind.ENROLL_KEY,
        BigInteger.valueOf(4),
        enrollmentRequest,
        enrollmentEffect);

    UUID workflowId = UUID.fromString("40000000-0000-7000-8000-000000000001");
    AttestationPreimage request =
        AttestationPreimage.of(
            List.of(
                policyRequest(AttestationCapability.CLOSE_PERIOD, 2).records().getFirst(),
                grantRequest(c, AttestationCapability.CLOSE_PERIOD, "grant"),
                workflowRequest(workflowId, "interim-result-sweep", null, null)));
    AttestationPreimage effect =
        AttestationPreimage.of(
            List.of(
                policyEffect(AttestationCapability.CLOSE_PERIOD, 2).records().getFirst(),
                grantEffect(c, AttestationCapability.CLOSE_PERIOD, "grant"),
                workflowEffect(workflowId, "interim-result-sweep", null, null)));
    history.accept(AttestationOperationKind.ALTER_POLICY, BigInteger.valueOf(5), request, effect);
    assertFailure(
        AttestationAuthorizationFailure.POLICY_CAPACITY_INVALID, history::requireAcceptedState);
  }

  private static AttestationRegistryEffectDecoder.DecodedFacts decode(
      AttestationOperationKind operationKind,
      AttestationPreimage request,
      AttestationPreimage effect) {
    return AttestationRegistryEffectDecoder.decode(operationKind, BigInteger.ONE, request, effect);
  }

  private static AttestationRegistryHistory history(TestCredential... founders) {
    return AttestationRegistryHistory.genesis(
        java.util.Arrays.stream(founders)
            .map(
                founder ->
                    new AttestationFounder(
                        founder.principalId(),
                        founder.keyId(),
                        AttestationSpki.of(founder.pair().getPublic().getEncoded())))
            .toList());
  }

  private static void assertDecoderFailure(AttestationPreimage effect) {
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationRegistryEffectFactDecoder.decode(
                BigInteger.ONE, AttestationRegistryEffectSets.from(effect)));
  }

  private static AttestationPreimage effects(AttestationPreimage... effectPreimages) {
    return AttestationPreimage.of(
        java.util.Arrays.stream(effectPreimages)
            .flatMap(effect -> effect.records().stream())
            .toList());
  }

  private static AttestationPreimage bindingRequest(
      TestCredential credential,
      String action,
      String purpose,
      @Nullable AttestationHash predecessorKeyId) {
    return AttestationPreimage.of(
        List.of(
            new AttestationPreimage.Fact(
                0x0180,
                List.of(
                    present(AttestationBinaryFieldValue.uuid(credential.principalId())),
                    present(AttestationBinaryFieldValue.hash(credential.keyId())),
                    present(AttestationTextFieldValue.token(action)),
                    present(
                        AttestationBinaryFieldValue.spki(
                            credential.pair().getPublic().getEncoded())),
                    present(AttestationTextFieldValue.token(purpose)),
                    optionalHash(predecessorKeyId)))));
  }

  private static AttestationPreimage bindingEffect(
      TestCredential credential,
      String action,
      String purpose,
      @Nullable AttestationHash predecessorKeyId) {
    return AttestationPreimage.of(
        List.of(
            new AttestationPreimage.Fact(
                0x0002,
                List.of(
                    present(AttestationNumericFieldValue.mutation(0)),
                    present(AttestationBinaryFieldValue.uuid(credential.principalId())),
                    present(AttestationBinaryFieldValue.hash(credential.keyId())),
                    present(AttestationTextFieldValue.token(action)),
                    present(
                        AttestationBinaryFieldValue.spki(
                            credential.pair().getPublic().getEncoded())),
                    present(AttestationTextFieldValue.token(purpose)),
                    optionalHash(predecessorKeyId)))));
  }

  private static AttestationPreimage revocationRequest(TestCredential credential) {
    return AttestationPreimage.of(
        List.of(
            new AttestationPreimage.Fact(
                0x0181,
                List.of(
                    present(AttestationBinaryFieldValue.hash(credential.keyId())),
                    present(AttestationBinaryFieldValue.uuid(credential.principalId())),
                    AttestationField.absent()))));
  }

  private static AttestationPreimage revocationEffect(TestCredential credential) {
    return AttestationPreimage.of(
        List.of(
            new AttestationPreimage.Fact(
                0x0004,
                List.of(
                    present(AttestationNumericFieldValue.mutation(0)),
                    present(AttestationBinaryFieldValue.hash(credential.keyId())),
                    present(AttestationBinaryFieldValue.uuid(credential.principalId())),
                    AttestationField.absent()))));
  }

  private static AttestationPreimage.Fact grantRequest(
      TestCredential credential, AttestationCapability capability, String state) {
    return new AttestationPreimage.Fact(
        0x0183,
        List.of(
            present(AttestationBinaryFieldValue.uuid(credential.principalId())),
            present(AttestationTextFieldValue.token(capability.token())),
            present(AttestationTextFieldValue.token(state))));
  }

  private static AttestationPreimage.Fact grantEffect(
      TestCredential credential, AttestationCapability capability, String state) {
    return new AttestationPreimage.Fact(
        0x0003,
        List.of(
            present(AttestationNumericFieldValue.mutation(0)),
            present(AttestationBinaryFieldValue.uuid(credential.principalId())),
            present(AttestationTextFieldValue.token(capability.token())),
            present(AttestationTextFieldValue.token(state))));
  }

  private static AttestationPreimage.Fact workflowRequest(
      UUID workflowId,
      String workflowKind,
      @Nullable String capitalAccountCode,
      @Nullable String retainedResultAccountCode) {
    return new AttestationPreimage.Fact(
        0x0184,
        List.of(
            present(AttestationBinaryFieldValue.uuid(workflowId)),
            present(AttestationTextFieldValue.token(workflowKind)),
            present(AttestationTextFieldValue.text("3000")),
            optionalText(capitalAccountCode),
            optionalText(retainedResultAccountCode),
            present(AttestationNumericFieldValue.booleanValue(true))));
  }

  private static AttestationPreimage.Fact workflowEffect(
      UUID workflowId,
      String workflowKind,
      @Nullable String capitalAccountCode,
      @Nullable String retainedResultAccountCode) {
    return new AttestationPreimage.Fact(
        0x0008,
        List.of(
            present(AttestationNumericFieldValue.mutation(0)),
            present(AttestationBinaryFieldValue.uuid(workflowId)),
            present(AttestationTextFieldValue.token(workflowKind)),
            present(AttestationTextFieldValue.text("3000")),
            optionalText(capitalAccountCode),
            optionalText(retainedResultAccountCode),
            present(AttestationNumericFieldValue.booleanValue(true))));
  }

  private static AttestationField present(AttestationFieldValue value) {
    return AttestationField.present(value);
  }

  private static AttestationField optionalHash(@Nullable AttestationHash value) {
    return value == null
        ? AttestationField.absent()
        : present(AttestationBinaryFieldValue.hash(value));
  }

  private static AttestationField optionalText(@Nullable String value) {
    return value == null
        ? AttestationField.absent()
        : present(AttestationTextFieldValue.text(value));
  }

  private static AttestationPreimage policyRequest(AttestationCapability capability, int quorum) {
    return AttestationPreimage.of(
        List.of(
            new AttestationPreimage.Fact(
                0x0182,
                List.of(
                    AttestationField.present(AttestationTextFieldValue.token(capability.token())),
                    AttestationField.present(AttestationNumericFieldValue.unsigned16(quorum))))));
  }

  private static AttestationPreimage policyEffect(AttestationCapability capability, int quorum) {
    return AttestationPreimage.of(
        List.of(
            new AttestationPreimage.Fact(
                0x0005,
                List.of(
                    AttestationField.present(AttestationNumericFieldValue.mutation(0)),
                    AttestationField.present(AttestationTextFieldValue.token(capability.token())),
                    AttestationField.present(AttestationNumericFieldValue.unsigned16(quorum))))));
  }
}
