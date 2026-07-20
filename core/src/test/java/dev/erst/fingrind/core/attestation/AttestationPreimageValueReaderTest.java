package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit coverage for typed values decoded from an already catalog-validated preimage fact. */
class AttestationPreimageValueReaderTest {
  private static final AttestationAuthorizationFailure FAILURE =
      AttestationAuthorizationFailure.GENESIS_INVALID;
  private static final UUID WORKFLOW_ID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");

  @Test
  void readsRequiredAndOptionalTextAndBooleanValues() {
    AttestationPreimage.Fact fact = systemWorkflowFact(true);
    AttestationPreimage.Fact inactiveFact = systemWorkflowFact(false);

    assertEquals("required", AttestationPreimageValueReader.text(fact, 3, FAILURE));
    assertEquals("optional", AttestationPreimageValueReader.optionalText(fact, 4, FAILURE));
    assertNull(AttestationPreimageValueReader.optionalText(fact, 5, FAILURE));
    assertTrue(AttestationPreimageValueReader.booleanValue(fact, 6, FAILURE));
    assertFalse(AttestationPreimageValueReader.booleanValue(inactiveFact, 6, FAILURE));
    assertFailure(FAILURE, () -> AttestationPreimageValueReader.text(fact, 5, FAILURE));
  }

  private static AttestationPreimage.Fact systemWorkflowFact(boolean active) {
    return new AttestationPreimage.Fact(
        0x0008,
        List.of(
            AttestationField.present(AttestationNumericFieldValue.mutation(0)),
            AttestationField.present(AttestationBinaryFieldValue.uuid(WORKFLOW_ID)),
            AttestationField.present(
                AttestationTextFieldValue.token(
                    AttestationSystemWorkflowKind.INTERIM_RESULT_SWEEP.wireToken())),
            AttestationField.present(AttestationTextFieldValue.text("required")),
            AttestationField.present(AttestationTextFieldValue.text("optional")),
            AttestationField.absent(),
            AttestationField.present(AttestationNumericFieldValue.booleanValue(active))));
  }
}
