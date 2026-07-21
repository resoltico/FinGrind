package dev.erst.fingrind.core.attestation;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;

/** Canonical backup-manifest payload signed independently of the source book acknowledgement. */
final class AttestationBackupManifestPayload implements AttestationPayload {
  private final UUID bookId;
  private final UUID backupId;
  private final BigInteger sourceOrder;
  private final AttestationHash sourceOperationHead;
  private final AttestationHash snapshotDigest;
  private final String algorithmId;

  AttestationBackupManifestPayload(
      UUID bookId,
      UUID backupId,
      BigInteger sourceOrder,
      AttestationHash sourceOperationHead,
      AttestationHash snapshotDigest) {
    this(
        bookId,
        backupId,
        sourceOrder,
        sourceOperationHead,
        snapshotDigest,
        AttestationAlgorithm.ED25519.id());
  }

  private AttestationBackupManifestPayload(
      UUID bookId,
      UUID backupId,
      BigInteger sourceOrder,
      AttestationHash sourceOperationHead,
      AttestationHash snapshotDigest,
      String algorithmId) {
    this.bookId = Objects.requireNonNull(bookId, "bookId");
    this.backupId = Objects.requireNonNull(backupId, "backupId");
    this.sourceOrder =
        AttestationUnsignedEncoding.requireUnsigned(sourceOrder, Long.BYTES, "sourceOrder");
    this.sourceOperationHead = Objects.requireNonNull(sourceOperationHead, "sourceOperationHead");
    this.snapshotDigest = Objects.requireNonNull(snapshotDigest, "snapshotDigest");
    this.algorithmId = Objects.requireNonNull(algorithmId, "algorithmId");
  }

  /** Decodes one complete backup-manifest payload at the raw attestation boundary. */
  static AttestationBackupManifestPayload decode(byte[] encoded) {
    return AttestationFormatFailure.decoding(
        AttestationAuthorizationFailure.MANIFEST_INVALID, () -> decodeUnchecked(encoded));
  }

  private static AttestationBackupManifestPayload decodeUnchecked(byte[] encoded) {
    AttestationByteReader input =
        new AttestationByteReader(encoded, AttestationAuthorizationFailure.MANIFEST_INVALID);
    input.requireAscii("FGATTBM1");
    if (input.readUnsigned(Byte.BYTES).intValueExact() != 1) {
      throw new AttestationAuthorizationException(
          AttestationAuthorizationFailure.UNSUPPORTED_VERSION);
    }
    UUID bookId = input.readUuid();
    UUID backupId = input.readUuid();
    BigInteger sourceOrder = input.readUnsigned(Long.BYTES);
    AttestationHash sourceOperationHead = input.readHash();
    AttestationHash snapshotDigest = input.readHash();
    String algorithmId = AttestationCanonicalValueReader.token(input);
    AttestationBackupManifestPayload payload =
        new AttestationBackupManifestPayload(
            bookId, backupId, sourceOrder, sourceOperationHead, snapshotDigest, algorithmId);
    input.requireAtEnd();
    return payload;
  }

  @Override
  public byte[] encoded() {
    ByteArrayOutputStream output = new ByteArrayOutputStream(121);
    AttestationTextEncoding.appendAscii(output, "FGATTBM1");
    AttestationUnsignedEncoding.appendByte(output, 1, "manifestVersion");
    AttestationEncoding.appendUuid(output, bookId);
    AttestationEncoding.appendUuid(output, backupId);
    AttestationUnsignedEncoding.appendUnsigned(output, sourceOrder, Long.BYTES, "sourceOrder");
    AttestationEncoding.appendHash(output, sourceOperationHead);
    AttestationEncoding.appendHash(output, snapshotDigest);
    AttestationTextEncoding.appendToken(output, algorithmId, "algorithmId");
    return output.toByteArray();
  }

  BigInteger sourceOrder() {
    return sourceOrder;
  }

  UUID bookId() {
    return bookId;
  }

  UUID backupId() {
    return backupId;
  }

  AttestationHash sourceOperationHead() {
    return sourceOperationHead;
  }

  AttestationHash snapshotDigest() {
    return snapshotDigest;
  }

  @Override
  public String algorithmId() {
    return algorithmId;
  }
}
