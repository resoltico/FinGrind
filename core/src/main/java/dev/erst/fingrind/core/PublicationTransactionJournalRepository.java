package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Owns one private transaction-journal store and its durable per-user authentication key. */
final class PublicationTransactionJournalRepository {
  private static final String JOURNAL_FILE_PREFIX = "txn-";
  private static final String JOURNAL_FILE_SUFFIX = ".json";
  private static final int MAXIMUM_JOURNAL_BYTES = 1_048_576;

  private final Path storeRoot;
  private final byte[] ownerKey;
  private final String ownerKeyFingerprint;
  private final PublicationTransactionDirectoryDurability directoryDurability;

  private PublicationTransactionJournalRepository(
      Path storeRoot,
      byte[] ownerKey,
      PublicationTransactionDirectoryDurability directoryDurability) {
    this.storeRoot = Objects.requireNonNull(storeRoot, "storeRoot");
    this.ownerKey = Objects.requireNonNull(ownerKey, "ownerKey").clone();
    this.ownerKeyFingerprint = CryptographicPrimitives.sha256Hex(this.ownerKey);
    this.directoryDurability = Objects.requireNonNull(directoryDurability, "directoryDurability");
  }

  static PublicationTransactionJournalRepository openCanonical()
      throws PrivateOutputDirectory.Violation, IOException {
    return open(PublicationTransactionStore.openCanonicalStore());
  }

  static PublicationTransactionJournalRepository open(Path plannedStoreRoot)
      throws PrivateOutputDirectory.Violation, IOException {
    return open(plannedStoreRoot, PublicationTransactionDirectoryDurability.production());
  }

  static PublicationTransactionJournalRepository open(
      Path plannedStoreRoot, PublicationTransactionDirectoryDurability directoryDurability)
      throws PrivateOutputDirectory.Violation, IOException {
    PublicationTransactionDirectoryDurability checkedDirectoryDurability =
        Objects.requireNonNull(directoryDurability, "directoryDurability");
    Path canonicalStoreRoot =
        PublicationTransactionStore.open(
            plannedStoreRoot, Path::toRealPath, checkedDirectoryDurability);
    return new PublicationTransactionJournalRepository(
        canonicalStoreRoot,
        PublicationTransactionOwnerKey.loadOrCreate(canonicalStoreRoot, checkedDirectoryDurability),
        checkedDirectoryDurability);
  }

  PublicationTransactionJournal create(PublicationTransactionJournal journal) throws IOException {
    PublicationTransactionJournal checkedJournal = Objects.requireNonNull(journal, "journal");
    requireOwnerFingerprint(checkedJournal);
    byte[] encodedJournal = PublicationTransactionJournalCodec.encode(checkedJournal, ownerKey);
    Path journalPath = journalPath(checkedJournal.transactionId());
    try (PrivateOutputFile.OpenedFile opened = PrivateOutputFile.createNew(journalPath)) {
      PublicationTransactionJournalFileIO.writeExactlyAndForce(
          opened, encodedJournal, "publication transaction journal");
    }
    directoryDurability.force(storeRoot);
    return checkedJournal;
  }

  PublicationTransactionJournal read(PublicationTransactionId transactionId) throws IOException {
    PublicationTransactionId checkedTransactionId =
        Objects.requireNonNull(transactionId, "transactionId");
    byte[] encodedJournal;
    try (PrivateOutputFile.OpenedFile opened =
        PrivateOutputFile.openExisting(
            journalPath(checkedTransactionId), PrivateOutputFile.Access.READ_ONLY)) {
      encodedJournal =
          PublicationTransactionJournalFileIO.readAtMost(
              opened, MAXIMUM_JOURNAL_BYTES, "publication transaction journal");
    }
    return decodeOwnedJournal(encodedJournal, checkedTransactionId);
  }

  PublicationTransactionJournal transition(
      PublicationTransactionId transactionId, PublicationTransactionTransition nextTransition)
      throws IOException {
    return transition(transactionId, nextTransition, null);
  }

  PublicationTransactionJournal transition(
      PublicationTransactionId transactionId,
      PublicationTransactionTransition nextTransition,
      @Nullable PublicationTransactionJournalMembers updatedMembers)
      throws IOException {
    PublicationTransactionId checkedTransactionId =
        Objects.requireNonNull(transactionId, "transactionId");
    PublicationTransactionTransition checkedTransition =
        Objects.requireNonNull(nextTransition, "nextTransition");
    try (PrivateOutputFile.OpenedFile opened =
        PrivateOutputFile.openExisting(
            journalPath(checkedTransactionId), PrivateOutputFile.Access.READ_WRITE)) {
      try (PrivateOutputFile.HeldLock ignored =
          PublicationTransactionJournalFileIO.requireExclusiveLock(opened)) {
        PublicationTransactionJournal prior =
            decodeOwnedJournal(
                PublicationTransactionJournalFileIO.readAtMost(
                    opened, MAXIMUM_JOURNAL_BYTES, "publication transaction journal"),
                checkedTransactionId);
        PublicationTransactionJournal updated =
            updatedMembers == null
                ? prior.transition(checkedTransition)
                : prior.transition(checkedTransition, updatedMembers.members());
        return writeUpdatedJournal(opened, updated);
      }
    }
  }

  PublicationTransactionJournal updateMembers(
      PublicationTransactionId transactionId, PublicationTransactionJournalMembers updatedMembers)
      throws IOException {
    PublicationTransactionId checkedTransactionId =
        Objects.requireNonNull(transactionId, "transactionId");
    PublicationTransactionJournalMembers checkedMembers =
        Objects.requireNonNull(updatedMembers, "updatedMembers");
    try (PrivateOutputFile.OpenedFile opened =
        PrivateOutputFile.openExisting(
            journalPath(checkedTransactionId), PrivateOutputFile.Access.READ_WRITE)) {
      try (PrivateOutputFile.HeldLock ignored =
          PublicationTransactionJournalFileIO.requireExclusiveLock(opened)) {
        PublicationTransactionJournal prior =
            decodeOwnedJournal(
                PublicationTransactionJournalFileIO.readAtMost(
                    opened, MAXIMUM_JOURNAL_BYTES, "publication transaction journal"),
                checkedTransactionId);
        return writeUpdatedJournal(opened, prior.updateMembers(checkedMembers.members()));
      }
    }
  }

  String ownerKeyFingerprint() {
    return ownerKeyFingerprint;
  }

  Path storeRoot() {
    return storeRoot;
  }

  Path journalPath(PublicationTransactionId transactionId) {
    return storeRoot.resolve(JOURNAL_FILE_PREFIX + transactionId.value() + JOURNAL_FILE_SUFFIX);
  }

  private PublicationTransactionJournal writeUpdatedJournal(
      PrivateOutputFile.OpenedFile opened, PublicationTransactionJournal updated)
      throws IOException {
    opened.truncate(0L);
    opened.position(0L);
    PublicationTransactionJournalFileIO.writeExactlyAndForce(
        opened,
        PublicationTransactionJournalCodec.encode(updated, ownerKey),
        "publication transaction journal");
    return updated;
  }

  private void requireOwnerFingerprint(PublicationTransactionJournal journal)
      throws PublicationTransactionJournalViolation {
    if (!ownerKeyFingerprint.equals(journal.ownerKeyFingerprint())) {
      throw integrityViolation("Publication transaction journal belongs to another owner key.");
    }
  }

  private static PublicationTransactionJournalViolation integrityViolation(String message) {
    return new PublicationTransactionJournalViolation(
        PublicationTransactionJournalViolation.Kind.INTEGRITY, message);
  }

  private PublicationTransactionJournal decodeOwnedJournal(
      byte[] encodedJournal, PublicationTransactionId expectedTransactionId)
      throws PublicationTransactionJournalViolation {
    PublicationTransactionJournal journal =
        PublicationTransactionJournalCodec.decode(encodedJournal, ownerKey);
    if (!journal.transactionId().equals(expectedTransactionId)) {
      throw integrityViolation(
          "Publication transaction journal does not match its lookup identifier.");
    }
    requireOwnerFingerprint(journal);
    return journal;
  }
}
