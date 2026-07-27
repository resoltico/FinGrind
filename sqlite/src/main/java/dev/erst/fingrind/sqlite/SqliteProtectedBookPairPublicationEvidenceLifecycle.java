package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationBinding;
import java.io.IOException;
import java.nio.file.Path;

/** Public lifecycle boundary for durable protected-book pair evidence. */
final class SqliteProtectedBookPairPublicationEvidenceLifecycle {
  private SqliteProtectedBookPairPublicationEvidenceLifecycle() {}

  static SqliteProtectedBookPairPublicationRecord create(
      Path bookTargetPath,
      Path secretTargetPath,
      Path bookStagePath,
      Path secretStagePath,
      RestoredBookTargetPolicy bookTargetPolicy,
      ProtectedBookPairPublicationBinding binding,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer,
      SqliteProtectedBookPairPublicationRecord.EvidenceLinkCreator evidenceLinkCreator)
      throws IOException {
    return SqlitePairPublicationEvidenceReservation.create(
        bookTargetPath,
        secretTargetPath,
        bookStagePath,
        secretStagePath,
        bookTargetPolicy,
        binding,
        directoryForcer,
        evidenceLinkCreator);
  }

  static void forceForRecoveredPublication(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer,
      SqliteProtectedBookPairPublicationRecord.RecoveryRecordFileForcer recordFileForcer)
      throws IOException {
    SqlitePairPublicationEvidenceRecovery.forceForRecoveredPublication(
        record, directoryForcer, recordFileForcer);
  }

  static void repairIncompleteEvidence(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer)
      throws IOException {
    SqlitePairPublicationEvidenceRecovery.repairIncompleteEvidence(record, directoryForcer);
  }

  static void retainPrepublication(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer)
      throws IOException {
    SqlitePairPublicationEvidenceRecovery.retainPrepublication(record, directoryForcer);
  }

  static void confirmCompletedPublication(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer)
      throws IOException {
    SqlitePairPublicationEvidenceRecovery.confirmCompletedPublication(record, directoryForcer);
  }

  static boolean hasDurablyRetainedPrepublication(SqliteProtectedBookPairPublicationRecord record) {
    return SqlitePairPublicationEvidenceStatus.hasComplete(
        record, SqliteProtectedBookPairPublicationEvidenceKind.RETAINED);
  }

  static boolean hasComplete(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPairPublicationEvidenceKind kind) {
    return SqlitePairPublicationEvidenceStatus.hasComplete(record, kind);
  }

  static boolean hasObserved(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPairPublicationEvidenceKind kind) {
    return SqlitePairPublicationEvidenceStatus.hasObserved(record, kind);
  }
}
