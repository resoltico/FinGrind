package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/** Immutable workflow evidence bound to a durable protected-book pair-recovery record. */
public sealed interface ProtectedBookPairPublicationBinding
    permits ProtectedBookPairPublicationBinding.Backup,
        ProtectedBookPairPublicationBinding.Restore,
        ProtectedBookPairPublicationBinding.Rekey {

  /** Canonical lifecycle operation whose staged pair is about to reach final publication. */
  OperationId operation();

  /** Binds a backup pair to the exact acknowledgement tuple that remains to be appended. */
  record Backup(Path sourceBookPath, AttestationBackupAcknowledgement acknowledgement)
      implements ProtectedBookPairPublicationBinding {
    public Backup {
      sourceBookPath = normalized(sourceBookPath, "sourceBookPath");
      Objects.requireNonNull(acknowledgement, "acknowledgement");
    }

    @Override
    public OperationId operation() {
      return OperationId.BACKUP_BOOK;
    }
  }

  /** Binds a restored pair to its verified backup source and exact staged restore operation. */
  record Restore(
      Path backupArtifactPath,
      Path backupKeyPath,
      AttestationBackupAcknowledgement acknowledgement,
      AttestationCommit attestationCommit)
      implements ProtectedBookPairPublicationBinding {
    public Restore {
      backupArtifactPath = normalized(backupArtifactPath, "backupArtifactPath");
      backupKeyPath = normalized(backupKeyPath, "backupKeyPath");
      if (backupArtifactPath.equals(backupKeyPath)) {
        throw new IllegalArgumentException("Restore source artifact and key paths must differ.");
      }
      Objects.requireNonNull(acknowledgement, "acknowledgement");
      Objects.requireNonNull(attestationCommit, "attestationCommit");
    }

    @Override
    public OperationId operation() {
      return OperationId.RESTORE_BOOK;
    }
  }

  /** Binds a rekey pair to the source head and exact staged rekey operation. */
  record Rekey(
      ProtectedBookPairPublicationSourceIdentity sourceIdentity,
      AttestationCommit sourceCommit,
      AttestationCommit attestationCommit)
      implements ProtectedBookPairPublicationBinding {
    public Rekey {
      Objects.requireNonNull(sourceIdentity, "sourceIdentity");
      Objects.requireNonNull(sourceCommit, "sourceCommit");
      Objects.requireNonNull(attestationCommit, "attestationCommit");
    }

    @Override
    public OperationId operation() {
      return OperationId.REKEY_BOOK;
    }
  }

  /** Returns whether this persisted binding admits the exact caller recovery request. */
  default boolean matches(ProtectedBookPairPublicationRecoveryRequest request) {
    Objects.requireNonNull(request, "request");
    return switch (this) {
      case Backup backup ->
          request instanceof ProtectedBookPairPublicationRecoveryRequest.Backup recovery
              && backup.sourceBookPath().equals(recovery.sourceBookPath())
              && backup.acknowledgement().backupId().equals(recovery.backupId());
      case Restore restore ->
          request instanceof ProtectedBookPairPublicationRecoveryRequest.Restore recovery
              && restore.backupArtifactPath().equals(recovery.backupArtifactPath())
              && restore.backupKeyPath().equals(recovery.backupKeyPath())
              && sameAcknowledgement(restore.acknowledgement(), recovery.acknowledgement());
      case Rekey rekey ->
          request instanceof ProtectedBookPairPublicationRecoveryRequest.Rekey recovery
              && rekey.sourceIdentity().equals(recovery.sourceIdentity());
    };
  }

  /** Compares the complete immutable acknowledgement tuple without exposing mutable arrays. */
  private static boolean sameAcknowledgement(
      AttestationBackupAcknowledgement first, AttestationBackupAcknowledgement second) {
    AttestationBackupAcknowledgement checkedFirst = Objects.requireNonNull(first, "first");
    AttestationBackupAcknowledgement checkedSecond = Objects.requireNonNull(second, "second");
    return checkedFirst.backupId().equals(checkedSecond.backupId())
        && checkedFirst.sourceOrder().equals(checkedSecond.sourceOrder())
        && Arrays.equals(checkedFirst.backupArtifactDigest(), checkedSecond.backupArtifactDigest())
        && Arrays.equals(checkedFirst.sourceOperationHead(), checkedSecond.sourceOperationHead());
  }

  private static Path normalized(Path path, String name) {
    return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
  }
}
