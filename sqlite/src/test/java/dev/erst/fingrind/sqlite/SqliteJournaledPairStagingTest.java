package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import dev.erst.fingrind.executor.spi.StagedPairPublicationCommitOutcome;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Exercises both staged workflow adapters against the sole authoritative journal transaction. */
class SqliteJournaledPairStagingTest extends SqliteArtifactPublicationTestSupport {
  @Test
  void backupStagesVerifiesSealsAndPublishesTheSameJournaledPairExactlyOnce() throws Exception {
    SourceBook source = initializedSourceBook("journaled-backup-source");
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path targetBook = tempDirectory.resolve("journaled-backup-target").resolve("backup.sqlite");
    Path targetSecret = tempDirectory.resolve("journaled-backup-target").resolve("backup.key");
    preparePrivateParent(targetBook);

    try (ProtectedBookMaintenanceStore.VerifiedBook verified =
            verifiedBook(store, source.access());
        ProtectedBookMaintenanceStore.PreparedPairPublication prepared =
            prepareBackupPair(store, targetBook, targetSecret);
        StagedBackupPair staged = acceptedValue(stageBackupPairForFixture(verified, prepared))) {
      assertInstanceOf(
          ProtectedBookMaintenanceStore.VerifiedBook.class,
          acceptedValue(staged.verifyInitializedBackup()));
      assertThrows(IllegalStateException.class, staged::commit);
      sealBackupForPublication(staged);
      StagedPairPublicationCommitOutcome first = staged.commit();
      assertInstanceOf(StagedPairPublicationCommitOutcome.Published.class, first);
      assertSame(first, staged.commit());
      assertInstanceOf(
          dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublication.class,
          fixturePreparedPublication(prepared).journaledPair().publish());
    }

    assertTrue(Files.isRegularFile(targetBook));
    assertTrue(Files.isRegularFile(targetSecret));
  }

  @Test
  void backupRejectsMutableSealInputAndClosesWithoutDeletingTheJournalStage() throws Exception {
    SourceBook source = initializedSourceBook("journaled-backup-rejection-source");
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path targetBook = tempDirectory.resolve("journaled-backup-rejection").resolve("backup.sqlite");
    Path targetSecret = targetBook.resolveSibling("backup.key");
    preparePrivateParent(targetBook);

    try (ProtectedBookMaintenanceStore.VerifiedBook verified =
            verifiedBook(store, source.access());
        ProtectedBookMaintenanceStore.PreparedPairPublication prepared =
            prepareBackupPair(store, targetBook, targetSecret);
        StagedBackupPair staged = acceptedValue(stageBackupPairForFixture(verified, prepared))) {
      assertThrows(IllegalArgumentException.class, () -> staged.sealArtifact(new byte[0]));
      staged.retainUnpublishedArtifacts();
      assertThrows(IllegalStateException.class, staged::snapshot);
    }
  }

  @Test
  void backupFinalCollisionReturnsTheJournalIncompleteOutcome() throws Exception {
    SourceBook source = initializedSourceBook("journaled-backup-collision-source");
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path targetBook = tempDirectory.resolve("journaled-backup-collision").resolve("backup.sqlite");
    Path targetSecret = targetBook.resolveSibling("backup.key");
    preparePrivateParent(targetBook);

    try (ProtectedBookMaintenanceStore.VerifiedBook verified =
            verifiedBook(store, source.access());
        ProtectedBookMaintenanceStore.PreparedPairPublication prepared =
            prepareBackupPair(store, targetBook, targetSecret);
        StagedBackupPair staged = acceptedValue(stageBackupPairForFixture(verified, prepared))) {
      sealBackupForPublication(staged);
      Files.writeString(targetBook, "concurrent target");
      assertInstanceOf(
          StagedPairPublicationCommitOutcome.PublicationTransactionIncomplete.class,
          staged.commit());
    }
  }

  @Test
  void restoreStagesVerifiesAndPublishesThroughOneJournaledReplaceTransaction() throws Exception {
    SourceBook source = initializedSourceBook("journaled-restore-source");
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path targetBook = tempDirectory.resolve("journaled-restore-target").resolve("restored.sqlite");
    Path targetSecret = tempDirectory.resolve("journaled-restore-target").resolve("restored.key");
    preparePrivateParent(targetBook);

    try (ProtectedBookMaintenanceStore.VerifiedBook verified =
            verifiedBook(store, source.access());
        ProtectedBookMaintenanceStore.PreparedPairPublication prepared =
            prepareRestoredBookPair(
                store,
                targetBook,
                targetSecret,
                ProtectedBookMaintenanceStore.RestoredBookTargetPolicy.REQUIRE_ABSENT);
        StagedRestoredBookPair staged =
            acceptedValue(stageRestoredBookPairForFixture(verified, prepared))) {
      assertInstanceOf(
          ProtectedBookMaintenanceStore.VerifiedBook.class,
          acceptedValue(staged.verifyInitializedRestoredBook()));
      StagedPairPublicationCommitOutcome first = staged.commit();
      assertInstanceOf(StagedPairPublicationCommitOutcome.Published.class, first);
      assertSame(first, staged.commit());
    }

    assertTrue(Files.isRegularFile(targetBook));
    assertTrue(Files.isRegularFile(targetSecret));
  }

  private SourceBook initializedSourceBook(String name) {
    Path book = tempDirectory.resolve(name).resolve("source.sqlite");
    BookAccess access = bookAccess(book);
    initializeBook(access);
    return new SourceBook(book, sourceKeyPath(access), access);
  }

  private void preparePrivateParent(Path target) throws IOException {
    Path parent = java.util.Objects.requireNonNull(target.getParent(), "target parent");
    Files.createDirectories(parent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parent);
  }

  private static Path sourceKeyPath(BookAccess access) {
    return switch (access.passphraseSource()) {
      case BookAccess.PassphraseSource.KeyFile keyFile -> keyFile.bookKeyFilePath();
      case BookAccess.PassphraseSource.StandardInput _ ->
          throw new AssertionError("The journaled staging fixture requires a key file.");
      case BookAccess.PassphraseSource.InteractivePrompt _ ->
          throw new AssertionError("The journaled staging fixture requires a key file.");
    };
  }

  private record SourceBook(Path bookPath, Path keyPath, BookAccess access) {}
}
