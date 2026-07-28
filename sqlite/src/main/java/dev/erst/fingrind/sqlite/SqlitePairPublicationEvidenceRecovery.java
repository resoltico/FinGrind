package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/** Repairs and force-confirms evidence after a protected-book pair reservation exists. */
final class SqlitePairPublicationEvidenceRecovery {
  private SqlitePairPublicationEvidenceRecovery() {}

  static void forceForRecoveredPublication(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer,
      SqliteProtectedBookPairPublicationRecord.RecoveryRecordFileForcer recordFileForcer)
      throws IOException {
    SqliteProtectedBookPairPublicationRecord checkedRecord =
        Objects.requireNonNull(record, "record");
    requireMandatoryRecoveryEvidence(checkedRecord);
    SqliteProtectedBookPairPublicationRecord.RecoveryRecordFileForcer checkedFileForcer =
        Objects.requireNonNull(recordFileForcer, "recordFileForcer");
    SqliteProtectedBookPublicationSupport.PairDirectoryForcer checkedDirectoryForcer =
        Objects.requireNonNull(directoryForcer, "directoryForcer");
    for (SqliteProtectedBookPairPublicationEvidenceKind kind :
        SqliteProtectedBookPairPublicationEvidenceKind.values()) {
      forceMandatoryEvidence(checkedRecord, kind, checkedFileForcer, checkedDirectoryForcer);
    }
  }

  static void repairIncompleteEvidence(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer)
      throws IOException {
    SqliteProtectedBookPairPublicationRecord checkedRecord =
        Objects.requireNonNull(record, "record");
    requireHeldEvidenceDirectoryLeases(checkedRecord);
    SqlitePairPublicationEvidenceStatus.requireComplete(
        checkedRecord, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM);
    complete(checkedRecord, SqliteProtectedBookPairPublicationEvidenceKind.INTENT, directoryForcer);
    complete(
        checkedRecord, SqliteProtectedBookPairPublicationEvidenceKind.RECOVERY, directoryForcer);
    if (SqlitePairPublicationEvidenceStatus.hasObserved(
        checkedRecord, SqliteProtectedBookPairPublicationEvidenceKind.RETAINED)) {
      complete(
          checkedRecord, SqliteProtectedBookPairPublicationEvidenceKind.RETAINED, directoryForcer);
    }
    if (SqlitePairPublicationEvidenceStatus.hasObserved(
        checkedRecord, SqliteProtectedBookPairPublicationEvidenceKind.COMPLETED)) {
      complete(
          checkedRecord, SqliteProtectedBookPairPublicationEvidenceKind.COMPLETED, directoryForcer);
    }
  }

  static void retainPrepublication(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer)
      throws IOException {
    SqliteProtectedBookPairPublicationRecord checkedRecord =
        Objects.requireNonNull(record, "record");
    requireMandatoryRecoveryEvidence(checkedRecord);
    complete(
        checkedRecord, SqliteProtectedBookPairPublicationEvidenceKind.RETAINED, directoryForcer);
  }

  static void confirmCompletedPublication(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer)
      throws IOException {
    SqliteProtectedBookPairPublicationRecord checkedRecord =
        Objects.requireNonNull(record, "record");
    requireMandatoryRecoveryEvidence(checkedRecord);
    if (!checkedRecord.finalBookMatches() || !checkedRecord.finalSecretMatches()) {
      throw new IOException(
          "The protected-book pair final members changed before durable completion was recorded.");
    }
    complete(
        checkedRecord, SqliteProtectedBookPairPublicationEvidenceKind.COMPLETED, directoryForcer);
  }

  static void writeNew(
      Path path,
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPairPublicationEvidenceKind kind)
      throws IOException {
    byte[] content =
        SqliteProtectedBookPairPublicationEvidenceCodec.encoded(record, kind)
            .getBytes(StandardCharsets.UTF_8);
    if (content.length > SqliteSecureRegularFileAccess.MAXIMUM_RECOVERY_METADATA_BYTES) {
      throw new IOException("Protected-book pair recovery metadata exceeds its supported size.");
    }
    try (FileChannel channel = SqliteSecureRegularFileAccess.openNewWrite(path)) {
      ByteBuffer bytes = ByteBuffer.wrap(content);
      while (bytes.hasRemaining()) {
        if (channel.write(bytes) <= 0) {
          throw new IOException(
              "Failed to write the complete protected-book pair recovery evidence.");
        }
      }
      channel.force(true);
    }
  }

  static void forceCopy(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPairPublicationEvidenceKind kind,
      Path evidencePath,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer)
      throws IOException {
    try {
      SqlitePairPublicationEvidenceStatus.requireExact(record, kind, evidencePath);
      Objects.requireNonNull(directoryForcer, "directoryForcer")
          .force(
              SqlitePairPublicationEvidenceStatus.durabilityStep(kind),
              SqlitePairPublicationRecordIntegrity.parentOf(evidencePath));
    } catch (IOException | RuntimeException failure) {
      throw new SqliteProtectedBookPairPublicationRecord
          .RecoveryRecordDurabilityUnconfirmedException(failure);
    }
  }

  private static void forceMandatoryEvidence(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPairPublicationEvidenceKind kind,
      SqliteProtectedBookPairPublicationRecord.RecoveryRecordFileForcer recordFileForcer,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer)
      throws IOException {
    if (!kind.isMandatoryRecoveryEvidence()) {
      return;
    }
    for (Path evidencePath : record.evidencePaths(kind)) {
      recordFileForcer.force(evidencePath);
      SqlitePairPublicationEvidenceStatus.requireComplete(record, kind);
      directoryForcer.force(
          SqlitePairPublicationEvidenceStatus.durabilityStep(kind),
          SqlitePairPublicationRecordIntegrity.parentOf(evidencePath));
      SqlitePairPublicationEvidenceStatus.requireComplete(record, kind);
    }
  }

  private static void requireMandatoryRecoveryEvidence(
      SqliteProtectedBookPairPublicationRecord record) throws IOException {
    for (SqliteProtectedBookPairPublicationEvidenceKind kind :
        SqliteProtectedBookPairPublicationEvidenceKind.values()) {
      if (kind.isMandatoryRecoveryEvidence()) {
        SqlitePairPublicationEvidenceStatus.requireComplete(record, kind);
      }
    }
  }

  private static void complete(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPairPublicationEvidenceKind kind,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer)
      throws IOException {
    for (Path evidencePath : record.evidencePaths(kind)) {
      if (Files.exists(evidencePath, LinkOption.NOFOLLOW_LINKS)) {
        SqlitePairPublicationEvidenceStatus.requireExact(record, kind, evidencePath);
      } else {
        copyMissing(record, kind, evidencePath, Files::createLink);
      }
      forceCopy(record, kind, evidencePath, directoryForcer);
    }
  }

  static void copyMissing(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPairPublicationEvidenceKind kind,
      Path evidencePath,
      SqliteProtectedBookPairPublicationRecord.EvidenceLinkCreator evidenceLinkCreator)
      throws IOException {
    Path temporaryPath =
        SqliteProtectedBookPairPublicationEvidencePaths.temporaryPath(
            evidencePath, UUID.randomUUID());
    try {
      writeNew(temporaryPath, record, kind);
      Objects.requireNonNull(evidenceLinkCreator, "evidenceLinkCreator")
          .create(evidencePath, temporaryPath);
    } catch (java.nio.file.FileAlreadyExistsException collision) {
      SqlitePairPublicationEvidenceStatus.requireExact(record, kind, evidencePath);
    }
  }

  private static void requireHeldEvidenceDirectoryLeases(
      SqliteProtectedBookPairPublicationRecord record) throws IOException {
    SqliteProtectedBookPairPublicationRecord checkedRecord =
        Objects.requireNonNull(record, "record");
    if (!SqliteMaintenanceLeaseAuthority.currentThreadOwnsArtifactLease(
            checkedRecord.bookTargetPath)
        || !SqliteMaintenanceLeaseAuthority.currentThreadOwnsArtifactLease(
            checkedRecord.secretTargetPath)) {
      throw new IOException(
          "FinGrind may repair protected-book pair evidence only while its exact final-target leases are held.");
    }
  }
}
