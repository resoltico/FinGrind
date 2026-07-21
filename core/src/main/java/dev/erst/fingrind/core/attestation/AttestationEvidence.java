package dev.erst.fingrind.core.attestation;

import java.util.Objects;

/**
 * Immutable raw evidence for one persisted attested operation.
 *
 * <p>The three byte sequences are the canonical operation envelope, request preimage, and effect
 * preimage. They are deliberately opaque at this module boundary: consumers persist and transport
 * the evidence but cannot alter its semantics without the attestation verifier detecting it.
 */
public final class AttestationEvidence {
  private final byte[] operationEnvelope;
  private final byte[] requestPreimage;
  private final byte[] effectPreimage;

  /** Defensively owns the canonical evidence bytes. */
  public AttestationEvidence(
      byte[] operationEnvelope, byte[] requestPreimage, byte[] effectPreimage) {
    this.operationEnvelope = copy(operationEnvelope, "operationEnvelope");
    this.requestPreimage = copy(requestPreimage, "requestPreimage");
    this.effectPreimage = copy(effectPreimage, "effectPreimage");
  }

  public byte[] operationEnvelope() {
    return operationEnvelope.clone();
  }

  public byte[] requestPreimage() {
    return requestPreimage.clone();
  }

  public byte[] effectPreimage() {
    return effectPreimage.clone();
  }

  private static byte[] copy(byte[] value, String name) {
    return Objects.requireNonNull(value, name).clone();
  }
}
