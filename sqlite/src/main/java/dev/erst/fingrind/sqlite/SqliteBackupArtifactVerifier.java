package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.attestation.AttestationArtifactSnapshotReader;
import dev.erst.fingrind.core.attestation.AttestationArtifactSnapshotReaderException;
import dev.erst.fingrind.core.attestation.AttestationBackupArtifact;
import dev.erst.fingrind.core.attestation.AttestationBackupArtifactVerification;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.AttestedProtectedBookMaintenanceStore.VerifiedBackupArtifact;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Verifies an external backup artifact by opening its manifest-authenticated temporary snapshot.
 */
final class SqliteBackupArtifactVerifier {
  private final SqliteProtectedBookVerificationSupport verificationSupport;

  SqliteBackupArtifactVerifier(SqliteProtectedBookVerificationSupport verificationSupport) {
    this.verificationSupport = Objects.requireNonNull(verificationSupport, "verificationSupport");
  }

  VerifiedBackupArtifact verify(
      Path normalizedBackupArtifactPath, Path normalizedBackupKeyFilePath) {
    Path checkedArtifactPath =
        Objects.requireNonNull(normalizedBackupArtifactPath, "normalizedBackupArtifactPath")
            .toAbsolutePath()
            .normalize();
    Path checkedKeyPath =
        Objects.requireNonNull(normalizedBackupKeyFilePath, "normalizedBackupKeyFilePath")
            .toAbsolutePath()
            .normalize();
    try {
      checkedArtifactPath = normalizeBackupArtifactPath(normalizedBackupArtifactPath);
      checkedKeyPath = normalizeBackupKeyPath(normalizedBackupKeyFilePath);
      Path snapshotKeyPath = checkedKeyPath;
      SqliteProtectedBookStagingFiles.requireRegularNonSymlinkFile(checkedArtifactPath);
      byte[] artifact = SqliteSecureRegularFileAccess.readAllBytes(checkedArtifactPath);
      try (SqliteVerifiedBackupSnapshot snapshot =
          new SqliteVerifiedBackupSnapshot(
              SqliteOwnedStagedArtifact.create(
                  checkedArtifactPath, ".artifact-snapshot-", ".sqlite"))) {
        AttestationArtifactSnapshotReader reader =
            artifactSnapshot -> {
              writeSnapshot(snapshot.stagedPath(), artifactSnapshot);
              snapshot.attachBook(openVerifiedSnapshot(snapshot.stagedPath(), snapshotKeyPath));
              return loadAttestationEvidence(snapshot.book());
            };
        AttestationBackupArtifactVerification verification =
            AttestationBackupArtifact.verify(artifact, reader);
        return snapshot.transfer(verification);
      }
    } catch (java.io.IOException exception) {
      throw new IllegalStateException("Failed to read the selected backup artifact.", exception);
    } catch (ProtectedBookMaintenanceRejectionException exception) {
      throw exception;
    } catch (BackupKeyVerificationException exception) {
      throw new ProtectedBookMaintenanceRejectionException(
          new ProtectedBookMaintenanceRejection.ArtifactVerificationFailed(
              ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_SOURCE,
              checkedKeyPath,
              ProtectedBookVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED),
          exception);
    } catch (SqliteCallerPathContractException exception) {
      throw SqliteProtectedBookMaintenanceArtifactStore.maintenanceRejection(
          ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE, exception);
    } catch (RuntimeException exception) {
      throw new ProtectedBookMaintenanceRejectionException(
          new ProtectedBookMaintenanceRejection.ArtifactVerificationFailed(
              ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE,
              checkedArtifactPath,
              ProtectedBookVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED),
          exception);
    }
  }

  private static Path normalizeBackupArtifactPath(Path backupArtifactPath) {
    try {
      return SqliteBookMaintenanceFiles.normalizeExistingSource(
          backupArtifactPath, "backupFilePath");
    } catch (SqliteCallerPathContractException exception) {
      throw SqliteProtectedBookMaintenanceArtifactStore.maintenanceRejection(
          ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE, exception);
    }
  }

  private static Path normalizeBackupKeyPath(Path backupKeyPath) {
    try {
      return SqliteBookMaintenanceFiles.normalizeExistingSource(backupKeyPath, "backupKeyFilePath");
    } catch (SqliteCallerPathContractException exception) {
      throw SqliteProtectedBookMaintenanceArtifactStore.maintenanceRejection(
          ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_SOURCE, exception);
    }
  }

  private SqliteVerifiedBook openVerifiedSnapshot(Path snapshotPath, Path backupKeyPath) {
    return verifySnapshotWithBackupPassphrase(snapshotPath, loadBackupKey(backupKeyPath));
  }

  /** Verifies one snapshot while transferring the supplied passphrase into its verified handle. */
  private SqliteVerifiedBook verifySnapshotWithBackupPassphrase(
      Path snapshotPath, SqliteBookPassphrase backupPassphrase) {
    ProtectedBookMaintenanceStore.BookVerification verification =
        verificationSupport.verifyResolvedBook(snapshotPath, backupPassphrase);
    if (verification instanceof SqliteVerifiedBook verifiedBook) {
      return verifiedBook;
    }
    ProtectedBookMaintenanceStore.VerificationFailure failure =
        (ProtectedBookMaintenanceStore.VerificationFailure) verification;
    throw new IllegalArgumentException(
        "Backup artifact snapshot cannot be opened as an initialized FinGrind book: "
            + failure.failure().name());
  }

  /** Loads only the separately selected backup-key source under its own failure classification. */
  private static SqliteBookPassphrase loadBackupKey(Path backupKeyPath) {
    try {
      return SqliteBookKeyFile.loadDecision(backupKeyPath)
          .fold(Optional::of, ignored -> Optional.<SqliteBookPassphrase>empty())
          .orElseThrow(
              () -> new BackupKeyVerificationException("Backup artifact key cannot be opened."));
    } catch (BackupKeyVerificationException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new BackupKeyVerificationException("Backup artifact key cannot be opened.", exception);
    }
  }

  /** Marks a selected backup-key failure without conflating it with an artifact failure. */
  private static final class BackupKeyVerificationException
      extends AttestationArtifactSnapshotReaderException {
    private static final long serialVersionUID = 1L;

    private BackupKeyVerificationException(String message) {
      super(message);
    }

    private BackupKeyVerificationException(String message, RuntimeException cause) {
      super(message, cause);
    }
  }

  private static List<AttestationEvidence> loadAttestationEvidence(
      SqliteVerifiedBook verifiedBook) {
    try (SqliteBookPassphrase passphrase = verifiedBook.passphraseCopy();
        SqliteNativeDatabase database =
            SqliteNativeConnections.open(
                verifiedBook.artifactPath(), passphrase, SqliteNativeOpenMode.READ_ONLY)) {
      return SqliteAttestationEvidenceStore.loadAll(database);
    }
  }

  private static void writeSnapshot(Path stagedPath, byte[] snapshot) {
    byte[] checkedSnapshot = Objects.requireNonNull(snapshot, "snapshot").clone();
    try (FileChannel channel = SqliteSecureRegularFileAccess.openTruncatingWrite(stagedPath)) {
      writeSnapshotBytes(channel, checkedSnapshot);
      channel.force(true);
    } catch (java.io.IOException exception) {
      throw new IllegalStateException(
          "Failed to stage the encrypted backup artifact snapshot.", exception);
    }
  }

  private static void writeSnapshotBytes(FileChannel channel, byte[] snapshot)
      throws java.io.IOException {
    ByteBuffer buffer = ByteBuffer.wrap(snapshot);
    while (buffer.hasRemaining()) {
      if (channel.write(buffer) <= 0) {
        throw new java.io.IOException(
            "Failed to write the complete encrypted backup artifact snapshot.");
      }
    }
  }
}
