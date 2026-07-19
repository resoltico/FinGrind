package dev.erst.fingrind.core.attestation;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.Objects;

/** Canonical snapshot-plus-manifest container for an independently verifiable backup artifact. */
final class AttestationArtifactContainer {
  private final byte[] snapshot;
  private final AttestationEnvelope<AttestationBackupManifestPayload> manifest;

  AttestationArtifactContainer(
      byte[] snapshot, AttestationEnvelope<AttestationBackupManifestPayload> manifest) {
    this.snapshot = AttestationEncoding.copy(snapshot, "snapshot");
    this.manifest = Objects.requireNonNull(manifest, "manifest");
  }

  byte[] snapshot() {
    return snapshot.clone();
  }

  AttestationEnvelope<AttestationBackupManifestPayload> manifest() {
    return manifest;
  }

  byte[] trailer() {
    byte[] manifestBytes = manifest.encoded();
    ByteArrayOutputStream output = new ByteArrayOutputStream(21);
    AttestationTextEncoding.appendAscii(output, "FGATBMF1");
    AttestationUnsignedEncoding.appendByte(output, 1, "containerVersion");
    AttestationUnsignedEncoding.appendUnsigned(
        output, BigInteger.valueOf(snapshot.length), Long.BYTES, "snapshotLength");
    AttestationUnsignedEncoding.appendUnsigned(
        output, BigInteger.valueOf(manifestBytes.length), Integer.BYTES, "manifestEnvelopeLength");
    return output.toByteArray();
  }

  byte[] encoded() {
    byte[] manifestBytes = manifest.encoded();
    byte[] trailerBytes = trailer();
    ByteArrayOutputStream output =
        new ByteArrayOutputStream(
            Math.addExact(
                snapshot.length, Math.addExact(manifestBytes.length, trailerBytes.length)));
    output.writeBytes(snapshot);
    output.writeBytes(manifestBytes);
    output.writeBytes(trailerBytes);
    return output.toByteArray();
  }

  AttestationHash digest() {
    return AttestationHash.sha256(encoded());
  }
}
