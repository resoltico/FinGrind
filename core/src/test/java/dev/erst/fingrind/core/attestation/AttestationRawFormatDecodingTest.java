package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
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
}
