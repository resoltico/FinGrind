package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Proves that raw parser inputs retain the exact bytes that the verifier must authenticate. */
class AttestationRawFormatDecodingTest {
  @Test
  void decodesEveryPublishedStandaloneEnvelopeWithoutCanonicalizingReceivedEntries()
      throws IOException {
    assertOperationVector();
    assertManifestVector();
    assertReceiptVector();
  }

  @Test
  void rejectsTruncatedAndUnsupportedPreimagesWithTheProtocolFailure() throws IOException {
    byte[] encoded =
        AttestationAuthorizationTestSupport.requestPreimage(
                AttestationOperationKind.POST_ENTRY, AttestationSourceChannel.CLI, null)
            .encoded();

    assertArrayEquals(
        encoded,
        AttestationPreimage.decode(encoded, AttestationAuthorizationFailure.PREIMAGE_INVALID)
            .encoded());
    assertFailure(
        AttestationAuthorizationFailure.PREIMAGE_INVALID,
        () ->
            AttestationPreimage.decode(
                Arrays.copyOf(encoded, encoded.length - 1),
                AttestationAuthorizationFailure.PREIMAGE_INVALID));

    byte[] unsupported =
        AttestationDocumentVectors.bytes(
            AttestationDocumentVectors.PROTOCOL_DOCUMENT, "V-OP-01", "payload");
    unsupported[8] = 2;
    assertFailure(
        AttestationAuthorizationFailure.UNSUPPORTED_VERSION,
        () -> AttestationOperationPayload.decode(unsupported));
  }

  @Test
  void reportsAMalformedRawOperationEnvelopeBeforeItReachesAuthorization() throws IOException {
    byte[] encoded =
        AttestationDocumentVectors.bytes(
            AttestationDocumentVectors.PROTOCOL_DOCUMENT, "V-OP-02", "envelope");
    byte[] truncated = Arrays.copyOf(encoded, encoded.length - 1);

    assertFailure(
        AttestationAuthorizationFailure.PREIMAGE_INVALID,
        () -> AttestationDecodedEnvelope.operation(truncated));
  }

  @Test
  void framesEveryBoundedAlgorithmIdentifierBeforeTheSignatureEntries() throws IOException {
    byte[] operation =
        AttestationDocumentVectors.bytes(
            AttestationDocumentVectors.PROTOCOL_DOCUMENT, "V-OP-02", "envelope");
    byte[] manifest =
        AttestationDocumentVectors.bytes(
            AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-MANIFEST-02", "envelope");
    byte[] receipt =
        AttestationDocumentVectors.bytes(
            AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-RECEIPT-02", "envelope");

    for (String algorithmId : new String[] {"ed2551", "ed255190"}) {
      assertEquals(
          algorithmId,
          AttestationDecodedEnvelope.operation(
                  replaceAlgorithmId(operation, operationAlgorithmOffset(operation), algorithmId))
              .payload()
              .algorithmId());
      assertEquals(
          algorithmId,
          AttestationDecodedEnvelope.manifest(
                  replaceAlgorithmId(manifest, manifestAlgorithmOffset(), algorithmId))
              .payload()
              .algorithmId());
      assertEquals(
          algorithmId,
          AttestationDecodedEnvelope.receipt(
                  replaceAlgorithmId(receipt, receiptAlgorithmOffset(), algorithmId))
              .payload()
              .algorithmId());
    }
  }

  @Test
  void rejectsAlgorithmIdentifiersLongerThanTheWireBound() throws IOException {
    String oversized = "a".repeat(33);
    byte[] operation =
        AttestationDocumentVectors.bytes(
            AttestationDocumentVectors.PROTOCOL_DOCUMENT, "V-OP-02", "envelope");
    byte[] manifest =
        AttestationDocumentVectors.bytes(
            AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-MANIFEST-02", "envelope");
    byte[] receipt =
        AttestationDocumentVectors.bytes(
            AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-RECEIPT-02", "envelope");

    assertFailure(
        AttestationAuthorizationFailure.PREIMAGE_INVALID,
        () ->
            AttestationDecodedEnvelope.operation(
                replaceAlgorithmId(operation, operationAlgorithmOffset(operation), oversized)));
    assertFailure(
        AttestationAuthorizationFailure.MANIFEST_INVALID,
        () ->
            AttestationDecodedEnvelope.manifest(
                replaceAlgorithmId(manifest, manifestAlgorithmOffset(), oversized)));
    assertFailure(
        AttestationAuthorizationFailure.RECEIPT_INVALID,
        () ->
            AttestationDecodedEnvelope.receipt(
                replaceAlgorithmId(receipt, receiptAlgorithmOffset(), oversized)));
  }

  @Test
  void splitsThePublishedContainerFromItsTrailerAndRejectsFramingTampering() throws IOException {
    byte[] encoded =
        AttestationDocumentVectors.bytes(
            AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-CONTAINER-01", "container");
    AttestationDecodedArtifact decoded = AttestationDecodedArtifact.decode(encoded);

    assertArrayEquals(
        AttestationDocumentVectors.bytes(
            AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-CONTAINER-01", "snapshot"),
        decoded.snapshot());
    assertEquals(
        AttestationDocumentVectors.value(
            AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-CONTAINER-01", "containerDigest"),
        decoded.digest().hex());

    byte[] invalidLength = encoded.clone();
    invalidLength[encoded.length - 12] ^= 1;
    assertFailure(
        AttestationAuthorizationFailure.MANIFEST_INVALID,
        () -> AttestationDecodedArtifact.decode(invalidLength));
  }

  private static void assertOperationVector() throws IOException {
    byte[] encoded =
        AttestationDocumentVectors.bytes(
            AttestationDocumentVectors.PROTOCOL_DOCUMENT, "V-OP-02", "envelope");
    AttestationDecodedEnvelope<AttestationOperationPayload> decoded =
        AttestationDecodedEnvelope.operation(encoded);

    assertArrayEquals(encoded, decoded.encoded());
    assertEquals(
        AttestationDocumentVectors.value(
            AttestationDocumentVectors.PROTOCOL_DOCUMENT, "V-OP-02", "head"),
        decoded.head().hex());
  }

  private static void assertManifestVector() throws IOException {
    byte[] encoded =
        AttestationDocumentVectors.bytes(
            AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-MANIFEST-02", "envelope");
    AttestationDecodedEnvelope<AttestationBackupManifestPayload> decoded =
        AttestationDecodedEnvelope.manifest(encoded);

    assertArrayEquals(encoded, decoded.encoded());
    assertEquals(
        AttestationDocumentVectors.value(
            AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-MANIFEST-02", "head"),
        decoded.head().hex());
  }

  private static void assertReceiptVector() throws IOException {
    byte[] encoded =
        AttestationDocumentVectors.bytes(
            AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-RECEIPT-02", "envelope");
    AttestationDecodedEnvelope<AttestationReceiptPayload> decoded =
        AttestationDecodedEnvelope.receipt(encoded);

    assertArrayEquals(encoded, decoded.encoded());
    assertEquals(
        AttestationDocumentVectors.value(
            AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-RECEIPT-02", "head"),
        decoded.head().hex());
  }

  private static int operationAlgorithmOffset(byte[] envelope) {
    int operationKindLengthOffset = 8 + 1 + 16 + Long.BYTES;
    return operationKindLengthOffset + 1 + Byte.toUnsignedInt(envelope[operationKindLengthOffset]);
  }

  private static int manifestAlgorithmOffset() {
    return 8 + 1 + 16 + 16 + Long.BYTES + AttestationHash.BYTE_LENGTH + AttestationHash.BYTE_LENGTH;
  }

  private static int receiptAlgorithmOffset() {
    return 8 + 1 + 16 + Long.BYTES + AttestationHash.BYTE_LENGTH + 24;
  }

  private static byte[] replaceAlgorithmId(byte[] encoded, int lengthOffset, String replacement) {
    byte[] replacementBytes = replacement.getBytes(StandardCharsets.US_ASCII);
    int previousLength = Byte.toUnsignedInt(encoded[lengthOffset]);
    byte[] replaced = new byte[encoded.length - previousLength + replacementBytes.length];
    System.arraycopy(encoded, 0, replaced, 0, lengthOffset);
    replaced[lengthOffset] = (byte) replacementBytes.length;
    System.arraycopy(replacementBytes, 0, replaced, lengthOffset + 1, replacementBytes.length);
    System.arraycopy(
        encoded,
        lengthOffset + 1 + previousLength,
        replaced,
        lengthOffset + 1 + replacementBytes.length,
        encoded.length - lengthOffset - 1 - previousLength);
    return replaced;
  }
}
