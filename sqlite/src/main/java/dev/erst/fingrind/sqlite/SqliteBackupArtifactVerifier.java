package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.attestation.AttestationArtifactSnapshotReader;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;

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
        SqliteBookMaintenanceFiles.normalize(normalizedBackupArtifactPath, "backupFilePath");
    Path checkedKeyPath =
        SqliteBookMaintenanceFiles.normalize(normalizedBackupKeyFilePath, "backupKeyFilePath");
    try {
      SqliteProtectedBookStagingFiles.requireRegularNonSymlinkFile(checkedArtifactPath);
      byte[] artifact = Files.readAllBytes(checkedArtifactPath);
      try (SqliteVerifiedBackupSnapshot snapshot =
          new SqliteVerifiedBackupSnapshot(
              SqliteOwnedStagedArtifact.create(
                  checkedArtifactPath, ".artifact-snapshot-", ".sqlite"))) {
        AttestationArtifactSnapshotReader reader =
            artifactSnapshot -> {
              writeSnapshot(snapshot.stagedPath(), artifactSnapshot);
              snapshot.attachBook(openVerifiedSnapshot(snapshot.stagedPath(), checkedKeyPath));
              return loadAttestationEvidence(snapshot.book());
            };
        AttestationBackupArtifactVerification verification =
            AttestationBackupArtifact.verify(artifact, reader);
        return snapshot.transfer(verification);
      }
    } catch (java.io.IOException exception) {
      throw new IllegalStateException("Failed to read the selected backup artifact.", exception);
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

  private SqliteVerifiedBook openVerifiedSnapshot(Path snapshotPath, Path backupKeyPath) {
    return SqliteBookKeyFile.loadDecision(backupKeyPath)
        .fold(
            passphrase -> {
              ProtectedBookMaintenanceStore.BookVerification verification =
                  verificationSupport.verifyResolvedBook(snapshotPath, passphrase);
              if (verification instanceof SqliteVerifiedBook verifiedBook) {
                return verifiedBook;
              }
              ProtectedBookMaintenanceStore.VerificationFailure failure =
                  (ProtectedBookMaintenanceStore.VerificationFailure) verification;
              throw new IllegalArgumentException(
                  "Backup artifact snapshot cannot be opened with the selected backup key: "
                      + failure.failure().name());
            },
            failure -> {
              throw new IllegalArgumentException(
                  "Backup artifact key cannot be opened: " + failure.code());
            });
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
    try (FileChannel channel =
        FileChannel.open(
            stagedPath, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
      ByteBuffer buffer = ByteBuffer.wrap(checkedSnapshot);
      while (buffer.hasRemaining()) {
        channel.write(buffer);
      }
      channel.force(true);
      SqliteBookFileSecurity.hardenBookArtifacts(stagedPath);
    } catch (java.io.IOException exception) {
      throw new IllegalStateException(
          "Failed to stage the encrypted backup artifact snapshot.", exception);
    }
  }
}
