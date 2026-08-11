package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import dev.erst.fingrind.executor.spi.StagedPairPublicationCommitOutcome;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Proves terminal journal failures retain recovery authority rather than reviving a sidecar. */
class SqliteJournaledPairLifecycleFailureTest extends SqliteArtifactPublicationTestSupport {
  @Test
  void preparedPairExposesOnlyJournalOwnedTargetsAndRejectsUseAfterClose() throws Exception {
    Path book = privateTarget("prepared-pair-target", "backup.sqlite");
    Path secret = book.resolveSibling("backup.key");
    SqlitePreparedPairPublication prepared =
        fixturePreparedPublication(prepareBackupPair(maintenanceStore(), book, secret));

    try (prepared) {
      assertEquals(book.toAbsolutePath().normalize(), prepared.bookTargetPath());
      assertEquals(secret.toAbsolutePath().normalize(), prepared.secretTargetPath());
      assertEquals(
          ProtectedBookMaintenanceStore.RestoredBookTargetPolicy.REQUIRE_ABSENT,
          prepared.bookTargetPolicy());
      assertEquals(book.toAbsolutePath().normalize(), prepared.journaledPair().bookTargetPath());
      assertEquals(
          secret.toAbsolutePath().normalize(), prepared.journaledPair().secretTargetPath());
      assertNotNull(prepared.journaledPair().transactionId());
      assertEquals(
          prepared.journaledPair().bookStagePath().getParent(),
          prepared.journaledPair().secretStagePath().getParent());
    }

    prepared.close();
    assertThrows(IllegalStateException.class, prepared::journaledPair);
  }

  @Test
  void missingBackupMemberReturnsTheJournalIncompleteOutcomeWithoutLocalCleanup() throws Exception {
    SourceBook source = initializedSourceBook("backup-incomplete-source");
    Path book = privateTarget("backup-incomplete-target", "backup.sqlite");
    Path secret = book.resolveSibling("backup.key");

    try (ProtectedBookMaintenanceStore.VerifiedBook verified =
            verifiedBook(maintenanceStore(), source.access());
        ProtectedBookMaintenanceStore.PreparedPairPublication prepared =
            prepareBackupPair(maintenanceStore(), book, secret);
        StagedBackupPair staged = acceptedValue(stageBackupPairForFixture(verified, prepared))) {
      sealBackupForPublication(staged);
      Files.delete(fixturePreparedPublication(prepared).journaledPair().secretStagePath());

      assertInstanceOf(
          StagedPairPublicationCommitOutcome.PublicationTransactionIncomplete.class,
          staged.commit());
      assertThrows(IllegalStateException.class, staged::snapshot);
    }
  }

  @Test
  void missingRestoredBookMemberReturnsTheJournalIncompleteOutcomeWithoutLocalCleanup()
      throws Exception {
    SourceBook source = initializedSourceBook("restore-incomplete-source");
    Path book = privateTarget("restore-incomplete-target", "restored.sqlite");
    Path secret = book.resolveSibling("restored.key");

    try (ProtectedBookMaintenanceStore.VerifiedBook verified =
            verifiedBook(maintenanceStore(), source.access());
        ProtectedBookMaintenanceStore.PreparedPairPublication prepared =
            prepareRestoredBookPair(
                maintenanceStore(),
                book,
                secret,
                ProtectedBookMaintenanceStore.RestoredBookTargetPolicy.REQUIRE_ABSENT);
        StagedRestoredBookPair staged =
            acceptedValue(stageRestoredBookPairForFixture(verified, prepared))) {
      Files.delete(fixturePreparedPublication(prepared).journaledPair().bookStagePath());

      assertInstanceOf(
          StagedPairPublicationCommitOutcome.PublicationTransactionIncomplete.class,
          staged.commit());
      assertThrows(IllegalStateException.class, () -> staged.verifyInitializedRestoredBook());
    }
  }

  @Test
  void producerStageCollisionRecordsTheIncompleteJournalFailureWithoutASecondOwner()
      throws Exception {
    SourceBook source = initializedSourceBook("backup-stage-collision-source");
    Path book = privateTarget("backup-stage-collision-target", "backup.sqlite");
    Path secret = book.resolveSibling("backup.key");

    try (ProtectedBookMaintenanceStore.VerifiedBook verified =
            verifiedBook(maintenanceStore(), source.access());
        ProtectedBookMaintenanceStore.PreparedPairPublication prepared =
            prepareBackupPair(maintenanceStore(), book, secret)) {
      Files.createFile(fixturePreparedPublication(prepared).journaledPair().bookStagePath());
      assertThrows(
          ContractFailureException.class,
          () -> acceptedValue(stageBackupPairForFixture(verified, prepared)));
    }
  }

  @Test
  void sqliteStoreRejectsAForeignPreparedPairHandleBeforeStagingCanBegin() throws Exception {
    SourceBook source = initializedSourceBook("foreign-prepared-pair-source");
    Path book = privateTarget("foreign-prepared-pair-target", "backup.sqlite");
    Path secret = book.resolveSibling("backup.key");
    try (ProtectedBookMaintenanceStore.PreparedPairPublication foreignPreparedPair =
            new ProtectedBookMaintenanceStore.PreparedPairPublication() {
              @Override
              public Path bookTargetPath() {
                return book;
              }

              @Override
              public Path secretTargetPath() {
                return secret;
              }

              @Override
              public ProtectedBookMaintenanceStore.RestoredBookTargetPolicy bookTargetPolicy() {
                return ProtectedBookMaintenanceStore.RestoredBookTargetPolicy.REQUIRE_ABSENT;
              }

              @Override
              public void close() {}
            };
        ProtectedBookMaintenanceStore.VerifiedBook verified =
            verifiedBook(maintenanceStore(), source.access())) {
      assertThrows(
          IllegalArgumentException.class,
          () -> maintenanceStore().stageBackupPair(verified, foreignPreparedPair));
    }
  }

  private SourceBook initializedSourceBook(String name) {
    Path book = tempDirectory.resolve(name).resolve("source.sqlite");
    BookAccess access = bookAccess(book);
    initializeBook(access);
    return new SourceBook(book, sourceKeyPath(access), access);
  }

  private Path privateTarget(String directory, String fileName) throws IOException {
    Path target = tempDirectory.resolve(directory).resolve(fileName);
    Path parent = java.util.Objects.requireNonNull(target.getParent(), "target parent");
    Files.createDirectories(parent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parent);
    return target;
  }

  private static Path sourceKeyPath(BookAccess access) {
    return switch (access.passphraseSource()) {
      case BookAccess.PassphraseSource.KeyFile keyFile -> keyFile.bookKeyFilePath();
      case BookAccess.PassphraseSource.StandardInput _ ->
          throw new AssertionError("Expected key file.");
      case BookAccess.PassphraseSource.InteractivePrompt _ ->
          throw new AssertionError("Expected key file.");
    };
  }

  private record SourceBook(Path book, Path key, BookAccess access) {}
}
