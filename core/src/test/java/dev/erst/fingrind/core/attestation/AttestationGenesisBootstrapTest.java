package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.credential;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisEffectPreimage;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisPayload;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisRequestPreimage;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.signedGenesisEnvelope;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Proves that genesis declarations are bound exactly to their immutable bootstrap effect. */
class AttestationGenesisBootstrapTest {
  @Test
  void rejectsMismatchedOrMissingFounderDeclarations() {
    TestCredential declaredFounder = credential();
    TestCredential effectFounder = credential();
    AttestationPreimage declaredRequest = genesisRequestPreimage(declaredFounder);
    AttestationPreimage effect = genesisEffectPreimage(effectFounder);
    AttestationHash zeroHead = AttestationHash.of(new byte[AttestationHash.BYTE_LENGTH]);

    assertFailure(
        AttestationAuthorizationFailure.GENESIS_INVALID,
        () ->
            AttestationGenesisAuthorizationContext.verify(
                genesisPayload(BigInteger.ZERO, zeroHead, declaredRequest, effect),
                declaredRequest,
                effect));

    AttestationPreimage missingFounderDeclaration = genesisRequestPreimage();
    assertFailure(
        AttestationAuthorizationFailure.GENESIS_INVALID,
        () ->
            AttestationGenesisAuthorizationContext.verify(
                genesisPayload(BigInteger.ZERO, zeroHead, missingFounderDeclaration, effect),
                missingFounderDeclaration,
                effect));
  }

  @Test
  void admitsOnlyExactActiveGenesisWorkflowPolicyDeclarations() {
    TestCredential founder = credential();
    UUID workflowId = UUID.randomUUID();
    AttestationPreimage request =
        requestWithInitialInterimWorkflow(genesisRequestPreimage(founder), workflowId);
    AttestationPreimage effect =
        effectWithInitialInterimWorkflow(genesisEffectPreimage(founder), workflowId);
    AttestationHash zeroHead = AttestationHash.of(new byte[AttestationHash.BYTE_LENGTH]);
    AttestationGenesisAuthorizationContext context =
        AttestationGenesisAuthorizationContext.verify(
            genesisPayload(BigInteger.ZERO, zeroHead, request, effect), request, effect);

    assertDoesNotThrow(
        () ->
            AttestationAuthorization.requireGenesis(
                context, signedGenesisEnvelope(context, founder)));

    AttestationPreimage mismatchedRequest =
        requestWithInitialInterimWorkflow(genesisRequestPreimage(founder), UUID.randomUUID());
    assertFailure(
        AttestationAuthorizationFailure.GENESIS_INVALID,
        () ->
            AttestationGenesisAuthorizationContext.verify(
                genesisPayload(BigInteger.ZERO, zeroHead, mismatchedRequest, effect),
                mismatchedRequest,
                effect));
  }

  private static AttestationPreimage requestWithInitialInterimWorkflow(
      AttestationPreimage request, UUID workflowId) {
    List<AttestationPreimage.Fact> records = new ArrayList<>(request.records());
    records.add(
        new AttestationPreimage.Fact(
            0x0184,
            List.of(
                AttestationField.present(AttestationBinaryFieldValue.uuid(workflowId)),
                AttestationField.present(
                    AttestationTextFieldValue.token(
                        AttestationSystemWorkflowKind.INTERIM_RESULT_SWEEP.wireToken())),
                AttestationField.present(AttestationTextFieldValue.text("3000")),
                AttestationField.absent(),
                AttestationField.absent(),
                AttestationField.present(AttestationNumericFieldValue.booleanValue(true)))));
    return AttestationPreimage.of(records);
  }

  private static AttestationPreimage effectWithInitialInterimWorkflow(
      AttestationPreimage effect, UUID workflowId) {
    List<AttestationPreimage.Fact> records = new ArrayList<>(effect.records());
    records.add(
        new AttestationPreimage.Fact(
            0x0008,
            List.of(
                AttestationField.present(AttestationNumericFieldValue.mutation(0)),
                AttestationField.present(AttestationBinaryFieldValue.uuid(workflowId)),
                AttestationField.present(
                    AttestationTextFieldValue.token(
                        AttestationSystemWorkflowKind.INTERIM_RESULT_SWEEP.wireToken())),
                AttestationField.present(AttestationTextFieldValue.text("3000")),
                AttestationField.absent(),
                AttestationField.absent(),
                AttestationField.present(AttestationNumericFieldValue.booleanValue(true)))));
    return AttestationPreimage.of(records);
  }
}
