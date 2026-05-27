package dev.erst.fingrind.executor;

import dev.erst.fingrind.executor.spi.LedgerPlanTransaction;
import org.jspecify.annotations.Nullable;

/** In-memory composite session used by executor tests and non-durable harness composition. */
public final class InMemoryBookSession extends AbstractInMemoryBookReadSession
    implements LedgerPlanTransaction, AutoCloseable {
  private @Nullable InMemoryBookSessionSnapshot transactionSnapshot;

  @Override
  public void close() {
    // No resources to release for the in-memory test fixture.
  }

  @Override
  public void beginLedgerPlanTransaction() {
    InMemoryBookSessionSupport.withLock(
        lock,
        () -> {
          if (transactionSnapshot != null) {
            throw new IllegalStateException("Ledger plan transaction is already active.");
          }
          transactionSnapshot = snapshotState();
        });
  }

  @Override
  public void commitLedgerPlanTransaction() {
    InMemoryBookSessionSupport.withLock(
        lock,
        () -> {
          if (transactionSnapshot == null) {
            throw new IllegalStateException("No ledger plan transaction is active.");
          }
          transactionSnapshot = null;
        });
  }

  @Override
  public void rollbackLedgerPlanTransaction() {
    InMemoryBookSessionSupport.withLock(
        lock,
        () -> {
          InMemoryBookSessionSnapshot snapshot = transactionSnapshot;
          if (snapshot == null) {
            return;
          }
          restoreSnapshot(snapshot);
          transactionSnapshot = null;
        });
  }
}
