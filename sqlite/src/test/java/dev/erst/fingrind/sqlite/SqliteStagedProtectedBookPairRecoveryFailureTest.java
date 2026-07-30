package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationFailureOutcome;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Exercises recoverability and failure disposition after staged-pair publication crosses its
 * boundary.
 */
class SqliteStagedProtectedBookPairRecoveryFailureTest
    extends SqliteStagedProtectedBookPairFailureTestSupport {
  @Test
  void recoveryRecordPromotionFailureStopsBackupAndRestoreBeforeAnyFinalMember() throws Exception {
    Path backupStagedPath = writeArtifact("record-promotion-failure/backup.stage", "backup");
    Path backupKeyStagedPath =
        writeArtifact("record-promotion-failure/backup.key.stage", "backup key");
    Path backupFinalPath =
        tempDirectory.resolve("record-promotion-failure").resolve("backup.sqlite");
    Path backupKeyFinalPath =
        tempDirectory.resolve("record-promotion-failure").resolve("backup.key");

    try (SqliteStagedBackupPair backupPair =
        SqliteStagedBackupPairFactory.create(
            new SqliteStagedProtectedBookPairArtifacts(
                SqliteOwnedStagedArtifact.recordExisting(backupFinalPath, backupStagedPath),
                backupFinalPath,
                SqliteOwnedStagedArtifact.recordExisting(backupKeyFinalPath, backupKeyStagedPath),
                backupKeyFinalPath),
            TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8),
            VERIFICATION_SUPPORT,
            Files::createLink,
            Files::createLink,
            null,
            null,
            recoveryRecordFailingDirectoryForcer(),
            SqliteOwnedRegularFileAccess::forceFile)) {
      sealBackupForPublication(backupPair);
      assertInstanceOf(
          ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired.class,
          backupPair.commit(backupBinding(backupFinalPath)));
    }

    assertFalse(Files.exists(backupFinalPath));
    assertFalse(Files.exists(backupKeyFinalPath));

    Path restoredStagedPath =
        writeArtifact("record-promotion-failure/restore.stage", "restored book");
    Path restoredKeyStagedPath =
        writeArtifact("record-promotion-failure/restore.key.stage", "restored key");
    Path restoredFinalPath =
        tempDirectory.resolve("record-promotion-failure").resolve("restored.sqlite");
    Path restoredKeyFinalPath =
        tempDirectory.resolve("record-promotion-failure").resolve("restored.key");

    try (SqliteStagedRestoredBookPair restoredPair =
        SqliteStagedRestoredBookPairFactory.create(
            new SqliteStagedProtectedBookPairArtifacts(
                SqliteOwnedStagedArtifact.recordExisting(restoredFinalPath, restoredStagedPath),
                restoredFinalPath,
                SqliteOwnedStagedArtifact.recordExisting(
                    restoredKeyFinalPath, restoredKeyStagedPath),
                restoredKeyFinalPath),
            RestoredBookTargetPolicy.REQUIRE_ABSENT,
            TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8),
            VERIFICATION_SUPPORT,
            SqliteRestoredBookPairPublication.defaultOperators(),
            null,
            null,
            recoveryRecordFailingDirectoryForcer(),
            SqliteOwnedRegularFileAccess::forceFile)) {
      assertInstanceOf(
          ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired.class,
          restoredPair.commit(restoreBinding(restoredStagedPath, restoredKeyStagedPath)));
    }

    assertFalse(Files.exists(restoredFinalPath));
    assertFalse(Files.exists(restoredKeyFinalPath));
  }

  @Test
  void recordForceFailureAfterSecretPublicationLeavesBothPairKindsRecoveryBound() throws Exception {
    Path backupStagedPath = writeArtifact("post-secret-force-failure/backup.stage", "backup");
    Path backupKeyStagedPath =
        writeArtifact("post-secret-force-failure/backup.key.stage", "backup key");
    Path backupFinalPath =
        tempDirectory.resolve("post-secret-force-failure").resolve("backup.sqlite");
    Path backupKeyFinalPath =
        tempDirectory.resolve("post-secret-force-failure").resolve("backup.key");
    AtomicInteger backupForceCalls = new AtomicInteger();

    try (SqliteStagedBackupPair backupPair =
        SqliteStagedBackupPairFactory.create(
            new SqliteStagedProtectedBookPairArtifacts(
                SqliteOwnedStagedArtifact.recordExisting(backupFinalPath, backupStagedPath),
                backupFinalPath,
                SqliteOwnedStagedArtifact.recordExisting(backupKeyFinalPath, backupKeyStagedPath),
                backupKeyFinalPath),
            TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8),
            VERIFICATION_SUPPORT,
            Files::createLink,
            Files::createLink,
            null,
            null,
            (ignoredStep, ignoredParent) -> {},
            recordForcerFailingAtBookBoundary(backupForceCalls))) {
      sealBackupForPublication(backupPair);
      assertInstanceOf(
          ProtectedBookPairPublicationFailureOutcome.CompletionUncertain.class,
          backupPair.commit(backupBinding(backupFinalPath)));
    }

    assertEquals(4, backupForceCalls.get());
    assertFalse(Files.exists(backupFinalPath));
    assertTrue(Files.exists(backupKeyFinalPath));

    Path restoredStagedPath =
        writeArtifact("post-secret-force-failure/restore.stage", "restored book");
    Path restoredKeyStagedPath =
        writeArtifact("post-secret-force-failure/restore.key.stage", "restored key");
    Path restoredFinalPath =
        tempDirectory.resolve("post-secret-force-failure").resolve("restored.sqlite");
    Path restoredKeyFinalPath =
        tempDirectory.resolve("post-secret-force-failure").resolve("restored.key");
    AtomicInteger restoredForceCalls = new AtomicInteger();

    try (SqliteStagedRestoredBookPair restoredPair =
        SqliteStagedRestoredBookPairFactory.create(
            new SqliteStagedProtectedBookPairArtifacts(
                SqliteOwnedStagedArtifact.recordExisting(restoredFinalPath, restoredStagedPath),
                restoredFinalPath,
                SqliteOwnedStagedArtifact.recordExisting(
                    restoredKeyFinalPath, restoredKeyStagedPath),
                restoredKeyFinalPath),
            RestoredBookTargetPolicy.REQUIRE_ABSENT,
            TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8),
            VERIFICATION_SUPPORT,
            SqliteRestoredBookPairPublication.defaultOperators(),
            null,
            null,
            (ignoredStep, ignoredParent) -> {},
            recordForcerFailingAtBookBoundary(restoredForceCalls))) {
      assertInstanceOf(
          ProtectedBookPairPublicationFailureOutcome.CompletionUncertain.class,
          restoredPair.commit(restoreBinding(restoredStagedPath, restoredKeyStagedPath)));
    }

    assertEquals(4, restoredForceCalls.get());
    assertFalse(Files.exists(restoredFinalPath));
    assertTrue(Files.exists(restoredKeyFinalPath));
  }

  @Test
  void restoredRecordForceFailureBeforeAnyFinalMemberRemainsPrepublicationRecoverable()
      throws Exception {
    Path stagedBookPath = writeArtifact("restore-pre-member-force-failure/staged.sqlite", "book");
    Path stagedKeyPath = writeArtifact("restore-pre-member-force-failure/staged.key", "key");
    Path finalBookPath =
        tempDirectory.resolve("restore-pre-member-force-failure").resolve("restored.sqlite");
    Path finalKeyPath =
        tempDirectory.resolve("restore-pre-member-force-failure").resolve("restored.key");

    try (SqliteStagedRestoredBookPair pair =
        SqliteStagedRestoredBookPairFactory.create(
            new SqliteStagedProtectedBookPairArtifacts(
                SqliteOwnedStagedArtifact.recordExisting(finalBookPath, stagedBookPath),
                finalBookPath,
                SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath),
                finalKeyPath),
            RestoredBookTargetPolicy.REQUIRE_ABSENT,
            TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8),
            VERIFICATION_SUPPORT,
            SqliteRestoredBookPairPublication.defaultOperators(),
            null,
            null,
            (ignoredStep, ignoredParent) -> {},
            ignoredEvidencePath -> {
              throw new IOException("simulated recovery-evidence force failure");
            })) {
      assertInstanceOf(
          ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired.class,
          pair.commit(restoreBinding(stagedBookPath, stagedKeyPath)));
    }

    assertFalse(Files.exists(finalBookPath));
    assertFalse(Files.exists(finalKeyPath));
    assertTrue(Files.exists(stagedBookPath));
    assertTrue(Files.exists(stagedKeyPath));
  }

  @Test
  void recoveryRecordForceMutation_blocksEveryRestoredFinalPrimitive() throws Exception {
    Path stagedBookPath = writeArtifact("record-forcer-mutates/staged.sqlite", "restored book");
    Path stagedKeyPath = writeArtifact("record-forcer-mutates/staged.key", "restored key");
    Path finalBookPath = writeArtifact("record-forcer-mutates/book.sqlite", "previous book");
    Path finalKeyPath = tempDirectory.resolve("record-forcer-mutates").resolve("book.key");
    AtomicInteger finalLinkCalls = new AtomicInteger();
    AtomicInteger finalMoveCalls = new AtomicInteger();

    try (SqliteStagedRestoredBookPair pair =
        SqliteStagedRestoredBookPairFactory.create(
            new SqliteStagedProtectedBookPairArtifacts(
                SqliteOwnedStagedArtifact.recordExisting(finalBookPath, stagedBookPath),
                finalBookPath,
                SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath),
                finalKeyPath),
            RestoredBookTargetPolicy.REPLACE_SELECTED,
            TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8),
            VERIFICATION_SUPPORT,
            new SqliteRestoredBookPairPublication.Operators(
                (finalPath, stagedPath) -> {
                  finalLinkCalls.incrementAndGet();
                  Files.createLink(finalPath, stagedPath);
                },
                (finalPath, stagedPath) -> {
                  finalLinkCalls.incrementAndGet();
                  Files.createLink(finalPath, stagedPath);
                },
                (stagedPath, finalPath) -> {
                  finalMoveCalls.incrementAndGet();
                  SqliteProtectedBookPublicationSupport.moveReplacing(stagedPath, finalPath);
                }),
            null,
            null,
            (step, parentDirectory) -> {},
            evidencePath -> Files.writeString(evidencePath, "mutated recovery evidence"))) {
      // Witness acquisition exercises the injected book primitive once; only commit boundaries
      // count for this proof.
      finalLinkCalls.set(0);
      finalMoveCalls.set(0);

      ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired outcome =
          assertInstanceOf(
              ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired.class,
              pair.commit(rekeyBinding(finalBookPath, finalBookPath.resolveSibling("source.key"))));
      assertEquals(
          dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRecoveryRecordState
              .DURABILITY_UNCONFIRMED,
          outcome.recoveryRecordState());
    }

    assertEquals(0, finalLinkCalls.get());
    assertEquals(0, finalMoveCalls.get());
    assertEquals("previous book", Files.readString(finalBookPath));
    assertFalse(Files.exists(finalKeyPath));
    assertTrue(Files.exists(stagedBookPath));
    assertTrue(Files.exists(stagedKeyPath));
  }

  @Test
  void stagedPairs_retainTheirStageEvidenceAfterSuccessfulPublicationWhenOwnerRecordsAreAltered()
      throws Exception {
    Path backupStagedPath = writeArtifact("backup-published-retention/staged.sqlite", "backup");
    Path backupKeyStagedPath = writeArtifact("backup-published-retention/staged.key", "key");
    Path backupFinalPath =
        tempDirectory.resolve("backup-published-retention").resolve("backup.sqlite");
    Path backupKeyFinalPath =
        tempDirectory.resolve("backup-published-retention").resolve("backup.key");
    SqliteOwnedStagedArtifact backupStaged =
        SqliteOwnedStagedArtifact.recordExisting(backupFinalPath, backupStagedPath);
    SqliteOwnedStagedArtifact backupKeyStaged =
        SqliteOwnedStagedArtifact.recordExisting(backupKeyFinalPath, backupKeyStagedPath);
    Path backupKeyRecordPath = ownedRecordPathForStage(backupKeyStagedPath);

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedBackupPair backupPair =
            SqliteStagedBackupPairFactory.create(
                backupStaged,
                backupFinalPath,
                backupKeyStaged,
                backupKeyFinalPath,
                passphrase,
                VERIFICATION_SUPPORT,
                Files::createLink,
                (finalPath, stagedPath) -> {
                  Files.createLink(finalPath, stagedPath);
                  replaceWithNonemptyDirectory(backupKeyRecordPath);
                },
                (step, parentDirectory) -> {})) {
      sealBackupForPublication(backupPair);
      backupPair.commit(backupBinding(backupFinalPath));
    }

    assertTrue(Files.exists(backupFinalPath));
    assertTrue(Files.exists(backupKeyFinalPath));
    assertTrue(Files.exists(backupStagedPath));
    assertTrue(Files.exists(backupKeyStagedPath));
    assertTrue(Files.isDirectory(backupKeyRecordPath));

    Path restoredStagedPath = writeArtifact("restore-published-retention/staged.sqlite", "book");
    Path restoredKeyStagedPath = writeArtifact("restore-published-retention/staged.key", "key");
    Path restoredFinalPath =
        tempDirectory.resolve("restore-published-retention").resolve("book.sqlite");
    Path restoredKeyFinalPath =
        tempDirectory.resolve("restore-published-retention").resolve("book.key");
    SqliteOwnedStagedArtifact restoredStaged =
        SqliteOwnedStagedArtifact.recordExisting(restoredFinalPath, restoredStagedPath);
    SqliteOwnedStagedArtifact restoredKeyStaged =
        SqliteOwnedStagedArtifact.recordExisting(restoredKeyFinalPath, restoredKeyStagedPath);
    Path restoredKeyRecordPath = ownedRecordPathForStage(restoredKeyStagedPath);

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedRestoredBookPair restoredPair =
            SqliteStagedRestoredBookPairFactory.create(
                new SqliteStagedProtectedBookPairArtifacts(
                    restoredStaged, restoredFinalPath, restoredKeyStaged, restoredKeyFinalPath),
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                passphrase,
                VERIFICATION_SUPPORT,
                new SqliteRestoredBookPairPublication.Operators(
                    Files::createLink,
                    (finalPath, stagedPath) -> {
                      Files.createLink(finalPath, stagedPath);
                      replaceWithNonemptyDirectory(restoredKeyRecordPath);
                    },
                    SqliteProtectedBookPublicationSupport::moveReplacing))) {
      restoredPair.commit(restoreBinding(restoredStagedPath, restoredKeyStagedPath));
    }

    assertTrue(Files.exists(restoredFinalPath));
    assertTrue(Files.exists(restoredKeyFinalPath));
    assertTrue(Files.exists(restoredStagedPath));
    assertTrue(Files.exists(restoredKeyStagedPath));
    assertTrue(Files.isDirectory(restoredKeyRecordPath));
  }

  @Test
  void stagedPairCommitReturnsItsExactSuccessfulOutcomeOnReplay() throws Exception {
    Path backupStagedPath = writeArtifact("replay-success/backup.stage", "backup");
    Path backupKeyStagedPath = writeArtifact("replay-success/backup.key.stage", "key");
    Path backupFinalPath = tempDirectory.resolve("replay-success").resolve("backup.sqlite");
    Path backupKeyFinalPath = tempDirectory.resolve("replay-success").resolve("backup.key");

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedBackupPair backupPair =
            SqliteStagedBackupPairFactory.create(
                SqliteOwnedStagedArtifact.recordExisting(backupFinalPath, backupStagedPath),
                backupFinalPath,
                SqliteOwnedStagedArtifact.recordExisting(backupKeyFinalPath, backupKeyStagedPath),
                backupKeyFinalPath,
                passphrase,
                VERIFICATION_SUPPORT)) {
      sealBackupForPublication(backupPair);
      var published = backupPair.commit(backupBinding(backupFinalPath));
      assertInstanceOf(
          dev.erst.fingrind.executor.spi.StagedPairPublicationCommitOutcome.Published.class,
          published);
      assertSame(published, backupPair.commit(backupBinding(backupFinalPath)));
    }

    Path restoredStagedPath = writeArtifact("replay-success/restore.stage", "restored book");
    Path restoredKeyStagedPath = writeArtifact("replay-success/restore.key.stage", "restored key");
    Path restoredFinalPath = tempDirectory.resolve("replay-success").resolve("restored.sqlite");
    Path restoredKeyFinalPath = tempDirectory.resolve("replay-success").resolve("restored.key");

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedRestoredBookPair restoredPair =
            SqliteStagedRestoredBookPairFactory.create(
                new SqliteStagedProtectedBookPairArtifacts(
                    SqliteOwnedStagedArtifact.recordExisting(restoredFinalPath, restoredStagedPath),
                    restoredFinalPath,
                    SqliteOwnedStagedArtifact.recordExisting(
                        restoredKeyFinalPath, restoredKeyStagedPath),
                    restoredKeyFinalPath),
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                passphrase,
                VERIFICATION_SUPPORT)) {
      var published =
          restoredPair.commit(restoreBinding(restoredStagedPath, restoredKeyStagedPath));
      assertInstanceOf(
          dev.erst.fingrind.executor.spi.StagedPairPublicationCommitOutcome.Published.class,
          published);
      assertSame(
          published,
          restoredPair.commit(restoreBinding(restoredStagedPath, restoredKeyStagedPath)));
    }
  }

  @Test
  void stagedRestoredBookPair_refusesASelectedTargetChangedAfterRecoveryEvidence()
      throws Exception {
    Path stagedBookPath = writeArtifact("restore-selected-target-change/staged.sqlite", "book");
    Path stagedKeyPath = writeArtifact("restore-selected-target-change/staged.key", "key");
    Path finalBookPath =
        writeArtifact("restore-selected-target-change/book.sqlite", "selected original book");
    Path finalKeyPath = tempDirectory.resolve("restore-selected-target-change").resolve("book.key");
    AtomicBoolean changedAfterRecoveryEvidence = new AtomicBoolean();

    try (SqliteStagedRestoredBookPair pair =
        SqliteStagedRestoredBookPairFactory.create(
            new SqliteStagedProtectedBookPairArtifacts(
                SqliteOwnedStagedArtifact.recordExisting(finalBookPath, stagedBookPath),
                finalBookPath,
                SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath),
                finalKeyPath),
            RestoredBookTargetPolicy.REPLACE_SELECTED,
            TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8),
            VERIFICATION_SUPPORT,
            SqliteRestoredBookPairPublication.defaultOperators(),
            null,
            null,
            (ignoredStep, ignoredParent) -> {},
            recordPath -> {
              SqliteOwnedRegularFileAccess.forceFile(recordPath);
              if (changedAfterRecoveryEvidence.compareAndSet(false, true)) {
                Files.writeString(finalBookPath, "selected replacement by another writer");
              }
            })) {
      assertInstanceOf(
          ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired.class,
          pair.commit(rekeyBinding(finalBookPath, finalBookPath.resolveSibling("source.key"))));
    }

    assertTrue(changedAfterRecoveryEvidence.get());
    assertEquals("selected replacement by another writer", Files.readString(finalBookPath));
    assertFalse(Files.exists(finalKeyPath));
    assertTrue(Files.exists(stagedBookPath));
    assertTrue(Files.exists(stagedKeyPath));
  }

  @Test
  void stagedRestoredBookPair_retainsEvidenceWhenUnexpectedFailureFollowsSecretPublication()
      throws Exception {
    Path stagedBookPath = writeArtifact("restore-post-boundary-runtime/staged.sqlite", "book");
    Path stagedKeyPath = writeArtifact("restore-post-boundary-runtime/staged.key", "key");
    Path finalBookPath =
        writeArtifact("restore-post-boundary-runtime/book.sqlite", "selected original book");
    Path finalKeyPath = tempDirectory.resolve("restore-post-boundary-runtime").resolve("book.key");

    try (SqliteStagedRestoredBookPair pair =
        SqliteStagedRestoredBookPairFactory.create(
            new SqliteStagedProtectedBookPairArtifacts(
                SqliteOwnedStagedArtifact.recordExisting(finalBookPath, stagedBookPath),
                finalBookPath,
                SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath),
                finalKeyPath),
            RestoredBookTargetPolicy.REPLACE_SELECTED,
            TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8),
            VERIFICATION_SUPPORT,
            SqliteRestoredBookPairPublication.defaultOperators(),
            null,
            null,
            (step, ignoredParent) -> {
              if (step
                  == SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
                      .GENERATED_SECRET_PUBLICATION) {
                throw new IllegalStateException("simulated post-publication runtime failure");
              }
            })) {
      assertInstanceOf(
          ProtectedBookPairPublicationFailureOutcome.CompletionUncertain.class,
          pair.commit(rekeyBinding(finalBookPath, finalBookPath.resolveSibling("source.key"))));
    }

    assertTrue(Files.exists(finalKeyPath));
    assertTrue(Files.exists(stagedBookPath));
    assertTrue(Files.exists(stagedKeyPath));
  }

  @Test
  void stagedRestoredBookPair_acceptsASelectedTargetAlreadyConvergedToTheStagedBook()
      throws Exception {
    Path stagedBookPath = writeArtifact("restore-selected-target-converged/staged.sqlite", "book");
    Path stagedKeyPath = writeArtifact("restore-selected-target-converged/staged.key", "key");
    Path finalBookPath =
        writeArtifact("restore-selected-target-converged/book.sqlite", "selected original book");
    Path finalKeyPath =
        tempDirectory.resolve("restore-selected-target-converged").resolve("book.key");
    AtomicBoolean convergedAfterRecoveryEvidence = new AtomicBoolean();

    try (SqliteStagedRestoredBookPair pair =
        SqliteStagedRestoredBookPairFactory.create(
            new SqliteStagedProtectedBookPairArtifacts(
                SqliteOwnedStagedArtifact.recordExisting(finalBookPath, stagedBookPath),
                finalBookPath,
                SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath),
                finalKeyPath),
            RestoredBookTargetPolicy.REPLACE_SELECTED,
            TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8),
            VERIFICATION_SUPPORT,
            SqliteRestoredBookPairPublication.defaultOperators(),
            null,
            null,
            (step, ignoredParent) -> {
              if (step
                      == SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
                          .RECOVERY_RECORD
                  && convergedAfterRecoveryEvidence.compareAndSet(false, true)) {
                Files.writeString(finalBookPath, "book");
              }
            })) {
      assertInstanceOf(
          dev.erst.fingrind.executor.spi.StagedPairPublicationCommitOutcome.Published.class,
          pair.commit(rekeyBinding(finalBookPath, finalBookPath.resolveSibling("source.key"))));
    }

    assertTrue(convergedAfterRecoveryEvidence.get());
    assertEquals("book", Files.readString(finalBookPath));
    assertTrue(Files.exists(finalKeyPath));
    assertTrue(Files.exists(stagedBookPath));
    assertTrue(Files.exists(stagedKeyPath));
  }

  @Test
  void stagedRestoredBookPair_keepsItsPublicationRecoverableWhenTheTargetChangesAfterSecret()
      throws Exception {
    Path stagedBookPath = writeArtifact("restore-post-secret-change/staged.sqlite", "book");
    Path stagedKeyPath = writeArtifact("restore-post-secret-change/staged.key", "key");
    Path finalBookPath =
        writeArtifact("restore-post-secret-change/book.sqlite", "selected original book");
    Path finalKeyPath = tempDirectory.resolve("restore-post-secret-change").resolve("book.key");
    AtomicBoolean changedAfterSecretPublication = new AtomicBoolean();

    try (SqliteStagedRestoredBookPair pair =
        SqliteStagedRestoredBookPairFactory.create(
            new SqliteStagedProtectedBookPairArtifacts(
                SqliteOwnedStagedArtifact.recordExisting(finalBookPath, stagedBookPath),
                finalBookPath,
                SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath),
                finalKeyPath),
            RestoredBookTargetPolicy.REPLACE_SELECTED,
            TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8),
            VERIFICATION_SUPPORT,
            SqliteRestoredBookPairPublication.defaultOperators(),
            null,
            null,
            (step, parentDirectory) -> {
              if (step
                      == SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
                          .GENERATED_SECRET_PUBLICATION
                  && changedAfterSecretPublication.compareAndSet(false, true)) {
                Files.writeString(
                    finalBookPath, "selected target changed after secret publication");
              }
            })) {
      assertInstanceOf(
          ProtectedBookPairPublicationFailureOutcome.CompletionUncertain.class,
          pair.commit(rekeyBinding(finalBookPath, finalBookPath.resolveSibling("source.key"))));
    }

    assertTrue(changedAfterSecretPublication.get());
    assertEquals(
        "selected target changed after secret publication", Files.readString(finalBookPath));
    assertTrue(Files.exists(finalKeyPath));
    assertTrue(Files.exists(stagedBookPath));
    assertTrue(Files.exists(stagedKeyPath));
  }

  @Test
  void restoredBookMoveFailureAfterItsAttemptIsCompletionUncertain() throws Exception {
    Path stagedBookPath = writeArtifact("restore-book-move-failure/staged.sqlite", "book");
    Path stagedKeyPath = writeArtifact("restore-book-move-failure/staged.key", "key");
    Path finalBookPath =
        writeArtifact("restore-book-move-failure/book.sqlite", "selected original book");
    Path finalKeyPath = tempDirectory.resolve("restore-book-move-failure").resolve("book.key");

    try (SqliteStagedRestoredBookPair pair =
        SqliteStagedRestoredBookPairFactory.create(
            new SqliteStagedProtectedBookPairArtifacts(
                SqliteOwnedStagedArtifact.recordExisting(finalBookPath, stagedBookPath),
                finalBookPath,
                SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath),
                finalKeyPath),
            RestoredBookTargetPolicy.REPLACE_SELECTED,
            TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8),
            VERIFICATION_SUPPORT,
            new SqliteRestoredBookPairPublication.Operators(
                Files::createLink,
                Files::createLink,
                (stagedPath, targetPath) -> {
                  throw new IOException("simulated final book move failure");
                }))) {
      assertInstanceOf(
          ProtectedBookPairPublicationFailureOutcome.CompletionUncertain.class,
          pair.commit(rekeyBinding(finalBookPath, finalBookPath.resolveSibling("source.key"))));
    }

    assertEquals("selected original book", Files.readString(finalBookPath));
    assertTrue(Files.exists(finalKeyPath));
    assertTrue(Files.exists(stagedBookPath));
    assertTrue(Files.exists(stagedKeyPath));
  }

  @Test
  void backupBookLinkFailureAfterItsAttemptIsCompletionUncertain() throws Exception {
    Path stagedBackupPath = writeArtifact("backup-book-link-failure/staged.sqlite", "backup");
    Path stagedKeyPath = writeArtifact("backup-book-link-failure/staged.key", "key");
    Path finalBackupPath =
        tempDirectory.resolve("backup-book-link-failure").resolve("backup.sqlite");
    Path finalKeyPath = tempDirectory.resolve("backup-book-link-failure").resolve("backup.key");

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedBackupPair pair =
            SqliteStagedBackupPairFactory.create(
                SqliteOwnedStagedArtifact.recordExisting(finalBackupPath, stagedBackupPath),
                finalBackupPath,
                SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath),
                finalKeyPath,
                passphrase,
                VERIFICATION_SUPPORT,
                Files::createLink,
                (finalPath, stagedPath) -> {
                  throw new IOException("simulated final backup link failure");
                },
                (ignoredStep, ignoredParent) -> {})) {
      sealBackupForPublication(pair);
      assertInstanceOf(
          ProtectedBookPairPublicationFailureOutcome.CompletionUncertain.class,
          pair.commit(backupBinding(finalBackupPath)));
    }

    assertFalse(Files.exists(finalBackupPath));
    assertTrue(Files.exists(finalKeyPath));
    assertTrue(Files.exists(stagedBackupPath));
    assertTrue(Files.exists(stagedKeyPath));
  }

  @Test
  void retainedRestoredPairCannotBeCommittedAfterItIsFinishedWithoutAPublicationOutcome()
      throws Exception {
    Path stagedBookPath = writeArtifact("restore-finished/staged.sqlite", "book");
    Path stagedKeyPath = writeArtifact("restore-finished/staged.key", "key");
    Path finalBookPath = tempDirectory.resolve("restore-finished").resolve("book.sqlite");
    Path finalKeyPath = tempDirectory.resolve("restore-finished").resolve("book.key");

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedRestoredBookPair pair =
            newStagedRestoredBookPair(
                stagedBookPath, finalBookPath, stagedKeyPath, finalKeyPath, passphrase)) {
      pair.retainUnpublishedArtifacts();
      assertThrows(
          IllegalStateException.class,
          () -> pair.commit(restoreBinding(stagedBookPath, stagedKeyPath)));
    }

    assertTrue(Files.exists(stagedBookPath));
    assertTrue(Files.exists(stagedKeyPath));
  }

  @Test
  void stagedRestoredBookPair_closesThePassphraseWhenFactoryValidationFails() throws Exception {
    Path stagedBookPath = writeArtifact("restore-factory/staged.sqlite", "book");
    Path stagedKeyPath = writeArtifact("restore-factory/staged.key", "key");
    try (SqliteBookPassphrase passphrase = testPassphrase()) {
      assertThrows(
          NullPointerException.class,
          () ->
              createWithNullVerificationSupport(
                  stagedBookPath,
                  tempDirectory.resolve("restore-factory").resolve("final.sqlite"),
                  stagedKeyPath,
                  tempDirectory.resolve("restore-factory").resolve("final.key"),
                  passphrase));
    }
  }

  @Test
  void stagedRestoredBookPair_retainsStagesBeforeAndAfterKeyPublicationFailures() throws Exception {
    Path missingKeyStagedBook = writeArtifact("restore-before/staged.sqlite", "book");
    Path missingKeyPath = tempDirectory.resolve("restore-before").resolve("missing.key");
    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedRestoredBookPair stagedPair =
            newStagedRestoredBookPair(
                missingKeyStagedBook,
                tempDirectory.resolve("restore-before").resolve("final.sqlite"),
                missingKeyPath,
                tempDirectory.resolve("restore-before").resolve("final.key"),
                passphrase)) {
      assertThrows(
          IllegalStateException.class,
          () -> stagedPair.commit(restoreBinding(missingKeyStagedBook, missingKeyPath)));
    }
    assertTrue(Files.exists(missingKeyStagedBook));

    Path stagedBookPath = writeArtifact("restore-after/staged.sqlite", "book");
    Path stagedKeyPath = writeArtifact("restore-after/staged.key", "key");
    Path finalBookPath = tempDirectory.resolve("restore-after").resolve("final.sqlite");
    Files.createDirectories(finalBookPath);
    Path originalBookContent = finalBookPath.resolve("child");
    Files.writeString(originalBookContent, "occupied");
    Path finalKeyPath = tempDirectory.resolve("restore-after").resolve("final.key");
    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedRestoredBookPair stagedPair =
            newStagedRestoredBookPair(
                stagedBookPath, finalBookPath, stagedKeyPath, finalKeyPath, passphrase)) {
      assertThrows(
          IllegalStateException.class,
          () -> stagedPair.commit(restoreBinding(stagedBookPath, stagedKeyPath)));
    }
    assertTrue(Files.exists(stagedBookPath));
    assertTrue(Files.exists(stagedKeyPath));
    assertFalse(Files.exists(finalKeyPath));
    assertEquals("occupied", Files.readString(originalBookContent));
  }

  @Test
  void restoredPairFailureDispositionDistinguishesEveryRecoveryBoundaryOutcome() {
    Path targetPath = tempDirectory.resolve("disposition-target.key");

    assertEquals(
        SqliteStagedRestoredBookPair.CommitFailureDisposition.PREPUBLICATION_RECOVERY_REQUIRED,
        SqliteStagedRestoredBookPair.failureDisposition(
            new SqliteProtectedBookPairPublicationRecord
                .RecoveryRecordDurabilityUnconfirmedException(new IOException("unconfirmed")),
            false,
            false));
    assertEquals(
        SqliteStagedRestoredBookPair.CommitFailureDisposition.PREPUBLICATION_RECOVERY_REQUIRED,
        SqliteStagedRestoredBookPair.failureDisposition(
            guardRejection(SqliteProtectedBookPublicationSupport.FinalMember.SECRET), true, false));
    assertEquals(
        SqliteStagedRestoredBookPair.CommitFailureDisposition.COMPLETION_UNCERTAIN,
        SqliteStagedRestoredBookPair.failureDisposition(
            guardRejection(SqliteProtectedBookPublicationSupport.FinalMember.BOOK), true, false));
    assertEquals(
        SqliteStagedRestoredBookPair.CommitFailureDisposition.PREBOUNDARY_UNEXPECTED,
        SqliteStagedRestoredBookPair.failureDisposition(
            new IOException("before recovery record"), false, false));

    assertEquals(
        SqliteStagedRestoredBookPair.CommitFailureDisposition.POSTPUBLICATION_RECOVERY_REQUIRED,
        SqliteStagedRestoredBookPair.failureDisposition(
            new SqliteGeneratedSecretTargetOccupiedException(targetPath), true, false));
    assertEquals(
        SqliteStagedRestoredBookPair.CommitFailureDisposition.POSTPUBLICATION_RECOVERY_REQUIRED,
        SqliteStagedRestoredBookPair.failureDisposition(
            new SqliteCallerPathContractException(
                targetPath,
                SqliteCallerPathFailure.TARGET_OWNER_ONLY_REQUIRED,
                "selected target is not owner-only"),
            true,
            false));
    assertEquals(
        SqliteStagedRestoredBookPair.CommitFailureDisposition.POSTPUBLICATION_RECOVERY_REQUIRED,
        SqliteStagedRestoredBookPair.failureDisposition(
            new java.nio.file.FileAlreadyExistsException(targetPath.toString()), true, false));
    assertEquals(
        SqliteStagedRestoredBookPair.CommitFailureDisposition.PREPUBLICATION_RECOVERY_REQUIRED,
        SqliteStagedRestoredBookPair.failureDisposition(
            new IOException("before a final primitive"), true, false));
    assertEquals(
        SqliteStagedRestoredBookPair.CommitFailureDisposition.COMPLETION_UNCERTAIN,
        SqliteStagedRestoredBookPair.failureDisposition(
            new IOException("after a final primitive"), true, true));
    assertEquals(
        SqliteStagedRestoredBookPair.CommitFailureDisposition.POSTBOUNDARY_UNEXPECTED,
        SqliteStagedRestoredBookPair.failureDisposition(
            new IllegalStateException("post-boundary programming failure"), true, true));
  }

  @Test
  void backupPairFailureDispositionDistinguishesEveryRecoveryBoundaryOutcome() {
    Path targetPath = tempDirectory.resolve("backup-disposition-target.key");

    assertEquals(
        SqliteStagedBackupPair.CommitFailureDisposition.PREPUBLICATION_RECOVERY_REQUIRED,
        SqliteStagedBackupPair.failureDisposition(
            new SqliteProtectedBookPairPublicationRecord
                .RecoveryRecordDurabilityUnconfirmedException(new IOException("unconfirmed")),
            false));
    assertEquals(
        SqliteStagedBackupPair.CommitFailureDisposition.DURABLY_RETAINED_PREPUBLICATION,
        SqliteStagedBackupPair.failureDisposition(
            guardRejection(SqliteProtectedBookPublicationSupport.FinalMember.SECRET), true));
    assertEquals(
        SqliteStagedBackupPair.CommitFailureDisposition.COMPLETION_UNCERTAIN,
        SqliteStagedBackupPair.failureDisposition(
            guardRejection(SqliteProtectedBookPublicationSupport.FinalMember.BOOK), true));
    assertEquals(
        SqliteStagedBackupPair.CommitFailureDisposition.PREBOUNDARY_UNEXPECTED,
        SqliteStagedBackupPair.failureDisposition(
            new IOException("before recovery record"), false));

    assertEquals(
        SqliteStagedBackupPair.CommitFailureDisposition.POSTPUBLICATION_RECOVERY_REQUIRED,
        SqliteStagedBackupPair.failureDisposition(
            new SqliteGeneratedSecretTargetOccupiedException(targetPath), true));
    assertEquals(
        SqliteStagedBackupPair.CommitFailureDisposition.POSTPUBLICATION_RECOVERY_REQUIRED,
        SqliteStagedBackupPair.failureDisposition(
            new SqliteCallerPathContractException(
                targetPath,
                SqliteCallerPathFailure.TARGET_OWNER_ONLY_REQUIRED,
                "selected target is not owner-only"),
            true));
    assertEquals(
        SqliteStagedBackupPair.CommitFailureDisposition.POSTPUBLICATION_RECOVERY_REQUIRED,
        SqliteStagedBackupPair.failureDisposition(
            new java.nio.file.FileAlreadyExistsException(targetPath.toString()), true));
    assertEquals(
        SqliteStagedBackupPair.CommitFailureDisposition.POSTBOUNDARY_UNEXPECTED,
        SqliteStagedBackupPair.failureDisposition(new IOException("after recovery record"), true));
    assertEquals(
        SqliteStagedBackupPair.CommitFailureDisposition.POSTBOUNDARY_UNEXPECTED,
        SqliteStagedBackupPair.failureDisposition(
            new IllegalStateException("post-boundary programming failure"), true));
  }
}
