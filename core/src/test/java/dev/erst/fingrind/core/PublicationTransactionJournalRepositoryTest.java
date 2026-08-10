package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies no-replace durable transaction-journal ownership and authenticated admission. */
class PublicationTransactionJournalRepositoryTest {
  private static final Instant CREATED_AT = Instant.parse("2026-08-10T12:34:56Z");

  @Test
  void createsOnePrivateOwnerKeyAndOneNoReplaceAuthenticatedJournal(
      @TempDir Path temporaryDirectory) throws Exception {
    PublicationTransactionJournalRepository repository = repository(temporaryDirectory);
    PublicationTransactionJournal journal = journal(repository, "0123456789abcdef0123456789abcdef");

    assertEquals(journal, repository.create(journal));
    assertEquals(journal, repository.read(journal.transactionId()));
    assertEquals(
        journal,
        repository(temporaryDirectory)
            .read(new PublicationTransactionId(journal.transactionId().value())));
    assertEquals(
        32L, Files.size(PublicationTransactionOwnerKey.path(storeRoot(temporaryDirectory))));
    PrivateOutputDirectory.requireExistingOwnerOnly(storeRoot(temporaryDirectory));
    PrivateOutputFile.requireExistingOwnerOnly(
        repository.journalPath(journal.transactionId()), PrivateOutputFile.Access.READ_ONLY);
  }

  @Test
  void refusesToReplaceOneTransactionJournalOrReadOneUnknownIdentifier(
      @TempDir Path temporaryDirectory) throws Exception {
    PublicationTransactionJournalRepository repository = repository(temporaryDirectory);
    PublicationTransactionJournal journal = journal(repository, "0123456789abcdef0123456789abcdef");
    repository.create(journal);

    assertThrows(IOException.class, () -> repository.create(journal));
    assertThrows(
        IOException.class,
        () -> repository.read(new PublicationTransactionId("fedcba9876543210fedcba9876543210")));
  }

  @Test
  void rejectsJournalCreationForAnotherOwnerKey(@TempDir Path temporaryDirectory) throws Exception {
    PublicationTransactionJournalRepository repository = repository(temporaryDirectory);
    PublicationTransactionJournal foreignJournal =
        PublicationTransactionJournal.prepared(
            new PublicationTransactionId("0123456789abcdef0123456789abcdef"),
            "fedcba9876543210fedcba9876543210",
            "a".repeat(64),
            CREATED_AT,
            plannedMember());

    PublicationTransactionJournalViolation violation =
        assertThrows(
            PublicationTransactionJournalViolation.class, () -> repository.create(foreignJournal));

    assertEquals(PublicationTransactionJournalViolation.Kind.INTEGRITY, violation.kind());
  }

  @Test
  void rejectsCorruptOwnedJournalBytesWithoutAdoptingTheResidue(@TempDir Path temporaryDirectory)
      throws Exception {
    PublicationTransactionJournalRepository repository = repository(temporaryDirectory);
    PublicationTransactionJournal journal = journal(repository, "0123456789abcdef0123456789abcdef");
    repository.create(journal);
    Files.writeString(repository.journalPath(journal.transactionId()), "not a journal");

    PublicationTransactionJournalViolation violation =
        assertThrows(
            PublicationTransactionJournalViolation.class,
            () -> repository.read(journal.transactionId()));

    assertEquals(PublicationTransactionJournalViolation.Kind.MALFORMED, violation.kind());
  }

  @Test
  void rejectsAnOwnerKeyWhoseExactPrivateBytesAreMalformed(@TempDir Path temporaryDirectory)
      throws Exception {
    Path storeRoot = PublicationTransactionStore.open(storeRoot(temporaryDirectory));
    writePrivateFile(PublicationTransactionOwnerKey.path(storeRoot), new byte[] {1});

    assertThrows(IOException.class, () -> PublicationTransactionJournalRepository.open(storeRoot));
  }

  @Test
  void rejectsAValidJournalCopiedUnderAnotherTransactionIdentifier(@TempDir Path temporaryDirectory)
      throws Exception {
    PublicationTransactionJournalRepository repository = repository(temporaryDirectory);
    PublicationTransactionJournal journal = journal(repository, "0123456789abcdef0123456789abcdef");
    PublicationTransactionId copiedIdentifier =
        new PublicationTransactionId("fedcba9876543210fedcba9876543210");
    repository.create(journal);
    writePrivateFile(
        repository.journalPath(copiedIdentifier),
        Files.readAllBytes(repository.journalPath(journal.transactionId())));

    PublicationTransactionJournalViolation violation =
        assertThrows(
            PublicationTransactionJournalViolation.class, () -> repository.read(copiedIdentifier));

    assertEquals(PublicationTransactionJournalViolation.Kind.INTEGRITY, violation.kind());
  }

  @Test
  void opensTheCanonicalRepositoryUnderTheTestPrivateHome() throws Exception {
    PublicationTransactionJournalRepository repository =
        PublicationTransactionJournalRepository.openCanonical();

    assertTrue(repository.ownerKeyFingerprint().matches("[0-9a-f]{64}"));
  }

  @Test
  void forceConfirmsEachLockedTransitionBeforeItBecomesRecoveryAuthority(
      @TempDir Path temporaryDirectory) throws Exception {
    PublicationTransactionJournalRepository repository = repository(temporaryDirectory);
    PublicationTransactionJournal journal = journal(repository, "0123456789abcdef0123456789abcdef");
    repository.create(journal);

    PublicationTransactionJournal staged =
        repository.transition(
            journal.transactionId(),
            new PublicationTransactionTransition(
                PublicationTransactionState.STAGED,
                CREATED_AT.plusSeconds(1L),
                new PublicationTransactionOutcome(
                    PublicationCommitOutcome.NONE_COMMITTED, PublicationCleanupOutcome.COMPLETE)));

    assertEquals(PublicationTransactionState.STAGED, staged.state());
    assertEquals(staged, repository.read(journal.transactionId()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            repository.transition(
                journal.transactionId(),
                new PublicationTransactionTransition(
                    PublicationTransactionState.COMPLETE,
                    CREATED_AT.plusSeconds(2L),
                    new PublicationTransactionOutcome(
                        PublicationCommitOutcome.ALL_COMMITTED,
                        PublicationCleanupOutcome.COMPLETE))));
    assertEquals(
        PublicationTransactionState.STAGED, repository.read(journal.transactionId()).state());
  }

  @Test
  void rejectsIncompleteChannelProgressAndOversizedPrivateJournalFiles() {
    assertThrows(
        IOException.class,
        () ->
            PublicationTransactionJournalFileIO.writeExactlyAndForce(
                new FakeOpenedFile(new byte[0], false, true, false, null),
                new byte[] {1},
                "publication transaction journal"));
    assertThrows(
        IOException.class,
        () ->
            PublicationTransactionJournalFileIO.readAtMost(
                new FakeOpenedFile(new byte[] {1, 2}, false, false, false, null),
                1,
                "publication transaction journal"));
    assertThrows(
        IOException.class,
        () ->
            PublicationTransactionJournalFileIO.readAtMost(
                new FakeOpenedFile(new byte[] {1}, false, false, true, null),
                1,
                "publication transaction journal"));
  }

  @Test
  void propagatesOwnerKeyCloseFailureAfterItReadsTheExactKey() {
    assertThrows(
        IOException.class,
        () ->
            PublicationTransactionOwnerKey.load(
                new FakeOpenedFile(
                    new byte[32], false, false, false, new IOException("close failed"))));
    assertThrows(
        IOException.class,
        () ->
            PublicationTransactionOwnerKey.load(
                new FakeOpenedFile(
                    new byte[1], false, false, false, new IOException("close failed"))));
  }

  @Test
  void refusesToProceedWithoutOneExclusiveJournalLease() {
    assertThrows(
        IOException.class,
        () ->
            PublicationTransactionJournalFileIO.requireExclusiveLock(
                new FakeOpenedFile(new byte[0], false, false, false, null, true)));
    assertThrows(
        IOException.class,
        () ->
            PublicationTransactionJournalFileIO.requireExclusiveLock(
                new FakeOpenedFile(new byte[0], false, false, false, null, false, true)));
  }

  private static PublicationTransactionJournalRepository repository(Path temporaryDirectory)
      throws PrivateOutputDirectory.Violation, IOException {
    return PublicationTransactionJournalRepository.open(storeRoot(temporaryDirectory));
  }

  private static Path storeRoot(Path temporaryDirectory) {
    return temporaryDirectory.resolve("publication-state");
  }

  private static PublicationTransactionJournal journal(
      PublicationTransactionJournalRepository repository, String transactionId) {
    return PublicationTransactionJournal.prepared(
        new PublicationTransactionId(transactionId),
        "fedcba9876543210fedcba9876543210",
        repository.ownerKeyFingerprint(),
        CREATED_AT,
        plannedMember());
  }

  private static java.util.List<PublicationTransactionMember> plannedMember() {
    return java.util.List.of(
        new PublicationTransactionMember(
            "protected-book",
            PublicationTransactionMemberRole.PROTECTED_BOOK,
            Path.of("reports", "book.fgb"),
            "directory-identity",
            PublicationMode.NO_REPLACE_LINK,
            PublicationTransactionMemberProgress.PLANNED,
            Optional.empty(),
            Optional.empty()));
  }

  private static void writePrivateFile(Path path, byte[] bytes) throws IOException {
    try (PrivateOutputFile.OpenedFile opened = PrivateOutputFile.createNew(path)) {
      ByteBuffer pending = ByteBuffer.wrap(bytes);
      while (pending.hasRemaining()) {
        if (opened.write(pending) <= 0) {
          throw new IOException("The test fixture could not write its private file.");
        }
      }
      opened.force();
    }
  }

  /** Minimal exact channel for proving fail-closed journal-I/O behavior. */
  private static final class FakeOpenedFile implements PrivateOutputFile.OpenedFile {
    private final byte[] bytes;
    private final boolean created;
    private final boolean writeWithoutProgress;
    private final boolean readWithoutProgress;
    private final @Nullable IOException closeFailure;
    private final boolean lockUnavailable;
    private final boolean overlappingLock;
    private int position;

    FakeOpenedFile(
        byte[] bytes,
        boolean created,
        boolean writeWithoutProgress,
        boolean readWithoutProgress,
        @Nullable IOException closeFailure) {
      this(bytes, created, writeWithoutProgress, readWithoutProgress, closeFailure, false);
    }

    FakeOpenedFile(
        byte[] bytes,
        boolean created,
        boolean writeWithoutProgress,
        boolean readWithoutProgress,
        @Nullable IOException closeFailure,
        boolean lockUnavailable) {
      this(
          bytes,
          created,
          writeWithoutProgress,
          readWithoutProgress,
          closeFailure,
          lockUnavailable,
          false);
    }

    FakeOpenedFile(
        byte[] bytes,
        boolean created,
        boolean writeWithoutProgress,
        boolean readWithoutProgress,
        @Nullable IOException closeFailure,
        boolean lockUnavailable,
        boolean overlappingLock) {
      this.bytes = bytes.clone();
      this.created = created;
      this.writeWithoutProgress = writeWithoutProgress;
      this.readWithoutProgress = readWithoutProgress;
      this.closeFailure = closeFailure;
      this.lockUnavailable = lockUnavailable;
      this.overlappingLock = overlappingLock;
    }

    @Override
    public boolean created() {
      return created;
    }

    @Override
    public int read(ByteBuffer destination) {
      if (readWithoutProgress) {
        return 0;
      }
      int readable = Math.min(destination.remaining(), bytes.length - position);
      destination.put(bytes, position, readable);
      position += readable;
      return readable;
    }

    @Override
    public int write(ByteBuffer source) {
      if (writeWithoutProgress) {
        return 0;
      }
      int written = source.remaining();
      source.position(source.limit());
      return written;
    }

    @Override
    public boolean isOpen() {
      return true;
    }

    @Override
    public long size() {
      return bytes.length;
    }

    @Override
    public void truncate(long ignored) {}

    @Override
    public void position(long position) {
      this.position = Math.toIntExact(position);
    }

    @Override
    public void force() {}

    @Override
    public PrivateOutputFile.HeldLock tryExclusiveLock(long position, long size) {
      if (overlappingLock) {
        throw new OverlappingFileLockException();
      }
      if (lockUnavailable) {
        return nullOf();
      }
      return () -> {};
    }

    @Override
    public String physicalObjectIdentity() {
      return "fake";
    }

    @Override
    public void close() throws IOException {
      if (closeFailure != null) {
        throw closeFailure;
      }
    }
  }
}
