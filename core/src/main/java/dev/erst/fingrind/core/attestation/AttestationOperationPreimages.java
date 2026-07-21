package dev.erst.fingrind.core.attestation;

import java.util.Arrays;
import java.util.Objects;

/** Immutable canonical request/effect bytes ready for the transaction-local signing boundary. */
public final class AttestationOperationPreimages {
  private final byte[] request;
  private final byte[] effect;

  /** Defensively owns verified-canonical preimage byte sequences. */
  public AttestationOperationPreimages(byte[] request, byte[] effect) {
    this.request = copy(request, "request");
    this.effect = copy(effect, "effect");
  }

  /** Returns the canonical semantic request preimage. */
  public byte[] request() {
    return request.clone();
  }

  /** Returns the canonical committed-domain-effect preimage. */
  public byte[] effect() {
    return effect.clone();
  }

  private static byte[] copy(byte[] value, String name) {
    return Arrays.copyOf(Objects.requireNonNull(value, name), value.length);
  }
}
