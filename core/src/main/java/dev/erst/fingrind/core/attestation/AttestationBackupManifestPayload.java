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

  AttestationBackupManifestPayload(
      UUID bookId,
      UUID backupId,
      BigInteger sourceOrder,
      AttestationHash sourceOperationHead,
      AttestationHash snapshotDigest) {
    this.bookId = Objects.requireNonNull(bookId, "bookId");
    this.backupId = Objects.requireNonNull(backupId, "backupId");
    this.sourceOrder =
        AttestationUnsignedEncoding.requireUnsigned(sourceOrder, Long.BYTES, "sourceOrder");
    this.sourceOperationHead = Objects.requireNonNull(sourceOperationHead, "sourceOperationHead");
    this.snapshotDigest = Objects.requireNonNull(snapshotDigest, "snapshotDigest");
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
    AttestationTextEncoding.appendToken(output, AttestationEncoding.ALGORITHM_ID, "algorithmId");
    return output.toByteArray();
  }
}
