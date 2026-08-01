package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** Unit coverage for typed values decoded from an already catalog-validated preimage fact. */
class AttestationPreimageValueReaderTest {
  private static final AttestationAuthorizationFailure FAILURE =
      AttestationAuthorizationFailure.GENESIS_INVALID;
  private static final TestCredential CREDENTIAL = AttestationAuthorizationTestSupport.credential();
  private static final AttestationHash HASH = AttestationHash.sha256(new byte[] {1});
  private static final UUID WORKFLOW_ID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
  private static final Map<AttestationFieldType, Supplier<AttestationFieldValue>> NUMERIC_VALUES =
      Map.of(
          AttestationFieldType.UNSIGNED_8, () -> AttestationNumericFieldValue.unsigned8(1),
          AttestationFieldType.UNSIGNED_16, () -> AttestationNumericFieldValue.unsigned16(1),
          AttestationFieldType.UNSIGNED_32,
              () -> AttestationNumericFieldValue.unsigned32(BigInteger.ONE),
          AttestationFieldType.UNSIGNED_64,
              () -> AttestationNumericFieldValue.unsigned64(BigInteger.ONE),
          AttestationFieldType.SIGNED_64,
              () -> AttestationNumericFieldValue.signed64(BigInteger.ONE),
          AttestationFieldType.SIGNED_128,
              () -> AttestationNumericFieldValue.signed128(BigInteger.ONE),
          AttestationFieldType.MONEY,
              () -> AttestationNumericFieldValue.money("EUR", false, BigInteger.ONE),
          AttestationFieldType.SCALED,
              () -> AttestationNumericFieldValue.scaled(0, false, BigInteger.ONE),
          AttestationFieldType.BOOLEAN, () -> AttestationNumericFieldValue.booleanValue(true),
          AttestationFieldType.MUTATION, () -> AttestationNumericFieldValue.mutation(0));
  private static final Map<AttestationFieldType, Supplier<AttestationFieldValue>> BINARY_VALUES =
      Map.of(
          AttestationFieldType.UUID, () -> AttestationBinaryFieldValue.uuid(WORKFLOW_ID),
          AttestationFieldType.HASH, () -> AttestationBinaryFieldValue.hash(HASH),
          AttestationFieldType.SPKI,
              () -> AttestationBinaryFieldValue.spki(CREDENTIAL.pair().getPublic().getEncoded()),
          AttestationFieldType.BYTES, () -> AttestationBinaryFieldValue.bytes(new byte[] {3, 4}),
          AttestationFieldType.EMBEDDED,
              () -> AttestationBinaryFieldValue.embedded(new byte[] {7, 8}));
  private static final Map<AttestationFieldType, Supplier<AttestationFieldValue>> TEXTUAL_VALUES =
      Map.of(
          AttestationFieldType.TOKEN, () -> AttestationTextFieldValue.token("value"),
          AttestationFieldType.TEXT, () -> AttestationTextFieldValue.text("text"),
          AttestationFieldType.CURRENCY, () -> AttestationTextFieldValue.currency("EUR"),
          AttestationFieldType.DATE,
              () -> AttestationTextFieldValue.date(LocalDate.of(2026, 7, 20)),
          AttestationFieldType.INSTANT,
              () -> AttestationTextFieldValue.instant(Instant.parse("2026-07-20T00:00:00.000Z")));

  @Test
  void readsRequiredAndOptionalTextAndBooleanValues() {
    AttestationPreimage.Fact fact = systemWorkflowFact(true);
    AttestationPreimage.Fact inactiveFact = systemWorkflowFact(false);

    assertEquals("required", AttestationPreimageValueReader.text(fact, 3, FAILURE));
    assertEquals("optional", AttestationPreimageOptionalValueReader.text(fact, 4, FAILURE));
    assertNull(AttestationPreimageOptionalValueReader.text(fact, 5, FAILURE));
    assertTrue(AttestationPreimageValueReader.booleanValue(fact, 6, FAILURE));
    assertFalse(AttestationPreimageValueReader.booleanValue(inactiveFact, 6, FAILURE));
    assertFailure(FAILURE, () -> AttestationPreimageValueReader.text(fact, 5, FAILURE));
  }

  @Test
  void readsEveryDirectTypedValueAndBothOptionalPresenceStates() {
    AttestationPreimage.Fact command = validFact(0x0100);
    AttestationPreimage.Fact founder = validFact(0x0102);
    AttestationPreimage.Fact acknowledgement = validFact(0x0006);
    AttestationPreimage.Fact posting = validFact(0x0120);
    AttestationPreimage.Fact policy = validFact(0x0103);
    AttestationPreimage.Fact binding = validFact(0x0002);
    AttestationPreimage.Fact qualifiedFact = validFact(0x00A1);

    assertEquals("value", AttestationPreimageValueReader.token(command, 0, FAILURE));
    assertEquals(WORKFLOW_ID, AttestationPreimageValueReader.uuid(founder, 0, FAILURE));
    assertEquals(HASH, AttestationPreimageValueReader.hash(founder, 1, FAILURE));
    assertArrayEquals(
        CREDENTIAL.pair().getPublic().getEncoded(),
        AttestationPreimageValueReader.spki(founder, 2, FAILURE).bytes());
    assertEquals(
        LocalDate.of(2026, 7, 20),
        AttestationPreimageValueReader.date(validFact(0x0101), 11, FAILURE));
    assertEquals(
        BigInteger.ONE, AttestationPreimageValueReader.unsigned64(acknowledgement, 3, FAILURE));
    assertEquals(BigInteger.ONE, AttestationPreimageValueReader.unsigned32(posting, 0, FAILURE));
    assertEquals(0, AttestationPreimageValueReader.mutation(acknowledgement, 0, FAILURE));
    assertEquals(1, AttestationPreimageValueReader.unsigned16(policy, 1, FAILURE));
    assertArrayEquals(
        new byte[] {7, 8}, AttestationPreimageValueReader.embedded(qualifiedFact, 2, FAILURE));
    assertEquals(HASH, AttestationPreimageOptionalValueReader.hash(binding, 6, FAILURE));
    assertNull(
        AttestationPreimageOptionalValueReader.hash(
            replaceField(binding, 6, AttestationField.absent()), 6, FAILURE));
  }

  @Test
  void rejectsMalformedEmbeddedEncodingsWithTheTypedFailure() {
    AttestationPreimage.Fact qualifiedFact = validFact(0x00A1);
    AttestationPreimage.Fact nonEmbedded = qualifiedFact;
    AttestationPreimage.Fact shortEmbedded =
        replaceField(qualifiedFact, 2, present(AttestationFieldType.EMBEDDED, new byte[] {1, 2}));
    AttestationPreimage.Fact negativeLengthEmbedded =
        replaceField(
            qualifiedFact,
            2,
            present(AttestationFieldType.EMBEDDED, ByteBuffer.allocate(4).putInt(-1).array()));
    AttestationPreimage.Fact inconsistentEmbedded =
        replaceField(
            qualifiedFact,
            2,
            present(AttestationFieldType.EMBEDDED, ByteBuffer.allocate(4).putInt(3).array()));

    assertFailure(FAILURE, () -> AttestationPreimageValueReader.embedded(nonEmbedded, 0, FAILURE));
    assertFailure(
        FAILURE, () -> AttestationPreimageValueReader.embedded(shortEmbedded, 2, FAILURE));
    assertFailure(
        FAILURE, () -> AttestationPreimageValueReader.embedded(negativeLengthEmbedded, 2, FAILURE));
    assertFailure(
        FAILURE, () -> AttestationPreimageValueReader.embedded(inconsistentEmbedded, 2, FAILURE));
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

  private static AttestationPreimage.Fact validFact(int recordTypeTag) {
    AttestationRecordSchema schema = AttestationPreimageCatalog.require(recordTypeTag);
    List<AttestationField> fields = new ArrayList<>(schema.fieldCount());
    for (int index = 0; index < schema.fieldCount(); index++) {
      fields.add(AttestationField.present(value(schema.fieldSchema(index).type())));
    }
    return new AttestationPreimage.Fact(recordTypeTag, fields);
  }

  private static AttestationPreimage.Fact replaceField(
      AttestationPreimage.Fact fact, int fieldIndex, AttestationField replacement) {
    List<AttestationField> fields = new ArrayList<>(fact.fields());
    fields.set(fieldIndex, replacement);
    return new AttestationPreimage.Fact(fact.recordTypeTag(), fields);
  }

  private static AttestationField present(AttestationFieldType type, byte[] encoded) {
    return AttestationField.present(
        AttestationFieldValue.encode(type, output -> output.writeBytes(encoded)));
  }

  private static AttestationFieldValue value(AttestationFieldType type) {
    Supplier<AttestationFieldValue> factory = NUMERIC_VALUES.get(type);
    if (factory == null) {
      factory = BINARY_VALUES.get(type);
    }
    if (factory == null) {
      factory = TEXTUAL_VALUES.get(type);
    }
    if (factory == null) {
      throw new IllegalArgumentException("No test value factory is available for " + type + ".");
    }
    return factory.get();
  }
}
