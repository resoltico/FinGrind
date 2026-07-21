package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Proves pair publication leaves no final or owned-stage artifact at every interrupted checkpoint.
 */
class SqliteProtectedBookPairPublicationFaultMatrixTest
    extends SqliteArtifactPublicationTestSupport {
  private static final SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator
      NATIVE_LINK_CREATOR = Files::createLink;
  private static final SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator
      FAILING_LINK_CREATOR =
          (finalPath, stagedPath) -> {
            throw new IOException("injected publication failure");
          };
  private static final SqliteProtectedBookPublicationSupport.AtomicBookMover NATIVE_BOOK_MOVER =
      SqliteProtectedBookPublicationSupport::moveReplacing;
  private static final SqliteProtectedBookPublicationSupport.AtomicBookMover FAILING_BOOK_MOVER =
      (stagedPath, finalPath) -> {
        throw new IOException("injected publication failure");
      };

  @ParameterizedTest(name = "backup: {0}")
  @MethodSource("backupPublicationFaults")
  void backupPublicationFailure_removesEveryOwnedArtifact(
      String checkpoint,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator keyLinkCreator,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator backupLinkCreator)
      throws Exception {
    Path stagedBackupPath = writeArtifact("backup-" + checkpoint + "/staged.sqlite", "backup");
    Path stagedKeyPath = writeArtifact("backup-" + checkpoint + "/staged.key", "key");
    Path finalBackupPath = tempDirectory.resolve("backup-" + checkpoint).resolve("backup.sqlite");
    Path finalKeyPath = tempDirectory.resolve("backup-" + checkpoint).resolve("backup.key");

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedBackupPair pair =
            SqliteStagedBackupPairFactory.create(
                SqliteOwnedStagedArtifact.recordExisting(finalBackupPath, stagedBackupPath),
                finalBackupPath,
                SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath),
                finalKeyPath,
                passphrase,
                VERIFICATION_SUPPORT,
                keyLinkCreator,
                backupLinkCreator)) {
      assertThrows(IllegalStateException.class, pair::commit);
    }

    assertNoInterruptedPairArtifacts(
        stagedBackupPath, stagedKeyPath, finalBackupPath, finalKeyPath);
  }

  @ParameterizedTest(name = "restore: {0}")
  @MethodSource("restorePublicationFaults")
  void restorePublicationFailure_removesEveryOwnedArtifact(
      String checkpoint,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator keyLinkCreator,
      SqliteProtectedBookPublicationSupport.AtomicBookMover bookMover)
      throws Exception {
    Path stagedBookPath = writeArtifact("restore-" + checkpoint + "/staged.sqlite", "book");
    Path stagedKeyPath = writeArtifact("restore-" + checkpoint + "/staged.key", "key");
    Path finalBookPath =
        writeArtifact("restore-" + checkpoint + "/book.sqlite", "existing-live-book");
    Path finalKeyPath = tempDirectory.resolve("restore-" + checkpoint).resolve("book.key");
    byte[] finalBookBefore = Files.readAllBytes(finalBookPath);

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedRestoredBookPair pair =
            SqliteStagedRestoredBookPairFactory.create(
                new SqliteStagedProtectedBookPairArtifacts(
                    SqliteOwnedStagedArtifact.recordExisting(finalBookPath, stagedBookPath),
                    finalBookPath,
                    SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath),
                    finalKeyPath),
                RestoredBookTargetPolicy.REPLACE_SELECTED,
                passphrase,
                VERIFICATION_SUPPORT,
                new SqliteRestoredBookPairPublication.Operators(
                    keyLinkCreator, NATIVE_LINK_CREATOR, bookMover))) {
      assertThrows(IllegalStateException.class, pair::commit);
    }

    assertFalse(Files.exists(stagedBookPath));
    assertFalse(Files.exists(stagedKeyPath));
    assertArrayEquals(finalBookBefore, Files.readAllBytes(finalBookPath));
    assertFalse(Files.exists(finalKeyPath));
    assertTrue(SqliteOwnedStageRecord.findFor(finalBookPath).isEmpty());
    assertTrue(SqliteOwnedStageRecord.findFor(finalKeyPath).isEmpty());
  }

  private static Stream<Arguments> backupPublicationFaults() {
    return Stream.of(
        Arguments.of("generated-key", FAILING_LINK_CREATOR, NATIVE_LINK_CREATOR),
        Arguments.of("backup-file", NATIVE_LINK_CREATOR, FAILING_LINK_CREATOR));
  }

  private static Stream<Arguments> restorePublicationFaults() {
    return Stream.of(
        Arguments.of("generated-key", FAILING_LINK_CREATOR, NATIVE_BOOK_MOVER),
        Arguments.of("restored-book", NATIVE_LINK_CREATOR, FAILING_BOOK_MOVER));
  }

  private static void assertNoInterruptedPairArtifacts(
      Path stagedBookPath, Path stagedKeyPath, Path finalBookPath, Path finalKeyPath) {
    assertFalse(Files.exists(stagedBookPath));
    assertFalse(Files.exists(stagedKeyPath));
    assertFalse(Files.exists(finalBookPath));
    assertFalse(Files.exists(finalKeyPath));
    assertTrue(SqliteOwnedStageRecord.findFor(finalBookPath).isEmpty());
    assertTrue(SqliteOwnedStageRecord.findFor(finalKeyPath).isEmpty());
  }

  private static SqliteBookPassphrase testPassphrase() {
    return SqliteBookPassphrase.fromUtf8Bytes(
        "pair publication fault matrix", TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8));
  }
}
