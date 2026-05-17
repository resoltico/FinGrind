package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Focused coverage for isolated ledger-plan transaction coordination branches. */
class SqliteLedgerPlanTransactionCoordinatorTest {
  @TempDir Path tempDirectory;

  @BeforeEach
  void hardenTempDirectory() {
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(tempDirectory);
  }

  @Test
  void noteBookArtifactsMayMutate_isIdempotentForDeferredMissingBookTransactions() {
    Path bookPath = tempDirectory.resolve("deferred-plan").resolve("nested").resolve("book.sqlite");
    SqliteLedgerPlanTransactionCoordinator coordinator =
        new SqliteLedgerPlanTransactionCoordinator(
            new SqliteStoreContext(
                bookPath, SqliteStoreAccessMode.PLAN_EXECUTION, SqliteNativeBootstrap::api));
    AtomicInteger databaseOpenCalls = new AtomicInteger();

    coordinator.begin(
        () -> {
          databaseOpenCalls.incrementAndGet();
          throw new AssertionError(
              "Deferred missing-book transactions must not open the database.");
        },
        ignored -> {});

    assertTrue(coordinator.active());
    assertFalse(coordinator.begunInDatabase());
    assertFalse(coordinator.createdBookArtifacts());

    coordinator.noteBookArtifactsMayMutate();
    coordinator.noteBookArtifactsMayMutate();

    assertEquals(0, databaseOpenCalls.get());
    assertTrue(coordinator.createdBookArtifacts());
    assertEquals(
        tempDirectory.toAbsolutePath().normalize(), coordinator.preexistingAncestorDirectory());

    NullablePathBox cleanedAncestorDirectory = new NullablePathBox();
    coordinator.rollback(
        null, ancestorDirectory -> cleanedAncestorDirectory.value = ancestorDirectory);

    assertEquals(tempDirectory.toAbsolutePath().normalize(), cleanedAncestorDirectory.value);
    assertFalse(coordinator.active());
    assertFalse(coordinator.createdBookArtifacts());
  }

  /** Captures one nullable cleanup callback argument for assertions. */
  private static final class NullablePathBox {
    private @Nullable Path value;
  }
}
