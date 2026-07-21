package dev.erst.fingrind.core.attestation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One typed payload and its raw signature envelope, decoded without normalizing attacker input.
 *
 * <p>The raw encoding remains authoritative for operation-head calculation. The authorization
 * envelope intentionally retains the received entry order so the shared verifier can classify an
 * ordering failure before any convenience canonicalization occurs.
 */
final class AttestationDecodedEnvelope<P extends AttestationPayload> {
  private static final int MAX_ENTRY_COUNT = 65_534;

  private final P payload;
  private final AttestationAuthorizationEnvelope authorizationEnvelope;
  private final byte[] encoded;

  private AttestationDecodedEnvelope(
      P payload, AttestationAuthorizationEnvelope authorizationEnvelope, byte[] encoded) {
    this.payload = Objects.requireNonNull(payload, "payload");
    this.authorizationEnvelope =
        Objects.requireNonNull(authorizationEnvelope, "authorizationEnvelope");
    this.encoded = AttestationEncoding.copy(encoded, "encoded");
  }

  static AttestationDecodedEnvelope<AttestationOperationPayload> operation(byte[] encoded) {
    return decode(
        encoded,
        AttestationOperationPayload::decode,
        AttestationAuthorizationFailure.PREIMAGE_INVALID);
  }

  static AttestationDecodedEnvelope<AttestationBackupManifestPayload> manifest(byte[] encoded) {
    return decode(
        encoded,
        AttestationBackupManifestPayload::decode,
        AttestationAuthorizationFailure.MANIFEST_INVALID);
  }

  static AttestationDecodedEnvelope<AttestationReceiptPayload> receipt(byte[] encoded) {
    return decode(
        encoded,
        AttestationReceiptPayload::decode,
        AttestationAuthorizationFailure.RECEIPT_INVALID);
  }

  private static <P extends AttestationPayload> AttestationDecodedEnvelope<P> decode(
      byte[] encoded, PayloadDecoder<P> payloadDecoder, AttestationAuthorizationFailure failure) {
    return AttestationFormatFailure.decoding(
        failure, () -> decodeUnchecked(encoded, payloadDecoder, failure));
  }

  private static <P extends AttestationPayload> AttestationDecodedEnvelope<P> decodeUnchecked(
      byte[] encoded, PayloadDecoder<P> payloadDecoder, AttestationAuthorizationFailure failure) {
    AttestationByteReader input = new AttestationByteReader(encoded, failure);
    P payload = payloadDecoder.decode(input);
    byte[] payloadBytes = input.sourceSlice(0, input.offset());
    int entryCount = input.readUnsigned(Short.BYTES).intValueExact();
    if (entryCount > MAX_ENTRY_COUNT) {
      throw input.failure();
    }
    List<AttestationSignatureEntry> entries = new ArrayList<>(entryCount);
    for (int index = 0; index < entryCount; index++) {
      entries.add(
          new AttestationSignatureEntry(input.readUuid(), input.readHash(), input.readBytes(64)));
    }
    input.requireAtEnd();
    return new AttestationDecodedEnvelope<>(
        payload, new AttestationAuthorizationEnvelope(payloadBytes, entries), encoded);
  }

  P payload() {
    return payload;
  }

  AttestationAuthorizationEnvelope authorizationEnvelope() {
    return authorizationEnvelope;
  }

  byte[] encoded() {
    return encoded.clone();
  }

  AttestationHash head() {
    return AttestationHash.sha256(encoded);
  }

  /** Decodes one complete typed payload while advancing the shared raw-envelope reader. */
  @FunctionalInterface
  private interface PayloadDecoder<P extends AttestationPayload> {
    /** Returns the payload that occupies the reader's next complete raw prefix. */
    P decode(AttestationByteReader input);
  }
}
