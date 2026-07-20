package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.BOOK_ID;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.credential;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.operationPayload;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.requestPreimage;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.appendRecord;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisEffectPreimage;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisPayload;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisRequestPreimage;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.replaceFirstRecord;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.signedGenesisEnvelope;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.withField;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.withoutRecords;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Exercises each independent rejection condition for the genesis authorization boundary. */
class AttestationGenesisBootstrapValidationTest {
  private static final AttestationHash ZERO_HEAD =
      AttestationHash.of(new byte[AttestationHash.BYTE_LENGTH]);

  @Test
  void rejectsPayloadThatIsNotTheExactCliGenesisAndRejectsAnotherEnvelopePayload() {
    TestCredential founder = credential();
    AttestationPreimage request = genesisRequestPreimage(founder);
    AttestationPreimage effect = genesisEffectPreimage(founder);

    assertGenesisFailure(
        genesisPayload(BigInteger.ONE, ZERO_HEAD, request, effect), request, effect);
    assertGenesisFailure(
        payload(BigInteger.ZERO, "post-entry", ZERO_HEAD, request, effect), request, effect);
    assertGenesisFailure(
        genesisPayload(BigInteger.ZERO, AttestationHash.sha256(new byte[] {1}), request, effect),
        request,
        effect);
    assertGenesisFailure(
        payload(
            BigInteger.ZERO,
            AttestationOperationKind.BOOK_GENESIS.wireToken(),
            ZERO_HEAD,
            request,
            AttestationHash.sha256(new byte[] {2})),
        request,
        effect);
    assertGenesisFailure(
        new AttestationOperationPayload(
            BOOK_ID,
            BigInteger.ZERO,
            AttestationOperationKind.BOOK_GENESIS.wireToken(),
            ZERO_HEAD,
            Instant.parse("2026-07-20T00:00:00Z"),
            AttestationHash.sha256(new byte[] {3}),
            AttestationHash.sha256(effect.encoded())),
        request,
        effect);

    AttestationPreimage commandKindMismatch =
        replaceFirstRecord(
            request,
            0x0100,
            record ->
                withField(
                    record,
                    0,
                    AttestationField.present(AttestationTextFieldValue.token("post-entry"))));
    assertGenesisFailure(commandKindMismatch, effect);
    AttestationPreimage unknownSourceRequest =
        replaceFirstRecord(
            request,
            0x0100,
            record ->
                withField(
                    record,
                    3,
                    AttestationField.present(AttestationTextFieldValue.token("unknown"))));
    assertGenesisFailure(unknownSourceRequest, effect);

    AttestationPreimage systemCommandRequest =
        replaceFirstRecord(
            request,
            0x0100,
            record ->
                withField(
                    record,
                    3,
                    AttestationField.present(
                        AttestationTextFieldValue.token(
                            AttestationSourceChannel.SYSTEM.wireToken()))));
    AttestationPreimage.Fact systemWorkflowRun =
        new AttestationPreimage.Fact(
            0x0141,
            List.of(AttestationField.present(AttestationBinaryFieldValue.uuid(UUID.randomUUID()))));
    AttestationPreimage systemGenesisRequest =
        appendRecord(systemCommandRequest, systemWorkflowRun);
    assertGenesisFailure(systemGenesisRequest, effect);

    AttestationGenesisAuthorizationContext context =
        AttestationGenesisAuthorizationContext.verify(
            genesisPayload(BigInteger.ZERO, ZERO_HEAD, request, effect), request, effect);
    byte[] alteredPayload = context.payload();
    alteredPayload[alteredPayload.length - 1] ^= 1;
    AttestationAuthorizationEnvelope envelope = signedGenesisEnvelope(context, founder);
    assertFailure(
        AttestationAuthorizationFailure.GENESIS_INVALID,
        () ->
            AttestationAuthorization.requireGenesis(
                context, new AttestationAuthorizationEnvelope(alteredPayload, envelope.entries())));
  }

  @Test
  void rejectsEachInvalidFounderEffectCondition() {
    TestCredential founder = credential();
    assertGenesisFailure(
        genesisRequestPreimage(founder), withoutRecords(genesisEffectPreimage(founder), 0x0002));

    TestCredential[] fiveFounders =
        java.util.stream.IntStream.range(0, 5)
            .mapToObj(ignored -> credential())
            .toArray(TestCredential[]::new);
    TestCredential sixthFounder = credential();
    AttestationPreimage sixFounderEffect =
        appendRecord(
            genesisEffectPreimage(fiveFounders),
            genesisEffectPreimage(sixthFounder).records().stream()
                .filter(record -> record.recordTypeTag() == 0x0002)
                .findFirst()
                .orElseThrow());
    assertGenesisFailure(genesisRequestPreimage(fiveFounders), sixFounderEffect);

    AttestationPreimage request = genesisRequestPreimage(founder);
    AttestationPreimage effect = genesisEffectPreimage(founder);
    assertGenesisFailure(
        request,
        replaceFirstRecord(
            effect,
            0x0002,
            record ->
                withField(
                    record,
                    0,
                    AttestationField.present(AttestationNumericFieldValue.mutation(1)))));
    assertGenesisFailure(
        request,
        replaceFirstRecord(
            effect,
            0x0002,
            record ->
                withField(
                    record,
                    3,
                    AttestationField.present(AttestationTextFieldValue.token("rollover")))));
    assertGenesisFailure(
        request,
        replaceFirstRecord(
            effect,
            0x0002,
            record ->
                withField(
                    record,
                    5,
                    AttestationField.present(AttestationTextFieldValue.token("auditor")))));
    assertGenesisFailure(
        request,
        replaceFirstRecord(
            effect,
            0x0002,
            record ->
                withField(
                    record,
                    6,
                    AttestationField.present(AttestationBinaryFieldValue.hash(founder.keyId())))));
    assertGenesisFailure(
        request,
        replaceFirstRecord(
            effect,
            0x0002,
            record ->
                withField(
                    record,
                    2,
                    AttestationField.present(
                        AttestationBinaryFieldValue.hash(
                            AttestationHash.sha256(new byte[] {3}))))));
  }

  @Test
  void rejectsEffectShapeIdentityAndDuplicateFounderKeys() {
    TestCredential founder = credential();
    AttestationPreimage request = genesisRequestPreimage(founder);
    AttestationPreimage effect = genesisEffectPreimage(founder);

    assertGenesisFailure(
        request, appendRecord(effect, genesisRequestPreimage().records().getFirst()));
    assertGenesisFailure(
        request,
        appendRecord(
            withoutRecords(effect, 0x0001), genesisRequestPreimage().records().getFirst()));
    assertGenesisFailure(
        request,
        replaceFirstRecord(
            effect,
            0x0001,
            record ->
                withField(
                    record,
                    0,
                    AttestationField.present(AttestationNumericFieldValue.mutation(1)))));
    assertGenesisFailure(
        request,
        replaceFirstRecord(
            effect,
            0x0001,
            record ->
                withField(
                    record,
                    1,
                    AttestationField.present(
                        AttestationBinaryFieldValue.uuid(UUID.randomUUID())))));

    TestCredential sameKeyDifferentPrincipal =
        new TestCredential(UUID.randomUUID(), founder.pair(), founder.keyId());
    assertGenesisFailure(
        genesisRequestPreimage(founder, sameKeyDifferentPrincipal),
        genesisEffectPreimage(founder, sameKeyDifferentPrincipal));
  }

  @Test
  void rejectsEveryMalformedInitialPolicyAndGrantEffect() {
    TestCredential founder = credential();
    AttestationPreimage request = genesisRequestPreimage(founder);
    AttestationPreimage effect = genesisEffectPreimage(founder);

    assertGenesisFailure(request, withoutRecords(effect, 0x0005));
    assertGenesisFailure(
        request,
        replaceFirstRecord(
            effect,
            0x0005,
            record ->
                withField(
                    record,
                    0,
                    AttestationField.present(AttestationNumericFieldValue.mutation(1)))));
    assertGenesisFailure(
        request,
        replaceFirstRecord(
            effect,
            0x0005,
            record ->
                withField(
                    record,
                    1,
                    AttestationField.present(AttestationTextFieldValue.token("unknown")))));
    assertGenesisFailure(
        request,
        replaceFirstRecord(
            effect,
            0x0005,
            record ->
                withField(
                    record,
                    2,
                    AttestationField.present(AttestationNumericFieldValue.unsigned16(9)))));

    assertGenesisFailure(request, withoutRecords(effect, 0x0003));
    assertGenesisFailure(
        request,
        replaceFirstRecord(
            effect,
            0x0003,
            record ->
                withField(
                    record,
                    0,
                    AttestationField.present(AttestationNumericFieldValue.mutation(1)))));
    assertGenesisFailure(
        request,
        replaceFirstRecord(
            effect,
            0x0003,
            record ->
                withField(
                    record,
                    1,
                    AttestationField.present(
                        AttestationBinaryFieldValue.uuid(UUID.randomUUID())))));
    assertGenesisFailure(
        request,
        replaceFirstRecord(
            effect,
            0x0003,
            record ->
                withField(
                    record,
                    2,
                    AttestationField.present(AttestationTextFieldValue.token("unknown")))));
    assertGenesisFailure(
        request,
        replaceFirstRecord(
            effect,
            0x0003,
            record ->
                withField(
                    record,
                    3,
                    AttestationField.present(AttestationTextFieldValue.token("revoke")))));
  }

  @Test
  void rejectsEveryMalformedGenesisDeclaration() {
    TestCredential founder = credential();
    AttestationPreimage request = genesisRequestPreimage(founder);
    AttestationPreimage effect = genesisEffectPreimage(founder);

    assertGenesisFailure(appendRecord(request, effect.records().getFirst()), effect);
    assertGenesisFailure(withoutRecords(request, 0x0101), effect);
    AttestationPreimage mismatchedBookIdentityRequest =
        replaceFirstRecord(
            request,
            0x0101,
            record ->
                withField(
                    record,
                    0,
                    AttestationField.present(AttestationBinaryFieldValue.uuid(UUID.randomUUID()))));
    assertGenesisFailure(mismatchedBookIdentityRequest, effect);
    assertGenesisFailure(withoutRecords(request, 0x0102), effect);
    assertGenesisFailure(
        replaceFirstRecord(
            request,
            0x0102,
            record ->
                withField(
                    record,
                    3,
                    AttestationField.present(AttestationTextFieldValue.token("auditor")))),
        effect);
    AttestationPreimage mismatchedFounderKeyRequest =
        replaceFirstRecord(
            request,
            0x0102,
            record ->
                withField(
                    record,
                    1,
                    AttestationField.present(
                        AttestationBinaryFieldValue.hash(AttestationHash.sha256(new byte[] {4})))));
    assertGenesisFailure(mismatchedFounderKeyRequest, effect);

    assertGenesisFailure(withoutRecords(request, 0x0103), effect);
    assertGenesisFailure(
        replaceFirstRecord(
            request,
            0x0103,
            record ->
                withField(
                    record,
                    0,
                    AttestationField.present(AttestationTextFieldValue.token("unknown")))),
        effect);
    assertGenesisFailure(
        replaceFirstRecord(
            request,
            0x0103,
            record ->
                withField(
                    record,
                    1,
                    AttestationField.present(AttestationNumericFieldValue.unsigned16(9)))),
        effect);

    assertGenesisFailure(withoutRecords(request, 0x0183), effect);
    AttestationPreimage nonFounderGrantRequest =
        replaceFirstRecord(
            request,
            0x0183,
            record ->
                withField(
                    record,
                    0,
                    AttestationField.present(AttestationBinaryFieldValue.uuid(UUID.randomUUID()))));
    assertGenesisFailure(nonFounderGrantRequest, effect);
    assertGenesisFailure(
        replaceFirstRecord(
            request,
            0x0183,
            record ->
                withField(
                    record,
                    1,
                    AttestationField.present(AttestationTextFieldValue.token("unknown")))),
        effect);
    assertGenesisFailure(
        replaceFirstRecord(
            request,
            0x0183,
            record ->
                withField(
                    record,
                    2,
                    AttestationField.present(AttestationTextFieldValue.token("revoke")))),
        effect);
  }

  @Test
  void rejectsProvenanceThatIsNotExactlyBoundToOneOperationRequest() {
    AttestationPreimage cliRequest =
        requestPreimage(AttestationOperationKind.POST_ENTRY, AttestationSourceChannel.CLI, null);
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationVerifiedOperationProvenance.verify(
                new AttestationOperationPayload(
                    BOOK_ID,
                    BigInteger.ONE,
                    AttestationOperationKind.POST_ENTRY.wireToken(),
                    AttestationHash.sha256(new byte[] {5}),
                    Instant.parse("2026-07-20T00:00:00Z"),
                    AttestationHash.sha256(new byte[] {6}),
                    AttestationHash.sha256(new byte[] {7})),
                cliRequest));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationVerifiedOperationProvenance.verify(
                operationPayload(
                    BigInteger.ONE,
                    AttestationOperationKind.POST_ENTRY,
                    AttestationPreimage.of(List.of())),
                AttestationPreimage.of(List.of())));
    AttestationPreimage unknownSourceRequest =
        replaceFirstRecord(
            cliRequest,
            0x0100,
            record ->
                withField(
                    record,
                    3,
                    AttestationField.present(AttestationTextFieldValue.token("unknown"))));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationVerifiedOperationProvenance.verify(
                operationPayload(
                    BigInteger.ONE, AttestationOperationKind.POST_ENTRY, unknownSourceRequest),
                unknownSourceRequest));
    AttestationPreimage twoCommands =
        appendRecord(
            cliRequest,
            requestPreimage(
                    AttestationOperationKind.EXECUTE_PLAN, AttestationSourceChannel.CLI, null)
                .records()
                .getFirst());
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationVerifiedOperationProvenance.verify(
                operationPayload(BigInteger.ONE, AttestationOperationKind.POST_ENTRY, twoCommands),
                twoCommands));
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationVerifiedOperationProvenance.verify(
                operationPayload(
                    BigInteger.ONE,
                    AttestationOperationKind.EXECUTE_PLAN,
                    requestPreimage(
                        AttestationOperationKind.POST_ENTRY, AttestationSourceChannel.CLI, null)),
                requestPreimage(
                    AttestationOperationKind.POST_ENTRY, AttestationSourceChannel.CLI, null)));
    AttestationPreimage.Fact workflowRun =
        new AttestationPreimage.Fact(
            0x0141,
            List.of(AttestationField.present(AttestationBinaryFieldValue.uuid(UUID.randomUUID()))));
    AttestationPreimage cliWithWorkflow = appendRecord(cliRequest, workflowRun);
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationVerifiedOperationProvenance.verify(
                operationPayload(
                    BigInteger.ONE, AttestationOperationKind.POST_ENTRY, cliWithWorkflow),
                cliWithWorkflow));

    AttestationPreimage systemWithoutWorkflow =
        requestPreimage(
            AttestationOperationKind.INTERIM_RESULT_SWEEP, AttestationSourceChannel.SYSTEM, null);
    assertFailure(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID,
        () ->
            AttestationVerifiedOperationProvenance.verify(
                operationPayload(
                    BigInteger.ONE,
                    AttestationOperationKind.INTERIM_RESULT_SWEEP,
                    systemWithoutWorkflow),
                systemWithoutWorkflow));
  }

  private static AttestationOperationPayload payload(
      BigInteger operationOrder,
      String operationKind,
      AttestationHash previousHead,
      AttestationPreimage request,
      AttestationPreimage effect) {
    return payload(
        operationOrder,
        operationKind,
        previousHead,
        request,
        AttestationHash.sha256(effect.encoded()));
  }

  private static AttestationOperationPayload payload(
      BigInteger operationOrder,
      String operationKind,
      AttestationHash previousHead,
      AttestationPreimage request,
      AttestationHash effectDigest) {
    return new AttestationOperationPayload(
        BOOK_ID,
        operationOrder,
        operationKind,
        previousHead,
        Instant.parse("2026-07-20T00:00:00Z"),
        AttestationHash.sha256(request.encoded()),
        effectDigest);
  }

  private static void assertGenesisFailure(
      AttestationPreimage request, AttestationPreimage effect) {
    assertGenesisFailure(
        genesisPayload(BigInteger.ZERO, ZERO_HEAD, request, effect), request, effect);
  }

  private static void assertGenesisFailure(
      AttestationOperationPayload payload,
      AttestationPreimage request,
      AttestationPreimage effect) {
    assertFailure(
        AttestationAuthorizationFailure.GENESIS_INVALID,
        () -> AttestationGenesisAuthorizationContext.verify(payload, request, effect));
  }
}
