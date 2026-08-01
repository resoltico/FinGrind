package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.attestation.AttestationBackupArtifactVerification;
import dev.erst.fingrind.executor.spi.AttestedProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.util.Objects;

/** Owns the verified decrypted artifact snapshot until restored-book staging has consumed it. */
final class SqliteVerifiedBackupArtifact
    implements AttestedProtectedBookMaintenanceStore.VerifiedBackupArtifact {
  private final SqliteOwnedStagedArtifact snapshotStage;
  private final SqliteVerifiedBook snapshotBook;
  private final AttestationBackupArtifactVerification verification;
  private boolean closed;

  SqliteVerifiedBackupArtifact(
      SqliteOwnedStagedArtifact snapshotStage,
      SqliteVerifiedBook snapshotBook,
      AttestationBackupArtifactVerification verification) {
    this.snapshotStage = Objects.requireNonNull(snapshotStage, "snapshotStage");
    this.snapshotBook = Objects.requireNonNull(snapshotBook, "snapshotBook");
    this.verification = Objects.requireNonNull(verification, "verification");
  }

  @Override
  public AttestationBackupArtifactVerification verification() {
    requireOpen();
    return verification;
  }

  @Override
  public ProtectedBookMaintenanceStore.VerifiedBook snapshotBook() {
    requireOpen();
    return snapshotBook;
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    try {
      snapshotBook.close();
    } finally {
      snapshotStage.releaseRetained();
      closed = true;
    }
  }

  private void requireOpen() {
    if (closed) {
      throw new IllegalStateException("The verified backup artifact is already closed.");
    }
  }
}
