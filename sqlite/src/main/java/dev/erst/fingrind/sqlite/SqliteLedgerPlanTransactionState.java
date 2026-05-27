package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Internal ledger-plan transaction state model for one SQLite store lifecycle instance. */
sealed interface LedgerPlanTransactionState
    permits NoLedgerPlanTransaction, ActiveLedgerPlanTransaction {}

/** Lifecycle state when no ledger-plan transaction is active. */
record NoLedgerPlanTransaction() implements LedgerPlanTransactionState {}

/** Active ledger-plan transaction with explicit database and artifact tracking state. */
record ActiveLedgerPlanTransaction(
    DatabaseTransactionState databaseTransactionState, ArtifactCleanupState artifactCleanupState)
    implements LedgerPlanTransactionState {
  ActiveLedgerPlanTransaction {
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
sealed interface DatabaseTransactionState
    permits DatabaseTransactionDeferred, DatabaseTransactionBegun {}

/** Active ledger-plan transaction before the SQLite database transaction begins. */
record DatabaseTransactionDeferred() implements DatabaseTransactionState {}

/** Active ledger-plan transaction after the SQLite database transaction begins. */
record DatabaseTransactionBegun() implements DatabaseTransactionState {}

/** Missing-book artifact cleanup state for one active ledger-plan transaction. */
sealed interface ArtifactCleanupState
    permits NoArtifactCleanup, MissingBookArtifactsPending, MissingBookArtifactsCreated {
  /** Returns the nearest preexisting ancestor that cleanup must preserve, if any. */
  @Nullable default Path preexistingAncestorDirectory() {
    return null;
  }
}

/** Active ledger-plan transaction that did not begin from a missing-book path. */
record NoArtifactCleanup() implements ArtifactCleanupState {}

/** Missing-book transaction before schema/bootstrap work creates cleanup-eligible artifacts. */
record MissingBookArtifactsPending(@Nullable Path preexistingAncestorDirectory)
    implements ArtifactCleanupState {}

/** Missing-book transaction after schema/bootstrap work creates cleanup-eligible artifacts. */
record MissingBookArtifactsCreated(@Nullable Path preexistingAncestorDirectory)
    implements ArtifactCleanupState {}
