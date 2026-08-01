package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** Successful independent verification of one manifest-attested backup artifact. */
public final class AttestationBackupArtifactVerification {
  private final byte[] snapshot;
  private final UUID bookId;
  private final UUID backupId;
  private final BigInteger sourceOrder;
  private final byte[] sourceOperationHead;
  private final byte[] snapshotDigest;
  private final byte[] artifactDigest;
  private final AttestationVerification sourceVerification;

  AttestationBackupArtifactVerification(
      byte[] snapshot,
      UUID bookId,
      UUID backupId,
      BigInteger sourceOrder,
      byte[] sourceOperationHead,
      byte[] snapshotDigest,
      byte[] artifactDigest,
      AttestationVerification sourceVerification) {
    this.snapshot = copy(snapshot, "snapshot", -1);
    this.bookId = Objects.requireNonNull(bookId, "bookId");
    this.backupId = Objects.requireNonNull(backupId, "backupId");
    this.sourceOrder = Objects.requireNonNull(sourceOrder, "sourceOrder");
    this.sourceOperationHead = copy(sourceOperationHead, "sourceOperationHead", 32);
    this.snapshotDigest = copy(snapshotDigest, "snapshotDigest", 32);
    this.artifactDigest = copy(artifactDigest, "artifactDigest", 32);
    this.sourceVerification = Objects.requireNonNull(sourceVerification, "sourceVerification");
  }

  /** Returns the verified opaque snapshot bytes needed to stage a restored protected book. */
  public byte[] snapshot() {
    return snapshot.clone();
  }

  /** Returns the restored book's preserved identity. */
  public UUID bookId() {
    return bookId;
  }

  /** Returns the manifest's caller-selected, immutable backup identity. */
  public UUID backupId() {
    return backupId;
  }

  /** Returns the authenticated source-chain position represented by the snapshot. */
  public BigInteger sourceOrder() {
    return sourceOrder;
  }

  /** Returns the authenticated head at {@link #sourceOrder()}. */
  public byte[] sourceOperationHead() {
    return sourceOperationHead.clone();
  }

  /** Returns SHA-256 over the opaque snapshot block. */
  public byte[] snapshotDigest() {
    return snapshotDigest.clone();
  }

  /** Returns SHA-256 over the complete published artifact container. */
  public byte[] artifactDigest() {
    return artifactDigest.clone();
  }

  /** Returns the independently reconstructed and verified source-chain state. */
  public AttestationVerification sourceVerification() {
    return sourceVerification;
  }

  private static byte[] copy(byte[] value, String name, int requiredLength) {
    byte[] copy = Arrays.copyOf(Objects.requireNonNull(value, name), value.length);
    if (requiredLength >= 0 && copy.length != requiredLength) {
      throw new IllegalArgumentException(
          name + " must contain exactly " + requiredLength + " bytes.");
    }
    return copy;
  }
}
