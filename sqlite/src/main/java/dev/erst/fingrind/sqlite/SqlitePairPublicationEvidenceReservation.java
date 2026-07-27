package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationBinding;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/** Reserves the immutable claim, intent, and recovery evidence that precede publication. */
final class SqlitePairPublicationEvidenceReservation {
  private static final int RESERVATION_ATTEMPTS = 8;

  private SqlitePairPublicationEvidenceReservation() {}

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
    ReservationInputs inputs =
        ReservationInputs.create(
            bookTargetPath,
            secretTargetPath,
            bookStagePath,
            secretStagePath,
            bookTargetPolicy,
            binding);
    for (int attempt = 0; attempt < RESERVATION_ATTEMPTS; attempt++) {
      @org.jspecify.annotations.Nullable SqliteProtectedBookPairPublicationRecord reserved =
          reserveAttempt(inputs, directoryForcer, evidenceLinkCreator);
      if (reserved != null) {
        return reserved;
      }
    }
    throw new IOException(
        "Unable to reserve durable protected-book pair recovery evidence beside "
            + SqliteMachinePaths.absoluteValue(inputs.bookTargetPath)
            + ".");
  }

  private static @org.jspecify.annotations.Nullable SqliteProtectedBookPairPublicationRecord
      reserveAttempt(
          ReservationInputs inputs,
          SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer,
          SqliteProtectedBookPairPublicationRecord.EvidenceLinkCreator evidenceLinkCreator)
          throws IOException {
    SqliteProtectedBookPairPublicationRecord record = inputs.newRecord();
    if (!promote(
        record,
        SqliteProtectedBookPairPublicationEvidenceKind.CLAIM,
        directoryForcer,
        evidenceLinkCreator)) {
      return null;
    }
    requirePromoted(
        promote(
            record,
            SqliteProtectedBookPairPublicationEvidenceKind.INTENT,
            directoryForcer,
            evidenceLinkCreator),
        "The protected-book pair claim was durable but recovery intent collided.");
    requirePromoted(
        promote(
            record,
            SqliteProtectedBookPairPublicationEvidenceKind.RECOVERY,
            directoryForcer,
            evidenceLinkCreator),
        "The protected-book pair intent was durable but recovery evidence collided.");
    return record;
  }

  private static boolean promote(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPairPublicationEvidenceKind kind,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer,
      SqliteProtectedBookPairPublicationRecord.EvidenceLinkCreator evidenceLinkCreator)
      throws IOException {
    boolean anyCopyPromoted = false;
    try {
      for (Path evidencePath : record.evidencePaths(kind)) {
        promoteCopy(record, kind, evidencePath, directoryForcer, evidenceLinkCreator);
        anyCopyPromoted = true;
      }
    } catch (java.nio.file.FileAlreadyExistsException collision) {
      if (!anyCopyPromoted) {
        return false;
      }
      throw new SqliteProtectedBookPairPublicationRecord
          .RecoveryRecordDurabilityUnconfirmedException(collision);
    } catch (IOException failure) {
      if (anyCopyPromoted) {
        throw new SqliteProtectedBookPairPublicationRecord
            .RecoveryRecordDurabilityUnconfirmedException(failure);
      }
      throw failure;
    }
    return true;
  }

  private static void promoteCopy(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPairPublicationEvidenceKind kind,
      Path evidencePath,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer,
      SqliteProtectedBookPairPublicationRecord.EvidenceLinkCreator evidenceLinkCreator)
      throws IOException {
    Path temporaryPath =
        SqliteProtectedBookPairPublicationEvidencePaths.temporaryPath(
            evidencePath, UUID.randomUUID());
    SqlitePairPublicationEvidenceRecovery.writeNew(temporaryPath, record, kind);
    Objects.requireNonNull(evidenceLinkCreator, "evidenceLinkCreator")
        .create(evidencePath, temporaryPath);
    SqlitePairPublicationEvidenceRecovery.forceCopy(record, kind, evidencePath, directoryForcer);
  }

  private static void requirePromoted(boolean promoted, String message) {
    if (!promoted) {
      throw new SqliteProtectedBookPairPublicationRecord
          .RecoveryRecordDurabilityUnconfirmedException(new IOException(message));
    }
  }

  /** Normalized paths and content identities reused across collision-safe reservation attempts. */
  private static final class ReservationInputs {
    private final Path bookTargetPath;
    private final Path secretTargetPath;
    private final Path bookStagePath;
    private final Path secretStagePath;
    private final byte[] bookDigest;
    private final byte[] secretDigest;
    private final byte @org.jspecify.annotations.Nullable [] replaceTargetDigest;
    private final RestoredBookTargetPolicy bookTargetPolicy;
    private final ProtectedBookPairPublicationBinding binding;

    private ReservationInputs(
        Path bookTargetPath,
        Path secretTargetPath,
        Path bookStagePath,
        Path secretStagePath,
        byte[] bookDigest,
        byte[] secretDigest,
        byte @org.jspecify.annotations.Nullable [] replaceTargetDigest,
        RestoredBookTargetPolicy bookTargetPolicy,
        ProtectedBookPairPublicationBinding binding) {
      this.bookTargetPath = bookTargetPath;
      this.secretTargetPath = secretTargetPath;
      this.bookStagePath = bookStagePath;
      this.secretStagePath = secretStagePath;
      this.bookDigest = bookDigest;
      this.secretDigest = secretDigest;
      this.replaceTargetDigest = replaceTargetDigest;
      this.bookTargetPolicy = bookTargetPolicy;
      this.binding = binding;
    }

    private static ReservationInputs create(
        Path bookTargetPath,
        Path secretTargetPath,
        Path bookStagePath,
        Path secretStagePath,
        RestoredBookTargetPolicy bookTargetPolicy,
        ProtectedBookPairPublicationBinding binding)
        throws IOException {
      Path checkedBookTarget =
          SqlitePairPublicationRecordIntegrity.normalized(bookTargetPath, "bookTargetPath");
      Path checkedSecretTarget =
          SqlitePairPublicationRecordIntegrity.normalized(secretTargetPath, "secretTargetPath");
      Path checkedBookStage =
          SqlitePairPublicationRecordIntegrity.normalized(bookStagePath, "bookStagePath");
      Path checkedSecretStage =
          SqlitePairPublicationRecordIntegrity.normalized(secretStagePath, "secretStagePath");
      RestoredBookTargetPolicy checkedPolicy =
          Objects.requireNonNull(bookTargetPolicy, "bookTargetPolicy");
      byte @org.jspecify.annotations.Nullable [] replaceTargetDigest =
          checkedPolicy == RestoredBookTargetPolicy.REPLACE_SELECTED
              ? SqlitePairPublicationRecordIntegrity.digestRegularFile(
                  checkedBookTarget, "selected rekey book target")
              : null;
      return new ReservationInputs(
          checkedBookTarget,
          checkedSecretTarget,
          checkedBookStage,
          checkedSecretStage,
          SqlitePairPublicationRecordIntegrity.digestRegularFile(
              checkedBookStage, "staged protected-book artifact"),
          SqlitePairPublicationRecordIntegrity.digestRegularFile(
              checkedSecretStage, "staged generated-secret artifact"),
          replaceTargetDigest,
          checkedPolicy,
          Objects.requireNonNull(binding, "binding"));
    }

    private SqliteProtectedBookPairPublicationRecord newRecord() {
      return new SqliteProtectedBookPairPublicationRecord(
          new SqliteProtectedBookPairPublicationRecord.Components(
              UUID.randomUUID(),
              new SqliteProtectedBookPairPublicationRecord.PairPaths(
                  bookTargetPath, secretTargetPath, bookStagePath, secretStagePath),
              new SqliteProtectedBookPairPublicationRecord.PairDigests(
                  bookDigest, secretDigest, replaceTargetDigest),
              bookTargetPolicy,
              binding));
    }
  }
}
