package dev.erst.fingrind.core.attestation;

/** Converts one raw backup snapshot into immutable attestation evidence for the pure verifier. */
@FunctionalInterface
interface AttestationSnapshotDecoder {
  /** Decodes the complete raw snapshot into immutable operation evidence. */
  AttestationBook decode(byte[] snapshot);
}
