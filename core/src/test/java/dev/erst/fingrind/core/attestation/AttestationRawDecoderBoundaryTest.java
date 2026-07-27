package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.credential;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Exercises every bounded primitive and canonical scalar branch at the raw format boundary. */
class AttestationRawDecoderBoundaryTest {
  @Test
  void readsPrimitiveAndCanonicalValuesWithoutNormalizingTheReceivedBytes() {
    assertEquals(BigInteger.valueOf(255), reader(new byte[] {(byte) 255}).readUnsigned(1));
    assertEquals(BigInteger.valueOf(-1), reader(new byte[] {(byte) 255}).readSigned(1));
    assertEquals(0, reader(new byte[] {1, 2}).offset());
    assertEquals(1, reader(new byte[] {1, 2}).sourceSlice(0, 1).length);
    assertTrue(reader(new byte[] {1, 2}).hasRemaining(2));
    assertFalse(reader(new byte[] {1, 2}).hasRemaining(-1));

    assertEquals("post", AttestationCanonicalValueReader.token(reader(token("post"))));
    assertEquals("book", AttestationCanonicalValueReader.text(reader(text("book"))));
    assertEquals("EUR", AttestationCanonicalValueReader.currency(reader(currency("EUR"))));
    assertEquals(
        LocalDate.of(2026, 7, 20),
        AttestationCanonicalValueReader.date(reader(date(LocalDate.of(2026, 7, 20)))));
    assertEquals(
        Instant.parse("2026-07-20T00:00:00.000Z"),
        AttestationCanonicalValueReader.instant(
            reader(instant(Instant.parse("2026-07-20T00:00:00.000Z")))));
    TestCredential credential = credential();
    assertEquals(
        AttestationSpki.of(credential.pair().getPublic().getEncoded()),
        AttestationCanonicalValueReader.spki(
            reader(
                AttestationBinaryFieldValue.spki(credential.pair().getPublic().getEncoded())
                    .encoded())));

    assertFailure(
        AttestationAuthorizationFailure.PREIMAGE_INVALID,
        () ->
            AttestationCanonicalValueReader.text(reader(new byte[] {0, 0, 0, 2, (byte) 195, 40})));
    AttestationByteReader ascii = reader(new byte[] {'a'});
    assertFailure(AttestationAuthorizationFailure.PREIMAGE_INVALID, () -> ascii.requireAscii("b"));
    AttestationByteReader trailing = reader(new byte[] {1});
    assertFailure(AttestationAuthorizationFailure.PREIMAGE_INVALID, trailing::requireAtEnd);
    assertFailure(
        AttestationAuthorizationFailure.PREIMAGE_INVALID, () -> reader(new byte[0]).readBytes(1));
  }

  @Test
  void decodesEveryFieldTypeAndRejectsInvalidBooleanAndSignEncodings() {
    TestCredential credential = credential();
    assertType(new byte[] {1}, AttestationFieldType.UNSIGNED_8);
    assertType(new byte[] {0, 1}, AttestationFieldType.UNSIGNED_16);
    assertType(new byte[] {0, 0, 0, 1}, AttestationFieldType.UNSIGNED_32);
    assertType(new byte[] {0, 0, 0, 0, 0, 0, 0, 1}, AttestationFieldType.UNSIGNED_64);
    assertType(
        new byte[] {(byte) 255, (byte) 255, (byte) 255, (byte) 255, 0, 0, 0, 1},
        AttestationFieldType.SIGNED_64);
    assertType(new byte[16], AttestationFieldType.SIGNED_128);
    assertType(uuidBytes(), AttestationFieldType.UUID);
    assertType(AttestationHash.sha256(new byte[] {1}).bytes(), AttestationFieldType.HASH);
    assertType(spkiBytes(credential), AttestationFieldType.SPKI);
    assertType(bytes(new byte[] {1, 2, 3}), AttestationFieldType.BYTES);
    assertType(embedded(new byte[] {1, 2, 3}), AttestationFieldType.EMBEDDED);
    assertType(token("post"), AttestationFieldType.TOKEN);
    assertType(text("book"), AttestationFieldType.TEXT);
    assertType(currency("EUR"), AttestationFieldType.CURRENCY);
    assertType(date(LocalDate.of(2026, 7, 20)), AttestationFieldType.DATE);
    assertType(instant(Instant.parse("2026-07-20T00:00:00.000Z")), AttestationFieldType.INSTANT);
    assertType(money("EUR", false, BigInteger.ONE), AttestationFieldType.MONEY);
    assertType(money("EUR", true, BigInteger.ONE), AttestationFieldType.MONEY);
    assertType(scaled(2, false, BigInteger.ONE), AttestationFieldType.SCALED);
    assertType(scaled(2, true, BigInteger.ONE), AttestationFieldType.SCALED);
    assertType(new byte[] {1}, AttestationFieldType.BOOLEAN);
    assertType(new byte[] {0}, AttestationFieldType.BOOLEAN);
    assertType(new byte[] {0}, AttestationFieldType.MUTATION);

    assertFailure(
        AttestationAuthorizationFailure.PREIMAGE_INVALID,
        () ->
            AttestationFieldValueDecoder.decode(
                reader(new byte[] {2}), AttestationFieldType.BOOLEAN));
    assertFailure(
        AttestationAuthorizationFailure.PREIMAGE_INVALID,
        () ->
            AttestationFieldValueDecoder.decode(
                reader(new byte[] {'E', 'U', 'R', 2, 0, 0}), AttestationFieldType.MONEY));
    assertFailure(
        AttestationAuthorizationFailure.PREIMAGE_INVALID,
        () ->
            AttestationFieldValueDecoder.decode(
                reader(new byte[] {0, 2, 0, 0}), AttestationFieldType.SCALED));
  }

  @Test
  void classifiesFormatFailuresAndRawEnvelopeAndArtifactBoundaryFailures() throws IOException {
    IllegalStateException cause = new IllegalStateException("format");
    AttestationAuthorizationException classified =
        assertThrows(
            AttestationAuthorizationException.class,
            () ->
                AttestationFormatFailure.decoding(
                    AttestationAuthorizationFailure.PREIMAGE_INVALID,
                    () -> {
                      throw cause;
                    }));
    assertEquals(AttestationAuthorizationFailure.PREIMAGE_INVALID, classified.failure());
    assertSame(cause, classified.getCause());
    AttestationAuthorizationException original =
        new AttestationAuthorizationException(
            AttestationAuthorizationFailure.KEY_ALGORITHM_INVALID);
    assertSame(
        original,
        assertThrows(
            AttestationAuthorizationException.class,
            () ->
                AttestationFormatFailure.decoding(
                    AttestationAuthorizationFailure.PREIMAGE_INVALID,
                    () -> {
                      throw original;
                    })));

    assertFailure(
        AttestationAuthorizationFailure.PREIMAGE_INVALID,
        () -> AttestationDecodedEnvelope.operation(new byte[33]));
    byte[] operation = operationEnvelope();
    int payloadLength = 162 + Byte.toUnsignedInt(operation[33]);
    operation[payloadLength] = (byte) 255;
    operation[payloadLength + 1] = (byte) 255;
    assertFailure(
        AttestationAuthorizationFailure.PREIMAGE_INVALID,
        () -> AttestationDecodedEnvelope.operation(operation));

    assertFailure(
        AttestationAuthorizationFailure.MANIFEST_INVALID,
        () -> AttestationDecodedArtifact.decode(new byte[20]));
    assertFailure(
        AttestationAuthorizationFailure.UNSUPPORTED_VERSION,
        () -> AttestationDecodedArtifact.decode(trailer((byte) 2)));
    byte[] operationPayload = operationPayload();
    byte[] manifestPayload = manifestPayload();
    byte[] receiptPayload = receiptPayload();
    assertFailure(
        AttestationAuthorizationFailure.UNSUPPORTED_VERSION,
        () -> AttestationBackupManifestPayload.decode(withVersion(manifestPayload, (byte) 2)));
    assertEquals(
        "ed25510",
        AttestationBackupManifestPayload.decode(withAlgorithm(manifestPayload)).algorithmId());
    assertFailure(
        AttestationAuthorizationFailure.UNSUPPORTED_VERSION,
        () -> AttestationReceiptPayload.decode(withVersion(receiptPayload, (byte) 2)));
    assertEquals(
        "ed25510", AttestationReceiptPayload.decode(withAlgorithm(receiptPayload)).algorithmId());
    assertEquals(
        "xd25519",
        AttestationOperationPayload.decode(withOperationAlgorithm(operationPayload)).algorithmId());

    byte[] artifact =
        AttestationDocumentVectors.bytes(
            AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-CONTAINER-01", "container");
    assertEquals(artifact.length, AttestationDecodedArtifact.decode(artifact).encoded().length);
    byte[] impossibleSnapshotLength = artifact.clone();
    Arrays.fill(
        impossibleSnapshotLength,
        impossibleSnapshotLength.length - Integer.BYTES,
        impossibleSnapshotLength.length,
        (byte) 255);
    impossibleSnapshotLength[impossibleSnapshotLength.length - Integer.BYTES] = 127;
    assertFailure(
        AttestationAuthorizationFailure.MANIFEST_INVALID,
        () -> AttestationDecodedArtifact.decode(impossibleSnapshotLength));
    byte[] mismatchedSnapshotLength = artifact.clone();
    mismatchedSnapshotLength[mismatchedSnapshotLength.length - 5] ^= 1;
    assertFailure(
        AttestationAuthorizationFailure.MANIFEST_INVALID,
        () -> AttestationDecodedArtifact.decode(mismatchedSnapshotLength));

    AttestationBackupManifestPayload manifest =
        new AttestationBackupManifestPayload(
            AttestationAuthorizationTestSupport.BOOK_ID,
            AttestationAuthorizationTestSupport.SYSTEM_WORKFLOW_ID,
            BigInteger.ONE,
            AttestationHash.sha256(new byte[] {4}),
            AttestationHash.sha256(new byte[] {5}));
    AttestationReceiptPayload receipt =
        new AttestationReceiptPayload(
            AttestationAuthorizationTestSupport.BOOK_ID,
            BigInteger.ONE,
            AttestationHash.sha256(new byte[] {6}),
            Instant.parse("2026-07-20T00:00:00Z"));
    assertEquals(121, manifest.encoded().length);
    assertEquals(AttestationAuthorizationTestSupport.SYSTEM_WORKFLOW_ID, manifest.backupId());
    assertEquals(97, receipt.encoded().length);
    assertEquals(Instant.parse("2026-07-20T00:00:00Z"), receipt.receiptTimestamp());
  }

  @Test
  void rejectsEveryNoncanonicalPreimageStructureBeforeItCanBecomeEvidence() {
    AttestationPreimage encoded =
        AttestationAuthorizationTestSupport.requestPreimage(
            AttestationOperationKind.POST_ENTRY,
            AttestationSourceChannel.SYSTEM,
            AttestationAuthorizationTestSupport.SYSTEM_WORKFLOW_ID);
    byte[] bytes = encoded.encoded();
    int firstRecordLength = recordLength(encoded.records().getFirst());
    byte[] reordered = new byte[bytes.length];
    System.arraycopy(bytes, 0, reordered, 0, Integer.BYTES);
    System.arraycopy(
        bytes,
        Integer.BYTES + firstRecordLength,
        reordered,
        Integer.BYTES,
        bytes.length - Integer.BYTES - firstRecordLength);
    System.arraycopy(
        bytes, Integer.BYTES, reordered, bytes.length - firstRecordLength, firstRecordLength);
    byte[] wrongFieldCount = bytes.clone();
    wrongFieldCount[Integer.BYTES + Short.BYTES + 1]++;
    byte[] invalidPresence = bytes.clone();
    invalidPresence[Integer.BYTES + Short.BYTES + Short.BYTES] = 2;
    byte[] oversizedCount = new byte[] {0, 15, 66, 65};

    assertFailure(
        AttestationAuthorizationFailure.PREIMAGE_INVALID,
        () ->
            AttestationPreimage.decode(
                reordered, AttestationAuthorizationFailure.PREIMAGE_INVALID));
    assertFailure(
        AttestationAuthorizationFailure.PREIMAGE_INVALID,
        () ->
            AttestationPreimage.decode(
                wrongFieldCount, AttestationAuthorizationFailure.PREIMAGE_INVALID));
    assertFailure(
        AttestationAuthorizationFailure.PREIMAGE_INVALID,
        () ->
            AttestationPreimage.decode(
                invalidPresence, AttestationAuthorizationFailure.PREIMAGE_INVALID));
    assertFailure(
        AttestationAuthorizationFailure.PREIMAGE_INVALID,
        () ->
            AttestationPreimage.decode(
                oversizedCount, AttestationAuthorizationFailure.PREIMAGE_INVALID));
    assertFailure(
        AttestationAuthorizationFailure.PREIMAGE_INVALID,
        () ->
            AttestationPreimage.decode(
                Arrays.copyOf(bytes, bytes.length + 1),
                AttestationAuthorizationFailure.PREIMAGE_INVALID));

    AttestationPreimage.Fact mutation =
        new AttestationPreimage.Fact(
            0x0006,
            List.of(
                AttestationField.present(AttestationNumericFieldValue.mutation(1)),
                AttestationField.present(
                    AttestationBinaryFieldValue.uuid(AttestationAuthorizationTestSupport.BOOK_ID)),
                AttestationField.present(
                    AttestationBinaryFieldValue.hash(AttestationHash.sha256(new byte[] {7}))),
                AttestationField.present(AttestationNumericFieldValue.unsigned64(BigInteger.ZERO)),
                AttestationField.present(
                    AttestationBinaryFieldValue.hash(AttestationHash.sha256(new byte[] {8})))));
    assertEquals(
        1,
        AttestationPreimageValueReader.mutation(
            mutation, 0, AttestationAuthorizationFailure.PREIMAGE_INVALID));
    assertEquals(
        BigInteger.ZERO,
        AttestationPreimageValueReader.unsigned64(
            mutation, 3, AttestationAuthorizationFailure.PREIMAGE_INVALID));
  }

  private static AttestationByteReader reader(byte[] encoded) {
    return new AttestationByteReader(encoded, AttestationAuthorizationFailure.PREIMAGE_INVALID);
  }

  private static void assertType(byte[] encoded, AttestationFieldType type) {
    assertEquals(type, AttestationFieldValueDecoder.decode(reader(encoded), type).type());
  }

  private static byte[] uuidBytes() {
    return AttestationBinaryFieldValue.uuid(AttestationAuthorizationTestSupport.BOOK_ID).encoded();
  }

  private static byte[] spkiBytes(TestCredential credential) {
    return AttestationBinaryFieldValue.spki(credential.pair().getPublic().getEncoded()).encoded();
  }

  private static byte[] token(String value) {
    return AttestationTextFieldValue.token(value).encoded();
  }

  private static byte[] text(String value) {
    return AttestationTextFieldValue.text(value).encoded();
  }

  private static byte[] currency(String value) {
    return AttestationTextFieldValue.currency(value).encoded();
  }

  private static byte[] date(LocalDate value) {
    return AttestationTextFieldValue.date(value).encoded();
  }

  private static byte[] instant(Instant value) {
    return AttestationTextFieldValue.instant(value).encoded();
  }

  private static byte[] bytes(byte[] value) {
    return AttestationBinaryFieldValue.bytes(value).encoded();
  }

  private static byte[] embedded(byte[] value) {
    return AttestationBinaryFieldValue.embedded(value).encoded();
  }

  private static byte[] money(String currency, boolean negative, BigInteger minorUnits) {
    return AttestationNumericFieldValue.money(currency, negative, minorUnits).encoded();
  }

  private static byte[] scaled(int scale, boolean negative, BigInteger units) {
    return AttestationNumericFieldValue.scaled(scale, negative, units).encoded();
  }

  private static byte[] operationEnvelope() throws IOException {
    return AttestationDocumentVectors.bytes(
        AttestationDocumentVectors.PROTOCOL_DOCUMENT, "V-OP-02", "envelope");
  }

  private static int recordLength(AttestationPreimage.Fact fact) {
    return Short.BYTES
        + Short.BYTES
        + fact.fields().stream().mapToInt(field -> field.encoded().length).sum();
  }

  private static byte[] operationPayload() throws IOException {
    return AttestationDocumentVectors.bytes(
        AttestationDocumentVectors.PROTOCOL_DOCUMENT, "V-OP-01", "payload");
  }

  private static byte[] manifestPayload() throws IOException {
    return AttestationDocumentVectors.bytes(
        AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-MANIFEST-02", "payload");
  }

  private static byte[] receiptPayload() throws IOException {
    return AttestationDocumentVectors.bytes(
        AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-RECEIPT-02", "payload");
  }

  private static byte[] withVersion(byte[] payload, byte version) {
    byte[] copy = payload.clone();
    copy[8] = version;
    return copy;
  }

  private static byte[] withAlgorithm(byte[] payload) {
    byte[] copy = payload.clone();
    copy[copy.length - 1] = '0';
    return copy;
  }

  private static byte[] withOperationAlgorithm(byte[] payload) {
    byte[] copy = payload.clone();
    int operationKindLengthOffset = 9 + 16 + Long.BYTES;
    int algorithmOffset =
        operationKindLengthOffset + 1 + Byte.toUnsignedInt(copy[operationKindLengthOffset]);
    copy[algorithmOffset + 1] = 'x';
    return copy;
  }

  private static byte[] trailer(byte version) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    AttestationTextEncoding.appendAscii(output, "FGATBMF1");
    output.write(version);
    output.writeBytes(new byte[12]);
    return output.toByteArray();
  }
}
