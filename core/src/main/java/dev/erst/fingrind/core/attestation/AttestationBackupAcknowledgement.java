package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** Immutable identity of one on-chain acknowledgement for a published backup artifact. */
public final class AttestationBackupAcknowledgement {
  private final UUID backupId;
  private final byte[] backupArtifactDigest;
  private final BigInteger sourceOrder;
  private final byte[] sourceOperationHead;

  /** Owns the exact tuple whose replay is the only idempotent backup acknowledgement. */
  public AttestationBackupAcknowledgement(
      UUID backupId,
      byte[] backupArtifactDigest,
      BigInteger sourceOrder,
      byte[] sourceOperationHead) {
    this.backupId = Objects.requireNonNull(backupId, "backupId");
    this.backupArtifactDigest = copyHash(backupArtifactDigest, "backupArtifactDigest");
    this.sourceOrder = Objects.requireNonNull(sourceOrder, "sourceOrder");
    if (this.sourceOrder.signum() < 0 || this.sourceOrder.bitLength() > Long.SIZE) {
      throw new IllegalArgumentException("sourceOrder must be an unsigned 64-bit value.");
    }
    this.sourceOperationHead = copyHash(sourceOperationHead, "sourceOperationHead");
  }

  /** Returns the caller-selected immutable backup identity. */
  public UUID backupId() {
    return backupId;
  }

  /** Returns SHA-256 over the complete published backup container. */
  public byte[] backupArtifactDigest() {
    return backupArtifactDigest.clone();
  }

  /** Returns the source-chain order represented by the backup snapshot. */
  public BigInteger sourceOrder() {
    return sourceOrder;
  }

  /** Returns the source-chain head represented by the backup snapshot. */
  public byte[] sourceOperationHead() {
    return sourceOperationHead.clone();
  }

  boolean sameTuple(AttestationBackupAcknowledgement other) {
    AttestationBackupAcknowledgement checked = Objects.requireNonNull(other, "other");
    return backupId.equals(checked.backupId)
        && sourceOrder.equals(checked.sourceOrder)
        && Arrays.equals(backupArtifactDigest, checked.backupArtifactDigest)
        && Arrays.equals(sourceOperationHead, checked.sourceOperationHead);
  }

  private static byte[] copyHash(byte[] value, String name) {
    byte[] copy = Arrays.copyOf(Objects.requireNonNull(value, name), value.length);
    if (copy.length != 32) {
      throw new IllegalArgumentException(name + " must contain exactly 32 bytes.");
    }
    return copy;
  }
}
