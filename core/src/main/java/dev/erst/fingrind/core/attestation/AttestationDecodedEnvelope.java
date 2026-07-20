package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

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
    this.authorizationEnvelope = Objects.requireNonNull(authorizationEnvelope, "authorizationEnvelope");
    this.encoded = AttestationEncoding.copy(encoded, "encoded");
  }

  static AttestationDecodedEnvelope<AttestationOperationPayload> operation(byte[] encoded) {
    Objects.requireNonNull(encoded, "encoded");
    if (encoded.length < 34) {
      throw failure(AttestationAuthorizationFailure.PREIMAGE_INVALID);
    }
    int operationKindLength = Byte.toUnsignedInt(encoded[33]);
    int payloadLength;
    try {
      payloadLength = Math.addExact(162, operationKindLength);
    } catch (ArithmeticException exception) {
      throw failure(AttestationAuthorizationFailure.PREIMAGE_INVALID);
    }
    return decode(
        encoded,
        payloadLength,
        AttestationOperationPayload::decode,
        AttestationAuthorizationFailure.PREIMAGE_INVALID);
  }

  static AttestationDecodedEnvelope<AttestationBackupManifestPayload> manifest(byte[] encoded) {
    return decode(
        encoded,
        121,
        AttestationBackupManifestPayload::decode,
        AttestationAuthorizationFailure.MANIFEST_INVALID);
  }

  static AttestationDecodedEnvelope<AttestationReceiptPayload> receipt(byte[] encoded) {
    return decode(
        encoded,
        97,
        AttestationReceiptPayload::decode,
        AttestationAuthorizationFailure.RECEIPT_INVALID);
  }

  private static <P extends AttestationPayload> AttestationDecodedEnvelope<P> decode(
      byte[] encoded,
      int payloadLength,
      Function<byte[], P> payloadDecoder,
      AttestationAuthorizationFailure failure) {
    try {
      AttestationByteReader input = new AttestationByteReader(encoded, failure);
      byte[] payloadBytes = input.readBytes(payloadLength);
      P payload = payloadDecoder.apply(payloadBytes);
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
    } catch (AttestationAuthorizationException exception) {
      throw exception;
    } catch (IllegalArgumentException | ArithmeticException exception) {
      throw failure(failure);
    }
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

  private static AttestationAuthorizationException failure(AttestationAuthorizationFailure failure) {
    return new AttestationAuthorizationException(failure);
  }
}
