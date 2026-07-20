package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

/** Raw backup artifact split by its final self-describing trailer without trusting SQLite state. */
final class AttestationDecodedArtifact {
  private static final int TRAILER_BYTE_LENGTH = 21;
  private static final String TRAILER_MAGIC = "FGATBMF1";

  private final byte[] snapshot;
  private final AttestationDecodedEnvelope<AttestationBackupManifestPayload> manifest;
  private final byte[] encoded;

  private AttestationDecodedArtifact(
      byte[] snapshot,
      AttestationDecodedEnvelope<AttestationBackupManifestPayload> manifest,
      byte[] encoded) {
    this.snapshot = AttestationEncoding.copy(snapshot, "snapshot");
    this.manifest = Objects.requireNonNull(manifest, "manifest");
    this.encoded = AttestationEncoding.copy(encoded, "encoded");
  }

  static AttestationDecodedArtifact decode(byte[] encoded) {
    try {
      byte[] checkedEncoded = AttestationEncoding.copy(encoded, "encoded");
      if (checkedEncoded.length < TRAILER_BYTE_LENGTH) {
        throw failure();
      }
      int trailerOffset = checkedEncoded.length - TRAILER_BYTE_LENGTH;
      AttestationByteReader trailer =
          new AttestationByteReader(
              Arrays.copyOfRange(checkedEncoded, trailerOffset, checkedEncoded.length),
              AttestationAuthorizationFailure.MANIFEST_INVALID);
      trailer.requireAscii(TRAILER_MAGIC);
      if (trailer.readUnsigned(Byte.BYTES).intValueExact() != 1) {
        throw new AttestationAuthorizationException(
            AttestationAuthorizationFailure.UNSUPPORTED_VERSION);
      }
      int snapshotLength = intLength(trailer.readUnsigned(Long.BYTES));
      int manifestLength = intLength(trailer.readUnsigned(Integer.BYTES));
      trailer.requireAtEnd();
      int actualSnapshotLength = checkedEncoded.length - TRAILER_BYTE_LENGTH - manifestLength;
      if (actualSnapshotLength < 0 || actualSnapshotLength != snapshotLength) {
        throw failure();
      }
      byte[] snapshot = Arrays.copyOfRange(checkedEncoded, 0, actualSnapshotLength);
      byte[] manifestBytes =
          Arrays.copyOfRange(checkedEncoded, actualSnapshotLength, trailerOffset);
      AttestationDecodedEnvelope<AttestationBackupManifestPayload> manifest =
          AttestationDecodedEnvelope.manifest(manifestBytes);
      return new AttestationDecodedArtifact(snapshot, manifest, checkedEncoded);
    } catch (AttestationAuthorizationException exception) {
      throw exception;
    } catch (IllegalArgumentException | ArithmeticException exception) {
      throw failure();
    }
  }

  byte[] snapshot() {
    return snapshot.clone();
  }

  AttestationDecodedEnvelope<AttestationBackupManifestPayload> manifest() {
    return manifest;
  }

  byte[] encoded() {
    return encoded.clone();
  }

  AttestationHash digest() {
    return AttestationHash.sha256(encoded);
  }

  private static int intLength(BigInteger value) {
    if (value.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
      throw failure();
    }
    return value.intValueExact();
  }

  private static AttestationAuthorizationException failure() {
    return new AttestationAuthorizationException(AttestationAuthorizationFailure.MANIFEST_INVALID);
  }
}
