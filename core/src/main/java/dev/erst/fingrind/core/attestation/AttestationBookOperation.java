package dev.erst.fingrind.core.attestation;

import java.util.Objects;

/** One raw, self-contained operation evidence unit supplied to the protected-book verifier. */
final class AttestationBookOperation {
  private final AttestationDecodedEnvelope<AttestationOperationPayload> envelope;
  private final AttestationPreimage requestPreimage;
  private final AttestationPreimage effectPreimage;

  private AttestationBookOperation(
      AttestationDecodedEnvelope<AttestationOperationPayload> envelope,
      AttestationPreimage requestPreimage,
      AttestationPreimage effectPreimage) {
    this.envelope = Objects.requireNonNull(envelope, "envelope");
    this.requestPreimage = Objects.requireNonNull(requestPreimage, "requestPreimage");
    this.effectPreimage = Objects.requireNonNull(effectPreimage, "effectPreimage");
  }

  static AttestationBookOperation decode(
      byte[] envelopeBytes, byte[] requestPreimageBytes, byte[] effectPreimageBytes) {
    return new AttestationBookOperation(
        AttestationDecodedEnvelope.operation(envelopeBytes),
        AttestationPreimage.decode(
            requestPreimageBytes, AttestationAuthorizationFailure.PREIMAGE_INVALID),
        AttestationPreimage.decode(
            effectPreimageBytes, AttestationAuthorizationFailure.PREIMAGE_INVALID));
  }

  AttestationDecodedEnvelope<AttestationOperationPayload> envelope() {
    return envelope;
  }

  AttestationPreimage requestPreimage() {
    return requestPreimage;
  }

  AttestationPreimage effectPreimage() {
    return effectPreimage;
  }
}
