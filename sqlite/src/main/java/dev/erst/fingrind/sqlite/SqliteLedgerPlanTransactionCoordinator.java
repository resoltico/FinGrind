package dev.erst.fingrind.sqlite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** Owns SQLite ledger-plan transaction state, deferred begin, and missing-book cleanup policy. */
final class SqliteLedgerPlanTransactionCoordinator {
  /** Callback that removes any created missing-book artifacts after a failed transaction path. */
  @FunctionalInterface
  interface ArtifactCleanupAction {
    /** Removes any created missing-book artifacts rooted at the supplied preexisting ancestor. */
    void cleanup(@Nullable Path preexistingAncestorDirectory);
  }

  private final SqliteStoreContext context;
  private LedgerPlanTransactionState transactionState = new NoLedgerPlanTransaction();

  /** Creates one coordinator bound to one immutable SQLite store context. */
  SqliteLedgerPlanTransactionCoordinator(SqliteStoreContext context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  void begin(Supplier<SqliteNativeDatabase> databaseSupplier, ArtifactCleanupAction cleanupAction) {
    context.accessMode().requireWritableMutation();
    if (transactionState instanceof ActiveLedgerPlanTransaction) {
      throw new IllegalStateException("Ledger plan transaction is already active.");
    }
    boolean missingBookAtStart = Files.notExists(context.bookPath());
    ArtifactCleanupState artifactCleanupState =
        missingBookAtStart
            ? new MissingBookArtifactsPending(
                SqliteLedgerPlanArtifactCleanup.nearestExistingAncestor(context.bookPath()))
            : new NoArtifactCleanup();
    transactionState =
        new ActiveLedgerPlanTransaction(new DatabaseTransactionDeferred(), artifactCleanupState);
    if (context.accessMode().defersMissingBookOpen() && missingBookAtStart) {
      return;
    }
    noteBookArtifactsMayMutate();
    SqliteBookSchemaBootstrap.ensureParentDirectory(context.bookPath());
    try {
      Objects.requireNonNull(databaseSupplier.get(), "activeDatabase");
    } catch (RuntimeException exception) {
      ActiveLedgerPlanTransaction activeTransaction =
          (ActiveLedgerPlanTransaction) transactionState;
      try {
        cleanupCreatedMissingBookArtifacts(activeTransaction, cleanupAction);
      } catch (RuntimeException cleanupFailure) {
        exception.addSuppressed(cleanupFailure);
      } finally {
        reset();
      }
      throw exception;
    }
  }

  void commit(Supplier<SqliteNativeDatabase> databaseSupplier) {
    if (!(transactionState instanceof ActiveLedgerPlanTransaction activeTransaction)) {
      throw new IllegalStateException("No ledger plan transaction is active.");
    }
    if (!activeTransaction.begunInDatabase()) {
      reset();
      return;
    }
    try {
      databaseSupplier.get().executeStatement("commit");
      reset();
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(
          "Failed to commit SQLite ledger plan transaction.", exception);
    } catch (IllegalStateException exception) {
      throw new IllegalStateException(
          "Failed to commit SQLite ledger plan transaction.", exception);
    }
  }

  void rollback(
      @Nullable SqliteNativeDatabase publishedDatabase, ArtifactCleanupAction cleanupAction) {
    if (!(transactionState instanceof ActiveLedgerPlanTransaction activeTransaction)) {
      return;
    }
    boolean rollbackDatabase = activeTransaction.begunInDatabase();
    boolean cleanupCreatedBookArtifacts = activeTransaction.createdBookArtifacts();
    @Nullable Path preexistingAncestorDirectory = activeTransaction.preexistingAncestorDirectory();
    if (rollbackDatabase && publishedDatabase != null) {
      SqliteStoreOperations.rollbackQuietly(publishedDatabase);
    }
    try {
      if (cleanupCreatedBookArtifacts) {
        cleanupAction.cleanup(preexistingAncestorDirectory);
      }
    } finally {
      reset();
    }
  }

  void beginImmediateIfNeeded(SqliteNativeDatabase activeDatabase) {
    if (transactionState instanceof ActiveLedgerPlanTransaction activeTransaction
        && !activeTransaction.begunInDatabase()) {
      activeDatabase.executeStatement("begin immediate");
      transactionState = activeTransaction.withBegunDatabase();
    }
  }

  void noteBookArtifactsMayMutate() {
    if (transactionState instanceof ActiveLedgerPlanTransaction activeTransaction) {
      transactionState = activeTransaction.withCreatedBookArtifacts();
    }
  }

  boolean active() {
    return transactionState instanceof ActiveLedgerPlanTransaction;
  }

  boolean begunInDatabase() {
    return transactionState instanceof ActiveLedgerPlanTransaction activeTransaction
        && activeTransaction.begunInDatabase();
  }

  boolean createdBookArtifacts() {
    return transactionState instanceof ActiveLedgerPlanTransaction activeTransaction
        && activeTransaction.createdBookArtifacts();
  }

  @Nullable Path preexistingAncestorDirectory() {
    return switch (transactionState) {
      case NoLedgerPlanTransaction ignored -> null;
      case ActiveLedgerPlanTransaction activeTransaction ->
          activeTransaction.preexistingAncestorDirectory();
    };
  }

  void reset() {
    transactionState = new NoLedgerPlanTransaction();
  }

  private void cleanupCreatedMissingBookArtifacts(
      ActiveLedgerPlanTransaction activeTransaction, ArtifactCleanupAction cleanupAction) {
    cleanupAction.cleanup(activeTransaction.preexistingAncestorDirectory());
  }

  /** Internal ledger-plan transaction model for one store lifecycle instance. */
  private sealed interface LedgerPlanTransactionState
      permits NoLedgerPlanTransaction, ActiveLedgerPlanTransaction {}

  /** Lifecycle state when no ledger-plan transaction is active. */
  private record NoLedgerPlanTransaction() implements LedgerPlanTransactionState {}

  /** Active ledger-plan transaction with explicit database and artifact tracking state. */
  private record ActiveLedgerPlanTransaction(
      DatabaseTransactionState databaseTransactionState, ArtifactCleanupState artifactCleanupState)
      implements LedgerPlanTransactionState {
    private ActiveLedgerPlanTransaction {
      Objects.requireNonNull(databaseTransactionState, "databaseTransactionState");
      Objects.requireNonNull(artifactCleanupState, "artifactCleanupState");
    }

    boolean begunInDatabase() {
      return databaseTransactionState instanceof DatabaseTransactionBegun;
    }

    boolean createdBookArtifacts() {
      return artifactCleanupState instanceof MissingBookArtifactsCreated;
    }

    @Nullable Path preexistingAncestorDirectory() {
      return artifactCleanupState.preexistingAncestorDirectory();
    }

    ActiveLedgerPlanTransaction withBegunDatabase() {
      return new ActiveLedgerPlanTransaction(new DatabaseTransactionBegun(), artifactCleanupState);
    }

    ActiveLedgerPlanTransaction withCreatedBookArtifacts() {
      return switch (artifactCleanupState) {
        case NoArtifactCleanup ignored -> this;
        case MissingBookArtifactsPending pending ->
            new ActiveLedgerPlanTransaction(
                databaseTransactionState,
                new MissingBookArtifactsCreated(pending.preexistingAncestorDirectory()));
        case MissingBookArtifactsCreated ignored -> this;
      };
    }
  }

  /** Database-begin state for one active ledger-plan transaction. */
  private sealed interface DatabaseTransactionState
      permits DatabaseTransactionDeferred, DatabaseTransactionBegun {}

  /** Active ledger-plan transaction before the SQLite database transaction begins. */
  private record DatabaseTransactionDeferred() implements DatabaseTransactionState {}

  /** Active ledger-plan transaction after the SQLite database transaction begins. */
  private record DatabaseTransactionBegun() implements DatabaseTransactionState {}

  /** Missing-book artifact cleanup state for one active ledger-plan transaction. */
  private sealed interface ArtifactCleanupState
      permits NoArtifactCleanup, MissingBookArtifactsPending, MissingBookArtifactsCreated {
    /** Returns the nearest preexisting ancestor that cleanup must preserve, if any. */
    @Nullable default Path preexistingAncestorDirectory() {
      return null;
    }
  }

  /** Active ledger-plan transaction that did not begin from a missing-book path. */
  private record NoArtifactCleanup() implements ArtifactCleanupState {}

  /** Missing-book transaction before schema/bootstrap work creates cleanup-eligible artifacts. */
  private record MissingBookArtifactsPending(@Nullable Path preexistingAncestorDirectory)
      implements ArtifactCleanupState {}

  /** Missing-book transaction after schema/bootstrap work creates cleanup-eligible artifacts. */
  private record MissingBookArtifactsCreated(@Nullable Path preexistingAncestorDirectory)
      implements ArtifactCleanupState {}
}
