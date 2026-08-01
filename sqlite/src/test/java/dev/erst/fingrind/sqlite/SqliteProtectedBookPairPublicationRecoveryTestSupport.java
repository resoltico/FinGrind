package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationRecoveryRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Shared recovery fixtures for protected-book pair-publication test scenarios. */
abstract class SqliteProtectedBookPairPublicationRecoveryTestSupport
    extends SqliteArtifactPublicationTestSupport {
  protected static SqliteProtectedBookPairPublicationRecovery recovery() {
    return new SqliteProtectedBookPairPublicationRecovery(
        (ignoredBook, ignoredSecret, ignoredBinding) -> true,
        (ignoredStep, ignoredParent) -> {},
        ignoredRecord -> {});
  }

  protected static ProtectedBookPairPublicationRecoveryRequest.Backup backupRequest(Path bookPath) {
    return new ProtectedBookPairPublicationRecoveryRequest.Backup(
        bookPath.resolveSibling("source.sqlite"), new UUID(0L, 1L));
  }

  protected static ProtectedBookPairPublicationRecoveryRequest.Restore restoreRequest(
      Path bookPath) {
    return new ProtectedBookPairPublicationRecoveryRequest.Restore(
        bookPath.resolveSibling("source-backup.sqlite"),
        bookPath.resolveSibling("source-backup.key"),
        backupBinding(bookPath).acknowledgement());
  }

  protected static SqlitePairPublicationMemberReconciler reconciler(
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer) {
    return reconciler(directoryForcer, SqliteProtectedBookPublicationSupport::moveReplacing);
  }

  protected static SqlitePairPublicationMemberReconciler reconciler(
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer,
      SqliteProtectedBookPairPublicationRecord.RecoveryRecordFileForcer recoveryRecordFileForcer) {
    return new SqlitePairPublicationMemberReconciler(
        directoryForcer,
        recoveryRecordFileForcer,
        SqliteProtectedBookPublicationSupport::moveReplacing);
  }

  protected static SqlitePairPublicationMemberReconciler reconciler(
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer,
      SqliteProtectedBookPublicationSupport.AtomicBookMover bookMover) {
    return new SqlitePairPublicationMemberReconciler(
        directoryForcer, ignoredRecord -> {}, bookMover);
  }

  protected static SqlitePairPublicationRecoveryWorkflow recoveryWorkflow(
      boolean verifiesRecoveredPair) {
    return recoveryWorkflow(verifiesRecoveredPair, (ignoredStep, ignoredParent) -> {});
  }

  protected static SqlitePairPublicationRecoveryWorkflow recoveryWorkflow(
      boolean verifiesRecoveredPair,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer) {
    return recoveryWorkflow(
        (ignoredBook, ignoredSecret, ignoredBinding) -> verifiesRecoveredPair, directoryForcer);
  }

  protected static SqlitePairPublicationRecoveryWorkflow recoveryWorkflow(
      SqliteProtectedBookPairPublicationPreparation.RecoveredPairVerifier recoveredPairVerifier) {
    return recoveryWorkflow(recoveredPairVerifier, (ignoredStep, ignoredParent) -> {});
  }

  protected static SqlitePairPublicationRecoveryWorkflow recoveryWorkflow(
      SqliteProtectedBookPairPublicationPreparation.RecoveredPairVerifier recoveredPairVerifier,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer) {
    return new SqlitePairPublicationRecoveryWorkflow(
        recoveredPairVerifier, directoryForcer, ignoredRecord -> {});
  }

  protected static SqlitePublicationCapabilityWitness.Set witnessesFor(
      SqliteProtectedBookPairPublicationRecord record) throws IOException {
    return SqlitePublicationCapabilityWitness.acquirePair(
        record.bookTargetPath,
        SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK,
        record.secretTargetPath,
        Files::createLink,
        SqliteProtectedBookPublicationSupport::moveReplacing);
  }

  protected SqliteProtectedBookPairPublicationRecord retainedRecord(String directoryName)
      throws IOException {
    return pairRecord(directoryName, true);
  }

  protected SqliteProtectedBookPairPublicationRecord retainedCrossParentRecord(String directoryName)
      throws IOException {
    Path bookTarget = absentTarget(directoryName + "/book/book.sqlite");
    Path secretTarget = absentTarget(directoryName + "/secret/book.key");
    Path bookStage = writeArtifact(directoryName + "/book/.book.stage", "retained book stage");
    Path secretStage =
        writeArtifact(directoryName + "/secret/.secret.stage", "retained secret stage");
    SqliteOwnedStagedArtifact.recordExisting(bookTarget, bookStage);
    SqliteOwnedStagedArtifact.recordExisting(secretTarget, secretStage);
    return SqliteProtectedBookPairPublicationRecord.create(
        bookTarget,
        secretTarget,
        bookStage,
        secretStage,
        RestoredBookTargetPolicy.REQUIRE_ABSENT,
        backupBinding(bookTarget.resolveSibling("source.sqlite")),
        (ignoredStep, ignoredParent) -> {});
  }

  protected void reserveEvidence(
      String directoryName,
      boolean separateParents,
      SqliteProtectedBookPairPublicationRecord.EvidenceLinkCreator evidenceLinkCreator)
      throws IOException {
    Path bookTarget =
        absentTarget(
            directoryName + (separateParents ? "/book-parent/book.sqlite" : "/book.sqlite"));
    Path secretTarget =
        absentTarget(directoryName + (separateParents ? "/secret-parent/book.key" : "/book.key"));
    Path bookStage =
        writeArtifact(
            directoryName + (separateParents ? "/book-parent/.book.stage" : "/.book.stage"),
            "reservation book stage");
    Path secretStage =
        writeArtifact(
            directoryName + (separateParents ? "/secret-parent/.secret.stage" : "/.secret.stage"),
            "reservation secret stage");
    SqliteProtectedBookPairPublicationRecord.create(
        bookTarget,
        secretTarget,
        bookStage,
        secretStage,
        RestoredBookTargetPolicy.REQUIRE_ABSENT,
        backupBinding(bookTarget.resolveSibling("source.sqlite")),
        (ignoredStep, ignoredParent) -> {},
        evidenceLinkCreator);
  }

  protected SqliteProtectedBookPairPublicationRecord pairRecord(
      String directoryName, boolean recordStageOwnership) throws IOException {
    Path bookTarget = absentTarget(directoryName + "/book.sqlite");
    Path secretTarget = absentTarget(directoryName + "/book.key");
    Path bookStage = writeArtifact(directoryName + "/.book.stage", "retained book stage");
    Path secretStage = writeArtifact(directoryName + "/.secret.stage", "retained secret stage");
    if (recordStageOwnership) {
      SqliteOwnedStagedArtifact.recordExisting(bookTarget, bookStage);
      SqliteOwnedStagedArtifact.recordExisting(secretTarget, secretStage);
    }
    return SqliteProtectedBookPairPublicationRecord.create(
        bookTarget,
        secretTarget,
        bookStage,
        secretStage,
        RestoredBookTargetPolicy.REQUIRE_ABSENT,
        backupBinding(bookTarget.resolveSibling("source.sqlite")),
        (ignoredStep, ignoredParent) -> {});
  }

  protected SqliteProtectedBookPairPublicationRecord restoreRecord(String directoryName)
      throws IOException {
    Path bookTarget = absentTarget(directoryName + "/book.sqlite");
    Path secretTarget = absentTarget(directoryName + "/book.key");
    Path bookStage = writeArtifact(directoryName + "/.book.stage", "retained restored book stage");
    Path secretStage = writeArtifact(directoryName + "/.secret.stage", "retained restored key");
    SqliteOwnedStagedArtifact.recordExisting(bookTarget, bookStage);
    SqliteOwnedStagedArtifact.recordExisting(secretTarget, secretStage);
    return SqliteProtectedBookPairPublicationRecord.create(
        bookTarget,
        secretTarget,
        bookStage,
        secretStage,
        RestoredBookTargetPolicy.REQUIRE_ABSENT,
        restoreBinding(
            bookTarget.resolveSibling("source-backup.sqlite"),
            secretTarget.resolveSibling("source-backup.key")),
        (ignoredStep, ignoredParent) -> {});
  }

  protected static SqliteProtectedBookPairPublicationRecord withChangedBookDigest(
      SqliteProtectedBookPairPublicationRecord original) {
    byte[] changedBookDigest = original.bookDigest.clone();
    changedBookDigest[0] ^= 1;
    return new SqliteProtectedBookPairPublicationRecord(
        new SqliteProtectedBookPairPublicationRecord.Components(
            original.pairId,
            new SqliteProtectedBookPairPublicationRecord.PairPaths(
                original.bookTargetPath,
                original.secretTargetPath,
                original.bookStagePath,
                original.secretStagePath),
            new SqliteProtectedBookPairPublicationRecord.PairDigests(
                changedBookDigest, original.secretDigest, original.replaceTargetDigest),
            original.bookTargetPolicy,
            original.binding));
  }

  protected SqliteProtectedBookPairPublicationRecord rekeyRecord(String directoryName)
      throws IOException {
    Path bookTarget = writeArtifact(directoryName + "/book.sqlite", "selected source book");
    Path secretTarget = absentTarget(directoryName + "/book.key");
    Path bookStage = writeArtifact(directoryName + "/.book.stage", "rekeyed book");
    Path secretStage = writeArtifact(directoryName + "/.secret.stage", "generated key");
    SqliteOwnedStagedArtifact.recordExisting(bookTarget, bookStage);
    SqliteOwnedStagedArtifact.recordExisting(secretTarget, secretStage);
    return SqliteProtectedBookPairPublicationRecord.create(
        bookTarget,
        secretTarget,
        bookStage,
        secretStage,
        RestoredBookTargetPolicy.REPLACE_SELECTED,
        rekeyBinding(bookTarget, bookTarget.resolveSibling("source.book-key")),
        (ignoredStep, ignoredParent) -> {});
  }

  protected static void deleteEvidence(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPairPublicationEvidenceKind evidenceKind)
      throws IOException {
    for (Path evidencePath : record.evidencePaths(evidenceKind)) {
      Files.delete(evidencePath);
    }
  }

  protected static boolean throwVerifierFault(AssertionError failure) {
    throw failure;
  }

  protected Path absentTarget(String relativePath) throws IOException {
    Path target = tempDirectory.resolve(relativePath);
    Path parent = target.getParent();
    if (parent == null) {
      throw new AssertionError("Fixture target requires one parent.");
    }
    Files.createDirectories(parent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parent);
    return target;
  }
}
