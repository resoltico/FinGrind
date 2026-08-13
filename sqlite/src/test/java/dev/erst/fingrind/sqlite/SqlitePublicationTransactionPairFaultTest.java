package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.PublicationCleanupOutcome;
import dev.erst.fingrind.core.PublicationCommitOutcome;
import dev.erst.fingrind.core.PublicationTransactionId;
import dev.erst.fingrind.core.PublicationTransactionOutcome;
import dev.erst.fingrind.core.PublicationTransactionOwnerContext;
import dev.erst.fingrind.core.PublicationTransactionPublisher;
import dev.erst.fingrind.core.PublicationTransactionRecoveryReceipt;
import dev.erst.fingrind.core.PublicationTransactionRequest;
import dev.erst.fingrind.core.PublicationTransactionResult;
import dev.erst.fingrind.core.PublicationTransactionService;
import dev.erst.fingrind.core.PublicationTransactionStageReservation;
import dev.erst.fingrind.core.PublicationTransactionState;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.HeldLease;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Proves pair adapters fail through journal recovery when publication or producer work aborts. */
class SqlitePublicationTransactionPairFaultTest extends SqliteNativeBridgeTestSupport {
  @Test
  void finalCollisionRecoversTheJournalOutcomeAndReleasesStageAccess() throws Exception {
    SqlitePublicationTransactionPair pair = reserve(new FaultTransactions(Mode.COLLISION));

    assertThrows(
        dev.erst.fingrind.core.PublicationTransactionExecutionException.class, pair::publish);
    assertNull(SqliteJournaledStageAccess.finalTargetForCurrentThread(pair.bookStagePath()));
    assertNull(SqliteJournaledStageAccess.finalTargetForCurrentThread(pair.secretStagePath()));
  }

  @Test
  void producerFailureEscalatesWhenTheJournalOutcomeCannotBeRecovered() throws Exception {
    SqlitePublicationTransactionPair pair = reserve(new FaultTransactions(Mode.RECOVERY_FAILURE));
    IOException producerFailure = new IOException("producer failure");

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () -> pair.incompleteFailure(pair.bookTargetPath(), "backupFilePath", producerFailure));
    assertEquals(
        "recovery failure", assertInstanceOf(IOException.class, failure.getCause()).getMessage());
    assertSame(producerFailure, failure.getSuppressed()[0]);
    assertNull(SqliteJournaledStageAccess.finalTargetForCurrentThread(pair.bookStagePath()));
  }

  @Test
  void incompletePublicationResultIsNeverPresentedAsAPublishedPair() throws Exception {
    SqlitePublicationTransactionPair pair = reserve(new FaultTransactions(Mode.INCOMPLETE_RESULT));

    dev.erst.fingrind.core.PublicationTransactionExecutionException failure =
        assertThrows(
            dev.erst.fingrind.core.PublicationTransactionExecutionException.class, pair::publish);

    assertEquals(PublicationTransactionState.BLOCKED, failure.result().state());
    assertNull(SqliteJournaledStageAccess.finalTargetForCurrentThread(pair.bookStagePath()));
    assertNull(SqliteJournaledStageAccess.finalTargetForCurrentThread(pair.secretStagePath()));
  }

  @Test
  void stagedCommittersExposeUnderlyingJournalIoFailuresWithoutInventingRecoveryAuthority()
      throws Exception {
    SqlitePublicationTransactionPair backupPair =
        reserve(new FaultTransactions(Mode.PUBLISH_FAILURE));
    SqliteOwnedRegularFileAccess.createNewEmptyFile(backupPair.bookStagePath());
    try (SqliteJournaledStagedBackupPair staged =
        new SqliteJournaledStagedBackupPair(
            backupPair,
            backupPair.bookStagePath(),
            passphrase("backup staged publish failure"),
            new SqliteProtectedBookVerificationSupport())) {
      staged.sealArtifact(new byte[] {1});
      IllegalStateException failure = assertThrows(IllegalStateException.class, staged::commit);
      assertEquals(
          "publish failure", assertInstanceOf(IOException.class, failure.getCause()).getMessage());
    }

    SqlitePublicationTransactionPair restoredPair =
        reserve(new FaultTransactions(Mode.PUBLISH_FAILURE));
    try (SqliteJournaledStagedRestoredBookPair staged =
        new SqliteJournaledStagedRestoredBookPair(
            restoredPair,
            restoredPair.bookStagePath(),
            passphrase("restored staged publish failure"),
            new SqliteProtectedBookVerificationSupport())) {
      IllegalStateException failure = assertThrows(IllegalStateException.class, staged::commit);
      assertEquals(
          "publish failure", assertInstanceOf(IOException.class, failure.getCause()).getMessage());
    }
  }

  @Test
  void preparedPairClosesBothTargetLeasesWhenTheFirstLeaseFails() throws Exception {
    SqlitePublicationTransactionPair pair = reserve(new FaultTransactions(Mode.PUBLISH_FAILURE));
    AtomicBoolean bookLeaseClosed = new AtomicBoolean();
    AtomicBoolean secretLeaseFailed = new AtomicBoolean();
    RuntimeException secretCloseFailure = new RuntimeException("secret lease close failure");
    try (SqlitePreparedPairPublication prepared =
        new SqlitePreparedPairPublication(
            pair,
            pair.bookTargetPath(),
            pair.secretTargetPath(),
            RestoredBookTargetPolicy.REQUIRE_ABSENT,
            lease(pair.bookTargetPath(), () -> bookLeaseClosed.set(true)),
            lease(
                pair.secretTargetPath(),
                () -> {
                  if (secretLeaseFailed.compareAndSet(false, true)) {
                    throw secretCloseFailure;
                  }
                }))) {
      assertSame(secretCloseFailure, assertThrows(RuntimeException.class, prepared::close));
      assertTrue(bookLeaseClosed.get());
      assertNull(SqliteJournaledStageAccess.finalTargetForCurrentThread(pair.bookStagePath()));
    }
  }

  @Test
  void preparedPairPreservesBothLeaseCloseFailures() throws Exception {
    SqlitePublicationTransactionPair pair = reserve(new FaultTransactions(Mode.PUBLISH_FAILURE));
    RuntimeException secretCloseFailure = new RuntimeException("secret lease close failure");
    RuntimeException bookCloseFailure = new RuntimeException("book lease close failure");
    AtomicBoolean bookLeaseFailed = new AtomicBoolean();
    AtomicBoolean secretLeaseFailed = new AtomicBoolean();
    try (SqlitePreparedPairPublication prepared =
        new SqlitePreparedPairPublication(
            pair,
            pair.bookTargetPath(),
            pair.secretTargetPath(),
            RestoredBookTargetPolicy.REQUIRE_ABSENT,
            lease(
                pair.bookTargetPath(),
                () -> {
                  if (bookLeaseFailed.compareAndSet(false, true)) {
                    throw bookCloseFailure;
                  }
                }),
            lease(
                pair.secretTargetPath(),
                () -> {
                  if (secretLeaseFailed.compareAndSet(false, true)) {
                    throw secretCloseFailure;
                  }
                }))) {
      RuntimeException failure = assertThrows(RuntimeException.class, prepared::close);

      assertSame(secretCloseFailure, failure);
      assertEquals(1, failure.getSuppressed().length);
      assertSame(bookCloseFailure, failure.getSuppressed()[0]);
    }
  }

  @Test
  void backupSnapshotMapsOwnedStageReadFailureWithoutReleasingJournalRecovery() throws Exception {
    SqlitePublicationTransactionPair pair = reserve(new FaultTransactions(Mode.PUBLISH_FAILURE));
    IOException readFailure = new IOException("backup stage read failure");
    try (SqliteJournaledStagedBackupPair staged =
        new SqliteJournaledStagedBackupPair(
            pair,
            pair.bookStagePath(),
            passphrase("backup snapshot read failure"),
            new SqliteProtectedBookVerificationSupport(),
            ignoredStage -> {
              throw readFailure;
            })) {
      IllegalStateException failure = assertThrows(IllegalStateException.class, staged::snapshot);
      assertSame(readFailure, failure.getCause());
      assertEquals(
          "Failed to read the journal-owned encrypted backup stage.", failure.getMessage());
    }
  }

  private SqlitePublicationTransactionPair reserve(FaultTransactions transactions)
      throws IOException {
    Path parent = tempDirectory.resolve("pair-fault");
    Files.createDirectories(parent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parent);
    return SqlitePublicationTransactionPair.reserve(
        transactions,
        parent.resolve("backup.sqlite"),
        parent.resolve("backup.key"),
        RestoredBookTargetPolicy.REQUIRE_ABSENT,
        PublicationTransactionOwnerContext.fromCanonicalDescription("pair fault test"));
  }

  private static SqliteBookPassphrase passphrase(String source) {
    return SqliteBookPassphrase.fromUtf8Bytes(
        source, source.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static HeldLease lease(Path artifactPath, Runnable closeAction) {
    return new HeldLease() {
      @Override
      public Path artifactPath() {
        return artifactPath;
      }

      @Override
      public void close() {
        closeAction.run();
      }
    };
  }

  /** Selects the controlled journal fault exposed by the fake transaction service. */
  private enum Mode {
    COLLISION,
    RECOVERY_FAILURE,
    INCOMPLETE_RESULT,
    PUBLISH_FAILURE
  }

  private record FaultTransactions(Mode mode) implements PublicationTransactionService {
    @Override
    public PublicationTransactionResult publish(PublicationTransactionRequest request) {
      throw new AssertionError("Pair faults reserve before publication.");
    }

    @Override
    public PublicationTransactionStageReservation reserveStages(
        PublicationTransactionRequest request) throws IOException {
      return PublicationTransactionPublisher.openCanonical().reserveStages(request);
    }

    @Override
    public PublicationTransactionResult publishReservedStages(
        PublicationTransactionStageReservation reservation) throws IOException {
      if (mode == Mode.INCOMPLETE_RESULT) {
        return new PublicationTransactionResult(
            reservation.transactionId(),
            PublicationTransactionState.BLOCKED,
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.NONE_COMMITTED, PublicationCleanupOutcome.INCOMPLETE));
      }
      if (mode == Mode.PUBLISH_FAILURE) {
        throw new IOException("publish failure");
      }
      throw new FileAlreadyExistsException("concurrent final artifact");
    }

    @Override
    public PublicationTransactionResult recover(PublicationTransactionId transactionId)
        throws IOException {
      if (mode == Mode.RECOVERY_FAILURE) {
        throw new IOException("recovery failure");
      }
      return new PublicationTransactionResult(
          transactionId,
          PublicationTransactionState.BLOCKED,
          new PublicationTransactionOutcome(
              PublicationCommitOutcome.NONE_COMMITTED, PublicationCleanupOutcome.INCOMPLETE));
    }

    @Override
    public PublicationTransactionRecoveryReceipt recoverWithReceipt(
        PublicationTransactionId transactionId) {
      throw new AssertionError("Pair faults do not request receipts.");
    }

    @Override
    public Optional<PublicationTransactionRecoveryReceipt> recoverMatchingOwnerContext(
        PublicationTransactionOwnerContext ownerContext) {
      throw new AssertionError("Pair faults do not discover journals by owner context.");
    }
  }
}
